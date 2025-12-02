/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.demo;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.SingleDocumentPolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Standalone SAPL Policy Decision Point performance benchmark.
 * <p>
 * This benchmark measures throughput and latency across different
 * parallelization
 * strategies to identify bottlenecks and optimal configurations.
 * <p>
 * Benchmark modes:
 * <ul>
 * <li><b>single</b> - Single-threaded baseline with Reactor</li>
 * <li><b>reactor</b> - Multi-core using Reactor Flux.parallel()</li>
 * <li><b>executor</b> - Multi-core using Java ExecutorService with blocking
 * calls</li>
 * <li><b>pure</b> - Multi-core with direct evaluation (minimal Reactor
 * overhead)</li>
 * <li><b>all</b> - Run all benchmarks sequentially</li>
 * </ul>
 * <p>
 * Usage: java SaplBenchmark [mode] [iterations] [warmup]
 * <p>
 * Example: java SaplBenchmark all 1000000 50000
 */
public final class SaplBenchmark {

    private static final int DEFAULT_ITERATIONS        = 2_000_000;
    private static final int DEFAULT_WARMUP_ROUNDS     = 100_000;
    private static final int DEFAULT_DISTRIBUTION_SIZE = 100_000;

    private static final ThreadLocal<Object> BLACKHOLE = new ThreadLocal<>();

    private static final String STRUCTURAL_STRESS_POLICY = """
            policy "structural_entropy_benchmark"
            permit
            where
                subject.role in ["admin","manager","engineer"];
                subject.age >= 18;
                subject.age <= 90;

                resource.type in ["document", "image", "dataset"];
                resource.classification != "top_secret";

                action.method in ["read","write","update","execute"];

                resource.owner.department == subject.department;

                subject.attributes.level + action.transaction.amount > 200
                ||
                filter.replace(resource.content, subject.random_key) != resource.content;

                array.size(subject.dynamic_list) > 3;
                subject.dynamic_map[(subject.dynamic_key)] != null;

            obligation "log_access"
            advice "notify_owner"
            transform resource.content
            """;

    private final int iterations;
    private final int warmupRounds;
    private final int distributionSize;
    private final int cores;

    private SingleDocumentPolicyDecisionPoint pdp;

    public SaplBenchmark(int iterations, int warmupRounds) {
        this.iterations       = iterations;
        this.warmupRounds     = warmupRounds;
        this.distributionSize = Math.min(DEFAULT_DISTRIBUTION_SIZE, iterations / 10);
        this.cores            = Runtime.getRuntime().availableProcessors();
    }

    public static void main(String[] args) throws Exception {
        var mode       = args.length > 0 ? args[0].toLowerCase() : "all";
        var iterations = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_ITERATIONS;
        var warmup     = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_WARMUP_ROUNDS;

        var benchmark = new SaplBenchmark(iterations, warmup);
        benchmark.initialize();

        switch (mode) {
        case "single"   -> benchmark.runSingleThreaded();
        case "reactor"  -> benchmark.runReactorParallel();
        case "executor" -> benchmark.runExecutorParallel();
        case "pure"     -> benchmark.runPureEvaluation();
        case "all"      -> benchmark.runAll();
        default         -> {
            System.err.println("Unknown mode: " + mode);
            System.err.println("Available modes: single, reactor, executor, pure, all");
            System.exit(1);
        }
        }
    }

    private void initialize() throws InitializationException {
        printHeader("SAPL PERFORMANCE BENCHMARK SUITE");
        System.out.printf("Cores: %d | Iterations: %,d | Warmup: %,d%n%n", cores, iterations, warmupRounds);

        pdp = new SingleDocumentPolicyDecisionPoint();

        var initStart = System.nanoTime();
        pdp.loadDocument(STRUCTURAL_STRESS_POLICY);
        var initEnd = System.nanoTime();

        System.out.printf("Policy load time: %.3f ms%n%n", (initEnd - initStart) / 1_000_000.0);
    }

    private void runAll() throws Exception {
        runSingleThreaded();
        System.out.println();
        runReactorParallel();
        System.out.println();
        runExecutorParallel();
        System.out.println();
        runPureEvaluation();

        printHeader("BENCHMARK COMPLETE");
    }

    /**
     * Single-threaded benchmark with Reactor - establishes baseline performance.
     */
    private void runSingleThreaded() {
        printHeader("SINGLE-THREADED BENCHMARK (Reactor baseline)");

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < warmupRounds; i++) {
            BLACKHOLE.set(pdp.decide(createSubscription(i)).blockFirst());
        }
        System.out.println("Warmup complete.\n");

