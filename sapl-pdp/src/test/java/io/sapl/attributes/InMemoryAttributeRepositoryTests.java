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
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.sapl.attributes.InMemoryAttributeRepository.ERROR_ATTRIBUTE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
        val souls = Value.of(42);

        repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();

        val invocation = createInvocation(Value.of(STORMBRINGER), STORMBRINGER_SOULS);

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(souls).verifyComplete();
    }

    @Test
    void when_publishingChaosPactWithTTL_then_pactExpiresAndBecomesUnavailable() {
        val pactActive = Value.of(true);
        val pactTTL    = Duration.ofMillis(100);

        repository.publishAttribute(Value.of(ARIOCH), CHAOS_PACT, pactActive, pactTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        val invocation = createInvocation(Value.of(ARIOCH), CHAOS_PACT);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(pactActive)
                .expectNext(ERROR_ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @Test
    void when_publishingElricStrengthWithBecomeUndefined_then_strengthBecomesUndefinedAfterDrugWearOff() {
        val strengthened = Value.of(100);
        val drugDuration = Duration.ofMillis(100);

        repository.publishAttribute(Value.of(ELRIC), ELRIC_STRENGTH, strengthened, drugDuration,
                TimeOutStrategy.BECOME_UNDEFINED).subscribe();

        val invocation = createInvocation(Value.of(ELRIC), ELRIC_STRENGTH);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(strengthened).expectNext(Value.UNDEFINED)
                .verifyComplete();
    }

    @Test
    void when_removingDragonBond_then_activeSubscribersReceiveUnavailable() {
        val bondStrength = Value.of(95);

        repository.publishAttribute(Value.of(ELRIC), DRAGON_BOND, bondStrength).subscribe();

        val invocation = createInvocation(Value.of(ELRIC), DRAGON_BOND);
        val flux       = repository.invoke(invocation);

        StepVerifier.create(flux.take(2)).expectNext(bondStrength).then(() -> {
            repository.removeAttribute(Value.of(ELRIC), DRAGON_BOND).subscribe();
        }).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE).verifyComplete();
    }

    @Test
    void when_multipleSubscribersForSameAttribute_then_allReceiveUpdates() {
        val throneOccupied = Value.of(true);

        repository.publishAttribute(THRONE_ACCESS, throneOccupied).subscribe();

        val invocation = createInvocation(null, THRONE_ACCESS);

        val subscriber1 = repository.invoke(invocation).take(2);
        val subscriber2 = repository.invoke(invocation).take(2);
        val subscriber3 = repository.invoke(invocation).take(2);

        StepVerifier.create(Flux.merge(subscriber1, subscriber2, subscriber3).take(3)).expectNextCount(3)
                .verifyComplete();

        val throneVacated = Value.of(false);
        repository.publishAttribute(THRONE_ACCESS, throneVacated).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            val verification = repository.invoke(invocation).take(1);
            StepVerifier.create(verification).expectNext(throneVacated).verifyComplete();
        });
    }

    @Test
    void when_publishingWithInfiniteTTL_then_attributeNeverExpires() {
        val balanceActive = Value.of(true);

        repository.publishAttribute(BALANCE_KEEPER, balanceActive, Duration.ofSeconds(Long.MAX_VALUE),
                TimeOutStrategy.REMOVE).subscribe();

        val invocation = createInvocation(null, BALANCE_KEEPER);

        controlledClock.advanceBy(Duration.ofDays(365 * 100));

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(balanceActive).verifyComplete();
    }

    @Test
    void when_queryingNonExistentAttribute_then_receivesUnavailable() {
        val invocation = createInvocation(Value.of(CYMORIL), "sorcery.unknown");

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE)
                .verifyComplete();
    }

    @Test
    void when_publishingAttributeWithArguments_then_attributeIsRetrievableWithSameArguments() {
        val summoningPower = Value.of(85);
        val arguments      = List.<Value>of(Value.of(CHAOS_REALM), Value.of(5));

        repository.publishAttribute("summoning.power", arguments, summoningPower).subscribe();

        val invocation = createInvocation(null, "summoning.power", arguments);

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(summoningPower).verifyComplete();
    }

    @Test
    void when_publishingAttributeWithDifferentArguments_then_attributesAreIndependent() {
        val chaosPower = Value.of(95);
        val lawPower   = Value.of(45);

        repository.publishAttribute("summoning.power", List.of(Value.of(CHAOS_REALM)), chaosPower).subscribe();
        repository.publishAttribute("summoning.power", List.of(Value.of(LAW_REALM)), lawPower).subscribe();

        val chaosInvocation = createInvocation(null, "summoning.power", List.of(Value.of(CHAOS_REALM)));
        val lawInvocation   = createInvocation(null, "summoning.power", List.of(Value.of(LAW_REALM)));

        StepVerifier.create(repository.invoke(chaosInvocation).take(1)).expectNext(chaosPower).verifyComplete();
        StepVerifier.create(repository.invoke(lawInvocation).take(1)).expectNext(lawPower).verifyComplete();
    }

    @Test
    void when_updatingExistingAttribute_then_subscribersReceiveNewValue() {
        val initialSouls = Value.of(42);
        val updatedSouls = Value.of(43);

        repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, initialSouls).subscribe();

        val invocation = createInvocation(Value.of(STORMBRINGER), STORMBRINGER_SOULS);
        val flux       = repository.invoke(invocation).take(2);

        StepVerifier.create(flux).expectNext(initialSouls).then(() -> {
            repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, updatedSouls).subscribe();
        }).expectNext(updatedSouls).verifyComplete();
    }

    @Test
    void when_initializingWithPersistedAttributes_then_attributesAreRecovered() {
        val shieldPower    = Value.of(88);
        val ttl            = Duration.ofHours(1);
        val deadline       = clock.instant().plus(ttl);
        val key            = new AttributeKey(Value.of(IMRRYR), DREAMING_CITY_PROTECTION, List.of());
        val persistedValue = new PersistedAttribute(shieldPower, clock.instant(), ttl, TimeOutStrategy.REMOVE,
                deadline);

        storage.put(key, persistedValue).block();

        val newRepository = new InMemoryAttributeRepository(clock, storage);

        val invocation = createInvocation(Value.of(IMRRYR), DREAMING_CITY_PROTECTION);

        StepVerifier.create(newRepository.invoke(invocation).take(1)).expectNext(shieldPower).verifyComplete();
    }

    @Test
    void when_initializingWithExpiredAttributes_then_expiredAttributesAreNotRecovered() {
        val expiredPact    = Value.of(true);
        val ttl            = Duration.ofMillis(1);
        val deadline       = clock.instant().plus(ttl);
        val key            = new AttributeKey(Value.of(ARIOCH), CHAOS_PACT, List.of());
        val persistedValue = new PersistedAttribute(expiredPact, clock.instant(), ttl, TimeOutStrategy.REMOVE,
                deadline);

        storage.put(key, persistedValue).block();

        controlledClock.advanceBy(Duration.ofMillis(100));

        val newRepository = new InMemoryAttributeRepository(clock, storage);

        val invocation = createInvocation(Value.of(ARIOCH), CHAOS_PACT);

        StepVerifier.create(newRepository.invoke(invocation).take(1)).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE)
                .verifyComplete();
    }

    @Test
    void when_cancellingScheduledTimeout_then_timeoutDoesNotFire() {
        val treacheryPlanned = Value.of(true);
        val planDuration     = Duration.ofMillis(100);

        repository.publishAttribute(Value.of(YYRKOON), "yyrkoon.treachery", treacheryPlanned, planDuration,
                TimeOutStrategy.REMOVE).subscribe();

        Awaitility.await().pollDelay(Duration.ofMillis(20)).atMost(Duration.ofMillis(50)).untilAsserted(() -> {
            repository.removeAttribute(Value.of(YYRKOON), "yyrkoon.treachery").block();
        });

        val invocation = createInvocation(Value.of(YYRKOON), "yyrkoon.treachery");

        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE)
                .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("provideTimeoutStrategyScenarios")
    void when_attributeTimesOutWithStrategy_then_correctBehaviorApplied(String scenario, TimeOutStrategy strategy,
            Value expectedAfterTimeout) {
        val initialValue = Value.of(true);
        val ttl          = Duration.ofMillis(100);

        repository.publishAttribute(scenario, initialValue, ttl, strategy).subscribe();

        val invocation = createInvocation(null, scenario);

        StepVerifier.create(repository.invoke(invocation).take(2)).expectNext(initialValue)
                .expectNext(expectedAfterTimeout).verifyComplete();
    }

    private static Stream<Arguments> provideTimeoutStrategyScenarios() {
        return Stream.of(arguments("chaos.shield", TimeOutStrategy.REMOVE, ERROR_ATTRIBUTE_UNAVAILABLE),
                arguments("elric.drug", TimeOutStrategy.BECOME_UNDEFINED, Value.UNDEFINED),
                arguments("dragon.stamina", TimeOutStrategy.REMOVE, ERROR_ATTRIBUTE_UNAVAILABLE),
                arguments("sorcery.concentration", TimeOutStrategy.BECOME_UNDEFINED, Value.UNDEFINED));
    }

    @Test
    void when_validatingPublishParameters_then_invalidInputsAreRejected() {
        Runnable publishWithNullName = () -> repository.publishAttribute(null, Value.of(1));
        assertThatThrownBy(publishWithNullName::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        Runnable publishWithEmptyName = () -> repository.publishAttribute("", Value.of(1));
        assertThatThrownBy(publishWithEmptyName::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        Runnable publishWithNullValue = () -> repository.publishAttribute("test.attribute", (Value) null);
        assertThatThrownBy(publishWithNullValue::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must not be null");

        Runnable publishWithNegativeTTL = () -> repository.publishAttribute("test.attribute", Value.of(1),
                Duration.ofMillis(-1));
        assertThatThrownBy(publishWithNegativeTTL::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL must not be negative.");

        Runnable publishWithNullStrategy = () -> repository.publishAttribute("test.attribute", Value.of(1),
                Duration.ofMillis(100), null);
        assertThatThrownBy(publishWithNullStrategy::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Timeout strategy must not be null");

        Runnable publishWithNullArguments = () -> repository.publishAttribute("test.attribute", null, Value.of(1));
        assertThatThrownBy(publishWithNullArguments::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Arguments must not be null");
    }

    @Test
    void when_validatingRemoveParameters_then_invalidInputsAreRejected() {
        Runnable removeWithNullName = () -> repository.removeAttribute(null);
        assertThatThrownBy(removeWithNullName::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        Runnable removeWithEmptyName = () -> repository.removeAttribute("");
        assertThatThrownBy(removeWithEmptyName::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute name must not be null");

        Runnable removeWithNullArguments = () -> repository.removeAttribute("test.attribute", null);
        assertThatThrownBy(removeWithNullArguments::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Arguments must not be null");
    }

    @Test
    void when_concurrentPublishesOccur_then_allAreHandledCorrectly() throws InterruptedException {
        val threadCount = 20;
        val latch       = new CountDownLatch(threadCount);
        val errors      = new CopyOnWriteArrayList<Throwable>();

        val threads = IntStream.range(0, threadCount).mapToObj(i -> new Thread(() -> {
            try {
                val sorcerer = Value.of("sorcerer-" + i);
                val power    = Value.of(50 + i);
                repository.publishAttribute(sorcerer, SORCERY_LEVEL, power).block();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        })).toList();
        threads.forEach(Thread::start);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();

        for (var i = 0; i < threadCount; i++) {
            val sorcerer   = Value.of("sorcerer-" + i);
            val expected   = Value.of(50 + i);
            val invocation = createInvocation(sorcerer, SORCERY_LEVEL);
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(expected).verifyComplete();
        }
    }

    @Test
    void when_concurrentSubscriptionsOccur_then_allReceiveValues() throws InterruptedException {
        val souls           = Value.of(42);
        val subscriberCount = 50;
        val latch           = new CountDownLatch(subscriberCount);
        val receivedValues  = new CopyOnWriteArrayList<Value>();

        repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();

        val invocation = createInvocation(Value.of(STORMBRINGER), STORMBRINGER_SOULS);

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
        val pactActive = Value.of(true);

        repository.publishAttribute(Value.of(ARIOCH), CHAOS_PACT, pactActive).subscribe();

        val invocation = createInvocation(Value.of(ARIOCH), CHAOS_PACT);
        val disposable = repository.invoke(invocation).subscribe();

        disposable.dispose();

        val pactInactive = Value.of(false);
        repository.publishAttribute(Value.of(ARIOCH), CHAOS_PACT, pactInactive).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(pactInactive).verifyComplete();
        });
    }

    @Test
    void when_rapidPublishAndRemoveCycles_then_noRaceConditionsOccur() throws InterruptedException {
        val iterations = 100;
        val latch      = new CountDownLatch(iterations * 2);
        val errors     = new CopyOnWriteArrayList<Throwable>();

        for (var i = 0; i < iterations; i++) {
            val index = i;
            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power", Value.of(index)).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

            new Thread(() -> {
                try {
                    repository.removeAttribute("battle.power").block();
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

    @Test
    void when_multipleTimeoutsScheduledConcurrently_then_allFireCorrectly() {
        val shieldCount = 10;
        val baseDelay   = Duration.ofMillis(50);
        val counters    = new CopyOnWriteArrayList<AtomicInteger>();

        for (var i = 0; i < shieldCount; i++) {
            val shieldId = "chaos.shield" + i;
            val delay    = baseDelay.multipliedBy(i + 1);
            val counter  = new AtomicInteger(0);
            counters.add(counter);

            repository.publishAttribute(shieldId, Value.of(true), delay, TimeOutStrategy.REMOVE).block();

            val invocation = createInvocation(null, shieldId);
            repository.invoke(invocation).subscribe(value -> {
                if (ERROR_ATTRIBUTE_UNAVAILABLE.equals(value)) {
                    counter.incrementAndGet();
                }
            });
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(counters).allMatch(counter -> counter.get() == 1);
        });
    }

    @Test
    void when_publishingDuringActiveSubscription_then_subscriberReceivesBothValues() {
        val weakStrength   = Value.of(20);
        val strongStrength = Value.of(100);

        repository.publishAttribute(Value.of(ELRIC), ELRIC_STRENGTH, weakStrength).subscribe();

        val invocation = createInvocation(Value.of(ELRIC), ELRIC_STRENGTH);
        val values     = new ArrayList<Value>();

        repository.invoke(invocation).take(2).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        repository.publishAttribute(Value.of(ELRIC), ELRIC_STRENGTH, strongStrength).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(weakStrength, strongStrength);
    }

    @Test
    void when_removingAttributeWithMultipleSubscribers_then_allReceiveUnavailable() {
        val shieldActive = Value.of(true);

        repository.publishAttribute(Value.of(IMRRYR), DREAMING_CITY_PROTECTION, shieldActive).subscribe();

        val invocation = createInvocation(Value.of(IMRRYR), DREAMING_CITY_PROTECTION);
        val values1    = new CopyOnWriteArrayList<Value>();
        val values2    = new CopyOnWriteArrayList<Value>();
        val values3    = new CopyOnWriteArrayList<Value>();

        repository.invoke(invocation).subscribe(values1::add);
        repository.invoke(invocation).subscribe(values2::add);
        repository.invoke(invocation).subscribe(values3::add);

        Awaitility.await().atMost(Duration.ofSeconds(1))
                .until(() -> values1.size() == 1 && values2.size() == 1 && values3.size() == 1);

        repository.removeAttribute(Value.of(IMRRYR), DREAMING_CITY_PROTECTION).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1))
                .until(() -> values1.size() == 2 && values2.size() == 2 && values3.size() == 2);

        assertThat(values1.get(1)).isEqualTo(ERROR_ATTRIBUTE_UNAVAILABLE);
        assertThat(values2.get(1)).isEqualTo(ERROR_ATTRIBUTE_UNAVAILABLE);
        assertThat(values3.get(1)).isEqualTo(ERROR_ATTRIBUTE_UNAVAILABLE);
    }

    @Test
    void when_storageOperationFails_then_errorIsPropagated() {
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

        Runnable createRepositoryWithFailingStorage = () -> new InMemoryAttributeRepository(clock, failingStorage);
        assertThatThrownBy(createRepositoryWithFailingStorage::run).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Chaos realm instability");
    }

    @Test
    void when_largeNumberOfAttributesPublished_then_allAreAccessible() {
        val kingdomCount = 100;

        for (var i = 0; i < kingdomCount; i++) {
            val kingdom     = Value.of("kingdom-" + i);
            val sovereignty = Value.of(true);
            repository.publishAttribute(kingdom, "kingdom.sovereign", sovereignty).subscribe();
        }

        for (var i = 0; i < kingdomCount; i++) {
            val kingdom    = Value.of("kingdom-" + i);
            val invocation = createInvocation(kingdom, "kingdom.sovereign");
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(Value.of(true)).verifyComplete();
        }
    }

    @Test
    void when_timeoutOccursDuringActiveSubscriptions_then_subscribersNotifiedImmediately() {
        val bondActive = Value.of(true);
        val bondTTL    = Duration.ofMillis(100);
        val values     = new CopyOnWriteArrayList<Value>();

        repository.publishAttribute(Value.of(ELRIC), DRAGON_BOND, bondActive, bondTTL, TimeOutStrategy.REMOVE).block();

        val invocation = createInvocation(Value.of(ELRIC), DRAGON_BOND);
        repository.invoke(invocation).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(bondActive, ERROR_ATTRIBUTE_UNAVAILABLE);
    }

    @Test
    void when_updatingAttributeChangesToUndefined_then_subscribersReceiveUndefined() {
        val strengthened = Value.of(100);

        repository.publishAttribute(Value.of(ELRIC), ELRIC_STRENGTH, strengthened).subscribe();

        val invocation = createInvocation(Value.of(ELRIC), ELRIC_STRENGTH);
        val values     = new ArrayList<Value>();

        repository.invoke(invocation).take(2).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        repository.publishAttribute(Value.of(ELRIC), ELRIC_STRENGTH, Value.UNDEFINED).subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 2);

        assertThat(values).containsExactly(strengthened, Value.UNDEFINED);
    }

    @Test
    void when_attributeHasVeryLargeTTL_then_deadlineIsInstantMax() {
        val balanceActive = Value.of(true);
        val infiniteTTL   = Duration.ofSeconds(Long.MAX_VALUE);

        repository.publishAttribute(BALANCE_KEEPER, balanceActive, infiniteTTL, TimeOutStrategy.REMOVE).subscribe();

        val key       = new AttributeKey(null, BALANCE_KEEPER, List.of());
        val persisted = storage.get(key).block();

        assertThat(persisted).isNotNull();
        assertThat(persisted.timeoutDeadline()).isEqualTo(Instant.MAX);
    }

    @Test
    void when_multipleUpdatesInQuickSuccession_then_allSubscribersReceiveAllUpdates() {
        val updateCount = 5;
        val values      = new CopyOnWriteArrayList<Value>();

        val initialSouls = Value.of(42);
        repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, initialSouls).subscribe();

        val invocation = createInvocation(Value.of(STORMBRINGER), STORMBRINGER_SOULS);
        repository.invoke(invocation).subscribe(values::add);

        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> values.size() == 1);

        for (var i = 1; i <= updateCount; i++) {
            val souls = Value.of(42 + i);
            repository.publishAttribute(Value.of(STORMBRINGER), STORMBRINGER_SOULS, souls).subscribe();
        }

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> values.size() == updateCount + 1);

        assertThat(values).hasSize(updateCount + 1);
        assertThat(values.getFirst()).isEqualTo(initialSouls);
        assertThat(values.getLast()).isEqualTo(Value.of(42 + updateCount));
    }

    @Test
    void when_attributeUpdatedAfterTimeout_then_newTimeoutScheduled() {
        val pactActive = Value.of(true);
        val initialTTL = Duration.ofMillis(100);
        val renewedTTL = Duration.ofMillis(200);

        repository.publishAttribute(Value.of(ARIOCH), CHAOS_PACT, pactActive, initialTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        Awaitility.await().atMost(Duration.ofMillis(200)).pollDelay(Duration.ofMillis(120)).untilAsserted(() -> {
            val invocation = createInvocation(Value.of(ARIOCH), CHAOS_PACT);
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE)
                    .verifyComplete();
        });

        repository.publishAttribute(Value.of(ARIOCH), CHAOS_PACT, pactActive, renewedTTL, TimeOutStrategy.REMOVE)
                .subscribe();

        val invocation = createInvocation(Value.of(ARIOCH), CHAOS_PACT);
        StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(pactActive).verifyComplete();

        Awaitility.await().atMost(Duration.ofMillis(300)).pollDelay(Duration.ofMillis(220)).untilAsserted(() -> {
            StepVerifier.create(repository.invoke(invocation).take(1)).expectNext(ERROR_ATTRIBUTE_UNAVAILABLE)
                    .verifyComplete();
        });
    }

    @Test
    void when_stressTestingWithConcurrentOperations_then_repositoryRemainsStable() throws InterruptedException {
        val operationCount = 200;
        val latch          = new CountDownLatch(operationCount);
        val errors         = new CopyOnWriteArrayList<Throwable>();

        for (var i = 0; i < operationCount / 4; i++) {
            val index = i;

            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power." + index, Value.of(index)).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

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

            new Thread(() -> {
                try {
                    repository.publishAttribute("battle.power" + index, Value.of(index * 2L)).block();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();

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

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private AttributeFinderInvocation createInvocation(Value entity, String attributeName) {
        return createInvocation(entity, attributeName, List.of());
    }

    private AttributeFinderInvocation createInvocation(Value entity, String attributeName, List<Value> arguments) {
        if (entity == null) {
            return new AttributeFinderInvocation("test-security", attributeName, arguments, Duration.ofSeconds(1),
                    Duration.ofMillis(100), Duration.ofMillis(50), 3, false, EMPTY_CTX);
        }
        return new AttributeFinderInvocation("test-security", attributeName, entity, arguments, Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(50), 3, false, EMPTY_CTX);
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
                    return currentInstant;
                }
            };
        }

        public void advanceBy(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }

}
