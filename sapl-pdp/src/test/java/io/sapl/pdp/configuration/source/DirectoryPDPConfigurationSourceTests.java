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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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

        assertThat(configs).hasSize(1).first().satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("default");
            assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
            assertThat(config.saplDocuments()).hasSize(1);
        });
    }

    @Test
    void whenLoadingWithCustomPdpIdThenConfigurationHasCorrectPdpId() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "innermouth", pdpVoterSource);

        assertThat(configs.getFirst().pdpId()).isEqualTo("innermouth");
    }

    @Test
    void whenPdpJsonHasConfigurationIdThenUsesExplicitId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "eldritch-v1"}
                        """);

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", pdpVoterSource);

        assertThat(configs.getFirst()).satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("cultist");
            assertThat(config.configurationId()).isEqualTo("eldritch-v1");
        });
    }

    @Test
    void whenPdpJsonHasNoConfigurationIdThenAutoGeneratesId() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, "cultist", pdpVoterSource);

        assertThat(configs.getFirst()).satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("cultist");
            assertThat(config.configurationId()).startsWith("dir:").contains("@sha256:");
        });
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
    void whenNoPdpJsonPresentThenInitialLoadFailsButMonitorContinues() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();
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

        assertThat(configs).hasSize(1).first()
                .satisfies(config -> assertThat(config.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES));

        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(2)
                .last().satisfies(config -> assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES)));
    }

    @Test
    void whenFileIsAddedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("first.sapl"), "policy \"first\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1).first().satisfies(config -> assertThat(config.saplDocuments()).hasSize(1));

        createFile(tempDir.resolve("second.sapl"), "policy \"second\" deny true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(2)
                .last().satisfies(config -> assertThat(config.saplDocuments()).hasSize(2)));
    }

    @Test
    void whenFileIsDeletedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        writePdpJson(tempDir);
        val firstPolicy  = tempDir.resolve("first.sapl");
        val secondPolicy = tempDir.resolve("second.sapl");
        createFile(firstPolicy, "policy \"first\" permit true;");
        createFile(secondPolicy, "policy \"second\" deny true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).hasSize(1).first().satisfies(config -> assertThat(config.saplDocuments()).hasSize(2));

        Files.delete(secondPolicy);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(2)
                .last().satisfies(config -> assertThat(config.saplDocuments()).hasSize(1)));
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        source.dispose();
        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenEmptyDirectoryThenInitialLoadFailsButMonitorContinues() {
        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();
    }

    @Test
    void whenSymlinkFilePresentThenItIsLoaded() throws IOException {
        writePdpJson(tempDir);
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
        writePdpJson(realDir);
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
    void whenTotalSizeExceedsLimitThenSourceCreatesWithoutConfiguration() throws IOException {
        writePdpJson(tempDir);
        val largeContent = "x".repeat(2 * 1024 * 1024);
        for (int i = 0; i < 6; i++) {
            createFile(tempDir.resolve("large" + i + ".sapl"),
                    "policy \"large%d\" permit \"%s\";".formatted(i, largeContent));
        }

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();
    }

    @Test
    void whenFileCountExceedsLimitThenSourceCreatesWithoutConfiguration() throws IOException {
        writePdpJson(tempDir);
        for (int i = 0; i < 1002; i++) {
            createFile(tempDir.resolve("policy" + i + ".sapl"), "policy \"p%d\" permit true;".formatted(i));
        }

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();
    }

    @Test
    void whenSubdirectoryOrNonSaplFilesExistThenTheyAreIgnored() throws IOException {
        writePdpJson(tempDir);
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
        writePdpJson(tempDir);
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
        writePdpJson(tempDir);
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

    @ParameterizedTest(name = "invalid pdp.json: {0}")
    @MethodSource
    void whenPdpJsonIsInvalidThenSourceCreatesWithoutConfiguration(String description, String pdpJsonContent)
            throws IOException {
        createFile(tempDir.resolve("pdp.json"), pdpJsonContent);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(source.isDisposed()).isFalse();
    }

    private static Stream<Arguments> whenPdpJsonIsInvalidThenSourceCreatesWithoutConfiguration() {
        return Stream.of(arguments("malformed JSON", "not valid json {{{"), arguments("missing algorithm", "{}"),
                arguments("invalid algorithm", """
                        { "algorithm": "INVALID_ALGORITHM" }
                        """));
    }

    @Test
    void whenPdpJsonIsInvalidThenRecoveryOnValidFileChange() throws IOException {
        createFile(tempDir.resolve("pdp.json"), "not valid json {{{");
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs).isEmpty();

        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(configs).hasSize(1).first().satisfies(config -> {
                    assertThat(config.combiningAlgorithm()).isEqualTo(DENY_OVERRIDES);
                    assertThat(config.saplDocuments()).hasSize(1);
                }));
    }

    @Test
    void whenPdpJsonOnlyNoSaplFilesThenConfigHasEmptyDocuments() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);

        val configs = captureConfigurations();

        source = new DirectoryPDPConfigurationSource(tempDir, pdpVoterSource);

        assertThat(configs.getFirst()).satisfies(config -> {
            assertThat(config.saplDocuments()).isEmpty();
            assertThat(config.combiningAlgorithm()).isEqualTo(PERMIT_OVERRIDES);
        });
    }

    private void createFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    private void writePdpJson(Path directory) throws IOException {
        createFile(directory.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }}
                        """);
    }

}
