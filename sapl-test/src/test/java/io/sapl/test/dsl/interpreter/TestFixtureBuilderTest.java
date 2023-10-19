//package io.sapl.test.dsl.interpreter;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoMoreInteractions;
//import static org.mockito.Mockito.when;
//
//import io.sapl.functions.FilterFunctionLibrary;
//import io.sapl.functions.LoggingFunctionLibrary;
//import io.sapl.functions.StandardFunctionLibrary;
//import io.sapl.functions.TemporalFunctionLibrary;
//import io.sapl.interpreter.InitializationException;
//import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
//import io.sapl.test.grammar.sAPLTest.GivenStep;
//import io.sapl.test.grammar.sAPLTest.Library;
//import io.sapl.test.grammar.sAPLTest.Pip;
//import io.sapl.test.grammar.sAPLTest.TestSuite;
//import io.sapl.test.steps.GivenOrWhenStep;
//import io.sapl.test.unit.SaplUnitTestFixture;
//import java.util.ArrayList;
//import java.util.List;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//class TestFixtureBuilderTest {
//
//    private SaplUnitTestFixture unitTestFixtureMock;
//
//    private TestSuiteInterpreter testSuiteInterpreterMock;
//
//    private TestSuite testSuiteMock;
//
//    private ValInterpreter valInterpreterMock;
//
//    private TestFixtureBuilder testFixtureBuilder;
//
//    @BeforeEach
//    void setUp() {
//        unitTestFixtureMock = mock(SaplUnitTestFixture.class);
//        testSuiteInterpreterMock = mock(TestSuiteInterpreter.class);
//        testSuiteMock = mock(TestSuite.class);
//        valInterpreterMock = mock(ValInterpreter.class);
//        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, null)).thenReturn(unitTestFixtureMock);
//
//        testFixtureBuilder = new TestFixtureBuilder(testSuiteInterpreterMock);
//    }
//
//    @Test
//    void buildTestFixture_handlesNullGivenSteps_returnsTestCaseWithoutMocks() throws InitializationException {
//        final var testCaseMock = mock(GivenOrWhenStep.class);
//
//        when(unitTestFixtureMock.constructTestCase()).thenReturn(testCaseMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(null, testSuiteMock, null);
//
//        assertEquals(testCaseMock, result);
//    }
//
//    @Test
//    void buildTestFixture_handlesEmptyGivenSteps_returnsTestCaseWithoutMocks() throws InitializationException {
//        final var testCaseMock = mock(GivenOrWhenStep.class);
//
//        when(unitTestFixtureMock.constructTestCase()).thenReturn(testCaseMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(List.of(), testSuiteMock, null);
//
//        assertEquals(testCaseMock, result);
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithoutFixtureRegistrations_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock));
//
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep2Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        verifyNoMoreInteractions(unitTestFixtureMock);
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithLibraryFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(Library.class);
//        final var givenStep3Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock));
//
//        when(givenStep2Mock.getLibrary()).thenReturn(FunctionLibrary.FILTER);
//        when(unitTestFixtureMock.registerFunctionLibrary(any(FilterFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep3Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(FilterFunctionLibrary.class));
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(Pip.class);
//        final var givenStep3Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock));
//
//
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep3Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        //verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithStandardFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(Pip.class);
//        final var givenStep3Mock = mock(Library.class);
//        final var givenStep4Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));
//
//        when(givenStep3Mock.getLibrary()).thenReturn(FunctionLibrary.STANDARD);
//        when(unitTestFixtureMock.registerFunctionLibrary(any(StandardFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        //verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
//        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(StandardFunctionLibrary.class));
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithLoggingFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(Pip.class);
//        final var givenStep3Mock = mock(Library.class);
//        final var givenStep4Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));
//
//        when(givenStep3Mock.getLibrary()).thenReturn(FunctionLibrary.LOGGING);
//        when(unitTestFixtureMock.registerFunctionLibrary(any(LoggingFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        //verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
//        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(LoggingFunctionLibrary.class));
//    }
//
//    @Test
//    void buildTestFixture_handlesMultipleGivenStepsWithTemporalFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
//        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);
//
//        final var givenStepMock = mock(GivenStep.class);
//        final var givenStep2Mock = mock(Pip.class);
//        final var givenStep3Mock = mock(Library.class);
//        final var givenStep4Mock = mock(GivenStep.class);
//
//        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));
//
//        when(givenStep3Mock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);
//        when(unitTestFixtureMock.registerFunctionLibrary(any(TemporalFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
//        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);
//
//        final var result = testFixtureBuilder.buildTestFixture(givenSteps, testSuiteMock, null);
//
//        assertEquals(testCaseWithMocksMock, result);
//        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
//        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
//        //verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
//        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(TemporalFunctionLibrary.class));
//    }
//}