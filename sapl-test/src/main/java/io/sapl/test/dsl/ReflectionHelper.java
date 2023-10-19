package io.sapl.test.dsl;

import io.sapl.test.SaplTestException;
import java.lang.reflect.InvocationTargetException;

public class ReflectionHelper {
    public Object constructInstanceOfClass(final String className) {
        try {
            final var clazz = Class.forName(className);

            final var constructor = clazz.getConstructor();
            return constructor.newInstance();

        } catch (ClassNotFoundException e) {
            throw new SaplTestException("Class %s could not be found".formatted(className));
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new SaplTestException(e);
        }
    }
}
