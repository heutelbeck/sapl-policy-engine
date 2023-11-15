package io.sapl.test.dsl.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.factories.SaplTestInterpreterFactory;
import io.sapl.test.dsl.factories.TestProviderFactory;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseTestAdapterTest {
    @Mock
    private TestProvider testProviderMock;
    @Mock
    private SaplTestInterpreter saplTestInterpreterMock;

    private BaseTestAdapter<TestContainer> baseTestAdapter;

    private final MockedStatic<TestProviderFactory> testProviderFactoryMockedStatic = mockStatic(TestProviderFactory.class);
    private final MockedStatic<SaplTestInterpreterFactory> saplTestInterpreterFactoryMockedStatic = mockStatic(SaplTestInterpreterFactory.class);
    private final MockedStatic<DocumentHelper> documentHelperMockedStatic = mockStatic(DocumentHelper.class);
    private final MockedStatic<TestContainer> testContainerMockedStatic = mockStatic(TestContainer.class);

    @AfterEach
    void tearDown() {
        testProviderFactoryMockedStatic.close();
        saplTestInterpreterFactoryMockedStatic.close();
        documentHelperMockedStatic.close();
        testContainerMockedStatic.close();
    }


    private void buildInstanceOfBaseTestAdapterWithDefaultConstructor() {
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null)).thenReturn(testProviderMock);
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create).thenReturn(saplTestInterpreterMock);

        baseTestAdapter = new BaseTestAdapter<>() {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
                return testContainer;
            }
        };
    }

    private void buildInstanceOfBaseTestAdapterWithNonDefaultConstructor() {
        final var stepConstructorMock = mock(StepConstructor.class);

        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(stepConstructorMock)).thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(stepConstructorMock, saplTestInterpreterMock) {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
                return testContainer;
            }
        };
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

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenThrow(new SaplTestException("no file here"));
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

    @Test
    void createTest_withNullIdentifier_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest(null, "foo"));

        assertEquals("identifier or input is null", exception.getMessage());
    }

    @Test
    void createTest_withNullInput_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest("identifier", null));

        assertEquals("identifier or input is null", exception.getMessage());
    }

    @Test
    void createTest_withNullIdentifierAndNullInput_throwsSaplTestException() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest(null, null));

        assertEquals("identifier or input is null", exception.getMessage());
    }

    @Test
    void createTest_withFilenameBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = List.<TestContainer>of();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("foo", testContainers)).thenReturn(testContainerMock);

        final var result = baseTestAdapter.createTest("foo");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainer_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = List.<TestContainer>of();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("foo", testContainers)).thenReturn(testContainerMock);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withFilenameBuildsTestContainer_usingCustomStepConstructorAndSaplTestInterpreter_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithNonDefaultConstructor();

        documentHelperMockedStatic.when(() -> DocumentHelper.findFileOnClasspath("foo")).thenReturn("input");

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = List.<TestContainer>of();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("foo", testContainers)).thenReturn(testContainerMock);

        final var result = baseTestAdapter.createTest("foo");

        assertEquals(testContainerMock, result);
    }

    @Test
    void createTest_withIdentifierAndInputBuildsTestContainer_usingCustomStepConstructorAndSaplTestInterpreter_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithNonDefaultConstructor();

        final var saplTestMock = mock(SAPLTest.class);
        when(saplTestInterpreterMock.loadAsResource("input")).thenReturn(saplTestMock);

        final var testContainers = List.<TestContainer>of();
        when(testProviderMock.buildTests(saplTestMock)).thenReturn(testContainers);

        final var testContainerMock = mock(TestContainer.class);
        testContainerMockedStatic.when(() -> TestContainer.from("foo", testContainers)).thenReturn(testContainerMock);

        final var result = baseTestAdapter.createTest("foo", "input");

        assertEquals(testContainerMock, result);
    }
}