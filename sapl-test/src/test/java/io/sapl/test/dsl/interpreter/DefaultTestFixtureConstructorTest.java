package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.utils.ReflectionHelper;
import io.sapl.test.grammar.sAPLTest.CustomFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.SaplFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTestFixtureConstructorTest {
    @Mock
    private TestSuiteInterpreter testSuiteInterpreterMock;
    @Mock
    private FunctionLibraryInterpreter functionLibraryInterpreter;
    @Mock
    private ReflectionHelper reflectionHelperMock;
    @Mock
    private SaplTestFixture testFixtureMock;
    @Mock
    private TestSuite testSuiteMock;
    @Mock
    private io.sapl.test.grammar.sAPLTest.Object environmentMock;
    @InjectMocks
    private DefaultTestFixtureConstructor defaultTestFixtureConstructor;

    @Test
    void buildTestFixture_testSuiteInterpreterThrows_throwsSaplTestException() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenThrow(new SaplTestException("no fixture here"));

        final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(null, testSuiteMock, environmentMock, false));

        assertEquals("no fixture here", exception.getMessage());
    }

    @Test
    void buildTestFixture_testSuiteInterpreterReturnsNull_throwsSaplTestException() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(null, testSuiteMock, environmentMock, false));

        assertEquals("could not build test fixture", exception.getMessage());
    }

    @Test
    void buildTestFixture_handlesNullFixtureRegistrationsWithoutMocks_returnsFixtureWithoutMocks() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(testFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestFixtureConstructor.buildTestFixture(null, testSuiteMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void buildTestFixture_handlesNullFixtureRegistrationsWithMocks_returnsFixtureWithMocks() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(testFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestFixtureConstructor.buildTestFixture(null, testSuiteMock, environmentMock, true);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void buildTestFixture_handlesEmptyFixtureRegistrationsWithoutMocks_returnsFixtureWithoutMocks() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(testFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestFixtureConstructor.buildTestFixture(Collections.emptyList(), testSuiteMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void buildTestFixture_handlesEmptyFixtureRegistrationsWithMocks_returnsFixtureWithMocks() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        when(testFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

        final var result = defaultTestFixtureConstructor.buildTestFixture(Collections.emptyList(), testSuiteMock, environmentMock, true);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Nested
    @DisplayName("fixture registration handling")
    class FixtureRegistrationHandlingTest {
        @Test
        void buildTestFixture_handlesUnknownFixtureRegistration_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var unknownFixtureRegistrationMock = mock(FixtureRegistration.class);

            final var fixtureRegistration = List.of(unknownFixtureRegistrationMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistration, testSuiteMock, environmentMock, false));

            assertEquals("Unknown type of FixtureRegistration", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_whenFunctionLibraryInterpreterThrows_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            when(functionLibraryInterpreter.getFunctionLibrary(FunctionLibrary.TEMPORAL)).thenThrow(new RuntimeException("no library here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(saplFunctionLibraryMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, false));

            assertEquals("no library here", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_whenRegisterFunctionLibraryThrowsForSaplFunctionLibrary_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            final var libraryMock = mock(java.lang.Object.class);
            when(functionLibraryInterpreter.getFunctionLibrary(FunctionLibrary.TEMPORAL)).thenReturn(libraryMock);

            when(testFixtureMock.registerFunctionLibrary(libraryMock)).thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(saplFunctionLibraryMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, false));

            assertEquals("failed to register library", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_handlesSaplFunctionLibrary_returnsFixtureWithoutMocks() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            final var libraryMock = mock(java.lang.Object.class);
            when(functionLibraryInterpreter.getFunctionLibrary(FunctionLibrary.TEMPORAL)).thenReturn(libraryMock);

            final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
            when(testFixtureMock.constructTestCase()).thenReturn(givenOrWhenStepMock);

            final var result = defaultTestFixtureConstructor.buildTestFixture(List.of(saplFunctionLibraryMock), testSuiteMock, environmentMock, false);

            assertEquals(givenOrWhenStepMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(libraryMock);
        }

        @Test
        void buildTestFixture_whenReflectionHelperThrowsForCustomFunctionLibrary_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenThrow(new RuntimeException("no class here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(customFunctionLibrary);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true));

            assertEquals("no class here", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_whenRegisterFunctionLibraryThrowsForCustomFunctionLibrary_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            when(testFixtureMock.registerFunctionLibrary(libraryMock)).thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(customFunctionLibrary);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true));

            assertEquals("failed to register library", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_handlesCustomFunctionLibrary_returnsFixtureWithMocks() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
            when(testFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

            final var result = defaultTestFixtureConstructor.buildTestFixture(List.of(customFunctionLibrary), testSuiteMock, environmentMock, true);

            assertEquals(givenOrWhenStepMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(libraryMock);
        }

        @Test
        void buildTestFixture_whenReflectionHelperThrowsForPIP_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenThrow(new RuntimeException("no class here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(pip);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true));

            assertEquals("no class here", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_whenRegisterPIPThrows_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            when(testFixtureMock.registerPIP(pipMock)).thenThrow(new InitializationException("failed to register PIP"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(pip);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true));

            assertEquals("failed to register PIP", exception.getCause().getMessage());
        }

        @Test
        void buildTestFixture_handlesPIP_returnsFixtureWithMocks() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock, environmentMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);
            when(testFixtureMock.constructTestCaseWithMocks()).thenReturn(givenOrWhenStepMock);

            final var result = defaultTestFixtureConstructor.buildTestFixture(List.of(pip), testSuiteMock, environmentMock, true);

            assertEquals(givenOrWhenStepMock, result);

            verify(testFixtureMock, times(1)).registerPIP(pipMock);
        }
    }

}