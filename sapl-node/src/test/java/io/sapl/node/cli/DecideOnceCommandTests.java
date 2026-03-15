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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;
import picocli.CommandLine;

@DisplayName("decide-once command")
class DecideOnceCommandTests {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--dir sets policy source directory")
        void whenDirOption_thenPolicySourceDirIsSet() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--dir", "/tmp/policies");
            assertThat(cmd.pdpOptions.policySource.dir).isEqualTo(Path.of("/tmp/policies"));
        }

        @Test
        @DisplayName("--bundle sets policy source bundle")
        void whenBundleOption_thenPolicySourceBundleIsSet() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--bundle", "/tmp/my.saplbundle");
            assertThat(cmd.pdpOptions.policySource.bundle).isEqualTo(Path.of("/tmp/my.saplbundle"));
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
            assertThat(cmd.pdpOptions.subscriptionInput.named).satisfies(named -> {
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
            assertThat(cmd.pdpOptions.subscriptionInput).satisfies(input -> {
                assertThat(input.file).isEqualTo(Path.of("request.json"));
                assertThat(input.named).isNull();
            });
        }

        @Test
        @DisplayName("-f - sets stdin marker path")
        void whenFileDash_thenStdinMarkerPath() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("-f", "-");
            assertThat(cmd.pdpOptions.subscriptionInput.file).isEqualTo(Path.of("-"));
        }

        @Test
        @DisplayName("--trace sets trace flag")
        void whenTraceFlag_thenTraceIsTrue() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--trace");
            assertThat(cmd.pdpOptions.trace).isTrue();
        }

        @Test
        @DisplayName("no subscription input leaves subscriptionInput null")
        void whenNoSubscriptionInput_thenSubscriptionInputIsNull() {
            val cmd = new DecideOnceCommand();
            new CommandLine(cmd).parseArgs("--dir", "/tmp");
            assertThat(cmd.pdpOptions.subscriptionInput).isNull();
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

}
