package io.sapl.test.dsl.interpreter.matcher;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBoolean;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNull;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNumber;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.test.grammar.sAPLTest.*;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class JsonNodeMatcherInterpreter {

    private final StringMatcherInterpreter stringMatcherInterpreter;

    Matcher<JsonNode> getHamcrestJsonNodeMatcher(final JsonNodeMatcher jsonNodeMatcher) {
        if (jsonNodeMatcher instanceof IsJsonNull) {
            return jsonNull();
        } else if (jsonNodeMatcher instanceof IsJsonText text) {
            final var stringOrMatcher = text.getText();
            if (stringOrMatcher instanceof PlainString plainString) {
                return jsonText(plainString.getValue());
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
        return null;
    }

    private Matcher<JsonNode> interpretJsonArray(IsJsonArray isJsonArray) {
        final var arrayMatcher = isJsonArray.getMatcher();
        if (arrayMatcher == null) {
            return jsonArray();
        }

        final var matchers = arrayMatcher.getMatchers();
        if (matchers == null || matchers.isEmpty()) {
            return jsonArray();
        }

        final var mappedMatchers = matchers.stream().map(this::getHamcrestJsonNodeMatcher).toList();
        return jsonArray(is(mappedMatchers));
    }

    private Matcher<JsonNode> interpretJsonObject(IsJsonObject isJsonObject) {
        final var jsonObjectMatcher = isJsonObject.getMatcher();

        if (jsonObjectMatcher == null) {
            return jsonObject();
        }

        final var matchers = jsonObjectMatcher.getMatchers();

        if (matchers == null || matchers.isEmpty()) {
            return jsonObject();
        }

        return matchers.stream().reduce(jsonObject(), (previous, matcherPair) -> previous.where(matcherPair.getKey(), getHamcrestJsonNodeMatcher(matcherPair.getMatcher())), (oldEntry, newEntry) -> newEntry);
    }
}
