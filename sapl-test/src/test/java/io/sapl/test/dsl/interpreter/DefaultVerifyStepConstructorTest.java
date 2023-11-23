package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultVerifyStepConstructorTest {
    @Mock
    private ExpectInterpreter expectInterpreterMock;
    @InjectMocks
    private DefaultVerifyStepConstructor verifyStepBuilderServiceDefault;

    @Test
    void constructVerifyStep_doesNothingForUnknownExpect_throwsSaplTestException() {
        final var testCaseMock = mock(TestCase.class);
        final var expectChainMock = mock(ExpectChain.class);

        when(testCaseMock.getExpect()).thenReturn(expectChainMock);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var exception = assertThrows(SaplTestException.class, () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("Unknown type of ExpectChain", exception.getMessage());
        verifyNoInteractions(expectInterpreterMock);
    }

    @Test
    void constructVerifyStep_doesNothingForNullExpect_throwsSaplTestException() {
        final var testCaseMock = mock(TestCase.class);

        when(testCaseMock.getExpect()).thenReturn(null);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var exception = assertThrows(SaplTestException.class, () -> verifyStepBuilderServiceDefault.constructVerifyStep(testCaseMock, expectOrVerifyStepMock));

        assertEquals("Unknown type of ExpectChain", exception.getMessage());
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
    void constructVerifyStep_interpretsSingleExpectWithMatcher_returnsVerifyStep() {
        final var testCaseMock = mock(TestCase.class);
        final var singleExpectWithMatcher = mock(SingleExpectWithMatcher.class);

        when(testCaseMock.getExpect()).thenReturn(singleExpectWithMatcher);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);
        when(expectInterpreterMock.interpretSingleExpectWithMatcher(expectOrVerifyStepMock, singleExpectWithMatcher)).thenReturn(verifyStepMock);

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