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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.hamcrest.jackson.IsJsonObject;
import com.spotify.hamcrest.jackson.JsonMatchers;
import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.IsJsonArray;
import io.sapl.test.grammar.sAPLTest.IsJsonBoolean;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.IsJsonNumber;
import io.sapl.test.grammar.sAPLTest.IsJsonText;
import io.sapl.test.grammar.sAPLTest.JsonArrayMatcher;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import io.sapl.test.grammar.sAPLTest.JsonObjectMatcher;
import io.sapl.test.grammar.sAPLTest.StringIsNull;
import io.sapl.test.grammar.sAPLTest.StringOrStringMatcher;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonNodeMatcherInterpreterTests {
    @Mock
    private StringMatcherInterpreter   stringMatcherInterpreterMock;
    @InjectMocks
    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;
    @Mock
    private Matcher<? super JsonNode>  jsonNodeMatcherMock;

    @Mock
    private Matcher<String> stringMatcherMock;

    private final MockedStatic<JsonMatchers> jsonMatchersMockedStatic     = mockStatic(JsonMatchers.class);
    private final MockedStatic<Matchers>     hamcrestMatchersMockedStatic = mockStatic(Matchers.class);

    @AfterEach
    void tearDown() {
        jsonMatchersMockedStatic.close();
        hamcrestMatchersMockedStatic.close();
    }

    private <T extends JsonNodeMatcher> T buildJsonNodeMatcher(final String input, final Class<T> clazz) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getJsonNodeMatcherRule, clazz);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesNullMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(null));

        assertEquals("Unknown type of JsonNodeMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesUnknownMatcher_throwsSaplTestException() {
        final var matcherMock = mock(JsonNodeMatcher.class);

        final var exception = assertThrows(SaplTestException.class,
                () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

        assertEquals("Unknown type of JsonNodeMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNull_returnsJsonNullMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("null", IsJsonNull.class);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithNullMatcher_returnsAnyJsonTextMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("text", IsJsonText.class);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonText).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithUnknownMatcher_throwsSaplTestException() {
        final var matcherMock = mock(IsJsonText.class);

        final var unknownMatcher = mock(StringOrStringMatcher.class);
        when(matcherMock.getMatcher()).thenReturn(unknownMatcher);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonText).thenReturn(jsonNodeMatcherMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

        assertEquals("Unknown type of StringOrStringMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithPlainString_returnsJsonTextMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("text \"foo\"", IsJsonText.class);

        jsonMatchersMockedStatic.when(() -> jsonText("foo")).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithStringMatcher_returnsJsonTextMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("text null", IsJsonText.class);

        doReturn(stringMatcherMock).when(stringMatcherInterpreterMock)
                .getHamcrestStringMatcher(any(StringIsNull.class));

        jsonMatchersMockedStatic.when(() -> jsonText(stringMatcherMock)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNumberWithNullNumber_returnsAnyJsonNumberMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("number", IsJsonNumber.class);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonNumber).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNumber_returnsJsonBigDecimalMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("number 10", IsJsonNumber.class);

        jsonMatchersMockedStatic.when(() -> jsonBigDecimal(BigDecimal.TEN)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithNullValue_returnsAnyJsonBooleanMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("boolean", IsJsonBoolean.class);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithUnknownType_throwsSaplTestException() {
        final var matcherMock = mock(IsJsonBoolean.class);

        final var booleanLiteralMock = mock(Value.class);

        when(matcherMock.getLiteral()).thenReturn(booleanLiteralMock);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonNodeMatcherMock);

        final var exception = assertThrows(SaplTestException.class,
                () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

        assertEquals("Unknown type of BooleanLiteral", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithTrueLiteral_returnsJsonBooleanMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("boolean true", IsJsonBoolean.class);

        jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonBoolean(true)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithFalseLiteral_returnsJsonBooleanMatcher() {
        final var jsonNodeMatcher = buildJsonNodeMatcher("boolean false", IsJsonBoolean.class);

        jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonBoolean(false)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Nested
    @DisplayName("JsonArray tests")
    class JsonArrayTests {

        @Mock
        Matcher<? super Collection<? extends JsonNode>> jsonNodesMatcher;

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithNullArrayMatcher_returnsAnyJsonArrayMatcher() {
            final var jsonNodeMatcher = buildJsonNodeMatcher("array", IsJsonArray.class);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            assertEquals(jsonNodeMatcherMock, result);
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithNullMatchers_throwsSaplTestException() {
            final var matcherMock = mock(IsJsonArray.class);

            final var jsonArrayMatcher = mock(JsonArrayMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonArrayMatcher);

            when(jsonArrayMatcher.getMatchers()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonNodeMatcher found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithEmptyMatchers_throwsSaplTestException() {
            final var matcherMock = mock(IsJsonArray.class);

            final var jsonArrayMatcher = mock(JsonArrayMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonArrayMatcher);

            TestHelper.mockEListResult(jsonArrayMatcher::getMatchers, Collections.emptyList());

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonNodeMatcher found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithMultipleDifferentMatchers_returnsJsonArrayMatcher() {
            final var jsonNodeMatcher = buildJsonNodeMatcher("array where [ null, boolean ]", IsJsonArray.class);

            final var jsonNullMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNullMatcherMock);

            final var jsonBooleanMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonBooleanMatcherMock);

            hamcrestMatchersMockedStatic
                    .when(() -> Matchers.is(eq(List.of(jsonNullMatcherMock, jsonBooleanMatcherMock))))
                    .thenReturn(jsonNodesMatcher);

            jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonArray(jsonNodesMatcher))
                    .thenReturn(jsonNodeMatcherMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            assertEquals(jsonNodeMatcherMock, result);
        }
    }

    @Nested
    @DisplayName("JsonObject tests")
    class JsonObjectTests {

        @Mock
        IsJsonObject isJsonObjectMock;

        @Mock
        Matcher<JsonNode> jsonNullMatcherMock;
        @Mock
        Matcher<JsonNode> jsonBooleanMatcherMock;

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithNullObjectMatcher_returnsAnyJsonObjectMatcher() {
            final var jsonNodeMatcher = buildJsonNodeMatcher("object",
                    io.sapl.test.grammar.sAPLTest.IsJsonObject.class);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            assertEquals(isJsonObjectMock, result);
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithNullMatchers_throwsSaplTestException() {
            final var matcherMock = mock(io.sapl.test.grammar.sAPLTest.IsJsonObject.class);

            final var jsonObjectMatcherMock = mock(JsonObjectMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonObjectMatcherMock);

            when(jsonObjectMatcherMock.getMembers()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonObjectMatcherPair found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithEmptyMatchers_returnsAnyJsonObjectMatcher() {
            final var matcherMock = mock(io.sapl.test.grammar.sAPLTest.IsJsonObject.class);

            final var jsonObjectMatcherMock = mock(JsonObjectMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonObjectMatcherMock);

            TestHelper.mockEListResult(jsonObjectMatcherMock::getMembers, Collections.emptyList());

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonObjectMatcherPair found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithMultipleDifferentMatchers_returnsJsonObjectMatcher() {
            final var jsonNodeMatcher = buildJsonNodeMatcher(
                    "object where { \"jsonNullKey\" is null and \"jsonBooleanKey\" is boolean}",
                    io.sapl.test.grammar.sAPLTest.IsJsonObject.class);

            final var initialJsonObjectMock = mock(com.spotify.hamcrest.jackson.IsJsonObject.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(initialJsonObjectMock);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNullMatcherMock);

            final var jsonObjectWithOneWhereConditionMock = mock(com.spotify.hamcrest.jackson.IsJsonObject.class);
            when(initialJsonObjectMock.where("jsonNullKey", jsonNullMatcherMock))
                    .thenReturn(jsonObjectWithOneWhereConditionMock);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonBooleanMatcherMock);

            when(jsonObjectWithOneWhereConditionMock.where("jsonBooleanKey", jsonBooleanMatcherMock))
                    .thenReturn(isJsonObjectMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            assertEquals(isJsonObjectMock, result);
        }
    }
}
