package io.sapl.test.services.matcher;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithErrorString;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.services.ValInterpreter;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class ValMatcherInterpreter {

    private final ValInterpreter valInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    public Matcher<Val> getValMatcherFromValMatcher(final ValMatcher valMatcher) {
        if (valMatcher instanceof ValWithValue valWithValueMatcher) {
            return is(valInterpreter.getValFromReturnValue(valWithValueMatcher.getValue()));
        } else if (valMatcher instanceof AnyVal) {
            return anyVal();
        } else if (valMatcher instanceof ValWithMatcher valWithMatcherMatcher) {
            return val(jsonNodeMatcherInterpreter.getJsonNodeMatcherFromJsonNodeMatcher(valWithMatcherMatcher.getMatcher()));
        } else if (valMatcher instanceof ValWithErrorString valWithErrorStringMatcher) {
            final var errorMatcher = valWithErrorStringMatcher.getError();
            if (errorMatcher instanceof PlainString plainString) {
                return valError(plainString.getValue());
            } else {
                //TODO Handling for StringMatchers
            }
            return valError();
        }
        return null;
    }
}
