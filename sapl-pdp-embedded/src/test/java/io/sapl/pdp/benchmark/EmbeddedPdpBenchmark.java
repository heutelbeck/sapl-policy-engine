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
package io.sapl.pdp.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance benchmark for the embedded Policy Decision Point with multiple
 * policies.
 * <p>
 * This benchmark uses the production {@link PolicyDecisionPointFactory} to
 * create
 * a real PDP with multiple policies and combining algorithms, measuring
 * throughput
 * and latency under various parallelization strategies.
 * <p>
 * Benchmark modes:
 * <ul>
 * <li><b>single</b> - Single-threaded baseline with latency distribution</li>
 * <li><b>reactor</b> - Multi-core using Reactor Flux.parallel()</li>
 * <li><b>executor</b> - Multi-core using Java ExecutorService</li>
 * <li><b>scaling</b> - Measures throughput at different concurrency levels</li>
 * <li><b>latency</b> - Measures latency under varying concurrent load</li>
 * <li><b>all</b> - Run all benchmarks sequentially</li>
 * </ul>
 * <p>
 * Usage: java EmbeddedPdpBenchmark [mode] [iterations] [warmup]
 */
public final class EmbeddedPdpBenchmark {

    private static final int DEFAULT_ITERATIONS    = 500_000;
    private static final int DEFAULT_WARMUP_ROUNDS = 50_000;

    private static final ThreadLocal<Object> BLACKHOLE = new ThreadLocal<>();
    private static final ObjectMapper        MAPPER    = new ObjectMapper();

    private static final String[] ROLES           = { "admin", "manager", "engineer", "employee", "contractor" };
    private static final String[] DEPARTMENTS     = { "engineering", "finance", "operations", "hr" };
    private static final String[] ACTIONS         = { "read", "write", "update", "delete", "export", "execute" };
    private static final String[] RESOURCE_TYPES  = { "document", "dataset", "code", "image", "internal_doc" };
    private static final String[] CLASSIFICATIONS = { "public", "internal", "confidential", "top_secret" };
    private static final String[] CLEARANCES      = { "none", "confidential", "secret", "top_secret" };

    /**
     * Multiple policies simulating a realistic policy set for an enterprise
     * application. Uses single-line format to avoid text block parsing issues.
     */
    private static final List<String> POLICIES = List.of(
            "policy \"admin_full_access\" permit where subject.role == \"admin\";",
            "policy \"manager_department_access\" permit action in [\"read\", \"write\", \"update\"] where subject.role == \"manager\"; resource.owner.department == subject.department;",
            "policy \"engineer_technical_read\" permit action == \"read\" where subject.role == \"engineer\"; resource.type in [\"document\", \"dataset\", \"code\"]; resource.classification != \"confidential\";",
            "policy \"age_restricted_content\" deny where resource.ageRestricted == true; subject.age < 18;",
            "policy \"classification_denial\" deny where resource.classification == \"top_secret\"; !(subject.clearanceLevel in [\"top_secret\", \"cosmic\"]);",
            "policy \"working_hours\" permit where subject.role in [\"employee\", \"contractor\"]; action == \"read\"; resource.type == \"internal_doc\";",
            "policy \"audit_logging\" permit where subject.role in [\"admin\", \"manager\", \"engineer\"]; action in [\"read\", \"write\", \"update\", \"delete\"];",
            "policy \"export_restriction\" deny action == \"export\" where resource.containsPII == true; !(subject.role in [\"admin\", \"data_officer\"]);",
            "policy \"cross_department_read\" permit action == \"read\" where subject.collaborationEnabled == true;");

    private final int iterations;
    private final int warmupRounds;
    private final int distributionSize;
    private final int cores;

    private PolicyDecisionPoint         pdp;
    private AuthorizationSubscription[] subscriptions;

    public EmbeddedPdpBenchmark(int iterations, int warmupRounds) {
        this.iterations       = iterations;
        this.warmupRounds     = warmupRounds;
        this.distributionSize = Math.min(100_000, iterations / 10);
        this.cores            = Runtime.getRuntime().availableProcessors();
    }

    public static void main(String[] args) throws Exception {
        var mode       = args.length > 0 ? args[0].toLowerCase() : "all";
        var iterations = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_ITERATIONS;
        var warmup     = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_WARMUP_ROUNDS;

        var benchmark = new EmbeddedPdpBenchmark(iterations, warmup);
        benchmark.initialize();

        switch (mode) {
        case "single"   -> benchmark.runSingleThreaded();
        case "reactor"  -> benchmark.runReactorParallel();
        case "executor" -> benchmark.runExecutorParallel();
        case "scaling"  -> benchmark.runScalingAnalysisChunked();
        case "latency"  -> benchmark.runLatencyUnderLoad();
        case "all"      -> benchmark.runAll();
        default         -> {
            System.err.println("Unknown mode: " + mode);
            System.err.println("Available modes: single, reactor, executor, scaling, latency, all");
            System.exit(1);
        }
        }
    }

