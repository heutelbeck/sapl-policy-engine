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
package io.sapl.attributes.broker.repository;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryAttributeRepository")
class InMemoryAttributeRepositoryTests {

    private InMemoryAttributeRepository broker;

    @BeforeEach
    void setUp() {
        broker = new InMemoryAttributeRepository();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    private static AttributeFinderInvocation envInvocation(String fqn) {
        return envInvocation(fqn, List.of());
    }

    private static AttributeFinderInvocation envInvocation(String fqn, List<Value> arguments) {
        return new AttributeFinderInvocation("default", fqn, arguments, Duration.ofSeconds(1), Duration.ofMillis(100),
                Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static SubscriptionKey envKey(String fqn) {
        return new SubscriptionKey(envInvocation(fqn), false);
    }

    private static SubscriptionKey envHeadKey(String fqn) {
        return new SubscriptionKey(envInvocation(fqn), true);
    }

    private static RepositoryKey repoKey(String fqn) {
        return new RepositoryKey(null, fqn, List.of());
    }

    private static final class Recorder {
        final List<Map<SubscriptionKey, AttributeSnapshot>> snapshots = new CopyOnWriteArrayList<>();
        private final Set<SubscriptionKey>                  deps;

        Recorder(Set<SubscriptionKey> deps) {
            this.deps = deps;
        }

        Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> asCallback() {
            return snapshot -> {
                snapshots.add(snapshot);
                return deps;
            };
        }
    }

    private static Value valueFor(SubscriptionKey key, Recorder recorder, int snapshotIndex) {
        return recorder.snapshots.get(snapshotIndex).get(key).value();
    }

    @Nested
    @DisplayName("when no entry exists")
    class WhenNoEntryExists {

        @Test
        @DisplayName("then open fires synchronously with UNDEFINED")
        void thenOpenFiresSynchronouslyWithUndefined() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(recorder.snapshots).hasSize(1);
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when a value is published before open")
    class WhenValuePublishedBeforeOpen {

        @Test
        @DisplayName("then open fires synchronously with the published value")
        void thenOpenFiresSynchronouslyWithThePublishedValue() {
            val key = envKey("env.x");
            broker.publish(repoKey("env.x"), Value.of("hello"));

            val recorder = new Recorder(Set.of(key));
            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(recorder.snapshots).hasSize(1);
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("hello"));
            }
        }
    }

    @Nested
    @DisplayName("when publish happens after open")
    class WhenPublishHappensAfterOpen {

        @Test
        @DisplayName("then the consumer callback fires with the new value")
        void thenTheConsumerCallbackFiresWithTheNewValue() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(recorder.snapshots).hasSize(1);

                broker.publish(repoKey("env.x"), Value.of("v1"));

                assertThat(recorder.snapshots).hasSize(2);
                assertThat(valueFor(key, recorder, 1)).isEqualTo(Value.of("v1"));
            }
        }

        @Test
        @DisplayName("then re-publish fires again with the updated value")
        void thenRePublishFiresAgainWithTheUpdatedValue() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                broker.publish(repoKey("env.x"), Value.of("v1"));
                broker.publish(repoKey("env.x"), Value.of("v2"));

                assertThat(recorder.snapshots).hasSize(3);
                assertThat(valueFor(key, recorder, 2)).isEqualTo(Value.of("v2"));
            }
        }
    }

    @Nested
    @DisplayName("when an entry is removed")
    class WhenAnEntryIsRemoved {

        @Test
        @DisplayName("then the consumer callback fires with UNDEFINED")
        void thenTheConsumerCallbackFiresWithUndefined() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            broker.publish(repoKey("env.x"), Value.of("v1"));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("v1"));

                broker.remove(repoKey("env.x"));

                assertThat(recorder.snapshots).hasSize(2);
                assertThat(valueFor(key, recorder, 1)).isEqualTo(Value.UNDEFINED);
            }
        }

        @Test
        @DisplayName("then removing a key with no entry is a no-op")
        void thenRemovingAKeyWithNoEntryIsANoop() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                val before = recorder.snapshots.size();
                broker.remove(repoKey("env.x"));
                assertThat(recorder.snapshots).hasSize(before);
            }
        }
    }

    @Nested
    @DisplayName("when an entry has a TTL")
    class WhenAnEntryHasATtl {

        @Test
        @DisplayName("then the entry expires and the consumer sees UNDEFINED")
        void thenTheEntryExpiresAndTheConsumerSeesUndefined() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                broker.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(valueFor(key, recorder, recorder.snapshots.size() - 1))
                                .isEqualTo(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("then republish without TTL cancels the prior expiry")
        void thenRepublishWithoutTtlCancelsThePriorExpiry() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                broker.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
                broker.publish(repoKey("env.x"), Value.of("v2"));

                // After 500ms the no-TTL entry must still be present.
                Awaitility.await().pollDelay(Duration.ofMillis(500)).until(() -> true);
                assertThat(valueFor(key, recorder, recorder.snapshots.size() - 1)).isEqualTo(Value.of("v2"));
            }
        }

        @Test
        @DisplayName("then explicit remove cancels a pending expiry")
        void thenExplicitRemoveCancelsAPendingExpiry() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                broker.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMinutes(10));
                broker.remove(repoKey("env.x"));

                assertThat(valueFor(key, recorder, recorder.snapshots.size() - 1)).isEqualTo(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when multiple consumers track keys")
    class WhenMultipleConsumersTrackKeys {

        @Test
        @DisplayName("then publish fires every consumer whose deps include the key")
        void thenPublishFiresEveryConsumerWhoseDepsIncludeTheKey() {
            val keyX  = envKey("env.x");
            val keyY  = envKey("env.y");
            val rcdrX = new Recorder(Set.of(keyX));
            val rcdrY = new Recorder(Set.of(keyY));

            try (val sx = broker.open("sx", Set.of(keyX), rcdrX.asCallback());
                    val sy = broker.open("sy", Set.of(keyY), rcdrY.asCallback())) {
                val sizeXBefore = rcdrX.snapshots.size();
                val sizeYBefore = rcdrY.snapshots.size();

                broker.publish(repoKey("env.x"), Value.of("only-x"));

                assertThat(rcdrX.snapshots.size()).isGreaterThan(sizeXBefore);
                assertThat(rcdrY.snapshots).hasSize(sizeYBefore);
                assertThat(valueFor(keyX, rcdrX, rcdrX.snapshots.size() - 1)).isEqualTo(Value.of("only-x"));
            }
        }
    }

    @Nested
    @DisplayName("when a consumer subscription is closed")
    class WhenAConsumerSubscriptionIsClosed {

        @Test
        @DisplayName("then no further callbacks fire on subsequent publishes")
        void thenNoFurtherCallbacksFireOnSubsequentPublishes() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            val sub      = broker.open("s1", Set.of(key), recorder.asCallback());
            val before   = recorder.snapshots.size();

            sub.close();
            broker.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.snapshots).hasSize(before);
        }
    }

    @Nested
    @DisplayName("when the broker is closed")
    class WhenTheStoreIsClosed {

        @Test
        @DisplayName("then subsequent publishes are silently ignored")
        void thenSubsequentPublishesAreSilentlyIgnored() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            broker.open("s1", Set.of(key), recorder.asCallback());
            val before = recorder.snapshots.size();

            broker.close();
            broker.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.snapshots).hasSize(before);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            broker.close();
            broker.close();
        }
    }

    @Nested
    @DisplayName("when the consumer changes its dependencies")
    class WhenTheConsumerChangesItsDependencies {

        @Test
        @DisplayName("then added keys appear in the next snapshot with their current value")
        void thenAddedKeysAppearInTheNextSnapshotWithTheirCurrentValue() {
            val keyA      = envKey("env.a");
            val keyB      = envKey("env.b");
            val snapshots = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
            val deps      = new Object() {
                              Set<SubscriptionKey> next = Set.of(keyA);
                          };
            broker.publish(repoKey("env.b"), Value.of("b-value"));

            try (val sub = broker.open("s1", Set.of(keyA), snapshot -> {
                snapshots.add(snapshot);
                val current = deps.next;
                deps.next = Set.of(keyA);
                return current;
            })) {
                deps.next = Set.of(keyA, keyB);
                broker.publish(repoKey("env.a"), Value.of("a-value"));

                Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(
                        () -> assertThat(snapshots).anySatisfy(snap -> assertThat(snap).containsKey(keyB)));
                val latest = snapshots.get(snapshots.size() - 1);
                assertThat(latest.get(keyA).value()).isEqualTo(Value.of("a-value"));
                assertThat(latest.get(keyB).value()).isEqualTo(Value.of("b-value"));
            }
        }

        @Test
        @DisplayName("then publishes to dropped keys no longer trigger the callback")
        void thenPublishesToDroppedKeysNoLongerTriggerTheCallback() {
            val keyA      = envKey("env.a");
            val keyB      = envKey("env.b");
            val snapshots = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();

            try (val sub = broker.open("s1", Set.of(keyA, keyB), snapshot -> {
                snapshots.add(snapshot);
                return Set.of(keyA);
            })) {
                val sizeAfterDrop = snapshots.size();
                broker.publish(repoKey("env.b"), Value.of("ignored"));

                Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
                assertThat(snapshots).hasSize(sizeAfterDrop);
            }
        }
    }

    @Nested
    @DisplayName("when head=true is used")
    class WhenHeadTrueIsUsed {

        @Test
        @DisplayName("then the broker is head-agnostic: head=true keys deliver the latest value on every publish; "
                + "freeze-at-first-observation semantic lives in the eval-side HeadCache, not in the broker")
        void thenHeadTrueDeliversLatestValueLikeHeadFalse() {
            val key      = envHeadKey("env.x");
            val recorder = new Recorder(Set.of(key));
            broker.publish(repoKey("env.x"), Value.of("initial"));

            try (val sub = broker.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("initial"));

                broker.publish(repoKey("env.x"), Value.of("updated"));

                assertThat(recorder.snapshots).hasSize(2);
                assertThat(valueFor(key, recorder, 1)).isEqualTo(Value.of("updated"));
            }
        }
    }

    @Nested
    @DisplayName("when input is invalid")
    class WhenInputIsInvalid {

        @Test
        @DisplayName("then a blank subscriptionId is rejected")
        void thenABlankSubscriptionIdIsRejected() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> broker.open("   ", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("then a duplicate subscriptionId is rejected")
        void thenADuplicateSubscriptionIdIsRejected() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            broker.open("s1", Set.of(key), recorder.asCallback());

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> broker.open("s1", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("already open");
        }

        @Test
        @DisplayName("then empty initialDependencies is rejected")
        void thenEmptyInitialDependenciesIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> broker.open("s1", Set.of(), m -> Set.of(envKey("env.x"))))
                    .withMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("then a zero TTL is rejected")
        void thenAZeroTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> broker.publish(repoKey("env.x"), Value.of("v"), Duration.ZERO))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a negative TTL is rejected")
        void thenANegativeTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> broker.publish(repoKey("env.x"), Value.of("v"), Duration.ofMillis(-1)))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a callback returning empty deps causes an IllegalStateException")
        void thenACallbackReturningEmptyDepsCausesAnIllegalStateException() {
            val key = envKey("env.x");
            assertThatThrownBy(() -> broker.open("s1", Set.of(key), snapshot -> Set.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty/null dependencies");
        }
    }

    @Nested
    @DisplayName("dispatch coalescing")
    class DispatchCoalescing {

        @Test
        @DisplayName("rapid publishes from many threads do not serialize on a slow consumer callback; "
                + "fires coalesce and the publishers return well before the serial-fire deadline")
        void whenManyConcurrentPublishesAndSlowConsumerThenPublishersDoNotBlock() throws Exception {
            val key      = envKey("env.coalesce");
            val repoFqn  = repoKey("env.coalesce");
            val fires    = new AtomicInteger();
            val lastSeen = new AtomicReference<Value>();
            val callback = (Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>>) snapshot -> {
                             fires.incrementAndGet();
                             lastSeen.set(snapshot.get(key).value());
                             try {
                                 Thread.sleep(30);
                             } catch (InterruptedException e) {
                                 Thread.currentThread().interrupt();
                             }
                             return Set.of(key);
                         };

            try (val ignored = broker.open("coalesce", Set.of(key), callback)) {
                val publisherCount     = 8;
                val publishesPerThread = 50;
                val total              = publisherCount * publishesPerThread;
                val barrier            = new CountDownLatch(publisherCount);
                val done               = new CountDownLatch(publisherCount);
                val threads            = new ArrayList<Thread>(publisherCount);

                val startNanos = System.nanoTime();
                for (int t = 0; t < publisherCount; t++) {
                    val threadIndex = t;
                    val thread      = Thread.ofVirtual().start(() -> {
                                        barrier.countDown();
                                        try {
                                            barrier.await();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            return;
                                        }
                                        for (int i = 0; i < publishesPerThread; i++) {
                                            broker.publish(repoFqn, Value.of(threadIndex * publishesPerThread + i));
                                        }
                                        done.countDown();
                                    });
                    threads.add(thread);
                }
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
                val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

                // Without coalescing, 400 publishes that each blocked on a 30ms
                // onUpdate would serialize for ~12 seconds. With the coalescer,
                // non-driver publishers return immediately and only the
                // driver thread runs fires. Comfortably under that bound.
                assertThat(elapsedMillis).isLessThan(4_000);

                // After all publishes settle, the consumer must have observed
                // far fewer fires than publishes, with a final value reflecting
                // a real published value.
                Awaitility.await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(() -> assertThat(lastSeen.get()).isNotNull());
                assertThat(fires.get()).isLessThan(total / 2);
            }
        }
    }
}
