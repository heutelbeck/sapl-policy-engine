package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultVerifyStepBuilderTest {

    private ExpectInterpreter expectInterpreterMock;

    private DefaultVerifyStepBuilder verifyStepBuilderServiceDefault;

    @BeforeEach
    void setUp() {
        expectInterpreterMock = mock(ExpectInterpreter.class);

        verifyStepBuilderServiceDefault = new DefaultVerifyStepBuilder(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_doesNothingForUnknownExpect_throwsRuntimeException() {
        final var testCaseMock = mock(TestCase.class);
        final var expectChainMock = mock(ExpectChain.class);

        when(testCaseMock.getExpect()).thenReturn(expectChainMock);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var exception = assertThrows(RuntimeException.class, () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("TestCase does not contain known expect", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_doesNothingForNullExpect_throwsRuntimeException() {
        final var testCaseMock = mock(TestCase.class);

        when(testCaseMock.getExpect()).thenReturn(null);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var exception = assertThrows(RuntimeException.class, () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("TestCase does not contain known expect", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_interpretsSingleExpect_returnsVerifyStep() {
        final var testCaseMock = mock(TestCase.class);
        final var singleExpectMock = mock(SingleExpect.class);

        when(testCaseMock.getExpect()).thenReturn(singleExpectMock);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpect(expectOrVerifyStepMock, singleExpectMock)).thenReturn(verifyStepMock);

        final var result = verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructVerifyStep_interpretsRepeatedExpect_returnsVerifyStep() {
        final var testCaseMock = mock(TestCase.class);
        final var repeatedExpectMock = mock(RepeatedExpect.class);

        when(testCaseMock.getExpect()).thenReturn(repeatedExpectMock);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretRepeatedExpect(expectOrVerifyStepMock, repeatedExpectMock)).thenReturn(verifyStepMock);

        final var result = verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }
}