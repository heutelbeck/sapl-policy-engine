/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sapl.test.SaplTestException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultipleAmountInterpreterTest {
    private MultipleAmountInterpreter multipleAmountInterpreter;

    @BeforeEach
    void setUp() {
        multipleAmountInterpreter = new MultipleAmountInterpreter();
    }

    @ParameterizedTest
    @ValueSource(strings = { "a", "x", "5c", "5.3x", "", "longString5", "xx", "0", Integer.MIN_VALUE + "x" })
    @NullSource
    void getAmountFromMultipleAmountString_handlesInvalidAmount_throwsSaplTestException(final String amount) {
        final var exception = assertThrows(SaplTestException.class,
                () -> multipleAmountInterpreter.getAmountFromMultipleAmountString(amount));

        assertEquals("MultipleAmount has invalid format", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validAmountPairs")
    void getAmountFromMultipleAmountString_handlesValidAmount_returnsInt(final String amount,
            final int expectedResult) {
        final var result = multipleAmountInterpreter.getAmountFromMultipleAmountString(amount);

        assertEquals(expectedResult, result);
    }

    private static Stream<Arguments> validAmountPairs() {
        return Stream.of(Arguments.of("5x", 5), Arguments.of("12341x", 12341), Arguments.of("0x", 0),
                Arguments.of("12x", 12), Arguments.of(Integer.MAX_VALUE + "x", Integer.MAX_VALUE),
                Arguments.of("-123x", 123), Arguments.of("-0x", 0));
    }
}
