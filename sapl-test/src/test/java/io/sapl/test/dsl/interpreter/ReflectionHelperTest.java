/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.test.dsl.interpreter;

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
        final var exception = assertThrows(SaplTestException.class,
                () -> reflectionHelper.constructInstanceOfClass(null));

        assertEquals("null or empty className", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withEmptyClassName_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> reflectionHelper.constructInstanceOfClass(""));

        assertEquals("null or empty className", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withFaultyClassName_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> reflectionHelper.constructInstanceOfClass("io.foo.bar.shizzle.Class"));

        assertEquals("Could not construct instance of 'io.foo.bar.shizzle.Class' class", exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withClassNameWithoutPublicNoArgsConstructor_throwsSaplTestException() {
        final var className = this.getClass().getName();

        final var exception = assertThrows(SaplTestException.class,
                () -> reflectionHelper.constructInstanceOfClass(className));

        assertEquals("Could not construct instance of 'io.sapl.test.dsl.interpreter.ReflectionHelperTest' class",
                exception.getMessage());
    }

    @Test
    void constructInstanceOfClass_withValidClassName_returnsInstanceOfClass() {
        final var result = reflectionHelper.constructInstanceOfClass(String.class.getName());

        assertInstanceOf(String.class, result);
    }
}
