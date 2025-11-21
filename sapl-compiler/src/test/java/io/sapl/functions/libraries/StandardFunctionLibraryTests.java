/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.HashMap;
import java.util.stream.Stream;

class StandardFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertDoesNotThrow(() -> functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class));
    }

    @ParameterizedTest
    @MethodSource("emptyCollections")
    void lengthOfEmptyCollectionsIsZero(Value emptyCollection) {
        assertThat(StandardFunctionLibrary.length(emptyCollection)).isEqualTo(Value.of(0));
    }

    private static Stream<Arguments> emptyCollections() {
        return Stream.of(Arguments.of(Value.EMPTY_ARRAY), Arguments.of(Value.EMPTY_OBJECT),
                Arguments.of(Value.EMPTY_TEXT));
    }

    @Test
    void lengthOfArrayWithElements() {
        val array = Value.ofArray(Value.FALSE, Value.FALSE, Value.FALSE, Value.FALSE);
        assertThat(StandardFunctionLibrary.length(array)).isEqualTo(Value.of(4));
    }

    @Test
    void lengthOfObjectWithElements() {
        val map = new HashMap<String, Value>();
        map.put("key1", Value.FALSE);
        map.put("key2", Value.FALSE);
        map.put("key3", Value.FALSE);
        map.put("key4", Value.FALSE);
        map.put("key5", Value.FALSE);
        val object = Value.ofObject(map);
        assertThat(StandardFunctionLibrary.length(object)).isEqualTo(Value.of(5));
    }

    @ParameterizedTest
    @MethodSource("textAndLengths")
    void lengthOfText(String text, int expectedLength) {
        assertThat(StandardFunctionLibrary.length(Value.of(text))).isEqualTo(Value.of(expectedLength));
    }

    private static Stream<Arguments> textAndLengths() {
        return Stream.of(Arguments.of("ABC", 3), Arguments.of("Hello, World!", 13), Arguments.of("", 0),
                Arguments.of("🌸", 2), Arguments.of("多言語", 3));
    }

    @ParameterizedTest
    @MethodSource("valuesAndStringRepresentations")
    void asStringConvertsValuesToStrings(Value value, String expected) {
        assertThat(StandardFunctionLibrary.asString(value)).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> valuesAndStringRepresentations() {
        return Stream.of(Arguments.of(Value.TRUE, "true"), Arguments.of(Value.FALSE, "false"),
                Arguments.of(Value.NULL, "null"), Arguments.of(Value.of("ABC"), "ABC"),
                Arguments.of(Value.of(1.23e-1D), "0.123"),
                // Arguments.of(Value.ofJson("[1,2,3]"), "[1,2,3]"),
                Arguments.of(Value.of(42), "42"), Arguments.of(Value.of(-17), "-17"));
    }

    @Test
    void onErrorMapReturnsOriginalValueWhenNoError() {
        assertThat(StandardFunctionLibrary.onErrorMap(Value.of("ORIGINAL"), Value.of("FALLBACK")))
                .isEqualTo(Value.of("ORIGINAL"));
    }

    /*
     * @Test
     * void onErrorMapReturnsFallbackValueWhenError() {
     * assertThat(StandardFunctionLibrary.onErrorMap(Value.error((String) null),
     * Value.of("FALLBACK")))
     * .isEqualTo(Value.of("FALLBACK"));
     * }
     */

    @Test
    void onErrorMapReturnsFallbackForErrorWithMessage() {
        assertThat(StandardFunctionLibrary.onErrorMap(Value.error("Something went wrong"), Value.of(999)))
                .isEqualTo(Value.of(999));
    }

}
