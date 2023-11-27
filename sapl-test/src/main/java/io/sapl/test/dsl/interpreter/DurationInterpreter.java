package io.sapl.test.dsl.interpreter;


import io.sapl.test.SaplTestException;
import java.time.Duration;

public class DurationInterpreter {
    Duration getJavaDurationFromDuration(final io.sapl.test.grammar.sAPLTest.Duration duration) {
        if (duration == null) {
            throw new SaplTestException("The passed Duration is null");
        }
        try {
            return java.time.Duration.parse(duration.getDuration()).abs();
        } catch (Exception e) {
            throw new SaplTestException("The provided duration has an invalid format");
        }
    }
}
