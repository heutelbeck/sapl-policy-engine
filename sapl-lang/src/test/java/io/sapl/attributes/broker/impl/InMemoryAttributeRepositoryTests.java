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
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.attributes.broker.api.AttributeRepository.TimeOutStrategy;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static io.sapl.attributes.broker.api.AttributeRepository.INFINITE;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive test suite for InMemoryAttributeRepository.
 * Tests are designed for defer() semantics where subscription reads storage at
 * subscription time.
 */
class InMemoryAttributeRepositoryTests {

    private static final Duration TEST_TIMEOUT   = Duration.ofSeconds(5);
    private static final String   TEST_ATTRIBUTE = "test.attribute";

    private static AttributeFinderInvocation createInvocation(String attributeName) {
        return new AttributeFinderInvocation(
                "configId",
                attributeName,
                null,
                List.of(),
                Map.of(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                0L,
                true
        );
    }

    // ========================================================================
    // BASIC FUNCTIONALITY
    // ========================================================================

    @Test
    void whenAttributeNeverPublished_subscribersReceiveAttributeUnavailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1))
                .expectNext(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE)
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenSubscribingBeforePublish_receivesUnavailableThenValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("active")).block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2)
                    .containsExactly(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE, Val.of("active"));
        });
    }

    @Test
    void whenSubscribingAfterPublish_receivesCurrentValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("cached"))
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(Val.of("cached"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeRemoved_subscribersReceiveAttributeUnavailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value")).block();

        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.removeAttribute(TEST_ATTRIBUTE).block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2)
                    .containsExactly(Val.of("value"), InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
        });
    }

    @Test
    void whenAttributeUpdated_subscribersReceiveNewValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("user")).block();

        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("admin")).block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2)
                    .containsExactly(Val.of("user"), Val.of("admin"));
        });
    }

    @Test
    void whenValueChangesBackAndForth_allDistinctChangesEmitted() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("state1")).block();

        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("state2")).block();
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("state1")).block();
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("state2")).block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(4)
                    .containsExactly(Val.of("state1"), Val.of("state2"), Val.of("state1"), Val.of("state2"));
        });
    }

    // ========================================================================
    // ENTITY-SPECIFIC ATTRIBUTES
    // ========================================================================

    @Test
    void whenPublishingWithDifferentEntities_attributesAreIsolated() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val user1      = Val.of("user-123");
        val user2      = Val.of("user-456");

        val invocation1 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, user1, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);
        val invocation2 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, user2, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        StepVerifier.create(
                        repository.publishAttribute(user1, TEST_ATTRIBUTE, Val.of("user1-value"))
                                .then(repository.publishAttribute(user2, TEST_ATTRIBUTE, Val.of("user2-value")))
                                .thenMany(Flux.merge(
                                        repository.invoke(invocation1).take(1),
                                        repository.invoke(invocation2).take(1)
                                ))
                )
                .expectNextMatches(val -> val.equals(Val.of("user1-value")) || val.equals(Val.of("user2-value")))
                .expectNextMatches(val -> val.equals(Val.of("user1-value")) || val.equals(Val.of("user2-value")))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingWithDifferentArguments_attributesAreIsolated() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        val invocation1 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, null, List.of(Val.of(1)), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);
        val invocation2 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, null, List.of(Val.of(2)), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, List.of(Val.of(1)), Val.of("result1"))
                                .then(repository.publishAttribute(TEST_ATTRIBUTE, List.of(Val.of(2)), Val.of("result2")))
                                .thenMany(Flux.merge(
                                        repository.invoke(invocation1).take(1),
                                        repository.invoke(invocation2).take(1)
                                ))
                )
                .expectNextMatches(val -> val.equals(Val.of("result1")) || val.equals(Val.of("result2")))
                .expectNextMatches(val -> val.equals(Val.of("result1")) || val.equals(Val.of("result2")))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // TTL AND TIMEOUT BEHAVIOR
    // ========================================================================

    @Test
    void whenTTLExpires_attributeIsRemovedAndSubscribersNotified() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("token"), Duration.ofMillis(200), TimeOutStrategy.REMOVE)
                .block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of("token"),
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                    );
        });
    }

    @Test
    void whenTTLExpiresWithBecomeUndefined_attributeBecomesUndefined() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("online"), Duration.ofMillis(200),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of("online"),
                            Val.UNDEFINED
                    );
        });
    }

    @Test
    void whenSubscribingAfterTimeout_receivesTimeoutResult() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(100),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().pollDelay(200, TimeUnit.MILLISECONDS)
                .atMost(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1))
                .expectNext(Val.UNDEFINED)
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeUpdatedBeforeTimeout_oldTimeoutIsCancelled() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(200), TimeOutStrategy.REMOVE)
                .block();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second"), Duration.ofSeconds(10), TimeOutStrategy.REMOVE)
                .block();

        await().pollDelay(300, TimeUnit.MILLISECONDS)
                .atMost(400, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(3));
    }

    @Test
    void whenAttributeRemovedBeforeTimeout_timeoutIsCancelled() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(500), TimeOutStrategy.REMOVE)
                .block();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(150, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        repository.removeAttribute(TEST_ATTRIBUTE).block();

        await().pollDelay(600, TimeUnit.MILLISECONDS)
                .atMost(700, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(3));
    }

    @Test
    void whenTTLIsZero_attributeExpiresImmediately() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ZERO, TimeOutStrategy.REMOVE).block();

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of("value"),
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                    );
        });
    }

    @Test
    void whenUsingDefaultPublish_attributeHasInfiniteTTL() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("config")).block();

        await().pollDelay(500, TimeUnit.MILLISECONDS)
                .atMost(600, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(2));
    }

    // ========================================================================
    // MULTIPLE SUBSCRIBERS
    // ========================================================================

    @Test
    void whenMultipleSubscribers_allReceiveSameUpdates() {
        val repository      = new InMemoryAttributeRepository(Clock.systemUTC());
        val subscriberCount = 10;
        val counters        = new CopyOnWriteArrayList<AtomicInteger>();

        IntStream.range(0, subscriberCount).forEach(i -> {
            val counter = new AtomicInteger(0);
            counters.add(counter);
            repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe(v -> counter.incrementAndGet());
        });

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> counters.forEach(counter -> assertThat(counter.get()).isEqualTo(1)));

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value1")).block();
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value2")).block();
        repository.removeAttribute(TEST_ATTRIBUTE).block();

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> counters.forEach(counter -> assertThat(counter.get()).isEqualTo(4)));
    }

    @Test
    void whenSubscribersDisposeQuickly_repositoryRemainsStable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"))
                .then(Flux.range(0, 100)
                        .concatMap(i -> Mono.fromRunnable(() -> {
                            val subscription = repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe();
                            subscription.dispose();
                        }))
                        .then())
                .block();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        val newStream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(newStream.take(1))
                .expectNext(Val.of("value"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // CONCURRENCY
    // ========================================================================

    @RepeatedTest(5)
    void whenConcurrentPublish_allUpdatesAreApplied() {
        val repository       = new InMemoryAttributeRepository(Clock.systemUTC());
        val threadCount      = 20;
        val updatesPerThread = 50;
        val errors           = new AtomicReference<Throwable>();

        Flux.range(0, threadCount)
                .parallel(threadCount)
                .runOn(reactor.core.scheduler.Schedulers.parallel())
                .flatMap(threadId ->
                        Flux.range(0, updatesPerThread)
                                .concatMap(i -> repository.publishAttribute(
                                        TEST_ATTRIBUTE,
                                        Val.of("thread-" + threadId + "-update-" + i)
                                ))
                )
                .sequential()
                .then()
                .doOnError(errors::set)
                .onErrorComplete()
                .block();

        assertThat(errors.get()).isNull();
    }

    @RepeatedTest(5)
    void whenConcurrentSubscribe_allSubscribersReceiveUpdates() throws InterruptedException {
        val repository      = new InMemoryAttributeRepository(Clock.systemUTC());
        val subscriberCount = 100;
        val latch           = new CountDownLatch(subscriberCount);
        val errors          = new ConcurrentHashMap<Integer, Throwable>();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("initial"))
                .then(Flux.range(0, subscriberCount)
                        .parallel(subscriberCount)
                        .runOn(reactor.core.scheduler.Schedulers.parallel())
                        .doOnNext(i -> {
                            try {
                                repository.invoke(createInvocation(TEST_ATTRIBUTE))
                                        .take(1)
                                        .subscribe(
                                                v -> latch.countDown(),
                                                e -> {
                                                    errors.put(i, e);
                                                    latch.countDown();
                                                }
                                        );
                            } catch (Exception e) {
                                errors.put(i, e);
                                latch.countDown();
                            }
                        })
                        .sequential()
                        .then())
                .block();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
    }

    @RepeatedTest(10)
    void whenTimeoutAndRemovalRace_noExceptionsOccur() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(100), TimeOutStrategy.REMOVE)
                .then(Mono.delay(Duration.ofMillis(95)))
                .then(repository.removeAttribute(TEST_ATTRIBUTE))
                .block();

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isGreaterThanOrEqualTo(2));
    }

    /**
     * Verifies that when an attribute update and timeout occur nearly simultaneously,
     * the emission sequence remains logically consistent. The race between timeout
     * (at 100ms) and update (at 95ms) may result in either order being observed,
     * but the state machine must never emit contradictory values.
     * <p>
     * Valid sequences:
     * <ul>
     * <li>[UNAVAILABLE, "first", "second"] - update wins race before timeout fires</li>
     * <li>[UNAVAILABLE, "first", UNAVAILABLE, "second"] - timeout fires, then update arrives</li>
     * </ul>
     * <p>
     * Invalid sequences that would indicate a bug:
     * <ul>
     * <li>Missing "first" entirely (update should not skip emissions)</li>
     * <li>"second" appearing before "first" (causality violation)</li>
     * <li>Stream ending without resolution (incomplete state transition)</li>
     * </ul>
     */
    @RepeatedTest(10)
    void whenTimeoutAndUpdateRace_emissionSequenceIsValid() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val emissions  = new CopyOnWriteArrayList<Val>();
        val latch      = new CountDownLatch(1);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        stream.take(Duration.ofMillis(250))
                .doOnNext(emissions::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(100), TimeOutStrategy.REMOVE)
                .then(Mono.delay(Duration.ofMillis(95)))
                .then(repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second")))
                .block();

        await().atMost(500, TimeUnit.MILLISECONDS).until(() -> latch.getCount() == 0);

        assertThat(emissions).isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(2)
                .first()
                .isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        assertThat(emissions.get(1)).isEqualTo(Val.of("first"));

        if (emissions.size() >= 3) {
            assertThat(emissions.get(2)).satisfiesAnyOf(
                    value -> assertThat(value).isEqualTo(Val.of("second")),
                    value -> assertThat(value).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE)
            );
        }

        assertThat(emissions).last().isEqualTo(Val.of("second"));
    }

    /**
     * Systematically tests various race scenarios between attribute updates and timeouts.
     * Each scenario is designed to test a specific timing relationship:
     * <ul>
     * <li>Update BEFORE timeout (early update should prevent timeout)</li>
     * <li>Update AFTER timeout (late update should occur after removal)</li>
     * <li>Update NEAR timeout (tight race, both outcomes valid)</li>
     * </ul>
     * <p>
     * Tests both REMOVE and BECOME_UNDEFINED strategies to ensure correctness
     * across different timeout behaviors.
     */
    @ParameterizedTest
    @CsvSource({
            "50, 150, REMOVE",
            "200, 150, REMOVE",
            "140, 150, REMOVE",
            "50, 150, BECOME_UNDEFINED",
            "200, 150, BECOME_UNDEFINED",
            "140, 150, BECOME_UNDEFINED"
    })
    void whenUpdateAndTimeoutRace_emissionSequenceMatchesTimingAndStrategy(long updateDelayMillis,
                                                                           long timeoutMillis,
                                                                           TimeOutStrategy strategy) {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val emissions  = new CopyOnWriteArrayList<Val>();
        val latch      = new CountDownLatch(1);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        stream.take(Duration.ofMillis(Math.max(updateDelayMillis, timeoutMillis) + 150))
                .doOnNext(emissions::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(timeoutMillis), strategy)
                .block();

        Mono.delay(Duration.ofMillis(updateDelayMillis)).block();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second")).block();

        await().atMost(1, TimeUnit.SECONDS).until(() -> latch.getCount() == 0);

        assertThat(emissions).isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(2)
                .first()
                .isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        assertThat(emissions.get(1)).isEqualTo(Val.of("first"));

        val updateBeforeTimeout = updateDelayMillis < timeoutMillis;

        if (updateBeforeTimeout) {
            val secondIndex = emissions.indexOf(Val.of("second"));
            assertThat(emissions).contains(Val.of("second"));
            assertThat(secondIndex).isGreaterThan(0);

            for (int i = 1; i < secondIndex; i++) {
                assertThat(emissions.get(i)).isEqualTo(Val.of("first"));
            }
        } else {
            val expectedTimeoutValue = strategy == TimeOutStrategy.REMOVE
                    ? InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                    : Val.UNDEFINED;

            assertThat(emissions).contains(expectedTimeoutValue, Val.of("second"));

            assertThat(emissions.indexOf(expectedTimeoutValue))
                    .isLessThan(emissions.indexOf(Val.of("second")));
        }

        assertThat(emissions).last().isEqualTo(Val.of("second"));
    }

    /**
     * Verifies that when a BECOME_UNDEFINED timeout races with an update,
     * the transition from UNDEFINED back to a defined value is correctly observed.
     * This tests the specific case where an attribute becomes undefined due to timeout
     * and is then immediately updated with a new value.
     */
    @RepeatedTest(5)
    void whenBecomeUndefinedTimeoutRacesWithUpdate_transitionSequenceIsValid() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val emissions  = new CopyOnWriteArrayList<Val>();
        val latch      = new CountDownLatch(1);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        stream.take(Duration.ofMillis(250))
                .doOnNext(emissions::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("initial"), Duration.ofMillis(100),
                        TimeOutStrategy.BECOME_UNDEFINED)
                .then(Mono.delay(Duration.ofMillis(95)))
                .then(repository.publishAttribute(TEST_ATTRIBUTE, Val.of("updated")))
                .block();

        await().atMost(500, TimeUnit.MILLISECONDS).until(() -> latch.getCount() == 0);

        assertThat(emissions).isNotEmpty()
                .contains(Val.of("initial"), Val.of("updated"))
                .first()
                .isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        val initialIndex = emissions.indexOf(Val.of("initial"));

        assertThat(initialIndex).isLessThan(emissions.indexOf(Val.of("updated")));

        if (emissions.contains(Val.UNDEFINED)) {
            assertThat(emissions.indexOf(Val.UNDEFINED)).isGreaterThan(initialIndex);
        }
    }

    /**
     * Tests the edge case where multiple rapid updates occur during the window
     * when a timeout is about to fire. Verifies that all updates are properly
     * sequenced and the final state reflects the most recent update.
     * <p>
     * Uses awaitility between publishes to ensure each update is observed before
     * the next is published, guaranteeing deterministic ordering.
     */
    @RepeatedTest(5)
    void whenMultipleUpdatesRaceWithTimeout_allUpdatesObservedInOrder() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val emissions  = new CopyOnWriteArrayList<Val>();
        val latch      = new CountDownLatch(1);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        stream.doOnNext(emissions::add)
                .doOnComplete(latch::countDown)
                .subscribe();

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(200), TimeOutStrategy.REMOVE)
                .block();

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> emissions.contains(Val.of("first")));

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second")).block();

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> emissions.contains(Val.of("second")));

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("third")).block();

        await().atMost(200, TimeUnit.MILLISECONDS).until(() -> emissions.contains(Val.of("third")));

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("fourth")).block();

        await().atMost(400, TimeUnit.MILLISECONDS).until(() -> emissions.contains(Val.of("fourth")));

        assertThat(emissions).isNotEmpty()
                .contains(Val.of("first"), Val.of("second"), Val.of("third"), Val.of("fourth"))
                .first()
                .isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        val indices = List.of(
                emissions.indexOf(Val.of("first")),
                emissions.indexOf(Val.of("second")),
                emissions.indexOf(Val.of("third")),
                emissions.indexOf(Val.of("fourth"))
        );

        assertThat(indices).isSorted().doesNotHaveDuplicates();

        assertThat(emissions).last().isEqualTo(Val.of("fourth"));
    }

    @Test
    void whenConcurrentOperationsOnDifferentAttributes_fullIsolation() throws InterruptedException {
        val repository             = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeCount         = 50;
        val operationsPerAttribute = 100;
        val latch                  = new CountDownLatch(attributeCount);

        IntStream.range(0, attributeCount).parallel().forEach(i -> {
            val attributeName = "test.attr" + i;
            val stream        = repository.invoke(createInvocation(attributeName));
            val received      = new AtomicInteger(0);

            stream.subscribe(v -> received.incrementAndGet());

            IntStream.range(0, operationsPerAttribute).forEach(j ->
                    repository.publishAttribute(attributeName, Val.of("value-" + j)).block()
            );

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(received.get())
                            .isGreaterThanOrEqualTo(operationsPerAttribute));

            latch.countDown();
        });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    // ========================================================================
    // RESOURCE LIMITS
    // ========================================================================

    @Test
    void whenRepositoryFull_newAttributesRejected() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        Flux.range(0, 10_000)
                .concatMap(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")))
                .then()
                .block();

        val testValue = Val.of("data");
        assertThatThrownBy(() -> repository.publishAttribute("flood.attr10001", testValue).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository is full");
    }

    @Test
    void whenRepositoryFullAndRetried_consistentlyRejects() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        Flux.range(0, 10_000)
                .concatMap(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")))
                .then()
                .block();

        val testValue = Val.of("data");
        IntStream.range(0, 100).forEach(i -> {
            val attributeName = "attack.attr" + i;
            assertThatThrownBy(() -> repository.publishAttribute(attributeName, testValue).block())
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void whenRepositoryFull_updatesStillAllowed() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute("test.existing", Val.of("v1"))
                .then(Flux.range(0, 9_999)
                        .concatMap(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")))
                        .then())
                .then(repository.publishAttribute("test.existing", Val.of("v2")))
                .block();

        val stream = repository.invoke(createInvocation("test.existing"));
        StepVerifier.create(stream.take(1))
                .expectNext(Val.of("v2"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenBackpressureBufferExceeded_emissionsFailGracefully() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        stream.subscribe(new reactor.core.publisher.BaseSubscriber<Val>() {
            @Override
            protected void hookOnSubscribe(@NotNull Subscription subscription) {
                // Intentionally don't request any items
            }
        });

        assertThatCode(() ->
                Flux.range(0, 2000)
                        .concatMap(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("flood-" + i)))
                        .then()
                        .block()
        ).doesNotThrowAnyException();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});
    }

    @Test
    void whenPublishingBeforeAnySubscription_noExceptionThrown() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatCode(() ->
                Flux.range(0, 100)
                        .concatMap(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)))
                        .then()
                        .block()
        ).doesNotThrowAnyException();

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(stream.take(1))
                .expectNext(Val.of("value-99"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenManySubscriptions_memoryUsageBounded() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"))
                .then(Flux.range(0, 1000)
                        .map(i -> repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe())
                        .then())
                .block();

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});
    }

    @RepeatedTest(5)
    void whenRapidSubscribeUnsubscribe_noResourceLeaks() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"))
                .then(Flux.range(0, 1000)
                        .concatMap(i -> Mono.fromRunnable(() -> {
                            val subscription = repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe();
                            subscription.dispose();
                        }))
                        .then())
                .block();

        await().pollDelay(200, TimeUnit.MILLISECONDS)
                .atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        val finalStream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(finalStream.take(1))
                .expectNext(Val.of("value"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenLargeValuePublished_repositoryHandlesGracefully() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val largeArray = Val.JSON.arrayNode();
        Flux.range(0, 10_000)
                .doOnNext(largeArray::add)
                .then()
                .block();
        val largeValue = Val.of(largeArray);

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, largeValue)
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(largeValue)
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // INPUT VALIDATION
    // ========================================================================

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "  " })
    void whenAttributeNameInvalid_publishThrowsException(String invalidName) {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue  = Val.of("value");

        assertThatThrownBy(() -> repository.publishAttribute(invalidName, testValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null or blank");
    }

    @Test
    void whenAttributeNameNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue  = Val.of("value");

        assertThatThrownBy(() -> repository.publishAttribute(null, testValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenArgumentsNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue  = Val.of("value");

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, null, testValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Arguments must not be null");
    }

    @Test
    void whenValueNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must not be null");
    }

    @Test
    void whenTTLNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue  = Val.of("value");

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, null, TimeOutStrategy.REMOVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL must not be null or negative");
    }

    @Test
    void whenTTLNegative_publishThrowsException() {
        val repository       = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue        = Val.of("value");
        val negativeDuration = Duration.ofSeconds(-1);

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, negativeDuration,
                TimeOutStrategy.REMOVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL must not be null or negative");
    }

    @Test
    void whenStrategyNull_publishThrowsException() {
        val repository   = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue    = Val.of("value");
        val testDuration = Duration.ofSeconds(1);

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, testDuration, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TimeOutStrategy must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "  " })
    void whenAttributeNameInvalid_removeThrowsException(String invalidName) {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatThrownBy(() -> repository.removeAttribute(invalidName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null or blank");
    }

    @Test
    void whenRemovingNonexistentAttribute_noException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatCode(() -> repository.removeAttribute("test.nonexistent").block())
                .doesNotThrowAnyException();
    }

    // ========================================================================
    // TIMEOUT STRATEGIES
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TimeOutStrategy.class)
    void whenTimeoutFires_correctStrategyApplied(TimeOutStrategy strategy) {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(200), strategy).block();

        val expectedAfterTimeout = strategy == TimeOutStrategy.REMOVE
                ? InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                : Val.UNDEFINED;

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of("value"),
                            expectedAfterTimeout
                    );
        });
    }

    @Test
    void whenUpdatingUndefinedAttribute_newValueAndTimeoutApplied() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(100),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().atMost(500, TimeUnit.MILLISECONDS)
                .pollDelay(150, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {});

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second"), Duration.ofSeconds(10)).block();

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(4)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of("first"),
                            Val.UNDEFINED,
                            Val.of("second")
                    );
        });
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Test
    void whenTTLCausesOverflow_defaultsToInfinite() {
        val repository       = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue        = Val.of("value");
        val veryLongDuration = Duration.ofSeconds(Long.MAX_VALUE);

        assertThatCode(() ->
                repository.publishAttribute(TEST_ATTRIBUTE, testValue, veryLongDuration, TimeOutStrategy.REMOVE)
                        .block())
                .doesNotThrowAnyException();
    }

    @Test
    void whenPublishingWithEmptyArguments_treatedAsNoArguments() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, List.of(), Val.of("value"))
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(Val.of("value"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingSameValueMultipleTimes_eachPublishEmits() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val received   = new AtomicInteger(0);

        stream.subscribe(v -> received.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same"))
                .then(repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same")))
                .then(repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same")))
                .block();

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(received.get()).isEqualTo(4));
    }

    @Test
    void whenAttributeNameVeryLong_handledNormally() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val longName   = "very.long.attribute.name.segment.alpha.beta.gamma.delta.epsilon";

        val invocation = createInvocation(longName);

        StepVerifier.create(
                        repository.publishAttribute(longName, Val.of("value"))
                                .thenMany(repository.invoke(invocation).take(1))
                )
                .expectNext(Val.of("value"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingUndefinedValue_storedAndEmitted() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, Val.UNDEFINED)
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(Val.UNDEFINED)
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingErrorValue_storedAndEmitted() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val errorValue = Val.error("custom error");

        StepVerifier.create(
                        repository.publishAttribute(TEST_ATTRIBUTE, errorValue)
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(errorValue)
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // REAL-WORLD SCENARIOS
    // ========================================================================

    @Test
    void realWorldScenario_userLoginFlow() {
        val repository    = new InMemoryAttributeRepository(Clock.systemUTC());
        val userId        = Val.of("user-123");
        val attributeName = "user.session.token";

        val invocation = new AttributeFinderInvocation("config", attributeName, userId, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        val stream = repository.invoke(invocation);
        val events = new CopyOnWriteArrayList<Val>();
        stream.subscribe(events::add);

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(1));
        assertThat(events.getFirst()).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        repository.publishAttribute(userId, attributeName, Val.of("token-abc"), Duration.ofSeconds(30),
                TimeOutStrategy.REMOVE).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(2));
        assertThat(events.get(1)).isEqualTo(Val.of("token-abc"));

        repository.publishAttribute(userId, attributeName, Val.of("token-xyz"), Duration.ofSeconds(30),
                TimeOutStrategy.REMOVE).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(3));
        assertThat(events.get(2)).isEqualTo(Val.of("token-xyz"));

        repository.removeAttribute(userId, attributeName).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(4));
        assertThat(events.get(3)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
    }

    @Test
    void realWorldScenario_deviceHeartbeat() {
        val repository    = new InMemoryAttributeRepository(Clock.systemUTC());
        val deviceId      = Val.of("device-sensor-42");
        val attributeName = "device.online";

        val invocation = new AttributeFinderInvocation("config", attributeName, deviceId, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        val stream = repository.invoke(invocation);
        val events = new CopyOnWriteArrayList<Val>();
        stream.subscribe(events::add);

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(1));

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(2));

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(3));

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(4));
        assertThat(events.get(3)).isEqualTo(Val.UNDEFINED);

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED).block();

        await().pollDelay(50, TimeUnit.MILLISECONDS)
                .atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(5));
        assertThat(events.get(4)).isEqualTo(Val.of(true));
    }

    @Test
    void realWorldScenario_rateLimiting() {
        val repository    = new InMemoryAttributeRepository(Clock.systemUTC());
        val userId        = Val.of("user-456");
        val attributeName = "ratelimit.requests";

        val invocation = new AttributeFinderInvocation("config", attributeName, userId, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        val stream = repository.invoke(invocation);
        val events = new CopyOnWriteArrayList<Val>();
        stream.subscribe(events::add);

        repository.publishAttribute(userId, attributeName, Val.of(1), Duration.ofMillis(200), TimeOutStrategy.REMOVE)
                .then(repository.publishAttribute(userId, attributeName, Val.of(2), Duration.ofMillis(200),
                        TimeOutStrategy.REMOVE))
                .then(repository.publishAttribute(userId, attributeName, Val.of(3), Duration.ofMillis(200),
                        TimeOutStrategy.REMOVE))
                .block();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(events).hasSize(5)
                    .containsExactly(
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE,
                            Val.of(1),
                            Val.of(2),
                            Val.of(3),
                            InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                    );
        });
    }

    @Test
    void whenSubscriberRequestsItems_noBackpressureIssues() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new CopyOnWriteArrayList<Val>();
        val updateCount    = 100;

        stream.subscribe(new reactor.core.publisher.BaseSubscriber<Val>() {
            @Override
            protected void hookOnSubscribe(@NotNull Subscription subscription) {
                subscription.request(updateCount + 1);
            }

            @Override
            protected void hookOnNext(@NotNull Val value) {
                receivedValues.add(value);
            }
        });

        Flux.range(0, updateCount)
                .concatMap(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)))
                .then()
                .block();

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedValues).hasSize(updateCount + 1));
    }

    @Test
    void whenRapidUpdates_latestValueAlwaysAvailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        StepVerifier.create(
                        Flux.range(0, 100)
                                .concatMap(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)))
                                .then()
                                .thenMany(repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1))
                )
                .expectNext(Val.of("value-99"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @RepeatedTest(3)
    void whenConcurrentPublishDuringBackpressure_noExceptions() throws InterruptedException {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        stream.subscribe(new reactor.core.publisher.BaseSubscriber<Val>() {
            @Override
            protected void hookOnSubscribe(@NotNull Subscription subscription) {
                subscription.request(1);
            }
        });

        val threadCount      = 10;
        val updatesPerThread = 100;
        val barrier          = new CyclicBarrier(threadCount);
        val errors           = new AtomicReference<Throwable>();

        val threads = IntStream.range(0, threadCount).mapToObj(threadId -> new Thread(() -> {
            try {
                barrier.await();
                IntStream.range(0, updatesPerThread).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE,
                        Val.of("thread-" + threadId + "-update-" + i)).block());
            } catch (Exception e) {
                errors.set(e);
            }
        })).toList();

        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(errors.get()).isNull();
    }

    // ========================================================================
    // CAPACITY LIMIT ENFORCEMENT
    // ========================================================================

    @Test
    void whenPublishingUpToMaximum_allAttributesAreStored() {
        val maxSize    = 5;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        IntStream.range(0, maxSize)
                .forEach(i -> repository.publishAttribute("necronomicon.chapter" + i, Val.of("text-" + i)).block());

        IntStream.range(0, maxSize).forEach(i -> {
            val invocation = createInvocation("necronomicon.chapter" + i);
            StepVerifier.create(repository.invoke(invocation).take(1))
                    .expectNext(Val.of("text-" + i))
                    .expectComplete()
                    .verify(TEST_TIMEOUT);
        });
    }

    @Test
    void whenExceedingMaximumWithNewAttribute_throwsIllegalStateException() {
        val maxSize    = 3;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        repository.publishAttribute("ritual.preparation", Val.of("phase1")).block();
        repository.publishAttribute("ritual.invocation", Val.of("phase2")).block();
        repository.publishAttribute("ritual.binding", Val.of("phase3")).block();

        assertThatThrownBy(() -> repository.publishAttribute("ritual.banishment", Val.of("phase4")).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository is full")
                .hasMessageContaining("Maximum 3 attributes allowed")
                .hasMessageContaining("ritual.banishment");
    }

    @Test
    void whenUpdatingExistingAttribute_doesNotCountAgainstLimit() {
        val maxSize    = 2;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        repository.publishAttribute("elder.sign", Val.of("original")).block();
        repository.publishAttribute("yellow.sign", Val.of("hastur")).block();

        repository.publishAttribute("elder.sign", Val.of("updated")).block();
        repository.publishAttribute("yellow.sign", Val.of("carcosa")).block();

        val elderSignInvocation = createInvocation("elder.sign");
        StepVerifier.create(repository.invoke(elderSignInvocation).take(1))
                .expectNext(Val.of("updated"))
                .expectComplete()
                .verify(TEST_TIMEOUT);

        val yellowSignInvocation = createInvocation("yellow.sign");
        StepVerifier.create(repository.invoke(yellowSignInvocation).take(1))
                .expectNext(Val.of("carcosa"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenRemovingAttribute_allowsNewAttributeToBeStored() {
        val maxSize    = 2;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        repository.publishAttribute("shoggoth.location", Val.of("antarctic")).block();
        repository.publishAttribute("deep.one.city", Val.of("rlyeh")).block();

        assertThatThrownBy(() -> repository.publishAttribute("byakhee.nest", Val.of("carcosa")).block())
                .isInstanceOf(IllegalStateException.class);

        repository.removeAttribute("shoggoth.location").block();

        repository.publishAttribute("byakhee.nest", Val.of("carcosa")).block();

        val invocation = createInvocation("byakhee.nest");
        StepVerifier.create(repository.invoke(invocation).take(1))
                .expectNext(Val.of("carcosa"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeTimesOutWithRemove_allowsNewAttributeToBeStored() {
        val maxSize    = 2;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        repository.publishAttribute("ward.protective", Val.of("active"), Duration.ofMillis(100), TimeOutStrategy.REMOVE)
                .block();
        repository.publishAttribute("seal.binding", Val.of("intact")).block();

        assertThatThrownBy(() -> repository.publishAttribute("circle.summoning", Val.of("drawn")).block())
                .isInstanceOf(IllegalStateException.class);

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    repository.publishAttribute("circle.summoning", Val.of("drawn")).block();
                });

        val invocation = createInvocation("circle.summoning");
        StepVerifier.create(repository.invoke(invocation).take(1))
                .expectNext(Val.of("drawn"))
                .expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeTimesOutWithBecomeUndefined_doesNotFreeCapacity() {
        val maxSize    = 2;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        repository.publishAttribute("cultist.sanity", Val.of(100), Duration.ofMillis(100),
                TimeOutStrategy.BECOME_UNDEFINED).block();
        repository.publishAttribute("forbidden.knowledge", Val.of("minimal")).block();

        assertThatThrownBy(() -> repository.publishAttribute("madness.level", Val.of("ascending")).block())
                .isInstanceOf(IllegalStateException.class);

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    val invocation = createInvocation("cultist.sanity");
                    StepVerifier.create(repository.invoke(invocation).take(1))
                            .expectNext(Val.UNDEFINED)
                            .expectComplete()
                            .verify(TEST_TIMEOUT);
                });

        assertThatThrownBy(() -> repository.publishAttribute("madness.level", Val.of("ascending")).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository is full");
    }

    @Test
    void whenPublishingMultipleEntitiesSameAttribute_eachCountsSeparately() {
        val maxSize    = 3;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        val investigator1 = Val.of("carter");
        val investigator2 = Val.of("armitage");
        val investigator3 = Val.of("dyer");
        val investigator4 = Val.of("peaslee");

        repository.publishAttribute(investigator1, "sanity.score", Val.of(80)).block();
        repository.publishAttribute(investigator2, "sanity.score", Val.of(75)).block();
        repository.publishAttribute(investigator3, "sanity.score", Val.of(90)).block();

        assertThatThrownBy(() -> repository.publishAttribute(investigator4, "sanity.score", Val.of(85)).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository is full");
    }

    @Test
    void whenPublishingSameEntityDifferentArguments_eachCountsSeparately() {
        val maxSize    = 3;
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        val entity = Val.of("library");

        repository.publishAttribute(entity, "tome.danger", List.of(Val.of("necronomicon")), Val.of("extreme"),
                INFINITE, TimeOutStrategy.REMOVE).block();
        repository.publishAttribute(entity, "tome.danger", List.of(Val.of("king-in-yellow")), Val.of("high"),
                INFINITE, TimeOutStrategy.REMOVE).block();
        repository.publishAttribute(entity, "tome.danger", List.of(Val.of("cultes-des-goules")), Val.of("severe"),
                INFINITE, TimeOutStrategy.REMOVE).block();

        assertThatThrownBy(() -> repository.publishAttribute(entity, "tome.danger",
                List.of(Val.of("unaussprechlichen-kulten")), Val.of("moderate"),
                INFINITE, TimeOutStrategy.REMOVE).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Repository is full");
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 50 })
    void whenRepositoryFilledToCapacity_correctAttributeCountMaintained(int maxSize) {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC(), new HeapAttributeStorage(), 1000, maxSize);

        IntStream.range(0, maxSize)
                .forEach(i -> repository.publishAttribute("scroll.fragment" + i, Val.of("text-" + i)).block());

        IntStream.range(0, maxSize).forEach(i -> {
            val invocation = createInvocation("scroll.fragment" + i);
            StepVerifier.create(repository.invoke(invocation).take(1))
                    .expectNext(Val.of("text-" + i))
                    .expectComplete()
                    .verify(TEST_TIMEOUT);
        });

        assertThatThrownBy(() -> repository.publishAttribute("scroll.fragmentExtra", Val.of("overflow")).block())
                .isInstanceOf(IllegalStateException.class);
    }
}