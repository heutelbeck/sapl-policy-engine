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
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.Expression;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiles array literals and preserves element order in every stratum.
 * Undefined elements are omitted. A streaming array visits every element to
 * collect its dependencies, including when another element is still pending.
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

        val     compiled  = CompilationChildren.map(elements, element -> ExpressionCompiler.compile(element, ctx));
        boolean allValues = true;
        boolean hasStream = false;
        for (val result : compiled) {
            if (result instanceof ErrorValue) {
                return result;
            }
            if (result instanceof StreamOperator) {
                hasStream = true;
                allValues = false;
            } else if (!(result instanceof Value)) {
                allValues = false;
            }
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

    /**
     * Array with all pure elements (values and pure operators, no streams).
     * Constructed after compilation establishes that no element reads an attribute.
     * The {@link StreamOperator} branch in element dispatch is therefore
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
                hash = SemanticHashing.ordered(hash, SemanticHashing.expressionHash(element));
            }
            return hash;
        }
    }

    /**
     * Evaluates every compiled element via {@link StreamOperator#evalChild},
     * accumulating subscriptions even past any encountered
     * {@link ErrorValue}. Holds the first error and returns it after the
     * full walk completes. Drops {@link UndefinedValue} elements per array
     * literal semantics. {@code null} from a child sets the incomplete
     * flag. On a clean walk with no error, returns the assembled array.
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
