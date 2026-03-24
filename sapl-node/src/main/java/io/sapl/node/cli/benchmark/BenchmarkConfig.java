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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.json.JsonMapper;

/**
 * Configuration for reproducible benchmark runs, loaded from a JSON file via
 * {@code --config}. All fields are optional; CLI flags override config values.
 *
 * @param warmupIterations number of warmup iterations
 * @param warmupTimeSeconds duration of each warmup iteration in seconds
 * @param measurementIterations number of measurement iterations
 * @param measurementTimeSeconds duration of each measurement iteration in
 * seconds
 * @param threads list of thread counts for scalability runs
 * @param benchmarks filter benchmark methods by name substring
 * @param output output directory for JSON results
 */
public record BenchmarkConfig(
        @Nullable Integer warmupIterations,
        @Nullable Integer warmupTimeSeconds,
        @Nullable Integer measurementIterations,
        @Nullable Integer measurementTimeSeconds,
        @Nullable List<Integer> threads,
        @Nullable List<String> benchmarks,
        @Nullable String output) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static BenchmarkConfig load(Path path) throws IOException {
        return MAPPER.readValue(Files.readString(path), BenchmarkConfig.class);
    }

}
