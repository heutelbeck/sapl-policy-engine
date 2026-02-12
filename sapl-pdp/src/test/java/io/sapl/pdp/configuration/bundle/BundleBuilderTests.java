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
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BundleBuilder")
class BundleBuilderTests {

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

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();

        signedPolicy = BundleSecurityPolicy.builder(cultKeyPair.getPublic()).build();
    }

    @Test
    void whenBuildingEmptyBundleThenCreatesValidZip() throws IOException {
        val bundle = BundleBuilder.create().build();

        assertThat(bundle).isNotEmpty();
        assertThat(extractEntryNames(bundle)).isEmpty();
    }

    @Test
    void whenAddingPdpJsonThenBundleContainsPdpJson() throws IOException {
        val pdpJsonContent = """
                { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                """;
        val bundle         = BundleBuilder.create().withPdpJson(pdpJsonContent).build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("pdp.json");
        assertThat(entries.get("pdp.json")).contains("PRIORITY_DENY");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithmCases")
    void whenSettingCombiningAlgorithmThenPdpJsonContainsAlgorithm(CombiningAlgorithm algorithm, String expectedContent)
            throws IOException {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(algorithm).build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("pdp.json");
        assertThat(entries.get("pdp.json")).contains(expectedContent);
    }

    static Stream<Arguments> algorithmCases() {
        return Stream.of(
                arguments(
                        new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.DENY, ErrorHandling.PROPAGATE),
                        "PRIORITY_DENY"),
                arguments(new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                        ErrorHandling.ABSTAIN), "PRIORITY_PERMIT"),
                arguments(
                        new CombiningAlgorithm(VotingMode.UNANIMOUS, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE),
                        "UNANIMOUS"),
                arguments(new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.ABSTAIN),
                        "UNIQUE"),
                arguments(new CombiningAlgorithm(VotingMode.FIRST, DefaultDecision.PERMIT, ErrorHandling.PROPAGATE),
                        "FIRST"));
    }

    @Test
    void whenAddingPolicyThenBundleContainsPolicy() throws IOException {
        val policyContent = """
                policy "elder-access"
                permit subject.cultRank == "elder"
                """;
        val bundle        = BundleBuilder.create().withPolicy("access.sapl", policyContent).build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("access.sapl");
        assertThat(entries.get("access.sapl")).contains("elder-access");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "ritual", "ritual.sapl" })
    void whenAddingPolicyWithOrWithoutExtensionThenExtensionIsNormalized(String filename) throws IOException {
        val bundle = BundleBuilder.create().withPolicy(filename, "policy \"ritual\" permit true").build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("ritual.sapl");
    }

    @Test
    void whenAddingMultiplePoliciesThenBundleContainsAll() throws IOException {
        val bundle = BundleBuilder.create().withPolicy("forbidden-tome.sapl", "policy \"tome\" deny true")
                .withPolicy("altar-access.sapl", "policy \"altar\" permit subject.initiated == true")
                .withPolicy("deep-one-greeting.sapl", "policy \"greeting\" permit resource.location == \"Innsmouth\"")
                .build();

        val entries = extractEntries(bundle);
        assertThat(entries).hasSize(3).containsKeys("forbidden-tome.sapl", "altar-access.sapl",
                "deep-one-greeting.sapl");
    }

    @Test
    void whenAddingPoliciesAsMapThenBundleContainsAll() throws IOException {
        val policies = new LinkedHashMap<String, String>();
        policies.put("shoggoth-containment.sapl", "policy \"containment\" deny subject.sanity < 20");
        policies.put("mi-go-trade.sapl", "policy \"trade\" permit resource.type == \"brain_cylinder\"");

        val bundle = BundleBuilder.create().withPolicies(policies).build();

        val entries = extractEntries(bundle);
        assertThat(entries).hasSize(2).containsKeys("shoggoth-containment.sapl", "mi-go-trade.sapl");
    }

    @Test
    void whenBuildingCompletBundleThenContainsPdpJsonAndPolicies() throws IOException {
        val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.DENY, ErrorHandling.ABSTAIN);
        val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm)
                .withPolicy("necronomicon-access.sapl", """
                        policy "necronomicon"
                        deny subject.sanity < 50
                        obligation { "action": "summon_librarian" }
                        """).withPolicy("miskatonic-library.sapl", """
                        policy "library"
                        permit subject.affiliation == "Miskatonic University"
                        """).build();

        val entries = extractEntries(bundle);
        assertThat(entries).hasSize(3).containsKeys("pdp.json", "necronomicon-access.sapl", "miskatonic-library.sapl");
        assertThat(entries.get("pdp.json")).contains("PRIORITY_PERMIT");
    }

    @Test
    void whenWritingToPathThenFileIsCreated() throws IOException {
        val bundlePath = tempDir.resolve("cthulhu-cult.saplbundle");
        val algorithm  = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                ErrorHandling.ABSTAIN);

        BundleBuilder.create().withCombiningAlgorithm(algorithm)
                .withPolicy("summoning.sapl", "policy \"summon\" permit subject.stars == \"right\"")
                .writeTo(bundlePath);

        assertThat(bundlePath).exists();

        val content = Files.readAllBytes(bundlePath);
        val entries = extractEntries(content);
        assertThat(entries).hasSize(2).containsKeys("pdp.json", "summoning.sapl");
    }

    @Test
    void whenWritingToOutputStreamThenStreamContainsBundle() throws IOException {
        val outputStream = new ByteArrayOutputStream();
        val algorithm    = new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.PROPAGATE);

        BundleBuilder.create().withCombiningAlgorithm(algorithm)
                .withPolicy("yog-sothoth.sapl", "policy \"gate\" permit resource.dimension == \"outer\"")
                .writeTo(outputStream);

        val entries = extractEntries(outputStream.toByteArray());
        assertThat(entries).hasSize(2).containsKeys("pdp.json", "yog-sothoth.sapl");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void whenAddingPolicyWithInvalidFilenameThenThrowsException(String filename) {
        val builder = BundleBuilder.create();

        assertThatThrownBy(() -> builder.withPolicy(filename, "policy \"test\" permit true"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("filename");
    }

    @Test
    void whenAddingPolicyWithNullContentThenEmptyPolicyIsAdded() throws IOException {
        val bundle = BundleBuilder.create().withPolicy("empty.sapl", null).build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("empty.sapl");
        assertThat(entries.get("empty.sapl")).isEmpty();
    }

    @Test
    void whenWritingToInvalidPathThenThrowsException() {
        val invalidPath = tempDir.resolve("non-existent-dir/bundle.saplbundle");

        val builder = BundleBuilder.create().withPolicy("test.sapl", "policy \"test\" permit true");

        assertThatThrownBy(() -> builder.writeTo(invalidPath)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("Failed to write bundle");
    }

    @Test
    void whenBuildingWithConfigurationThenPdpJsonContainsVariables() throws IOException {
        val variables = new LinkedHashMap<String, String>();
        variables.put("maxSanity", "100");
        variables.put("cultName", "\"Esoteric Order of Dagon\"");

        val bundle = BundleBuilder.create().withConfiguration(CombiningAlgorithm.DEFAULT, variables).build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("pdp.json");
        assertThat(entries.get("pdp.json")).contains("PRIORITY_DENY").contains("variables").contains("maxSanity")
                .contains("cultName");
    }

    @Test
    void whenBuildingAndParsingThenRoundtripPreservesContent() {
        val originalPolicy = """
                policy "arkham-asylum"
                permit subject.role == "doctor"
                resource.ward != "forbidden"
                """;
        val algorithm      = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.DENY,
                ErrorHandling.ABSTAIN);

        val bundle = BundleBuilder.create().withCombiningAlgorithm(algorithm).withPolicy("arkham.sapl", originalPolicy)
                .build();

        val config = BundleParser.parse(bundle, "arkham-pdp", developmentPolicy);

        assertThat(config.pdpId()).isEqualTo("arkham-pdp");
        assertThat(config.configurationId()).startsWith("bundle-");
        assertThat(config.combiningAlgorithm()).isEqualTo(algorithm);
        assertThat(config.saplDocuments()).hasSize(1).first().asString().contains("arkham-asylum");
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
                                { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" } }
                                """)
                .build();

        val entries = extractEntries(bundle);
        assertThat(entries.get("pdp.json")).contains("PRIORITY_PERMIT");
    }

    @Test
    void whenAddingSamePolicyTwiceThenLastContentWins() throws IOException {
        val bundle = BundleBuilder.create().withPolicy("duplicate.sapl", "policy \"first\" permit true")
                .withPolicy("duplicate.sapl", "policy \"second\" deny true").build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey("duplicate.sapl");
        assertThat(entries.get("duplicate.sapl")).contains("second");
    }

    @Test
    void whenSigningBundleThenManifestIsIncluded() throws IOException {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                .withPolicy("elder-ritual.sapl", "policy \"ritual\" permit subject.initiated == true")
                .signWith(cultKeyPair.getPrivate(), "necronomicon-key").build();

        val entries = extractEntries(bundle);
        assertThat(entries).containsKey(BundleManifest.MANIFEST_FILENAME).containsKey("pdp.json")
                .containsKey("elder-ritual.sapl");
    }

    @Test
    void whenSigningBundleThenManifestContainsValidSignature() throws IOException {
        val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT, DefaultDecision.PERMIT,
                ErrorHandling.ABSTAIN);
        val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm)
                .withPolicy("deep-one.sapl", "policy \"greeting\" permit resource.location == \"Innsmouth\"")
                .signWith(cultKeyPair.getPrivate(), "dagon-key").build();

        val entries      = extractEntries(bundle);
        val manifestJson = entries.get(BundleManifest.MANIFEST_FILENAME);
        val manifest     = BundleManifest.fromJson(manifestJson);

        assertThat(manifest.signature()).isNotNull();
        assertThat(manifest.signature().algorithm()).isEqualTo("Ed25519");
        assertThat(manifest.signature().keyId()).isEqualTo("dagon-key");
        assertThat(manifest.files()).hasSize(2);
    }

    @Test
    void whenSigningWithNullKeyThenThrowsException() {
        val builder = BundleBuilder.create().withPolicy("forbidden.sapl", "policy \"forbidden\" deny true");

        assertThatThrownBy(() -> builder.signWith(null, "null-key")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private key must not be null");
    }

    @Test
    void whenSigningWithNonEd25519KeyThenThrowsException() {
        val builder = BundleBuilder.create().withPolicy("wrong-key.sapl", "policy \"wrong\" deny true");

        assertThatThrownBy(() -> builder.signWith(rsaPrivate, "rsa-key")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ed25519");
    }

    @Test
    void whenSigningWithNullKeyIdThenDefaultKeyIdIsUsed() throws IOException {
        val bundle = BundleBuilder.create().withPolicy("default-key.sapl", "policy \"default\" permit true")
                .signWith(cultKeyPair.getPrivate(), null).build();

        val entries      = extractEntries(bundle);
        val manifestJson = entries.get(BundleManifest.MANIFEST_FILENAME);
        val manifest     = BundleManifest.fromJson(manifestJson);

        assertThat(manifest.signature().keyId()).isEqualTo("default");
    }

    @Test
    void whenCheckingIfBuilderIsSignedThenReturnsCorrectly() {
        val unsignedBuilder = BundleBuilder.create().withPolicy("unsigned.sapl", "policy \"unsigned\" permit true");

        val signedBuilder = BundleBuilder.create().withPolicy("signed.sapl", "policy \"signed\" permit true")
                .signWith(cultKeyPair.getPrivate(), "test-key");

        assertThat(unsignedBuilder.isSigned()).isFalse();
        assertThat(signedBuilder.isSigned()).isTrue();
    }

    @Test
    void whenSigningAndParsingThenVerificationSucceeds() {
        val algorithm = new CombiningAlgorithm(VotingMode.UNIQUE, DefaultDecision.DENY, ErrorHandling.PROPAGATE);
        val bundle    = BundleBuilder.create().withCombiningAlgorithm(algorithm)
                .withPolicy("mi-go.sapl", "policy \"brain-cylinder\" permit resource.type == \"specimen\"")
                .signWith(cultKeyPair.getPrivate(), "yuggoth-key").build();

        val config = BundleParser.parse(bundle, "cult-pdp", signedPolicy);

        assertThat(config.pdpId()).isEqualTo("cult-pdp");
        assertThat(config.combiningAlgorithm()).isEqualTo(algorithm);
    }

    @Test
    void whenSigningAndParsingWithWrongKeyThenVerificationFails() {
        val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                .withPolicy("cthulhu.sapl", "policy \"awakening\" deny subject.stars != \"right\"")
                .signWith(cultKeyPair.getPrivate(), "rlyeh-key").build();

        val wrongKeyPair = generateEd25519KeyPair();
        val wrongPolicy  = BundleSecurityPolicy.builder(wrongKeyPair.getPublic()).build();

        assertThatThrownBy(() -> BundleParser.parse(bundle, "cult-pdp", wrongPolicy))
                .isInstanceOf(BundleSignatureException.class);
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

    private Set<String> extractEntryNames(byte[] bundle) throws IOException {
        return extractEntries(bundle).keySet();
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
