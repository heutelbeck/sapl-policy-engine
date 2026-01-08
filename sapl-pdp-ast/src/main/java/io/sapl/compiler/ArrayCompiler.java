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

import io.sapl.api.model.*;
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.ArrayExpression;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles array literal expressions using the PRECOMPILED pattern.
 * Elements that evaluate to UndefinedValue are dropped from the result.
 */
@UtilityClass
public class ArrayCompiler {

    public static CompiledExpression compile(ArrayExpression expr, CompilationContext ctx) {
        val elements = expr.elements();

        if (elements.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        val compiled = new ArrayList<CompiledExpression>(elements.size());
        for (var element : elements) {
            val result = ExpressionCompiler.compile(element, ctx);
            if (result instanceof ErrorValue) {
                return result;
            }
            compiled.add(result);
        }

        return buildFromCompiled(compiled, expr.location());
    }

    /**
     * Builds an optimized array operator from pre-compiled elements.
     * Used by ArrayExpression compilation and "each" filter compilation.
     *
     * @param compiled the compiled elements (Value/PureOperator/StreamOperator)
     * @param location source location for error reporting
     * @return appropriate CompiledExpression based on element types
     */
    static CompiledExpression buildFromCompiled(List<CompiledExpression> compiled, SourceLocation location) {
        val cat = CategorizedExpressions.categorize(compiled);

        if (cat.hasOnlyValues()) {
            return buildArrayFromValues(List.of(cat.values()));
        }
        if (cat.streamCount() == 0) {
            return new AllPureArray(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.totalCount(), location);
        }
        if (cat.hasSingleStream()) {
            return new SingleStreamArray(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.streamIndices()[0], cat.streams()[0], cat.totalCount());
        }
        return new MultiStreamArray(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                cat.streamIndices(), cat.streams(), cat.totalCount());
    }

    private static ArrayValue buildArrayFromValues(List<Value> values) {
        val builder = ArrayValue.builder();
        for (var v : values) {
            if (!(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        return builder.build();
    }

    static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Categorizes compiled expressions into Value, PureOperator, and StreamOperator
     * strata.
     * Used by ArrayCompiler, ObjectCompiler, and AttributeCompiler.
     */
    record CategorizedExpressions(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalCount) {

        static CategorizedExpressions categorize(List<CompiledExpression> compiled) {
            val streamIndices = new ArrayList<Integer>();
            val streams       = new ArrayList<StreamOperator>();
            val pureIndices   = new ArrayList<Integer>();
            val pureOperators = new ArrayList<PureOperator>();
            val valueIndices  = new ArrayList<Integer>();
            val values        = new ArrayList<Value>();

            for (int i = 0; i < compiled.size(); i++) {
                switch (compiled.get(i)) {
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

            return new CategorizedExpressions(toIntArray(valueIndices), values.toArray(Value[]::new),
                    toIntArray(pureIndices), pureOperators.toArray(PureOperator[]::new), toIntArray(streamIndices),
                    streams.toArray(StreamOperator[]::new), compiled.size());
        }

        int streamCount() {
            return streams.length;
        }

        boolean hasOnlyValues() {
            return pureOperators.length == 0 && streams.length == 0;
        }

        boolean hasSingleStream() {
            return streams.length == 1;
        }
    }

    /**
     * Array with all pure elements (values and pure operators, no streams).
     */
    public record AllPureArray(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalElements,
            SourceLocation location) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val elements = new Value[totalElements];

            for (int i = 0; i < valueIndices.length; i++) {
                elements[valueIndices[i]] = values[i];
            }

            for (int i = 0; i < pureIndices.length; i++) {
                val value = pureOperators[i].evaluate(ctx);
                if (value instanceof ErrorValue) {
                    return value;
                }
                elements[pureIndices[i]] = value;
            }

            val builder = ArrayValue.builder();
            for (var element : elements) {
                if (!(element instanceof UndefinedValue)) {
                    builder.add(element);
                }
            }
            return builder.build();
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (var op : pureOperators) {
                if (op.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Array with exactly one stream element.
     */
    public record SingleStreamArray(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator streamOp,
            int totalElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return streamOp.stream().switchMap(tracedValue -> {
                var streamVal = tracedValue.value();
                if (streamVal instanceof ErrorValue) {
                    return Flux.just(tracedValue);
                }

                return Flux.deferContextual(ctx -> {
                    var evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        var value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, tracedValue.contributingAttributes()));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    elements[streamIndex] = streamVal;

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), tracedValue.contributingAttributes()));
                });
            });
        }
    }

    /**
     * Array with multiple stream elements.
     */
    public record MultiStreamArray(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            List<Flux<TracedValue>> fluxList = new ArrayList<>(streams.length);
            for (var s : streams) {
                fluxList.add(s.stream());
            }

            return Flux.combineLatest(fluxList, arr -> {
                val combinedTraces = new ArrayList<AttributeRecord>();
                val streamValues   = new TracedValue[arr.length];
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
                    var evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        var value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, combined.traces));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    for (int i = 0; i < streamIndices.length; i++) {
                        elements[streamIndices[i]] = combined.values[i].value();
                    }

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), combined.traces));
                });
            });
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {}
    }

}
