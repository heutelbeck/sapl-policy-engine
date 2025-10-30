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
import io.sapl.attributes.broker.api.AttributeKey;
import io.sapl.attributes.broker.api.AttributeRepository.TimeOutStrategy;
import io.sapl.attributes.broker.api.AttributeStorage;
import io.sapl.attributes.broker.api.PersistedAttribute;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.sapl.attributes.broker.impl.InMemoryAttributeRepository.ATTRIBUTE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for InMemoryAttributeRepository using scenarios from the
 * world of Elric of Melniboné.
 * <p>
 * Test scenarios are based on the authorization requirements of the Dreaming
 * City and the balance between Law and Chaos.
 */
@Slf4j
@Timeout(30)
class InMemoryAttributeRepositoryTests {

    private static final String ELRIC                    = "elric-of-melnibone";
    private static final String ARIOCH                   = "arioch-duke-of-hell";
    private static final String STORMBRINGER             = "stormbringer";
    private static final String CYMORIL                  = "cymoril-of-imrryr";
    private static final String YYRKOON                  = "yyrkoon-the-traitor";
    private static final String IMRRYR                   = "imrryr-the-dreaming-city";
    private static final String CHAOS_REALM              = "realm-of-chaos";
    private static final String LAW_REALM                = "realm-of-law";
    private static final String PDP_CONFIGURATION        = "melnibone-pdp";
    private static final String STORMBRINGER_SOULS       = "stormbringer.souls";
    private static final String CHAOS_PACT               = "chaos.pact";
    private static final String DRAGON_BOND              = "dragon.bond";
    private static final String ELRIC_STRENGTH           = "elric.strength";
    private static final String THRONE_ACCESS            = "throne.access";
    private static final String SORCERY_LEVEL            = "sorcery.level";
    private static final String DREAMING_CITY_PROTECTION = "imrryr.shield";
    private static final String BALANCE_KEEPER           = "balance.keeper";

    private Clock                       clock;
    private InMemoryAttributeRepository repository;
    private AttributeStorage            storage;
    private ControlledClock             controlledClock;

    @BeforeEach
    void setUp() {
        controlledClock = new ControlledClock();
        clock           = controlledClock.getClock();
        storage         = new HeapAttributeStorage();
        repository      = new InMemoryAttributeRepository(clock, storage);
    }

    @Test
    void when_publishingStormbringerSoulCount_then_subscriberReceivesCurrentValue() {
        // Stormbringer starts with 42 souls already consumed
        val souls = Val.of(42);

        repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();

        val invocation = createInvocation(Val.of(STORMBRINGER), STORMBRINGER_SOULS);

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(souls).verifyComplete();
    }

