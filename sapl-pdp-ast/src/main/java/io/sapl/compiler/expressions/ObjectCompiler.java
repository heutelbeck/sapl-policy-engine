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

import io.sapl.api.model.*;
import io.sapl.ast.ObjectExpression;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.expressions.ArrayCompiler.CategorizedExpressions;

/**
 * Compiles object literal expressions using the PRECOMPILED pattern.
 * Entries where the value evaluates to UndefinedValue are dropped from the
 * result.
 */
@UtilityClass
public class ObjectCompiler {

    public static CompiledExpression compile(ObjectExpression expr, CompilationContext ctx) {
        val entries = expr.entries();

        if (entries.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }

        val keys     = new String[entries.size()];
        val compiled = new ArrayList<CompiledExpression>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            val entry = entries.get(i);
            keys[i] = entry.key();
            val result = ExpressionCompiler.compile(entry.value(), ctx);
            if (result instanceof ErrorValue) {
                return result;
            }
            compiled.add(result);
        }

        return buildFromCompiled(keys, compiled, expr.location());
    }

    /**
     * Builds an optimized object operator from pre-compiled values.
     * Used by ObjectExpression compilation and "each" filter compilation.
     *
     * @param keys the keys for each entry
     * @param compiled the compiled values (Value/PureOperator/StreamOperator)
     * @param location metadata location for error reporting
     * @return appropriate CompiledExpression based on value types
     */
    static CompiledExpression buildFromCompiled(List<String> keys, List<CompiledExpression> compiled,
            SourceLocation location) {
        return buildFromCompiled(keys.toArray(String[]::new), compiled, location);
    }

    private static CompiledExpression buildFromCompiled(String[] keys, List<CompiledExpression> compiled,
            SourceLocation location) {
        val cat = CategorizedExpressions.categorize(compiled);

        if (cat.hasOnlyValues()) {
            return buildObjectFromValues(keys, cat.valueIndices(), cat.values());
        }
        if (cat.streamCount() == 0) {
            return new AllPureObject(keys, cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.totalCount(), location);
        }
        if (cat.hasSingleStream()) {
            return new SingleStreamObject(keys, cat.valueIndices(), cat.values(), cat.pureIndices(),
                    cat.pureOperators(), cat.streamIndices()[0], cat.streams()[0], cat.totalCount());
        }
        return new MultiStreamObject(keys, cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                cat.streamIndices(), cat.streams(), cat.totalCount());
    }

    private static ObjectValue buildObjectFromValues(String[] keys, int[] valueIndices, Value[] values) {
        val builder = ObjectValue.builder();
        for (int i = 0; i < valueIndices.length; i++) {
            val idx = valueIndices[i];
            val v   = values[i];
            if (!(v instanceof UndefinedValue)) {
                builder.put(keys[idx], v);
            }
        }
        return builder.build();
    }

    /**
     * Object with all pure values (values and pure operators, no streams).
     */
    public record AllPureObject(
            String[] keys,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalEntries,
            SourceLocation location) implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val entryValues = new Value[totalEntries];

            for (int i = 0; i < valueIndices.length; i++) {
                entryValues[valueIndices[i]] = values[i];
            }

            for (int i = 0; i < pureIndices.length; i++) {
                val value = pureOperators[i].evaluate(ctx);
                if (value instanceof ErrorValue) {
                    return value;
                }
                entryValues[pureIndices[i]] = value;
            }

            val builder = ObjectValue.builder();
            for (int i = 0; i < totalEntries; i++) {
                val v = entryValues[i];
                if (!(v instanceof UndefinedValue)) {
                    builder.put(keys[i], v);
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
     * Object with exactly one stream value.
     */
    public record SingleStreamObject(
            String[] keys,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator streamOp,
            int totalEntries) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return streamOp.stream().switchMap(tracedValue -> {
                var streamVal = tracedValue.value();
                if (streamVal instanceof ErrorValue) {
                    return Flux.just(tracedValue);
                }

                return Flux.deferContextual(ctx -> {
                    var evalCtx     = ctx.get(EvaluationContext.class);
                    val entryValues = new Value[totalEntries];

                    for (int i = 0; i < valueIndices.length; i++) {
                        entryValues[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        var value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, tracedValue.contributingAttributes()));
                        }
                        entryValues[pureIndices[i]] = value;
                    }

                    entryValues[streamIndex] = streamVal;

                    val builder = ObjectValue.builder();
                    for (int i = 0; i < totalEntries; i++) {
                        val v = entryValues[i];
                        if (v != null && !(v instanceof UndefinedValue)) {
                            builder.put(keys[i], v);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), tracedValue.contributingAttributes()));
                });
            });
        }
    }

    /**
     * Object with multiple stream values.
     */
    public record MultiStreamObject(
            String[] keys,
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalEntries) implements StreamOperator {

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
                    var evalCtx     = ctx.get(EvaluationContext.class);
                    val entryValues = new Value[totalEntries];

                    for (int i = 0; i < valueIndices.length; i++) {
                        entryValues[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        var value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, combined.traces));
                        }
                        entryValues[pureIndices[i]] = value;
                    }

                    for (int i = 0; i < streamIndices.length; i++) {
                        entryValues[streamIndices[i]] = combined.values[i].value();
                    }

                    val builder = ObjectValue.builder();
                    for (int i = 0; i < totalEntries; i++) {
                        val v = entryValues[i];
                        if (v != null && !(v instanceof UndefinedValue)) {
                            builder.put(keys[i], v);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), combined.traces));
                });
            });
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {}
    }

}
