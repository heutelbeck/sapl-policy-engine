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
import io.sapl.ast.ObjectExpression;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

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

        return buildOptimizedOperator(keys, compiled, expr.location());
    }

    private static CompiledExpression buildOptimizedOperator(String[] keys, List<CompiledExpression> compiled,
            SourceLocation location) {
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

        int streamCount  = streams.size();
        int totalEntries = compiled.size();

        if (streamCount == 0) {
            if (pureOperators.isEmpty()) {
                // All values - build object directly, dropping undefined
                return buildObjectFromValues(keys, valueIndices, values);
            }
            return new AllPureObject(keys, toIntArray(valueIndices), values.toArray(Value[]::new),
                    toIntArray(pureIndices), pureOperators.toArray(PureOperator[]::new), totalEntries, location);
        } else if (streamCount == 1) {
            return new SingleStreamObject(keys, toIntArray(valueIndices), values.toArray(Value[]::new),
                    toIntArray(pureIndices), pureOperators.toArray(PureOperator[]::new), streamIndices.get(0),
                    streams.get(0), totalEntries);
        } else {
            return new MultiStreamObject(keys, toIntArray(valueIndices), values.toArray(Value[]::new),
                    toIntArray(pureIndices), pureOperators.toArray(PureOperator[]::new), toIntArray(streamIndices),
                    streams.toArray(StreamOperator[]::new), totalEntries);
        }
    }

    private static ObjectValue buildObjectFromValues(String[] keys, List<Integer> valueIndices, List<Value> values) {
        val builder = ObjectValue.builder();
        for (int i = 0; i < valueIndices.size(); i++) {
            val idx = valueIndices.get(i);
            val v   = values.get(i);
            if (!(v instanceof UndefinedValue)) {
                builder.put(keys[idx], v);
            }
        }
        return builder.build();
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
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
            return streamOp.stream().map(tracedValue -> {
                var streamVal = tracedValue.value();
                if (streamVal instanceof ErrorValue) {
                    return tracedValue;
                }

                val entryValues = new Value[totalEntries];

                for (int i = 0; i < valueIndices.length; i++) {
                    entryValues[valueIndices[i]] = values[i];
                }

                // Pure operators evaluated without context - will be fixed when needed
                for (int i = 0; i < pureIndices.length; i++) {
                    entryValues[pureIndices[i]] = Value.UNDEFINED;
                }

                entryValues[streamIndex] = streamVal;

                val builder = ObjectValue.builder();
                for (int i = 0; i < totalEntries; i++) {
                    val v = entryValues[i];
                    if (v != null && !(v instanceof UndefinedValue)) {
                        builder.put(keys[i], v);
                    }
                }
                return new TracedValue(builder.build(), tracedValue.contributingAttributes());
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
                val combinedTraces = new ArrayList<io.sapl.api.pdp.internal.AttributeRecord>();
                val streamValues   = new TracedValue[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    streamValues[i] = (TracedValue) arr[i];
                    combinedTraces.addAll(streamValues[i].contributingAttributes());
                }
                return new CombinedStreams(streamValues, combinedTraces);
            }).map(combined -> {
                for (var tv : combined.values) {
                    if (tv.value() instanceof ErrorValue) {
                        return new TracedValue(tv.value(), combined.traces);
                    }
                }

                val entryValues = new Value[totalEntries];

                for (int i = 0; i < valueIndices.length; i++) {
                    entryValues[valueIndices[i]] = values[i];
                }

                // Pure operators evaluated without context - will be fixed when needed
                for (int i = 0; i < pureIndices.length; i++) {
                    entryValues[pureIndices[i]] = Value.UNDEFINED;
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
                return new TracedValue(builder.build(), combined.traces);
            });
        }

        private record CombinedStreams(TracedValue[] values, List<io.sapl.api.pdp.internal.AttributeRecord> traces) {}
    }

}
