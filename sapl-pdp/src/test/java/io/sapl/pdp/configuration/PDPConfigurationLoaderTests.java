/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
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

import org.junit.jupiter.api.DisplayName;

@DisplayName("PDPConfigurationLoader")
class PDPConfigurationLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void whenLoadingFromEmptyDirectoryThenUsesDefaults() {
        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config).satisfies(c -> {
            assertThat(c.pdpId()).isEqualTo("arkham-pdp");
            assertThat(c.configurationId()).startsWith("dir:").contains("@sha256:");
            assertThat(c.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
            assertThat(c.data().variables()).isEmpty();
            assertThat(c.saplDocuments()).isEmpty();
        });
    }

    @Test
    void whenLoadingWithPdpJsonThenParsesAlgorithmAndVariables() throws IOException {
        val pdpJson = """
                {
                  "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" },
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

        assertThat(config.combiningAlgorithm()).isEqualTo(
                new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.PERMIT, ErrorHandling.ABSTAIN));
        assertThat(config.configurationId()).isEqualTo("innsmouth-v1");
        assertThat(config.data().variables()).containsEntry("cultName", Value.of("Esoteric Order of Dagon"))
                .containsEntry("memberCount", Value.of(42)).containsEntry("isActive", Value.TRUE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithmCases")
    void whenLoadingWithDifferentAlgorithmsThenParsesCorrectly(String algorithmJson, CombiningAlgorithm expected)
            throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": %s}
                """.formatted(algorithmJson));

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(expected);
    }

    static Stream<Arguments> algorithmCases() {
        return Stream.of(
                arguments("""
                        { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }""",
                        new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.DENY,
                                ErrorHandling.PROPAGATE)),
                arguments("""
                        { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" }""",
                        new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                                ErrorHandling.ABSTAIN)),
                arguments("""
                        { "votingMode": "UNANIMOUS", "defaultDecision": "ABSTAIN", "errorHandling": "PROPAGATE" }""",
                        new CombiningAlgorithm(VotingMode.UNANIMOUS, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE)),
                arguments("""
                        { "votingMode": "UNIQUE", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" }""",
                        new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.ABSTAIN)));
    }

    @Test
    void whenLoadingWithSaplFilesThenLoadsAllDocuments() throws IOException {
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
    void whenLoadingWithMixedFilesThenOnlyLoadsSaplFiles() throws IOException {
        Files.writeString(tempDir.resolve("valid.sapl"), "policy \"valid\" permit");
        Files.writeString(tempDir.resolve("readme.md"), "# Documentation");
        Files.writeString(tempDir.resolve("security.json"), "{}");
        Files.writeString(tempDir.resolve("pdp.json"), "{}");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("valid");
    }

    @Test
    void whenLoadingWithNestedVariablesThenParsesCorrectly() throws IOException {
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

        assertThat(config.data().variables()).containsKey("shrine");
        assertThat(config.data().variables().get("shrine")).isInstanceOf(ObjectValue.class);
    }

    @Test
    void whenLoadingWithInvalidJsonThenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{ invalid json }");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse pdp.json");
    }

    @Test
    void whenLoadingWithFirstAlgorithmThenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": { "votingMode": "FIRST", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }}
                """);

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class).cause().hasMessageContaining("FIRST");
    }

    @Test
    void whenLoadingWithInvalidAlgorithmThenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": { "votingMode": "INVALID", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" }}
                """);

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenLoadingFromContentThenCreatesConfiguration() {
        val pdpJsonContent = """
                {
                  "algorithm": { "votingMode": "UNIQUE", "defaultDecision": "ABSTAIN", "errorHandling": "PROPAGATE" },
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
        assertThat(config.combiningAlgorithm())
                .isEqualTo(new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE));
        assertThat(config.data().variables()).containsEntry("ritual", Value.of("summoning"));
        assertThat(config.saplDocuments()).hasSize(2);
    }

    @ParameterizedTest(name = "pdpJson = \"{0}\" should use defaults")
    @NullAndEmptySource
    @ValueSource(strings = { "   \n\t  ", "{}" })
    void whenLoadingFromContentWithAbsentOrEmptyPdpJsonThenUsesDefaults(String pdpJson) {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent(pdpJson, saplDocuments, "test-pdp", "/test/policies");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
        assertThat(config.data().variables()).isEmpty();
        assertThat(config.configurationId()).startsWith("res:").contains("@sha256:");
    }

    @Test
    void whenLoadingFromContentWithOnlyAlgorithmThenVariablesAreEmpty() {
        val config = PDPConfigurationLoader.loadFromContent(
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" } }
                        """,
                Map.of(), "test-pdp", "/policies/test");

        assertThat(config.combiningAlgorithm()).isEqualTo(
                new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT, ErrorHandling.ABSTAIN));
        assertThat(config.data().variables()).isEmpty();
        assertThat(config.configurationId()).startsWith("res:").contains("@sha256:");
    }

    @Test
    void whenLoadingFromContentWithOnlyVariablesThenUsesDefaultAlgorithm() {
        val config = PDPConfigurationLoader.loadFromContent("""
                { "variables": { "realm": "arkham" } }
                """, Map.of(), "test-pdp", "/policies/arkham");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
        assertThat(config.data().variables()).containsEntry("realm", Value.of("arkham"));
    }

    @Test
    void whenLoadingFromContentWithInvalidJsonThenThrowsException() {
        val emptyMap = Map.<String, String>of();
        assertThatThrownBy(
                () -> PDPConfigurationLoader.loadFromContent("not valid {{{", emptyMap, "test-pdp", "/invalid"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse pdp.json");
    }

    @Test
    void whenLoadingWithSubdirectoryThenSubdirectoryIsIgnored() throws IOException {
        Files.writeString(tempDir.resolve("root.sapl"), "policy \"root\" permit true;");
        val subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Files.writeString(subdir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("root");
    }

    @Test
    void whenPdpJsonContainsNullAndArrayValuesThenTheyAreHandled() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {
                  "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" },
                  "variables": {
                    "nullValue": null,
                    "realValue": "test",
                    "roles": ["admin", "user", "guest"],
                    "permissions": [1, 2, 3]
                  }
                }
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.data().variables()).containsKey("nullValue").containsEntry("realValue", Value.of("test"))
                .containsKey("roles").containsKey("permissions");
    }

}
