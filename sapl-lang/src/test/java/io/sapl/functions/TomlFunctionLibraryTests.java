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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TomlFunctionLibraryTests {

    @Test
    void tomlToValParsesSimpleKeyValuePairs() {
        val toml   = """
                name = "Poppy"
                color = "RED"
                petals = 9
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("name").asText()).isEqualTo("Poppy");
        assertThat(result.get().get("color").asText()).isEqualTo("RED");
        assertThat(result.get().get("petals").asInt()).isEqualTo(9);
    }

    @Test
    void tomlToValParsesTable() {
        val toml   = """
                [flower]
                name = "Rose"
                color = "PINK"
                petals = 5
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("flower").get("name").asText()).isEqualTo("Rose");
        assertThat(result.get().get("flower").get("color").asText()).isEqualTo("PINK");
        assertThat(result.get().get("flower").get("petals").asInt()).isEqualTo(5);
    }

    @Test
    void tomlToValParsesNestedTables() {
        val toml   = """
                [database]
                server = "localhost"

                [database.connection]
                max_retries = 5
                timeout = 30
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("database").get("server").asText()).isEqualTo("localhost");
        assertThat(result.get().get("database").get("connection").get("max_retries").asInt()).isEqualTo(5);
        assertThat(result.get().get("database").get("connection").get("timeout").asInt()).isEqualTo(30);
    }

    @Test
    void tomlToValParsesArrays() {
        val toml   = """
                fruits = ["apple", "banana", "cherry"]
                numbers = [1, 2, 3, 4, 5]
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));

        val fruits = result.get().get("fruits");
        assertThat(fruits.isArray()).isTrue();
        assertThat(fruits.size()).isEqualTo(3);
        assertThat(fruits.get(0).asText()).isEqualTo("apple");

        val numbers = result.get().get("numbers");
        assertThat(numbers.size()).isEqualTo(5);
        assertThat(numbers.get(0).asInt()).isEqualTo(1);
    }

    @Test
    void tomlToValParsesArrayOfTables() {
        val toml     = """
                [[products]]
                name = "Laptop"
                price = 999

                [[products]]
                name = "Mouse"
                price = 25
                """;
        val result   = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        val products = result.get().get("products");

        assertThat(products.isArray()).isTrue();
        assertThat(products.size()).isEqualTo(2);
        assertThat(products.get(0).get("name").asText()).isEqualTo("Laptop");
        assertThat(products.get(1).get("name").asText()).isEqualTo("Mouse");
    }

    @Test
    void tomlToValParsesInlineTable() {
        val toml   = """
                point = { x = 10, y = 20, z = 30 }
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("point").get("x").asInt()).isEqualTo(10);
        assertThat(result.get().get("point").get("y").asInt()).isEqualTo(20);
        assertThat(result.get().get("point").get("z").asInt()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void tomlToValParsesBooleans(String boolValue) {
        val toml   = "flag = " + boolValue;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("flag").isBoolean()).isTrue();
    }

    @Test
    void tomlToValParsesIntegers() {
        val toml   = """
                positive = 42
                negative = -17
                zero = 0
                large = 1000000
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("positive").asInt()).isEqualTo(42);
        assertThat(result.get().get("negative").asInt()).isEqualTo(-17);
        assertThat(result.get().get("zero").asInt()).isZero();
    }

    @Test
    void tomlToValParsesFloats() {
        val toml   = """
                pi = 2.14
                negative = -0.5
                scientific = 1.23e-4
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("pi").asDouble()).isEqualTo(2.14);
        assertThat(result.get().get("negative").asDouble()).isEqualTo(-0.5);
    }

    @Test
    void tomlToValParsesStrings() {
        val toml   = """
                basic = "Hello, World!"
                multiline = \"""
                Line 1
                Line 2
                Line 3\"""
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("basic").asText()).isEqualTo("Hello, World!");
        assertThat(result.get().get("multiline").asText()).contains("Line 1");
        assertThat(result.get().get("multiline").asText()).contains("Line 2");
    }

    @Test
    void tomlToValHandlesQuotedKeys() {
        val toml   = """
                "key with spaces" = "value"
                "special.key" = "another value"
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("key with spaces").asText()).isEqualTo("value");
        assertThat(result.get().get("special.key").asText()).isEqualTo("another value");
    }

    @Test
    void tomlToValHandlesComments() {
        val toml   = """
                # This is a comment
                name = "Alice" # inline comment
                # Another comment
                age = 30
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("name").asText()).isEqualTo("Alice");
        assertThat(result.get().get("age").asInt()).isEqualTo(30);
    }

    @Test
    void tomlToValHandlesEmptyDocument() {
        val toml   = "";
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().isObject()).isTrue();
        assertThat(result.get().size()).isZero();
    }

    @Test
    void tomlToValHandlesComplexConfiguration() {
        val toml   = """
                title = "TOML Example"

                [owner]
                name = "Tom Preston-Werner"

                [database]
                server = "192.168.1.1"
                ports = [ 8001, 8001, 8002 ]
                connection_max = 5000
                enabled = true

                [servers]

                [servers.alpha]
                ip = "10.0.0.1"
                dc = "eqdc10"

                [servers.beta]
                ip = "10.0.0.2"
                dc = "eqdc10"
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));

        assertThat(result.get().get("title").asText()).isEqualTo("TOML Example");
        assertThat(result.get().get("owner").get("name").asText()).isEqualTo("Tom Preston-Werner");
        assertThat(result.get().get("database").get("server").asText()).isEqualTo("192.168.1.1");
        assertThat(result.get().get("database").get("enabled").asBoolean()).isTrue();
        assertThat(result.get().get("servers").get("alpha").get("ip").asText()).isEqualTo("10.0.0.1");
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid = ", "[unclosed", "key = value without quotes", "= value",
            "[table\nkey = value" })
    void tomlToValThrowsExceptionForInvalidToml(String invalidToml) {
        assertThatThrownBy(() -> TomlFunctionLibrary.tomlToVal(Val.of(invalidToml))).isInstanceOf(Exception.class);
    }

    @Test
    void valToTomlConvertsObjectToTomlString() throws JsonProcessingException {
        val object = Val.ofJson("{\"name\":\"Rose\",\"color\":\"PINK\",\"petals\":5}");

        val result = TomlFunctionLibrary.valToToml(object);

        assertThat(result.getText()).contains("name");
        assertThat(result.getText()).contains("Rose");
        assertThat(result.getText()).contains("color");
        assertThat(result.getText()).contains("PINK");
        assertThat(result.getText()).contains("petals");
    }

    @Test
    void valToTomlHandlesNestedObjects() {
        val parent = Val.JSON.objectNode();
        val child  = Val.JSON.objectNode();
        child.put("key", "value");
        parent.set("child", child);

        val result = TomlFunctionLibrary.valToToml(Val.of(parent));

        assertThat(result.getText()).contains("child");
        assertThat(result.getText()).contains("key");
        assertThat(result.getText()).contains("value");
    }

    @Test
    void valToTomlHandlesArrays() {
        val object = Val.JSON.objectNode();
        val array  = Val.JSON.arrayNode();
        array.add("apple");
        array.add("banana");
        object.set("fruits", array);

        val result = TomlFunctionLibrary.valToToml(Val.of(object));

        assertThat(result.getText()).contains("fruits");
        assertThat(result.getText()).contains("apple");
        assertThat(result.getText()).contains("banana");
    }

    @Test
    void valToTomlReturnsErrorForErrorValue() {
        val error  = Val.error("Test error");
        val result = TomlFunctionLibrary.valToToml(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToTomlReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = TomlFunctionLibrary.valToToml(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void roundTripConversionPreservesData() {
        val original   = """
                [person]
                name = "Charlie"
                age = 35
                hobbies = ["reading", "coding"]
                """;
        val parsed     = TomlFunctionLibrary.tomlToVal(Val.of(original));
        val serialized = TomlFunctionLibrary.valToToml(parsed);
        val reparsed   = TomlFunctionLibrary.tomlToVal(serialized);

        assertThat(reparsed.get().get("person").get("name").asText()).isEqualTo("Charlie");
        assertThat(reparsed.get().get("person").get("age").asInt()).isEqualTo(35);
        assertThat(reparsed.get().get("person").get("hobbies").size()).isEqualTo(2);
    }

    @Test
    void valToTomlHandlesEmptyObject() {
        val result = TomlFunctionLibrary.valToToml(Val.ofEmptyObject());
        assertThat(result.getText()).isNotNull();
    }

    @Test
    void valToTomlHandlesPrimitiveValues() {
        val object = Val.JSON.objectNode();
        object.put("bool", true);
        object.put("number", 42);
        object.put("text", "hello");
        object.putNull("nullable");

        val result = TomlFunctionLibrary.valToToml(Val.of(object));

        assertThat(result.getText()).contains("bool");
        assertThat(result.getText()).contains("number");
        assertThat(result.getText()).contains("text");
    }

}
