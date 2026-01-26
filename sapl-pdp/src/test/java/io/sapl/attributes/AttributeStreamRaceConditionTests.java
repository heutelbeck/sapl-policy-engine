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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinder;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Race condition stress tests for AttributeStream.
 * <p>
 * These tests intentionally create concurrent scenarios that could expose
 * threading bugs. Tests use:
 * <ul>
 * <li>CountDownLatch to synchronize thread starts and maximize contention</li>
 * <li>RepeatedTest to increase probability of exposing rare races</li>
 * <li>Deliberate delays to hit specific timing windows</li>
 * <li>Multiple verification points to detect inconsistent state</li>
 * </ul>
 */
@Timeout(10)
@DisplayName("AttributeStreamRaceCondition")
class AttributeStreamRaceConditionTests {

    private static final Duration SHORT_GRACE_PERIOD = Duration.ofMillis(100);
    private static final Duration LONG_GRACE_PERIOD  = Duration.ofSeconds(10);

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static AttributeFinderInvocation createInvocation() {
        return new AttributeFinderInvocation("test-security", "test.attribute", List.of(), Duration.ofMillis(100),
                Duration.ofSeconds(1), Duration.ofMillis(10), 0, false, EMPTY_CTX);
    }

    /**
     * Tests concurrent connect operations with active subscription.
     * <p>
     * Creates 10 threads that simultaneously connect different PIPs to the same
     * stream while values are being consumed.
     * Each PIP emits a distinct identifier.
     * <p>
     * Expected: Stream remains functional and emits values from the winning PIP.
     * Race condition being tested: Multiple
     * threads racing to update currentPipSubscription.
     */
    @RepeatedTest(10)
    void concurrentConnectsStreamRemainsFunctional() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val results    = new CopyOnWriteArrayList<Value>();
        val latch      = new CountDownLatch(10);

        val subscription = stream.getStream().subscribe(results::add);

        val threads = new ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            val pipId = i;
            threads.add(new Thread(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                val pip = (AttributeFinder) inv -> Flux.just(Value.of("pip-" + pipId));
                stream.connectToPolicyInformationPoint(pip);
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS)
                .until(() -> results.stream().anyMatch(value -> !(value instanceof ErrorValue)));

        subscription.dispose();

        val validValues = results.stream()
                .filter(value -> value instanceof TextValue tv && tv.value().startsWith("pip-")).toList();

        assertThat(validValues).as("Stream should emit values after concurrent connects").isNotEmpty();
    }

    /**
     * Tests PIP connection and re-subscription during grace period.
     * <p>
     * Simulates connecting a new PIP near the end of the grace period and
     * immediately re-subscribing. The
     * re-subscription should reset the grace period timer and preserve the new PIP.
     * <p>
     * Expected: New PIP is preserved, stream emits new values, cleanup is deferred.
     * Race condition being tested: PIP
     * swap and re-subscription during grace period.
     */
    @RepeatedTest(5)
    void reconnectAndResubscribeDuringGracePeriodPipPreserved() throws Exception {
        val invocation    = createInvocation();
        val cleanupCalled = new AtomicInteger(0);
        val stream        = new AttributeStream(invocation, s -> cleanupCalled.incrementAndGet(), LONG_GRACE_PERIOD);
        val initialPip    = (AttributeFinder) inv -> Flux.just(Value.of("initial"));
        stream.connectToPolicyInformationPoint(initialPip);

        val subscription = stream.getStream().subscribe();
        subscription.dispose();

        // Thread.sleep is intentional here: we need wall-clock delay to simulate
        // realistic
        // reconnection timing, not wait for a condition. Using LONG_GRACE_PERIOD (10s)
        // with
        // 50ms sleep provides large margin for CI environments with variable timing.
        Thread.sleep(50);

        val newPip = (AttributeFinder) inv -> Flux.just(Value.of("reconnected"));
        stream.connectToPolicyInformationPoint(newPip);

        val reconnectedStream = stream.getStream();
        assertThat(reconnectedStream)
                .as("Stream should still be available during grace period (if null, grace period expired prematurely)")
                .isNotNull();

        val result = reconnectedStream.blockFirst(Duration.ofSeconds(1));

        assertThat(result).as("Stream should emit value from new PIP").isNotNull()
                .matches(value -> value instanceof TextValue tv && "reconnected".equals(tv.value()));

        assertThat(cleanupCalled.get()).as("Cleanup should not have fired due to re-subscription").isZero();
    }

