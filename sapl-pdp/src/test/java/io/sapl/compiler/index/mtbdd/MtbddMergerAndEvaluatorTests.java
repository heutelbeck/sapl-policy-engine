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
package io.sapl.compiler.index.mtbdd;

import java.util.List;
import java.util.stream.Stream;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.compiler.index.IndexTestFixtures.PREDICATE_RESULTS;
import static io.sapl.compiler.index.IndexTestFixtures.atom;
import static io.sapl.compiler.index.IndexTestFixtures.configurablePredicate;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("MTBDD merge and evaluate")
class MtbddMergerAndEvaluatorTests {

    private static final boolean PRINT_RESULTS = false;

    @AfterEach
    void clearPredicateResults() {
        PREDICATE_RESULTS.clear();
    }

    @Nested
    @DisplayName("single formula evaluate")
    class SingleFormulaEvaluateTests {

        @MethodSource
        @DisplayName("atom p1 evaluates correctly")
        @ParameterizedTest(name = "p1={0} -> {1}")
        void whenAtomThenCorrectResult(Value predicateResult, Value expected) {
            val p1    = configurablePredicate(1L);
            val table = new UniqueTable();
            val order = VariableOrder.fromExpressions(List.of(new Atom(p1)));
            val bdd   = MtbddBuilder.buildFormula(table, order, new Atom(p1), 0);

            PREDICATE_RESULTS.put(1L, predicateResult);
            val result = MtbddEvaluator.evaluateSingle(bdd, order, evaluationContext());
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenAtomThenCorrectResult() {
            return Stream.of(arguments(Value.TRUE, Value.TRUE), arguments(Value.FALSE, Value.FALSE),
                    arguments(new ErrorValue("broken"), new ErrorValue("broken")));
        }

        @MethodSource
        @DisplayName("p1 AND p2 evaluates correctly")
        @ParameterizedTest(name = "p1={0}, p2={1} -> {2}")
        void whenAndThenCorrectResult(Value p1Result, Value p2Result, Value expected) {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val expr  = new And(new Atom(p1), new Atom(p2));
            val table = new UniqueTable();
            val order = VariableOrder.fromExpressions(List.of(expr));
            val bdd   = MtbddBuilder.buildFormula(table, order, expr, 0);

            PREDICATE_RESULTS.put(1L, p1Result);
            PREDICATE_RESULTS.put(2L, p2Result);
            val result = MtbddEvaluator.evaluateSingle(bdd, order, evaluationContext());
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenAndThenCorrectResult() {
            val error     = new ErrorValue("broken");
            val evalError = new ErrorValue("broken");
            return Stream.of(arguments(Value.TRUE, Value.TRUE, Value.TRUE),
                    arguments(Value.TRUE, Value.FALSE, Value.FALSE), arguments(Value.FALSE, Value.TRUE, Value.FALSE),
                    arguments(Value.FALSE, Value.FALSE, Value.FALSE), arguments(error, Value.TRUE, evalError),
                    arguments(error, Value.FALSE, evalError), arguments(Value.TRUE, error, evalError),
                    arguments(error, error, evalError));
        }

        @MethodSource
        @DisplayName("p1 OR p2 evaluates correctly")
        @ParameterizedTest(name = "p1={0}, p2={1} -> {2}")
        void whenOrThenCorrectResult(Value p1Result, Value p2Result, Value expected) {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val expr  = new Or(new Atom(p1), new Atom(p2));
            val table = new UniqueTable();
            val order = VariableOrder.fromExpressions(List.of(expr));
            val bdd   = MtbddBuilder.buildFormula(table, order, expr, 0);

            PREDICATE_RESULTS.put(1L, p1Result);
            PREDICATE_RESULTS.put(2L, p2Result);
            val result = MtbddEvaluator.evaluateSingle(bdd, order, evaluationContext());
            assertThat(result).isEqualTo(expected);
        }

        static Stream<Arguments> whenOrThenCorrectResult() {
            val error     = new ErrorValue("broken");
            val evalError = new ErrorValue("broken");
            return Stream.of(arguments(Value.TRUE, Value.TRUE, Value.TRUE),
                    arguments(Value.TRUE, Value.FALSE, Value.TRUE), arguments(Value.FALSE, Value.TRUE, Value.TRUE),
                    arguments(Value.FALSE, Value.FALSE, Value.FALSE), arguments(error, Value.TRUE, evalError),
                    arguments(error, Value.FALSE, evalError), arguments(Value.TRUE, error, Value.TRUE),
                    arguments(Value.FALSE, error, evalError));
        }
    }

    @Nested
    @DisplayName("merge two formulas")
    class MergeTwoFormulasTests {

        @Test
        @DisplayName("visual: merge p1 and p2 into combined diagram")
        void whenTwoAtomsThenMergedDiagram() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val table = new UniqueTable();
            val exprs = List.<BooleanExpression>of(new Atom(p1), new Atom(p2));
            val order = VariableOrder.fromExpressions(exprs);

            val bdd0 = MtbddBuilder.buildFormula(table, order, exprs.get(0), 0);
            val bdd1 = MtbddBuilder.buildFormula(table, order, exprs.get(1), 1);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Before merge ===");
                System.out.println("Formula 0: p1\n" + bdd0.toTree(order));
                System.out.println("Formula 1: p2\n" + bdd1.toTree(order));
            }

            val merged = MtbddMerger.merge(table, bdd0, bdd1);

            if (PRINT_RESULTS) {
                System.out.println("=== After merge ===\n" + merged.toTree(order));
                System.out.println("Unique table size: " + table.size());
            }

            // Evaluate: p1=T, p2=T -> both matched
            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            val result = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matched().get(0)).isTrue();
                assertThat(r.matched().get(1)).isTrue();
                assertThat(r.errored().isEmpty()).isTrue();
            });
        }

        @Test
        @DisplayName("merge p1 and p2: only matching formula returned")
        void whenOneTrueThenOnlyThatFormulaMatched() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val table = new UniqueTable();
            val exprs = List.<BooleanExpression>of(new Atom(p1), new Atom(p2));
            val order = VariableOrder.fromExpressions(exprs);

            val bdd0   = MtbddBuilder.buildFormula(table, order, exprs.get(0), 0);
            val bdd1   = MtbddBuilder.buildFormula(table, order, exprs.get(1), 1);
            val merged = MtbddMerger.merge(table, bdd0, bdd1);

            // p1=T, p2=F -> only formula 0 matched
            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            val result = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matched().get(0)).isTrue();
                assertThat(r.matched().get(1)).isFalse();
            });
        }

        @Test
        @DisplayName("merge p1 and p2: error kills only the affected formula")
        void whenOneErrorsThenOnlyThatFormulaErrored() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val table = new UniqueTable();
            val exprs = List.<BooleanExpression>of(new Atom(p1), new Atom(p2));
            val order = VariableOrder.fromExpressions(exprs);

            val bdd0   = MtbddBuilder.buildFormula(table, order, exprs.get(0), 0);
            val bdd1   = MtbddBuilder.buildFormula(table, order, exprs.get(1), 1);
            val merged = MtbddMerger.merge(table, bdd0, bdd1);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Merged p1, p2 ===\n" + merged.toTree(order));
            }

            // p1=error, p2=T -> formula 0 errored, formula 1 matched
            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            val result = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result).satisfies(r -> {
                assertThat(r.matched().get(0)).isFalse();
                assertThat(r.errored().get(0)).isTrue();
                assertThat(r.matched().get(1)).isTrue();
                assertThat(r.errored().get(1)).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("merge with shared predicates")
    class SharedPredicateTests {

        @Test
        @DisplayName("visual: f0=p1 AND p2, f1=p1 OR p3 - shared p1")
        void whenSharedPredicateThenCompactDiagram() {
            val p1    = configurablePredicate(1L);
            val p2    = configurablePredicate(2L);
            val p3    = configurablePredicate(3L);
            val expr0 = new And(new Atom(p1), new Atom(p2));
            val expr1 = new Or(new Atom(p1), new Atom(p3));
            val table = new UniqueTable();
            val order = VariableOrder.fromExpressions(List.of(expr0, expr1));

            val bdd0 = MtbddBuilder.buildFormula(table, order, expr0, 0);
            val bdd1 = MtbddBuilder.buildFormula(table, order, expr1, 1);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Before merge (shared predicate) ===");
                System.out.println("Formula 0: p1 AND p2\n" + bdd0.toTree(order));
                System.out.println("Formula 1: p1 OR p3\n" + bdd1.toTree(order));
            }

            val merged = MtbddMerger.merge(table, bdd0, bdd1);

            if (PRINT_RESULTS) {
                System.out.println("=== After merge ===\n" + merged.toTree(order));
                System.out.println("Unique table size: " + table.size());
            }

            // p1=T, p2=T, p3=F -> both matched (f0 via p1&p2, f1 via p1)
            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            PREDICATE_RESULTS.put(3L, Value.FALSE);
            val bothTrue = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(bothTrue.matched()).satisfies(m -> {
                assertThat(m.get(0)).isTrue();
                assertThat(m.get(1)).isTrue();
            });

            // p1=F, p2=T, p3=T -> f0 not matched (p1 false), f1 matched (via p3)
            PREDICATE_RESULTS.put(1L, Value.FALSE);
            PREDICATE_RESULTS.put(3L, Value.TRUE);
            val onlyF1 = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(onlyF1.matched()).satisfies(m -> {
                assertThat(m.get(0)).isFalse();
                assertThat(m.get(1)).isTrue();
            });

            // p1=error, p2=T, p3=T -> f0 errored (uses p1), f1 errored (uses p1)
            PREDICATE_RESULTS.put(1L, new ErrorValue("broken"));
            val bothErrored = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(bothErrored.errored()).satisfies(e -> {
                assertThat(e.get(0)).isTrue();
                assertThat(e.get(1)).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("merge multiple formulas incrementally")
    class IncrementalMergeTests {

        @Test
        @DisplayName("visual: four formulas merged pairwise")
        void whenFourFormulasThenCorrectMerge() {
            val p1 = configurablePredicate(1L);
            val p2 = configurablePredicate(2L);
            val p3 = configurablePredicate(3L);
            val p4 = configurablePredicate(4L);

            val expr0 = new Atom(p1);
            val expr1 = new Atom(p2);
            val expr2 = new And(new Atom(p1), new Atom(p3));
            val expr3 = new Or(new Atom(p2), new Not(new Atom(p4)));

            val exprs = List.<BooleanExpression>of(expr0, expr1, expr2, expr3);
            val table = new UniqueTable();
            val order = VariableOrder.fromExpressions(exprs);

            // Build and merge incrementally
            var merged = MtbddBuilder.buildFormula(table, order, expr0, 0);
            for (var i = 1; i < exprs.size(); i++) {
                val formulaBdd = MtbddBuilder.buildFormula(table, order, exprs.get(i), i);
                merged = MtbddMerger.merge(table, merged, formulaBdd);
            }

            if (PRINT_RESULTS) {
                System.out.println("\n=== Four formulas: f0=p1, f1=p2, f2=p1 AND p3, f3=p2 OR NOT p4 ===");
                System.out.println(merged.toTree(order));
                System.out.println("Unique table size: " + table.size());
            }

            // p1=T, p2=F, p3=T, p4=T -> f0 matched, f1 not, f2 matched, f3 not (p2=F AND
            // p4=T so NOT p4=F)
            PREDICATE_RESULTS.put(1L, Value.TRUE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            PREDICATE_RESULTS.put(3L, Value.TRUE);
            PREDICATE_RESULTS.put(4L, Value.TRUE);
            val result1 = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result1.matched()).satisfies(m -> {
                assertThat(m.get(0)).isTrue();
                assertThat(m.get(1)).isFalse();
                assertThat(m.get(2)).isTrue();
                assertThat(m.get(3)).isFalse();
            });

            // p1=T, p2=T, p3=F, p4=F -> f0 matched, f1 matched, f2 not (p3=F), f3 matched
            // (p2=T)
            PREDICATE_RESULTS.put(3L, Value.FALSE);
            PREDICATE_RESULTS.put(2L, Value.TRUE);
            PREDICATE_RESULTS.put(4L, Value.FALSE);
            val result2 = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result2.matched()).satisfies(m -> {
                assertThat(m.get(0)).isTrue();
                assertThat(m.get(1)).isTrue();
                assertThat(m.get(2)).isFalse();
                assertThat(m.get(3)).isTrue();
            });

            // p1=F, p2=F, p3=F, p4=F -> f0 not, f1 not, f2 not, f3 matched (NOT p4 = T)
            PREDICATE_RESULTS.put(1L, Value.FALSE);
            PREDICATE_RESULTS.put(2L, Value.FALSE);
            val result3 = MtbddEvaluator.evaluate(merged, order, evaluationContext());
            assertThat(result3.matched()).satisfies(m -> {
                assertThat(m.get(0)).isFalse();
                assertThat(m.get(1)).isFalse();
                assertThat(m.get(2)).isFalse();
                assertThat(m.get(3)).isTrue();
            });
        }
    }

}
