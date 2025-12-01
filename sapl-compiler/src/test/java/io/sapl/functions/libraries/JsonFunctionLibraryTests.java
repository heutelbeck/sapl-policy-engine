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

import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFunctionLibraryTests {

    @Test
    void whenSimpleObject_thenParsesCorrectly() {
        val json   = """
                {"cultist": "Wilbur Whateley", "role": "ACOLYTE", "securityLevel": 3}
                """;
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).containsEntry("cultist", Value.of("Wilbur Whateley")).containsEntry("role", Value.of("ACOLYTE"))
                .containsEntry("securityLevel", Value.of(3));
    }

    @Test
    void whenNestedObject_thenParsesCorrectly() {
        val json   = """
                {"entity": {"name": "Azathoth", "title": "Daemon Sultan", "threatLevel": 9}}
                """;
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val entity = (ObjectValue) ((ObjectValue) result).get("entity");
        assertThat(entity).containsEntry("name", Value.of("Azathoth")).containsEntry("title", Value.of("Daemon Sultan"))
                .containsEntry("threatLevel", Value.of(9));
    }

    @Test
    void whenDeeplyNestedObject_thenParsesCorrectly() {
        val json   = """
                {"ritual": {"name": "Summoning", "location": {"site": "Miskatonic University", "dangerLevel": 5}}}
                """;
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val ritual   = (ObjectValue) ((ObjectValue) result).get("ritual");
        val location = (ObjectValue) ritual.get("location");
        assertThat(ritual).containsEntry("name", Value.of("Summoning"));
        assertThat(location).containsEntry("site", Value.of("Miskatonic University")).containsEntry("dangerLevel",
                Value.of(5));
    }

    @Test
    void whenArrays_thenParsesCorrectly() {
        val json   = """
                {"artifacts": ["Necronomicon", "Silver Key", "Shining Trapezohedron"], "threatLevels": [1, 2, 3, 4, 5]}
                """;
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj       = (ObjectValue) result;
        val artifacts = (ArrayValue) obj.get("artifacts");
        assertThat(artifacts).hasSize(3);
        assertThat(artifacts.getFirst()).isEqualTo(Value.of("Necronomicon"));

        val threatLevels = (ArrayValue) obj.get("threatLevels");
        assertThat(threatLevels).hasSize(5);
        assertThat(threatLevels.getFirst()).isEqualTo(Value.of(1));
    }

    @Test
    void whenArrayOfObjects_thenParsesCorrectly() {
        val json   = """
                {"investigators": [{"name": "Carter", "sanity": 85}, {"name": "Pickman", "sanity": 42}]}
                """;
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val investigators = (ArrayValue) ((ObjectValue) result).get("investigators");
        assertThat(investigators).hasSize(2);
        assertThat((ObjectValue) investigators.getFirst()).containsEntry("name", Value.of("Carter"));
        assertThat((ObjectValue) investigators.get(1)).containsEntry("name", Value.of("Pickman"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void whenBooleans_thenParsesCorrectly(String boolValue) {
        val json   = "{\"sealed\": " + boolValue + "}";
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val expected = "true".equals(boolValue) ? Value.TRUE : Value.FALSE;
        assertThat((ObjectValue) result).containsEntry("sealed", expected);
    }

    @Test
    void whenNullValue_thenParsesCorrectly() {
        val json   = "{\"value\": null}";
        val result = JsonFunctionLibrary.jsonToVal(Value.of(json));

        assertThat(result).isInstanceOf(ObjectValue.class);
        assertThat((ObjectValue) result).containsEntry("value", Value.NULL);
    }

    @Test
    void whenEmptyObject_thenReturnsEmptyObject() {
        val result = JsonFunctionLibrary.jsonToVal(Value.of("{}"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        assertThat((ObjectValue) result).isEmpty();
    }

    @Test
    void whenEmptyArray_thenReturnsEmptyArray() {
        val result = JsonFunctionLibrary.jsonToVal(Value.of("[]"));

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{invalid}", "[unclosed", "{\"key\": }", "not json at all" })
    void whenInvalidJson_thenReturnsError(String invalidJson) {
        val result = JsonFunctionLibrary.jsonToVal(Value.of(invalidJson));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).startsWith("Failed to parse JSON:");
    }

    @Test
    void whenObjectToJson_thenConvertsCorrectly() {
        val object = ObjectValue.builder().put("name", Value.of("Nyarlathotep"))
                .put("title", Value.of("Crawling Chaos")).put("threatLevel", Value.of(8)).build();

        val result = JsonFunctionLibrary.valToJson(object);

        assertThat(result).isInstanceOf(TextValue.class);
        val jsonText = ((TextValue) result).value();
        assertThat(jsonText).contains("\"name\"").contains("Nyarlathotep").contains("\"title\"")
                .contains("Crawling Chaos").contains("\"threatLevel\"");
    }

    @Test
    void whenNestedObjectToJson_thenConvertsCorrectly() {
        val location = ObjectValue.builder().put("site", Value.of("R'lyeh")).build();
        val ritual   = ObjectValue.builder().put("location", location).build();

        val result = JsonFunctionLibrary.valToJson(ritual);

        assertThat(result).isInstanceOf(TextValue.class);
        val jsonText = ((TextValue) result).value();
        assertThat(jsonText).contains("\"location\"").contains("\"site\"").contains("R'lyeh");
    }

    @Test
    void whenArrayToJson_thenConvertsCorrectly() {
        val deities = ArrayValue.builder().add(Value.of("Dagon")).add(Value.of("Hydra")).build();
        val object  = ObjectValue.builder().put("deities", deities).build();

        val result = JsonFunctionLibrary.valToJson(object);

        assertThat(result).isInstanceOf(TextValue.class);
        val jsonText = ((TextValue) result).value();
        assertThat(jsonText).contains("\"deities\"").contains("Dagon").contains("Hydra");
    }

    @Test
    void whenEmptyObjectToJson_thenConvertsToEmptyObject() {
        val result = JsonFunctionLibrary.valToJson(Value.EMPTY_OBJECT);

        assertThat(result).isEqualTo(Value.of("{}"));
    }

    @Test
    void whenRoundTrip_thenPreservesData() {
        val original   = """
                {"investigator": {"name": "Carter", "sanity": 77, "artifacts": ["Silver Key", "Lamp"]}}
                """;
        val parsed     = JsonFunctionLibrary.jsonToVal(Value.of(original));
        val serialized = JsonFunctionLibrary.valToJson(parsed);
        val reparsed   = JsonFunctionLibrary.jsonToVal((TextValue) serialized);

        assertThat(reparsed).isInstanceOf(ObjectValue.class);
        val investigator = (ObjectValue) ((ObjectValue) reparsed).get("investigator");
        assertThat(investigator).containsEntry("name", Value.of("Carter")).containsEntry("sanity", Value.of(77));
        assertThat((ArrayValue) investigator.get("artifacts")).hasSize(2);
    }
}
