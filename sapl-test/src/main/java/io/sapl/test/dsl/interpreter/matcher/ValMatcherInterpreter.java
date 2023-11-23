package io.sapl.test.dsl.interpreter.matcher;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interpreter.ValInterpreter;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithError;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class ValMatcherInterpreter {

    private final ValInterpreter valInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;
    private final StringMatcherInterpreter stringMatcherInterpreter;

    public Matcher<Val> getHamcrestValMatcher(final ValMatcher valMatcher) {
        if (valMatcher instanceof ValWithValue valWithValueMatcher) {
            return is(valInterpreter.getValFromValue(valWithValueMatcher.getValue()));
        } else if (valMatcher instanceof AnyVal) {
            return anyVal();
        } else if (valMatcher instanceof ValWithMatcher valWithMatcherMatcher) {
            return val(jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(valWithMatcherMatcher.getMatcher()));
        } else if (valMatcher instanceof ValWithError valWithErrorStringMatcher) {
            final var errorMatcher = valWithErrorStringMatcher.getError();
            if (errorMatcher instanceof PlainString plainString) {
                return valError(plainString.getText());
            } else if (errorMatcher instanceof StringMatcher stringMatcher) {
                return valError(stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher));
            }
            return valError();
        }
        throw new SaplTestException("Unknown type of ValMatcher");
    }
}
