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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class DirectoryPDPConfigurationSourceTests {

    @TempDir
    Path tempDir;

    private DirectoryPDPConfigurationSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.dispose();
        }
    }

    @Test
    void whenLoadingFromDirectory_thenCallbackIsInvokedWithConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                {
                  "algorithm": "PERMIT_OVERRIDES",
                  "variables": { "realm": "arkham" }
                }
                """);
        createFile(tempDir.resolve("forbidden.sapl"), """
                policy "forbidden-knowledge"
                deny action == "access" where resource.category == "forbidden";
                """);

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get()).isNotNull();
        assertThat(receivedConfig.get().pdpId()).isEqualTo("default");
        assertThat(receivedConfig.get().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(receivedConfig.get().saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingWithCustomPdpId_thenConfigurationHasCorrectPdpId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, "innermouth", receivedConfig::set);

        assertThat(receivedConfig.get().pdpId()).isEqualTo("innermouth");
    }

    @Test
    void whenPdpJsonHasConfigurationId_thenUsesExplicitId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("pdp.json"), "{\"configurationId\":\"eldritch-v1\"}");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", receivedConfig::set);

        assertThat(receivedConfig.get().pdpId()).isEqualTo("cultist");
        assertThat(receivedConfig.get().configurationId()).isEqualTo("eldritch-v1");
    }

    @Test
    void whenPdpJsonHasNoConfigurationId_thenAutoGeneratesId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", receivedConfig::set);

        assertThat(receivedConfig.get().pdpId()).isEqualTo("cultist");
        // Auto-generated format: dir:<path>@<timestamp>@sha256:<hash>
        assertThat(receivedConfig.get().configurationId()).startsWith("dir:");
        assertThat(receivedConfig.get().configurationId()).contains("@sha256:");
    }

    @Test
    void whenDirectoryDoesNotExist_thenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(nonExistentPath, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("does not exist");
    }

    @Test
    void whenPathIsNotADirectory_thenThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        createFile(file, "content");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(file, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenNoPdpJsonPresent_thenUsesDefaultAlgorithm() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
    }

    @Test
    void whenFileIsModified_thenCallbackIsInvokedAgain() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                { "algorithm": "DENY_OVERRIDES" }
                """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);

        createFile(tempDir.resolve("pdp.json"), """
                { "algorithm": "PERMIT_OVERRIDES" }
                """);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        });
    }

    @Test
    void whenFileIsAdded_thenCallbackIsInvokedAgain() throws IOException {
        createFile(tempDir.resolve("first.sapl"), "policy \"first\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);

        createFile(tempDir.resolve("second.sapl"), "policy \"second\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments()).hasSize(2);
        });
    }

    @Test
    void whenFileIsDeleted_thenCallbackIsInvokedWithUpdatedConfiguration() throws IOException {
        val firstPolicy  = tempDir.resolve("first.sapl");
        val secondPolicy = tempDir.resolve("second.sapl");
        createFile(firstPolicy, "policy \"first\" permit true;");
        createFile(secondPolicy, "policy \"second\" deny true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(2);

        Files.delete(secondPolicy);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments()).hasSize(1);
        });
    }

    @Test
    void whenDisposeIsCalled_thenIsDisposedReturnsTrue() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        source = new DirectoryPDPConfigurationSource(tempDir, config -> {});

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwice_thenIsIdempotent() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        source = new DirectoryPDPConfigurationSource(tempDir, config -> {});

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenEmptyDirectory_thenCallbackIsStillInvokedWithEmptyConfiguration() {
        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get()).isNotNull();
        assertThat(receivedConfig.get().saplDocuments()).isEmpty();
    }

    @Test
    void whenSymlinkFilePresent_thenItIsSkipped() throws IOException {
        createFile(tempDir.resolve("real.sapl"), "policy \"real\" permit true;");

        val target = tempDir.resolve("target.sapl");
        createFile(target, "policy \"target\" deny true;");

        val link = tempDir.resolve("link.sapl");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            return; // Skip test on systems that don't support symlinks
        }

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        // Real file + target file are loaded; symlink is skipped
        assertThat(receivedConfig.get().saplDocuments()).hasSize(2);
    }

    @Test
    void whenSymlinkDirectoryProvided_thenThrowsException() throws IOException {
        val realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);
        createFile(realDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val linkDir = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            return; // Skip test on systems that don't support symlinks
        }

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(linkDir, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("symbolic link");
    }

    @Test
    void whenTotalSizeExceedsLimit_thenThrowsException() throws IOException {
        val largeContent = "x".repeat(2 * 1024 * 1024);
        for (int i = 0; i < 6; i++) {
            createFile(tempDir.resolve("large" + i + ".sapl"),
                    "policy \"large%d\" permit \"%s\";".formatted(i, largeContent));
        }

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds maximum");
    }

    @Test
    void whenFileCountExceedsLimit_thenThrowsException() throws IOException {
        for (int i = 0; i < 1002; i++) {
            createFile(tempDir.resolve("policy" + i + ".sapl"), "policy \"p%d\" permit true;".formatted(i));
        }

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("File count exceeds maximum");
    }

    @Test
    void whenSubdirectoryOrNonSaplFilesExist_thenTheyAreIgnored() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("readme.txt"), "This should be ignored.");
        createFile(tempDir.resolve("config.yaml"), "key: value");

        val subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        createFile(subDir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get().saplDocuments()).hasSize(1).first().asString().contains("test");
    }

    @Test
    void whenInvalidPdpId_thenThrowsException() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, "invalid id with spaces", config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("invalid characters");
    }

    @Test
    void whenMultipleSaplFiles_thenAllAreLoaded() throws IOException {
        createFile(tempDir.resolve("access.sapl"), "policy \"access\" permit true;");
        createFile(tempDir.resolve("audit.sapl"), "policy \"audit\" deny false;");
        createFile(tempDir.resolve("admin.sapl"), "policy \"admin\" permit action == \"admin\";");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get().saplDocuments()).hasSize(3);
    }

    @Test
    void whenPdpJsonHasVariables_thenVariablesAreLoaded() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                {
                  "algorithm": "DENY_OVERRIDES",
                  "variables": {
                    "tenant": "miskatonic",
                    "department": "antiquities",
                    "accessLevel": 5
                  }
                }
                """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get().variables()).containsKey("tenant").containsKey("department")
                .containsKey("accessLevel");
    }

    @Test
    void whenFileChangesAfterDispose_thenTheyAreIgnored() throws IOException {
        val policyFile = tempDir.resolve("policy.sapl");
        createFile(policyFile, "policy \"test\" permit true;");

        val configs = new CopyOnWriteArrayList<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, configs::add);

        assertThat(configs).hasSize(1);

        source.dispose();

        // Modify, delete, and add files after dispose
        createFile(policyFile, "policy \"modified\" deny true;");
        Files.delete(policyFile);
        createFile(tempDir.resolve("new.sapl"), "policy \"new\" deny true;");

        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(configs).hasSize(1);
        });
    }

    @Test
    void whenPdpJsonIsInvalid_thenThrowsException() throws IOException {
        createFile(tempDir.resolve("pdp.json"), "not valid json {{{");
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, config -> {}))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse");
    }

    @Test
    void whenPdpJsonHasInvalidAlgorithm_thenThrowsException() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                { "algorithm": "INVALID_ALGORITHM" }
                """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, config -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenPdpJsonOnlyNoSaplFiles_thenConfigHasEmptyDocuments() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                { "algorithm": "PERMIT_OVERRIDES" }
                """);

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        assertThat(receivedConfig.get().saplDocuments()).isEmpty();
        assertThat(receivedConfig.get().combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
    }

    private void createFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

}
