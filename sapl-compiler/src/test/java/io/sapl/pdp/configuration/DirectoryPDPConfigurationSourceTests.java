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
    void whenLoadingWithCustomConfigurationId_thenConfigurationHasCorrectId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", "eldritch-v1", receivedConfig::set);

        assertThat(receivedConfig.get().pdpId()).isEqualTo("cultist");
        assertThat(receivedConfig.get().configurationId()).isEqualTo("eldritch-v1");
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
    void whenSymlinkPresent_thenItIsSkipped() throws IOException {
        createFile(tempDir.resolve("real.sapl"), "policy \"real\" permit true;");

        val target = tempDir.resolve("target.sapl");
        createFile(target, "policy \"target\" deny true;");

        val link = tempDir.resolve("link.sapl");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            // Skip test on systems that don't support symlinks
            return;
        }

        val receivedConfig = new AtomicReference<PDPConfiguration>();

        source = new DirectoryPDPConfigurationSource(tempDir, receivedConfig::set);

        // Only the real file should be loaded, symlink should be skipped
        assertThat(receivedConfig.get().saplDocuments()).hasSize(1);
    }

    private void createFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

}
