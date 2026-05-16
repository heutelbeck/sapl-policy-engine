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
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryAttributeStore")
class InMemoryAttributeStoreTests {

    private InMemoryAttributeStore store;

    @BeforeEach
    void setUp() {
        ControllablePip.reset();
        store = new InMemoryAttributeStore();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static AttributeFinderInvocation envInvocation(String fqn) {
        return envInvocation(fqn, false, List.of());
    }

    private static AttributeFinderInvocation envInvocation(String fqn, boolean fresh) {
        return envInvocation(fqn, fresh, List.of());
    }

    private static AttributeFinderInvocation envInvocation(String fqn, boolean fresh, List<Value> arguments) {
        return new AttributeFinderInvocation("default", fqn, arguments, Duration.ofSeconds(1), Duration.ofMillis(100),
                Duration.ofMillis(100), 0L, fresh,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static SubscriptionKey envKey(String fqn) {
        return new SubscriptionKey(envInvocation(fqn), false);
    }

    private static SubscriptionKey envKey(String fqn, boolean fresh, boolean head) {
        return new SubscriptionKey(envInvocation(fqn, fresh), head);
    }

    private static class Recorder {
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

    private static Value valueFor(SubscriptionKey key, Recorder r, int snapshotIndex) {
        return r.snapshots.get(snapshotIndex).get(key).value();
    }

    private static void simulateSlowCallback(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // =================================================================
    // Catalog management (admin surface)
    // =================================================================

    @Nested
    @DisplayName("Catalog: load happy paths")
    class CatalogLoadHappyPaths {

        @Test
        @DisplayName("loads a PIP with a single static method and exposes it via catalog and resolve")
        void whenLoadingStaticOnlyPipThenAvailable() {
            val handle = store.load(new StaticPip());

            assertThat(handle.pipName()).isEqualTo("static");
            assertThat(handle.isLoaded()).isTrue();
            assertThat(store.catalog()).containsExactly(handle);
            assertThat(store.resolve(envInvocation("static.greeting"))).isPresent();
        }

        @Test
        @DisplayName("loads a PIP with instance methods bound to the instance")
        void whenLoadingInstancePipThenBound() {
            val handle = store.load(new InstancePip());

            assertThat(handle.pipName()).isEqualTo("instance");
            assertThat(store.resolve(envInvocation("instance.identity"))).isPresent();
        }

        @Test
        @DisplayName("loads a PIP mixing static and instance methods on the same class")
        void whenLoadingMixedPipThenBothShapesRegister() {
            store.load(new MixedPip());

            assertThat(store.resolve(envInvocation("mixed.staticPart"))).isPresent();
            assertThat(store.resolve(envInvocation("mixed.instancePart"))).isPresent();
        }

        @Test
        @DisplayName("loading a PIP with no annotated methods returns a handle but registers no specs")
        void whenLoadingPipWithoutAttributesThenHandleButNoSpecs() {
            val handle = store.load(new EmptyPip());

            assertThat(store.catalog()).containsExactly(handle);
            assertThat(store.resolve(envInvocation("empty.foo"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Catalog: failure modes are atomic")
    class CatalogAtomicity {

        @Test
        @DisplayName("instance with no @PolicyInformationPoint annotation throws and leaves catalog unchanged")
        void whenAnnotationMissingThenThrowsAndCatalogUnchanged() {
            store.load(new StaticPip());
            val before   = store.catalog();
            val unloaded = new Object();

            assertThatThrownBy(() -> store.load(unloaded)).isInstanceOf(PipLoadException.class)
                    .hasMessageContaining("not annotated");
            assertThat(store.catalog()).isEqualTo(before);
        }

        @Test
        @DisplayName("instance with an invalid attribute method signature throws and leaves catalog unchanged")
        void whenInvalidSignatureThenThrowsAndNoPartialRegistration() {
            val before  = store.catalog();
            val invalid = new InvalidReturnTypePip();

            assertThatThrownBy(() -> store.load(invalid)).isInstanceOf(PipLoadException.class);
            assertThat(store.catalog()).isEqualTo(before);
            assertThat(store.resolve(envInvocation("invalidreturn.good"))).isEmpty();
        }

        @Test
        @DisplayName("collision with already-loaded PIP throws and leaves catalog unchanged")
        void whenCollisionThenThrowsAndExistingHandleIntact() {
            val handleA   = store.load(new StaticPip());
            val colliding = new CollidingStaticPip();

            assertThatThrownBy(() -> store.load(colliding)).isInstanceOf(PipLoadException.class)
                    .hasMessageContaining("collides");
            assertThat(store.catalog()).containsExactly(handleA);
            assertThat(handleA.isLoaded()).isTrue();
        }

        @Test
        @DisplayName("partial-good then-collision PIP rolls back fully (first attribute also not registered)")
        void whenSecondAttributeCollidesThenFirstAlsoNotRegistered() {
            store.load(new StaticPip());
            val partial = new PipWithGoodAttributeAndColliding();

            assertThatThrownBy(() -> store.load(partial)).isInstanceOf(PipLoadException.class);
            assertThat(store.resolve(envInvocation("static.newAttribute"))).as("rolled back attribute should be absent")
                    .isEmpty();
            assertThat(store.resolve(envInvocation("static.greeting"))).as("existing PIP unaffected").isPresent();
        }
    }

    @Nested
    @DisplayName("Catalog: unload")
    class CatalogUnload {

        @Test
        @DisplayName("unload removes specs from catalog and marks handle inactive")
        void whenUnloadingThenSpecsRemoved() {
            val handle = store.load(new StaticPip());
            handle.unload();

            assertThat(handle.isLoaded()).isFalse();
            assertThat(store.catalog()).isEmpty();
            assertThat(store.resolve(envInvocation("static.greeting"))).isEmpty();
        }

        @Test
        @DisplayName("unload is idempotent")
        void whenUnloadingTwiceThenNoError() {
            val handle = store.load(new StaticPip());
            handle.unload();
            handle.unload();

            assertThat(handle.isLoaded()).isFalse();
        }

        @Test
        @DisplayName("after unload, the same name can be loaded again")
        void whenReloadingAfterUnloadThenSucceeds() {
            store.load(new StaticPip()).unload();

            val handle2 = store.load(new StaticPip());
            assertThat(handle2.isLoaded()).isTrue();
            assertThat(store.resolve(envInvocation("static.greeting"))).isPresent();
        }
    }

    @Nested
    @DisplayName("Catalog: resolve match priority")
    class CatalogResolve {

        @Test
        @DisplayName("EXACT match preferred over VARARGS for same arity")
        void whenExactAndVarargsAvailableThenExactWins() {
            store.load(new ExactPip());
            store.load(new VarargsPip());

            val resolved = store.resolve(envInvocation("exactvarargs.foo", false, List.of(Value.of("a"))));
            assertThat(resolved).isPresent();
            assertThat(resolved.get().fullyQualifiedName()).isEqualTo("exactvarargs.foo");
            assertThat(resolved.get().hasVariableNumberOfArguments()).isFalse();
        }

        @Test
        @DisplayName("VARARGS match used when no exact-arity spec exists")
        void whenOnlyVarargsAvailableThenItMatches() {
            store.load(new VarargsPip());

            val resolved = store
                    .resolve(envInvocation("exactvarargs.foo", false, List.of(Value.of("a"), Value.of("b"))));
            assertThat(resolved).isPresent();
            assertThat(resolved.get().hasVariableNumberOfArguments()).isTrue();
        }

        @Test
        @DisplayName("no match returns empty")
        void whenNoSpecMatchesThenEmpty() {
            store.load(new StaticPip());

            assertThat(store.resolve(envInvocation("static.unknown"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Catalog: swap")
    class CatalogSwap {

        @Test
        @DisplayName("swap replaces specs and yields a new handle while marking the old one unloaded")
        void whenSwappingThenNewHandleActiveOldInactive() {
            val v1 = store.load(new InstancePip());
            val v2 = store.swap(v1, new InstancePip());

            assertThat(v1.isLoaded()).isFalse();
            assertThat(v2.isLoaded()).isTrue();
            assertThat(store.catalog()).containsExactly(v2);
            assertThat(store.resolve(envInvocation("instance.identity"))).isPresent();
        }

        @Test
        @DisplayName("swap atomically rejects if new specs would collide with another loaded PIP, leaving original intact")
        void whenSwapWouldCollideThenThrowsAndOriginalIntact() {
            val a         = store.load(new StaticPip());
            val target    = store.load(new InstancePip());
            val colliding = new CollidingStaticPip();

            assertThatThrownBy(() -> store.swap(target, colliding)).isInstanceOf(PipLoadException.class);
            assertThat(target.isLoaded()).isTrue();
            assertThat(a.isLoaded()).isTrue();
        }

        @Test
        @DisplayName("swap does NOT collide with the old handle's own specs (you can swap like-for-like)")
        void whenSwappingWithSameShapeThenAllowed() {
            val v1 = store.load(new StaticPip());
            val v2 = store.swap(v1, new StaticPip());

            assertThat(v1.isLoaded()).isFalse();
            assertThat(v2.isLoaded()).isTrue();
            assertThat(store.resolve(envInvocation("static.greeting"))).isPresent();
        }
    }

    // =================================================================
    // Consumer surface (the AttributeStore interface)
    // =================================================================

    @Nested
    @DisplayName("Open/close lifecycle")
    class OpenCloseLifecycle {

        @Test
        @DisplayName("blank subscriptionId throws")
        void whenBlankIdThenIllegalArgument() {
            val deps = Set.of(envKey("static.greeting"));

            assertThatThrownBy(() -> store.open("", deps, s -> deps)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("empty deps throws")
        void whenEmptyDepsThenIllegalArgument() {
            val emptyDeps = Set.<SubscriptionKey>of();

            assertThatThrownBy(() -> store.open("s1", emptyDeps, s -> emptyDeps))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("subscriptionId collision while still open throws")
        void whenIdCollisionThenIllegalArgument() {
            store.load(new ConstantPip());
            val deps = Set.of(envKey("constant.value"));
            store.open("s1", deps, s -> deps);

            assertThatThrownBy(() -> store.open("s1", deps, s -> deps)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("subscription id reusable after close")
        void whenReuseIdAfterCloseThenOk() {
            store.load(new ConstantPip());
            val deps = Set.of(envKey("constant.value"));
            val sub  = store.open("s1", deps, s -> deps);
            sub.close();

            val sub2 = store.open("s1", deps, s -> deps);
            sub2.close();
        }
    }

    @Nested
    @DisplayName("Gate")
    class Gate {

        @Test
        @DisplayName("single-dep PIP with immediate value fires gate synchronously on open")
        void whenSingleDepReadyThenFiresImmediately() {
            store.load(new ConstantPip());
            val key      = envKey("constant.value");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.of("constant"));
            }
        }

        @Test
        @DisplayName("multi-dep gate stays closed until last dep fires")
        void whenMultiDepThenGateOpensWhenAllReady() {
            store.load(new ConstantPip());
            store.load(new ControllablePip());
            val constantKey = envKey("constant.value");
            val ctrlKey     = envKey("ctrl.latest");
            val recorder    = new Recorder(Set.of(constantKey, ctrlKey));

            try (val sub = store.open("s1", Set.of(constantKey, ctrlKey), recorder.asCallback())) {
                Awaitility.await().pollDelay(Duration.ofMillis(150)).atMost(Duration.ofMillis(250))
                        .untilAsserted(() -> assertThat(recorder.snapshots).isEmpty());

                ControllablePip.emitToAll(Value.of("ctrl1"));

                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                assertThat(valueFor(constantKey, recorder, 0)).isEqualTo(Value.of("constant"));
                assertThat(valueFor(ctrlKey, recorder, 0)).isEqualTo(Value.of("ctrl1"));
            }
        }

        @Test
        @DisplayName("ErrorValue counts as a fulfilled value (gate opens with it in the snapshot)")
        void whenErrorValueThenGateOpens() {
            store.load(new ErroringPip());
            val key      = envKey("erroring.always");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                assertThat(valueFor(key, recorder, 0)).isInstanceOf(ErrorValue.class);
            }
        }

        @Test
        @DisplayName("bare-Value PIP returning Java null surfaces an ErrorValue (gate opens, no hang)")
        void whenBareValueReturnsJavaNullThenErrorValue() {
            store.load(new NullReturningPip());
            val key      = envKey("nullpip.value");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                assertThat(valueFor(key, recorder, 0)).isInstanceOfSatisfying(ErrorValue.class,
                        e -> assertThat(e.message()).contains("returned Java null"));
            }
        }
    }

    @Nested
    @DisplayName("Sharing and fresh")
    class SharingAndFresh {

        @Test
        @DisplayName("two consumers same invocation, fresh=false: share one backing")
        void whenSameInvocationFreshFalseThenShareBacking() {
            store.load(new ControllablePip());
            val key = envKey("ctrl.latest", false, false);

            val r1   = new Recorder(Set.of(key));
            val r2   = new Recorder(Set.of(key));
            val sub1 = store.open("s1", Set.of(key), r1.asCallback());
            val sub2 = store.open("s2", Set.of(key), r2.asCallback());

            assertThat(ControllablePip.STREAMS).hasSize(1);

            ControllablePip.emitToAll(Value.of("shared"));
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(r1.snapshots).hasSizeGreaterThanOrEqualTo(1);
                assertThat(r2.snapshots).hasSizeGreaterThanOrEqualTo(1);
            });
            assertThat(valueFor(key, r1, 0)).isEqualTo(Value.of("shared"));
            assertThat(valueFor(key, r2, 0)).isEqualTo(Value.of("shared"));

            sub1.close();
            sub2.close();
        }

        @Test
        @DisplayName("fresh=true creates a private backing not shared with fresh=false consumers")
        void whenFreshTrueThenPrivateBacking() {
            store.load(new ControllablePip());
            val sharedKey = envKey("ctrl.latest", false, false);
            val freshKey  = envKey("ctrl.latest", true, false);

            val r1 = new Recorder(Set.of(sharedKey));
            val r2 = new Recorder(Set.of(freshKey));
            val s1 = store.open("s1", Set.of(sharedKey), r1.asCallback());
            val s2 = store.open("s2", Set.of(freshKey), r2.asCallback());

            assertThat(ControllablePip.STREAMS).hasSize(2);

            s1.close();
            s2.close();
        }
    }

    @Nested
    @DisplayName("Head")
    class Head {

        @Test
        @DisplayName("head=true freezes at first value; head=false follows updates")
        void whenHeadTrueThenFrozenWhileHeadFalseUpdates() {
            store.load(new ControllablePip());
            val freshKey  = envKey("ctrl.latest", true, false);
            val frozenKey = envKey("ctrl.latest", false, true);

            val rFresh  = new Recorder(Set.of(freshKey));
            val rFrozen = new Recorder(Set.of(frozenKey));
            store.open("fresh", Set.of(freshKey), rFresh.asCallback());
            store.open("frozen", Set.of(frozenKey), rFrozen.asCallback());

            ControllablePip.emitToAll(Value.of("v1"));
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                assertThat(rFresh.snapshots).hasSizeGreaterThanOrEqualTo(1);
                assertThat(rFrozen.snapshots).hasSizeGreaterThanOrEqualTo(1);
            });
            val freshSnapsAfterFirst = rFresh.snapshots.size();

            ControllablePip.emitToAll(Value.of("v2"));
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(rFresh.snapshots.size()).isGreaterThan(freshSnapsAfterFirst));
            assertThat(valueFor(freshKey, rFresh, rFresh.snapshots.size() - 1)).isEqualTo(Value.of("v2"));
            for (val s : rFrozen.snapshots) {
                assertThat(s.get(frozenKey).value()).isEqualTo(Value.of("v1"));
            }
        }
    }

    @Nested
    @DisplayName("Errors and missing PIPs")
    class ErrorsAndMissing {

        @Test
        @DisplayName("invocation with no loaded PIP yields UNDEFINED immediately")
        void whenNoLoadedPipThenUndefinedImmediately() {
            val key      = envKey("nothing.here");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                assertThat(valueFor(key, recorder, 0)).isEqualTo(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("Hot-unload during a live subscription")
    class HotUnload {

        @Test
        @DisplayName("PIP unload while consumer subscribed publishes UNDEFINED to mailbox")
        void whenUnloadDuringSubscriptionThenUndefinedDelivered() {
            val handle   = store.load(new ControllablePip());
            val key      = envKey("ctrl.latest");
            val recorder = new Recorder(Set.of(key));
            val sub      = store.open("s1", Set.of(key), recorder.asCallback());

            ControllablePip.emitToAll(Value.of("v1"));
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
            val before = recorder.snapshots.size();

            handle.unload();

            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(recorder.snapshots.size()).isGreaterThan(before));
            val last = recorder.snapshots.get(recorder.snapshots.size() - 1).get(key).value();
            assertThat(last).isEqualTo(Value.UNDEFINED);

            sub.close();
        }
    }

    @Nested
    @DisplayName("Hot-swap preserves mailbox")
    class HotSwap {

        @Test
        @DisplayName("swap with same attribute does NOT publish a transient ErrorValue to the mailbox")
        void whenSwappingSameShapeThenNoTransientError() {
            val v1       = store.load(new ControllablePip());
            val key      = envKey("ctrl.latest");
            val recorder = new Recorder(Set.of(key));
            val sub      = store.open("s1", Set.of(key), recorder.asCallback());

            ControllablePip.emitToAll(Value.of("v1"));
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));

            store.swap(v1, new ControllablePip());

            ControllablePip.emitToAll(Value.of("v2"));
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(recorder.snapshots)
                    .anySatisfy(s -> assertThat(s.get(key).value()).isEqualTo(Value.of("v2"))));

            for (val snap : recorder.snapshots) {
                assertThat(snap.get(key).value()).isNotInstanceOf(ErrorValue.class);
            }

            sub.close();
        }
    }

    @Nested
    @DisplayName("Subscription and store close")
    class CloseLifecycle {

        @Test
        @DisplayName("closing the only consumer of a backing tears it down (refcount=0)")
        void whenLastConsumerClosesThenBackingTornDown() {
            store.load(new ControllablePip());
            val key      = envKey("ctrl.latest");
            val recorder = new Recorder(Set.of(key));

            val sub = store.open("s1", Set.of(key), recorder.asCallback());
            assertThat(ControllablePip.STREAMS).hasSize(1);

            sub.close();

            val recorder2 = new Recorder(Set.of(key));
            val sub2      = store.open("s2", Set.of(key), recorder2.asCallback());
            assertThat(ControllablePip.STREAMS).hasSizeGreaterThanOrEqualTo(2);
            sub2.close();
        }

        @Test
        @DisplayName("Subscription.close is idempotent")
        void whenSubscriptionClosedTwiceThenOk() {
            store.load(new ConstantPip());
            val sub = store.open("s1", Set.of(envKey("constant.value")), s -> Set.of(envKey("constant.value")));

            sub.close();
            sub.close();
        }

        @Test
        @DisplayName("Store.close releases all subscriptions and backings")
        void whenStoreCloseThenEverythingReleased() {
            store.load(new ControllablePip());
            val key      = envKey("ctrl.latest");
            val recorder = new Recorder(Set.of(key));
            store.open("s1", Set.of(key), recorder.asCallback());

            store.close();
            store.close(); // idempotent
        }
    }

    @Nested
    @DisplayName("Stream completion")
    class StreamCompletion {

        @Test
        @DisplayName("PIP stream completes after first emission; mailbox value persists")
        void whenStreamCompletesThenLastValueRetained() {
            store.load(new ConstantPip());
            val key      = envKey("constant.value");
            val recorder = new Recorder(Set.of(key));

            try (val sub = store.open("s1", Set.of(key), recorder.asCallback())) {
                Awaitility.await().atMost(Duration.ofSeconds(2))
                        .untilAsserted(() -> assertThat(recorder.snapshots).hasSizeGreaterThanOrEqualTo(1));
                Awaitility.await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofMillis(200))
                        .untilAsserted(() -> {
                            val last = recorder.snapshots.get(recorder.snapshots.size() - 1).get(key).value();
                            assertThat(last).isEqualTo(Value.of("constant"));
                        });
            }
        }
    }

    @Nested
    @DisplayName("Threading: per-consumer callback serialization")
    class Threading {

        @Test
        @DisplayName("rapid pushes to one backing fire the consumer's callback serially (never concurrently)")
        void whenRapidPushesThenCallbackNeverConcurrent() {
            store.load(new ControllablePip());
            val key                 = envKey("ctrl.latest");
            val concurrentEntries   = new AtomicInteger();
            val maxConcurrent       = new AtomicInteger();
            val concurrencyDetected = new AtomicBoolean(false);

            try (val sub = store.open("s1", Set.of(key), snapshot -> {
                val cur = concurrentEntries.incrementAndGet();
                if (cur > 1) {
                    concurrencyDetected.set(true);
                }
                maxConcurrent.accumulateAndGet(cur, Math::max);
                try {
                    simulateSlowCallback(5);
                } finally {
                    concurrentEntries.decrementAndGet();
                }
                return Set.of(key);
            })) {
                for (int i = 0; i < 50; i++) {
                    ControllablePip.emitToAll(Value.of(i));
                }
                Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> concurrentEntries.get() == 0);
            }

            assertThat(concurrencyDetected).isFalse();
            assertThat(maxConcurrent.get()).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------
    // Test PIP fixtures
    // ----------------------------------------------------------------

    @PolicyInformationPoint(name = "constant")
    static class ConstantPip {
        @EnvironmentAttribute
        public static Value value() {
            return Value.of("constant");
        }
    }

    @PolicyInformationPoint(name = "erroring")
    static class ErroringPip {
        @EnvironmentAttribute
        public static Value always() {
            return Value.error("always-fail");
        }
    }

    @PolicyInformationPoint(name = "nullpip")
    static class NullReturningPip {
        @EnvironmentAttribute
        public static Value value() {
            return null;
        }
    }

    @PolicyInformationPoint(name = "static")
    static class StaticPip {
        @EnvironmentAttribute
        public static Value greeting() {
            return Value.of("hello");
        }
    }

    @PolicyInformationPoint(name = "instance")
    static class InstancePip {
        @EnvironmentAttribute
        public Stream<Value> identity(AttributeAccessContext ctx) {
            return Streams.just(Value.of("instance"));
        }
    }

    @PolicyInformationPoint(name = "mixed")
    static class MixedPip {
        @EnvironmentAttribute
        public static Value staticPart() {
            return Value.of(1);
        }

        @EnvironmentAttribute
        public Stream<Value> instancePart() {
            return Streams.just(Value.of(2));
        }
    }

    @PolicyInformationPoint(name = "empty")
    static class EmptyPip {
        public static int unrelated() {
            return 0;
        }
    }

    /** Same FQN + arity as {@link StaticPip#greeting()}. */
    @PolicyInformationPoint(name = "static")
    static class CollidingStaticPip {
        @EnvironmentAttribute
        public static Value greeting() {
            return Value.of("conflict");
        }
    }

    @PolicyInformationPoint(name = "static")
    static class PipWithGoodAttributeAndColliding {
        @EnvironmentAttribute
        public static Value newAttribute() {
            return Value.of("fine");
        }

        @EnvironmentAttribute(name = "greeting")
        public static Value greetingThatCollides() {
            return Value.of("collides");
        }
    }

    @PolicyInformationPoint(name = "invalidreturn")
    static class InvalidReturnTypePip {
        @EnvironmentAttribute
        public static Value good() {
            return Value.of("ok");
        }

        @EnvironmentAttribute
        public static int bad() {
            return 0;
        }
    }

    @PolicyInformationPoint(name = "exactvarargs")
    static class ExactPip {
        @EnvironmentAttribute(name = "foo")
        public static Value fooExact(TextValue first) {
            return first;
        }
    }

    @PolicyInformationPoint(name = "exactvarargs")
    static class VarargsPip {
        @EnvironmentAttribute(name = "foo")
        public static Value fooVarargs(TextValue... rest) {
            return Value.of(rest.length);
        }
    }

    /**
     * Each invocation creates a fresh {@link LatestSlotStream}; the
     * test pushes via {@link #emitToAll} to deliver values to all
     * currently open backing subscriptions.
     */
    @PolicyInformationPoint(name = "ctrl")
    static class ControllablePip {
        static final List<LatestSlotStream<Value>> STREAMS = new CopyOnWriteArrayList<>();

        static void reset() {
            for (val s : new ArrayList<>(STREAMS)) {
                try {
                    s.close();
                } catch (RuntimeException ignored) {
                    // best-effort cleanup
                }
            }
            STREAMS.clear();
        }

        static void emitToAll(Value v) {
            for (val s : STREAMS) {
                s.put(v);
            }
        }

        @EnvironmentAttribute
        public Stream<Value> latest() {
            val s = new LatestSlotStream<Value>();
            STREAMS.add(s);
            return s;
        }
    }
}
