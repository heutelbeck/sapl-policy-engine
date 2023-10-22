package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DurationInterpreterTest {

    private DurationInterpreter durationInterpreter;


    @BeforeEach
    void setUp() {
        durationInterpreter = new DurationInterpreter();
    }


    @Test
    void getJavaDurationFromDuration_handlesNullDuration_returnsNull() {
        final var result = durationInterpreter.getJavaDurationFromDuration(null);

        assertNull(result);
    }

    @Test
    void getJavaDurationFromDuration_parseThrowsException_throwsSaplTestException() {
        final var durationMock = mock(io.sapl.test.grammar.sAPLTest.Duration.class);
        when(durationMock.getDuration()).thenReturn("");

        final var exception = assertThrows(SaplTestException.class, () -> durationInterpreter.getJavaDurationFromDuration(durationMock));

        assertEquals("The provided duration has an invalid format", exception.getMessage());
    }

    @Test
    void getJavaDurationFromDuration_handlesNegativeDuration_returnsAbsoluteDuration() {
        final var durationMock = mock(io.sapl.test.grammar.sAPLTest.Duration.class);
        when(durationMock.getDuration()).thenReturn("-PT5S");

        final var result = durationInterpreter.getJavaDurationFromDuration(durationMock);

        assertEquals(Duration.ofSeconds(5), result);
    }

    @Test
    void getJavaDurationFromDuration_handlesPositiveDuration_returnsAbsoluteDuration() {
        final var durationMock = mock(io.sapl.test.grammar.sAPLTest.Duration.class);
        when(durationMock.getDuration()).thenReturn("PT5M");

        final var result = durationInterpreter.getJavaDurationFromDuration(durationMock);

        assertEquals(Duration.ofMinutes(5), result);
    }
}