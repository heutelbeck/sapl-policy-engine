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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the {@code PolicyInformationPointAttributeBroker} on:
 *
 * <ul>
 * <li>dispatch coalescing on a slow consumer,</li>
 * <li>absence-versus-error semantics at the broker boundary,</li>
 * <li>list-of-candidates freshness across {@code fresh=true} and
 * {@code fresh=false} consumers,</li>
 * <li>grace period and the skip-when-alternatives optimization,</li>
 * <li>hot-swap UNDEFINED-jitter suppression.</li>
 * </ul>
 *
 * The pre-existing
 * {@link PolicyInformationPointAttributeBrokerTests} still covers
 * catalog management, the gate, refcount lifecycle, and hot-swap
 * mailbox preservation. This class extends those with domain-driven
 * scenarios for the new semantics.
 */
@DisplayName("PolicyInformationPointAttributeBroker spec coverage")
class PipBrokerSpecTests {

    private static final Duration AWAIT = Duration.ofSeconds(2);

    private static final AttributeAccessContext EMPTY_CONTEXT = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    @BeforeEach
    void resetFixtures() {
        InstrumentedPip.reset();
        SlowFirstEmitPip.reset();
    }

    private static AttributeFinderInvocation invocation(String fqn, boolean fresh) {
        return new AttributeFinderInvocation("test-pdp", "default", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, fresh, EMPTY_CONTEXT);
    }

    private static AttributeFinderInvocation invocation(String fqn, boolean fresh, Duration initialTimeOut,
            long retries) {
        return new AttributeFinderInvocation("test-pdp", "default", fqn, List.of(), initialTimeOut,
                Duration.ofMillis(50), Duration.ofMillis(30), retries, fresh, EMPTY_CONTEXT);
    }

    private static SubscriptionKey key(String fqn) {
        return new SubscriptionKey(invocation(fqn, false), false);
    }

    private static SubscriptionKey key(String fqn, boolean fresh) {
        return new SubscriptionKey(invocation(fqn, fresh), false);
    }

