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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RSocket/protobuf load generator with two engine modes:
 * <ul>
 * <li><b>reactive</b> (default): {@code Flux.range().flatMap()} for
 * saturation, {@code Flux.interval()} for paced sending</li>
 * <li><b>virtual-threads</b>: blocking request-response loops on VTs
 * (original implementation)</li>
 * </ul>
 * Paced mode uses coordinated omission correction regardless of engine.
 * The protobuf payload is pre-serialized to eliminate client-side overhead.
 */
@Slf4j
@UtilityClass
public class RSocketLoadGenerator {

    private static final int    MAX_LATENCY_SAMPLES   = 5_000_000;
    private static final int    CONVERGENCE_THRESHOLD = 5;
    private static final int    CONVERGENCE_WINDOW    = 3;
    private static final int    MAX_WARMUP_ITERATIONS = 15;
    private static final int    WARMUP_INTERVAL_SECS  = 3;
    private static final String ROUTE                 = "decide-once";

    /**
     * Runs an RSocket load test against a SAPL PDP server.
     *
     * @param host the RSocket server host
     * @param port the RSocket server port
     * @param socketPath Unix domain socket path (null for TCP)
     * @param protoPayload pre-serialized protobuf subscription
     * @param connections number of TCP connections
     * @param vtPerConnection concurrency per connection
     * @param warmupSeconds warmup duration (convergence-based)
     * @param measureSeconds measurement duration
     * @param targetRate target requests per second (0 = saturation)
     * @param useVirtualThreads true for VT engine, false for reactive
     * @param out writer for progress output
     * @return the benchmark result with throughput and latency distribution
     */
    public static BenchmarkResult run(String host, int port, String socketPath, byte[] protoPayload, int connections,
            int vtPerConnection, int warmupSeconds, int measureSeconds, int targetRate, boolean useVirtualThreads,
            PrintWriter out) {
        val routeBytes   = ROUTE.getBytes(StandardCharsets.UTF_8);
        val totalWorkers = connections * vtPerConnection;
        val sockets      = new ArrayList<RSocket>(connections);

        try {
            for (int i = 0; i < connections; i++) {
                val transport = socketPath != null
                        ? TcpClientTransport.create(reactor.netty.tcp.TcpClient.create()
                                .remoteAddress(() -> new io.netty.channel.unix.DomainSocketAddress(socketPath)))
                        : TcpClientTransport.create(host, port);
                val rsocket   = RSocketConnector.create().connect(transport).block();
                val endpoint  = socketPath != null ? "unix://" + socketPath : "rsocket://" + host + ":" + port;
                if (rsocket == null || rsocket.isDisposed()) {
                    out.println("  ERROR: Failed to connect to " + endpoint);
                    return emptyResult(connections, vtPerConnection);
                }
                sockets.add(rsocket);
            }
            val endpoint    = socketPath != null ? "unix://" + socketPath : host + ":" + port;
            val engineLabel = useVirtualThreads ? "virtual-threads" : "reactive";
            out.printf("  [%s] Connected %d socket(s) to %s, %d workers/conn = %d total%n", engineLabel, connections,
                    endpoint, vtPerConnection, totalWorkers);

            if (warmupSeconds > 0) {
                warmupUntilConverged(sockets, protoPayload, routeBytes, totalWorkers, useVirtualThreads, out);
            }

            val ops     = new AtomicLong(0);
            val latency = new LatencyCollector(MAX_LATENCY_SAMPLES);
            val start   = System.nanoTime();

            if (targetRate > 0) {
                runReactivePacedPhase(sockets, protoPayload, routeBytes, totalWorkers, measureSeconds, targetRate, ops,
                        latency);
            } else if (useVirtualThreads) {
                runVtSaturationPhase(sockets, protoPayload, routeBytes, vtPerConnection, measureSeconds, ops, latency);
            } else {
                runReactiveSaturationPhase(sockets, protoPayload, routeBytes, totalWorkers, measureSeconds, ops,
                        latency);
            }

            val elapsed    = (System.nanoTime() - start) / 1_000_000_000.0;
            val throughput = ops.get() / elapsed;

            if (targetRate > 0) {
                out.printf("  [%s] %d conns x %d workers @ %,d req/s target: %,.0f req/s actual%n", engineLabel,
                        connections, vtPerConnection, targetRate, throughput);
            } else {
                out.printf("  [%s] %d conns x %d workers: %,.0f req/s%n", engineLabel, connections, vtPerConnection,
                        throughput);
            }
            out.flush();

            val label = targetRate > 0 ? "rsocket-%dc-%dw-%dr".formatted(connections, vtPerConnection, targetRate)
                    : "rsocket-%dc-%dw".formatted(connections, vtPerConnection);
            return BenchmarkResult.fromIterations(label, 1, List.of(throughput), latency.toLatency());
        } finally {
            for (val s : sockets) {
                s.dispose();
            }
        }
    }

