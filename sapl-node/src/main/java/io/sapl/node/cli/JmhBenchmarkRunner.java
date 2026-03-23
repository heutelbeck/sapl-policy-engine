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

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Benchmark runner using JMH for JAR mode. Uses {@code forks(0)} because
 * Spring Boot's fat JAR classloader prevents JMH from forking new JVM
 * processes.
 */
@UtilityClass
class JmhBenchmarkRunner {

    static final String ERROR_BENCHMARK_FAILED = "Error: JMH benchmark failed: %s";

    static int run(BenchmarkContext ctx, BenchmarkOptions opts, PrintWriter out, PrintWriter err) {
        try {
            val builder = new OptionsBuilder().include(EmbeddedBenchmark.class.getName()).forks(0)
                    .warmupIterations(opts.warmupIterations).warmupTime(TimeValue.seconds(opts.warmupTimeSeconds))
                    .measurementIterations(opts.measurementIterations)
                    .measurementTime(TimeValue.seconds(opts.measurementTimeSeconds)).threads(opts.threads)
                    .param("contextJson", ctx.toJson()).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS)
                    .shouldDoGC(true).syncIterations(true);

            if (opts.output != null) {
                val resultPath = opts.output.resolve("results.json").toString();
                builder.resultFormat(ResultFormatType.JSON).result(resultPath);
            }

            val results = new Runner(builder.build()).run();
            printSummary(results, out);
            return 0;
        } catch (RunnerException e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return 1;
        }
    }

    private static void printSummary(Collection<RunResult> results, PrintWriter out) {
        out.println();
        out.println("%-30s %10s %15s %10s".formatted("Benchmark", "Threads", "Throughput", "Error"));
        out.println("-".repeat(70));
        for (val result : results) {
            val primary    = result.getPrimaryResult();
            val label      = primary.getLabel();
            val score      = primary.getScore();
            val scoreError = primary.getScoreError();
            val unit       = primary.getScoreUnit();
            val threads    = result.getParams().getThreads();
            out.println(String.format(Locale.US, "%-30s %10d %,13.1f %s %,10.1f %s", label, threads, score, unit,
                    scoreError, unit));
        }
        out.flush();
    }

}
