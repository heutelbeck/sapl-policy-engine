package io.sapl.test.services;

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Once;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class FunctionInterpreter {

    private final ValInterpreter valInterpreter;
    private final MatcherInterpreter matcherInterpreter;

    GivenOrWhenStep interpretFunction(GivenOrWhenStep initial, Function function) {
        final var importName = function.getImportName();
        final var returnValue = valInterpreter.getValFromReturnValue(function.getReturn());

        var timesCalled = 0;

        if (function.getAmount() instanceof Multiple multiple) {
            timesCalled = multiple.getAmount().intValue();
        } else if (function.getAmount() instanceof Once) {
            timesCalled = 1;
        }

        final var parameters = interpretFunctionParameters(function.getParameters());

        if (timesCalled == 0) {
            if (parameters != null) {
                return initial.givenFunction(importName, parameters, returnValue);
            }
            return initial.givenFunction(importName, returnValue);
        } else {
            final var verification = Imports.times(timesCalled);
            if (parameters != null) {
                return initial.givenFunction(importName, parameters, returnValue, verification);
            }
            return initial.givenFunction(importName, returnValue, verification);
        }
    }

    GivenOrWhenStep interpretFunctionInvokedOnce(GivenOrWhenStep initial, FunctionInvokedOnce function) {
        final var importName = function.getImportName();
        final var valuesToBeReturned = function.getReturn();

        if (valuesToBeReturned == null || valuesToBeReturned.isEmpty()) {
            return initial;
        }

        final var returnValues = valuesToBeReturned.stream().map(valInterpreter::getValFromReturnValue).toArray(Val[]::new);

        if (returnValues.length == 1) {
            return initial.givenFunctionOnce(importName, returnValues[0]);
        }
        return initial.givenFunctionOnce(importName, returnValues);
    }

    private FunctionParameters interpretFunctionParameters(io.sapl.test.grammar.sAPLTest.FunctionParameters functionParameters) {
        if (functionParameters == null) {
            return null;
        }

        final var functionParameterMatchers = functionParameters.getMatchers();

        if (functionParameterMatchers == null || functionParameterMatchers.isEmpty()) {
            return null;
        }

        final var matchers = functionParameterMatchers.stream().map(matcherInterpreter::getValMatcherFromParameterMatcher).toArray(Matcher[]::new);

        return new FunctionParameters(matchers);
    }
}
