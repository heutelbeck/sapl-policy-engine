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
package io.sapl.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NumberValue Tests")
class NumberValueTests {

    @ParameterizedTest(name = "NumberValue({0}, {1}) construction")
    @MethodSource("provideNumberCombinations")
    @DisplayName("Constructor creates NumberValue")
    void constructorCreatesValue(BigDecimal number, boolean secret) {
        var value = new NumberValue(number, secret);

        assertThat(value.value()).isEqualByComparingTo(number);
        assertThat(value.secret()).isEqualTo(secret);
    }

    @Test
    @DisplayName("Constructor with null value throws NullPointerException")
    void constructorNullValueThrows() {
        assertThatThrownBy(() -> new NumberValue(null, false)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "Value.of({0}L) returns singleton")
    @ValueSource(longs = { 0, 1, 10 })
    @DisplayName("Value.of(long) returns singletons for 0, 1, 10")
    void factoryLongReturnsSingletons(long value) {
        Value expected = switch ((int) value) {
        case 0  -> Value.ZERO;
        case 1  -> Value.ONE;
        case 10 -> Value.TEN;
        default -> throw new IllegalArgumentException();
        };

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @ParameterizedTest(name = "Value.of({0}) returns singleton")
    @ValueSource(doubles = { 0.0, 1.0, 10.0 })
    @DisplayName("Value.of(double) returns singletons for 0.0, 1.0, 10.0")
    void factoryDoubleReturnsSingletons(double value) {
        Value expected = switch ((int) value) {
        case 0  -> Value.ZERO;
        case 1  -> Value.ONE;
        case 10 -> Value.TEN;
        default -> throw new IllegalArgumentException();
        };

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @Test
    @DisplayName("Value.of(BigDecimal) returns singletons for ZERO, ONE, TEN")
    void factoryBigDecimalReturnsSingletons() {
        assertThat(Value.of(BigDecimal.ZERO)).isSameAs(Value.ZERO);
        assertThat(Value.of(BigDecimal.ONE)).isSameAs(Value.ONE);
        assertThat(Value.of(BigDecimal.TEN)).isSameAs(Value.TEN);
    }

    @Test
    @DisplayName("Value.of(double) with NaN throws IllegalArgumentException")
    void factoryDoubleNaNThrows() {
        assertThatThrownBy(() -> Value.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
    }

    @ParameterizedTest(name = "Value.of({0}) throws IllegalArgumentException")
    @ValueSource(doubles = { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
    @DisplayName("Value.of(double) with infinity throws IllegalArgumentException")
    void factoryDoubleInfinityThrows(double value) {
        assertThatThrownBy(() -> Value.of(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("infinite");
    }

    @ParameterizedTest(name = "asSecret() on {0}")
    @ValueSource(strings = { "0", "1", "10", "3.14", "-5", "1000000" })
    @DisplayName("asSecret() creates secret copy or returns same instance")
    void asSecretBehavior(String numberStr) {
        var number        = new BigDecimal(numberStr);
        var original      = new NumberValue(number, false);
        var alreadySecret = new NumberValue(number, true);

        var secretCopy = original.asSecret();
        assertThat(secretCopy.secret()).isTrue();
        assertThat(((NumberValue) secretCopy).value()).isEqualByComparingTo(number);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0} equals {1} numerically")
    @MethodSource("provideNumericallyEqualPairs")
    @DisplayName("equals() and hashCode() use numerical comparison (1.0 equals 1.00)")
    void equalsAndHashCodeUseNumericalComparison(BigDecimal value1, BigDecimal value2) {
        var num1 = new NumberValue(value1, false);
        var num2 = new NumberValue(value2, false);

        assertThat(num1).isEqualTo(num2).hasSameHashCodeAs(num2);
    }

    @Test
    @DisplayName("equals() and hashCode() ignore secret flag")
    void equalsAndHashCodeIgnoreSecretFlag() {
        var regular = new NumberValue(BigDecimal.TEN, false);
        var secret  = new NumberValue(BigDecimal.TEN, true);

        assertThat(regular).isEqualTo(secret).hasSameHashCodeAs(secret);
    }

    @Test
    @DisplayName("equals() and hashCode() differ for different numerical values")
    void equalsAndHashCodeDifferForDifferentValues() {
        var one = new NumberValue(BigDecimal.ONE, false);
        var ten = new NumberValue(BigDecimal.TEN, false);

        assertThat(one).isNotEqualTo(ten).doesNotHaveSameHashCodeAs(ten);
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() shows value or placeholder")
    void toStringShowsValueOrPlaceholder(BigDecimal number, boolean secret, String expected) {
        var value = new NumberValue(number, secret);

        assertThat(value).hasToString(expected);
    }

    @Test
    @DisplayName("Very large BigDecimal beyond double range")
    void veryLargeBigDecimal() {
        var huge  = new BigDecimal("1" + "0".repeat(1000));
        var value = new NumberValue(huge, false);

        assertThat(value.value()).isEqualByComparingTo(huge);
    }

    @Test
    @DisplayName("Very small BigDecimal beyond double precision")
    void verySmallBigDecimal() {
        var tiny  = new BigDecimal("0." + "0".repeat(1000) + "1");
        var value = new NumberValue(tiny, false);

        assertThat(value.value()).isEqualByComparingTo(tiny);
    }

    @Test
    @DisplayName("Pattern matching extracts value correctly")
    void patternMatchingExtractsValue() {
        Value accessLevel = Value.of(3);

        var result = switch (accessLevel) {
        case NumberValue(BigDecimal level, boolean ignore) when level.compareTo(BigDecimal.valueOf(5)) >= 0 ->
            "High clearance";
        case NumberValue(BigDecimal level, boolean ignore) when level.compareTo(BigDecimal.ZERO) > 0        ->
            "Standard clearance";
        case NumberValue ignore                                                                             ->
            "No clearance";
        default                                                                                             ->
            "Invalid";
        };

        assertThat(result).isEqualTo("Standard clearance");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideConstantCases")
    @DisplayName("Constants have expected secret flag")
    void constantsHaveExpectedSecretFlag(String description, Value constant, boolean expectedSecret) {
        assertThat(constant.secret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> provideNumberCombinations() {
        return Stream.of(Arguments.of(BigDecimal.ZERO, false), Arguments.of(BigDecimal.ONE, true),
                Arguments.of(new BigDecimal("3.14"), false), Arguments.of(new BigDecimal("-100"), true));
    }

    static Stream<Arguments> provideNumericallyEqualPairs() {
        return Stream.of(Arguments.of(new BigDecimal("1"), new BigDecimal("1.0")),
                Arguments.of(new BigDecimal("10"), new BigDecimal("10.00")),
                Arguments.of(new BigDecimal("0"), new BigDecimal("0.0")),
                Arguments.of(new BigDecimal("-0.0"), new BigDecimal("0.0")),
                Arguments.of(new BigDecimal("100"), new BigDecimal("100.000")),
                Arguments.of(new BigDecimal("1E+3"), new BigDecimal("1000")));
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(Arguments.of(new BigDecimal("3.14"), false, "3.14"),
                Arguments.of(BigDecimal.TEN, true, "***SECRET***"), Arguments.of(BigDecimal.ZERO, false, "0"),
                Arguments.of(new BigDecimal("-5"), false, "-5"));
    }

    static Stream<Arguments> provideConstantCases() {
        return Stream.of(Arguments.of("Value.ZERO is not secret", Value.ZERO, false),
                Arguments.of("Value.ONE is not secret", Value.ONE, false),
                Arguments.of("Value.TEN is not secret", Value.TEN, false));
    }

    // ============================================================================
    // HASH CONTRACT AND EDGE CASES
    // ============================================================================

    @ParameterizedTest(name = "Zero with scale {0} hashes identically")
    @MethodSource("provideZeroScales")
    @DisplayName("All zero values hash identically regardless of scale")
    void allZerosHashIdentically(int scale) {
        var zero1 = new NumberValue(BigDecimal.ZERO, false);
        var zero2 = new NumberValue(BigDecimal.ZERO.setScale(scale), false);
        var zero3 = new NumberValue(new BigDecimal(BigInteger.ZERO, scale), false);

        assertThat(zero1).isEqualTo(zero2).isEqualTo(zero3);
        assertThat(zero1.hashCode()).isEqualTo(zero2.hashCode()).isEqualTo(zero3.hashCode());
    }

    @ParameterizedTest(name = "Extreme scale: {1}")
    @MethodSource("provideExtremeScales")
    @DisplayName("hashCode() handles extreme scales without exceptions")
    void extremeScalesHandledGracefully(BigDecimal value, String description) {
        assertThatCode(() -> {
            var num  = new NumberValue(value, false);
            var hash = num.hashCode();
            assertThat(num.hashCode()).isEqualTo(hash);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("stripTrailingZeros overflow case handled correctly")
    void stripTrailingZerosOverflowHandled() {
        var extremeScale = Integer.MAX_VALUE;
        var value        = new BigDecimal(BigInteger.valueOf(100), extremeScale);

        assertThatCode(() -> {
            var num = new NumberValue(value, false);
            num.hashCode();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Common values have distinct hashCodes")
    void commonValuesHaveDistinctHashes() {
        var values = Stream
                .of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("0.1"), new BigDecimal("0.01"),
                        new BigDecimal("-1"), new BigDecimal("100"), new BigDecimal("1000"))
                .map(v -> new NumberValue(v, false)).toList();

        var hashes = values.stream().map(NumberValue::hashCode).distinct().count();

        assertThat(hashes).isEqualTo(values.size());
    }

    @ParameterizedTest(name = "Powers of 10: {0} equals {1}")
    @MethodSource("providePowersOfTen")
    @DisplayName("Powers of ten hash consistently")
    void powersOfTenHashConsistently(String decimalNotation, String exponentialNotation) {
        var decimal     = new NumberValue(new BigDecimal(decimalNotation), false);
        var exponential = new NumberValue(new BigDecimal(exponentialNotation), false);

        assertThat(decimal).isEqualTo(exponential).hasSameHashCodeAs(exponential);
    }

    @Test
    @DisplayName("Zero hashCode uses fast path")
    void zeroUsesOptimizedPath() {
        var zero         = new NumberValue(BigDecimal.ZERO, false);
        var expectedHash = BigDecimal.ZERO.hashCode();

        assertThat(zero.hashCode()).isEqualTo(expectedHash);
    }

    @ParameterizedTest(name = "JSON number: {0}")
    @MethodSource("provideJsonTypicalNumbers")
    @DisplayName("Typical JSON numbers hash correctly")
    void jsonNumbersHashCorrectly(String jsonValue) {
        var value1 = new NumberValue(new BigDecimal(jsonValue), false);
        var value2 = new NumberValue(new BigDecimal(jsonValue), false);

        assertThat(value1).hasSameHashCodeAs(value2);
    }

    @Test
    @DisplayName("Precision preserved in hashCode for high-precision values")
    void precisionPreservedInHash() {
        var highPrecision = new BigDecimal("1.23456789012345678901234567890");
        var rounded       = new BigDecimal("1.23456789012345678901234567891");

        var num1 = new NumberValue(highPrecision, false);
        var num2 = new NumberValue(rounded, false);

        assertThat(num1).isNotEqualTo(num2);
    }

    static Stream<Integer> provideZeroScales() {
        return Stream.of(0, 1, 2, 5, 10, 100, -1, -2, -10);
    }

    static Stream<Arguments> provideExtremeScales() {
        return Stream.of(Arguments.of(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE), "MIN scale: 1E-2147483647"),
                Arguments.of(new BigDecimal(BigInteger.valueOf(123), Integer.MAX_VALUE - 1),
                        "Near MIN scale: 123E-2147483646"),
                Arguments.of(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE), "MAX scale: 1E+2147483648"),
                Arguments.of(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE + 1), "Near MAX scale: 10E+2147483647"),
                Arguments.of(new BigDecimal(BigInteger.valueOf(100), Integer.MAX_VALUE / 2),
                        "Mid-extreme scale: 100E-1073741823"));
    }

    static Stream<Arguments> providePowersOfTen() {
        return Stream.of(Arguments.of("10", "1E1"), Arguments.of("100", "1E2"), Arguments.of("1000", "1E3"),
                Arguments.of("0.1", "1E-1"), Arguments.of("0.01", "1E-2"), Arguments.of("0.001", "1E-3"));
    }

    static Stream<String> provideJsonTypicalNumbers() {
        return Stream.of("0", "1", "-1", "0.5", "1.5", "10.5", "123.456", "-789.012", "1000000", "0.000001",
                "3.14159265", "2.71828182");
    }

}
