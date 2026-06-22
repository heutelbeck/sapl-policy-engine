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
package io.sapl.benchmark.sapl4;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sapl4Benchmark CLI contract")
class Sapl4BenchmarkTests {

    @Nested
    @DisplayName("indexing validation")
    class IndexingValidation {

        @Test
        @DisplayName("when --indexing is unknown then it exits 1 with a clean error and no stack trace")
        void whenIndexingUnknownThenExitsOneWithCleanError() {
            val benchmark   = new Sapl4Benchmark();
            val commandLine = new CommandLine(benchmark);
            val errWriter   = new StringWriter();
            commandLine.setErr(new PrintWriter(errWriter));

            val exitCode = commandLine.execute("--indexing", "FOO");

            assertThat(exitCode).isEqualTo(1);
            assertThat(errWriter.toString()).contains("Unknown indexing strategy").contains("FOO");
        }
    }

    @Nested
    @DisplayName("reported statistics windowing")
    class StatisticsWindowing {

        @Test
        @DisplayName("final statistics use only the trailing convergence window, not pre-convergence warmup forks")
        void whenWarmupForksPresentThenStatsUseTrailingWindowOnly() throws Exception {
            val benchmark = new Sapl4Benchmark();
            val window    = Sapl4Benchmark.class.getDeclaredField("convergenceWindow");
            window.setAccessible(true);
            window.setInt(benchmark, 3);

            // Two low warmup forks then a stable high window. All-5 mean is 606; window
            // mean is 1,000.
            val forks = List.of(10.0, 20.0, 1000.0, 1010.0, 990.0);
            val sw    = new StringWriter();
            val out   = new PrintWriter(sw);

            Method printResults = null;
            for (val method : Sapl4Benchmark.class.getDeclaredMethods()) {
                if ("printResults".equals(method.getName())) {
                    printResults = method;
                    break;
                }
            }
            printResults.setAccessible(true);
            printResults.invoke(benchmark, forks, null, out);
            out.flush();

            assertThat(sw.toString()).contains("1,000 ops/s").contains("3 of 5 run").doesNotContain("606 ops/s");
        }
    }
}
