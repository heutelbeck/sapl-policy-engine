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
import static org.mockito.Mockito.mockStatic;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class DurationInterpreterTests {
    protected DurationInterpreter durationInterpreter;

    protected final MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class,
            Answers.RETURNS_DEEP_STUBS);

    @BeforeEach
    void setUp() {
        durationInterpreter = new DurationInterpreter();
    }

    @AfterEach
    void tearDown() {
        durationMockedStatic.close();
    }

    private io.sapl.test.grammar.sapltest.Duration buildDuration(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getDurationRule,
                io.sapl.test.grammar.sapltest.Duration.class);
    }

    @Test
    void getJavaDurationFromDuration_forNullDuration_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> durationInterpreter.getJavaDurationFromDuration(null));

        assertEquals("The passed Duration is null", exception.getMessage());
    }

    @Test
    void getJavaDurationFromDuration_parseThrowsException_throwsSaplTestException() {
        final var duration = buildDuration("\"\"");

        durationMockedStatic.when(() -> Duration.parse("")).thenThrow(new RuntimeException("error"));

        final var exception = assertThrows(SaplTestException.class,
                () -> durationInterpreter.getJavaDurationFromDuration(duration));

        assertEquals("The provided Duration has an invalid format", exception.getMessage());
    }

    @Test
    void getJavaDurationFromDuration_handlesDuration_returnsAbsoluteDuration() {
        final var duration = buildDuration("\"-PT5S\"");

        final var javaDurationMock = mock(Duration.class);
        durationMockedStatic.when(() -> Duration.parse("-PT5S").abs()).thenReturn(javaDurationMock);

        final var result = durationInterpreter.getJavaDurationFromDuration(duration);

        assertEquals(javaDurationMock, result);
    }
}
