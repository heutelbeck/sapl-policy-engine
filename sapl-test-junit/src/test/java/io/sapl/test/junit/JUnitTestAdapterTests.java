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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
class JUnitTestAdapterTests {
    @Mock
    protected TestProvider        testProviderMock;
    @Mock
    protected SaplTestInterpreter saplTestInterpreterMock;

    protected JUnitTestAdapter jUnitTestAdapter;

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

    protected final MockedStatic<Path> pathMockedStatic = mockStatic(Path.class);

    @BeforeEach
    void setUp() {
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create)
                .thenReturn(saplTestInterpreterMock);
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null, null)).thenReturn(testProviderMock);
        jUnitTestAdapter = new JUnitTestAdapter();
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
        pathMockedStatic.close();
    }

    private TestContainer mockTestContainerCreation(final String identifier, final List<TestContainer> testContainers) {
        final var testContainerMock = mock(TestContainer.class);
        // Required since Spotbugs complains about unused return value from method call
        // with no side effects here
        final Callable<TestContainer> expectedCall = () -> TestContainer.from(identifier, testContainers);
        testContainerMockedStatic.when(expectedCall::call).thenReturn(testContainerMock);
        return testContainerMock;
    }

    private URI mockPathAndUri(final String identifier) {
        // Required since Spotbugs complains about unused return value from method call
        // with no side effects here
        final Callable<Path> expectedCall = () -> Path.of("src/test/resources", identifier);

        final var pathMock = mock(Path.class);
        pathMockedStatic.when(expectedCall::call).thenReturn(pathMock);

        final var uriMock = mock(URI.class);
        when(pathMock.toUri()).thenReturn(uriMock);

        return uriMock;
    }

    @Test
    void getTests_withNullTestDiscoveryResult_returnsEmptyList() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(null);

        final var result = jUnitTestAdapter.getTests();

        assertTrue(result.isEmpty());
    }

    @Test
    void getTests_withEmptyTestDiscoveryResult_returnsEmptyList() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(Collections.emptyList());

        final var result = jUnitTestAdapter.getTests();

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

        final var uriMock = mockPathAndUri("container");

        final var dynamicContainerMock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("container"), eq(uriMock), any()))
                .thenAnswer(invocationOnMock -> {
                    final Stream<DynamicNode> nodes = invocationOnMock.getArgument(2);

                    assertTrue(nodes.toList().isEmpty());
                    return dynamicContainerMock;
                });

        final var result = jUnitTestAdapter.getTests();

        assertFalse(result.isEmpty());
        assertEquals(dynamicContainerMock, result.get(0));
    }

    @Test
    void getTests_withTestNodesOfUnknownTypeInTestContainer_throwsSaplTestException() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(List.of("filename"));

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("input");

        final var saplTestMock = Mockito.mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var baseAdapterTestContainer = Mockito.mock(TestContainer.class);
        final var testContainers           = List.of(baseAdapterTestContainer);
        when(testProviderMock.buildTests(saplTestMock, Collections.emptyMap())).thenReturn(testContainers);

        final var testContainerMock = mockTestContainerCreation("filename", testContainers);

        final var testNodeWithUnknownTypeMock = Mockito.mock(TestNode.class);

        when(testContainerMock.getTestNodes()).thenAnswer(invocationOnMock -> List.of(testNodeWithUnknownTypeMock));

        when(testContainerMock.getIdentifier()).thenReturn("container");

        final var uriMock = mockPathAndUri("container");

        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("container"), eq(uriMock), any()))
                .thenAnswer(invocationOnMock -> {
                    final Stream<DynamicNode> nodes = invocationOnMock.getArgument(2);

                    final var exception = assertThrows(SaplTestException.class, nodes::toList);
                    assertEquals("Unknown type of TestNode", exception.getMessage());

                    return null;
                });

        final var result = jUnitTestAdapter.getTests();
        assertEquals(Collections.singletonList(null), result);
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

        final var testContainerMock   = mock(TestContainer.class);
        final var nestedTestCase1Mock = mock(TestCase.class);
        final var nestedTestCase2Mock = mock(TestCase.class);
        final var testCase1Mock       = mock(TestCase.class);
        final var testCase2Mock       = mock(TestCase.class);
        final var testCase3Mock       = mock(TestCase.class);

        mockTestContainerCreation("filename", List.of(testContainerMock, testCase1Mock), "container1");
        mockTestContainerCreation("filename2", List.of(testCase2Mock, testCase3Mock), "container2");

        when(testContainerMock.getIdentifier()).thenReturn("nestedContainer");
        when(nestedTestCase1Mock.getIdentifier()).thenReturn("nestedTestCase1");
        when(nestedTestCase2Mock.getIdentifier()).thenReturn("nestedTestCase2");
        when(testCase1Mock.getIdentifier()).thenReturn("testCase1");
        when(testCase2Mock.getIdentifier()).thenReturn("testCase2");
        when(testCase3Mock.getIdentifier()).thenReturn("testCase3");

        when(testContainerMock.getTestNodes())
                .thenAnswer(invocationOnMock -> List.of(nestedTestCase1Mock, nestedTestCase2Mock));

        final var nestedTestCase1DynamicTestMock = mock(DynamicTest.class);
        final var nestedTestCase2DynamicTestMock = mock(DynamicTest.class);
        final var testCase1DynamicTestMock       = mock(DynamicTest.class);
        final var testCase2DynamicTestMock       = mock(DynamicTest.class);
        final var testCase3DynamicTestMock       = mock(DynamicTest.class);

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

        final var uri1Mock = mockPathAndUri("container1");
        final var uri2Mock = mockPathAndUri("container2");

        final var dynamicContainerWithNestedTestCasesMock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("nestedContainer"),
                ArgumentMatchers.<Stream<DynamicNode>>any())).thenAnswer(invocationOnMock -> {
                    final Stream<DynamicNode> nodes = invocationOnMock.getArgument(1);

                    assertEquals(List.of(nestedTestCase1DynamicTestMock, nestedTestCase2DynamicTestMock),
                            nodes.toList());
                    return dynamicContainerWithNestedTestCasesMock;
                });

        final var mappedDynamicContainer1Mock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("container1"), eq(uri1Mock), any()))
                .thenAnswer(invocationOnMock -> {
                    final Stream<DynamicNode> nodes = invocationOnMock.getArgument(2);

                    assertEquals(List.of(dynamicContainerWithNestedTestCasesMock, testCase1DynamicTestMock),
                            nodes.toList());
                    return mappedDynamicContainer1Mock;
                });

        final var mappedDynamicContainer2Mock = Mockito.mock(DynamicContainer.class);
        dynamicContainerMockedStatic
                .when(() -> DynamicContainer.dynamicContainer(eq("container2"), eq(uri2Mock), any()))
                .thenAnswer(invocationOnMock -> {
                    final Stream<DynamicNode> nodes = invocationOnMock.getArgument(2);

                    assertEquals(List.of(testCase2DynamicTestMock, testCase3DynamicTestMock), nodes.toList());
                    return mappedDynamicContainer2Mock;
                });

        final var result = jUnitTestAdapter.getTests();

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

        verify(nestedTestCase1Mock, times(1)).run();
        verify(nestedTestCase2Mock, times(1)).run();
        verify(testCase1Mock, times(1)).run();
        verify(testCase2Mock, times(1)).run();
        verify(testCase3Mock, times(1)).run();
    }
}
