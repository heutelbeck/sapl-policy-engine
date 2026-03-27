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
        @DisplayName("--help shows usage without error")
        void whenHelp_thenExitCode0() {
            val cmd      = new CommandLine(new LoadtestCommand());
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("parses HTTP options with defaults")
        void whenDefaults_thenHttpOptionsPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd).satisfies(c -> {
                assertThat(c.rsocket).isFalse();
                assertThat(c.url).isEqualTo("http://localhost:8443");
                assertThat(c.concurrency).isEqualTo(64);
            });
        }

        @Test
        @DisplayName("parses RSocket options")
        void whenRsocket_thenRsocketOptionsPopulated() {
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
        void whenLabel_thenLabelPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--label", "Server pinned to CPUs 0-7", "-s", "\"alice\"", "-a", "\"read\"",
                    "-r", "\"doc\"");
            assertThat(cmd.label).isEqualTo("Server pinned to CPUs 0-7");
        }

        @Test
        @DisplayName("parses measurement options")
        void whenMeasurementOptions_thenPopulated() {
            val cmd = new LoadtestCommand();
            new CommandLine(cmd).parseArgs("--warmup-seconds", "10", "--measurement-seconds", "30", "-s", "\"alice\"",
                    "-a", "\"read\"", "-r", "\"doc\"");
            assertThat(cmd.warmupSeconds).isEqualTo(10);
            assertThat(cmd.measureSeconds).isEqualTo(30);
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
        void whenNoSubscription_thenExitCode1() {
            assertThat(cmd.execute()).isEqualTo(1);
            assertThat(err.toString()).contains(LoadtestCommand.ERROR_SUBSCRIPTION_MISSING);
        }
    }
}
