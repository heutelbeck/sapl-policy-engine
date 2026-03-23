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

import java.io.IOException;
import java.io.PrintWriter;
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

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Benchmark runner for GraalVM native images. Uses simple timing loops instead
 * of JMH, which is valid for AOT-compiled code because there is no JIT
 * warmup, dead code elimination, or constant folding at runtime.
 */
@UtilityClass
class NativeBenchmarkRunner {

    static final String ERROR_BENCHMARK_FAILED = "Error: Benchmark failed: %s";
    static final String WARN_JSON_WRITE_FAILED = "Warning: Failed to write JSON results: %s";

    static List<BenchmarkResult> run(BenchmarkContext ctx, BenchmarkRunConfig cfg, int threads, PrintWriter out,
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
                components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of(ctx.policiesPath()))
                        .build();
                pdp        = components.pdp();
            }

            val methods = resolveMethods(pdp, subscription, cfg.benchmarks());

            out.println("# Native benchmark (%s): %d method(s), %d thread(s)".formatted(mode, methods.size(), threads));
            out.println("# Warmup: %d x %d s, Measurement: %d x %d s".formatted(cfg.warmupIterations(),
                    cfg.warmupTimeSeconds(), cfg.measurementIterations(), cfg.measurementTimeSeconds()));
            out.println();

            val rawResults       = new LinkedHashMap<String, List<Double>>();
            val benchmarkResults = new ArrayList<BenchmarkResult>();
            for (val entry : methods.entrySet()) {
                out.println("--- %s ---".formatted(entry.getKey()));
                runPhase("Warmup", cfg.warmupIterations(), cfg.warmupTimeSeconds(), threads, entry.getValue(), out,
                        false);
                val iterationData = runPhase("Iteration", cfg.measurementIterations(), cfg.measurementTimeSeconds(),
                        threads, entry.getValue(), out, true);
                rawResults.put(entry.getKey(), iterationData);
                benchmarkResults.add(BenchmarkResult.fromIterations(entry.getKey(), threads, iterationData));
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

    private static Map<String, Supplier<AuthorizationDecision>> resolveMethods(PolicyDecisionPoint pdp,
            AuthorizationSubscription subscription, List<String> filter) {
        val all = new LinkedHashMap<String, Supplier<AuthorizationDecision>>();
        all.put("decideOnceBlocking", () -> pdp.decideOnceBlocking(subscription));
        all.put("decideOnceReactive", () -> pdp.decideOnce(subscription).block());
        all.put("decideStreamFirst", () -> pdp.decide(subscription).blockFirst());

        if (filter == null || filter.isEmpty()) {
            return all;
        }
        val filtered = new LinkedHashMap<String, Supplier<AuthorizationDecision>>();
        for (val entry : all.entrySet()) {
            if (filter.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered.isEmpty() ? all : filtered;
    }

    private static List<Double> runPhase(String phaseName, int iterations, int timePerIterationSeconds, int threads,
            Supplier<AuthorizationDecision> method, PrintWriter out, boolean collect) {
        val results = collect ? new ArrayList<Double>(iterations) : null;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            val opsCount   = runTimedIteration(timePerIterationSeconds, threads, method);
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

    private static long runTimedIteration(int seconds, int threadCount, Supplier<AuthorizationDecision> method) {
        val totalOps = new AtomicLong(0);
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        val latch    = new CountDownLatch(threadCount);
        val threads  = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                long   localOps   = 0;
                Object lastResult = null;
                while (System.nanoTime() < deadline) {
                    lastResult = method.get();
                    localOps++;
                }
                sink = lastResult;
                totalOps.addAndGet(localOps);
                latch.countDown();
            });
            threads[t].setDaemon(true);
            threads[t].start();
        }
        try {
            latch.await(seconds + 5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return totalOps.get();
    }

    private static void printSummary(List<BenchmarkResult> results, PrintWriter out) {
        out.println("%-30s %10s %15s %10s".formatted("Benchmark", "Threads", "Throughput", "Error"));
        out.println("-".repeat(70));
        for (val r : results) {
            out.println(String.format(Locale.US, "%-30s %10d %,13.1f ops/s %,10.1f ops/s", r.method(), r.threads(),
                    r.mean(), r.stddev()));
        }
        out.flush();
    }

    private static void writeJsonResults(Map<String, List<Double>> rawResults, BenchmarkRunConfig cfg, int threads,
            Path outputPath, PrintWriter err) {
        val sb = new StringBuilder();
        sb.append("[\n");
        val entries = new ArrayList<>(rawResults.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            val entry = entries.get(i);
            val r     = BenchmarkResult.fromIterations(entry.getKey(), threads, entry.getValue());
            sb.append("  {\n");
            sb.append("    \"benchmark\": \"").append(r.method()).append("\",\n");
            sb.append("    \"mode\": \"thrpt\",\n");
            sb.append("    \"threads\": ").append(threads).append(",\n");
            sb.append("    \"warmupIterations\": ").append(cfg.warmupIterations()).append(",\n");
            sb.append("    \"warmupTime\": \"").append(cfg.warmupTimeSeconds()).append(" s\",\n");
            sb.append("    \"measurementIterations\": ").append(cfg.measurementIterations()).append(",\n");
            sb.append("    \"measurementTime\": \"").append(cfg.measurementTimeSeconds()).append(" s\",\n");
            sb.append("    \"primaryMetric\": {\n");
            sb.append("      \"score\": ").append(fmt(r.mean())).append(",\n");
            sb.append("      \"scoreError\": ").append(fmt(r.stddev())).append(",\n");
            sb.append("      \"scoreUnit\": \"ops/s\",\n");
            sb.append("      \"scorePercentiles\": {\n");
            sb.append("        \"5.0\": ").append(fmt(r.p5())).append(",\n");
            sb.append("        \"50.0\": ").append(fmt(r.median())).append(",\n");
            sb.append("        \"95.0\": ").append(fmt(r.p95())).append('\n');
            sb.append("      },\n");
            sb.append("      \"rawData\": [").append(formatRawData(r.rawData())).append("]\n");
            sb.append("    }\n");
            sb.append("  }").append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]\n");
        try {
            Files.writeString(outputPath, sb.toString());
        } catch (IOException e) {
            err.println(WARN_JSON_WRITE_FAILED.formatted(e.getMessage()));
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String formatRawData(List<Double> values) {
        val sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            sb.append(fmt(values.get(i)));
            if (i < values.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb.toString();
    }

}
