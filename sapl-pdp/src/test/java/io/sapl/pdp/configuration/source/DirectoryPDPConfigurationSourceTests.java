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

import com.github.valfirst.slf4jtest.LoggingEvent;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;
import org.slf4j.event.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("DirectoryPDPConfigurationSource")
class DirectoryPDPConfigurationSourceTests {

    private static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    @TempDir
    Path tempDir;

    private DirectoryPDPConfigurationSource source;

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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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
        source = new DirectoryPDPConfigurationSource(tempDir, "innermouth");

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst().pdpId()).isEqualTo("innermouth");
    }

    @Test
    void whenPdpJsonHasConfigurationIdThenUsesExplicitId() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        createFile(tempDir.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }, "configurationId": "eldritch-v1"}
                        """);
        source = new DirectoryPDPConfigurationSource(tempDir, "cultist");

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst()).satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("cultist");
            assertThat(config.configurationId()).isEqualTo("eldritch-v1");
        });
    }

    @Test
    void whenPdpJsonHasNoConfigurationIdThenAutoGeneratesId() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir, "cultist");

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst()).satisfies(config -> {
            assertThat(config.pdpId()).isEqualTo("cultist");
            assertThat(config.configurationId()).startsWith("dir:").doesNotContain("sha256");
        });
    }

    @Test
    void whenDirectoryAbsentAtStartupThenToleratedAndRecoversOnCreation() throws IOException {
        val path = tempDir.resolve("appears-later");
        source = new DirectoryPDPConfigurationSource(path);

        val capture = new CapturingSubscriber();
        assertThatCode(() -> source.subscribe(capture)).doesNotThrowAnyException();
        assertThat(source.isClosed()).isFalse();
        assertThat(capture.configs()).isEmpty();

        Files.createDirectory(path);
        writePdpJson(path);
        createFile(path.resolve("policy.sapl"), "policy \"p\" permit true;");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(capture.configs()).isNotEmpty());
    }

    @Test
    void whenDirectoryDeletedAtRuntimeThenEmitsRemoveAndRecoversOnRecreation() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"p\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val capture = new CapturingSubscriber();
        source.subscribe(capture);
        await().atMost(Duration.ofSeconds(5)).until(() -> !capture.configs().isEmpty());

        deleteDirectoryContents(tempDir);
        Files.delete(tempDir);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(capture.removedPdpIds()).isNotEmpty());

        Files.createDirectory(tempDir);
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"p\" permit true;");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(capture.configs()).hasSizeGreaterThanOrEqualTo(2));
    }

    @Test
    void whenPathIsNotADirectoryThenSubscribeThrowsException() throws IOException {
        val file = tempDir.resolve("not-a-directory.txt");
        createFile(file, "content");
        source = new DirectoryPDPConfigurationSource(file);

        val capture = new CapturingSubscriber();

        assertThatThrownBy(() -> source.subscribe(capture)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void whenNoPdpJsonPresentThenInitialLoadFailsButMonitorContinues() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        source = new DirectoryPDPConfigurationSource(tempDir);

        assertThat(source.isClosed()).isFalse();
    }

    @Test
    void whenFileIsModifiedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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
    @DisplayName("a subscriber that throws does not stop hot-reload for other subscribers")
    void whenSubscriberThrowsThenOtherSubscribersKeepReceivingUpdates() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" } }
                        """);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val capture = new CapturingSubscriber();
        source.subscribe(capture);
        val configs = capture.configs();
        source.subscribe(event -> {
            throw new RuntimeException("hostile subscriber");
        });

        assertThat(configs).hasSize(1);

        createFile(tempDir.resolve("pdp.json"),
                """
                        { "algorithm": { "votingMode": "PRIORITY_PERMIT", "defaultDecision": "PERMIT", "errorHandling": "PROPAGATE" } }
                        """);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(2));

        createFile(tempDir.resolve("second.sapl"), "policy \"second\" deny true;");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void whenFileIsAddedThenVoterSourceReceivesUpdatedConfiguration() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("first.sapl"), "policy \"first\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1).first().satisfies(config -> assertThat(config.saplDocuments()).hasSize(2));

        Files.delete(secondPolicy);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(configs).hasSizeGreaterThanOrEqualTo(2)
                .last().satisfies(config -> assertThat(config.saplDocuments()).hasSize(1)));
    }

    @Test
    void whenDisposeIsCalledThenIsDisposedReturnsTrue() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        captureConfigurations(source);

        assertThat(source.isClosed()).isFalse();

        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    void whenDisposeIsCalledTwiceThenIsIdempotent() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        captureConfigurations(source);

        source.close();
        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    @DisplayName("closing before the monitor ever started shuts down quietly")
    void whenClosedBeforeMonitorStartedThenNoWarningOrErrorLogged() {
        TestLoggerFactory.clearAll();
        source = new DirectoryPDPConfigurationSource(tempDir);
        source.close();
        assertThat(TestLoggerFactory.getAllLoggingEvents()).extracting(LoggingEvent::getLevel)
                .doesNotContain(Level.WARN, Level.ERROR);
    }

    @Test
    void whenEmptyDirectoryThenInitialLoadFailsButMonitorContinues() {
        source = new DirectoryPDPConfigurationSource(tempDir);

        assertThat(source.isClosed()).isFalse();
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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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

        // Symlink directories are accepted for flexible deployment scenarios
        source = new DirectoryPDPConfigurationSource(linkDir);

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst().saplDocuments()).hasSize(1);
    }

    @Test
    void whenTotalSizeExceedsLimitThenSourceCreatesWithoutConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        {
                          "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" },
                          "compilerOptions": { "maxTotalSizeMegabytes": 2 }
                        }
                        """);
        val oneMegabyte = "x".repeat(1024 * 1024);
        for (int i = 0; i < 3; i++) {
            createFile(tempDir.resolve("large" + i + ".sapl"),
                    "policy \"large%d\" permit \"%s\";".formatted(i, oneMegabyte));
        }

        source = new DirectoryPDPConfigurationSource(tempDir);

        assertThat(source.isClosed()).isFalse();
        assertThat(captureConfigurations(source)).isEmpty();
    }

    @Test
    void whenFileCountExceedsLimitThenSourceCreatesWithoutConfiguration() throws IOException {
        createFile(tempDir.resolve("pdp.json"),
                """
                        {
                          "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" },
                          "compilerOptions": { "maxPolicyDocuments": 3 }
                        }
                        """);
        for (int i = 0; i < 4; i++) {
            createFile(tempDir.resolve("policy" + i + ".sapl"), "policy \"p%d\" permit true;".formatted(i));
        }

        source = new DirectoryPDPConfigurationSource(tempDir);

        assertThat(source.isClosed()).isFalse();
        assertThat(captureConfigurations(source)).isEmpty();
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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst().saplDocuments()).hasSize(1).first().asString().contains("test");
    }

    @Test
    void whenInvalidPdpIdThenThrowsException() throws IOException {
        createFile(tempDir.resolve("policy.sapl"), "policy \"test\" permit true;");

        assertThatThrownBy(() -> new DirectoryPDPConfigurationSource(tempDir, "invalid id with spaces"))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("invalid characters");
    }

    @Test
    void whenMultipleSaplFilesThenAllAreLoaded() throws IOException {
        writePdpJson(tempDir);
        createFile(tempDir.resolve("access.sapl"), "policy \"access\" permit true;");
        createFile(tempDir.resolve("audit.sapl"), "policy \"audit\" deny false;");
        createFile(tempDir.resolve("admin.sapl"), "policy \"admin\" permit action == \"admin\";");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

        assertThat(configs.getFirst().data().variables()).containsKey("tenant").containsKey("department")
                .containsKey("accessLevel");
    }

    @Test
    void whenFileChangesAfterDisposeThenTheyAreIgnored() throws IOException {
        writePdpJson(tempDir);
        val policyFile = tempDir.resolve("policy.sapl");
        createFile(policyFile, "policy \"test\" permit true;");
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

        assertThat(configs).hasSize(1);

        source.close();

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

        source = new DirectoryPDPConfigurationSource(tempDir);

        assertThat(source.isClosed()).isFalse();
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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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
        source = new DirectoryPDPConfigurationSource(tempDir);

        val configs = captureConfigurations(source);

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

    private static void deleteDirectoryContents(Path directory) throws IOException {
        try (val entries = Files.newDirectoryStream(directory)) {
            for (val entry : entries) {
                Files.delete(entry);
            }
        }
    }

}
