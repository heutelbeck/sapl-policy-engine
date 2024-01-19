/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static io.sapl.test.dsl.ParserUtil.compareArgumentToStringLiteral;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.AnyVal;
import io.sapl.test.grammar.sapltest.Function;
import io.sapl.test.grammar.sapltest.FunctionInvokedOnce;
import io.sapl.test.grammar.sapltest.FunctionParameters;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.Multiple;
import io.sapl.test.grammar.sapltest.NumberLiteral;
import io.sapl.test.grammar.sapltest.Once;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.sapltest.ValWithValue;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.verification.TimesCalledVerification;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
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
class FunctionInterpreterTests {
    @Mock
    protected ValueInterpreter      valueInterpreterMock;
    @Mock
    protected ValMatcherInterpreter matcherInterpreterMock;
    @Mock
    protected MultipleInterpreter   multipleInterpreterMock;
    @InjectMocks
    protected FunctionInterpreter   functionInterpreter;
    @Mock
    protected GivenOrWhenStep       givenOrWhenStepMock;

    protected final MockedStatic<Imports> importsMockedStatic = mockStatic(Imports.class);

    @AfterEach
    void tearDown() {
        importsMockedStatic.close();
    }

    private <T extends GivenStep> T buildGivenStep(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getGivenStepRule, clazz);
    }

    private Function buildFunction(final String input) {
        return buildGivenStep(input, Function.class);
    }

    private FunctionInvokedOnce buildFunctionInvokedOnce(final String input) {
        return buildGivenStep(input, FunctionInvokedOnce.class);
    }

    @Nested
    @DisplayName("Interpret function")
    class InterpretFunctionTests {

        @Mock
        Matcher<Val> valMatcherMock;

        @Test
        void interpretFunction_handlesNullGivenOrWhenStep_throwsSaplTestException() {
            final var function = buildFunction("function \"foo\" returns \"bar\"");

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(null, function));

            assertEquals("GivenOrWhenStep or function is null", exception.getMessage());
        }

        @Test
        void interpretFunction_handlesNullFunction_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, null));

            assertEquals("GivenOrWhenStep or function is null", exception.getMessage());
        }

        @Test
        void interpretFunction_handlesNullGivenOrWhenStepAndNullFunction_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(null, null));

            assertEquals("GivenOrWhenStep or function is null", exception.getMessage());
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParameters_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var function = buildFunction("function \"fooFunction\" returns \"bar\"");

            final var expectedVal = Val.of("bar");

            when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

            when(givenOrWhenStepMock.givenFunction("fooFunction", expectedVal)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, function);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndNullFunctionParametersMatchers_throwsSaplTestException() {
            final var functionMock = mock(Function.class);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No FunctionParameterMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndEmptyFunctionParametersMatchers_throwsSaplTestException() {
            final var functionMock = mock(Function.class);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            TestHelper.mockEListResult(functionParametersMock::getMatchers, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No FunctionParameterMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withoutTimesCalledVerificationAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var function = buildFunction("function \"fooFunction\" parameters matching any returns \"bar\"");

            when(matcherInterpreterMock.getHamcrestValMatcher(any(AnyVal.class))).thenReturn(valMatcherMock);

            final var expectedVal = Val.of("bar");

            when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

            final var functionParametersArgumentCaptor = ArgumentCaptor
                    .forClass(io.sapl.test.mocking.function.models.FunctionParameters.class);
            when(givenOrWhenStepMock.givenFunction(eq("fooFunction"), functionParametersArgumentCaptor.capture(),
                    eq(expectedVal))).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, function);

            final var usedFunctionParameters = functionParametersArgumentCaptor.getValue();
            assertEquals(List.of(valMatcherMock), usedFunctionParameters.getParameterMatchers());
            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingOnceAndNullFunctionParameters_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var function = buildFunction("function \"fooFunction\" returns \"bar\" called once");

            final var expectedVal = Val.of("bar");

            when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(1)).thenReturn(timesCalledVerificationMock);

            when(givenOrWhenStepMock.givenFunction("fooFunction", expectedVal, timesCalledVerificationMock))
                    .thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, function);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndNullFunctionParametersMatchers_throwsSaplTestException() {
            final var functionMock = mock(Function.class);
            final var multipleMock = mock(Multiple.class);

            when(functionMock.getTimesCalled()).thenReturn(multipleMock);
            when(multipleMock.getAmount()).thenReturn(BigDecimal.ONE);

            when(multipleInterpreterMock.getAmountFromMultiple(any())).thenAnswer(invocationOnMock -> {
                final Multiple multiple = invocationOnMock.getArgument(0);

                assertEquals(1, multiple.getAmount().intValueExact());
                return 1;
            });

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            when(functionParametersMock.getMatchers()).thenReturn(null);

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(3)).thenReturn(timesCalledVerificationMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No FunctionParameterMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingOnceAndEmptyFunctionParametersMatchers_throwsSaplTestException() {
            final var functionMock = mock(Function.class);
            final var onceMock     = mock(Once.class);

            when(functionMock.getTimesCalled()).thenReturn(onceMock);

            final var functionParametersMock = mock(FunctionParameters.class);
            when(functionMock.getParameters()).thenReturn(functionParametersMock);

            TestHelper.mockEListResult(functionParametersMock::getMatchers, Collections.emptyList());

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(1)).thenReturn(timesCalledVerificationMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunction(givenOrWhenStepMock, functionMock));

            assertEquals("No FunctionParameterMatcher found", exception.getMessage());
        }

        @Test
        void interpretFunction_withTimesCalledVerificationBeingMultipleAndFunctionParametersMatchers_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var function = buildFunction(
                    "function \"fooFunction\" parameters matching \"parameter\" returns \"bar\" called 3 times");

            final var expectedVal = Val.of("bar");

            when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

            when(multipleInterpreterMock.getAmountFromMultiple(any())).thenAnswer(invocationOnMock -> {
                final Multiple multiple = invocationOnMock.getArgument(0);

                assertEquals(3, multiple.getAmount().intValueExact());
                return 3;
            });

            final var matcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getHamcrestValMatcher(any(ValWithValue.class))).thenAnswer(invocationOnMock -> {
                final ValWithValue valWithValue = invocationOnMock.getArgument(0);

                assertEquals("parameter", ((StringLiteral) valWithValue.getValue()).getString());
                return matcherMock;
            });

            final var timesCalledVerificationMock = mock(TimesCalledVerification.class);
            importsMockedStatic.when(() -> Imports.times(3)).thenReturn(timesCalledVerificationMock);

            final var functionParametersArgumentCaptor = ArgumentCaptor
                    .forClass(io.sapl.test.mocking.function.models.FunctionParameters.class);
            when(givenOrWhenStepMock.givenFunction(eq("fooFunction"), functionParametersArgumentCaptor.capture(),
                    eq(expectedVal), eq(timesCalledVerificationMock))).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunction(givenOrWhenStepMock, function);

            final var usedFunctionParameters = functionParametersArgumentCaptor.getValue();
            assertEquals(List.of(matcherMock), usedFunctionParameters.getParameterMatchers());

            assertEquals(givenOrWhenStepMock, result);
        }
    }

    @Nested
    @DisplayName("Interpret function invoked once")
    class InterpretFunctionInvokedOnceTests {
        @Test
        void interpretFunctionInvokedOnce_handlesNullGivenOrWhenStep_throwsSaplTestException() {
            final var functionInvokedOnce = buildFunctionInvokedOnce("function \"fooFunction\" returns stream \"bar\"");

            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunctionInvokedOnce(null, functionInvokedOnce));

            assertEquals("GivenOrWhenStep or functionInvokedOnce is null", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesNullFunctionInvokedOnce_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock, null));

            assertEquals("GivenOrWhenStep or functionInvokedOnce is null", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesNullGivenOrWhenStepAndNullFunctionInvokedOnce_throwsSaplTestException() {
            final var exception = assertThrows(SaplTestException.class,
                    () -> functionInterpreter.interpretFunctionInvokedOnce(null, null));

            assertEquals("GivenOrWhenStep or functionInvokedOnce is null", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesNullReturnValues_throwsSaplTestException() {
            final var functionInvokedOnceMock = mock(FunctionInvokedOnce.class);
            when(functionInvokedOnceMock.getReturnValue()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter
                    .interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokedOnceMock));

            assertEquals("No ReturnValue found", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesEmptyReturnValues_throwsSaplTestException() {
            final var functionInvokedOnceMock = mock(FunctionInvokedOnce.class);

            TestHelper.mockEListResult(functionInvokedOnceMock::getReturnValue, Collections.emptyList());

            final var exception = assertThrows(SaplTestException.class, () -> functionInterpreter
                    .interpretFunctionInvokedOnce(givenOrWhenStepMock, functionInvokedOnceMock));

            assertEquals("No ReturnValue found", exception.getMessage());
        }

        @Test
        void interpretFunctionInvokedOnce_handlesSingleReturnValue_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokedOnce = buildFunctionInvokedOnce("function \"fooFunction\" returns stream \"bar\"");

            final var expectedVal = Val.of("bar");

            when(valueInterpreterMock.getValFromValue(compareArgumentToStringLiteral("bar"))).thenReturn(expectedVal);

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", expectedVal)).thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock,
                    functionInvokedOnce);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretFunctionInvokedOnce_handlesMultipleReturnValues_returnsGivenOrWhenStepWithExpectedFunctionMocking() {
            final var functionInvokedOnce = buildFunctionInvokedOnce(
                    "function \"fooFunction\" returns stream \"bar\", 1");

            final var expectedVal1 = Val.of("bar");
            final var expectedVal2 = Val.of(5);

            when(valueInterpreterMock.getValFromValue(any(StringLiteral.class))).thenAnswer(invocationOnMock -> {
                final StringLiteral stringLiteral = invocationOnMock.getArgument(0);

                assertEquals("bar", stringLiteral.getString());
                return expectedVal1;
            });

            when(valueInterpreterMock.getValFromValue(any(NumberLiteral.class))).thenAnswer(invocationOnMock -> {
                final NumberLiteral numberLiteral = invocationOnMock.getArgument(0);

                assertEquals(BigDecimal.ONE, numberLiteral.getNumber());
                return expectedVal2;
            });

            when(givenOrWhenStepMock.givenFunctionOnce("fooFunction", expectedVal1, expectedVal2))
                    .thenReturn(givenOrWhenStepMock);

            final var result = functionInterpreter.interpretFunctionInvokedOnce(givenOrWhenStepMock,
                    functionInvokedOnce);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

}
