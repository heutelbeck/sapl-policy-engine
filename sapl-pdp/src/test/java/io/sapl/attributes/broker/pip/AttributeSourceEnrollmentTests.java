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
package io.sapl.attributes.broker.pip;

import static io.sapl.api.test.pdp.PdpTestHelper.configuration;
import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.LIMIT;
import static io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.attributes.broker.AttributeSubscriptionObserver;
import io.sapl.attributes.broker.pip.AttributeSourceReplacementTests.Palette;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.val;

@Timeout(20)
@DisplayName("Attribute enrollment, source ownership and terminal observation failures")
class AttributeSourceEnrollmentTests {

    @Test
    @DisplayName("an observation infrastructure failure reaches single, grouped and one-shot decision owners")
    void whenTheClockStopsThenEveryDecisionOwnerFailsClosed() throws Exception {
        val           clockFailed = new AtomicBoolean();
        InstantSource clock       = () -> {
                                      if (clockFailed.get()) {
                                          throw new DateTimeException("No observation time available.");
                                      }
                                      return Instant.EPOCH;
                                  };
        val           streams     = new CopyOnWriteArrayList<LatestSlotStream<Value>>();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, null, clock);
                val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(broker).build()) {
            broker.load(new Palette(() -> {
                val source = new LatestSlotStream<Value>();
                source.put(Value.TRUE);
                streams.add(source);
                return source;
            }));
            components.pdpVoterSource().loadConfiguration(configuration("""
                    policy "Read the current color" permit <palette.color> == true;
                    """));
            val subject = subscription("reader", "read", "color");
            val group   = new MultiAuthorizationSubscription().addSubscription("left", subject).addSubscription("right",
                    subject);
            try (val single = components.pdp().decide(subject); val grouped = components.pdp().decideAll(group)) {
                assertThat(single.awaitNext(LIMIT).decision()).isEqualTo(Decision.PERMIT);
                val first = grouped.awaitNext(LIMIT);
                assertThat(List.of(first.getDecision("left").decision(), first.getDecision("right").decision()))
                        .containsExactly(Decision.PERMIT, Decision.PERMIT);
                clockFailed.set(true);
                streams.forEach(source -> source.put(Value.FALSE));
                assertThat(single.awaitNext(LIMIT).decision()).isEqualTo(Decision.INDETERMINATE);
                val failed = grouped.awaitNext(LIMIT);
                assertThat(List.of(failed.getDecision("left").decision(), failed.getDecision("right").decision()))
                        .containsExactly(Decision.INDETERMINATE, Decision.INDETERMINATE);
            }
            val prior = streams.size();
            val once  = CompletableFuture.supplyAsync(() -> components.pdp().decideOnce(subject),
                    Thread::startVirtualThread);
            await().atMost(LIMIT).until(() -> streams.size() > prior);
            streams.getLast().put(Value.TRUE);
            assertThat(once.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS).decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Test
    @DisplayName("a later catalog replacement wins when an earlier replacement is still opening")
    void whenASecondSwapOvertakesTheFirstThenConsumersShareOnlyTheLastSource() throws Exception {
        val key      = key();
        val entered  = new CompletableFuture<Void>();
        val release  = new CompletableFuture<Void>();
        val obsolete = new LatestSlotStream<Value>();
        val latest   = new LatestSlotStream<Value>();
        val values   = new CopyOnWriteArrayList<Value>();
        val closes   = new AtomicInteger();
        obsolete.onClose(closes::incrementAndGet);
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val handle = broker.load(new Palette(LatestSlotStream::new));
            try (val reader = broker.open("reader", Set.of(key), inputs -> {
                values.add(inputs.get(key).value());
                return Set.of(key);
            })) {
                val earlier = CompletableFuture.runAsync(() -> broker.swap(handle, new Palette(() -> {
                    entered.complete(null);
                    release.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                    return obsolete;
                })), Thread::startVirtualThread);
                try {
                    entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    broker.swap(broker.catalog().iterator().next(), new Palette(() -> latest));
                    obsolete.put(Value.FALSE);
                    latest.put(Value.TRUE);
                    await().atMost(LIMIT).untilAsserted(() -> assertThat(values).containsExactly(Value.TRUE));
                } finally {
                    release.complete(null);
                }
                earlier.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                val late = new CopyOnWriteArrayList<Value>();
                try (val joiner = broker.open("late", Set.of(key), inputs -> {
                    late.add(inputs.get(key).value());
                    return Set.of(key);
                })) {
                    assertThat(late).containsExactly(Value.TRUE);
                    assertThat(closes).hasValue(1);
                    assertThat(values).containsExactly(Value.TRUE);
                }
            }
        }
    }

    @Test
    @DisplayName("unload without a fallback preserves interest for a subsequent matching PIP")
    void whenTheProviderIsReloadedThenTheExistingConsumerReceivesItsNewObservation() {
        val key    = key();
        val source = new LatestSlotStream<Value>();
        val values = new CopyOnWriteArrayList<Value>();
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val original = broker.load(new Palette(() -> source));
            try (val reader = broker.open("reader", Set.of(key), inputs -> {
                values.add(inputs.get(key).value());
                return Set.of(key);
            })) {
                source.put(Value.TRUE);
                await().atMost(LIMIT).untilAsserted(() -> assertThat(values).containsExactly(Value.TRUE));
                original.unload();
                val successor = new LatestSlotStream<Value>();
                broker.load(new Palette(() -> successor));
                successor.put(Value.FALSE);
                await().atMost(LIMIT).untilAsserted(
                        () -> assertThat(values).containsExactly(Value.TRUE, Value.UNDEFINED, Value.FALSE));
            }
        }
    }

    @Test
    @DisplayName("closing the broker disconnects peer subscriptions from a shared backing")
    void whenTheBrokerClosesThenAHeldSubscriptionReleasesItsRoutesAndPeerReferences() {
        val key = key();
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val held = (PolicyInformationPointAttributeBroker.BrokerSubscription) broker.open("one", Set.of(key),
                    inputs -> Set.of(key));
            broker.open("two", Set.of(key), inputs -> Set.of(key));
            val backing = held.routingTable.get(key);
            assertThat(backing.refcount()).isEqualTo(2);
            broker.close();
            assertThat(held.routingTable).isEmpty();
            assertThat(backing.subscribers()).isEmpty();
            assertThat(backing.refcount()).isZero();
        }
    }

    @Test
    @DisplayName("an already running grace expiry cannot consume the renewed interval")
    void whenAConsumerRenewsGraceThenAnOlderRunningExpiryLeavesItsBackingWarm() throws Exception {
        val key   = key();
        val opens = new AtomicInteger();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ofMillis(750))) {
            broker.load(new Palette(() -> {
                opens.incrementAndGet();
                return new LatestSlotStream<>();
            }));
            val type      = PolicyInformationPointAttributeBroker.class;
            val lookup    = privateLookupIn(type, lookup());
            val lock      = (ReentrantLock) lookup.findVarHandle(type, "lock", ReentrantLock.class).get(broker);
            val scheduler = (ScheduledExecutorService) lookup
                    .findVarHandle(type, "teardownScheduler", ScheduledExecutorService.class).get(broker);
            val first     = broker.open("first", Set.of(key), ignored -> Set.of(key));
            first.close();
            lock.lock();
            try {
                // The timer is running and waiting to enter. Reuse and release the same source while it is queued.
                await().atMost(LIMIT).until(lock::hasQueuedThreads);
                broker.open("second", Set.of(key), ignored -> Set.of(key)).close();
            } finally {
                lock.unlock();
            }
            // The single scheduler runs this immediate task after the old expiry and ahead of the renewed interval.
            scheduler.submit(() -> {}).get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
            try (val third = broker.open("third", Set.of(key), ignored -> Set.of(key))) {
                assertThat(opens).hasValue(1);
            }
        }
    }

    @Test
    @DisplayName("overlapping replacement can serve a consumer while the previous finder is still opening")
    void whenTheCatalogOvertakesAnInitialEnrollmentThenOnlyTheCurrentFinderServesTheConsumer() throws Exception {
        val entered   = new CompletableFuture<Void>();
        val release   = new CompletableFuture<Void>();
        val key       = key();
        val retired   = new LatestSlotStream<Value>();
        val successor = new LatestSlotStream<Value>();
        val released  = new AtomicInteger();
        val seen      = new CopyOnWriteArrayList<Value>();
        retired.onClose(released::incrementAndGet);
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val old     = broker.load(new Palette(() -> {
                            entered.complete(null);
                            release.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                            return retired;
                        }));
            val opening = CompletableFuture.supplyAsync(() -> broker.open("reader", Set.of(key), inputs -> {
                            seen.add(inputs.get(key).value());
                            return Set.of(key);
                        }), Thread::startVirtualThread);
            try {
                entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                broker.swap(old, new Palette(() -> successor));
                successor.put(Value.TRUE);
                await().atMost(LIMIT).untilAsserted(() -> assertThat(seen).containsExactly(Value.TRUE));
                assertThat(opening.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isNotNull();
                retired.put(Value.FALSE);
            } finally {
                release.complete(null);
            }
            try (val reader = opening.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                await().atMost(LIMIT).untilAsserted(() -> assertThat(released).hasValue(1));
                assertThat(seen).containsExactly(Value.TRUE);
            }
        }
    }

    @Test
    @DisplayName("known repository input can withdraw a dependency while its finder is still opening")
    void whenTheEnabledFlagChangesThenAConsumerCanDropAnUnfinishedRegistration() throws Exception {
        val enabled   = key("ward.enabled");
        val remote    = key();
        val entered   = new CompletableFuture<Void>();
        val release   = new CompletableFuture<Void>();
        val withdrawn = new CompletableFuture<Void>();
        val closed    = new CompletableFuture<Void>();
        val values    = new CopyOnWriteArrayList<Value>();
        val physical  = new LatestSlotStream<Value>();
        physical.onClose(() -> closed.complete(null));
        try (val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository)) {
            broker.load(new Palette(() -> {
                entered.complete(null);
                release.orTimeout(LIMIT.toMillis(), TimeUnit.MILLISECONDS).join();
                return physical;
            }));
            val flag = RepositoryKey.fromInvocation(enabled.invocation());
            repository.publish(flag, Value.TRUE);
            try (val reader = broker.open("access", Set.of(enabled), inputs -> {
                val current = inputs.get(enabled).value();
                values.add(current);
                if (Value.FALSE.equals(current)) {
                    withdrawn.complete(null);
                    return Set.of(enabled);
                }
                return Set.of(enabled, remote);
            })) {
                try {
                    entered.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                    repository.publish(flag, Value.FALSE);
                    withdrawn.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                } finally {
                    release.complete(null);
                }
                closed.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(values).containsExactly(Value.TRUE, Value.FALSE);
            }
        }
    }

    @Test
    @DisplayName("an unsuccessful timestamp on a live PIP terminates the owning observer")
    void whenAnObservationCannotBeTimestampedThenTheManagedReaderReceivesItsFailure() throws Exception {
        val           failed      = new DateTimeException("No observation time available.");
        val           clockFailed = new AtomicBoolean();
        InstantSource clock       = () -> {
                                      if (clockFailed.get()) {
                                          throw failed;
                                      }
                                      return Instant.EPOCH;
                                  };
        val           source      = new LatestSlotStream<Value>();
        val           first       = new CompletableFuture<Value>();
        val           failure     = new CompletableFuture<RuntimeException>();
        val           key         = key();
        try (val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, null, clock)) {
            broker.load(new Palette(() -> source));
            try (val reader = broker.open("observer", Set.of(key), new AttributeSubscriptionObserver(inputs -> {
                first.complete(inputs.get(key).value());
                return Set.of(key);
            }, failure::complete))) {
                source.put(Value.TRUE);
                assertThat(first.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(Value.TRUE);
                clockFailed.set(true);
                source.put(Value.FALSE);
                assertThat(failure.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isSameAs(failed);
            }
        }
    }

    private enum Activation {
        OPEN,
        DISCOVERY,
        PROMOTION
    }

    @EnumSource(Activation.class)
    @ParameterizedTest(name = "registration through {0}")
    @DisplayName("two brokers can enroll independently while their finders publish repository status")
    void whenTwoFindersReportAvailabilityDuringEnrollmentThenBothBrokersCanReceiveIt(Activation activation)
            throws Exception {
        val status      = key("ward.available");
        val physical    = key();
        val bothEntered = new CountDownLatch(2);
        val tasks       = new ArrayList<CompletableFuture<?>>();
        try (val repository = new InMemoryAttributeRepository();
                val left = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository);
                val right = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository)) {
            for (val broker : List.of(left, right)) {
                val flag      = key(broker == left ? "ward.left" : "ward.right");
                val observed  = new CompletableFuture<Void>();
                val delivered = new CompletableFuture<Void>();
                broker.open("availability", Set.of(status), inputs -> {
                    if (Value.TRUE.equals(inputs.get(status).value())) {
                        observed.complete(null);
                    }
                    return Set.of(status);
                });
                val      pip  = new Palette(() -> {
                                  bothEntered.countDown();
                                  try {
                                      if (!bothEntered.await(LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                                          throw new IllegalStateException("The second finder did not enter.");
                                      }
                                  } catch (InterruptedException interrupted) {
                                      Thread.currentThread().interrupt();
                                      throw new IllegalStateException(interrupted);
                                  }
                                  repository.publish(RepositoryKey.fromInvocation(status.invocation()), Value.TRUE);
                                  val stream = new LatestSlotStream<Value>();
                                  stream.put(Value.TRUE);
                                  return stream;
                              });
                Runnable open = () -> broker.open("color", Set.of(physical), inputs -> {
                                  if (Value.TRUE.equals(inputs.get(physical).value())) {
                                      delivered.complete(null);
                                  }
                                  return Set.of(physical);
                              });
                Runnable activate;
                switch (activation) {
                case OPEN      -> {
                    broker.load(pip);
                    activate = open;
                }
                case PROMOTION -> {
                    open.run();
                    activate = () -> broker.load(pip);
                }
                case DISCOVERY -> {
                    broker.load(pip);
                    broker.open("discover", Set.of(flag), inputs -> {
                        val observedPhysical = inputs.get(physical);
                        if (observedPhysical != null && Value.TRUE.equals(observedPhysical.value())) {
                            delivered.complete(null);
                        }
                        if (Value.TRUE.equals(inputs.get(flag).value())) {
                            return Set.of(flag, physical);
                        }
                        return Set.of(flag);
                    });
                    activate = () -> repository.publish(RepositoryKey.fromInvocation(flag.invocation()), Value.TRUE);
                }
                default        -> throw new AssertionError(activation);
                }
                tasks.add(observed);
                tasks.add(delivered);
                tasks.add(CompletableFuture.runAsync(activate, Thread::startVirtualThread));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new)).get(LIMIT.toMillis(),
                    TimeUnit.MILLISECONDS);
            assertThat(bothEntered.getCount()).isZero();
        }
    }

    @Test
    @DisplayName("a nested synchronous enrollment can use independent keys and rejects waiting for itself")
    void whenAFinderOpensAnotherObservationThenOnlyAWaitOnTheUnfinishedSourceIsRejected() throws Exception {
        val remote      = key();
        val independent = key("ward.available");
        val nested      = new CompletableFuture<Value>();
        val circular    = new CompletableFuture<Void>();
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            broker.load(new Palette(() -> {
                broker.open("independent", Set.of(independent), inputs -> {
                    nested.complete(inputs.get(independent).value());
                    return Set.of(independent);
                });
                val circularKeys = Set.of(remote);
                assertThatThrownBy(() -> broker.open("circular", circularKeys, inputs -> circularKeys))
                        .isInstanceOf(IllegalStateException.class);
                circular.complete(null);
                return new LatestSlotStream<>();
            }));
            try (val reader = broker.open("outer", Set.of(remote), inputs -> Set.of(remote))) {
                circular.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(nested.get(LIMIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(Value.UNDEFINED);
            }
        }
    }
}
