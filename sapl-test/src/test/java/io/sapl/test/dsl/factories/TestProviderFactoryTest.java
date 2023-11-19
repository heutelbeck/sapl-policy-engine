package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.dsl.setup.DefaultStepConstructor;
import io.sapl.test.dsl.setup.TestProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TestProviderFactoryTest {

    private final MockedStatic<TestProvider> testProviderMockedStatic = mockStatic(TestProvider.class);

    @AfterEach
    void tearDown() {
        testProviderMockedStatic.close();
    }

    @Test
    void create_withNullStepConstructor_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestProviderFactory.create(null));

        assertEquals("Provided stepConstructor is null", exception.getMessage());
    }

    @Test
    void create_usesGivenStepConstructor_returnsTestProvider() {
        final var stepConstructorMock = mock(StepConstructor.class);

        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(stepConstructorMock)).thenReturn(testProviderMock);

        final var result = TestProviderFactory.create(stepConstructorMock);

        assertEquals(testProviderMock, result);
    }

    @Test
    void create_withUnitTestPolicyResolverAndIntegrationTestPolicyResolver_returnsTestProvider() {
        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(any(DefaultStepConstructor.class))).thenReturn(testProviderMock);

        final var unitTestPolicyResolver = mock(UnitTestPolicyResolver.class);
        final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);

        final var result = TestProviderFactory.create(unitTestPolicyResolver, integrationTestPolicyResolver);

        assertEquals(testProviderMock, result);
    }
}