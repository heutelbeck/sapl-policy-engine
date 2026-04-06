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

    record BuildResult(MtbddNode root, VariableOrder order, UniqueTable table) {}

    @Nested
    @DisplayName("constants")
    class ConstantTests {

        @Test
        @DisplayName("constant true produces matched terminal")
        void whenTrueThenMatched() {
            val result = buildSingle(new Constant(true));
            printFormula("true", result);
            assertThat(result.root()).isInstanceOf(Terminal.class);
            assertThat(((Terminal) result.root()).matched().get(0)).isTrue();
        }

        @Test
        @DisplayName("constant false produces empty terminal")
        void whenFalseThenEmpty() {
            val result = buildSingle(new Constant(false));
            printFormula("false", result);
            assertThat(result.root()).isSameAs(UniqueTable.EMPTY);
        }
    }

    @Nested
    @DisplayName("single atom")
    class AtomTests {

        @Test
        @DisplayName("atom produces decision with matched/empty/errored leaves")
        void whenAtomThenDecisionNode() {
            val result = buildSingle(atom(1L));
            printFormula("p1", result);

            assertThat(result.root()).isInstanceOf(Decision.class).satisfies(n -> {
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
        @DisplayName("negated atom swaps true/false children")
        void whenNegatedAtomThenSwapped() {
            val result = buildSingle(new Not(atom(1L)));
            printFormula("NOT p1", result);

            assertThat(result.root()).isInstanceOf(Decision.class).satisfies(n -> {
                val d = (Decision) n;
                assertThat(d.trueChild()).isSameAs(UniqueTable.EMPTY);
                assertThat(((Terminal) d.falseChild()).matched().get(0)).isTrue();
                assertThat(((Terminal) d.errorChild()).errored().get(0)).isTrue();
            });
        }

        @Test
        @DisplayName("double negation equals original")
        void whenDoubleNegationThenOriginal() {
            val direct  = buildSingle(atom(1L));
            val doubled = buildSingle(new Not(new Not(atom(1L))));
            printFormula("NOT NOT p1", doubled);
            assertThat(doubled.root()).isEqualTo(direct.root());
        }
    }

    @Nested
    @DisplayName("OR")
    class OrTests {

        @Test
        @DisplayName("p1 OR p2: matched if either true, error is terminal")
        void whenOrThenMatchedOnEither() {
            val result = buildSingle(new Or(atom(1L), atom(2L)), atom(1L), atom(2L));
            printFormula("p1 OR p2", result);

            val root = (Decision) result.root();
            assertThat(root.level()).isZero();
            assertThat(((Terminal) root.trueChild()).matched().get(0)).isTrue();
            assertThat(root.falseChild()).isInstanceOf(Decision.class);
            assertThat(root.errorChild()).isInstanceOf(Terminal.class);
            assertThat(((Terminal) root.errorChild()).errored().get(0)).isTrue();
        }

        @Test
        @DisplayName("p1 OR true simplifies to matched")
        void whenOrWithTrueThenMatched() {
            val result = buildSingle(new Or(atom(1L), new Constant(true)));
            printFormula("p1 OR true", result);
            assertThat(result.root()).isInstanceOf(Terminal.class);
            assertThat(((Terminal) result.root()).matched().get(0)).isTrue();
        }

        @Test
        @DisplayName("p1 OR false simplifies to p1")
        void whenOrWithFalseThenSameAsAtom() {
            val orResult   = buildSingle(new Or(atom(1L), new Constant(false)));
            val atomResult = buildSingle(atom(1L));
            printFormula("p1 OR false", orResult);
            assertThat(orResult.root()).isEqualTo(atomResult.root());
        }
    }

    @Nested
    @DisplayName("AND")
    class AndTests {

        @Test
        @DisplayName("p1 AND p2: matched only if both true, error is terminal")
        void whenAndThenMatchedOnBoth() {
            val result = buildSingle(new And(atom(1L), atom(2L)), atom(1L), atom(2L));
            printFormula("p1 AND p2", result);

            val root = (Decision) result.root();
            assertThat(root.level()).isZero();
            assertThat(root.trueChild()).isInstanceOf(Decision.class);
            assertThat(root.falseChild()).isSameAs(UniqueTable.EMPTY);
            assertThat(root.errorChild()).isInstanceOf(Terminal.class);
            assertThat(((Terminal) root.errorChild()).errored().get(0)).isTrue();
        }

        @Test
        @DisplayName("p1 AND true simplifies to p1")
        void whenAndWithTrueThenSameAsAtom() {
            val andResult  = buildSingle(new And(atom(1L), new Constant(true)));
            val atomResult = buildSingle(atom(1L));
            printFormula("p1 AND true", andResult);
            assertThat(andResult.root()).isEqualTo(atomResult.root());
        }

        @Test
        @DisplayName("p1 AND false simplifies to empty")
        void whenAndWithFalseThenEmpty() {
            val result = buildSingle(new And(atom(1L), new Constant(false)));
            printFormula("p1 AND false", result);
            assertThat(result.root()).isSameAs(UniqueTable.EMPTY);
        }
    }

    @Nested
    @DisplayName("De Morgan")
    class DeMorganTests {

        @Test
        @DisplayName("NOT(p1 OR p2) equals NOT p1 AND NOT p2")
        void whenNotOrThenAndOfNots() {
            val notOr   = buildSingle(new Not(new Or(atom(1L), atom(2L))), atom(1L), atom(2L));
            val andNots = buildSingle(new And(new Not(atom(1L)), new Not(atom(2L))), atom(1L), atom(2L));
            printFormula("NOT(p1 OR p2)", notOr);
            printFormula("NOT p1 AND NOT p2", andNots);
            assertThat(notOr.root()).isEqualTo(andNots.root());
        }

        @Test
        @DisplayName("NOT(p1 AND p2) equals NOT p1 OR NOT p2")
        void whenNotAndThenOrOfNots() {
            val notAnd = buildSingle(new Not(new And(atom(1L), atom(2L))), atom(1L), atom(2L));
            val orNots = buildSingle(new Or(new Not(atom(1L)), new Not(atom(2L))), atom(1L), atom(2L));
            printFormula("NOT(p1 AND p2)", notAnd);
            printFormula("NOT p1 OR NOT p2", orNots);
            assertThat(notAnd.root()).isEqualTo(orNots.root());
        }
    }

    @Nested
    @DisplayName("visual: multi-predicate formulas")
    class VisualTests {

        @Test
        @DisplayName("(p1 AND p2) OR p3 - mixed formula")
        void whenMixedFormulaThenCorrectStructure() {
            val expr   = new Or(new And(atom(1L), atom(2L)), atom(3L));
            val result = buildSingle(expr, atom(1L), atom(2L), atom(3L));

            System.out.println("\n=== (p1 AND p2) OR p3 ===");
            System.out.println(MtbddPrinter.print(result.root(), result.order()));
            System.out.println("Unique table size: " + result.table().size());

            assertThat(result.root()).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("NOT(p1 AND p2) AND p3 - negation with conjunction")
        void whenNegatedConjunctionAndAtomThenCorrect() {
            val expr   = new And(new Not(new And(atom(1L), atom(2L))), atom(3L));
            val result = buildSingle(expr, atom(1L), atom(2L), atom(3L));

            System.out.println("\n=== NOT(p1 AND p2) AND p3 ===");
            System.out.println(MtbddPrinter.print(result.root(), result.order()));
            System.out.println("Unique table size: " + result.table().size());

            assertThat(result.root()).isInstanceOf(Decision.class);
        }
    }

    private static BuildResult buildSingle(BooleanExpression expression) {
        val table = new UniqueTable();
        val order = VariableOrder.fromExpressions(List.of(expression));
        return new BuildResult(MtbddBuilder.buildFormula(table, order, expression, 0), order, table);
    }

    private static BuildResult buildSingle(BooleanExpression expression, BooleanExpression... orderSources) {
        val table = new UniqueTable();
        val order = VariableOrder.fromExpressions(List.of(orderSources));
        return new BuildResult(MtbddBuilder.buildFormula(table, order, expression, 0), order, table);
    }

    private static void printFormula(String label, BuildResult result) {
        System.out.println("\n=== " + label + " ===");
        System.out.println(MtbddPrinter.print(result.root(), result.order()));
    }

}
