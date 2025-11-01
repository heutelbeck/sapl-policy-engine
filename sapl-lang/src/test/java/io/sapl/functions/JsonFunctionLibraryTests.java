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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFunctionLibraryTests {

    @ParameterizedTest
    @MethodSource("validJsonExamples")
    void jsonToValParsesValidJson(String json, String expectedKey, String expectedValue) {
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get(expectedKey).asText()).isEqualTo(expectedValue);
    }

    static Stream<Arguments> validJsonExamples() {
        return Stream.of(Arguments.of("{\"commander\":\"Perry Rhodan\"}", "commander", "Perry Rhodan"),
                Arguments.of("{\"ship\":\"SOL\"}", "ship", "SOL"),
                Arguments.of("{\"species\":\"Arkonide from the three-sun system\"}", "species",
                        "Arkonide from the three-sun system"));
    }

    @Test
    void jsonToValParsesNestedJson() {
        var json   = """
                {
                  "immortal": {
                    "name": "Atlan",
                    "age": 10000
                  }
                }
                """;
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("immortal").get("name").asText()).isEqualTo("Atlan");
        assertThat(result.get().get("immortal").get("age").asInt()).isEqualTo(10000);
    }

    @Test
    void jsonToValParsesArray() {
        var json   = "[1,2,3,5,8]";
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get()).hasSize(5);
        assertThat(result.get().get(0).asInt()).isEqualTo(1);
    }

    @Test
    void jsonToValParsesEmptyObject() {
        var result = JsonFunctionLibrary.jsonToVal(Val.of("{}"));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isObject()).isTrue();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void jsonToValParsesEmptyArray() {
        var result = JsonFunctionLibrary.jsonToVal(Val.of("[]"));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "true", "false", "2326", "3.14159", "\"hyperspace\"" })
    void jsonToValParsesPrimitives(String json) {
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void jsonToValHandlesUnicodeCharacters() {
        var json   = "{\"greeting\":\"Welcome to Arkon 🚀🌟\"}";
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("greeting").asText()).isEqualTo("Welcome to Arkon 🚀🌟");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{invalid}", "{\"key\":}", "{\"key\"", "not json at all", "{\"key\": undefined}",
            "{unquoted: value}", "[1, 2, 3,]" })
    void jsonToValReturnsErrorForInvalidJson(String invalidJson) {
        var result = JsonFunctionLibrary.jsonToVal(Val.of(invalidJson));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse JSON");
    }

    @Test
    void valToJsonConvertsObjectToJsonString() throws JsonProcessingException {
        var object = Val.ofJson("{\"name\":\"Gucky\",\"species\":\"Mousebeaver\"}");
        var result = JsonFunctionLibrary.valToJson(object);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("\"name\"").contains("\"Gucky\"").contains("\"species\"")
                .contains("\"Mousebeaver\"");
    }

    @Test
    void valToJsonConvertsArrayToJsonString() throws JsonProcessingException {
        var array  = Val.ofJson("[1,2,3]");
        var result = JsonFunctionLibrary.valToJson(array);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo("[1,2,3]");
    }

    @ParameterizedTest
    @MethodSource("primitiveValues")
    void valToJsonConvertsPrimitives(Val value, String expectedJson) {
        var result = JsonFunctionLibrary.valToJson(value);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo(expectedJson);
    }

    static Stream<Arguments> primitiveValues() {
        return Stream.of(Arguments.of(Val.TRUE, "true"), Arguments.of(Val.FALSE, "false"),
                Arguments.of(Val.NULL, "null"), Arguments.of(Val.of(2500), "2500"),
                Arguments.of(Val.of("NATHAN"), "\"NATHAN\""));
    }

    @Test
    void valToJsonReturnsErrorForErrorValue() {
        var error  = Val.error("Hyperspace transition failed");
        var result = JsonFunctionLibrary.valToJson(error);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToJsonReturnsUndefinedForUndefinedValue() {
        var result = JsonFunctionLibrary.valToJson(Val.UNDEFINED);

        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void roundTripConversionPreservesData() throws JsonProcessingException {
        var original   = Val.ofJson(
                "{\"pilot\":\"Reginald Bull\",\"coordinates\":[42,13,7],\"destination\":{\"system\":\"Arkon\"}}");
        var jsonString = JsonFunctionLibrary.valToJson(original);
        var restored   = JsonFunctionLibrary.jsonToVal(jsonString);

        assertThat(restored.isDefined()).isTrue();
        assertThat(restored.get().get("pilot").asText()).isEqualTo("Reginald Bull");
        assertThat(restored.get().get("coordinates")).hasSize(3);
        assertThat(restored.get().get("destination").get("system").asText()).isEqualTo("Arkon");
    }

    @Test
    void valToJsonHandlesEmptyObject() {
        var result = JsonFunctionLibrary.valToJson(Val.ofEmptyObject());

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo("{}");
    }

    @Test
    void valToJsonHandlesEmptyArray() {
        var result = JsonFunctionLibrary.valToJson(Val.ofEmptyArray());

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo("[]");
    }

    @Test
    void jsonToValWithComplexNestedStructure() {
        var json   = """
                {
                  "vessel": "SOL",
                  "commander": {
                    "name": "Perry Rhodan",
                    "cellActivator": true
                  },
                  "crew": [
                    {"name": "Atlan", "role": "Science Officer"},
                    {"name": "Gucky", "role": "Teleporter"}
                  ],
                  "diameter": 2500,
                  "hyperdrive": true
                }
                """;
        var result = JsonFunctionLibrary.jsonToVal(Val.of(json));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("vessel").asText()).isEqualTo("SOL");
        assertThat(result.get().get("commander").get("name").asText()).isEqualTo("Perry Rhodan");
        assertThat(result.get().get("commander").get("cellActivator").asBoolean()).isTrue();
        assertThat(result.get().get("crew")).hasSize(2);
        assertThat(result.get().get("crew").get(1).get("name").asText()).isEqualTo("Gucky");
        assertThat(result.get().get("diameter").asInt()).isEqualTo(2500);
        assertThat(result.get().get("hyperdrive").asBoolean()).isTrue();
    }

}
