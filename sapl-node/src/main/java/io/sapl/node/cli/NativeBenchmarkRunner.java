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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.sapl.api.model.jackson.SaplJacksonModule;
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

    static int run(BenchmarkContext ctx, BenchmarkOptions opts, PrintWriter out, PrintWriter err) {
        PDPComponents components = null;
        try {
            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class);
            components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of(ctx.policiesPath()))
                    .build();
            val pdp = components.pdp();

            out.println("# Native benchmark: decideOnceBlocking");
            out.println("# Warmup: %d iterations x %d s".formatted(opts.warmupIterations, opts.warmupTimeSeconds));
            out.println("# Measurement: %d iterations x %d s".formatted(opts.measurementIterations,
                    opts.measurementTimeSeconds));
            out.println("# Threads: %d".formatted(opts.threads));
            out.println();

            runPhase("Warmup", opts.warmupIterations, opts.warmupTimeSeconds, opts.threads, pdp, subscription, out,
                    false);
            val results = runPhase("Iteration", opts.measurementIterations, opts.measurementTimeSeconds, opts.threads,
                    pdp, subscription, out, true);

            out.println();
            printSummary(results, out);
            return 0;
        } catch (RuntimeException e) {
            err.println(ERROR_BENCHMARK_FAILED.formatted(e.getMessage()));
            return 1;
        } finally {
            if (components != null) {
                components.dispose();
            }
        }
    }

    private static List<Double> runPhase(String phaseName, int iterations, int timePerIterationSeconds, int threads,
            PolicyDecisionPoint pdp, AuthorizationSubscription subscription, PrintWriter out, boolean collect) {
        val results = collect ? new ArrayList<Double>(iterations) : null;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            val opsCount   = runTimedIteration(timePerIterationSeconds, threads, pdp, subscription);
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
     * AOT compiler from eliminating the {@code decideOnceBlocking} call as dead
     * code, mirroring JMH's Blackhole mechanism.
     */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    private static long runTimedIteration(int seconds, int threadCount, PolicyDecisionPoint pdp,
            AuthorizationSubscription subscription) {
        val totalOps = new AtomicLong(0);
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        val latch    = new CountDownLatch(threadCount);
        val threads  = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                long   localOps   = 0;
                Object lastResult = null;
                while (System.nanoTime() < deadline) {
                    lastResult = pdp.decideOnceBlocking(subscription);
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

    private static void printSummary(List<Double> throughputs, PrintWriter out) {
        if (throughputs.isEmpty()) {
            return;
        }
        val sorted = new ArrayList<>(throughputs);
        Collections.sort(sorted);
        val mean   = throughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        val stddev = Math.sqrt(throughputs.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0.0));
        val p50    = percentile(sorted, 0.50);
        val p95    = percentile(sorted, 0.95);
        val p99    = percentile(sorted, 0.99);

        out.println("Result \"decideOnceBlocking\":");
        out.println(String.format(Locale.US, "  %,.1f +/- %,.1f ops/s", mean, stddev));
        out.println(
                String.format(Locale.US, "  p50 = %,.1f ops/s, p95 = %,.1f ops/s, p99 = %,.1f ops/s", p50, p95, p99));
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        val index = p * (sorted.size() - 1);
        val lower = (int) Math.floor(index);
        val upper = Math.min(lower + 1, sorted.size() - 1);
        val frac  = index - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

}
