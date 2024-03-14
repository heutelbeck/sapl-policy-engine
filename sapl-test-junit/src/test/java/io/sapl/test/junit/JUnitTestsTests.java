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
package io.sapl.test.junit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.dsl.setup.SaplTestInterpreterFactory;
import io.sapl.test.dsl.setup.TestCase;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.dsl.setup.TestProviderFactory;
import io.sapl.test.grammar.sapltest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;

@ExtendWith(MockitoExtension.class)
class JUnitTestsTests {
    @Mock
    protected TestProvider        testProviderMock;
    @Mock
    protected SaplTestInterpreter saplTestInterpreterMock;

    protected JUnitTests jUnitTests;

    protected final MockedStatic<TestProviderFactory>        testProviderFactoryMockedStatic        = mockStatic(
            TestProviderFactory.class);
    protected final MockedStatic<SaplTestInterpreterFactory> saplTestInterpreterFactoryMockedStatic = mockStatic(
            SaplTestInterpreterFactory.class);
    protected final MockedStatic<DocumentHelper>             documentHelperMockedStatic             = mockStatic(
            DocumentHelper.class);
    protected final MockedStatic<TestContainer>              testContainerMockedStatic              = mockStatic(
            TestContainer.class);
    protected final MockedStatic<TestDiscoveryHelper>        testDiscoveryHelperMockedStatic        = mockStatic(
            TestDiscoveryHelper.class);
    protected final MockedStatic<DynamicContainer>           dynamicContainerMockedStatic           = mockStatic(
            DynamicContainer.class);
    protected final MockedStatic<DynamicTest>                dynamicTestMockedStatic                = mockStatic(
            DynamicTest.class);

