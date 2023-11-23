package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interpreter.matcher.MultipleAmountInterpreter;
import io.sapl.test.dsl.interpreter.matcher.ValMatcherInterpreter;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.FunctionParameters;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Once;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.verification.TimesCalledVerification;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FunctionInterpreterTest {
    @Mock
    private ValInterpreter valInterpreterMock;
    @Mock
    private ValMatcherInterpreter matcherInterpreterMock;
    @Mock
    private MultipleAmountInterpreter multipleAmountInterpreter;
    @InjectMocks
    private FunctionInterpreter functionInterpreter;
    @Mock
    private GivenOrWhenStep givenOrWhenStepMock;

    private final MockedStatic<Imports> importsMockedStatic = mockStatic(Imports.class);

    @AfterEach
    void tearDown() {
        importsMockedStatic.close();
    }

    @Nested
    @DisplayName("Interpret function")
    class InterpretFunctionTests {

        @Mock
        private Function functionMock;
        @Mock
        private Value valMock;
        @Mock
        private io.sapl.api.interpreter.Val saplValMock;

        @BeforeEach
        void setUp() {
            when(functionMock.getName()).thenReturn("fooFunction");

            when(functionMock.getReturnValue()).thenReturn(valMock);

            when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParameters_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            when(functionMock.getParameters()).thenReturn(null);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParametersMatchers_throwsSaplTestException() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No ValMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndEmptyFunctionParametersMatchers_throwsSaplTestException() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var eListMock = Helper.mockEList(List.<ValMatcher>of());
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No ValMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var parameterMatcher = mock(ValMatcher.class);
            final var eListMock = Helper.mockEList(List.of(parameterMatcher));
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var matcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getHamcrestValMatcher(parameterMatcher)).thenReturn(matcherMock);

            final var functionParametersArgumentCaptor = ArgumentCaptor.forClass(io.sapl.test.mocking.function.models.FunctionParameters.class);
            when(givenOrWhenStepMock.givenFunction(eq("fooFunction"), functionParametersArgumentCaptor.capture(), eq(saplValMock))).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            final var usedFunctionParameters = functionParametersArgumentCaptor.getValue();
            assertEquals(List.of(matcherMock), usedFunctionParameters.getParameterMatchers());
            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingOnceAndNullFunctionParameters_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var onceMock = mock(Once.class);

            when(functionMock.getAmount()).thenReturn(onceMock);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(1)).thenReturn(timesCalledVerificationMock);

            when(functionMock.getParameters()).thenReturn(null);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock, timesCalledVerificationMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndNullFunctionParametersMatchers_throwsSaplTestException() {
            final var multipleMock = mock(Multiple.class);

            when(functionMock.getAmount()).thenReturn(multipleMock);
            when(multipleMock.getAmount()).thenReturn("3x");

            when(multipleAmountInterpreter.getAmountFromMultipleAmountString("3x")).thenReturn(3);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(3)).thenReturn(timesCalledVerificationMock);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No ValMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingOnceAndEmptyFunctionParametersMatchers_throwsSaplTestException() {
            final var onceMock = mock(Once.class);

            when(functionMock.getAmount()).thenReturn(onceMock);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var eListMock = Helper.mockEList(List.<ValMatcher>of());
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(1)).thenReturn(timesCalledVerificationMock);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No ValMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var multipleMock = mock(Multiple.class);

            when(functionMock.getAmount()).thenReturn(multipleMock);
            when(multipleMock.getAmount()).thenReturn("3x");

            when(multipleAmountInterpreter.getAmountFromMultipleAmountString("3x")).thenReturn(3);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var parameterMatcher = mock(ValMatcher.class);
            final var eListMock = Helper.mockEList(List.of(parameterMatcher));
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var matcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getHamcrestValMatcher(parameterMatcher)).thenReturn(matcherMock);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(3)).thenReturn(timesCalledVerificationMock);

            final var functionParametersArgumentCaptor = ArgumentCaptor.forClass(io.sapl.test.mocking.function.models.FunctionParameters.class);
            when(givenOrWhenStepMock.givenFunction(eq("fooFunction"), functionParametersArgumentCaptor.capture(), eq(saplValMock), eq(timesCalledVerificationMock))).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            final var usedFunctionParameters = functionParametersArgumentCaptor.getValue();
            assertEquals(List.of(matcherMock), usedFunctionParameters.getParameterMatchers());
            assertEquals(givenOrWhenStepMock, result);
        }
    }


    @Nested
    @DisplayName("Interpret function invoked once")
    class InterpretFunctionInvokedOnceTests {

        @Test
        void interpretFunctionInvokedOnce_handlesNullReturnValues_throwsSaplTestException() {
            final var functionInvokedOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokedOnceMock.getReturnValue()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokedOnceMock));

            assertEquals("No Value found", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesEmptyReturnValues_throwsSaplTestException() {
            final var functionInvokedOnceMock = mock(FunctionInvokedOnce.class);

            final var eListMock = Helper.mockEList(List.<Value>of());
            when(functionInvokedOnceMock.getReturnValue()).thenReturn(eListMock);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokedOnceMock));

            assertEquals("No Value found", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesSingleReturnValue_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getName()).thenReturn("fooFunction");

            final var valMock = mock(Value.class);
            final var eListMock = Helper.mockEList(List.of(valMock));
            when(functionInvokecOnceMock.getReturnValue()).thenReturn(eListMock);

            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunctionInvokedOnce_handlesMultipleReturnValues_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getName()).thenReturn("fooFunction");

            final var valMock = mock(Value.class);
            final var valMock2 = mock(Value.class);
            final var eListMock = Helper.mockEList(List.of(valMock, valMock2));
            when(functionInvokecOnceMock.getReturnValue()).thenReturn(eListMock);

            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            final var saplValMock2 = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);
            when(valInterpreterMock.getValFromValue(valMock2)).thenReturn(saplValMock2);

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", saplValMock, saplValMock2)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

}