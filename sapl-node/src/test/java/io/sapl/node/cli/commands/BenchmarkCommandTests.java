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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;
import picocli.CommandLine;

@DisplayName("benchmark command")
class BenchmarkCommandTests {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelp_thenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new BenchmarkCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("benchmark", "--dir", "--warmup-iterations", "--measurement-iterations",
                    "--threads");
        }

        @Test
        @DisplayName("--dir sets policy source directory")
        void whenDirOption_thenPolicySourceDirIsSet() {
            val cmd = new BenchmarkCommand();
            new CommandLine(cmd).parseArgs("--dir", "/tmp/policies", "-s", "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
            assertThat(cmd.policySource.dir).isEqualTo(Path.of("/tmp/policies"));
        }

        @Test
        @DisplayName("benchmark options have sensible defaults")
        void whenNoOptions_thenDefaultsApplied() {
            val cmd = new BenchmarkCommand();
            new CommandLine(cmd).parseArgs("-s", "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
            assertThat(cmd.benchmarkOptions).satisfies(opts -> {
                assertThat(opts.warmupIterations).isEqualTo(3);
                assertThat(opts.warmupTimeSeconds).isEqualTo(45);
                assertThat(opts.measurementIterations).isEqualTo(5);
                assertThat(opts.measurementTimeSeconds).isEqualTo(45);
                assertThat(opts.threads).isEqualTo(1);
                assertThat(opts.output).isNull();
            });
        }

        @Test
        @DisplayName("all benchmark options are configurable via CLI flags")
        void whenAllBenchmarkOptions_thenAllParsed() {
            val cmd = new BenchmarkCommand();
            new CommandLine(cmd).parseArgs("--warmup-iterations", "10", "--warmup-time", "5",
                    "--measurement-iterations", "20", "--measurement-time", "15", "-t", "8", "-o", "/tmp/results", "-s",
                    "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
            assertThat(cmd.benchmarkOptions).satisfies(opts -> {
                assertThat(opts.warmupIterations).isEqualTo(10);
                assertThat(opts.warmupTimeSeconds).isEqualTo(5);
                assertThat(opts.measurementIterations).isEqualTo(20);
                assertThat(opts.measurementTimeSeconds).isEqualTo(15);
                assertThat(opts.threads).isEqualTo(8);
                assertThat(opts.output).isEqualTo(Path.of("/tmp/results"));
            });
        }

        @ParameterizedTest(name = "rejects {0}")
        @DisplayName("mutually exclusive options are rejected")
        @MethodSource
        void whenMutuallyExclusiveOptions_thenNonZeroExitCode(String description, String[] args) {
            val err = new StringWriter();
            val cmd = new CommandLine(new BenchmarkCommand());
            cmd.setErr(new PrintWriter(err));
            val exitCode = cmd.execute(args);
            assertThat(exitCode).isNotZero();
        }

        static Stream<Arguments> whenMutuallyExclusiveOptions_thenNonZeroExitCode() {
            return Stream.of(arguments("--dir and --bundle", new String[] { "--dir", "/a", "--bundle", "/b" }),
                    arguments("named flags and --file",
                            new String[] { "-s", "\"x\"", "-a", "\"y\"", "-r", "\"z\"", "-f", "input.json" }));
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
            cmd = new CommandLine(new BenchmarkCommand());
            cmd.setErr(new PrintWriter(err));
        }

        @Test
        @DisplayName("missing subscription returns exit code 1 with error message")
        void whenNoSubscription_thenExitCode1WithError() {
            assertThat(cmd.execute("--dir", "/tmp/policies")).isEqualTo(1);
            assertThat(err.toString()).contains(BenchmarkCommand.ERROR_SUBSCRIPTION_MISSING);
        }

        @Test
        @DisplayName("non-existent policy directory returns exit code 1")
        void whenInvalidPolicyDir_thenExitCode1() {
            assertThat(cmd.execute("--dir", "/nonexistent/path", "-s", "\"alice\"", "-a", "\"read\"", "-r", "\"doc\""))
                    .isEqualTo(1);
            assertThat(err.toString()).contains("not found");
        }

    }

}
