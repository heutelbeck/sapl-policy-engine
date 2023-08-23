package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.hamcrest.IsVal;
import io.sapl.test.grammar.sAPLTest.Any;
import io.sapl.test.grammar.sAPLTest.Equals;
import io.sapl.test.grammar.sAPLTest.ParameterMatcher;
import io.sapl.test.grammar.sAPLTest.Val;
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
        final var unknownParameterMatcherMock = mock(ParameterMatcher.class);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(unknownParameterMatcherMock);
        assertNull(result);
    }

    @Test
    void getValMatcherFromParameterMatcher_forEqualsParameterMatcher_returnsIsMatcher() {
        final var equalsMock = mock(Equals.class);
        final var valMock = mock(Val.class);
        when(equalsMock.getValue()).thenReturn(valMock);

        final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
        when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(equalsMock);
        assertTrue(result.matches(saplValMock));
    }

    @Test
    void getValMatcherFromParameterMatcher_forAnyParameterMatcher_returnsAnyValMatcher() {
        final var anyMock = mock(Any.class);

        final var result = matcherInterpreter.getValMatcherFromParameterMatcher(anyMock);
        assertInstanceOf(IsVal.class, result);
    }
}