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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.test.TestHelper;
import io.sapl.test.grammar.sapltest.Environment;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.MockDefinition;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.Scenario;
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

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sapltest.Expectation;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.TestException;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;

@ExtendWith(MockitoExtension.class)
class TestCaseTests {
    @Mock
    protected StepConstructor stepConstructorMock;
    @Mock
    protected Requirement     requirementMock;
    @Mock
    protected Scenario        scenarioMock;
    @Mock
    protected Given           requirementGivenMock;
    @Mock
    protected Given           scenarioGivenMock;

    protected final MockedStatic<org.assertj.core.api.Assertions> assertionsMockedStatic = mockStatic(
            org.assertj.core.api.Assertions.class, Answers.RETURNS_DEEP_STUBS);

    @AfterEach
    void tearDown() {
        assertionsMockedStatic.close();
    }

    private Environment mockEnvironment(final Given givenMock) {
        final var environment = mock(Environment.class);
        when(givenMock.getEnvironment()).thenReturn(environment);
        return environment;
    }

    private void mockGiven(final Given requirementGivenMock, final Given scenarioGivenMock) {
        when(requirementMock.getGiven()).thenReturn(requirementGivenMock);
        when(scenarioMock.getGiven()).thenReturn(scenarioGivenMock);
    }

    private SaplTestFixture mockTestFixture(final Given given, final List<GivenStep> givenSteps) {
        final var saplTestFixtureMock = mock(SaplTestFixture.class);
        when(stepConstructorMock.constructTestFixture(given, givenSteps)).thenReturn(saplTestFixtureMock);
        return saplTestFixtureMock;
    }

    private TestException mockTestCaseWithTestException(Scenario scenarioWithException) {
        final var testExceptionMock = mock(TestException.class);
        when(scenarioWithException.getExpectation()).thenReturn(testExceptionMock);

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
        return testExceptionMock;
    }

