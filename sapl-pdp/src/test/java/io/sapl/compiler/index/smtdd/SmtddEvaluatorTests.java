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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.PREDICATE_RESULTS;
import static io.sapl.compiler.index.IndexTestFixtures.configurablePredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.OPERATOR_RESULTS;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.eqPredicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.extractPredicates;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.stubOperand;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmtddEvaluator")
class SmtddEvaluatorTests {

    private static final boolean PRINT_RESULTS = false;

    @AfterEach
    void clearResults() {
        PREDICATE_RESULTS.clear();
        OPERATOR_RESULTS.clear();
    }

    @Nested
    @DisplayName("equality branch routing")
    class EqualityBranchRoutingTests {

        @Test
        @DisplayName("operand value routes to correct branch")
        void whenOperandMatchesThenCorrectFormulaMatched() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val evaluator = buildEvaluator(expressions);

            OPERATOR_RESULTS.put(100L, Value.of("a"));
            val matchA = evaluate(evaluator);
            assertThat(matchA.matched()).satisfies(m -> {
                assertThat(m.get(0)).isTrue();
                assertThat(m.get(1)).isFalse();
            });

            OPERATOR_RESULTS.put(100L, Value.of("b"));
            val matchB = evaluate(evaluator);
            assertThat(matchB.matched()).satisfies(m -> {
                assertThat(m.get(0)).isFalse();
                assertThat(m.get(1)).isTrue();
            });
        }

        @Test
        @DisplayName("unknown operand value routes to default (no match)")
        void whenUnknownValueThenNoMatch() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val evaluator = buildEvaluator(expressions);

            OPERATOR_RESULTS.put(100L, Value.of("unknown"));
            val result = evaluate(evaluator);
            assertThat(result.matched().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("operand error kills all affected formulas")
        void whenOperandErrorThenFormulasErrored() {
            val operand     = stubOperand(100L);
            val expressions = List.<BooleanExpression>of(new Atom(eqPredicate(operand, Value.of("a"))),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val evaluator = buildEvaluator(expressions);

            OPERATOR_RESULTS.put(100L, new ErrorValue("broken"));
            val result = evaluate(evaluator);
            assertThat(result).satisfies(r -> {
                assertThat(r.matched().isEmpty()).isTrue();
                assertThat(r.errored().get(0)).isTrue();
                assertThat(r.errored().get(1)).isTrue();
                assertThat(r.firstError()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("mixed equality + binary predicates")
    class MixedPredicateTests {

        @Test
        @DisplayName("EQ branch followed by binary predicate evaluation")
        void whenEqAndBinaryThenBothEvaluated() {
            val operand     = stubOperand(100L);
            val binaryPred  = configurablePredicate(1L);
            val expressions = List.<BooleanExpression>of(
                    new And(new Atom(eqPredicate(operand, Value.of("a"))), new Atom(binaryPred)),
                    new Atom(eqPredicate(operand, Value.of("b"))));

            val evaluator = buildEvaluator(expressions);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Mixed EQ + binary ===\n" + evaluator.root.toTree(evaluator.binaryOrder));
            }

            // EQ matches "a", binary true -> formula 0 matches
            OPERATOR_RESULTS.put(100L, Value.of("a"));
            PREDICATE_RESULTS.put(1L, Value.TRUE);
            assertThat(evaluate(evaluator).matched()).satisfies(m -> {
                assertThat(m.get(0)).isTrue();
                assertThat(m.get(1)).isFalse();
            });

            // EQ matches "a", binary false -> no match
            PREDICATE_RESULTS.put(1L, Value.FALSE);
            assertThat(evaluate(evaluator).matched().isEmpty()).isTrue();
        }
    }

    record EvaluatorContext(SmtddNode root, BinaryVariableOrder binaryOrder) {}

    private static EvaluatorContext buildEvaluator(List<BooleanExpression> expressions) {
        val predicatesPerFormula = extractPredicates(expressions);
        val analysis             = SemanticVariableOrder.analyze(predicatesPerFormula);
        val root                 = SmtddBuilder.build(analysis, expressions, 0);
        val binaryOrder          = new BinaryVariableOrder(analysis.remainingPredicates(),
                analysis.formulasPerRemainingPredicate());
        return new EvaluatorContext(root, binaryOrder);
    }

    private static SmtddEvaluator.EvaluationResult evaluate(EvaluatorContext evaluator) {
        return SmtddEvaluator.evaluate(evaluator.root, evaluator.binaryOrder, evaluationContext());
    }

}