    @Test
    void when_publishingChaosPactWithTTL_then_pactExpiresAndBecomesUnavailable() {
        // Arioch grants a pact for 100ms, then withdraws it
        val pactActive = Val.of(true);
        val pactTTL    = Duration.ofMillis(100);

        repository.publishAttribute(Val.of(ARIOCH), CHAOS_PACT, pactActive, pactTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        val invocation = createInvocation(Val.of(ARIOCH), CHAOS_PACT);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(pactActive)
                .expectNext(ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @Test
    void when_publishingElricStrengthWithBecomeUndefined_then_strengthBecomesUndefinedAfterDrugWearOff() {
        // Elric takes drugs for strength, but they wear off leaving him undefined
        val strengthened = Val.of(100);
        val drugDuration = Duration.ofMillis(100);

        repository.publishAttribute(Val.of(ELRIC), ELRIC_STRENGTH, strengthened, drugDuration,
                TimeOutStrategy.BECOME_UNDEFINED).subscribe();

        val invocation = createInvocation(Val.of(ELRIC), ELRIC_STRENGTH);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(strengthened).expectNext(Val.UNDEFINED)
                .verifyComplete();
    }

    @Test
    void when_removingDragonBond_then_activeSubscribersReceiveUnavailable() {
        // Dragon rider bond is severed
        val bondStrength = Val.of(95);

        repository.publishAttribute(Val.of(ELRIC), DRAGON_BOND, bondStrength).subscribe();

        val invocation = createInvocation(Val.of(ELRIC), DRAGON_BOND);
        val flux       = repository.invoke(invocation);

        StepVerifier.create(flux.take(2)).expectNext(bondStrength).then(() -> {
            repository.removeAttribute(Val.of(ELRIC), DRAGON_BOND).subscribe();
        }).expectNext(ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @Test
    void when_multipleSubscribersForSameAttribute_then_allReceiveUpdates() {
        // Multiple sorcerers monitor the Ruby Throne access
        val throneOccupied = Val.of(true);

        repository.publishAttribute(THRONE_ACCESS, throneOccupied).subscribe();

        val invocation = createInvocation(null, THRONE_ACCESS);

        val subscriber1 = repository.invoke(invocation).take(2);
        val subscriber2 = repository.invoke(invocation).take(2);
        val subscriber3 = repository.invoke(invocation).take(2);

        // All subscribers get initial value
        StepVerifier.create(Flux.merge(subscriber1, subscriber2, subscriber3).take(3)).expectNextCount(3)
                .verifyComplete();

        // Update and verify all receive new value
        val throneVacated = Val.of(false);
        repository.publishAttribute(THRONE_ACCESS, throneVacated).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            val verification = repository.invoke(invocation).take(1);
            StepVerifier.create(verification).expectNext(throneVacated).verifyComplete();
        });
    }

    @Test
    void when_publishingWithInfiniteTTL_then_attributeNeverExpires() {
        // The balance between Law and Chaos is eternal
        val balanceActive = Val.of(true);

        repository.publishAttribute(BALANCE_KEEPER, balanceActive, Duration.ofSeconds(Long.MAX_VALUE),
                TimeOutStrategy.REMOVE).subscribe();

        val invocation = createInvocation(null, BALANCE_KEEPER);

        // Advance time significantly - attribute should persist
        controlledClock.advanceBy(Duration.ofDays(365 * 100));

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(balanceActive).verifyComplete();
    }

    @Test
    void when_queryingNonExistentAttribute_then_receivesUnavailable() {
        // Querying a non-existent sorcerous ability
        val invocation = createInvocation(Val.of(CYMORIL), "sorcery.unknown");

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @Test
    void when_publishingAttributeWithArguments_then_attributeIsRetrievableWithSameArguments() {
        // Summoning power varies by realm and ritual complexity
        val summoningPower = Val.of(85);
        val arguments      = List.of(Val.of(CHAOS_REALM), Val.of(5)); // realm, ritual complexity

        repository.publishAttribute("summoning.power", arguments, summoningPower).subscribe();

        val invocation = createInvocation(null, "summoning.power", arguments);

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(summoningPower).verifyComplete();
    }

    @Test
    void when_publishingAttributeWithDifferentArguments_then_attributesAreIndependent() {
        // Summoning power differs between Chaos and Law realms
        val chaosPower = Val.of(95);
        val lawPower   = Val.of(45);

        repository.publishAttribute("summoning.power", List.of(Val.of(CHAOS_REALM)), chaosPower).subscribe();
        repository.publishAttribute("summoning.power", List.of(Val.of(LAW_REALM)), lawPower).subscribe();

        val chaosInvocation = createInvocation(null, "summoning.power", List.of(Val.of(CHAOS_REALM)));
        val lawInvocation   = createInvocation(null, "summoning.power", List.of(Val.of(LAW_REALM)));

        StepVerifier.create(repository.invoke(chaosInvocation).take(1)).expectNext(chaosPower).verifyComplete();
        StepVerifier.create(repository.invoke(lawInvocation).take(1)).expectNext(lawPower).verifyComplete();
    }

    @Test
    void when_updatingExistingAttribute_then_subscribersReceiveNewValue() {
        // Stormbringer's soul count increases as it feeds
        val initialSouls = Val.of(42);
        val updatedSouls = Val.of(43);

        repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, initialSouls).subscribe();

        val invocation = createInvocation(Val.of(STORMBRINGER), STORMBRINGER_SOULS);
        val flux       = repository.invoke(invocation).take(2);

        StepVerifier.create(flux).expectNext(initialSouls).then(() -> {
            repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, updatedSouls).subscribe();
        }).expectNext(updatedSouls).verifyComplete();
    }

    @Test
    void when_initializingWithPersistedAttributes_then_attributesAreRecovered() {
        // Pre-populate storage with Melnibonéan shield power
        val shieldPower    = Val.of(88);
        val ttl            = Duration.ofHours(1);
        val deadline       = clock.instant().plus(ttl);
        val key            = new AttributeKey(Val.of(IMRRYR), DREAMING_CITY_PROTECTION, List.of());
        val persistedValue = new PersistedAttribute(shieldPower, clock.instant(), ttl, TimeOutStrategy.REMOVE,
                deadline);

        storage.put(key, persistedValue).block();

        // Create new repository instance - should recover attribute
        val newRepository = new InMemoryAttributeRepository(clock, storage);

        val invocation = createInvocation(Val.of(IMRRYR), DREAMING_CITY_PROTECTION);

        StepVerifier.create(newRepository.invoke(invocation).take(1)).expectNext(shieldPower).verifyComplete();
    }

    @Test
    void when_initializingWithExpiredAttributes_then_expiredAttributesAreNotRecovered() {
        // Pre-populate storage with an expired chaos pact
        val expiredPact    = Val.of(true);
        val ttl            = Duration.ofMillis(1);
        val deadline       = clock.instant().plus(ttl);
        val key            = new AttributeKey(Val.of(ARIOCH), CHAOS_PACT, List.of());
        val persistedValue = new PersistedAttribute(expiredPact, clock.instant(), ttl, TimeOutStrategy.REMOVE,
                deadline);

        storage.put(key, persistedValue).block();

        // Advance time past deadline
        controlledClock.advanceBy(Duration.ofMillis(100));

        // Create new repository - expired attribute should not be recovered
        val newRepository = new InMemoryAttributeRepository(clock, storage);

        val invocation = createInvocation(Val.of(ARIOCH), CHAOS_PACT);

        StepVerifier.create(newRepository.invoke(invocation).take(1)).expectNext(ATTRIBUTE_UNAVAILABLE)
                .verifyComplete();
    }

    @Test
    void when_cancellingScheduledTimeout_then_timeoutDoesNotFire() {
        // Yyrkoon's treachery is discovered and stopped before completing
        val treacheryPlanned = Val.of(true);
        val planDuration     = Duration.ofMillis(100);

        repository.publishAttribute(Val.of(YYRKOON), "yyrkoon.treachery", treacheryPlanned, planDuration,
                TimeOutStrategy.REMOVE).subscribe();

        // Cancel by removing attribute before timeout
        Awaitility.await().pollDelay(Duration.ofMillis(20)).atMost(Duration.ofMillis(50)).untilAsserted(() -> {
            repository.removeAttribute(Val.of(YYRKOON), "yyrkoon.treachery").block();
        });

        val invocation = createInvocation(Val.of(YYRKOON), "yyrkoon.treachery");

        // Should receive unavailable immediately, not wait for original timeout
        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("provideTimeoutStrategyScenarios")
    void when_attributeTimesOutWithStrategy_then_correctBehaviorApplied(String scenario, TimeOutStrategy strategy,
            Val expectedAfterTimeout) {
        // Test both timeout strategies with various Melnibonéan scenarios
        val initialValue = Val.of(true);
        val ttl          = Duration.ofMillis(100);

        repository.publishAttribute(scenario, initialValue, ttl, strategy).subscribe();

        val invocation = createInvocation(null, scenario);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(initialValue)
                .expectNext(expectedAfterTimeout).verifyComplete();
    }

    private static Stream<Arguments> provideTimeoutStrategyScenarios() {
        return Stream.of(Arguments.of("chaos.shield", TimeOutStrategy.REMOVE, ATTRIBUTE_UNAVAILABLE),
                Arguments.of("elric.drug", TimeOutStrategy.BECOME_UNDEFINED, Val.UNDEFINED),
                Arguments.of("dragon.stamina", TimeOutStrategy.REMOVE, ATTRIBUTE_UNAVAILABLE),
                Arguments.of("sorcery.concentration", TimeOutStrategy.BECOME_UNDEFINED, Val.UNDEFINED));
    }

    @Test
    void when_validatingPublishParameters_then_invalidInputsAreRejected() {
        assertThatThrownBy(() -> repository.publishAttribute(null, Val.of(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Attribute name must not be null");

        assertThatThrownBy(() -> repository.publishAttribute("", Val.of(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Attribute name must not be null");

        assertThatThrownBy(() -> repository.publishAttribute("test.attribute", (Val) null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Value must not be null");

        assertThatThrownBy(() -> repository.publishAttribute("test.attribute", Val.of(1), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TTL must not be negative.");

        assertThatThrownBy(() -> repository.publishAttribute("test.attribute", Val.of(1), Duration.ofMillis(100), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Timeout strategy must not be null");

        assertThatThrownBy(() -> repository.publishAttribute("test.attribute", null, Val.of(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Arguments must not be null");
    }

    @Test
    void when_validatingRemoveParameters_then_invalidInputsAreRejected() {
        assertThatThrownBy(() -> repository.removeAttribute(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        assertThatThrownBy(() -> repository.removeAttribute("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        assertThatThrownBy(() -> repository.removeAttribute("test.attribute", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Arguments must not be null");
    }

    @Test
    void when_concurrentPublishesOccur_then_allAreHandledCorrectly() throws InterruptedException {
        // Multiple sorcerers attempt to claim the Ruby Throne simultaneously
        val threadCount = 20;
        val latch       = new CountDownLatch(threadCount);
        val errors      = new CopyOnWriteArrayList<Throwable>();

        val threads = IntStream.range(0, threadCount).mapToObj(i -> new Thread(() -> {
            try {
                val sorcerer = Val.of("sorcerer-" + i);
                val power    = Val.of(50 + i);
                log.error("-> {}", sorcerer);
                repository.publishAttribute(sorcerer, SORCERY_LEVEL, power).block();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        })).toList();
        log.error("*****");
        threads.forEach(Thread::start);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
        log.error("<<<<<<");

        // Verify all attributes were published
        for (var i = 0; i < threadCount; i++) {
            val sorcerer   = Val.of("sorcerer-" + i);
            val expected   = Val.of(50 + i);
            val invocation = createInvocation(sorcerer, SORCERY_LEVEL);
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(expected).verifyComplete();
        }
    }

    @Test
    void when_concurrentSubscriptionsOccur_then_allReceiveValues() throws InterruptedException {
        // Multiple observers monitor Stormbringer's soul consumption
        val souls           = Val.of(42);
        val subscriberCount = 50;
        val latch           = new CountDownLatch(subscriberCount);
        val receivedValues  = new CopyOnWriteArrayList<Val>();

        repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();

        val invocation = createInvocation(Val.of(STORMBRINGER), STORMBRINGER_SOULS);

        val threads = IntStream.range(0, subscriberCount).mapToObj(i -> new Thread(() -> {
            repository.invoke(invocation).take(1).subscribe(value -> {
                receivedValues.add(value);
                latch.countDown();
            });
        })).toList();

        threads.forEach(Thread::start);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedValues).hasSize(subscriberCount).allMatch(v -> v.equals(souls));
    }

    @Test
    void when_subscriberUnsubscribesEarly_then_cleanupOccurs() {
        // A sorcerer stops monitoring the chaos pact mid-stream
        val pactActive = Val.of(true);

        repository.publishAttribute(Val.of(ARIOCH), CHAOS_PACT, pactActive).subscribe();

        val invocation = createInvocation(Val.of(ARIOCH), CHAOS_PACT);
        val disposable = repository.invoke(invocation).subscribe();

        // Immediately unsubscribe
        disposable.dispose();

        // Publish update - no subscribers should exist
        val pactInactive = Val.of(false);
        repository.publishAttribute(Val.of(ARIOCH), CHAOS_PACT, pactInactive).subscribe();

        // Verify cleanup by checking that a new subscription gets the latest value
        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(pactInactive).verifyComplete();
        });
    }

    @Test
    void when_rapidPublishAndRemoveCycles_then_noRaceConditionsOccur() throws InterruptedException {
        // Simulate rapid chaos/law power fluctuations during a battle
        val iterations = 100;
        val latch      = new CountDownLatch(iterations * 2);
        val errors     = new CopyOnWriteArrayList<Throwable>();

        for (var i = 0; i < iterations; i++) {
            val index = i;
            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power", Val.of(index)).block();
                    latch.countDown();
                } catch (Exception e) {
                    errors.add(e);
                    latch.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    repository.removeAttribute("battle.power").block();
                    latch.countDown();
                } catch (Exception e) {
                    errors.add(e);
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
    }

    @Test
    void when_multipleTimeoutsScheduledConcurrently_then_allFireCorrectly() {
        // Multiple chaos shields expire at staggered intervals
        val shieldCount = 10;
        val baseDelay   = Duration.ofMillis(50);
        val counters    = new CopyOnWriteArrayList<AtomicInteger>();

        for (var i = 0; i < shieldCount; i++) {
            val shieldId = "chaos.shield" + i;
            val delay    = baseDelay.multipliedBy(i + 1);
            val counter  = new AtomicInteger(0);
            counters.add(counter);

            repository.publishAttribute(shieldId, Val.of(true), delay, TimeOutStrategy.REMOVE).block();

            val invocation = createInvocation(null, shieldId);
            repository.invoke(invocation).subscribe(value -> {
                if (ATTRIBUTE_UNAVAILABLE.equals(value)) {
                    counter.incrementAndGet();
                }
            });
        }

        // Wait for all timeouts to fire
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(counters).allMatch(counter -> counter.get() == 1);
        });
    }

    @Test
    void when_publishingDuringActiveSubscription_then_subscriberReceivesBothValues() {
        // Monitor Elric's strength as he takes drugs
        val weakStrength   = Val.of(20);
        val strongStrength = Val.of(100);

        repository.publishAttribute(Val.of(ELRIC), ELRIC_STRENGTH, weakStrength).subscribe();

        val invocation = createInvocation(Val.of(ELRIC), ELRIC_STRENGTH);
        val values     = new ArrayList<Val>();

        repository.invoke(invocation).take(2).subscribe(values::add);

        // Wait for initial value
        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        // Publish update
        repository.publishAttribute(Val.of(ELRIC), ELRIC_STRENGTH, strongStrength).subscribe();

        // Wait for updated value
        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(weakStrength, strongStrength);
    }

    @Test
    void when_removingAttributeWithMultipleSubscribers_then_allReceiveUnavailable() {
        // Dreaming City shield collapses, all observers notified
        val shieldActive = Val.of(true);

        repository.publishAttribute(Val.of(IMRRYR), DREAMING_CITY_PROTECTION, shieldActive).subscribe();

        val invocation = createInvocation(Val.of(IMRRYR), DREAMING_CITY_PROTECTION);
        val values1    = new CopyOnWriteArrayList<Val>();
        val values2    = new CopyOnWriteArrayList<Val>();
        val values3    = new CopyOnWriteArrayList<Val>();

        repository.invoke(invocation).subscribe(values1::add);
        repository.invoke(invocation).subscribe(values2::add);
        repository.invoke(invocation).subscribe(values3::add);

        // Wait for all to receive initial value
        Awaitility.await().atMost(Duration.ofSeconds(1))
                .until(() -> values1.size() == 1 && values2.size() == 1 && values3.size() == 1);

        // Remove attribute
        repository.removeAttribute(Val.of(IMRRYR), DREAMING_CITY_PROTECTION).subscribe();

        // All should receive unavailable
        Awaitility.await().atMost(Duration.ofSeconds(1))
                .until(() -> values1.size() == 2 && values2.size() == 2 && values3.size() == 2);

        assertThat(values1.get(1)).isEqualTo(ATTRIBUTE_UNAVAILABLE);
        assertThat(values2.get(1)).isEqualTo(ATTRIBUTE_UNAVAILABLE);
        assertThat(values3.get(1)).isEqualTo(ATTRIBUTE_UNAVAILABLE);
    }

    @Test
    void when_storageOperationFails_then_errorIsPropagated() {
        // Simulate storage failure during chaos realm instability
        val failingStorage = new AttributeStorage() {
            @Override
            public Mono<PersistedAttribute> get(AttributeKey key) {
                return Mono.error(new RuntimeException("Chaos realm instability"));
            }

            @Override
            public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
                return Mono.error(new RuntimeException("Chaos realm instability"));
            }

            @Override
            public Mono<Void> remove(AttributeKey key) {
                return Mono.error(new RuntimeException("Chaos realm instability"));
            }

            @Override
            public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll() {
                return Flux.error(new RuntimeException("Chaos realm instability"));
            }
        };

        assertThatThrownBy(() -> new InMemoryAttributeRepository(clock, failingStorage))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Chaos realm instability");
    }

    @Test
    void when_largeNumberOfAttributesPublished_then_allAreAccessible() {
        // The Young Kingdoms each have sovereign access controls
        val kingdomCount = 100;

        for (var i = 0; i < kingdomCount; i++) {
            val kingdom     = Val.of("kingdom-" + i);
            val sovereignty = Val.of(true);
            repository.publishAttribute(kingdom, "kingdom.sovereign", sovereignty).subscribe();
        }

        // Verify all are accessible
        for (var i = 0; i < kingdomCount; i++) {
            val kingdom    = Val.of("kingdom-" + i);
            val invocation = createInvocation(kingdom, "kingdom.sovereign");
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(Val.of(true)).verifyComplete();
        }
    }

    @Test
    void when_timeoutOccursDuringActiveSubscriptions_then_subscribersNotifiedImmediately() {
        // Dragon bond weakens and fails while riders monitor it
        val bondActive = Val.of(true);
        val bondTTL    = Duration.ofMillis(100);
        val values     = new CopyOnWriteArrayList<Val>();

        repository.publishAttribute(Val.of(ELRIC), DRAGON_BOND, bondActive, bondTTL, TimeOutStrategy.REMOVE).block();

        val invocation = createInvocation(Val.of(ELRIC), DRAGON_BOND);
        repository.invoke(invocation).subscribe(values::add);

        // Wait for timeout to fire
        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(bondActive, ATTRIBUTE_UNAVAILABLE);
    }

    @Test
    void when_updatingAttributeChangesToUndefined_then_subscribersReceiveUndefined() {
        // Elric's strength explicitly set to undefined (drugs completely worn off)
        val strengthened = Val.of(100);

        repository.publishAttribute(Val.of(ELRIC), ELRIC_STRENGTH, strengthened).subscribe();

        val invocation = createInvocation(Val.of(ELRIC), ELRIC_STRENGTH);
        val values     = new ArrayList<Val>();

        repository.invoke(invocation).take(2).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        // Update to undefined
        repository.publishAttribute(Val.of(ELRIC), ELRIC_STRENGTH, Val.UNDEFINED).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(strengthened, Val.UNDEFINED);
    }

    @Test
    void when_attributeHasVeryLargeTTL_then_deadlineIsInstantMax() {
        // The balance between Law and Chaos is eternal
        val balanceActive = Val.of(true);
        val infiniteTTL   = Duration.ofSeconds(Long.MAX_VALUE);

        repository.publishAttribute(BALANCE_KEEPER, balanceActive, infiniteTTL, TimeOutStrategy.REMOVE).subscribe();

        // Verify attribute is stored with Instant.MAX deadline
        val key       = new AttributeKey(null, BALANCE_KEEPER, List.of());
        val persisted = storage.get(key).block();

        assertThat(persisted).isNotNull();
        assertThat(persisted.timeoutDeadline()).isEqualTo(Instant.MAX);
    }

    @Test
    void when_multipleUpdatesInQuickSuccession_then_allSubscribersReceiveAllUpdates() {
        // Stormbringer rapidly consumes souls during battle
        val updateCount = 5;
        val values      = new CopyOnWriteArrayList<Val>();

        val initialSouls = Val.of(42);
        repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, initialSouls).subscribe();

        val invocation = createInvocation(Val.of(STORMBRINGER), STORMBRINGER_SOULS);
        repository.invoke(invocation).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        // Rapid updates
        for (var i = 1; i <= updateCount; i++) {
            val souls = Val.of(42 + i);
            repository.publishAttribute(Val.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();
        }

        // Wait for all updates
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> values.size() == updateCount + 1);

        assertThat(values).hasSize(updateCount + 1);
        assertThat(values.getFirst()).isEqualTo(initialSouls);
        assertThat(values.getLast()).isEqualTo(Val.of(42 + updateCount));
    }

    @Test
    void when_attributeUpdatedAfterTimeout_then_newTimeoutScheduled() {
        // Chaos pact renewed after expiration
        val pactActive = Val.of(true);
        val initialTTL = Duration.ofMillis(100);
        val renewedTTL = Duration.ofMillis(200);

        repository.publishAttribute(Val.of(ARIOCH), CHAOS_PACT, pactActive, initialTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        // Wait for initial timeout
        Awaitility.await().atMost(Duration.ofMillis(200)).pollDelay(Duration.ofMillis(120)).untilAsserted(() -> {
            val invocation = createInvocation(Val.of(ARIOCH), CHAOS_PACT);
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ATTRIBUTE_UNAVAILABLE)
                    .verifyComplete();
        });

        // Renew pact
        repository.publishAttribute(Val.of(ARIOCH), CHAOS_PACT, pactActive, renewedTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        // Verify renewed pact is active
        val invocation = createInvocation(Val.of(ARIOCH), CHAOS_PACT);
        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(pactActive).verifyComplete();

        // Verify new timeout will fire
        Awaitility.await().atMost(Duration.ofMillis(300)).pollDelay(Duration.ofMillis(220)).untilAsserted(() -> {
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ATTRIBUTE_UNAVAILABLE)
                    .verifyComplete();
        });
    }

    @Test
    void when_stressTestingWithConcurrentOperations_then_repositoryRemainsStable() throws InterruptedException {
        // Simulate chaotic battle with multiple simultaneous operations
        val operationCount = 200;
        val latch          = new CountDownLatch(operationCount);
        val errors         = new CopyOnWriteArrayList<Throwable>();

        for (var i = 0; i < operationCount / 4; i++) {
            val index = i;

            // Publish
            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power." + index, Val.of(index)).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

            // Subscribe
            new Thread(() -> {
                try {
                    val invocation = createInvocation(null, "battle.power" + index);
                    repository.invoke(invocation).take(1).blockFirst();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

            // Update
            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power" + index, Val.of(index * 2)).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

            // Remove
            new Thread(() -> {
                try {
                    repository.removeAttribute("battle.power" + index).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
    }

    private AttributeFinderInvocation createInvocation(Val entity, String attributeName) {
        return createInvocation(entity, attributeName, List.of());
    }

    private AttributeFinderInvocation createInvocation(Val entity, String attributeName, List<Val> arguments) {
        if (entity == null) {
            return new AttributeFinderInvocation(PDP_CONFIGURATION, attributeName, arguments, Map.of(),
                    Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(50), 3, false);
        }
        return new AttributeFinderInvocation(PDP_CONFIGURATION, attributeName, entity, arguments, Map.of(),
                Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(50), 3, false);
    }

    private static class ControlledClock {
        private Instant currentInstant = Instant.now();

        public Clock getClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneId.systemDefault();
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return currentInstant; // ← Read current value dynamically
                }
            };
        }

        public void advanceBy(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }

}
