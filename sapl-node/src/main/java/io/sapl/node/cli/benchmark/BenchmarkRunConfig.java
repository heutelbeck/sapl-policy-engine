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
 * Resolved benchmark configuration after merging CLI flags with an optional
 * JSON config file. CLI flags take precedence over config file values.
 *
 * @param warmupIterations number of warmup iterations
 * @param warmupTimeSeconds seconds per warmup iteration
 * @param measurementIterations number of measurement iterations
 * @param measurementTimeSeconds seconds per measurement iteration
 * @param threads list of thread counts to benchmark
 * @param benchmarks benchmark method name filter (null = all)
 * @param output output directory (null = no file output)
 * @param timestamp run timestamp for output filenames
 */
public record BenchmarkRunConfig(
        int warmupIterations,
        int warmupTimeSeconds,
        int measurementIterations,
        int measurementTimeSeconds,
        List<Integer> threads,
        @Nullable List<String> benchmarks,
        @Nullable Path output,
        String timestamp) {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final List<String> RAW_BENCHMARKS = List.of("decideOnceRaw");

    public static BenchmarkRunConfig resolve(BenchmarkOptions opts, @Nullable BenchmarkConfig config) {
        val          ts = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        List<String> benchmarks;
        if (opts.raw) {
            benchmarks = RAW_BENCHMARKS;
        } else if (config != null) {
            benchmarks = config.benchmarks();
        } else {
            benchmarks = null;
        }
        if (config == null) {
            return new BenchmarkRunConfig(opts.warmupIterations, opts.warmupTimeSeconds, opts.measurementIterations,
                    opts.measurementTimeSeconds, List.of(opts.threads), benchmarks, opts.output, ts);
        }
        return new BenchmarkRunConfig(override(config.warmupIterations(), opts.warmupIterations, 3),
                override(config.warmupTimeSeconds(), opts.warmupTimeSeconds, 1),
                override(config.measurementIterations(), opts.measurementIterations, 5),
                override(config.measurementTimeSeconds(), opts.measurementTimeSeconds, 3),
                resolveThreads(config.threads(), opts.threads), benchmarks, resolveOutput(config.output(), opts.output),
                ts);
    }

    public String outputFileName(String mode, String method, int threadCount) {
        return "%s_%s_%s_%dthreads.json".formatted(timestamp, mode, method, threadCount);
    }

    public int estimatedDurationSeconds() {
        val perMethod       = (warmupIterations * warmupTimeSeconds + measurementIterations * measurementTimeSeconds)
                + 5;
        val methodCount     = benchmarks != null ? benchmarks.size() : 1;
        val latencyMethods  = benchmarks != null ? benchmarks.stream().filter(BLOCKING_METHODS::contains).count()
                : BLOCKING_METHODS.size();
        val throughputTotal = perMethod * methodCount;
        val latencyTotal    = perMethod * latencyMethods;
        return (int) (throughputTotal + latencyTotal) * threads.size();
    }

    static final List<String> BLOCKING_METHODS = List.of("decideOnceBlocking", "decideStreamFirst");

    private static int override(@Nullable Integer configValue, int cliValue, int defaultValue) {
        if (cliValue != defaultValue) {
            return cliValue;
        }
        return configValue != null ? configValue : defaultValue;
    }

    private static List<Integer> resolveThreads(@Nullable List<Integer> configThreads, int cliThreads) {
        if (cliThreads != 1) {
            return List.of(cliThreads);
        }
        return configThreads != null && !configThreads.isEmpty() ? configThreads : List.of(1);
    }

    private static @Nullable Path resolveOutput(@Nullable String configOutput, @Nullable Path cliOutput) {
        if (cliOutput != null) {
            return cliOutput;
        }
        return configOutput != null ? Path.of(configOutput) : null;
    }

}
