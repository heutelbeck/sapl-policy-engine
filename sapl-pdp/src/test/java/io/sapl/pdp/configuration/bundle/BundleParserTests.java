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

import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.secrets.SecretSealing;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BundleParser")
class BundleParserTests {

    private static final String TEST_PDP_ID    = "cthulhu-pdp";
    private static final String TEST_CONFIG_ID = "eldritch-v1";

    private static final String DEFAULT_PDP_JSON = """
            {"algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"PROPAGATE"}}""";

    private static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm DENY_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    private static BundleSecurityPolicy developmentPolicy;
    private static BundleSecurityPolicy signedPolicy;
    private static KeyPair              signingKeyPair;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupSecurityPolicy() throws NoSuchAlgorithmException {
        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
        signingKeyPair    = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        signedPolicy      = BundleSecurityPolicy.builder(signingKeyPair.getPublic()).build();
    }

    @Test
    @DisplayName("signedAt is exposed only when the signature was actually verified")
    void whenSignatureNotVerifiedThenSignedAtIsNull() {
        val signedBundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                .withPolicy("test.sapl", "policy \"p\" permit true;").signWith(signingKeyPair.getPrivate(), "test-key")
                .build();

        // Verified against the trusted key: the signing time is authenticated and exposed.
        assertThat(BundleParser.parseWithMetadata(signedBundle, TEST_PDP_ID, signedPolicy).signedAt()).isNotNull();
        // Verification disabled: the same signing time is unauthenticated and withheld.
        assertThat(BundleParser.parseWithMetadata(signedBundle, TEST_PDP_ID, developmentPolicy).signedAt()).isNull();
    }

