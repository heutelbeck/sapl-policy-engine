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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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
        return new AttributeFinderInvocation("configId", attributeName, List.of(), Map.of(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);
    }

    // ========================================================================
    // BASIC FUNCTIONALITY
    // ========================================================================

    @Test
    void whenAttributeNeverPublished_subscribersReceiveAttributeUnavailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1)).expectNext(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE)
                .expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenSubscribingBeforePublish_receivesUnavailableThenValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("active"));

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("active"));
        });
    }

    @Test
    void whenSubscribingAfterPublish_receivesCurrentValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("cached"));

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1)).expectNext(Val.of("cached")).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeRemoved_subscribersReceiveAttributeUnavailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"));

        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.removeAttribute(TEST_ATTRIBUTE);

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2);
            assertThat(receivedValues.get(0)).isEqualTo(Val.of("value"));
            assertThat(receivedValues.get(1)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
        });
    }

    @Test
    void whenAttributeUpdated_subscribersReceiveNewValue() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("user"));

        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("admin"));

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(2);
            assertThat(receivedValues.get(0)).isEqualTo(Val.of("user"));
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("admin"));
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

        repository.publishAttribute(user1, TEST_ATTRIBUTE, Val.of("user1-value"));
        repository.publishAttribute(user2, TEST_ATTRIBUTE, Val.of("user2-value"));

        val invocation1 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, user1, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);
        val invocation2 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, user2, List.of(), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        StepVerifier.create(repository.invoke(invocation1).take(1)).expectNext(Val.of("user1-value")).expectComplete()
                .verify(TEST_TIMEOUT);

        StepVerifier.create(repository.invoke(invocation2).take(1)).expectNext(Val.of("user2-value")).expectComplete()
                .verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingWithDifferentArguments_attributesAreIsolated() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, List.of(Val.of(1)), Val.of("result1"));
        repository.publishAttribute(TEST_ATTRIBUTE, List.of(Val.of(2)), Val.of("result2"));

        val invocation1 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, null, List.of(Val.of(1)), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);
        val invocation2 = new AttributeFinderInvocation("config", TEST_ATTRIBUTE, null, List.of(Val.of(2)), Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, true);

        StepVerifier.create(repository.invoke(invocation1).take(1)).expectNext(Val.of("result1")).expectComplete()
                .verify(TEST_TIMEOUT);

        StepVerifier.create(repository.invoke(invocation2).take(1)).expectNext(Val.of("result2")).expectComplete()
                .verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // TTL AND TIMEOUT BEHAVIOR
    // ========================================================================

    @Test
    void whenTTLExpires_attributeIsRemovedAndSubscribersNotified() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("token"), Duration.ofMillis(200), TimeOutStrategy.REMOVE);

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("token"));
            assertThat(receivedValues.get(2)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
        });
    }

    @Test
    void whenTTLExpiresWithBecomeUndefined_attributeBecomesUndefined() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("online"), Duration.ofMillis(200),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("online"));
            assertThat(receivedValues.get(2)).isEqualTo(Val.UNDEFINED);
        });
    }

    @Test
    void whenSubscribingAfterTimeout_receivesTimeoutResult() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(100),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().pollDelay(200, TimeUnit.MILLISECONDS).atMost(300, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1)).expectNext(Val.UNDEFINED).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenAttributeUpdatedBeforeTimeout_oldTimeoutIsCancelled() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(200), TimeOutStrategy.REMOVE);

        await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(150, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second"), Duration.ofSeconds(10), TimeOutStrategy.REMOVE);

        await().pollDelay(300, TimeUnit.MILLISECONDS).atMost(400, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(3));
    }

    @Test
    void whenAttributeRemovedBeforeTimeout_timeoutIsCancelled() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(500), TimeOutStrategy.REMOVE);

        await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(150, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        repository.removeAttribute(TEST_ATTRIBUTE);

        await().pollDelay(600, TimeUnit.MILLISECONDS).atMost(700, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(3));
    }

    @Test
    void whenTTLIsZero_attributeExpiresImmediately() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ZERO, TimeOutStrategy.REMOVE);

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("value"));
            assertThat(receivedValues.get(2)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
        });
    }

    @Test
    void whenUsingDefaultPublish_attributeHasInfiniteTTL() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("config"));

        await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(600, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isEqualTo(2));
    }

    // ========================================================================
    // MULTIPLE SUBSCRIBERS
    // ========================================================================

    @Test
    void whenMultipleSubscribers_allReceiveSameUpdates() {
        val repository      = new InMemoryAttributeRepository(Clock.systemUTC());
        val subscriberCount = 10;
        val counters        = new ArrayList<AtomicInteger>();

        IntStream.range(0, subscriberCount).forEach(i -> {
            val counter = new AtomicInteger(0);
            counters.add(counter);
            repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe(v -> counter.incrementAndGet());
        });

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value1"));
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value2"));
        repository.removeAttribute(TEST_ATTRIBUTE);

        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> counters.forEach(counter -> assertThat(counter.get()).isEqualTo(4)));
    }

    @Test
    void whenSubscribersDisposeQuickly_repositoryRemainsStable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"));

        IntStream.range(0, 100).forEach(i -> {
            val subscription = repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe();
            subscription.dispose();
        });

        await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(300, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        val newStream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(newStream.take(1)).expectNext(Val.of("value")).expectComplete().verify(TEST_TIMEOUT);
    }

    // ========================================================================
    // CONCURRENCY
    // ========================================================================

    @RepeatedTest(5)
    void whenConcurrentPublish_allUpdatesAreApplied() throws InterruptedException {
        val repository       = new InMemoryAttributeRepository(Clock.systemUTC());
        val threadCount      = 20;
        val updatesPerThread = 50;
        val barrier          = new CyclicBarrier(threadCount);
        val errors           = new AtomicReference<Throwable>();

        val threads = IntStream.range(0, threadCount).mapToObj(threadId -> new Thread(() -> {
            try {
                barrier.await();
                IntStream.range(0, updatesPerThread).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE,
                        Val.of("thread-" + threadId + "-update-" + i)));
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

    @RepeatedTest(5)
    void whenConcurrentSubscribe_allSubscribersReceiveUpdates() throws InterruptedException {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("initial"));

        val subscriberCount = 100;
        val latch           = new CountDownLatch(subscriberCount);
        val errors          = new ConcurrentHashMap<Integer, Throwable>();

        IntStream.range(0, subscriberCount).parallel().forEach(i -> {
            try {
                repository.invoke(createInvocation(TEST_ATTRIBUTE)).take(1).subscribe(v -> latch.countDown(), e -> {
                    errors.put(i, e);
                    latch.countDown();
                });
            } catch (Exception e) {
                errors.put(i, e);
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
    }

    @RepeatedTest(10)
    void whenTimeoutAndRemovalRace_noExceptionsOccur() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new AtomicInteger(0);

        stream.subscribe(v -> receivedValues.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(100), TimeOutStrategy.REMOVE);

        Mono.delay(Duration.ofMillis(95)).subscribe(tick -> repository.removeAttribute(TEST_ATTRIBUTE));

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedValues.get()).isGreaterThanOrEqualTo(2));
    }

    @RepeatedTest(10)
    void whenTimeoutAndUpdateRace_stateRemainsConsistent() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(100), TimeOutStrategy.REMOVE);

        Mono.delay(Duration.ofMillis(95))
                .subscribe(tick -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second")));

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        StepVerifier.create(stream.take(1)).expectNextMatches(
                val -> val.equals(Val.of("second")) || val.equals(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE))
                .expectComplete().verify(TEST_TIMEOUT);
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

            IntStream.range(0, operationsPerAttribute).forEach(j -> {
                repository.publishAttribute(attributeName, Val.of("value-" + j));
            });

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(received.get()).isGreaterThanOrEqualTo(operationsPerAttribute));

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

        IntStream.range(0, 10_000).forEach(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")));

        val testValue = Val.of("data");
        assertThatThrownBy(() -> repository.publishAttribute("flood.attr10001", testValue))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Repository is full");
    }

    @Test
    void whenRepositoryFullAndRetried_consistentlyRejects() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        IntStream.range(0, 10_000).forEach(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")));

        val testValue = Val.of("data");
        IntStream.range(0, 100).forEach(i -> {
            val attributeName = "attack.attr" + i;
            assertThatThrownBy(() -> repository.publishAttribute(attributeName, testValue))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void whenRepositoryFull_updatesStillAllowed() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute("test.existing", Val.of("v1"));

        IntStream.range(0, 9_999).forEach(i -> repository.publishAttribute("flood.attr" + i, Val.of("data")));

        repository.publishAttribute("test.existing", Val.of("v2"));

        val stream = repository.invoke(createInvocation("test.existing"));
        StepVerifier.create(stream.take(1)).expectNext(Val.of("v2")).expectComplete().verify(TEST_TIMEOUT);
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

        assertThatCode(() -> {
            IntStream.range(0, 2000).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("flood-" + i)));
        }).doesNotThrowAnyException();

        await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {});
    }

    @Test
    void whenPublishingBeforeAnySubscription_noExceptionThrown() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatCode(() -> {
            IntStream.range(0, 100).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)));
        }).doesNotThrowAnyException();

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(stream.take(1)).expectNext(Val.of("value-99")).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenManySubscriptions_memoryUsageBounded() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"));

        val subscriptions = IntStream.range(0, 1000)
                .mapToObj(i -> repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe()).toList();

        await().pollDelay(100, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        subscriptions.forEach(reactor.core.Disposable::dispose);
    }

    @RepeatedTest(5)
    void whenRapidSubscribeUnsubscribe_noResourceLeaks() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"));

        IntStream.range(0, 1000).forEach(i -> {
            val subscription = repository.invoke(createInvocation(TEST_ATTRIBUTE)).subscribe();
            subscription.dispose();
        });

        await().pollDelay(200, TimeUnit.MILLISECONDS).atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        val finalStream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(finalStream.take(1)).expectNext(Val.of("value")).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenLargeValuePublished_repositoryHandlesGracefully() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val largeArray = Val.JSON.arrayNode();
        for (int i = 0; i < 10_000; i++) {
            largeArray.add(i);
        }
        val largeValue = Val.of(largeArray);

        repository.publishAttribute(TEST_ATTRIBUTE, largeValue);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(stream.take(1)).expectNext(largeValue).expectComplete().verify(TEST_TIMEOUT);
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
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Arguments must not be null");
    }

    @Test
    void whenValueNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Value must not be null");
    }

    @Test
    void whenTTLNull_publishThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue  = Val.of("value");

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, null, TimeOutStrategy.REMOVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TTL must not be null or negative");
    }

    @Test
    void whenTTLNegative_publishThrowsException() {
        val repository       = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue        = Val.of("value");
        val negativeDuration = Duration.ofSeconds(-1);

        assertThatThrownBy(
                () -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, negativeDuration, TimeOutStrategy.REMOVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TTL must not be null or negative");
    }

    @Test
    void whenStrategyNull_publishThrowsException() {
        val repository   = new InMemoryAttributeRepository(Clock.systemUTC());
        val testValue    = Val.of("value");
        val testDuration = Duration.ofSeconds(1);

        assertThatThrownBy(() -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, testDuration, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TimeOutStrategy must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "  " })
    void whenAttributeNameInvalid_removeThrowsException(String invalidName) {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatThrownBy(() -> repository.removeAttribute(invalidName)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null or blank");
    }

    @Test
    void whenRemovingNonexistentAttribute_noException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        assertThatCode(() -> repository.removeAttribute("test.nonexistent")).doesNotThrowAnyException();
    }

    // ========================================================================
    // TIMEOUT STRATEGIES
    // ========================================================================

    @ParameterizedTest
    @EnumSource(TimeOutStrategy.class)
    void whenTimeoutFires_correctStrategyApplied(TimeOutStrategy strategy) {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value"), Duration.ofMillis(200), strategy);

        val expectedAfterTimeout = strategy == TimeOutStrategy.REMOVE
                ? InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE
                : Val.UNDEFINED;

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(3);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("value"));
            assertThat(receivedValues.get(2)).isEqualTo(expectedAfterTimeout);
        });
    }

    @Test
    void whenUpdatingUndefinedAttribute_newValueAndTimeoutApplied() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
        stream.subscribe(receivedValues::add);

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("first"), Duration.ofMillis(100),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().atMost(500, TimeUnit.MILLISECONDS).pollDelay(150, TimeUnit.MILLISECONDS).untilAsserted(() -> {});

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("second"), Duration.ofSeconds(10));

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            assertThat(receivedValues).hasSize(4);
            assertThat(receivedValues.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(receivedValues.get(1)).isEqualTo(Val.of("first"));
            assertThat(receivedValues.get(2)).isEqualTo(Val.UNDEFINED);
            assertThat(receivedValues.get(3)).isEqualTo(Val.of("second"));
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

        assertThatCode(
                () -> repository.publishAttribute(TEST_ATTRIBUTE, testValue, veryLongDuration, TimeOutStrategy.REMOVE))
                .doesNotThrowAnyException();
    }

    @Test
    void whenPublishingWithEmptyArguments_treatedAsNoArguments() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, List.of(), Val.of("value"));

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(stream.take(1)).expectNext(Val.of("value")).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingSameValueMultipleTimes_eachPublishEmits() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream     = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val received   = new AtomicInteger(0);

        stream.subscribe(v -> received.incrementAndGet());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same"));
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same"));
        repository.publishAttribute(TEST_ATTRIBUTE, Val.of("same"));

        await().atMost(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertThat(received.get()).isEqualTo(4));
    }

    @Test
    void whenAttributeNameVeryLong_handledNormally() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val longName   = "very.long.attribute.name.segment.alpha.beta.gamma.delta.epsilon";

        repository.publishAttribute(longName, Val.of("value"));

        val invocation = createInvocation(longName);
        val stream     = repository.invoke(invocation);

        StepVerifier.create(stream.take(1)).expectNext(Val.of("value")).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingUndefinedValue_storedAndEmitted() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        repository.publishAttribute(TEST_ATTRIBUTE, Val.UNDEFINED);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1)).expectNext(Val.UNDEFINED).expectComplete().verify(TEST_TIMEOUT);
    }

    @Test
    void whenPublishingErrorValue_storedAndEmitted() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val errorValue = Val.error("custom error");

        repository.publishAttribute(TEST_ATTRIBUTE, errorValue);

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));

        StepVerifier.create(stream.take(1)).expectNext(errorValue).expectComplete().verify(TEST_TIMEOUT);
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
        val events = new ArrayList<Val>();
        stream.subscribe(events::add);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(1));
        assertThat(events.getFirst()).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);

        repository.publishAttribute(userId, attributeName, Val.of("token-abc"), Duration.ofSeconds(30),
                TimeOutStrategy.REMOVE);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(2));
        assertThat(events.get(1)).isEqualTo(Val.of("token-abc"));

        repository.publishAttribute(userId, attributeName, Val.of("token-xyz"), Duration.ofSeconds(30),
                TimeOutStrategy.REMOVE);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(3));
        assertThat(events.get(2)).isEqualTo(Val.of("token-xyz"));

        repository.removeAttribute(userId, attributeName);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
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
        val events = new ArrayList<Val>();
        stream.subscribe(events::add);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(1));

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(2));

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(events).hasSize(3));

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).hasSize(4));
        assertThat(events.get(3)).isEqualTo(Val.UNDEFINED);

        repository.publishAttribute(deviceId, attributeName, Val.of(true), Duration.ofMillis(300),
                TimeOutStrategy.BECOME_UNDEFINED);

        await().pollDelay(50, TimeUnit.MILLISECONDS).atMost(200, TimeUnit.MILLISECONDS)
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
        val events = new ArrayList<Val>();
        stream.subscribe(events::add);

        repository.publishAttribute(userId, attributeName, Val.of(1), Duration.ofMillis(200), TimeOutStrategy.REMOVE);
        repository.publishAttribute(userId, attributeName, Val.of(2), Duration.ofMillis(200), TimeOutStrategy.REMOVE);
        repository.publishAttribute(userId, attributeName, Val.of(3), Duration.ofMillis(200), TimeOutStrategy.REMOVE);

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(events).hasSize(5);
            assertThat(events.get(0)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
            assertThat(events.get(1)).isEqualTo(Val.of(1));
            assertThat(events.get(2)).isEqualTo(Val.of(2));
            assertThat(events.get(3)).isEqualTo(Val.of(3));
            assertThat(events.get(4)).isEqualTo(InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE);
        });
    }

    @Test
    void whenSubscriberRequestsItems_noBackpressureIssues() {
        val repository     = new InMemoryAttributeRepository(Clock.systemUTC());
        val stream         = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        val receivedValues = new ArrayList<Val>();
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

        IntStream.range(0, updateCount).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)));

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(receivedValues).hasSize(updateCount + 1));
    }

    @Test
    void whenRapidUpdates_latestValueAlwaysAvailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());

        IntStream.range(0, 100).forEach(i -> repository.publishAttribute(TEST_ATTRIBUTE, Val.of("value-" + i)));

        val stream = repository.invoke(createInvocation(TEST_ATTRIBUTE));
        StepVerifier.create(stream.take(1)).expectNext(Val.of("value-99")).expectComplete().verify(TEST_TIMEOUT);
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
                        Val.of("thread-" + threadId + "-update-" + i)));
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
}
