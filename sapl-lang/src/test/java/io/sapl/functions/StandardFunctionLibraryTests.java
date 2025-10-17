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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StandardFunctionLibraryTests {

    @ParameterizedTest
    @MethodSource("emptyCollections")
    void lengthOfEmptyCollectionsIsZero(Val emptyCollection) {
        assertThat(StandardFunctionLibrary.length(emptyCollection), is(val(0)));
    }

    private static Stream<Arguments> emptyCollections() {
        return Stream.of(Arguments.of(Val.ofEmptyArray()), Arguments.of(Val.ofEmptyObject()), Arguments.of(Val.of("")));
    }

    @Test
    void lengthOfArrayWithElements() {
        val array = Val.JSON.arrayNode();
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        array.add(Val.JSON.booleanNode(false));
        assertThat(StandardFunctionLibrary.length(Val.of(array)), is(val(4)));
    }

    @Test
    void lengthOfObjectWithElements() {
        val object = Val.JSON.objectNode();
        object.set("key1", Val.JSON.booleanNode(false));
        object.set("key2", Val.JSON.booleanNode(false));
        object.set("key3", Val.JSON.booleanNode(false));
        object.set("key4", Val.JSON.booleanNode(false));
        object.set("key5", Val.JSON.booleanNode(false));
        assertThat(StandardFunctionLibrary.length(Val.of(object)), is(val(5)));
    }

    @ParameterizedTest
    @MethodSource("textAndLengths")
    void lengthOfText(String text, int expectedLength) {
        assertThat(StandardFunctionLibrary.length(Val.of(text)), is(val(expectedLength)));
    }

    private static Stream<Arguments> textAndLengths() {
        return Stream.of(Arguments.of("ABC", 3), Arguments.of("Hello, World!", 13), Arguments.of("", 0),
                Arguments.of("🌸", 2), Arguments.of("多言語", 3));
    }

    @ParameterizedTest
    @MethodSource("valuesAndStringRepresentations")
    void asStringConvertsValuesToStrings(Val value, String expected) {
        assertThat(StandardFunctionLibrary.asString(value), is(val(expected)));
    }

    private static Stream<Arguments> valuesAndStringRepresentations() throws JsonProcessingException {
        return Stream.of(Arguments.of(Val.TRUE, "true"), Arguments.of(Val.FALSE, "false"),
                Arguments.of(Val.NULL, "null"), Arguments.of(Val.of("ABC"), "ABC"),
                Arguments.of(Val.of(1.23e-1D), "0.123"), Arguments.of(Val.ofJson("[1,2,3]"), "[1,2,3]"),
                Arguments.of(Val.of(42), "42"), Arguments.of(Val.of(-17), "-17"));
    }

    @Test
    void onErrorMapReturnsOriginalValueWhenNoError() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.of("ORIGINAL"), Val.of("FALLBACK")), is(val("ORIGINAL")));
    }

    @Test
    void onErrorMapReturnsFallbackValueWhenError() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.error((String) null), Val.of("FALLBACK")),
                is(val("FALLBACK")));
    }

    @Test
    void onErrorMapReturnsFallbackForErrorWithMessage() {
        assertThat(StandardFunctionLibrary.onErrorMap(Val.error("Something went wrong"), Val.of(999)), is(val(999)));
    }

}