    // ---- Reactive engine ----

    private static Mono<Void> sendOnce(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes, int workerIndex) {
        val rsocket = sockets.get(workerIndex % sockets.size());
        val payload = DefaultPayload.create(protoPayload.clone(), routeBytes.clone());
        return rsocket.requestResponse(payload).doOnNext(r -> r.release()).then();
    }

    private static void runReactiveSaturationPhase(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes,
            int totalWorkers, int seconds, AtomicLong ops, LatencyCollector latency) {
        val deadline = Mono.delay(Duration.ofSeconds(seconds));
        Flux.range(0, totalWorkers).flatMap(worker -> Mono.defer(() -> {
            val sendTime = System.nanoTime();
            return sendOnce(sockets, protoPayload, routeBytes, worker).doOnSuccess(ignored -> {
                latency.addSample(System.nanoTime() - sendTime);
                ops.incrementAndGet();
            });
        }).repeat().takeUntilOther(deadline), totalWorkers).blockLast();
    }

    private static void runReactivePacedPhase(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes,
            int totalWorkers, int seconds, int targetRate, AtomicLong ops, LatencyCollector latency) {
        val intervalNanos = 1_000_000_000L / targetRate;
        val startTime     = System.nanoTime();
        val workerCount   = sockets.size();

        Flux.interval(Duration.ofNanos(intervalNanos)).take(Duration.ofSeconds(seconds)).flatMap(tick -> {
            val intendedSendTime = startTime + tick * intervalNanos;
            val workerIndex      = (int) (tick % workerCount);
            return sendOnce(sockets, protoPayload, routeBytes, workerIndex).doOnSuccess(ignored -> {
                latency.addSample(System.nanoTime() - intendedSendTime);
                ops.incrementAndGet();
            });
        }, totalWorkers).blockLast();
    }

    // ---- Virtual threads engine ----

    private static void runVtSaturationPhase(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes,
            int vtPerConnection, int seconds, AtomicLong ops, LatencyCollector latency) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        val totalVTs = sockets.size() * vtPerConnection;
        val latch    = new CountDownLatch(totalVTs);

        for (int i = 0; i < totalVTs; i++) {
            val rsocket = sockets.get(i % sockets.size());
            Thread.ofVirtual().start(() -> {
                try {
                    while (System.nanoTime() < deadline) {
                        val payload  = DefaultPayload.create(protoPayload.clone(), routeBytes.clone());
                        val sendTime = System.nanoTime();
                        val response = rsocket.requestResponse(payload).block();
                        val elapsed  = System.nanoTime() - sendTime;
                        if (response != null) {
                            response.release();
                        }
                        latency.addSample(elapsed);
                        ops.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("RSocket worker stopped: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(seconds + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Warmup (uses the selected engine) ----

    private static void warmupUntilConverged(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes,
            int totalWorkers, boolean useVirtualThreads, PrintWriter out) {
        out.print("  Warmup: ");
        val samples = new long[MAX_WARMUP_ITERATIONS];
        for (int i = 0; i < MAX_WARMUP_ITERATIONS; i++) {
            val ops     = new AtomicLong(0);
            val discard = new LatencyCollector(1);
            if (useVirtualThreads) {
                val vtPerConn = totalWorkers / sockets.size();
                runVtSaturationPhase(sockets, protoPayload, routeBytes, vtPerConn, WARMUP_INTERVAL_SECS, ops, discard);
            } else {
                runReactiveSaturationPhase(sockets, protoPayload, routeBytes, totalWorkers, WARMUP_INTERVAL_SECS, ops,
                        discard);
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

    private static BenchmarkResult emptyResult(int connections, int vtPerConnection) {
        val label = "rsocket-%dc-%dw".formatted(connections, vtPerConnection);
        return BenchmarkResult.fromIterations(label, 1, List.of());
    }

}
