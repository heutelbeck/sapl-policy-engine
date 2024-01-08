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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTestCaseConstructorTest {

    @Mock
    ValInterpreter valInterpreterMock;

    @InjectMocks
    DefaultTestCaseConstructor defaultTestCaseConstructor;

    @Mock
    SaplTestFixture saplTestFixtureMock;

    @Test
    void constructTestCase_handlesNullEnvironmentWithoutMocks_returnsGivenOrWhenStep() {
        when(valInterpreterMock.destructureObject(null)).thenReturn(null);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(saplTestFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, false);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesNullEnvironmentWithMocks_returnsGivenOrWhenStep() {
        when(valInterpreterMock.destructureObject(null)).thenReturn(null);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, true);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesEmptyEnvironmentVariables_returnsGivenOrWhenStep() {
        when(valInterpreterMock.destructureObject(null)).thenReturn(Collections.emptyMap());

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, true);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesSingleEnvironmentVariable_returnsGivenOrWhenStep() {
        final var valueMock = mock(JsonNode.class);
        when(valInterpreterMock.destructureObject(null)).thenReturn(Map.of("key", valueMock));

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, true);

        assertEquals(givenOrWhenStepMock, result);

        verify(saplTestFixtureMock, times(1)).registerVariable("key", valueMock);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesMultipleEnvironmentVariables_returnsGivenOrWhenStep() {
        final var valueMock  = mock(JsonNode.class);
        final var value2Mock = mock(JsonNode.class);
        when(valInterpreterMock.destructureObject(null)).thenReturn(Map.of("key", valueMock, "key2", value2Mock));

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(saplTestFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, false);

        assertEquals(givenOrWhenStepMock, result);

        verify(saplTestFixtureMock, times(1)).registerVariable("key", valueMock);
        verify(saplTestFixtureMock, times(1)).registerVariable("key2", value2Mock);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }
}
