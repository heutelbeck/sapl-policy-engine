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
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiles object literal expressions using the PRECOMPILED pattern.
 * Entries where the value evaluates to UndefinedValue are dropped from the
 * result.
 */
@UtilityClass
public class ObjectCompiler {

    private static final String ERROR_PURE_OBJECT_RECEIVED_STREAM_OPERATOR = "PureObject cannot contain StreamOperator. Indicates an implementation bug.";

    public static CompiledExpression compile(ObjectExpression expr, CompilationContext ctx) {
        val entries = expr.entries();

        if (entries.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }

        val     keys      = new String[entries.size()];
        val     compiled  = new ArrayList<CompiledExpression>(entries.size());
        boolean allValues = true;
        boolean hasStream = false;
        for (int i = 0; i < entries.size(); i++) {
            val entry = entries.get(i);
            keys[i] = entry.key();
            val result = ExpressionCompiler.compile(entry.value(), ctx);
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
            return foldEntries(keys, compiled);
        }
        if (hasStream) {
            return new StreamObject(keys, compiled);
        }
        return new PureObject(keys, compiled, expr.location());
    }

    private static ObjectValue foldEntries(String[] keys, List<CompiledExpression> compiled) {
        val builder = ObjectValue.builder();
        for (int i = 0; i < compiled.size(); i++) {
            val v = (Value) compiled.get(i);
            if (!(v instanceof UndefinedValue)) {
                builder.put(keys[i], v);
            }
        }
        return builder.build();
    }

    /**
     * Object with all pure entries (values and pure operators, no streams).
     * Constructed only when the compile-time scan reports zero stream entries;
     * the {@link StreamOperator} branch in element dispatch is therefore
     * unreachable and folds to an {@link ErrorValue} defensively.
     */
    record PureObject(String[] keys, List<CompiledExpression> entries, SourceLocation location)
            implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(PureObject.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val builder = ObjectValue.builder();
            for (int i = 0; i < entries.size(); i++) {
                val value = switch (entries.get(i)) {
                case Value v                -> v;
                case PureOperator p         -> p.evaluate(ctx);
                case StreamOperator ignored -> Value.error(ERROR_PURE_OBJECT_RECEIVED_STREAM_OPERATOR);
                };
                if (value instanceof ErrorValue) {
                    return value;
                }
                if (!(value instanceof UndefinedValue)) {
                    builder.put(keys[i], value);
                }
            }
            return builder.build();
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (val entry : entries) {
                if (entry instanceof PureOperator p && p.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isRelativeExpression() {
            for (val entry : entries) {
                if (entry instanceof PureOperator p && p.isRelativeExpression()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long semanticHash() {
            long hash = KIND;
            for (int i = 0; i < entries.size(); i++) {
                val entry     = entries.get(i);
                val entryHash = switch (entry) {
                              case Value v                -> SemanticHashing.valueHash(v);
                              case PureOperator p         -> p.semanticHash();
                              case StreamOperator ignored -> (long) entry.hashCode();
                              };
                hash = SemanticHashing.ordered(hash, SemanticHashing.textHash(keys[i]), entryHash);
            }
            return SemanticHashing.ordered(hash, Objects.hashCode(location));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PureObject that)) {
                return false;
            }
            return Arrays.equals(keys, that.keys) && Objects.equals(entries, that.entries)
                    && Objects.equals(location, that.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(keys), entries, location);
        }

        @Override
        public String toString() {
            return "PureObject[keys=" + Arrays.toString(keys) + ", entries=" + entries + ", location=" + location + "]";
        }
    }

    /**
     * Object with at least one stream entry. Walks every compiled entry via
     * {@link StreamOperator#evalChild}, accumulating subscriptions even past
     * any encountered {@link ErrorValue}. Holds the first error and returns
     * it after the full walk completes. Drops entries whose value is
     * {@link UndefinedValue} per object literal semantics. {@code null} from
     * a child sets the incomplete flag; on a clean walk with no error,
     * returns the assembled object. Precedence at the end:
     * error &gt; null &gt; built object.
     */
    record StreamObject(String[] keys, List<CompiledExpression> entries) implements StreamOperator {

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val     deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(entries.size());
            boolean seenNull   = false;
            Value   firstError = null;
            val     builder    = ObjectValue.builder();
            for (int i = 0; i < entries.size(); i++) {
                val v = evalChild(entries.get(i), ctx, deps);
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
                    builder.put(keys[i], v);
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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StreamObject(var thatKeys, var thatEntries))) {
                return false;
            }
            return Arrays.equals(keys, thatKeys) && Objects.equals(entries, thatEntries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(keys), entries);
        }

        @Override
        public String toString() {
            return "StreamObject[keys=" + Arrays.toString(keys) + ", entries=" + entries + "]";
        }
    }

}
