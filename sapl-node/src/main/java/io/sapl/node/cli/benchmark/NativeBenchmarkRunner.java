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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.Unpooled;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Benchmark runner for GraalVM native images. Uses simple timing loops instead
 * of JMH, which is valid for AOT-compiled code because there is no JIT
 * warmup, dead code elimination, or constant folding at runtime.
 */
@Slf4j
@UtilityClass
public class NativeBenchmarkRunner {

    // Derived from Little's Law: L = cores x (1 + W_io / W_cpu).
    // For fast policy evaluation behind RSocket/HTTP, W_cpu is small relative
    // to W_io (network round-trip), giving a high IO/CPU ratio. Using a
    // multiplier of 10 is conservative for localhost benchmarks (ratio ~5-6)
    // and reasonable for remote benchmarks (ratio ~20+). Clamped to [32, 512]
    // to avoid under-saturation on small machines or memory pressure on large ones.
    static final int CONCURRENT_BATCH = Math.clamp(Runtime.getRuntime().availableProcessors() * 10, 32, 512);

    static final String ERROR_BENCHMARK_FAILED = "Error: Benchmark failed: %s";
    static final String WARN_BENCHMARK_THREADS = "Benchmark threads did not complete within timeout";
    static final String WARN_JSON_WRITE_FAILED = "Warning: Failed to write JSON results: %s";

    record BenchmarkMethod(Supplier<Object> method, int opsPerInvocation) {}

    public static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
            PrintWriter err) {
        PDPComponents components = null;
        try {
            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class);
            val mode         = ctx.isRemote() ? "remote" : "embedded";

            PolicyDecisionPoint pdp;
            if (ctx.isRemote()) {
                pdp = RemotePdpFactory.create(ctx);
            } else {
                components = ctx.buildEmbeddedPdp();
                pdp        = components.pdp();
            }

            val methods = resolveMethods(pdp, subscription, ctx, cfg.benchmarks());

            out.println("# Native benchmark (%s): %d method(s), %d thread(s)".formatted(mode, methods.size(), threads));
            out.println("# Warmup: %d x %d s, Measurement: %d x %d s".formatted(cfg.warmupIterations(),
                    cfg.warmupTimeSeconds(), cfg.measurementIterations(), cfg.measurementTimeSeconds()));
            out.println();

            val rawResults       = new LinkedHashMap<String, List<Double>>();
            val benchmarkResults = new ArrayList<BenchmarkResult>();
            for (val entry : methods.entrySet()) {
                val bm           = entry.getValue();
                val collectNanos = bm.opsPerInvocation() == 1;
                out.println("--- %s ---".formatted(entry.getKey()));
                runPhase("Warmup", cfg.warmupIterations(), cfg.warmupTimeSeconds(), threads, bm.method(), out, false,
                        bm.opsPerInvocation());
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
                val fileName = cfg.outputFileName(mode, "all", threads);
                writeJsonResults(rawResults, cfg, threads, cfg.output().resolve(fileName), err);
            }

            return benchmarkResults;
        } catch (RuntimeException | SSLException e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return List.of();
        } finally {
            if (components != null) {
                components.dispose();
            }
        }
    }

    private static Map<String, BenchmarkMethod> resolveMethods(PolicyDecisionPoint pdp,
            AuthorizationSubscription subscription, BenchmarkContext ctx, List<String> filter) {
        val all = new LinkedHashMap<String, BenchmarkMethod>();
        all.put("decideOnceBlocking", new BenchmarkMethod(() -> pdp.decideOnceBlocking(subscription), 1));
        all.put("decideStreamFirst", new BenchmarkMethod(() -> pdp.decide(subscription).blockFirst(), 1));

        if (ctx.isRemote()) {
            all.put("decideOnceConcurrent",
                    new BenchmarkMethod(
                            () -> Flux.range(0, CONCURRENT_BATCH)
                                    .flatMap(i -> pdp.decideOnce(subscription), CONCURRENT_BATCH).collectList().block(),
                            CONCURRENT_BATCH));

            if (ctx.remoteUrl() != null) {
                val body = ctx.subscriptionJson().getBytes(StandardCharsets.UTF_8);
                val raw  = HttpClient.create().baseUrl(ctx.remoteUrl())
                        .headers(h -> h.add("Content-Type", "application/json"));
                all.put("decideOnceRaw",
                        new BenchmarkMethod(() -> Flux.range(0, CONCURRENT_BATCH)
                                .flatMap(i -> raw.post().uri("/api/pdp/decide-once")
                                        .send(Mono.fromSupplier(() -> Unpooled.wrappedBuffer(body)))
                                        .responseSingle((resp, buf) -> buf.asByteArray()), CONCURRENT_BATCH)
                                .collectList().block(), CONCURRENT_BATCH));
            }
        }

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
        val totalOps     = new AtomicLong(0);
        val deadline     = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        val latch        = new CountDownLatch(threadCount);
        val threads      = new Thread[threadCount];
        val collectNanos = latencySamples != null;
        @SuppressWarnings("unchecked")
        val perThread    = collectNanos ? new List[threadCount] : null;
        for (int t = 0; t < threadCount; t++) {
            val threadIndex = t;
            threads[t] = createBenchmarkThread(method, deadline, collectNanos, totalOps, perThread, threadIndex, latch);
            threads[t].start();
        }
        awaitCompletion(latch, seconds);
        if (collectNanos) {
            mergeLatencySamples(perThread, latencySamples);
        }
        return totalOps.get();
    }

    private static Thread createBenchmarkThread(Supplier<Object> method, long deadline, boolean collectNanos,
            AtomicLong totalOps, @Nullable List<Double>[] perThread, int threadIndex, CountDownLatch latch) {
        val thread = new Thread(() -> {
            long   localOps   = 0;
            Object lastResult = null;
            val    localNanos = collectNanos ? new ArrayList<Double>(10_000) : null;
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
            latch.countDown();
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
