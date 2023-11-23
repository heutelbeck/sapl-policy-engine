package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PoliciesByIdentifier;
import io.sapl.test.grammar.sAPLTest.PoliciesByInputString;
import io.sapl.test.grammar.sAPLTest.PolicyResolverConfig;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.emf.common.util.EList;
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
class TestProviderTest {
    @Mock
    private StepConstructor stepConstructorMock;
    @InjectMocks
    private TestProvider testProvider;
    @Mock
    SAPLTest saplTestTMock;
    @Mock
    UnitTestSuite unitTestSuiteMock;
    @Mock
    TestCase testCaseMock;

    private final MockedStatic<TestContainer> testContainerMockedStatic = mockStatic(TestContainer.class);
    private final MockedStatic<io.sapl.test.dsl.setup.TestCase> testMockedStatic = mockStatic(io.sapl.test.dsl.setup.TestCase.class);

    @AfterEach
    void tearDown() {
        testContainerMockedStatic.close();
        testMockedStatic.close();
    }

    private void mockTestSuites(final List<TestSuite> testSuites) {
        final var mockedTestSuites = Helper.mockEList(testSuites);
        when(saplTestTMock.getElements()).thenReturn(mockedTestSuites);
    }

    private EList<TestCase> mockTestCases(final List<TestCase> testSuites) {
        return Helper.mockEList(testSuites);
    }

    @Nested
    @DisplayName("Early return cases")
    class EarlyReturnCasesTest {
        @Test
        void buildTests_calledWithNullSAPLTest_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(null));

            assertEquals("provided SAPLTest is null", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestSuites_throwsSaplTestException() {
            when(saplTestTMock.getElements()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestSuites_throwsSaplTestException() {
            mockTestSuites(Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestCases_throwsSaplTestException() {
            mockTestSuites(List.of(unitTestSuiteMock));

            when(unitTestSuiteMock.getTestCases()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestCases_throwsSaplTestException() {
            mockTestSuites(List.of(unitTestSuiteMock));

            mockTestCases(Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Container name construction")
    class ContainerNameConstructionTest {
        private List<TestNode> mockTestContainerForName(final String name, final TestContainer dynamicContainer) {
            List<TestNode> dynamicTestCases = new ArrayList<>();
            testContainerMockedStatic.when(() -> TestContainer.from(eq(name), any(List.class))).thenAnswer(invocationOnMock ->
            {
                final List<TestNode> dynamicTests = invocationOnMock.getArgument(1);
                dynamicTestCases.addAll(dynamicTests);
                return dynamicContainer;
            });
            return dynamicTestCases;
        }

        @Test
        void buildTests_UnknownTestSuite_throwsSaplTestException() {
            final var unknownTestSuiteMock = mock(TestSuite.class);

            mockTestSuites(List.of(unknownTestSuiteMock));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(unknownTestSuiteMock.getTestCases()).thenReturn(testCases);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("Unknown type of TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_usingUnitTestSuite_returnsDynamicContainerWithPolicyName() {
            mockTestSuites(List.of(unitTestSuiteMock));
            final var testCases = mockTestCases(List.of(testCaseMock));

            when(unitTestSuiteMock.getTestCases()).thenReturn(testCases);
            when(unitTestSuiteMock.getId()).thenReturn("policyName");

            final var unitTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes = mockTestContainerForName("policyName", unitTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock, testCaseMock)).thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestTMock);

            assertEquals(unitTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithUnknownTypeOfPolicyResolverConfig_throwsSaplTestException() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var unknownPolicyResolverConfigMock = mock(PolicyResolverConfig.class);
            when(integrationTestSuite.getConfig()).thenReturn(unknownPolicyResolverConfigMock);

            final var exception = assertThrows(SaplTestException.class, () -> testProvider.buildTests(saplTestTMock));

            assertEquals("Unknown type of PolicyResolverConfig", exception.getMessage());
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicyFolder_returnsDynamicContainerWithPolicyFolderName() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            when(policiesByIdentifierMock.getIdentifier()).thenReturn("policyFolder");

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes = mockTestContainerForName("policyFolder", integrationTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, testCaseMock)).thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestTMock);

            assertEquals(integrationTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicySet_returnsDynamicContainerWithJoinedPolicyNames() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            final var policiesMock = Helper.mockEList(List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));
            when(policiesByInputStringMock.getPolicies()).thenReturn(policiesMock);

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var testNodes = mockTestContainerForName("name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteTestContainer);

            final var testMock = mock(io.sapl.test.dsl.setup.TestCase.class);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, testCaseMock)).thenReturn(testMock);

            final var result = testProvider.buildTests(saplTestTMock);

            assertEquals(integrationTestSuiteTestContainer, result.get(0));
            assertEquals(testMock, testNodes.get(0));
        }

        @Test
        void buildTests_usingUnitAndIntegrationTestSuitesWithMultipleTestCasesEach_returnsDynamicContainers() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            mockTestSuites(List.of(integrationTestSuite, unitTestSuiteMock));

            final var integrationTestCase1Mock = mock(TestCase.class);
            final var integrationTestCase2Mock = mock(TestCase.class);

            final var unitTestCase1Mock = mock(TestCase.class);
            final var unitTestCase2Mock = mock(TestCase.class);
            final var unitTestCase3Mock = mock(TestCase.class);

            final var integrationTestCases = mockTestCases(List.of(integrationTestCase1Mock, integrationTestCase2Mock));
            final var unitTestCases = mockTestCases(List.of(unitTestCase1Mock, unitTestCase2Mock, unitTestCase3Mock));

            when(integrationTestSuite.getTestCases()).thenReturn(integrationTestCases);
            when(unitTestSuiteMock.getTestCases()).thenReturn(unitTestCases);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            final var policiesMock = Helper.mockEList(List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));
            when(policiesByInputStringMock.getPolicies()).thenReturn(policiesMock);

            final var integrationTestSuiteTestContainer = mock(TestContainer.class);
            final var actualIntegrationTestCases = mockTestContainerForName("name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteTestContainer);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            final var unitTestSuiteTestContainer = mock(TestContainer.class);
            final var actualUnitTestCases = mockTestContainerForName("fooPolicy", unitTestSuiteTestContainer);

            final var integrationTest1Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var integrationTest2Mock = mock(io.sapl.test.dsl.setup.TestCase.class);

            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, integrationTestCase1Mock)).thenReturn(integrationTest1Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, integrationTestSuite, integrationTestCase2Mock)).thenReturn(integrationTest2Mock);

            final var unitTest1Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var unitTest2Mock = mock(io.sapl.test.dsl.setup.TestCase.class);
            final var unitTest3Mock = mock(io.sapl.test.dsl.setup.TestCase.class);

            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock, unitTestCase1Mock)).thenReturn(unitTest1Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock, unitTestCase2Mock)).thenReturn(unitTest2Mock);
            testMockedStatic.when(() -> io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, unitTestSuiteMock, unitTestCase3Mock)).thenReturn(unitTest3Mock);

            final var result = testProvider.buildTests(saplTestTMock);

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