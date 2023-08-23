package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.Library;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestFixtureBuilderTest {

    private Object pipMock;

    private SaplUnitTestFixture unitTestFixtureMock;

    private TestFixtureBuilder testFixtureBuilder;

    @BeforeEach
    void setUp() {
        pipMock = mock(Object.class);
        unitTestFixtureMock = mock(SaplUnitTestFixture.class);
        testFixtureBuilder = new TestFixtureBuilder(pipMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void buildTestFixture_handlesNullGivenSteps_returnsTestCaseWithoutMocks() throws InitializationException {
        final var testCaseMock = mock(GivenOrWhenStep.class);

        when(unitTestFixtureMock.constructTestCase()).thenReturn(testCaseMock);

        final var result = testFixtureBuilder.buildTestFixture(null, unitTestFixtureMock);

        assertEquals(testCaseMock, result);
    }

    @Test
    void buildTestFixture_handlesEmptyGivenSteps_returnsTestCaseWithoutMocks() throws InitializationException {
        final var testCaseMock = mock(GivenOrWhenStep.class);

        when(unitTestFixtureMock.constructTestCase()).thenReturn(testCaseMock);

        final var result = testFixtureBuilder.buildTestFixture(List.of(), unitTestFixtureMock);

        assertEquals(testCaseMock, result);
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithUnknownLibraryFixtureRegistrations_throwsIllegalStateException() {
        final var givenStepMock = mock(Library.class);
        final var givenStep2Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock));

        when(givenStepMock.getLibrary()).thenReturn("UnknownLibrary");

        assertThrows(IllegalStateException.class, () -> testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock));
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithoutFixtureRegistrations_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock));

        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep2Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verifyNoMoreInteractions(unitTestFixtureMock);
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithLibraryFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(Library.class);
        final var givenStep3Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock));

        when(givenStep2Mock.getLibrary()).thenReturn("FilterFunctionLibrary");
        when(unitTestFixtureMock.registerFunctionLibrary(any(FilterFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep3Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(FilterFunctionLibrary.class));
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(Pip.class);
        final var givenStep3Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock));


        when(unitTestFixtureMock.registerPIP(pipMock)).thenReturn(unitTestFixtureMock);
        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep3Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithStandardFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(Pip.class);
        final var givenStep3Mock = mock(Library.class);
        final var givenStep4Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));

        when(unitTestFixtureMock.registerPIP(pipMock)).thenReturn(unitTestFixtureMock);
        when(givenStep3Mock.getLibrary()).thenReturn("StandardFunctionLibrary");
        when(unitTestFixtureMock.registerFunctionLibrary(any(StandardFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(StandardFunctionLibrary.class));
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithLoggingFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(Pip.class);
        final var givenStep3Mock = mock(Library.class);
        final var givenStep4Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));

        when(unitTestFixtureMock.registerPIP(pipMock)).thenReturn(unitTestFixtureMock);
        when(givenStep3Mock.getLibrary()).thenReturn("LoggingFunctionLibrary");
        when(unitTestFixtureMock.registerFunctionLibrary(any(LoggingFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(LoggingFunctionLibrary.class));
    }

    @Test
    void buildTestFixture_handlesMultipleGivenStepsWithTemporalFunctionLibraryAndPipFixtureRegistrationsAndRemovesFixtureRegistrationsFromGivenSteps_returnsTestCaseWithMocks() throws InitializationException {
        final var testCaseWithMocksMock = mock(GivenOrWhenStep.class);

        final var givenStepMock = mock(GivenStep.class);
        final var givenStep2Mock = mock(Pip.class);
        final var givenStep3Mock = mock(Library.class);
        final var givenStep4Mock = mock(GivenStep.class);

        final var givenSteps = new ArrayList<>(List.of(givenStepMock, givenStep2Mock, givenStep3Mock, givenStep4Mock));

        when(unitTestFixtureMock.registerPIP(pipMock)).thenReturn(unitTestFixtureMock);
        when(givenStep3Mock.getLibrary()).thenReturn("TemporalFunctionLibrary");
        when(unitTestFixtureMock.registerFunctionLibrary(any(TemporalFunctionLibrary.class))).thenReturn(unitTestFixtureMock);
        when(unitTestFixtureMock.constructTestCaseWithMocks()).thenReturn(testCaseWithMocksMock);

        final var result = testFixtureBuilder.buildTestFixture(givenSteps, unitTestFixtureMock);

        assertEquals(testCaseWithMocksMock, result);
        assertEquals(List.of(givenStepMock, givenStep4Mock), givenSteps);
        verify(unitTestFixtureMock, times(1)).constructTestCaseWithMocks();
        verify(unitTestFixtureMock, times(1)).registerPIP(pipMock);
        verify(unitTestFixtureMock, times(1)).registerFunctionLibrary(any(TemporalFunctionLibrary.class));
    }
}