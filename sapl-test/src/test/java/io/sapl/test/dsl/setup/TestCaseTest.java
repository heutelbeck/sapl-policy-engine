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

package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sAPLTest.Expectation;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseTest {
    @Mock
    private StepConstructor                        stepConstructorMock;
    @Mock
    private TestSuite                              testSuiteMock;
    @Mock
    private io.sapl.test.grammar.sAPLTest.TestCase dslTestCaseMock;

    private final MockedStatic<org.assertj.core.api.Assertions> assertionsMockedStatic = mockStatic(
            org.assertj.core.api.Assertions.class, Answers.RETURNS_DEEP_STUBS);

    @AfterEach
    void tearDown() {
        assertionsMockedStatic.close();
    }

    private List<GivenStep> mockGivenSteps(final List<GivenStep> givenSteps) {
        return TestHelper.mockEListResult(dslTestCaseMock::getGivenSteps, givenSteps);

    }

    private List<FixtureRegistration> mockFixtureRegistrations(final List<FixtureRegistration> fixtureRegistrations) {
        return TestHelper.mockEListResult(dslTestCaseMock::getRegistrations, fixtureRegistrations);
    }

    private io.sapl.test.grammar.sAPLTest.Object mockEnvironment() {
        final var environment = mock(io.sapl.test.grammar.sAPLTest.Object.class);
        when(dslTestCaseMock.getEnvironment()).thenReturn(environment);
        return environment;
    }

    private SaplTestFixture mockTestFixture(final List<FixtureRegistration> fixtureRegistrations) {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        when(stepConstructorMock.constructTestFixture(eq(fixtureRegistrations), any(TestSuite.class)))
                .thenReturn(saplTestFixtureMock);
        return saplTestFixtureMock;
    }

    private void mockTestCaseWithTestException(io.sapl.test.grammar.sAPLTest.TestCase testCaseWithExceptionMock) {
        final var testExceptionMock = mock(TestException.class);
        when(testCaseWithExceptionMock.getExpectation()).thenReturn(testExceptionMock);

        assertionsMockedStatic
                .when(() -> Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(any()))
                .thenAnswer(invocationOnMock -> {
                    if (invocationOnMock.getArgument(0) instanceof ThrowableAssert.ThrowingCallable callable) {
                        callable.call();
                    } else {
                        fail("No proper ThrowingCallable was passed as an argument!");
                    }
                    return null;
                });
    }

    private VerifyStep mockTestBuildingChain(final List<GivenStep> givenSteps, final SaplTestFixture testFixture,
            final io.sapl.test.grammar.sAPLTest.TestCase testCase,
            final io.sapl.test.grammar.sAPLTest.Object environment, final boolean needsMocks) {
        final var expectationMock = mock(Expectation.class);
        when(testCase.getExpectation()).thenReturn(expectationMock);

        final var initialTestCaseMock = mock(GivenOrWhenStep.class);
        when(stepConstructorMock.constructTestCase(testFixture, environment, needsMocks))
                .thenReturn(initialTestCaseMock);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenSteps, initialTestCaseMock)).thenReturn(whenStepMock);

        final var expectStepMock = mock(ExpectStep.class);
        when(stepConstructorMock.constructExpectStep(testCase, whenStepMock)).thenReturn(expectStepMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(stepConstructorMock.constructVerifyStep(testCase, expectStepMock)).thenReturn(verifyStepMock);

        return verifyStepMock;
    }

    private Runnable assertDynamicTestAndGetRunnable() {
        when(dslTestCaseMock.getName()).thenReturn("test1");

        final var result = io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, testSuiteMock, dslTestCaseMock);

        assertEquals("test1", result.getIdentifier());

        return result;
    }

    @Test
    void from_withStepConstructorBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(null, testSuiteMock, dslTestCaseMock));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withTestSuiteBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(stepConstructorMock, null, dslTestCaseMock));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withTestCaseBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(stepConstructorMock, testSuiteMock, null));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withStepConstructorAndTestSuiteAndTestCaseBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(null, null, null));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withNullTestCaseName_throwsSaplTestException() {
        when(dslTestCaseMock.getName()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(stepConstructorMock, testSuiteMock, dslTestCaseMock));

        assertEquals("Name of the test case is null", exception.getMessage());
    }

    @Test
    void from_withNullEnvironmentBuildsFixtureWithoutEnvironment_returnsTestCase() {
        when(dslTestCaseMock.getEnvironment()).thenReturn(null);

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCaseMock, null, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNonObjectEnvironmentBuildsFixtureWithoutEnvironment_returnsTestCase() {
        final var valueMock = mock(Value.class);
        when(dslTestCaseMock.getEnvironment()).thenReturn(valueMock);

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock           = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCaseMock, null, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withEnvironmentBuildsFixtureWithEnvironment_returnsTestCase() {
        final var environmentMock          = mockEnvironment();
        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock           = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCaseMock,
                environmentMock, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNullFixtureRegistrations_returnsTestCase() {
        final var environmentMock = mockEnvironment();

        when(dslTestCaseMock.getRegistrations()).thenReturn(null);

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(null);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCaseMock,
                environmentMock, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNullGivenStepsNeedsNoMocks_returnsTestCase() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        when(dslTestCaseMock.getGivenSteps()).thenReturn(null);

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var verifyStepMock = mockTestBuildingChain(null, testFixtureMock, dslTestCaseMock, environmentMock,
                false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withGivenStepsNeedsMocks_returnsTestCase() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepMock  = mock(GivenStep.class);
        final var givenStepsMock = mockGivenSteps(List.of(givenStepMock));

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCaseMock,
                environmentMock, true);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withExpectedTestException_returnsTestCase() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(stepConstructorMock.constructTestCase(testFixtureMock, environmentMock, false))
                .thenReturn(givenOrWhenStepMock);

        mockTestCaseWithTestException(dslTestCaseMock);

        assertDynamicTestAndGetRunnable().run();

        verify(stepConstructorMock, times(1)).constructWhenStep(givenStepsMock, givenOrWhenStepMock);
    }
}
