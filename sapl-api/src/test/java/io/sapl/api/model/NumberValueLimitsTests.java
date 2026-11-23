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

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("NumberValueLimits")
class NumberValueLimitsTests {

    @Nested
    @DisplayName("boundedNumber")
    class BoundedNumber {

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
            assertThat(NumberValueLimits.boundedNumber(value)).isInstanceOf(ErrorValue.class);
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
        void whenInvalidOrOutOfBoundsThenError(String description, String literal) {
            assertThat(NumberValueLimits.parseBoundedNumber(literal)).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> whenInvalidOrOutOfBoundsThenError() {
            return Stream.of(arguments("over-long literal", "1".repeat(1001)),
                    arguments("over-long zero", "0." + "0".repeat(1009)), arguments("insufficient precision", "1E2000"),
                    arguments("rounding required", "1E-1001"),
                    arguments("scale outside the host range", "0e2147483649"),
                    arguments("extreme exponent", "1E1000001"), arguments("not a number", "not-a-number"),
                    arguments("empty", ""));
        }
    }
}
