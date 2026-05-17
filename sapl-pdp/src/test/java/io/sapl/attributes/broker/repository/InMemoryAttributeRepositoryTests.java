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
import io.sapl.api.model.ErrorValue;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("InMemoryAttributeRepository")
class InMemoryAttributeRepositoryTests {

    private InMemoryAttributeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttributeRepository();
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private static AttributeFinderInvocation invocation(String fqn) {
        return new AttributeFinderInvocation("default", fqn, List.of(), Duration.ofSeconds(1), Duration.ofMillis(100),
                Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static RepositoryKey repoKey(String fqn) {
        return new RepositoryKey(null, fqn, List.of());
    }

    private static final class Recorder implements Consumer<Value> {
        final List<Value> values = new CopyOnWriteArrayList<>();

        @Override
        public void accept(Value value) {
            values.add(value);
        }
    }

    @Nested
    @DisplayName("when no entry exists")
    class WhenNoEntryExists {

        @Test
        @DisplayName("then observe fires synchronously with UNDEFINED")
        void thenObserveFiresSynchronouslyWithUndefined() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                assertThat(recorder.values).containsExactly(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when a value is published before observe")
    class WhenValuePublishedBeforeObserve {

        @Test
        @DisplayName("then observe fires synchronously with the published value")
        void thenObserveFiresSynchronouslyWithThePublishedValue() {
            repository.publish(repoKey("env.x"), Value.of("hello"));

            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                assertThat(recorder.values).containsExactly(Value.of("hello"));
            }
        }
    }

    @Nested
    @DisplayName("when publish happens after observe")
    class WhenPublishHappensAfterObserve {

        @Test
        @DisplayName("then the observer fires with the new value")
        void thenTheObserverFiresWithTheNewValue() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.publish(repoKey("env.x"), Value.of("v1"));
                assertThat(recorder.values).containsExactly(Value.UNDEFINED, Value.of("v1"));
            }
        }

        @Test
        @DisplayName("then re-publish fires again with the updated value")
        void thenRePublishFiresAgainWithTheUpdatedValue() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.publish(repoKey("env.x"), Value.of("v1"));
                repository.publish(repoKey("env.x"), Value.of("v2"));
                assertThat(recorder.values).containsExactly(Value.UNDEFINED, Value.of("v1"), Value.of("v2"));
            }
        }
    }

    @Nested
    @DisplayName("when an entry is removed")
    class WhenAnEntryIsRemoved {

        @Test
        @DisplayName("then the observer fires with UNDEFINED")
        void thenTheObserverFiresWithUndefined() {
            repository.publish(repoKey("env.x"), Value.of("v1"));
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.remove(repoKey("env.x"));
                assertThat(recorder.values).containsExactly(Value.of("v1"), Value.UNDEFINED);
            }
        }

        @Test
        @DisplayName("then removing a key with no entry is a no-op")
        void thenRemovingAKeyWithNoEntryIsANoop() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.remove(repoKey("env.x"));
                assertThat(recorder.values).containsExactly(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when an entry has a TTL")
    class WhenAnEntryHasATtl {

        @Test
        @DisplayName("then the entry expires and the observer sees UNDEFINED")
        void thenTheEntryExpiresAndTheObserverSeesUndefined() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.values).endsWith(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("then republish without TTL cancels the prior expiry")
        void thenRepublishWithoutTtlCancelsThePriorExpiry() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMillis(100));
                repository.publish(repoKey("env.x"), Value.of("v2"));

                Awaitility.await().pollDelay(Duration.ofMillis(500)).until(() -> true);
                assertThat(recorder.values).endsWith(Value.of("v2"));
            }
        }

        @Test
        @DisplayName("then explicit remove cancels a pending expiry")
        void thenExplicitRemoveCancelsAPendingExpiry() {
            val recorder = new Recorder();
            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                repository.publish(repoKey("env.x"), Value.of("v1"), Duration.ofMinutes(10));
                repository.remove(repoKey("env.x"));
                assertThat(recorder.values).endsWith(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when multiple observers track keys")
    class WhenMultipleObserversTrackKeys {

        @Test
        @DisplayName("then a publish fires only the observers of that key")
        void thenAPublishFiresOnlyTheObserversOfThatKey() {
            val rcdrX = new Recorder();
            val rcdrY = new Recorder();
            try (val sx = repository.observe(invocation("env.x"), rcdrX);
                    val sy = repository.observe(invocation("env.y"), rcdrY)) {
                repository.publish(repoKey("env.x"), Value.of("only-x"));
                assertThat(rcdrX.values).containsExactly(Value.UNDEFINED, Value.of("only-x"));
                assertThat(rcdrY.values).containsExactly(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("when an observer is closed")
    class WhenAnObserverIsClosed {

        @Test
        @DisplayName("then no further callbacks fire on subsequent publishes")
        void thenNoFurtherCallbacksFireOnSubsequentPublishes() {
            val recorder = new Recorder();
            val handle   = repository.observe(invocation("env.x"), recorder);
            val before   = recorder.values.size();

            handle.close();
            repository.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.values).hasSize(before);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            val handle = repository.observe(invocation("env.x"), new Recorder());
            handle.close();
            handle.close();
        }
    }

    @Nested
    @DisplayName("when the repository is closed")
    class WhenTheRepositoryIsClosed {

        @Test
        @DisplayName("then subsequent publishes are silently ignored")
        void thenSubsequentPublishesAreSilentlyIgnored() {
            val recorder = new Recorder();
            repository.observe(invocation("env.x"), recorder);
            val before = recorder.values.size();

            repository.close();
            repository.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.values).hasSize(before);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            repository.close();
            repository.close();
        }

        @Test
        @DisplayName("then observe after close yields a one-shot ErrorValue and is never fired again")
        void thenObserveAfterCloseYieldsErrorValue() {
            repository.close();
            val recorder = new Recorder();

            try (val ignored = repository.observe(invocation("env.x"), recorder)) {
                assertThat(recorder.values).singleElement().isInstanceOf(ErrorValue.class);
            }
        }
    }

    @Nested
    @DisplayName("when input is invalid")
    class WhenInputIsInvalid {

        @Test
        @DisplayName("then a zero TTL is rejected")
        void thenAZeroTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> repository.publish(repoKey("env.x"), Value.of("v"), Duration.ZERO))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a negative TTL is rejected")
        void thenANegativeTtlIsRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> repository.publish(repoKey("env.x"), Value.of("v"), Duration.ofMillis(-1)))
                    .withMessageContaining("strictly positive");
        }
    }

}
