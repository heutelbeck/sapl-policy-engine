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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class UnitsFunctionLibraryTests {

    private static final TextValue VERY_LONG_NUMBER = Value.of("1".repeat(10000) + "X");

    @ParameterizedTest(name = "parse plain number {0} -> {1}")
    @CsvSource({ "42, 42.0", "3.14159, 3.14159", "0, 0.0", "-100, -100.0" })
    void parse_whenPlainNumbers_thenReturnsNumberUnchanged(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse decimal unit {0} -> {1}")
    @CsvSource({ "5K, 5000", "10K, 10000", "1K, 1000", "0.5K, 500", "4M, 4000000", "1.5M, 1500000", "0.001M, 1000",
            "2.5M, 2500000", "10G, 10000000000", "1G, 1000000000", "0.5G, 500000000", "2.25G, 2250000000",
            "1T, 1000000000000", "0.5T, 500000000000", "2T, 2000000000000", "1P, 1000000000000000",
            "1E, 1000000000000000000" })
    void parse_whenDecimalUnits_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parse milli unit {0} -> {1}")
    @CsvSource({ "1500m, 1.5", "500m, 0.5", "1m, 0.001", "2000m, 2.0" })
    void parse_whenMilliUnits_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(0.00001));
    }

    @ParameterizedTest(name = "parse case-sensitive M: {0} -> {1}")
    @CsvSource({ "1500m, 1.5, 0.00001", "1500M, 1500000000.0, 1.0" })
    void parse_whenLowercaseAndUppercaseM_thenShowsDifferentInterpretations(String input, double expected,
            double tolerance) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(tolerance));
    }

    @ParameterizedTest(name = "parse binary unit {0} -> {1}")
    @CsvSource({ "1Ki, 1024", "2Ki, 2048", "10Ki, 10240", "0.5Ki, 512", "1Mi, 1048576", "2Mi, 2097152", "5Mi, 5242880",
            "0.5Mi, 524288", "1Gi, 1073741824", "2Gi, 2147483648", "0.5Gi, 536870912", "1Ti, 1099511627776",
            "1Pi, 1125899906842624", "1Ei, 1152921504606846976" })
    void parse_whenBinaryUnits_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parse scientific notation {0} -> {1}")
    @CsvSource({ "1e3K, 1000000", "1e-3K, 1", "2.5e6M, 2500000000000", "1.5e3M, 1500000000", "1e-2G, 10000000",
            "5e-2M, 50000" })
    void parse_whenScientificNotation_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parse with whitespace: {0}")
    @ValueSource(strings = { "  10K  ", " 5M ", "  42  ", " 1.5Gi " })
    void parse_whenWhitespace_thenTrimsAndParsesCorrectly(String input) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
    }

    @ParameterizedTest(name = "parse negative/zero {0} -> {1}")
    @CsvSource({ "-5K, -5000", "0K, 0" })
    void parse_whenNegativeOrZeroValues_thenHandlesCorrectly(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parse invalid format: {0}")
    @ValueSource(strings = { "invalid", "ABC", "", "   ", "10 K 20" })
    void parse_whenInvalidFormat_thenReturnsError(String input) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid unit format");
    }

    @ParameterizedTest(name = "parse unknown unit: {0}")
    @ValueSource(strings = { "10Z", "5Q", "3X", "10XY" })
    void parse_whenUnknownUnit_thenReturnsError(String input) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Unknown unit");
    }

    @Test
    void parse_whenMalformedNumber_thenReturnsError() {
        val result = UnitsFunctionLibrary.parse(Value.of("1.2.3K"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid unit format");
    }

    @ParameterizedTest(name = "parseBytes plain bytes {0} -> {1}")
    @CsvSource({ "1024, 1024", "1024B, 1024", "0, 0", "-512, -512" })
    void parseBytes_whenPlainBytesOrWithExplicitB_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parseBytes decimal {0} -> {1}")
    @CsvSource({ "10KB, 10000", "5KB, 5000", "1KB, 1000", "0.5KB, 500", "4MB, 4000000", "1.5MB, 1500000",
            "10MB, 10000000", "2.5MB, 2500000", "1GB, 1000000000", "5GB, 5000000000", "0.5GB, 500000000",
            "2.25GB, 2250000000", "1TB, 1000000000000", "2TB, 2000000000000", "0.5TB, 500000000000" })
    void parseBytes_whenDecimalByteUnits_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parseBytes binary {0} -> {1}")
    @CsvSource({ "1KiB, 1024", "2KiB, 2048", "10KiB, 10240", "0.5KiB, 512", "1MiB, 1048576", "2MiB, 2097152",
            "5MiB, 5242880", "0.5MiB, 524288", "10.75MiB, 11272192", "1GiB, 1073741824", "2GiB, 2147483648",
            "0.5GiB, 536870912", "1TiB, 1099511627776" })
    void parseBytes_whenBinaryByteUnits_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parseBytes optional suffix {0} -> {1}")
    @CsvSource({ "10K, 10000", "5K, 5000", "1M, 1000000", "1Ki, 1024", "1KiB, 1024", "2Mi, 2097152", "2MiB, 2097152" })
    void parseBytes_whenOptionalByteSuffix_thenProducesSameResult(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(0.01));
    }

    @ParameterizedTest(name = "parseBytes case variation {0} -> {1}")
    @CsvSource({ "100MB, 100000000", "100mb, 100000000", "100Mb, 100000000", "100mB, 100000000" })
    void parseBytes_whenDifferentCaseVariations_thenAllProduceSameResult(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(0.01));
    }

    @ParameterizedTest(name = "parseBytes decimal vs binary {0} -> {1}")
    @CsvSource({ "1MB, 1000000.0, 0.01", "1MiB, 1048576.0, 0.01" })
    void parseBytes_whenDecimalAndBinaryMegabyte_thenShowsDifferentValues(String input, double expected,
            double tolerance) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(tolerance));
    }

    @ParameterizedTest(name = "parseBytes scientific {0} -> {1}")
    @CsvSource({ "1.5e3MB, 1500000000", "2e6GiB, 2147483648000000", "1e-2KB, 10" })
    void parseBytes_whenScientificNotation_thenReturnsCorrectValue(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected,
                within(Math.abs(expected * 0.0001)));
    }

    @ParameterizedTest(name = "parseBytes with whitespace: {0}")
    @ValueSource(strings = { "  100KB  ", " 5MB ", "  1024  ", " 1.5GiB " })
    void parseBytes_whenWhitespace_thenTrimsAndParsesCorrectly(String input) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
    }

    @ParameterizedTest(name = "parseBytes negative/zero {0} -> {1}")
    @CsvSource({ "-5KB, -5000", "0MB, 0" })
    void parseBytes_whenNegativeOrZeroValues_thenHandlesCorrectly(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "parseBytes invalid format: {0}")
    @ValueSource(strings = { "not-a-size", "invalid", "", "   " })
    void parseBytes_whenInvalidFormat_thenReturnsError(String input) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid byte format");
    }

    @ParameterizedTest(name = "parseBytes unsupported unit: {0}")
    @ValueSource(strings = { "10PB", "5EB", "1ZB", "10XB" })
    void parseBytes_whenUnsupportedUnit_thenReturnsError(String input) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Unknown byte unit");
    }

    @Test
    void parseBytes_whenMalformedNumber_thenReturnsError() {
        val result = UnitsFunctionLibrary.parseBytes(Value.of("1.2.3MB"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid byte format");
    }

    @ParameterizedTest(name = "parseBytes extreme {0}")
    @MethodSource("extremeValueCases")
    void parseBytes_whenExtremeValues_thenHandlesCorrectly(String input, double expectedMinimum) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isGreaterThan(expectedMinimum);
    }

    private static Stream<Arguments> extremeValueCases() {
        return Stream.of(arguments("999TiB", 1e14), arguments("0.001KB", 0.9));
    }

    @ParameterizedTest(name = "empty or whitespace: {0}")
    @ValueSource(strings = { "", "   " })
    void parse_whenEmptyOrWhitespaceOnly_thenReturnsError(String input) {
        val parseResult      = UnitsFunctionLibrary.parse(Value.of(input));
        val parseBytesResult = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(parseResult).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) parseResult).message()).contains("Invalid unit format");
        assertThat(parseBytesResult).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) parseBytesResult).message()).contains("Invalid byte format");
    }

    @Test
    void parse_whenMaxLongValue_thenHandlesWithoutOverflow() {
        val result = UnitsFunctionLibrary.parse(Value.of("9223372036854775807"));

        assertThat(result).isInstanceOf(NumberValue.class);
    }

    @Test
    void parse_whenVeryLargeExponent_thenHandlesCorrectly() {
        val result = UnitsFunctionLibrary.parse(Value.of("1e15K"));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isGreaterThan(1e17);
    }

    @ParameterizedTest(name = "ReDoS pattern: {0}")
    @ValueSource(strings = { "1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1X",
            "1111111111111111111111111111111111111111111111111111111111111111111111X",
            "1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1eX", "................................K",
            "1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0Z" })
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parse_whenPotentialReDoSPatterns_thenCompletesQuickly(String input) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "ReDoS byte pattern: {0}")
    @ValueSource(strings = { "1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1KB",
            "1111111111111111111111111111111111111111111111111111111111111111111111XB",
            "................................MB" })
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parseBytes_whenPotentialReDoSPatterns_thenCompletesQuickly(String input) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parse_whenVeryLongInvalidString_thenCompletesQuickly() {
        val result = UnitsFunctionLibrary.parse(VERY_LONG_NUMBER);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }
}
