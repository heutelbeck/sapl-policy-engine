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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.grammar.sapltest.Requirement;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.Scenario;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestProviderTests {
    @InjectMocks
    protected TestProvider    testProvider;
    @Mock
    protected StepConstructor stepConstructorMock;
    @Mock
    protected SAPLTest        saplTestMock;

    protected final MockedStatic<TestContainer>                   testContainerMockedStatic = mockStatic(
            TestContainer.class);
    protected final MockedStatic<io.sapl.test.dsl.setup.TestCase> testMockedStatic          = mockStatic(
            io.sapl.test.dsl.setup.TestCase.class);

    @AfterEach
    void tearDown() {
        testContainerMockedStatic.close();
        testMockedStatic.close();
    }

    @Nested
    @DisplayName("Early return cases")
    class EarlyReturnCasesTests {
        @Mock
        protected Requirement requirementMock;

        @Test
        void buildTests_calledWithNullSAPLTest_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(null, null));

            assertEquals("provided SAPLTest is null", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullRequirements_throwsSaplTestException() {
            when(saplTestMock.getRequirements()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("provided SAPLTest does not contain a Requirement", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyRequirements_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getRequirements, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("provided SAPLTest does not contain a Requirement", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithRequirementsWithDuplicateName_throwsSaplTestException() {
            final var requirement1Mock = mock(Requirement.class);
            final var requirement2Mock = mock(Requirement.class);

            when(requirement1Mock.getName()).thenReturn("nonUniqueName");
            when(requirement2Mock.getName()).thenReturn("nonUniqueName");

            TestHelper.mockEListResult(saplTestMock::getRequirements, List.of(requirement1Mock, requirement2Mock));

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("Requirement name needs to be unique", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullScenarios_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getRequirements, List.of(requirementMock));

            when(requirementMock.getScenarios()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("provided Requirement does not contain a Scenario", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyScenarios_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getRequirements, List.of(requirementMock));
            TestHelper.mockEListResult(requirementMock::getScenarios, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("provided Requirement does not contain a Scenario", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithScenariosWithDuplicateName_throwsSaplTestException() {
            final var scenario1Mock = mock(Scenario.class);
            final var scenario2Mock = mock(Scenario.class);

            when(scenario1Mock.getName()).thenReturn("nonUniqueName");
            when(scenario2Mock.getName()).thenReturn("nonUniqueName");

            TestHelper.mockEListResult(saplTestMock::getRequirements, List.of(requirementMock));
            TestHelper.mockEListResult(requirementMock::getScenarios, List.of(scenario1Mock, scenario2Mock));

            final var exception = assertThrows(SaplTestException.class,
                    () -> testProvider.buildTests(saplTestMock, null));

            assertEquals("Scenario name needs to be unique within one Requirement", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Container name construction")
    class ContainerNameConstructionTests {
        private List<TestNode> mockTestContainerForName(final String name, final TestContainer dynamicContainer) {
            List<TestNode> dynamicTestCases = new ArrayList<>();
            // Required since Spotbugs complains about unused return value from method call
            // with no side effects here
            final Callable<TestContainer> testContainerCallable = () -> TestContainer.from(eq(name), anyList());
            testContainerMockedStatic.when(testContainerCallable::call).thenAnswer(invocationOnMock -> {
                final List<TestNode> dynamicTests = invocationOnMock.getArgument(1);
                dynamicTestCases.addAll(dynamicTests);
                return dynamicContainer;
            });
            return dynamicTestCases;
        }

        private TestCase mockTestCaseCreation(final Requirement requirement, final Scenario scenario) {
            final var testCaseMock = mock(TestCase.class);
            testMockedStatic.when(() -> TestCase.from(stepConstructorMock, requirement, scenario, null))
                    .thenReturn(testCaseMock);
            return testCaseMock;
        }

        @Test
        void buildTests_usingMultipleRequirementsWithMultipleScenarios_returnsDynamicContainer() {
            final var requirement1Mock = mock(Requirement.class);
            final var requirement2Mock = mock(Requirement.class);

            final var scenario1Mock = mock(Scenario.class);
            final var scenario2Mock = mock(Scenario.class);

            when(scenario1Mock.getName()).thenReturn("scenario1");
            when(scenario2Mock.getName()).thenReturn("scenario2");

            final var scenario3Mock = mock(Scenario.class);
            final var scenario4Mock = mock(Scenario.class);

            // duplicate names are intended here to test the name duplication in 2 different
            // requirements is allowed
            when(scenario3Mock.getName()).thenReturn("scenario1");
            when(scenario4Mock.getName()).thenReturn("scenario2");

            TestHelper.mockEListResult(saplTestMock::getRequirements, List.of(requirement1Mock, requirement2Mock));
            TestHelper.mockEListResult(requirement1Mock::getScenarios, List.of(scenario1Mock, scenario2Mock));
            TestHelper.mockEListResult(requirement2Mock::getScenarios, List.of(scenario3Mock, scenario4Mock));

            when(requirement1Mock.getName()).thenReturn("requirement1");
            when(requirement2Mock.getName()).thenReturn("requirement2");

            final var requirement1TestContainer = mock(TestContainer.class);
            final var requirement2TestContainer = mock(TestContainer.class);

            final var requirement1TestNodes = mockTestContainerForName("requirement1", requirement1TestContainer);
            final var requirement2TestNodes = mockTestContainerForName("requirement2", requirement2TestContainer);

            final var scenario1TestCase = mockTestCaseCreation(requirement1Mock, scenario1Mock);
            final var scenario2TestCase = mockTestCaseCreation(requirement1Mock, scenario2Mock);

            final var scenario3TestCase = mockTestCaseCreation(requirement2Mock, scenario3Mock);
            final var scenario4TestCase = mockTestCaseCreation(requirement2Mock, scenario4Mock);

            final var result = testProvider.buildTests(saplTestMock, null);

            assertEquals(requirement1TestContainer, result.get(0));
            assertEquals(requirement2TestContainer, result.get(1));

            assertEquals(requirement1TestNodes, List.of(scenario1TestCase, scenario2TestCase));
            assertEquals(requirement2TestNodes, List.of(scenario3TestCase, scenario4TestCase));
        }
    }

    @Test
    void of_buildsInstanceOfTestProviderUsingStepConstructor_returnsTestProviderInstance() {
        final var aStepConstructorMock = mock(StepConstructor.class);
        final var result               = TestProvider.of(aStepConstructorMock);
        assertNotNull(result);
    }
}
