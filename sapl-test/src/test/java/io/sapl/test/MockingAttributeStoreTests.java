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
package io.sapl.test;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.compiler.eval.AttributeStore;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import io.sapl.test.verification.MockVerificationError;
import io.sapl.test.verification.Times;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.args;
import static io.sapl.test.Matchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MockingAttributeStore")
class MockingAttributeStoreTests {

    private static final String                 CONFIG_ID       = "test-config";
    private static final Duration               DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration               DEFAULT_POLL    = Duration.ofSeconds(1);
    private static final Duration               DEFAULT_BACKOFF = Duration.ofMillis(100);
    private static final long                   DEFAULT_RETRIES = 3;
    private static final boolean                DEFAULT_FRESH   = false;
    private static final AttributeAccessContext EMPTY_CTX       = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private MockingAttributeStore store;

    @BeforeEach
    void setUp() {
        store = new MockingAttributeStore();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static SubscriptionKey envKey(String name, Value... arguments) {
        return new SubscriptionKey(new AttributeFinderInvocation(CONFIG_ID, name, List.of(arguments), DEFAULT_TIMEOUT,
                DEFAULT_POLL, DEFAULT_BACKOFF, DEFAULT_RETRIES, DEFAULT_FRESH, EMPTY_CTX), false);
    }

    private static SubscriptionKey entityKey(String name, Value entity, Value... arguments) {
        return new SubscriptionKey(new AttributeFinderInvocation(CONFIG_ID, name, entity, List.of(arguments),
                DEFAULT_TIMEOUT, DEFAULT_POLL, DEFAULT_BACKOFF, DEFAULT_RETRIES, DEFAULT_FRESH, EMPTY_CTX), false);
    }

    /**
     * Records callback invocations and the snapshots passed; returns the same
     * dependency set across all calls so the gate stays open for subsequent
     * publishes.
     */
    private static final class RecordingCallback {
        final AtomicInteger                                            count = new AtomicInteger(0);
        final AtomicReference<Map<SubscriptionKey, AttributeSnapshot>> last  = new AtomicReference<>();
        final Set<SubscriptionKey>                                     deps;

        RecordingCallback(Set<SubscriptionKey> deps) {
            this.deps = deps;
        }

        Set<SubscriptionKey> apply(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
            count.incrementAndGet();
            last.set(snapshot);
            return deps;
        }
    }

    @Nested
    @DisplayName("Mock registration validation")
    class MockRegistrationValidation {

        @Test
        @DisplayName("blank mockId is rejected")
        void blankMockIdRejected() {
            assertThatThrownBy(() -> store.mockEnvironmentAttribute("", "time.now", args()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        }

        @Test
        @DisplayName("duplicate mockId is rejected")
        void duplicateMockIdRejected() {
            store.mockEnvironmentAttribute("mock1", "time.now", args());
            assertThatThrownBy(() -> store.mockEnvironmentAttribute("mock1", "other.attr", args()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("non-args() arguments rejected")
        void nonArgsArgumentsRejected() {
            val badParams = (SaplTestFixture.Parameters) new SaplTestFixture.Parameters() {};
            assertThatThrownBy(() -> store.mockEnvironmentAttribute("mock1", "time.now", badParams))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
        }

        @Test
        @DisplayName("hasMock returns true after registration; false otherwise")
        void hasMockReflectsRegistration() {
            assertThat(store.hasMock("mock1")).isFalse();
            store.mockEnvironmentAttribute("mock1", "time.now", args());
            assertThat(store.hasMock("mock1")).isTrue();
            assertThat(store.hasMock("other")).isFalse();
        }

        @Test
        @DisplayName("hasMockForAttribute returns true when any mock for that name exists")
        void hasMockForAttributeReflectsRegistration() {
            assertThat(store.hasMockForAttribute("time.now")).isFalse();
            store.mockEnvironmentAttribute("mock1", "time.now", args());
            assertThat(store.hasMockForAttribute("time.now")).isTrue();
            assertThat(store.hasMockForAttribute("other")).isFalse();
        }
    }

    @Nested
    @DisplayName("Subscribe validation")
    class SubscribeValidation {

        @Test
        @DisplayName("blank subscriptionId rejected")
        void blankSubscriptionIdRejected() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("x"));
            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            assertThatThrownBy(() -> store.open("", deps, cb::apply)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("subscriptionId");
        }

        @Test
        @DisplayName("duplicate subscriptionId rejected")
        void duplicateSubscriptionIdRejected() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("x"));
            val deps = Set.of(envKey("time.now"));
            store.open("sub-1", deps, snap -> deps);
            val cb = new RecordingCallback(deps);
            assertThatThrownBy(() -> store.open("sub-1", deps, cb::apply)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already open");
        }

        @Test
        @DisplayName("empty initialDependencies rejected")
        void emptyInitialDepsRejected() {
            val empty = Set.<SubscriptionKey>of();
            val cb    = new RecordingCallback(empty);
            assertThatThrownBy(() -> store.open("sub-1", empty, cb::apply)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("initialDependencies");
        }
    }

    @Nested
    @DisplayName("Dispatch and gate")
    class DispatchAndGate {

        @Test
        @DisplayName("matched mock with initial value fires callback synchronously on subscribe")
        void initialValueFiresImmediately() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("initial"));
            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).hasSize(1)
                    .allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("initial")));
        }

        @Test
        @DisplayName("matched mock without initial value waits for emit")
        void noInitialValueWaitsForEmit() {
            store.mockEnvironmentAttribute("m1", "time.now", args());
            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(0);

            store.emit("m1", Value.of("emitted"));
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("emitted")));
        }

        @Test
        @DisplayName("unmatched key materialises ErrorValue and fires callback immediately")
        void unmatchedKeyAutoErrors() {
            val deps = Set.of(envKey("never.mocked"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isInstanceOf(ErrorValue.class));
        }

        @Test
        @DisplayName("mixed deps: matched + unmatched both populate snapshot, gate opens immediately")
        void mixedMatchedAndUnmatched() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v1"));
            val deps = Set.of(envKey("time.now"), envKey("never.mocked"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).hasSize(2);
        }

        @Test
        @DisplayName("emit before subscribe caches latest value (subscribe sees it)")
        void emitBeforeSubscribeCaches() {
            store.mockEnvironmentAttribute("m1", "time.now", args());
            store.emit("m1", Value.of("pre1"));
            store.emit("m1", Value.of("pre2"));

            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("pre2")));
        }
    }

    @Nested
    @DisplayName("emit propagation")
    class EmitPropagation {

        @Test
        @DisplayName("emit unknown mockId throws")
        void emitUnknownThrows() {
            assertThatThrownBy(() -> store.emit("nope", Value.of("x"))).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nope");
        }

        @Test
        @DisplayName("emit fires callback for all subscribed keys bound to mockId")
        void emitFiresForAllBound() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("init"));
            val deps1 = Set.of(envKey("time.now"));
            val deps2 = Set.of(envKey("time.now"));
            val cb1   = new RecordingCallback(deps1);
            val cb2   = new RecordingCallback(deps2);
            store.open("sub-1", deps1, cb1::apply);
            store.open("sub-2", deps2, cb2::apply);
            assertThat(cb1.count).hasValue(1);
            assertThat(cb2.count).hasValue(1);

            store.emit("m1", Value.of("update"));
            assertThat(cb1.count).hasValue(2);
            assertThat(cb2.count).hasValue(2);
            assertThat(cb1.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("update")));
            assertThat(cb2.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("update")));
        }

        @Test
        @DisplayName("multiple emits coalesce: pre-subscribe emits leave only the last value visible")
        void multipleEmitsCoalesceBeforeSubscribe() {
            store.mockEnvironmentAttribute("m1", "time.now", args());
            store.emit("m1", Value.of("v1"));
            store.emit("m1", Value.of("v2"));
            store.emit("m1", Value.of("v3"));
            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("v3")));
        }
    }

    @Nested
    @DisplayName("Most-specific-first dispatch")
    class MostSpecificDispatch {

        @Test
        @DisplayName("Exact matcher wins over Any matcher")
        void exactBeatsAny() {
            store.mockEnvironmentAttribute("anyMock", "time.day", args(any()), Value.of("any-result"));
            store.mockEnvironmentAttribute("exactMock", "time.day", args(eq(Value.of("monday"))),
                    Value.of("exact-result"));
            val deps = Set.of(envKey("time.day", Value.of("monday")));
            val cb   = new RecordingCallback(deps);
            store.open("sub-1", deps, cb::apply);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("exact-result")));
        }

        @Test
        @DisplayName("environment vs entity dispatch keyed by null-entity")
        void environmentVsEntityDispatch() {
            store.mockEnvironmentAttribute("envMock", "shared.attr", args(), Value.of("env"));
            store.mockAttribute("entMock", "shared.attr", any(), args(), Value.of("ent"));
            val envDeps = Set.of(envKey("shared.attr"));
            val cbEnv   = new RecordingCallback(envDeps);
            store.open("env-sub", envDeps, cbEnv::apply);
            assertThat(cbEnv.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("env")));

            val entDeps = Set.of(entityKey("shared.attr", Value.of("alice")));
            val cbEnt   = new RecordingCallback(entDeps);
            store.open("ent-sub", entDeps, cbEnt::apply);
            assertThat(cbEnt.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("ent")));
        }
    }

    @Nested
    @DisplayName("Invocation recording")
    class InvocationRecording {

        @Test
        @DisplayName("subscribe records one invocation per key")
        void subscribeRecordsInvocations() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val deps = Set.of(envKey("time.now"));
            store.open("sub-1", deps, snap -> deps);
            assertThat(store.getInvocations()).hasSize(1);
            assertThat(store.getInvocations("time.now")).hasSize(1);
            assertThat(store.getInvocations("other")).isEmpty();
        }

        @Test
        @DisplayName("sequence numbers are strictly increasing across recordings")
        void sequenceNumbersIncreasing() {
            store.mockEnvironmentAttribute("m1", "a.attr", args(), Value.of("x"));
            store.mockEnvironmentAttribute("m2", "b.attr", args(), Value.of("y"));
            store.open("sub-1", Set.of(envKey("a.attr")), snap -> Set.of(envKey("a.attr")));
            store.open("sub-2", Set.of(envKey("b.attr")), snap -> Set.of(envKey("b.attr")));
            val seqs = store.getInvocations().stream().mapToLong(r -> r.sequenceNumber()).toArray();
            assertThat(seqs[1]).isGreaterThan(seqs[0]);
        }

        @Test
        @DisplayName("clearInvocations resets records but keeps mocks")
        void clearInvocationsKeepsMocks() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            store.open("sub-1", Set.of(envKey("time.now")), snap -> Set.of(envKey("time.now")));
            assertThat(store.getInvocations()).hasSize(1);

            store.clearInvocations();
            assertThat(store.getInvocations()).isEmpty();
            assertThat(store.hasMock("m1")).isTrue();
        }

        @Test
        @DisplayName("clearAllMocks resets mocks and invocations")
        void clearAllMocksResetsBoth() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            store.open("sub-1", Set.of(envKey("time.now")), snap -> Set.of(envKey("time.now")));
            store.clearAllMocks();
            assertThat(store.hasMock("m1")).isFalse();
            assertThat(store.getInvocations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Verification")
    class Verification {

        @Test
        @DisplayName("verifyEnvironmentAttributeCalled passes when invoked")
        void verifyCalledPasses() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            store.open("sub-1", Set.of(envKey("time.now")), snap -> Set.of(envKey("time.now")));
            store.verifyEnvironmentAttributeCalled("time.now", args());
        }

        @Test
        @DisplayName("verifyEnvironmentAttribute Times.never passes when not invoked")
        void verifyNeverPassesWhenNotInvoked() {
            store.verifyEnvironmentAttribute("time.now", args(), Times.never());
        }

        @Test
        @DisplayName("verifyEnvironmentAttribute Times.never fails when invoked, with message")
        void verifyNeverFailsWhenInvoked() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            store.open("sub-1", Set.of(envKey("time.now")), snap -> Set.of(envKey("time.now")));
            assertThatThrownBy(() -> store.verifyEnvironmentAttribute("time.now", args(), Times.never()))
                    .isInstanceOf(MockVerificationError.class).hasMessageContaining("time.now");
        }

        @Test
        @DisplayName("verifyAttribute matches entity matcher")
        void verifyAttributeWithEntityMatcher() {
            store.mockAttribute("m1", "user.role", any(), args(), Value.of("admin"));
            store.open("sub-1", Set.of(entityKey("user.role", Value.of("alice"))),
                    snap -> Set.of(entityKey("user.role", Value.of("alice"))));
            store.verifyAttribute("user.role", eq(Value.of("alice")), args(), Times.once());
        }

        @Test
        @DisplayName("verification error message includes recorded invocations")
        void verificationErrorIncludesRecorded() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            store.open("sub-1", Set.of(envKey("time.now")), snap -> Set.of(envKey("time.now")));
            assertThatThrownBy(() -> store.verifyEnvironmentAttribute("time.now", args(), Times.times(2)))
                    .isInstanceOf(MockVerificationError.class).hasMessageContaining("Recorded invocations");
        }
    }

    @Nested
    @DisplayName("Subscription contract")
    class SubscriptionContract {

        @Test
        @DisplayName("callback returning empty set throws IllegalStateException")
        void emptyReturnedDepsThrows() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val initialDeps = Set.of(envKey("time.now"));
            assertThatThrownBy(() -> store.open("sub-1", initialDeps, snap -> Set.<SubscriptionKey>of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("sub-1");
        }

        @Test
        @DisplayName("callback returning null throws IllegalStateException")
        void nullReturnedDepsThrows() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val initialDeps = Set.of(envKey("time.now"));
            assertThatThrownBy(() -> store.open("sub-1", initialDeps, snap -> null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("callback returning new key (mocked) re-evaluates and includes it on next emit")
        void newKeyAddedViaCallback() {
            store.mockEnvironmentAttribute("m1", "first.attr", args(), Value.of("v1"));
            store.mockEnvironmentAttribute("m2", "second.attr", args(), Value.of("v2"));
            val initialDeps   = Set.of(envKey("first.attr"));
            val expandedDeps  = Set.of(envKey("first.attr"), envKey("second.attr"));
            val firstCallSeen = new AtomicReference<Set<SubscriptionKey>>();
            store.open("sub-1", initialDeps, snap -> {
                if (firstCallSeen.compareAndSet(null, snap.keySet())) {
                    return expandedDeps;
                }
                return expandedDeps;
            });
            assertThat(firstCallSeen.get()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("Subscription.close stops further callbacks for that subscription")
        void subscriptionCloseStopsCallbacks() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val deps = Set.of(envKey("time.now"));
            val cb   = new RecordingCallback(deps);
            val sub  = store.open("sub-1", deps, cb::apply);
            assertThat(cb.count).hasValue(1);

            sub.close();
            store.emit("m1", Value.of("after-close"));
            assertThat(cb.count).hasValue(1);
        }

        @Test
        @DisplayName("Store.close drops all subscriptions and prevents callbacks")
        void storeCloseDropsAllSubs() {
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val cb1 = new RecordingCallback(Set.of(envKey("time.now")));
            val cb2 = new RecordingCallback(Set.of(envKey("time.now")));
            store.open("sub-1", Set.of(envKey("time.now")), cb1::apply);
            store.open("sub-2", Set.of(envKey("time.now")), cb2::apply);

            store.close();
            store = new MockingAttributeStore();
            store.mockEnvironmentAttribute("m1", "time.now", args(), Value.of("v"));
            val initial1 = cb1.count.get();
            val initial2 = cb2.count.get();
            store.emit("m1", Value.of("post-close-emit"));
            assertThat(cb1.count.get()).isEqualTo(initial1);
            assertThat(cb2.count.get()).isEqualTo(initial2);
        }
    }

    /**
     * Minimal in-memory {@link AttributeStore} test fake for delegate
     * forwarding tests. Tracks subscriptions, exposes a publish hook,
     * fires the appropriate callback when the published key is in the
     * subscription's deps.
     */
    private static final class FakeDelegateStore implements AttributeStore {

        private final Map<SubscriptionKey, AttributeSnapshot> mailbox    = new java.util.HashMap<>();
        private final Map<String, FakeSubscription>           subs       = new java.util.HashMap<>();
        final AtomicInteger                                   openCount  = new AtomicInteger(0);
        final AtomicInteger                                   closeCount = new AtomicInteger(0);

        @Override
        public synchronized Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
                java.util.function.Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            openCount.incrementAndGet();
            val sub = new FakeSubscription(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            subs.put(subscriptionId, sub);
            if (initialDependencies.stream().allMatch(mailbox::containsKey)) {
                fireSync(sub);
            }
            return sub;
        }

        @Override
        public synchronized void close() {
            subs.clear();
            mailbox.clear();
        }

        synchronized void publish(SubscriptionKey key, Value value) {
            mailbox.put(key, new AttributeSnapshot(value, java.time.Instant.now()));
            for (val sub : subs.values()) {
                if (sub.deps.contains(key) && sub.deps.stream().allMatch(mailbox::containsKey)) {
                    fireSync(sub);
                }
            }
        }

        private void fireSync(FakeSubscription sub) {
            val snap = new java.util.HashMap<SubscriptionKey, AttributeSnapshot>();
            for (val k : sub.deps) {
                val v = mailbox.get(k);
                if (v != null) {
                    snap.put(k, v);
                }
            }
            sub.onUpdate.apply(Map.copyOf(snap));
        }

        private final class FakeSubscription implements Subscription {
            final String                                                                                     id;
            final Set<SubscriptionKey>                                                                       deps;
            final java.util.function.Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;

            FakeSubscription(String id,
                    Set<SubscriptionKey> deps,
                    java.util.function.Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
                this.id       = id;
                this.deps     = deps;
                this.onUpdate = onUpdate;
            }

            @Override
            public void close() {
                synchronized (FakeDelegateStore.this) {
                    closeCount.incrementAndGet();
                    subs.remove(id);
                }
            }
        }
    }

    @Nested
    @DisplayName("Delegate forwarding")
    class DelegateForwarding {

        @Test
        @DisplayName("with delegate set, unmatched key opens a forwarding subscription against delegate")
        void unmatchedRoutesToDelegate() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            delegate.publish(key, Value.of("from-delegate"));

            val cb = new RecordingCallback(Set.of(key));
            store.open("sub-1", Set.of(key), cb::apply);
            assertThat(delegate.openCount).hasValue(1);
            assertThat(cb.count).hasValue(1);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("from-delegate")));
        }

        @Test
        @DisplayName("delegate publish after subscribe propagates to consumer callback")
        void delegatePublishPropagatesAsync() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            // Pre-publish so the gate opens immediately on subscribe
            delegate.publish(key, Value.of("first"));

            val cb = new RecordingCallback(Set.of(key));
            store.open("sub-1", Set.of(key), cb::apply);
            assertThat(cb.count).hasValue(1);

            delegate.publish(key, Value.of("second"));
            assertThat(cb.count).hasValue(2);
            assertThat(cb.last.get()).allSatisfy((k, v) -> assertThat(v.value()).isEqualTo(Value.of("second")));
        }

        @Test
        @DisplayName("multiple consumers sharing a delegated key share one delegate subscription")
        void sharedDelegateSubscription() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            delegate.publish(key, Value.of("v"));

            store.open("sub-1", Set.of(key), snap -> Set.of(key));
            store.open("sub-2", Set.of(key), snap -> Set.of(key));
            assertThat(delegate.openCount).hasValue(1);
        }

        @Test
        @DisplayName("closing one consumer keeps the delegate sub open while others still need it")
        void delegateSubKeptAliveByRefcount() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            delegate.publish(key, Value.of("v"));

            val sub1 = store.open("sub-1", Set.of(key), snap -> Set.of(key));
            store.open("sub-2", Set.of(key), snap -> Set.of(key));
            sub1.close();
            assertThat(delegate.closeCount).hasValue(0);
        }

        @Test
        @DisplayName("closing all consumers releases the delegate sub")
        void delegateSubReleasedWhenLastConsumerCloses() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            delegate.publish(key, Value.of("v"));

            val sub1 = store.open("sub-1", Set.of(key), snap -> Set.of(key));
            val sub2 = store.open("sub-2", Set.of(key), snap -> Set.of(key));
            sub1.close();
            sub2.close();
            assertThat(delegate.closeCount).hasValue(1);
        }

        @Test
        @DisplayName("store close cascades closure to delegate forwards")
        void storeCloseCascadesToDelegate() {
            val delegate = new FakeDelegateStore();
            store.setDelegate(delegate);
            val key = envKey("other.attr");
            delegate.publish(key, Value.of("v"));
            store.open("sub-1", Set.of(key), snap -> Set.of(key));

            store.close();
            assertThat(delegate.closeCount).hasValue(1);

            // Recreate store for @AfterEach safety.
            store = new MockingAttributeStore();
        }
    }
}
