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
package io.sapl.pdp.configuration;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PDPConfigurationLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void whenLoadingFromEmptyDirectory_thenUsesDefaults() {
        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config.pdpId()).isEqualTo("arkham-pdp");
        assertThat(config.configurationId()).startsWith("dir:").contains("@sha256:");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).isEmpty();
        assertThat(config.saplDocuments()).isEmpty();
    }

    @Test
    void whenLoadingWithPdpJson_thenParsesAlgorithmAndVariables() throws IOException {
        val pdpJson = """
                {
                  "algorithm": "PERMIT_UNLESS_DENY",
                  "configurationId": "innsmouth-v1",
                  "variables": {
                    "cultName": "Esoteric Order of Dagon",
                    "memberCount": 42,
                    "isActive": true
                  }
                }
                """;
        Files.writeString(tempDir.resolve("pdp.json"), pdpJson);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "innsmouth-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_UNLESS_DENY);
        assertThat(config.configurationId()).isEqualTo("innsmouth-v1");
        assertThat(config.variables()).containsEntry("cultName", Value.of("Esoteric Order of Dagon"))
                .containsEntry("memberCount", Value.of(42)).containsEntry("isActive", Value.TRUE);
    }

    @ParameterizedTest
    @MethodSource("algorithmCases")
    void whenLoadingWithDifferentAlgorithms_thenParsesCorrectly(String algorithmJson, CombiningAlgorithm expected)
            throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": "%s"}
                """.formatted(algorithmJson));

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(expected);
    }

    static Stream<Arguments> algorithmCases() {
        return Stream.of(arguments("DENY_OVERRIDES", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("deny_overrides", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("deny-overrides", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("PERMIT_OVERRIDES", CombiningAlgorithm.PERMIT_OVERRIDES),
                arguments("permit-overrides", CombiningAlgorithm.PERMIT_OVERRIDES),
                arguments("DENY_UNLESS_PERMIT", CombiningAlgorithm.DENY_UNLESS_PERMIT),
                arguments("deny-unless-permit", CombiningAlgorithm.DENY_UNLESS_PERMIT),
                arguments("PERMIT_UNLESS_DENY", CombiningAlgorithm.PERMIT_UNLESS_DENY),
                arguments("permit-unless-deny", CombiningAlgorithm.PERMIT_UNLESS_DENY),
                arguments("ONLY_ONE_APPLICABLE", CombiningAlgorithm.ONLY_ONE_APPLICABLE),
                arguments("only-one-applicable", CombiningAlgorithm.ONLY_ONE_APPLICABLE));
    }

    @Test
    void whenLoadingWithSaplFiles_thenLoadsAllDocuments() throws IOException {
        Files.writeString(tempDir.resolve("access.sapl"), """
                policy "grant access to deep ones"
                permit subject.species == "deep_one"
                """);
        Files.writeString(tempDir.resolve("deny.sapl"), """
                policy "deny outsiders"
                deny subject.origin != "innsmouth"
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(2).anyMatch(doc -> doc.contains("grant access to deep ones"))
                .anyMatch(doc -> doc.contains("deny outsiders"));
    }

    @Test
    void whenLoadingWithMixedFiles_thenOnlyLoadsSaplFiles() throws IOException {
        Files.writeString(tempDir.resolve("valid.sapl"), "policy \"valid\" permit");
        Files.writeString(tempDir.resolve("readme.md"), "# Documentation");
        Files.writeString(tempDir.resolve("config.json"), "{}");
        Files.writeString(tempDir.resolve("pdp.json"), "{}");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("valid");
    }

    @Test
    void whenLoadingWithNestedVariables_thenParsesCorrectly() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {
                  "variables": {
                    "shrine": {
                      "location": "Devil Reef",
                      "depth": 100,
                      "guardians": ["shoggoth", "deep_one"]
                    }
                  }
                }
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.variables()).containsKey("shrine");
        assertThat(config.variables().get("shrine")).isInstanceOf(ObjectValue.class);
    }

    @Test
    void whenLoadingWithInvalidJson_thenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{ invalid json }");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse pdp.json");
    }

    @Test
    void whenLoadingWithInvalidAlgorithm_thenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{\"algorithm\": \"UNKNOWN_ALGORITHM\"}");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenLoadingFromContent_thenCreatesConfiguration() {
        val pdpJsonContent = """
                {
                  "algorithm": "ONLY_ONE_APPLICABLE",
                  "configurationId": "ritual-v2",
                  "variables": {"ritual": "summoning"}
                }
                """;
        val saplDocuments  = Map.of("summon.sapl", "policy \"summon\" permit action == \"summon\"", "banish.sapl",
                "policy \"banish\" permit action == \"banish\"");

        val config = PDPConfigurationLoader.loadFromContent(pdpJsonContent, saplDocuments, "ritual-pdp",
                "/policies/rituals");

        assertThat(config.pdpId()).isEqualTo("ritual-pdp");
        assertThat(config.configurationId()).isEqualTo("ritual-v2");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.ONLY_ONE_APPLICABLE);
        assertThat(config.variables()).containsEntry("ritual", Value.of("summoning"));
        assertThat(config.saplDocuments()).hasSize(2);
    }

    @ParameterizedTest(name = "pdpJson = \"{0}\" should use defaults")
    @NullAndEmptySource
    @ValueSource(strings = { "   \n\t  ", "{}" })
    void whenLoadingFromContentWithAbsentOrEmptyPdpJson_thenUsesDefaults(String pdpJson) {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent(pdpJson, saplDocuments, "test-pdp", "/test/policies");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).isEmpty();
        assertThat(config.configurationId()).startsWith("res:").contains("@sha256:");
    }

    @Test
    void whenLoadingFromContentWithOnlyAlgorithm_thenVariablesAreEmpty() {
        val config = PDPConfigurationLoader.loadFromContent("""
                { "algorithm": "PERMIT_OVERRIDES" }
                """, Map.of(), "test-pdp", "/policies/test");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(config.variables()).isEmpty();
        assertThat(config.configurationId()).startsWith("res:").contains("@sha256:");
    }

    @Test
    void whenLoadingFromContentWithOnlyVariables_thenUsesDefaultAlgorithm() {
        val config = PDPConfigurationLoader.loadFromContent("""
                { "variables": { "realm": "arkham" } }
                """, Map.of(), "test-pdp", "/policies/arkham");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).containsEntry("realm", Value.of("arkham"));
    }

    @Test
    void whenLoadingFromContentWithInvalidJson_thenThrowsException() {
        assertThatThrownBy(
                () -> PDPConfigurationLoader.loadFromContent("not valid {{{", Map.of(), "test-pdp", "/invalid"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse pdp.json");
    }

    @Test
    void whenLoadingWithSubdirectory_thenSubdirectoryIsIgnored() throws IOException {
        Files.writeString(tempDir.resolve("root.sapl"), "policy \"root\" permit true;");
        val subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Files.writeString(subdir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("root");
    }

    @Test
    void whenPdpJsonContainsNullAndArrayValues_thenTheyAreHandled() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {
                  "algorithm": "DENY_OVERRIDES",
                  "variables": {
                    "nullValue": null,
                    "realValue": "test",
                    "roles": ["admin", "user", "guest"],
                    "permissions": [1, 2, 3]
                  }
                }
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.variables()).containsKey("nullValue").containsEntry("realValue", Value.of("test"))
                .containsKey("roles").containsKey("permissions");
    }

}
