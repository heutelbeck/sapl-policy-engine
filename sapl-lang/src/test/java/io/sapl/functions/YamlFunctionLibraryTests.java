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

class YamlFunctionLibraryTests {

    @Test
    void yamlToValParsesSimpleMapping() {
        val yaml   = """
                cultist: Wilbur Whateley
                role: ACOLYTE
                securityLevel: 3
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("cultist").asText()).isEqualTo("Wilbur Whateley");
        assertThat(result.get().get("role").asText()).isEqualTo("ACOLYTE");
        assertThat(result.get().get("securityLevel").asInt()).isEqualTo(3);
    }

    @Test
    void yamlToValParsesNestedMappings() {
        val yaml   = """
                entity:
                  name: Cthulhu
                  location:
                    city: R'lyeh
                    depth: 9999
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("entity").get("name").asText()).isEqualTo("Cthulhu");
        assertThat(result.get().get("entity").get("location").get("city").asText()).isEqualTo("R'lyeh");
        assertThat(result.get().get("entity").get("location").get("depth").asInt()).isEqualTo(9999);
    }

    @Test
    void yamlToValParsesSequences() {
        val yaml      = """
                artifacts:
                  - Necronomicon
                  - Silver Key
                  - Shining Trapezohedron
                """;
        val result    = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val artifacts = result.get().get("artifacts");
        assertThat(artifacts.isArray()).isTrue();
        assertThat(artifacts.size()).isEqualTo(3);
        assertThat(artifacts.get(0).asText()).isEqualTo("Necronomicon");
        assertThat(artifacts.get(1).asText()).isEqualTo("Silver Key");
        assertThat(artifacts.get(2).asText()).isEqualTo("Shining Trapezohedron");
    }

    @Test
    void yamlToValParsesInlineSequence() {
        val yaml    = "threats: [1, 2, 3, 4, 5]";
        val result  = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val threats = result.get().get("threats");
        assertThat(threats.isArray()).isTrue();
        assertThat(threats.size()).isEqualTo(5);
    }

    @Test
    void yamlToValParsesInlineMapping() {
        val yaml   = "investigator: {name: Herbert West, sanity: 30}";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("investigator").get("name").asText()).isEqualTo("Herbert West");
        assertThat(result.get().get("investigator").get("sanity").asInt()).isEqualTo(30);
    }

    @Test
    void yamlToValParsesMultilineString() {
        val yaml     = """
                prophecy: |
                  When the stars are right
                  the Great Old Ones
                  shall return.
                """;
        val result   = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        val prophecy = result.get().get("prophecy").asText();
        assertThat(prophecy).contains("stars are right").contains("Great Old Ones");
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false", "yes", "no", "on", "off" })
    void yamlToValParsesBooleans(String booleanValue) {
        val yaml   = "sealed: " + booleanValue;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("sealed").isBoolean()).isTrue();
    }

    @Test
    void yamlToValParsesNull() {
        val yaml   = "sanity: null";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("sanity").isNull()).isTrue();
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
        val yaml   = "incantations: []";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("incantations").isArray()).isTrue();
        assertThat(result.get().get("incantations").size()).isZero();
    }

    @Test
    void yamlToValHandlesQuotedStrings() {
        val yaml   = """
                chant: "Ia! Ia! Cthulhu fhtagn!"
                path: 'C:\\Arkham\\Miskatonic'
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("chant").asText()).isEqualTo("Ia! Ia! Cthulhu fhtagn!");
        assertThat(result.get().get("path").asText()).isEqualTo("C:\\Arkham\\Miskatonic");
    }

    @Test
    void yamlToValHandlesNumbers() {
        val yaml   = """
                cultists: 42
                power: 9.99
                depth: -999
                probability: 1.23e-4
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("cultists").asInt()).isEqualTo(42);
        assertThat(result.get().get("power").asDouble()).isEqualTo(9.99);
        assertThat(result.get().get("depth").asInt()).isEqualTo(-999);
    }

    @Test
    void yamlToValHandlesUnicodeCharacters() {
        val yaml   = "inscription: Ph'nglui mglw'nafh 世界 🌙";
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("inscription").asText()).isEqualTo("Ph'nglui mglw'nafh 世界 🌙");
    }

    @Test
    void yamlToValParsesComplexStructure() {
        val yaml   = """
                rituals:
                  summoning:
                    grimoire: Necronomicon
                    requirements:
                      - full moon
                      - sacrificial altar
                    danger:
                      level: EXTREME
                      containment: IMPOSSIBLE
                  banishment:
                    grimoire: Elder Sign
                    requirements:
                      - pure silver
                      - ancient words
                """;
        val result = YamlFunctionLibrary.yamlToVal(Val.of(yaml));
        assertThat(result.get().get("rituals").get("summoning").get("grimoire").asText()).isEqualTo("Necronomicon");
        assertThat(result.get().get("rituals").get("summoning").get("requirements").size()).isEqualTo(2);
        assertThat(result.get().get("rituals").get("summoning").get("danger").get("level").asText())
                .isEqualTo("EXTREME");
    }

    @ParameterizedTest
    @ValueSource(strings = { ":", "[invalid", "  : misplaced colon", "{unclosed: bracket", "key: [unclosed" })
    void yamlToValReturnsErrorForInvalidYaml(String invalidYaml) {
        val result = YamlFunctionLibrary.yamlToVal(Val.of(invalidYaml));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).startsWith("Failed to parse YAML:");
    }

    @Test
    void valToYamlConvertsObjectToYamlString() {
        val object = Val.JSON.objectNode();
        object.put("name", "Azathoth");
        object.put("title", "Daemon Sultan");
        object.put("threatLevel", 9);

        val result = YamlFunctionLibrary.valToYaml(Val.of(object));
        assertThat(result.getText()).contains("name:").contains("Azathoth").contains("title:")
                .contains("Daemon Sultan");
    }

    @Test
    void valToYamlConvertsArrayToYamlString() {
        val array = Val.JSON.arrayNode();
        array.add("Dagon");
        array.add("Hydra");
        array.add("Cthulhu");

        val result = YamlFunctionLibrary.valToYaml(Val.of(array));
        assertThat(result.getText()).contains("Dagon").contains("Hydra").contains("Cthulhu");
    }

    @Test
    void valToYamlHandlesNestedStructures() {
        val ritual   = Val.JSON.objectNode();
        val location = Val.JSON.objectNode();
        location.put("place", "Miskatonic University");
        ritual.set("location", location);

        val result = YamlFunctionLibrary.valToYaml(Val.of(ritual));
        assertThat(result.getText()).contains("location:").contains("place:").contains("Miskatonic University");
    }

    @Test
    void valToYamlReturnsErrorForErrorValue() {
        val error  = Val.error("Ritual failed.");
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
                investigator: Carter
                sanity: 35
                artifacts:
                  - Silver Key
                  - Lamp
                """;
        val parsed     = YamlFunctionLibrary.yamlToVal(Val.of(original));
        val serialized = YamlFunctionLibrary.valToYaml(parsed);
        val reparsed   = YamlFunctionLibrary.yamlToVal(serialized);

        assertThat(reparsed.get().get("investigator").asText()).isEqualTo("Carter");
        assertThat(reparsed.get().get("sanity").asInt()).isEqualTo(35);
        assertThat(reparsed.get().get("artifacts").size()).isEqualTo(2);
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
        assertThat(YamlFunctionLibrary.valToYaml(Val.of(1928)).getText()).contains("1928");
    }

}
