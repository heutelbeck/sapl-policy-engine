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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UnitsFunctionLibraryTests {

    private static void assertParsesTo(String input, double expected, double tolerance) {
        val result = UnitsFunctionLibrary.parse(Val.of(input));
        assertThat(result.isNumber()).isTrue();
        assertThat(result.getDouble()).isCloseTo(expected, within(tolerance));
    }

    private static void assertParsesToExactly(String input, double expected) {
        val result = UnitsFunctionLibrary.parse(Val.of(input));
        assertThat(result.isNumber()).isTrue();
        assertThat(result.getDouble()).isEqualTo(expected);
    }

    private static void assertByteParsesTo(String input, double expected, double tolerance) {
        val result = UnitsFunctionLibrary.parseBytes(Val.of(input));
        assertThat(result.isNumber()).isTrue();
        assertThat(result.getDouble()).isCloseTo(expected, within(tolerance));
    }

    private static void assertByteParsesToExactly(String input, double expected) {
        val result = UnitsFunctionLibrary.parseBytes(Val.of(input));
        assertThat(result.isNumber()).isTrue();
        assertThat(result.getDouble()).isEqualTo(expected);
    }

    private static void assertParsesSuccessfully(String input) {
        val result = UnitsFunctionLibrary.parse(Val.of(input));
        assertThat(result.isNumber()).isTrue();
    }

    private static void assertByteParsesSuccessfully(String input) {
        val result = UnitsFunctionLibrary.parseBytes(Val.of(input));
        assertThat(result.isNumber()).isTrue();
    }

    private static void assertParseError(String input, String errorFragment) {
        val result = UnitsFunctionLibrary.parse(Val.of(input));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains(errorFragment);
    }

    private static void assertByteParseError(String input, String errorFragment) {
        val result = UnitsFunctionLibrary.parseBytes(Val.of(input));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains(errorFragment);
    }

    @Nested
    class ParseGeneralUnitsTests {

        @ParameterizedTest
        @CsvSource({ "42, 42.0", "3.14159, 3.14159", "0, 0.0", "-100, -100.0" })
        void whenParsingPlainNumbers_thenReturnsNumberUnchanged(String input, double expected) {
            assertParsesToExactly(input, expected);
        }

        @ParameterizedTest
        @CsvSource({ "5K, 5000", "10K, 10000", "1K, 1000", "0.5K, 500", "4M, 4000000", "1.5M, 1500000", "0.001M, 1000",
                "2.5M, 2500000", "10G, 10000000000", "1G, 1000000000", "0.5G, 500000000", "2.25G, 2250000000",
                "1T, 1000000000000", "0.5T, 500000000000", "2T, 2000000000000", "1P, 1000000000000000",
                "1E, 1000000000000000000" })
        void whenParsingDecimalUnits_thenReturnsCorrectValue(String input, double expected) {
            assertParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @CsvSource({ "1500m, 1.5", "500m, 0.5", "1m, 0.001", "2000m, 2.0" })
        void whenParsingMilliUnits_thenReturnsCorrectValue(String input, double expected) {
            assertParsesTo(input, expected, 0.00001);
        }

        @ParameterizedTest
        @CsvSource({ "1500m, 1.5, 0.00001", "1500M, 1500000000.0, 1.0" })
        void whenComparingLowercaseAndUppercaseM_thenShowsDifferentInterpretations(String input, double expected,
                double tolerance) {
            assertParsesTo(input, expected, tolerance);
        }

        @ParameterizedTest
        @CsvSource({ "1Ki, 1024", "2Ki, 2048", "10Ki, 10240", "0.5Ki, 512", "1Mi, 1048576", "2Mi, 2097152",
                "5Mi, 5242880", "0.5Mi, 524288", "1Gi, 1073741824", "2Gi, 2147483648", "0.5Gi, 536870912",
                "1Ti, 1099511627776", "1Pi, 1125899906842624", "1Ei, 1152921504606846976" })
        void whenParsingBinaryUnits_thenReturnsCorrectValue(String input, double expected) {
            assertParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @CsvSource({ "1e3K, 1000000", "1e-3K, 1", "2.5e6M, 2500000000000", "1.5e3M, 1500000000", "1e-2G, 10000000",
                "5e-2M, 50000" })
        void whenParsingScientificNotation_thenReturnsCorrectValue(String input, double expected) {
            assertParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @ValueSource(strings = { "  10K  ", " 5M ", "  42  ", " 1.5Gi " })
        void whenParsingWithWhitespace_thenTrimsAndParsesCorrectly(String input) {
            assertParsesSuccessfully(input);
        }

        @ParameterizedTest
        @CsvSource({ "-5K, -5000", "0K, 0" })
        void whenParsingNegativeOrZeroValues_thenHandlesCorrectly(String input, double expected) {
            assertParsesToExactly(input, expected);
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid", "ABC", "", "   ", "10 K 20" })
        void whenParsingInvalidFormat_thenReturnsError(String input) {
            assertParseError(input, "Invalid unit format");
        }

        @ParameterizedTest
        @ValueSource(strings = { "10Z", "5Q", "3X", "10XY" })
        void whenParsingUnknownUnit_thenReturnsError(String input) {
            assertParseError(input, "Unknown unit");
        }

        @Test
        void whenParsingMalformedNumber_thenReturnsError() {
            assertParseError("1.2.3K", "Invalid unit format");
        }
    }

    @Nested
    class ParseBytesTests {

        @ParameterizedTest
        @CsvSource({ "1024, 1024", "1024B, 1024", "0, 0", "-512, -512" })
        void whenParsingPlainBytesOrWithExplicitB_thenReturnsCorrectValue(String input, double expected) {
            assertByteParsesToExactly(input, expected);
        }

        @ParameterizedTest
        @CsvSource({ "10KB, 10000", "5KB, 5000", "1KB, 1000", "0.5KB, 500", "4MB, 4000000", "1.5MB, 1500000",
                "10MB, 10000000", "2.5MB, 2500000", "1GB, 1000000000", "5GB, 5000000000", "0.5GB, 500000000",
                "2.25GB, 2250000000", "1TB, 1000000000000", "2TB, 2000000000000", "0.5TB, 500000000000" })
        void whenParsingDecimalByteUnits_thenReturnsCorrectValue(String input, double expected) {
            assertByteParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @CsvSource({ "1KiB, 1024", "2KiB, 2048", "10KiB, 10240", "0.5KiB, 512", "1MiB, 1048576", "2MiB, 2097152",
                "5MiB, 5242880", "0.5MiB, 524288", "10.75MiB, 11272192", "1GiB, 1073741824", "2GiB, 2147483648",
                "0.5GiB, 536870912", "1TiB, 1099511627776" })
        void whenParsingBinaryByteUnits_thenReturnsCorrectValue(String input, double expected) {
            assertByteParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @CsvSource({ "10K, 10000", "5K, 5000", "1M, 1000000", "1Ki, 1024", "1KiB, 1024", "2Mi, 2097152",
                "2MiB, 2097152" })
        void whenParsingWithOptionalByteSuffix_thenProducesSameResult(String input, double expected) {
            assertByteParsesTo(input, expected, 0.01);
        }

        @ParameterizedTest
        @CsvSource({ "100MB, 100000000", "100mb, 100000000", "100Mb, 100000000", "100mB, 100000000" })
        void whenParsingDifferentCaseVariations_thenAllProduceSameResult(String input, double expected) {
            assertByteParsesTo(input, expected, 0.01);
        }

        @ParameterizedTest
        @CsvSource({ "1MB, 1000000.0, 0.01", "1MiB, 1048576.0, 0.01" })
        void whenComparingDecimalAndBinaryMegabyte_thenShowsDifferentValues(String input, double expected,
                double tolerance) {
            assertByteParsesTo(input, expected, tolerance);
        }

        @ParameterizedTest
        @CsvSource({ "1.5e3MB, 1500000000", "2e6GiB, 2147483648000000", "1e-2KB, 10" })
        void whenParsingScientificNotation_thenReturnsCorrectValue(String input, double expected) {
            assertByteParsesTo(input, expected, Math.abs(expected * 0.0001));
        }

        @ParameterizedTest
        @ValueSource(strings = { "  100KB  ", " 5MB ", "  1024  ", " 1.5GiB " })
        void whenParsingWithWhitespace_thenTrimsAndParsesCorrectly(String input) {
            assertByteParsesSuccessfully(input);
        }

        @ParameterizedTest
        @CsvSource({ "-5KB, -5000", "0MB, 0" })
        void whenParsingNegativeOrZeroValues_thenHandlesCorrectly(String input, double expected) {
            assertByteParsesToExactly(input, expected);
        }

        @ParameterizedTest
        @ValueSource(strings = { "not-a-size", "invalid", "", "   " })
        void whenParsingInvalidFormat_thenReturnsError(String input) {
            assertByteParseError(input, "Invalid byte format");
        }

        @ParameterizedTest
        @ValueSource(strings = { "10PB", "5EB", "1ZB", "10XB" })
        void whenParsingUnsupportedUnit_thenReturnsError(String input) {
            assertByteParseError(input, "Unknown byte unit");
        }

        @Test
        void whenParsingMalformedNumber_thenReturnsError() {
            assertByteParseError("1.2.3MB", "Invalid byte format");
        }

        @ParameterizedTest
        @CsvSource({ "999TiB, 1e14", "0.001KB, 0.9" })
        void whenParsingExtremeValues_thenHandlesCorrectly(String input, double expectedMinimum) {
            val result = UnitsFunctionLibrary.parseBytes(Val.of(input));
            assertThat(result.isNumber()).isTrue();
            assertThat(result.getDouble()).isGreaterThan(expectedMinimum);
        }
    }

    @Nested
    class EdgeCaseTests {

        @ParameterizedTest
        @ValueSource(strings = { "", "   " })
        void whenParsingEmptyOrWhitespaceOnly_thenReturnsError(String input) {
            assertParseError(input, "Invalid unit format");
            assertByteParseError(input, "Invalid byte format");
        }

        @Test
        void whenParsingMaxLongValue_thenHandlesWithoutOverflow() {
            assertParsesSuccessfully("9223372036854775807");
        }

        @Test
        void whenParsingVeryLargeExponent_thenHandlesCorrectly() {
            val result = UnitsFunctionLibrary.parse(Val.of("1e15K"));
            assertThat(result.isNumber()).isTrue();
            assertThat(result.getDouble()).isGreaterThan(1e17);
        }
    }
}
