package io.sapl.test.dsl.interpreter;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBoolean;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNull;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNumber;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.IsJsonArray;
import io.sapl.test.grammar.sAPLTest.IsJsonBoolean;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.IsJsonNumber;
import io.sapl.test.grammar.sAPLTest.IsJsonObject;
import io.sapl.test.grammar.sAPLTest.IsJsonText;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class JsonNodeMatcherInterpreter {

    private final StringMatcherInterpreter stringMatcherInterpreter;

    Matcher<JsonNode> getHamcrestJsonNodeMatcher(final JsonNodeMatcher jsonNodeMatcher) {
        if (jsonNodeMatcher instanceof IsJsonNull) {
            return jsonNull();
        } else if (jsonNodeMatcher instanceof IsJsonText text) {
            final var stringOrMatcher = text.getText();

            if (stringOrMatcher instanceof PlainString plainString) {
                return jsonText(plainString.getText());
            } else if (stringOrMatcher instanceof StringMatcher stringMatcher) {
                return jsonText(stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher));
            }

            return jsonText();
        } else if (jsonNodeMatcher instanceof IsJsonNumber isJsonNumber) {
            final var number = isJsonNumber.getNumber();

            return number == null ? jsonNumber() : jsonBigDecimal(number);
        } else if (jsonNodeMatcher instanceof IsJsonBoolean isJsonBoolean) {
            final var boolValue = isJsonBoolean.getValue();

            if (boolValue instanceof TrueLiteral) {
                return jsonBoolean(true);
            } else if (boolValue instanceof FalseLiteral) {
                return jsonBoolean(false);
            }

            return jsonBoolean();
        } else if (jsonNodeMatcher instanceof IsJsonArray isJsonArray) {
            return interpretJsonArray(isJsonArray);
        } else if (jsonNodeMatcher instanceof IsJsonObject isJsonObject) {
            return interpretJsonObject(isJsonObject);
        }

        throw new SaplTestException("Unknown type of JsonNodeMatcher");
    }

    private Matcher<JsonNode> interpretJsonArray(final IsJsonArray isJsonArray) {
        final var arrayMatcher = isJsonArray.getMatcher();

        if (arrayMatcher == null) {
            return jsonArray();
        }

        final var matchers = arrayMatcher.getMatchers();

        if (matchers == null || matchers.isEmpty()) {
            throw new SaplTestException("No JsonNodeMatcher found");
        }

        final var mappedMatchers = matchers.stream().map(this::getHamcrestJsonNodeMatcher).toList();

        return jsonArray(is(mappedMatchers));
    }

    private Matcher<JsonNode> interpretJsonObject(final IsJsonObject isJsonObject) {
        final var jsonObjectMatcher = isJsonObject.getMatcher();

        if (jsonObjectMatcher == null) {
            return jsonObject();
        }

        final var matchers = jsonObjectMatcher.getMatchers();

        if (matchers == null || matchers.isEmpty()) {
            throw new SaplTestException("No JsonObjectMatcherPair found");
        }

        return matchers.stream().reduce(jsonObject(), (previous, matcherPair) -> previous.where(matcherPair.getKey(), getHamcrestJsonNodeMatcher(matcherPair.getMatcher())), (oldEntry, newEntry) -> newEntry);
    }
}
