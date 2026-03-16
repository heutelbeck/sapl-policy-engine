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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;
import picocli.CommandLine;

@DisplayName("test command")
class TestCommandTests {

    private static final String POLICY = """
            policy "greet"
            permit
                action == "greet";
            """;

    private static final String TEST_PASSING = """
            requirement "greeting permitted" {
                given
                    - document "greet"
                scenario "when greeting"
                    when "alice" attempts "greet" on "world"
                    expect permit;
            }
            """;

    private static final String TEST_FAILING = """
            requirement "greeting denied" {
                given
                    - document "greet"
                scenario "when greeting"
                    when "alice" attempts "greet" on "world"
                    expect deny;
            }
            """;

    private static CommandLine newCommand(StringWriter out, StringWriter err) {
        val cmd = new CommandLine(new TestCommand());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd;
    }

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelpThenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new TestCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("test", "--dir", "--testdir", "--output", "--sonar",
                    "--policy-hit-ratio");
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("empty directory returns exit code 1 with error message")
        void whenEmptyDirThenExitCode1(@TempDir Path tempDir) {
            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("No .sapl files found");
        }

        @Test
        @DisplayName("directory with policies but no tests returns exit code 1")
        void whenPoliciesButNoTestsThenExitCode1(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("test.sapl"), "policy \"p\" permit");

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("No .sapltest files found");
        }

        @Test
        @DisplayName("nonexistent directory returns exit code 1")
        void whenNonexistentDirThenExitCode1(@TempDir Path tempDir) {
            val nonexistent = tempDir.resolve("does-not-exist");
            val out         = new StringWriter();
            val err         = new StringWriter();
            val cmd         = newCommand(out, err);
            val exitCode    = cmd.execute("--dir", nonexistent.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Failed to read files");
        }

    }

    @Nested
    @DisplayName("test execution")
    class ExecutionTests {

        @Test
        @DisplayName("passing tests return exit code 0")
        void whenAllTestsPassThenExitCode0(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_PASSING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html");

            assertThat(exitCode).as("stdout: %s, stderr: %s", out, err).isZero();
            assertThat(out.toString()).contains("PASS", "1 passed");
        }

        @Test
        @DisplayName("failing tests return exit code 2")
        void whenTestsFailThenExitCode2(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_FAILING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html");

            assertThat(exitCode).isEqualTo(2);
            assertThat(out.toString()).contains("FAIL", "1 failed");
        }

    }

    @Nested
    @DisplayName("--testdir option")
    class TestdirTests {

        @Test
        @DisplayName("discovers policies from --dir and tests from --testdir")
        void whenTestdirSetThenDiscoversSeparately(@TempDir Path tempDir) throws Exception {
            val policyDir = tempDir.resolve("policies");
            val testDir   = tempDir.resolve("tests");
            Files.createDirectories(policyDir);
            Files.createDirectories(testDir);
            Files.writeString(policyDir.resolve("greet.sapl"), POLICY);
            Files.writeString(testDir.resolve("greet.sapltest"), TEST_PASSING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", policyDir.toString(), "--testdir", testDir.toString(), "--output",
                    tempDir.resolve("coverage").toString(), "--no-html");

            assertThat(exitCode).as("stdout: %s, stderr: %s", out, err).isZero();
            assertThat(out.toString()).contains("PASS", "1 passed");
        }

        @Test
        @DisplayName("missing test files in --testdir returns exit code 1")
        void whenTestdirEmptyThenExitCode1(@TempDir Path tempDir) throws Exception {
            val policyDir = tempDir.resolve("policies");
            val testDir   = tempDir.resolve("tests");
            Files.createDirectories(policyDir);
            Files.createDirectories(testDir);
            Files.writeString(policyDir.resolve("greet.sapl"), POLICY);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", policyDir.toString(), "--testdir", testDir.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("No .sapltest files found");
        }

    }

    @Nested
    @DisplayName("quality gate")
    class QualityGateTests {

        @Test
        @DisplayName("quality gate met returns exit code 0")
        void whenQualityGateMetThenExitCode0(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_PASSING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html", "--policy-hit-ratio", "0");

            assertThat(exitCode).as("stdout: %s, stderr: %s", out, err).isZero();
        }

        @Test
        @DisplayName("quality gate not met returns exit code 3")
        void whenQualityGateNotMetThenExitCode3(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_PASSING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html", "--branch-coverage-ratio", "100");

            assertThat(exitCode).isEqualTo(3);
            assertThat(err.toString()).contains("Quality gate not met");
        }

        @Test
        @DisplayName("test failures take precedence over quality gate")
        void whenTestsFailAndQualityGateNotMetThenExitCode2(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_FAILING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html", "--branch-coverage-ratio", "100");

            assertThat(exitCode).isEqualTo(2);
        }

        @Test
        @DisplayName("invalid threshold returns exit code 1")
        void whenInvalidThresholdThenExitCode1(@TempDir Path tempDir) {
            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--policy-hit-ratio", "101");

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("must be between 0 and 100");
        }

    }

    @Nested
    @DisplayName("report generation")
    class ReportGenerationTests {

        @Test
        @DisplayName("--sonar generates SonarQube report")
        void whenSonarEnabledThenGeneratesReport(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("greet.sapl"), POLICY);
            Files.writeString(tempDir.resolve("greet.sapltest"), TEST_PASSING);

            val out      = new StringWriter();
            val err      = new StringWriter();
            val cmd      = newCommand(out, err);
            val exitCode = cmd.execute("--dir", tempDir.toString(), "--output", tempDir.resolve("coverage").toString(),
                    "--no-html", "--sonar");

            assertThat(exitCode).as("stdout: %s, stderr: %s", out, err).isZero();
            assertThat(out.toString()).contains("SonarQube coverage report:");
            assertThat(tempDir.resolve("coverage/sonar/sonar-generic-coverage.xml")).exists();
        }

    }

}