    /**
     * Tests that streams are properly removed from broker index after grace period.
     * <p>
     * When grace period expires without re-subscription, the cleanup callback
     * removes the stream from the broker's
     * index. Future requests for the same invocation should create a new stream,
     * not return a disposed one.
     * <p>
     * Expected: Broker creates new stream after cleanup, old stream marked as
     * disposed. Tests: Grace period cleanup
     * integration with broker.
     */
    @RepeatedTest(20)
    void afterGracePeriodExpirationBrokerCreatesNewStream() throws Exception {
        val invocation    = createInvocation();
        val cleanupCalled = new AtomicInteger(0);
        val stream        = new AttributeStream(invocation, s -> cleanupCalled.incrementAndGet(), SHORT_GRACE_PERIOD);

        val initialPip = (AttributeFinder) inv -> Flux.just(Value.of("initial"));
        stream.connectToPolicyInformationPoint(initialPip);

        val subscription = stream.getStream().subscribe();
        subscription.dispose();

        await().pollDelay(50, MILLISECONDS).atMost(500, MILLISECONDS).until(() -> cleanupCalled.get() == 1);

        assertThat(cleanupCalled.get()).as("Cleanup should have fired after grace period").isEqualTo(1);

        val streamAfterCleanup = stream.getStream();
        assertThat(streamAfterCleanup).as("Stream should be disposed and return null").isNull();
    }

    /**
     * Tests disconnect racing with connect op.
     * <p>
     * Two threads race: one connects a slow-starting PIP (delayed emission),
     * another disconnects immediately. The
     * disconnect may happen before, during, or after the connect completes.
     * <p>
     * Expected: Either disconnect errors appears, or the PIP connects and then gets
     * disconnected. The stream should not
     * hang or crash. Race condition being tested: Disconnect during PIP
     * subscription setup.
     */
    @RepeatedTest(10)
    void disconnectRacingWithConnectStreamRemainsStable() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val results    = new CopyOnWriteArrayList<Value>();
        val latch      = new CountDownLatch(2);

        stream.getStream().subscribe(results::add);

