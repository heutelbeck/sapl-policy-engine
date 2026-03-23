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
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import picocli.CommandLine;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("NativeBenchmarkRunner")
class NativeBenchmarkRunnerTests {

    private static final String TEST_POLICIES_DIR = Path.of("src/test/resources/it/policies/single-pdp")
            .toAbsolutePath().toString();

    private BenchmarkOptions createOptions(int warmupIterations, int warmupTime, int measurementIterations,
            int measurementTime, int threads) {
        val benchCmd = new BenchmarkCommand();
        new CommandLine(benchCmd).parseArgs("--warmup-iterations", String.valueOf(warmupIterations), "--warmup-time",
                String.valueOf(warmupTime), "--measurement-iterations", String.valueOf(measurementIterations),
                "--measurement-time", String.valueOf(measurementTime), "-t", String.valueOf(threads), "-s", "\"alice\"",
                "-a", "\"eat\"", "-r", "\"apple\"");
        return benchCmd.benchmarkOptions;
    }

    private BenchmarkContext createContext() {
        val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val subscription = AuthorizationSubscription.of("alice", "eat", "apple");
        val subJson      = mapper.writeValueAsString(subscription);
        return new BenchmarkContext(subJson, TEST_POLICIES_DIR, "DIRECTORY");
    }

    @Nested
    @DisplayName("successful benchmark execution")
    class SuccessTests {

        @Test
        @DisplayName("returns exit code 0 with valid policies and subscription")
        void whenValidPoliciesAndSubscription_thenExitCode0() {
            val out      = new StringWriter();
            val err      = new StringWriter();
            val errPw    = new PrintWriter(err, true);
            val outPw    = new PrintWriter(out, true);
            val ctx      = createContext();
            val opts     = createOptions(1, 1, 1, 1, 1);
            val exitCode = NativeBenchmarkRunner.run(ctx, opts, outPw, errPw);
            assertThat(err.toString()).as("stderr should be empty but was: %s", err).isEmpty();
            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("output contains warmup and measurement headers")
        void whenBenchmarkRuns_thenOutputContainsHeaders() {
            val out  = new StringWriter();
            val err  = new StringWriter();
            val ctx  = createContext();
            val opts = createOptions(1, 1, 1, 1, 1);
            NativeBenchmarkRunner.run(ctx, opts, new PrintWriter(out), new PrintWriter(err));
            val output = out.toString();
            assertThat(output).contains("# Native benchmark: decideOnceBlocking").contains("Warmup 1:")
                    .contains("Iteration 1:").contains("Result \"decideOnceBlocking\":").contains("ops/s");
        }

        @Test
        @DisplayName("reports positive throughput for a simple permit policy")
        void whenSimplePolicy_thenPositiveThroughput() {
            val out  = new StringWriter();
            val err  = new StringWriter();
            val ctx  = createContext();
            val opts = createOptions(1, 1, 1, 1, 1);
            NativeBenchmarkRunner.run(ctx, opts, new PrintWriter(out), new PrintWriter(err));
            val output = out.toString();
            assertThat(output).contains("Iteration 1:").contains("ops/s");
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorTests {

        @Test
        @DisplayName("returns exit code 1 with non-existent policy directory")
        void whenInvalidPolicyDir_thenExitCode1() {
            val out      = new StringWriter();
            val err      = new StringWriter();
            val ctx      = new BenchmarkContext("{}", "/nonexistent/path", "DIRECTORY");
            val opts     = createOptions(1, 1, 1, 1, 1);
            val exitCode = NativeBenchmarkRunner.run(ctx, opts, new PrintWriter(out), new PrintWriter(err));
            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Error:");
        }

        @Test
        @DisplayName("returns exit code 1 with malformed subscription JSON")
        void whenMalformedSubscription_thenExitCode1() {
            val out      = new StringWriter();
            val err      = new StringWriter();
            val ctx      = new BenchmarkContext("not json", TEST_POLICIES_DIR, "DIRECTORY");
            val opts     = createOptions(1, 1, 1, 1, 1);
            val exitCode = NativeBenchmarkRunner.run(ctx, opts, new PrintWriter(out), new PrintWriter(err));
            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Error:");
        }

    }

}
