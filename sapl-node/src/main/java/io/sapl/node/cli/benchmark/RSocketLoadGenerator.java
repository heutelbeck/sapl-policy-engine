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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive RSocket/protobuf load generator using multiple TCP connections.
 * Saturation mode uses {@code Flux.range().flatMap()} for maximum
 * throughput with backpressure-driven concurrency. Paced mode uses
 * {@code Flux.interval()} for constant-rate sending with coordinated
 * omission correction.
 * <p>
 * Multiple connections distribute frame processing across kernel socket
 * buffers and Netty channels. The protobuf payload is pre-serialized to
 * eliminate client-side serialization overhead from the measurement.
 */
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
     * @param concurrencyPerConnection concurrency per connection
     * @param warmupSeconds warmup duration (convergence-based)
     * @param measureSeconds measurement duration
     * @param targetRate target requests per second (0 = saturation)
     * @param out writer for progress output
     * @return the benchmark result with throughput and latency distribution
     */
    private record LoadResources(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes) {}

    public static BenchmarkResult run(String host, int port, String socketPath, byte[] protoPayload, int connections,
            int concurrencyPerConnection, int warmupSeconds, int measureSeconds, int targetRate, PrintWriter out) {
        Hooks.onErrorDropped(e -> {});

        val totalConcurrency = connections * concurrencyPerConnection;
        val sockets          = connectAll(host, port, socketPath, connections, out);

        if (sockets == null) {
            return emptyResult(connections, concurrencyPerConnection);
        }

        val res = new LoadResources(sockets, protoPayload, ROUTE.getBytes(StandardCharsets.UTF_8));

        try {
            val endpoint = socketPath != null ? "unix://" + socketPath : host + ":" + port;
            out.printf("  Connected %d socket(s) to %s, %d concurrent/conn = %d total%n", connections, endpoint,
                    concurrencyPerConnection, totalConcurrency);

            if (warmupSeconds > 0) {
                warmupUntilConverged(res, totalConcurrency, out);
            }

            val ops     = new AtomicLong(0);
            val latency = new LatencyCollector(MAX_LATENCY_SAMPLES);
            val start   = System.nanoTime();

            if (targetRate > 0) {
                runPacedPhase(res, totalConcurrency, measureSeconds, targetRate, ops, latency);
            } else {
                runSaturationPhase(res, totalConcurrency, measureSeconds, ops, latency);
            }

            val elapsed    = (System.nanoTime() - start) / 1_000_000_000.0;
            val throughput = ops.get() / elapsed;

            if (targetRate > 0) {
                out.printf("  %d conns x %d concurrent @ %d req/s target: %.0f req/s actual%n", connections,
                        concurrencyPerConnection, targetRate, throughput);
            } else {
                out.printf("  %d conns x %d concurrent: %.0f req/s%n", connections, concurrencyPerConnection,
                        throughput);
            }
            out.flush();

            val label = targetRate > 0
                    ? "rsocket-%dc-%dw-%dr".formatted(connections, concurrencyPerConnection, targetRate)
                    : "rsocket-%dc-%dw".formatted(connections, concurrencyPerConnection);
            return BenchmarkResult.fromIterations(label, 1, List.of(throughput), latency.toLatency());
        } finally {
            for (val s : sockets) {
                s.dispose();
            }
        }
    }

    private static List<RSocket> connectAll(String host, int port, String socketPath, int connections,
            PrintWriter out) {
        val sockets = new ArrayList<RSocket>(connections);
        for (int i = 0; i < connections; i++) {
            val transport = socketPath != null
                    ? TcpClientTransport.create(reactor.netty.tcp.TcpClient.create()
                            .remoteAddress(() -> new io.netty.channel.unix.DomainSocketAddress(socketPath)))
                    : TcpClientTransport.create(host, port);
            val rsocket   = RSocketConnector.create().connect(transport).block();
            val endpoint  = socketPath != null ? "unix://" + socketPath : "rsocket://" + host + ":" + port;
            if (rsocket == null || rsocket.isDisposed()) {
                out.println("  ERROR: Failed to connect to " + endpoint);
                sockets.forEach(RSocket::dispose);
                return null;
            }
            sockets.add(rsocket);
        }
        return sockets;
    }

    private static Mono<Void> sendOnce(LoadResources res, int workerIndex) {
        val rsocket = res.sockets().get(workerIndex % res.sockets().size());
        val payload = DefaultPayload.create(res.protoPayload().clone(), res.routeBytes().clone());
        return rsocket.requestResponse(payload).doOnNext(r -> r.release()).then();
    }

    private static void runSaturationPhase(LoadResources res, int totalConcurrency, int seconds, AtomicLong ops,
            LatencyCollector latency) {
        val running = new AtomicBoolean(true);
        Schedulers.parallel().schedule(() -> running.set(false), seconds, TimeUnit.SECONDS);

        Flux.range(0, totalConcurrency).flatMap(worker -> Mono.defer(() -> {
            val sendTime = System.nanoTime();
            return sendOnce(res, worker).onErrorResume(e -> Mono.empty()).doOnSuccess(ignored -> {
                latency.addSample(System.nanoTime() - sendTime);
                ops.incrementAndGet();
            });
        }).repeat(running::get), totalConcurrency).blockLast();
    }

    private static void runPacedPhase(LoadResources res, int totalConcurrency, int seconds, int targetRate,
            AtomicLong ops, LatencyCollector latency) {
        val workerCount      = res.sockets().size();
        val ratePerWorker    = Math.max(1, targetRate / workerCount);
        val workerIntervalNs = 1_000_000_000L / ratePerWorker;
        val ticksPerWorker   = (long) ratePerWorker * seconds;
        val concPerWorker    = Math.max(1, totalConcurrency / workerCount);

        Flux.range(0, workerCount).flatMap(worker -> {
            return Flux.interval(Duration.ofNanos(workerIntervalNs), Schedulers.newSingle("pacer-" + worker, true))
                    .take(ticksPerWorker).onBackpressureDrop().flatMap(tick -> Mono.defer(() -> {
                        val sendTime = System.nanoTime();
                        return sendOnce(res, worker).onErrorResume(e -> Mono.empty()).doOnSuccess(ignored -> {
                            latency.addSample(System.nanoTime() - sendTime);
                            ops.incrementAndGet();
                        });
                    }), concPerWorker);
        }, workerCount).blockLast();
    }

    private static void warmupUntilConverged(LoadResources res, int totalConcurrency, PrintWriter out) {
        out.print("  Warmup: ");
        val samples = new long[MAX_WARMUP_ITERATIONS];
        for (int i = 0; i < MAX_WARMUP_ITERATIONS; i++) {
            val ops     = new AtomicLong(0);
            val discard = new LatencyCollector(1);
            runSaturationPhase(res, totalConcurrency, WARMUP_INTERVAL_SECS, ops, discard);
            val rps = ops.get() / WARMUP_INTERVAL_SECS;
            out.printf("%d/s ", rps);
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

    private static BenchmarkResult emptyResult(int connections, int concurrencyPerConnection) {
        val label = "rsocket-%dc-%dw".formatted(connections, concurrencyPerConnection);
        return BenchmarkResult.fromIterations(label, 1, List.of());
    }

}
