package io.sapl.test.dsl.setup;

import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.lang.DefaultSaplTestInterpreter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SaplTestInterpreterFactory {
    public static SaplTestInterpreter create() {
        return new DefaultSaplTestInterpreter();
    }
}
