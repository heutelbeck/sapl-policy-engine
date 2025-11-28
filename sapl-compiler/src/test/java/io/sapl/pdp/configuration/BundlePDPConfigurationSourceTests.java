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
import io.sapl.api.pdp.PDPConfiguration;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class BundlePDPConfigurationSourceTests {

    @TempDir
    Path tempDir;

    private BundlePDPConfigurationSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.dispose();
        }
    }

    @Test
    void whenLoadingSingleBundle_thenCallbackIsInvokedWithDerivedPdpId() throws IOException {
        createBundle(tempDir.resolve("necronomicon.saplbundle"), """
                { "algorithm": "PERMIT_OVERRIDES" }
                """, "forbidden.sapl", "policy \"forbidden\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("necronomicon");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingMultipleBundles_thenCallbackIsInvokedForEach() throws IOException {
        createBundle(tempDir.resolve("rlyeh.saplbundle"), """
                { "algorithm": "DENY_OVERRIDES" }
                """, "cthulhu.sapl", "policy \"cthulhu\" deny true;");

        createBundle(tempDir.resolve("yuggoth.saplbundle"), """
                { "algorithm": "PERMIT_UNLESS_DENY" }
                """, "migo.sapl", "policy \"migo\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("rlyeh", "yuggoth");

        val rlyehConfig = configs.stream().filter(c -> "rlyeh".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(rlyehConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);

        val yuggothConfig = configs.stream().filter(c -> "yuggoth".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(yuggothConfig.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_UNLESS_DENY);
    }

    @Test
    void whenDirectoryDoesNotExist_thenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(nonExistentPath, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("does not exist");
    }

    @Test
    void whenPathIsNotADirectory_thenThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "content");

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(file, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenEmptyDirectory_thenCallbackIsNotInvoked() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).isEmpty();
    }

    @Test
    void whenProvidingCustomConfigurationId_thenConfigurationIdIsSet() throws IOException {
        createBundle(tempDir.resolve("arkham.saplbundle"), null, "policy.sapl", "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, "eldritch-bundle-v1", configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-bundle-v1");
    }

    @Test
    void whenNoPdpJsonInBundle_thenUsesDefaultAlgorithm() throws IOException {
        createBundle(tempDir.resolve("miskatonic.saplbundle"), null, "policy.sapl", "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
    }

    @Test
    void whenBundleIsModified_thenCallbackIsInvokedAgain() throws IOException {
        createBundle(tempDir.resolve("innsmouth.saplbundle"), """
                { "algorithm": "DENY_OVERRIDES" }
                """, "policy.sapl", "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);

        createBundle(tempDir.resolve("innsmouth.saplbundle"), """
                { "algorithm": "PERMIT_OVERRIDES" }
                """, "policy.sapl", "policy \"updated\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        });
    }

    @Test
    void whenBundleContainsNestedArchive_thenBundleIsSkippedAndCallbackNotInvoked() throws IOException {
        val bundlePath = tempDir.resolve("malicious.saplbundle");
        createBundleWithNestedArchive(bundlePath);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // Malicious bundles are logged but don't throw - this allows other bundles to
        // load
        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        // No valid configurations should be loaded from the malicious bundle
        assertThat(configs).isEmpty();
    }

    @Test
    void whenBundleContainsPathTraversal_thenBundleIsSkippedAndCallbackNotInvoked() throws IOException {
        val bundlePath = tempDir.resolve("malicious.saplbundle");
        createBundleWithPathTraversal(bundlePath);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // Malicious bundles are logged but don't throw - this allows other bundles to
        // load
        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        // No valid configurations should be loaded from the malicious bundle
        assertThat(configs).isEmpty();
    }

    @Test
    void whenBundleContainsNestedDirectories_thenNestedFilesAreSkipped() throws IOException {
        val bundlePath = tempDir.resolve("nested.saplbundle");
        createBundleWithNestedDirectory(bundlePath);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
        assertThat(configs.getFirst().saplDocuments().getFirst()).contains("root-policy");
    }

    @Test
    void whenDisposeIsCalled_thenIsDisposedReturnsTrue() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"), null, "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, config -> {});

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwice_thenIsIdempotent() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"), null, "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, config -> {});

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    private void createBundle(Path bundlePath, String pdpJson, String saplFileName, String saplContent)
            throws IOException {
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
        Files.write(bundlePath, baos.toByteArray());
    }

    private void createBundleWithNestedArchive(Path bundlePath) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("nested.zip"));
            zos.write(new byte[] { 0x50, 0x4B, 0x03, 0x04 });
            zos.closeEntry();
        }
        Files.write(bundlePath, baos.toByteArray());
    }

    private void createBundleWithPathTraversal(Path bundlePath) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("../../../etc/passwd"));
            zos.write("malicious".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(bundlePath, baos.toByteArray());
    }

    private void createBundleWithNestedDirectory(Path bundlePath) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("root.sapl"));
            zos.write("policy \"root-policy\" permit true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("subdir/nested.sapl"));
            zos.write("policy \"nested-policy\" deny true;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(bundlePath, baos.toByteArray());
    }

}
