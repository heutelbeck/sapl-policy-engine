package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import org.junit.jupiter.api.Test;

class SaplTestInterpreterFactoryTest {

    @Test
    void create_constructsInstanceOfSaplTestInterpreter_returnsSaplTestInterpreterInstance() {
        final var result = SaplTestInterpreterFactory.create();

        assertInstanceOf(SaplTestInterpreter.class, result);
    }
}