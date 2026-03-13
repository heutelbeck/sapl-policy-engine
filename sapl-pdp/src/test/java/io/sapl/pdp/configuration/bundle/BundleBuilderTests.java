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
package io.sapl.pdp.configuration.bundle;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BundleBuilder")
class BundleBuilderTests {

    private static final String VALID_PDP_JSON = """
            { "configurationId": "test", "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
            """;

    @TempDir
    Path tempDir;

    private static KeyPair cultKeyPair;

    private static PrivateKey rsaPrivate;

    private static BundleSecurityPolicy developmentPolicy;
    private static BundleSecurityPolicy signedPolicy;

    @BeforeAll
    static void setupKeys() throws NoSuchAlgorithmException {
        val ed25519Generator = KeyPairGenerator.getInstance("Ed25519");
        cultKeyPair = ed25519Generator.generateKeyPair();

        val rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        val rsaKeyPair = rsaGenerator.generateKeyPair();
        rsaPrivate = rsaKeyPair.getPrivate();

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        signedPolicy = BundleSecurityPolicy.builder(cultKeyPair.getPublic()).build();
    }

    @Nested
    @DisplayName("PDP JSON validation")
    class PdpJsonValidation {

        @Test
        void whenBuildingWithoutPdpJsonThenThrowsException() {
            val builder = BundleBuilder.create();

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("missing pdp.json");
        }

        @Test
        void whenAddingPdpJsonThenBundleContainsPdpJson() throws IOException {
            val pdpJsonContent = """
                    { "configurationId": "test-v1", "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                    """;
            val bundle         = BundleBuilder.create().withPdpJson(pdpJsonContent).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("pdp.json").extractingByKey("pdp.json").asString()
                    .contains("PRIORITY_DENY");
        }

        @ParameterizedTest(name = "configurationId {0}")
        @MethodSource("invalidConfigurationIdCases")
        void whenAddingPdpJsonWithInvalidConfigurationIdThenThrowsException(String description, String pdpJson) {
            val builder = BundleBuilder.create().withPdpJson(pdpJson);

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("configurationId");
        }

        static Stream<Arguments> invalidConfigurationIdCases() {
            return Stream.of(
                    arguments("missing",
                            """
                                    { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                                    """),
                    arguments("blank",
                            """
                                    { "configurationId": "   ", "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                                    """));
        }

