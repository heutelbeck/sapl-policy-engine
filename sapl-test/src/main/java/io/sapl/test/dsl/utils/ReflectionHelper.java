package io.sapl.test.dsl.utils;

import io.sapl.test.SaplTestException;

public class ReflectionHelper {
    public Object constructInstanceOfClass(final String className) {
        if (className == null || className.isEmpty()) {
            throw new SaplTestException("null or empty className");
        }
        try {
            final var clazz = Class.forName(className);

            final var constructor = clazz.getConstructor();
            return constructor.newInstance();

        } catch (Exception e) {
            throw new SaplTestException("Could not construct instance of '%s' class".formatted(className));
        }
    }
}
