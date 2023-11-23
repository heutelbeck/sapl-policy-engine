package io.sapl.test.dsl.interpreter.matcher;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.spotify.hamcrest.jackson.JsonMatchers;
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.IsJsonArray;
import io.sapl.test.grammar.sAPLTest.IsJsonBoolean;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.IsJsonNumber;
import io.sapl.test.grammar.sAPLTest.IsJsonObject;
import io.sapl.test.grammar.sAPLTest.IsJsonText;
import io.sapl.test.grammar.sAPLTest.JsonArrayMatcher;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import io.sapl.test.grammar.sAPLTest.JsonObjectMatcher;
import io.sapl.test.grammar.sAPLTest.JsonObjectMatcherPair;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.StringOrStringMatcher;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import io.sapl.test.grammar.sAPLTest.Value;
import java.math.BigDecimal;
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
class JsonNodeMatcherInterpreterTest {
    @Mock
    private StringMatcherInterpreter stringMatcherInterpreterMock;
    @InjectMocks
    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;
    @Mock
    private Matcher<? super JsonNode> jsonNodeMatcherMock;

    private final MockedStatic<JsonMatchers> jsonMatchersMockedStatic = mockStatic(JsonMatchers.class);
    private final MockedStatic<Matchers> hamcrestMatchersMockedStatic = mockStatic(Matchers.class);


    @AfterEach
    void tearDown() {
        jsonMatchersMockedStatic.close();
        hamcrestMatchersMockedStatic.close();
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesNullMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(null));

