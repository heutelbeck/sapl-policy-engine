package io.sapl.test.dsl.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.setup.SaplTestInterpreterFactory;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestDiscoveryHelper;
import io.sapl.test.dsl.setup.TestNode;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.dsl.setup.TestProviderFactory;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;
import java.util.Collections;
import java.util.List;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JUnitTestsTest {
    @Mock
    private TestProvider testProviderMock;
    @Mock
    private SaplTestInterpreter saplTestInterpreterMock;

    private JUnitTests jUnitTests;

    private final MockedStatic<TestProviderFactory> testProviderFactoryMockedStatic = mockStatic(TestProviderFactory.class);
    private final MockedStatic<SaplTestInterpreterFactory> saplTestInterpreterFactoryMockedStatic = mockStatic(SaplTestInterpreterFactory.class);
    private final MockedStatic<DocumentHelper> documentHelperMockedStatic = mockStatic(DocumentHelper.class);
    private final MockedStatic<TestContainer> testContainerMockedStatic = mockStatic(TestContainer.class);
    private final MockedStatic<TestDiscoveryHelper> testDiscoveryHelperMockedStatic = mockStatic(TestDiscoveryHelper.class);
    private final MockedStatic<DynamicContainer> dynamicContainerMockedStatic = mockStatic(DynamicContainer.class);
    private final MockedStatic<DynamicTest> dynamicTestMockedStatic = mockStatic(DynamicTest.class);


    @BeforeEach
    void setUp() {
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create).thenReturn(saplTestInterpreterMock);
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null)).thenReturn(testProviderMock);
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

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        when(testProviderMock.buildTests(saplTestMock)).thenReturn(null);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("filename", null)).thenReturn(testContainerMock);

        when(testContainerMock.getTestNodes()).thenReturn(null);
        when(testContainerMock.getIdentifier()).thenReturn("container");

        final var dynamicContainerMock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("container"), eq(Collections.emptyList()))).thenReturn(dynamicContainerMock);

        final var result = jUnitTests.getTests();

        assertFalse(result.isEmpty());
        assertEquals(dynamicContainerMock, result.get(0));
    }

    @Test
    void getTests_withTestNodesOfUnknownTypeInTestContainer_throwsSaplTestException() {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(List.of("filename"));

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("filename")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var baseAdapterTestContainer = mock(TestContainer.class);
        final var testContainers = List.of(baseAdapterTestContainer);
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("filename", testContainers)).thenReturn(testContainerMock);

        final var testNodeWithUnknownTypeMock = mock(TestNode.class);
        doReturn(List.of(testNodeWithUnknownTypeMock)).when(testContainerMock).getTestNodes();
        when(testContainerMock.getIdentifier()).thenReturn("container");

        final var exception = assertThrows(SaplTestException.class, jUnitTests::getTests);

        assertEquals("Unknown type of TestNode", exception.getMessage());
    }

    private void mockTestContainerCreation(final String filename, final List<? extends TestNode> testNodes, final String containerName) {
        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath(filename)).thenReturn(filename);

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource(filename)).thenReturn(saplTestMock);

        final var baseAdapterTestContainerMock = mock(TestContainer.class);
        final var testContainers = List.of(baseAdapterTestContainerMock);

        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from(filename, testContainers)).thenReturn(testContainerMock);

        doReturn(testNodes).when(testContainerMock).getTestNodes();
        when(testContainerMock.getIdentifier()).thenReturn(containerName);
    }

    private ArgumentCaptor<Executable> mockDynamicTestAndCaptureArgument(final String identifier, final DynamicTest dynamicTestMock) {
        final ArgumentCaptor<Executable> argumentCaptor = ArgumentCaptor.forClass(Executable.class);

        dynamicTestMockedStatic.when(() -> DynamicTest.dynamicTest(eq(identifier), argumentCaptor.capture())).thenReturn(dynamicTestMock);
        return argumentCaptor;
    }

    @Test
    void getTests_withMultipleMixedTestNodesInMultipleTestContainers_returnsMultipleDynamicContainers() throws Throwable {
        testDiscoveryHelperMockedStatic.when(TestDiscoveryHelper::discoverTests).thenReturn(List.of("filename", "filename2"));

        final var testContainerMock = mock(TestContainer.class);
        final var nestedTestCase1Mock = mock(io.sapl.test.dsl.setup.Test.class);
        final var nestedTestCase2Mock = mock(io.sapl.test.dsl.setup.Test.class);
        final var testCase1Mock = mock(io.sapl.test.dsl.setup.Test.class);
        final var testCase2Mock = mock(io.sapl.test.dsl.setup.Test.class);
        final var testCase3Mock = mock(io.sapl.test.dsl.setup.Test.class);

        mockTestContainerCreation("filename", List.of(testContainerMock, testCase1Mock), "container1");
        mockTestContainerCreation("filename2", List.of(testCase2Mock, testCase3Mock), "container2");

        when(testContainerMock.getIdentifier()).thenReturn("nestedContainer");
        when(nestedTestCase1Mock.getIdentifier()).thenReturn("nestedTestCase1");
        when(nestedTestCase2Mock.getIdentifier()).thenReturn("nestedTestCase2");
        when(testCase1Mock.getIdentifier()).thenReturn("testCase1");
        when(testCase2Mock.getIdentifier()).thenReturn("testCase2");
        when(testCase3Mock.getIdentifier()).thenReturn("testCase3");

        doReturn(List.of(nestedTestCase1Mock, nestedTestCase2Mock)).when(testContainerMock).getTestNodes();

        final var nestedTestCase1DynamicTestMock = mock(DynamicTest.class);
        final var nestedTestCase2DynamicTestMock = mock(DynamicTest.class);
        final var testCase1DynamicTestMock = mock(DynamicTest.class);
        final var testCase2DynamicTestMock = mock(DynamicTest.class);
        final var testCase3DynamicTestMock = mock(DynamicTest.class);

        final var nestedTestCase1ArgumentCaptor = mockDynamicTestAndCaptureArgument("nestedTestCase1", nestedTestCase1DynamicTestMock);
        final var nestedTestCase2ArgumentCaptor = mockDynamicTestAndCaptureArgument("nestedTestCase2", nestedTestCase2DynamicTestMock);
        final var testCase1ArgumentCaptor = mockDynamicTestAndCaptureArgument("testCase1", testCase1DynamicTestMock);
        final var testCase2ArgumentCaptor = mockDynamicTestAndCaptureArgument("testCase2", testCase2DynamicTestMock);
        final var testCase3ArgumentCaptor = mockDynamicTestAndCaptureArgument("testCase3", testCase3DynamicTestMock);

        final var dynamicContainerWithNestedTestCasesMock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("nestedContainer"), eq(List.of(nestedTestCase1DynamicTestMock, nestedTestCase2DynamicTestMock)))).thenReturn(dynamicContainerWithNestedTestCasesMock);

        final var mappedDynamicContainer1Mock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("container1"), eq(List.of(dynamicContainerWithNestedTestCasesMock, testCase1DynamicTestMock)))).thenReturn(mappedDynamicContainer1Mock);

        final var mappedDynamicContainer2Mock = mock(DynamicContainer.class);
        dynamicContainerMockedStatic.when(() -> DynamicContainer.dynamicContainer(eq("container2"), eq(List.of(testCase2DynamicTestMock, testCase3DynamicTestMock)))).thenReturn(mappedDynamicContainer2Mock);

        final var result = jUnitTests.getTests();

        assertEquals(2, result.size());
        assertEquals(mappedDynamicContainer1Mock, result.get(0));
        assertEquals(mappedDynamicContainer2Mock, result.get(1));

        nestedTestCase1ArgumentCaptor.getValue().execute();
        nestedTestCase2ArgumentCaptor.getValue().execute();
        testCase1ArgumentCaptor.getValue().execute();
        testCase2ArgumentCaptor.getValue().execute();
        testCase3ArgumentCaptor.getValue().execute();

        verify(nestedTestCase1Mock, times(1)).run();
        verify(nestedTestCase2Mock, times(1)).run();
        verify(testCase1Mock, times(1)).run();
        verify(testCase2Mock, times(1)).run();
        verify(testCase3Mock, times(1)).run();
    }
}