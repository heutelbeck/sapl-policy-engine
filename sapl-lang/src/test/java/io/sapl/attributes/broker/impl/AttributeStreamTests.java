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
package io.sapl.attributes.broker.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinder;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive test suite for AttributeStream reactive streaming system.
 * <p>
 * Tests cover the complete lifecycle and behavior of attribute streams in the
 * SAPL Policy Engine's attribute broker, including:
 * <ul>
 * <li>Basic stream functionality and multicast semantics</li>
 * <li>Grace period management for efficient re-subscription</li>
 * <li>PIP connection lifecycle (connect, disconnect, hot-swap)</li>
 * <li>Race condition prevention during concurrent operations</li>
 * <li>Timeout handling for slow PIPs</li>
 * <li>Polling behavior for changing attributes</li>
 * <li>Retry mechanisms with exponential backoff</li>
 * <li>Error handling and propagation</li>
 * <li>Complex scenarios under load</li>
 * </ul>
 * <p>
 * The test suite validates that AttributeStream correctly implements:
 * <ul>
 * <li>Thread-safe PIP hot-swapping without race conditions</li>
 * <li>Multicast distribution with last-value caching</li>
 * <li>Graceful cleanup after subscriber cancellation</li>
 * <li>Resilience features (retry, timeout, polling)</li>
 * </ul>
 */
@Timeout(5)
class AttributeStreamTests {

    private static final Duration SHORT_TIMEOUT       = Duration.ofMillis(50L);
    private static final Duration MEDIUM_TIMEOUT      = Duration.ofMillis(100L);
    private static final Duration GRACE_PERIOD        = Duration.ofMillis(200L);
    private static final Duration GRACE_PERIOD_MARGIN = Duration.ofMillis(250L);

    private static AttributeFinderInvocation createInvocation() {
        return new AttributeFinderInvocation("testConfig", "test.attribute", null, List.of(), Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofMillis(50L), 3L, false);
    }

    private static AttributeFinderInvocation createInvocation(Duration initialTimeout) {
        return new AttributeFinderInvocation("testConfig", "test.attribute", null, List.of(), Map.of(), initialTimeout,
                Duration.ofSeconds(1L), Duration.ofMillis(50L), 3L, false);
    }

    private static AttributeFinderInvocation createInvocation(Duration initialTimeout, Duration pollInterval,
            Duration backoff, long retries) {
        return new AttributeFinderInvocation("testConfig", "test.attribute", null, List.of(), Map.of(), initialTimeout,
                pollInterval, backoff, retries, false);
    }

    /* ========== Basic Functionality Tests ========== */

    /**
     * Validates that the invocation configuration is accessible via getter.
     * <p>
     * Use case: Debugging, logging, and stream identification in the broker's
     * registry.
     */
    @Test
    void whenCreatedThenInvocationIsAccessible() {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD);

