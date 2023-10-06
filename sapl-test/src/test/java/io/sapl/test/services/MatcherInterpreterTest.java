package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.hamcrest.IsVal;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.grammar.sAPLTest.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatcherInterpreterTest {

    private ValInterpreter valInterpreterMock;
    private MatcherInterpreter matcherInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        matcherInterpreter = new MatcherInterpreter(valInterpreterMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getValMatcherFromParameterMatcher_forNullParameterMatcher_returnsNull() {
        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(null);
        assertNull(result);
    }

    @Test
    void getValMatcherFromParameterMatcher_forUnknownParameterMatcher_returnsNull() {
        final var unknownParameterMatcherMock = mock(ValMatcher.class);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(unknownParameterMatcherMock);
        assertNull(result);
    }

    @Test
    void getValMatcherFromParameterMatcher_forEqualsParameterMatcher_returnsIsMatcher() {
        final var valWithValueMock = mock(ValWithValue.class);
        final var valMock = mock(Value.class);
        when(valWithValueMock.getValue()).thenReturn(valMock);

        final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
        when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(valWithValueMock);
        assertTrue(result.matches(saplValMock));
    }

    @Test
    void getValMatcherFromParameterMatcher_forAnyParameterMatcher_returnsAnyValMatcher() {
        final var anyMock = mock(AnyVal.class);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(anyMock);
        assertInstanceOf(IsVal.class, result);
    }
}