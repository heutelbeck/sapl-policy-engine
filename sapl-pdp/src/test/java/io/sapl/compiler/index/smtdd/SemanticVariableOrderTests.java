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
package io.sapl.compiler.index.smtdd;

import java.util.List;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.configurablePredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.eqPredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.extractPredicates;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.nePredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.stubOperand;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SemanticVariableOrder")
class SemanticVariableOrderTests {

    @Nested
    @DisplayName("equality grouping")
    class EqualityGroupingTests {

        @Test
        @DisplayName("two EQ predicates on same operand produce one group")
        void whenTwoEqOnSameOperandThenOneGroup() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
            assertThat(result.equalityGroups().getFirst().getEqualsFormulas()).hasSize(2);
        }

        @Test
        @DisplayName("EQ predicates on different operands produce separate groups")
        void whenDifferentOperandsThenSeparateGroups() {
            val operandA    = stubOperand(100L);
            val operandB    = stubOperand(200L);
            val expressions = List.<BooleanExpression>of(
                    new And(new Atom(eqPredicate(operandA, Value.of("a"))),
                            new Atom(eqPredicate(operandB, Value.of("x")))),
                    new And(new Atom(eqPredicate(operandA, Value.of("b"))),
                            new Atom(eqPredicate(operandB, Value.of("y")))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(2);
        }

        @Test
        @DisplayName("single constant value not grouped (falls back to remaining)")
        void whenSingleConstantThenNoGroup() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(configurablePredicate(1L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).isEmpty();
            assertThat(result.remainingPredicates()).isNotEmpty();
        }

        @Test
        @DisplayName("non-equality predicates go to remaining")
        void whenNonEqualityThenRemaining() {
            val expressions = List.<BooleanExpression>of(new Atom(configurablePredicate(1L)),
                    new Atom(configurablePredicate(2L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).isEmpty();
            assertThat(result.remainingPredicates()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("NE (!=) handling")
    class NeHandlingTests {

        @Test
        @DisplayName("NE predicate detected and recorded in excludeFormulas")
        void whenNePredThenRecordedInExcludes() {
            val operand = stubOperand(100L);
            // EQ "a" + NE "b" on same operand -> 2 distinct constants -> forms group
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(nePredicate(operand, Value.of("b"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
            val group = result.equalityGroups().getFirst();
            assertThat(group.getEqualsFormulas()).hasSize(1);
            assertThat(group.getExcludeFormulas()).hasSize(1);
        }

        @Test
        @DisplayName("NE + EQ on same operand with different constants form a group")
        void whenNeAndEqDifferentConstantsThenGrouped() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(nePredicate(operand, Value.of("b"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
        }
    }

}
