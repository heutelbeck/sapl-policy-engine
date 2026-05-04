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

import java.util.Arrays;
import java.util.Objects;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.Expression;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiles array literal expressions using the PRECOMPILED pattern.
 * Elements that evaluate to UndefinedValue are dropped from the result.
 */
@UtilityClass
public class ArrayCompiler {

    private static final String ERROR_PURE_ARRAY_RECEIVED_STREAM_OPERATOR = "PureArray cannot contain StreamOperator. Indicates an implementation bug.";

    public static CompiledExpression compile(ArrayExpression expr, CompilationContext ctx) {
        return compileExpressionsToArray(expr.elements(), expr.location(), ctx);
    }

    public static CompiledExpression compileExpressionsToArray(List<Expression> elements, SourceLocation location,
            CompilationContext ctx) {
        if (elements.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        val     compiled  = new ArrayList<CompiledExpression>(elements.size());
        boolean allValues = true;
        boolean hasStream = false;
        for (val element : elements) {
            val result = ExpressionCompiler.compile(element, ctx);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (result instanceof StreamOperator) {
                hasStream = true;
                allValues = false;
            } else if (!(result instanceof Value)) {
                allValues = false;
            }
            compiled.add(result);
        }

        if (allValues) {
            return foldValues(compiled);
        }
        if (hasStream) {
            return new StreamArray(compiled);
        }
        return new PureArray(compiled, location);
    }

    private static Value foldValues(List<CompiledExpression> compiled) {
        val builder = ArrayValue.builder();
        for (val e : compiled) {
            val v = (Value) e;
            if (v instanceof ErrorValue) {
                return v;
            }
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

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), Arrays.hashCode(streamIndices), Arrays.hashCode(streams),
                    totalCount);
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof CategorizedExpressions(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreams, var oTotal)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && Arrays.equals(streamIndices, oStreamIdx) && Arrays.equals(streams, oStreams)
                            && totalCount == oTotal);
        }
    }

    /**
     * Array with all pure elements (values and pure operators, no streams).
     * Constructed only when the categorizer reports zero stream elements;
     * the {@link StreamOperator} branch in element dispatch is therefore
     * unreachable and folds to an {@link ErrorValue} defensively.
     */
    record PureArray(List<CompiledExpression> elements, SourceLocation location) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(PureArray.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val builder = ArrayValue.builder();
            for (val element : elements) {
                val value = switch (element) {
                case Value v                -> v;
                case PureOperator p         -> p.evaluate(ctx);
                case StreamOperator ignored -> Value.error(ERROR_PURE_ARRAY_RECEIVED_STREAM_OPERATOR);
                };
                if (value instanceof ErrorValue) {
                    return value;
                }
                if (!(value instanceof UndefinedValue)) {
                    builder.add(value);
                }
            }
            return builder.build();
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (val element : elements) {
                if (element instanceof PureOperator p && p.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isRelativeExpression() {
            for (val element : elements) {
                if (element instanceof PureOperator p && p.isRelativeExpression()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long semanticHash() {
            long hash = KIND;
            for (val element : elements) {
                val elementHash = switch (element) {
                case Value v                -> (long) v.hashCode();
                case PureOperator p         -> p.semanticHash();
                case StreamOperator ignored -> (long) element.hashCode();
                };
                hash = SemanticHashing.ordered(hash, elementHash);
            }
            return SemanticHashing.ordered(hash, Objects.hashCode(location));
        }
    }

    /**
     * Evaluates every compiled element via {@link StreamOperator#evalChild},
     * accumulating subscriptions even past any encountered
     * {@link ErrorValue}. Holds the first error and returns it after the
     * full walk completes. Drops {@link UndefinedValue} elements per array
     * literal semantics. {@code null} from a child sets the incomplete
     * flag; on a clean walk with no error, returns the assembled array.
     * Precedence at the end: error &gt; null &gt; built array.
     */
    record StreamArray(List<CompiledExpression> compiledElements) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val     deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(compiledElements.size());
            boolean seenNull   = false;
            Value   firstError = null;
            val     builder    = ArrayValue.builder();
            for (val element : compiledElements) {
                val v = evalChild(element, ctx, deps);
                if (v == null) {
                    seenNull = true;
                    continue;
                }
                if (v instanceof ErrorValue) {
                    if (firstError == null) {
                        firstError = v;
                    }
                    continue;
                }
                if (!(v instanceof UndefinedValue)) {
                    builder.add(v);
                }
            }
            if (firstError != null) {
                return new ExpressionResult(firstError, deps);
            }
            if (seenNull) {
                return new ExpressionResult(null, deps);
            }
            return new ExpressionResult(builder.build(), deps);
        }
    }
}
