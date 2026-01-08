/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.compiler;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.AttributeStep;
import io.sapl.ast.EnvironmentAttribute;
import io.sapl.ast.Expression;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.AttributeOptionsCompiler.*;

@UtilityClass
public class AttributeCompiler {

    private static final String ERROR_UNDEFINED_ENTITY_IN_ATTRIBUTE_ACCESS = "Undefined entity in attribute access";

    public static StreamOperator compileEnvironmentAttribute(EnvironmentAttribute attr, CompilationContext ctx) {
        return compileAttribute(null, attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    public static StreamOperator compileAttributeStep(AttributeStep attr, CompilationContext ctx) {
        return compileAttribute(attr.base(), attr.name().full(), attr.arguments(), attr.options(), attr.head(),
                attr.location(), ctx);
    }

    private static StreamOperator compileAttribute(Expression entityExpr, String attributeName,
            @NonNull List<Expression> arguments, Expression optionsExpr, boolean head, @NonNull SourceLocation location,
            CompilationContext ctx) {

        val compiledOptions = AttributeOptionsCompiler.compileOptions(optionsExpr, location, ctx);
        if (compiledOptions instanceof ErrorValue err) {
            return errorStream(err);
        }

        CompiledExpression compiledEntity = null;
        if (entityExpr != null) {
            compiledEntity = ExpressionCompiler.compile(entityExpr, ctx);
            if (compiledEntity instanceof ErrorValue err) {
                return errorStream(err);
            }
        }

        val compiledArgs = new ArrayList<CompiledExpression>(arguments.size());
        for (var argExpr : arguments) {
            val compiled = ExpressionCompiler.compile(argExpr, ctx);
            if (compiled instanceof ErrorValue err) {
                return errorStream(err);
            }
            compiledArgs.add(compiled);
        }

        return buildOptimizedOperator(compiledEntity, compiledArgs, compiledOptions, attributeName, head, location);
    }

    private static StreamOperator errorStream(ErrorValue error) {
        return () -> Flux.just(new TracedValue(error, List.of()));
    }

    private static StreamOperator buildOptimizedOperator(CompiledExpression entity, List<CompiledExpression> arguments,
            CompiledExpression options, String attributeName, boolean head, SourceLocation location) {

        boolean entityIsStream = entity instanceof StreamOperator;
        boolean entityIsPure   = entity instanceof PureOperator;
        Value   entityValue    = entity instanceof Value v ? v : null;

        val streamIndices = new ArrayList<Integer>();
        val streams       = new ArrayList<StreamOperator>();
        val pureIndices   = new ArrayList<Integer>();
        val pureOperators = new ArrayList<PureOperator>();
        val valueIndices  = new ArrayList<Integer>();
        val values        = new ArrayList<Value>();

        for (int i = 0; i < arguments.size(); i++) {
            switch (arguments.get(i)) {
            case Value v          -> {
                valueIndices.add(i);
                values.add(v);
            }
            case PureOperator p   -> {
                pureIndices.add(i);
                pureOperators.add(p);
            }
            case StreamOperator s -> {
                streamIndices.add(i);
                streams.add(s);
            }
            }
        }

        int totalArgs   = arguments.size();
        int streamCount = streams.size() + (entityIsStream ? 1 : 0);

        if (streamCount == 0) {
            return new AllPureAttribute(attributeName, entityValue, entityIsPure ? (PureOperator) entity : null,
                    toIntArray(valueIndices), values.toArray(Value[]::new), toIntArray(pureIndices),
                    pureOperators.toArray(PureOperator[]::new), totalArgs, options, head, location);
        } else if (streamCount == 1) {
            if (entityIsStream) {
                return new EntityStreamAttribute(attributeName, (StreamOperator) entity, toIntArray(valueIndices),
                        values.toArray(Value[]::new), toIntArray(pureIndices),
                        pureOperators.toArray(PureOperator[]::new), totalArgs, options, head, location);
            } else {
                return new SingleStreamAttribute(attributeName, entityValue,
                        entityIsPure ? (PureOperator) entity : null, toIntArray(valueIndices),
                        values.toArray(Value[]::new), toIntArray(pureIndices),
                        pureOperators.toArray(PureOperator[]::new), streamIndices.get(0), streams.get(0), totalArgs,
                        options, head, location);
            }
        } else {
            val allStreams       = new ArrayList<StreamOperator>(streamCount);
            val allStreamIndices = new ArrayList<Integer>(streamCount);
            if (entityIsStream) {
                allStreams.add((StreamOperator) entity);
                allStreamIndices.add(-1);
            }
            allStreams.addAll(streams);
            allStreamIndices.addAll(streamIndices);

            return new MultiStreamAttribute(attributeName, entityValue, entityIsPure ? (PureOperator) entity : null,
                    toIntArray(valueIndices), values.toArray(Value[]::new), toIntArray(pureIndices),
                    pureOperators.toArray(PureOperator[]::new), toIntArray(allStreamIndices),
                    allStreams.toArray(StreamOperator[]::new), totalArgs, options, head, location);
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    public record AllPureAttribute(
            String attributeName,
            Value entityValue,
            PureOperator entityPure,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalArgs,
            CompiledExpression options,
            boolean head,
            SourceLocation location) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctx -> {
                var evalCtx = ctx.get(EvaluationContext.class);

                Value entity = entityValue;
                if (entityPure != null) {
                    entity = entityPure.evaluate(evalCtx);
                    if (entity instanceof ErrorValue) {
                        return Flux.just(errorTracedValue(entity));
                    }
                }
                if (entity instanceof UndefinedValue) {
                    return Flux.just(errorTracedValue(Value.error(ERROR_UNDEFINED_ENTITY_IN_ATTRIBUTE_ACCESS)));
                }

                var optionsValue = evaluateOptions(options, evalCtx);
                if (optionsValue instanceof ErrorValue) {
                    return Flux.just(errorTracedValue(optionsValue));
                }

                var args = buildArgumentArray(valueIndices, values, pureIndices, pureOperators, totalArgs, evalCtx);
                if (args instanceof ErrorValue err) {
                    return Flux.just(errorTracedValue(err));
                }

                var invocation = createInvocation(attributeName, entity, (List<Value>) args, optionsValue, evalCtx);
                return invokeAndTrace(invocation, head, location);
            });
        }
    }

    public record EntityStreamAttribute(
            String attributeName,
            StreamOperator entityStream,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalArgs,
            CompiledExpression options,
            boolean head,
            SourceLocation location) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return entityStream.stream().switchMap(tracedEntity -> {
                var entityVal = tracedEntity.value();
                if (entityVal instanceof ErrorValue) {
                    return Flux.just(tracedEntity);
                }
                if (entityVal instanceof UndefinedValue) {
                    return Flux.just(errorTracedValue(Value.error(ERROR_UNDEFINED_ENTITY_IN_ATTRIBUTE_ACCESS)));
                }

                return Flux.deferContextual(ctx -> {
                    var evalCtx = ctx.get(EvaluationContext.class);

                    var optionsValue = evaluateOptions(options, evalCtx);
                    if (optionsValue instanceof ErrorValue) {
                        return Flux.just(errorTracedValue(optionsValue));
                    }

                    var args = buildArgumentArray(valueIndices, values, pureIndices, pureOperators, totalArgs, evalCtx);
                    if (args instanceof ErrorValue err) {
                        return Flux.just(errorTracedValue(err));
                    }

                    var invocation = createInvocation(attributeName, entityVal, (List<Value>) args, optionsValue,
                            evalCtx);
                    return invokeAndTrace(invocation, head, location)
                            .map(tv -> mergeTraces(tv, tracedEntity.contributingAttributes()));
                });
            });
        }
    }

    public record SingleStreamAttribute(
            String attributeName,
            Value entityValue,
            PureOperator entityPure,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator argStream,
            int totalArgs,
            CompiledExpression options,
            boolean head,
            SourceLocation location) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return argStream.stream().switchMap(tracedArg -> {
                var argVal = tracedArg.value();
                if (argVal instanceof ErrorValue) {
                    return Flux.just(tracedArg);
                }

                return Flux.deferContextual(ctx -> {
                    var evalCtx = ctx.get(EvaluationContext.class);

                    Value entity = entityValue;
                    if (entityPure != null) {
                        entity = entityPure.evaluate(evalCtx);
                        if (entity instanceof ErrorValue) {
                            return Flux.just(errorTracedValue(entity));
                        }
                    }

                    var optionsValue = evaluateOptions(options, evalCtx);
                    if (optionsValue instanceof ErrorValue) {
                        return Flux.just(errorTracedValue(optionsValue));
                    }

                    var args = buildArgumentArrayWithStreamValue(valueIndices, values, pureIndices, pureOperators,
                            streamIndex, argVal, totalArgs, evalCtx);
                    if (args instanceof ErrorValue err) {
                        return Flux.just(errorTracedValue(err));
                    }

                    var invocation = createInvocation(attributeName, entity, (List<Value>) args, optionsValue, evalCtx);
                    return invokeAndTrace(invocation, head, location)
                            .map(tv -> mergeTraces(tv, tracedArg.contributingAttributes()));
                });
            });
        }
    }

    public record MultiStreamAttribute(
            String attributeName,
            Value entityValue,
            PureOperator entityPure,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalArgs,
            CompiledExpression options,
            boolean head,
            SourceLocation location) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            List<Flux<TracedValue>> fluxList = new ArrayList<>(streams.length);
            for (var s : streams) {
                fluxList.add(s.stream());
            }

            return Flux.combineLatest(fluxList, arr -> {
                var combinedTraces = new ArrayList<AttributeRecord>();
                var streamValues   = new TracedValue[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    streamValues[i] = (TracedValue) arr[i];
                    combinedTraces.addAll(streamValues[i].contributingAttributes());
                }
                return new CombinedStreams(streamValues, combinedTraces);
            }).switchMap(combined -> {
                for (var tv : combined.values) {
                    if (tv.value() instanceof ErrorValue) {
                        return Flux.just(new TracedValue(tv.value(), combined.traces));
                    }
                }

                return Flux.deferContextual(ctx -> {
                    var evalCtx = ctx.get(EvaluationContext.class);

                    Value entity = entityValue;
                    if (streamIndices.length > 0 && streamIndices[0] == -1) {
                        entity = combined.values[0].value();
                    } else if (entityPure != null) {
                        entity = entityPure.evaluate(evalCtx);
                        if (entity instanceof ErrorValue) {
                            return Flux.just(errorTracedValue(entity));
                        }
                    }

                    var optionsValue = evaluateOptions(options, evalCtx);
                    if (optionsValue instanceof ErrorValue) {
                        return Flux.just(errorTracedValue(optionsValue));
                    }

                    var args = buildArgumentArrayWithMultipleStreams(valueIndices, values, pureIndices, pureOperators,
                            streamIndices, combined.values, totalArgs, evalCtx);
                    if (args instanceof ErrorValue err) {
                        return Flux.just(errorTracedValue(err));
                    }

                    var invocation = createInvocation(attributeName, entity, (List<Value>) args, optionsValue, evalCtx);
                    return invokeAndTrace(invocation, head, location).map(tv -> mergeTraces(tv, combined.traces));
                });
            });
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {}
    }

    private static Value evaluateOptions(CompiledExpression options, EvaluationContext ctx) {
        if (options instanceof Value v) {
            return v;
        }
        if (options instanceof PureOperator po) {
            return po.evaluate(ctx);
        }
        return ObjectValue.builder().build();
    }

    private static Object buildArgumentArray(int[] valueIndices, Value[] values, int[] pureIndices,
            PureOperator[] pureOperators, int totalArgs, EvaluationContext ctx) {
        var args = new ArrayList<Value>(totalArgs);
        for (int i = 0; i < totalArgs; i++) {
            args.add(null);
        }

        for (int i = 0; i < valueIndices.length; i++) {
            args.set(valueIndices[i], values[i]);
        }

        for (int i = 0; i < pureIndices.length; i++) {
            var value = pureOperators[i].evaluate(ctx);
            if (value instanceof ErrorValue) {
                return value;
            }
            args.set(pureIndices[i], value);
        }

        return args.stream().filter(v -> !(v instanceof UndefinedValue)).toList();
    }

    private static Object buildArgumentArrayWithStreamValue(int[] valueIndices, Value[] values, int[] pureIndices,
            PureOperator[] pureOperators, int streamIndex, Value streamValue, int totalArgs, EvaluationContext ctx) {
        var args = new ArrayList<Value>(totalArgs);
        for (int i = 0; i < totalArgs; i++) {
            args.add(null);
        }

        for (int i = 0; i < valueIndices.length; i++) {
            args.set(valueIndices[i], values[i]);
        }

        for (int i = 0; i < pureIndices.length; i++) {
            var value = pureOperators[i].evaluate(ctx);
            if (value instanceof ErrorValue) {
                return value;
            }
            args.set(pureIndices[i], value);
        }

        args.set(streamIndex, streamValue);

        return args.stream().filter(v -> !(v instanceof UndefinedValue)).toList();
    }

    private static Object buildArgumentArrayWithMultipleStreams(int[] valueIndices, Value[] values, int[] pureIndices,
            PureOperator[] pureOperators, int[] streamIndices, TracedValue[] streamValues, int totalArgs,
            EvaluationContext ctx) {
        var args = new ArrayList<Value>(totalArgs);
        for (int i = 0; i < totalArgs; i++) {
            args.add(null);
        }

        for (int i = 0; i < valueIndices.length; i++) {
            args.set(valueIndices[i], values[i]);
        }

        for (int i = 0; i < pureIndices.length; i++) {
            var value = pureOperators[i].evaluate(ctx);
            if (value instanceof ErrorValue) {
                return value;
            }
            args.set(pureIndices[i], value);
        }

        for (int i = 0; i < streamIndices.length; i++) {
            if (streamIndices[i] == -1) {
                continue;
            }
            args.set(streamIndices[i], streamValues[i].value());
        }

        return args.stream().filter(v -> !(v instanceof UndefinedValue)).toList();
    }

    private static AttributeFinderInvocation createInvocation(String attributeName, Value entity, List<Value> arguments,
            Value options, EvaluationContext ctx) {
        var configurationId = ctx.configurationId() != null ? ctx.configurationId() : "default";
        var timeout         = Duration.ofMillis(longOption(options, OPTION_INITIAL_TIMEOUT, DEFAULT_TIMEOUT_MS));
        var pollInterval    = Duration.ofMillis(longOption(options, OPTION_POLL_INTERVAL, DEFAULT_POLL_INTERVAL_MS));
        var backoff         = Duration.ofMillis(longOption(options, OPTION_BACKOFF, DEFAULT_BACKOFF_MS));
        var retries         = longOption(options, OPTION_RETRIES, DEFAULT_RETRIES);
        var fresh           = freshOption(options);

        return new AttributeFinderInvocation(configurationId, attributeName, entity, arguments, ctx.variables(),
                timeout, pollInterval, backoff, retries, fresh);
    }

    private static Flux<TracedValue> invokeAndTrace(AttributeFinderInvocation invocation, boolean head,
            SourceLocation location) {
        return Flux.deferContextual(ctx -> {
            var evalCtx = ctx.get(EvaluationContext.class);
            var stream  = evalCtx.attributeBroker().attributeStream(invocation).map(value -> {
                            var attributeRecord = new AttributeRecord(invocation, value, Instant.now(), location);
                            return new TracedValue(value, List.of(attributeRecord));
                        });

            if (head) {
                stream = stream.take(1);
            }

            return stream;
        });
    }

    private static TracedValue errorTracedValue(Value error) {
        return new TracedValue(error, List.of());
    }

    private static TracedValue mergeTraces(TracedValue base, List<AttributeRecord> additional) {
        if (additional.isEmpty()) {
            return base;
        }
        var merged = new ArrayList<>(base.contributingAttributes());
        merged.addAll(additional);
        return new TracedValue(base.value(), merged);
    }

    private static long longOption(Value options, String key, long defaultValue) {
        if (!(options instanceof ObjectValue obj)) {
            return defaultValue;
        }
        var value = obj.get(key);
        if (value instanceof NumberValue(BigDecimal value1)) {
            return value1.longValue();
        }
        return defaultValue;
    }

    private static boolean freshOption(Value options) {
        if (!(options instanceof ObjectValue obj)) {
            return false;
        }
        var value = obj.get(OPTION_FRESH);
        if (value instanceof BooleanValue(boolean value1)) {
            return value1;
        }
        return false;
    }

}
