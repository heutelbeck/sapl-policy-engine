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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Embedded benchmark runner using simple timing loops. Writes return values
 * to a volatile sink to prevent dead code elimination. Supports two modes:
 * interactive (human-friendly output) and machine-readable (parseable
 * single-line output for script integration).
 */
@Slf4j
@UtilityClass
public class EmbeddedBenchmarkRunner {

    static final String ERROR_BENCHMARK_FAILED = "Error: Benchmark failed: %s.";
    static final String WARN_BENCHMARK_THREADS = "Benchmark threads did not complete within timeout.";
    static final String WARN_JSON_WRITE_FAILED = "Warning: Failed to write JSON results: %s.";

    private static final PrintWriter SILENT_OUT = new PrintWriter(OutputStream.nullOutputStream(), false,
            StandardCharsets.UTF_8);

    record BenchmarkMethod(Supplier<Object> method, int opsPerInvocation) {}

    /**
     * Runs the embedded benchmark in interactive or machine-readable mode.
     *
     * @param ctx benchmark context with PDP configuration
     * @param cfg run configuration with timing and output parameters
     * @param threads number of concurrent benchmark threads
     * @param out writer for benchmark output
     * @param err writer for error output
     * @return list of benchmark results, empty on failure
     */
    public static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
            PrintWriter err) {
        PDPComponents components = null;
        try {
            components = ctx.buildEmbeddedPdp();
            val mapper        = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscriptions = loadSubscriptions(ctx, mapper);
            val methods       = resolveMethods(components.pdp(), subscriptions, cfg.benchmarks());

            val results = cfg.machineReadable() ? measureMachineReadable(methods, cfg, threads, out, err)
                    : measureInteractive(methods, cfg, threads, out, err);

            if (!cfg.machineReadable()) {
                writeJsonOutput(results, cfg, threads, err);
            }
            return results;
        } catch (Exception e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return List.of();
        } finally {
            if (components != null) {
                components.dispose();
            }
        }
    }

    private static List<BenchmarkResult> measureMachineReadable(Map<String, BenchmarkMethod> methods,
            BenchmarkRunConfig cfg, int threads, PrintWriter out, PrintWriter err) {
        val results = new ArrayList<BenchmarkResult>();
        for (val entry : methods.entrySet()) {
            val bm = entry.getValue();

            runPhase("Warmup", cfg.warmupIterations(), cfg.warmupTimeSeconds(), threads, bm, SILENT_OUT, false);

            val opsCount   = runThroughputIteration(cfg.measurementTimeSeconds(), threads, bm.method())
                    * bm.opsPerInvocation();
            val throughput = (double) opsCount / cfg.measurementTimeSeconds();
            val latency    = measureLatency(cfg, threads, bm.method(), entry.getKey(), err);

            out.println(String.format(Locale.US, "THROUGHPUT:%.2f", throughput));
            if (latency != null) {
                out.println(String.format(Locale.US, "LATENCY:%.0f:%.0f:%.0f:%.0f:%.0f", latency.p50(), latency.p90(),
                        latency.p99(), latency.p999(), latency.max()));
            }
            out.flush();

            results.add(BenchmarkResult.fromIterations(entry.getKey(), threads, List.of(throughput), latency));
        }
        return results;
    }

    private static List<BenchmarkResult> measureInteractive(Map<String, BenchmarkMethod> methods,
            BenchmarkRunConfig cfg, int threads, PrintWriter out, PrintWriter err) {
        out.println("SAPL Embedded Benchmark (quick assessment)");
        out.println("This measures policy evaluation speed in the current process.");
        out.println("For rigorous results with JIT isolation, use sapl-benchmark-sapl4.");
        out.println();
        out.println("Method(s): %d, Thread(s): %d".formatted(methods.size(), threads));
        out.println("Warmup: %d x %d s, Measurement: %d x %d s".formatted(cfg.warmupIterations(),
                cfg.warmupTimeSeconds(), cfg.measurementIterations(), cfg.measurementTimeSeconds()));
        out.println();

        val results = new ArrayList<BenchmarkResult>();
        for (val entry : methods.entrySet()) {
            val bm = entry.getValue();
            out.println("--- %s ---".formatted(entry.getKey()));

            val warmupData = runPhase("Warmup", cfg.warmupIterations(), cfg.warmupTimeSeconds(), threads, bm, out,
                    true);
            checkWarmupConvergence(warmupData, out);

            val iterationData = runPhase("Iteration", cfg.measurementIterations(), cfg.measurementTimeSeconds(),
                    threads, bm, out, true);
            val latency       = measureLatency(cfg, threads, bm.method(), entry.getKey(), err);

            if (latency != null) {
                out.println("Latency pass...");
            }

            results.add(BenchmarkResult.fromIterations(entry.getKey(), threads, iterationData, latency));
            out.println();
        }

        printSummary(results, out);
        return results;
    }

    private static BenchmarkResult.Latency measureLatency(BenchmarkRunConfig cfg, int threads, Supplier<Object> method,
            String methodName, PrintWriter err) {
        if (!cfg.latency()) {
            return null;
        }
        System.gc();
        val histogram = new Histogram(3_600_000_000_000L, 3);
        runLatencyIteration(cfg.measurementTimeSeconds(), threads, method, histogram);
        if (cfg.output() != null) {
            writeLatencyJson(histogram, methodName, threads, cfg, err);
        }
        return latencyFromHistogram(histogram);
    }

    private static void writeLatencyJson(Histogram histogram, String methodName, int threads, BenchmarkRunConfig cfg,
            PrintWriter err) {
        val mean   = histogram.getMean();
        val stddev = histogram.getStdDeviation();
        val n      = histogram.getTotalCount();
        val sem    = n > 0 ? stddev / Math.sqrt(n) : 0.0;
        val t      = n > 1 ? 1.96 : 12.706;
        val margin = t * sem;

        val percentiles = new LinkedHashMap<String, Object>();
        percentiles.put("0.0", (double) histogram.getMinValue());
        percentiles.put("50.0", (double) histogram.getValueAtPercentile(50.0));
        percentiles.put("90.0", (double) histogram.getValueAtPercentile(90.0));
        percentiles.put("95.0", (double) histogram.getValueAtPercentile(95.0));
        percentiles.put("99.0", (double) histogram.getValueAtPercentile(99.0));
        percentiles.put("99.9", (double) histogram.getValueAtPercentile(99.9));
        percentiles.put("99.99", (double) histogram.getValueAtPercentile(99.99));
        percentiles.put("99.999", (double) histogram.getValueAtPercentile(99.999));
        percentiles.put("99.9999", (double) histogram.getValueAtPercentile(99.9999));
        percentiles.put("100.0", (double) histogram.getMaxValue());

        val rawHistogram = new ArrayList<List<Number>>();
        for (val entry : histogram.recordedValues()) {
            rawHistogram.add(List.of((double) entry.getValueIteratedTo(), entry.getCountAtValueIteratedTo()));
        }

        val metric = new LinkedHashMap<String, Object>();
        metric.put("score", mean);
        metric.put("scoreError", margin);
        metric.put("scoreConfidence", List.of(mean - margin, mean + margin));
        metric.put("scoreUnit", "ns/op");
        metric.put("scorePercentiles", percentiles);
        metric.put("rawDataHistogram", List.of(List.of(rawHistogram)));

        val record = new LinkedHashMap<String, Object>();
        record.put("benchmark", methodName);
        record.put("mode", "sample");
        record.put("threads", threads);
        record.put("runtime", "native");
        record.put("forks", 1);
        record.put("warmupIterations", cfg.warmupIterations());
        record.put("warmupTime", cfg.warmupTimeSeconds() + " s");
        record.put("measurementIterations", 1);
        record.put("measurementTime", cfg.measurementTimeSeconds() + " s");
        record.put("primaryMetric", metric);

        val prefix = cfg.outputPrefix() != null ? cfg.outputPrefix() + "_" : "";
        val path   = cfg.output().resolve(prefix + methodName + "_" + threads + "t_latency.json");
        try {
            val mapper = JsonMapper.builder().build();
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(List.of(record)));
        } catch (IOException e) {
            err.println(WARN_JSON_WRITE_FAILED.formatted(e.getMessage()));
        }
    }

    private static void writeJsonOutput(List<BenchmarkResult> results, BenchmarkRunConfig cfg, int threads,
            PrintWriter err) {
        if (cfg.output() == null) {
            return;
        }
        val rawResults = new LinkedHashMap<String, List<Double>>();
        for (val r : results) {
            rawResults.put(r.method(), r.rawData());
        }
        val fileName = cfg.outputFileName("embedded", "all", threads);
        writeJsonResults(rawResults, cfg, threads, cfg.output().resolve(fileName), err);
    }

    private static AuthorizationSubscription[] loadSubscriptions(BenchmarkContext ctx, JsonMapper mapper) {
        if (ctx.subscriptionsJson() != null) {
            AuthorizationSubscription[] list = mapper.readValue(ctx.subscriptionsJson(),
                    AuthorizationSubscription[].class);
            if (list.length > 0) {
                return list;
            }
        }
        return new AuthorizationSubscription[] {
                mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class) };
    }

    private static Map<String, BenchmarkMethod> resolveMethods(PolicyDecisionPoint pdp,
            AuthorizationSubscription[] subscriptions, List<String> filter) {
        val                                 index   = new AtomicInteger(0);
        Supplier<AuthorizationSubscription> nextSub = () -> subscriptions[Integer
                .remainderUnsigned(index.getAndIncrement(), subscriptions.length)];
        val                                 all     = new LinkedHashMap<String, BenchmarkMethod>();
        all.put("decideOnceBlocking", new BenchmarkMethod(() -> pdp.decideOnceBlocking(nextSub.get()), 1));
        all.put("decideStreamFirst", new BenchmarkMethod(() -> pdp.decide(nextSub.get()).blockFirst(), 1));

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

    private static List<Double> runPhase(String phaseName, int iterations, int seconds, int threads, BenchmarkMethod bm,
            PrintWriter out, boolean collect) {
        val results = collect ? new ArrayList<Double>(iterations) : null;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            val opsCount   = runThroughputIteration(seconds, threads, bm.method()) * bm.opsPerInvocation();
            val throughput = (double) opsCount / seconds;
            val nsPerOp    = seconds * 1_000_000_000.0 / opsCount;
            out.println("%s %d: %,.1f ops/s  (%.0f ns/op)".formatted(phaseName, i + 1, throughput, nsPerOp));
            out.flush();
            if (collect) {
                results.add(throughput);
            }
        }
        return collect ? results : List.of();
    }

    /**
     * Sink for benchmark return values. Storing to a volatile field prevents
     * the compiler from eliminating the benchmark call as dead code.
     */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    private static long runThroughputIteration(int seconds, int threadCount, Supplier<Object> method) {
        val totalOps      = new AtomicLong(0);
        val startBarrier  = new CountDownLatch(threadCount);
        val completeLatch = new CountDownLatch(threadCount);
        val threads       = new Thread[threadCount];
        val durationNanos = TimeUnit.SECONDS.toNanos(seconds);
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                startBarrier.countDown();
                try {
                    startBarrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                val    deadline   = System.nanoTime() + durationNanos;
                long   localOps   = 0;
                Object lastResult = null;
                while (System.nanoTime() < deadline) {
                    lastResult = method.get();
                    localOps++;
                }
                sink = lastResult;
                totalOps.addAndGet(localOps);
                completeLatch.countDown();
            });
            threads[t].setDaemon(true);
            threads[t].start();
        }
        awaitCompletion(completeLatch, seconds);
        return totalOps.get();
    }

    private static void runLatencyIteration(int seconds, int threadCount, Supplier<Object> method, Histogram target) {
        val startBarrier  = new CountDownLatch(threadCount);
        val completeLatch = new CountDownLatch(threadCount);
        val threads       = new Thread[threadCount];
        val durationNanos = TimeUnit.SECONDS.toNanos(seconds);
        val perThread     = new Histogram[threadCount];
        for (int t = 0; t < threadCount; t++) {
            val threadIndex = t;
            threads[t] = new Thread(() -> {
                startBarrier.countDown();
                try {
                    startBarrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                val    deadline       = System.nanoTime() + durationNanos;
                Object lastResult     = null;
                val    localHistogram = new Histogram(3_600_000_000_000L, 3);
                while (System.nanoTime() < deadline) {
                    val start = System.nanoTime();
                    lastResult = method.get();
                    localHistogram.recordValue(System.nanoTime() - start);
                }
                sink                   = lastResult;
                perThread[threadIndex] = localHistogram;
                completeLatch.countDown();
            });
            threads[t].setDaemon(true);
            threads[t].start();
        }
        awaitCompletion(completeLatch, seconds);
        for (val threadHistogram : perThread) {
            if (threadHistogram != null) {
                target.add(threadHistogram);
            }
        }
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

    private static BenchmarkResult.Latency latencyFromHistogram(Histogram histogram) {
        return new BenchmarkResult.Latency(histogram.getMean(), histogram.getValueAtPercentile(50.0),
                histogram.getValueAtPercentile(90.0), histogram.getValueAtPercentile(99.0),
                histogram.getValueAtPercentile(99.9), histogram.getMinValue(), histogram.getMaxValue());
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
