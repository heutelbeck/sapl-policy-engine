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
import io.sapl.test.grammar.sapltest.CustomFunctionLibrary;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.FixtureRegistration;
import io.sapl.test.grammar.sapltest.FunctionLibrary;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.sapltest.Pip;
import io.sapl.test.grammar.sapltest.SaplFunctionLibrary;
import io.sapl.test.integration.SaplIntegrationTestFixture;
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
class DefaultTestFixtureConstructorTests {
    @Mock
    protected DocumentInterpreter           documentInterpreterMock;
    @Mock
    PdpConfigurationHandler                 pdpConfigurationHandlerMock;
    @Mock
    protected FunctionLibraryInterpreter    functionLibraryInterpreterMock;
    @Mock
    protected ReflectionHelper              reflectionHelperMock;
    @InjectMocks
    protected DefaultTestFixtureConstructor defaultTestFixtureConstructor;

    @Mock
    protected SaplTestFixture testFixtureMock;
    @Mock
    protected Given           requirementGivenMock;
    @Mock
    protected Given           scenarioGivenMock;

    @Test
    void constructTestFixture_withNullGiven_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultTestFixtureConstructor.constructTestFixture(null, null));

        assertEquals("Given is null", exception.getMessage());
    }

    @Test
    void constructTestFixture_documentInterpreterReturnsNull_throwsSaplTestException() {
        final var documentMock = mock(Document.class);

        when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, null));

        assertEquals("TestFixture is null", exception.getMessage());
    }

    @Test
    void constructTestFixture_usesRequirementGivenWhenScenarioGivenIsNull_returnsFixture() {
        final var documentMock = mock(Document.class);

        when(requirementGivenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(requirementGivenMock, null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(pdpConfigurationHandlerMock);
        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_handlesNullFixtureRegistrations_returnsFixture() {
        final var documentMock = mock(Document.class);

        when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(pdpConfigurationHandlerMock);
        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_handlesEmptyFixtureRegistrations_returnsFixture() {
        final var documentMock = mock(Document.class);

        when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock,
                Collections.emptyList());

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(pdpConfigurationHandlerMock);
        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_callsPdpConfigurationInterpreterWhenIntegrationTestFixtureIsReturned_returnsFixture() {
        final var integrationTestFixture = mock(SaplIntegrationTestFixture.class);

        final var documentMock              = mock(Document.class);
        final var pdpVariablesMock          = mock(PdpVariables.class);
        final var pdpCombiningAlgorithmMock = mock(PdpCombiningAlgorithm.class);

        when(scenarioGivenMock.getDocument()).thenReturn(documentMock);
        when(scenarioGivenMock.getPdpVariables()).thenReturn(pdpVariablesMock);
        when(scenarioGivenMock.getPdpCombiningAlgorithm()).thenReturn(pdpCombiningAlgorithmMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(integrationTestFixture);

        when(pdpConfigurationHandlerMock.applyPdpConfigurationToFixture(integrationTestFixture, pdpVariablesMock,
                pdpCombiningAlgorithmMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock,
                Collections.emptyList());

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Nested
    @DisplayName("fixture registration handling")
    class FixtureRegistrationHandlingTests {
        @Test
        void constructTestFixture_handlesUnknownFixtureRegistration_throwsSaplTestException() {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var unknownFixtureRegistrationMock = mock(FixtureRegistration.class);

            final var fixtureRegistrations = List.<GivenStep>of(unknownFixtureRegistrationMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, fixtureRegistrations));

            assertEquals("Unknown type of FixtureRegistration", exception.getMessage());
        }

        @Test
        void constructTestFixture_whenRegisterFunctionLibraryThrowsForSaplFunctionLibrary_throwsInitializationException()
                throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            doReturn(TemporalFunctionLibrary.class).when(functionLibraryInterpreterMock)
                    .getFunctionLibrary(FunctionLibrary.TEMPORAL);

            when(testFixtureMock.registerFunctionLibrary(TemporalFunctionLibrary.class))
                    .thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<GivenStep>of(saplFunctionLibraryMock);

            final var exception = assertThrows(InitializationException.class,
                    () -> defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, fixtureRegistrations));

            assertEquals("failed to register library", exception.getMessage());
        }

        @Test
        void constructTestFixture_handlesSaplFunctionLibrary_returnsFixture() throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var saplFunctionLibraryMock = mock(SaplFunctionLibrary.class);
            when(saplFunctionLibraryMock.getLibrary()).thenReturn(FunctionLibrary.TEMPORAL);

            doReturn(TemporalFunctionLibrary.class).when(functionLibraryInterpreterMock)
                    .getFunctionLibrary(FunctionLibrary.TEMPORAL);

            final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock,
                    List.of(saplFunctionLibraryMock));

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(TemporalFunctionLibrary.class);
        }

        @Test
        void constructTestFixture_whenRegisterFunctionLibraryThrowsForCustomFunctionLibrary_throwsInitializationException()
                throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            when(testFixtureMock.registerFunctionLibrary(libraryMock))
                    .thenThrow(new InitializationException("failed to register library"));

            final var fixtureRegistrations = List.<GivenStep>of(customFunctionLibrary);

            final var exception = assertThrows(InitializationException.class,
                    () -> defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, fixtureRegistrations));

            assertEquals("failed to register library", exception.getMessage());
        }

        @Test
        void constructTestFixture_handlesCustomFunctionLibrary_returnsFixture() throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var customFunctionLibrary = mock(CustomFunctionLibrary.class);
            when(customFunctionLibrary.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var libraryMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(libraryMock);

            final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock,
                    List.of(customFunctionLibrary));

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerFunctionLibrary(libraryMock);
        }

        @Test
        void constructTestFixture_whenRegisterPIPThrows_throwsInitializationException() throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            when(testFixtureMock.registerPIP(pipMock)).thenThrow(new InitializationException("failed to register PIP"));

            final var fixtureRegistrations = List.<GivenStep>of(pip);

            final var exception = assertThrows(InitializationException.class,
                    () -> defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, fixtureRegistrations));

            assertEquals("failed to register PIP", exception.getMessage());
        }

        @Test
        void constructTestFixture_handlesPIP_returnsFixture() throws InitializationException {
            final var documentMock = mock(Document.class);

            when(scenarioGivenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

            final var pip = mock(Pip.class);
            when(pip.getFqn()).thenReturn("io.my.classpath.ClassName");

            final var pipMock = mock(java.lang.Object.class);
            when(reflectionHelperMock.constructInstanceOfClass("io.my.classpath.ClassName")).thenReturn(pipMock);

            final var result = defaultTestFixtureConstructor.constructTestFixture(scenarioGivenMock, List.of(pip));

            assertEquals(testFixtureMock, result);

            verify(testFixtureMock, times(1)).registerPIP(pipMock);
        }
    }
}