    private VerifyStep mockTestBuildingChain(final List<GivenStep> givenSteps, final SaplTestFixture testFixture,
            final Scenario scenarioMock, final Environment environment, final boolean needsMocks) {
        final var expectationMock = mock(Expectation.class);
        when(scenarioMock.getExpectation()).thenReturn(expectationMock);

        final var initialTestCaseMock = mock(GivenOrWhenStep.class);
        when(stepConstructorMock.constructTestCase(testFixture, environment, needsMocks))
                .thenReturn(initialTestCaseMock);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenSteps, initialTestCaseMock, expectationMock))
                .thenReturn(whenStepMock);

        final var expectStepMock = mock(ExpectStep.class);
        when(stepConstructorMock.constructExpectStep(scenarioMock, whenStepMock)).thenReturn(expectStepMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(stepConstructorMock.constructVerifyStep(scenarioMock, expectStepMock)).thenReturn(verifyStepMock);

        return verifyStepMock;
    }

    private Runnable assertDynamicTestAndGetRunnable() {
        when(scenarioMock.getName()).thenReturn("test1");

        final var result = io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, requirementMock, scenarioMock);

        assertEquals("test1", result.getIdentifier());

        return result;
    }

    @Test
    void from_withStepConstructorBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(null, requirementMock, scenarioMock));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withRequirementBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(stepConstructorMock, null, scenarioMock));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withScenarioBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(stepConstructorMock, requirementMock, null));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withStepConstructorAndRequirementAndScenarioBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(null, null, null));

        assertEquals("StepConstructor or testSuite or testCase is null", exception.getMessage());
    }

    @Test
    void from_withNullScenarioName_throwsSaplTestException() {
        when(scenarioMock.getName()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(stepConstructorMock, requirementMock, scenarioMock));

        assertEquals("Name of the test case is null", exception.getMessage());
    }

    @Test
    void from_withNullRequirementGivenAndScenarioGiven_throwsSaplTestException() {
        mockGiven(null, null);
        when(scenarioMock.getName()).thenReturn("scenario");

        final var exception = assertThrows(SaplTestException.class,
                () -> TestCase.from(stepConstructorMock, requirementMock, scenarioMock));

        assertEquals("Neither Requirement nor Scenario defines a GivenBlock", exception.getMessage());
    }

    @Test
    void from_withNullEnvironmentBuildsFixtureWithoutEnvironment_returnsTestCase() {
        mockGiven(null, scenarioGivenMock);
        when(scenarioGivenMock.getEnvironment()).thenReturn(null);

        final var givenSteps = TestHelper.mockEListResult(scenarioGivenMock::getGivenSteps, Collections.emptyList());

        final var testFixtureMock = mockTestFixture(scenarioGivenMock, givenSteps);

        final var verifyStepMock = mockTestBuildingChain(givenSteps, testFixtureMock, scenarioMock, null, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNoScenarioGivenAndEnvironmentBuildsFixtureWithEnvironment_returnsTestCase() {
        mockGiven(requirementGivenMock, null);
        final var environmentMock = mockEnvironment(requirementGivenMock);

        final var givenSteps = TestHelper.mockEListResult(requirementGivenMock::getGivenSteps, Collections.emptyList());

        final var testFixtureMock = mockTestFixture(requirementGivenMock, givenSteps);

        final var verifyStepMock = mockTestBuildingChain(givenSteps, testFixtureMock, scenarioMock, environmentMock,
                false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNullGivenStepsNeedsNoMocks_returnsTestCase() {
        mockGiven(null, scenarioGivenMock);
        final var environmentMock = mockEnvironment(scenarioGivenMock);

        when(scenarioGivenMock.getGivenSteps()).thenReturn(null);

        final var testFixtureMock = mockTestFixture(scenarioGivenMock, Collections.emptyList());

        final var verifyStepMock = mockTestBuildingChain(Collections.emptyList(), testFixtureMock, scenarioMock,
                environmentMock, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withEmptyGivenStepsNeedsNoMocks_returnsTestCase() {
        mockGiven(null, scenarioGivenMock);
        final var environmentMock = mockEnvironment(scenarioGivenMock);

        TestHelper.mockEListResult(scenarioGivenMock::getGivenSteps, Collections.emptyList());

        final var testFixtureMock = mockTestFixture(scenarioGivenMock, Collections.emptyList());

        final var verifyStepMock = mockTestBuildingChain(Collections.emptyList(), testFixtureMock, scenarioMock,
                environmentMock, false);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withGivenStepsContainingMockDefinitionNeedsMocks_returnsTestCase() {
        mockGiven(requirementGivenMock, scenarioGivenMock);

        final var environmentMock = mockEnvironment(scenarioGivenMock);

        final var requirementGivenStep = mock(MockDefinition.class);
        final var scenarioGivenStep    = mock(MockDefinition.class);

        TestHelper.mockEListResult(requirementGivenMock::getGivenSteps, List.of(requirementGivenStep));
        TestHelper.mockEListResult(scenarioGivenMock::getGivenSteps, List.of(scenarioGivenStep));

        final var givenSteps = List.<GivenStep>of(requirementGivenStep, scenarioGivenStep);

        final var testFixtureMock = mockTestFixture(scenarioGivenMock, givenSteps);

        final var verifyStepMock = mockTestBuildingChain(givenSteps, testFixtureMock, scenarioMock, environmentMock,
                true);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withExpectedTestException_returnsTestCase() {
        mockGiven(null, scenarioGivenMock);
        final var environmentMock = mockEnvironment(scenarioGivenMock);

        final var expectationMock = mock(Expectation.class);
        when(scenarioMock.getExpectation()).thenReturn(expectationMock);

        final var givenSteps = TestHelper.mockEListResult(scenarioGivenMock::getGivenSteps, Collections.emptyList());

        final var testFixtureMock = mockTestFixture(scenarioGivenMock, givenSteps);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(stepConstructorMock.constructTestCase(testFixtureMock, environmentMock, false))
                .thenReturn(givenOrWhenStepMock);

        final var expectation = mockTestCaseWithTestException(scenarioMock);

        assertDynamicTestAndGetRunnable().run();

        verify(stepConstructorMock, times(1)).constructWhenStep(givenSteps, givenOrWhenStepMock, expectation);
    }
}