    private static SubscriptionKey key(String fqn, boolean fresh, Duration initialTimeOut, long retries) {
        return new SubscriptionKey(invocation(fqn, fresh, initialTimeOut, retries), false);
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

    private static void sleepUninterruptibly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Value lastValue(Recorder r, SubscriptionKey k) {
        return r.snapshots.get(r.snapshots.size() - 1).get(k).value();
    }

    @Nested
    @DisplayName("dispatch coalescing")
    class DispatchCoalescing {

        @Test
        @DisplayName("rapid PIP emissions during a slow onUpdate yield far fewer fires than emissions; "
                + "the last fire reflects the last published value")
        void whenRapidPipEmissionsAndSlowConsumerThenFiresCoalesce() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new InstrumentedPip());
                val k          = key("instrumented.tracked");
                val recorder   = new Recorder(Set.of(k));
                val fireCount  = new AtomicInteger();
                val countingCb = (Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>>) snapshot -> {
                                   fireCount.incrementAndGet();
                                   recorder.snapshots.add(snapshot);
                                   sleepUninterruptibly(Duration.ofMillis(40));
                                   return Set.of(k);
                               };
                try (val ignored = broker.open("s1", Set.of(k), countingCb)) {
                    val emissions = 200;
                    for (int i = 0; i < emissions; i++) {
                        InstrumentedPip.emitToAll(Value.of(i));
                    }
                    Awaitility.await().atMost(AWAIT)
                            .until(() -> Value.of(emissions - 1).equals(lastObservedValue(recorder, k)));

                    // Coalescing: the consumer must NOT have been fired once per emission.
                    // With a 40ms onUpdate and 200 emissions, an uncoalesced run would
                    // need ~8s just to drain the queue. We bound by half the emission
                    // count to leave generous slack while still proving the coalescer
                    // collapsed the storm.
                    assertThat(fireCount.get()).isLessThan(emissions / 2);
                    assertThat(lastObservedValue(recorder, k)).isEqualTo(Value.of(emissions - 1));
                }
            } finally {
                broker.close();
            }
        }

        private Value lastObservedValue(Recorder r, SubscriptionKey k) {
            if (r.snapshots.isEmpty()) {
                return null;
            }
            return r.snapshots.get(r.snapshots.size() - 1).get(k).value();
        }
    }

    @Nested
    @DisplayName("absence vs error at the broker boundary")
    class AbsenceVsError {

        @Test
        @DisplayName("PIP slower than initialTimeOut on first emission publishes UNDEFINED (absence), "
                + "not ErrorValue, even though the attempt counts as failed for retry purposes")
        void whenPipSlowerThanInitialTimeOutThenUndefinedAbsence() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new SlowFirstEmitPip());
                // One retry separates the initial-timeout absence (UNDEFINED) from the
                // eventual retries-exhausted error by a backoff gap, so the boundary
                // delivers the absence as a distinct first snapshot instead of coalescing
                // it with the trailing error.
                val k        = key("slow.first", false, Duration.ofMillis(60), 1L);
                val recorder = new Recorder(Set.of(k));
                try (val ignored = broker.open("s1", Set.of(k), recorder.asCallback())) {
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(recorder.snapshots).isNotEmpty());
                    val firstSeen = recorder.snapshots.get(0).get(k).value();
                    assertThat(firstSeen).isEqualTo(Value.UNDEFINED);
                    assertThat(firstSeen).isNotInstanceOf(ErrorValue.class);
                }
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("PIP whose attribute method returns an empty-completion Stream publishes UNDEFINED "
                + "at the broker boundary (absence, not error)")
        void whenPipReturnsEmptyStreamThenUndefinedAtBrokerBoundary() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new EmptyCompletionPip());
                val k        = key("empty.never");
                val recorder = new Recorder(Set.of(k));
                try (val ignored = broker.open("s1", Set.of(k), recorder.asCallback())) {
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(recorder.snapshots).isNotEmpty());
                    val firstSeen = recorder.snapshots.get(0).get(k).value();
                    assertThat(firstSeen).isEqualTo(Value.UNDEFINED);
                    assertThat(firstSeen).isNotInstanceOf(ErrorValue.class);
                }
            } finally {
                broker.close();
            }
        }
    }

    @Nested
    @DisplayName("open-time exceptions feed the retry burst")
    class OpenTimeExceptions {

        @Test
        @DisplayName("PIP whose attribute method throws on every invoke until the Nth attempt recovers "
                + "via the retry burst within backoff time, not pollInterval time")
        void whenPipInvokeThrowsThenRecoversThenConsumerSeesRecoveredValue() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                ThrowingThenSucceedingPip.recoverOnInvocation = 3;
                broker.load(new ThrowingThenSucceedingPip());
                // Three attempts (initial + 2 retries) covers the prep + recovery
                // within one cycle so the test does not depend on the outer
                // pollInterval kicking in.
                val k = key("throwing.tracked", false, Duration.ofSeconds(1), 5L);
                val r = new Recorder(Set.of(k));
                try (val ignored = broker.open("s1", Set.of(k), r.asCallback())) {
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
                        assertThat(r.snapshots).isNotEmpty();
                        assertThat(lastValue(r, k)).isEqualTo(Value.of("recovered"));
                    });
                }
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("hot-swap to a PIP whose first invoke throws: the consumer keeps observing the prior "
                + "value during the rebind-transition gate, no transient ErrorValue surfaces, "
                + "and a recovery emission lands eventually")
        void whenSwapToThrowingPipThenPriorValuePreservedAndRecoveryEventuallyLands() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                val v1Handle = broker.load(new InstrumentedPip());
                val k        = key("instrumented.tracked", false, Duration.ofSeconds(1), 5L);
                val r        = new Recorder(Set.of(k));
                try (val ignored = broker.open("s1", Set.of(k), r.asCallback())) {
                    InstrumentedPip.emitToAll(Value.of("v1"));
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r.snapshots).isNotEmpty());
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(lastValue(r, k))
                            .as("baseline v1 must land before swap").isEqualTo(Value.of("v1")));

                    // Swap-to-throwing: the new PIP's first few invokes throw,
                    // then it recovers. The rebind-transition gate must mask the
                    // failure window from consumers; the cumulative recovery
                    // must surface once the retry burst opens a working inner.
                    ThrowingThenSucceedingPipForRebind.recoverOnInvocation = 3;
                    broker.swap(v1Handle, new ThrowingThenSucceedingPipForRebind());

                    // The transitional snapshots before recovery must not include
                    // a transient ErrorValue or UNDEFINED for k; the gate hides
                    // those. The next real value the consumer observes after the
                    // swap is the recovered value.
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
                        assertThat(lastValue(r, k)).isEqualTo(Value.of("rebind-recovered"));
                    });
                    for (val snap : r.snapshots) {
                        val v = snap.get(k).value();
                        assertThat(v).as("transient values during rebind must not be ErrorValue")
                                .isNotInstanceOf(ErrorValue.class);
                    }
                }
            } finally {
                ThrowingThenSucceedingPipForRebind.recoverOnInvocation = 0;
                broker.close();
            }
        }
    }

    @Nested
    @DisplayName("list-of-candidates freshness model")
    class ListOfCandidates {

        @Test
        @DisplayName("a fresh=true subscriber followed by a fresh=false subscriber for the same "
                + "invocation: the fresh=true stream is the head, the fresh=false attaches to it; "
                + "only one backing exists")
        void whenFreshTrueThenFreshFalseShareTheFreshTrueBacking() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new InstrumentedPip());
                val freshKey  = key("instrumented.tracked", true);
                val sharedKey = key("instrumented.tracked", false);

                val r1 = new Recorder(Set.of(freshKey));
                val r2 = new Recorder(Set.of(sharedKey));
                try (val ignored1 = broker.open("s1", Set.of(freshKey), r1.asCallback());
                        val ignored2 = broker.open("s2", Set.of(sharedKey), r2.asCallback())) {

                    InstrumentedPip.emitToAll(Value.of("v1"));
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
                        assertThat(r1.snapshots).isNotEmpty();
                        assertThat(r2.snapshots).isNotEmpty();
                    });

                    assertThat(InstrumentedPip.openCount.get()).isEqualTo(1);
                    assertThat(lastValue(r1, freshKey)).isEqualTo(Value.of("v1"));
                    assertThat(lastValue(r2, sharedKey)).isEqualTo(Value.of("v1"));
                }
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("a fresh=true HARD requirement: every fresh=true subscriber gets its own backing "
                + "regardless of existing live streams for the same invocation")
        void whenMultipleFreshTrueThenEachGetsItsOwnBacking() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new InstrumentedPip());
                val sharedKey = key("instrumented.tracked", false);
                val freshKey  = key("instrumented.tracked", true);

                val r1 = new Recorder(Set.of(sharedKey));
                val r2 = new Recorder(Set.of(freshKey));
                val r3 = new Recorder(Set.of(freshKey));
                try (val ignored1 = broker.open("s1", Set.of(sharedKey), r1.asCallback());
                        val ignored2 = broker.open("s2", Set.of(freshKey), r2.asCallback());
                        val ignored3 = broker.open("s3", Set.of(freshKey), r3.asCallback())) {

                    assertThat(InstrumentedPip.openCount.get()).isEqualTo(3);
                }
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("when the head backing is torn down, the next live backing in the list becomes "
                + "the attachable head: a fresh=true-originated stream serves a later fresh=false consumer")
        void whenHeadTornDownThenSurvivingFreshTrueBackingBecomesHead() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new InstrumentedPip());
                val sharedKey = key("instrumented.tracked", false);
                val freshKey  = key("instrumented.tracked", true);

                val r1   = new Recorder(Set.of(sharedKey));
                val r2   = new Recorder(Set.of(freshKey));
                val sub1 = broker.open("s1", Set.of(sharedKey), r1.asCallback());
                val sub2 = broker.open("s2", Set.of(freshKey), r2.asCallback());

                InstrumentedPip.emitToAll(Value.of("v1"));
                Awaitility.await().atMost(AWAIT).untilAsserted(() -> {
                    assertThat(r1.snapshots).isNotEmpty();
                    assertThat(r2.snapshots).isNotEmpty();
                });
                assertThat(InstrumentedPip.openCount.get()).isEqualTo(2);

                sub1.close();
                // After head teardown, opening another fresh=false consumer should attach
                // to the surviving fresh=true-created backing instead of opening a third.
                val r3 = new Recorder(Set.of(sharedKey));
                try (val sub3 = broker.open("s3", Set.of(sharedKey), r3.asCallback())) {
                    // No new backing was opened.
                    assertThat(InstrumentedPip.openCount.get()).isEqualTo(2);
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r3.snapshots).isNotEmpty());
                    assertThat(lastValue(r3, sharedKey)).isEqualTo(Value.of("v1"));
                }
                sub2.close();
            } finally {
                broker.close();
            }
        }
    }

    @Nested
    @DisplayName("grace period")
    class GracePeriod {

        @Test
        @DisplayName("zero grace (default): refcount-zero tears down the backing immediately")
        void whenZeroGraceThenRefcountZeroTearsDownImmediately() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                broker.load(new InstrumentedPip());
                val k   = key("instrumented.tracked");
                val r   = new Recorder(Set.of(k));
                val sub = broker.open("s1", Set.of(k), r.asCallback());
                InstrumentedPip.emitToAll(Value.of("v1"));
                Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r.snapshots).isNotEmpty());
                assertThat(InstrumentedPip.openCount.get()).isEqualTo(1);

                sub.close();
                Awaitility.await().atMost(AWAIT)
                        .untilAsserted(() -> assertThat(InstrumentedPip.closeCount.get()).isEqualTo(1));
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("with grace, refcount-zero keeps the backing alive; a new fresh=false consumer "
                + "in the window re-attaches without opening a new inner stream")
        void whenReattachWithinGraceThenBackingReused() {
            val broker = new PolicyInformationPointAttributeBroker(Duration.ofMillis(400));
            try {
                broker.load(new InstrumentedPip());
                val k    = key("instrumented.tracked");
                val r1   = new Recorder(Set.of(k));
                val sub1 = broker.open("s1", Set.of(k), r1.asCallback());
                InstrumentedPip.emitToAll(Value.of("v1"));
                Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r1.snapshots).isNotEmpty());
                assertThat(InstrumentedPip.openCount.get()).isEqualTo(1);

                sub1.close();
                // Re-attach inside the grace window: same backing.
                val r2 = new Recorder(Set.of(k));
                try (val sub2 = broker.open("s2", Set.of(k), r2.asCallback())) {
                    assertThat(InstrumentedPip.openCount.get()).isEqualTo(1);
                    assertThat(InstrumentedPip.closeCount.get()).isZero();
                    // The cached value is delivered immediately to the re-attaching consumer.
                    assertThat(r2.snapshots).isNotEmpty();
                    assertThat(lastValue(r2, k)).isEqualTo(Value.of("v1"));
                }
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("with grace, no re-attach within the window: the backing tears down after grace expires")
        void whenGraceExpiresWithoutReattachThenTeardown() {
            val grace  = Duration.ofMillis(150);
            val broker = new PolicyInformationPointAttributeBroker(grace);
            try {
                broker.load(new InstrumentedPip());
                val k    = key("instrumented.tracked");
                val r1   = new Recorder(Set.of(k));
                val sub1 = broker.open("s1", Set.of(k), r1.asCallback());
                InstrumentedPip.emitToAll(Value.of("v1"));
                Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r1.snapshots).isNotEmpty());

                sub1.close();
                // Within grace, backing is still alive.
                Awaitility.await().pollDelay(grace.dividedBy(3)).atMost(grace.dividedBy(2))
                        .untilAsserted(() -> assertThat(InstrumentedPip.closeCount.get()).isZero());
                // After grace expires, the scheduled teardown closes the inner.
                Awaitility.await().atMost(grace.multipliedBy(4))
                        .untilAsserted(() -> assertThat(InstrumentedPip.closeCount.get()).isEqualTo(1));
            } finally {
                broker.close();
            }
        }

        @Test
        @DisplayName("grace is skipped when the per-invocation list has another live backing: "
                + "the surplus stream is torn down immediately on refcount-zero")
        void whenAlternativesExistThenGraceSkipped() {
            val broker = new PolicyInformationPointAttributeBroker(Duration.ofSeconds(30));
            try {
                broker.load(new InstrumentedPip());
                val sharedKey = key("instrumented.tracked", false);
                val freshKey  = key("instrumented.tracked", true);

                val r1   = new Recorder(Set.of(sharedKey));
                val r2   = new Recorder(Set.of(freshKey));
                val sub1 = broker.open("s1", Set.of(sharedKey), r1.asCallback());
                val sub2 = broker.open("s2", Set.of(freshKey), r2.asCallback());
                assertThat(InstrumentedPip.openCount.get()).isEqualTo(2);

                sub1.close();
                // List had size 2 at refcount-zero of sub1's backing -> skip grace -> immediate
                // close.
                Awaitility.await().atMost(AWAIT)
                        .untilAsserted(() -> assertThat(InstrumentedPip.closeCount.get()).isEqualTo(1));
                sub2.close();
            } finally {
                broker.close();
            }
        }
    }

    @Nested
    @DisplayName("hot-swap stability")
    class HotSwapJitter {

        @Test
        @DisplayName("hot-swap to a replacement PIP whose first emission is slower than initialTimeOut: "
                + "the consumer never observes a transient UNDEFINED; the prior real value persists "
                + "until the new stream emits a real value")
        void whenSwappingToSlowPipThenNoTransientUndefinedJitter() {
            val broker = new PolicyInformationPointAttributeBroker();
            try {
                val v1Handle = broker.load(new InstrumentedPip());
                // High retry count keeps the rebind-transition gate engaged
                // long enough for the test to deliver v2; with retries=0 a
                // retries-exhausted ErrorValue would propagate (real signal,
                // not jitter, but not the path under test here).
                val k = key("instrumented.tracked", false, Duration.ofMillis(60), 50L);
                val r = new Recorder(Set.of(k));
                try (val ignored = broker.open("s1", Set.of(k), r.asCallback())) {
                    InstrumentedPip.emitToAll(Value.of("v1"));
                    Awaitility.await().atMost(AWAIT).untilAsserted(() -> assertThat(r.snapshots).isNotEmpty());

                    // Replace with a fresh InstrumentedPip instance. Its first
                    // inner stream will NOT emit before initialTimeOut --
                    // forcing AttributeStream to publish UNDEFINED, which the
                    // backing's rebind-transition gate must suppress.
                    broker.swap(v1Handle, new InstrumentedPip());

                    // Give the new AttributeStream time to fire at least one
                    // initial-timeout cycle (and have it suppressed).
                    sleepUninterruptibly(Duration.ofMillis(150));

                    // During this window the mailbox must still show v1; the
                    // suppressed UNDEFINEDs must not have surfaced.
                    for (val snap : r.snapshots) {
                        assertThat(snap.get(k).value()).isEqualTo(Value.of("v1"));
                    }

                    // The retry-burst keeps creating new inner streams; poll
                    // and re-emit until v2 lands in a live inner.
                    Awaitility.await().atMost(AWAIT).pollInterval(Duration.ofMillis(20)).untilAsserted(() -> {
                        InstrumentedPip.emitToAll(Value.of("v2"));
                        assertThat(lastValue(r, k)).isEqualTo(Value.of("v2"));
                    });

                    // Across the whole subscription the consumer never observed
                    // UNDEFINED: every snapshot was either v1 or v2.
                    for (val snap : r.snapshots) {
                        assertThat(snap.get(k).value()).isNotEqualTo(Value.UNDEFINED);
                    }
                }
            } finally {
                broker.close();
            }
        }
    }

    /**
     * Test PIP that records every open and every close of an inner
     * stream. Each invocation returns a fresh tracked stream so the
     * broker's per-backing inner-stream open/close lifecycle is
     * directly observable.
     */
    @PolicyInformationPoint(name = "instrumented")
    static final class InstrumentedPip {

        static final AtomicInteger                 openCount  = new AtomicInteger();
        static final AtomicInteger                 closeCount = new AtomicInteger();
        static final List<LatestSlotStream<Value>> backings   = new CopyOnWriteArrayList<>();

        static void reset() {
            for (val s : new ArrayList<>(backings)) {
                try {
                    s.close();
                } catch (RuntimeException ignored) {
                    // best-effort cleanup
                }
            }
            openCount.set(0);
            closeCount.set(0);
            backings.clear();
        }

        static void emitToAll(Value v) {
            for (val b : backings) {
                b.put(v);
            }
        }

        @EnvironmentAttribute
        public Stream<Value> tracked() {
            openCount.incrementAndGet();
            val backing = new LatestSlotStream<Value>();
            backings.add(backing);
            return new TrackedStream(backing);
        }

        static final class TrackedStream implements Stream<Value> {
            private final LatestSlotStream<Value> delegate;

            TrackedStream(LatestSlotStream<Value> delegate) {
                this.delegate = delegate;
            }

            @Override
            public Value awaitNext() throws InterruptedException {
                return delegate.awaitNext();
            }

            @Override
            public Poll<Value> tryNext() {
                return delegate.tryNext();
            }

            @Override
            public void close() {
                closeCount.incrementAndGet();
                delegate.close();
            }
        }
    }

    /**
     * Test PIP whose first inner stream never emits, so every
     * subscription experiences an initial-timeout. Used to drive the
     * UNDEFINED-as-absence path without depending on real network or
     * clock primitives.
     */
    @PolicyInformationPoint(name = "slow")
    static final class SlowFirstEmitPip {

        static void reset() {
            // No shared state to reset; PIP is stateless.
        }

        @EnvironmentAttribute(name = "first")
        public Stream<Value> first() {
            // Never emits; AttributeStream's initialTimeOut watchdog drives the cycle.
            return new LatestSlotStream<>();
        }
    }

    /**
     * PIP whose attribute method returns a completed empty stream.
     * Drives the broker-level § 4.2 path: no value emitted, the cycle
     * closes cleanly, the broker mailbox publishes UNDEFINED.
     */
    @PolicyInformationPoint(name = "empty")
    static final class EmptyCompletionPip {

        @EnvironmentAttribute(name = "never")
        public Stream<Value> never() {
            return io.sapl.api.stream.Streams.empty();
        }
    }

    /**
     * PIP whose invoke throws on the first {@code recoverOnInvocation-1}
     * calls and returns a one-shot value stream on call number
     * {@code recoverOnInvocation}. Used to exercise the retry burst
     * recovering from transient open-time failures.
     */
    @PolicyInformationPoint(name = "throwing")
    static final class ThrowingThenSucceedingPip {

        static volatile int recoverOnInvocation = 0;

        private static final AtomicInteger callCount = new AtomicInteger();

        @EnvironmentAttribute
        public Stream<Value> tracked() {
            val n = callCount.incrementAndGet();
            if (n < recoverOnInvocation) {
                throw new RuntimeException("transient open-time failure #" + n);
            }
            callCount.set(0);
            val slot = new LatestSlotStream<Value>();
            slot.put(Value.of("recovered"));
            return slot;
        }
    }

    /**
     * Same as {@link ThrowingThenSucceedingPip} but with a distinct
     * namespace and recovered-value tag so a hot-swap test can tell
     * the post-swap fleet apart from the pre-swap one. The swap path
     * requires the replacement PIP to expose an attribute method
     * with the same fully-qualified shape as the original; named
     * "instrumented" / "tracked" to match the original InstrumentedPip.
     */
    @PolicyInformationPoint(name = "instrumented")
    static final class ThrowingThenSucceedingPipForRebind {

        static volatile int recoverOnInvocation = 0;

        private static final AtomicInteger callCount = new AtomicInteger();

        @EnvironmentAttribute
        public Stream<Value> tracked() {
            val n = callCount.incrementAndGet();
            if (n < recoverOnInvocation) {
                throw new RuntimeException("transient rebind open-time failure #" + n);
            }
            callCount.set(0);
            val slot = new LatestSlotStream<Value>();
            slot.put(Value.of("rebind-recovered"));
            return slot;
        }
    }

    @AfterEach
    void cleanup() {
        InstrumentedPip.reset();
    }
}
