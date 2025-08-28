/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.attributes.pips.http.HttpPolicyInformationPoint;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.ImportType;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;

@ExtendWith(MockitoExtension.class)
class BaseTestAdapterTests {
    @Mock
    protected TestProvider        testProviderMock;
    @Mock
    protected SaplTestInterpreter saplTestInterpreterMock;

    protected BaseTestAdapter<TestContainer> baseTestAdapter;

    protected final MockedStatic<TestProviderFactory>        testProviderFactoryMockedStatic        = mockStatic(
            TestProviderFactory.class);
    protected final MockedStatic<SaplTestInterpreterFactory> saplTestInterpreterFactoryMockedStatic = mockStatic(
            SaplTestInterpreterFactory.class);
    protected final MockedStatic<DocumentHelper>             documentHelperMockedStatic             = mockStatic(
            DocumentHelper.class);
    protected final MockedStatic<TestContainer>              testContainerMockedStatic              = mockStatic(
            TestContainer.class);

    @AfterEach
    void tearDown() {
        testProviderFactoryMockedStatic.close();
        saplTestInterpreterFactoryMockedStatic.close();
        documentHelperMockedStatic.close();
        testContainerMockedStatic.close();
    }

    private void buildInstanceOfBaseTestAdapterWithDefaultConstructor(
            final Map<ImportType, Map<String, Object>> fixtureRegistrations) {
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null, null)).thenReturn(testProviderMock);
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create)
                .thenReturn(saplTestInterpreterMock);

        baseTestAdapter = new BaseTestAdapter<>() {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer,
                    final boolean shouldSetTestSourceUri) {
                return testContainer;
            }

            @Override
            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                return fixtureRegistrations;
            }

        };

        testProviderFactoryMockedStatic.verify(() -> TestProviderFactory.create(null, null), times(1));
    }

    private void buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter() {
        final var stepConstructorMock = mock(StepConstructor.class);

        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(stepConstructorMock))
                .thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(stepConstructorMock, saplTestInterpreterMock) {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer,
                    final boolean shouldSetTestSourceUri) {
                return testContainer;
            }

            @Override
            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                return null;
            }
        };
    }

    private void buildInstanceOfBaseTestAdapterWithCustomUnitTestAndIntegrationTestPolicyResolver() {
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create)
                .thenReturn(saplTestInterpreterMock);

        final var unitTestPolicyResolverMock    = mock(UnitTestPolicyResolver.class);
        final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);

        testProviderFactoryMockedStatic
                .when(() -> TestProviderFactory.create(unitTestPolicyResolverMock, integrationTestPolicyResolver))
                .thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(unitTestPolicyResolverMock, integrationTestPolicyResolver) {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer,
                    final boolean shouldSetTestSourceUri) {
                return testContainer;
            }

            @Override
            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                return null;
            }
        };
    }

    private TestContainer mockTestContainerCreation(final String identifier, final List<TestContainer> testContainers) {
        final var testContainerMock = mock(TestContainer.class);
        // Required since Spotbugs complains about unused return value from method call
        // with no side effects here
        final Callable<TestContainer> expectedCall = () -> TestContainer.from(identifier, testContainers);
        testContainerMockedStatic.when(expectedCall::call).thenReturn(testContainerMock);
        return testContainerMock;
    }

    @Test
    void createTest_withNullFilename_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest(null));

        assertEquals("provided filename is null", exception.getMessage());
    }

    @Test
    void createTest_withFilenameAndDocumentHelperThrows_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo"))
                .thenThrow(new SaplTestException("no file here"));
        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest("foo"));

        assertEquals("no file here", exception.getMessage());
    }

    @Test
    void createTest_withFilenameAndDocumentHelperReturnsNull_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn(null);
        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest("foo"));

        assertEquals("file does not exist", exception.getMessage());
    }

    private static Stream<Arguments> invalidInputCombinationsArgumentSource() {
        return Stream.of(Arguments.of(null, "foo"), Arguments.of("identifier", null), Arguments.of(null, null));
    }

    @ParameterizedTest
    @MethodSource("invalidInputCombinationsArgumentSource")
    void createTest_withInvalidInputCombinations_throwsSaplTestException(final String identifier, final String input) {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        final var exception = assertThrows(SaplTestException.class,
                () -> baseTestAdapter.createTest(identifier, input));

        assertEquals("identifier or input is null", exception.getMessage());
    }

    @Test
    void createTest_withFilenameBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(eq(saplTestMock), any())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(eq(saplTestMock), any())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withFilenameBuildsTestContainerUsingCustomStepConstructorAndSaplTestInterpreter_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter();

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(eq(saplTestMock), any())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainerUsingCustomStepConstructorAndSaplTestInterpreter_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter();

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(eq(saplTestMock), any())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainerUsingCustomUnitTestAndIntegrationTestPolicyResolver_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithCustomUnitTestAndIntegrationTestPolicyResolver();

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(eq(saplTestMock), any())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }

    @Nested
    @DisplayName("FixtureRegistration handling")
    class FixtureRegistrationHandlingTests {
        @Test
        void createTest_withIdentifierAndInputPassesFixtureRegistrationsToTestProvider_returnsMappedTestContainer() {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final Map<ImportType, Map<String, Object>> fixtureRegistrations = new EnumMap<>(ImportType.class);
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var testContainers = Collections.<TestContainer>emptyList();

            final var testContainerMock = mockTestContainerCreation("foo", testContainers);

            when(testProviderMock.buildTests(saplTestMock, fixtureRegistrations)).thenReturn(testContainers);

            final var result = baseTestAdapter.createTest("foo", "input");

            assertEquals(testContainerMock, result);

            verify(testProviderMock, times(1)).buildTests(saplTestMock, fixtureRegistrations);
        }

        @Test
        void createTest_withFilenameAndInputPassesFixtureRegistrationsToTestProvider_returnsMappedTestContainer() {
            documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            buildInstanceOfBaseTestAdapterWithDefaultConstructor(null);

            final var testContainers    = Collections.<TestContainer>emptyList();
            final var testContainerMock = mockTestContainerCreation("foo", testContainers);

            when(testProviderMock.buildTests(saplTestMock, null)).thenReturn(testContainers);

            final var result = baseTestAdapter.createTest("foo");

            assertEquals(testContainerMock, result);

            verify(testProviderMock, times(1)).buildTests(saplTestMock, null);
        }

        private static Stream<Arguments> invalidNameToObjectCombinations() {
            return Stream.of(Arguments.of(ImportType.PIP, null, "dummy"), Arguments.of(ImportType.PIP, "name", null),
                    Arguments.of(ImportType.PIP, null, null), Arguments.of(ImportType.STATIC_PIP, null, "dummy"),
                    Arguments.of(ImportType.STATIC_PIP, "name", null), Arguments.of(ImportType.STATIC_PIP, null, null),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, null, "dummy"),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, "name", null),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, null, null),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, null, "dummy"),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, "name", null),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, null, null));
        }

        @ParameterizedTest
        @MethodSource("invalidNameToObjectCombinations")
        void createTest_nullNameOrRegistration_throwsSaplTestException(final ImportType fixtureRegistrationType,
                final String name, final Object registration) {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final var fixtureRegistrations = Collections.singletonMap(fixtureRegistrationType,
                    Collections.singletonMap(name, registration));
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var exception = assertThrows(SaplTestException.class,
                    () -> baseTestAdapter.createTest("foo", "input"));

            assertEquals("Map contains null key or value", exception.getMessage());
        }

        private static Stream<Arguments> importTypeToRegistrationMap() {
            return Stream.of(Arguments.of(ImportType.PIP, null), Arguments.of(ImportType.PIP, Collections.emptyMap()),
                    Arguments.of(ImportType.STATIC_PIP, null),
                    Arguments.of(ImportType.STATIC_PIP, Collections.emptyMap()),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, null),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, Collections.emptyMap()),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, null),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, Collections.emptyMap()));
        }

        @ParameterizedTest
        @MethodSource("importTypeToRegistrationMap")
        void createTest_forTypeWithNullOrEmptyRegistrations_returnsMappedTestContainer(
                final ImportType fixtureRegistrationType, final Map<String, Object> registrationMap) {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final var fixtureRegistrations = Collections.singletonMap(fixtureRegistrationType, registrationMap);
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var testContainers    = Collections.<TestContainer>emptyList();
            final var testContainerMock = mockTestContainerCreation("foo", testContainers);

            when(testProviderMock.buildTests(saplTestMock, fixtureRegistrations)).thenReturn(testContainers);

            final var result = baseTestAdapter.createTest("foo", "input");

            assertEquals(testContainerMock, result);
        }

        private static Stream<Arguments> importTypeToRegistrationAndExpectedAnnotationName() {
            return Stream.of(Arguments.of(ImportType.PIP, "PIP", "PolicyInformationPoint"),
                    Arguments.of(ImportType.STATIC_PIP, String.class, "PolicyInformationPoint"),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, "FunctionLibrary", "FunctionLibrary"),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, String.class, "FunctionLibrary"));
        }

        @ParameterizedTest
        @MethodSource("importTypeToRegistrationAndExpectedAnnotationName")
        void createTest_forRegistrationWithMissingAnnotation_throwsSaplTestExeption(
                final ImportType fixtureRegistrationType, final Object registration, final String annotationName) {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final var fixtureRegistrations = Map.of(fixtureRegistrationType, Map.of("foo", registration));
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var exception = assertThrows(SaplTestException.class,
                    () -> baseTestAdapter.createTest("foo", "input"));

            assertEquals("Passed object is missing the %s annotation".formatted(annotationName),
                    exception.getMessage());
        }

        private static Stream<Arguments> staticImportTypeToNonClassRegistration() {
            return Stream.of(Arguments.of(ImportType.STATIC_PIP, "nonStatic"),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, "nonStatic"));
        }

        @ParameterizedTest
        @MethodSource("staticImportTypeToNonClassRegistration")
        void createTest_handlesStaticRegistrationWithNonClassType_throwsSaplTestExeption(
                final ImportType fixtureRegistrationType, final Object registration) {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final var fixtureRegistrations = Map.of(fixtureRegistrationType, Map.of("foo", registration));
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var exception = assertThrows(SaplTestException.class,
                    () -> baseTestAdapter.createTest("foo", "input"));

            assertEquals("Static registrations require a class type", exception.getMessage());
        }

        private static Stream<Arguments> importTypeToValidRegistration() {
            return Stream.of(Arguments.of(ImportType.PIP, mock(HttpPolicyInformationPoint.class)),
                    Arguments.of(ImportType.STATIC_PIP, HttpPolicyInformationPoint.class),
                    Arguments.of(ImportType.FUNCTION_LIBRARY, mock(FilterFunctionLibrary.class)),
                    Arguments.of(ImportType.STATIC_FUNCTION_LIBRARY, FilterFunctionLibrary.class));
        }

        @ParameterizedTest
        @MethodSource("importTypeToValidRegistration")
        void createTest_handlesValidRegistrations_returnsMappedTestContainer(final ImportType importType,
                final Object registration) {
            final var saplTestMock = mock(SAPLTest.class);
            when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

            final var fixtureRegistrations = Map.of(importType, Map.of("foo", registration));
            buildInstanceOfBaseTestAdapterWithDefaultConstructor(fixtureRegistrations);

            final var testContainers    = Collections.<TestContainer>emptyList();
            final var testContainerMock = mockTestContainerCreation("foo", testContainers);

            final var result = baseTestAdapter.createTest("foo", "input");

            assertEquals(testContainerMock, result);
        }
    }
}