        val connectThread = new Thread(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            val slowPip = (AttributeFinder) inv -> Flux.<Value>just(Value.of("slow-value"))
                    .delayElements(Duration.ofMillis(50));
            stream.connectToPolicyInformationPoint(slowPip);
        });

        val disconnectThread = new Thread(() -> {
            latch.countDown();
            try {
                latch.await();
                // Intentional delay to create race timing window with connect op
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stream.disconnectFromPolicyInformationPoint();
        });

        connectThread.start();
        disconnectThread.start();

        connectThread.join();
        disconnectThread.join();

        await().pollDelay(10, MILLISECONDS).atMost(300, MILLISECONDS).until(() -> results.stream()
                .anyMatch(value -> value instanceof ErrorValue ev && ev.message().contains("disconnected")));

        val hasDisconnectError = results.stream()
                .anyMatch(value -> value instanceof ErrorValue ev && ev.message().contains("disconnected"));

        assertThat(hasDisconnectError).as("Disconnect errors should be published").isTrue();
        assertThat(results).as("Stream should publish at least the disconnect errors").isNotEmpty();
    }

    /**
     * Tests multiple concurrent disconnect calls.
     * <p>
     * Multiple threads call disconnect simultaneously on an active stream. The
     * implementation should ensure only one
     * disconnect errors is published.
     * <p>
     * Expected: Exactly one disconnect errors published. Race condition being
     * tested: Multiple threads racing to set
     * disconnected flag and publish errors.
     */
    @RepeatedTest(10)
    void concurrentDisconnectsSingleErrorPublished() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val results    = new CopyOnWriteArrayList<Value>();
        val latch      = new CountDownLatch(5);

        val pip = (AttributeFinder) inv -> Flux.just(Value.of("connected"));
        stream.connectToPolicyInformationPoint(pip);
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS).until(() -> results.stream()
                .anyMatch(value -> value instanceof TextValue tv && "connected".equals(tv.value())));

        val threads = new ArrayList<Thread>();
        for (int i = 0; i < 5; i++) {
            threads.add(new Thread(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                stream.disconnectFromPolicyInformationPoint();
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Wait for disconnect errors to appear
        await().pollDelay(20, MILLISECONDS).atMost(300, MILLISECONDS).until(() -> results.stream()
                .anyMatch(value -> value instanceof ErrorValue ev && ev.message().contains("disconnected")));

        // Verify count has stabilized (no additional errors appear)
        await().pollDelay(100, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> true);

        val disconnectErrors = results.stream()
                .filter(value -> value instanceof ErrorValue ev && ev.message().contains("disconnected")).count();

        assertThat(disconnectErrors).as("Should publish exactly one disconnect errors").isEqualTo(1);
    }

    /**
     * Tests connect racing with another connect op.
     * <p>
     * Two threads connect different PIPs simultaneously. Each PIP emits
     * continuously. The implementation should
     * properly dispose the losing PIP's subscription.
     * <p>
     * Expected: Only values from one PIP appear after both connects complete. Race
     * condition being tested: Disposal of
     * old subscription during concurrent connects.
     */
    @RepeatedTest(10)
    void concurrentConnectsWithStreamingPipsNoValueMixing() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val results    = new CopyOnWriteArrayList<Value>();
        val latch      = new CountDownLatch(2);

        stream.getStream().subscribe(results::add);

        val pip1 = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(10)).map(i -> Value.of("pip1-" + i));
        val pip2 = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(10)).map(i -> Value.of("pip2-" + i));

        val thread1 = new Thread(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stream.connectToPolicyInformationPoint(pip1);
        });

        val thread2 = new Thread(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stream.connectToPolicyInformationPoint(pip2);
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // Wait for sufficient values to be emitted (at least 5 non-errors values)
        await().pollDelay(20, MILLISECONDS).atMost(500, MILLISECONDS).until(() -> {
            val nonErrorCount = results.stream().filter(value -> !(value instanceof ErrorValue)).count();
            return nonErrorCount >= 5;
        });

        val snapshot = new ArrayList<>(results);
        if (snapshot.isEmpty()) {
            return;
        }

        val lastValue = snapshot.getLast();
        if (lastValue instanceof ErrorValue) {
            return;
        }

        val winningPip = lastValue instanceof TextValue tv && tv.value().startsWith("pip1") ? "pip1" : "pip2";

        val recentValues  = snapshot.subList(Math.max(0, snapshot.size() - 5), snapshot.size());
        val allFromWinner = recentValues.stream().filter(value -> !(value instanceof ErrorValue))
                .allMatch(value -> value instanceof TextValue tv && tv.value().startsWith(winningPip));

        assertThat(allFromWinner).as("Recent values should all be from the winning PIP").isTrue();
    }

    /**
     * Chaos test with mixed operations under high concurrency.
     * <p>
     * Multiple threads perform random operations: connect, disconnect, subscribe,
     * cancel. The stream should handle all
     * operations without crashing or deadlocking.
     * <p>
     * Expected: Stream remains functional and can emit values at the end. Tests
     * overall thread safety under
     * unpredictable load.
     */
    @RepeatedTest(3)
    void chaosMonkeyMixedOperations() throws Exception {
        val invocation     = createInvocation();
        val cleanupCalled  = new AtomicInteger(0);
        val stream         = new AttributeStream(invocation, s -> cleanupCalled.incrementAndGet(), SHORT_GRACE_PERIOD);
        val operationCount = new AtomicInteger(0);
        val latch          = new CountDownLatch(20);
        val threads        = new ArrayList<Thread>();

        for (int i = 0; i < 20; i++) {
            val threadId = i;
            threads.add(new Thread(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                switch (threadId % 4) {
                case 0  -> {
                    val pip = (AttributeFinder) inv -> Flux.just(Value.of("pip-" + threadId));
                    stream.connectToPolicyInformationPoint(pip);
                    operationCount.incrementAndGet();
                }
                case 1  -> {
                    stream.disconnectFromPolicyInformationPoint();
                    operationCount.incrementAndGet();
                }
                case 2  -> {
                    val subscription = stream.getStream().subscribe();
                    subscription.dispose();
                    operationCount.incrementAndGet();
                }
                default -> { // 3+ not case 3 to satisfy sonarqube
                    try {
                        stream.getStream().blockFirst(Duration.ofMillis(100));
                        operationCount.incrementAndGet();
                    } catch (Exception e) {
                        // Expected under chaos
                    }
                }
                }
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        val finalPip = (AttributeFinder) inv -> Flux.just(Value.of("final-check"));
        stream.connectToPolicyInformationPoint(finalPip);

        val result = stream.getStream().blockFirst(Duration.ofSeconds(1));
        assertThat(result).as("Stream should remain functional after chaos").isNotNull();
    }

    /**
     * Tests extreme backpressure handling.
     * <p>
     * Creates a fast producer emitting 1000 values and a slow consumer with 10ms
     * delays. The bounded buffer should
     * prevent memory leaks while dropping excess values.
     * <p>
     * Expected: Some values received, none crashed, buffer doesn't overflow memory.
     * Tests the bounded buffer (128
     * elements) under extreme load.
     */
    @Test
    void extremeBackpressureHandlesGracefully() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val received   = new AtomicInteger(0);

        stream.getStream().delayElements(Duration.ofMillis(10)).subscribe(value -> received.incrementAndGet());

        val pip = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(1)).take(1000).map(Value::of);
        stream.connectToPolicyInformationPoint(pip);

        // Wait for producer to emit values and consumer to process some (demonstrating
        // backpressure)
        // Producer emits 1000 values at 1ms intervals (~1 second), consumer processes
        // at ~10ms per value
        await().pollDelay(200, MILLISECONDS).atMost(Duration.ofSeconds(2)).until(() -> received.get() > 10);

        // Allow additional time for buffered values to process
        await().pollDelay(200, MILLISECONDS).atMost(300, MILLISECONDS).until(() -> true);

        val receivedCount = received.get();
        assertThat(receivedCount).as("Should receive some values but drop some under backpressure").isBetween(1, 999);
    }

    /**
     * Tests hot-swapping PIPs during active high-frequency emission.
     * <p>
     * Starts with a fast-emitting PIP, then hot-swaps to another PIP mid-stream.
     * Valueidates that the swap happens
     * cleanly without value corruption or hanging.
     * <p>
     * Expected: Valueues from second PIP appear, no intermixed values. Tests PIP
     * replacement under load.
     */
    @Test
    void hotSwapDuringHighFrequencyEmissionSwapSucceeds() throws Exception {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, LONG_GRACE_PERIOD);
        val results    = new CopyOnWriteArrayList<Value>();

        val pip1 = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(5)).take(100)
                .map(i -> Value.of("pip1-" + i));
        stream.connectToPolicyInformationPoint(pip1);

        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS).until(() -> results.size() >= 10);

        val pip2 = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(5)).take(100)
                .map(i -> Value.of("pip2-" + i));
        stream.connectToPolicyInformationPoint(pip2);

        await().pollDelay(10, MILLISECONDS).atMost(300, MILLISECONDS).until(() -> results.stream()
                .anyMatch(value -> value instanceof TextValue tv && tv.value().startsWith("pip2")));

        val pip2Values = results.stream()
                .filter(value -> value instanceof TextValue tv && tv.value().startsWith("pip2")).toList();

        assertThat(pip2Values).as("Should receive values from second PIP after swap").isNotEmpty();
    }
}
