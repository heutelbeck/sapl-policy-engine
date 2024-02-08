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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.Multiple;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;

@ExtendWith(MockitoExtension.class)
class MultipleInterpreterTests {
    protected MultipleInterpreter multipleInterpreter;

    @BeforeEach
    void setUp() {
        multipleInterpreter = new MultipleInterpreter();
    }

    private Multiple buildMultiple(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getNumericAmountRule, Multiple.class);
    }

    @Test
    void getAmountFromMultiple_handlesNullAmount_throwsSaplTestException() {
        final var multiple = mock(Multiple.class);

        when(multiple.getAmount()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> multipleInterpreter.getAmountFromMultiple(multiple));

        assertEquals("Amount needs to be a natural number larger than 1", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = { -2, -1, 0, 1, Integer.MIN_VALUE, Double.MIN_VALUE, Double.MAX_VALUE, -0.33, -0.5, 0.5 })
    void getAmountFromMultiple_handlesInvalidAmount_throwsSaplTestException(final Double amount) {
        final var multiple = mock(Multiple.class);

        when(multiple.getAmount()).thenReturn(BigDecimal.valueOf(amount));

        final var exception = assertThrows(SaplTestException.class,
                () -> multipleInterpreter.getAmountFromMultiple(multiple));

        assertEquals("Amount needs to be a natural number larger than 1", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validAmountPairs")
    void getAmountFromMultiple_handlesValidMultiple_returnsInt(final String multipleString, final int expectedResult) {
        final var multiple = buildMultiple(multipleString);

        final var result = multipleInterpreter.getAmountFromMultiple(multiple);

        assertEquals(expectedResult, result);
    }

    private static Stream<Arguments> validAmountPairs() {
        return Stream.of(Arguments.of("5 times", 5), Arguments.of("12341 times", 12341), Arguments.of("2 times", 2),
                Arguments.of("12 times", 12), Arguments.of(Integer.MAX_VALUE + "times", Integer.MAX_VALUE));
    }
}
