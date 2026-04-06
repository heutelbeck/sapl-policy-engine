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

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.atom;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MtbddBuilder - per-formula MTBDD construction")
class MtbddBuilderTests {

    @Nested
    @DisplayName("constants")
    class ConstantTests {

        @Test
        @DisplayName("constant true produces matched terminal")
        void whenTrueThenMatched() {
            val result = buildSingle(new Constant(true), 0);
            printFormula("true", result);
            assertThat(result).isInstanceOf(Terminal.class);
            assertThat(((Terminal) result).matched().get(0)).isTrue();
        }

        @Test
        @DisplayName("constant false produces empty terminal")
        void whenFalseThenEmpty() {
            val result = buildSingle(new Constant(false), 0);
            printFormula("false", result);
            assertThat(result).isSameAs(UniqueTable.EMPTY);
        }
    }

    @Nested
    @DisplayName("single atom")
    class AtomTests {

        @Test
        @DisplayName("atom p0 produces decision with matched/empty/errored leaves")
        void whenAtomThenDecisionNode() {
            val result = buildSingle(atom(1L), 0);
            printFormula("p1", result);

            assertThat(result).isInstanceOf(Decision.class).satisfies(n -> {
                val d = (Decision) n;
                assertThat(d.level()).isZero();
                assertThat(d.trueChild()).isInstanceOf(Terminal.class);
                assertThat(((Terminal) d.trueChild()).matched().get(0)).isTrue();
                assertThat(d.falseChild()).isSameAs(UniqueTable.EMPTY);
                assertThat(d.errorChild()).isInstanceOf(Terminal.class);
                assertThat(((Terminal) d.errorChild()).errored().get(0)).isTrue();
            });
        }

        @Test
        @DisplayName("negated atom NOT p0 swaps true/false children")
        void whenNegatedAtomThenSwapped() {
            val result = buildSingle(new Not(atom(1L)), 0);
            printFormula("NOT p1", result);

            assertThat(result).isInstanceOf(Decision.class).satisfies(n -> {
                val d = (Decision) n;
                assertThat(d.trueChild()).isSameAs(UniqueTable.EMPTY);
                assertThat(((Terminal) d.falseChild()).matched().get(0)).isTrue();
                assertThat(((Terminal) d.errorChild()).errored().get(0)).isTrue();
            });
        }

        @Test
        @DisplayName("double negation NOT NOT p0 equals p0")
        void whenDoubleNegationThenOriginal() {
            val direct  = buildSingle(atom(1L), 0);
            val doubled = buildSingle(new Not(new Not(atom(1L))), 0);
            printFormula("NOT NOT p1", doubled);
            assertThat(doubled).isEqualTo(direct);
        }
    }

    @Nested
    @DisplayName("OR")
    class OrTests {

        @Test
        @DisplayName("p1 OR p2: matched if either true")
        void whenOrThenMatchedOnEither() {
            val expr   = new Or(atom(1L), atom(2L));
            val result = buildSingle(expr, 0, atom(1L), atom(2L));
            printFormula("p1 OR p2", result);

            // p1? (level 0)
            // T: matched (p1 true -> formula satisfied)
            // F: p2? (need to check p2)
            // T: matched
            // F: empty
            // E: errored
            // E: p2? (p1 errored, check p2)
            // T: matched+errored
            // F: errored
            // E: errored
            assertThat(result).isInstanceOf(Decision.class);
            val root = (Decision) result;
            assertThat(root.level()).isZero();
            assertThat(((Terminal) root.trueChild()).matched().get(0)).isTrue();
            assertThat(root.falseChild()).isInstanceOf(Decision.class);
            assertThat(root.errorChild()).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("p1 OR true simplifies to matched")
        void whenOrWithTrueThenMatched() {
            val result = buildSingle(new Or(atom(1L), new Constant(true)), 0);
            printFormula("p1 OR true", result);
            assertThat(result).isInstanceOf(Terminal.class);
            assertThat(((Terminal) result).matched().get(0)).isTrue();
        }

        @Test
        @DisplayName("p1 OR false simplifies to p1")
        void whenOrWithFalseThenSameAsAtom() {
            val orResult   = buildSingle(new Or(atom(1L), new Constant(false)), 0);
            val atomResult = buildSingle(atom(1L), 0);
            printFormula("p1 OR false", orResult);
            assertThat(orResult).isEqualTo(atomResult);
        }
    }

    @Nested
    @DisplayName("AND")
    class AndTests {

