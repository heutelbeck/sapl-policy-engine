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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import java.math.BigDecimal;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("MathFunctionLibrary")
class MathFunctionLibraryTests {

    private static final double EPSILON = 0.000001;

    // Builds a NumberValue from a finite test double. Value.of(double) returns
    // Value
    // (ErrorValue for NaN/infinity); the math functions take NumberValue, and all
    // inputs here are finite, so the cast is safe.
    private static NumberValue num(double value) {
        return (NumberValue) Value.of(value);
    }

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new MathFunctionLibrary())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an over-range number (infinite as a double) fails closed to an ErrorValue, not a throw")
    void whenAbsOfOverRangeNumberThenErrorValue() {
        val huge = (NumberValue) Value.of(new BigDecimal("1e400"));
        assertThat(MathFunctionLibrary.abs(huge)).isInstanceOf(ErrorValue.class);
    }

    @Test
    @DisplayName("a pow result that overflows to infinity fails closed to an ErrorValue")
    void whenPowOverflowsThenErrorValue() {
        assertThat(MathFunctionLibrary.pow(Value.of(10L), Value.of(400L))).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "{0}: {1} and {2} = {3}")
    @MethodSource("binaryOperationCases")
    void whenBinaryOperationThenReturnsCorrectResult(String operation, double a, double b, double expected) {
        val actual = switch (operation) {
        case "min" -> MathFunctionLibrary.min(num(a), num(b));
        case "max" -> MathFunctionLibrary.max(num(a), num(b));
        default    -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .isEqualTo(expected);
    }

    private static Stream<Arguments> binaryOperationCases() {
        return Stream.of(arguments("min", 5.0, 3.0, 3.0), arguments("max", 5.0, 3.0, 5.0),
                arguments("min", -10.0, -5.0, -10.0), arguments("max", -10.0, -5.0, -5.0),
                arguments("min", 2.5, 2.7, 2.5), arguments("max", 2.5, 2.7, 2.7), arguments("min", 0.0, 0.0, 0.0),
                arguments("max", 0.0, 0.0, 0.0));
    }

    @ParameterizedTest(name = "{0}({1}) = {2}")
    @MethodSource("unaryOperationCases")
    void whenUnaryOperationThenReturnsCorrectResult(String operation, double value, double expected) {
        val actual = switch (operation) {
        case "abs"   -> MathFunctionLibrary.abs(num(value));
        case "ceil"  -> MathFunctionLibrary.ceil(num(value));
        case "floor" -> MathFunctionLibrary.floor(num(value));
        case "round" -> MathFunctionLibrary.round(num(value));
        case "sign"  -> MathFunctionLibrary.sign(num(value));
        default      -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .isEqualTo(expected);
    }

    private static Stream<Arguments> unaryOperationCases() {
        return Stream.of(
                // abs
                arguments("abs", -5.0, 5.0), arguments("abs", 3.7, 3.7), arguments("abs", 0.0, 0.0),
                arguments("abs", -123.456, 123.456),
                // ceil
                arguments("ceil", 2.2, 3.0), arguments("ceil", 2.8, 3.0), arguments("ceil", -2.2, -2.0),
                arguments("ceil", 5.0, 5.0),
                // floor
                arguments("floor", 2.2, 2.0), arguments("floor", 2.8, 2.0), arguments("floor", -2.2, -3.0),
                arguments("floor", 5.0, 5.0),
                // round
                arguments("round", 2.2, 2.0), arguments("round", 2.8, 3.0), arguments("round", 3.5, 4.0),
                arguments("round", -3.5, -3.0), arguments("round", 5.0, 5.0),
                // sign
                arguments("sign", -5.0, -1.0), arguments("sign", 0.0, 0.0), arguments("sign", 3.7, 1.0),
                arguments("sign", -0.1, -1.0), arguments("sign", 100.0, 1.0));
    }

    @ParameterizedTest(name = "pow({0}, {1}) = {2}")
    @MethodSource("powCases")
    void whenPowThenReturnsCorrectResult(double base, double exponent, double expected) {
        val actual = MathFunctionLibrary.pow(num(base), num(exponent));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .satisfies(v -> assertThat(v).isCloseTo(expected, within(EPSILON)));
    }

    private static Stream<Arguments> powCases() {
        return Stream.of(arguments(2.0, 3.0, 8.0), arguments(5.0, 2.0, 25.0), arguments(2.0, -1.0, 0.5),
                arguments(4.0, 0.5, 2.0), arguments(10.0, 0.0, 1.0), arguments(0.0, 5.0, 0.0),
                arguments(2.0, 10.0, 1024.0));
    }

    @ParameterizedTest(name = "sqrt({0}) = {1}")
    @MethodSource("sqrtCases")
    void whenSqrtThenReturnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.sqrt(num(value));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .satisfies(v -> assertThat(v).isCloseTo(expected, within(EPSILON)));
    }

    private static Stream<Arguments> sqrtCases() {
        return Stream.of(arguments(16.0, 4.0), arguments(2.0, 1.4142135623730951), arguments(0.0, 0.0),
                arguments(100.0, 10.0), arguments(0.25, 0.5));
    }

    @ParameterizedTest(name = "clamp({0}, {1}, {2}) = {3}")
    @MethodSource("clampCases")
    void whenClampThenReturnsCorrectResult(double value, double min, double max, double expected) {
        val actual = MathFunctionLibrary.clamp(num(value), num(min), num(max));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .isEqualTo(expected);
    }

    private static Stream<Arguments> clampCases() {
        return Stream.of(arguments(5.0, 0.0, 10.0, 5.0), arguments(-5.0, 0.0, 10.0, 0.0),
                arguments(15.0, 0.0, 10.0, 10.0), arguments(7.5, 7.5, 7.5, 7.5), arguments(0.0, 0.0, 10.0, 0.0),
                arguments(10.0, 0.0, 10.0, 10.0));
    }

    @ParameterizedTest(name = "{0}() = {1}")
    @MethodSource("constantCases")
    void whenConstantFunctionThenReturnsCorrectValue(String constantName, double expectedValue) {
        val actual = "pi".equals(constantName) ? MathFunctionLibrary.pi() : MathFunctionLibrary.e();

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .isEqualTo(expectedValue);
    }

    private static Stream<Arguments> constantCases() {
        return Stream.of(arguments("pi", Math.PI), arguments("e", Math.E));
    }

    @ParameterizedTest(name = "log({0}) = {1}")
    @MethodSource("logCases")
    void whenLogThenReturnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.log(num(value));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .satisfies(v -> assertThat(v).isCloseTo(expected, within(EPSILON)));
    }

    private static Stream<Arguments> logCases() {
        return Stream.of(arguments(Math.E, 1.0), arguments(1.0, 0.0), arguments(10.0, 2.302585092994046),
                arguments(Math.E * Math.E, 2.0));
    }

    @ParameterizedTest(name = "log10({0}) = {1}")
    @MethodSource("log10Cases")
    void whenLog10ThenReturnsCorrectResult(double value, double expected) {
        val actual = MathFunctionLibrary.log10(num(value));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .satisfies(v -> assertThat(v).isCloseTo(expected, within(EPSILON)));
    }

    private static Stream<Arguments> log10Cases() {
        return Stream.of(arguments(100.0, 2.0), arguments(1000.0, 3.0), arguments(1.0, 0.0), arguments(10.0, 1.0),
                arguments(0.1, -1.0));
    }

    @ParameterizedTest(name = "logb({0}, {1}) = {2}")
    @MethodSource("logbCases")
    void whenLogbThenReturnsCorrectResult(double value, double base, double expected) {
        val actual = MathFunctionLibrary.logb(num(value), num(base));

        assertThat(actual).isInstanceOf(NumberValue.class).extracting(v -> ((NumberValue) v).value().doubleValue())
                .satisfies(v -> assertThat(v).isCloseTo(expected, within(EPSILON)));
    }

    private static Stream<Arguments> logbCases() {
        return Stream.of(arguments(8.0, 2.0, 3.0), arguments(27.0, 3.0, 3.0), arguments(100.0, 10.0, 2.0),
                arguments(16.0, 4.0, 2.0), arguments(1.0, 10.0, 0.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorCases")
    void whenInvalidInputThenReturnsError(String description, Value result, String expectedMessage) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(expectedMessage);
    }

    private static Stream<Arguments> errorCases() {
        return Stream.of(
                // pow errors
                arguments("pow(-1, 0.5) - NaN result", MathFunctionLibrary.pow(num(-1.0), num(0.5)),
                        "Power operation resulted in NaN"),
                // sqrt errors
                arguments("sqrt(-1) - negative value", MathFunctionLibrary.sqrt(num(-1.0)),
                        "Cannot calculate square root of a negative number"),
                // clamp errors
                arguments("clamp with min > max", MathFunctionLibrary.clamp(num(5.0), num(10.0), num(0.0)),
                        "Minimum must be less than or equal to maximum"),
                // log errors
                arguments("log(0) - zero value", MathFunctionLibrary.log(num(0.0)),
                        "Logarithm requires a positive value"),
                arguments("log(-1) - negative value", MathFunctionLibrary.log(num(-1.0)),
                        "Logarithm requires a positive value"),
                arguments("log10(0) - zero value", MathFunctionLibrary.log10(num(0.0)),
                        "Logarithm requires a positive value"),
                arguments("log10(-1) - negative value", MathFunctionLibrary.log10(num(-1.0)),
                        "Logarithm requires a positive value"),
                arguments("logb(-1, 10) - negative value", MathFunctionLibrary.logb(num(-1.0), num(10.0)),
                        "Logarithm requires a positive value"),
                arguments("logb(10, 1) - base 1", MathFunctionLibrary.logb(num(10.0), num(1.0)),
                        "Logarithm base must be positive and not equal to 1"),
                arguments("logb(10, -2) - negative base", MathFunctionLibrary.logb(num(10.0), num(-2.0)),
                        "Logarithm base must be positive and not equal to 1"),
                arguments("logb(10, 0) - zero base", MathFunctionLibrary.logb(num(10.0), num(0.0)),
                        "Logarithm base must be positive and not equal to 1"));
    }
}
