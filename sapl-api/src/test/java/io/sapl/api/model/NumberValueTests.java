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
package io.sapl.api.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class NumberValueTests {

    @ParameterizedTest(name = "NumberValue({0}, {1}) construction")
    @MethodSource
    void when_constructedWithNumberAndSecretFlag_then_valuesAreSet(BigDecimal number, boolean secret) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new NumberValue(number, metadata);

        assertThat(value.value()).isEqualByComparingTo(number);
        assertThat(value.isSecret()).isEqualTo(secret);
    }

    static Stream<Arguments> when_constructedWithNumberAndSecretFlag_then_valuesAreSet() {
        return Stream.of(arguments(BigDecimal.ZERO, false), arguments(BigDecimal.ONE, true),
                arguments(new BigDecimal("3.14"), false), arguments(new BigDecimal("-100"), true));
    }

    @Test
    void when_constructedWithNullValue_then_throws() {
        assertThatThrownBy(() -> new NumberValue(null, ValueMetadata.EMPTY)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "Value.of({0}L) returns singleton")
    @ValueSource(longs = { 0, 1, 10 })
    void when_factoryLongCalledWithSingletonValue_then_returnsSingleton(long value) {
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
    void when_factoryDoubleCalledWithSingletonValue_then_returnsSingleton(double value) {
        Value expected = switch ((int) value) {
        case 0  -> Value.ZERO;
        case 1  -> Value.ONE;
        case 10 -> Value.TEN;
        default -> throw new IllegalArgumentException();
        };

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @Test
    void when_factoryBigDecimalCalledWithSingletonValue_then_returnsSingleton() {
        assertThat(Value.of(BigDecimal.ZERO)).isSameAs(Value.ZERO);
        assertThat(Value.of(BigDecimal.ONE)).isSameAs(Value.ONE);
        assertThat(Value.of(BigDecimal.TEN)).isSameAs(Value.TEN);
    }

    @Test
    void when_factoryDoubleCalledWithNaN_then_throws() {
        assertThatThrownBy(() -> Value.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN");
    }

    @ParameterizedTest(name = "Value.of({0}) throws IllegalArgumentException")
    @ValueSource(doubles = { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
    void when_factoryDoubleCalledWithInfinity_then_throws(double value) {
        assertThatThrownBy(() -> Value.of(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("infinite");
    }

    @ParameterizedTest(name = "asSecret() on {0}")
    @ValueSource(strings = { "0", "1", "10", "3.14", "-5", "1000000" })
    void when_asSecretCalled_then_createsSecretCopyOrReturnsSameInstance(String numberStr) {
        var number        = new BigDecimal(numberStr);
        var original      = new NumberValue(number, ValueMetadata.EMPTY);
        var alreadySecret = new NumberValue(number, ValueMetadata.SECRET_EMPTY);

        var secretCopy = original.asSecret();
        assertThat(secretCopy.isSecret()).isTrue();
        assertThat(((NumberValue) secretCopy).value()).isEqualByComparingTo(number);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0} equals {1} numerically")
    @MethodSource
    void when_comparedNumerically_then_equalValuesAreEqual(BigDecimal value1, BigDecimal value2) {
        var num1 = new NumberValue(value1, ValueMetadata.EMPTY);
        var num2 = new NumberValue(value2, ValueMetadata.EMPTY);

        assertThat(num1).isEqualTo(num2).hasSameHashCodeAs(num2);
    }

    static Stream<Arguments> when_comparedNumerically_then_equalValuesAreEqual() {
        return Stream.of(arguments(new BigDecimal("1"), new BigDecimal("1.0")),
                arguments(new BigDecimal("10"), new BigDecimal("10.00")),
                arguments(new BigDecimal("0"), new BigDecimal("0.0")),
                arguments(new BigDecimal("-0.0"), new BigDecimal("0.0")),
                arguments(new BigDecimal("100"), new BigDecimal("100.000")),
                arguments(new BigDecimal("1E+3"), new BigDecimal("1000")));
    }

    @Test
    void when_equalsAndHashCodeCompared_then_secretFlagIsIgnored() {
        var regular = new NumberValue(BigDecimal.TEN, ValueMetadata.EMPTY);
        var secret  = new NumberValue(BigDecimal.TEN, ValueMetadata.SECRET_EMPTY);

        assertThat(regular).isEqualTo(secret).hasSameHashCodeAs(secret);
    }

    @Test
    void when_equalsAndHashCodeCompared_then_differForDifferentValues() {
        var one = new NumberValue(BigDecimal.ONE, ValueMetadata.EMPTY);
        var ten = new NumberValue(BigDecimal.TEN, ValueMetadata.EMPTY);

        assertThat(one).isNotEqualTo(ten).doesNotHaveSameHashCodeAs(ten);
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource
    void when_toStringCalled_then_showsValueOrPlaceholder(BigDecimal number, boolean secret, String expected) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new NumberValue(number, metadata);

        assertThat(value).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsValueOrPlaceholder() {
        return Stream.of(arguments(new BigDecimal("3.14"), false, "3.14"),
                arguments(BigDecimal.TEN, true, "***SECRET***"), arguments(BigDecimal.ZERO, false, "0"),
                arguments(new BigDecimal("-5"), false, "-5"));
    }

    @Test
    void when_veryLargeBigDecimalUsed_then_handlesCorrectly() {
        var huge  = new BigDecimal("1" + "0".repeat(1000));
        var value = new NumberValue(huge, ValueMetadata.EMPTY);

        assertThat(value.value()).isEqualByComparingTo(huge);
    }

    @Test
    void when_verySmallBigDecimalUsed_then_handlesCorrectly() {
        var tiny  = new BigDecimal("0." + "0".repeat(1000) + "1");
        var value = new NumberValue(tiny, ValueMetadata.EMPTY);

        assertThat(value.value()).isEqualByComparingTo(tiny);
    }

    @Test
    void when_patternMatchingUsed_then_extractsValueCorrectly() {
        Value accessLevel = Value.of(3);

        assertThat(accessLevel).isInstanceOf(NumberValue.class);
        if (accessLevel instanceof NumberValue(BigDecimal level, ValueMetadata ignored)) {
            assertThat(level).isEqualByComparingTo(BigDecimal.valueOf(3));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constantsChecked_then_haveExpectedSecretFlag(String description, Value constant, boolean expectedSecret) {
        assertThat(constant.isSecret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> when_constantsChecked_then_haveExpectedSecretFlag() {
        return Stream.of(arguments("Value.ZERO is not secret", Value.ZERO, false),
                arguments("Value.ONE is not secret", Value.ONE, false),
                arguments("Value.TEN is not secret", Value.TEN, false));
    }

    // ============================================================================
    // HASH CONTRACT AND EDGE CASES
    // ============================================================================

    @ParameterizedTest(name = "Zero with scale {0} hashes identically")
    @MethodSource
    void when_zeroWithDifferentScale_then_hashesIdentically(int scale) {
        var zero1 = new NumberValue(BigDecimal.ZERO, ValueMetadata.EMPTY);
        var zero2 = new NumberValue(BigDecimal.ZERO.setScale(scale), ValueMetadata.EMPTY);
        var zero3 = new NumberValue(new BigDecimal(BigInteger.ZERO, scale), ValueMetadata.EMPTY);

        assertThat(zero1).isEqualTo(zero2).isEqualTo(zero3);
        assertThat(zero1.hashCode()).isEqualTo(zero2.hashCode()).isEqualTo(zero3.hashCode());
    }

    static Stream<Integer> when_zeroWithDifferentScale_then_hashesIdentically() {
        return Stream.of(0, 1, 2, 5, 10, 100, -1, -2, -10);
    }

    @ParameterizedTest(name = "Extreme scale: {1}")
    @MethodSource
    void when_extremeScaleUsed_then_handledGracefully(BigDecimal value, String description) {
        assertThatCode(() -> {
            var num  = new NumberValue(value, ValueMetadata.EMPTY);
            var hash = num.hashCode();
            assertThat(num.hashCode()).isEqualTo(hash);
        }).doesNotThrowAnyException();
    }

    static Stream<Arguments> when_extremeScaleUsed_then_handledGracefully() {
        return Stream.of(arguments(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE), "MIN scale: 1E-2147483647"),
                arguments(new BigDecimal(BigInteger.valueOf(123), Integer.MAX_VALUE - 1),
                        "Near MIN scale: 123E-2147483646"),
                arguments(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE), "MAX scale: 1E+2147483648"),
                arguments(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE + 1), "Near MAX scale: 10E+2147483647"),
                arguments(new BigDecimal(BigInteger.valueOf(100), Integer.MAX_VALUE / 2),
                        "Mid-extreme scale: 100E-1073741823"));
    }

    @Test
    void when_stripTrailingZerosOverflowCase_then_handledCorrectly() {
        var extremeScale = Integer.MAX_VALUE;
        var value        = new BigDecimal(BigInteger.valueOf(100), extremeScale);

        assertThatCode(() -> {
            var num = new NumberValue(value, ValueMetadata.EMPTY);
            num.hashCode();
        }).doesNotThrowAnyException();
    }

    @Test
    void when_commonValuesHashed_then_haveDistinctHashes() {
        var values = Stream
                .of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("0.1"), new BigDecimal("0.01"),
                        new BigDecimal("-1"), new BigDecimal("100"), new BigDecimal("1000"))
                .map(v -> new NumberValue(v, ValueMetadata.EMPTY)).toList();

        var hashes = values.stream().map(NumberValue::hashCode).distinct().count();

        assertThat(hashes).isEqualTo(values.size());
    }

    @ParameterizedTest(name = "Powers of 10: {0} equals {1}")
    @MethodSource
    void when_powersOfTen_then_hashConsistently(String decimalNotation, String exponentialNotation) {
        var decimal     = new NumberValue(new BigDecimal(decimalNotation), ValueMetadata.EMPTY);
        var exponential = new NumberValue(new BigDecimal(exponentialNotation), ValueMetadata.EMPTY);

        assertThat(decimal).isEqualTo(exponential).hasSameHashCodeAs(exponential);
    }

    static Stream<Arguments> when_powersOfTen_then_hashConsistently() {
        return Stream.of(arguments("10", "1E1"), arguments("100", "1E2"), arguments("1000", "1E3"),
                arguments("0.1", "1E-1"), arguments("0.01", "1E-2"), arguments("0.001", "1E-3"));
    }

    @Test
    void when_zeroHashed_then_usesFastPath() {
        var zero         = new NumberValue(BigDecimal.ZERO, ValueMetadata.EMPTY);
        var expectedHash = BigDecimal.ZERO.hashCode();

        assertThat(zero.hashCode()).isEqualTo(expectedHash);
    }

    @ParameterizedTest(name = "JSON number: {0}")
    @MethodSource
    void when_typicalJsonNumbersHashed_then_hashCorrectly(String jsonValue) {
        var value1 = new NumberValue(new BigDecimal(jsonValue), ValueMetadata.EMPTY);
        var value2 = new NumberValue(new BigDecimal(jsonValue), ValueMetadata.EMPTY);

        assertThat(value1).hasSameHashCodeAs(value2);
    }

    static Stream<String> when_typicalJsonNumbersHashed_then_hashCorrectly() {
        return Stream.of("0", "1", "-1", "0.5", "1.5", "10.5", "123.456", "-789.012", "1000000", "0.000001",
                "3.14159265", "2.71828182");
    }

    @Test
    void when_highPrecisionValuesCompared_then_precisionPreservedInHash() {
        var highPrecision = new BigDecimal("1.23456789012345678901234567890");
        var rounded       = new BigDecimal("1.23456789012345678901234567891");

        var num1 = new NumberValue(highPrecision, ValueMetadata.EMPTY);
        var num2 = new NumberValue(rounded, ValueMetadata.EMPTY);

        assertThat(num1).isNotEqualTo(num2);
    }

}
