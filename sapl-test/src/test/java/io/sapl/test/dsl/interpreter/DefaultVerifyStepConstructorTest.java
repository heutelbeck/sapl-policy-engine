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

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultVerifyStepConstructorTest {
    @Mock
    private ExpectInterpreter            expectInterpreterMock;
    @InjectMocks
    private DefaultVerifyStepConstructor verifyStepBuilderServiceDefault;
    @Mock
    TestCase                             testCaseMock;
    @Mock
    ExpectOrVerifyStep                   expectOrVerifyStepMock;

    @Test
    void constructVerifyStep_handlesNullTestCase_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> verifyStepBuilderServiceDefault.constructVerifyStep(null, expectOrVerifyStepMock));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, null));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullTestCaseAndNullExpectOrVerifyStep_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> verifyStepBuilderServiceDefault.constructVerifyStep(null, null));

        assertEquals("TestCase or expectStep is null", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesUnknownExpect_throwsSaplTestException() {
        final var expectChainMock = mock(ExpectChain.class);

        when(testCaseMock.getExpect()).thenReturn(expectChainMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("Unknown type of ExpectChain", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_handlesNullExpect_throwsSaplTestException() {
        when(testCaseMock.getExpect()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("Unknown type of ExpectChain", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpect_returnsVerifyStep() {
        final var singleExpectMock = mock(SingleExpect.class);

        when(testCaseMock.getExpect()).thenReturn(singleExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpect(expectOrVerifyStepMock, singleExpectMock))
                .thenReturn(verifyStepMock);

        final var result = verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpectWithMatcher_returnsVerifyStep() {
        final var singleExpectWithMatcher = mock(SingleExpectWithMatcher.class);

        when(testCaseMock.getExpect()).thenReturn(singleExpectWithMatcher);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpectWithMatcher(expectOrVerifyStepMock, singleExpectWithMatcher))
                .thenReturn(verifyStepMock);

        final var result = verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsRepeatedExpect_returnsVerifyStep() {
        final var repeatedExpectMock = mock(RepeatedExpect.class);

        when(testCaseMock.getExpect()).thenReturn(repeatedExpectMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock))
                .thenReturn(verifyStepMock);

        final var result = verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }
}
