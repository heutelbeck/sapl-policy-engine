package io.sapl.test.dsl.interpreter;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;

class FunctionLibraryInterpreter {
    Object getFunctionLibrary(final FunctionLibrary functionLibrary) {
        if (functionLibrary == null) {
            throw new SaplTestException("FunctionLibrary is null");
        }

        return switch (functionLibrary) {
            case FILTER -> new FilterFunctionLibrary();
            case LOGGING -> new LoggingFunctionLibrary();
            case STANDARD -> new StandardFunctionLibrary();
            case TEMPORAL -> new TemporalFunctionLibrary();
        };
    }
}
