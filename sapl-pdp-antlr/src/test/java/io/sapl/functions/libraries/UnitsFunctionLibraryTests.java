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
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class UnitsFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(UnitsFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    private static final TextValue VERY_LONG_NUMBER = Value.of("1".repeat(10000) + "X");

    @ParameterizedTest(name = "{2}")
    @MethodSource("parseValidCases")
    void parse_whenValidInput_thenReturnsCorrectValue(String input, double expected, String description) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        val tolerance = expected == 0 ? 0.0001 : Math.abs(expected * 0.0001);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(tolerance));
    }

    private static Stream<Arguments> parseValidCases() {
        return Stream.of(
                // Plain numbers
                arguments("42", 42.0, "plain integer"), arguments("4.14159", 4.14159, "plain decimal"),
                arguments("0", 0.0, "zero"), arguments("-100", -100.0, "negative integer"),

                // Decimal units - K (kilo)
                arguments("5K", 5000.0, "kilo: 5K"), arguments("10K", 10000.0, "kilo: 10K"),
                arguments("1K", 1000.0, "kilo: 1K"), arguments("0.5K", 500.0, "kilo: fractional 0.5K"),
                arguments("-5K", -5000.0, "kilo: negative -5K"), arguments("0K", 0.0, "kilo: zero 0K"),

                // Decimal units - M (mega)
                arguments("4M", 4000000.0, "mega: 4M"), arguments("1.5M", 1500000.0, "mega: fractional 1.5M"),
                arguments("0.001M", 1000.0, "mega: small fraction 0.001M"), arguments("2.5M", 2500000.0, "mega: 2.5M"),

                // Decimal units - G (giga)
                arguments("10G", 10000000000.0, "giga: 10G"), arguments("1G", 1000000000.0, "giga: 1G"),
                arguments("0.5G", 500000000.0, "giga: fractional 0.5G"),
                arguments("2.25G", 2250000000.0, "giga: 2.25G"),

                // Decimal units - T, P, E
                arguments("1T", 1000000000000.0, "tera: 1T"),
                arguments("0.5T", 500000000000.0, "tera: fractional 0.5T"),
                arguments("2T", 2000000000000.0, "tera: 2T"), arguments("1P", 1000000000000000.0, "peta: 1P"),
                arguments("1E", 1000000000000000000.0, "exa: 1E"),

                // Milli units (lowercase m)
                arguments("1500m", 1.5, "milli: 1500m"), arguments("500m", 0.5, "milli: 500m"),
                arguments("1m", 0.001, "milli: 1m"), arguments("2000m", 2.0, "milli: 2000m"),

                // Case sensitivity - m vs M
                arguments("1500M", 1500000000.0, "case sensitivity: 1500M (mega) vs 1500m (milli)"),

                // Binary units - Ki, Mi, Gi, Ti, Pi, Ei
                arguments("1Ki", 1024.0, "binary kilo: 1Ki"), arguments("2Ki", 2048.0, "binary kilo: 2Ki"),
                arguments("10Ki", 10240.0, "binary kilo: 10Ki"),
                arguments("0.5Ki", 512.0, "binary kilo: fractional 0.5Ki"),
                arguments("1Mi", 1048576.0, "binary mega: 1Mi"), arguments("2Mi", 2097152.0, "binary mega: 2Mi"),
                arguments("5Mi", 5242880.0, "binary mega: 5Mi"),
                arguments("0.5Mi", 524288.0, "binary mega: fractional 0.5Mi"),
                arguments("1Gi", 1073741824.0, "binary giga: 1Gi"), arguments("2Gi", 2147483648.0, "binary giga: 2Gi"),
                arguments("0.5Gi", 536870912.0, "binary giga: fractional 0.5Gi"),
                arguments("1Ti", 1099511627776.0, "binary tera: 1Ti"),
                arguments("1Pi", 1125899906842624.0, "binary peta: 1Pi"),
                arguments("1Ei", 1152921504606846976.0, "binary exa: 1Ei"),

                // Scientific notation with units
                arguments("1e3K", 1000000.0, "scientific: 1e3K"),
                arguments("1e-3K", 1.0, "scientific: negative exponent 1e-3K"),
                arguments("2.5e6M", 2500000000000.0, "scientific: 2.5e6M"),
                arguments("1.5e3M", 1500000000.0, "scientific: 1.5e3M"),
                arguments("1e-2G", 10000000.0, "scientific: 1e-2G"), arguments("5e-2M", 50000.0, "scientific: 5e-2M"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("parseWhitespaceCases")
    void parse_whenWhitespace_thenTrimsAndParsesCorrectly(String input, String description) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
    }

    private static Stream<Arguments> parseWhitespaceCases() {
        return Stream.of(arguments("  10K  ", "leading and trailing spaces with kilo"),
                arguments(" 5M ", "single spaces with mega"), arguments("  42  ", "plain number with spaces"),
                arguments(" 1.5Gi ", "binary unit with spaces"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("parseErrorCases")
    void parse_whenInvalidInput_thenReturnsError(String input, String expectedMessage, String description) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(expectedMessage);
    }

    private static Stream<Arguments> parseErrorCases() {
        return Stream.of(
                // Invalid formats
                arguments("invalid", "Invalid unit format", "non-numeric text"),
                arguments("ABC", "Invalid unit format", "alphabetic only"),
                arguments("", "Invalid unit format", "empty string"),
                arguments("   ", "Invalid unit format", "whitespace only"),
                arguments("10 K 20", "Invalid unit format", "spaces within value"),
                arguments("1.2.3K", "Invalid unit format", "malformed number with multiple decimals"),

                // Unknown units
                arguments("10Z", "Unknown unit", "unknown unit Z"), arguments("5Q", "Unknown unit", "unknown unit Q"),
                arguments("3X", "Unknown unit", "unknown unit X"),
                arguments("10XY", "Unknown unit", "unknown multi-char unit XY"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("parseBytesValidCases")
    void parseBytes_whenValidInput_thenReturnsCorrectValue(String input, double expected, String description) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        val tolerance = expected == 0 ? 0.01 : Math.abs(expected * 0.0001);
        assertThat(((NumberValue) result).value().doubleValue()).isCloseTo(expected, within(tolerance));
    }

    private static Stream<Arguments> parseBytesValidCases() {
        return Stream.of(
                // Plain bytes
                arguments("1024", 1024.0, "plain bytes without unit"), arguments("1024B", 1024.0, "explicit B suffix"),
                arguments("0", 0.0, "zero bytes"), arguments("-512", -512.0, "negative bytes"),
                arguments("-5KB", -5000.0, "negative kilobytes"), arguments("0MB", 0.0, "zero megabytes"),

                // Decimal byte units - KB
                arguments("10KB", 10000.0, "decimal kilo: 10KB"), arguments("5KB", 5000.0, "decimal kilo: 5KB"),
                arguments("1KB", 1000.0, "decimal kilo: 1KB"),
                arguments("0.5KB", 500.0, "decimal kilo: fractional 0.5KB"),

                // Decimal byte units - MB
                arguments("4MB", 4000000.0, "decimal mega: 4MB"),
                arguments("1.5MB", 1500000.0, "decimal mega: fractional 1.5MB"),
                arguments("10MB", 10000000.0, "decimal mega: 10MB"),
                arguments("2.5MB", 2500000.0, "decimal mega: 2.5MB"),

                // Decimal byte units - GB
                arguments("1GB", 1000000000.0, "decimal giga: 1GB"),
                arguments("5GB", 5000000000.0, "decimal giga: 5GB"),
                arguments("0.5GB", 500000000.0, "decimal giga: fractional 0.5GB"),
                arguments("2.25GB", 2250000000.0, "decimal giga: 2.25GB"),

                // Decimal byte units - TB
                arguments("1TB", 1000000000000.0, "decimal tera: 1TB"),
                arguments("2TB", 2000000000000.0, "decimal tera: 2TB"),
                arguments("0.5TB", 500000000000.0, "decimal tera: fractional 0.5TB"),

                // Binary byte units - KiB
                arguments("1KiB", 1024.0, "binary kilo: 1KiB"), arguments("2KiB", 2048.0, "binary kilo: 2KiB"),
                arguments("10KiB", 10240.0, "binary kilo: 10KiB"),
                arguments("0.5KiB", 512.0, "binary kilo: fractional 0.5KiB"),

                // Binary byte units - MiB
                arguments("1MiB", 1048576.0, "binary mega: 1MiB"), arguments("2MiB", 2097152.0, "binary mega: 2MiB"),
                arguments("5MiB", 5242880.0, "binary mega: 5MiB"),
                arguments("0.5MiB", 524288.0, "binary mega: fractional 0.5MiB"),
                arguments("10.75MiB", 11272192.0, "binary mega: 10.75MiB"),

                // Binary byte units - GiB, TiB
                arguments("1GiB", 1073741824.0, "binary giga: 1GiB"),
                arguments("2GiB", 2147483648.0, "binary giga: 2GiB"),
                arguments("0.5GiB", 536870912.0, "binary giga: fractional 0.5GiB"),
                arguments("1TiB", 1099511627776.0, "binary tera: 1TiB"),

                // Optional B suffix equivalence
                arguments("10K", 10000.0, "optional B suffix: 10K same as 10KB"),
                arguments("1M", 1000000.0, "optional B suffix: 1M same as 1MB"),
                arguments("1Ki", 1024.0, "optional B suffix: 1Ki same as 1KiB"),
                arguments("2Mi", 2097152.0, "optional B suffix: 2Mi same as 2MiB"),

                // Case insensitivity for byte units
                arguments("100MB", 100000000.0, "case variation: 100MB"),
                arguments("100mb", 100000000.0, "case variation: 100mb (lowercase)"),
                arguments("100Mb", 100000000.0, "case variation: 100Mb (mixed)"),
                arguments("100mB", 100000000.0, "case variation: 100mB (mixed)"),

                // Decimal vs binary comparison
                arguments("1MB", 1000000.0, "decimal vs binary: 1MB (decimal)"),
                arguments("1MiB", 1048576.0, "decimal vs binary: 1MiB (binary, 4.86% larger)"),

                // Scientific notation
                arguments("1.5e3MB", 1500000000.0, "scientific: 1.5e3MB"),
                arguments("2e6GiB", 2147483648000000.0, "scientific: 2e6GiB"),
                arguments("1e-2KB", 10.0, "scientific: negative exponent 1e-2KB"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("parseBytesWhitespaceCases")
    void parseBytes_whenWhitespace_thenTrimsAndParsesCorrectly(String input, String description) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
    }

    private static Stream<Arguments> parseBytesWhitespaceCases() {
        return Stream.of(arguments("  100KB  ", "leading and trailing spaces with KB"),
                arguments(" 5MB ", "single spaces with MB"), arguments("  1024  ", "plain bytes with spaces"),
                arguments(" 1.5GiB ", "binary unit with spaces"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("parseBytesErrorCases")
    void parseBytes_whenInvalidInput_thenReturnsError(String input, String expectedMessage, String description) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(expectedMessage);
    }

    private static Stream<Arguments> parseBytesErrorCases() {
        return Stream.of(
                // Invalid formats
                arguments("not-a-size", "Invalid byte format", "non-numeric text"),
                arguments("invalid", "Invalid byte format", "alphabetic word"),
                arguments("", "Invalid byte format", "empty string"),
                arguments("   ", "Invalid byte format", "whitespace only"),
                arguments("1.2.3MB", "Invalid byte format", "malformed number with multiple decimals"),

                // Unknown/unsupported units
                arguments("10PB", "Unknown byte unit", "unsupported petabyte unit"),
                arguments("5EB", "Unknown byte unit", "unsupported exabyte unit"),
                arguments("1ZB", "Unknown byte unit", "unsupported zettabyte unit"),
                arguments("10XB", "Unknown byte unit", "unknown unit XB"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("extremeValueCases")
    void parseBytes_whenExtremeValues_thenHandlesCorrectly(String input, double expectedMinimum, String description) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().doubleValue()).isGreaterThan(expectedMinimum);
    }

    private static Stream<Arguments> extremeValueCases() {
        return Stream.of(arguments("999TiB", 1e14, "very large tebibyte value"),
                arguments("0.001KB", 0.9, "very small kilobyte value"));
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

    @ParameterizedTest(name = "{1}")
    @MethodSource("reDoSPatternCases")
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parse_whenPotentialReDoSPatterns_thenCompletesQuickly(String input, String description) {
        val result = UnitsFunctionLibrary.parse(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> reDoSPatternCases() {
        return Stream.of(arguments("1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1X", "many decimal points with unknown unit"),
                arguments("1111111111111111111111111111111111111111111111111111111111111111111111X",
                        "very long digit sequence with unknown unit"),
                arguments("1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1eX", "repeated scientific notation pattern"),
                arguments("................................K", "many dots with kilo unit"),
                arguments("1.2.3.4.5.6.7.8.9.0.1.2.3.4.5.6.7.8.9.0Z", "IP-like pattern with unknown unit"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("reDoSBytePatternCases")
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parseBytes_whenPotentialReDoSPatterns_thenCompletesQuickly(String input, String description) {
        val result = UnitsFunctionLibrary.parseBytes(Value.of(input));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> reDoSBytePatternCases() {
        return Stream.of(arguments("1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1KB", "many decimal points with KB"),
                arguments("1111111111111111111111111111111111111111111111111111111111111111111111XB",
                        "very long digit sequence with unknown byte unit"),
                arguments("................................MB", "many dots with MB unit"));
    }

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void parse_whenVeryLongInvalidString_thenCompletesQuickly() {
        val result = UnitsFunctionLibrary.parse(VERY_LONG_NUMBER);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }
}
