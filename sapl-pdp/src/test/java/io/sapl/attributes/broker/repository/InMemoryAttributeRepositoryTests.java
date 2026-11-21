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
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.AttributeAssignment.UnboundValue;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    private static final String TEST_PDP_ID = "test-pdp";

    private static AttributeFinderInvocation invocation(String fqn) {
        return invocation(fqn, TEST_PDP_ID);
    }

    private static AttributeFinderInvocation invocation(String fqn, String pdpId) {
        return new AttributeFinderInvocation(pdpId, "default", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static RepositoryKey repoKey(String fqn) {
        return new RepositoryKey(null, fqn, List.of(), TEST_PDP_ID);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("the same attribute observed by two different pdpIds maps to distinct repository keys (tenant isolation)")
    void whenSameAttributeDifferentPdpIdThenDistinctRepositoryKeys() {
        val keyTenantA = RepositoryKey.fromInvocation(invocation("env.shared", "tenant-a"));
        val keyTenantB = RepositoryKey.fromInvocation(invocation("env.shared", "tenant-b"));

        assertThat(keyTenantA).isNotEqualTo(keyTenantB);
    }

    @Test
    @Timeout(30)
    @DisplayName("concurrent same-key publishes never leave an observer on a stale value")
    void whenConcurrentPublishesThenObserverConvergesToRepositoryState() throws InterruptedException {
        for (int round = 0; round < 300; round++) {
            try (val repo = new InMemoryAttributeRepository()) {
                val observed = new AtomicReference<Value>();
                // A read-park-write consumer widens the delivery window, so a stale racing
                // delivery would clobber the latest value.
                repo.observe(invocation("env.race"), value -> {
                    observed.get();
                    LockSupport.parkNanos(1_000);
                    observed.set(value);
                });
                val barrier = new CyclicBarrier(2);
                val writerA = Thread.ofVirtual().unstarted(publisher(repo, barrier, Value.of(1)));
                val writerB = Thread.ofVirtual().unstarted(publisher(repo, barrier, Value.of(2)));
                writerA.start();
                writerB.start();
                writerA.join();
                writerB.join();
                // A fresh observer reads the settled state, which the racing observer must have
                // converged to.
                val authoritative = new AtomicReference<Value>();
                repo.observe(invocation("env.race"), authoritative::set);
                assertThat(observed.get()).as("round %d", round).isEqualTo(authoritative.get());
            }
        }
    }

    private static Runnable publisher(InMemoryAttributeRepository repo, CyclicBarrier barrier, Value value) {
        return () -> {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                Thread.currentThread().interrupt();
                return;
            }
            repo.publish(repoKey("env.race"), value);
        };
    }

    private static final class Recorder implements Consumer<Value> {
        final List<Value> values = new CopyOnWriteArrayList<>();

        @Override
        public void accept(Value value) {
            values.add(value);
        }
    }

    @Test
    @DisplayName("one transfer publishes one complete state, with the final assignment for each key")
    void whenTransferCommitsThenObserversReceiveItsFinalState() {
        val department = repoKey("work.department");
        val clearance  = repoKey("work.clearance");
        val views      = new CopyOnWriteArrayList<RepositoryState>();
        try (val watch = repository.observeState(TEST_PDP_ID, "default", views::add)) {
            repository.publish(List.of(new BoundValue(department, Value.of("HR"), null),
                    new BoundValue(clearance, Value.of(5), null)));
            repository.publish(List.of(new UnboundValue(department), new BoundValue(department, Value.of("IT"), null),
                    new BoundValue(clearance, Value.of(1), null)));
            assertThat(watch.initialState().bindings()).isEmpty();
            assertThat(views).extracting(state -> List.of(state.value(department), state.value(clearance)))
                    .containsExactly(List.of(Value.of("HR"), Value.of(5)), List.of(Value.of("IT"), Value.of(1)));
        }
    }

    @Test
    @DisplayName("a publication spanning tenants leaves both tenants and their observers untouched")
    void whenPublicationSpansTenantsThenNoAssignmentIsPublished() {
        val first   = repoKey("work.department");
        val other   = new RepositoryKey(null, "work.clearance", List.of(), "other");
        val changes = List.<AttributeAssignment>of(new BoundValue(first, Value.of("IT"), null),
                new BoundValue(other, Value.of(5), null));
        val updates = new CopyOnWriteArrayList<RepositoryState>();
        try (val watch = repository.observeState(TEST_PDP_ID, "default", updates::add)) {
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repository.publish(changes));
            try (val current = repository.observeState(TEST_PDP_ID, "default", updates::add)) {
                assertThat(current.initialState()).isEqualTo(watch.initialState());
            }
            assertThat(updates).isEmpty();
        }
    }

    @Test
    @DisplayName("renewing the same value cancels its former expiry without extending another binding")
    void whenEqualValueIsReboundThenOnlyItsCurrentLifetimeApplies() {
        val renewed  = repoKey("work.renewed");
        val expires  = repoKey("work.expires");
        val value    = Value.of("active");
        val observed = new CopyOnWriteArrayList<RepositoryState>();
        try (val ignored = repository.observeState(TEST_PDP_ID, "default", observed::add)) {
            repository.publish(renewed, value, Duration.ofMillis(80));
            repository.publish(List.of(new BoundValue(renewed, value, null),
                    new BoundValue(expires, value, Duration.ofMillis(160))));
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .until(() -> observed.getLast().value(expires).equals(Value.UNDEFINED));
            assertThat(observed.getLast().value(expires)).isEqualTo(Value.UNDEFINED);
            assertThat(observed.getLast().value(renewed)).isEqualTo(value);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("an expiry observer in one tenant cannot extend another tenant's grant")
    void whenAnExpiryObserverIsBlockedThenAnotherTenantExpiresAndIsNotified() throws Exception {
        val first   = repoKey("access.grant");
        val second  = new RepositoryKey(null, "access.grant", List.of(), "other");
        val entered = new CompletableFuture<Void>();
        val release = new CompletableFuture<Void>();
        val expired = new CompletableFuture<RepositoryState>();
        val ttl     = Duration.ofMillis(20);
        try (val slow = repository.observeState(first.pdpId(), "default", state -> {
            if (state.value(first).equals(Value.UNDEFINED)) {
                entered.complete(null);
                release.join();
            }
        }); val fast = repository.observeState(second.pdpId(), "default", state -> {
            if (state.value(second).equals(Value.UNDEFINED)) {
                expired.complete(state);
            }
        })) {
            repository.publish(first, Value.TRUE, ttl);
            entered.get(2, TimeUnit.SECONDS);
            repository.publish(second, Value.TRUE, ttl);
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                try (val reader = repository.observeState(second.pdpId(), "default", ignored -> {})) {
                    assertThat(reader.initialState().value(second)).isEqualTo(Value.UNDEFINED);
                }
            });
            assertThat(expired.get(2, TimeUnit.SECONDS).bindings()).doesNotContainKey(second);
        } finally {
            release.complete(null);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("a slow registration catches up to the latest complete expiry while a second observer keeps receiving")
    void whenExpiriesAccumulateThenTheSlowObserverReceivesTheCurrentView() throws Exception {
        val key          = repoKey("access.grant");
        val entered      = new CompletableFuture<Void>();
        val release      = new CompletableFuture<Void>();
        val caughtUp     = new CompletableFuture<RepositoryState>();
        val slowExpiries = new CopyOnWriteArrayList<RepositoryState>();
        val fastExpiries = new LinkedBlockingQueue<RepositoryState>();
        try (val slow = repository.observeState(TEST_PDP_ID, "default", state -> {
            if (state.bindings().isEmpty()) {
                slowExpiries.add(state);
                if (entered.complete(null)) {
                    release.join();
                } else {
                    caughtUp.complete(state);
                }
            }
        }); val fast = repository.observeState(TEST_PDP_ID, "default", state -> {
            if (state.bindings().isEmpty()) {
                fastExpiries.add(state);
            }
        })) {
            val ttl = Duration.ofMillis(20);
            repository.publish(key, Value.TRUE, ttl);
            entered.get(2, TimeUnit.SECONDS);
            val first = fastExpiries.poll(2, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            RepositoryState latest = first;
            for (int renewal = 0; renewal < 3; renewal++) {
                repository.publish(key, Value.TRUE, ttl);
                latest = fastExpiries.poll(2, TimeUnit.SECONDS);
                assertThat(latest).isNotNull();
            }
            assertThat(slowExpiries).containsExactly(first);
            release.complete(null);
            assertThat(caughtUp.get(2, TimeUnit.SECONDS)).isEqualTo(latest);
        } finally {
            release.complete(null);
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
            repository.close();
            val atClose = List.copyOf(recorder.values);
            repository.publish(repoKey("env.x"), Value.of("v1"));

            assertThat(recorder.values).containsExactlyElementsOf(atClose);
            assertThat(atClose.getLast()).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("then close is idempotent")
        void thenCloseIsIdempotent() {
            repository.close();
            assertThatCode(repository::close).doesNotThrowAnyException();
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
    @DisplayName("when a slow observer is in flight on one key")
    class WhenSlowObserverInFlight {

        @Test
        @Timeout(5)
        @DisplayName("then a TTL expiry on an unrelated key fires its own observer concurrently "
                + "rather than queuing behind the slow callback")
        void thenTtlExpiryOnUnrelatedKeyFiresWhileSlowConsumerStillRunning() throws Exception {
            val             slowEntered  = new java.util.concurrent.CountDownLatch(1);
            val             ttlObserved  = new java.util.concurrent.CountDownLatch(1);
            val             unblockSlow  = new java.util.concurrent.CountDownLatch(1);
            Consumer<Value> slowObserver = value -> {
                                             // The synchronous initial UNDEFINED delivery happens on the
                                             // main thread inside observe(...). Blocking on it would park the
                                             // main thread, so the concurrent scenario could never be set up.
                                             // Only the real "trigger" value, delivered from the separate
                                             // virtual thread below, parks the slow callback.
                                             if (Value.UNDEFINED.equals(value)) {
                                                 return;
                                             }
                                             slowEntered.countDown();
                                             try {
                                                 unblockSlow.await(5, java.util.concurrent.TimeUnit.SECONDS);
                                             } catch (InterruptedException e) {
                                                 Thread.currentThread().interrupt();
                                             }
                                         };
            val             ttlValueSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
            Consumer<Value> fastObserver = value -> {
                                             if (!Value.UNDEFINED.equals(value)) {
                                                 // The published value precedes the expiry UNDEFINED.
                                                 ttlValueSeen.set(true);
                                             } else if (ttlValueSeen.get()) {
                                                 // Only the expiry UNDEFINED (after the published value)
                                                 // counts, not the initial registration UNDEFINED.
                                                 ttlObserved.countDown();
                                             }
                                         };

            try (val ignored1 = repository.observe(invocation("env.slow"), slowObserver);
                    val ignored2 = repository.observe(invocation("env.ttl"), fastObserver)) {

                // Drive the slow observer from a separate thread so the main
                // thread retains control to publish to the unrelated key.
                Thread.startVirtualThread(() -> repository.publish(repoKey("env.slow"), Value.of("trigger")));

                assertThat(slowEntered.await(2, java.util.concurrent.TimeUnit.SECONDS))
                        .as("slow observer must be entered before we test TTL on unrelated key").isTrue();

                // Publish to the unrelated key with a short TTL. Expiry fires
                // independently. The slow observer is still parked
                // in its accept(...) call right now.
                repository.publish(repoKey("env.ttl"), Value.of("with-ttl"), Duration.ofMillis(50));

                // TTL expiry must reach the fast observer regardless of the
                // slow observer's in-flight callback.
                assertThat(ttlObserved.await(2, java.util.concurrent.TimeUnit.SECONDS))
                        .as("TTL expiry on env.ttl must fire while env.slow's onUpdate is still running").isTrue();

                unblockSlow.countDown();
            }
        }
    }

    @Nested
    @DisplayName("when input is invalid")
    class WhenInputIsInvalid {

        @Test
        @DisplayName("then a zero TTL is rejected")
        void thenAZeroTtlIsRejected() {
            val key   = repoKey("env.x");
            val value = Value.of("v");
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> repository.publish(key, value, Duration.ZERO))
                    .withMessageContaining("strictly positive");
        }

        @Test
        @DisplayName("then a negative TTL is rejected")
        void thenANegativeTtlIsRejected() {
            val key      = repoKey("env.x");
            val value    = Value.of("v");
            val negative = Duration.ofMillis(-1);
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> repository.publish(key, value, negative))
                    .withMessageContaining("strictly positive");
        }
    }

}
