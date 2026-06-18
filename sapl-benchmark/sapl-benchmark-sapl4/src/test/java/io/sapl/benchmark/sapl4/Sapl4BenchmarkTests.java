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
}
