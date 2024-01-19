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
import io.sapl.test.grammar.sapltest.IntegrationTestSuite;
import io.sapl.test.grammar.sapltest.PoliciesByIdentifier;
import io.sapl.test.grammar.sapltest.PoliciesByInputString;
import io.sapl.test.grammar.sapltest.PolicyResolverConfig;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.grammar.sapltest.TestCase;
import io.sapl.test.grammar.sapltest.TestSuite;
import io.sapl.test.grammar.sapltest.UnitTestSuite;
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
    @Mock
    private StepConstructor stepConstructorMock;
    @InjectMocks
    private TestProvider    testProvider;
    @Mock
    private SAPLTest        saplTestMock;
    @Mock
    private UnitTestSuite   unitTestSuiteMock;
    @Mock
    private TestCase        testCaseMock;

    private final MockedStatic<TestContainer>                   testContainerMockedStatic = mockStatic(
            TestContainer.class);
    private final MockedStatic<io.sapl.test.dsl.setup.TestCase> testMockedStatic          = mockStatic(
            io.sapl.test.dsl.setup.TestCase.class);

    @AfterEach
    void tearDown() {
        testContainerMockedStatic.close();
        testMockedStatic.close();
    }

    @Nested
    @DisplayName("Early return cases")
    class EarlyReturnCasesTests {
        @Test
        void buildTests_calledWithNullSAPLTest_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(null));

            assertEquals("provided SAPLTest is null", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestSuites_throwsSaplTestException() {
            when(saplTestMock.getTestSuites()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestSuites_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getTestSuites, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestCases_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(unitTestSuiteMock));

            when(unitTestSuiteMock.getTestCases()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestCases_throwsSaplTestException() {
            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(unitTestSuiteMock));
            TestHelper.mockEListResult(unitTestSuiteMock::getTestCases, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
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

        @Test
        void buildTests_handlesUnknownTestSuite_throwsSaplTestException() {
            final var unknownTestSuiteMock = mock(TestSuite.class);

            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(unknownTestSuiteMock));
            TestHelper.mockEListResult(unknownTestSuiteMock::getTestCases, List.of(testCaseMock));

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("Unknown type of TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_usingUnitTestSuite_returnsDynamicContainerWithPolicyName() {
            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(unitTestSuiteMock));
            TestHelper.mockEListResult(unitTestSuiteMock::getTestCases, List.of(testCaseMock));

            when(unitTestSuiteMock.getPolicyName()).thenReturn("policyName");

            final var unitTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes                  = mockTestContainerForName("policyName", unitTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(
                    () -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock, testCaseMock))
                    .thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestMock);

            assertEquals(unitTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithUnknownTypeOfPolicyResolverConfig_throwsSaplTestException() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(integrationTestSuite));
            TestHelper.mockEListResult(integrationTestSuite::getTestCases, List.of(testCaseMock));

            final var unknownPolicyResolverConfigMock = mock(PolicyResolverConfig.class);
            when(integrationTestSuite.getConfiguration()).thenReturn(unknownPolicyResolverConfigMock);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestMock));

            assertEquals("Unknown type of PolicyResolverConfig", exception.getMessage());
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicyFolder_returnsDynamicContainerWithPolicyFolderName() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(integrationTestSuite));
            TestHelper.mockEListResult(integrationTestSuite::getTestCases, List.of(testCaseMock));

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(integrationTestSuite.getConfiguration()).thenReturn(policiesByIdentifierMock);

            when(policiesByIdentifierMock.getIdentifier()).thenReturn("policyFolder");

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes                         = mockTestContainerForName("policyFolder",
                    integrationTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(
                    () -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, testCaseMock))
                    .thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestMock);

            assertEquals(integrationTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicySet_returnsDynamicContainerWithJoinedPolicyNames() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(integrationTestSuite));
            TestHelper.mockEListResult(integrationTestSuite::getTestCases, List.of(testCaseMock));

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(integrationTestSuite.getConfiguration()).thenReturn(policiesByInputStringMock);

            TestHelper.mockEListResult(policiesByInputStringMock::getPolicies,
                    List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes                         = mockTestContainerForName(
                    "name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(
                    () -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, testCaseMock))
                    .thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestMock);

            assertEquals(integrationTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingUnitAndIntegrationTestSuitesWithMultipleTestCasesEach_returnsDynamicContainers() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            final var unitTestSuiteMock    = mock(UnitTestSuite.class);

            TestHelper.mockEListResult(saplTestMock::getTestSuites, List.of(integrationTestSuite, unitTestSuiteMock));

            final var integrationTestCase1Mock = mock(TestCase.class);
            final var integrationTestCase2Mock = mock(TestCase.class);

            TestHelper.mockEListResult(integrationTestSuite::getTestCases,
                    List.of(integrationTestCase1Mock, integrationTestCase2Mock));

            final var unitTestCase1Mock = mock(TestCase.class);
            final var unitTestCase2Mock = mock(TestCase.class);
            final var unitTestCase3Mock = mock(TestCase.class);

            TestHelper.mockEListResult(unitTestSuiteMock::getTestCases,
                    List.of(unitTestCase1Mock, unitTestCase2Mock, unitTestCase3Mock));

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(integrationTestSuite.getConfiguration()).thenReturn(policiesByInputStringMock);

            TestHelper.mockEListResult(policiesByInputStringMock::getPolicies,
                    List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var actualIntegrationTestCases        = mockTestContainerForName(
                    "name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteTestContainer);

            when(unitTestSuiteMock.getPolicyName()).thenReturn("fooPolicy");

            final var unitTestSuiteTestContainer = mock(TestContainer.class);
            final var actualUnitTestCases        = mockTestContainerForName("fooPolicy", unitTestSuiteTestContainer);

            final var integrationTest1Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var integrationTest2Mock = mock(io.sapl.test.dsl.setup.TestCase.class);

            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite,
                    integrationTestCase1Mock)).thenReturn(integrationTest1Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite,
                    integrationTestCase2Mock)).thenReturn(integrationTest2Mock);

            final var unitTest1Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var unitTest2Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var unitTest3Mock = mock(io.sapl.test.dsl.setup.TestCase.class);

            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock,
                    unitTestCase1Mock)).thenReturn(unitTest1Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock,
                    unitTestCase2Mock)).thenReturn(unitTest2Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock,
                    unitTestCase3Mock)).thenReturn(unitTest3Mock);

            final var result = testProvider.buildTests(saplTestMock);

            assertEquals(integrationTestSuiteTestContainer, result.get(0));
            assertEquals(unitTestSuiteTestContainer, result.get(1));

            assertEquals(integrationTest1Mock, actualIntegrationTestCases.get(0));
            assertEquals(integrationTest2Mock, actualIntegrationTestCases.get(1));

            assertEquals(unitTest1Mock, actualUnitTestCases.get(0));
            assertEquals(unitTest2Mock, actualUnitTestCases.get(1));
            assertEquals(unitTest3Mock, actualUnitTestCases.get(2));
        }
    }

    @Test
    void of_buildsInstanceOfTestProviderUsingStepConstructor_returnsTestProviderInstance() {
        final var stepConstructorMock = mock(StepConstructor.class);

        final var result = TestProvider.of(stepConstructorMock);

        assertNotNull(result);
    }
}
