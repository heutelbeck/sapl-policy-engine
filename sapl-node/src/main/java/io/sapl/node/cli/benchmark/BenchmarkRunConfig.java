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

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.sapl.node.cli.options.BenchmarkOptions;
import lombok.val;

/**
 * Resolved benchmark configuration from CLI flags.
 *
 * @param warmupIterations number of warmup iterations
 * @param warmupTimeSeconds seconds per warmup iteration
 * @param measurementIterations number of measurement iterations
 * @param measurementTimeSeconds seconds per measurement iteration
 * @param threads list of thread counts to benchmark
 * @param benchmarks benchmark method name filter
 * @param latency whether to run a separate latency pass
 * @param machineReadable whether to output single-line parseable results
 * @param output output directory (null = no file output)
 * @param timestamp run timestamp for output filenames
 */
public record BenchmarkRunConfig(
        int warmupIterations,
        int warmupTimeSeconds,
        int measurementIterations,
        int measurementTimeSeconds,
        List<Integer> threads,
        List<String> benchmarks,
        boolean latency,
        boolean machineReadable,
        @Nullable Path output,
        String timestamp) {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Creates a run configuration from CLI options.
     *
     * @param opts the parsed CLI options
     * @return the resolved configuration
     */
    public static BenchmarkRunConfig resolve(BenchmarkOptions opts) {
        val ts         = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        val benchmarks = List.of(opts.benchmark);
        return new BenchmarkRunConfig(opts.warmupIterations, opts.warmupTimeSeconds, opts.measurementIterations,
                opts.measurementTimeSeconds, List.of(opts.threads), benchmarks, opts.latency, opts.machineReadable,
                opts.output, ts);
    }

    /**
     * Generates a timestamped output filename.
     *
     * @param mode the benchmark mode (e.g., "embedded")
     * @param method the benchmark method name
     * @param threadCount the thread count
     * @return the formatted filename
     */
    public String outputFileName(String mode, String method, int threadCount) {
        return "%s_%s_%s_%dthreads.json".formatted(timestamp, mode, method, threadCount);
    }

}
