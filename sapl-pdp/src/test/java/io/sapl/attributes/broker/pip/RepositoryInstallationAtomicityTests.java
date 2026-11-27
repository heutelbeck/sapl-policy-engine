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

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Duration;
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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.NumberValue;
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
@DisplayName("Atomic capture during installation of a repository transaction")
class RepositoryInstallationAtomicityTests {

    @Test
    @DisplayName("an independent wake and a new reader cannot capture a partially installed transfer")
    void whenReadersArriveBetweenTwoProjectionUpdatesThenBothSeeTheCommittedTransfer() throws Exception {
        val barrier  = new ProjectionBarrier();
        val a        = key("work.a", List.of(barrier.readerArgument()));
        val c        = key("work.c", List.of(barrier.readerArgument()));
        val wake     = key("external.signal", List.of(Value.of("wake")));
        val pending  = key("external.signal", List.of(Value.of("pending")));
        val pair     = Set.of(a, c);
        val expanded = Set.of(a, c, wake, pending);
        val existing = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
        val joining  = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();

        try (val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository);
                val worker = Executors.newVirtualThreadPerTaskExecutor()) {
            broker.load(new ExternalPip());
            repository.publish(List.of(binding(a, Value.TRUE), binding(c, Value.FALSE)));
            try (val handle = broker.open("existing", Set.of(a, c, wake), inputs -> {
                existing.add(inputs);
                return expanded;
            })) {
                val subscription = (PolicyInformationPointAttributeBroker.BrokerSubscription) handle;
                val signal       = (ActivePolicyInformationPointInvocation) subscription.routingTable.get(wake);
                val projectionA  = subscription.routingTable.get(a);
                val projectionC  = subscription.routingTable.get(c);
                signal.publishImmediate(Value.FALSE);
                assertThat(existing).singleElement()
                        .satisfies(inputs -> assertPair(inputs, a, c, Value.TRUE, Value.FALSE));
                assertThat(subscription.routingTable.get(pending).snapshot()).isEmpty();

                val field       = privateLookupIn(PolicyInformationPointAttributeBroker.class, lookup())
                        .findVarHandle(PolicyInformationPointAttributeBroker.class, "lock", ReentrantLock.class);
                val routingLock = (ReentrantLock) field.get(broker);
                val notifyPip   = new FutureTask<Void>(() -> signal.publishImmediate(Value.TRUE), null);
                val openReader  = new FutureTask<AttributeBroker.Subscription>(
                        () -> broker.open("joining", pair, inputs -> {
                                            joining.add(inputs);
                                            return pair;
                                        }));
                val notifier    = Thread.ofVirtual().unstarted(notifyPip);
                val opener      = Thread.ofVirtual().unstarted(openReader);
                val transfer    = worker.submit(() -> {
                                    barrier.armForCurrentThread();
                                    repository.publish(List.of(binding(a, Value.FALSE), binding(c, Value.TRUE)));
                                });
                try {
                    barrier.halfway.get(5, TimeUnit.SECONDS);
                    // The barrier publishes the first write and holds the second. Both committed pairs have unequal
                    // values.
                    assertThat(projectionA.snapshot().orElseThrow().value())
                            .isEqualTo(projectionC.snapshot().orElseThrow().value());
                    notifier.start();
                    opener.start();
                    await().atMost(Duration.ofSeconds(5))
                            .until(() -> (routingLock.hasQueuedThread(notifier) || notifyPip.isDone())
                                    && (routingLock.hasQueuedThread(opener) || openReader.isDone()));
                    assertThat(existing).singleElement()
                            .satisfies(inputs -> assertPair(inputs, a, c, Value.TRUE, Value.FALSE));
                    assertThat(joining).isEmpty();
                    assertThat(routingLock.hasQueuedThread(notifier)).isTrue();
                    assertThat(routingLock.hasQueuedThread(opener)).isTrue();
                } finally {
                    barrier.resume.complete(null);
                }
                transfer.get(5, TimeUnit.SECONDS);
                notifyPip.get(5, TimeUnit.SECONDS);
                try (val joined = openReader.get(5, TimeUnit.SECONDS)) {
                    assertThat(joining).isNotEmpty()
                            .allSatisfy(inputs -> assertPair(inputs, a, c, Value.FALSE, Value.TRUE));
                    assertThat(existing).allSatisfy(inputs -> {
                        assertThat(inputs).containsOnlyKeys(a, c, wake);
                        assertThat(List.of(inputs.get(a).value(), inputs.get(c).value()))
                                .isIn(List.of(Value.TRUE, Value.FALSE), List.of(Value.FALSE, Value.TRUE));
                    }).anySatisfy(inputs -> {
                        assertThat(inputs.get(wake).value()).isEqualTo(Value.TRUE);
                        assertPair(inputs, a, c, Value.FALSE, Value.TRUE);
                    });
                }
            } finally {
                barrier.resume.complete(null);
            }
        }
    }

    private static void assertPair(Map<SubscriptionKey, AttributeSnapshot> inputs, SubscriptionKey a, SubscriptionKey c,
            Value expectedA, Value expectedC) {
        assertThat(inputs.get(a).value()).isEqualTo(expectedA);
        assertThat(inputs.get(c).value()).isEqualTo(expectedC);
    }

    private static BoundValue binding(SubscriptionKey key, Value value) {
        // Publishers use ordinary numeric arguments equal to the reader arguments.
        val invocation = key.invocation();
        return new BoundValue(
                new RepositoryKey(null, invocation.attributeName(), List.of(Value.of(1)), invocation.pdpId()), value,
                null);
    }

    private static SubscriptionKey key(String name, List<Value> arguments) {
        val context = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new SubscriptionKey(new AttributeFinderInvocation("installation", "default", name, arguments,
                Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMillis(100), 0L, false, context), false);
    }

    private static final class ProjectionBarrier {
        final CompletableFuture<Void> halfway = new CompletableFuture<>();
        final CompletableFuture<Void> resume  = new CompletableFuture<>();
        private volatile Thread       publisher;
        private LookupNumber          firstLookup;

        NumberValue readerArgument() {
            return new NumberValue(new LookupNumber(this));
        }

        void armForCurrentThread() {
            publisher = Thread.currentThread();
        }

        void onKeyHash(LookupNumber number) {
            // Only the transfer thread's lookup of the second distinct reader key pauses installation.
            if (publisher != Thread.currentThread() || halfway.isDone()) {
                return;
            }
            if (firstLookup == null) {
                firstLookup = number;
            } else if (firstLookup != number) {
                halfway.complete(null);
                resume.orTimeout(10, TimeUnit.SECONDS).join();
            }
        }
    }

    private static final class LookupNumber extends BigDecimal {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient ProjectionBarrier barrier;

        LookupNumber(ProjectionBarrier barrier) {
            super(1);
            this.barrier = barrier;
        }

        @Override
        public int signum() {
            // NumberValue uses signum when hashing a numeric repository key. Its numeric result stays unchanged.
            barrier.onKeyHash(this);
            return super.signum();
        }
    }

    @PolicyInformationPoint(name = "external")
    static class ExternalPip {
        @EnvironmentAttribute
        public Stream<Value> signal(TextValue key) {
            return new LatestSlotStream<>();
        }
    }
}
