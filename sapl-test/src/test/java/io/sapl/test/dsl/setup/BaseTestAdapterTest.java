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

package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseTestAdapterTests {
    @Mock
    private TestProvider        testProviderMock;
    @Mock
    private SaplTestInterpreter saplTestInterpreterMock;

    private BaseTestAdapter<TestContainer> baseTestAdapter;

    private final MockedStatic<TestProviderFactory>        testProviderFactoryMockedStatic        = mockStatic(
            TestProviderFactory.class);
    private final MockedStatic<SaplTestInterpreterFactory> saplTestInterpreterFactoryMockedStatic = mockStatic(
            SaplTestInterpreterFactory.class);
    private final MockedStatic<DocumentHelper>             documentHelperMockedStatic             = mockStatic(
            DocumentHelper.class);
    private final MockedStatic<TestContainer>              testContainerMockedStatic              = mockStatic(
            TestContainer.class);

    @AfterEach
    void tearDown() {
        testProviderFactoryMockedStatic.close();
        saplTestInterpreterFactoryMockedStatic.close();
        documentHelperMockedStatic.close();
        testContainerMockedStatic.close();
    }

    private void buildInstanceOfBaseTestAdapterWithDefaultConstructor() {
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null, null)).thenReturn(testProviderMock);
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create)
                .thenReturn(saplTestInterpreterMock);

        baseTestAdapter = new BaseTestAdapter<>() {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
                return testContainer;
            }
        };
    }

    private void buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter() {
        final var stepConstructorMock = mock(StepConstructor.class);

        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(stepConstructorMock))
                .thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(stepConstructorMock, saplTestInterpreterMock) {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer) {
                return testContainer;
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
            protected TestContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer) {
                return testContainer;
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
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest(null));

        assertEquals("provided filename is null", exception.getMessage());
    }

    @Test
    void createTest_withFilenameAndDocumentHelperThrows_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo"))
                .thenThrow(new SaplTestException("no file here"));
        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest("foo"));

        assertEquals("no file here", exception.getMessage());
    }

    @Test
    void createTest_withFilenameAndDocumentHelperReturnsNull_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

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
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class,
                () -> baseTestAdapter.createTest(identifier, input));

        assertEquals("identifier or input is null", exception.getMessage());
    }

    @Test
    void createTest_withFilenameBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = Collections.<TestContainer>emptyList();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

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
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

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
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

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
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("foo", testContainers);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }
}
