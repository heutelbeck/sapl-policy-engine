package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.setup.DefaultStepConstructor;
import io.sapl.test.dsl.setup.TestProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TestProviderFactoryTest {

    private final MockedStatic<TestProvider> testProviderMockedStatic = mockStatic(TestProvider.class);

    @AfterEach
    void tearDown() {
        testProviderMockedStatic.close();
    }

    @Test
    void create_handlesNullStepConstructorAndUsesDefaultStepConstructor_returnsTestProviderInstance() {
        final var stepConstructorArgumentCaptor = ArgumentCaptor.forClass(StepConstructor.class);

        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(stepConstructorArgumentCaptor.capture())).thenReturn(testProviderMock);

        final var result = TestProviderFactory.create(null);

        assertEquals(testProviderMock, result);
        assertInstanceOf(DefaultStepConstructor.class, stepConstructorArgumentCaptor.getValue());
    }

    @Test
    void create_usesGivenStepConstructor_returnsTestProviderInstance() {
        final var stepConstructorMock = mock(StepConstructor.class);

        final var testProviderMock = mock(TestProvider.class);
        testProviderMockedStatic.when(() -> TestProvider.of(stepConstructorMock)).thenReturn(testProviderMock);

        final var result = TestProviderFactory.create(stepConstructorMock);

        assertEquals(testProviderMock, result);
    }
}