    private void initialize() throws Exception {
        printHeader("EMBEDDED PDP BENCHMARK SUITE");
        System.out.printf("Cores: %d | Iterations: %,d | Warmup: %,d%n", cores, iterations, warmupRounds);
        System.out.printf("Policies: %d | Combining: DENY_OVERRIDES%n%n", POLICIES.size());

        System.out.println("Initializing PDP with multiple policies...");
        var initStart = System.nanoTime();

        pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(POLICIES,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var initEnd = System.nanoTime();
        System.out.printf("PDP initialization time: %.3f ms%n%n", (initEnd - initStart) / 1_000_000.0);

        System.out.printf("Pre-allocating %,d subscriptions...%n", iterations);
        subscriptions = new AuthorizationSubscription[iterations];
        for (int i = 0; i < iterations; i++) {
            subscriptions[i] = createSubscription(ThreadLocalRandom.current().nextInt());
        }
        System.out.println("Pre-allocation complete.\n");

        // Validate that policies actually evaluate to meaningful decisions
        System.out.println("Validating decision distribution (sample of 1000)...");
        var decisionCounts = new java.util.HashMap<String, AtomicLong>();
        for (int i = 0; i < 1000; i++) {
            var decision = pdp.decide(subscriptions[i]).blockFirst();
            var key      = decision != null ? decision.getDecision().name() : "NULL";
            decisionCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        }
        decisionCounts.forEach(
                (key, value) -> System.out.printf("  %s: %d (%.1f%%)%n", key, value.get(), value.get() / 10.0));
        System.out.println();
    }

    private void runAll() throws Exception {
        runSingleThreaded();
        System.out.println();
        runReactorParallel();
        System.out.println();
        runExecutorParallel();
        System.out.println();
        runScalingAnalysisChunked();
        System.out.println();
        runLatencyUnderLoad();

        printHeader("BENCHMARK COMPLETE");
    }

    /**
     * Single-threaded benchmark - establishes baseline performance.
     */
    private void runSingleThreaded() {
        printHeader("SINGLE-THREADED BENCHMARK");

        System.out.println("Warming up...");
        for (int i = 0; i < warmupRounds; i++) {
            BLACKHOLE.set(pdp.decide(subscriptions[i % subscriptions.length]).blockFirst());
        }
        System.out.println("Warmup complete.\n");

        // Latency distribution
        System.out.println("Collecting latency distribution...");
        var samples = new long[distributionSize];
        for (int i = 0; i < distributionSize; i++) {
            var start = System.nanoTime();
            BLACKHOLE.set(pdp.decide(subscriptions[i]).blockFirst());
            samples[i] = System.nanoTime() - start;
        }
        printLatencyDistribution(samples);

        // Throughput
        System.out.println("Measuring throughput...");
        var start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BLACKHOLE.set(pdp.decide(subscriptions[i]).blockFirst());
        }
        var elapsed = System.nanoTime() - start;

        printThroughput("Single-threaded", iterations, elapsed);
    }

