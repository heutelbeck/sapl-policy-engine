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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.RepeatedExpect;
import io.sapl.test.grammar.sapltest.SingleExpect;
import io.sapl.test.grammar.sapltest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sapltest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.VerifyStep;

@ExtendWith(MockitoExtension.class)
class DefaultVerifyStepConstructorTests {
    @Mock
    protected ExpectationInterpreter       expectInterpreterMock;
    @InjectMocks
    protected DefaultVerifyStepConstructor defaultVerifyStepConstructor;
    @Mock
    protected TestCase                     testCaseMock;
    @Mock
    protected ExpectStep                   expectStepMock;

    @Test
    void constructVerifyStep_handlesNullTestCase_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(null, expectStepMock));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, null));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullTestCaseAndNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(null, null));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesUnknownExpectation_throwsSaplTestException() {
        final var expectationMock = mock(Expectation.class);

        when(testCaseMock.getExpectation()).thenReturn(expectationMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, expectStepMock));

        assertEquals("Unknown type of Expectation", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpectation_throwsSaplTestException() {
        when(testCaseMock.getExpectation()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, expectStepMock));

        assertEquals("Unknown type of Expectation", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpect_returnsVerifyStep() {
        final var singleExpectMock = mock(SingleExpect.class);

        when(testCaseMock.getExpectation()).thenReturn(singleExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpect(expectStepMock, singleExpectMock)).thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpectWithMatcher_returnsVerifyStep() {
        final var singleExpectWithMatcher = mock(SingleExpectWithMatcher.class);

        when(testCaseMock.getExpectation()).thenReturn(singleExpectWithMatcher);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpectWithMatcher(expectStepMock, singleExpectWithMatcher))
                .thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsRepeatedExpect_returnsVerifyStep() {
        final var repeatedExpectMock = mock(RepeatedExpect.class);

        when(testCaseMock.getExpectation()).thenReturn(repeatedExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretRepeatedExpect(expectStepMock, repeatedExpectMock))
                .thenReturn(verifyStepMock);

        final var result = defaultVerifyStepConstructor.constructVerifyStep(testCaseMock, expectStepMock);

        assertEquals(verifyStepMock, result);
    }
}
