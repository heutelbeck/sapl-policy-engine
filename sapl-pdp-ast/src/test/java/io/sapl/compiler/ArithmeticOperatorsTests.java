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
package io.sapl.compiler;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
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

class ArithmeticOperatorsTests {

    // ========== Addition ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_add_withNumbers_then_returnsSum(String description, Value a, Value b, Value expected) {
        val actual = ArithmeticOperators.add(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_add_withNumbers_then_returnsSum() {
        return Stream.of(arguments("positive + positive", Value.of(3), Value.of(2), Value.of(5)),
                arguments("negative + negative", Value.of(-3), Value.of(-2), Value.of(-5)),
                arguments("positive + negative", Value.of(3), Value.of(-2), Value.of(1)),
                arguments("zero + number", Value.of(0), Value.of(5), Value.of(5)),
                arguments("number + zero", Value.of(5), Value.of(0), Value.of(5)),
                arguments("decimal addition", Value.of(1.5), Value.of(2.5), Value.of(4.0)), arguments("large numbers",
                        Value.of(1_000_000_000L), Value.of(2_000_000_000L), Value.of(3_000_000_000L)));
    }

    @Test
    void when_add_withStrings_then_concatenates() {
        val actual = ArithmeticOperators.add(null, Value.of("hello"), Value.of("world"));
        assertThat(actual).isEqualTo(Value.of("helloworld"));
    }

    @Test
    void when_add_withEmptyStrings_then_concatenates() {
        val actual = ArithmeticOperators.add(null, Value.of(""), Value.of(""));
        assertThat(actual).isEqualTo(Value.of(""));
    }

    @Test
    void when_add_withStringAndNumber_then_concatenatesUsingToString() {
        val actual = ArithmeticOperators.add(null, Value.of("value:"), Value.of(5));
        assertThat(actual).isEqualTo(Value.of("value:5"));
    }

    @Test
    void when_add_withStringAndBoolean_then_concatenatesUsingToString() {
        val actual = ArithmeticOperators.add(null, Value.of("flag:"), Value.of(true));
        assertThat(actual).isEqualTo(Value.of("flag:true"));
    }

    @Test
    void when_add_withNumberAndNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Numeric operation requires number values");
    }

