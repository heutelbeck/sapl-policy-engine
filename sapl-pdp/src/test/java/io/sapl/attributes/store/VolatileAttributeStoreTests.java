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
package io.sapl.attributes.store;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VolatileAttributeStore")
class VolatileAttributeStoreTests {

    private VolatileAttributeStore store;

    @BeforeEach
    void setUp() {
        store = new VolatileAttributeStore();
    }

    @AfterEach
    void tearDown() {
        store.close();
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

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
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
            store.publish(repoKey("env.x"), Value.of("hello"));

            val recorder = new Recorder(Set.of(key));
            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
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

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(recorder.snapshots).hasSize(1);

                store.publish(repoKey("env.x"), Value.of("v1"));

                assertThat(recorder.snapshots).hasSize(2);
                assertThat(valueFor(key, recorder, 1)).isEqualTo(Value.of("v1"));
            }
        }

        @Test
        @DisplayName("then re-publish fires again with the updated value")
        void thenRePublishFiresAgainWithTheUpdatedValue() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                store.publish(repoKey("env.x"), Value.of("v1"));
                store.publish(repoKey("env.x"), Value.of("v2"));

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
            store.publish(repoKey("env.x"), Value.of("v1"));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("v1"));

                store.remove(repoKey("env.x"));

                assertThat(recorder.snapshots).hasSize(2);
                assertThat(valueFor(key, recorder, 1)).isEqualTo(Value.UNDEFINED);
            }
        }

        @Test
        @DisplayName("then removing a key with no entry is a no-op")
        void thenRemovingAKeyWithNoEntryIsANoop() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                val before = recorder.snapshots.size();
                store.remove(repoKey("env.x"));
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

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                store.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
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

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                store.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
                store.publish(repoKey("env.x"), Value.of("v2"));

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

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                store.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMinutes(10));
                store.remove(repoKey("env.x"));

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

            try (val sx = store.open("sx", Set.of(keyX), rcdrX.asCallback());
                    val sy = store.open("sy", Set.of(keyY), rcdrY.asCallback())) {
                val sizeXBefore = rcdrX.snapshots.size();
                val sizeYBefore = rcdrY.snapshots.size();

                store.publish(repoKey("env.x"), Value.of("only-x"));

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
            val sub      = store.open("s1", Set.of(key), recorder.asCallback());
            val before   = recorder.snapshots.size();

            sub.close();
            store.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.snapshots).hasSize(before);
        }
    }

    @Nested
    @DisplayName("when the store is closed")
    class WhenTheStoreIsClosed {

        @Test
        @DisplayName("then subsequent publishes are silently ignored")
        void thenSubsequentPublishesAreSilentlyIgnored() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            store.open("s1", Set.of(key), recorder.asCallback());
            val before = recorder.snapshots.size();

            store.close();
            store.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.snapshots).hasSize(before);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            store.close();
            store.close();
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
            store.publish(repoKey("env.b"), Value.of("b-value"));

            try (val sub = store.open("s1", Set.of(keyA), snapshot -> {
                snapshots.add(snapshot);
                val current = deps.next;
                deps.next = Set.of(keyA);
                return current;
            })) {
                deps.next = Set.of(keyA, keyB);
                store.publish(repoKey("env.a"), Value.of("a-value"));

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

            try (val sub = store.open("s1", Set.of(keyA, keyB), snapshot -> {
                snapshots.add(snapshot);
                return Set.of(keyA);
            })) {
                val sizeAfterDrop = snapshots.size();
                store.publish(repoKey("env.b"), Value.of("ignored"));

                Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
                assertThat(snapshots).hasSize(sizeAfterDrop);
            }
        }
    }

    @Nested
    @DisplayName("when head=true is used")
    class WhenHeadTrueIsUsed {

        @Test
        @DisplayName("then the captured first-observed value persists across subsequent publishes")
        void thenTheCapturedFirstObservedValuePersistsAcrossSubsequentPublishes() {
            val key      = envHeadKey("env.x");
            val recorder = new Recorder(Set.of(key));
            store.publish(repoKey("env.x"), Value.of("initial"));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("initial"));

                store.publish(repoKey("env.x"), Value.of("updated"));

                // head=true keys do not re-fire on publish; consumer is unchanged.
                Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
                assertThat(recorder.snapshots).hasSize(1);
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("initial"));
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
                    .isThrownBy(() -> store.open("   ", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("then a duplicate subscriptionId is rejected")
        void thenADuplicateSubscriptionIdIsRejected() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            store.open("s1", Set.of(key), recorder.asCallback());

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store.open("s1", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("already open");
        }

        @Test
        @DisplayName("then empty initialDependencies is rejected")
        void thenEmptyInitialDependenciesIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store.open("s1", Set.of(), m -> Set.of(envKey("env.x"))))
                    .withMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("then a zero TTL is rejected")
        void thenAZeroTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store.publish(repoKey("env.x"), Value.of("v"), Duration.ZERO))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a negative TTL is rejected")
        void thenANegativeTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> store.publish(repoKey("env.x"), Value.of("v"), Duration.ofMillis(-1)))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a callback returning empty deps causes an IllegalStateException")
        void thenACallbackReturningEmptyDepsCausesAnIllegalStateException() {
            val key = envKey("env.x");
            assertThatThrownBy(() -> store.open("s1", Set.of(key), snapshot -> Set.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty/null dependencies");
        }
    }
}
