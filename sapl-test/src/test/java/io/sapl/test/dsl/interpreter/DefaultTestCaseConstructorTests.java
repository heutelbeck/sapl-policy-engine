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

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.steps.GivenOrWhenStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultTestCaseConstructorTests {

    @Mock
    protected ValueInterpreter valueInterpreterMock;

    @InjectMocks
    protected DefaultTestCaseConstructor defaultTestCaseConstructor;

    @Mock
    protected SaplTestFixture saplTestFixtureMock;

    @Mock
    protected GivenOrWhenStep givenOrWhenStepMock;

    protected Environment buildEnvironment(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getEnvironmentRule, Environment.class);
    }

    @Test
    void constructTestCase_handlesNullEnvironmentWithoutMocks_returnsGivenOrWhenStep() {
        when(saplTestFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, false);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesNullEnvironmentWithMocks_returnsGivenOrWhenStep() {
        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, null, true);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesEnvironmentWithWrongType_returnsGivenOrWhenStep() {
        final var environmentMock = mock(Environment.class);
        when(environmentMock.getEnvironment()).thenReturn(ParserUtil.buildStringLiteral("\"ABC\""));

        when(saplTestFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesEmptyEnvironmentVariables_returnsGivenOrWhenStep() {
        final var environment = buildEnvironment("environment {}");

        when(valueInterpreterMock.destructureObject(any())).thenAnswer(invocationOnMock -> {
            final io.sapl.test.grammar.sapltest.Object object = invocationOnMock.getArgument(0);

            assertEquals(0, object.getMembers().size());
            return Collections.emptyMap();
        });

        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, environment, true);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesNullEnvironmentVariables_returnsGivenOrWhenStep() {
        final var environment = buildEnvironment("environment {}");

        when(valueInterpreterMock.destructureObject(any())).thenReturn(null);

        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, environment, true);

        assertEquals(givenOrWhenStepMock, result);

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesSingleEnvironmentVariable_returnsGivenOrWhenStep() {
        final var environment = buildEnvironment("environment { \"key\": \"value\" }");

        final var expectedJsonNode = Val.of("value").get();
        when(valueInterpreterMock.destructureObject(any())).thenAnswer(invocationOnMock -> {
            final io.sapl.test.grammar.sapltest.Object object = invocationOnMock.getArgument(0);

            assertEquals(1, object.getMembers().size());

            final var pair = object.getMembers().get(0);
            assertEquals("key", pair.getKey());
            assertEquals("value", ((StringLiteral) pair.getValue()).getString());

            return Map.of("key", expectedJsonNode);
        });

        when(saplTestFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, environment, true);

        assertEquals(givenOrWhenStepMock, result);

        verify(saplTestFixtureMock, times(1)).registerVariable("key", Val.of(expectedJsonNode));

        verifyNoMoreInteractions(saplTestFixtureMock);
    }

    @Test
    void constructTestCase_handlesNestedEnvironmentVariables_returnsGivenOrWhenStep() {
        final var environment = buildEnvironment(
                "environment { \"key\": \"value\", \"foo\": { \"key2\": \"value2\" } }");

        final var expectedJsonNode  = Val.of("value").get();
        final var expectedJsonNode2 = Val.of("value2").get();
        when(valueInterpreterMock.destructureObject(any())).thenAnswer(invocationOnMock -> {
            final io.sapl.test.grammar.sapltest.Object object = invocationOnMock.getArgument(0);

            assertEquals(2, object.getMembers().size());

            final var flatPair = object.getMembers().get(0);
            assertEquals("key", flatPair.getKey());
            assertEquals("value", ((StringLiteral) flatPair.getValue()).getString());

            final var objectPair = object.getMembers().get(1);
            assertEquals("foo", objectPair.getKey());

            final var nestedMembers = ((io.sapl.test.grammar.sapltest.Object) objectPair.getValue()).getMembers();

            assertEquals(1, nestedMembers.size());

            final var nestedPair = nestedMembers.get(0);

            assertEquals("key2", nestedPair.getKey());
            assertEquals("value2", ((StringLiteral) nestedPair.getValue()).getString());

            return Map.of("key", expectedJsonNode, "key2", expectedJsonNode2);
        });

        when(saplTestFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestCaseConstructor.constructTestCase(saplTestFixtureMock, environment, false);

        assertEquals(givenOrWhenStepMock, result);

        verify(saplTestFixtureMock, times(1)).registerVariable("key", Val.of(expectedJsonNode));
        verify(saplTestFixtureMock, times(1)).registerVariable("key2", Val.of(expectedJsonNode2));

        verifyNoMoreInteractions(saplTestFixtureMock);
    }
}
