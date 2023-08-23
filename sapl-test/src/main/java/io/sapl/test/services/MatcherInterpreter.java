package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.anyVal;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.Any;
import io.sapl.test.grammar.sAPLTest.Equals;
import io.sapl.test.grammar.sAPLTest.ParameterMatcher;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class MatcherInterpreter {

    private final ValInterpreter valInterpreter;

    Matcher<Val> getValMatcherFromParameterMatcher(final ParameterMatcher functionParameterMatcher) {
        if (functionParameterMatcher instanceof Equals equals) {
            return is(valInterpreter.getValFromReturnValue(equals.getValue()));
        } else if (functionParameterMatcher instanceof Any) {
            return anyVal();
        }
        return null;
    }
}