        assertEquals("Unknown type of JsonNodeMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesUnknownMatcher_throwsSaplTestException() {
        final var matcherMock = mock(JsonNodeMatcher.class);

        final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

        assertEquals("Unknown type of JsonNodeMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNull_returnsJsonNullMatcher() {
        final var matcherMock = mock(IsJsonNull.class);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithNullMatcher_returnsAnyJsonTextMatcher() {
        final var matcherMock = mock(IsJsonText.class);

        when(matcherMock.getText()).thenReturn(null);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonText).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithUnknownMatcher_returnsAnyJsonTextMatcher() {
        final var matcherMock = mock(IsJsonText.class);

        final var unknownMatcher = mock(StringOrStringMatcher.class);
        when(matcherMock.getText()).thenReturn(unknownMatcher);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonText).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithPlainString_returnsJsonTextMatcher() {
        final var matcherMock = mock(IsJsonText.class);

        final var plainString = mock(PlainString.class);
        when(matcherMock.getText()).thenReturn(plainString);

        when(plainString.getValue()).thenReturn("foo");

        jsonMatchersMockedStatic.when(() -> jsonText("foo")).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonTextWithStringMatcher_returnsJsonTextMatcher() {
        final var matcherMock = mock(IsJsonText.class);

        final var stringMatcher = mock(StringMatcher.class);
        when(matcherMock.getText()).thenReturn(stringMatcher);

        final var hamcrestStringMatcherMock = mock(Matcher.class);
        when(stringMatcherInterpreterMock.getHamcrestStringMatcher(stringMatcher)).thenReturn(hamcrestStringMatcherMock);

        jsonMatchersMockedStatic.when(() -> jsonText(hamcrestStringMatcherMock)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNumberWithNullNumber_returnsAnyJsonNumberMatcher() {
        final var matcherMock = mock(IsJsonNumber.class);

        when(matcherMock.getNumber()).thenReturn(null);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonNumber).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonNumber_returnsJsonBigDecimalMatcher() {
        final var matcherMock = mock(IsJsonNumber.class);

        when(matcherMock.getNumber()).thenReturn(BigDecimal.TEN);

        jsonMatchersMockedStatic.when(() -> jsonBigDecimal(BigDecimal.TEN)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithNullValue_returnsAnyJsonBooleanMatcher() {
        final var matcherMock = mock(IsJsonBoolean.class);

        when(matcherMock.getValue()).thenReturn(null);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithUnknownType_returnsAnyJsonBooleanMatcher() {
        final var matcherMock = mock(IsJsonBoolean.class);

        final var booleanLiteralMock = mock(Value.class);

        when(matcherMock.getValue()).thenReturn(booleanLiteralMock);

        jsonMatchersMockedStatic.when(JsonMatchers::jsonBoolean).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithTrueLiteral_returnsJsonBooleanMatcher() {
        final var matcherMock = mock(IsJsonBoolean.class);

        final var trueLiteralMock = mock(TrueLiteral.class);

        when(matcherMock.getValue()).thenReturn(trueLiteralMock);

        jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonBoolean(true)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestJsonNodeMatcher_handlesIsJsonBooleanWithFalseLiteral_returnsJsonBooleanMatcher() {
        final var matcherMock = mock(IsJsonBoolean.class);

        final var falseLiteralMock = mock(FalseLiteral.class);

        when(matcherMock.getValue()).thenReturn(falseLiteralMock);

        jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonBoolean(false)).thenReturn(jsonNodeMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

        assertEquals(jsonNodeMatcherMock, result);
    }

    @Nested
    @DisplayName("JsonArray tests")
    class JsonArrayTests {
        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithNullArrayMatcher_returnsAnyJsonArrayMatcher() {
            final var matcherMock = mock(IsJsonArray.class);

            when(matcherMock.getMatcher()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

            assertEquals(jsonNodeMatcherMock, result);
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithNullMatchers_throwsSaplTestException() {
            final var matcherMock = mock(IsJsonArray.class);

            final var jsonArrayMatcher = mock(JsonArrayMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonArrayMatcher);

            when(jsonArrayMatcher.getMatchers()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonNodeMatcher found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithEmptyMatchers_throwsSaplTestException() {
            final var matcherMock = mock(IsJsonArray.class);

            final var jsonArrayMatcher = mock(JsonArrayMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonArrayMatcher);

            final var matchers = Helper.mockEList(List.<JsonNodeMatcher>of());
            when(jsonArrayMatcher.getMatchers()).thenReturn(matchers);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonArray).thenReturn(jsonNodeMatcherMock);

            final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonNodeMatcher found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonArrayWithMultipleDifferentMatchers_returnsJsonArrayMatcher() {
            final var matcherMock = mock(IsJsonArray.class);

            final var jsonArrayMatcher = mock(JsonArrayMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonArrayMatcher);

            final var isJsonNullMock = mock(IsJsonNull.class);
            final var isJsonNumberMock = mock(IsJsonNumber.class);

            final var matchers = Helper.mockEList(List.of(isJsonNullMock, isJsonNumberMock));
            when(jsonArrayMatcher.getMatchers()).thenReturn(matchers);

            final var jsonNullMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNullMatcherMock);

            when(isJsonNumberMock.getNumber()).thenReturn(null);
            final var jsonNumberMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonNumber).thenReturn(jsonNumberMatcherMock);

            final var isEqualToMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(eq(List.of(jsonNullMatcherMock, jsonNumberMatcherMock)))).thenReturn(isEqualToMock);

            jsonMatchersMockedStatic.when(() -> JsonMatchers.jsonArray(isEqualToMock)).thenReturn(jsonNodeMatcherMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

            assertEquals(jsonNodeMatcherMock, result);
        }
    }

    @Nested
    @DisplayName("JsonObject tests")
    class JsonObjectTests {

        @Mock
        com.spotify.hamcrest.jackson.IsJsonObject isJsonObjectMock;

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithNullObjectMatcher_returnsAnyJsonObjectMatcher() {
            final var matcherMock = mock(IsJsonObject.class);

            when(matcherMock.getMatcher()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

            assertEquals(isJsonObjectMock, result);
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithNullMatchers_throwsSaplTestException() {
            final var matcherMock = mock(IsJsonObject.class);

            final var jsonObjectMatcherMock = mock(JsonObjectMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonObjectMatcherMock);

            when(jsonObjectMatcherMock.getMatchers()).thenReturn(null);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonObjectMatcherPair found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithEmptyMatchers_returnsAnyJsonObjectMatcher() {
            final var matcherMock = mock(IsJsonObject.class);

            final var jsonObjectMatcherMock = mock(JsonObjectMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonObjectMatcherMock);

            final var matchers = Helper.mockEList(List.<JsonObjectMatcherPair>of());
            when(jsonObjectMatcherMock.getMatchers()).thenReturn(matchers);

            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(isJsonObjectMock);

            final var exception = assertThrows(SaplTestException.class, () -> jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock));

            assertEquals("No JsonObjectMatcherPair found", exception.getMessage());
        }

        @Test
        void getHamcrestJsonNodeMatcher_handlesIsJsonObjectWithMultipleDifferentMatchers_returnsJsonObjectMatcher() {
            final var matcherMock = mock(IsJsonObject.class);

            final var jsonObjectMatcherMock = mock(JsonObjectMatcher.class);
            when(matcherMock.getMatcher()).thenReturn(jsonObjectMatcherMock);

            final var isJsonNullMock = mock(IsJsonNull.class);
            final var isJsonNumberMock = mock(IsJsonNumber.class);

            final var isJsonNullObjectMatcherPairMock = mock(JsonObjectMatcherPair.class);
            final var isJsonNumberObjectMatcherPairMock = mock(JsonObjectMatcherPair.class);

            final var matchers = Helper.mockEList(List.of(isJsonNullObjectMatcherPairMock, isJsonNumberObjectMatcherPairMock));
            when(jsonObjectMatcherMock.getMatchers()).thenReturn(matchers);

            final var initialJsonObjectMock = mock(com.spotify.hamcrest.jackson.IsJsonObject.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonObject).thenReturn(initialJsonObjectMock);

            when(isJsonNullObjectMatcherPairMock.getKey()).thenReturn("jsonNullKey");
            when(isJsonNullObjectMatcherPairMock.getMatcher()).thenReturn(isJsonNullMock);

            when(isJsonNumberObjectMatcherPairMock.getKey()).thenReturn("jsonNumberKey");
            when(isJsonNumberObjectMatcherPairMock.getMatcher()).thenReturn(isJsonNumberMock);

            final var jsonNullMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonNull).thenReturn(jsonNullMatcherMock);

            final var jsonObjectWithOneWhereConditionMock = mock(com.spotify.hamcrest.jackson.IsJsonObject.class);
            when(initialJsonObjectMock.where("jsonNullKey", jsonNullMatcherMock)).thenReturn(jsonObjectWithOneWhereConditionMock);

            when(isJsonNumberMock.getNumber()).thenReturn(null);
            final var jsonNumberMatcherMock = mock(Matcher.class);
            jsonMatchersMockedStatic.when(JsonMatchers::jsonNumber).thenReturn(jsonNumberMatcherMock);

            when(jsonObjectWithOneWhereConditionMock.where("jsonNumberKey", jsonNumberMatcherMock)).thenReturn(isJsonObjectMock);

            final var result = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcherMock);

            assertEquals(isJsonObjectMock, result);
        }
    }
}