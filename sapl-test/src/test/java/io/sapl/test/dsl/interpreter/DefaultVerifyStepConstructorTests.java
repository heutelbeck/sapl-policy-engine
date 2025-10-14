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
package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.*;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.VerifyStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultVerifyStepConstructorTests {
    @Mock
    protected ExpectationInterpreter       expectInterpreterMock;
    @InjectMocks
    protected DefaultVerifyStepConstructor defaultVerifyStepConstructor;
    @Mock
    protected Scenario                     scenarioMock;
    @Mock
    protected ExpectStep                   expectStepMock;

    @Test
    void constructVerifyStep_handlesNullScenario_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(null, expectStepMock));

        assertEquals("Scenario or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, null));

        assertEquals("Scenario or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullScenarioAndNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(null, null));

        assertEquals("Scenario or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesUnknownExpectation_throwsSaplTestException() {
        final var expectationMock = mock(Expectation.class);

        when(scenarioMock.getExpectation()).thenReturn(expectationMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, expectStepMock));

        assertEquals("Unknown type of Expectation", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpectation_throwsSaplTestException() {
        when(scenarioMock.getExpectation()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, expectStepMock));

        assertEquals("Unknown type of Expectation", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpect_returnsVerifyStep() {
        final var singleExpectMock = mock(SingleExpect.class);

        when(scenarioMock.getExpectation()).thenReturn(singleExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpect(expectStepMock, singleExpectMock)).thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpectWithMatcher_returnsVerifyStep() {
        final var singleExpectWithMatcher = mock(SingleExpectWithMatcher.class);

        when(scenarioMock.getExpectation()).thenReturn(singleExpectWithMatcher);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpectWithMatcher(expectStepMock, singleExpectWithMatcher))
                .thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsRepeatedExpect_returnsVerifyStep() {
        final var repeatedExpectMock = mock(RepeatedExpect.class);

        when(scenarioMock.getExpectation()).thenReturn(repeatedExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretRepeatedExpect(expectStepMock, repeatedExpectMock))
                .thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(scenarioMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }
}
