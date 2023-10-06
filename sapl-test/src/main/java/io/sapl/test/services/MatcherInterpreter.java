package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.anyVal;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class MatcherInterpreter {

    private final ValInterpreter valInterpreter;

    Matcher<Val> getValMatcherFromParameterMatcher(final ValMatcher functionParameterMatcher) {
        if (functionParameterMatcher instanceof ValWithValue valWithValue) {
            return is(valInterpreter.getValFromReturnValue(valWithValue.getValue()));
        } else if (functionParameterMatcher instanceof AnyVal) {
            return anyVal();
        }
        return null;
    }
}
