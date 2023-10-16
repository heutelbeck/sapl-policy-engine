package io.sapl.test.dsl.interpreter.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.dsl.interpreter.ValInterpreter;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithErrorString;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.grammar.sAPLTest.Value;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ValMatcherInterpreterTest {

    private ValInterpreter valInterpreterMock;
    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreterMock;

    private StringMatcherInterpreter stringMatcherInterpreterMock;

    private ValMatcherInterpreter matcherInterpreter;

    private MockedStatic<Matchers> hamcrestMatchersMockedStatic;
    private MockedStatic<CoreMatchers> hamcrestCoreMatchersMockedStatic;
    private MockedStatic<io.sapl.hamcrest.Matchers> saplMatchersMockedStatic;


    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        jsonNodeMatcherInterpreterMock = mock(JsonNodeMatcherInterpreter.class);
        stringMatcherInterpreterMock = mock(StringMatcherInterpreter.class);
        hamcrestMatchersMockedStatic = mockStatic(Matchers.class);
        hamcrestCoreMatchersMockedStatic = mockStatic(CoreMatchers.class);
        saplMatchersMockedStatic = mockStatic(io.sapl.hamcrest.Matchers.class);
        matcherInterpreter = new ValMatcherInterpreter(valInterpreterMock, jsonNodeMatcherInterpreterMock, stringMatcherInterpreterMock);
    }

    @AfterEach
    void tearDown() {
        hamcrestMatchersMockedStatic.close();
        hamcrestCoreMatchersMockedStatic.close();
        saplMatchersMockedStatic.close();
    }

    @Test
    void getHamcrestValMatcher_forNullParameterMatcher_returnsNull() {
        final var result = matcherInterpreter.getHamcrestValMatcher(null);
        assertNull(result);
    }

    @Test
    void getHamcrestValMatcher_forUnknownParameterMatcher_returnsNull() {
        final var unknownParameterMatcherMock = mock(ValMatcher.class);

        final var result = matcherInterpreter.getHamcrestValMatcher(unknownParameterMatcherMock);
        assertNull(result);
    }

    @Test
    void getHamcrestValMatcher_forValWithValue_returnsIsMatcher() {
        final var valWithValueMock = mock(ValWithValue.class);

        final var valMock = mock(Value.class);
        when(valWithValueMock.getValue()).thenReturn(valMock);

        final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
        when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);

        final var isMatcherMock = mock(Matcher.class);
        hamcrestCoreMatchersMockedStatic.when(() -> CoreMatchers.is(saplValMock)).thenReturn(isMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valWithValueMock);

        assertEquals(isMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forAnyVal_returnsAnyValMatcher() {
        final var anyMock = mock(AnyVal.class);

        final var anyValMatcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::anyVal).thenReturn(anyValMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(anyMock);

        assertEquals(anyValMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithMatcher_returnsValMatcher() {
        final var valWithMatcherMock = mock(ValWithMatcher.class);

        final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
        when(valWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

        final var matcherMock = mock(Matcher.class);
        when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(matcherMock);

        final var valWithJsonNodeMatcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.val(matcherMock)).thenReturn(valWithJsonNodeMatcherMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valWithMatcherMock);

        assertEquals(valWithJsonNodeMatcherMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorStringAndNullMatcher_returnsAnyValErrorMatcher() {
        final var valWithErrorString = mock(ValWithErrorString.class);

        when(valWithErrorString.getError()).thenReturn(null);

        final var valErrorMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::valError).thenReturn(valErrorMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valWithErrorString);

        assertEquals(valErrorMock, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorStringAndStringMatcher_returnsValErrorWithStringMatcher() {
        final var valWithErrorString = mock(ValWithErrorString.class);

        final var stringMatcher = mock(StringMatcher.class);
        when(valWithErrorString.getError()).thenReturn(stringMatcher);

        final var errorStringMatcher = mock(Matcher.class);
        when(stringMatcherInterpreterMock.getHamcrestStringMatcher(stringMatcher)).thenReturn(errorStringMatcher);

        final var valErrorWithMatcher = mock(Matcher.class);

        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.valError(errorStringMatcher)).thenReturn(valErrorWithMatcher);

        final var result = matcherInterpreter.getHamcrestValMatcher(valWithErrorString);

        assertEquals(valErrorWithMatcher, result);
    }

    @Test
    void getHamcrestValMatcher_forValWithErrorStringAndPlainString_returnsValErrorMatcherWithMessage() {
        final var valWithErrorString = mock(ValWithErrorString.class);

        final var stringMatcher = mock(PlainString.class);
        when(valWithErrorString.getError()).thenReturn(stringMatcher);

        when(stringMatcher.getValue()).thenReturn("foo");

        final var valErrorMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.valError("foo")).thenReturn(valErrorMock);

        final var result = matcherInterpreter.getHamcrestValMatcher(valWithErrorString);

        assertEquals(valErrorMock, result);
    }
}