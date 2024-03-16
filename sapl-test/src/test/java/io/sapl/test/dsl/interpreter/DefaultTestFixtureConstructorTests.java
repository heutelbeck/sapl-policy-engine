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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.pip.TimePolicyInformationPoint;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.Attribute;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.Function;
import io.sapl.test.grammar.sapltest.Given;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.Import;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTestFixtureConstructorTests {
    @Mock
    protected DocumentInterpreter           documentInterpreterMock;
    @Mock
    PdpConfigurationHandler                 pdpConfigurationHandlerMock;
    @InjectMocks
    protected DefaultTestFixtureConstructor defaultTestFixtureConstructor;
    @Mock
    protected SaplTestFixture               testFixtureMock;
    @Mock
    protected Given                         givenMock;

    @Test
    void constructTestFixture_withNullGiven_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> defaultTestFixtureConstructor.constructTestFixture(null, null, null));

        assertEquals("Given is null", exception.getMessage());
    }

    @Test
    void constructTestFixture_documentInterpreterReturnsNull_throwsSaplTestException() {
        final var documentMock = mock(Document.class);

        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, null, null));

        assertEquals("TestFixture is null", exception.getMessage());
    }

    @Test
    void constructTestFixture_handlesNullImports_returnsFixture() {
        final var documentMock = mock(Document.class);

        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, null, null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(pdpConfigurationHandlerMock);
        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_handlesEmptyImports_returnsFixture() {
        final var documentMock = mock(Document.class);

        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, Collections.emptyList(), null);

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

        when(givenMock.getDocument()).thenReturn(documentMock);
        when(givenMock.getPdpVariables()).thenReturn(pdpVariablesMock);
        when(givenMock.getPdpCombiningAlgorithm()).thenReturn(pdpCombiningAlgorithmMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(integrationTestFixture);

        when(pdpConfigurationHandlerMock.applyPdpConfigurationToFixture(integrationTestFixture, pdpVariablesMock,
                pdpCombiningAlgorithmMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, Collections.emptyList(), null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_doesNoRegistrationWhenGivenStepsIsNull_returnsFixture() {
        final var documentMock = mock(Document.class);
        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, null, null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_doesNoRegistrationWhenGivenStepsIsEmpty_returnsFixture() {
        final var documentMock = mock(Document.class);
        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, Collections.emptyList(), null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Test
    void constructTestFixture_doesNoRegistrationWhenGivenStepsOnlyContainsNonImportTypes_returnsFixture() {
        final var documentMock = mock(Document.class);
        when(givenMock.getDocument()).thenReturn(documentMock);

        when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);

        final var functionMock  = mock(Function.class);
        final var attributeMock = mock(Attribute.class);

        final var givenSteps = List.<GivenStep>of(functionMock, attributeMock);

        final var result = defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, null);

        assertEquals(testFixtureMock, result);

        verifyNoInteractions(testFixtureMock);
    }

    @Nested
    @DisplayName("fixture registration handling")
    class ImportHandlingTests {

        private Import buildImport(final String input) {
            return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getImportRule, Import.class);
        }

        @BeforeEach
        void setUp() {
            final var documentMock = mock(Document.class);
            when(givenMock.getDocument()).thenReturn(documentMock);

            when(documentInterpreterMock.getFixtureFromDocument(documentMock)).thenReturn(testFixtureMock);
        }

        @Nested
        @DisplayName("Error cases")
        class ErrorCases {
            @Test
            void constructTestFixture_handlesNullGivenStep_throwsSaplTestException() {
                final var attributeMock = mock(Attribute.class);

                final var givenSteps = Arrays.<GivenStep>asList(attributeMock, null);

                final var exception = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, null));

                assertEquals("GivenStep is null", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesImportWithNullType_throwsSaplTestException() {
                final var importWithNullType = mock(Import.class);

                when(importWithNullType.getType()).thenReturn(null);

                final var givenSteps = List.<GivenStep>of(importWithNullType);

                final var exception = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, null));

                assertEquals("Invalid Import", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesImportWithNullIdentifier_throwsSaplTestException() {
                final var importWithNullIdentifier = mock(Import.class);

                when(importWithNullIdentifier.getType()).thenReturn(ImportType.PIP);
                when(importWithNullIdentifier.getIdentifier()).thenReturn(null);

                final var givenSteps = List.<GivenStep>of(importWithNullIdentifier);

                final var exception = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, null));

                assertEquals("Invalid Import", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @ParameterizedTest
            @EnumSource(value = ImportType.class)
            void constructTestFixture_handlesNullRegistrations_throwsSaplTestException(final ImportType importType) {
                final var importMock = mock(Import.class);

                when(importMock.getType()).thenReturn(importType);
                when(importMock.getIdentifier()).thenReturn("foo");

                final var givenSteps = List.<GivenStep>of(importMock);

                final var exception = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, null));

                assertEquals("No FixtureRegistrations present, please check your setup", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @ParameterizedTest
            @EnumSource(value = ImportType.class)
            void constructTestFixture_handlesEmptyRegistrations_throwsSaplTestException(final ImportType importType) {
                final var importMock = mock(Import.class);

                when(importMock.getType()).thenReturn(importType);
                when(importMock.getIdentifier()).thenReturn("foo");

                final var givenSteps = List.<GivenStep>of(importMock);

                final var registrations = Collections.<ImportType, Map<String, Object>>emptyMap();
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("No FixtureRegistrations present, please check your setup", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @ParameterizedTest
            @EnumSource(value = ImportType.class)
            void constructTestFixture_handlesRegistrationsWithTypeSpecificRegistrationsBeingNull_throwsSaplTestException(
                    final ImportType importType) {
                final var importMock = mock(Import.class);

                when(importMock.getType()).thenReturn(importType);
                when(importMock.getIdentifier()).thenReturn("foo");

                final var givenSteps = List.<GivenStep>of(importMock);

                final var registrations = Collections.<ImportType, Map<String, Object>>singletonMap(null, null);
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("No registrations for type \"%s\" found".formatted(importType), exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @ParameterizedTest
            @EnumSource(value = ImportType.class)
            void constructTestFixture_handlesRegistrationsWithTypeSpecificRegistrationsBeingEmpty_throwsSaplTestException(
                    final ImportType importType) {
                final var importMock = mock(Import.class);

                when(importMock.getType()).thenReturn(importType);
                when(importMock.getIdentifier()).thenReturn("foo");

                final var givenSteps = List.<GivenStep>of(importMock);

                final var registrations = Collections.<ImportType, Map<String, Object>>singletonMap(importType,
                        Collections.emptyMap());
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("No registrations for type \"%s\" found".formatted(importType), exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @ParameterizedTest
            @EnumSource(value = ImportType.class)
            void constructTestFixture_handlesRegistrationsWithTypeSpecificRegistrationsHavingNullRegistrationForIdentifier_throwsSaplTestException(
                    final ImportType importType) {
                final var importMock = mock(Import.class);

                when(importMock.getType()).thenReturn(importType);
                when(importMock.getIdentifier()).thenReturn("foo");

                final var givenSteps = List.<GivenStep>of(importMock);

                final var registrations = Collections.singletonMap(importType, Collections.singletonMap("foo", null));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("No \"%s\" registration for name \"foo\" found".formatted(importType),
                        exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }
        }

        @Nested
        @DisplayName("PIP")
        class PIP {
            @Test
            void constructTestFixture_handlesPipWithMissingAnnotation_throwsSaplTestException() {
                final var pipImport = buildImport("pip \"foo\"");

                final var givenSteps = List.<GivenStep>of(pipImport);

                final var registrations = Collections.singletonMap(ImportType.PIP,
                        Collections.<String, Object>singletonMap("foo", "abc"));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("registration with name \"foo\" is missing the \"PolicyInformationPoint\" annotation",
                        exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesValidPip_returnsFixture() throws InitializationException {
                final var pipImport = buildImport("pip \"foo\"");
                final var pipMock   = mock(TimePolicyInformationPoint.class);

                final var givenSteps = List.<GivenStep>of(pipImport);

                final var registrations = Collections.singletonMap(ImportType.PIP,
                        Collections.<String, Object>singletonMap("foo", pipMock));
                final var result        = defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps,
                        registrations);

                assertEquals(testFixtureMock, result);

                verify(testFixtureMock, times(1)).registerPIP(pipMock);
            }
        }

        @Nested
        @DisplayName("StaticPip")
        class StaticPip {
            @Test
            void constructTestFixture_handlesStaticPipWithNonClassType_throwsSaplTestException() {
                final var staticPipImport = buildImport("static-pip \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticPipImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_PIP,
                        Collections.<String, Object>singletonMap("foo", "abc"));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("Static \"PolicyInformationPoint\" registration with name \"foo\" is not a class type",
                        exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesStaticPipWithClassTypeAndMissingAnnotation_throwsSaplTestException() {
                final var staticPipImport = buildImport("static-pip \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticPipImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_PIP,
                        Collections.<String, Object>singletonMap("foo", Object.class));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("Class is missing the \"PolicyInformationPoint\" annotation", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesValidStaticPip_returnsFixture() throws InitializationException {
                final var staticPipImport = buildImport("static-pip \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticPipImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_PIP,
                        Collections.<String, Object>singletonMap("foo", TimePolicyInformationPoint.class));
                final var result        = defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps,
                        registrations);

                assertEquals(testFixtureMock, result);

                verify(testFixtureMock, times(1)).registerPIP(TimePolicyInformationPoint.class);
            }
        }

        @Nested
        @DisplayName("FunctionLibrary")
        class FunctionLibrary {
            @Test
            void constructTestFixture_handlesPipWithMissingAnnotation_throwsSaplTestException() {
                final var functionLibraryImport = buildImport("function-library \"foo\"");

                final var givenSteps = List.<GivenStep>of(functionLibraryImport);

                final var registrations = Collections.singletonMap(ImportType.FUNCTION_LIBRARY,
                        Collections.<String, Object>singletonMap("foo", "abc"));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("registration with name \"foo\" is missing the \"FunctionLibrary\" annotation",
                        exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesValidPip_returnsFixture() throws InitializationException {
                final var functionLibraryImport = buildImport("function-library \"foo\"");
                final var functionLibraryMock   = mock(FilterFunctionLibrary.class);

                final var givenSteps = List.<GivenStep>of(functionLibraryImport);

                final var registrations = Collections.singletonMap(ImportType.FUNCTION_LIBRARY,
                        Collections.<String, Object>singletonMap("foo", functionLibraryMock));
                final var result        = defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps,
                        registrations);

                assertEquals(testFixtureMock, result);

                verify(testFixtureMock, times(1)).registerFunctionLibrary(functionLibraryMock);
            }
        }

        @Nested
        @DisplayName("StaticFunctionLibrary")
        class StaticFunctionLibrary {
            @Test
            void constructTestFixture_handlesStaticFunctionLibraryWithNonClassType_throwsSaplTestException() {
                final var staticFunctionLibraryImport = buildImport("static-function-library \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticFunctionLibraryImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_FUNCTION_LIBRARY,
                        Collections.<String, Object>singletonMap("foo", "abc"));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("Static \"FunctionLibrary\" registration with name \"foo\" is not a class type",
                        exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesStaticFunctionLibraryWithClassTypeAndMissingAnnotation_throwsSaplTestException() {
                final var staticFunctionLibraryImport = buildImport("static-function-library \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticFunctionLibraryImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_FUNCTION_LIBRARY,
                        Collections.<String, Object>singletonMap("foo", Object.class));
                final var exception     = assertThrows(SaplTestException.class,
                        () -> defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps, registrations));

                assertEquals("Class is missing the \"FunctionLibrary\" annotation", exception.getMessage());

                verifyNoInteractions(testFixtureMock);
            }

            @Test
            void constructTestFixture_handlesValidStaticFunctionLibrary_returnsFixture()
                    throws InitializationException {
                final var staticFunctionLibraryImport = buildImport("static-function-library \"foo\"");

                final var givenSteps = List.<GivenStep>of(staticFunctionLibraryImport);

                final var registrations = Collections.singletonMap(ImportType.STATIC_FUNCTION_LIBRARY,
                        Collections.<String, Object>singletonMap("foo", FilterFunctionLibrary.class));
                final var result        = defaultTestFixtureConstructor.constructTestFixture(givenMock, givenSteps,
                        registrations);

                assertEquals(testFixtureMock, result);

                verify(testFixtureMock, times(1)).registerPIP(FilterFunctionLibrary.class);
            }
        }
    }
}
