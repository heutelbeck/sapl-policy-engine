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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FunctionLibraryInterpreterTest {
    private FunctionLibraryInterpreter functionLibraryInterpreter;

    @BeforeEach
    void setUp() {
        functionLibraryInterpreter = new FunctionLibraryInterpreter();
    }

    @Test
    void getFunctionLibrary_handlesNullFunctionLibrary_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> functionLibraryInterpreter.getFunctionLibrary(null));

        assertEquals("FunctionLibrary is null", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("functionLibraryArgumentProvider")
    void getFunctionLibrary_mapsFunctionLibraryToExpectedLibraryInstance_returnsLibraryInstance(
            final FunctionLibrary functionLibrary, final Class<?> libraryClass) {
        final var result = functionLibraryInterpreter.getFunctionLibrary(functionLibrary);

        assertEquals(libraryClass, result);
    }

    private static Stream<Arguments> functionLibraryArgumentProvider() {
        return Stream.of(Arguments.of(FunctionLibrary.FILTER, FilterFunctionLibrary.class),
                Arguments.of(FunctionLibrary.LOGGING, LoggingFunctionLibrary.class),
                Arguments.of(FunctionLibrary.STANDARD, StandardFunctionLibrary.class),
                Arguments.of(FunctionLibrary.TEMPORAL, TemporalFunctionLibrary.class));
    }
}
