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
package io.sapl.node.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

@DisplayName("loadtest command")
class LoadtestCommandTests {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelpThenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new LoadtestCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("loadtest", "--rsocket", "--concurrency", "--rate", "-s", "-r");
        }

        @Test
        @DisplayName("parses HTTP options with defaults")
        void whenDefaultsThenHttpOptionsPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd).satisfies(c -> {
                assertThat(c.rsocket).isFalse();
                assertThat(c.url).isEqualTo("https://localhost:8443");
                assertThat(c.concurrency).isEqualTo(64);
            });
        }

        @Test
        @DisplayName("parses RSocket options")
        void whenRsocketThenRsocketOptionsPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--rsocket", "--host", "pdp.example.com", "--port", "9000", "--connections",
                    "4", "--vt-per-connection", "256", "-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd).satisfies(c -> {
                assertThat(c.rsocket).isTrue();
                assertThat(c.rsocketHost).isEqualTo("pdp.example.com");
                assertThat(c.rsocketPort).isEqualTo(9000);
                assertThat(c.connections).isEqualTo(4);
                assertThat(c.vtPerConnection).isEqualTo(256);
            });
        }

        @Test
        @DisplayName("parses label option")
        void whenLabelThenLabelPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--label", "Server pinned to CPUs 0-7", "-s", "\"alice\"", "-a", "\"read\"",
                    "-r", "\"doc\"");
            assertThat(cmd.label).isEqualTo("Server pinned to CPUs 0-7");
        }

        @Test
        @DisplayName("parses measurement options")
        void whenMeasurementOptionsThenPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--warmup-seconds", "10", "--measurement-seconds", "30", "-s", "\"alice\"",
                    "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd.warmupSeconds).isEqualTo(10);
            assertThat(cmd.measureSeconds).isEqualTo(30);
        }

        @Test
        @DisplayName("machine-readable defaults to false")
        void whenDefaultThenMachineReadableFalse() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd.machineReadable).isFalse();
        }

        @Test
        @DisplayName("parses machine-readable flag")
        void whenMachineReadableThenTrue() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--machine-readable", "-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd.machineReadable).isTrue();
        }
    }

    @Nested
    @DisplayName("execution validation")
    class ExecutionValidationTests {

        private StringWriter err;
        private CommandLine  cmd;

        @BeforeEach
        void setUp() {
            err = new StringWriter();
            cmd = new CommandLine(new LoadtestCommand());
            cmd.setErr(new PrintWriter(err));
        }

        @Test
        @DisplayName("missing subscription returns exit code 1")
        void whenNoSubscriptionThenExitCode1() {
            assertThat(cmd.execute()).isEqualTo(1);
            assertThat(err.toString()).contains(LoadtestCommand.ERROR_SUBSCRIPTION_MISSING);
        }
    }
}