        assertThat(stream.getInvocation()).isEqualTo(invocation);
    }

    /**
     * Validates that the reactive stream is accessible and ready for subscription.
     * <p>
     * Use case: Multiple policy evaluations subscribing to the same attribute
     * stream.
     */
    @Test
    void whenCreatedThenStreamIsAccessible() {
        val invocation = createInvocation();
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD);

        assertThat(stream.getStream()).isNotNull();
    }

    /**
     * Tests basic value emission from PIP to subscriber through the multicast
     * stream.
     * <p>
     * Use case: Single policy evaluation subscribing to an attribute value.
     */
    @Test
    void whenPipEmitsValueThenSubscriberReceivesValue() {
        val invocation = createInvocation();
        val testPip    = (AttributeFinder) inv -> Flux.just(Val.of("test-value"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, testPip);

        StepVerifier.create(stream.getStream().take(1)).expectNext(Val.of("test-value")).verifyComplete();
    }

    /**
     * Tests multiple value emission through the multicast stream.
     * <p>
     * Use case: Streaming attributes that emit multiple values over time.
     */
    @Test
    void whenPipEmitsMultipleValuesThenSubscriberReceivesAll() {
        val invocation = createInvocation();
        val testPip    = (AttributeFinder) inv -> Flux.just(Val.of(1), Val.of(2), Val.of(3));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, testPip);

        StepVerifier.create(stream.getStream().take(3)).expectNext(Val.of(1), Val.of(2), Val.of(3)).verifyComplete();
    }

    /* ========== Multicast and Caching Tests ========== */

    /**
     * Tests multicast semantics where multiple subscribers receive the same values
     * from a single PIP subscription.
     * <p>
     * Use case: Multiple policy rules evaluating the same attribute simultaneously
     * without redundant PIP invocations.
     * <p>
     * Validates that the replay() operator caches and multicasts values to all
     * active subscribers efficiently.
     */
    @Test
    void whenMultipleSubscribersThenAllReceiveSameValues() {
        val invocation = createInvocation();
        val testPip    = (AttributeFinder) inv -> Flux.just(Val.of("shared"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, testPip);

        StepVerifier.create(stream.getStream().take(1)).expectNext(Val.of("shared")).verifyComplete();
        StepVerifier.create(stream.getStream().take(1)).expectNext(Val.of("shared")).verifyComplete();
    }

    /**
     * Tests that new subscribers receive the last emitted value immediately through
     * the replay buffer.
     * <p>
     * Use case: Late-joining policy evaluation that needs the current attribute
     * state without waiting for the next emission.
     * <p>
     * Validates that replay(1) caches the most recent value and replays it to new
     * subscribers, ensuring consistent policy evaluation state.
     */
    @Test
    void whenNewSubscriberAfterValueThenReceivesCachedLastValue() {
        val invocation = createInvocation();
        val testPip    = (AttributeFinder) inv -> Flux.just(Val.of("cached"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, testPip);

        val firstValue = stream.getStream().blockFirst();
        assertThat(firstValue).isEqualTo(Val.of("cached"));

        val secondValue = stream.getStream().blockFirst();
        assertThat(secondValue).isEqualTo(Val.of("cached"));
    }

    /* ========== Grace Period and Cleanup Tests ========== */

    /**
     * Tests that cleanup is deferred by the grace period after the last subscriber
     * cancels.
     * <p>
     * Use case: Rapid policy re-evaluation cycles where subscriptions may cancel
     * and re-subscribe quickly (e.g., request handling with retries).
     * <p>
     * The grace period prevents expensive PIP reconnection overhead by keeping the
     * stream alive briefly after cancellation. Validates that:
     * <ul>
     * <li>Cleanup is not immediate upon cancellation</li>
     * <li>Cleanup executes after grace period expires</li>
     * <li>The stream and PIP remain available during the grace period</li>
     * </ul>
     */
    @Test
    void whenLastSubscriberCancelsThenCleanupCalledAfterGracePeriod() {
        val cleanupCalled = new AtomicInteger(0);
        val invocation    = createInvocation();
        val testPip       = (AttributeFinder) inv -> Flux.just(Val.of("test"));
        val stream        = new AttributeStream(invocation, s -> cleanupCalled.incrementAndGet(), GRACE_PERIOD,
                testPip);

        stream.getStream().blockFirst();
        assertThat(cleanupCalled.get()).isZero();
        await().atMost(GRACE_PERIOD_MARGIN.toMillis(), MILLISECONDS).until(() -> cleanupCalled.get() == 1);
    }

    /**
     * Tests that re-subscription within the grace period prevents cleanup
     * execution.
     * <p>
     * Use case: Request retry scenarios where a failed policy evaluation quickly
     * retries and re-subscribes to the same attribute.
     * <p>
     * Validates that the refCount operator resets the grace period timer when a new
     * subscriber arrives, avoiding unnecessary cleanup and PIP reconnection
     * overhead.
     */
    @Test
    void whenNewSubscriberWithinGracePeriodThenCleanupNotCalled() {
        val cleanupCalled = new AtomicInteger(0);
        val invocation    = createInvocation();
        val testPip       = (AttributeFinder) inv -> Flux.just(Val.of("test"));
        val stream        = new AttributeStream(invocation, s -> cleanupCalled.incrementAndGet(), GRACE_PERIOD,
                testPip);

        stream.getStream().blockFirst();
        assertThat(cleanupCalled.get()).isZero();

        stream.getStream().blockFirst();
        assertThat(cleanupCalled.get()).isZero();

        await().atMost(GRACE_PERIOD_MARGIN.toMillis(), MILLISECONDS).until(() -> cleanupCalled.get() == 1);
    }

    /* ========== PIP Connection and Disconnection Tests ========== */

    /**
     * Tests that disconnection publishes an error value and stops further
     * emissions.
     * <p>
     * Use case: PIP becomes unavailable (unregistered, disabled, or removed from
     * configuration) during active stream usage.
     * <p>
     * Validates that:
     * <ul>
     * <li>Values before disconnection are received normally</li>
     * <li>Disconnection triggers publication of a "PIP disconnected" error</li>
     * <li>The stream remains active (doesn't complete) to allow reconnection</li>
     * <li>No further values are emitted after disconnection</li>
     * </ul>
     */
    @Test
    void whenPipDisconnectedThenErrorValueEmitted() {
        val invocation = createInvocation();
        val testPip    = (AttributeFinder) inv -> Flux.just(Val.of("before-disconnect"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, testPip);

        val results = new CopyOnWriteArrayList<Val>();
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> !results.isEmpty());

        stream.disconnectFromPolicyInformationPoint();

        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> results.size() >= 2);

        assertThat(results.get(0)).isEqualTo(Val.of("before-disconnect"));
        assertThat(results.get(1).isError()).isTrue();
        assertThat(results.get(1).getMessage()).contains("PIP disconnected");
    }

    /**
     * Tests PIP hot-swapping by disconnecting from one PIP and connecting to
     * another.
     * <p>
     * Use case: Configuration update or PIP replacement where the attribute source
     * changes but the stream continues operating.
     * <p>
     * Validates that:
     * <ul>
     * <li>First PIP's values are received before disconnection</li>
     * <li>Disconnection error is published</li>
     * <li>Second PIP can be connected to the same stream</li>
     * <li>Second PIP's values are received normally</li>
     * <li>The stream maintains continuity throughout the swap</li>
     * </ul>
     */
    @Test
    void whenPipReconnectedThenNewValuesEmitted() {
        val invocation = createInvocation();
        val pip1       = (AttributeFinder) inv -> Flux.just(Val.of("pip1"));
        val pip2       = (AttributeFinder) inv -> Flux.just(Val.of("pip2"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, pip1);

        val results = new CopyOnWriteArrayList<Val>();
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> !results.isEmpty());

        stream.disconnectFromPolicyInformationPoint();
        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> results.size() >= 2);

        stream.connectToPolicyInformationPoint(pip2);
        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> results.size() >= 3);

        assertThat(results.get(0)).isEqualTo(Val.of("pip1"));
        assertThat(results.get(1).isError()).isTrue();
        assertThat(results.get(2)).isEqualTo(Val.of("pip2"));
    }

    /**
     * Tests that connecting a new PIP while already connected disposes of the old
     * PIP's subscription.
     * <p>
     * Use case: Hot-swapping PIPs without explicit disconnection call (atomic
     * replacement).
     * <p>
     * Validates that:
     * <ul>
     * <li>The old PIP's subscription is disposed automatically</li>
     * <li>No values from the old PIP appear after the new PIP connection</li>
     * <li>The new PIP's values dominate the stream</li>
     * <li>Memory leaks are prevented through proper disposal</li>
     * </ul>
     */
    @Test
    void whenConnectingWhileAlreadyConnectedThenOldSubscriptionDisposed() {
        val invocation = createInvocation();
        val pip1       = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(10)).map(i -> Val.of("pip1-" + i));
        val pip2       = (AttributeFinder) inv -> Flux.just(Val.of("pip2"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, pip1);

        val results = new CopyOnWriteArrayList<Val>();
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> results.size() >= 2);

        stream.connectToPolicyInformationPoint(pip2);
        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS)
                .until(() -> results.stream().anyMatch(v -> v.equals(Val.of("pip2"))));

        val pip2Index       = results.indexOf(Val.of("pip2"));
        val resultsSnapshot = new ArrayList<>(results);
        val valuesAfterPip2 = resultsSnapshot.subList(pip2Index + 1, resultsSnapshot.size());

        assertThat(valuesAfterPip2).noneMatch(v -> v.get().asText().startsWith("pip1"));
    }

    /* ========== Race Condition Prevention Tests ========== */

    /**
     * Tests that disconnection properly prevents late-arriving values from a
     * disconnected PIP from being published.
     * <p>
     * Use case: PIP hot-swapping where the old PIP may have async operations in
     * flight when disconnection occurs.
     * <p>
     * The test validates that:
     * <ul>
     * <li>The disconnected flag is set atomically before disposal</li>
     * <li>The filter() operator blocks emissions after disconnection</li>
     * <li>Async values from the old PIP do not pollute the stream</li>
     * <li>Only a single "PIP disconnected" error is emitted</li>
     * </ul>
     */
    @Test
    void whenDisconnectDuringAsyncEmissionThenNoValuesAfterDisconnectError() {
        val invocation = createInvocation();
        val slowPip    = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(10)).take(50).map(Val::of);
        val stream     = new AttributeStream(invocation, s -> {}, Duration.ofSeconds(10), slowPip);

        val results = new CopyOnWriteArrayList<Val>();
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(150, MILLISECONDS).until(() -> results.size() >= 3);

        stream.disconnectFromPolicyInformationPoint();

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS)
                .until(() -> results.stream().anyMatch(Val::isError));

        for (var i = 0; i < results.size(); i++) {
            if (results.get(i).isError()) {
                val resultsSnapshot  = new ArrayList<>(results);
                val valuesAfterError = resultsSnapshot.subList(i + 1, resultsSnapshot.size());
                assertThat(valuesAfterError).isEmpty();
                return;
            }
        }
    }

    /**
     * Tests that PIP errors occurring after disconnection are not published to
     * subscribers.
     * <p>
     * Use case: A PIP with delayed error emissions (e.g., network timeout) is
     * disconnected before the error materializes.
     * <p>
     * The test validates that:
     * <ul>
     * <li>Only the "PIP disconnected" error is published</li>
     * <li>Subsequent PIP errors are filtered out by the disconnected flag</li>
     * <li>The error count remains 1 (no duplicate errors)</li>
     * </ul>
     */
    @Test
    void whenPipThrowsErrorAfterDisconnectThenErrorNotPublished() {
        val invocation = createInvocation();
        val faultyPip  = (AttributeFinder) inv -> Flux.concat(Flux.just(Val.of("before-error")),
                Flux.<Val>error(new RuntimeException("async-error")).delaySubscription(Duration.ofMillis(50)));
        val stream     = new AttributeStream(invocation, s -> {}, Duration.ofSeconds(10), faultyPip);

        val results      = new CopyOnWriteArrayList<Val>();
        val subscription = stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(100, MILLISECONDS).until(() -> !results.isEmpty());

        stream.disconnectFromPolicyInformationPoint();

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS)
                .until(() -> results.stream().anyMatch(Val::isError));

        val errors = results.stream().filter(Val::isError).toList();

        subscription.dispose();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getMessage()).contains("PIP disconnected");
    }

    /* ========== Timeout Behavior Tests ========== */

    /**
     * Tests initial timeout handling when a PIP is slow to emit its first value.
     * <p>
     * Use case: External system with high latency or cold-start delays where policy
     * evaluation needs a timely result (even if UNDEFINED).
     * <p>
     * Validates that:
     * <ul>
     * <li>UNDEFINED is emitted if the first value doesn't arrive within
     * initialTimeout</li>
     * <li>The subscription remains active (doesn't cancel)</li>
     * <li>The actual value arrives after the timeout and is still published</li>
     * <li>Policy evaluation can proceed with UNDEFINED while waiting for real
     * data</li>
     * </ul>
     */
    @Test
    void whenPipSlowThenInitialTimeoutEmitsUndefined() {
        val invocation = createInvocation(SHORT_TIMEOUT);
        val slowPip    = (AttributeFinder) inv -> Flux.just(Val.of("slow")).delayElements(MEDIUM_TIMEOUT);
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, slowPip);

        StepVerifier.create(stream.getStream().take(2)).expectNext(Val.UNDEFINED).expectNext(Val.of("slow"))
                .verifyComplete();
    }

    /**
     * Tests that fast PIPs bypass timeout handling and emit values normally.
     * <p>
     * Use case: Local or cached attribute sources that respond quickly.
     * <p>
     * Validates that:
     * <ul>
     * <li>Values arriving before initialTimeout are published immediately</li>
     * <li>No UNDEFINED placeholder is emitted</li>
     * <li>Timeout overhead is minimal for responsive PIPs</li>
     * </ul>
     */
    @Test
    void whenPipFastThenNoTimeoutEmitted() {
        val invocation = createInvocation(MEDIUM_TIMEOUT);
        val fastPip    = (AttributeFinder) inv -> Flux.just(Val.of("fast")).delayElements(SHORT_TIMEOUT);
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, fastPip);

        StepVerifier.create(stream.getStream().take(1)).expectNext(Val.of("fast")).verifyComplete();
    }

    /* ========== Polling Behavior Tests ========== */

    /**
     * Tests the polling mechanism for attributes that complete early but require
     * periodic re-evaluation.
     * <p>
     * Use case: Sensor readings, changing user status, or external system state
     * that needs continuous monitoring.
     * <p>
     * The test validates that when a PIP's Flux completes, the repeatWhen operator
     * waits for pollInterval before re-subscribing to obtain fresh values. The PIP
     * must use Flux.defer() to ensure the supplier is re-evaluated on each
     * subscription, not just once at lambda creation time.
     */
    @Test
    void whenPipCompletesEarlyThenPollingRepeatsRequest() {
        val pollInterval = Duration.ofMillis(50);
        val invocation   = createInvocation(Duration.ofMillis(10), pollInterval, Duration.ofMillis(10), 0);
        val counter      = new AtomicInteger(0);
        val pollingPip   = (AttributeFinder) inv -> Flux.defer(() -> Flux.just(Val.of(counter.incrementAndGet())));
        val stream       = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, pollingPip);

        StepVerifier.create(stream.getStream().take(3).timeout(Duration.ofMillis(500))).expectNext(Val.of(1))
                .expectNext(Val.of(2)).expectNext(Val.of(3)).verifyComplete();
    }

    /* ========== Retry Behavior Tests ========== */

    /**
     * Tests the retry mechanism with exponential backoff for transient PIP
     * failures.
     * <p>
     * Use case: Network issues, rate limiting, or temporary service unavailability
     * where the system should automatically retry before giving up.
     * <p>
     * The test validates that:
     * <ul>
     * <li>The PIP is invoked retries+1 times (initial attempt + configured
     * retries)</li>
     * <li>Each failure triggers exponential backoff before the next attempt</li>
     * <li>After exhausting retries, the PIP succeeds and publishes the success
     * value</li>
     * </ul>
     * <p>
     * Implementation note: The PIP must use Flux.defer() to re-evaluate the
     * success/failure condition on each subscription attempt. Without defer(), the
     * retryWhen operator would re-subscribe to the same error Flux repeatedly.
     */
    @ParameterizedTest
    @ValueSource(longs = { 1, 2, 3 })
    void whenPipFailsThenRetryUpToConfiguredLimit(long retries) {
        val invocation = createInvocation(Duration.ofMillis(10), Duration.ofSeconds(10), Duration.ofMillis(1), retries);
        val attempts   = new AtomicInteger(0);
        val failingPip = (AttributeFinder) inv -> Flux.defer(() -> {
                           if (attempts.incrementAndGet() <= retries) {
                               return Flux.error(new RuntimeException("attempt-" + attempts.get()));
                           }
                           return Flux.just(Val.of("success-after-retries"));
                       });
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, failingPip);

        StepVerifier.create(stream.getStream().take(1).timeout(Duration.ofSeconds(2)))
                .expectNext(Val.of("success-after-retries")).verifyComplete();

        assertThat(attempts.get()).isEqualTo((int) retries + 1);
    }

    /* ========== Empty Flux Handling Tests ========== */

    /**
     * Tests handling of PIPs that emit no values (empty Flux).
     * <p>
     * Use case: Attribute queries that return no results or optional attributes
     * that may not exist.
     * <p>
     * Validates that:
     * <ul>
     * <li>Empty streams are converted to UNDEFINED via defaultIfEmpty operator</li>
     * <li>Policy evaluation receives a defined value (not hanging
     * indefinitely)</li>
     * <li>Consistency with other "no value" scenarios</li>
     * </ul>
     */
    @Test
    void whenPipEmitsEmptyFluxThenUndefinedEmitted() {
        val invocation = createInvocation();
        val emptyPip   = (AttributeFinder) inv -> Flux.empty();
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, emptyPip);

        StepVerifier.create(stream.getStream().take(1)).expectNext(Val.UNDEFINED).verifyComplete();
    }

    /* ========== Error Handling Tests ========== */

    /**
     * Tests error propagation when a PIP fails immediately with retries disabled.
     * <p>
     * Use case: Permanent failures (invalid configuration, missing permissions, or
     * non-existent resources) that should not trigger retry attempts.
     * <p>
     * The test validates that:
     * <ul>
     * <li>Errors are wrapped in Val.error() for consistent handling</li>
     * <li>Original error messages are preserved (not wrapped in "Retries
     * exhausted")</li>
     * <li>The retry mechanism is bypassed when retries=0</li>
     * </ul>
     * <p>
     * With retries=0, the stream should publish the original error message directly
     * to help with debugging and error diagnosis.
     */
    @Test
    void whenPipEmitsErrorThenErrorValuePublished() {
        val invocation = createInvocation(Duration.ofMillis(10), Duration.ofSeconds(10), Duration.ofMillis(10), 0);
        val errorPip   = (AttributeFinder) inv -> Flux.error(new RuntimeException("test-error"));
        val stream     = new AttributeStream(invocation, s -> {}, GRACE_PERIOD, errorPip);

        StepVerifier.create(stream.getStream().take(1))
                .expectNextMatches(value -> value.isError() && value.getMessage().contains("test-error"))
                .verifyComplete();
    }

    /* ========== Complex Scenario Tests ========== */

    /**
     * Tests PIP hot-swapping under high-frequency emission load.
     * <p>
     * Use case: Configuration updates or PIP replacements during active streaming
     * with minimal disruption to ongoing policy evaluations.
     * <p>
     * Validates that:
     * <ul>
     * <li>Hot-swapping works correctly even with rapid emissions</li>
     * <li>No race conditions occur between old PIP disposal and new PIP
     * connection</li>
     * <li>The stream remains stable and continues operating</li>
     * <li>Values from the new PIP are successfully delivered</li>
     * <li>Memory and resource cleanup happens properly</li>
     * </ul>
     * <p>
     * This test exercises the thread-safety mechanisms (atomic operations, volatile
     * flags, reactive filtering) under realistic load conditions.
     */
    @Test
    void whenHotSwappingUnderLoadThenStreamRemainsStable() {
        val invocation = createInvocation();
        val pip1       = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(5)).take(100)
                .map(i -> Val.of("pip1-" + i));
        val stream     = new AttributeStream(invocation, s -> {}, Duration.ofSeconds(10), pip1);
        val pip2       = (AttributeFinder) inv -> Flux.interval(Duration.ofMillis(5)).take(100)
                .map(i -> Val.of("pip2-" + i));

        val results = new CopyOnWriteArrayList<Val>();
        stream.getStream().subscribe(results::add);

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS).until(() -> results.size() >= 5);

        stream.connectToPolicyInformationPoint(pip2);

        await().pollDelay(10, MILLISECONDS).atMost(200, MILLISECONDS).until(
                () -> results.stream().anyMatch(value -> !value.isError() && value.get().asText().startsWith("pip2")));

        val pip2Values = results.stream().filter(value -> !value.isError() && value.get().asText().startsWith("pip2"))
                .toList();

        assertThat(pip2Values).isNotEmpty();
    }
}
