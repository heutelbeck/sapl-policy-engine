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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;
import picocli.CommandLine;

@DisplayName("BenchmarkRunConfig")
class BenchmarkRunConfigTests {

    private static BenchmarkOptions defaultOptions() {
        val cmd = new BenchmarkCommand();
        new CommandLine(cmd).parseArgs("-s", "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
        return cmd.benchmarkOptions;
    }

    private static BenchmarkOptions optionsWithThreads(int threads) {
        val cmd = new BenchmarkCommand();
        new CommandLine(cmd).parseArgs("-t", String.valueOf(threads), "-s", "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
        return cmd.benchmarkOptions;
    }

    @Nested
    @DisplayName("resolve without config file")
    class WithoutConfigTests {

        @Test
        @DisplayName("uses CLI defaults when no config file")
        void whenNoConfig_thenCliDefaults() {
            val cfg = BenchmarkRunConfig.resolve(defaultOptions(), null);
            assertThat(cfg).satisfies(c -> {
                assertThat(c.warmupIterations()).isEqualTo(3);
                assertThat(c.warmupTimeSeconds()).isEqualTo(1);
                assertThat(c.measurementIterations()).isEqualTo(5);
                assertThat(c.measurementTimeSeconds()).isEqualTo(3);
                assertThat(c.threads()).containsExactly(1);
                assertThat(c.benchmarks()).isNull();
                assertThat(c.output()).isNull();
                assertThat(c.timestamp()).matches("\\d{8}-\\d{6}");
            });
        }

        @Test
        @DisplayName("CLI thread override produces single-element list")
        void whenCliThreadOverride_thenSingleElementList() {
            val cfg = BenchmarkRunConfig.resolve(optionsWithThreads(8), null);
            assertThat(cfg.threads()).containsExactly(8);
        }

    }

    @Nested
    @DisplayName("resolve with config file")
    class WithConfigTests {

        @Test
        @DisplayName("config file values override defaults")
        void whenConfigProvided_thenConfigValuesUsed() {
            val config = new BenchmarkConfig(10, 5, 20, 10, List.of(1, 4, 8), List.of("decideOnceBlocking"), null);
            val cfg    = BenchmarkRunConfig.resolve(defaultOptions(), config);
            assertThat(cfg).satisfies(c -> {
                assertThat(c.warmupIterations()).isEqualTo(10);
                assertThat(c.warmupTimeSeconds()).isEqualTo(5);
                assertThat(c.measurementIterations()).isEqualTo(20);
                assertThat(c.measurementTimeSeconds()).isEqualTo(10);
                assertThat(c.threads()).containsExactly(1, 4, 8);
                assertThat(c.benchmarks()).containsExactly("decideOnceBlocking");
            });
        }

        @Test
        @DisplayName("CLI flags take precedence over config file")
        void whenCliOverridesConfig_thenCliWins() {
            val config  = new BenchmarkConfig(10, 5, 20, 10, List.of(1, 4), null, null);
            val cliOpts = optionsWithThreads(16);
            val cfg     = BenchmarkRunConfig.resolve(cliOpts, config);
            assertThat(cfg.threads()).containsExactly(16);
        }

    }

    @Nested
    @DisplayName("output file naming")
    class OutputNamingTests {

        @Test
        @DisplayName("generates timestamp-prefixed filename")
        void whenOutputFileName_thenTimestampPrefixed() {
            val cfg  = BenchmarkRunConfig.resolve(defaultOptions(), null);
            val name = cfg.outputFileName("embedded", "decideOnceBlocking", 4);
            assertThat(name).matches("\\d{8}-\\d{6}_embedded_decideOnceBlocking_4threads\\.json");
        }

    }

    @Nested
    @DisplayName("duration estimation")
    class DurationTests {

        @Test
        @DisplayName("estimates duration from warmup + measurement + overhead")
        void whenEstimated_thenCalculatesCorrectly() {
            val config    = new BenchmarkConfig(5, 10, 10, 10, List.of(1, 4), null, null);
            val cfg       = BenchmarkRunConfig.resolve(defaultOptions(), config);
            val estimated = cfg.estimatedDurationSeconds();
            assertThat(estimated).isEqualTo((5 * 10 + 10 * 10 + 5) * 2);
        }

    }

    @Nested
    @DisplayName("BenchmarkConfig JSON loading")
    class ConfigLoadTests {

        @Test
        @DisplayName("loads config from JSON file")
        void whenValidJson_thenLoadsSuccessfully(@TempDir Path tempDir) throws IOException {
            val configFile = tempDir.resolve("bench.json");
            Files.writeString(configFile, """
                    {"warmupIterations": 5, "threads": [1, 2, 4], "benchmarks": ["decideOnceBlocking"]}
                    """);
            val config = BenchmarkConfig.load(configFile);
            assertThat(config).satisfies(c -> {
                assertThat(c.warmupIterations()).isEqualTo(5);
                assertThat(c.threads()).containsExactly(1, 2, 4);
                assertThat(c.benchmarks()).containsExactly("decideOnceBlocking");
                assertThat(c.measurementIterations()).isNull();
            });
        }

    }

}
