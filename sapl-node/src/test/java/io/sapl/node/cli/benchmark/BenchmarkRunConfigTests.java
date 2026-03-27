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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.node.cli.commands.BenchmarkCommand;
import io.sapl.node.cli.options.BenchmarkOptions;
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

    private static BenchmarkOptions optionsWithMachineReadable() {
        val cmd = new BenchmarkCommand();
        new CommandLine(cmd).parseArgs("--machine-readable", "-s", "\"a\"", "-a", "\"b\"", "-r", "\"c\"");
        return cmd.benchmarkOptions;
    }

    @Nested
    @DisplayName("resolve from CLI options")
    class ResolveTests {

        @Test
        @DisplayName("uses CLI defaults")
        void whenDefaults_thenCliDefaultsApplied() {
            val cfg = BenchmarkRunConfig.resolve(defaultOptions());
            assertThat(cfg).satisfies(c -> {
                assertThat(c.warmupIterations()).isEqualTo(3);
                assertThat(c.warmupTimeSeconds()).isEqualTo(45);
                assertThat(c.measurementIterations()).isEqualTo(5);
                assertThat(c.measurementTimeSeconds()).isEqualTo(45);
                assertThat(c.threads()).containsExactly(1);
                assertThat(c.benchmarks()).containsExactly("decideOnceBlocking");
                assertThat(c.latency()).isTrue();
                assertThat(c.machineReadable()).isFalse();
                assertThat(c.output()).isNull();
                assertThat(c.timestamp()).matches("\\d{8}-\\d{6}");
            });
        }

        @Test
        @DisplayName("CLI thread override produces single-element list")
        void whenCliThreadOverride_thenSingleElementList() {
            val cfg = BenchmarkRunConfig.resolve(optionsWithThreads(8));
            assertThat(cfg.threads()).containsExactly(8);
        }

        @Test
        @DisplayName("machine-readable flag resolves to true when set")
        void whenMachineReadableFlag_thenResolvedTrue() {
            val cfg = BenchmarkRunConfig.resolve(optionsWithMachineReadable());
            assertThat(cfg.machineReadable()).isTrue();
        }

    }

    @Nested
    @DisplayName("output file naming")
    class OutputNamingTests {

        @Test
        @DisplayName("generates timestamp-prefixed filename")
        void whenOutputFileName_thenTimestampPrefixed() {
            val cfg  = BenchmarkRunConfig.resolve(defaultOptions());
            val name = cfg.outputFileName("embedded", "decideOnceBlocking", 4);
            assertThat(name).matches("\\d{8}-\\d{6}_embedded_decideOnceBlocking_4threads\\.json");
        }

    }

}
