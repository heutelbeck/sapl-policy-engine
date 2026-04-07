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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP load generator with two engine modes:
 * <ul>
 * <li><b>reactive</b> (default): {@code Flux.range().flatMap()} for
 * saturation, {@code Flux.interval()} for paced sending</li>
 * <li><b>virtual-threads</b>: async callback chaining for saturation
 * (original implementation)</li>
 * </ul>
 * Paced mode uses coordinated omission correction regardless of engine.
 * The request body is pre-serialized to eliminate client-side overhead.
 */
@UtilityClass
public class HttpLoadGenerator {

    private static final int    MAX_LATENCY_SAMPLES   = 2_000_000;
    private static final int    CONVERGENCE_THRESHOLD = 5;
    private static final int    CONVERGENCE_WINDOW    = 3;
    private static final int    MAX_WARMUP_ITERATIONS = 15;
    private static final int    WARMUP_INTERVAL_SECS  = 3;
    private static final String ENDPOINT              = "/api/pdp/decide-once";

    /**
     * Runs an HTTP load test against a SAPL PDP server.
     *
     * @param baseUrl the server base URL
     * @param body pre-serialized JSON request body
     * @param concurrency number of concurrent in-flight requests
     * @param warmupSeconds warmup duration in seconds
     * @param measureSeconds measurement duration in seconds
     * @param targetRate target requests per second (0 = saturation)
     * @param useVirtualThreads true for VT engine, false for reactive
     * @param out writer for progress output
     * @return the benchmark result with throughput and latency distribution
     */
    public static BenchmarkResult run(String baseUrl, byte[] body, int concurrency, int warmupSeconds,
            int measureSeconds, int targetRate, boolean useVirtualThreads, PrintWriter out) {
        val url     = baseUrl + ENDPOINT;
        val request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(body)).version(Version.HTTP_1_1).build();

        try (val client = HttpClient.newBuilder().version(Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor()).build()) {

            val engineLabel = useVirtualThreads ? "virtual-threads" : "reactive";

            if (warmupSeconds > 0) {
                warmupUntilConverged(client, request, concurrency, useVirtualThreads, out);
            }

            val ops     = new AtomicLong(0);
            val latency = new LatencyCollector(MAX_LATENCY_SAMPLES);
            val start   = System.nanoTime();

            if (targetRate > 0) {
                runPacedPhase(client, request, concurrency, measureSeconds, targetRate, ops, latency);
            } else if (useVirtualThreads) {
                runVtSaturationPhase(client, request, concurrency, measureSeconds, ops, latency);
            } else {
                runReactiveSaturationPhase(client, request, concurrency, measureSeconds, ops, latency);
            }

            val elapsed    = (System.nanoTime() - start) / 1_000_000_000.0;
            val throughput = ops.get() / elapsed;

            if (targetRate > 0) {
                out.printf("  [%s] %d concurrent @ %,d req/s target: %,.0f req/s actual%n", engineLabel, concurrency,
                        targetRate, throughput);
            } else {
                out.printf("  [%s] %d concurrent: %,.0f req/s%n", engineLabel, concurrency, throughput);
            }
            out.flush();

            val label = targetRate > 0 ? "http-%dc-%dr".formatted(concurrency, targetRate)
                    : "http-%dc".formatted(concurrency);
            return BenchmarkResult.fromIterations(label, 1, List.of(throughput), latency.toLatency());
        }
    }

    // ---- Reactive engine ----

    private static Mono<Void> sendAsync(HttpClient client, HttpRequest request) {
        return Mono.fromCompletionStage(() -> client.sendAsync(request, BodyHandlers.discarding())).then();
    }

    private static void runReactiveSaturationPhase(HttpClient client, HttpRequest request, int concurrency, int seconds,
            AtomicLong ops, @Nullable LatencyCollector latency) {
        val deadline = Mono.delay(Duration.ofSeconds(seconds));
        Flux.range(0, concurrency).flatMap(slot -> Mono.defer(() -> {
            val sendTime = System.nanoTime();
            return sendAsync(client, request).doOnSuccess(ignored -> {
                if (latency != null) {
                    latency.addSample(System.nanoTime() - sendTime);
                }
                ops.incrementAndGet();
            });
        }).repeat().takeUntilOther(deadline), concurrency).blockLast();
    }

    private static void runPacedPhase(HttpClient client, HttpRequest request, int concurrency, int seconds,
            int targetRate, AtomicLong ops, LatencyCollector latency) {
        val intervalNanos = 1_000_000_000L / targetRate;
        val startTime     = System.nanoTime();

        Flux.interval(Duration.ofNanos(intervalNanos)).take(Duration.ofSeconds(seconds)).flatMap(tick -> {
            val intendedSendTime = startTime + tick * intervalNanos;
            return sendAsync(client, request).doOnSuccess(ignored -> {
                latency.addSample(System.nanoTime() - intendedSendTime);
                ops.incrementAndGet();
            });
        }, concurrency).blockLast();
    }

    // ---- Virtual threads engine ----

    private static void runVtSaturationPhase(HttpClient client, HttpRequest request, int concurrency, int seconds,
            AtomicLong ops, @Nullable LatencyCollector latency) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        val latch    = new CountDownLatch(concurrency);

        for (int i = 0; i < concurrency; i++) {
            fireNext(client, request, deadline, ops, latency, latch);
        }

        try {
            latch.await(seconds + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void fireNext(HttpClient client, HttpRequest request, long deadline, AtomicLong ops,
            @Nullable LatencyCollector latency, CountDownLatch latch) {
        if (System.nanoTime() >= deadline) {
            latch.countDown();
            return;
        }

        val sendTime = System.nanoTime();
        client.sendAsync(request, BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error == null) {
                if (latency != null) {
                    latency.addSample(System.nanoTime() - sendTime);
                }
                ops.incrementAndGet();
            }
            fireNext(client, request, deadline, ops, latency, latch);
        });
    }

    // ---- Warmup (uses the selected engine) ----

    private static void warmupUntilConverged(HttpClient client, HttpRequest request, int concurrency,
            boolean useVirtualThreads, PrintWriter out) {
        out.print("  Warmup: ");
        val samples = new long[MAX_WARMUP_ITERATIONS];
        for (int i = 0; i < MAX_WARMUP_ITERATIONS; i++) {
            val ops = new AtomicLong(0);
            if (useVirtualThreads) {
                runVtSaturationPhase(client, request, concurrency, WARMUP_INTERVAL_SECS, ops, null);
            } else {
                runReactiveSaturationPhase(client, request, concurrency, WARMUP_INTERVAL_SECS, ops, null);
            }
            val rps = ops.get() / WARMUP_INTERVAL_SECS;
            out.printf("%,d/s ", rps);
            out.flush();
            samples[i] = rps;

            if (isConverged(samples, i)) {
                out.println("(converged)");
                return;
            }
        }
        out.println("(max iterations)");
    }

    private static boolean isConverged(long[] samples, int currentIndex) {
        if (currentIndex < CONVERGENCE_WINDOW - 1) {
            return false;
        }
        val current = samples[currentIndex];
        for (int j = 1; j < CONVERGENCE_WINDOW; j++) {
            val prev = samples[currentIndex - j];
            if (prev > 0 && Math.abs(current - prev) * 100 / prev > CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

}
