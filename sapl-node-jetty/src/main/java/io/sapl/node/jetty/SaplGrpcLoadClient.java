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
package io.sapl.node.jetty;

import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.val;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal gRPC load client for {@code PolicyDecisionPointService.DecideOnce}.
 * Each worker virtual thread sends unary calls in a tight loop for the
 * configured duration; the main thread reports throughput and a
 * latency histogram (p50, p90, p99, p99.9, max) computed from
 * per-request service time samples.
 * <p>
 * Usage:
 * {@code java -cp ... SaplGrpcLoadClient <host:port> <workers> <duration-seconds>}.
 * Defaults: {@code localhost:9091 32 30}.
 */
public final class SaplGrpcLoadClient {

    private SaplGrpcLoadClient() {
    }

    private static final MethodDescriptor<AuthorizationSubscription, AuthorizationDecision> DECIDE_ONCE = MethodDescriptor
            .<AuthorizationSubscription, AuthorizationDecision>newBuilder().setType(MethodType.UNARY)
            .setFullMethodName("io.sapl.api.proto.PolicyDecisionPointService/DecideOnce")
            .setRequestMarshaller(new SaplGrpcMarshaller<>(SaplProtobufCodec::writeAuthorizationSubscription,
                    SaplProtobufCodec::readAuthorizationSubscription))
            .setResponseMarshaller(new SaplGrpcMarshaller<>(SaplProtobufCodec::writeAuthorizationDecision,
                    SaplProtobufCodec::readAuthorizationDecision))
            .build();

    public static void main(String[] args) throws Exception {
        val target          = args.length > 0 ? args[0] : "localhost:9091";
        val workers         = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        val durationSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        val channelCount    = args.length > 3 ? Integer.parseInt(args[3]) : Math.max(1, workers / 16);

        val subscription = buildRbacSubscription();
        val channels     = new ManagedChannel[channelCount];
        for (int i = 0; i < channelCount; i++) {
            channels[i] = NettyChannelBuilder.forTarget(target).usePlaintext()
                    .executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        }

        warmup(channels, subscription, workers);

        System.out.printf("Driving %s with %d workers across %d channels for %ds...%n", target, workers, channelCount,
                durationSeconds);
        val deadline   = System.nanoTime() + durationSeconds * 1_000_000_000L;
        val sampleBank = new ConcurrentLinkedDeque<Long>();
        val total      = new AtomicLong();
        val errors     = new AtomicLong();
        val pool       = Executors.newVirtualThreadPerTaskExecutor();

        val start = System.nanoTime();
        for (int i = 0; i < workers; i++) {
            val channel = channels[i % channelCount];
            pool.submit(() -> drive(channel, subscription, deadline, sampleBank, total, errors));
        }
        pool.shutdown();
        pool.awaitTermination(durationSeconds + 5, java.util.concurrent.TimeUnit.SECONDS);
        val elapsedNs = System.nanoTime() - start;

        val samples = sampleBank.toArray(new Long[0]);
        java.util.Arrays.sort(samples);
        val sec = elapsedNs / 1_000_000_000.0;
        System.out.printf("requests:  %,d in %.2fs%n", total.get(), sec);
        System.out.printf("errors:    %,d%n", errors.get());
        System.out.printf("rps:       %,.0f%n", total.get() / sec);
        if (samples.length > 0) {
            System.out.printf("p50:       %,.3f ms%n", pct(samples, 50) / 1e6);
            System.out.printf("p90:       %,.3f ms%n", pct(samples, 90) / 1e6);
            System.out.printf("p99:       %,.3f ms%n", pct(samples, 99) / 1e6);
            System.out.printf("p99.9:     %,.3f ms%n", pct(samples, 99.9) / 1e6);
            System.out.printf("max:       %,.3f ms%n", samples[samples.length - 1] / 1e6);
        }
        for (val c : channels) {
            c.shutdownNow();
        }
    }

    private static void warmup(ManagedChannel[] channels, AuthorizationSubscription sub, int workers) {
        val pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < workers; i++) {
            val channel = channels[i % channels.length];
            pool.submit(() -> {
                for (int j = 0; j < 200; j++) {
                    callUnary(channel, sub);
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final int INFLIGHT_PER_WORKER = Integer.getInteger("sapl.inflight", 8);

    private static void drive(ManagedChannel channel, AuthorizationSubscription sub, long deadline,
            java.util.Collection<Long> sampleBank, AtomicLong total, AtomicLong errors) {
        val inflight = new java.util.ArrayDeque<long[]>();
        val futures  = new java.util.ArrayDeque<com.google.common.util.concurrent.ListenableFuture<AuthorizationDecision>>();
        try {
            while (System.nanoTime() < deadline) {
                while (futures.size() < INFLIGHT_PER_WORKER && System.nanoTime() < deadline) {
                    inflight.addLast(new long[] { System.nanoTime() });
                    futures.addLast(ClientCalls
                            .futureUnaryCall(channel.newCall(DECIDE_ONCE, io.grpc.CallOptions.DEFAULT), sub));
                }
                if (!futures.isEmpty()) {
                    val f  = futures.removeFirst();
                    val t0 = inflight.removeFirst()[0];
                    try {
                        f.get();
                        sampleBank.add(System.nanoTime() - t0);
                        total.incrementAndGet();
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    }
                }
            }
            while (!futures.isEmpty()) {
                val f  = futures.removeFirst();
                val t0 = inflight.removeFirst()[0];
                try {
                    f.get();
                    sampleBank.add(System.nanoTime() - t0);
                    total.incrementAndGet();
                } catch (Throwable t) {
                    errors.incrementAndGet();
                }
            }
        } catch (Throwable failure) {
            errors.incrementAndGet();
        }
    }

    private static AuthorizationDecision callUnary(ManagedChannel channel, AuthorizationSubscription sub) {
        ClientCall<AuthorizationSubscription, AuthorizationDecision> call = channel.newCall(DECIDE_ONCE,
                io.grpc.CallOptions.DEFAULT);
        return ClientCalls.blockingUnaryCall(call, sub);
    }

    private static long pct(Long[] sortedSamples, double p) {
        val idx = (int) Math.min(sortedSamples.length - 1L, Math.round(sortedSamples.length * p / 100.0));
        return sortedSamples[idx];
    }

    private static AuthorizationSubscription buildRbacSubscription() {
        val subject  = io.sapl.api.model.ObjectValue.builder().put("username", Value.of("bob"))
                .put("role", Value.of("test")).build();
        val resource = io.sapl.api.model.ObjectValue.builder().put("type", Value.of("foo123")).build();
        return new AuthorizationSubscription(subject, Value.of("read"), resource, Value.UNDEFINED, Value.EMPTY_OBJECT);
    }
}
