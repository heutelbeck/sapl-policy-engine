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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class BundlePDPConfigurationSourceTests {

    private static final CombiningAlgorithm PERMIT_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES     = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_UNLESS_DENY = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);

    private static KeyPair              elderKeyPair;
    private static BundleSecurityPolicy developmentPolicy;
    private static BundleSecurityPolicy signedPolicy;

    @TempDir
    Path tempDir;

    private BundlePDPConfigurationSource source;

    @BeforeAll
    static void setupSecurityPolicies() throws NoSuchAlgorithmException {
        val generator = KeyPairGenerator.getInstance("Ed25519");
        elderKeyPair = generator.generateKeyPair();

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();

        signedPolicy = BundleSecurityPolicy.requireSignature(elderKeyPair.getPublic());
    }

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.dispose();
        }
    }

    @Test
    void whenLoadingSingleBundle_thenCallbackIsInvokedWithDerivedPdpId() throws IOException {
        createBundle(tempDir.resolve("necronomicon.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "necronomicon-v1" }
                        """,
                "forbidden.sapl", "policy \"forbidden\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("necronomicon");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingMultipleBundles_thenCallbackIsInvokedForEach() throws IOException {
        createBundle(tempDir.resolve("rlyeh.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "rlyeh-v1" }
                        """,
                "cthulhu.sapl", "policy \"cthulhu\" deny true;");

        createBundle(tempDir.resolve("yuggoth.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "PERMIT", "errorHandling": "ABSTAIN" }, "configurationId": "yuggoth-v1" }
                        """,
                "migo.sapl", "policy \"migo\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("rlyeh", "yuggoth");

        val rlyehConfig = configs.stream().filter(c -> "rlyeh".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(rlyehConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        val yuggothConfig = configs.stream().filter(c -> "yuggoth".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(yuggothConfig.combiningAlgorithm()).isEqualTo(PERMIT_UNLESS_DENY);
    }

    @Test
    void whenDirectoryDoesNotExist_thenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(nonExistentPath, developmentPolicy, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("does not exist");
    }

    @Test
    void whenPathIsNotADirectory_thenThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "content");

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(file, developmentPolicy, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenEmptyDirectory_thenCallbackIsNotInvoked() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).isEmpty();
    }

    @Test
    void whenBundleHasExplicitConfigurationId_thenConfigurationIdIsUsed() throws IOException {
        createBundle(tempDir.resolve("arkham.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "eldritch-bundle-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-bundle-v1");
    }

    @Test
    void whenNoPdpJsonInBundle_thenBundleIsSkipped() throws IOException {
        createBundleWithoutPdpJson(tempDir.resolve("miskatonic.saplbundle"), "policy.sapl",
                "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        // Bundles without pdp.json are now skipped (they're invalid)
        assertThat(configs).isEmpty();
    }

    @Test
    void whenBundleIsModified_thenCallbackIsInvokedAgain() throws IOException {
        createBundle(tempDir.resolve("innsmouth.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "innsmouth-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        createBundle(tempDir.resolve("innsmouth.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "innsmouth-v2" }
                        """,
                "policy.sapl", "policy \"updated\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        });
    }

    @Test
    void whenBundleContainsNestedArchive_thenBundleIsSkippedAndCallbackNotInvoked() throws IOException {
        val bundlePath = tempDir.resolve("malicious.saplbundle");
        createBundleWithNestedArchive(bundlePath);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // Malicious bundles are logged but don't throw - this allows other bundles to
        // load
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

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
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        // No valid configurations should be loaded from the malicious bundle
        assertThat(configs).isEmpty();
    }

    @Test
    void whenBundleContainsNestedDirectories_thenNestedFilesAreSkipped() throws IOException {
        val bundlePath = tempDir.resolve("nested.saplbundle");
        createBundleWithNestedDirectory(bundlePath);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
        assertThat(configs.getFirst().saplDocuments().getFirst()).contains("root-policy");
    }

    @Test
    void whenDisposeIsCalled_thenIsDisposedReturnsTrue() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, config -> {});

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwice_thenIsIdempotent() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v2" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, config -> {});

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenSymlinkDirectoryProvided_thenThrowsException() throws IOException {
        val realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);

        val linkDir = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(linkDir, developmentPolicy, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("symbolic link");
    }

    @Test
    void whenSymlinkBundleFilePresent_thenItIsSkipped() throws IOException {
        createBundle(tempDir.resolve("real.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "real-v1" }
                        """,
                "policy.sapl", "policy \"real\" permit true;");

        val target = tempDir.resolve("real.saplbundle");
        val link   = tempDir.resolve("link.saplbundle");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        // Only the real bundle should be loaded, symlink should be skipped
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("real");
    }

    @Test
    void whenNewBundleIsAdded_thenCallbackIsInvoked() throws IOException {
        createBundle(tempDir.resolve("initial.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "initial-v1" }
                        """,
                "policy.sapl", "policy \"initial\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);

        createBundle(tempDir.resolve("added.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "added-v1" }
                        """,
                "policy.sapl", "policy \"added\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs).hasSizeGreaterThanOrEqualTo(2);
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
            assertThat(pdpIds).contains("added");
        });
    }

    @Test
    void whenBundleIsDeleted_thenNoExceptionIsThrown() throws IOException {
        val bundlePath = tempDir.resolve("deletable.saplbundle");
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "deletable-v1" }
                        """,
                "policy.sapl", "policy \"deletable\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);

        Files.delete(bundlePath);

        // Wait and verify the source is still operational
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600)).untilAsserted(() -> {
            assertThat(source.isDisposed()).isFalse();
        });
    }

    @Test
    void whenNonBundleFilesPresent_thenTheyAreIgnored() throws IOException {
        createBundle(tempDir.resolve("valid.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "valid-v1" }
                        """,
                "policy.sapl", "policy \"valid\" permit true;");

        Files.writeString(tempDir.resolve("readme.txt"), "This should be ignored.");
        Files.writeString(tempDir.resolve("security.json"), "{}");
        Files.writeString(tempDir.resolve("bundle.zip"), "Not a real zip");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
    }

    @Test
    void whenBundleWithVariables_thenVariablesAreLoaded() throws IOException {
        createBundleWithVariables(tempDir.resolve("cultist.saplbundle"));

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().variables()).containsKey("realm");
        assertThat(configs.getFirst().variables()).containsKey("accessLevel");
    }

    @Test
    void whenManyBundles_thenAllAreLoaded() throws IOException {
        for (int i = 0; i < 10; i++) {
            createBundle(tempDir.resolve("tenant" + i + ".saplbundle"),
                    """
                            { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "tenant%d-v1" }
                            """
                            .formatted(i),
                    "policy.sapl", "policy \"tenant%d\" permit subject.tenant == %d;".formatted(i, i));
        }

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(10);
    }

    private void createBundle(Path bundlePath, String pdpJson, String saplFileName, String saplContent)
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
        Files.write(bundlePath, baos.toByteArray());
    }

    private void createBundleWithoutPdpJson(Path bundlePath, String saplFileName, String saplContent)
            throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
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
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write(
                    "{\"algorithm\":{\"votingMode\":\"PRIORITY_DENY\",\"defaultDecision\":\"DENY\",\"errorHandling\":\"PROPAGATE\"},\"configurationId\":\"nested-v1\"}"
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

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

    @Test
    void whenBundleChangesAfterDispose_thenTheyAreIgnored() throws IOException {
        val bundlePath = tempDir.resolve("disposable.saplbundle");
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v3" }
                        """,
                "policy.sapl", "policy \"original\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);

        source.dispose();

        // Modify existing bundle and add new bundle after dispose
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v4" }
                        """,
                "policy.sapl", "policy \"modified\" deny true;");
        createBundle(tempDir.resolve("new.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "new-v1" }
                        """,
                "policy.sapl", "policy \"new\" deny true;");

        // Wait - changes should be ignored
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(configs).hasSize(1);
        });
    }

    @Test
    void whenBundleHasInvalidName_thenItIsSkipped() throws IOException {
        // Create bundle with valid name
        createBundle(tempDir.resolve("valid.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "valid-v2" }
                        """,
                "policy.sapl", "policy \"valid\" permit true;");

        // Create bundle with invalid name (spaces)
        createBundle(tempDir.resolve("invalid name.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "invalid-v1" }
                        """,
                "policy.sapl", "policy \"invalid\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        // Only valid bundle should be loaded
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
    }

    @Test
    void whenBundleIsCorrupt_thenItIsSkipped() throws IOException {
        createBundle(tempDir.resolve("valid.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "valid-v3" }
                        """,
                "policy.sapl", "policy \"valid\" permit true;");

        // Create corrupt "bundle" that's not a valid ZIP
        Files.writeString(tempDir.resolve("corrupt.saplbundle"), "This is not a ZIP file!");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        // Only valid bundle is loaded - corrupt one is skipped because it has no
        // pdp.json
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenSymlinkBundleAddedAfterStart_thenItIsIgnored(@TempDir Path externalDir) throws IOException {
        createBundle(tempDir.resolve("initial.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "initial-v2" }
                        """,
                "policy.sapl", "policy \"initial\" permit true;");

        // Create target bundle OUTSIDE the watched directory
        val targetBundle = externalDir.resolve("target.saplbundle");
        createBundle(targetBundle,
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "target-v1" }
                        """,
                "policy.sapl", "policy \"target\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("initial");

        // Try to add a symlink bundle pointing to the external target
        val link = tempDir.resolve("link.saplbundle");
        try {
            Files.createSymbolicLink(link, targetBundle);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        // Wait for file watcher - symlink should be detected but ignored
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600)).untilAsserted(() -> {
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).distinct().toList();
            assertThat(pdpIds).doesNotContain("link");
        });
    }

    @Test
    void whenSecurityPolicyIsNull_thenThrowsException() {
        assertThatThrownBy(() -> new BundlePDPConfigurationSource(tempDir, null, config -> {}))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("Security policy");
    }

    @Test
    void whenSecurityPolicyDisabledWithoutRiskAcceptance_thenThrowsException() {
        val invalidPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        assertThatThrownBy(() -> new BundlePDPConfigurationSource(tempDir, invalidPolicy, config -> {}))
                .isInstanceOf(BundleSignatureException.class).hasMessageContaining("risk acceptance");
    }

    @Test
    void whenLoadingSignedBundleWithCorrectKey_thenSucceeds() throws IOException {
        val signedBundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                .withPolicy("signed.sapl", "policy \"signed\" permit true")
                .signWith(elderKeyPair.getPrivate(), "test-key").build();

        Files.write(tempDir.resolve("signed.saplbundle"), signedBundle);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, signedPolicy, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("signed");
    }

    @Test
    void whenLoadingUnsignedBundleWithRequiredSignature_thenBundleIsSkipped() throws IOException {
        createBundle(tempDir.resolve("unsigned.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "unsigned-v1" }
                        """,
                "policy.sapl", "policy \"unsigned\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new BundlePDPConfigurationSource(tempDir, signedPolicy, configs::add);

        // Unsigned bundle should be skipped when signature is required
        assertThat(configs).isEmpty();
    }

    private void createBundleWithVariables(Path bundlePath) throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write(
                    """
                            {
                              "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" },
                              "configurationId": "cultist-v1",
                              "variables": {
                                "realm": "arkham",
                                "accessLevel": 5
                              }
                            }
                            """
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("policy.sapl"));
            zos.write("policy \"cultist\" permit subject.realm == realm;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(bundlePath, baos.toByteArray());
    }

}
