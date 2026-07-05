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
package io.sapl.pdp.configuration.source;

import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DisplayName("BundlePDPConfigurationSource")
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

        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();

        signedPolicy = BundleSecurityPolicy.builder(elderKeyPair.getPublic()).build();
    }

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.close();
        }
    }

    private List<PDPConfiguration> captureConfigurations(PDPConfigurationSource src) {
        val capture = new CapturingSubscriber();
        src.subscribe(capture);
        return capture.configs();
    }

    @Test
    void whenLoadingSingleBundleThenVoterSourceReceivesConfigWithDerivedPdpId() throws IOException {
        createBundle(tempDir.resolve("necronomicon.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "necronomicon-v1" }
                        """,
                "forbidden.sapl", "policy \"forbidden\" deny true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("necronomicon");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingMultipleBundlesThenVoterSourceReceivesConfigForEach() throws IOException {
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
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("rlyeh", "yuggoth");

        val rlyehConfig = configs.stream().filter(c -> "rlyeh".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(rlyehConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        val yuggothConfig = configs.stream().filter(c -> "yuggoth".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(yuggothConfig.combiningAlgorithm()).isEqualTo(PERMIT_UNLESS_DENY);
    }

    @Test
    void whenDirectoryDoesNotExistThenSubscribeThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");
        source = new BundlePDPConfigurationSource(nonExistentPath, developmentPolicy);

        val capture = new CapturingSubscriber();

        assertThatThrownBy(() -> source.subscribe(capture)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void whenPathIsNotADirectoryThenSubscribeThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "content");
        source = new BundlePDPConfigurationSource(file, developmentPolicy);

        val capture = new CapturingSubscriber();

        assertThatThrownBy(() -> source.subscribe(capture)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void whenEmptyDirectoryThenVoterSourceNotInvoked() {
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        assertThat(captureConfigurations(source)).isEmpty();
    }

    @Test
    void whenBundleHasExplicitConfigurationIdThenConfigurationIdIsUsed() throws IOException {
        createBundle(tempDir.resolve("arkham.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "eldritch-bundle-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-bundle-v1");
    }

    @Test
    @DisplayName("a bundle without pdp.json is reported as a configuration error, not a configuration")
    void whenNoPdpJsonInBundleThenConfigurationErrorEmitted() throws IOException {
        createBundleWithoutPdpJson(tempDir.resolve("miskatonic.saplbundle"), "policy.sapl",
                "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);
        val capture = new CapturingSubscriber();
        source.subscribe(capture);

        assertThat(capture.configs()).isEmpty();
        assertThat(capture.errors()).singleElement()
                .satisfies(error -> assertThat(error.pdpId()).isEqualTo("miskatonic"));
    }

    @Test
    void whenBundleIsModifiedThenVoterSourceReceivesUpdatedConfig() throws IOException {
        createBundle(tempDir.resolve("innsmouth.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "innsmouth-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

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
    void whenOneSubscriberThrowsThenOthersStillReceiveReloads() throws IOException {
        createBundle(tempDir.resolve("dunwich.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "dunwich-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val recorder = new CapturingSubscriber();
        source.subscribe(recorder);
        // A throwing subscriber must not starve the others or kill the hot-reload
        // file-watch thread.
        source.subscribe(event -> {
            throw new IllegalStateException("subscriber boom");
        });

        createBundle(tempDir.resolve("dunwich.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" }, "configurationId": "dunwich-v2" }
                        """,
                "policy.sapl", "policy \"updated\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(recorder.configs())
                .anyMatch(config -> config.combiningAlgorithm().equals(PERMIT_OVERRIDES)));
    }

    @Test
    void whenBundleContainsNestedArchiveThenBundleIsSkippedAndVoterSourceNotInvoked() throws IOException {
        val bundlePath = tempDir.resolve("malicious.saplbundle");
        createBundleWithNestedArchive(bundlePath);

        // Malicious bundles are logged but don't throw - this allows other bundles to
        // load
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        // No valid configurations should be loaded from the malicious bundle
        assertThat(captureConfigurations(source)).isEmpty();
    }

    @Test
    void whenBundleContainsPathTraversalThenBundleIsSkippedAndVoterSourceNotInvoked() throws IOException {
        val bundlePath = tempDir.resolve("malicious.saplbundle");
        createBundleWithPathTraversal(bundlePath);

        // Malicious bundles are logged but don't throw - this allows other bundles to
        // load
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        // No valid configurations should be loaded from the malicious bundle
        assertThat(captureConfigurations(source)).isEmpty();
    }

    @Test
    void whenBundleContainsNestedDirectoriesThenBundleIsRejected() throws IOException {
        val bundlePath = tempDir.resolve("nested.saplbundle");
        createBundleWithNestedDirectory(bundlePath);
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).isEmpty();
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);
        captureConfigurations(source);

        assertThat(source.isClosed()).isFalse();

        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() throws IOException {
        createBundle(tempDir.resolve("disposable.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v2" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);
        captureConfigurations(source);

        source.close();
        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    void whenSymlinkDirectoryProvidedThenItIsAccepted() throws IOException {
        val realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);
        createBundle(realDir.resolve("test.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "test-v1" }
                        """,
                "policy.sapl", "policy \"test\" permit true;");

        val linkDir = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }
        // Symlink directories are accepted for flexible deployment scenarios
        source = new BundlePDPConfigurationSource(linkDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
    }

    @Test
    void whenSymlinkBundleFilePresentThenItIsLoaded() throws IOException {
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
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        // Symlinks are followed for flexible deployment scenarios
        assertThat(configs).hasSize(2);
        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("real", "link");
    }

    @Test
    void whenNewBundleIsAddedThenVoterSourceReceivesConfig() throws IOException {
        createBundle(tempDir.resolve("initial.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "initial-v1" }
                        """,
                "policy.sapl", "policy \"initial\" permit true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

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
    void whenBundleIsDeletedThenConfigurationIsRemoved() throws IOException {
        val bundlePath = tempDir.resolve("deletable.saplbundle");
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "deletable-v1" }
                        """,
                "policy.sapl", "policy \"deletable\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val capture = new CapturingSubscriber();
        source.subscribe(capture);

        assertThat(capture.configs()).hasSize(1);

        Files.delete(bundlePath);

        // Wait for file watcher to detect deletion and verify configuration is removed
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600))
                .untilAsserted(() -> assertThat(capture.removedPdpIds()).contains("deletable"));
    }

    @Test
    void whenStemlessBundleIsDeletedThenNoEmptyRemovalIsEmitted() throws IOException {
        val bundlePath = tempDir.resolve(".saplbundle");
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "stemless-v1" }
                        """,
                "policy.sapl", "policy \"stemless\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val capture = new CapturingSubscriber();
        source.subscribe(capture);

        // The stemless name derives an empty pdpId, which the load path rejects,
        // so no configuration was ever loaded for it.
        assertThat(capture.configs()).isEmpty();

        Files.delete(bundlePath);

        // Deleting it must not emit a spurious Remove("") for a configuration that
        // was never loaded.
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(capture.removedPdpIds()).doesNotContain(""));
    }

    @Test
    void whenNonBundleFilesPresentThenTheyAreIgnored() throws IOException {
        createBundle(tempDir.resolve("valid.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "valid-v1" }
                        """,
                "policy.sapl", "policy \"valid\" permit true;");

        Files.writeString(tempDir.resolve("readme.txt"), "This should be ignored.");
        Files.writeString(tempDir.resolve("security.json"), "{}");
        Files.writeString(tempDir.resolve("bundle.zip"), "Not a real zip");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
    }

    @Test
    void whenBundleWithVariablesThenVariablesAreLoaded() throws IOException {
        createBundleWithVariables(tempDir.resolve("cultist.saplbundle"));
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().data().variables()).containsKey("realm");
        assertThat(configs.getFirst().data().variables()).containsKey("accessLevel");
    }

    @Test
    void whenManyBundlesThenAllAreLoaded() throws IOException {
        for (int i = 0; i < 10; i++) {
            createBundle(tempDir.resolve("tenant" + i + ".saplbundle"),
                    """
                            { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "tenant%d-v1" }
                            """
                            .formatted(i),
                    "policy.sapl", "policy \"tenant%d\" permit subject.tenant == %d;".formatted(i, i));
        }
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

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
    void whenBundleChangesAfterDisposeThenTheyAreIgnored() throws IOException {
        val bundlePath = tempDir.resolve("disposable.saplbundle");
        createBundle(bundlePath,
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "disposable-v3" }
                        """,
                "policy.sapl", "policy \"original\" permit true;");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);

        source.close();

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
    void whenBundleHasInvalidNameThenItIsSkipped() throws IOException {
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
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        // Only valid bundle should be loaded
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
    }

    @Test
    void whenBundleIsCorruptThenItIsSkipped() throws IOException {
        createBundle(tempDir.resolve("valid.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "valid-v3" }
                        """,
                "policy.sapl", "policy \"valid\" permit true;");

        // Create corrupt "bundle" that's not a valid ZIP
        Files.writeString(tempDir.resolve("corrupt.saplbundle"), "This is not a ZIP file!");
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        // Only valid bundle is loaded - corrupt one is skipped because it has no
        // pdp.json
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid");
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenSymlinkBundleAddedAfterStartThenItIsLoaded(@TempDir Path externalDir) throws IOException {
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
        source = new BundlePDPConfigurationSource(tempDir, developmentPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("initial");

        // Add a symlink bundle pointing to the external target
        val link = tempDir.resolve("link.saplbundle");
        try {
            Files.createSymbolicLink(link, targetBundle);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        // Wait for file watcher - symlink should be detected and loaded
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600)).untilAsserted(() -> {
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).distinct().toList();
            assertThat(pdpIds).contains("link");
        });
    }

    @Test
    void whenSecurityPolicyIsNullThenThrowsException() {
        assertThatThrownBy(() -> new BundlePDPConfigurationSource(tempDir, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("Security policy");
    }

    @Test
    void whenSecurityPolicyDisabledThenValidateSucceeds() throws IOException {
        val unsignedBundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                .withPolicy("test.sapl", "policy \"test\" permit true;").build();
        Files.write(tempDir.resolve("test.saplbundle"), unsignedBundle);

        val unsignedPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
        source = new BundlePDPConfigurationSource(tempDir, unsignedPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
    }

    @Test
    void whenLoadingSignedBundleWithCorrectKeyThenSucceeds() throws IOException {
        val signedBundle = BundleBuilder.create().withCombiningAlgorithm(DENY_OVERRIDES)
                .withPolicy("signed.sapl", "policy \"signed\" permit true;")
                .signWith(elderKeyPair.getPrivate(), "test-key").build();

        Files.write(tempDir.resolve("signed.saplbundle"), signedBundle);
        source = new BundlePDPConfigurationSource(tempDir, signedPolicy);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("signed");
    }

    @Test
    void whenLoadingUnsignedBundleWithRequiredSignatureThenBundleIsSkipped() throws IOException {
        createBundle(tempDir.resolve("unsigned.saplbundle"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "unsigned-v1" }
                        """,
                "policy.sapl", "policy \"unsigned\" permit true;");

        source = new BundlePDPConfigurationSource(tempDir, signedPolicy);

        // Unsigned bundle should be skipped when signature is required
        assertThat(captureConfigurations(source)).isEmpty();
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
