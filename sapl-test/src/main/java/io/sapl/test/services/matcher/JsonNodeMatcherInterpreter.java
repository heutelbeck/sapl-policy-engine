package io.sapl.test.services.matcher;

import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.test.grammar.sAPLTest.Equals;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import org.hamcrest.Matcher;

public class JsonNodeMatcherInterpreter {
    Matcher<? super JsonNode> getJsonNodeMatcherFromJsonNodeMatcher(final JsonNodeMatcher jsonNodeMatcher) {
        if (jsonNodeMatcher instanceof Equals equals) {
            return is(equals.getValue());
        }
        return null;
    }
}
