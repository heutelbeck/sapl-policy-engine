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

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TomlFunctionLibraryTests {

    @Test
    void tomlToValParsesSimpleKeyValuePairs() {
        val toml   = """
                cultist = "Wilbur Whateley"
                role = "ACOLYTE"
                securityLevel = 3
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("cultist").asText()).isEqualTo("Wilbur Whateley");
        assertThat(result.get().get("role").asText()).isEqualTo("ACOLYTE");
        assertThat(result.get().get("securityLevel").asInt()).isEqualTo(3);
    }

    @Test
    void tomlToValParsesTable() {
        val toml   = """
                [entity]
                name = "Azathoth"
                title = "Daemon Sultan"
                threatLevel = 9
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("entity").get("name").asText()).isEqualTo("Azathoth");
        assertThat(result.get().get("entity").get("title").asText()).isEqualTo("Daemon Sultan");
        assertThat(result.get().get("entity").get("threatLevel").asInt()).isEqualTo(9);
    }

    @Test
    void tomlToValParsesNestedTables() {
        val toml   = """
                [ritual]
                name = "Summoning"

                [ritual.location]
                site = "Miskatonic University"
                dangerLevel = 5
                containment = 30
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("ritual").get("name").asText()).isEqualTo("Summoning");
        assertThat(result.get().get("ritual").get("location").get("site").asText()).isEqualTo("Miskatonic University");
        assertThat(result.get().get("ritual").get("location").get("dangerLevel").asInt()).isEqualTo(5);
        assertThat(result.get().get("ritual").get("location").get("containment").asInt()).isEqualTo(30);
    }

    @Test
    void tomlToValParsesArrays() {
        val toml   = """
                artifacts = ["Necronomicon", "Silver Key", "Shining Trapezohedron"]
                threatLevels = [1, 2, 3, 4, 5]
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));

        val artifacts = result.get().get("artifacts");
        assertThat(artifacts.isArray()).isTrue();
        assertThat(artifacts.size()).isEqualTo(3);
        assertThat(artifacts.get(0).asText()).isEqualTo("Necronomicon");

        val threatLevels = result.get().get("threatLevels");
        assertThat(threatLevels.size()).isEqualTo(5);
        assertThat(threatLevels.get(0).asInt()).isEqualTo(1);
    }

    @Test
    void tomlToValParsesArrayOfTables() {
        val toml          = """
                [[investigators]]
                name = "Carter"
                sanity = 85

                [[investigators]]
                name = "Pickman"
                sanity = 42
                """;
        val result        = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        val investigators = result.get().get("investigators");

        assertThat(investigators.isArray()).isTrue();
        assertThat(investigators.size()).isEqualTo(2);
        assertThat(investigators.get(0).get("name").asText()).isEqualTo("Carter");
        assertThat(investigators.get(1).get("name").asText()).isEqualTo("Pickman");
    }

    @Test
    void tomlToValParsesInlineTable() {
        val toml   = """
                location = { city = "Arkham", state = "Massachusetts", year = 1928 }
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("location").get("city").asText()).isEqualTo("Arkham");
        assertThat(result.get().get("location").get("state").asText()).isEqualTo("Massachusetts");
        assertThat(result.get().get("location").get("year").asInt()).isEqualTo(1928);
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void tomlToValParsesBooleans(String boolValue) {
        val toml   = "sealed = " + boolValue;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("sealed").isBoolean()).isTrue();
    }

    @Test
    void tomlToValParsesIntegers() {
        val toml   = """
                cultists = 42
                depth = -999
                zero = 0
                population = 1000000
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("cultists").asInt()).isEqualTo(42);
        assertThat(result.get().get("depth").asInt()).isEqualTo(-999);
        assertThat(result.get().get("zero").asInt()).isZero();
    }

    @Test
    void tomlToValParsesFloats() {
        val toml   = """
                power = 9.99
                negative = -0.5
                probability = 1.23e-4
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("power").asDouble()).isEqualTo(9.99);
        assertThat(result.get().get("negative").asDouble()).isEqualTo(-0.5);
    }

    @Test
    void tomlToValParsesStrings() {
        val toml   = """
                chant = "Ia! Ia! Cthulhu fhtagn!"
                prophecy = \"""
                When the stars are right
                The Great Old Ones shall return
                From their cosmic slumber\"""
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("chant").asText()).isEqualTo("Ia! Ia! Cthulhu fhtagn!");
        assertThat(result.get().get("prophecy").asText()).contains("stars are right");
        assertThat(result.get().get("prophecy").asText()).contains("Great Old Ones");
    }

    @Test
    void tomlToValHandlesQuotedKeys() {
        val toml   = """
                "cultist name" = "Lavinia Whateley"
                "location.city" = "Dunwich"
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("cultist name").asText()).isEqualTo("Lavinia Whateley");
        assertThat(result.get().get("location.city").asText()).isEqualTo("Dunwich");
    }

    @Test
    void tomlToValHandlesComments() {
        val toml   = """
                # Elder entity configuration
                name = "Cthulhu" # The Dreamer in R'lyeh
                # Location data
                depth = 9999
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));
        assertThat(result.get().get("name").asText()).isEqualTo("Cthulhu");
        assertThat(result.get().get("depth").asInt()).isEqualTo(9999);
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
                title = "Arkham Asylum Configuration"

                [warden]
                name = "Herbert West"

                [security]
                system = "Restricted"
                levels = [ 1, 2, 3, 4, 5 ]
                max_containment = 999
                enabled = true

                [facilities]

                [facilities.cellBlock_A]
                location = "East Wing"
                dangerLevel = "EXTREME"

                [facilities.cellBlock_B]
                location = "West Wing"
                dangerLevel = "HIGH"
                """;
        val result = TomlFunctionLibrary.tomlToVal(Val.of(toml));

        assertThat(result.get().get("title").asText()).isEqualTo("Arkham Asylum Configuration");
        assertThat(result.get().get("warden").get("name").asText()).isEqualTo("Herbert West");
        assertThat(result.get().get("security").get("system").asText()).isEqualTo("Restricted");
        assertThat(result.get().get("security").get("enabled").asBoolean()).isTrue();
        assertThat(result.get().get("facilities").get("cellBlock_A").get("location").asText()).isEqualTo("East Wing");
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid = ", "[unclosed", "= value", "[table\nkey = value" })
    void tomlToValReturnsErrorForInvalidToml(String invalidToml) {
        val result = TomlFunctionLibrary.tomlToVal(Val.of(invalidToml));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).startsWith("Failed to parse TOML:");
    }

    @Test
    void valToTomlConvertsObjectToTomlString() {
        val object = Val.JSON.objectNode();
        object.put("name", "Nyarlathotep");
        object.put("title", "Crawling Chaos");
        object.put("threatLevel", 8);

        val result = TomlFunctionLibrary.valToToml(Val.of(object));

        assertThat(result.getText()).contains("name").contains("Nyarlathotep").contains("title")
                .contains("Crawling Chaos").contains("threatLevel");
    }

    @Test
    void valToTomlHandlesNestedObjects() {
        val ritual   = Val.JSON.objectNode();
        val location = Val.JSON.objectNode();
        location.put("site", "R'lyeh");
        ritual.set("location", location);

        val result = TomlFunctionLibrary.valToToml(Val.of(ritual));

        assertThat(result.getText()).contains("location").contains("site").contains("R'lyeh");
    }

    @Test
    void valToTomlHandlesArrays() {
        val object = Val.JSON.objectNode();
        val array  = Val.JSON.arrayNode();
        array.add("Dagon");
        array.add("Hydra");
        object.set("deities", array);

        val result = TomlFunctionLibrary.valToToml(Val.of(object));

        assertThat(result.getText()).contains("deities").contains("Dagon").contains("Hydra");
    }

    @Test
    void valToTomlReturnsErrorForErrorValue() {
        val error  = Val.error("Ritual interrupted.");
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
                [investigator]
                name = "Carter"
                sanity = 77
                artifacts = ["Silver Key", "Lamp"]
                """;
        val parsed     = TomlFunctionLibrary.tomlToVal(Val.of(original));
        val serialized = TomlFunctionLibrary.valToToml(parsed);
        val reparsed   = TomlFunctionLibrary.tomlToVal(serialized);

        assertThat(reparsed.get().get("investigator").get("name").asText()).isEqualTo("Carter");
        assertThat(reparsed.get().get("investigator").get("sanity").asInt()).isEqualTo(77);
        assertThat(reparsed.get().get("investigator").get("artifacts").size()).isEqualTo(2);
    }

    @Test
    void valToTomlHandlesEmptyObject() {
        val result = TomlFunctionLibrary.valToToml(Val.ofEmptyObject());
        assertThat(result.getText()).isNotNull();
    }

    @Test
    void valToTomlHandlesPrimitiveValues() {
        val object = Val.JSON.objectNode();
        object.put("sealed", true);
        object.put("year", 1928);
        object.put("location", "Arkham");
        object.putNull("sanity");

        val result = TomlFunctionLibrary.valToToml(Val.of(object));

        assertThat(result.getText()).contains("sealed").contains("year").contains("location");
    }

}
