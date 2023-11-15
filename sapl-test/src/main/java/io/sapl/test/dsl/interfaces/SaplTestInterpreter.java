package io.sapl.test.dsl.interfaces;

import io.sapl.test.grammar.sAPLTest.SAPLTest;
import java.io.InputStream;

public interface SaplTestInterpreter {
    SAPLTest loadAsResource(InputStream inputStream);

    SAPLTest loadAsResource(String input);
}
