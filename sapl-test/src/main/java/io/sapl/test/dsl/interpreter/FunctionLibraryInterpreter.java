package io.sapl.test.dsl.interpreter;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;

public class FunctionLibraryInterpreter {
    Object getFunctionLibrary(final FunctionLibrary functionLibrary) {
        return switch (functionLibrary) {
            case FILTER -> new FilterFunctionLibrary();
            case LOGGING -> new LoggingFunctionLibrary();
            case STANDARD -> new StandardFunctionLibrary();
            case TEMPORAL -> new TemporalFunctionLibrary();
        };
    }
}