    @Test
    void whenParsingValidBundleThenManifestIdLandsInConfiguration() throws IOException {
        val bundleBytes = manifestedBundle(Map.of("pdp.json",
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" } }
                        """,
                "elder-sign.sapl", "policy \"elder-sign\" permit true;"));

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.pdpId()).isEqualTo(TEST_PDP_ID);
        assertThat(config.configurationId()).isEqualTo(TEST_CONFIG_ID);
        assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingBundleWithoutPdpJsonThenThrowsException() throws IOException {
        val bundleBytes = manifestedBundle(Map.of("cultist.sapl", "policy \"cultist\" deny true;"));

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("pdp.json");
    }

    @Test
    @DisplayName("a bundle without a manifest is rejected fail-closed")
    void whenParsingBundleWithoutManifestThenRejected() throws IOException {
        val bundleBytes = rawZip(Map.of("pdp.json", DEFAULT_PDP_JSON, "cultist.sapl", "policy \"cultist\" deny true;"));

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining(".sapl-manifest.json");
    }

    @Test
    @DisplayName("a legacy pdp.json still carrying configurationId is rejected with the migration message")
    void whenParsingBundleWithLegacyConfigurationIdInPdpJsonThenRejected() throws IOException {
        val legacyPdpJson = """
                {"algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"PROPAGATE"},"configurationId":"legacy-v1"}""";
        val bundleBytes   = manifestedBundle(Map.of("pdp.json", legacyPdpJson));

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("moved to the bundle manifest");
    }

    @Test
    void whenParsingBundleWithMultiplePoliciesThenAllPoliciesExtracted() throws IOException {
        val files = new LinkedHashMap<String, String>();
        files.put("pdp.json", DEFAULT_PDP_JSON);
        for (val name : new String[] { "access.sapl", "audit.sapl", "logging.sapl" }) {
            files.put(name, "policy \"" + name + "\" permit true;");
        }
        val bundleBytes = manifestedBundle(files);

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(3);
    }

    @Test
    void whenParsingFromPathThenConfigurationIsExtracted() throws IOException {
        val bundlePath = tempDir.resolve("test.saplbundle");
        Files.write(bundlePath,
                manifestedBundle(Map.of("pdp.json",
                        """
                                { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                                """,
                        "shoggoth.sapl", "policy \"shoggoth\" deny true;")));

        val config = BundleParser.parse(bundlePath, TEST_PDP_ID, developmentPolicy);

        assertThat(config.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @ParameterizedTest(name = "parse from InputStream with size known = {0}")
    @ValueSource(booleans = { true, false })
    void whenParsingFromInputStreamThenConfigurationIsExtracted(boolean sizeKnown) throws IOException {
        val bundleBytes = manifestedBundle(
                Map.of("pdp.json", DEFAULT_PDP_JSON, "dagon.sapl", "policy \"dagon\" permit true;"));
        val inputStream = new ByteArrayInputStream(bundleBytes);

        val config = sizeKnown ? BundleParser.parse(inputStream, bundleBytes.length, TEST_PDP_ID, developmentPolicy)
                : BundleParser.parse(inputStream, TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Nested
    @DisplayName("audience possession pre-check")
    class AudiencePossessionPreCheck {

        private byte[] sealedBundle() {
            val recipient = SecretSealing.generateRecipientKey();
            return BundleBuilder.create().withPdpJson(DEFAULT_PDP_JSON).withSecrets("""
                    { "apiKey": "TOP-SECRET-VALUE" }""").sealSecretsWith(recipient.toPublicJWK()).build();
        }

        @Test
        @DisplayName("a sealed bundle whose recipient key is not held is rejected before any unseal attempt")
        void whenSealingRecipientNotHeldThenFailFast() {
            val bundleBytes    = sealedBundle();
            val mismatchPolicy = BundleSecurityPolicy.builder().disableSignatureVerification()
                    .withSealingKeyIds(Set.of("other-key")).build();

            assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, mismatchPolicy))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("recipient")
                    .hasMessageContaining("other-key");
        }

        @Test
        @DisplayName("a sealed bundle whose recipient key is held is accepted")
        void whenSealingRecipientHeldThenAccepted() {
            val bundleBytes = sealedBundle();
            val matchPolicy = BundleSecurityPolicy.builder().disableSignatureVerification()
                    .withSealingKeyIds(Set.of("recipient")).build();

            val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, matchPolicy);

            assertThat(config.data().secrets()).containsKey("apiKey");
        }

        @Test
        @DisplayName("an empty sealing key set skips the possession pre-check")
        void whenNoSealingKeyIdsDeclaredThenPossessionCheckIsSkipped() {
            val bundleBytes = sealedBundle();

            val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

            assertThat(config.data().secrets()).containsKey("apiKey");
        }

        @Test
        @DisplayName("sealed content without a manifest audience is rejected")
        void whenSealedContentWithoutAudienceThenRejected() throws IOException {
            val bundleBytes = manifestedBundle(Map.of("pdp.json", DEFAULT_PDP_JSON, "secrets.sealed.json", """
                    { "apiKey": "ENC[ciphertext]" }"""), null);

            assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                    .isInstanceOf(PDPConfigurationException.class)
                    .hasMessageContaining("names no audience.sealingRecipient");
        }

        @Test
        @DisplayName("a manifest audience without sealed content is rejected")
        void whenAudienceWithoutSealedContentThenRejected() throws IOException {
            val bundleBytes = manifestedBundle(Map.of("pdp.json", DEFAULT_PDP_JSON), "orphan-recipient-key");

            assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                    .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("no sealed content");
        }
    }

    @Test
    @DisplayName("a bundle with a subdirectory entry is rejected")
    void whenBundleContainsNestedDirectoriesThenRejected() throws IOException {
        val bundleBytes = createBundleWithNestedDirectory();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Unexpected file")
                .hasMessageContaining("subdir/");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pathTraversalAttempts")
    void whenBundleContainsPathTraversalThenThrowsException(String maliciousPath) throws IOException {
        val bundleBytes = createBundleWithEntryAndConfig(maliciousPath, "malicious content");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Path traversal");
    }

    static Stream<Arguments> pathTraversalAttempts() {
        return Stream.of(arguments("../../../etc/passwd"), arguments("..\\..\\windows\\system32\\security"),
                arguments("/etc/passwd"), arguments("\\windows\\system32"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedArchiveExtensions")
    void whenBundleContainsNestedArchiveThenThrowsException(String archiveName) throws IOException {
        val bundleBytes = createBundleWithEntryAndConfig(archiveName, "PK\003\004");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Nested archive");
    }

    static Stream<Arguments> nestedArchiveExtensions() {
        return Stream.of(arguments("nested.zip"), arguments("nested.saplbundle"), arguments("nested.jar"),
                arguments("nested.war"), arguments("NESTED.ZIP"), arguments("archive.JAR"));
    }

    @Test
    void whenBundleHasTooManyEntriesThenThrowsException() throws IOException {
        val bundleBytes = createBundleWithManyEntries(1001);

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Too many entries");
    }

    @Test
    void whenBundleExceedsCompressionRatioThenThrowsException() throws IOException {
        val largeRepetitiveContent = "A".repeat(50_000);
        val bundleBytes            = createBundleWithEntryAndConfig("eldritch-tome.sapl",
                "policy \"forbidden-knowledge\" permit true; /* " + largeRepetitiveContent + " */");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Compression ratio");
    }

    @Test
    @DisplayName("a compression bomb hidden in a subdirectory entry is still counted toward the ratio limit")
    void whenSubdirectoryEntryExceedsCompressionRatioThenThrowsException() throws IOException {
        val largeRepetitiveContent = "A".repeat(500_000);
        val baos                   = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);
            // A skipped subdirectory entry must still have its bytes counted against the
            // bomb limits.
            zos.putNextEntry(new ZipEntry("nested/eldritch-tome.sapl"));
            zos.write(("policy \"forbidden-knowledge\" permit true; /* " + largeRepetitiveContent + " */")
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        val bundleBytes = baos.toByteArray();
        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Compression ratio");
    }

    @Test
    void whenUncompressedSizeExceedsConfiguredLimitThenThrowsException() throws IOException {
        val largeContent = "X".repeat(2 * 1024 * 1024);
        val bundleBytes  = createBundleWithEntryAndConfig("necronomicon.sapl", largeContent);

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy, 1024L * 1024))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("the cumulative uncompressed-size limit counts decompressed bytes, not UTF-16 chars, for multibyte content")
    void whenEarlierEntryIsMultibyteThenCumulativeSizeLimitCountsBytesNotChars() throws IOException {
        // first.sapl holds 120000 chars that each encode to two UTF-8 bytes, so it
        // decompresses to 240000 bytes while its String char count is only 120000.
        // The content is pseudo-random across the two-byte UTF-8 range so it does not
        // compress, keeping the compression-ratio guard out of the way and isolating
        // the cumulative uncompressed-size guard. Together with the 40000-byte ascii
        // second.sapl the byte-accurate total is 280000 (> the 260000 cap, so parsing
        // must be rejected), while a char-based total would be only 160000 and would
        // wrongly accept the bundle.
        val firstEntry  = pseudoRandomTwoByteUtf8(120_000);
        val secondEntry = "X".repeat(40_000);
        val baos        = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("first.sapl"));
            zos.write(firstEntry.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("second.sapl"));
            zos.write(secondEntry.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        val bundleBytes = baos.toByteArray();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy, 260_000L))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds");
    }

    private static String pseudoRandomTwoByteUtf8(int charCount) {
        val random  = new Random(7L);
        val builder = new StringBuilder(charCount);
        for (int i = 0; i < charCount; i++) {
            builder.append((char) (0x00A1 + random.nextInt(0x07FF - 0x00A1)));
        }
        return builder.toString();
    }

    @Test
    void whenBundleHasEntryWithLongNameThenThrowsException() throws IOException {
        val longName    = "a".repeat(256) + ".sapl";
        val bundleBytes = createBundleWithEntryAndConfig(longName, "policy \"test\" permit true;");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Entry name too long");
    }

    @Test
    void whenBundleContainsOnlyPdpJsonThenReturnsEmptyDocuments() throws IOException {
        val pdpJsonBundle = manifestedBundle(Map.of("pdp.json", """
                {"algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"ABSTAIN"}}
                """));

        val pdpJsonConfig = BundleParser.parse(pdpJsonBundle, TEST_PDP_ID, developmentPolicy);

        assertThat(pdpJsonConfig.saplDocuments()).isEmpty();
        assertThat(pdpJsonConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
    }

    @Test
    @DisplayName("a bundle with a Windows-style nested path is rejected")
    void whenBundleHasWindowsStylePathsThenRejected() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("windows\\style\\path.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        val bundleBytes = baos.toByteArray();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Unexpected file")
                .hasMessageContaining("windows/style/path.sapl");
    }

    @Test
    void whenParsingNonExistentFileThenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent.saplbundle");

        assertThatThrownBy(() -> BundleParser.parse(nonExistentPath, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to read bundle file");
    }

    @Test
    void whenParsingInvalidZipDataThenThrowsExceptionForMissingManifest() {
        val invalidData = "This is not a ZIP file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> BundleParser.parse(invalidData, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining(".sapl-manifest.json");
    }

    @Test
    @DisplayName("a non-.sapl file at the bundle root is rejected")
    void whenBundleContainsNonSaplRootFileThenRejected() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("policy.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("This is a readme".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        val bundleBytes = baos.toByteArray();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Unexpected file")
                .hasMessageContaining("readme.txt");
    }

    @Test
    @DisplayName("a file inside a subdirectory is rejected")
    void whenBundleContainsSubdirectoryFileThenRejected() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("nested/policy.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        val bundleBytes = baos.toByteArray();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Unexpected file")
                .hasMessageContaining("nested/policy.sapl");
    }

    @Test
    @DisplayName("a critical-extensions.json entry is an allowed bundle file")
    void whenCriticalExtensionsFilePresentThenAccepted() throws IOException {
        val bundleBytes = manifestedBundle(Map.of("pdp.json", DEFAULT_PDP_JSON, "critical-extensions.json", "[]"));

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.criticalExtensions()).isEmpty();
    }

    @Test
    @DisplayName("a bundle mixing sealed and plaintext secrets files is rejected")
    void whenBundleMixesSealingThenRejected() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);
            zos.putNextEntry(new ZipEntry("secrets.sealed.json"));
            zos.write("""
                    { "apiKey": "ENC[ciphertext]" }""".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("ext-upstreams-secrets.json"));
            zos.write("""
                    { "apiKey": "cleartext" }""".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        val bundleBytes = baos.toByteArray();

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("mixes sealed and plaintext");
    }

    @Test
    @DisplayName("a sealed-named entry with plaintext content is rejected")
    void whenSealedNamedEntryHasPlaintextThenRejected() throws IOException {
        val bundleBytes = createBundleWithEntryAndConfig("secrets.sealed.json", """
                { "apiKey": "cleartext" }""");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("content is not sealed");
    }

    @Test
    @DisplayName("a bundle's secrets file is loaded into the PDP data")
    void whenBundleHasSecretsFileThenLoaded() throws IOException {
        val bundleBytes = manifestedBundle(Map.of("pdp.json", DEFAULT_PDP_JSON, "secrets.sealed.json", """
                { "apiKey": "ENC[ciphertext]" }"""), "recipient-key");

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.data().secrets()).containsKey("apiKey");
    }

    @Test
    @DisplayName("a critical extension without any payload is rejected at parse time")
    void whenCriticalExtensionMissingPayloadThenRejected() throws IOException {
        val bundleBytes = manifestedBundle(Map.of("pdp.json", DEFAULT_PDP_JSON, "critical-extensions.json", """
                ["upstreams"]"""));

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Critical extension 'upstreams'");
    }

    private static byte[] manifestedBundle(Map<String, String> files) throws IOException {
        return manifestedBundle(files, null);
    }

    private static byte[] manifestedBundle(Map<String, String> files, String sealingRecipient) throws IOException {
        val hashes = new TreeMap<String, String>();
        for (val entry : files.entrySet()) {
            hashes.put(entry.getKey(), BundleManifest.computeHash(entry.getValue()));
        }
        val manifest = BundleManifest.builder().configurationId(TEST_CONFIG_ID)
                .attribution(BundleManifest.attributionOfText("bundle-parser-tests")).audience(sealingRecipient)
                .files(hashes).buildUnsigned();

        val allEntries = new LinkedHashMap<String, String>(files);
        allEntries.put(BundleManifest.MANIFEST_FILENAME, manifest.toJson());
        return rawZip(allEntries);
    }

    private static byte[] rawZip(Map<String, String> entries) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            for (val entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private void addPdpJsonEntry(ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry("pdp.json"));
        zos.write(DEFAULT_PDP_JSON.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private byte[] createBundleWithNestedDirectory() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("root.sapl"));
            zos.write("policy \"root\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("subdir/nested.sapl"));
            zos.write("policy \"nested\" deny true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithEntryAndConfig(String entryName, String content) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithManyEntries(int count) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            for (int i = 0; i < count; i++) {
                zos.putNextEntry(new ZipEntry("policy" + i + ".sapl"));
                zos.write(("policy \"p" + i + "\" permit true;").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

}
