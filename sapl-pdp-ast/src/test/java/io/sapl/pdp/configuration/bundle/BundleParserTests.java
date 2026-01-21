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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BundleParserTests {

    private static final String TEST_PDP_ID    = "cthulhu-pdp";
    private static final String TEST_CONFIG_ID = "eldritch-v1";

    private static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm DENY_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    private static BundleSecurityPolicy developmentPolicy;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupSecurityPolicy() {
        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();
    }

    @Test
    void whenParsingValidBundle_thenExtractsAllContent() throws IOException {
        val bundleBytes = createBundleWithConfigId(
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" }, "configurationId": "%s" }
                        """
                        .formatted(TEST_CONFIG_ID),
                "elder-sign.sapl", "policy \"elder-sign\" permit true;");

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.pdpId()).isEqualTo(TEST_PDP_ID);
        assertThat(config.configurationId()).isEqualTo(TEST_CONFIG_ID);
        assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingBundleWithoutPdpJson_thenThrowsException() throws IOException {
        val bundleBytes = createBundleWithoutPdpJson("cultist.sapl", "policy \"cultist\" deny true;");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("pdp.json");
    }

    @Test
    void whenParsingBundleWithoutConfigurationId_thenThrowsException() throws IOException {
        val bundleBytes = createBundleWithConfigId(
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                        """,
                "cultist.sapl", "policy \"cultist\" deny true;");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("configurationId");
    }

    @Test
    void whenParsingBundleWithMultiplePolicies_thenAllPoliciesExtracted() throws IOException {
        val bundleBytes = createBundleWithMultiplePolicies();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(3);
    }

    @Test
    void whenParsingFromPath_thenConfigurationIsExtracted() throws IOException {
        val bundlePath = tempDir.resolve("test.saplbundle");
        Files.write(bundlePath,
                createBundleWithConfigId(
                        """
                                { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" }, "configurationId": "%s" }
                                """
                                .formatted(TEST_CONFIG_ID),
                        "shoggoth.sapl", "policy \"shoggoth\" deny true;"));

        val config = BundleParser.parse(bundlePath, TEST_PDP_ID, developmentPolicy);

        assertThat(config.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @ParameterizedTest(name = "parse from InputStream with size known = {0}")
    @ValueSource(booleans = { true, false })
    void whenParsingFromInputStream_thenConfigurationIsExtracted(boolean sizeKnown) throws IOException {
        val bundleBytes = createBundleWithConfigId("""
                { "configurationId": "%s" }
                """.formatted(TEST_CONFIG_ID), "dagon.sapl", "policy \"dagon\" permit true;");
        val inputStream = new ByteArrayInputStream(bundleBytes);

        val config = sizeKnown ? BundleParser.parse(inputStream, bundleBytes.length, TEST_PDP_ID, developmentPolicy)
                : BundleParser.parse(inputStream, TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenBundleContainsNestedDirectories_thenNestedFilesAreSkipped() throws IOException {
        val bundleBytes = createBundleWithNestedDirectory();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("pathTraversalAttempts")
    void whenBundleContainsPathTraversal_thenThrowsException(String maliciousPath) throws IOException {
        val bundleBytes = createBundleWithEntryAndConfig(maliciousPath, "malicious content");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Path traversal");
    }

    static Stream<Arguments> pathTraversalAttempts() {
        return Stream.of(arguments("../../../etc/passwd"), arguments("..\\..\\windows\\system32\\security"),
                arguments("/etc/passwd"), arguments("\\windows\\system32"));
    }

    @ParameterizedTest
    @MethodSource("nestedArchiveExtensions")
    void whenBundleContainsNestedArchive_thenThrowsException(String archiveName) throws IOException {
        val bundleBytes = createBundleWithEntryAndConfig(archiveName, "PK\003\004");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Nested archive");
    }

    static Stream<Arguments> nestedArchiveExtensions() {
        return Stream.of(arguments("nested.zip"), arguments("nested.saplbundle"), arguments("nested.jar"),
                arguments("nested.war"), arguments("NESTED.ZIP"), arguments("archive.JAR"));
    }

    @Test
    void whenBundleHasTooManyEntries_thenThrowsException() throws IOException {
        val bundleBytes = createBundleWithManyEntries(1001);

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Too many entries");
    }

    @Test
    void whenBundleExceedsCompressionRatio_thenThrowsException() throws IOException {
        val largeRepetitiveContent = "A".repeat(50_000);
        val bundleBytes            = createBundleWithConfigId("""
                { "configurationId": "%s" }
                """.formatted(TEST_CONFIG_ID), "eldritch-tome.sapl",
                "policy \"forbidden-knowledge\" permit true; /* " + largeRepetitiveContent + " */");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Compression ratio");
    }

    @Test
    void whenUncompressedSizeExceedsLimit_thenThrowsException() throws IOException {
        val hugeContent = "X".repeat(11 * 1024 * 1024);
        val bundleBytes = createBundleWithEntryAndConfig("necronomicon.sapl", hugeContent);

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds");
    }

    @Test
    void whenBundleHasEntryWithLongName_thenThrowsException() throws IOException {
        val longName    = "a".repeat(256) + ".sapl";
        val bundleBytes = createBundleWithEntryAndConfig(longName, "policy \"test\" permit true;");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Entry name too long");
    }

    @Test
    void whenBundleContainsOnlyPdpJson_thenReturnsEmptyDocuments() throws IOException {
        val pdpJsonBundle = createBundleWithOnlyPdpJson();

        val pdpJsonConfig = BundleParser.parse(pdpJsonBundle, TEST_PDP_ID, developmentPolicy);

        assertThat(pdpJsonConfig.saplDocuments()).isEmpty();
        assertThat(pdpJsonConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
    }

    @Test
    void whenBundleHasWindowsStylePaths_thenNestedPathsAreSkipped() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("windows\\style\\path.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        val config = BundleParser.parse(baos.toByteArray(), TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).isEmpty();
    }

    @Test
    void whenParsingNonExistentFile_thenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent.saplbundle");

        assertThatThrownBy(() -> BundleParser.parse(nonExistentPath, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to read bundle file");
    }

    @Test
    void whenParsingInvalidZipData_thenThrowsExceptionForMissingPdpJson() {
        val invalidData = "This is not a ZIP file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> BundleParser.parse(invalidData, TEST_PDP_ID, developmentPolicy))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("pdp.json");
    }

    @Test
    void whenBundleContainsNonSaplFiles_thenTheyAreIgnored() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            zos.putNextEntry(new ZipEntry("policy.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("This is a readme".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("security.xml"));
            zos.write("<security/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        val config = BundleParser.parse(baos.toByteArray(), TEST_PDP_ID, developmentPolicy);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    private byte[] createBundleWithConfigId(String pdpJson, String saplFileName, String saplContent)
            throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write(pdpJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(saplFileName));
            zos.write(saplContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithoutPdpJson(String saplFileName, String saplContent) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(saplFileName));
            zos.write(saplContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private void addPdpJsonEntry(ZipOutputStream zos) throws IOException {
        val pdpJson = "{\"configurationId\":\"%s\"}".formatted(TEST_CONFIG_ID);
        zos.putNextEntry(new ZipEntry("pdp.json"));
        zos.write(pdpJson.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private byte[] createBundleWithMultiplePolicies() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            addPdpJsonEntry(zos);

            for (String name : new String[] { "access.sapl", "audit.sapl", "logging.sapl" }) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(("policy \"" + name + "\" permit true;").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
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

    private byte[] createBundleWithOnlyPdpJson() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            val pdpJson = """
                    {"algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"ABSTAIN"},"configurationId":"%s"}
                    """
                    .formatted(TEST_CONFIG_ID);
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write(pdpJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

}
