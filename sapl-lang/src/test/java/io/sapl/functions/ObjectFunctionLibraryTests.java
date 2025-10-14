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

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

class ObjectFunctionLibraryTests {

    private static Val json(String json) throws JsonProcessingException {
        return Val.ofJson(json);
    }

    @Test
    void when_keysOnEmptyObject_then_returnsEmptyArray() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.keys(json("{}"));

        assertThatVal(actual).hasValue().isArray().isEmpty();
    }

    @Test
    void when_keysOnObject_then_returnsAllKeys() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.keys(json("{\"name\": \"Alice\", \"age\": 30, \"city\": \"Berlin\"}"));

        assertThatVal(actual).hasValue().isArray();
        val arrayNode = actual.getArrayNode();
        assertThat(arrayNode).hasSize(3);
        assertThat(arrayNode.get(0).asText()).isEqualTo("name");
        assertThat(arrayNode.get(1).asText()).isEqualTo("age");
        assertThat(arrayNode.get(2).asText()).isEqualTo("city");
    }

    @Test
    void when_keysOnNestedObject_then_returnsTopLevelKeysOnly() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.keys(json("{\"outer\": {\"inner\": 1}, \"value\": 42}"));

        assertThatVal(actual).hasValue().isArray();
        val arrayNode = actual.getArrayNode();
        assertThat(arrayNode).hasSize(2);
        assertThat(arrayNode.get(0).asText()).isEqualTo("outer");
        assertThat(arrayNode.get(1).asText()).isEqualTo("value");
    }

    @Test
    void when_valuesOnEmptyObject_then_returnsEmptyArray() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.values(json("{}"));

        assertThatVal(actual).hasValue().isArray().isEmpty();
    }

    @Test
    void when_valuesOnObject_then_returnsAllValues() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.values(json("{\"name\": \"Alice\", \"age\": 30, \"active\": true}"));

        assertThatVal(actual).hasValue().isArray();
        val arrayNode = actual.getArrayNode();
        assertThat(arrayNode).hasSize(3);
        assertThat(arrayNode.get(0).asText()).isEqualTo("Alice");
        assertThat(arrayNode.get(1).asInt()).isEqualTo(30);
        assertThat(arrayNode.get(2).asBoolean()).isTrue();
    }

    @Test
    void when_valuesWithNullValue_then_includesNull() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.values(json("{\"a\": null, \"b\": 1}"));

        assertThatVal(actual).hasValue().isArray();
        val arrayNode = actual.getArrayNode();
        assertThat(arrayNode).hasSize(2);
        assertThat(arrayNode.get(0).isNull()).isTrue();
        assertThat(arrayNode.get(1).asInt()).isEqualTo(1);
    }

    @Test
    void when_valuesOnNestedObject_then_includesNestedStructures() throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.values(json("{\"nested\": {\"x\": 1}, \"array\": [1, 2]}"));

        assertThatVal(actual).hasValue().isArray();
        val arrayNode = actual.getArrayNode();
        assertThat(arrayNode).hasSize(2);
        assertThat(arrayNode.get(0).isObject()).isTrue();
        assertThat(arrayNode.get(1).isArray()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("provideSizeTestCases")
    void when_size_then_returnsCorrectCount(String objectJson, int expectedSize) throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.size(json(objectJson));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expectedSize);
    }

    private static Stream<Arguments> provideSizeTestCases() {
        return Stream.of(Arguments.of("{}", 0), Arguments.of("{\"a\": 1}", 1),
                Arguments.of("{\"a\": 1, \"b\": 2, \"c\": 3}", 3),
                Arguments.of("{\"nested\": {\"x\": 1, \"y\": 2}, \"value\": 42}", 2));
    }

    @ParameterizedTest
    @MethodSource("provideHasKeyTestCases")
    void when_hasKey_then_returnsCorrectResult(String objectJson, String key, boolean expected)
            throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.hasKey(json(objectJson), Val.of(key));

        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideHasKeyTestCases() {
        return Stream.of(Arguments.of("{\"name\": \"Alice\", \"age\": 30}", "name", true),
                Arguments.of("{\"name\": \"Alice\", \"age\": 30}", "age", true),
                Arguments.of("{\"name\": \"Alice\", \"age\": 30}", "email", false), Arguments.of("{}", "anyKey", false),
                Arguments.of("{\"active\": null}", "active", true),
                Arguments.of("{\"nested\": {\"inner\": 1}}", "nested", true),
                Arguments.of("{\"nested\": {\"inner\": 1}}", "inner", false),
                Arguments.of("{\"0\": \"zero\", \"1\": \"one\"}", "0", true),
                Arguments.of("{\"key with spaces\": 42}", "key with spaces", true));
    }

    @Test
    void when_hasKeyWithNullValue_then_returnsTrue() throws JsonProcessingException {
        val object = json("{\"nullValue\": null}");
        val actual = ObjectFunctionLibrary.hasKey(object, Val.of("nullValue"));

        assertThatVal(actual).isEqualTo(Val.TRUE);
    }

    @Test
    void when_hasKeyOnEmptyObject_then_returnsFalse() throws JsonProcessingException {
        val object = json("{}");
        val actual = ObjectFunctionLibrary.hasKey(object, Val.of("anyKey"));

        assertThatVal(actual).isEqualTo(Val.FALSE);
    }

    @ParameterizedTest
    @MethodSource("provideIsEmptyTestCases")
    void when_isEmpty_then_returnsCorrectResult(String objectJson, boolean expected) throws JsonProcessingException {
        val actual = ObjectFunctionLibrary.isEmpty(json(objectJson));

        assertThatVal(actual).isEqualTo(Val.of(expected));
    }

    private static Stream<Arguments> provideIsEmptyTestCases() {
        return Stream.of(Arguments.of("{}", true), Arguments.of("{\"a\": 1}", false),
                Arguments.of("{\"name\": \"Alice\", \"age\": 30}", false), Arguments.of("{\"nested\": {}}", false),
                Arguments.of("{\"array\": []}", false), Arguments.of("{\"null\": null}", false));
    }
}
