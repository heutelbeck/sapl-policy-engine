package io.sapl.test.interfaces;

import io.sapl.test.grammar.sAPLTest.SAPLTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public interface SaplTestDslInterpreter {
    default SAPLTest loadAsResource(String input) {
        final var inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        return loadAsResource(inputStream);
    }

    SAPLTest loadAsResource(InputStream inputStream);

}
