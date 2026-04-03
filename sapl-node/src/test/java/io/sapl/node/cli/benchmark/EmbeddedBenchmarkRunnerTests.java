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
package io.sapl.node.cli.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("EmbeddedBenchmarkRunner")
class EmbeddedBenchmarkRunnerTests {

    private static final String TEST_POLICIES_DIR = Path.of("src/test/resources/it/policies/single-pdp")
            .toAbsolutePath().toString();

    private static BenchmarkRunConfig interactiveConfig(List<String> benchmarks, boolean latency) {
        return new BenchmarkRunConfig(1, 1, 1, 1, List.of(1), benchmarks, latency, false, null, null,
                "20260323-120000");
    }

    private static BenchmarkRunConfig machineReadableConfig(List<String> benchmarks, boolean latency) {
        return new BenchmarkRunConfig(1, 1, 1, 1, List.of(1), benchmarks, latency, true, null, null, "20260323-120000");
    }

    private static BenchmarkContext createContext() {
        val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val subscription = AuthorizationSubscription.of("alice", "eat", "apple");
        val subJson      = mapper.writeValueAsString(subscription);
        return new BenchmarkContext(subJson, null, TEST_POLICIES_DIR, "DIRECTORY");
    }

    private record RunOutput(String stdout, String stderr, List<BenchmarkResult> results) {}

    private static RunOutput runBenchmark(BenchmarkContext ctx, BenchmarkRunConfig cfg) {
        val out     = new StringWriter();
        val err     = new StringWriter();
        val results = EmbeddedBenchmarkRunner.run(ctx, cfg, 1, new PrintWriter(out, true), new PrintWriter(err, true));
        return new RunOutput(out.toString(), err.toString(), results);
    }

    @Nested
    @DisplayName("interactive mode")
    class InteractiveTests {

        private StringWriter out;
        private StringWriter err;

        @BeforeEach
        void setUp() {
            out = new StringWriter();
            err = new StringWriter();
            val ctx     = createContext();
            val cfg     = interactiveConfig(List.of("decideOnceBlocking"), true);
            val results = EmbeddedBenchmarkRunner.run(ctx, cfg, 1, new PrintWriter(out, true),
                    new PrintWriter(err, true));
            assertThat(results).as("runner should return results").isNotNull();
        }

        @Test
        @DisplayName("produces no errors on stderr")
        void whenValidInput_thenNoErrors() {
            assertThat(err.toString()).as("stderr should be empty but was: %s", err).isEmpty();
        }

        @Test
        @DisplayName("output contains benchmark header and iteration results")
        void whenBenchmarkRuns_thenOutputContainsHeaders() {
            assertThat(out.toString()).contains("SAPL Embedded Benchmark").contains("Warmup 1:")
                    .contains("Iteration 1:").contains("ops/s");
        }

        @Test
        @DisplayName("output contains summary table with benchmark name")
        void whenBenchmarkCompletes_thenSummaryTablePresent() {
            assertThat(out.toString()).contains("Benchmark").contains("Threads").contains("Throughput")
                    .contains("decideOnceBlocking");
        }

        @Test
        @DisplayName("does not contain machine-readable markers")
        void whenInteractive_thenNoMachineReadableOutput() {
            assertThat(out.toString()).doesNotContain("THROUGHPUT:").doesNotContain("LATENCY:");
        }

    }

    @Nested
    @DisplayName("machine-readable mode")
    class MachineReadableTests {

        @Test
        @DisplayName("outputs THROUGHPUT line with numeric value")
        void whenMachineReadable_thenThroughputLinePresent() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.stdout()).containsPattern("THROUGHPUT:\\d+\\.\\d+");
        }

        @Test
        @DisplayName("outputs LATENCY line when latency enabled")
        void whenLatencyEnabled_thenLatencyLinePresent() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), true));
            assertThat(output.stdout()).containsPattern("LATENCY:\\d+:\\d+:\\d+:\\d+:\\d+");
        }

        @Test
        @DisplayName("omits LATENCY line when latency disabled")
        void whenLatencyDisabled_thenNoLatencyLine() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.stdout()).doesNotContain("LATENCY:");
        }

        @Test
        @DisplayName("does not contain interactive headers or summaries")
        void whenMachineReadable_thenNoInteractiveOutput() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.stdout()).doesNotContain("SAPL Embedded Benchmark").doesNotContain("Warmup 1:")
                    .doesNotContain("Iteration 1:").doesNotContain("Benchmark");
        }

        @Test
        @DisplayName("returns non-empty result list")
        void whenMachineReadable_thenResultsReturned() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.results()).hasSize(1);
            assertThat(output.results().getFirst().mean()).isPositive();
        }

        @Test
        @DisplayName("produces no errors on stderr")
        void whenValidInput_thenNoErrors() {
            val output = runBenchmark(createContext(), machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.stderr()).isEmpty();
        }

    }

    @Nested
    @DisplayName("multiple benchmark methods")
    class MultiMethodTests {

        @Test
        @DisplayName("runs all methods when no filter specified in interactive mode")
        void whenNoFilter_thenAllMethodsRun() {
            val output = runBenchmark(createContext(), interactiveConfig(null, false));
            assertThat(output.stdout()).contains("--- decideOnceBlocking ---").contains("--- decideStreamFirst ---");
        }

        @Test
        @DisplayName("runs only filtered methods when filter specified")
        void whenFilterSpecified_thenOnlyMatchingMethodsRun() {
            val output = runBenchmark(createContext(), interactiveConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.stdout()).contains("--- decideOnceBlocking ---")
                    .doesNotContain("--- decideStreamFirst ---");
        }

        @Test
        @DisplayName("machine-readable mode outputs one THROUGHPUT line per method")
        void whenMachineReadableMultipleMethods_thenMultipleThroughputLines() {
            val output = runBenchmark(createContext(), machineReadableConfig(null, false));
            val lines  = output.stdout().lines().filter(l -> l.startsWith("THROUGHPUT:")).count();
            assertThat(lines).isEqualTo(2);
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorTests {

        @Test
        @DisplayName("non-existent policy directory returns empty results in interactive mode")
        void whenInvalidPolicyDir_thenEmptyResults() {
            val output = runBenchmark(new BenchmarkContext("{}", null, "/nonexistent/path", "DIRECTORY"),
                    interactiveConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.results()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("malformed subscription returns empty results in interactive mode")
        void whenMalformedSubscription_thenFailsGracefully() {
            val output = runBenchmark(new BenchmarkContext("not json", null, TEST_POLICIES_DIR, "DIRECTORY"),
                    interactiveConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.results()).satisfiesAnyOf(r -> assertThat(r).isNull(), r -> assertThat(r).isEmpty());
        }

        @Test
        @DisplayName("non-existent policy directory returns empty results in machine-readable mode")
        void whenInvalidPolicyDirMachineReadable_thenEmptyResults() {
            val output = runBenchmark(new BenchmarkContext("{}", null, "/nonexistent/path", "DIRECTORY"),
                    machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.results()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("malformed subscription returns empty results in machine-readable mode")
        void whenMalformedSubscriptionMachineReadable_thenFailsGracefully() {
            val output = runBenchmark(new BenchmarkContext("not json", null, TEST_POLICIES_DIR, "DIRECTORY"),
                    machineReadableConfig(List.of("decideOnceBlocking"), false));
            assertThat(output.results()).satisfiesAnyOf(r -> assertThat(r).isNull(), r -> assertThat(r).isEmpty());
        }

    }

}
