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

import io.sapl.test.TestHelper;
import io.sapl.test.grammar.sapltest.AdjustBlock;
import io.sapl.test.grammar.sapltest.AdjustStep;
import io.sapl.test.grammar.sapltest.AttributeAdjustment;
import io.sapl.test.grammar.sapltest.Await;
import io.sapl.test.grammar.sapltest.ExpectBlock;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.Import;
import io.sapl.test.grammar.sapltest.MockDefinition;
import io.sapl.test.grammar.sapltest.RepeatedExpect;
import io.sapl.test.grammar.sapltest.SingleExpect;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    protected GivenOrWhenStep            initialTestCaseMock;
    @Mock
    protected Expectation                expectationMock;
    @InjectMocks
    protected DefaultWhenStepConstructor defaultWhenStepConstructor;

    protected <T extends GivenStep> T buildGivenStep(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getGivenStepRule, clazz);
    }

    @Test
    void constructWhenStep_handlesNullGivenSteps_returnsInitialTestCase() {
        final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

        assertEquals(initialTestCaseMock, result);
        verifyNoInteractions(initialTestCaseMock);
    }

    @Test
    void constructWhenStep_handlesEmptyGivenSteps_returnsInitialTestCase() {
        final var result = defaultWhenStepConstructor.constructWhenStep(Collections.emptyList(), initialTestCaseMock,
                expectationMock);

        assertEquals(initialTestCaseMock, result);
        verifyNoInteractions(initialTestCaseMock);
    }

    @Test
    void constructWhenStep_filtersNonMockDefinitions_returnsInitialTestCase() {
        final var importMock = mock(Import.class);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(importMock, importMock),
                initialTestCaseMock, expectationMock);

        assertEquals(initialTestCaseMock, result);
        verifyNoInteractions(initialTestCaseMock);
    }

    @Test
    void constructWhenStep_handlesMoreThanOneVirtualTimeDeclaration_throwsSaplTestException() {
        final var virtualTimeMock = mock(VirtualTime.class);

        final var givenSteps = List.<GivenStep>of(virtualTimeMock, virtualTimeMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultWhenStepConstructor.constructWhenStep(givenSteps, initialTestCaseMock, expectationMock));

        assertEquals("Scenario contains more than one virtual-time declaration", exception.getMessage());
    }

    @Test
    void constructWhenStep_handlesUnknownTypeOfGivenStep_throwsSaplTestException() {
        final var unknownMockDefinition = mock(MockDefinition.class);

        final var givenSteps = List.<GivenStep>of(unknownMockDefinition);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultWhenStepConstructor.constructWhenStep(givenSteps, initialTestCaseMock, expectationMock));

        assertEquals("Unknown type of GivenStep", exception.getMessage());
    }

    @Test
    void constructWhenStep_handlesFunctionGivenStep_returnsAdjustedTestCase() {
        final var function = buildGivenStep("function \"foo\" maps to \"bar\"", Function.class);

        when(functionInterpreterMock.interpretFunction(initialTestCaseMock, function)).thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(function), initialTestCaseMock,
                expectationMock);

        assertEquals(initialTestCaseMock, result);
    }

    @Test
    void constructWhenStep_handlesFunctionInvokedOnceGivenStep_returnsAdjustedTestCase() {
        final var functionInvokedOnce = buildGivenStep("function \"foo\" maps to stream \"bar\"",
                FunctionInvokedOnce.class);

        when(functionInterpreterMock.interpretFunctionInvokedOnce(initialTestCaseMock, functionInvokedOnce))
                .thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(functionInvokedOnce),
                initialTestCaseMock, expectationMock);

        assertEquals(initialTestCaseMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeGivenStep_returnsAdjustedTestCase() {
        final var attribute = buildGivenStep("attribute \"foo\" emits \"bar\"", Attribute.class);

        when(attributeInterpreterMock.interpretAttribute(initialTestCaseMock, attribute))
                .thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(attribute), initialTestCaseMock,
                expectationMock);

        assertEquals(initialTestCaseMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeWithParametersGivenStep_returnsAdjustedTestCase() {
        final var attributeWithParameters = buildGivenStep("attribute \"foo\" of <any> emits \"bar\"",
                AttributeWithParameters.class);

        when(attributeInterpreterMock.interpretAttributeWithParameters(initialTestCaseMock, attributeWithParameters))
                .thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(attributeWithParameters),
                initialTestCaseMock, expectationMock);

        assertEquals(initialTestCaseMock, result);
    }

    @Test
    void constructWhenStep_handlesVirtualTimeGivenStep_returnsAdjustedTestCase() {
        final var virtualTime = buildGivenStep("virtual-time", VirtualTime.class);

        when(initialTestCaseMock.withVirtualTime()).thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(virtualTime), initialTestCaseMock,
                expectationMock);

        assertEquals(initialTestCaseMock, result);
    }

    @Test
    void constructWhenStep_handlesMultipleGivenSteps_returnsAdjustedTestCase() {
        final var function    = buildGivenStep("function \"foo\" maps to \"bar\"", Function.class);
        final var attribute   = buildGivenStep("attribute \"foo\" emits \"bar\"", Attribute.class);
        final var virtualTime = buildGivenStep("virtual-time", VirtualTime.class);

        when(functionInterpreterMock.interpretFunction(initialTestCaseMock, function)).thenReturn(initialTestCaseMock);
        when(attributeInterpreterMock.interpretAttribute(initialTestCaseMock, attribute))
                .thenReturn(initialTestCaseMock);
        when(initialTestCaseMock.withVirtualTime()).thenReturn(initialTestCaseMock);

        final var result = defaultWhenStepConstructor.constructWhenStep(List.of(virtualTime, function, attribute),
                initialTestCaseMock, expectationMock);

        assertEquals(initialTestCaseMock, result);
        verify(functionInterpreterMock, times(1)).interpretFunction(initialTestCaseMock, function);
        verify(attributeInterpreterMock, times(1)).interpretAttribute(initialTestCaseMock, attribute);
        verify(initialTestCaseMock, times(1)).withVirtualTime();
        verifyNoMoreInteractions(initialTestCaseMock, functionInterpreterMock, attributeInterpreterMock);
    }

    @Nested
    @DisplayName("Apply required attribute mocking")
    class ApplyRequiredAttributeMockingTests {
        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationIsNull() {
            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, null);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationIsNotRepeatedExpect() {
            final var expectationMock = mock(SingleExpect.class);
            final var result          = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock,
                    expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationIsUnknownExpectation() {
            final var expectationMock = mock(Expectation.class);
            final var result          = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock,
                    expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationHasNullBlocks() {
            final var expectationMock = mock(RepeatedExpect.class);

            when(expectationMock.getExpectOrAdjustBlocks()).thenReturn(null);

            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationHasOnlyNullAndExpectBlocks() {
            final var expectationMock = mock(RepeatedExpect.class);

            final var expectBlock1Mock = mock(ExpectBlock.class);
            final var expectBlock2Mock = mock(ExpectBlock.class);

            TestHelper.mockEListResult(expectationMock::getExpectOrAdjustBlocks,
                    Arrays.asList(expectBlock1Mock, null, expectBlock2Mock));

            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationHasOnlyNullAndInvalidAdjustBlocks() {
            final var expectationMock = mock(RepeatedExpect.class);

            final var adjustBlock1Mock = mock(AdjustBlock.class);
            final var adjustBlock2Mock = mock(AdjustBlock.class);

            TestHelper.mockEListResult(expectationMock::getExpectOrAdjustBlocks,
                    Arrays.asList(adjustBlock1Mock, null, adjustBlock2Mock));

            when(adjustBlock1Mock.getAdjustSteps()).thenReturn(null);
            TestHelper.mockEListResult(adjustBlock2Mock::getAdjustSteps, Collections.emptyList());

            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationHasOnlyNullAndAdjustBlocksWithNonRelevantAdjustSteps() {
            final var expectationMock = mock(RepeatedExpect.class);

            final var adjustBlock1Mock = mock(AdjustBlock.class);
            final var adjustBlock2Mock = mock(AdjustBlock.class);

            TestHelper.mockEListResult(expectationMock::getExpectOrAdjustBlocks,
                    Arrays.asList(adjustBlock1Mock, null, adjustBlock2Mock));

            when(adjustBlock1Mock.getAdjustSteps()).thenReturn(null);

            final var unknownAdjustStep = mock(AdjustStep.class);
            final var awaitMock         = mock(Await.class);

            TestHelper.mockEListResult(adjustBlock2Mock::getAdjustSteps, List.of(unknownAdjustStep, awaitMock));

            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

            assertEquals(initialTestCaseMock, result);
            verifyNoInteractions(initialTestCaseMock);
        }

        @Test
        void constructWhenStep_doesNothingWhenGivenStepsIsNullAndExpectationHasNullAndAdjustBlocksWithMixedAdjustStepsAndDuplicates() {
            final var expectationMock = mock(RepeatedExpect.class);

            final var adjustBlock1Mock = mock(AdjustBlock.class);
            final var adjustBlock2Mock = mock(AdjustBlock.class);
            final var adjustBlock3Mock = mock(AdjustBlock.class);

            TestHelper.mockEListResult(expectationMock::getExpectOrAdjustBlocks,
                    Arrays.asList(adjustBlock1Mock, adjustBlock2Mock, null, adjustBlock3Mock));

            when(adjustBlock1Mock.getAdjustSteps()).thenReturn(null);

            final var unknownAdjustStepMock    = mock(AdjustStep.class);
            final var awaitMock                = mock(Await.class);
            final var attributeAdjustment1Mock = mock(AttributeAdjustment.class);
            final var attributeAdjustment2Mock = mock(AttributeAdjustment.class);

            when(attributeAdjustment1Mock.getAttribute()).thenReturn("foo");
            when(attributeAdjustment2Mock.getAttribute()).thenReturn("foo");

            TestHelper.mockEListResult(adjustBlock2Mock::getAdjustSteps,
                    List.of(unknownAdjustStepMock, awaitMock, attributeAdjustment1Mock));
            TestHelper.mockEListResult(adjustBlock3Mock::getAdjustSteps,
                    Arrays.asList(null, attributeAdjustment2Mock, unknownAdjustStepMock));

            final var result = defaultWhenStepConstructor.constructWhenStep(null, initialTestCaseMock, expectationMock);

            assertEquals(initialTestCaseMock, result);
            verify(initialTestCaseMock, times(1)).givenAttribute("foo");
        }
    }
}
