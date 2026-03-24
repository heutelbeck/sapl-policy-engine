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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class JmhBenchmarkRunner {

    static final String ERROR_BENCHMARK_FAILED = "Error: JMH benchmark failed: %s";

    private static final List<String> BLOCKING_METHODS = List.of("decideOnceBlocking", "decideStreamFirst");

    public static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
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
            val latencyByMethod  = runLatencyPass(benchmarkClass, cfg, threads, ctx, err);
            val benchmarkResults = toBenchmarkResults(runResults, latencyByMethod);
            printSummary(benchmarkResults, out);
            return benchmarkResults;
        } catch (RunnerException e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return List.of();
        }
    }

    private static Map<String, BenchmarkResult.Latency> runLatencyPass(Class<?> benchmarkClass, BenchmarkRunConfig cfg,
            int threads, BenchmarkContext ctx, PrintWriter err) {
        val latencyMethods = resolveLatencyMethods(benchmarkClass, cfg);
        if (latencyMethods.isEmpty()) {
            return Map.of();
        }
        try {
            val pattern    = benchmarkClass.getName() + "\\.(" + String.join("|", latencyMethods) + ")";
            val latencyRun = new OptionsBuilder().include(pattern).forks(0).warmupIterations(cfg.warmupIterations())
                    .warmupTime(TimeValue.seconds(cfg.warmupTimeSeconds()))
                    .measurementIterations(cfg.measurementIterations())
                    .measurementTime(TimeValue.seconds(cfg.measurementTimeSeconds())).threads(threads)
                    .param("contextJson", ctx.toJson()).mode(Mode.SampleTime).timeUnit(TimeUnit.NANOSECONDS)
                    .shouldDoGC(true).syncIterations(true).build();
            val results    = new Runner(latencyRun).run();
            val map        = new HashMap<String, BenchmarkResult.Latency>();
            for (val rr : results) {
                val label = rr.getPrimaryResult().getLabel();
                val stats = rr.getPrimaryResult().getStatistics();
                map.put(label,
                        new BenchmarkResult.Latency(stats.getMean(), stats.getPercentile(50.0),
                                stats.getPercentile(90.0), stats.getPercentile(99.0), stats.getPercentile(99.9),
                                stats.getMin(), stats.getMax()));
            }
            return map;
        } catch (RunnerException e) {
            err.println("Warning: Latency pass failed: %s".formatted(e.getMessage()));
            return Map.of();
        }
    }

    private static List<String> resolveLatencyMethods(Class<?> benchmarkClass, BenchmarkRunConfig cfg) {
        if (cfg.benchmarks() != null && !cfg.benchmarks().isEmpty()) {
            return cfg.benchmarks().stream().filter(BLOCKING_METHODS::contains).toList();
        }
        return BLOCKING_METHODS;
    }

    private static String buildIncludePattern(Class<?> benchmarkClass, BenchmarkRunConfig cfg) {
        val base = benchmarkClass.getName();
        if (cfg.benchmarks() == null || cfg.benchmarks().isEmpty()) {
            return base;
        }
        val methods = String.join("|", cfg.benchmarks());
        return base + "\\.(" + methods + ")";
    }

    private static List<BenchmarkResult> toBenchmarkResults(Collection<RunResult> runResults,
            Map<String, BenchmarkResult.Latency> latencyByMethod) {
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
            results.add(BenchmarkResult.fromIterations(label, threads, rawData, latencyByMethod.get(label)));
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