        // Latency distribution
        System.out.println("Collecting latency distribution...");
        var samples = new long[distributionSize];
        for (int i = 0; i < distributionSize; i++) {
            var subscription = createSubscription(i * 31);
            var start        = System.nanoTime();
            BLACKHOLE.set(pdp.decide(subscription).blockFirst());
            samples[i] = System.nanoTime() - start;
        }

        printLatencyDistribution(samples);

        // Throughput
        System.out.println("Measuring throughput...");
        var start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BLACKHOLE.set(pdp.decide(createSubscription(ThreadLocalRandom.current().nextInt())).blockFirst());
        }
        var elapsed = System.nanoTime() - start;

        printThroughput("Single-threaded", iterations, elapsed);
    }

    /**
     * Multi-core benchmark using Reactor Flux.parallel() with boundedElastic
     * scheduler.
     */
    private void runReactorParallel() throws InterruptedException {
        printHeader("REACTOR PARALLEL BENCHMARK (Flux.parallel + boundedElastic)");

        // Pre-allocate subscriptions
        var subscriptions = preAllocateSubscriptions(iterations);

        // Warmup with parallel execution
        System.out.println("Warming up on all cores...");
        var warmupSubs = preAllocateSubscriptions(warmupRounds);
        Flux.range(0, warmupRounds).parallel(cores).runOn(Schedulers.boundedElastic())
                .flatMap(i -> pdp.decide(warmupSubs[i]).take(1)).doOnNext(BLACKHOLE::set).sequential().blockLast();
        System.out.println("Warmup complete.\n");

        // Throughput measurement
        System.out.println("Measuring throughput...");
        var latch   = new CountDownLatch(1);
        var counter = new LongAdder();

        var start = System.nanoTime();

        Flux.range(0, iterations).parallel(cores).runOn(Schedulers.boundedElastic())
                .flatMap(i -> pdp.decide(subscriptions[i]).doOnNext(result -> {
                    BLACKHOLE.set(result);
                    counter.increment();
                }).take(1)).sequential().doOnComplete(latch::countDown).subscribe();

        latch.await(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
        var elapsed = System.nanoTime() - start;

        printThroughput("Reactor parallel", counter.sum(), elapsed);
    }

    /**
     * Multi-core benchmark using Java ExecutorService with blocking decide calls.
     * Bypasses Reactor's parallel coordination overhead.
     */
    private void runExecutorParallel() throws InterruptedException {
        printHeader("EXECUTOR PARALLEL BENCHMARK (Java ExecutorService + blockFirst)");

        // Pre-allocate subscriptions
        var subscriptions = preAllocateSubscriptions(iterations);

        // Warmup
        System.out.println("Warming up on all cores...");
        var warmupSubs     = preAllocateSubscriptions(warmupRounds);
        var warmupExecutor = Executors.newFixedThreadPool(cores);
        var warmupLatch    = new CountDownLatch(warmupRounds);

        for (int i = 0; i < warmupRounds; i++) {
            var subscription = warmupSubs[i];
            warmupExecutor.submit(() -> {
                BLACKHOLE.set(pdp.decide(subscription).blockFirst());
                warmupLatch.countDown();
            });
        }
        warmupLatch.await(5, TimeUnit.MINUTES);
        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("Warmup complete.\n");

        // Throughput measurement
        System.out.println("Measuring throughput...");
        var executor = Executors.newFixedThreadPool(cores);
        var latch    = new CountDownLatch(iterations);
        var counter  = new LongAdder();

        var start = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            var subscription = subscriptions[i];
            executor.submit(() -> {
                BLACKHOLE.set(pdp.decide(subscription).blockFirst());
                counter.increment();
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.MINUTES);
        var elapsed = System.nanoTime() - start;

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        printThroughput("Executor parallel", counter.sum(), elapsed);
    }

    /**
     * Multi-core benchmark with direct pure evaluation - minimal Reactor overhead.
     * Uses chunked work distribution for optimal throughput.
     */
    private void runPureEvaluation() throws Exception {
        printHeader("PURE EVALUATION BENCHMARK (Chunked, minimal Reactor)");

        // Pre-allocate subscriptions
        var subscriptions = preAllocateSubscriptions(iterations);

        // Warmup (sequential - JIT optimizes across threads)
        System.out.println("Warming up...");
        var warmupSubs = preAllocateSubscriptions(warmupRounds);
        for (int i = 0; i < warmupRounds; i++) {
            BLACKHOLE.set(pdp.decidePure(warmupSubs[i]));
        }
        System.out.println("Warmup complete.\n");

        // Throughput measurement with chunked parallelism
        System.out.println("Measuring throughput...");
        var executor  = Executors.newWorkStealingPool(cores);
        var counter   = new LongAdder();
        var chunkSize = iterations / cores;
        var futures   = new Future<?>[cores];

        var start = System.nanoTime();

        for (int t = 0; t < cores; t++) {
            var startIndex = t * chunkSize;
            var endIndex   = (t == cores - 1) ? iterations : startIndex + chunkSize;

            futures[t] = executor.submit(() -> {
                for (int i = startIndex; i < endIndex; i++) {
                    BLACKHOLE.set(pdp.decidePure(subscriptions[i]));
                    counter.increment();
                }
            });
        }

        for (var future : futures) {
            future.get(10, TimeUnit.MINUTES);
        }
        var elapsed = System.nanoTime() - start;

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        printThroughput("Pure evaluation", counter.sum(), elapsed);
    }

    private AuthorizationSubscription[] preAllocateSubscriptions(int count) {
        System.out.printf("Pre-allocating %,d subscriptions...%n", count);
        var subscriptions = new AuthorizationSubscription[count];
        for (int i = 0; i < count; i++) {
            subscriptions[i] = createSubscription(ThreadLocalRandom.current().nextInt());
        }
        return subscriptions;
    }

    private AuthorizationSubscription createSubscription(int seed) {
        var random     = ThreadLocalRandom.current();
        var dynamicKey = "k" + Math.abs(seed % 1000);
        var department = switch (seed % 3) {
                       case 0  -> "engineering";
                       case 1  -> "finance";
                       default -> "operations";
                       };

        var dynamicListBuilder = ArrayValue.builder();
        for (int i = 0; i < 5 + (seed % 5); i++) {
            dynamicListBuilder.add(Value.of(random.nextInt(1_000)));
        }

        var dynamicList = dynamicListBuilder.build();
        var dynamicMap  = ObjectValue.builder().put(dynamicKey, Value.of("v" + random.nextInt(10_000)))
                .put("static", Value.of(seed)).build();

        var subject = ObjectValue.builder().put("role", Value.of(switch (seed % 3) {
        case 0  -> "admin";
        case 1  -> "manager";
        default -> "engineer";
        })).put("age", Value.of(18 + Math.abs(seed % 50))).put("department", Value.of(department))
                .put("random_key", Value.of(dynamicKey)).put("dynamic_key", Value.of(dynamicKey))
                .put("dynamic_list", dynamicList).put("dynamic_map", dynamicMap)
                .put("attributes", ObjectValue.builder().put("level", Value.of((seed % 10) + 1)).build()).build();

        var action = ObjectValue.builder().put("method", Value.of(switch (seed % 4) {
        case 0  -> "read";
        case 1  -> "write";
        case 2  -> "update";
        default -> "execute";
        })).put("transaction", ObjectValue.builder().put("amount", Value.of(Math.abs(seed % 500))).build()).build();

        var resource = ObjectValue.builder().put("type", Value.of(switch (seed % 3) {
        case 0  -> "document";
        case 1  -> "image";
        default -> "dataset";
        })).put("classification", Value.of(seed % 11 == 0 ? "confidential" : "internal"))
                .put("content", Value.of("payload-" + seed))
                .put("owner", ObjectValue.builder().put("department", Value.of(department)).build()).build();

        return new AuthorizationSubscription(subject, action, resource, Value.UNDEFINED);
    }

    private void printHeader(String title) {
        System.out.println("=".repeat(90));
        System.out.println(title);
        System.out.println("=".repeat(90));
    }

    private void printLatencyDistribution(long[] samples) {
        var sorted = samples.clone();
        Arrays.sort(sorted);

        var p50 = sorted[(int) (sorted.length * 0.50)];
        var p90 = sorted[(int) (sorted.length * 0.90)];
        var p99 = sorted[(int) (sorted.length * 0.99)];

        System.out.println("\nLatency distribution:");
        System.out.printf("  p50: %,d ns (%.3f ms)%n", p50, p50 / 1_000_000.0);
        System.out.printf("  p90: %,d ns (%.3f ms)%n", p90, p90 / 1_000_000.0);
        System.out.printf("  p99: %,d ns (%.3f ms)%n", p99, p99 / 1_000_000.0);
        System.out.println();
    }

    private void printThroughput(String label, long count, long elapsedNanos) {
        var seconds   = elapsedNanos / 1_000_000_000.0;
        var opsPerSec = (long) (count / seconds);

        System.out.println("\n" + label + " throughput:");
        System.out.printf("  %,d decisions in %.2f seconds%n", count, seconds);
        System.out.printf("  %,d ops/sec total%n", opsPerSec);
        System.out.printf("  ~%,d ops/sec per core%n", opsPerSec / cores);
    }
}
