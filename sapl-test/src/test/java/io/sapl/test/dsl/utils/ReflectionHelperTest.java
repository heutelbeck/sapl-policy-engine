package io.sapl.test.dsl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sapl.test.SaplTestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReflectionHelperTest {
    private ReflectionHelper reflectionHelper;

    @BeforeEach
    void setUp() {
        reflectionHelper = new ReflectionHelper();
    }

    @Test
    void constructInstanceOfClass_withNullClassName_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> reflectionHelper.constructInstanceOfClass(null));

        assertEquals("null or empty className", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withEmptyClassName_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> reflectionHelper.constructInstanceOfClass(""));

        assertEquals("null or empty className", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withFaultyClassName_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> reflectionHelper.constructInstanceOfClass("io.foo.bar.shizzle.Class"));

        assertEquals("Could not construct instance of 'io.foo.bar.shizzle.Class' class", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withClassNameWithoutPublicNoArgsConstructor_throwsSaplTestException() {
        final var className = this.getClass().getName();

        final var exception = assertThrows(SaplTestException.class, () -> reflectionHelper.constructInstanceOfClass(className));

        assertEquals("Could not construct instance of 'io.sapl.test.dsl.utils.ReflectionHelperTest' class", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withValidClassName_returnsInstanceOfClass() {
        final var result = reflectionHelper.constructInstanceOfClass(ReflectionHelper.class.getName());

        assertInstanceOf(ReflectionHelper.class, result);
    }
}