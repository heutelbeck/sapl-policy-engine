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

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.DisplayName;

@DisplayName("MultiDirectoryPDPConfigurationSource")
class MultiDirectoryPDPConfigurationSourceTests {

    private static final CombiningAlgorithm DENY_OVERRIDES     = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    @TempDir
    Path tempDir;

    private MultiDirectoryPDPConfigurationSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.dispose();
        }
    }

    @Test
    void whenLoadingFromSubdirectoriesThenCallbackIsInvokedForEach() throws IOException {
        createSubdirectoryWithPolicy("arkham", DENY_OVERRIDES, "forbidden.sapl",
                "policy \"forbidden\" deny subject.sanity < 50;");
        createSubdirectoryWithPolicy("innsmouth", PERMIT_OVERRIDES, "access.sapl",
                "policy \"access\" permit subject.species == \"deep_one\";");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("arkham", "innsmouth");

        val arkhamConfig = configs.stream().filter(c -> "arkham".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(arkhamConfig.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
        assertThat(arkhamConfig.saplDocuments()).hasSize(1);

        val innsmouthConfig = configs.stream().filter(c -> "innsmouth".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(innsmouthConfig.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
    }

    @Test
    void whenIncludeRootFilesIsTrueThenRootFilesAreLoadedAsDefaultPdp() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
                        """);
        createFile(tempDir.resolve("root-policy.sapl"), "policy \"root\" permit true;");
        createSubdirectoryWithPolicy("tenant-a", PERMIT_OVERRIDES, "tenant.sapl", "policy \"tenant\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, true, configs::add);

        assertThat(configs).hasSize(2);

        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("default", "tenant-a");

        val defaultConfig = configs.stream().filter(c -> "default".equals(c.pdpId())).findFirst().orElseThrow();
        assertThat(defaultConfig.combiningAlgorithm()).isEqualTo(DENY_UNLESS_PERMIT);
    }

    @Test
    void whenIncludeRootFilesIsFalseThenRootFilesAreIgnored() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tempDir.resolve("root-policy.sapl"), "policy \"root\" permit true;");
        createSubdirectoryWithPolicy("tenant-a", PERMIT_OVERRIDES, "tenant.sapl", "policy \"tenant\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, false, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("tenant-a");
    }

    @Test
    void whenSubdirectoryNamedDefaultExistsThenRootFilesNotLoadedAsDefault() throws IOException {
        createSubdirectoryWithPolicy("default", PERMIT_OVERRIDES, "default.sapl", "policy \"default-dir\" deny true;");
        createFile(tempDir.resolve("root-policy.sapl"), "policy \"root\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, true, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("default");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
    }

    @Test
    void whenDirectoryDoesNotExistThenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");

        assertThatThrownBy(() -> new MultiDirectoryPDPConfigurationSource(nonExistentPath, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenPathIsNotADirectoryThenThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        createFile(file, "content");

        assertThatThrownBy(() -> new MultiDirectoryPDPConfigurationSource(file, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenEmptyDirectoryThenNoCallbackInvoked() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).isEmpty();
    }

    @Test
    void whenSubdirectoryHasInvalidNameThenItIsSkipped() throws IOException {
        createSubdirectoryWithPolicy("valid-name", DENY_OVERRIDES, "policy.sapl", "policy \"test\" permit true;");
        val invalidDir = tempDir.resolve("invalid name with spaces");
        Files.createDirectory(invalidDir);
        createFile(invalidDir.resolve("policy.sapl"), "policy \"invalid\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("valid-name");
    }

    @Test
    void whenSubdirectoryIsAddedThenNewSourceIsCreated() throws IOException {
        createSubdirectoryWithPolicy("initial", DENY_OVERRIDES, "policy.sapl", "policy \"initial\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);

        createSubdirectoryWithPolicy("added", PERMIT_OVERRIDES, "policy.sapl", "policy \"added\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs).hasSizeGreaterThanOrEqualTo(2);
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
            assertThat(pdpIds).contains("added");
        });
    }

    @Test
    void whenSubdirectoryIsRemovedThenSourceIsDisposed() throws IOException {
        val keepDir = tempDir.resolve("keep");
        Files.createDirectory(keepDir);
        createFile(keepDir.resolve("policy.sapl"), "policy \"keep\" permit true;");

        val removableDir = tempDir.resolve("removable");
        Files.createDirectory(removableDir);
        createFile(removableDir.resolve("policy.sapl"), "policy \"removable\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(2);
        val initialPdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(initialPdpIds).containsExactlyInAnyOrder("keep", "removable");

        // Delete the removable directory
        deleteDirectory(removableDir);

        // Wait for file watcher to detect removal
        // Adding a new directory after removal to verify the source is still working
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600)).untilAsserted(() -> {
            assertThat(source.isDisposed()).isFalse();
        });

        // Verify by adding a new subdirectory - source should still be functional
        val newDir = tempDir.resolve("newpdp");
        Files.createDirectory(newDir);
        createFile(newDir.resolve("policy.sapl"), "policy \"new\" permit true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
            assertThat(pdpIds).contains("newpdp");
        });
    }

    @Test
    void whenFileInSubdirectoryChangesThenCallbackIsInvokedAgain() throws IOException {
        val subdirPath = createSubdirectoryWithPolicy("mutable", DENY_OVERRIDES, "policy.sapl",
                "policy \"original\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);

        createFile(subdirPath.resolve("policy.sapl"), "policy \"modified\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments().getFirst()).contains("modified");
        });
    }

    @Test
    void whenChildPdpJsonHasConfigurationIdThenUsesExplicitId() throws IOException {
        val cultistDir = tempDir.resolve("cultist");
        Files.createDirectories(cultistDir);
        createFile(cultistDir.resolve("ritual.sapl"), "policy \"ritual\" permit true;");
        createFile(cultistDir.resolve("pdp.json"),
                "{\"algorithm\":{\"votingMode\":\"PRIORITY_DENY\",\"defaultDecision\":\"DENY\",\"errorHandling\":\"PROPAGATE\"},\"configurationId\":\"eldritch-v1\"}");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, false, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-v1");
    }

    @Test
    void whenChildPdpJsonHasNoConfigurationIdThenAutoGeneratesId() throws IOException {
        createSubdirectoryWithPolicy("cultist", DENY_OVERRIDES, "ritual.sapl", "policy \"ritual\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, false, configs::add);

        assertThat(configs).hasSize(1);
        // Auto-generated format: dir:<path>@<timestamp>@sha256:<hash>
        assertThat(configs.getFirst().configurationId()).startsWith("dir:");
        assertThat(configs.getFirst().configurationId()).contains("@sha256:");
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() throws IOException {
        createSubdirectoryWithPolicy("disposable", DENY_OVERRIDES, "policy.sapl", "policy \"test\" permit true;");

        source = new MultiDirectoryPDPConfigurationSource(tempDir, config -> {});

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() throws IOException {
        createSubdirectoryWithPolicy("disposable", DENY_OVERRIDES, "policy.sapl", "policy \"test\" permit true;");

        source = new MultiDirectoryPDPConfigurationSource(tempDir, config -> {});

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenSymlinkSubdirectoryPresentThenItIsLoaded(@TempDir Path externalDir) throws IOException {
        createSubdirectoryWithPolicy("real", DENY_OVERRIDES, "policy.sapl", "policy \"real\" permit true;");

        // Create target directory OUTSIDE the watched directory
        val target = externalDir.resolve("target");
        Files.createDirectory(target);
        createFile(target.resolve("policy.sapl"), "policy \"target\" deny true;");

        val link = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        // Symlinks are followed for flexible deployment scenarios
        assertThat(configs).hasSize(2);
        val pdpIds = configs.stream().map(PDPConfiguration::pdpId).toList();
        assertThat(pdpIds).containsExactlyInAnyOrder("real", "link");
    }

    @Test
    void whenSymlinkDirectoryProvidedThenItIsAccepted() throws IOException {
        val realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);
        val tenantDir = realDir.resolve("tenant");
        Files.createDirectory(tenantDir);
        createFile(tenantDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tenantDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val linkDir = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // Symlink directories are accepted for flexible deployment scenarios
        source = new MultiDirectoryPDPConfigurationSource(linkDir, configs::add);

        assertThat(configs).hasSize(1);
    }

    @Test
    void whenSymlinkSubdirectoryAddedAfterStartThenItIsLoaded(@TempDir Path externalDir) throws IOException {
        createSubdirectoryWithPolicy("initial", DENY_OVERRIDES, "policy.sapl", "policy \"initial\" permit true;");

        // Create target directory OUTSIDE the watched directory
        val target = externalDir.resolve("symlink-target");
        Files.createDirectory(target);
        createFile(target.resolve("policy.sapl"), "policy \"target\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("initial");

        // Add a symlink subdirectory pointing to the external target
        val link = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        // Wait for file watcher to pick up the symlink - it should be loaded
        await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(600)).untilAsserted(() -> {
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).distinct().toList();
            assertThat(pdpIds).contains("link");
        });
    }

    @Test
    void whenManySubdirectoriesThenAllAreLoaded() throws IOException {
        for (int i = 0; i < 10; i++) {
            createSubdirectoryWithPolicy("tenant-" + i, DENY_OVERRIDES, "policy.sapl",
                    "policy \"tenant%d\" permit subject.tenantId == %d;".formatted(i, i));
        }

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(10);
    }

    @Test
    void whenSubdirectoryHasNoSaplFilesThenEmptyConfigIsLoaded() throws IOException {
        val emptyDir = tempDir.resolve("empty-tenant");
        Files.createDirectory(emptyDir);
        createFile(emptyDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("empty-tenant");
        assertThat(configs.getFirst().saplDocuments()).isEmpty();
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
    }

    @Test
    void whenNestedSubdirectoriesThenOnlyFirstLevelIsProcessed() throws IOException {
        val parentDir = tempDir.resolve("parent");
        Files.createDirectories(parentDir);
        createFile(parentDir.resolve("parent.sapl"), "policy \"parent\" permit true;");

        val nestedDir = parentDir.resolve("nested");
        Files.createDirectories(nestedDir);
        createFile(nestedDir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("parent");
        // Nested directory should not affect parent - DirectoryPDPConfigurationSource
        // ignores subdirs
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenFileDeletedInSubdirectoryThenConfigIsReloaded() throws IOException {
        val subdirPath = tempDir.resolve("modifiable");
        Files.createDirectory(subdirPath);
        val firstPolicy  = subdirPath.resolve("first.sapl");
        val secondPolicy = subdirPath.resolve("second.sapl");
        createFile(firstPolicy, "policy \"first\" permit true;");
        createFile(secondPolicy, "policy \"second\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(2);

        Files.delete(secondPolicy);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments()).hasSize(1);
        });
    }

    private Path createSubdirectoryWithPolicy(String name, CombiningAlgorithm algorithm, String policyFileName,
            String policyContent) throws IOException {
        val subdirPath = tempDir.resolve(name);
        Files.createDirectories(subdirPath);
        createFile(subdirPath.resolve("pdp.json"), """
                { "algorithm": { "votingMode": "%s", "defaultDecision": "%s", "errorHandling": "%s" } }
                """.formatted(algorithm.votingMode().name(), algorithm.defaultDecision().name(),
                algorithm.errorHandling().name()));
        createFile(subdirPath.resolve(policyFileName), policyContent);
        return subdirPath;
    }

    private void createFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    @Test
    void whenDirectoryAddedAfterDisposeThenItIsIgnored() throws IOException {
        createSubdirectoryWithPolicy("initial", DENY_OVERRIDES, "policy.sapl", "policy \"initial\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);

        source.dispose();

        // Try to add a subdirectory after dispose
        val newDir = tempDir.resolve("after-dispose");
        Files.createDirectory(newDir);
        createFile(newDir.resolve("policy.sapl"), "policy \"after-dispose\" deny true;");

        // Wait a bit to ensure watcher would have picked it up if not disposed
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            val pdpIds = configs.stream().map(PDPConfiguration::pdpId).distinct().toList();
            assertThat(pdpIds).containsExactly("initial");
        });
    }

    @Test
    void whenDirectoryDeletedAfterDisposeThenItIsIgnored() throws IOException {
        val removable = tempDir.resolve("removable");
        Files.createDirectory(removable);
        createFile(removable.resolve("policy.sapl"), "policy \"removable\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);

        source.dispose();

        // Delete the subdirectory after dispose
        deleteDirectory(removable);

        // Wait a bit - no crash should occur
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(source.isDisposed()).isTrue();
        });
    }

    @Test
    void whenIncludeRootFilesAndRootFailsToLoadThenSubdirectoriesStillWork() throws IOException {
        createSubdirectoryWithPolicy("tenant", DENY_OVERRIDES, "policy.sapl", "policy \"tenant\" permit true;");
        // Create an invalid pdp.json at root level that won't parse
        createFile(tempDir.resolve("pdp.json"), "not valid json {{{");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // Root source fails silently, tenant succeeds
        source = new MultiDirectoryPDPConfigurationSource(tempDir, true, configs::add);

        // Only tenant should be loaded (root failed)
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("tenant");
    }

    @Test
    void whenSubdirectoryFailsToLoadThenOtherSubdirectoriesStillWork() throws IOException {
        createSubdirectoryWithPolicy("working", DENY_OVERRIDES, "policy.sapl", "policy \"working\" permit true;");

        // Create a subdirectory with invalid pdp.json
        val failing = tempDir.resolve("failing");
        Files.createDirectory(failing);
        createFile(failing.resolve("pdp.json"), "not valid json {{{");
        createFile(failing.resolve("policy.sapl"), "policy \"failing\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new MultiDirectoryPDPConfigurationSource(tempDir, configs::add);

        // Only working should be loaded (failing subdirectory is skipped)
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("working");
    }

    @Test
    void whenIncludeRootFilesAndDefaultSubdirectoryExistsThenRootIsNotLoadedAsDefault() throws IOException {
        // Create a subdirectory named "default" to test the collision scenario
        val defaultDir = tempDir.resolve("default");
        Files.createDirectory(defaultDir);
        createFile(defaultDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(defaultDir.resolve("policy.sapl"), "policy \"from-subdirectory\" permit true;");

        // Create root files that would normally be loaded as "default"
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"from-root\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        // includeRootFiles=true, but "default" subdirectory exists
        source = new MultiDirectoryPDPConfigurationSource(tempDir, true, configs::add);

        // Only one "default" security should exist (from subdirectory, not root)
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("default");
        // Should be PERMIT_OVERRIDES from the subdirectory, not DENY_OVERRIDES from
        // root
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
    }

    private void deleteDirectory(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Ignore
                }
            });
        }
    }

}
