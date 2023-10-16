package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.sapl.interpreter.InitializationException;
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.ExpectStepBuilder;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.VerifyStepBuilder;
import io.sapl.test.dsl.interfaces.WhenStepBuilder;
import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import io.sapl.test.utils.DocumentHelper;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class DefaultTestBuilderTest {

    private MockedStatic<DocumentHelper> documentHelperMockedStatic;

    private MockedStatic<org.assertj.core.api.Assertions> assertionsMockedStatic;

    private TestFixtureBuilder testFixtureBuilderMock;
    private WhenStepBuilder whenStepBuilderMock;
    private ExpectStepBuilder expectStepBuilderMock;
    private VerifyStepBuilder verifyStepBuilderMock;
    private SaplTestInterpreter saplTestInterpreterMock;

    private DefaultTestBuilder defaultTestBuilder;

    @BeforeEach
    void setUp() {
        documentHelperMockedStatic = mockStatic(DocumentHelper.class);
        assertionsMockedStatic = mockStatic(org.assertj.core.api.Assertions.class, Answers.RETURNS_DEEP_STUBS);

        testFixtureBuilderMock = mock(TestFixtureBuilder.class);
        whenStepBuilderMock = mock(WhenStepBuilder.class);
        expectStepBuilderMock = mock(ExpectStepBuilder.class);
        verifyStepBuilderMock = mock(VerifyStepBuilder.class);
        saplTestInterpreterMock = mock(SaplTestInterpreter.class);


        defaultTestBuilder = new DefaultTestBuilder(testFixtureBuilderMock, whenStepBuilderMock, expectStepBuilderMock, verifyStepBuilderMock, saplTestInterpreterMock);
    }

    @AfterEach
    void tearDown() {
        documentHelperMockedStatic.close();
        assertionsMockedStatic.close();
    }

    @Nested
    @DisplayName("Early return cases")
    class EarlyReturnCases {
        @Test
        void buildTest_doesNothingForNullFilename() {
            final var result = defaultTestBuilder.buildTests(null);

            assertTrue(result.isEmpty());

            documentHelperMockedStatic.verifyNoInteractions();
        }

        @Test
        void buildTest_doesNothingWhenInterpreterReturnsNullForInput() {
            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(null);
            final var result = defaultTestBuilder.buildTests("filename");

            assertTrue(result.isEmpty());

            verify(saplTestInterpreterMock, times(1)).loadAsResource("testCase");
            verifyNoMoreInteractions(saplTestInterpreterMock);
        }

        @Test
        void buildTest_doesNothingWhenInterpreterReturnsNullElements() {
            final var saplTestMock = mock(SAPLTest.class);

            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(saplTestMock);
            when(saplTestMock.getElements()).thenReturn(null);
            final var result = defaultTestBuilder.buildTests("filename");

            assertTrue(result.isEmpty());

            verify(saplTestInterpreterMock, times(1)).loadAsResource("testCase");
            verifyNoMoreInteractions(saplTestInterpreterMock);
        }

        @Test
        void buildTest_doesNothingWhenInterpreterReturnsEmptyElements() {
            final var saplTestMock = mock(SAPLTest.class);

            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(saplTestMock);
            final var eListMock = Helper.mockEList(List.<TestSuite>of());
            when(saplTestMock.getElements()).thenReturn(eListMock);
            final var result = defaultTestBuilder.buildTests("filename");

            assertTrue(result.isEmpty());

            verify(saplTestInterpreterMock, times(1)).loadAsResource("testCase");
            verifyNoMoreInteractions(saplTestInterpreterMock);
        }

        @Test
        void buildTest_doesNothingWhenThereAreOnlyTestSuitesWithNullOrEmptyTestCases() {
            final var saplTestMock = mock(SAPLTest.class);

            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(saplTestMock);

            final var testSuite1Mock = mock(TestSuite.class);
            final var testSuite2Mock = mock(TestSuite.class);
            final var eListMock = Helper.mockEList(List.of(testSuite1Mock, testSuite2Mock));
            when(saplTestMock.getElements()).thenReturn(eListMock);

            final var emptyTestCasesMock = Helper.mockEList(List.<TestCase>of());
            when(testSuite1Mock.getTestCases()).thenReturn(null);
            when(testSuite2Mock.getTestCases()).thenReturn(emptyTestCasesMock);

            final var result = defaultTestBuilder.buildTests("filename");

            assertTrue(result.isEmpty());

            verify(saplTestInterpreterMock, times(1)).loadAsResource("testCase");
            verifyNoMoreInteractions(saplTestInterpreterMock);
        }
    }

    @Nested
    @DisplayName("Dynamic test setup")
    class DynamicTestSetup {

        private UnitTestSuite testSuiteMock;

        private void setupTestExecution(List<TestCase> testCases) {
            final var saplTestMock = mock(SAPLTest.class);

            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(saplTestMock);

            testSuiteMock = mock(UnitTestSuite.class);
            final var testSuitesMock = Helper.mockEList(List.<TestSuite>of(testSuiteMock));
            when(saplTestMock.getElements()).thenReturn(testSuitesMock);

            final var testCasesMock = Helper.mockEList(testCases);
            when(testSuiteMock.getTestCases()).thenReturn(testCasesMock);
        }

        private List<GivenStep> mockGivenSteps(TestCase testCase) {
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCase.getGivenSteps()).thenReturn(givenStepsMock);
            return givenStepsMock;
        }

        private GivenOrWhenStep mockTestFixture(List<GivenStep> givenSteps, Object environment) throws InitializationException {
            final var testFixtureMock = mock(GivenOrWhenStep.class);
            when(testFixtureBuilderMock.buildTestFixture(eq(givenSteps), any(TestSuite.class), eq(environment))).thenReturn(testFixtureMock);
            return testFixtureMock;
        }

        private void mockTestCaseWithTestException(TestCase testCaseWithExceptionMock) {
            final var testExceptionMock = mock(TestException.class);
            when(testCaseWithExceptionMock.getExpect()).thenReturn(testExceptionMock);

            assertionsMockedStatic.when(() -> Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(any())).thenAnswer(invocationOnMock -> {
                if (invocationOnMock.getArgument(0) instanceof ThrowableAssert.ThrowingCallable callable) {
                    callable.call();
                } else {
                    fail("No proper ThrowingCallable was passed as an argument!");
                }
                return null;
            });
        }

        private VerifyStep mockTestBuildingChain(List<GivenStep> givenSteps, GivenOrWhenStep testFixture, TestCase testCase) {
            final var whenStepMock = mock(WhenStep.class);
            when(whenStepBuilderMock.constructWhenStep(givenSteps, testFixture)).thenReturn(whenStepMock);

            final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);
            when(expectStepBuilderMock.constructExpectStep(testCase, whenStepMock)).thenReturn(expectOrVerifyStepMock);

            final var verifyStepMock = mock(VerifyStep.class);
            when(verifyStepBuilderMock.constructVerifyStep(testCase, expectOrVerifyStepMock)).thenReturn(verifyStepMock);
            return verifyStepMock;
        }


        @Test
        void buildTest_handlesMultipleTestSuitesWithMultipleTestCasesPerSuite() {
            final var saplTestMock = mock(SAPLTest.class);

            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("testCase");

            when(saplTestInterpreterMock.loadAsResource("testCase")).thenReturn(saplTestMock);

            testSuiteMock = mock(UnitTestSuite.class);
            final var testSuite2Mock = mock(TestSuite.class);
            final var testSuites = Helper.mockEList(List.of(testSuiteMock, testSuite2Mock));
            when(saplTestMock.getElements()).thenReturn(testSuites);

            final var testCase1Mock = mock(TestCase.class);
            final var testCase2Mock = mock(TestCase.class);
            final var testCase3Mock = mock(TestCase.class);
            final var testCase4Mock = mock(TestCase.class);
            final var testCase5Mock = mock(TestCase.class);

            final var testSuiteTestCases = Helper.mockEList(List.of(testCase1Mock, testCase2Mock));
            final var testSuite2TestCases = Helper.mockEList(List.of(testCase3Mock, testCase4Mock, testCase5Mock));

            when(testSuiteMock.getTestCases()).thenReturn(testSuiteTestCases);
            when(testSuite2Mock.getTestCases()).thenReturn(testSuite2TestCases);

            when(testCase1Mock.getName()).thenReturn("testCase1");
            when(testCase2Mock.getName()).thenReturn("testCase2");
            when(testCase3Mock.getName()).thenReturn("testCase3");
            when(testCase4Mock.getName()).thenReturn("testCase4");
            when(testCase5Mock.getName()).thenReturn("testCase5");

            final var result = defaultTestBuilder.buildTests("filename");

            assertEquals("testCase1", result.get(0).getDisplayName());
            assertEquals("testCase2", result.get(1).getDisplayName());
            assertEquals("testCase3", result.get(2).getDisplayName());
            assertEquals("testCase4", result.get(3).getDisplayName());
            assertEquals("testCase5", result.get(4).getDisplayName());
        }

        @Test
        void buildTest_addsTestForSingleTestCaseWithExpectedException() throws Throwable {
            final var testCaseMock = mock(TestCase.class);
            when(testCaseMock.getName()).thenReturn("singleTest");

            setupTestExecution(List.of(testCaseMock));

            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCaseMock.getGivenSteps()).thenReturn(givenStepsMock);

            final var testFixtureMock = mockTestFixture(givenStepsMock, null);

            mockTestCaseWithTestException(testCaseMock);

            result.get(0).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
        }

        @Test
        void buildTest_addsTestForSingleTestCase() throws Throwable {
            final var testCaseMock = mock(TestCase.class);
            when(testCaseMock.getName()).thenReturn("singleTest");

            setupTestExecution(List.of(testCaseMock));
            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCaseMock.getGivenSteps()).thenReturn(givenStepsMock);

            final var testFixtureMock = mockTestFixture(givenStepsMock, null);

            final var expectChainMock = mock(ExpectChain.class);
            when(testCaseMock.getExpect()).thenReturn(expectChainMock);

            final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, testCaseMock);

            result.get(0).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
            verify(verifyStepMock, times(1)).verify();
        }

        @Test
        void buildTest_addsTestForMultipleTestCasesWithAndWithoutExpectedException() throws Throwable {
            final var testCaseWithoutException1Mock = mock(TestCase.class);
            when(testCaseWithoutException1Mock.getName()).thenReturn("testCaseWithoutException1");

            final var testCaseWithExceptionMock = mock(TestCase.class);
            when(testCaseWithExceptionMock.getName()).thenReturn("testCaseWithException");

            final var testCaseWithoutException2Mock = mock(TestCase.class);
            when(testCaseWithoutException2Mock.getName()).thenReturn("testCaseWithoutException2");

            setupTestExecution(List.of(testCaseWithoutException1Mock, testCaseWithExceptionMock, testCaseWithoutException2Mock));
            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");

            final var givenSteps1Mock = mockGivenSteps(testCaseWithoutException1Mock);
            final var givenSteps2Mock = mockGivenSteps(testCaseWithExceptionMock);
            final var givenSteps3Mock = mockGivenSteps(testCaseWithoutException2Mock);

            final var testFixture1Mock = mockTestFixture(givenSteps1Mock, null);
            final var testFixture2Mock = mockTestFixture(givenSteps2Mock, null);
            final var testFixture3Mock = mockTestFixture(givenSteps3Mock, null);

            final var expectChain1Mock = mock(ExpectChain.class);
            when(testCaseWithoutException1Mock.getExpect()).thenReturn(expectChain1Mock);

            mockTestCaseWithTestException(testCaseWithExceptionMock);

            final var expectChain2Mock = mock(ExpectChain.class);
            when(testCaseWithoutException2Mock.getExpect()).thenReturn(expectChain2Mock);

            final var verifyStep1Mock = mockTestBuildingChain(givenSteps1Mock, testFixture1Mock, testCaseWithoutException1Mock);
            final var verifyStep2Mock = mockTestBuildingChain(givenSteps3Mock, testFixture3Mock, testCaseWithoutException2Mock);

            result.get(0).getExecutable().execute();
            result.get(1).getExecutable().execute();
            result.get(2).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenSteps2Mock, testFixture2Mock);
            verify(verifyStep1Mock, times(1)).verify();
            verify(verifyStep2Mock, times(1)).verify();
        }

        @Test
        void buildTest_handlesEnvironmentBeingObject() throws Throwable {
            final var environmentMock = mock(Object.class);
            final var testCaseMock = mock(TestCase.class);
            when(testCaseMock.getName()).thenReturn("singleTest");
            when(testCaseMock.getEnvironment()).thenReturn(environmentMock);

            setupTestExecution(List.of(testCaseMock));
            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCaseMock.getGivenSteps()).thenReturn(givenStepsMock);

            final var testFixtureMock = mockTestFixture(givenStepsMock, environmentMock);

            final var expectChainMock = mock(ExpectChain.class);
            when(testCaseMock.getExpect()).thenReturn(expectChainMock);

            final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, testCaseMock);

            result.get(0).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
            verify(verifyStepMock, times(1)).verify();
        }

        @Test
        void buildTest_handlesEnvironmentBeingNonObject() throws Throwable {
            final var environmentMock = mock(Value.class);
            final var testCaseMock = mock(TestCase.class);
            when(testCaseMock.getName()).thenReturn("singleTest");
            when(testCaseMock.getEnvironment()).thenReturn(environmentMock);

            setupTestExecution(List.of(testCaseMock));
            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCaseMock.getGivenSteps()).thenReturn(givenStepsMock);

            final var testFixtureMock = mockTestFixture(givenStepsMock, null);

            final var expectChainMock = mock(ExpectChain.class);
            when(testCaseMock.getExpect()).thenReturn(expectChainMock);

            final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, testCaseMock);

            result.get(0).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
            verify(verifyStepMock, times(1)).verify();
        }

        @Test
        void buildTest_handlesEnvironmentBeingNull() throws Throwable {
            final var testCaseMock = mock(TestCase.class);
            when(testCaseMock.getName()).thenReturn("singleTest");
            when(testCaseMock.getEnvironment()).thenReturn(null);

            setupTestExecution(List.of(testCaseMock));
            final var result = defaultTestBuilder.buildTests("filename");

            when(testSuiteMock.getPolicy()).thenReturn("samplePolicy");
            final var givenStepsMock = Helper.mockEList(List.<GivenStep>of());
            when(testCaseMock.getGivenSteps()).thenReturn(givenStepsMock);

            final var testFixtureMock = mockTestFixture(givenStepsMock, null);

            final var expectChainMock = mock(ExpectChain.class);
            when(testCaseMock.getExpect()).thenReturn(expectChainMock);

            final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, testCaseMock);

            result.get(0).getExecutable().execute();

            verify(whenStepBuilderMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
            verify(verifyStepMock, times(1)).verify();
        }
    }

}