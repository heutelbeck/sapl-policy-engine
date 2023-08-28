package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.Imports;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.FunctionParameters;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Once;
import io.sapl.test.grammar.sAPLTest.ParameterMatcher;
import io.sapl.test.grammar.sAPLTest.Val;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.verification.TimesCalledVerification;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class FunctionInterpreterTest {

    private ValInterpreter valInterpreterMock;
    private MatcherInterpreter matcherInterpreterMock;
    private GivenOrWhenStep givenOrWhenStepMock;
    private MockedStatic<Imports> importsMockedStatic;
    private FunctionInterpreter functionInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        matcherInterpreterMock = mock(MatcherInterpreter.class);
        givenOrWhenStepMock = mock(GivenOrWhenStep.class);
        importsMockedStatic = mockStatic(Imports.class);

        functionInterpreter = new FunctionInterpreter(valInterpreterMock, matcherInterpreterMock);
    }

    @AfterEach
    void tearDown() {
        importsMockedStatic.close();
    }

    @Nested
    @DisplayName("Interpret function")
    class InterpretFunctionTests {

        private Function functionMock;
        private Val valMock;
        private io.sapl.api.interpreter.Val saplValMock;

        @BeforeEach
        void setUp() {
            functionMock = mock(Function.class);
            when(functionMock.getImportName()).thenReturn("fooFunction");

            valMock = mock(Val.class);
            when(functionMock.getReturnValue()).thenReturn(valMock);

            saplValMock = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParameters_returnsGivenOrWhenStepWithExpectedFunctionMocking() {


            when(functionMock.getParameters()).thenReturn(null);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndEmptyFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var eListMock = Helper.mockEList(List.<ParameterMatcher>of());
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var parameterMatcher = mock(ParameterMatcher.class);
            final var eListMock = Helper.mockEList(List.of(parameterMatcher));
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var matcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getValMatcherFromParameterMatcher(parameterMatcher)).thenReturn(matcherMock);

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
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndNullFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var multipleMock = mock(Multiple.class);

            when(functionMock.getAmount()).thenReturn(multipleMock);
            when(multipleMock.getAmount()).thenReturn(3);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(3)).thenReturn(timesCalledVerificationMock);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock, timesCalledVerificationMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingOnceAndEmptyFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var onceMock = mock(Once.class);

            when(functionMock.getAmount()).thenReturn(onceMock);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var eListMock = Helper.mockEList(List.<ParameterMatcher>of());
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(1)).thenReturn(timesCalledVerificationMock);

            when(givenOrWhenStepMock.givenFunction("fooFunction", saplValMock, timesCalledVerificationMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var multipleMock = mock(Multiple.class);

            when(functionMock.getAmount()).thenReturn(multipleMock);
            when(multipleMock.getAmount()).thenReturn(3);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            final var parameterMatcher = mock(ParameterMatcher.class);
            final var eListMock = Helper.mockEList(List.of(parameterMatcher));
            when(functionParametersMock.getMatchers()).thenReturn(eListMock);

            final var matcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getValMatcherFromParameterMatcher(parameterMatcher)).thenReturn(matcherMock);

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
        void interpretFunctionInvokedOnce_handlesNullReturnValues_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getImportName()).thenReturn("fooFunction");
            when(functionInvokecOnceMock.getReturn()).thenReturn(null);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
            verifyNoInteractions(givenOrWhenStepMock);
        }

        @Test
        void interpretFunctionInvokedOnce_handlesEmptyReturnValues_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getImportName()).thenReturn("fooFunction");

            final var eListMock = Helper.mockEList(List.<Val>of());
            when(functionInvokecOnceMock.getReturn()).thenReturn(eListMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
            verifyNoInteractions(givenOrWhenStepMock);
        }

        @Test
        void interpretFunctionInvokedOnce_handlesSingleReturnValue_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getImportName()).thenReturn("fooFunction");

            final var valMock = mock(Val.class);
            final var eListMock = Helper.mockEList(List.of(valMock));
            when(functionInvokecOnceMock.getReturn()).thenReturn(eListMock);

            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunctionInvokedOnce_handlesMultipleReturnValues_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokecOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokecOnceMock.getImportName()).thenReturn("fooFunction");

            final var valMock = mock(Val.class);
            final var valMock2 = mock(Val.class);
            final var eListMock = Helper.mockEList(List.of(valMock, valMock2));
            when(functionInvokecOnceMock.getReturn()).thenReturn(eListMock);

            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            final var saplValMock2 = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);
            when(valInterpreterMock.getValFromReturnValue(valMock2)).thenReturn(saplValMock2);

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", saplValMock, saplValMock2)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokecOnceMock);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

}