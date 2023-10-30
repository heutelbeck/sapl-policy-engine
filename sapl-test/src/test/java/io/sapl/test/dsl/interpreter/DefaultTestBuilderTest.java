package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PolicyFolder;
import io.sapl.test.grammar.sAPLTest.PolicyResolverConfig;
import io.sapl.test.grammar.sAPLTest.PolicySet;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTestBuilderTest {

    @Mock
    private TestCaseBuilder testCaseBuilderMock;

    @InjectMocks
    private DefaultTestBuilder defaultTestBuilder;

    private final MockedStatic<DynamicContainer> dynamicContainerMockedStatic = mockStatic(DynamicContainer.class);

    @Mock
    SAPLTest saplTestTMock;

    @Mock
    UnitTestSuite unitTestSuiteMock;
    @Mock
    TestCase testCaseMock;

    @AfterEach
    void tearDown() {
        dynamicContainerMockedStatic.close();
    }

    private EList<TestSuite> mockTestSuites(final List<TestSuite> testSuites) {
        final var mockedTestSuites = Helper.mockEList(testSuites);
        when(saplTestTMock.getElements()).thenReturn(mockedTestSuites);
        return mockedTestSuites;
    }

    private EList<TestCase> mockTestCases(final List<TestCase> testSuites) {
        return Helper.mockEList(testSuites);
    }

    @Nested
    @DisplayName("Early return cases")
    class EarlyReturnCasesTest {
        @Test
        void buildTests_calledWithNullSAPLTest_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(null));

            assertEquals("provided SAPLTest is null", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestSuites_throwsSaplTestException() {
            when(saplTestTMock.getElements()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestSuites_throwsSaplTestException() {
            mockTestSuites(Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("provided SAPLTest does not contain a TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithNullTestCases_throwsSaplTestException() {
            mockTestSuites(List.of(unitTestSuiteMock));

            when(unitTestSuiteMock.getTestCases()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
        }

        @Test
        void buildTests_calledWithSAPLTestWithEmptyTestCases_throwsSaplTestException() {
            mockTestSuites(List.of(unitTestSuiteMock));

            mockTestCases(Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("provided TestSuite does not contain a Test", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Container name construction")
    class ContainerNameConstructionTest {
        private List<DynamicTest> mockDynamicContainerForName(final String name, final DynamicContainer dynamicContainer) {
            List<DynamicTest> dynamicTestCases = new ArrayList<>();
            dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq(name), any(Stream.class))).thenAnswer(invocationOnMock ->
            {
                final Stream<DynamicTest> dynamicTests = invocationOnMock.getArgument(1);
                dynamicTests.forEach(dynamicTestCases::add);
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

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("Unknown type of TestSuite", exception.getMessage());
        }

        @Test
        void buildTests_usingUnitTestSuite_returnsDynamicContainerWithPolicyName() {
            mockTestSuites(List.of(unitTestSuiteMock));
            final var testCases = mockTestCases(List.of(testCaseMock));

            when(unitTestSuiteMock.getTestCases()).thenReturn(testCases);
            when(unitTestSuiteMock.getId()).thenReturn("policyName");

            final var unitTestSuiteDynamicContainer = mock(DynamicContainer.class);
            final var dynamicTestCases = mockDynamicContainerForName("policyName", unitTestSuiteDynamicContainer);

            final var dynamicTestMock = mock(DynamicTest.class);
            when(testCaseBuilderMock.constructTestCase(unitTestSuiteMock, testCaseMock)).thenReturn(dynamicTestMock);

            final var result = defaultTestBuilder.buildTests(saplTestTMock);

            assertEquals(unitTestSuiteDynamicContainer, result.get(0));
            assertEquals(dynamicTestMock, dynamicTestCases.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithUnknownTypeOfPolicyResolverConfig_throwsSaplTestException() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var unknownPolicyResolverConfigMock = mock(PolicyResolverConfig.class);
            when(integrationTestSuite.getConfig()).thenReturn(unknownPolicyResolverConfigMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestBuilder.buildTests(saplTestTMock));

            assertEquals("Unknown type of PolicyResolverConfig", exception.getMessage());
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicyFolder_returnsDynamicContainerWithPolicyFolderName() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var policyFolderMock = mock(PolicyFolder.class);
            when(integrationTestSuite.getConfig()).thenReturn(policyFolderMock);

            when(policyFolderMock.getPolicyFolder()).thenReturn("policyFolder");

            final var integrationTestSuiteDynamicContainer = mock(DynamicContainer.class);
            final var dynamicTestCases = mockDynamicContainerForName("policyFolder", integrationTestSuiteDynamicContainer);

            final var dynamicTestMock = mock(DynamicTest.class);
            when(testCaseBuilderMock.constructTestCase(integrationTestSuite, testCaseMock)).thenReturn(dynamicTestMock);

            final var result = defaultTestBuilder.buildTests(saplTestTMock);

            assertEquals(integrationTestSuiteDynamicContainer, result.get(0));
            assertEquals(dynamicTestMock, dynamicTestCases.get(0));
        }

        @Test
        void buildTests_usingIntegrationTestSuiteWithPolicySet_returnsDynamicContainerWithJoinedPolicyNames() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);
            mockTestSuites(List.of(integrationTestSuite));

            final var testCases = mockTestCases(List.of(testCaseMock));

            when(integrationTestSuite.getTestCases()).thenReturn(testCases);

            final var policySetMock = mock(PolicySet.class);
            when(integrationTestSuite.getConfig()).thenReturn(policySetMock);

            final var policiesMock = Helper.mockEList(List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));
            when(policySetMock.getPolicies()).thenReturn(policiesMock);

            final var integrationTestSuiteDynamicContainer = mock(DynamicContainer.class);
            final var dynamicTestCases = mockDynamicContainerForName("name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteDynamicContainer);

            final var dynamicTestMock = mock(DynamicTest.class);
            when(testCaseBuilderMock.constructTestCase(integrationTestSuite, testCaseMock)).thenReturn(dynamicTestMock);

            final var result = defaultTestBuilder.buildTests(saplTestTMock);

            assertEquals(integrationTestSuiteDynamicContainer, result.get(0));
            assertEquals(dynamicTestMock, dynamicTestCases.get(0));
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

            final var policySetMock = mock(PolicySet.class);
            when(integrationTestSuite.getConfig()).thenReturn(policySetMock);

            final var policiesMock = Helper.mockEList(List.of("name1", "foo/name2", "foo/subfoo/nested/policy3.sapl"));
            when(policySetMock.getPolicies()).thenReturn(policiesMock);

            final var integrationTestSuiteDynamicContainer = mock(DynamicContainer.class);
            final var actualIntegrationTestCases = mockDynamicContainerForName("name1,foo/name2,foo/subfoo/nested/policy3.sapl", integrationTestSuiteDynamicContainer);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            final var unitTestSuiteDynamicContainer = mock(DynamicContainer.class);
            final var actualUnitTestCases = mockDynamicContainerForName("fooPolicy", unitTestSuiteDynamicContainer);

            final var dynamicIntegrationTest1Mock = mock(DynamicTest.class);
            final var dynamicIntegrationTest2Mock = mock(DynamicTest.class);

            when(testCaseBuilderMock.constructTestCase(integrationTestSuite, integrationTestCase1Mock)).thenReturn(dynamicIntegrationTest1Mock);
            when(testCaseBuilderMock.constructTestCase(integrationTestSuite, integrationTestCase2Mock)).thenReturn(dynamicIntegrationTest2Mock);

            final var dynamicUnitTest1Mock = mock(DynamicTest.class);
            final var dynamicUnitTest2Mock = mock(DynamicTest.class);
            final var dynamicUnitTest3Mock = mock(DynamicTest.class);

            when(testCaseBuilderMock.constructTestCase(unitTestSuiteMock, unitTestCase1Mock)).thenReturn(dynamicUnitTest1Mock);
            when(testCaseBuilderMock.constructTestCase(unitTestSuiteMock, unitTestCase2Mock)).thenReturn(dynamicUnitTest2Mock);
            when(testCaseBuilderMock.constructTestCase(unitTestSuiteMock, unitTestCase3Mock)).thenReturn(dynamicUnitTest3Mock);

            final var result = defaultTestBuilder.buildTests(saplTestTMock);

            assertEquals(integrationTestSuiteDynamicContainer, result.get(0));
            assertEquals(unitTestSuiteDynamicContainer, result.get(1));

            assertEquals(dynamicIntegrationTest1Mock, actualIntegrationTestCases.get(0));
            assertEquals(dynamicIntegrationTest2Mock, actualIntegrationTestCases.get(1));

            assertEquals(dynamicUnitTest1Mock, actualUnitTestCases.get(0));
            assertEquals(dynamicUnitTest2Mock, actualUnitTestCases.get(1));
            assertEquals(dynamicUnitTest3Mock, actualUnitTestCases.get(2));
        }
    }
}