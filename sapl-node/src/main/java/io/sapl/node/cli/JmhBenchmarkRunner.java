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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
            PrintWriter err) {
        try {
            val benchmarkClass = ctx.isRemote() ? RemoteBenchmark.class : EmbeddedBenchmark.class;
            val includePattern = buildIncludePattern(benchmarkClass, cfg);
            val builder        = new OptionsBuilder().include(includePattern).forks(0)
                    .warmupIterations(cfg.warmupIterations()).warmupTime(TimeValue.seconds(cfg.warmupTimeSeconds()))
                    .measurementIterations(cfg.measurementIterations())
                    .measurementTime(TimeValue.seconds(cfg.measurementTimeSeconds())).threads(threads)
                    .param("contextJson", ctx.toJson()).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS)
                    .shouldDoGC(true).syncIterations(true);

            if (cfg.output() != null) {
                val mode       = ctx.isRemote() ? "remote" : "embedded";
                val resultPath = cfg.output().resolve(cfg.outputFileName(mode, "all", threads)).toString();
                builder.resultFormat(ResultFormatType.JSON).result(resultPath);
            }

            val runResults       = new Runner(builder.build()).run();
            val benchmarkResults = toBenchmarkResults(runResults);
            printSummary(benchmarkResults, out);
            return benchmarkResults;
        } catch (RunnerException e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return List.of();
        }
    }

    private static String buildIncludePattern(Class<?> benchmarkClass, BenchmarkRunConfig cfg) {
        val base = benchmarkClass.getName();
        if (cfg.benchmarks() == null || cfg.benchmarks().isEmpty()) {
            return base;
        }
        val methods = String.join("|", cfg.benchmarks());
        return base + "\\.(" + methods + ")";
    }

    private static List<BenchmarkResult> toBenchmarkResults(Collection<RunResult> runResults) {
        val results = new ArrayList<BenchmarkResult>();
        for (val rr : runResults) {
            val label   = rr.getPrimaryResult().getLabel();
            val threads = rr.getParams().getThreads();
            val rawData = new ArrayList<Double>();
            for (val br : rr.getBenchmarkResults()) {
                for (val ir : br.getIterationResults()) {
                    rawData.add(ir.getPrimaryResult().getScore());
                }
            }
            results.add(BenchmarkResult.fromIterations(label, threads, rawData));
        }
        return results;
    }

    private static void printSummary(List<BenchmarkResult> results, PrintWriter out) {
        out.println();
        out.println("%-30s %10s %15s %10s".formatted("Benchmark", "Threads", "Throughput", "Error"));
        out.println("-".repeat(70));
        for (val r : results) {
            out.println(String.format(Locale.US, "%-30s %10d %,13.1f ops/s %,10.1f ops/s", r.method(), r.threads(),
                    r.mean(), r.stddev()));
        }
        out.flush();
    }

}
