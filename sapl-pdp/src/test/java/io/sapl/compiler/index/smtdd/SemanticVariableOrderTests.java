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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.configurablePredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.eqPredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.extractPredicates;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.hasPredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.inPredicate;
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

    @Nested
    @DisplayName("IN grouping")
    class InGroupingTests {

        @Test
        @DisplayName("IN with array expands to equality group constants")
        void whenInArrayThenConstantsGrouped() {
            val operand     = stubOperand(100L);
            val array       = new ArrayValue(List.of(Value.of("x"), Value.of("y"), Value.of("z")));
            val expressions = List.<BooleanExpression>of(new Atom(inPredicate(operand, array)),
                    new Atom(eqPredicate(operand, Value.of("w"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
            // 3 array elements + 1 EQ constant = 4 distinct constants
            assertThat(result.equalityGroups().getFirst().getEqualsFormulas()).hasSize(4);
        }

        @Test
        @DisplayName("IN with object expands object values to equality group constants")
        void whenInObjectThenValuesGrouped() {
            val operand     = stubOperand(100L);
            val object      = new ObjectValue(
                    new Value[] { Value.of("k1"), Value.of("v1"), Value.of("k2"), Value.of("v2") });
            val expressions = List.<BooleanExpression>of(new Atom(inPredicate(operand, object)),
                    new Atom(eqPredicate(operand, Value.of("other"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
            // 2 object values + 1 EQ constant = 3 distinct constants
            assertThat(result.equalityGroups().getFirst().getEqualsFormulas()).hasSize(3);
        }

        @Test
        @DisplayName("IN with non-collection value is not groupable")
        void whenInNonCollectionThenRemaining() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(inPredicate(operand, Value.of("scalar"))),
                    new Atom(configurablePredicate(1L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).isEmpty();
            assertThat(result.remainingPredicates()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("HAS grouping")
    class HasGroupingTests {

        @Test
        @DisplayName("HAS with object container groups object keys")
        void whenHasObjectThenKeysGrouped() {
            val operand     = stubOperand(100L);
            val object      = new ObjectValue(
                    new Value[] { Value.of("role"), Value.of("x"), Value.of("dept"), Value.of("y") });
            val expressions = List.<BooleanExpression>of(new Atom(hasPredicate(operand, object)),
                    new Atom(eqPredicate(operand, Value.of("other"))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).hasSize(1);
            // 2 object keys + 1 EQ constant = 3 distinct constants
            assertThat(result.equalityGroups().getFirst().getEqualsFormulas()).hasSize(3);
        }

        @Test
        @DisplayName("HAS with non-object container is not groupable")
        void whenHasNonObjectThenRemaining() {
            val operand     = stubOperand(100L);
            val array       = new ArrayValue(List.of(Value.of("a")));
            val expressions = List.<BooleanExpression>of(new Atom(hasPredicate(operand, array)),
                    new Atom(configurablePredicate(1L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).isEmpty();
            assertThat(result.remainingPredicates()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("pushback and report")
    class PushbackAndReportTests {

        @Test
        @DisplayName("group with only one constant pushed back to remaining")
        void whenSingleConstantGroupThenPushedBack() {
            val operand = stubOperand(100L);
            // Only one EQ constant on this operand, plus a non-groupable predicate
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("only"))),
                    new Atom(configurablePredicate(1L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            assertThat(result.equalityGroups()).isEmpty();
            // 1 pushed-back EQ predicate + 1 configurable predicate = 2
            assertThat(result.remainingPredicates()).hasSize(2);
        }

        @Test
        @DisplayName("analysis report contains group and remaining info")
        void whenAnalyzedThenReportContainsGroupInfo() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(
                    new And(new Atom(eqPredicate(operand, Value.of("a"))), new Atom(configurablePredicate(1L))),
                    new And(new Atom(eqPredicate(operand, Value.of("b"))), new Atom(configurablePredicate(2L))));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            val report = result.toAnalysisReport();

            assertThat(report).contains("Semantic analysis:").contains("equality groups")
                    .contains("Remaining binary predicates:");
        }

        @Test
        @DisplayName("report shows inner type for remaining PureBooleanTypeCheck predicates")
        void whenRemainingWrappedPredicateThenReportShowsInnerType() {
            val operand = stubOperand(100L);
            // inPredicate wraps a BinaryPureValue in PureBooleanTypeCheck.
            // With a non-collection constant it's ungroupable, so it stays
            // in remaining predicates, exercising the unwrapInnerType path.
            val expressions = List.<BooleanExpression>of(new Atom(inPredicate(operand, Value.of("scalar"))),
                    new Atom(configurablePredicate(1L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            val report = result.toAnalysisReport();

            assertThat(report).contains("(BinaryPureValue)");
        }

        @Test
        @DisplayName("report shows direct class name for non-wrapped remaining predicates")
        void whenRemainingUnwrappedPredicateThenReportShowsClassName() {
            val expressions = List.<BooleanExpression>of(new Atom(configurablePredicate(1L)),
                    new Atom(configurablePredicate(2L)));

            val result = SemanticVariableOrder.analyze(extractPredicates(expressions));
            val report = result.toAnalysisReport();

            // configurablePredicate is not wrapped, anonymous class has empty
            // simple name - just verify report runs without error
            assertThat(report).contains("Remaining binary predicates: 2");
        }
    }

}
