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
            return Stream.of(arguments("zero", BigDecimal.ZERO), arguments("small decimal", new BigDecimal("2.5")),
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
        @DisplayName("fails closed on an over-long, malformed, or out-of-bounds literal")
        void whenInvalidOrOutOfBoundsThenError(String description, String literal) {
            assertThat(NumberValueLimits.parseBoundedNumber(literal)).isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> whenInvalidOrOutOfBoundsThenError() {
            return Stream.of(arguments("over-long literal", "1".repeat(1001)),
                    arguments("extreme exponent", "1E1000001"), arguments("not a number", "not-a-number"),
                    arguments("empty", ""));
        }
    }
}
