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
package io.sapl.compiler.expressions;

import java.util.stream.Stream;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.PureOperator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BooleanExpression extraction from PureOperator")
class BooleanExpressionExtractionTests {

    @Nested
    @DisplayName("non-boolean operators return Atom")
    class NonBooleanOperators {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenNonBooleanThenAtom(String description, String expression) {
            assertThat(compileExpression(expression)).isInstanceOf(PureOperator.class)
                    .satisfies(c -> assertThat(((PureOperator) c).booleanExpression()).isInstanceOf(Atom.class));
        }

        static Stream<Arguments> whenNonBooleanThenAtom() {
            return Stream.of(arguments("comparison", "subject.name == \"alice\""),
                    arguments("regex", "subject.id =~ \"^[a-z]+$\""));
        }
    }

    @Nested
    @DisplayName("binary boolean operators")
    class BinaryBooleanOperators {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void whenBinaryBooleanThenCorrectType(String description, String expression,
                Class<? extends BooleanExpression> expectedType) {
            val compiled = compileExpression(expression);
            assertThat(compiled).isInstanceOf(PureOperator.class).satisfies(c -> {
                val expr = ((PureOperator) c).booleanExpression();
                assertThat(expr).isInstanceOf(expectedType);
                assertThat(switch (expr) {
                case And(var ops) -> ops;
                case Or(var ops)  -> ops;
                default           -> throw new AssertionError("unexpected type");
                }).hasSize(2).allMatch(Atom.class::isInstance);
            });
        }

        static Stream<Arguments> whenBinaryBooleanThenCorrectType() {
            return Stream.of(arguments("binary AND", "(subject.x == 1) && (resource.y == 2)", And.class),
                    arguments("binary OR", "(subject.x == 1) || (resource.y == 2)", Or.class));
        }
    }

    @Nested
    @DisplayName("nested boolean structure")
    class NestedStructure {

        @Test
        @DisplayName("AND inside OR preserves structure")
        void whenAndInsideOrThenPreservesNesting() {
            val expr = extractBooleanExpression("((subject.x == 1) && (subject.y == 2)) || (resource.z == 3)");

            assertThat(expr).isInstanceOf(Or.class);
            assertThat(((Or) expr).operands()).hasSize(2).satisfies(ops -> {
                assertThat(ops.get(0)).isInstanceOf(And.class);
                assertThat(ops.get(1)).isInstanceOf(Atom.class);
            });
        }

        @Test
        @DisplayName("OR inside AND preserves structure")
        void whenOrInsideAndThenPreservesNesting() {
            val expr = extractBooleanExpression("(subject.x == 1) && ((subject.y == 2) || (resource.z == 3))");

            assertThat(expr).isInstanceOf(And.class);
            assertThat(((And) expr).operands()).hasSize(2).satisfies(ops -> {
                assertThat(ops.get(0)).isInstanceOf(Atom.class);
                assertThat(ops.get(1)).isInstanceOf(Or.class);
            });
        }

        @Test
        @DisplayName("NOT of AND preserves structure")
        void whenNotOfAndThenNotWrappingAnd() {
            val expr = extractBooleanExpression("!((subject.x == 1) && (resource.y == 2))");

            assertThat(expr).isInstanceOf(Not.class)
                    .satisfies(e -> assertThat(((Not) e).operand()).isInstanceOf(And.class));
        }

        @Test
        @DisplayName("deeply nested boolean tree")
        void whenDeeplyNestedThenFullStructurePreserved() {
            val expr = extractBooleanExpression(
                    "((subject.a == 1) && (subject.b == 2)) || !((resource.c == 3) || (action == \"read\"))");

            assertThat(expr).isInstanceOf(Or.class);
            val or = (Or) expr;
            assertThat(or.operands()).satisfies(ops -> {
                assertThat(ops.get(0)).isInstanceOf(And.class);
                assertThat(ops.get(1)).isInstanceOf(Not.class);
                assertThat(((Not) ops.get(1)).operand()).isInstanceOf(Or.class);
            });
        }

        @Test
        @DisplayName("negated comparison folds to atom, not Not")
        void whenNegatedComparisonThenFoldedToAtom() {
            assertThat(compileExpression("!(subject.x == 1)")).isInstanceOf(PureOperator.class)
                    .satisfies(c -> assertThat(((PureOperator) c).booleanExpression()).isInstanceOf(Atom.class));
        }
    }

    private static BooleanExpression extractBooleanExpression(String expression) {
        val compiled = compileExpression(expression);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        return ((PureOperator) compiled).booleanExpression();
    }

}
