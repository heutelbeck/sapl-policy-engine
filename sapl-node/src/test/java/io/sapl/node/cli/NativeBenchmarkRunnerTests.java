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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("NativeBenchmarkRunner")
class NativeBenchmarkRunnerTests {

    private static final String TEST_POLICIES_DIR = Path.of("src/test/resources/it/policies/single-pdp")
            .toAbsolutePath().toString();

    private static BenchmarkRunConfig quickConfig(List<String> benchmarks) {
        return new BenchmarkRunConfig(1, 1, 1, 1, List.of(1), benchmarks, null, "20260323-120000");
    }

    private static BenchmarkContext createContext() {
        val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val subscription = AuthorizationSubscription.of("alice", "eat", "apple");
        val subJson      = mapper.writeValueAsString(subscription);
        return BenchmarkContext.embedded(subJson, TEST_POLICIES_DIR, "DIRECTORY");
    }

    @Nested
    @DisplayName("successful benchmark execution")
    class SuccessTests {

        private StringWriter out;
        private StringWriter err;

        @BeforeEach
        void setUp() {
            out = new StringWriter();
            err = new StringWriter();
            val ctx     = createContext();
            val cfg     = quickConfig(List.of("decideOnceBlocking"));
            val results = NativeBenchmarkRunner.run(ctx, cfg, 1, new PrintWriter(out, true),
                    new PrintWriter(err, true));
            assertThat(results).as("runner should return results, not null").isNotNull();
        }

        @Test
        @DisplayName("produces no errors on stderr")
        void whenValidPoliciesAndSubscription_thenNoErrors() {
            assertThat(err.toString()).as("stderr should be empty but was: %s", err).isEmpty();
        }

        @Test
        @DisplayName("output contains benchmark header and iteration results")
        void whenBenchmarkRuns_thenOutputContainsHeaders() {
            assertThat(out.toString()).contains("# Native benchmark (embedded):").contains("Warmup 1:")
                    .contains("Iteration 1:").contains("ops/s");
        }

        @Test
        @DisplayName("output contains summary table with benchmark name")
        void whenBenchmarkCompletes_thenSummaryTablePresent() {
            assertThat(out.toString()).contains("Benchmark").contains("Threads").contains("Throughput")
                    .contains("decideOnceBlocking");
        }

    }

    private record RunOutput(String stdout, String stderr, List<BenchmarkResult> results) {}

    private static RunOutput runBenchmark(BenchmarkContext ctx, BenchmarkRunConfig cfg) {
        val out     = new StringWriter();
        val err     = new StringWriter();
        val results = NativeBenchmarkRunner.run(ctx, cfg, 1, new PrintWriter(out, true), new PrintWriter(err, true));
        return new RunOutput(out.toString(), err.toString(), results);
    }

    @Nested
    @DisplayName("multiple benchmark methods")
    class MultiMethodTests {

        @Test
        @DisplayName("runs all three methods when no filter specified")
        void whenNoFilter_thenAllMethodsRun() {
            val output = runBenchmark(createContext(), quickConfig(null));
            assertThat(output.stdout()).contains("--- decideOnceBlocking ---").contains("--- decideOnceReactive ---")
                    .contains("--- decideStreamFirst ---");
        }

        @Test
        @DisplayName("runs only filtered methods when filter specified")
        void whenFilterSpecified_thenOnlyMatchingMethodsRun() {
            val output = runBenchmark(createContext(), quickConfig(List.of("decideOnceBlocking")));
            assertThat(output.stdout()).contains("--- decideOnceBlocking ---")
                    .doesNotContain("--- decideOnceReactive ---").doesNotContain("--- decideStreamFirst ---");
        }

    }

    @Nested
    @DisplayName("error handling")
    class ErrorTests {

        @Test
        @DisplayName("non-existent policy directory returns empty results")
        void whenInvalidPolicyDir_thenEmptyResults() {
            val output = runBenchmark(BenchmarkContext.embedded("{}", "/nonexistent/path", "DIRECTORY"),
                    quickConfig(List.of("decideOnceBlocking")));
            assertThat(output.results()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("malformed subscription JSON returns null or empty results")
        void whenMalformedSubscription_thenFailsGracefully() {
            val output = runBenchmark(BenchmarkContext.embedded("not json", TEST_POLICIES_DIR, "DIRECTORY"),
                    quickConfig(List.of("decideOnceBlocking")));
            assertThat(output.results()).satisfiesAnyOf(r -> assertThat(r).isNull(), r -> assertThat(r).isEmpty());
        }

    }

}