    @Test
    void when_add_withBooleanAndNumber_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(true), Value.of(5));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_add_withLeftError_then_returnsLeftError() {
        val error  = Value.error("left error");
        val actual = ArithmeticOperators.add(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_add_withRightError_then_returnsRightError() {
        val error  = Value.error("right error");
        val actual = ArithmeticOperators.add(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_add_withBothErrors_then_returnsLeftError() {
        val leftError  = Value.error("left error");
        val rightError = Value.error("right error");
        val actual     = ArithmeticOperators.add(null, leftError, rightError);
        assertThat(actual).isSameAs(leftError);
    }

    // ========== Subtraction ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtract_then_returnsDifference(String description, Value a, Value b, Value expected) {
        val actual = ArithmeticOperators.subtract(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtract_then_returnsDifference() {
        return Stream.of(arguments("positive - positive", Value.of(5), Value.of(3), Value.of(2)),
                arguments("negative - negative", Value.of(-5), Value.of(-3), Value.of(-2)),
                arguments("positive - negative", Value.of(5), Value.of(-3), Value.of(8)),
                arguments("negative - positive", Value.of(-5), Value.of(3), Value.of(-8)),
                arguments("zero - number", Value.of(0), Value.of(5), Value.of(-5)),
                arguments("number - zero", Value.of(5), Value.of(0), Value.of(5)),
                arguments("decimal subtraction", Value.of(5.5), Value.of(2.5), Value.of(3.0)),
                arguments("same numbers", Value.of(7), Value.of(7), Value.of(0)));
    }

    @Test
    void when_subtract_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.subtract(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_subtract_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.subtract(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_subtract_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.subtract(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Multiplication ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_multiply_then_returnsProduct(String description, Value a, Value b, Value expected) {
        val actual = ArithmeticOperators.multiply(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_multiply_then_returnsProduct() {
        return Stream.of(arguments("positive * positive", Value.of(3), Value.of(4), Value.of(12)),
                arguments("negative * negative", Value.of(-3), Value.of(-4), Value.of(12)),
                arguments("positive * negative", Value.of(3), Value.of(-4), Value.of(-12)),
                arguments("negative * positive", Value.of(-3), Value.of(4), Value.of(-12)),
                arguments("zero * number", Value.of(0), Value.of(5), Value.of(0)),
                arguments("number * zero", Value.of(5), Value.of(0), Value.of(0)),
                arguments("one * number", Value.of(1), Value.of(7), Value.of(7)),
                arguments("decimal multiplication", Value.of(2.5), Value.of(4), Value.of(10.0)));
    }

    @Test
    void when_multiply_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.multiply(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_multiply_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.multiply(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_multiply_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.multiply(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Division ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_divide_then_returnsQuotient(String description, Value a, Value b, Value expected) {
        val actual = ArithmeticOperators.divide(null, a, b);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_divide_then_returnsQuotient() {
        return Stream.of(arguments("exact division", Value.of(10), Value.of(2), Value.of(5)),
                arguments("negative / positive", Value.of(-10), Value.of(2), Value.of(-5)),
                arguments("positive / negative", Value.of(10), Value.of(-2), Value.of(-5)),
                arguments("negative / negative", Value.of(-10), Value.of(-2), Value.of(5)),
                arguments("zero / number", Value.of(0), Value.of(5), Value.of(0)),
                arguments("number / one", Value.of(7), Value.of(1), Value.of(7)));
    }

    @Test
    void when_divide_byZero_then_returnsError() {
        val actual = ArithmeticOperators.divide(null, Value.of(5), Value.of(0));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Division by zero");
    }

    @Test
    void when_divide_nonTerminating_then_returnsResultWithPrecision() {
        // ArithmeticOperators uses MathContext.DECIMAL128 (34 digits precision)
        // so non-terminating decimals produce a result, not an error
        val actual = ArithmeticOperators.divide(null, Value.of(10), Value.of(3));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val result = ((NumberValue) actual).value();
        // 10/3 ≈ 3.333...
        assertThat(result.doubleValue()).isCloseTo(3.333333333, org.assertj.core.api.Assertions.within(0.0001));
    }

    @Test
    void when_divide_oneThird_then_returnsApproximation() {
        val actual = ArithmeticOperators.divide(null, Value.of(1), Value.of(3));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val result = ((NumberValue) actual).value();
        // Verify precision - should have 34 significant digits
        assertThat(result.precision()).isEqualTo(34);
    }

    @Test
    void when_divide_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.divide(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_divide_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.divide(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_divide_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.divide(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Modulo ==========
    // Uses Euclidean semantics: result is non-negative when divisor is positive

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_modulo_then_returnsRemainder(String description, Value dividend, Value divisor, Value expected) {
        val actual = ArithmeticOperators.modulo(null, dividend, divisor);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_modulo_then_returnsRemainder() {
        return Stream.of(arguments("positive mod positive", Value.of(7), Value.of(3), Value.of(1)),
                arguments("exact multiple", Value.of(9), Value.of(3), Value.of(0)),
                arguments("zero mod number", Value.of(0), Value.of(5), Value.of(0)),
                arguments("number mod 1", Value.of(7), Value.of(1), Value.of(0)),
                arguments("smaller mod larger", Value.of(3), Value.of(7), Value.of(3)),
                // Euclidean: negative mod positive -> positive result
                arguments("-7 mod 3 = 2 (Euclidean)", Value.of(-7), Value.of(3), Value.of(2)),
                arguments("-1 mod 3 = 2", Value.of(-1), Value.of(3), Value.of(2)),
                arguments("-10 mod 3 = 2", Value.of(-10), Value.of(3), Value.of(2)),
                arguments("positive mod negative", Value.of(7), Value.of(-3), Value.of(1)),
                arguments("negative mod negative", Value.of(-7), Value.of(-3), Value.of(-1)));
    }

    @Test
    void when_modulo_byZero_then_returnsError() {
        val actual = ArithmeticOperators.modulo(null, Value.of(5), Value.of(0));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Division by zero");
    }

    @Test
    void when_modulo_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.modulo(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_modulo_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.modulo(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_modulo_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.modulo(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_modulo_withDecimals_then_computesCorrectly() {
        val actual = ArithmeticOperators.modulo(null, Value.of(5.5), Value.of(2.0));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val result = ((NumberValue) actual).value();
        assertThat(result.compareTo(new BigDecimal("1.5"))).isZero();
    }

    // ========== Unary Plus ==========

    @Test
    void when_unaryPlus_withPositiveNumber_then_returnsUnchanged() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of(5));
        assertThat(actual).isEqualTo(Value.of(5));
    }

    @Test
    void when_unaryPlus_withNegativeNumber_then_returnsUnchanged() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of(-5));
        assertThat(actual).isEqualTo(Value.of(-5));
    }

    @Test
    void when_unaryPlus_withZero_then_returnsZero() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of(0));
        assertThat(actual).isEqualTo(Value.of(0));
    }

    @Test
    void when_unaryPlus_withDecimal_then_returnsUnchanged() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of(5.64));
        assertThat(actual).isEqualTo(Value.of(5.64));
    }

    @Test
    void when_unaryPlus_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryPlus_withBoolean_then_returnsError() {
        val actual = ArithmeticOperators.unaryPlus(null, Value.of(true));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryPlus_withError_then_returnsError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.unaryPlus(null, error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Unary Minus ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_unaryMinus_then_returnsNegated(String description, Value input, Value expected) {
        val actual = ArithmeticOperators.unaryMinus(null, input);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_unaryMinus_then_returnsNegated() {
        return Stream.of(arguments("positive to negative", Value.of(5), Value.of(-5)),
                arguments("negative to positive", Value.of(-5), Value.of(5)),
                arguments("zero unchanged", Value.of(0), Value.of(0)),
                arguments("decimal negated", Value.of(2.5), Value.of(-2.5)),
                arguments("negative decimal to positive", Value.of(-5.64), Value.of(5.64)));
    }

    @Test
    void when_unaryMinus_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.unaryMinus(null, Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryMinus_withBoolean_then_returnsError() {
        val actual = ArithmeticOperators.unaryMinus(null, Value.of(false));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryMinus_withError_then_returnsError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.unaryMinus(null, error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Less Than ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_lessThan_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = ArithmeticOperators.lessThan(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_lessThan_then_returnsComparison() {
        return Stream.of(arguments("less than", Value.of(3), Value.of(5), true),
                arguments("greater than", Value.of(5), Value.of(3), false),
                arguments("equal", Value.of(5), Value.of(5), false),
                arguments("negative less than positive", Value.of(-3), Value.of(5), true),
                arguments("negative less than zero", Value.of(-3), Value.of(0), true),
                arguments("zero less than positive", Value.of(0), Value.of(5), true),
                arguments("decimal comparison", Value.of(2.5), Value.of(3.5), true),
                arguments("close decimals", Value.of(3.14), Value.of(3.15), true));
    }

    @Test
    void when_lessThan_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.lessThan(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_lessThan_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.lessThan(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_lessThan_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.lessThan(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Less Than or Equal ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_lessThanOrEqual_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = ArithmeticOperators.lessThanOrEqual(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_lessThanOrEqual_then_returnsComparison() {
        return Stream.of(arguments("less than", Value.of(3), Value.of(5), true),
                arguments("greater than", Value.of(5), Value.of(3), false),
                arguments("equal integers", Value.of(5), Value.of(5), true),
                arguments("equal decimals", Value.of(2.5), Value.of(2.5), true),
                arguments("negative <= zero", Value.of(-1), Value.of(0), true),
                arguments("zero <= zero", Value.of(0), Value.of(0), true));
    }

    @Test
    void when_lessThanOrEqual_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.lessThanOrEqual(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_lessThanOrEqual_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.lessThanOrEqual(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    // ========== Greater Than ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_greaterThan_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = ArithmeticOperators.greaterThan(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_greaterThan_then_returnsComparison() {
        return Stream.of(arguments("greater than", Value.of(5), Value.of(3), true),
                arguments("less than", Value.of(3), Value.of(5), false),
                arguments("equal", Value.of(5), Value.of(5), false),
                arguments("positive greater than negative", Value.of(5), Value.of(-3), true),
                arguments("zero greater than negative", Value.of(0), Value.of(-5), true),
                arguments("decimal comparison", Value.of(3.5), Value.of(2.5), true));
    }

    @Test
    void when_greaterThan_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.greaterThan(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_greaterThan_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.greaterThan(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_greaterThan_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.greaterThan(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Greater Than or Equal ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_greaterThanOrEqual_then_returnsComparison(String description, Value a, Value b, boolean expected) {
        val actual = ArithmeticOperators.greaterThanOrEqual(null, a, b);
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> when_greaterThanOrEqual_then_returnsComparison() {
        return Stream.of(arguments("greater than", Value.of(5), Value.of(3), true),
                arguments("less than", Value.of(3), Value.of(5), false),
                arguments("equal integers", Value.of(5), Value.of(5), true),
                arguments("equal decimals", Value.of(2.5), Value.of(2.5), true),
                arguments("zero >= negative", Value.of(0), Value.of(-1), true),
                arguments("zero >= zero", Value.of(0), Value.of(0), true));
    }

    @Test
    void when_greaterThanOrEqual_withNonNumber_then_returnsError() {
        val actual = ArithmeticOperators.greaterThanOrEqual(null, Value.of(5), Value.of("text"));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_greaterThanOrEqual_withLeftError_then_returnsLeftError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.greaterThanOrEqual(null, error, Value.of(5));
        assertThat(actual).isSameAs(error);
    }

    @Test
    void when_greaterThanOrEqual_withRightError_then_returnsRightError() {
        val error  = Value.error("error");
        val actual = ArithmeticOperators.greaterThanOrEqual(null, Value.of(5), error);
        assertThat(actual).isSameAs(error);
    }

    // ========== Null and Undefined handling ==========

    @Test
    void when_add_withNull_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(5), Value.NULL);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_add_withUndefined_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(5), Value.UNDEFINED);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_lessThan_withNull_then_returnsError() {
        val actual = ArithmeticOperators.lessThan(null, Value.of(5), Value.NULL);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_unaryMinus_withNull_then_returnsError() {
        val actual = ArithmeticOperators.unaryMinus(null, Value.NULL);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    // ========== Array and Object handling ==========

    @Test
    void when_add_withArray_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(5), Value.ofArray(Value.of(1), Value.of(2)));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_add_withObject_then_returnsError() {
        val actual = ArithmeticOperators.add(null, Value.of(5), Value.EMPTY_OBJECT);
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_multiply_withArray_then_returnsError() {
        val actual = ArithmeticOperators.multiply(null, Value.EMPTY_ARRAY, Value.of(5));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }
}
