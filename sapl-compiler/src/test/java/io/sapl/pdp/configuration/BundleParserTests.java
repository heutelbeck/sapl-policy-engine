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

import io.sapl.api.pdp.CombiningAlgorithm;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    private static final String TEST_CONFIG_ID = "v1.0";

    @TempDir
    Path tempDir;

    @Test
    void whenParsingValidBundle_thenExtractsAllContent() throws IOException {
        val bundleBytes = createBundle("""
                { "algorithm": "PERMIT_OVERRIDES" }
                """, "elder-sign.sapl", "policy \"elder-sign\" permit true;");

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.pdpId()).isEqualTo(TEST_PDP_ID);
        assertThat(config.configurationId()).isEqualTo(TEST_CONFIG_ID);
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingBundleWithoutPdpJson_thenUsesDefaultAlgorithm() throws IOException {
        val bundleBytes = createBundle(null, "cultist.sapl", "policy \"cultist\" deny true;");

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingBundleWithMultiplePolicies_thenAllPoliciesExtracted() throws IOException {
        val bundleBytes = createBundleWithMultiplePolicies();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).hasSize(3);
    }

    @Test
    void whenParsingFromPath_thenConfigurationIsExtracted() throws IOException {
        val bundlePath = tempDir.resolve("test.saplbundle");
        Files.write(bundlePath, createBundle("""
                { "algorithm": "DENY_OVERRIDES" }
                """, "shoggoth.sapl", "policy \"shoggoth\" deny true;"));

        val config = BundleParser.parse(bundlePath, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingFromInputStream_thenConfigurationIsExtracted() throws IOException {
        val bundleBytes = createBundle(null, "dagon.sapl", "policy \"dagon\" permit true;");
        val inputStream = new ByteArrayInputStream(bundleBytes);

        val config = BundleParser.parse(inputStream, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingFromInputStreamWithSize_thenConfigurationIsExtracted() throws IOException {
        val bundleBytes = createBundle(null, "nyarlathotep.sapl", "policy \"nyarlathotep\" deny true;");
        val inputStream = new ByteArrayInputStream(bundleBytes);

        val config = BundleParser.parse(inputStream, bundleBytes.length, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenParsingBundleWithAlgorithm_thenAlgorithmIsApplied() throws IOException {
        val bundleBytes = createBundle("""
                { "algorithm": "ONLY_ONE_APPLICABLE" }
                """, "yog-sothoth.sapl", "policy \"yog-sothoth\" permit true;");

        val config = BundleParser.parse(bundleBytes, "outer-gods", "v1.0");

        assertThat(config.pdpId()).isEqualTo("outer-gods");
        assertThat(config.configurationId()).isEqualTo("v1.0");
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.ONLY_ONE_APPLICABLE);
        assertThat(config.saplDocuments()).hasSize(1);
    }

    @Test
    void whenBundleContainsNestedDirectories_thenNestedFilesAreSkipped() throws IOException {
        val bundleBytes = createBundleWithNestedDirectory();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("pathTraversalAttempts")
    void whenBundleContainsPathTraversal_thenThrowsException(String maliciousPath) throws IOException {
        val bundleBytes = createBundleWithEntry(maliciousPath, "malicious content");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Path traversal");
    }

    static Stream<Arguments> pathTraversalAttempts() {
        return Stream.of(arguments("../../../etc/passwd"), arguments("..\\..\\windows\\system32\\config"),
                arguments("/etc/passwd"), arguments("\\windows\\system32"));
    }

    @ParameterizedTest
    @MethodSource("nestedArchiveExtensions")
    void whenBundleContainsNestedArchive_thenThrowsException(String archiveName) throws IOException {
        val bundleBytes = createBundleWithEntry(archiveName, "PK\003\004");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Nested archive");
    }

    static Stream<Arguments> nestedArchiveExtensions() {
        return Stream.of(arguments("nested.zip"), arguments("nested.saplbundle"), arguments("nested.jar"),
                arguments("nested.war"), arguments("NESTED.ZIP"), arguments("archive.JAR"));
    }

    @Test
    void whenBundleHasTooManyEntries_thenThrowsException() throws IOException {
        val bundleBytes = createBundleWithManyEntries(1001);

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Too many entries");
    }

    @Test
    void whenBundleHasEntryWithLongName_thenThrowsException() throws IOException {
        val longName    = "a".repeat(256) + ".sapl";
        val bundleBytes = createBundleWithEntry(longName, "policy \"test\" permit true;");

        assertThatThrownBy(() -> BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Entry name too long");
    }

    @Test
    void whenBundleContentIsEmpty_thenReturnsEmptyConfiguration() throws IOException {
        val bundleBytes = createEmptyBundle();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).isEmpty();
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
    }

    @Test
    void whenBundleContainsOnlyPdpJson_thenReturnsConfigurationWithNoDocuments() throws IOException {
        val bundleBytes = createBundleWithOnlyPdpJson();

        val config = BundleParser.parse(bundleBytes, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).isEmpty();
        assertThat(config.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
    }

    @Test
    void whenBundleHasWindowsStylePaths_thenNestedPathsAreSkipped() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("windows\\style\\path.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        val config = BundleParser.parse(baos.toByteArray(), TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).isEmpty();
    }

    @Test
    void whenParsingNonExistentFile_thenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent.saplbundle");

        assertThatThrownBy(() -> BundleParser.parse(nonExistentPath, TEST_PDP_ID, TEST_CONFIG_ID))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to read bundle file");
    }

    @Test
    void whenParsingInvalidZipData_thenReturnsEmptyConfiguration() {
        val invalidData = "This is not a ZIP file".getBytes(StandardCharsets.UTF_8);

        val config = BundleParser.parse(invalidData, TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).isEmpty();
    }

    @Test
    void whenBundleContainsNonSaplFiles_thenTheyAreIgnored() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("policy.sapl"));
            zos.write("policy \"test\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("This is a readme".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("config.xml"));
            zos.write("<config/>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        val config = BundleParser.parse(baos.toByteArray(), TEST_PDP_ID, TEST_CONFIG_ID);

        assertThat(config.saplDocuments()).hasSize(1);
    }

    private byte[] createBundle(String pdpJson, String saplFileName, String saplContent) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            if (pdpJson != null) {
                zos.putNextEntry(new ZipEntry("pdp.json"));
                zos.write(pdpJson.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            zos.putNextEntry(new ZipEntry(saplFileName));
            zos.write(saplContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithMultiplePolicies() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
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

    private byte[] createBundleWithEntry(String entryName, String content) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithManyEntries(int count) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < count; i++) {
                zos.putNextEntry(new ZipEntry("policy" + i + ".sapl"));
                zos.write(("policy \"p" + i + "\" permit true;").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyBundle() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            // Empty ZIP
        }
        return baos.toByteArray();
    }

    private byte[] createBundleWithOnlyPdpJson() throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write("{ \"algorithm\": \"DENY_OVERRIDES\" }".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