    @BeforeEach
    void setUp() {
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create)
                .thenReturn(saplTestInterpreterMock);
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null, null)).thenReturn(testProviderMock);
        jUnitTests = new JUnitTests();
    }

    @AfterEach
    void tearDown() {
        testProviderFactoryMockedStatic.close();
        saplTestInterpreterFactoryMockedStatic.close();
        documentHelperMockedStatic.close();
        testContainerMockedStatic.close();
        testDiscoveryHelperMockedStatic.close();
        dynamicContainerMockedStatic.close();
        dynamicTestMockedStatic.close();
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
    void getTests_withNullTestDiscoveryResult_returnsEmptyList() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(null);

        final var result = jUnitTests.getTests();

        assertTrue(result.isEmpty());
    }

    @Test
    void getTests_withEmptyTestDiscoveryResult_returnsEmptyList() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(Collections.emptyList());

        final var result = jUnitTests.getTests();

        assertTrue(result.isEmpty());
    }

    @Test
    void getTests_withNullTestNodesInTestContainer_returnsListWithSingleDynamicContainer() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(List.of("filename"));

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("input");

        final var saplTestMock = Mockito.mock(SAPLTest.class);
        Mockito.when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        Mockito.when(testProviderMock.buildTests(saplTestMock, Collections.emptyMap())).thenReturn(null);

        final var testContainerMock = mockTestContainerCreation("filename", null);

        Mockito.when(testContainerMock.getTestNodes()).thenReturn(null);
        Mockito.when(testContainerMock.getIdentifier()).thenReturn("container");

        final var dynamicContainerMock = Mockito.mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("container"), eq(Collections.emptyList())))
                .thenReturn(dynamicContainerMock);

        final var result = jUnitTests.getTests();

        assertFalse(result.isEmpty());
        assertEquals(dynamicContainerMock, result.get(0));
    }

    @Test
    void getTests_withTestNodesOfUnknownTypeInTestContainer_throwsSaplTestException() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(List.of("filename"));

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("input");

        final var saplTestMock = Mockito.mock(SAPLTest.class);
        Mockito.when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var baseAdapterTestContainer = Mockito.mock(TestContainer.class);
        final var testContainers           = List.of(baseAdapterTestContainer);
        Mockito.when(testProviderMock.buildTests(saplTestMock, Collections.emptyMap())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("filename", testContainers);

        final var testNodeWithUnknownTypeMock = Mockito.mock(TestNode.class);

        when(testContainerMock.getTestNodes()).thenAnswer(invocationOnMock -> List.of(testNodeWithUnknownTypeMock));

        Mockito.when(testContainerMock.getIdentifier()).thenReturn("container");

        final var exception = assertThrows(SaplTestException.class, jUnitTests::getTests);

        assertEquals("Unknown type of TestNode", exception.getMessage());
    }

    private void mockTestContainerCreation(final String filename, final List<? extends TestNode> testNodes,
            final String containerName) {
        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath(filename)).thenReturn(filename);

        final var saplTestMock = Mockito.mock(SAPLTest.class);
        Mockito.when(saplTestInterpreterMock.loadAsResource(filename)).thenReturn(saplTestMock);

        final var baseAdapterTestContainerMock = Mockito.mock(TestContainer.class);
        final var testContainers               = List.of(baseAdapterTestContainerMock);

        Mockito.when(testProviderMock.buildTests(saplTestMock, Collections.emptyMap())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation(filename, testContainers);

        when(testContainerMock.getTestNodes()).thenAnswer(invocationOnMock -> testNodes);

        Mockito.when(testContainerMock.getIdentifier()).thenReturn(containerName);
    }

    private ArgumentCaptor<Executable> mockDynamicTestAndCaptureArgument(final String identifier,
            final DynamicTest dynamicTestMock) {
        final ArgumentCaptor<Executable> argumentCaptor = ArgumentCaptor.forClass(Executable.class);

        dynamicTestMockedStatic.when(() -> DynamicTest.dynamicTest(eq(identifier), argumentCaptor.capture()))
                .thenReturn(dynamicTestMock);
        return argumentCaptor;
    }

    @Test
    void getTests_withMultipleMixedTestNodesInMultipleTestContainers_returnsMultipleDynamicContainers() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests)
                .thenReturn(List.of("filename", "filename2"));

        final var testContainerMock   = Mockito.mock(TestContainer.class);
        final var nestedTestCase1Mock = Mockito.mock(TestCase.class);
        final var nestedTestCase2Mock = Mockito.mock(TestCase.class);
        final var testCase1Mock       = Mockito.mock(TestCase.class);
        final var testCase2Mock       = Mockito.mock(TestCase.class);
        final var testCase3Mock       = Mockito.mock(TestCase.class);

        mockTestContainerCreation("filename", List.of(testContainerMock, testCase1Mock), "container1");
        mockTestContainerCreation("filename2", List.of(testCase2Mock, testCase3Mock), "container2");

        Mockito.when(testContainerMock.getIdentifier()).thenReturn("nestedContainer");
        Mockito.when(nestedTestCase1Mock.getIdentifier()).thenReturn("nestedTestCase1");
        Mockito.when(nestedTestCase2Mock.getIdentifier()).thenReturn("nestedTestCase2");
        Mockito.when(testCase1Mock.getIdentifier()).thenReturn("testCase1");
        Mockito.when(testCase2Mock.getIdentifier()).thenReturn("testCase2");
        Mockito.when(testCase3Mock.getIdentifier()).thenReturn("testCase3");

        when(testContainerMock.getTestNodes())
                .thenAnswer(invocationOnMock -> List.of(nestedTestCase1Mock, nestedTestCase2Mock));

        final var nestedTestCase1DynamicTestMock = Mockito.mock(DynamicTest.class);
        final var nestedTestCase2DynamicTestMock = Mockito.mock(DynamicTest.class);
        final var testCase1DynamicTestMock       = Mockito.mock(DynamicTest.class);
        final var testCase2DynamicTestMock       = Mockito.mock(DynamicTest.class);
        final var testCase3DynamicTestMock       = Mockito.mock(DynamicTest.class);

        final var nestedTestCase1ArgumentCaptor = mockDynamicTestAndCaptureArgument("nestedTestCase1",
                nestedTestCase1DynamicTestMock);
        final var nestedTestCase2ArgumentCaptor = mockDynamicTestAndCaptureArgument("nestedTestCase2",
                nestedTestCase2DynamicTestMock);
        final var testCase1ArgumentCaptor       = mockDynamicTestAndCaptureArgument("testCase1",
                testCase1DynamicTestMock);
        final var testCase2ArgumentCaptor       = mockDynamicTestAndCaptureArgument("testCase2",
                testCase2DynamicTestMock);
        final var testCase3ArgumentCaptor       = mockDynamicTestAndCaptureArgument("testCase3",
                testCase3DynamicTestMock);

        final var dynamicContainerWithNestedTestCasesMock = Mockito.mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("nestedContainer"),
                        eq(List.of(nestedTestCase1DynamicTestMock, nestedTestCase2DynamicTestMock))))
                .thenReturn(dynamicContainerWithNestedTestCasesMock);

        final var mappedDynamicContainer1Mock = Mockito.mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("container1"),
                        eq(List.of(dynamicContainerWithNestedTestCasesMock, testCase1DynamicTestMock))))
                .thenReturn(mappedDynamicContainer1Mock);

        final var mappedDynamicContainer2Mock = Mockito.mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("container2"),
                        eq(List.of(testCase2DynamicTestMock, testCase3DynamicTestMock))))
                .thenReturn(mappedDynamicContainer2Mock);

        final var result = jUnitTests.getTests();

        assertEquals(2, result.size());
        assertEquals(mappedDynamicContainer1Mock, result.get(0));
        assertEquals(mappedDynamicContainer2Mock, result.get(1));

        assertDoesNotThrow(() -> {
            nestedTestCase1ArgumentCaptor.getValue().execute();
            nestedTestCase2ArgumentCaptor.getValue().execute();
            testCase1ArgumentCaptor.getValue().execute();
            testCase2ArgumentCaptor.getValue().execute();
            testCase3ArgumentCaptor.getValue().execute();
        });

        Mockito.verify(nestedTestCase1Mock, Mockito.times(1)).run();
        Mockito.verify(nestedTestCase2Mock, Mockito.times(1)).run();
        Mockito.verify(testCase1Mock, Mockito.times(1)).run();
        Mockito.verify(testCase2Mock, Mockito.times(1)).run();
        Mockito.verify(testCase3Mock, Mockito.times(1)).run();
    }
}
