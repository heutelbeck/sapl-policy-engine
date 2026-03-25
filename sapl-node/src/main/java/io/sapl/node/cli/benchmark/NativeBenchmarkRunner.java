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
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Embedded benchmark runner for GraalVM native images. Uses simple timing
 * loops instead of JMH, which is valid for AOT-compiled code because there
 * is no JIT warmup, dead code elimination, or constant folding at runtime.
 */
@Slf4j
@UtilityClass
public class NativeBenchmarkRunner {

    static final String ERROR_BENCHMARK_FAILED = "Error: Benchmark failed: %s";
    static final String WARN_BENCHMARK_THREADS = "Benchmark threads did not complete within timeout";
    static final String WARN_JSON_WRITE_FAILED = "Warning: Failed to write JSON results: %s";

    record BenchmarkMethod(Supplier<Object> method, int opsPerInvocation) {}

    public static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
            PrintWriter err) {
        var components = (io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents) null;
        try {
            components = ctx.buildEmbeddedPdp();
            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class);
            val pdp          = components.pdp();
            val methods      = resolveMethods(pdp, subscription, cfg.benchmarks());

            out.println("# Native benchmark (embedded): %d method(s), %d thread(s)".formatted(methods.size(), threads));
            out.println("# Warmup: %d x %d s, Measurement: %d x %d s".formatted(cfg.warmupIterations(),
                    cfg.warmupTimeSeconds(), cfg.measurementIterations(), cfg.measurementTimeSeconds()));
            out.println();

            val rawResults       = new LinkedHashMap<String, List<Double>>();
            val benchmarkResults = new ArrayList<BenchmarkResult>();
            for (val entry : methods.entrySet()) {
                val bm           = entry.getValue();
                val collectNanos = bm.opsPerInvocation() == 1;
                out.println("--- %s ---".formatted(entry.getKey()));
                val warmupData = runPhase("Warmup", cfg.warmupIterations(), cfg.warmupTimeSeconds(), threads,
                        bm.method(), out, true, bm.opsPerInvocation());
                checkWarmupConvergence(warmupData, out);
                val latencySamples = collectNanos ? new ArrayList<Double>() : null;
                val iterationData  = runPhase("Iteration", cfg.measurementIterations(), cfg.measurementTimeSeconds(),
                        threads, bm.method(), out, true, bm.opsPerInvocation(), latencySamples);
                rawResults.put(entry.getKey(), iterationData);
                val latency = latencySamples != null ? BenchmarkResult.Latency.fromSamples(latencySamples) : null;
                benchmarkResults.add(BenchmarkResult.fromIterations(entry.getKey(), threads, iterationData, latency));
                out.println();
            }

            printSummary(benchmarkResults, out);

            if (cfg.output() != null) {
                val fileName = cfg.outputFileName("embedded", "all", threads);
                writeJsonResults(rawResults, cfg, threads, cfg.output().resolve(fileName), err);
            }

            return benchmarkResults;
        } catch (Exception e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return List.of();
        } finally {
            if (components != null) {
                components.dispose();
            }
        }
    }

    private static Map<String, BenchmarkMethod> resolveMethods(PolicyDecisionPoint pdp,
            AuthorizationSubscription subscription, List<String> filter) {
        val all = new LinkedHashMap<String, BenchmarkMethod>();
        all.put("decideOnceBlocking", new BenchmarkMethod(() -> pdp.decideOnceBlocking(subscription), 1));
        all.put("decideStreamFirst", new BenchmarkMethod(() -> pdp.decide(subscription).blockFirst(), 1));

        if (filter == null || filter.isEmpty()) {
            return all;
        }
        val filtered = new LinkedHashMap<String, BenchmarkMethod>();
        for (val entry : all.entrySet()) {
            if (filter.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered.isEmpty() ? all : filtered;
    }

    private static List<Double> runPhase(String phaseName, int iterations, int timePerIterationSeconds, int threads,
            Supplier<Object> method, PrintWriter out, boolean collect, int opsPerInvocation) {
        return runPhase(phaseName, iterations, timePerIterationSeconds, threads, method, out, collect, opsPerInvocation,
                null);
    }

    private static List<Double> runPhase(String phaseName, int iterations, int timePerIterationSeconds, int threads,
            Supplier<Object> method, PrintWriter out, boolean collect, int opsPerInvocation,
            @Nullable List<Double> latencySamples) {
        val results = collect ? new ArrayList<Double>(iterations) : null;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            val opsCount   = runTimedIteration(timePerIterationSeconds, threads, method, latencySamples)
                    * opsPerInvocation;
            val throughput = (double) opsCount / timePerIterationSeconds;
            val nsPerOp    = timePerIterationSeconds * 1_000_000_000.0 / opsCount;
            out.println("%s %d: %,.1f ops/s  (%.0f ns/op)".formatted(phaseName, i + 1, throughput, nsPerOp));
            out.flush();
            if (collect) {
                results.add(throughput);
            }
        }
        return collect ? results : List.of();
    }

    /**
     * Sink for benchmark return values. Storing to a volatile field prevents the
     * AOT compiler from eliminating the benchmark call as dead code, mirroring
     * JMH's Blackhole mechanism.
     */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    private static long runTimedIteration(int seconds, int threadCount, Supplier<Object> method,
            @Nullable List<Double> latencySamples) {
        val totalOps      = new AtomicLong(0);
        val startBarrier  = new CountDownLatch(threadCount);
        val completeLatch = new CountDownLatch(threadCount);
        val threads       = new Thread[threadCount];
        val collectNanos  = latencySamples != null;
        val durationNanos = TimeUnit.SECONDS.toNanos(seconds);
        @SuppressWarnings("unchecked")
        val perThread     = collectNanos ? new List[threadCount] : null;
        for (int t = 0; t < threadCount; t++) {
            val threadIndex = t;
            threads[t] = createBenchmarkThread(method, durationNanos, collectNanos, totalOps, perThread, threadIndex,
                    startBarrier, completeLatch);
            threads[t].start();
        }
        awaitCompletion(completeLatch, seconds);
        if (collectNanos) {
            mergeLatencySamples(perThread, latencySamples);
        }
        return totalOps.get();
    }

    private static Thread createBenchmarkThread(Supplier<Object> method, long durationNanos, boolean collectNanos,
            AtomicLong totalOps, @Nullable List<Double>[] perThread, int threadIndex, CountDownLatch startBarrier,
            CountDownLatch completeLatch) {
        val thread = new Thread(() -> {
            // Signal ready, then wait for all threads before starting measurement
            startBarrier.countDown();
            try {
                startBarrier.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // Deadline set per-thread after barrier, so all threads measure the same
            // duration
            val    deadline   = System.nanoTime() + durationNanos;
            long   localOps   = 0;
            Object lastResult = null;
            // Pre-allocate for ~100K ops/s to minimize resizing during measurement
            val estimatedOps = (int) (durationNanos / 1_000_000_000L * 100_000L);
            val localNanos   = collectNanos ? new ArrayList<Double>(Math.max(estimatedOps, 1_000)) : null;
            while (System.nanoTime() < deadline) {
                val start = collectNanos ? System.nanoTime() : 0;
                lastResult = method.get();
                if (collectNanos) {
                    localNanos.add((double) (System.nanoTime() - start));
                }
                localOps++;
            }
            sink = lastResult;
            totalOps.addAndGet(localOps);
            if (collectNanos) {
                perThread[threadIndex] = localNanos;
            }
            completeLatch.countDown();
        });
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitCompletion(CountDownLatch latch, int seconds) {
        try {
            if (!latch.await(seconds + 5L, TimeUnit.SECONDS)) {
                log.warn(WARN_BENCHMARK_THREADS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void mergeLatencySamples(List<Double>[] perThread, List<Double> latencySamples) {
        for (val samples : perThread) {
            if (samples != null) {
                latencySamples.addAll(samples);
            }
        }
    }

    static final double WARMUP_CV_THRESHOLD = 10.0;

    private static void checkWarmupConvergence(List<Double> warmupData, PrintWriter out) {
        if (warmupData.size() < 2) {
            return;
        }
        val last     = warmupData.getLast();
        val previous = warmupData.get(warmupData.size() - 2);
        val mean     = (last + previous) / 2.0;
        if (mean <= 0) {
            return;
        }
        val diff = Math.abs(last - previous);
        val cv   = (diff / mean) * 100.0;
        if (cv > WARMUP_CV_THRESHOLD) {
            out.println("WARNING: Warmup may not have converged (last two iterations differ by %.1f%%). ".formatted(cv)
                    + "Consider increasing warmup iterations.");
        }
    }

    private static void printSummary(List<BenchmarkResult> results, PrintWriter out) {
        BenchmarkResult.printSummary(results, out);
    }

    private static void writeJsonResults(Map<String, List<Double>> rawResults, BenchmarkRunConfig cfg, int threads,
            Path outputPath, PrintWriter err) {
        val resultList = new ArrayList<Map<String, Object>>();
        for (val entry : rawResults.entrySet()) {
            val r    = BenchmarkResult.fromIterations(entry.getKey(), threads, entry.getValue());
            val item = new LinkedHashMap<String, Object>();
            item.put("benchmark", r.method());
            item.put("mode", "thrpt");
            item.put("threads", threads);
            item.put("warmupIterations", cfg.warmupIterations());
            item.put("warmupTime", cfg.warmupTimeSeconds() + " s");
            item.put("measurementIterations", cfg.measurementIterations());
            item.put("measurementTime", cfg.measurementTimeSeconds() + " s");

            val metric = new LinkedHashMap<String, Object>();
            metric.put("score", r.mean());
            metric.put("scoreCi95", r.ci95());
            metric.put("scoreStdDev", r.stddev());
            metric.put("scoreUnit", "ops/s");
            metric.put("scorePercentiles", Map.of("5.0", r.p5(), "50.0", r.median(), "95.0", r.p95()));
            metric.put("rawData", r.rawData());
            item.put("primaryMetric", metric);

            resultList.add(item);
        }
        try {
            val mapper = JsonMapper.builder().build();
            Files.writeString(outputPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultList));
        } catch (IOException e) {
            err.println(WARN_JSON_WRITE_FAILED.formatted(e.getMessage()));
        }
    }
}
