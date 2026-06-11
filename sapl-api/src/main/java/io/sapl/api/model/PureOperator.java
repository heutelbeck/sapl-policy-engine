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
package io.sapl.api.model;

import lombok.val;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InaccessibleObjectException;
import java.util.List;

public non-sealed interface PureOperator extends CompiledExpression {
    Value evaluate(EvaluationContext ctx);

    SourceLocation location();

    boolean isDependingOnSubscription();

    /**
     * Returns true if this operator or any of its children depends on a
     * relative value context ({@code @} or {@code @location}). Relative
     * expressions are not subscription-dependent but cannot be folded at
     * compile time outside of their filter/subtemplate context.
     *
     * @return true if this operator uses relative references
     */
    default boolean isRelativeExpression() {
        return false;
    }

    /**
     * Returns a hash that identifies the semantic content of this operator,
     * ignoring source location and other non-semantic fields. Two operators
     * representing the same computation produce the same hash, even if they
     * were compiled from different source locations.
     * <p>
     * Used by the canonical policy index to identify equivalent predicates
     * across policies.
     *
     * @return semantic hash of this operator
     */
    long semanticHash();

    /**
     * Tests whether this operator is semantically equal to another, ignoring
     * source location and other non-semantic fields. This verifies the
     * identity that {@link #semanticHash()} only approximates, so that a hash
     * collision between two structurally different operators cannot merge them
     * into one predicate in the policy index and change which documents apply.
     * <p>
     * Record implementations are compared component by component: child
     * operators recursively, constant {@link Value}s by value equality,
     * identifiers and operator kinds by equality. Source locations and
     * compiled lambdas (which are derived from the semantic fields) are
     * ignored. Implementations that are not records fall back to hash
     * identity, so they must keep their {@link #semanticHash()} unique.
     *
     * @param other the operator to compare with
     * @return true if both operators denote the same computation
     */
    default boolean semanticEquals(@Nullable PureOperator other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        val components = getClass().getRecordComponents();
        if (components == null) {
            // Not a record: no structural view, trust the semantic hash.
            return semanticHash() == other.semanticHash();
        }
        try {
            for (val component : components) {
                val accessor = component.getAccessor();
                accessor.setAccessible(true);
                if (!semanticComponentEquals(accessor.invoke(this), accessor.invoke(other))) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | InaccessibleObjectException | SecurityException ignored) {
            // Cannot read components: keep the operators distinct (safe).
            return false;
        }
    }

    private static boolean semanticComponentEquals(@Nullable Object mine, @Nullable Object yours) {
        if (mine == yours) {
            return true;
        }
        if (mine == null || yours == null) {
            return false;
        }
        if (mine instanceof SourceLocation) {
            return true; // Position is not semantic.
        }
        if (mine.getClass().isSynthetic()) {
            return true; // Compiled lambda, derived from the semantic fields.
        }
        if (mine instanceof PureOperator minePure && yours instanceof PureOperator yoursPure) {
            return minePure.semanticEquals(yoursPure);
        }
        if (mine instanceof Value || yours instanceof Value) {
            return mine.equals(yours);
        }
        if (mine instanceof List<?> mineList && yours instanceof List<?> yoursList) {
            if (mineList.size() != yoursList.size()) {
                return false;
            }
            for (var i = 0; i < mineList.size(); i++) {
                if (!semanticComponentEquals(mineList.get(i), yoursList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (mine instanceof CharSequence || mine instanceof Number || mine instanceof Boolean
                || mine instanceof Character || mine instanceof Enum<?>) {
            return mine.equals(yours);
        }
        // Unknown non-semantic type: keep the operators distinct (safe).
        return false;
    }

    /**
     * Returns the boolean expression structure of this operator for the
     * policy index. Boolean operators (AND, OR, NOT) override this to expose
     * their structure. All other operators inherit the default, which returns
     * an opaque atomic predicate.
     *
     * @return the boolean expression representation
     */
    default BooleanExpression booleanExpression() {
        return new BooleanExpression.Atom(new IndexPredicate(semanticHash(), this));
    }

}
