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
package io.sapl.node.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.val;
import picocli.CommandLine;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("decide-once command")
class DecideOnceCommandTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--dir sets policy source directory")
        void whenDirOption_thenPolicySourceDirIsSet() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--dir", "/tmp/policies");
            assertThat(cmd.policySource.dir).isEqualTo(Path.of("/tmp/policies"));
        }

        @Test
        @DisplayName("--bundle sets policy source bundle")
        void whenBundleOption_thenPolicySourceBundleIsSet() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--bundle", "/tmp/my.saplbundle");
            assertThat(cmd.policySource.bundle).isEqualTo(Path.of("/tmp/my.saplbundle"));
        }

        @ParameterizedTest(name = "rejects {0}")
        @DisplayName("mutually exclusive options are rejected")
        @MethodSource
        void whenMutuallyExclusiveOptions_thenNonZeroExitCode(String description, String[] args) {
            val err = new StringWriter();
            val cmd = new CommandLine(new DecideOnceCommand());
            cmd.setErr(new PrintWriter(err));
            val exitCode = cmd.execute(args);
            assertThat(exitCode).isNotZero();
        }

        static Stream<Arguments> whenMutuallyExclusiveOptions_thenNonZeroExitCode() {
            return Stream.of(arguments("--dir and --bundle", new String[] { "--dir", "/a", "--bundle", "/b" }),
                    arguments("--public-key and --no-verify", new String[] { "--public-key", "/k", "--no-verify" }),
                    arguments("named flags and --file",
                            new String[] { "-s", "\"x\"", "-a", "\"y\"", "-r", "\"z\"", "-f", "input.json" }));
        }

        @Test
        @DisplayName("named flags populate subscription input fields")
        void whenNamedFlags_thenSubscriptionInputPopulated() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd.subscriptionInput.named).satisfies(named -> {
                assertThat(named.subject).isEqualTo("\"alice\"");
                assertThat(named.action).isEqualTo("\"read\"");
                assertThat(named.resource).isEqualTo("\"doc\"");
            });
        }

        @Test
        @DisplayName("-s alone without -a and -r is rejected")
        void whenSubjectOnly_thenNonZeroExitCode() {
            val err = new StringWriter();
            val cmd = new CommandLine(new DecideOnceCommand());
            cmd.setErr(new PrintWriter(err));
            val exitCode = cmd.execute("-s", "\"alice\"");
            assertThat(exitCode).isNotZero();
        }

        @Test
        @DisplayName("-f alone sets file path with named input null")
        void whenFileOnly_thenFilePopulatedAndNamedNull() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("-f", "request.json");
            assertThat(cmd.subscriptionInput).satisfies(input -> {
                assertThat(input.file).isEqualTo(Path.of("request.json"));
                assertThat(input.named).isNull();
            });
        }

        @Test
        @DisplayName("--trace sets trace flag")
        void whenTraceFlag_thenTraceIsTrue() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--trace");
            assertThat(cmd.trace).isTrue();
        }

        @Test
        @DisplayName("no subscription input leaves subscriptionInput null")
        void whenNoSubscriptionInput_thenSubscriptionInputIsNull() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--dir", "/tmp");
            assertThat(cmd.subscriptionInput).isNull();
        }

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelp_thenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new DecideOnceCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("decide-once", "--dir", "--bundle", "-s", "-f");
        }

    }

    @Nested
    @DisplayName("Spring args generation")
    class SpringArgsTests {

        @Test
        @DisplayName("directory mode produces config-type, config-path, and policies-path")
        void whenDirectoryMode_thenCorrectSpringArgs() {
            val cmd  = new DecideOnceCommand();
            val args = cmd.buildSpringArgs("DIRECTORY", "/tmp/policies", null, false);
            assertThat(args)
                    .contains("--io.sapl.pdp.embedded.pdp-config-type=DIRECTORY",
                            "--io.sapl.pdp.embedded.config-path=/tmp/policies",
                            "--io.sapl.pdp.embedded.policies-path=/tmp/policies")
                    .noneMatch(a -> a.contains("bundle-security"));
        }

        @Test
        @DisplayName("bundle with public key includes key path without allow-unsigned")
        void whenBundleWithKey_thenIncludesPublicKeyPath() {
            val cmd  = new DecideOnceCommand();
            val args = cmd.buildSpringArgs("BUNDLES", "/tmp", "/tmp/key.pub", false);
            assertThat(args)
                    .contains("--io.sapl.pdp.embedded.pdp-config-type=BUNDLES",
                            "--io.sapl.pdp.embedded.bundle-security.public-key-path=/tmp/key.pub")
                    .noneMatch(a -> a.contains("allow-unsigned"));
        }

        @Test
        @DisplayName("bundle with no-verify includes allow-unsigned without key path")
        void whenBundleNoVerify_thenIncludesAllowUnsigned() {
            val cmd  = new DecideOnceCommand();
            val args = cmd.buildSpringArgs("BUNDLES", "/tmp", null, true);
            assertThat(args).contains("--io.sapl.pdp.embedded.bundle-security.allow-unsigned=true")
                    .noneMatch(a -> a.contains("public-key-path"));
        }

        @ParameterizedTest(name = "when {0} enabled")
        @DisplayName("reporting flag includes property and logging override")
        @MethodSource
        void whenReportingFlagEnabled_thenIncludesPropertyAndLogging(String flagName, String expectedProperty) {
            val cmd = new DecideOnceCommand();
            switch (flagName) {
            case "trace"       -> cmd.trace = true;
            case "json-report" -> cmd.jsonReport = true;
            case "text-report" -> cmd.textReport = true;
            }
            val args = cmd.buildSpringArgs("DIRECTORY", "/tmp", null, false);
            assertThat(args).contains(expectedProperty, "--logging.level.[io.sapl.pdp.interceptors]=INFO");
        }

        static Stream<Arguments> whenReportingFlagEnabled_thenIncludesPropertyAndLogging() {
            return Stream.of(arguments("trace", "--io.sapl.pdp.embedded.print-trace=true"),
                    arguments("json-report", "--io.sapl.pdp.embedded.print-json-report=true"),
                    arguments("text-report", "--io.sapl.pdp.embedded.print-text-report=true"));
        }

        @Test
        @DisplayName("no reporting flags omits logging override")
        void whenNoReportingFlags_thenNoLoggingOverride() {
            val cmd  = new DecideOnceCommand();
            val args = cmd.buildSpringArgs("DIRECTORY", "/tmp", null, false);
            assertThat(args).noneMatch(a -> a.contains("logging.level"));
        }

        @Test
        @DisplayName("multiple reporting flags produce single logging override")
        void whenMultipleReportingFlags_thenSingleLoggingOverride() {
            val cmd = new DecideOnceCommand();
            cmd.trace      = true;
            cmd.jsonReport = true;
            cmd.textReport = true;
            val args         = cmd.buildSpringArgs("DIRECTORY", "/tmp", null, false);
            val loggingCount = Arrays.stream(args).filter(a -> a.contains("logging.level")).count();
            assertThat(loggingCount).isEqualTo(1);
        }

    }

    @Nested
    @DisplayName("subscription building")
    class SubscriptionBuildingTests {

        @Test
        @DisplayName("JSON string values create valid subscription")
        void whenJsonStringValues_thenSubscriptionCreated() {
            val cmd          = commandWithNamedInput("\"alice\"", "\"read\"", "\"document\"");
            val subscription = cmd.buildSubscription(MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":\"alice\"", "\"action\":\"read\"", "\"resource\":\"document\"");
        }

        @Test
        @DisplayName("JSON object values create valid subscription")
        void whenJsonObjectValues_thenSubscriptionCreated() {
            val cmd          = commandWithNamedInput("{\"name\":\"alice\"}", "\"read\"",
                    "{\"type\":\"doc\",\"id\":42}");
            val subscription = cmd.buildSubscription(MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"name\":\"alice\"", "\"type\":\"doc\"");
        }

        @Test
        @DisplayName("JSON number value is parsed as number")
        void whenJsonNumberValue_thenParsedAsNumber() {
            val cmd          = commandWithNamedInput("42", "\"read\"", "\"doc\"");
            val subscription = cmd.buildSubscription(MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":42");
        }

        @Test
        @DisplayName("invalid JSON value throws IllegalArgumentException")
        void whenInvalidJsonValue_thenThrowsIllegalArgument() {
            val cmd = commandWithNamedInput("not-valid-json", "\"read\"", "\"doc\"");
            assertThatThrownBy(() -> cmd.buildSubscription(MAPPER)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON value");
        }

        @Test
        @DisplayName("secrets that are not a JSON object throw IllegalArgumentException")
        void whenSecretsNotObject_thenThrowsIllegalArgument() {
            val cmd = commandWithNamedInput("\"alice\"", "\"read\"", "\"doc\"");
            cmd.subscriptionInput.named.secrets = "\"not-an-object\"";
            assertThatThrownBy(() -> cmd.buildSubscription(MAPPER)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("JSON object");
        }

        @Test
        @DisplayName("valid secrets JSON object is accepted")
        void whenSecretsObject_thenSubscriptionCreated() {
            val cmd = commandWithNamedInput("\"alice\"", "\"read\"", "\"doc\"");
            cmd.subscriptionInput.named.secrets = "{\"key\":\"value\"}";
            val subscription = cmd.buildSubscription(MAPPER);
            assertThat(subscription).isNotNull();
        }

        @Test
        @DisplayName("environment is included when provided")
        void whenEnvironmentProvided_thenIncludedInSubscription() {
            val cmd = commandWithNamedInput("\"alice\"", "\"read\"", "\"doc\"");
            cmd.subscriptionInput.named.environment = "{\"time\":\"morning\"}";
            val subscription = cmd.buildSubscription(MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"time\":\"morning\"");
        }

        @Test
        @DisplayName("file subscription is deserialized via SaplJacksonModule")
        void whenFileSubscription_thenDeserialized(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("request.json");
            Files.writeString(file, """
                    {"subject":"alice","action":"read","resource":"document"}
                    """);
            val cmd          = commandWithFileInput(file);
            val subscription = cmd.buildSubscription(MAPPER);
            val json         = MAPPER.writeValueAsString(subscription);
            assertThat(json).contains("\"subject\":\"alice\"", "\"action\":\"read\"", "\"resource\":\"document\"");
        }

        @Test
        @DisplayName("missing subscription file throws IllegalArgumentException")
        void whenFileMissing_thenThrowsIllegalArgument() {
            val cmd = commandWithFileInput(Path.of("/nonexistent/file.json"));
            assertThatThrownBy(() -> cmd.buildSubscription(MAPPER)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Subscription file not found");
        }

        @Test
        @DisplayName("invalid JSON in file throws IllegalArgumentException")
        void whenFileContainsInvalidJson_thenThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("bad.json");
            Files.writeString(file, "not valid json at all");
            val cmd = commandWithFileInput(file);
            assertThatThrownBy(() -> cmd.buildSubscription(MAPPER)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON");
        }

        @Test
        @DisplayName("file missing required fields throws IllegalArgumentException")
        void whenFileMissingRequiredFields_thenThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
            val file = tempDir.resolve("incomplete.json");
            Files.writeString(file, "{\"subject\":\"alice\"}");
            val cmd = commandWithFileInput(file);
            assertThatThrownBy(() -> cmd.buildSubscription(MAPPER)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON");
        }

    }

    private static DecideOnceCommand commandWithNamedInput(String subject, String action, String resource) {
        val cmd   = new DecideOnceCommand();
        val input = new DecideOnceCommand.SubscriptionInput();
        val named = new DecideOnceCommand.NamedSubscription();
        named.subject         = subject;
        named.action          = action;
        named.resource        = resource;
        input.named           = named;
        cmd.subscriptionInput = input;
        return cmd;
    }

    private static DecideOnceCommand commandWithFileInput(Path file) {
        val cmd   = new DecideOnceCommand();
        val input = new DecideOnceCommand.SubscriptionInput();
        input.file            = file;
        cmd.subscriptionInput = input;
        return cmd;
    }

}
