package io.sapl.test.services.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sAPLTest.Equals;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class JsonNodeMatcherInterpreterTest {

    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    private MockedStatic<Matchers> matchersMockedStatic;

    @BeforeEach
    void setUp() {
        matchersMockedStatic = mockStatic(Matchers.class);
        jsonNodeMatcherInterpreter = new JsonNodeMatcherInterpreter();
    }

    @AfterEach
    void tearDown() {
        matchersMockedStatic.close();
    }

    @Test
    void getJsonNodeMatcherFromJsonNodeMatcher_handlesNullMatcher_returnsNull() {
        final var result = jsonNodeMatcherInterpreter.getJsonNodeMatcherFromJsonNodeMatcher(null);

        assertNull(result);
    }

    @Test
    void getJsonNodeMatcherFromJsonNodeMatcher_handlesUnknownMatcher_returnsNull() {
        final var matcherMock = mock(JsonNodeMatcher.class);
        final var result = jsonNodeMatcherInterpreter.getJsonNodeMatcherFromJsonNodeMatcher(matcherMock);

        assertNull(result);
    }

    @Test
    void getJsonNodeMatcherFromJsonNodeMatcher_handlesEqualsMatcher_returnsMatcher() {
        final var matcherMock = mock(Equals.class);

        when(matcherMock.getValue()).thenReturn("foo");

        final var isMatcherMock = mock(Matcher.class);
        matchersMockedStatic.when(() -> Matchers.is("foo")).thenReturn(isMatcherMock);

        final var result = jsonNodeMatcherInterpreter.getJsonNodeMatcherFromJsonNodeMatcher(matcherMock);

        assertEquals(isMatcherMock, result);
    }
}