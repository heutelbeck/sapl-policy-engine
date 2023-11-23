package io.sapl.test.dsl.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.factories.SaplTestInterpreterFactory;
import io.sapl.test.dsl.factories.TestProviderFactory;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.setup.TestContainer;
import io.sapl.test.dsl.setup.TestProvider;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.utils.DocumentHelper;
import java.util.List;
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
        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(null, null)).thenReturn(testProviderMock);
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create).thenReturn(saplTestInterpreterMock);

        baseTestAdapter = new BaseTestAdapter<>() {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
                return testContainer;
            }
        };
    }

    private void buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter() {
        final var stepConstructorMock = mock(StepConstructor.class);

        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(stepConstructorMock)).thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(stepConstructorMock, saplTestInterpreterMock) {
            @Override
            protected TestContainer convertTestContainerToTargetRepresentation(TestContainer testContainer) {
                return testContainer;
            }
        };
    }

    private void buildInstanceOfBaseTestAdapterWithCustomUnitTestAndIntegrationTestPolicyResolver() {
        saplTestInterpreterFactoryMockedStatic.when(SaplTestInterpreterFactory::create).thenReturn(saplTestInterpreterMock);

        final var unitTestPolicyResolverMock = mock(UnitTestPolicyResolver.class);
        final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);

        testProviderFactoryMockedStatic.when(() -> TestProviderFactory.create(unitTestPolicyResolverMock, integrationTestPolicyResolver)).thenReturn(testProviderMock);

        baseTestAdapter = new BaseTestAdapter<>(unitTestPolicyResolverMock, integrationTestPolicyResolver) {
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

    private static Stream<Arguments> invalidInputCombinationsArgumentSource() {
        return Stream.of(Arguments.of(null, "foo"), Arguments.of("identifier", null), Arguments.of(null, null));
    }

    @ParameterizedTest
    @MethodSource("invalidInputCombinationsArgumentSource")
    void createTest_withInvalidInputCombinations_throwsSaplTestException(String identifier, String input) {
        buildInstanceOfBaseTestAdapterWithDefaultConstructor();

        final var exception = assertThrows(SaplTestException.class, () -> baseTestAdapter.createTest(identifier, input));

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
        buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter();

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
        buildInstanceOfBaseTestAdapterWithStepConstructorAndInterpreter();

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
    void createTest_withIdentifierAndInputBuildsTestContainer_usingCustomUnitTestAndIntegrationTestPolicyResolver_returnsMappedTestContainer() {
        buildInstanceOfBaseTestAdapterWithCustomUnitTestAndIntegrationTestPolicyResolver();

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