        @Test
        @DisplayName("p1 AND p2: matched only if both true")
        void whenAndThenMatchedOnBoth() {
            val expr   = new And(atom(1L), atom(2L));
            val result = buildSingle(expr, 0, atom(1L), atom(2L));
            printFormula("p1 AND p2", result);

            // p1? (level 0)
            // T: p2? (p1 true, still need p2)
            // T: matched
            // F: empty
            // E: errored
            // F: empty (p1 false -> conjunction fails)
            // E: p2? (p1 errored)
            // T: errored (p1 error propagates)
            // F: errored
            // E: errored
            assertThat(result).isInstanceOf(Decision.class);
            val root = (Decision) result;
            assertThat(root.level()).isZero();
            assertThat(root.trueChild()).isInstanceOf(Decision.class);
            assertThat(root.falseChild()).isSameAs(UniqueTable.EMPTY);
            assertThat(root.errorChild()).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("p1 AND true simplifies to p1")
        void whenAndWithTrueThenSameAsAtom() {
            val andResult  = buildSingle(new And(atom(1L), new Constant(true)), 0);
            val atomResult = buildSingle(atom(1L), 0);
            printFormula("p1 AND true", andResult);
            assertThat(andResult).isEqualTo(atomResult);
        }

        @Test
        @DisplayName("p1 AND false simplifies to empty")
        void whenAndWithFalseThenEmpty() {
            val result = buildSingle(new And(atom(1L), new Constant(false)), 0);
            printFormula("p1 AND false", result);
            assertThat(result).isSameAs(UniqueTable.EMPTY);
        }
    }

    @Nested
    @DisplayName("De Morgan")
    class DeMorganTests {

        @Test
        @DisplayName("NOT(p1 OR p2) equals NOT p1 AND NOT p2")
        void whenNotOrThenAndOfNots() {
            val notOr   = buildSingle(new Not(new Or(atom(1L), atom(2L))), 0, atom(1L), atom(2L));
            val andNots = buildSingle(new And(new Not(atom(1L)), new Not(atom(2L))), 0, atom(1L), atom(2L));
            printFormula("NOT(p1 OR p2)", notOr);
            printFormula("NOT p1 AND NOT p2", andNots);
            assertThat(notOr).isEqualTo(andNots);
        }

        @Test
        @DisplayName("NOT(p1 AND p2) equals NOT p1 OR NOT p2")
        void whenNotAndThenOrOfNots() {
            val notAnd = buildSingle(new Not(new And(atom(1L), atom(2L))), 0, atom(1L), atom(2L));
            val orNots = buildSingle(new Or(new Not(atom(1L)), new Not(atom(2L))), 0, atom(1L), atom(2L));
            printFormula("NOT(p1 AND p2)", notAnd);
            printFormula("NOT p1 OR NOT p2", orNots);
            assertThat(notAnd).isEqualTo(orNots);
        }
    }

    @Nested
    @DisplayName("visual: multi-predicate formulas")
    class VisualTests {

        @Test
        @DisplayName("(p1 AND p2) OR p3 - mixed formula")
        void whenMixedFormulaThenCorrectStructure() {
            val expr        = new Or(new And(atom(1L), atom(2L)), atom(3L));
            val expressions = List.<BooleanExpression>of(atom(1L), atom(2L), atom(3L));
            val table       = new UniqueTable();
            val order       = VariableOrder.fromExpressions(expressions);
            val result      = MtbddBuilder.buildFormula(table, order, expr, 0);

            System.out.println("\n=== (p1 AND p2) OR p3 ===");
            System.out.println(MtbddPrinter.print(result));
            System.out.println("Unique table size: " + table.size());

            assertThat(result).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("NOT(p1 AND p2) AND p3 - negation with conjunction")
        void whenNegatedConjunctionAndAtomThenCorrect() {
            val expr        = new And(new Not(new And(atom(1L), atom(2L))), atom(3L));
            val expressions = List.<BooleanExpression>of(atom(1L), atom(2L), atom(3L));
            val table       = new UniqueTable();
            val order       = VariableOrder.fromExpressions(expressions);
            val result      = MtbddBuilder.buildFormula(table, order, expr, 0);

            System.out.println("\n=== NOT(p1 AND p2) AND p3 ===");
            System.out.println(MtbddPrinter.print(result));
            System.out.println("Unique table size: " + table.size());

            assertThat(result).isInstanceOf(Decision.class);
        }
    }

    /**
     * Builds a single formula MTBDD using the given expression as both
     * the formula and the source for the variable order.
     */
    private static MtbddNode buildSingle(BooleanExpression expression, int formulaIndex) {
        val table = new UniqueTable();
        val order = VariableOrder.fromExpressions(List.of(expression));
        return MtbddBuilder.buildFormula(table, order, expression, formulaIndex);
    }

    /**
     * Builds a single formula MTBDD with a variable order derived from
     * the given additional expressions.
     */
    private static MtbddNode buildSingle(BooleanExpression expression, int formulaIndex,
            BooleanExpression... orderSources) {
        val table = new UniqueTable();
        val order = VariableOrder.fromExpressions(List.of(orderSources));
        return MtbddBuilder.buildFormula(table, order, expression, formulaIndex);
    }

    private static void printFormula(String label, MtbddNode node) {
        System.out.println("\n=== " + label + " ===");
        System.out.println(MtbddPrinter.print(node));
    }

}
