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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("NumberValueLimits")
class NumberValueLimitsTests {

    private static final String ERROR_NOT_A_NUMBER  = "The text is not a decimal number.";
    private static final String ERROR_OUT_OF_RANGE  = "The number is out of range.";
    private static final String ERROR_WIRE_TOO_LONG = "The number is too long. At most 1010 characters are allowed.";

    @Nested
    @DisplayName("fitExact")
    class FitExact {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("reports an exact-fit failure as an out-of-range number")
        void whenNoExactFitExistsThenOutOfRangeIsReported(String description, BigDecimal value) {
            assertThatThrownBy(() -> NumberValueLimits.fitExact(value)).isInstanceOf(ArithmeticException.class)
                    .hasMessage(ERROR_OUT_OF_RANGE);
        }

        static Stream<Arguments> whenNoExactFitExistsThenOutOfRangeIsReported() {
            return Stream.of(arguments("coefficient beyond precision", new BigDecimal("1".repeat(1001))),
                    arguments("quantity beyond maximum", new BigDecimal("1e2000")),
                    arguments("fraction requires rounding", new BigDecimal("1e-1001")));
        }
    }

    @Nested
    @DisplayName("boundedNumber")
    class BoundedNumber {

        @ParameterizedTest(name = "programmatic decimal digits {0}")
        @ValueSource(strings = { "\u0661\u0662\u0663", "\uff11\uff12\uff13" })
        @DisplayName("admits programmatically constructed decimal values")
        void whenDecimalIsProvidedProgrammaticallyThenItIsAccepted(String literal) {
            assertThat(NumberValueLimits.boundedNumber(new BigDecimal(literal))).isEqualTo(Value.of(123));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("admits a number within the precision and scale bounds")
        void whenWithinBoundsThenNumberValue(String description, BigDecimal value) {
            assertThat(NumberValueLimits.boundedNumber(value)).isInstanceOf(NumberValue.class)
                    .isEqualTo(Value.of(value));
        }

        static Stream<Arguments> whenWithinBoundsThenNumberValue() {
            return Stream.of(arguments("zero", BigDecimal.ZERO),
                    arguments("excess scale that can be removed exactly", new BigDecimal("10E-1001")),
                    arguments("largest power of ten", new BigDecimal("1E1999")),
                    arguments("negative power of ten", new BigDecimal("-1E1001")),
                    arguments("zero with large scale", new BigDecimal("0E-1000000")),
                    arguments("small decimal", new BigDecimal("2.5")),
                    arguments("large but bounded", new BigDecimal("1E1000")),
                    arguments("negative scale at the bound", new BigDecimal("1E-1000")),
                    arguments("high precision is not amplifying and is admitted", new BigDecimal("1".repeat(2000))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("rejects a number whose scale magnitude exceeds the bound")
        void whenExceedsBoundsThenError(String description, BigDecimal value) {
            assertThat(NumberValueLimits.boundedNumber(value)).isEqualTo(Value.error(ERROR_OUT_OF_RANGE));
        }

        static Stream<Arguments> whenExceedsBoundsThenError() {
            return Stream.of(arguments("extreme negative scale", new BigDecimal("1E1000001")),
                    arguments("extreme positive scale", new BigDecimal("1E-1000001")));
        }
    }

    @Nested
    @DisplayName("parseBoundedNumber")
    class ParseBoundedNumber {

        @Test
        @DisplayName("parses a valid literal within the bounds")
        void whenValidLiteralThenNumberValue() {
            assertThat(NumberValueLimits.parseBoundedNumber("123.45")).isInstanceOf(NumberValue.class)
                    .isEqualTo(Value.of(new BigDecimal("123.45")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("admits wire overhead only when the value fits exactly")
        void whenWireOverheadThenNumberValue(String description, String literal) {
            assertThat(NumberValueLimits.parseBoundedNumber(literal)).isEqualTo(Value.of(new BigDecimal(literal)));
        }

        static Stream<Arguments> whenWireOverheadThenNumberValue() {
            return Stream.of(arguments("negative coefficient", "-" + "9".repeat(1000)),
                    arguments("exponent at maximum precision", "-" + "9".repeat(1000) + "e1000"),
                    arguments("exactly reducible coefficient", "9".repeat(1000) + "0"),
                    arguments("zero at the wire limit", "0." + "0".repeat(1008)),
                    arguments("zero with large exponent", "0e1000000"),
                    arguments("zero with minimum host scale", "0e2147483648"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("fails closed on an over-long, malformed, or out-of-bounds literal")
        void whenInvalidOrOutOfBoundsThenError(String description, String literal, String message) {
            assertThat(NumberValueLimits.parseBoundedNumber(literal)).isInstanceOf(ErrorValue.class)
                    .isEqualTo(Value.error(message));
        }

        static Stream<Arguments> whenInvalidOrOutOfBoundsThenError() {
            return Stream.of(
                    arguments("irreducible coefficient beyond precision", "1".repeat(1001), ERROR_OUT_OF_RANGE),
                    arguments("over-long zero", "0." + "0".repeat(1009), ERROR_WIRE_TOO_LONG),
                    arguments("length checked before syntax", "x".repeat(1011), ERROR_WIRE_TOO_LONG),
                    arguments("insufficient precision", "1E2000", ERROR_OUT_OF_RANGE),
                    arguments("rounding required", "1E-1001", ERROR_OUT_OF_RANGE),
                    arguments("scale outside the host range", "0e2147483649", ERROR_OUT_OF_RANGE),
                    arguments("extreme exponent", "1E1000001", ERROR_OUT_OF_RANGE),
                    arguments("host exponent overflow", "1e999999999999999999999999999999", ERROR_OUT_OF_RANGE),
                    arguments("non-ASCII exponent digits", "1e" + "\u0669".repeat(30), ERROR_NOT_A_NUMBER),
                    arguments("BMP digits within length limit", "\u0661".repeat(600), ERROR_NOT_A_NUMBER),
                    arguments("BMP digits at length limit", "\u0661".repeat(1010), ERROR_NOT_A_NUMBER),
                    arguments("BMP digits beyond length limit", "\u0661".repeat(1011), ERROR_WIRE_TOO_LONG),
                    arguments("supplementary digits within length limit", "\uD835\uDFCE".repeat(500),
                            ERROR_NOT_A_NUMBER),
                    arguments("supplementary digits at length limit", "\uD835\uDFCE".repeat(505), ERROR_NOT_A_NUMBER),
                    arguments("supplementary digits beyond length limit", "\uD835\uDFCE".repeat(506),
                            ERROR_WIRE_TOO_LONG),
                    arguments("three-byte characters at length limit", "\u4e2d".repeat(1010), ERROR_NOT_A_NUMBER),
                    arguments("three-byte characters beyond length limit", "\u4e2d".repeat(1011), ERROR_WIRE_TOO_LONG),
                    arguments("not a number", "not-a-number", ERROR_NOT_A_NUMBER),
                    arguments("incomplete exponent", "1e+", ERROR_NOT_A_NUMBER),
                    arguments("supplementary digit is not a native decimal character", "\uD835\uDFCE",
                            ERROR_NOT_A_NUMBER),
                    arguments("empty", "", ERROR_NOT_A_NUMBER));
        }

        @ParameterizedTest(name = "native decimal digits {0}")
        @ValueSource(strings = { "\u0661\u0662\u0663", "\uff11\uff12\uff13" })
        @DisplayName("rejects non-ASCII wire digits")
        void whenNonAsciiDigitsAreProvidedToWireReaderThenTheyAreRejected(String literal) {
            assertThat(NumberValueLimits.parseBoundedNumber(literal)).isEqualTo(Value.error(ERROR_NOT_A_NUMBER));
        }
    }
}