    /**
     * Multi-core benchmark using Reactor Flux.parallel().
     */
    private void runReactorParallel() throws InterruptedException {
        printHeader("REACTOR PARALLEL BENCHMARK (Flux.parallel + boundedElastic)");

        System.out.println("Warming up on all cores...");
        Flux.range(0, warmupRounds).parallel(cores).runOn(Schedulers.boundedElastic())
                .flatMap(i -> pdp.decide(subscriptions[i % subscriptions.length]).take(1)).doOnNext(BLACKHOLE::set)
                .sequential().blockLast();
        System.out.println("Warmup complete.\n");

        System.out.println("Measuring throughput...");
        var latch   = new CountDownLatch(1);
        var counter = new LongAdder();

        var start = System.nanoTime();

        Flux.range(0, iterations).parallel(cores).runOn(Schedulers.boundedElastic())
                .flatMap(i -> pdp.decide(subscriptions[i]).doOnNext(result -> {
                    BLACKHOLE.set(result);
                    counter.increment();
                }).take(1)).sequential().doOnComplete(latch::countDown).subscribe();

        var completed = latch.await(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
        var elapsed   = System.nanoTime() - start;

        if (!completed) {
            System.err.println("Warning: Reactor parallel benchmark timed out.");
        }

        printThroughput("Reactor parallel", counter.sum(), elapsed);
    }

    /**
     * Multi-core benchmark using Java ExecutorService with chunked work.
     */
    private void runExecutorParallel() throws Exception {
        printHeader("EXECUTOR PARALLEL BENCHMARK (Chunked ExecutorService)");

        System.out.println("Warming up on all cores...");
        var warmupExecutor = Executors.newWorkStealingPool(cores);
        var warmupChunk    = warmupRounds / cores;
        var warmupFutures  = new Future<?>[cores];

        for (int t = 0; t < cores; t++) {
            var startIdx = t * warmupChunk;
            var endIdx   = (t == cores - 1) ? warmupRounds : startIdx + warmupChunk;
            warmupFutures[t] = warmupExecutor.submit(() -> {
                for (int i = startIdx; i < endIdx; i++) {
                    BLACKHOLE.set(pdp.decide(subscriptions[i % subscriptions.length]).blockFirst());
                }
            });
        }
        for (var future : warmupFutures) {
            future.get(5, TimeUnit.MINUTES);
        }
        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("Warmup complete.\n");

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
                    BLACKHOLE.set(pdp.decide(subscriptions[i]).blockFirst());
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

        printThroughput("Executor parallel", counter.sum(), elapsed);
    }

    /**
     * Scaling analysis with chunked work distribution - each thread processes
     * a chunk of subscriptions sequentially, avoiding per-task submission overhead.
     */
    private void runScalingAnalysisChunked() throws Exception {
        printHeader("SCALING ANALYSIS - CHUNKED (Throughput vs Concurrency)");

        var testIterations    = Math.min(iterations, 500_000);
        var concurrencyLevels = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 32, 40, 48 };

        // Global warmup first to ensure JIT is fully warmed
        System.out.println("Global warmup (single-threaded)...");
        for (int i = 0; i < warmupRounds; i++) {
            BLACKHOLE.set(pdp.decide(subscriptions[i % subscriptions.length]).blockFirst());
        }
        System.out.println("Warmup complete.\n");

        System.out.println("Testing throughput with chunked work distribution...\n");
        System.out.printf("%-12s %15s %15s %12s%n", "Concurrency", "Throughput", "Per-Thread", "Speedup");
        System.out.println("-".repeat(60));

        long baselineThroughput = 0;

        for (int concurrency : concurrencyLevels) {
            if (concurrency > cores) {
                continue;
            }

            var executor  = Executors.newFixedThreadPool(concurrency);
            var counter   = new LongAdder();
            var chunkSize = testIterations / concurrency;
            var futures   = new Future<?>[concurrency];

            var start = System.nanoTime();

            for (int t = 0; t < concurrency; t++) {
                var startIndex = t * chunkSize;
                var endIndex   = (t == concurrency - 1) ? testIterations : startIndex + chunkSize;

                futures[t] = executor.submit(() -> {
                    for (int i = startIndex; i < endIndex; i++) {
                        BLACKHOLE.set(pdp.decide(subscriptions[i % subscriptions.length]).blockFirst());
                        counter.increment();
                    }
                });
            }

            for (var future : futures) {
                future.get(5, TimeUnit.MINUTES);
            }
            var elapsed = System.nanoTime() - start;

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);

            var throughput = (long) (counter.sum() / (elapsed / 1_000_000_000.0));
            var perThread  = throughput / concurrency;

            if (concurrency == 1) {
                baselineThroughput = throughput;
            }

            var speedup = baselineThroughput > 0 ? throughput / (double) baselineThroughput : 1.0;

            System.out.printf("%-12d %,15d %,15d %11.1fx%n", concurrency, throughput, perThread, speedup);
        }

