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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.Attribute;
import io.sapl.test.grammar.sapltest.AttributeWithParameters;
import io.sapl.test.grammar.sapltest.Function;
import io.sapl.test.grammar.sapltest.FunctionInvokedOnce;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.VirtualTime;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.steps.GivenOrWhenStep;

@ExtendWith(MockitoExtension.class)
class DefaultWhenStepConstructorTests {
    @Mock
    protected FunctionInterpreter        functionInterpreterMock;
    @Mock
    protected AttributeInterpreter       attributeInterpreterMock;
    @Mock
    protected GivenOrWhenStep            saplUnitTestFixtureMock;
    @InjectMocks
    protected DefaultWhenStepConstructor defaultWhenStepConstructor;

    protected <T extends GivenStep> T buildGivenStep(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getGivenStepRule, clazz);
    }

    @Test
    void constructWhenStep_handlesNullGivenSteps_returnsGivenUnitTestFixture() {
        final var result = defaultWhenStepConstructor.constructWhenStep(null, saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verifyNoInteractions(saplUnitTestFixtureMock);
    }

    @Test
    void constructWhenStep_handlesEmptyGivenSteps_returnsGivenUnitTestFixture() {
        final var result = defaultWhenStepConstructor.constructWhenStep(Collections.emptyList(),
                saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verifyNoInteractions(saplUnitTestFixtureMock);
    }

    @Test
    void constructWhenStep_handlesUnknownTypeOfGivenStep_throwsSaplTestException() {
        final var unknownGivenStepMock = mock(GivenStep.class);

        final var givenSteps = List.of(unknownGivenStepMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultWhenStepConstructor.constructWhenStep(givenSteps, saplUnitTestFixtureMock));

        assertEquals("Unknown type of GivenStep", exception.getMessage());
    }

    @Test
    void constructWhenStep_handlesFunctionGivenStep_returnsAdjustedUnitTestFixture() {
        final var function = buildGivenStep("function \"foo\" returns \"bar\"", Function.class);

        when(functionInterpreterMock.interpretFunction(saplUnitTestFixtureMock, function))
                .thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(function), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesFunctionInvokedOnceGivenStep_returnsAdjustedUnitTestFixture() {
        final var functionInvokedOnce = buildGivenStep("function \"foo\" returns stream \"bar\"",
                FunctionInvokedOnce.class);

        when(functionInterpreterMock.interpretFunctionInvokedOnce(saplUnitTestFixtureMock, functionInvokedOnce))
                .thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(functionInvokedOnce),
                saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeGivenStep_returnsAdjustedUnitTestFixture() {
        final var attribute = buildGivenStep("attribute \"foo\"", Attribute.class);

        when(attributeInterpreterMock.interpretAttribute(saplUnitTestFixtureMock, attribute))
                .thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(attribute), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeWithParametersGivenStep_returnsAdjustedUnitTestFixture() {
        final var attributeWithParameters = buildGivenStep("attribute \"foo\" with parent value any returns \"bar\"",
                AttributeWithParameters.class);

        when(attributeInterpreterMock.interpretAttributeWithParameters(saplUnitTestFixtureMock,
                attributeWithParameters)).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(attributeWithParameters),
                saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesVirtualTimeGivenStep_returnsAdjustedUnitTestFixture() {
        final var virtualTime = buildGivenStep("virtual-time", VirtualTime.class);

        when(saplUnitTestFixtureMock.withVirtualTime()).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(virtualTime), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesMultipleGivenSteps_returnsAdjustedUnitTestFixture() {
        final var function    = buildGivenStep("function \"foo\" returns \"bar\"", Function.class);
        final var attribute   = buildGivenStep("attribute \"foo\"", Attribute.class);
        final var virtualTime = buildGivenStep("virtual-time", VirtualTime.class);

        when(functionInterpreterMock.interpretFunction(saplUnitTestFixtureMock, function))
                .thenReturn(saplUnitTestFixtureMock);
        when(attributeInterpreterMock.interpretAttribute(saplUnitTestFixtureMock, attribute))
                .thenReturn(saplUnitTestFixtureMock);
        when(saplUnitTestFixtureMock.withVirtualTime()).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(virtualTime, function, attribute),
                saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verify(functionInterpreterMock, times(1)).interpretFunction(saplUnitTestFixtureMock, function);
        verify(attributeInterpreterMock, times(1)).interpretAttribute(saplUnitTestFixtureMock, attribute);
        verify(saplUnitTestFixtureMock, times(1)).withVirtualTime();
        verifyNoMoreInteractions(saplUnitTestFixtureMock, functionInterpreterMock, attributeInterpreterMock);
    }
}
