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

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Once;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class FunctionInterpreter {

    private final ValInterpreter            valInterpreter;
    private final ValMatcherInterpreter     matcherInterpreter;
    private final MultipleAmountInterpreter multipleAmountInterpreter;

    GivenOrWhenStep interpretFunction(final GivenOrWhenStep givenOrWhenStep, final Function function) {
        if (givenOrWhenStep == null || function == null) {
            throw new SaplTestException("GivenOrWhenStep or function is null");
        }

        var timesCalled = 0;

        if (function.getAmount() instanceof Multiple multiple) {
            timesCalled = multipleAmountInterpreter.getAmountFromMultipleAmountString(multiple.getAmount());
        } else if (function.getAmount() instanceof Once) {
            timesCalled = 1;
        }

        final var parameters  = interpretFunctionParameters(function.getParameters());
        final var returnValue = valInterpreter.getValFromValue(function.getReturnValue());
        final var name        = function.getName();

        if (timesCalled == 0) {
            if (parameters != null) {
                return givenOrWhenStep.givenFunction(name, parameters, returnValue);
            }

            return givenOrWhenStep.givenFunction(name, returnValue);
        } else {
            final var verification = Imports.times(timesCalled);

            if (parameters != null) {
                return givenOrWhenStep.givenFunction(name, parameters, returnValue, verification);
            }

            return givenOrWhenStep.givenFunction(name, returnValue, verification);
        }
    }

    GivenOrWhenStep interpretFunctionInvokedOnce(final GivenOrWhenStep givenOrWhenStep,
            final FunctionInvokedOnce functionInvokedOnce) {
        if (givenOrWhenStep == null || functionInvokedOnce == null) {
            throw new SaplTestException("GivenOrWhenStep or functionInvokedOnce is null");
        }

        final var values = functionInvokedOnce.getReturnValue();

        if (values == null || values.isEmpty()) {
            throw new SaplTestException("No Value found");
        }

        final var returnValues = values.stream().map(valInterpreter::getValFromValue).toArray(Val[]::new);

        final var name = functionInvokedOnce.getName();

        if (returnValues.length == 1) {
            return givenOrWhenStep.givenFunctionOnce(name, returnValues[0]);
        }

        return givenOrWhenStep.givenFunctionOnce(name, returnValues);
    }

    private FunctionParameters interpretFunctionParameters(
            final io.sapl.test.grammar.sAPLTest.FunctionParameters functionParameters) {
        if (functionParameters == null) {
            return null;
        }

        final var functionParameterMatchers = functionParameters.getMatchers();

        if (functionParameterMatchers == null || functionParameterMatchers.isEmpty()) {
            throw new SaplTestException("No ValMatcher found");
        }

        final var matchers = functionParameterMatchers.stream().map(matcherInterpreter::getHamcrestValMatcher)
                .<Matcher<Val>>toArray(Matcher[]::new);

        return new io.sapl.test.mocking.function.models.FunctionParameters(matchers);
    }
}
