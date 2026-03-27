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
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * HTTP load generator using a single JDK HttpClient with async request
 * chaining. Each concurrent "slot" fires sequential request-response cycles
 * via {@code sendAsync} completion callbacks, giving N concurrent in-flight
 * requests across pooled HTTP/1.1 connections.
 * <p>
 * The request body is pre-serialized to eliminate client-side serialization
 * overhead from the measurement. Latency is recorded per-request from
 * {@code sendAsync} call to {@code whenComplete} callback.
 * <p>
 * Validated against wrk: matches or exceeds wrk throughput when server and
 * client are CPU-isolated via taskset.
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
     * @param baseUrl the server base URL (e.g., "http://localhost:8443")
     * @param body pre-serialized JSON request body
     * @param concurrency number of concurrent in-flight requests
     * @param warmupSeconds warmup duration in seconds (convergence-based)
     * @param measureSeconds measurement duration in seconds
     * @param out writer for progress output
     * @return the benchmark result with throughput and latency distribution
     */
    public static BenchmarkResult run(String baseUrl, byte[] body, int concurrency, int warmupSeconds,
            int measureSeconds, PrintWriter out) {
        val url     = baseUrl + ENDPOINT;
        val request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(body)).version(Version.HTTP_1_1).build();

        try (val client = HttpClient.newBuilder().version(Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor()).build()) {

            if (warmupSeconds > 0) {
                warmupUntilConverged(client, request, concurrency, out);
            }

            val ops     = new AtomicLong(0);
            val latency = new LatencyCollector(MAX_LATENCY_SAMPLES);
            val start   = System.nanoTime();
            runPhase(client, request, concurrency, measureSeconds, ops, latency);
            val elapsed    = (System.nanoTime() - start) / 1_000_000_000.0;
            val throughput = ops.get() / elapsed;

            out.printf("  %d concurrent: %,.0f req/s%n", concurrency, throughput);
            out.flush();

            return BenchmarkResult.fromIterations("http-" + concurrency + "c", 1, List.of(throughput),
                    latency.toLatency());
        }
    }

    private static void warmupUntilConverged(HttpClient client, HttpRequest request, int concurrency, PrintWriter out) {
        out.print("  Warmup: ");
        val samples = new long[MAX_WARMUP_ITERATIONS];
        var stable  = 0;
        for (int i = 0; i < MAX_WARMUP_ITERATIONS; i++) {
            val ops = new AtomicLong(0);
            runPhase(client, request, concurrency, WARMUP_INTERVAL_SECS, ops, null);
            val rps = ops.get() / WARMUP_INTERVAL_SECS;
            out.printf("%,d/s ", rps);
            out.flush();
            samples[i] = rps;

            if (i >= CONVERGENCE_WINDOW - 1) {
                var converged = true;
                for (int j = 1; j < CONVERGENCE_WINDOW; j++) {
                    val prev = samples[i - j];
                    if (prev > 0 && Math.abs(rps - prev) * 100 / prev > CONVERGENCE_THRESHOLD) {
                        converged = false;
                        break;
                    }
                }
                if (converged) {
                    out.println("(converged)");
                    return;
                }
            }
        }
        out.println("(max iterations)");
    }

    private static void runPhase(HttpClient client, HttpRequest request, int concurrency, int seconds, AtomicLong ops,
            @Nullable LatencyCollector latency) {
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
            LatencyCollector latency, CountDownLatch latch) {
        if (System.nanoTime() >= deadline) {
            latch.countDown();
            return;
        }

        val sendTime = System.nanoTime();
        client.sendAsync(request, BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error == null) {
                if (latency != null) {
                    latency.record(System.nanoTime() - sendTime);
                }
                ops.incrementAndGet();
            }
            fireNext(client, request, deadline, ops, latency, latch);
        });
    }
}
