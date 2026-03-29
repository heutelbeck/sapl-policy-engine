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
package io.sapl.compiler.document;

import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Conjunction;
import io.sapl.ast.Disjunction;
import io.sapl.ast.Expression;
import io.sapl.ast.Parenthesized;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.parseExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Boolean operator AST transformation")
class BooleanOperatorAstTransformationTests {

    private static Expression unwrap(Expression expr) {
        if (expr instanceof Parenthesized(var inner, var ignored)) {
            return unwrap(inner);
        }
        return expr;
    }

    @Nested
    @DisplayName("Binary (2 operands) produces BinaryOperator with correct type")
    class BinaryOperands {

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource
        void whenTwoOperandsThenBinaryOperatorWithCorrectType(String expr, BinaryOperatorType expectedType) {
            var ast = parseExpression(expr);
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class,
                    bin -> assertThat(bin.op()).isEqualTo(expectedType));
        }

        static Stream<Arguments> whenTwoOperandsThenBinaryOperatorWithCorrectType() {
            return Stream.of(arguments("true && false", BinaryOperatorType.LAZY_AND),
                    arguments("true || false", BinaryOperatorType.LAZY_OR),
                    arguments("true & false", BinaryOperatorType.EAGER_AND),
                    arguments("true | false", BinaryOperatorType.EAGER_OR));
        }
    }

    @Nested
    @DisplayName("N-ary (3 operands) produces Conjunction/Disjunction with correct isEager flag")
    class TernaryOperands {

        @Test
        @DisplayName("a && b && c -> Conjunction(isEager=false)")
        void whenThreeLazyAndThenLazyConjunction() {
            var ast = parseExpression("true && false && true");
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(3);
                assertThat(c.isEager()).isFalse();
            });
        }

        @Test
        @DisplayName("a & b & c -> Conjunction(isEager=true)")
        void whenThreeEagerAndThenEagerConjunction() {
            var ast = parseExpression("true & false & true");
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(3);
                assertThat(c.isEager()).isTrue();
            });
        }

        @Test
        @DisplayName("a || b || c -> Disjunction(isEager=false)")
        void whenThreeLazyOrThenLazyDisjunction() {
            var ast = parseExpression("true || false || true");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isFalse();
            });
        }

        @Test
        @DisplayName("a | b | c -> Disjunction(isEager=true)")
        void whenThreeEagerOrThenEagerDisjunction() {
            var ast = parseExpression("true | false | true");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("N-ary (4+ operands) produces correct nodes with varying lengths")
    class VaryingLengths {

        @ParameterizedTest(name = "&&: {0} operands")
        @MethodSource
        void whenMultipleLazyAndThenLazyConjunctionWithCorrectSize(String expr, int expectedSize) {
            var ast = parseExpression(expr);
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(expectedSize);
                assertThat(c.isEager()).isFalse();
            });
        }

        static Stream<Arguments> whenMultipleLazyAndThenLazyConjunctionWithCorrectSize() {
            return Stream.of(arguments("true && false && true", 3), arguments("true && false && true && false", 4),
                    arguments("true && true && true && true && true", 5));
        }

        @ParameterizedTest(name = "&: {0} operands")
        @MethodSource
        void whenMultipleEagerAndThenEagerConjunctionWithCorrectSize(String expr, int expectedSize) {
            var ast = parseExpression(expr);
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(expectedSize);
                assertThat(c.isEager()).isTrue();
            });
        }

        static Stream<Arguments> whenMultipleEagerAndThenEagerConjunctionWithCorrectSize() {
            return Stream.of(arguments("true & false & true", 3), arguments("true & false & true & false", 4),
                    arguments("true & true & true & true & true", 5));
        }

        @ParameterizedTest(name = "||: {0} operands")
        @MethodSource
        void whenMultipleLazyOrThenLazyDisjunctionWithCorrectSize(String expr, int expectedSize) {
            var ast = parseExpression(expr);
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(expectedSize);
                assertThat(d.isEager()).isFalse();
            });
        }

        static Stream<Arguments> whenMultipleLazyOrThenLazyDisjunctionWithCorrectSize() {
            return Stream.of(arguments("true || false || true", 3), arguments("true || false || true || false", 4),
                    arguments("true || true || true || true || true", 5));
        }

        @ParameterizedTest(name = "|: {0} operands")
        @MethodSource
        void whenMultipleEagerOrThenEagerDisjunctionWithCorrectSize(String expr, int expectedSize) {
            var ast = parseExpression(expr);
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(expectedSize);
                assertThat(d.isEager()).isTrue();
            });
        }

        static Stream<Arguments> whenMultipleEagerOrThenEagerDisjunctionWithCorrectSize() {
            return Stream.of(arguments("true | false | true", 3), arguments("true | false | true | false", 4),
                    arguments("true | true | true | true | true", 5));
        }
    }

    @Nested
    @DisplayName("Precedence without parentheses")
    class PrecedenceWithoutParens {

        @Test
        @DisplayName("a & b && c & d -> LAZY_AND(EAGER_AND, EAGER_AND) - & binds tighter than &&")
        void whenEagerAndWithinLazyAndThenCorrectPrecedence() {
            var ast = parseExpression("true & false && true & false");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_AND);
                assertThat(bin.left()).isInstanceOfSatisfying(BinaryOperator.class,
                        left -> assertThat(left.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                assertThat(bin.right()).isInstanceOfSatisfying(BinaryOperator.class,
                        right -> assertThat(right.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
            });
        }

        @Test
        @DisplayName("a | b || c | d -> LAZY_OR(EAGER_OR, EAGER_OR) - | binds tighter than ||")
        void whenEagerOrWithinLazyOrThenCorrectPrecedence() {
            var ast = parseExpression("true | false || true | false");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(BinaryOperator.class,
                        left -> assertThat(left.op()).isEqualTo(BinaryOperatorType.EAGER_OR));
                assertThat(bin.right()).isInstanceOfSatisfying(BinaryOperator.class,
                        right -> assertThat(right.op()).isEqualTo(BinaryOperatorType.EAGER_OR));
            });
        }

        @Test
        @DisplayName("a && b || c && d -> LAZY_OR(LAZY_AND, LAZY_AND) - && binds tighter than ||")
        void whenLazyAndWithinLazyOrThenCorrectPrecedence() {
            var ast = parseExpression("true && false || true && false");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(BinaryOperator.class,
                        left -> assertThat(left.op()).isEqualTo(BinaryOperatorType.LAZY_AND));
                assertThat(bin.right()).isInstanceOfSatisfying(BinaryOperator.class,
                        right -> assertThat(right.op()).isEqualTo(BinaryOperatorType.LAZY_AND));
            });
        }

        @Test
        @DisplayName("a & b | c & d -> EAGER_OR(EAGER_AND, EAGER_AND) - & binds tighter than |")
        void whenEagerAndWithinEagerOrThenCorrectPrecedence() {
            var ast = parseExpression("true & false | true & false");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(BinaryOperator.class,
                        left -> assertThat(left.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                assertThat(bin.right()).isInstanceOfSatisfying(BinaryOperator.class,
                        right -> assertThat(right.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
            });
        }

        @Test
        @DisplayName("a & b | c & d || e & f | g & h -> full precedence chain")
        void whenAllFourOperatorsWithoutParensThenFullPrecedenceChain() {
            // || is lowest: splits into two || operands
            // && next: each || operand is a single | expression (no && here)
            // | next: each side splits into two | operands
            // & highest: each | operand is an & expression
            var ast = parseExpression("true & false | true & false || true & false | true & false");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, lazyOr -> {
                assertThat(lazyOr.op()).isEqualTo(BinaryOperatorType.LAZY_OR);
                assertThat(lazyOr.left()).isInstanceOfSatisfying(BinaryOperator.class, leftEagerOr -> {
                    assertThat(leftEagerOr.op()).isEqualTo(BinaryOperatorType.EAGER_OR);
                    assertThat(leftEagerOr.left()).isInstanceOfSatisfying(BinaryOperator.class,
                            l -> assertThat(l.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                    assertThat(leftEagerOr.right()).isInstanceOfSatisfying(BinaryOperator.class,
                            r -> assertThat(r.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                });
                assertThat(lazyOr.right()).isInstanceOfSatisfying(BinaryOperator.class, rightEagerOr -> {
                    assertThat(rightEagerOr.op()).isEqualTo(BinaryOperatorType.EAGER_OR);
                    assertThat(rightEagerOr.left()).isInstanceOfSatisfying(BinaryOperator.class,
                            l -> assertThat(l.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                    assertThat(rightEagerOr.right()).isInstanceOfSatisfying(BinaryOperator.class,
                            r -> assertThat(r.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
                });
            });
        }

        @Test
        @DisplayName("a & b & c && d & e & f -> LAZY_AND(Conjunction(eager,3), Conjunction(eager,3))")
        void whenNaryEagerAndWithinLazyAndThenCorrectPrecedence() {
            var ast = parseExpression("true & false & true && true & false & true");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_AND);
                assertThat(bin.left()).isInstanceOfSatisfying(Conjunction.class, left -> {
                    assertThat(left.operands()).hasSize(3);
                    assertThat(left.isEager()).isTrue();
                });
                assertThat(bin.right()).isInstanceOfSatisfying(Conjunction.class, right -> {
                    assertThat(right.operands()).hasSize(3);
                    assertThat(right.isEager()).isTrue();
                });
            });
        }

        @Test
        @DisplayName("a | b | c || d | e | f -> LAZY_OR(Disjunction(eager,3), Disjunction(eager,3))")
        void whenNaryEagerOrWithinLazyOrThenCorrectPrecedence() {
            var ast = parseExpression("true | false | true || true | false | true");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(Disjunction.class, left -> {
                    assertThat(left.operands()).hasSize(3);
                    assertThat(left.isEager()).isTrue();
                });
                assertThat(bin.right()).isInstanceOfSatisfying(Disjunction.class, right -> {
                    assertThat(right.operands()).hasSize(3);
                    assertThat(right.isEager()).isTrue();
                });
            });
        }

        @Test
        @DisplayName("a & b & c | d & e & f -> EAGER_OR(Conjunction(eager,3), Conjunction(eager,3))")
        void whenNaryEagerAndWithinEagerOrThenCorrectPrecedence() {
            var ast = parseExpression("true & false & true | true & false & true");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(Conjunction.class, left -> {
                    assertThat(left.operands()).hasSize(3);
                    assertThat(left.isEager()).isTrue();
                });
                assertThat(bin.right()).isInstanceOfSatisfying(Conjunction.class, right -> {
                    assertThat(right.operands()).hasSize(3);
                    assertThat(right.isEager()).isTrue();
                });
            });
        }

        @Test
        @DisplayName("a && b && c || d && e && f -> LAZY_OR(Conjunction(lazy,3), Conjunction(lazy,3))")
        void whenNaryLazyAndWithinLazyOrThenCorrectPrecedence() {
            var ast = parseExpression("true && false && true || true && false && true");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.LAZY_OR);
                assertThat(bin.left()).isInstanceOfSatisfying(Conjunction.class, left -> {
                    assertThat(left.operands()).hasSize(3);
                    assertThat(left.isEager()).isFalse();
                });
                assertThat(bin.right()).isInstanceOfSatisfying(Conjunction.class, right -> {
                    assertThat(right.operands()).hasSize(3);
                    assertThat(right.isEager()).isFalse();
                });
            });
        }
    }

    @Nested
    @DisplayName("Explicit parentheses create Parenthesized wrappers and prevent n-ary flattening")
    class ParenthesizedBehavior {

        @Test
        @DisplayName("(a & b) is wrapped in Parenthesized containing EAGER_AND")
        void whenParenthesizedEagerAndThenWrapped() {
            var ast = parseExpression("(true & false)");
            assertThat(ast).isInstanceOfSatisfying(Parenthesized.class,
                    p -> assertThat(p.expression()).isInstanceOfSatisfying(BinaryOperator.class,
                            bin -> assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_AND)));
        }

        @Test
        @DisplayName("(a & b) & c -> EAGER_AND(Parenthesized, c) - parens prevent 3-way flattening")
        void whenParensPreventFlatteningThenBinaryNotConjunction() {
            var ast = parseExpression("(true & false) & true");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_AND);
                assertThat(bin.left()).isInstanceOf(Parenthesized.class);
                assertThat(unwrap(bin.left())).isInstanceOfSatisfying(BinaryOperator.class,
                        inner -> assertThat(inner.op()).isEqualTo(BinaryOperatorType.EAGER_AND));
            });
        }

        @Test
        @DisplayName("(a || b) & (c || d) -> EAGER_AND of two Parenthesized(LAZY_OR) - parens override precedence")
        void whenParensOverridePrecedenceThenCorrectNesting() {
            var ast = parseExpression("(true || false) & (true || false)");
            assertThat(ast).isInstanceOfSatisfying(BinaryOperator.class, bin -> {
                assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_AND);
                assertThat(unwrap(bin.left())).isInstanceOfSatisfying(BinaryOperator.class,
                        left -> assertThat(left.op()).isEqualTo(BinaryOperatorType.LAZY_OR));
                assertThat(unwrap(bin.right())).isInstanceOfSatisfying(BinaryOperator.class,
                        right -> assertThat(right.op()).isEqualTo(BinaryOperatorType.LAZY_OR));
            });
        }
    }

    @Nested
    @DisplayName("Single operand passthrough (no wrapping)")
    class SingleOperandPassthrough {

        @Test
        @DisplayName("Single operand is not wrapped in boolean node")
        void whenSingleOperandThenNoWrapping() {
            var ast = parseExpression("true");
            assertThat(ast).isNotInstanceOf(BinaryOperator.class).isNotInstanceOf(Conjunction.class)
                    .isNotInstanceOf(Disjunction.class);
        }
    }

    @Nested
    @DisplayName("Cross-family n-ary without parens")
    class CrossFamilyNary {

        @Test
        @DisplayName("a & b && c & d && e & f -> Conjunction(lazy, 3) where each operand is EAGER_AND")
        void whenNaryLazyAndOfBinaryEagerAndThenCorrectStructure() {
            var ast = parseExpression("true & false && true & false && true & false");
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(3);
                assertThat(c.isEager()).isFalse();
                assertThat(c.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(BinaryOperator.class,
                                bin -> assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_AND)));
            });
        }

        @Test
        @DisplayName("a | b || c | d || e | f -> Disjunction(lazy, 3) where each operand is EAGER_OR")
        void whenNaryLazyOrOfBinaryEagerOrThenCorrectStructure() {
            var ast = parseExpression("true | false || true | false || true | false");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isFalse();
                assertThat(d.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(BinaryOperator.class,
                                bin -> assertThat(bin.op()).isEqualTo(BinaryOperatorType.EAGER_OR)));
            });
        }

        @Test
        @DisplayName("a & b & c | d & e & f | g & h & i -> Disjunction(eager, 3) with Conjunction(eager, 3) children")
        void whenNaryEagerOrOfNaryEagerAndThenCorrectStructure() {
            var ast = parseExpression("true & false & true | true & false & true | true & false & true");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isTrue();
                assertThat(d.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(Conjunction.class, c -> {
                            assertThat(c.operands()).hasSize(3);
                            assertThat(c.isEager()).isTrue();
                        }));
            });
        }

        @Test
        @DisplayName("a && b && c || d && e && f || g && h && i -> Disjunction(lazy, 3) with Conjunction(lazy, 3) children")
        void whenNaryLazyOrOfNaryLazyAndThenCorrectStructure() {
            var ast = parseExpression("true && false && true || true && false && true || true && false && true");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isFalse();
                assertThat(d.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(Conjunction.class, c -> {
                            assertThat(c.operands()).hasSize(3);
                            assertThat(c.isEager()).isFalse();
                        }));
            });
        }

        @Test
        @DisplayName("a & b & c && d & e & f && g & h & i -> Conjunction(lazy, 3) with Conjunction(eager, 3) children")
        void whenNaryLazyAndOfNaryEagerAndThenCorrectStructure() {
            var ast = parseExpression("true & false & true && true & false & true && true & false & true");
            assertThat(ast).isInstanceOfSatisfying(Conjunction.class, c -> {
                assertThat(c.operands()).hasSize(3);
                assertThat(c.isEager()).isFalse();
                assertThat(c.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(Conjunction.class, inner -> {
                            assertThat(inner.operands()).hasSize(3);
                            assertThat(inner.isEager()).isTrue();
                        }));
            });
        }

        @Test
        @DisplayName("a | b | c || d | e | f || g | h | i -> Disjunction(lazy, 3) with Disjunction(eager, 3) children")
        void whenNaryLazyOrOfNaryEagerOrThenCorrectStructure() {
            var ast = parseExpression("true | false | true || true | false | true || true | false | true");
            assertThat(ast).isInstanceOfSatisfying(Disjunction.class, d -> {
                assertThat(d.operands()).hasSize(3);
                assertThat(d.isEager()).isFalse();
                assertThat(d.operands())
                        .allSatisfy(operand -> assertThat(operand).isInstanceOfSatisfying(Disjunction.class, inner -> {
                            assertThat(inner.operands()).hasSize(3);
                            assertThat(inner.isEager()).isTrue();
                        }));
            });
        }
    }
}