        System.out.println();
    }

    /**
     * Latency under load - measures how latency degrades as concurrent load
     * increases.
     */
    private void runLatencyUnderLoad() throws Exception {
        printHeader("LATENCY UNDER LOAD (Latency vs Concurrent Requests)");

        var concurrencyLevels = new int[] { 1, 4, 8, 16, 32, 64 };
        var samplesPerLevel   = 10_000;

        System.out.println("Measuring latency distribution at different load levels...\n");
        System.out.printf("%-12s %12s %12s %12s %12s %12s%n", "Concurrency", "p50 (us)", "p90 (us)", "p99 (us)",
                "p99.9 (us)", "Max (us)");
        System.out.println("-".repeat(78));

        for (int concurrency : concurrencyLevels) {
            if (concurrency > cores * 2) {
                continue;
            }

            var executor     = Executors.newFixedThreadPool(concurrency);
            var allLatencies = new ConcurrentLinkedQueue<Long>();
            var latch        = new CountDownLatch(samplesPerLevel);

            for (int i = 0; i < samplesPerLevel; i++) {
                var idx = i;
                executor.submit(() -> {
                    var start = System.nanoTime();
                    BLACKHOLE.set(pdp.decide(subscriptions[idx % subscriptions.length]).blockFirst());
                    var latency = System.nanoTime() - start;
                    allLatencies.add(latency);
                    latch.countDown();
                });
            }

            var completed = latch.await(5, TimeUnit.MINUTES);
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);

            if (!completed) {
                System.err.printf("Warning: Latency benchmark at concurrency %d timed out.%n", concurrency);
            }

            var sorted = allLatencies.stream().mapToLong(Long::longValue).sorted().toArray();

            var p50  = sorted[(int) (sorted.length * 0.50)] / 1000;
            var p90  = sorted[(int) (sorted.length * 0.90)] / 1000;
            var p99  = sorted[(int) (sorted.length * 0.99)] / 1000;
            var p999 = sorted[(int) (sorted.length * 0.999)] / 1000;
            var max  = sorted[sorted.length - 1] / 1000;

            System.out.printf("%-12d %,12d %,12d %,12d %,12d %,12d%n", concurrency, p50, p90, p99, p999, max);
        }

        System.out.println("\nNote: 'us' = microseconds");
    }

    private AuthorizationSubscription createSubscription(int seed) {
        var random  = ThreadLocalRandom.current();
        var absSeed = Math.abs(seed);

        var role           = ROLES[absSeed % ROLES.length];
        var department     = DEPARTMENTS[absSeed % DEPARTMENTS.length];
        var action         = ACTIONS[absSeed % ACTIONS.length];
        var resourceType   = RESOURCE_TYPES[absSeed % RESOURCE_TYPES.length];
        var classification = CLASSIFICATIONS[absSeed % CLASSIFICATIONS.length];
        var clearance      = CLEARANCES[absSeed % CLEARANCES.length];

        var subjectJson = """
                {
                    "id": "user_%d",
                    "role": "%s",
                    "department": "%s",
                    "age": %d,
                    "clearanceLevel": "%s",
                    "collaborationEnabled": %s
                }
                """.formatted(absSeed, role, department, 18 + absSeed % 50, clearance, seed % 3 == 0);

        var resourceDept = seed % 2 == 0 ? department : "other_dept";
        var resourceJson = """
                {
                    "id": "resource_%d",
                    "type": "%s",
                    "classification": "%s",
                    "owner": { "department": "%s" },
                    "ageRestricted": %s,
                    "containsPII": %s,
                    "sharedWith": ["%s", "operations"]
                }
                """.formatted(random.nextInt(10000), resourceType, classification, resourceDept, seed % 7 == 0,
                seed % 5 == 0, department);

        try {
            var subjectNode  = MAPPER.readTree(subjectJson);
            var resourceNode = MAPPER.readTree(resourceJson);
            return new AuthorizationSubscription(subjectNode, MAPPER.valueToTree(action), resourceNode, null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse subscription JSON.", e);
        }
    }

    private void printHeader(String title) {
        System.out.println("=".repeat(90));
        System.out.println(title);
        System.out.println("=".repeat(90));
    }

    private void printLatencyDistribution(long[] samples) {
        var sorted = samples.clone();
        Arrays.sort(sorted);

        var p50  = sorted[(int) (sorted.length * 0.50)];
        var p90  = sorted[(int) (sorted.length * 0.90)];
        var p99  = sorted[(int) (sorted.length * 0.99)];
        var p999 = sorted[Math.min((int) (sorted.length * 0.999), sorted.length - 1)];
        var max  = sorted[sorted.length - 1];
        var min  = sorted[0];

        var sum = 0L;
        for (var s : sorted) {
            sum += s;
        }
        var avg = sum / sorted.length;

        System.out.println("\nLatency distribution:");
        System.out.printf("  Min:   %,d ns (%.3f ms)%n", min, min / 1_000_000.0);
        System.out.printf("  Avg:   %,d ns (%.3f ms)%n", avg, avg / 1_000_000.0);
        System.out.printf("  p50:   %,d ns (%.3f ms)%n", p50, p50 / 1_000_000.0);
        System.out.printf("  p90:   %,d ns (%.3f ms)%n", p90, p90 / 1_000_000.0);
        System.out.printf("  p99:   %,d ns (%.3f ms)%n", p99, p99 / 1_000_000.0);
        System.out.printf("  p99.9: %,d ns (%.3f ms)%n", p999, p999 / 1_000_000.0);
        System.out.printf("  Max:   %,d ns (%.3f ms)%n", max, max / 1_000_000.0);
        System.out.println();
    }

    private void printThroughput(String label, long count, long elapsedNanos) {
        var seconds      = elapsedNanos / 1_000_000_000.0;
        var opsPerSec    = (long) (count / seconds);
        var avgLatencyNs = elapsedNanos / count;
        var avgLatencyUs = avgLatencyNs / 1000.0;

        System.out.println("\n" + label + " throughput:");
        System.out.printf("  %,d decisions in %.2f seconds%n", count, seconds);
        System.out.printf("  %,d ops/sec total%n", opsPerSec);
        System.out.printf("  ~%,d ops/sec per core%n", opsPerSec / cores);
        System.out.printf("  Avg latency: %.2f us%n", avgLatencyUs);
    }
}
