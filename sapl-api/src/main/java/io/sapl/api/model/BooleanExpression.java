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

import java.util.List;

/**
 * A boolean expression tree over index predicates. This is the intermediate
 * representation extracted from compiled {@code PureOperator} trees before
 * normalization to DNF.
 * <p>
 * The tree preserves the boolean structure (AND/OR/NOT) while treating
 * non-boolean sub-expressions as opaque atomic predicates.
 */
public sealed interface BooleanExpression {

    /**
     * Conjunction: all operands must be true.
     *
     * @param operands the conjuncts
     */
    record And(List<BooleanExpression> operands) implements BooleanExpression {
        public And(List<BooleanExpression> operands) {
            this.operands = List.copyOf(operands);
        }

        public And(BooleanExpression... operands) {
            this(List.of(operands));
        }
    }

    /**
     * Disjunction: at least one operand must be true.
     *
     * @param operands the disjuncts
     */
    record Or(List<BooleanExpression> operands) implements BooleanExpression {
        public Or(List<BooleanExpression> operands) {
            this.operands = List.copyOf(operands);
        }

        public Or(BooleanExpression... operands) {
            this(List.of(operands));
        }
    }

    /**
     * Negation: the operand must be false.
     *
     * @param operand the expression to negate
     */
    record Not(BooleanExpression operand) implements BooleanExpression {}

    /**
     * An atomic predicate: a leaf in the boolean tree.
     *
     * @param predicate the index predicate
     */
    record Atom(IndexPredicate predicate) implements BooleanExpression {}

    /**
     * A boolean constant.
     *
     * @param value true or false
     */
    record Constant(boolean value) implements BooleanExpression {}

}
