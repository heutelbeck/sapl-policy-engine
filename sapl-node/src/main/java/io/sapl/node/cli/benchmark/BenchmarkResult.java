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

import lombok.val;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Statistical summary of a single benchmark method run at a given thread count.
 * Computed from per-iteration throughput measurements and optional measured
 * latency percentiles.
 *
 * @param method benchmark method name
 * @param threads number of concurrent threads
 * @param mean mean throughput in ops/s
 * @param median median (p50) throughput in ops/s
 * @param stddev standard deviation of throughput
 * @param ci95 95% confidence interval half-width (t-distribution)
 * @param cv coefficient of variation (stddev/mean) as percentage
 * @param min minimum iteration throughput
 * @param max maximum iteration throughput
 * @param p5 5th percentile throughput
 * @param p95 95th percentile throughput
 * @param rawData per-iteration throughput values
 * @param latency measured per-request latency percentiles (null if not
 * measured)
 */
public record BenchmarkResult(
        String method,
        int threads,
        double mean,
        double median,
        double stddev,
        double ci95,
        double cv,
        double min,
        double max,
        double p5,
        double p95,
        List<Double> rawData,
        @Nullable Latency latency) {

    public record Latency(double mean, double p50, double p90, double p99, double p999, double min, double max) {

        /**
         * Computes latency percentiles from raw nanosecond samples.
         *
         * @param nanoseconds per-request latency samples in nanoseconds
         * @return latency statistics with percentiles
         */
        public static Latency fromSamples(List<Double> nanoseconds) {
            if (nanoseconds.isEmpty()) {
                return new Latency(0, 0, 0, 0, 0, 0, 0);
            }
            val sorted = new ArrayList<>(nanoseconds);
            Collections.sort(sorted);
            val avg = nanoseconds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new Latency(avg, percentile(sorted, 0.50), percentile(sorted, 0.90), percentile(sorted, 0.99),
                    percentile(sorted, 0.999), sorted.getFirst(), sorted.getLast());
        }
    }

    /**
     * Creates a result from per-iteration throughput values without latency data.
     *
     * @param method the benchmark method name
     * @param threads the thread count
     * @param throughputs per-iteration throughput values in ops/s
     * @return the computed result with statistics and 95% CI
     */
    public static BenchmarkResult fromIterations(String method, int threads, List<Double> throughputs) {
        return fromIterations(method, threads, throughputs, null);
    }

    /**
     * Creates a result from per-iteration throughput values with optional
     * measured latency data.
     *
     * @param method the benchmark method name
     * @param threads the thread count
     * @param throughputs per-iteration throughput values in ops/s
     * @param latency measured per-request latency (null if not measured)
     * @return the computed result with statistics, 95% CI, and latency
     */
    public static BenchmarkResult fromIterations(String method, int threads, List<Double> throughputs,
            @Nullable Latency latency) {
        if (throughputs.isEmpty()) {
            return new BenchmarkResult(method, threads, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), null);
        }
        val sorted = new ArrayList<>(throughputs);
        Collections.sort(sorted);
        val mean   = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        val n      = throughputs.size();
        val stddev = n > 1 ? Math.sqrt(throughputs.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / (n - 1))
                : 0.0;
        val ci95   = n > 1 ? tCritical95(n - 1) * stddev / Math.sqrt(n) : 0.0;
        val cv     = mean > 0 ? (stddev / mean) * 100.0 : 0.0;
        return new BenchmarkResult(method, threads, mean, percentile(sorted, 0.50), stddev, ci95, cv, sorted.getFirst(),
                sorted.getLast(), percentile(sorted, 0.05), percentile(sorted, 0.95), List.copyOf(throughputs),
                latency);
    }

    // t-distribution critical values for 95% CI (two-tailed, alpha=0.05).
    // Index 0 = df=1, index 1 = df=2, etc. For df>30, use z=1.96.
    private static final double[] T_CRITICAL_95 = { 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262,
            2.228, 2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064,
            2.060, 2.056, 2.052, 2.048, 2.045, 2.042 };

    private static double tCritical95(int degreesOfFreedom) {
        if (degreesOfFreedom <= 0) {
            return 0.0;
        }
        if (degreesOfFreedom <= T_CRITICAL_95.length) {
            return T_CRITICAL_95[degreesOfFreedom - 1];
        }
        return 1.96;
    }

    /**
     * Prints a formatted summary table of benchmark results.
     *
     * @param results the benchmark results
     * @param out the writer for output
     */
    static void printSummary(List<BenchmarkResult> results, PrintWriter out) {
        out.println("%-30s %10s %15s %15s".formatted("Benchmark", "Threads", "Throughput", "95% CI"));
        out.println("-".repeat(75));
        for (val r : results) {
            out.println(String.format(Locale.US, "%-30s %10d %13.1f ops/s +/- %10.1f ops/s", r.method(), r.threads(),
                    r.mean(), r.ci95()));
            if (r.latency() != null) {
                out.println(String.format(Locale.US,
                        "  Latency: p50=%.0f ns  p90=%.0f ns  p99=%.0f ns  p99.9=%.0f ns  max=%.0f ns",
                        r.latency().p50(), r.latency().p90(), r.latency().p99(), r.latency().p999(),
                        r.latency().max()));
            }
        }
        out.flush();
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        val index = p * (sorted.size() - 1);
        val lower = (int) Math.floor(index);
        val upper = Math.min(lower + 1, sorted.size() - 1);
        val frac  = index - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

}
