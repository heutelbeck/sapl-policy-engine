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
package io.sapl.compiler.expressions;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import io.sapl.ast.Expression;
import io.sapl.ast.FunctionCall;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiler for SAPL function calls.
 * <p>
 * Functions are synchronous operations that take arguments and return a value.
 * Unlike attribute finders, functions do not produce streams - they are pure
 * computations.
 * <p>
 * The compiler optimizes based on argument types:
 * <ul>
 * <li>No arguments: simple NoArgsFunction record</li>
 * <li>All Value arguments: constant fold at compile time</li>
 * <li>All Value/PureOperator arguments: returns PureOperator</li>
 * <li>Any StreamOperator argument: returns StreamOperator</li>
 * </ul>
 */
@UtilityClass
public class FunctionCallCompiler {

    public static CompiledExpression compile(FunctionCall call, CompilationContext ctx) {
        return compile(call.name().full(), call.arguments(), call.location(), ctx);
    }

    public static CompiledExpression compile(String functionName, List<Expression> arguments, SourceLocation location,
            CompilationContext ctx) {
        // Zero arguments - use simple record
        if (arguments.isEmpty()) {
            return compileNoArgs(functionName, ctx);
        }

        // Compile all arguments
        val compiledArgs = new ArrayList<CompiledExpression>(arguments.size());
        for (var argExpr : arguments) {
            val compiled = ExpressionCompiler.compile(argExpr, ctx);
            if (compiled instanceof ErrorValue err) {
                return err;
            }
            compiledArgs.add(compiled);
        }

        // Categorize arguments by type
        val valueIndices  = new ArrayList<Integer>();
        val values        = new ArrayList<Value>();
        val pureIndices   = new ArrayList<Integer>();
        val pureOperators = new ArrayList<PureOperator>();
        val streamIndices = new ArrayList<Integer>();
        val streams       = new ArrayList<StreamOperator>();

        for (int i = 0; i < compiledArgs.size(); i++) {
            switch (compiledArgs.get(i)) {
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

        int totalArgs   = compiledArgs.size();
        int streamCount = streams.size();

        // All values - constant fold at compile time
        if (streamCount == 0 && pureOperators.isEmpty()) {
            return evaluateConstant(functionName, values, ctx);
        }

        // No streams - return PureOperator
        if (streamCount == 0) {
            return new AllPureFunction(functionName, ArrayCompiler.toIntArray(valueIndices),
                    values.toArray(Value[]::new), ArrayCompiler.toIntArray(pureIndices),
                    pureOperators.toArray(PureOperator[]::new), totalArgs, location);
        }

        // Single stream
        if (streamCount == 1) {
            return new SingleStreamFunction(functionName, ArrayCompiler.toIntArray(valueIndices),
                    values.toArray(Value[]::new), ArrayCompiler.toIntArray(pureIndices),
                    pureOperators.toArray(PureOperator[]::new), streamIndices.getFirst(), streams.getFirst(), totalArgs,
                    location);
        }

        // Multiple streams
        return new MultiStreamFunction(functionName, ArrayCompiler.toIntArray(valueIndices),
                values.toArray(Value[]::new), ArrayCompiler.toIntArray(pureIndices),
                pureOperators.toArray(PureOperator[]::new), ArrayCompiler.toIntArray(streamIndices),
                streams.toArray(StreamOperator[]::new), totalArgs, location);
    }

    private static CompiledExpression compileNoArgs(String functionName, CompilationContext ctx) {
        val invocation = new FunctionInvocation(functionName, List.of());
        return ctx.getFunctionBroker().evaluateFunction(invocation);
    }

    private static Value evaluateConstant(String functionName, List<Value> values, CompilationContext ctx) {
        val args       = values.stream().filter(v -> !(v instanceof UndefinedValue)).toList();
        val invocation = new FunctionInvocation(functionName, args);
        return ctx.getFunctionBroker().evaluateFunction(invocation);
    }

    @SuppressWarnings("unchecked")
    private static Value invokeFunction(String functionName, Object argsOrError, EvaluationContext ctx) {
        if (argsOrError instanceof ErrorValue err) {
            return err;
        }
        val invocation = new FunctionInvocation(functionName, (List<Value>) argsOrError);
        return ctx.functionBroker().evaluateFunction(invocation);
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
            args.set(streamIndices[i], streamValues[i].value());
        }

        return args.stream().filter(v -> !(v instanceof UndefinedValue)).toList();
    }

    /**
     * Function with no arguments - simplest case.
     */
    public record NoArgsFunction(String functionName, SourceLocation location) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return ctx.functionBroker().evaluateFunction(new FunctionInvocation(functionName, List.of()));
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }
    }

    /**
     * All arguments are Value or PureOperator - evaluates synchronously at runtime.
     */
    public record AllPureFunction(
            String functionName,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalArgs,
            SourceLocation location) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            var args = buildArgumentArray(valueIndices, values, pureIndices, pureOperators, totalArgs, ctx);
            return invokeFunction(functionName, args, ctx);
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (var p : pureOperators) {
                if (p.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Exactly one argument is a StreamOperator.
     */
    public record SingleStreamFunction(
            String functionName,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator argStream,
            int totalArgs,
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
                    var args    = buildArgumentArrayWithStreamValue(valueIndices, values, pureIndices, pureOperators,
                            streamIndex, argVal, totalArgs, evalCtx);
                    var result  = invokeFunction(functionName, args, evalCtx);
                    return Flux.just(new TracedValue(result, tracedArg.contributingAttributes()));
                });
            });
        }
    }

    /**
     * Multiple arguments are StreamOperators.
     */
    public record MultiStreamFunction(
            String functionName,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalArgs,
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
                    var args    = buildArgumentArrayWithMultipleStreams(valueIndices, values, pureIndices,
                            pureOperators, streamIndices, combined.values, totalArgs, evalCtx);
                    var result  = invokeFunction(functionName, args, evalCtx);
                    return Flux.just(new TracedValue(result, combined.traces));
                });
            });
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {}
    }

}
