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

public non-sealed interface PureOperator extends CompiledExpression {
    Value evaluate(EvaluationContext ctx);

    SourceLocation location();

    boolean isDependingOnSubscription();

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
