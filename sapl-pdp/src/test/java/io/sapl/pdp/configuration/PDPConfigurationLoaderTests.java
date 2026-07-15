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
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PDPConfigurationLoader")
class PDPConfigurationLoaderTests {

    private static final long MAX_PDP_JSON_BYTES = 1024L * 1024L * 1024L;

    @TempDir
    Path tempDir;

    @Test
    void whenLoadingFromDirectoryWithoutPdpJsonThenUsesDefaults() {
        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
        assertThat(config.configurationId()).matches("^dir:[^@/\\\\]+@[0-9a-f]{16}$");
        assertThat(config.data().variables()).isEmpty();
        assertThat(config.data().secrets()).isEmpty();
    }

    @Test
    @DisplayName("extension files in the directory are loaded into the configuration")
    void whenDirectoryHasExtensionFilesThenLoadedIntoConfiguration() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("ext-upstreams.json"), """
                { "servers": [] }""");
        Files.writeString(tempDir.resolve("ext-upstreams-secrets.json"), """
                { "apiKey": "ENC[value]" }""");
        Files.writeString(tempDir.resolve("critical-extensions.json"), """
                ["upstreams"]""");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config.extensions()).containsKey("upstreams");
        assertThat(config.extensionSecrets()).containsKey("upstreams");
        assertThat(config.criticalExtensions()).containsExactly("upstreams");
    }

    @Test
    @DisplayName("a secrets.json file is loaded into the PDP data")
    void whenDirectoryHasSecretsFileThenLoaded() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("secrets.json"), """
                { "apiKey": "cleartext-value" }""");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config.data().secrets()).containsKey("apiKey");
    }

    @Test
    @DisplayName("a secrets.sealed.json file with sealed content is loaded into the PDP data")
    void whenDirectoryHasSealedSecretsFileThenLoaded() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("secrets.sealed.json"), """
                { "apiKey": "ENC[ciphertext]" }""");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp");

        assertThat(config.data().secrets()).containsKey("apiKey");
    }

    @Test
    @DisplayName("a sealed-named secrets file with plaintext content is rejected")
    void whenSealedNamedSecretsFileHasPlaintextThenThrows() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("secrets.sealed.json"), """
                { "apiKey": "cleartext-value" }""");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("content is not sealed");
    }

    @Test
    @DisplayName("a directory mixing sealed and plaintext secrets files is rejected")
    void whenDirectoryMixesSealingThenThrows() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("secrets.sealed.json"), """
                { "apiKey": "ENC[ciphertext]" }""");
        Files.writeString(tempDir.resolve("ext-upstreams.json"), "{}");
        Files.writeString(tempDir.resolve("ext-upstreams-secrets.json"), """
                { "apiKey": "cleartext" }""");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("mixes sealed and plaintext");
    }

    @Test
    @DisplayName("a pdp.json with an inline secrets section is rejected")
    void whenPdpJsonHasInlineSecretsThenThrows() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                { "secrets": { "k": "v" } }""");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp"))
                .isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("must not contain a 'secrets' section");
    }

    @Test
    @DisplayName("a critical extension without any payload file is rejected")
    void whenDirectoryCriticalExtensionMissingPayloadThenThrows() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), "{}");
        Files.writeString(tempDir.resolve("critical-extensions.json"), """
                ["upstreams"]""");

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "arkham-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Critical extension 'upstreams'");
    }

    @Test
    @DisplayName("an explicit null algorithm degrades gracefully to the default, like an absent field (run2-078)")
    void whenLoadingWithExplicitNullAlgorithmThenFallsBackToDefault() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": null}
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
    }

    @Test
    void whenLoadingWithPdpJsonThenParsesAlgorithmAndVariables() throws IOException {
        val pdpJson = """
                {
                  "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" },
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
        assertThat(config.configurationId()).matches("^dir:[^@]+@[0-9a-f]{16}$");
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
        writePdpJson(tempDir);
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
        writePdpJson(tempDir);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("valid");
    }

    @Test
    void whenLoadingWithNestedVariablesThenParsesCorrectly() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"),
                """
                        {
                          "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" },
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
    @DisplayName("a directory pdp.json larger than one GiB is rejected before parsing")
    void whenDirectoryPdpJsonExceedsMaximumSizeThenFailsBeforeParsing() throws IOException {
        val pdpJson = tempDir.resolve("pdp.json");
        try (val channel = FileChannel.open(pdpJson, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(MAX_PDP_JSON_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] { '\n' }));
        }

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("pdp.json exceeds maximum size");
    }

    @Test
    void whenLoadingWithFirstAlgorithmThenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": { "votingMode": "FIRST", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }}
                """);

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("FIRST");
    }

    @Test
    void whenLoadingWithInvalidAlgorithmThenThrowsException() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {"algorithm": { "votingMode": "INVALID", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" }}
                """);

        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                .isInstanceOf(PDPConfigurationException.class);
    }

    @Test
    void whenLoadingFromContentThenCreatesConfiguration() {
        val pdpJsonContent = """
                {
                  "algorithm": { "votingMode": "UNIQUE", "defaultDecision": "ABSTAIN", "errorHandling": "PROPAGATE" },
                  "variables": {"ritual": "summoning"}
                }
                """;
        val saplDocuments  = Map.of("summon.sapl", "policy \"summon\" permit action == \"summon\"", "banish.sapl",
                "policy \"banish\" permit action == \"banish\"");

        val config = PDPConfigurationLoader.loadFromContent(pdpJsonContent, saplDocuments, "ritual-pdp", "rituals");

        assertThat(config.pdpId()).isEqualTo("ritual-pdp");
        assertThat(config.configurationId()).matches("^res:rituals@[0-9a-f]{16}$");
        assertThat(config.combiningAlgorithm())
                .isEqualTo(new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE));
        assertThat(config.data().variables()).containsEntry("ritual", Value.of("summoning"));
        assertThat(config.saplDocuments()).hasSize(2);
    }

    @ParameterizedTest(name = "pdpJson = \"{0}\" should throw for empty content")
    @NullAndEmptySource
    @ValueSource(strings = "   \n\t  ")
    void whenLoadingFromContentWithNullOrBlankPdpJsonThenThrowsException(String pdpJson) {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        assertThatThrownBy(
                () -> PDPConfigurationLoader.loadFromContent(pdpJson, saplDocuments, "test-pdp", "/test/policies"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("must not be empty");
    }

    @Test
    void whenLoadingFromContentWithMissingAlgorithmThenUsesDefault() {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent("{}", saplDocuments, "test-pdp", "/test/policies");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
    }

    @Test
    void whenLoadingFromContentWithOnlyAlgorithmThenVariablesAreEmpty() {
        val config = PDPConfigurationLoader.loadFromContent(
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" } }
                        """,
                Map.of(), "test-pdp", "test");

        assertThat(config.combiningAlgorithm()).isEqualTo(
                new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT, ErrorHandling.ABSTAIN));
        assertThat(config.data().variables()).isEmpty();
        assertThat(config.configurationId()).matches("^res:test@[0-9a-f]{16}$");
    }

    @Test
    void whenBundleContainsMorePoliciesThanConfiguredMaximumThenThrowsException() {
        val pdpJson       = """
                {
                  "compilerOptions": { "maxPolicyDocuments": 1 }
                }
                """;
        val saplDocuments = Map.of("one.sapl", "policy \"one\" permit", "two.sapl", "policy \"two\" permit");

        assertThatThrownBy(
                () -> PDPConfigurationLoader.loadFromBundle(pdpJson, null, saplDocuments, "test-pdp", "bundle-v1"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("File count exceeds maximum");
    }

    @Test
    @DisplayName("the resource id equals the independently recomputed content derivation")
    void whenLoadingFromContentThenIdEqualsRecomputedDerivation() {
        val pdpJsonContent = """
                { "algorithm": { "votingMode": "UNIQUE", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                """;
        val saplDocuments  = Map.of("test.sapl", "policy \"test\" permit");

        val config = PDPConfigurationLoader.loadFromContent(pdpJsonContent, saplDocuments, "test-pdp", "arkham");

        val contents = new TreeMap<String, byte[]>();
        contents.put("pdp.json", pdpJsonContent.getBytes(StandardCharsets.UTF_8));
        contents.put("test.sapl", "policy \"test\" permit".getBytes(StandardCharsets.UTF_8));
        assertThat(config.configurationId()).isEqualTo(ConfigurationIds.derive("res:arkham", contents));
    }

    @Test
    @DisplayName("resource configs differing only in variables get different derived ids")
    void whenTwoResourceConfigsDifferOnlyInVariablesThenDerivedIdsDiffer() {
        val saplDocuments = Map.of("test.sapl", "policy \"test\" permit");
        val benign        = PDPConfigurationLoader.loadFromContent("""
                { "variables": { "cultName": "benign" } }
                """, saplDocuments, "test-pdp", "cult");
        val malicious     = PDPConfigurationLoader.loadFromContent("""
                { "variables": { "cultName": "malicious" } }
                """, saplDocuments, "test-pdp", "cult");

        assertThat(benign.configurationId()).isNotEqualTo(malicious.configurationId());
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
        writePdpJson(tempDir);
        Files.writeString(tempDir.resolve("root.sapl"), "policy \"root\" permit true;");
        val subdir = tempDir.resolve("subdir");
        Files.createDirectory(subdir);
        Files.writeString(subdir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("root");
    }

    @Test
    void whenLoadingFromDirectoryWithValidPdpJsonButNoSaplFilesThenSucceeds() throws IOException {
        writePdpJson(tempDir);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.saplDocuments()).isEmpty();
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
    }

    @Test
    void whenLoadingFromContentWithFirstAlgorithmThenThrowsException() {
        val emptyMap = Map.<String, String>of();
        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromContent("""
                {"algorithm": { "votingMode": "FIRST", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }}
                """, emptyMap, "test-pdp", "/test/policies")).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("FIRST");
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

    @Test
    void whenLoadingPdpJsonWithJavaCommentsThenParsesSuccessfully() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {
                  // This is a line comment
                  "algorithm": {
                    "votingMode": "PRIORITY_DENY", /* inline comment */
                    "defaultDecision": "DENY",
                    "errorHandling": "PROPAGATE"
                  }
                }
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DEFAULT);
    }

    @Test
    void whenLoadingPdpJsonWithYamlCommentsThenParsesSuccessfully() throws IOException {
        Files.writeString(tempDir.resolve("pdp.json"), """
                {
                  # YAML-style comment
                  "algorithm": {
                    "votingMode": "PRIORITY_PERMIT",
                    "defaultDecision": "PERMIT",
                    "errorHandling": "ABSTAIN"
                  }
                }
                """);

        val config = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

        assertThat(config.combiningAlgorithm()).isEqualTo(
                new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT, ErrorHandling.ABSTAIN));
    }

    @Nested
    @DisplayName("legacy configurationId rejection")
    class LegacyConfigurationIdRejection {

        private static final String LEGACY_PDP_JSON = """
                { "configurationId": "legacy-v1" }""";

        @Test
        @DisplayName("a directory pdp.json still carrying configurationId is rejected with the migration message")
        void whenDirectoryPdpJsonCarriesConfigurationIdThenRejected() throws IOException {
            Files.writeString(tempDir.resolve("pdp.json"), LEGACY_PDP_JSON);

            assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp"))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("moved to the bundle manifest");
        }

        @Test
        @DisplayName("content still carrying configurationId is rejected with the migration message")
        void whenContentCarriesConfigurationIdThenRejected() {
            val emptyMap = Map.<String, String>of();

            assertThatThrownBy(
                    () -> PDPConfigurationLoader.loadFromContent(LEGACY_PDP_JSON, emptyMap, "test-pdp", "test"))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("moved to the bundle manifest");
        }

        @Test
        @DisplayName("a bundle pdp.json still carrying configurationId is rejected with the migration message")
        void whenBundlePdpJsonCarriesConfigurationIdThenRejected() {
            val emptyMap = Map.<String, String>of();

            assertThatThrownBy(
                    () -> PDPConfigurationLoader.loadFromBundle(LEGACY_PDP_JSON, null, emptyMap, "test-pdp", "id-1"))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("moved to the bundle manifest");
        }
    }

    @Nested
    @DisplayName("derived directory ids")
    class DerivedDirectoryIds {

        @Test
        @DisplayName("the directory id uses the directory name, not the path")
        void whenLoadingFromDirectoryThenIdUsesDirectoryName() throws IOException {
            val policies = Files.createDirectory(tempDir.resolve("policies"));
            Files.writeString(policies.resolve("access.sapl"), "policy \"access\" permit true");

            val config = PDPConfigurationLoader.loadFromDirectory(policies, "test-pdp");

            assertThat(config.configurationId()).matches("^dir:policies@[0-9a-f]{16}$");
        }

        @Test
        @DisplayName("identical content in equally named directories at different paths yields the identical id")
        void whenSameContentInSameNamedDirectoriesAtDifferentPathsThenSameId() throws IOException {
            val firstParent  = Files.createDirectory(tempDir.resolve("host-a"));
            val secondParent = Files.createDirectory(tempDir.resolve("host-b"));
            val firstDir     = Files.createDirectory(firstParent.resolve("policies"));
            val secondDir    = Files.createDirectory(secondParent.resolve("policies"));
            for (val dir : new Path[] { firstDir, secondDir }) {
                Files.writeString(dir.resolve("access.sapl"), "policy \"access\" permit true");
            }

            val firstConfig  = PDPConfigurationLoader.loadFromDirectory(firstDir, "test-pdp");
            val secondConfig = PDPConfigurationLoader.loadFromDirectory(secondDir, "test-pdp");

            assertThat(firstConfig.configurationId()).isEqualTo(secondConfig.configurationId());
        }

        @Test
        @DisplayName("reloading an unchanged directory yields the identical id")
        void whenReloadingUnchangedDirectoryThenIdenticalId() throws IOException {
            writePdpJson(tempDir);
            Files.writeString(tempDir.resolve("access.sapl"), "policy \"access\" permit true");

            val firstLoad  = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");
            val secondLoad = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

            assertThat(firstLoad.configurationId()).isEqualTo(secondLoad.configurationId());
        }

        @Test
        @DisplayName("a secrets file change flips the derived id")
        void whenSecretsFileChangesThenIdChanges() throws IOException {
            writePdpJson(tempDir);
            Files.writeString(tempDir.resolve("secrets.json"), """
                    { "apiKey": "first-value" }""");
            val firstLoad = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

            Files.writeString(tempDir.resolve("secrets.json"), """
                    { "apiKey": "second-value" }""");
            val secondLoad = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

            assertThat(firstLoad.configurationId()).isNotEqualTo(secondLoad.configurationId());
        }

        @Test
        @DisplayName("a policy content change flips the derived id")
        void whenPolicyContentChangesThenIdChanges() throws IOException {
            writePdpJson(tempDir);
            Files.writeString(tempDir.resolve("access.sapl"), "policy \"access\" permit true");
            val firstLoad = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

            Files.writeString(tempDir.resolve("access.sapl"), "policy \"access\" deny true");
            val secondLoad = PDPConfigurationLoader.loadFromDirectory(tempDir, "test-pdp");

            assertThat(firstLoad.configurationId()).isNotEqualTo(secondLoad.configurationId());
        }
    }

    @ParameterizedTest(name = "configurationId = \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("loadFromBundle requires a non-blank configurationId")
    void whenLoadingFromBundleWithBlankConfigurationIdThenThrows(String configurationId) {
        val emptyMap = Map.<String, String>of();

        assertThatThrownBy(
                () -> PDPConfigurationLoader.loadFromBundle("{}", null, emptyMap, "test-pdp", configurationId))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("non-blank configurationId");
    }

    private void writePdpJson(Path directory) throws IOException {
        Files.writeString(directory.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }}
                        """);
    }

}
