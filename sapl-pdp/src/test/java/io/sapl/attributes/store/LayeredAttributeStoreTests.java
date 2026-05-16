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

@DisplayName("LayeredAttributeStore")
class LayeredAttributeStoreTests {

    private VolatileAttributeStore primary;
    private VolatileAttributeStore fallback;
    private LayeredAttributeStore  layered;

    @BeforeEach
    void setUp() {
        primary  = new VolatileAttributeStore();
        fallback = new VolatileAttributeStore();
        layered  = new LayeredAttributeStore(primary, fallback);
    }

    @AfterEach
    void tearDown() {
        layered.close();
    }

    private static AttributeFinderInvocation envInvocation(String fqn) {
        return new AttributeFinderInvocation("default", fqn, List.of(), Duration.ofSeconds(1), Duration.ofMillis(100),
                Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static SubscriptionKey envKey(String fqn) {
        return new SubscriptionKey(envInvocation(fqn), false);
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

    private static Value lastValueFor(SubscriptionKey key, Recorder recorder) {
        return recorder.snapshots.get(recorder.snapshots.size() - 1).get(key).value();
    }

    @Nested
    @DisplayName("when primary serves the key")
    class WhenPrimaryServesTheKey {

        @Test
        @DisplayName("then the consumer sees the primary value even if the fallback also has one")
        void thenTheConsumerSeesThePrimaryValueEvenIfTheFallbackAlsoHasOne() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            primary.publish(repoKey("env.x"), Value.of("primary"));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(recorder.snapshots).isNotEmpty());
                assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("primary"));
            }
        }
    }

    @Nested
    @DisplayName("when only the fallback serves the key")
    class WhenOnlyTheFallbackServesTheKey {

        @Test
        @DisplayName("then the consumer sees the fallback value")
        void thenTheConsumerSeesTheFallbackValue() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("fallback")));
            }
        }
    }

    @Nested
    @DisplayName("when neither store serves the key")
    class WhenNeitherStoreServesTheKey {

        @Test
        @DisplayName("then the consumer sees UNDEFINED")
        void thenTheConsumerSeesUndefined() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(recorder.snapshots).isNotEmpty());
                assertThat(lastValueFor(key, recorder)).isEqualTo(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when the primary transitions UNDEFINED -> Value")
    class WhenThePrimaryTransitionsUndefinedToValue {

        @Test
        @DisplayName("then the consumer's resolved value flips from fallback to primary")
        void thenTheConsumersResolvedValueFlipsFromFallbackToPrimary() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("fallback")));

                primary.publish(repoKey("env.x"), Value.of("primary"));

                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("primary")));
            }
        }
    }

    @Nested
    @DisplayName("when the primary transitions Value -> UNDEFINED")
    class WhenThePrimaryTransitionsValueToUndefined {

        @Test
        @DisplayName("then the consumer's resolved value falls through to the fallback")
        void thenTheConsumersResolvedValueFallsThroughToTheFallback() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            primary.publish(repoKey("env.x"), Value.of("primary"));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("primary")));

                primary.remove(repoKey("env.x"));

                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("fallback")));
            }
        }
    }

    @Nested
    @DisplayName("when the fallback updates while the primary serves")
    class WhenTheFallbackUpdatesWhileThePrimaryServes {

        @Test
        @DisplayName("then the consumer continues to see the primary value (fallback is shadowed)")
        void thenTheConsumerContinuesToSeeThePrimaryValue() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            primary.publish(repoKey("env.x"), Value.of("primary"));

            try (val sub = layered.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("primary")));
                val sizeBefore = recorder.snapshots.size();

                fallback.publish(repoKey("env.x"), Value.of("fallback"));

                Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
                assertThat(recorder.snapshots).hasSize(sizeBefore);
                assertThat(lastValueFor(key, recorder)).isEqualTo(Value.of("primary"));
            }
        }
    }

    @Nested
    @DisplayName("when the consumer adds a new dependency")
    class WhenTheConsumerAddsANewDependency {

        @Test
        @DisplayName("then a new snapshot includes the added key resolved by priority")
        void thenANewSnapshotIncludesTheAddedKeyResolvedByPriority() {
            val keyA      = envKey("env.a");
            val keyB      = envKey("env.b");
            val snapshots = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();
            fallback.publish(repoKey("env.b"), Value.of("b-fallback"));
            primary.publish(repoKey("env.b"), Value.of("b-primary"));

            try (val sub = layered.open("s1", Set.of(keyA), snapshot -> {
                snapshots.add(snapshot);
                return Set.of(keyA, keyB);
            })) {
                Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(
                        () -> assertThat(snapshots).anySatisfy(snap -> assertThat(snap).containsKey(keyB)));
                val latest = snapshots.get(snapshots.size() - 1);
                assertThat(latest.get(keyB).value()).isEqualTo(Value.of("b-primary"));
            }
        }
    }

    @Nested
    @DisplayName("when the consumer is closed")
    class WhenTheConsumerIsClosed {

        @Test
        @DisplayName("then no further callbacks fire on inner-store updates")
        void thenNoFurtherCallbacksFireOnInnerStoreUpdates() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            val sub      = layered.open("s1", Set.of(key), recorder.asCallback());
            Awaitility.await().atMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(recorder.snapshots).isNotEmpty());
            val before = recorder.snapshots.size();

            sub.close();
            primary.publish(repoKey("env.x"), Value.of("primary"));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
            assertThat(recorder.snapshots).hasSize(before);
        }
    }

    @Nested
    @DisplayName("when the layered store is closed")
    class WhenTheLayeredStoreIsClosed {

        @Test
        @DisplayName("then both inner stores are cascade-closed")
        void thenBothInnerStoresAreCascadeClosed() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            layered.open("s1", Set.of(key), recorder.asCallback());
            val before = recorder.snapshots.size();

            layered.close();
            primary.publish(repoKey("env.x"), Value.of("primary"));
            fallback.publish(repoKey("env.x"), Value.of("fallback"));

            Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
            assertThat(recorder.snapshots).hasSize(before);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            layered.close();
            layered.close();
        }
    }

    @Nested
    @DisplayName("when multiple consumers track the same key")
    class WhenMultipleConsumersTrackTheSameKey {

        @Test
        @DisplayName("then each consumer receives the priority-resolved snapshot independently")
        void thenEachConsumerReceivesThePriorityResolvedSnapshotIndependently() {
            val key = envKey("env.x");
            val r1  = new Recorder(Set.of(key));
            val r2  = new Recorder(Set.of(key));
            primary.publish(repoKey("env.x"), Value.of("primary"));

            try (val s1 = layered.open("s1", Set.of(key), r1.asCallback());
                    val s2 = layered.open("s2", Set.of(key), r2.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, r1)).isEqualTo(Value.of("primary")));
                Awaitility.await().atMost(Duration.ofSeconds(1))
                        .untilAsserted(() -> assertThat(lastValueFor(key, r2)).isEqualTo(Value.of("primary")));
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
                    .isThrownBy(() -> layered.open("   ", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("then a duplicate subscriptionId is rejected")
        void thenADuplicateSubscriptionIdIsRejected() {
            val key      = envKey("env.x");
            val recorder = new Recorder(Set.of(key));
            layered.open("s1", Set.of(key), recorder.asCallback());

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> layered.open("s1", Set.of(key), recorder.asCallback()))
                    .withMessageContaining("already open");
        }

        @Test
        @DisplayName("then empty initialDependencies is rejected")
        void thenEmptyInitialDependenciesIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> layered.open("s1", Set.of(), snapshot -> Set.of(envKey("env.x"))))
                    .withMessageContaining("must not be empty");
        }
    }
}
