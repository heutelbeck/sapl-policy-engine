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
        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp", "config-1");

        assertThat(config.pdpId()).isEqualTo("arkham-pdp");
        assertThat(config.configurationId()).isEqualTo("config-1");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).isEmpty();
        assertThat(config.saplDocuments()).isEmpty();
    }

    @Test
    void whenLoadingWithPdpJson_thenParsesAlgorithmAndVariables() throws IOException {
        val pdpJson = """
                {
                  "algorithm": "PERMIT_UNLESS_DENY",
                  "variables": {
                    "cultName": "Esoteric Order of Dagon",
                    "memberCount": 42,
                    "isActive": true
                  }
                }
                """;
        Files.writeString(tempDir.resolve("pdp.json"), pdpJson);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "innsmouth-pdp", "v1.0");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_UNLESS_DENY);
        assertThat(config.variables()).containsEntry("cultName", Value.of("Esoteric Order of Dagon"));
        assertThat(config.variables()).containsEntry("memberCount", Value.of(42));
        assertThat(config.variables()).containsEntry("isActive", Value.TRUE);
    }

    @ParameterizedTest
    @MethodSource("algorithmCases")
    void whenLoadingWithDifferentAlgorithms_thenParsesCorrectly(String algorithmJson, CombiningAlgorithm expected)
            throws IOException {
        val pdpJson = """
                {"algorithm": "%s"}
                """.formatted(algorithmJson);
        Files.writeString(tempDir.resolve("pdp.json"), pdpJson);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config");

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
        val policy1 = """
                policy "grant access to deep ones"
                permit
                    subject.species == "deep_one"
                """;
        val policy2 = """
                policy "deny outsiders"
                deny
                    subject.origin != "innsmouth"
                """;
        Files.writeString(tempDir.resolve("access.sapl"), policy1);
        Files.writeString(tempDir.resolve("deny.sapl"), policy2);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config");

        assertThat(config.saplDocuments()).hasSize(2);
        assertThat(config.saplDocuments()).anyMatch(doc -> doc.contains("grant access to deep ones"));
        assertThat(config.saplDocuments()).anyMatch(doc -> doc.contains("deny outsiders"));
    }

    @Test
    void whenLoadingWithMixedFiles_thenOnlyLoadsSaplFiles() throws IOException {
        Files.writeString(tempDir.resolve("valid.sapl"), "policy \"valid\" permit");
        Files.writeString(tempDir.resolve("readme.md"), "# Documentation");
        Files.writeString(tempDir.resolve("config.json"), "{}");
        Files.writeString(tempDir.resolve("pdp.json"), "{}");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config");

        assertThat(config.saplDocuments()).hasSize(1);
        assertThat(config.saplDocuments().getFirst()).contains("valid");
    }

    @Test
    void whenLoadingWithNestedVariables_thenParsesCorrectly() throws IOException {
        val pdpJson = """
                {
                  "variables": {
                    "shrine": {
                      "location": "Devil Reef",
                      "depth": 100,
                      "guardians": ["shoggoth", "deep_one"]
                    }
                  }
                }
                """;
        Files.writeString(tempDir.resolve("pdp.json"), pdpJson);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config");

        assertThat(config.variables()).containsKey("shrine");
        val shrine = config.variables().get("shrine");
        assertThat(shrine).isInstanceOf(ObjectValue.class);
    }

    @Test
    void whenLoadingWithInvalidJson_thenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{ invalid json }");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse pdp.json");
    }

    @Test
    void whenLoadingWithInvalidAlgorithm_thenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{\"algorithm\": \"UNKNOWN_ALGORITHM\"}");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp", "test-config"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenLoadingFromContent_thenCreatesConfiguration() {
        val pdpJsonContent = """
                {
                  "algorithm": "ONLY_ONE_APPLICABLE",
                  "variables": {"ritual": "summoning"}
                }
                """;
        val saplDocuments  = Map.of("summon.sapl", "policy \"summon\" permit action == \"summon\"", "banish.sapl",
                "policy \"banish\" permit action == \"banish\"");

        val config = PDPConfigurationLoader.loadFromContent(pdpJsonContent, saplDocuments, "ritual-pdp", "v2");

        assertThat(config.pdpId()).isEqualTo("ritual-pdp");
        assertThat(config.configurationId()).isEqualTo("v2");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.ONLY_ONE_APPLICABLE);
        assertThat(config.variables()).containsEntry("ritual", Value.of("summoning"));
        assertThat(config.saplDocuments()).hasSize(2);
    }

    @Test
    void whenLoadingFromContentWithNullPdpJson_thenUsesDefaults() {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent(null, saplDocuments, "test-pdp", "config-1");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).isEmpty();
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingFromContentWithEmptyPdpJson_thenUsesDefaults() {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent("", saplDocuments, "test-pdp", "config-1");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.variables()).isEmpty();
    }

}
