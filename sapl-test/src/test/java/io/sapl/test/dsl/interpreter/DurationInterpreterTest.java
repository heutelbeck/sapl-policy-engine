package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.SaplTestException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class DurationInterpreterTest {
    private DurationInterpreter durationInterpreter;

    private final MockedStatic<Duration> durationMockedStatic = mockStatic(Duration.class, Answers.RETURNS_DEEP_STUBS);

    @BeforeEach
    void setUp() {
        durationInterpreter = new DurationInterpreter();
    }

    @AfterEach
    void tearDown() {
        durationMockedStatic.close();
    }

    @Test
    void getJavaDurationFromDuration_forNullDuration_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> durationInterpreter.getJavaDurationFromDuration(null));

        assertEquals("The passed Duration is null", exception.getMessage());
    }

    @Test
    void getJavaDurationFromDuration_parseThrowsException_throwsSaplTestException() {
        final var durationMock = mock(io.sapl.test.grammar.sAPLTest.Duration.class);
        when(durationMock.getDuration()).thenReturn("");

        durationMockedStatic.when(() -> Duration.parse("")).thenThrow(new RuntimeException("error"));

        final var exception = assertThrows(SaplTestException.class, () -> durationInterpreter.getJavaDurationFromDuration(durationMock));

        assertEquals("The provided duration has an invalid format", exception.getMessage());
    }

    @Test
    void getJavaDurationFromDuration_handlesDuration_returnsAbsoluteDuration() {
        final var durationMock = mock(io.sapl.test.grammar.sAPLTest.Duration.class);
        when(durationMock.getDuration()).thenReturn("-PT5S");

        final var javaDurationMock = mock(Duration.class);
        durationMockedStatic.when(() -> Duration.parse("-PT5S").abs()).thenReturn(javaDurationMock);

        final var result = durationInterpreter.getJavaDurationFromDuration(durationMock);

        assertEquals(javaDurationMock, result);
    }
}