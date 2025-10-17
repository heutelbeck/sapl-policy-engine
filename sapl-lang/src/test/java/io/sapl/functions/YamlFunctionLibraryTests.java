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
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class YamlFunctionLibraryTests {

    @Test
    void yamlToValParsesSimpleMapping() {
        val yaml   = """
                name: Poppy
                color: RED
                petals: 9
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("name").asText()).isEqualTo("Poppy");
        assertThat(result.get().get("color").asText()).isEqualTo("RED");
        assertThat(result.get().get("petals").asInt()).isEqualTo(9);
    }

    @Test
    void yamlToValParsesNestedMappings() {
        val yaml   = """
                person:
                  name: Alice
                  address:
                    city: Wonderland
                    zip: 12345
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("person").get("name").asText()).isEqualTo("Alice");
        assertThat(result.get().get("person").get("address").get("city").asText()).isEqualTo("Wonderland");
        assertThat(result.get().get("person").get("address").get("zip").asInt()).isEqualTo(12345);
    }

    @Test
    void yamlToValParsesSequences() {
        val yaml   = """
                fruits:
                  - apple
                  - banana
                  - cherry
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val fruits = result.get().get("fruits");
        assertThat(fruits.isArray()).isTrue();
        assertThat(fruits.size()).isEqualTo(3);
        assertThat(fruits.get(0).asText()).isEqualTo("apple");
        assertThat(fruits.get(1).asText()).isEqualTo("banana");
        assertThat(fruits.get(2).asText()).isEqualTo("cherry");
    }

    @Test
    void yamlToValParsesInlineSequence() {
        val yaml    = "numbers: [1, 2, 3, 4, 5]";
        val result  = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val numbers = result.get().get("numbers");
        assertThat(numbers.isArray()).isTrue();
        assertThat(numbers.size()).isEqualTo(5);
    }

    @Test
    void yamlToValParsesInlineMapping() {
        val yaml   = "person: {name: Bob, age: 30}";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("person").get("name").asText()).isEqualTo("Bob");
        assertThat(result.get().get("person").get("age").asInt()).isEqualTo(30);
    }

    @Test
    void yamlToValParsesMultilineString() {
        val yaml        = """
                description: |
                  This is a multi-line
                  string in YAML
                  format.
                """;
        val result      = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val description = result.get().get("description").asText();
        assertThat(description).contains("multi-line");
        assertThat(description).contains("YAML");
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false", "yes", "no", "on", "off" })
    void yamlToValParsesBooleans(String booleanValue) {
        val yaml   = "flag: " + booleanValue;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("flag").isBoolean()).isTrue();
    }

    @Test
    void yamlToValParsesNull() {
        val yaml   = "value: null";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("value").isNull()).isTrue();
    }

    @Test
    void yamlToValParsesEmptyDocument() {
        val yaml   = "";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void yamlToValParsesEmptyMapping() {
        val yaml   = "{}";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().isObject()).isTrue();
        assertThat(result.get().size()).isZero();
    }

    @Test
    void yamlToValParsesEmptySequence() {
        val yaml   = "items: []";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("items").isArray()).isTrue();
        assertThat(result.get().get("items").size()).isZero();
    }

    @Test
    void yamlToValHandlesQuotedStrings() {
        val yaml   = """
                message: "Hello, World!"
                path: 'C:\\Users\\test'
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("message").asText()).isEqualTo("Hello, World!");
        assertThat(result.get().get("path").asText()).isEqualTo("C:\\Users\\test");
    }

    @Test
    void yamlToValHandlesNumbers() {
        val yaml   = """
                integer: 42
                decimal: 2.5
                negative: -17
                scientific: 1.23e-4
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("integer").asInt()).isEqualTo(42);
        assertThat(result.get().get("decimal").asDouble()).isEqualTo(2.5);
        assertThat(result.get().get("negative").asInt()).isEqualTo(-17);
    }

    @Test
    void yamlToValHandlesUnicodeCharacters() {
        val yaml   = "message: Hello 世界 🌸";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("message").asText()).isEqualTo("Hello 世界 🌸");
    }

    @Test
    void yamlToValParsesComplexStructure() {
        val yaml   = """
                services:
                  database:
                    image: postgres:latest
                    ports:
                      - 5432:5432
                    environment:
                      POSTGRES_PASSWORD: secret
                  web:
                    image: nginx:latest
                    ports:
                      - 80:80
                      - 443:443
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("services").get("database").get("image").asText()).isEqualTo("postgres:latest");
        assertThat(result.get().get("services").get("web").get("ports").size()).isEqualTo(2);
    }

    @Test
    void valToYamlConvertsObjectToYamlString() throws JsonProcessingException {
        val object = Val.ofJson("{\"name\":\"Rose\",\"color\":\"PINK\",\"petals\":5}");
        val result = YamlFunctionLibrary.valToYaml(object);
        assertThat(result.getText()).contains("name:");
        assertThat(result.getText()).contains("Rose");
        assertThat(result.getText()).contains("color:");
        assertThat(result.getText()).contains("PINK");
    }

    @Test
    void valToYamlConvertsArrayToYamlString() throws JsonProcessingException {
        val array  = Val.ofJson("[\"apple\",\"banana\",\"cherry\"]");
        val result = YamlFunctionLibrary.valToYaml(array);
        assertThat(result.getText()).contains("apple");
        assertThat(result.getText()).contains("banana");
        assertThat(result.getText()).contains("cherry");
    }

    @Test
    void valToYamlHandlesNestedStructures() {
        val parent = Val.JSON.objectNode();
        val child  = Val.JSON.objectNode();
        child.put("key", "value");
        parent.set("child", child);

        val result = YamlFunctionLibrary.valToYaml(Val.of(parent));
        assertThat(result.getText()).contains("child:");
        assertThat(result.getText()).contains("key:");
        assertThat(result.getText()).contains("value");
    }

    @Test
    void valToYamlReturnsErrorForErrorValue() {
        val error  = Val.error("Test error");
        val result = YamlFunctionLibrary.valToYaml(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToYamlReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = YamlFunctionLibrary.valToYaml(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void roundTripConversionPreservesData() {
        val original   = """
                name: Charlie
                age: 35
                hobbies:
                  - reading
                  - coding
                """;
        val parsed     = YamlFunctionLibrary.yamlToVal(Val.of(original));
        val serialized = YamlFunctionLibrary.valToYaml(parsed);
        val reparsed   = YamlFunctionLibrary.yamlToVal(serialized);

        assertThat(reparsed.get().get("name").asText()).isEqualTo("Charlie");
        assertThat(reparsed.get().get("age").asInt()).isEqualTo(35);
        assertThat(reparsed.get().get("hobbies").size()).isEqualTo(2);
    }

    @Test
    void valToYamlHandlesEmptyObject() {
        val result = YamlFunctionLibrary.valToYaml(Val.ofEmptyObject());
        assertThat(result.getText()).contains("{}");
    }

    @Test
    void valToYamlHandlesEmptyArray() {
        val result = YamlFunctionLibrary.valToYaml(Val.ofEmptyArray());
        assertThat(result.getText()).contains("[]");
    }

    @Test
    void valToYamlHandlesPrimitiveValues() {
        assertThat(YamlFunctionLibrary.valToYaml(Val.TRUE).getText()).contains("true");
        assertThat(YamlFunctionLibrary.valToYaml(Val.FALSE).getText()).contains("false");
        assertThat(YamlFunctionLibrary.valToYaml(Val.NULL).getText()).contains("null");
        assertThat(YamlFunctionLibrary.valToYaml(Val.of(42)).getText()).contains("42");
    }

}
