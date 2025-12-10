/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.Value;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Function;
import io.sapl.test.grammar.sapltest.FunctionInvokedOnce;
import io.sapl.test.grammar.sapltest.Multiple;
import io.sapl.test.grammar.sapltest.Once;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class FunctionInterpreter {

    private final ValueInterpreter        valueInterpreter;
    private final ValueMatcherInterpreter matcherInterpreter;
    private final MultipleInterpreter     multipleInterpreter;

    GivenOrWhenStep interpretFunction(GivenOrWhenStep givenOrWhenStep, Function function) {
        if (givenOrWhenStep == null || function == null) {
            throw new SaplTestException("GivenOrWhenStep or function is null.");
        }

        var dslTimesCalled = function.getTimesCalled();
        var timesCalled    = 0;

        if (dslTimesCalled instanceof Multiple multiple) {
            timesCalled = multipleInterpreter.getAmountFromMultiple(multiple);
        } else if (dslTimesCalled instanceof Once) {
            timesCalled = 1;
        }

        var parameters   = interpretFunctionParameters(function.getParameterMatchers());
        var returnValue  = valueInterpreter.getValueFromDslValue(function.getReturnValue());
        var functionName = function.getName();

        if (timesCalled == 0) {
            if (parameters != null) {
                return givenOrWhenStep.givenFunction(functionName, parameters, returnValue);
            }

            return givenOrWhenStep.givenFunction(functionName, returnValue);
        } else {
            var timesCalledVerification = Imports.times(timesCalled);

            if (parameters != null) {
                return givenOrWhenStep.givenFunction(functionName, parameters, returnValue, timesCalledVerification);
            }

            return givenOrWhenStep.givenFunction(functionName, returnValue, timesCalledVerification);
        }
    }

    GivenOrWhenStep interpretFunctionInvokedOnce(GivenOrWhenStep givenOrWhenStep,
            FunctionInvokedOnce functionInvokedOnce) {
        if (givenOrWhenStep == null || functionInvokedOnce == null) {
            throw new SaplTestException("GivenOrWhenStep or functionInvokedOnce is null.");
        }

        var returnValues = functionInvokedOnce.getReturnValue();

        if (returnValues == null || returnValues.isEmpty()) {
            throw new SaplTestException("No ReturnValue found.");
        }

        var mappedReturnValues = returnValues.stream().map(valueInterpreter::getValueFromDslValue)
                .toArray(Value[]::new);

        var name = functionInvokedOnce.getName();

        if (mappedReturnValues.length == 1) {
            return givenOrWhenStep.givenFunctionOnce(name, mappedReturnValues[0]);
        }

        return givenOrWhenStep.givenFunctionOnce(name, mappedReturnValues);
    }

    private FunctionParameters interpretFunctionParameters(
            io.sapl.test.grammar.sapltest.FunctionParameterMatchers parameterMatchers) {
        if (parameterMatchers == null) {
            return null;
        }

        var functionParameterMatchers = parameterMatchers.getMatchers();

        if (functionParameterMatchers == null || functionParameterMatchers.isEmpty()) {
            throw new SaplTestException("No FunctionParameterMatcher found.");
        }

        var valueMatchers = functionParameterMatchers.stream().map(matcherInterpreter::getHamcrestValueMatcher)
                .<Matcher<Value>>toArray(Matcher[]::new);

        return new FunctionParameters(valueMatchers);
    }
}
