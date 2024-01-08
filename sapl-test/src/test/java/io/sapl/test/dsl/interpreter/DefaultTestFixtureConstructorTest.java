/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.CustomFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.SaplFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.TestSuite;
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
    private TestSuiteInterpreter          testSuiteInterpreterMock;
    @Mock
    private FunctionLibraryInterpreter    functionLibraryInterpreter;
    @Mock
    private ReflectionHelper              reflectionHelperMock;
    @Mock
    private SaplTestFixture               testFixtureMock;
    @Mock
    private TestSuite                     testSuiteMock;
    @InjectMocks
    private DefaultTestFixtureConstructor defaultTestFixtureConstructor;

    @Test
    void constructTestFixture_testSuiteInterpreterThrows_throwsSaplTestException() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenThrow(new SaplTestException("no fixture here"));

        final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(null, testSuiteMock));

        assertEquals("no fixture here", exception.getMessage());
    }

    @Test
    void constructTestFixture_testSuiteInterpreterReturnsNull_throwsSaplTestException() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(null, testSuiteMock));

        assertEquals("could not build test fixture", exception.getMessage());
    }

    @Test
    void constructTestFixture_handlesNullFixtureRegistrations_returnsFixture() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(null, testSuiteMock);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_handlesEmptyFixtureRegistrations_returnsFixture() {
        when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(Collections.emptyList(), testSuiteMock);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Nested
    @DisplayName("fixture registration handling")
    class FixtureRegistrationHandlingTest {
        @Test
        void constructTestFixture_handlesUnknownFixtureRegistration_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var unknownFixtureRegistrationMock = mock(FixtureRegistration.class);

            final var fixtureRegistration = List.of(unknownFixtureRegistrationMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistration, testSuiteMock));

            assertEquals("Unknown type of FixtureRegistration", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_whenFunctionLibraryInterpreterThrows_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            when(functionLibraryInterpreter.getFunctionLibrary(FunctionLibrary.TEMPORAL)).thenThrow(new RuntimeException("no library here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(saplFunctionLibraryMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("no library here", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_whenRegisterFunctionLibraryThrowsForSaplFunctionLibrary_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            doReturn(TemporalFunctionLibrary.class).when(functionLibraryInterpreter).getFunctionLibrary(FunctionLibrary.TEMPORAL);

            when(testFixtureMock.registerFunctionLibrary(TemporalFunctionLibrary.class)).thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(saplFunctionLibraryMock);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("failed to register library", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_handlesSaplFunctionLibrary_returnsFixtureWithoutMocks() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            doReturn(TemporalFunctionLibrary.class).when(functionLibraryInterpreter).getFunctionLibrary(FunctionLibrary.TEMPORAL);

            final var result = defaultTestFixtureConstructor.constructTestFixture(List.of(saplFunctionLibraryMock), testSuiteMock);

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(TemporalFunctionLibrary.class);
        }

        @Test
        void constructTestFixture_whenReflectionHelperThrowsForCustomFunctionLibrary_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenThrow(new RuntimeException("no class here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(customFunctionLibrary);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("no class here", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_whenRegisterFunctionLibraryThrowsForCustomFunctionLibrary_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            when(testFixtureMock.registerFunctionLibrary(libraryMock)).thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(customFunctionLibrary);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("failed to register library", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_handlesCustomFunctionLibrary_returnsFixture() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            final var result = defaultTestFixtureConstructor.constructTestFixture(List.of(customFunctionLibrary), testSuiteMock);

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(libraryMock);
        }

        @Test
        void constructTestFixture_whenReflectionHelperThrowsForPIP_throwsSaplTestException() {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenThrow(new RuntimeException("no class here"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(pip);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("no class here", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_whenRegisterPIPThrows_throwsSaplTestException() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            when(testFixtureMock.registerPIP(pipMock)).thenThrow(new InitializationException("failed to register PIP"));

            final var fixtureRegistrations = List.<FixtureRegistration>of(pip);

            final var exception = assertThrows(SaplTestException.class, () -> defaultTestFixtureConstructor.constructTestFixture(fixtureRegistrations, testSuiteMock));

            assertEquals("failed to register PIP", exception.getCause().getMessage());
        }

        @Test
        void constructTestFixture_handlesPIP_returnsFixture() throws InitializationException {
            when(testSuiteInterpreterMock.getFixtureFromTestSuite(testSuiteMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            final var result = defaultTestFixtureConstructor.constructTestFixture(List.of(pip), testSuiteMock);

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerPIP(pipMock);
        }
    }
}
