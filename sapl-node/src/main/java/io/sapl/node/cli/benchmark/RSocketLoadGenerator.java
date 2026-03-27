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

/**
 * RSocket/protobuf load generator using multiple TCP connections with virtual
 * threads. Each virtual thread fires sequential blocking request-response
 * cycles on a multiplexed RSocket connection.
 * <p>
 * Multiple connections distribute frame processing across kernel socket
 * buffers and Netty channels. Validated at 1.57M req/s on a desktop
 * workstation (8 connections, 512 VT/connection).
 * <p>
 * The protobuf payload is pre-serialized to eliminate client-side
 * serialization overhead from the measurement.
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
     * @param protoPayload pre-serialized protobuf subscription
     * @param connections number of TCP connections
     * @param vtPerConnection virtual threads per connection
     * @param warmupSeconds warmup duration (convergence-based)
     * @param measureSeconds measurement duration
     * @param out writer for progress output
     * @return the benchmark result with throughput and latency distribution
     */
    public static BenchmarkResult run(String host, int port, byte[] protoPayload, int connections, int vtPerConnection,
            int warmupSeconds, int measureSeconds, PrintWriter out) {
        val routeBytes = ROUTE.getBytes(StandardCharsets.UTF_8);
        val totalVTs   = connections * vtPerConnection;
        val sockets    = new ArrayList<RSocket>(connections);

        try {
            for (int i = 0; i < connections; i++) {
                val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(host, port)).block();
                if (rsocket == null || rsocket.isDisposed()) {
                    out.println("  ERROR: Failed to connect to rsocket://" + host + ":" + port);
                    return emptyResult(connections, vtPerConnection);
                }
                sockets.add(rsocket);
            }
            out.printf("  Connected %d socket(s), %d VT/conn = %d total%n", connections, vtPerConnection, totalVTs);

            if (warmupSeconds > 0) {
                warmupUntilConverged(sockets, protoPayload, routeBytes, vtPerConnection, out);
            }

            val ops     = new AtomicLong(0);
            val latency = new LatencyCollector(MAX_LATENCY_SAMPLES);
            val start   = System.nanoTime();
            runPhase(sockets, protoPayload, routeBytes, vtPerConnection, measureSeconds, ops, latency);
            val elapsed    = (System.nanoTime() - start) / 1_000_000_000.0;
            val throughput = ops.get() / elapsed;

            out.printf("  %d conns x %d VT: %,.0f req/s%n", connections, vtPerConnection, throughput);
            out.flush();

            val label = "rsocket-%dc-%dvt".formatted(connections, vtPerConnection);
            return BenchmarkResult.fromIterations(label, 1, List.of(throughput), latency.toLatency());
        } finally {
            for (val s : sockets) {
                s.dispose();
            }
        }
    }

    private static void warmupUntilConverged(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes,
            int vtPerConnection, PrintWriter out) {
        out.print("  Warmup: ");
        val samples = new long[MAX_WARMUP_ITERATIONS];
        var stable  = 0;
        for (int i = 0; i < MAX_WARMUP_ITERATIONS; i++) {
            val ops = new AtomicLong(0);
            runPhase(sockets, protoPayload, routeBytes, vtPerConnection, WARMUP_INTERVAL_SECS, ops, null);
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

    private static void runPhase(List<RSocket> sockets, byte[] protoPayload, byte[] routeBytes, int vtPerConnection,
            int seconds, AtomicLong ops, LatencyCollector latency) {
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
                        if (latency != null) {
                            latency.record(elapsed);
                        }
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

    private static BenchmarkResult emptyResult(int connections, int vtPerConnection) {
        val label = "rsocket-%dc-%dvt".formatted(connections, vtPerConnection);
        return BenchmarkResult.fromIterations(label, 1, List.of());
    }
}
