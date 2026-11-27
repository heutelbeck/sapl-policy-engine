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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import lombok.val;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(20)
@DisplayName("Broker shutdown during dependency discovery")
class BrokerShutdownTests {

    @ValueSource(longs = { 0L, 1000L })
    @ParameterizedTest(name = "shutdown while discovering B with {0} ms source grace")
    @DisplayName("a returning evaluation cannot open a source after shutdown has claimed the active sources")
    void whenShutdownOverlapsDependencyDiscoveryThenEverySourceIsReleased(long graceMillis) throws Exception {
        val a       = key("shutdown.a");
        val b       = key("shutdown.b");
        val entered = new CompletableFuture<PausedEvaluation>();
        val resume  = new CompletableFuture<Void>();

        try (val pip = new ShutdownPip();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ofMillis(graceMillis), null)) {
            broker.load(pip);
            val closing = new FutureTask<Void>(() -> {
                            broker.close();
                            return null;
                        });
            val closer  = Thread.ofVirtual().unstarted(closing);

            try (val subscription = broker.open("discovering-b", Set.of(a), inputs -> {
                entered.complete(new PausedEvaluation(Thread.currentThread(), inputs));
                resume.orTimeout(10, TimeUnit.SECONDS).join();
                return Set.of(a, b);
            })) {
                pip.aStream.get().put(Value.TRUE);
                val evaluation = entered.get(5, TimeUnit.SECONDS);
                assertThat(evaluation.inputs()).containsOnlyKeys(a);
                assertThat(evaluation.inputs().get(a).value()).isEqualTo(Value.TRUE);
                assertThat(pip.aOpened).hasValue(1);
                assertThat(pip.bOpened).hasValue(0);

                val routingLock = routingLock(broker);
                assertThat(routingLock.isFair()).isTrue();
                routingLock.lock();
                try {
                    // Queue shutdown before the returning evaluation; the fair lock fixes the order.
                    closer.start();
                    await().atMost(Duration.ofSeconds(5)).until(() -> routingLock.hasQueuedThread(closer));
                    resume.complete(null);
                    await().atMost(Duration.ofSeconds(5)).until(() -> routingLock.hasQueuedThread(evaluation.thread()));
                } finally {
                    routingLock.unlock();
                    resume.complete(null);
                }

                closing.get(5, TimeUnit.SECONDS);
                evaluation.thread().join(5000);
                assertThat(evaluation.thread().isAlive()).isFalse();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(
                        () -> assertThat(pip.aClosed).as("the first source must be closed by shutdown").hasValue(1));
                assertThat(pip.bClosed).as("shutdown must release every B source it allowed to open")
                        .hasValue(pip.bOpened.get());
                assertThat(pip.bOpened).as("a returning evaluation must not open B after shutdown").hasValue(0);
            } finally {
                resume.complete(null);
                closer.join(5000);
                // A second close also reclaims sources missed by the first shutdown sweep.
                broker.close();
            }
        }
    }

    private static SubscriptionKey key(String name) {
        val context    = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val invocation = new AttributeFinderInvocation("test-pdp", "default", name, List.of(), Duration.ofSeconds(30),
                Duration.ofSeconds(30), Duration.ofMillis(100), 0L, false, context);
        return new SubscriptionKey(invocation, false);
    }

    private static ReentrantLock routingLock(PolicyInformationPointAttributeBroker broker)
            throws ReflectiveOperationException {
        val field = privateLookupIn(PolicyInformationPointAttributeBroker.class, lookup())
                .findVarHandle(PolicyInformationPointAttributeBroker.class, "lock", ReentrantLock.class);
        return (ReentrantLock) field.get(broker);
    }

    private record PausedEvaluation(Thread thread, Map<SubscriptionKey, AttributeSnapshot> inputs) {}

    @PolicyInformationPoint(name = "shutdown")
    static class ShutdownPip implements AutoCloseable {

        private final AtomicInteger                            aOpened = new AtomicInteger();
        private final AtomicInteger                            aClosed = new AtomicInteger();
        private final AtomicInteger                            bOpened = new AtomicInteger();
        private final AtomicInteger                            bClosed = new AtomicInteger();
        private final AtomicReference<LatestSlotStream<Value>> aStream = new AtomicReference<>();
        private final List<LatestSlotStream<Value>>            streams = new CopyOnWriteArrayList<>();

        @EnvironmentAttribute
        public Stream<Value> a() {
            val stream = track(aOpened, aClosed);
            aStream.set(stream);
            return stream;
        }

        @EnvironmentAttribute
        public Stream<Value> b() {
            return track(bOpened, bClosed);
        }

        @Override
        public void close() {
            streams.forEach(LatestSlotStream::close);
        }

        private LatestSlotStream<Value> track(AtomicInteger opened, AtomicInteger closed) {
            val stream = new LatestSlotStream<Value>();
            stream.onClose(closed::incrementAndGet);
            streams.add(stream);
            opened.incrementAndGet();
            return stream;
        }
    }
}