        @Test
        void whenAddingPdpJsonWithInvalidAlgorithmThenThrowsException() {
            val pdpJsonContent = """
                    { "configurationId": "test", "algorithm": { "votingMode": "NONSENSE", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                    """;
            val builder        = BundleBuilder.create().withPdpJson(pdpJsonContent);

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("Combining algorithm")
    class CombiningAlgorithmTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("algorithmCases")
        void whenSettingCombiningAlgorithmThenPdpJsonContainsAlgorithm(CombiningAlgorithm algorithm,
                String expectedContent) throws IOException {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(algorithm).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("pdp.json").extractingByKey("pdp.json").asString()
                    .contains(expectedContent);
        }

        static Stream<Arguments> algorithmCases() {
            return Stream.of(
                    arguments(new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.DENY,
                            ErrorHandling.PROPAGATE), "PRIORITY_DENY"),
                    arguments(new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                            ErrorHandling.ABSTAIN), "PRIORITY_PERMIT"),
                    arguments(new CombiningAlgorithm(VotingMode.UNANIMOUS, DefaultDecision.ABSTAIN,
                            ErrorHandling.PROPAGATE), "UNANIMOUS"),
                    arguments(new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.ABSTAIN),
                            "UNIQUE"));
        }

        @Test
        void whenBuildingWithFirstAlgorithmThenThrowsException() {
            val algorithm = new CombiningAlgorithm(VotingMode.FIRST, DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
            val builder   = BundleBuilder.create().withCombiningAlgorithm(algorithm);

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("FIRST is not allowed");
        }
    }

    @Nested
    @DisplayName("Policy addition")
    class PolicyAddition {

        @Test
        void whenAddingPolicyThenBundleContainsPolicy() throws IOException {
            val policyContent = """
                    policy "elder-access"
                    permit subject.cultRank == "elder";
                    """;
            val bundle        = BundleBuilder.create().withPdpJson(VALID_PDP_JSON)
                    .withPolicy("access.sapl", policyContent).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("access.sapl").extractingByKey("access.sapl").asString()
                    .contains("elder-access");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "ritual", "ritual.sapl" })
        void whenAddingPolicyWithOrWithoutExtensionThenExtensionIsNormalized(String filename) throws IOException {
            val bundle = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy(filename, """
                    policy "ritual" permit true;
                    """).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("ritual.sapl");
        }

        @Test
        void whenAddingMultiplePoliciesThenBundleContainsAll() throws IOException {
            val bundle = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("forbidden-tome.sapl", """
                    policy "tome" deny true;
                    """).withPolicy("altar-access.sapl", """
                    policy "altar" permit subject.initiated == true;
                    """).withPolicy("deep-one-greeting.sapl", """
                    policy "greeting" permit resource.location == "Innsmouth";
                    """).build();

            val entries = extractEntries(bundle);
            assertThat(entries).hasSize(4).containsKeys("pdp.json", "forbidden-tome.sapl", "altar-access.sapl",
                    "deep-one-greeting.sapl");
        }

        @Test
        void whenAddingPoliciesAsMapThenBundleContainsAll() throws IOException {
            val policies = new LinkedHashMap<String, String>();
            policies.put("shoggoth-containment.sapl", """
                    policy "containment" deny subject.sanity < 20;
                    """);
            policies.put("mi-go-trade.sapl", """
                    policy "trade" permit resource.type == "brain_cylinder";
                    """);

            val bundle = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicies(policies).build();

            val entries = extractEntries(bundle);
            assertThat(entries).hasSize(3).containsKeys("pdp.json", "shoggoth-containment.sapl", "mi-go-trade.sapl");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        void whenAddingPolicyWithInvalidFilenameThenThrowsException(String filename) {
            val builder = BundleBuilder.create();

            assertThatThrownBy(() -> builder.withPolicy(filename, """
                    policy "test" permit true;
                    """)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("filename");
        }

        @Test
        void whenAddingSamePolicyTwiceThenLastContentWins() throws IOException {
            val bundle = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("duplicate.sapl", """
                    policy "first" permit true;
                    """).withPolicy("duplicate.sapl", """
                    policy "second" deny true;
                    """).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("duplicate.sapl").extractingByKey("duplicate.sapl").asString()
                    .contains("second");
        }
    }

    @Nested
    @DisplayName("Build and write")
    class BuildAndWrite {

        @Test
        void whenBuildingCompleteBundleThenContainsPdpJsonAndPolicies() throws IOException {
            val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.DENY,
                    ErrorHandling.ABSTAIN);
            val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm)
                    .withPolicy("necronomicon-access.sapl", """
                            policy "necronomicon"
                            deny subject.sanity < 50;
                            obligation { "action": "summon_librarian" }
                            """).withPolicy("miskatonic-library.sapl", """
                            policy "library"
                            permit subject.affiliation == "Miskatonic University";
                            """).build();

            val entries = extractEntries(bundle);
            assertThat(entries).hasSize(3)
                    .containsKeys("pdp.json", "necronomicon-access.sapl", "miskatonic-library.sapl")
                    .extractingByKey("pdp.json").asString().contains("PRIORITY_PERMIT");
        }

        @Test
        void whenWritingToPathThenFileIsCreated() throws IOException {
            val bundlePath = tempDir.resolve("cthulhu-cult.saplbundle");
            val algorithm  = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                    ErrorHandling.ABSTAIN);

            BundleBuilder.create().withCombiningAlgorithm(algorithm).withPolicy("summoning.sapl", """
                    policy "summon" permit subject.stars == "right";
                    """).writeTo(bundlePath);

            assertThat(bundlePath).exists();

            val content = Files.readAllBytes(bundlePath);
            val entries = extractEntries(content);
            assertThat(entries).hasSize(2).containsKeys("pdp.json", "summoning.sapl");
        }

        @Test
        void whenWritingToOutputStreamThenStreamContainsBundle() throws IOException {
            val outputStream = new ByteArrayOutputStream();
            val algorithm    = new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.PROPAGATE);

            BundleBuilder.create().withCombiningAlgorithm(algorithm).withPolicy("yog-sothoth.sapl", """
                    policy "gate" permit resource.dimension == "outer";
                    """).writeTo(outputStream);

            val entries = extractEntries(outputStream.toByteArray());
            assertThat(entries).hasSize(2).containsKeys("pdp.json", "yog-sothoth.sapl");
        }

        @Test
        void whenWritingToInvalidPathThenThrowsException() {
            val invalidPath = tempDir.resolve("non-existent-dir/bundle.saplbundle");

            val builder = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("test.sapl", """
                    policy "test" permit true;
                    """);

            assertThatThrownBy(() -> builder.writeTo(invalidPath)).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("Failed to write bundle");
        }

        @Test
        void whenBuildingAndParsingThenRoundtripPreservesContent() {
            val originalPolicy = """
                    policy "arkham-asylum"
                    permit subject.role == "doctor";
                    resource.ward != "forbidden";
                    """;
            val algorithm      = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.DENY,
                    ErrorHandling.ABSTAIN);

            val bundle = BundleBuilder.create().withCombiningAlgorithm(algorithm)
                    .withPolicy("arkham.sapl", originalPolicy).build();

            val config = BundleParser.parse(bundle, "arkham-pdp", developmentPolicy);

            assertThat(config).satisfies(c -> {
                assertThat(c.pdpId()).isEqualTo("arkham-pdp");
                assertThat(c.configurationId()).startsWith("bundle-");
                assertThat(c.combiningAlgorithm()).isEqualTo(algorithm);
                assertThat(c.saplDocuments()).hasSize(1).first().asString().contains("arkham-asylum");
            });
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        void whenBuildingWithConfigurationThenPdpJsonContainsVariables() throws IOException {
            val variables = new LinkedHashMap<String, String>();
            variables.put("maxSanity", "100");
            variables.put("cultName", "\"Esoteric Order of Dagon\"");

            val bundle = BundleBuilder.create().withConfiguration(CombiningAlgorithm.DEFAULT, variables).build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey("pdp.json").extractingByKey("pdp.json").asString().contains("PRIORITY_DENY")
                    .contains("variables").contains("maxSanity").contains("cultName");
        }

        @Test
        void whenBuildingWithEmptyVariablesMapThenPdpJsonContainsEmptyObject() throws IOException {
            val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                    ErrorHandling.ABSTAIN);
            val bundle    = BundleBuilder.create().withConfiguration(algorithm, Map.of()).build();

            val entries = extractEntries(bundle);
            assertThat(entries.get("pdp.json")).contains("\"variables\":").containsPattern("\\{\\s*}");
        }

        @Test
        void whenOverwritingPdpJsonThenLastValueWins() throws IOException {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPdpJson(
                            """
                                    { "configurationId": "override", "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" } }
                                    """)
                    .build();

            val entries = extractEntries(bundle);
            assertThat(entries.get("pdp.json")).contains("PRIORITY_PERMIT");
        }
    }

    @Nested
    @DisplayName("Signing")
    class SigningTests {

        @Test
        void whenSigningBundleThenManifestIsIncluded() throws IOException {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("elder-ritual.sapl", """
                            policy "ritual" permit subject.initiated == true;
                            """).signWith(cultKeyPair.getPrivate(), "necronomicon-key").build();

            val entries = extractEntries(bundle);
            assertThat(entries).containsKey(BundleManifest.MANIFEST_FILENAME).containsKey("pdp.json")
                    .containsKey("elder-ritual.sapl");
        }

        @Test
        void whenSigningBundleThenManifestContainsValidSignature() throws IOException {
            val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                    ErrorHandling.ABSTAIN);
            val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm).withPolicy("deep-one.sapl", """
                    policy "greeting" permit resource.location == "Innsmouth";
                    """).signWith(cultKeyPair.getPrivate(), "dagon-key").build();

            val entries      = extractEntries(bundle);
            val manifestJson = entries.get(BundleManifest.MANIFEST_FILENAME);
            val manifest     = BundleManifest.fromJson(manifestJson);

            assertThat(manifest).satisfies(m -> {
                assertThat(m.signature()).isNotNull().satisfies(sig -> {
                    assertThat(sig.algorithm()).isEqualTo("Ed25519");
                    assertThat(sig.keyId()).isEqualTo("dagon-key");
                });
                assertThat(m.files()).hasSize(2);
            });
        }

        @Test
        void whenSigningWithNullKeyThenThrowsException() {
            val builder = BundleBuilder.create().withPolicy("forbidden.sapl", """
                    policy "forbidden" deny true;
                    """);

            assertThatThrownBy(() -> builder.signWith(null, "null-key")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Private key must not be null");
        }

        @Test
        void whenSigningWithNonEd25519KeyThenThrowsException() {
            val builder = BundleBuilder.create().withPolicy("wrong-key.sapl", """
                    policy "wrong" deny true;
                    """);

            assertThatThrownBy(() -> builder.signWith(rsaPrivate, "rsa-key"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ed25519");
        }

        @Test
        void whenSigningWithNullKeyIdThenDefaultKeyIdIsUsed() throws IOException {
            val bundle = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("default-key.sapl", """
                    policy "default" permit true;
                    """).signWith(cultKeyPair.getPrivate(), null).build();

            val entries      = extractEntries(bundle);
            val manifestJson = entries.get(BundleManifest.MANIFEST_FILENAME);
            val manifest     = BundleManifest.fromJson(manifestJson);

            assertThat(manifest.signature().keyId()).isEqualTo("default");
        }

        @Test
        void whenCheckingIfBuilderIsSignedThenReturnsCorrectly() {
            val unsignedBuilder = BundleBuilder.create().withPolicy("unsigned.sapl", """
                    policy "unsigned" permit true;
                    """);

            val signedBuilder = BundleBuilder.create().withPolicy("signed.sapl", """
                    policy "signed" permit true;
                    """).signWith(cultKeyPair.getPrivate(), "test-key");

            assertThat(unsignedBuilder.isSigned()).isFalse();
            assertThat(signedBuilder.isSigned()).isTrue();
        }

        @Test
        void whenSigningAndParsingThenVerificationSucceeds() {
            val algorithm = new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.PROPAGATE);
            val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm).withPolicy("mi-go.sapl", """
                    policy "brain-cylinder" permit resource.type == "specimen";
                    """).signWith(cultKeyPair.getPrivate(), "yuggoth-key").build();

            val config = BundleParser.parse(bundle, "cult-pdp", signedPolicy);

            assertThat(config).satisfies(c -> {
                assertThat(c.pdpId()).isEqualTo("cult-pdp");
                assertThat(c.combiningAlgorithm()).isEqualTo(algorithm);
            });
        }

        @Test
        void whenSigningAndParsingWithWrongKeyThenVerificationFails() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("cthulhu.sapl", """
                            policy "awakening" deny subject.stars != "right";
                            """).signWith(cultKeyPair.getPrivate(), "rlyeh-key").build();

            val wrongKeyPair = generateEd25519KeyPair();
            val wrongPolicy  = BundleSecurityPolicy.builder(wrongKeyPair.getPublic()).build();

            assertThatThrownBy(() -> BundleParser.parse(bundle, "cult-pdp", wrongPolicy))
                    .isInstanceOf(BundleSignatureException.class);
        }
    }

    @Nested
    @DisplayName("Policy syntax validation")
    class PolicySyntaxValidation {

        @Test
        void whenBuildingWithInvalidSaplSyntaxThenThrowsException() {
            val builder = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("invalid.sapl",
                    "this is not valid SAPL at all");

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("syntax errors").hasMessageContaining("invalid.sapl");
        }

        @Test
        void whenBuildingWithMultipleInvalidPoliciesThenErrorContainsAll() {
            val builder = BundleBuilder.create().withPdpJson(VALID_PDP_JSON)
                    .withPolicy("bad-one.sapl", "not valid sapl").withPolicy("bad-two.sapl", "also not valid");

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("bad-one.sapl").hasMessageContaining("bad-two.sapl");
        }

        @Test
        void whenBuildingWithMixOfValidAndInvalidPoliciesThenErrorReportsOnlyInvalid() {
            val builder = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("good.sapl", """
                    policy "good" permit true;
                    """).withPolicy("broken.sapl", "not valid sapl");

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("broken.sapl").hasMessageNotContaining("good.sapl");
        }

        @Test
        void whenAddingPolicyWithNullContentThenBuildRejectsEmptyPolicy() {
            val builder = BundleBuilder.create().withPdpJson(VALID_PDP_JSON).withPolicy("empty.sapl", null);

            assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("syntax errors").hasMessageContaining("empty.sapl");
        }
    }

    private Map<String, String> extractEntries(byte[] bundle) throws IOException {
        val entries = new HashMap<String, String>();
        try (val zipStream = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                val content = new String(zipStream.readAllBytes(), StandardCharsets.UTF_8);
                entries.put(entry.getName(), content);
            }
        }
        return entries;
    }

    private KeyPair generateEd25519KeyPair() {
        try {
            val generator = KeyPairGenerator.getInstance("Ed25519");
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 not available.", e);
        }
    }

}
