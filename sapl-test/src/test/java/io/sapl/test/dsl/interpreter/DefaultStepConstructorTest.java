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
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultStepConstructorTest {
    @Mock
    private DefaultTestFixtureConstructor defaultTestFixtureConstructorMock;
    @Mock
    private DefaultTestCaseConstructor    defaultTestCaseConstructorMock;
    @Mock
    private DefaultWhenStepConstructor    defaultWhenStepConstructorMock;
    @Mock
    private DefaultExpectStepConstructor  defaultExpectStepConstructorMock;
    @Mock
    private DefaultVerifyStepConstructor  defaultVerifyStepConstructorMock;
    @InjectMocks
    private DefaultStepConstructor        defaultStepConstructor;

    @Test
    void constructTestFixture_callsTestFixtureConstructor_returnsSaplTestFixture() {
        final var fixtureRegistrations = Collections.<FixtureRegistration>emptyList();
        final var testSuiteMock        = mock(TestSuite.class);

        final var saplTestFixtureMock = mock(SaplTestFixture.class);

        when(defaultTestFixtureConstructorMock.constructTestFixture(fixtureRegistrations, testSuiteMock))
                .thenReturn(saplTestFixtureMock);

        final var result = defaultStepConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock);

        assertEquals(saplTestFixtureMock, result);
    }

    @Test
    void constructTestCase_callsTestCaseConstructorWithoutMocks_returnsGivenOrWhenStep() {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        final var environmentMock     = mock(io.sapl.test.grammar.sAPLTest.Object.class);
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestCaseConstructorMock.constructTestCase(saplTestFixtureMock, environmentMock, false))
                .thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.constructTestCase(saplTestFixtureMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void constructTestCase_callsTestCaseConstructorWithMocks_returnsGivenOrWhenStep() {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        final var environmentMock     = mock(io.sapl.test.grammar.sAPLTest.Object.class);
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestCaseConstructorMock.constructTestCase(saplTestFixtureMock, environmentMock, true))
                .thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.constructTestCase(saplTestFixtureMock, environmentMock, true);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void constructWhenStep_callsWhenStepConstructor_returnsWhenStep() {
        final var givenSteps          = Collections.<GivenStep>emptyList();
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        final var whenStepMock = mock(WhenStep.class);

        when(defaultWhenStepConstructorMock.constructWhenStep(givenSteps, givenOrWhenStepMock))
                .thenReturn(whenStepMock);

        final var result = defaultStepConstructor.constructWhenStep(givenSteps, givenOrWhenStepMock);

        assertEquals(whenStepMock, result);
    }

    @Test
    void constructExpectStep_callsExpectStepConstructor_returnsExpectStep() {
        final var testCaseMock = mock(TestCase.class);
        final var whenStepMock = mock(WhenStep.class);

        final var expectStepMock = mock(ExpectStep.class);

        when(defaultExpectStepConstructorMock.constructExpectStep(testCaseMock, whenStepMock))
                .thenReturn(expectStepMock);

        final var result = defaultStepConstructor.constructExpectStep(testCaseMock, whenStepMock);

        assertEquals(expectStepMock, result);
    }

    @Test
    void constructVerifyStep_callsVerifyStepConstructor_returnsVerifyStep() {
        final var testCaseMock           = mock(TestCase.class);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);

        when(defaultVerifyStepConstructorMock.constructVerifyStep(testCaseMock, expectOrVerifyStepMock))
                .thenReturn(verifyStepMock);

        final var result = defaultStepConstructor.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }
}
