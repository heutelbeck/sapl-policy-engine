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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

class MathFunctionLibraryTests {

    private static final double EPSILON = 0.000001;

    @ParameterizedTest
    @MethodSource("provideBinaryOperationTestCases")
    void when_binaryOperation_then_returnsCorrectResult(String operation, double a, double b, double expected) {
        val actual = switch (operation) {
        case "min" -> MathFunctionLibrary.min(Val.of(a), Val.of(b));
        case "max" -> MathFunctionLibrary.max(Val.of(a), Val.of(b));
        default    -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBinaryOperationTestCases() {
        return Stream.of(Arguments.of("min", 5.0, 3.0, 3.0), Arguments.of("max", 5.0, 3.0, 5.0),
                Arguments.of("min", -10.0, -5.0, -10.0), Arguments.of("max", -10.0, -5.0, -5.0),
                Arguments.of("min", 2.5, 2.7, 2.5), Arguments.of("max", 2.5, 2.7, 2.7),
                Arguments.of("min", 0.0, 0.0, 0.0), Arguments.of("max", 0.0, 0.0, 0.0));
    }

    @ParameterizedTest
    @MethodSource("provideUnaryOperationTestCases")
    void when_unaryOperation_then_returnsCorrectResult(String operation, double value, double expected) {
        val actual = switch (operation) {
        case "abs"   -> MathFunctionLibrary.abs(Val.of(value));
        case "ceil"  -> MathFunctionLibrary.ceil(Val.of(value));
        case "floor" -> MathFunctionLibrary.floor(Val.of(value));
        case "round" -> MathFunctionLibrary.round(Val.of(value));
        case "sign"  -> MathFunctionLibrary.sign(Val.of(value));
        default      -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideUnaryOperationTestCases() {
        return Stream.of(Arguments.of("abs", -5.0, 5.0), Arguments.of("abs", 3.7, 3.7), Arguments.of("abs", 0.0, 0.0),
                Arguments.of("abs", -123.456, 123.456), Arguments.of("ceil", 2.2, 3.0), Arguments.of("ceil", 2.8, 3.0),
                Arguments.of("ceil", -2.2, -2.0), Arguments.of("ceil", 5.0, 5.0), Arguments.of("floor", 2.2, 2.0),
                Arguments.of("floor", 2.8, 2.0), Arguments.of("floor", -2.2, -3.0), Arguments.of("floor", 5.0, 5.0),
                Arguments.of("round", 2.2, 2.0), Arguments.of("round", 2.8, 3.0), Arguments.of("round", 3.5, 4.0),
                Arguments.of("round", -3.5, -3.0), Arguments.of("round", 5.0, 5.0), Arguments.of("sign", -5.0, -1.0),
                Arguments.of("sign", 0.0, 0.0), Arguments.of("sign", 3.7, 1.0), Arguments.of("sign", -0.1, -1.0),
                Arguments.of("sign", 100.0, 1.0));
    }

    @ParameterizedTest
    @MethodSource("providePowTestCases")
    void when_pow_then_returnsCorrectResult(double base, double exponent, double expected) {
        val actual = MathFunctionLibrary.pow(Val.of(base), Val.of(exponent));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(EPSILON));
    }

    private static Stream<Arguments> providePowTestCases() {
        return Stream.of(Arguments.of(2.0, 3.0, 8.0), Arguments.of(5.0, 2.0, 25.0), Arguments.of(2.0, -1.0, 0.5),
                Arguments.of(4.0, 0.5, 2.0), Arguments.of(10.0, 0.0, 1.0), Arguments.of(0.0, 5.0, 0.0),
                Arguments.of(2.0, 10.0, 1024.0));
    }

    @Test
    void when_powWithInvalidParameters_then_returnsError() {
        val actual = MathFunctionLibrary.pow(Val.of(-1.0), Val.of(0.5));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Power operation resulted in NaN");
    }

    @ParameterizedTest
    @MethodSource("provideSqrtTestCases")
    void when_sqrt_then_returnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.sqrt(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(EPSILON));
    }

    private static Stream<Arguments> provideSqrtTestCases() {
        return Stream.of(Arguments.of(16.0, 4.0), Arguments.of(2.0, 1.4142135623730951), Arguments.of(0.0, 0.0),
                Arguments.of(100.0, 10.0), Arguments.of(0.25, 0.5));
    }

    @Test
    void when_sqrtWithNegativeValue_then_returnsError() {
        val actual = MathFunctionLibrary.sqrt(Val.of(-1.0));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Cannot calculate square root of a negative number");
    }

    @ParameterizedTest
    @MethodSource("provideClampTestCases")
    void when_clamp_then_returnsCorrectResult(double value, double min, double max, double expected) {
        val actual = MathFunctionLibrary.clamp(Val.of(value), Val.of(min), Val.of(max));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideClampTestCases() {
        return Stream.of(Arguments.of(5.0, 0.0, 10.0, 5.0), Arguments.of(-5.0, 0.0, 10.0, 0.0),
                Arguments.of(15.0, 0.0, 10.0, 10.0), Arguments.of(7.5, 7.5, 7.5, 7.5),
                Arguments.of(0.0, 0.0, 10.0, 0.0), Arguments.of(10.0, 0.0, 10.0, 10.0));
    }

    @Test
    void when_clampWithInvalidRange_then_returnsError() {
        val actual = MathFunctionLibrary.clamp(Val.of(5.0), Val.of(10.0), Val.of(0.0));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Minimum must be less than or equal to maximum");
    }

    @Test
    void when_randomInteger_then_returnsValueInRange() {
        val actual = MathFunctionLibrary.randomInteger(Val.of(10));
        assertThatVal(actual).hasValue();
        val value = actual.get().asInt();
        assertThat(value).isGreaterThanOrEqualTo(0).isLessThan(10);
    }

    @Test
    void when_randomIntegerWithSameSeed_then_returnsSameValue() {
        val actual1 = MathFunctionLibrary.randomInteger(Val.of(100), Val.of(42));
        val actual2 = MathFunctionLibrary.randomInteger(Val.of(100), Val.of(42));

        assertThatVal(actual1).hasValue();
        assertThatVal(actual2).hasValue();
        assertThat(actual1.get().asInt()).isEqualTo(actual2.get().asInt());
    }

    @Test
    void when_randomIntegerWithDifferentSeeds_then_returnsDifferentValues() {
        val actual1 = MathFunctionLibrary.randomInteger(Val.of(100), Val.of(42));
        val actual2 = MathFunctionLibrary.randomInteger(Val.of(100), Val.of(43));

        assertThatVal(actual1).hasValue();
        assertThatVal(actual2).hasValue();
        assertThat(actual1.get().asInt()).isNotEqualTo(actual2.get().asInt());
    }

    @ParameterizedTest
    @MethodSource("provideRandomIntegerErrorTestCases")
    void when_randomIntegerWithInvalidInput_then_returnsError(int bound, Integer seed, String expectedErrorMessage) {
        val actual = seed == null ? MathFunctionLibrary.randomInteger(Val.of(bound))
                : MathFunctionLibrary.randomInteger(Val.of(bound), Val.of(seed));

        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains(expectedErrorMessage);
    }

    private static Stream<Arguments> provideRandomIntegerErrorTestCases() {
        return Stream.of(Arguments.of(0, null, "Bound must be positive"),
                Arguments.of(-5, null, "Bound must be positive"));
    }

    @Test
    void when_randomIntegerWithNonIntegerBound_then_returnsError() {
        val actual = MathFunctionLibrary.randomInteger(Val.of(10.5));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Bound must be an integer");
    }

    @Test
    void when_randomIntegerWithNonIntegerSeed_then_returnsError() {
        val actual = MathFunctionLibrary.randomInteger(Val.of(10), Val.of(42.5));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Seed must be an integer");
    }

    @Test
    void when_randomFloat_then_returnsValueInRange() {
        val actual = MathFunctionLibrary.randomFloat();
        assertThatVal(actual).hasValue();
        val value = actual.get().asDouble();
        assertThat(value).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
    }

    @Test
    void when_randomFloatWithSameSeed_then_returnsSameValue() {
        val actual1 = MathFunctionLibrary.randomFloat(Val.of(42));
        val actual2 = MathFunctionLibrary.randomFloat(Val.of(42));

        assertThatVal(actual1).hasValue();
        assertThatVal(actual2).hasValue();
        assertThat(actual1.get().asDouble()).isEqualTo(actual2.get().asDouble());
    }

    @Test
    void when_randomFloatWithDifferentSeeds_then_returnsDifferentValues() {
        val actual1 = MathFunctionLibrary.randomFloat(Val.of(42));
        val actual2 = MathFunctionLibrary.randomFloat(Val.of(43));

        assertThatVal(actual1).hasValue();
        assertThatVal(actual2).hasValue();
        assertThat(actual1.get().asDouble()).isNotEqualTo(actual2.get().asDouble());
    }

    @Test
    void when_randomFloatWithNonIntegerSeed_then_returnsError() {
        val actual = MathFunctionLibrary.randomFloat(Val.of(42.5));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Seed must be an integer");
    }

    @ParameterizedTest
    @MethodSource("provideConstantTestCases")
    void when_constantFunction_then_returnsCorrectValue(String constantName, double expectedValue) {
        val actual = constantName.equals("pi") ? MathFunctionLibrary.pi() : MathFunctionLibrary.e();
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> provideConstantTestCases() {
        return Stream.of(Arguments.of("pi", Math.PI), Arguments.of("e", Math.E));
    }

    @ParameterizedTest
    @MethodSource("provideLogTestCases")
    void when_log_then_returnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.log(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(EPSILON));
    }

    private static Stream<Arguments> provideLogTestCases() {
        return Stream.of(Arguments.of(Math.E, 1.0), Arguments.of(1.0, 0.0), Arguments.of(10.0, 2.302585092994046),
                Arguments.of(Math.E * Math.E, 2.0));
    }

    @ParameterizedTest
    @MethodSource("provideLog10TestCases")
    void when_log10_then_returnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.log10(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(EPSILON));
    }

    private static Stream<Arguments> provideLog10TestCases() {
        return Stream.of(Arguments.of(100.0, 2.0), Arguments.of(1000.0, 3.0), Arguments.of(1.0, 0.0),
                Arguments.of(10.0, 1.0), Arguments.of(0.1, -1.0));
    }

    @ParameterizedTest
    @MethodSource("provideLogWithBaseTestCases")
    void when_logWithBase_then_returnsCorrectResult(double value, double base, double expected) {
        val actual = MathFunctionLibrary.logb(Val.of(value), Val.of(base));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(EPSILON));
    }

    private static Stream<Arguments> provideLogWithBaseTestCases() {
        return Stream.of(Arguments.of(8.0, 2.0, 3.0), Arguments.of(27.0, 3.0, 3.0), Arguments.of(100.0, 10.0, 2.0),
                Arguments.of(16.0, 4.0, 2.0), Arguments.of(1.0, 10.0, 0.0));
    }

    @ParameterizedTest
    @MethodSource("provideLogErrorTestCases")
    void when_logWithInvalidInput_then_returnsError(String logType, Double value, Double base,
            String expectedErrorMessage) {
        val actual = switch (logType) {
        case "natural"  -> MathFunctionLibrary.log(Val.of(value));
        case "log10"    -> MathFunctionLibrary.log10(Val.of(value));
        case "withBase" -> MathFunctionLibrary.logb(Val.of(value), Val.of(base));
        default         -> throw new IllegalArgumentException("Unknown log type: " + logType);
        };

        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains(expectedErrorMessage);
    }

    private static Stream<Arguments> provideLogErrorTestCases() {
        return Stream.of(Arguments.of("natural", 0.0, null, "Logarithm requires a positive value"),
                Arguments.of("natural", -1.0, null, "Logarithm requires a positive value"),
                Arguments.of("log10", 0.0, null, "Logarithm requires a positive value"),
                Arguments.of("log10", -1.0, null, "Logarithm requires a positive value"),
                Arguments.of("withBase", -1.0, 10.0, "Logarithm requires a positive value"),
                Arguments.of("withBase", 10.0, 1.0, "Logarithm base must be positive and not equal to 1"),
                Arguments.of("withBase", 10.0, -2.0, "Logarithm base must be positive and not equal to 1"),
                Arguments.of("withBase", 10.0, 0.0, "Logarithm base must be positive and not equal to 1"));
    }
}
