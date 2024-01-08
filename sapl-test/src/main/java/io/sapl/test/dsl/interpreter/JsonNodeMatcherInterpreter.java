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
            return interpretJsonText(text);
        } else if (jsonNodeMatcher instanceof IsJsonNumber isJsonNumber) {
            return interpretJsonNumber(isJsonNumber);
        } else if (jsonNodeMatcher instanceof IsJsonBoolean isJsonBoolean) {
            return interpretJsonBoolean(isJsonBoolean);
        } else if (jsonNodeMatcher instanceof IsJsonArray isJsonArray) {
            return interpretJsonArray(isJsonArray);
        } else if (jsonNodeMatcher instanceof IsJsonObject isJsonObject) {
            return interpretJsonObject(isJsonObject);
        }

        throw new SaplTestException("Unknown type of JsonNodeMatcher");
    }

    private Matcher<JsonNode> interpretJsonNumber(IsJsonNumber isJsonNumber) {
        final var number = isJsonNumber.getNumber();

        return number == null ? jsonNumber() : jsonBigDecimal(number);
    }

    private Matcher<JsonNode> interpretJsonText(final IsJsonText text) {
        final var stringOrMatcher = text.getText();

        if (stringOrMatcher == null) {
            return jsonText();
        }

        if (stringOrMatcher instanceof PlainString plainString) {
            return jsonText(plainString.getText());
        } else if (stringOrMatcher instanceof StringMatcher stringMatcher) {
            return jsonText(stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher));
        }

        throw new SaplTestException("Unknown type of StringOrStringMatcher");
    }

    private Matcher<JsonNode> interpretJsonBoolean(final IsJsonBoolean isJsonBoolean) {
        final var boolValue = isJsonBoolean.getValue();

        if (boolValue == null) {
            return jsonBoolean();
        }

        if (boolValue instanceof TrueLiteral) {
            return jsonBoolean(true);
        } else if (boolValue instanceof FalseLiteral) {
            return jsonBoolean(false);
        }

        throw new SaplTestException("Unknown type of BooleanLiteral");
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

        return matchers.stream()
                .reduce(jsonObject(),
                        (previous, matcherPair) -> previous.where(matcherPair.getKey(),
                                getHamcrestJsonNodeMatcher(matcherPair.getMatcher())),
                        (oldEntry, newEntry) -> newEntry);
    }
}
