package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FunctionLibraryInterpreterTest {

    private FunctionLibraryInterpreter functionLibraryInterpreter;

    @BeforeEach
    void setUp() {
        functionLibraryInterpreter = new FunctionLibraryInterpreter();
    }

    @ParameterizedTest
    @MethodSource("functionLibraryArgumentProvider")
    void getFunctionLibrary_mapsFunctionLibraryToExpectedLibraryInstance_returnsLibraryInstance(final FunctionLibrary functionLibrary, final Class<?> libraryClass) {
        final var result = functionLibraryInterpreter.getFunctionLibrary(functionLibrary);

        assertInstanceOf(libraryClass, result);
    }

    private static Stream<Arguments> functionLibraryArgumentProvider() {
        return Stream.of(
                Arguments.of(FunctionLibrary.FILTER, FilterFunctionLibrary.class),
                Arguments.of(FunctionLibrary.LOGGING, LoggingFunctionLibrary.class),
                Arguments.of(FunctionLibrary.STANDARD, StandardFunctionLibrary.class),
                Arguments.of(FunctionLibrary.TEMPORAL, TemporalFunctionLibrary.class)
        );
    }
}