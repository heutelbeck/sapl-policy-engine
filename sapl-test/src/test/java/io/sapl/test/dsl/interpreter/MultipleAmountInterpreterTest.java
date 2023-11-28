package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sapl.test.SaplTestException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultipleAmountInterpreterTest {
    private MultipleAmountInterpreter multipleAmountInterpreter;

    @BeforeEach
    void setUp() {
        multipleAmountInterpreter = new MultipleAmountInterpreter();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "x", "5c", "5.3x", "", "longString5", "xx", "0", Integer.MIN_VALUE + "x"})
    @NullSource
    void getAmountFromMultipleAmountString_handlesInvalidAmount_throwsSaplTestException(final String amount) {
        final var exception = assertThrows(SaplTestException.class, () -> multipleAmountInterpreter.getAmountFromMultipleAmountString(amount));

        assertEquals("Given amount has invalid format", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validAmountPairs")
    void getAmountFromMultipleAmountString_handlesValidAmount_returnsInt(final String amount, final int expectedResult) {
        final var result = multipleAmountInterpreter.getAmountFromMultipleAmountString(amount);

        assertEquals(expectedResult, result);
    }

    private static Stream<Arguments> validAmountPairs() {
        return Stream.of(
                Arguments.of("5x", 5),
                Arguments.of("12341x", 12341),
                Arguments.of("0x", 0),
                Arguments.of("12x", 12),
                Arguments.of(Integer.MAX_VALUE + "x", Integer.MAX_VALUE),
                Arguments.of("-123x", 123),
                Arguments.of("-0x", 0)
        );
    }
}