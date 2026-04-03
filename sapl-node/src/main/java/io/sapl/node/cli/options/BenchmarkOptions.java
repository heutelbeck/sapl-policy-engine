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
package io.sapl.node.cli.options;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * CLI options for the benchmark command controlling warmup, measurement, and
 * concurrency parameters.
 */
public class BenchmarkOptions {

    @Option(names = "--warmup-iterations", defaultValue = "3", description = "Number of warmup iterations before measurement")
    public int warmupIterations;

    @Option(names = "--warmup-time", defaultValue = "45", description = "Duration of each warmup iteration in seconds")
    public int warmupTimeSeconds;

    @Option(names = "--measurement-iterations", defaultValue = "5", description = "Number of measurement iterations")
    public int measurementIterations;

    @Option(names = "--measurement-time", defaultValue = "45", description = "Duration of each measurement iteration in seconds")
    public int measurementTimeSeconds;

    @Option(names = { "-t", "--threads" }, defaultValue = "1", description = "Number of concurrent benchmark threads")
    public int threads;

    @Option(names = { "-b",
            "--benchmark" }, defaultValue = "decideOnceBlocking", description = "Benchmark method to run (decideOnceBlocking, decideStreamFirst, noOp)")
    public String benchmark;

    @Option(names = "--latency", defaultValue = "true", description = "Run a separate latency measurement pass after throughput")
    public boolean latency;

    @Option(names = { "-o", "--output" }, description = "Output directory for benchmark results (JSON, Markdown, CSV)")
    public Path output;

    @Option(names = "--machine-readable", defaultValue = "false", description = "Output single-line parseable results for script integration")
    public boolean machineReadable;

    @Option(names = "--output-prefix", description = "Filename prefix for output files (e.g., scenario_indexing)")
    public String outputPrefix;

}
