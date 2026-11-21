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

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.val;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(20)
@DisplayName("Attribute collection and updates of previously evaluated inputs")
class BrokerReadinessTests {

    @Test
    @DisplayName("concurrent first deliveries with delayed notifications produce one complete round")
    void whenFirstDeliveriesRaceThenDelayedNotificationsDoNotRepeatTheCapturedRound() throws Exception {
        val keys         = pendingKeys(20);
        val dependencies = Set.copyOf(keys);
        val rounds       = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val broker = broker(null); val subscription = broker.open("concurrent-initial", dependencies, inputs -> {
            rounds.add(inputs);
            return dependencies;
        })) {
            val tasks     = new ArrayList<FutureTask<Void>>();
            val producers = new ArrayList<Thread>();
            for (val key : keys) {
                val task = new FutureTask<Void>(() -> {
                    publish(subscription, key, Value.TRUE);
                    return null;
                });
                tasks.add(task);
                producers.add(Thread.ofVirtual().unstarted(task));
            }
            val field       = privateLookupIn(PolicyInformationPointAttributeBroker.class, lookup())
                    .findVarHandle(PolicyInformationPointAttributeBroker.class, "lock", ReentrantLock.class);
            val routingLock = (ReentrantLock) field.get(broker);
            routingLock.lock();
            try {
                producers.forEach(Thread::start);
                await().atMost(Duration.ofSeconds(5))
                        .until(() -> producers.stream().allMatch(routingLock::hasQueuedThread));
            } finally {
                routingLock.unlock();
            }
            for (val task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
            // A producer can join an existing drain and return before the callback runs.
            awaitRounds(rounds, 1);
            val changed = keys.getFirst();
            publish(subscription, changed, Value.FALSE);
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> Value.FALSE.equals(rounds.getLast().get(changed).value()));
            assertThat(rounds).satisfiesExactly(
                    first -> assertThat(first).containsOnlyKeys(keys)
                            .allSatisfy((key, observation) -> assertThat(observation.value()).isEqualTo(Value.TRUE)),
                    next -> assertThat(next).containsOnlyKeys(keys));
        }
    }

    @ValueSource(ints = { 1, 20 })
    @ParameterizedTest(name = "collect {0} initial attributes")
    @DisplayName("initial deliveries form one complete input before evaluation")
    void whenInitialAttributesArriveThenEvaluationWaitsForTheWholeSet(int count) {
        val keys         = pendingKeys(count);
        val dependencies = Set.copyOf(keys);
        val rounds       = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val broker = broker(null); val subscription = broker.open("initial", dependencies, inputs -> {
            rounds.add(inputs);
            return dependencies;
        })) {
            for (val key : keys) {
                assertThat(rounds).isEmpty();
                publish(subscription, key, Value.TRUE);
            }
            awaitRounds(rounds, 1);
            assertThat(rounds).singleElement().satisfies(inputs -> assertThat(inputs).containsOnlyKeys(keys));
        }
    }

    @Test
    @DisplayName("updates of collected first values do not bypass collection for the same subscription")
    void whenCollectedFirstValuesChangeThenTheyRemainNewUntilDeliveredToEvaluation() {
        val a        = key("work.a", List.of());
        val pending  = pendingKeys(20);
        val expanded = expanded(a, pending);
        val rounds   = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val repository = new InMemoryAttributeRepository(); val broker = broker(repository)) {
            repository.publish(List.of(binding(a, Value.TRUE)));
            try (val subscription = broker.open("collecting", Set.of(a), inputs -> {
                rounds.add(inputs);
                return expanded;
            })) {
                awaitRounds(rounds, 1);
                publish(subscription, pending.getFirst(), Value.TRUE);
                publish(subscription, pending.getFirst(), Value.FALSE);
                assertThat(rounds).singleElement().satisfies(inputs -> assertThat(inputs).containsOnlyKeys(a));
                for (val key : pending.subList(1, pending.size())) {
                    assertThat(rounds).hasSize(1);
                    publish(subscription, key, Value.TRUE);
                }
                awaitRounds(rounds, 2);
                assertThat(rounds.getLast()).satisfies(inputs -> {
                    assertThat(inputs).containsOnlyKeys(expanded);
                    assertThat(inputs.get(pending.getFirst()).value()).isEqualTo(Value.FALSE);
                });
            }
        }
    }

    @Test
    @DisplayName("known inputs are tracked separately for consumers sharing the same sources")
    void whenAKnownInputChangesThenAWarmConsumerRunsWhileAColdConsumerStillCollects() {
        val a          = key("work.a", List.of());
        val pending    = pendingKeys(2);
        val expanded   = expanded(a, pending);
        val warmRounds = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        val coldRounds = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val repository = new InMemoryAttributeRepository(); val broker = broker(repository)) {
            repository.publish(List.of(binding(a, Value.TRUE)));
            try (val warm = broker.open("warm", Set.of(a), inputs -> {
                warmRounds.add(inputs);
                return expanded;
            }); val cold = broker.open("cold", expanded, inputs -> {
                coldRounds.add(inputs);
                return expanded;
            })) {
                awaitRounds(warmRounds, 1);
                repository.publish(List.of(binding(a, Value.FALSE)));
                awaitRounds(warmRounds, 2);
                assertThat(warmRounds.getLast()).satisfies(inputs -> {
                    assertThat(inputs).containsOnlyKeys(a);
                    assertThat(inputs.get(a).value()).isEqualTo(Value.FALSE);
                });
                assertThat(coldRounds).isEmpty();
                publish(warm, pending.getFirst(), Value.TRUE);
                assertThat(warmRounds).hasSize(2);
                assertThat(coldRounds).isEmpty();
                publish(warm, pending.getLast(), Value.TRUE);
                awaitRounds(warmRounds, 3);
                awaitRounds(coldRounds, 1);
                assertThat(warmRounds.getLast()).satisfies(inputs -> assertThat(inputs).containsOnlyKeys(expanded));
                assertThat(coldRounds).singleElement()
                        .satisfies(inputs -> assertThat(inputs).containsOnlyKeys(expanded));
            }
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "known input supplied by PIP: {0}")
    @DisplayName("a renewal is visible even when its value and observation timestamp are equal")
    void whenAKnownBindingIsRenewedThenPendingFirstValuesCannotHideTheRenewal(boolean pipSource) {
        SubscriptionKey a;
        if (pipSource) {
            a = key("pending.value", List.of(Value.of("known")));
        } else {
            a = key("work.a", List.of());
        }
        val expanded = expanded(a, pendingKeys(1));
        val rounds   = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val repository = new InMemoryAttributeRepository(); val broker = broker(repository)) {
            if (!pipSource) {
                repository.publish(List.of(binding(a, Value.TRUE)));
            }
            try (val subscription = broker.open("renewal", Set.of(a), inputs -> {
                rounds.add(inputs);
                return expanded;
            })) {
                if (pipSource) {
                    publish(subscription, a, Value.TRUE);
                }
                awaitRounds(rounds, 1);
                if (pipSource) {
                    publish(subscription, a, Value.TRUE);
                } else {
                    repository.publish(List.of(binding(a, Value.TRUE)));
                }
                awaitRounds(rounds, 2);
                assertThat(rounds.getFirst().get(a)).isEqualTo(rounds.getLast().get(a))
                        .isNotSameAs(rounds.getLast().get(a));
            }
        }
    }

    @Test
    @DisplayName("queued known changes survive discovering new pending inputs and remain source-consistent")
    void whenATransferOverlapsDiscoveryThenParallelConsumersSeeCommittedViewsAndTheQueuedRoundRuns() throws Exception {
        val a        = key("work.a", List.of());
        val c        = key("work.c", List.of());
        val initial  = Set.of(a, c);
        val expanded = new HashSet<>(expanded(a, pendingKeys(20)));
        expanded.add(c);
        val entered    = new CompletableFuture<Void>();
        val resume     = new CompletableFuture<Void>();
        val slowRounds = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        val fastRounds = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val repository = new InMemoryAttributeRepository();
                val broker = broker(repository);
                val worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
            repository.publish(List.of(binding(a, Value.TRUE), binding(c, Value.FALSE)));
            val opening = worker.submit(() -> broker.open("slow", initial, inputs -> {
                slowRounds.add(inputs);
                if (slowRounds.size() == 1) {
                    entered.complete(null);
                    resume.orTimeout(5, TimeUnit.SECONDS).join();
                }
                return expanded;
            }));
            try {
                entered.get(5, TimeUnit.SECONDS);
                try (val fast = broker.open("fast", initial, inputs -> {
                    fastRounds.add(inputs);
                    return expanded;
                })) {
                    awaitRounds(fastRounds, 1);
                    repository.publish(List.of(binding(a, Value.FALSE), binding(c, Value.TRUE)));
                    awaitRounds(fastRounds, 2);
                    assertThat(slowRounds).singleElement()
                            .satisfies(inputs -> assertPair(inputs, a, c, Value.TRUE, Value.FALSE));
                    resume.complete(null);
                    try (val slow = opening.get(5, TimeUnit.SECONDS)) {
                        awaitRounds(slowRounds, 2);
                        assertThat(slowRounds.getLast())
                                .satisfies(inputs -> assertPair(inputs, a, c, Value.FALSE, Value.TRUE));
                        assertThat(fastRounds.getLast())
                                .satisfies(inputs -> assertPair(inputs, a, c, Value.FALSE, Value.TRUE));
                    }
                }
            } finally {
                resume.complete(null);
            }
        }
    }

    @Test
    @DisplayName("a changed dependency removed by the running evaluation does not schedule another round")
    void whenAChangedKeyIsDroppedThenOnlyRetainedInputsCanRequestAnotherRound() throws Exception {
        val a        = key("work.a", List.of());
        val c        = key("work.c", List.of());
        val expanded = expanded(a, pendingKeys(1));
        val entered  = new CompletableFuture<Void>();
        val resume   = new CompletableFuture<Void>();
        val rounds   = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        try (val repository = new InMemoryAttributeRepository();
                val broker = broker(repository);
                val worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
            repository.publish(List.of(binding(a, Value.TRUE), binding(c, Value.FALSE)));
            val opening = worker.submit(() -> broker.open("dropping", Set.of(a, c), inputs -> {
                rounds.add(inputs);
                entered.complete(null);
                resume.orTimeout(5, TimeUnit.SECONDS).join();
                return expanded;
            }));
            try {
                entered.get(5, TimeUnit.SECONDS);
                repository.publish(List.of(binding(c, Value.TRUE)));
            } finally {
                resume.complete(null);
            }
            try (val subscription = opening.get(5, TimeUnit.SECONDS)) {
                assertThat(rounds).hasSize(1);
            }
        }
    }

    private static void awaitRounds(List<?> rounds, int expected) {
        await().pollDelay(Duration.ZERO).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(rounds).hasSize(expected));
    }

    private static void assertPair(Map<SubscriptionKey, AttributeSnapshot> inputs, SubscriptionKey a, SubscriptionKey c,
            Value expectedA, Value expectedC) {
        assertThat(inputs).containsOnlyKeys(a, c);
        assertThat(inputs.get(a).value()).isEqualTo(expectedA);
        assertThat(inputs.get(c).value()).isEqualTo(expectedC);
    }

    private static PolicyInformationPointAttributeBroker broker(InMemoryAttributeRepository repository) {
        val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository,
                InstantSource.fixed(Instant.EPOCH));
        broker.load(new PendingPip());
        return broker;
    }

    private static BoundValue binding(SubscriptionKey key, Value value) {
        return new BoundValue(RepositoryKey.fromInvocation(key.invocation()), value, null);
    }

    private static Set<SubscriptionKey> expanded(SubscriptionKey a, List<SubscriptionKey> pending) {
        val result = new HashSet<>(pending);
        result.add(a);
        return Set.copyOf(result);
    }

    private static List<SubscriptionKey> pendingKeys(int count) {
        val keys = new ArrayList<SubscriptionKey>();
        for (int i = 0; i < count; i++) {
            keys.add(key("pending.value", List.of(Value.of("attribute" + i))));
        }
        return List.copyOf(keys);
    }

    private static SubscriptionKey key(String name, List<Value> arguments) {
        val context = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new SubscriptionKey(new AttributeFinderInvocation("readiness", "default", name, arguments,
                Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMillis(100), 0L, false, context), false);
    }

    private static void publish(AttributeBroker.Subscription subscription, SubscriptionKey key, Value value) {
        // Synchronous mailbox delivery isolates broker scheduling from the asynchronous PIP pump.
        val consumer = (PolicyInformationPointAttributeBroker.BrokerSubscription) subscription;
        val active   = (ActivePolicyInformationPointInvocation) consumer.routingTable.get(key);
        active.publishImmediate(value);
    }

    @PolicyInformationPoint(name = "pending")
    static class PendingPip {
        @EnvironmentAttribute
        public Stream<Value> value(TextValue key) {
            return new LatestSlotStream<>();
        }
    }
}
