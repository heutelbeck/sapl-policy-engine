/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler.operators;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class NumberOperatorsTests {

    // ========== Addition ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_add_withNumbers_then_returnsSum(String description, Value a, Value b, Value expected) {
        val actual = NumberOperators.add(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_add_withNumbers_then_returnsSum() {
        return Stream.of(arguments("positive + positive", Value.of(3), Value.of(2), Value.of(5)),
                arguments("negative + negative", Value.of(-3), Value.of(-2), Value.of(-5)),
                arguments("positive + negative", Value.of(3), Value.of(-2), Value.of(1)),
                arguments("zero + number", Value.of(0), Value.of(5), Value.of(5)),
                arguments("decimal addition", Value.of(1.5), Value.of(2.5), Value.of(4.0)));
    }

    @Test
    void when_add_withStrings_then_concatenates() {
        val actual = NumberOperators.add(Value.of("hello"), Value.of("world"));
        assertThat(actual).isEqualTo(Value.of("helloworld"));
    }

    @Test
    void when_add_withStringAndNumber_then_concatenatesUsingToString() {
        val actual = NumberOperators.add(Value.of("value:"), Value.of(5));
        assertThat(actual).isEqualTo(Value.of("value:5"));
    }

    @Test
    void when_add_withNumberAndNonNumber_then_returnsError() {
        val actual = NumberOperators.add(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Numeric operation requires number values");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_add_withSecrets_then_preservesSecretFlag(String description, Value a, Value b, boolean expectedSecret) {
        val actual = NumberOperators.add(a, b);
        assertThat(actual.secret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_add_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public numbers", Value.of(3), Value.of(2), false),
                arguments("left secret number", Value.of(3).asSecret(), Value.of(2), true),
                arguments("right secret number", Value.of(3), Value.of(2).asSecret(), true),
                arguments("both secret numbers", Value.of(3).asSecret(), Value.of(2).asSecret(), true),
                arguments("both public strings", Value.of("a"), Value.of("b"), false),
                arguments("left secret string", Value.of("a").asSecret(), Value.of("b"), true));
    }

    // ========== Subtraction ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_subtract_then_returnsDifference(String description, Value a, Value b, Value expected) {
        val actual = NumberOperators.subtract(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtract_then_returnsDifference() {
        return Stream.of(arguments("positive - positive", Value.of(5), Value.of(3), Value.of(2)),
                arguments("negative - negative", Value.of(-5), Value.of(-3), Value.of(-2)),
                arguments("positive - negative", Value.of(5), Value.of(-3), Value.of(8)),
                arguments("zero - number", Value.of(0), Value.of(5), Value.of(-5)),
                arguments("decimal subtraction", Value.of(5.5), Value.of(2.5), Value.of(3.0)));
    }

    @Test
    void when_subtract_withNonNumber_then_returnsError() {
        val actual = NumberOperators.subtract(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Multiplication ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_multiply_then_returnsProduct(String description, Value a, Value b, Value expected) {
        val actual = NumberOperators.multiply(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_multiply_then_returnsProduct() {
        return Stream.of(arguments("positive * positive", Value.of(3), Value.of(4), Value.of(12)),
                arguments("negative * negative", Value.of(-3), Value.of(-4), Value.of(12)),
                arguments("positive * negative", Value.of(3), Value.of(-4), Value.of(-12)),
                arguments("zero * number", Value.of(0), Value.of(5), Value.of(0)),
                arguments("decimal multiplication", Value.of(2.5), Value.of(4), Value.of(10.0)));
    }

    @Test
    void when_multiply_withNonNumber_then_returnsError() {
        val actual = NumberOperators.multiply(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Division ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_divide_then_returnsQuotient(String description, Value a, Value b, Value expected) {
        val actual = NumberOperators.divide(a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_divide_then_returnsQuotient() {
        return Stream.of(arguments("exact division", Value.of(10), Value.of(2), Value.of(5)),
                arguments("negative / positive", Value.of(-10), Value.of(2), Value.of(-5)),
                arguments("positive / negative", Value.of(10), Value.of(-2), Value.of(-5)),
                arguments("negative / negative", Value.of(-10), Value.of(-2), Value.of(5)));
    }

    @Test
    void when_divide_byZero_then_returnsError() {
        val actual = NumberOperators.divide(Value.of(5), Value.of(0));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_divide_nonTerminating_then_returnsError() {
        val actual = NumberOperators.divide(Value.of(10), Value.of(3));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_divide_withNonNumber_then_returnsError() {
        val actual = NumberOperators.divide(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Modulo ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_modulo_then_returnsRemainder(String description, Value dividend, Value divisor, Value expected) {
        val actual = NumberOperators.modulo(dividend, divisor);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_modulo_then_returnsRemainder() {
        return Stream.of(arguments("positive mod positive", Value.of(7), Value.of(3), Value.of(1)),
                arguments("negative mod positive adjusted to positive", Value.of(-7), Value.of(3), Value.of(2)),
                arguments("positive mod negative", Value.of(7), Value.of(-3), Value.of(1)),
                arguments("zero mod number", Value.of(0), Value.of(5), Value.of(0)),
                arguments("number mod 1", Value.of(7), Value.of(1), Value.of(0)));
    }

    @Test
    void when_modulo_byZero_then_returnsError() {
        val actual = NumberOperators.modulo(Value.of(5), Value.of(0));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Division by zero");
    }

    @Test
    void when_modulo_withNonNumber_then_returnsError() {
        val actual = NumberOperators.modulo(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Unary Plus ==========

    @Test
    void when_unaryPlus_withNumber_then_returnsUnchanged() {
        val actual = NumberOperators.unaryPlus(Value.of(5));
        assertThat(actual).isEqualTo(Value.of(5));
    }

    @Test
    void when_unaryPlus_withNegativeNumber_then_returnsUnchanged() {
        val actual = NumberOperators.unaryPlus(Value.of(-5));
        assertThat(actual).isEqualTo(Value.of(-5));
    }

    @Test
    void when_unaryPlus_withNonNumber_then_returnsError() {
        val actual = NumberOperators.unaryPlus(Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Unary Minus ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_unaryMinus_then_returnsNegated(String description, Value input, Value expected) {
        val actual = NumberOperators.unaryMinus(input);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_unaryMinus_then_returnsNegated() {
        return Stream.of(arguments("positive to negative", Value.of(5), Value.of(-5)),
                arguments("negative to positive", Value.of(-5), Value.of(5)),
                arguments("zero unchanged", Value.of(0), Value.of(0)),
                arguments("decimal negated", Value.of(2.5), Value.of(-2.5)));
    }

    @Test
    void when_unaryMinus_withNonNumber_then_returnsError() {
        val actual = NumberOperators.unaryMinus(Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryMinus_withSecret_then_preservesSecretFlag() {
        val actual = NumberOperators.unaryMinus(Value.of(5).asSecret());
        assertThat(actual).isEqualTo(Value.of(-5));
        assertThat(actual.secret()).isTrue();
    }

    // ========== Less Than ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_lessThan_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = NumberOperators.lessThan(a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_lessThan_then_returnsComparison() {
        return Stream.of(arguments("less than", Value.of(3), Value.of(5), true),
                arguments("greater than", Value.of(5), Value.of(3), false),
                arguments("equal", Value.of(5), Value.of(5), false),
                arguments("negative less than positive", Value.of(-3), Value.of(5), true),
                arguments("decimal comparison", Value.of(2.5), Value.of(3.5), true));
    }

    @Test
    void when_lessThan_withNonNumber_then_returnsError() {
        val actual = NumberOperators.lessThan(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Less Than or Equal ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_lessThanOrEqual_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = NumberOperators.lessThanOrEqual(a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_lessThanOrEqual_then_returnsComparison() {
        return Stream.of(arguments("less than", Value.of(3), Value.of(5), true),
                arguments("greater than", Value.of(5), Value.of(3), false),
                arguments("equal", Value.of(5), Value.of(5), true),
                arguments("decimal equal", Value.of(2.5), Value.of(2.5), true));
    }

    @Test
    void when_lessThanOrEqual_withNonNumber_then_returnsError() {
        val actual = NumberOperators.lessThanOrEqual(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Greater Than ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_greaterThan_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = NumberOperators.greaterThan(a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_greaterThan_then_returnsComparison() {
        return Stream.of(arguments("greater than", Value.of(5), Value.of(3), true),
                arguments("less than", Value.of(3), Value.of(5), false),
                arguments("equal", Value.of(5), Value.of(5), false),
                arguments("positive greater than negative", Value.of(5), Value.of(-3), true),
                arguments("decimal comparison", Value.of(3.5), Value.of(2.5), true));
    }

    @Test
    void when_greaterThan_withNonNumber_then_returnsError() {
        val actual = NumberOperators.greaterThan(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Greater Than or Equal ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_greaterThanOrEqual_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = NumberOperators.greaterThanOrEqual(a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_greaterThanOrEqual_then_returnsComparison() {
        return Stream.of(arguments("greater than", Value.of(5), Value.of(3), true),
                arguments("less than", Value.of(3), Value.of(5), false),
                arguments("equal", Value.of(5), Value.of(5), true),
                arguments("decimal equal", Value.of(2.5), Value.of(2.5), true));
    }

    @Test
    void when_greaterThanOrEqual_withNonNumber_then_returnsError() {
        val actual = NumberOperators.greaterThanOrEqual(Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Secret Preservation Tests ==========

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_arithmeticOperations_withSecrets_then_preservesSecretFlag(String description, Value a, Value b,
            boolean expectedSecret) {
        val subtractResult = NumberOperators.subtract(a, b);
        val multiplyResult = NumberOperators.multiply(a, b);
        val moduloResult   = NumberOperators.modulo(a, b);
        assertThat(subtractResult.secret()).isEqualTo(expectedSecret);
        assertThat(multiplyResult.secret()).isEqualTo(expectedSecret);
        assertThat(moduloResult.secret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_arithmeticOperations_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public", Value.of(10), Value.of(3), false),
                arguments("left secret", Value.of(10).asSecret(), Value.of(3), true),
                arguments("right secret", Value.of(10), Value.of(3).asSecret(), true),
                arguments("both secret", Value.of(10).asSecret(), Value.of(3).asSecret(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_comparisonOperations_withSecrets_then_preservesSecretFlag(String description, Value a, Value b,
            boolean expectedSecret) {
        val lessThanResult           = NumberOperators.lessThan(a, b);
        val lessThanOrEqualResult    = NumberOperators.lessThanOrEqual(a, b);
        val greaterThanResult        = NumberOperators.greaterThan(a, b);
        val greaterThanOrEqualResult = NumberOperators.greaterThanOrEqual(a, b);
        assertThat(lessThanResult.secret()).isEqualTo(expectedSecret);
        assertThat(lessThanOrEqualResult.secret()).isEqualTo(expectedSecret);
        assertThat(greaterThanResult.secret()).isEqualTo(expectedSecret);
        assertThat(greaterThanOrEqualResult.secret()).isEqualTo(expectedSecret);
    }

    private static Stream<Arguments> when_comparisonOperations_withSecrets_then_preservesSecretFlag() {
        return Stream.of(arguments("both public", Value.of(5), Value.of(3), false),
                arguments("left secret", Value.of(5).asSecret(), Value.of(3), true),
                arguments("right secret", Value.of(5), Value.of(3).asSecret(), true),
                arguments("both secret", Value.of(5).asSecret(), Value.of(3).asSecret(), true));
    }
}
