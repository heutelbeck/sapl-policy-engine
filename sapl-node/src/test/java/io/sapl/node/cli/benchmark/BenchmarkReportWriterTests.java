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
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("BenchmarkResult and BenchmarkReportWriter")
class BenchmarkReportWriterTests {

    private static final List<Double> SAMPLE_DATA = List.of(10000.0, 12000.0, 11000.0, 13000.0, 11500.0);

    @Nested
    @DisplayName("BenchmarkResult statistics")
    class ResultStatisticsTests {

        @Test
        @DisplayName("computes mean, median, stddev, min, max from iteration data")
        void whenFromIterations_thenStatisticsCorrect() {
            val result = BenchmarkResult.fromIterations("decideOnceBlocking", 4, SAMPLE_DATA);
            assertThat(result).satisfies(r -> {
                assertThat(r.method()).isEqualTo("decideOnceBlocking");
                assertThat(r.threads()).isEqualTo(4);
                assertThat(r.mean()).isCloseTo(11500.0, within(0.1));
                assertThat(r.median()).isCloseTo(11500.0, within(0.1));
                assertThat(r.min()).isEqualTo(10000.0);
                assertThat(r.max()).isEqualTo(13000.0);
                assertThat(r.stddev()).isGreaterThan(0);
                assertThat(r.cv()).isGreaterThan(0);
                assertThat(r.rawData()).hasSize(5);
            });
        }

        @Test
        @DisplayName("handles single iteration")
        void whenSingleIteration_thenAllFieldsEqual() {
            val result = BenchmarkResult.fromIterations("test", 1, List.of(5000.0));
            assertThat(result).satisfies(r -> {
                assertThat(r.mean()).isEqualTo(5000.0);
                assertThat(r.median()).isEqualTo(5000.0);
                assertThat(r.min()).isEqualTo(5000.0);
                assertThat(r.max()).isEqualTo(5000.0);
                assertThat(r.stddev()).isZero();
                assertThat(r.cv()).isZero();
            });
        }

        @Test
        @DisplayName("handles empty iteration list")
        void whenEmpty_thenAllZeros() {
            val result = BenchmarkResult.fromIterations("test", 1, List.of());
            assertThat(result).satisfies(r -> {
                assertThat(r.mean()).isZero();
                assertThat(r.rawData()).isEmpty();
            });
        }

    }

    @Nested
    @DisplayName("Markdown report generation")
    class MarkdownTests {

        @Test
        @DisplayName("contains methodology section with policy source")
        void whenEmbedded_thenMethodologyContainsPolicySource() {
            val results = List.of(BenchmarkResult.fromIterations("decideOnceBlocking", 1, SAMPLE_DATA));
            val ctx     = new BenchmarkContext("{}", "/tmp/policies", "DIRECTORY");
            val cfg     = new BenchmarkRunConfig(3, 1, 5, 3, List.of(1), null, true, false, null, "20260323-120000");
            val md      = BenchmarkReportWriter.buildMarkdown(results, ctx, cfg, "timing loop");
            assertThat(md).contains("# Benchmark Report").contains("## Methodology").contains("/tmp/policies")
                    .contains("DIRECTORY").contains("timing loop");
        }

        @Test
        @DisplayName("contains results table with statistics")
        void whenResults_thenTableContainsAllColumns() {
            val results = List.of(BenchmarkResult.fromIterations("decideOnceBlocking", 1, SAMPLE_DATA));
            val ctx     = new BenchmarkContext("{}", "/tmp", "DIRECTORY");
            val cfg     = new BenchmarkRunConfig(3, 1, 5, 3, List.of(1), null, true, false, null, "20260323-120000");
            val md      = BenchmarkReportWriter.buildMarkdown(results, ctx, cfg, "timing loop");
            assertThat(md).contains("## Results").contains("Mean (ops/s)").contains("Median (ops/s)").contains("StdDev")
                    .contains("CV%").contains("p5").contains("p95");
        }

        @Test
        @DisplayName("contains latency section derived from throughput")
        void whenResults_thenLatencyTablePresent() {
            val results = List.of(BenchmarkResult.fromIterations("decideOnceBlocking", 1, SAMPLE_DATA));
            val ctx     = new BenchmarkContext("{}", "/tmp", "DIRECTORY");
            val cfg     = new BenchmarkRunConfig(3, 1, 5, 3, List.of(1), null, true, false, null, "20260323-120000");
            val md      = BenchmarkReportWriter.buildMarkdown(results, ctx, cfg, "timing loop");
            assertThat(md).contains("## Latency").contains("ns/op");
        }

        @Test
        @DisplayName("contains scaling table when multiple thread counts present")
        void whenMultipleThreads_thenScalingTablePresent() {
            val results = List.of(BenchmarkResult.fromIterations("decideOnceBlocking", 1, List.of(10000.0)),
                    BenchmarkResult.fromIterations("decideOnceBlocking", 4, List.of(35000.0)));
            val ctx     = new BenchmarkContext("{}", "/tmp", "DIRECTORY");
            val cfg     = new BenchmarkRunConfig(3, 1, 5, 3, List.of(1, 4), null, true, false, null, "20260323-120000");
            val md      = BenchmarkReportWriter.buildMarkdown(results, ctx, cfg, "timing loop");
            assertThat(md).contains("## Scaling Efficiency").contains("Scaling vs 1T").contains("3.5x");
        }

    }

    @Nested
    @DisplayName("CSV report generation")
    class CsvTests {

        @Test
        @DisplayName("contains header row and data rows")
        void whenResults_thenCsvHasHeaderAndData() {
            val results = List.of(BenchmarkResult.fromIterations("decideOnceBlocking", 1, SAMPLE_DATA),
                    BenchmarkResult.fromIterations("decideOnceReactive", 1, SAMPLE_DATA));
            val csv     = BenchmarkReportWriter.buildCsv(results);
            val lines   = csv.split("\n");
            assertThat(lines).hasSize(3);
            assertThat(lines[0]).contains("method,threads,mean_ops_s,ci95,median_ops_s,stddev,cv_pct");
            assertThat(lines[1]).startsWith("decideOnceBlocking,1,");
            assertThat(lines[2]).startsWith("decideOnceReactive,1,");
        }

        @Test
        @DisplayName("includes derived latency column")
        void whenCsv_thenContainsNsPerOp() {
            val results = List.of(BenchmarkResult.fromIterations("test", 1, List.of(1000000.0)));
            val csv     = BenchmarkReportWriter.buildCsv(results);
            assertThat(csv).contains("mean_ns_op").contains("1000.00");
        }

    }

}
