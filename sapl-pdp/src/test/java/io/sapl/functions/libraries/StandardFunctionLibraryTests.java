/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.libraries;

import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("StandardFunctionLibrary")
class StandardFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyCollections")
    void whenLengthOfEmptyCollectionsThenIsZero(Value emptyCollection) {
        assertThat(StandardFunctionLibrary.length(emptyCollection)).isEqualTo(Value.of(0));
    }

    private static Stream<Arguments> emptyCollections() {
        return Stream.of(arguments(Value.EMPTY_ARRAY), arguments(Value.EMPTY_OBJECT), arguments(Value.EMPTY_TEXT));
    }

    @Test
    void whenLengthOfArrayWithElementsThenReturnsCorrectCount() {
        val array = Value.ofArray(Value.FALSE, Value.FALSE, Value.FALSE, Value.FALSE);
        assertThat(StandardFunctionLibrary.length(array)).isEqualTo(Value.of(4));
    }

    @Test
    void whenLengthOfObjectWithElementsThenReturnsCorrectCount() {
        val map = new HashMap<String, Value>();
        map.put("key1", Value.FALSE);
        map.put("key2", Value.FALSE);
        map.put("key3", Value.FALSE);
        map.put("key4", Value.FALSE);
        map.put("key5", Value.FALSE);
        val object = Value.ofObject(map);
        assertThat(StandardFunctionLibrary.length(object)).isEqualTo(Value.of(5));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("textAndLengths")
    void whenLengthOfTextThenReturnsExpectedLength(String text, int expectedLength) {
        assertThat(StandardFunctionLibrary.length(Value.of(text))).isEqualTo(Value.of(expectedLength));
    }

    private static Stream<Arguments> textAndLengths() {
        return Stream.of(arguments("ABC", 3), arguments("Hello, World!", 13), arguments("", 0), arguments("🌸", 2),
                arguments("多言語", 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesAndStringRepresentations")
    void whenAsStringThenConvertsValuesToStrings(Value value, String expected) {
        assertThat(StandardFunctionLibrary.asString(value)).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> valuesAndStringRepresentations() {
        return Stream.of(arguments(Value.TRUE, "true"), arguments(Value.FALSE, "false"), arguments(Value.NULL, "null"),
                arguments(Value.of("ABC"), "ABC"), arguments(Value.of(1.23e-1D), "0.123"),
                arguments(Value.of(42), "42"), arguments(Value.of(-17), "-17"));
    }

    @Test
    void whenOnErrorMapWithNoErrorThenReturnsOriginalValue() {
        assertThat(StandardFunctionLibrary.onErrorMap(Value.of("ORIGINAL"), Value.of("FALLBACK")))
                .isEqualTo(Value.of("ORIGINAL"));
    }

    @Test
    void whenOnErrorMapWithErrorThenReturnsFallbackValue() {
        assertThat(StandardFunctionLibrary.onErrorMap(Value.error(""), Value.of("FALLBACK")))
                .isEqualTo(Value.of("FALLBACK"));
    }

    @Test
    void whenOnErrorMapWithErrorMessageThenReturnsFallback() {
        assertThat(StandardFunctionLibrary.onErrorMap(Value.error("Something went wrong"), Value.of(999)))
                .isEqualTo(Value.of(999));
    }

}
