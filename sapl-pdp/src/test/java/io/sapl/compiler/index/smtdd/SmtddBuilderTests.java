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
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.EqualityBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
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

@DisplayName("SmtddBuilder - diagram construction")
class SmtddBuilderTests {

    private static final boolean PRINT_RESULTS = false;

    @Nested
    @DisplayName("equality branch structure")
    class EqualityBranchTests {

        @Test
        @DisplayName("two EQ predicates on same operand produce EqualityBranch root")
        void whenTwoEqualitiesThenEqualityBranchRoot() {
            val operand     = stubOperand(100L);
            val formula0    = new And(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(configurablePredicate(1L)));
            val formula1    = new And(new Atom(eqPredicate(operand, Value.of("b"))),
                    new Atom(configurablePredicate(2L)));
            val expressions = List.<BooleanExpression>of(formula0, formula1);

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Two EQ with binary predicates ===\n" + root.toTree());
            }

            assertThat(root).isInstanceOf(EqualityBranch.class)
                    .satisfies(n -> assertThat(((EqualityBranch) n).branches()).hasSize(2));
        }

        @Test
        @DisplayName("pure EQ predicates produce EqualityBranch with terminal leaves")
        void whenPureEqualitiesThenTerminalLeaves() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Pure EQ ===\n" + root.toTree());
            }

            assertThat(root).isInstanceOf(EqualityBranch.class);
        }

        @Test
        @DisplayName("multiple equality groups produce nested EqualityBranch levels")
        void whenMultipleGroupsThenNestedBranches() {
            val operandA    = stubOperand(100L);
            val operandB    = stubOperand(200L);
            val expressions = List.<BooleanExpression>of(
                    new And(new Atom(eqPredicate(operandA, Value.of("a"))),
                            new Atom(eqPredicate(operandB, Value.of("x")))),
                    new And(new Atom(eqPredicate(operandA, Value.of("b"))),
                            new Atom(eqPredicate(operandB, Value.of("y")))));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Two equality groups ===\n" + root.toTree());
            }

            assertThat(root).isInstanceOf(EqualityBranch.class);
        }
    }

    @Nested
    @DisplayName("binary subtree fallback")
    class BinarySubtreeTests {

        @Test
        @DisplayName("single formula without equality group produces binary BDD")
        void whenSingleFormulaThenBinaryBdd() {
            val expressions = List.<BooleanExpression>of(new Atom(configurablePredicate(1L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Single binary formula ===\n" + root.toTree());
            }

            assertThat(root).isInstanceOf(BinaryDecision.class);
        }

        @Test
        @DisplayName("formulas without groupable predicates merge as binary BDD")
        void whenNoEqualityPredicatesThenBinaryMerge() {
            val expressions = List.<BooleanExpression>of(new Atom(configurablePredicate(1L)),
                    new Atom(configurablePredicate(2L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Binary only ===\n" + root.toTree());
            }

            assertThat(root).isNotInstanceOf(EqualityBranch.class);
        }
    }

    @Nested
    @DisplayName("NE (!=) compaction")
    class NeCompactionTests {

        @Test
        @DisplayName("NE formula routed to all branches except excluded constant")
        void whenNePredicateThenExcludedFromOneBranch() {
            val operand = stubOperand(100L);
            // f0: == "a", f1: != "a" (should appear in "b" branch but not "a")
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(nePredicate(operand, Value.of("a"))), new Atom(eqPredicate(operand, Value.of("b"))));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== NE compaction ===\n" + root.toTree());
            }

            assertThat(root).isInstanceOf(EqualityBranch.class);
        }
    }

    @Nested
    @DisplayName("boolean expression variants")
    class BooleanExpressionTests {

        @Test
        @DisplayName("NOT expression produces negated binary BDD")
        void whenNotExpressionThenNegatedBdd() {
            val expressions = List.<BooleanExpression>of(new Not(new Atom(configurablePredicate(1L))),
                    new Atom(configurablePredicate(2L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== NOT expression ===\n" + root.toTree());
            }

            assertThat(root).isNotInstanceOf(Terminal.class);
        }

        @Test
        @DisplayName("OR expression merges branches")
        void whenOrExpressionThenMerged() {
            val expressions = List.<BooleanExpression>of(
                    new Or(new Atom(configurablePredicate(1L)), new Atom(configurablePredicate(2L))),
                    new Atom(configurablePredicate(3L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== OR expression ===\n" + root.toTree());
            }

            assertThat(root).isNotInstanceOf(Terminal.class);
        }

        @Test
        @DisplayName("constant TRUE formula produces matched terminal")
        void whenConstantTrueThenMatched() {
            val expressions = List.<BooleanExpression>of(new Constant(true));

            val root = buildSmtdd(expressions);
            assertThat(root).isInstanceOf(Terminal.class);
            assertThat(((Terminal) root).matched().get(0)).isTrue();
        }

        @Test
        @DisplayName("De Morgan: NOT(a AND b) builds correctly")
        void whenNotAndThenDeMorgan() {
            val expressions = List.<BooleanExpression>of(
                    new Not(new And(new Atom(configurablePredicate(1L)), new Atom(configurablePredicate(2L)))),
                    new Atom(configurablePredicate(3L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== NOT(a AND b) ===\n" + root.toTree());
            }

            assertThat(root).isNotInstanceOf(Terminal.class);
        }

        @Test
        @DisplayName("De Morgan: NOT(a OR b) builds correctly")
        void whenNotOrThenDeMorgan() {
            val expressions = List.<BooleanExpression>of(
                    new Not(new Or(new Atom(configurablePredicate(1L)), new Atom(configurablePredicate(2L)))),
                    new Atom(configurablePredicate(3L)));

            val root = buildSmtdd(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== NOT(a OR b) ===\n" + root.toTree());
            }

            assertThat(root).isNotInstanceOf(Terminal.class);
        }
    }

    private static SmtddNode buildSmtdd(List<BooleanExpression> expressions) {
        val predicatesPerFormula = extractPredicates(expressions);
        val analysis             = SemanticVariableOrder.analyze(predicatesPerFormula);
        return SmtddBuilder.build(analysis, expressions, predicatesPerFormula, 0);
    }

}
