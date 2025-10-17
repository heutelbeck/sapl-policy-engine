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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFunctionLibraryTests {

    @ParameterizedTest
    @MethodSource("validJsonExamples")
    void jsonToValParsesValidJson(String json, String expectedKey, String expectedValue) {
        val result = JsonFunctionLibrary.jsonToVal(Val.of(json));
        assertThat(result.get().get(expectedKey).asText()).isEqualTo(expectedValue);
    }

    private static Stream<Arguments> validJsonExamples() {
        return Stream.of(Arguments.of("{\"hello\":\"world\"}", "hello", "world"),
                Arguments.of("{\"name\":\"Alice\"}", "name", "Alice"),
                Arguments.of("{\"key\":\"value with spaces\"}", "key", "value with spaces"));
    }

    @Test
    void jsonToValParsesNestedJson() throws JsonProcessingException {
        val json   = """
                {
                  "person": {
                    "name": "Alice",
                    "age": 30
                  }
                }
                """;
        val result = JsonFunctionLibrary.jsonToVal(Val.of(json));
        assertThat(result.get().get("person").get("name").asText()).isEqualTo("Alice");
        assertThat(result.get().get("person").get("age").asInt()).isEqualTo(30);
    }

    @Test
    void jsonToValParsesArray() {
        val json   = "[1,2,3,4,5]";
        val result = JsonFunctionLibrary.jsonToVal(Val.of(json));
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().size()).isEqualTo(5);
        assertThat(result.get().get(0).asInt()).isEqualTo(1);
    }

    @Test
    void jsonToValParsesEmptyObject() {
        val result = JsonFunctionLibrary.jsonToVal(Val.of("{}"));
        assertThat(result.get().isObject()).isTrue();
        assertThat(result.get().size()).isZero();
    }

    @Test
    void jsonToValParsesEmptyArray() {
        val result = JsonFunctionLibrary.jsonToVal(Val.of("[]"));
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "true", "false", "42", "\"text\"" })
    void jsonToValParsesPrimitives(String json) {
        val result = JsonFunctionLibrary.jsonToVal(Val.of(json));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void jsonToValHandlesUnicodeCharacters() {
        val json   = "{\"message\":\"Hello 🌸 World 世界\"}";
        val result = JsonFunctionLibrary.jsonToVal(Val.of(json));
        assertThat(result.get().get("message").asText()).isEqualTo("Hello 🌸 World 世界");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{invalid}", "{\"key\":}", "{\"key\"", "not json at all", "{\"key\": undefined}" })
    void jsonToValThrowsExceptionForInvalidJson(String invalidJson) {
        assertThatThrownBy(() -> JsonFunctionLibrary.jsonToVal(Val.of(invalidJson))).isInstanceOf(Exception.class);
    }

    @Test
    void valToJsonConvertsObjectToJsonString() throws JsonProcessingException {
        val object = Val.ofJson("{\"name\":\"Bob\",\"age\":25}");
        val result = JsonFunctionLibrary.valToJson(object);
        assertThat(result.getText()).contains("\"name\"");
        assertThat(result.getText()).contains("\"Bob\"");
        assertThat(result.getText()).contains("\"age\"");
        assertThat(result.getText()).contains("25");
    }

    @Test
    void valToJsonConvertsArrayToJsonString() throws JsonProcessingException {
        val array  = Val.ofJson("[1,2,3]");
        val result = JsonFunctionLibrary.valToJson(array);
        assertThat(result.getText()).isEqualTo("[1,2,3]");
    }

    @ParameterizedTest
    @MethodSource("primitiveValues")
    void valToJsonConvertsPrimitives(Val value, String expectedJson) {
        val result = JsonFunctionLibrary.valToJson(value);
        assertThat(result.getText()).isEqualTo(expectedJson);
    }

    private static Stream<Arguments> primitiveValues() {
        return Stream.of(Arguments.of(Val.TRUE, "true"), Arguments.of(Val.FALSE, "false"),
                Arguments.of(Val.NULL, "null"), Arguments.of(Val.of(42), "42"),
                Arguments.of(Val.of("test"), "\"test\""));
    }

    @Test
    void valToJsonReturnsErrorForErrorValue() {
        val error  = Val.error("Test error");
        val result = JsonFunctionLibrary.valToJson(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToJsonReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = JsonFunctionLibrary.valToJson(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void roundTripConversionPreservesData() throws JsonProcessingException {
        val original   = Val.ofJson("{\"name\":\"Charlie\",\"values\":[1,2,3],\"nested\":{\"key\":\"value\"}}");
        val jsonString = JsonFunctionLibrary.valToJson(original);
        val restored   = JsonFunctionLibrary.jsonToVal(jsonString);

        assertThat(restored.get().get("name").asText()).isEqualTo("Charlie");
        assertThat(restored.get().get("values").size()).isEqualTo(3);
        assertThat(restored.get().get("nested").get("key").asText()).isEqualTo("value");
    }

    @Test
    void valToJsonHandlesEmptyObject() {
        val result = JsonFunctionLibrary.valToJson(Val.ofEmptyObject());
        assertThat(result.getText()).isEqualTo("{}");
    }

    @Test
    void valToJsonHandlesEmptyArray() {
        val result = JsonFunctionLibrary.valToJson(Val.ofEmptyArray());
        assertThat(result.getText()).isEqualTo("[]");
    }

}
