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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import lombok.val;

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
        double cv,
        double min,
        double max,
        double p5,
        double p95,
        List<Double> rawData,
        @Nullable Latency latency) {

    public record Latency(double mean, double p50, double p90, double p99, double p999, double min, double max) {

        public static Latency fromSamples(List<Double> nanoseconds) {
            if (nanoseconds.isEmpty()) {
                return new Latency(0, 0, 0, 0, 0, 0, 0);
            }
            val sorted = new ArrayList<>(nanoseconds);
            Collections.sort(sorted);
            val avg = nanoseconds.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new Latency(avg, pctl(sorted, 0.50), pctl(sorted, 0.90), pctl(sorted, 0.99), pctl(sorted, 0.999),
                    sorted.getFirst(), sorted.getLast());
        }

        private static double pctl(List<Double> sorted, double p) {
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

    public static BenchmarkResult fromIterations(String method, int threads, List<Double> throughputs) {
        return fromIterations(method, threads, throughputs, null);
    }

    public static BenchmarkResult fromIterations(String method, int threads, List<Double> throughputs,
            @Nullable Latency latency) {
        if (throughputs.isEmpty()) {
            return new BenchmarkResult(method, threads, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), null);
        }
        val sorted = new ArrayList<>(throughputs);
        Collections.sort(sorted);
        val mean   = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        val stddev = Math.sqrt(throughputs.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0.0));
        val cv     = mean > 0 ? (stddev / mean) * 100.0 : 0.0;
        return new BenchmarkResult(method, threads, mean, percentile(sorted, 0.50), stddev, cv, sorted.getFirst(),
                sorted.getLast(), percentile(sorted, 0.05), percentile(sorted, 0.95), List.copyOf(throughputs),
                latency);
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
