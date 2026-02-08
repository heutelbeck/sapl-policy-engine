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
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@DisplayName("DirectoryPDPConfigurationSource")
@ExtendWith(MockitoExtension.class)
class DirectoryPDPConfigurationSourceTests {

    private static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    @TempDir
    Path tempDir;

    @Mock
    PdpVoterSource pdpVoterSource;

    private DirectoryPDPConfigurationSource source;

    @AfterEach
    void tearDown() {
        if (source != null) {
            source.dispose();
        }
    }

    private CopyOnWriteArrayList<PDPConfiguration> captureConfigurations() {
        val configs = new CopyOnWriteArrayList<PDPConfiguration>();
        doAnswer(inv -> {
            configs.add(inv.getArgument(0));
            return null;
        }).when(pdpVoterSource).loadConfiguration(any(), eq(true));
        return configs;
    }

    @Test
    void whenLoadingFromDirectoryThenVoterSourceReceivesConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        {
                          "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" },
                          "variables": { "realm": "arkham" }
                        }
                        """);
        createFile(tempDir.resolve("forbidden.sapl"), """
                policy "forbidden-knowledge"
                deny action == "access";
                  resource.category == "forbidden";
                """);

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().pdpId()).isEqualTo("default");
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenLoadingWithCustomPdpIdThenConfigurationHasCorrectPdpId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "innermouth", pdpVoterSource);

        assertThat(configs.getFirst().pdpId()).isEqualTo("innermouth");
    }

    @Test
    void whenPdpJsonHasConfigurationIdThenUsesExplicitId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("pdp.json"), "{\"configurationId\":\"eldritch-v1\"}");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", pdpVoterSource);

        assertThat(configs.getFirst().pdpId()).isEqualTo("cultist");
        assertThat(configs.getFirst().configurationId()).isEqualTo("eldritch-v1");
    }

    @Test
    void whenPdpJsonHasNoConfigurationIdThenAutoGeneratesId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", pdpVoterSource);

        assertThat(configs.getFirst().pdpId()).isEqualTo("cultist");
        // Auto-generated format: dir:<path>@<timestamp>@sha256:<hash>
        assertThat(configs.getFirst().configurationId()).startsWith("dir:");
        assertThat(configs.getFirst().configurationId()).contains("@sha256:");
    }

    @Test
    void whenDirectoryDoesNotExistThenThrowsException() {
        val nonExistentPath = tempDir.resolve("non-existent");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(nonExistentPath, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenPathIsNotADirectoryThenThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        createFile(file, "content");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(file, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("not a directory");
    }

    @Test
    void whenNoPdpJsonPresentThenUsesDefaultAlgorithm() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
    }

    @Test
    void whenFileIsModifiedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);

        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        });
    }

    @Test
    void whenFileIsAddedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        createFile(tempDir.resolve("first.sapl"), "policy \"first\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(1);

        createFile(tempDir.resolve("second.sapl"), "policy \"second\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments()).hasSize(2);
        });
    }

    @Test
    void whenFileIsDeletedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        val firstPolicy  = tempDir.resolve("first.sapl");
        val secondPolicy = tempDir.resolve("second.sapl");
        createFile(firstPolicy, "policy \"first\" permit true;");
        createFile(secondPolicy, "policy \"second\" deny true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).hasSize(2);

        Files.delete(secondPolicy);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(configs.size()).isGreaterThanOrEqualTo(2);
            assertThat(configs.getLast().saplDocuments()).hasSize(1);
        });
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenEmptyDirectoryThenVoterSourceStillReceivesEmptyConfiguration() {
        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().saplDocuments()).isEmpty();
    }

    @Test
    void whenSymlinkFilePresentThenItIsLoaded() throws IOException {
        createFile(tempDir.resolve("real.sapl"), "policy \"real\" permit true;");

        val target = tempDir.resolve("target.sapl");
        createFile(target, "policy \"target\" deny true;");

        val link = tempDir.resolve("link.sapl");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            return; // Skip test on systems that don't support symlinks
        }

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        // All files including symlink are loaded
        assertThat(configs.getFirst().saplDocuments()).hasSize(3);
    }

    @Test
    void whenSymlinkDirectoryProvidedThenItIsAccepted() throws IOException {
        val realDir = tempDir.resolve("real");
        Files.createDirectory(realDir);
        createFile(realDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val linkDir = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            return; // Skip test on systems that don't support symlinks
        }

        val configs = captureConfigurations();

        // Symlink directories are accepted for flexible deployment scenarios
        source = new DirectoryPDPConfigurationSource(linkDir, pdpVoterSource);

        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenTotalSizeExceedsLimitThenThrowsException() throws IOException {
        val largeContent = "x".repeat(2 * 1024 * 1024);
        for (int i = 0; i < 6; i++) {
            createFile(tempDir.resolve("large" + i + ".sapl"),
                    "policy \"large%d\" permit \"%s\";".formatted(i, largeContent));
        }

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds maximum");
    }

    @Test
    void whenFileCountExceedsLimitThenThrowsException() throws IOException {
        for (int i = 0; i < 1002; i++) {
            createFile(tempDir.resolve("policy" + i + ".sapl"), "policy \"p%d\" permit true;".formatted(i));
        }

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("File count exceeds maximum");
    }

    @Test
    void whenSubdirectoryOrNonSaplFilesExistThenTheyAreIgnored() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("readme.txt"), "This should be ignored.");
        createFile(tempDir.resolve("security.yaml"), "key: value");

        val subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        createFile(subDir.resolve("nested.sapl"), "policy \"nested\" deny true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst().saplDocuments()).hasSize(1).first().asString().contains("test");
    }

    @Test
    void whenInvalidPdpIdThenThrowsException() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, "invalid id with spaces", pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("invalid characters");
    }

    @Test
    void whenMultipleSaplFilesThenAllAreLoaded() throws IOException {
        createFile(tempDir.resolve("access.sapl"), "policy \"access\" permit true;");
        createFile(tempDir.resolve("audit.sapl"), "policy \"audit\" deny false;");
        createFile(tempDir.resolve("admin.sapl"), "policy \"admin\" permit action == \"admin\";");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst().saplDocuments()).hasSize(3);
    }

    @Test
    void whenPdpJsonHasVariablesThenVariablesAreLoaded() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        {
                          "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" },
                          "variables": {
                            "tenant": "miskatonic",
                            "department": "antiquities",
                            "accessLevel": 5
                          }
                        }
                        """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst().data().variables()).containsKey("tenant").containsKey("department")
                .containsKey("accessLevel");
    }

    @Test
    void whenFileChangesAfterDisposeThenTheyAreIgnored() throws IOException {
        val policyFile = tempDir.resolve("policy.sapl");
        createFile(policyFile, "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

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
    void whenPdpJsonIsInvalidThenThrowsException() throws IOException {
        createFile(tempDir.resolve("pdp.json"), "not valid json {{{");
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse");
    }

    @Test
    void whenPdpJsonHasInvalidAlgorithmThenThrowsException() throws IOException {
        createFile(tempDir.resolve("pdp.json"), """
                { "algorithm": "INVALID_ALGORITHM" }
                """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("Failed to parse");
    }

    @Test
    void whenPdpJsonOnlyNoSaplFilesThenConfigHasEmptyDocuments() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst().saplDocuments()).isEmpty();
        assertThat(configs.getFirst().combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
    }

    private void createFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

}
