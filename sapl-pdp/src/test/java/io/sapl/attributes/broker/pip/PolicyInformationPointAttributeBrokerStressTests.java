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
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.val;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stress tests for the PIP attribute broker. Self-contained (no external
 * infrastructure), runs under Surefire alongside the regular unit tests.
 * <p>
 * Scope, per dimension:
 * <ul>
 * <li>10 PIPs, each contributing 10 attributes (100 attributes total)</li>
 * <li>thousands of concurrent broker subscriptions</li>
 * <li>each subscription holds many dependencies spread across PIPs</li>
 * <li>real PIP emissions driven by background virtual threads</li>
 * <li>hot swaps mid-flight</li>
 * <li>dep-set churn (consumer callbacks rotate their dep set)</li>
 * </ul>
 * <p>
 * Each test verifies a domain invariant under load rather than a specific
 * latency target. Correctness invariants prove themselves under chaos.
 * Performance numbers come from external load tools.
 */
@DisplayName("PolicyInformationPointAttributeBroker stress")
class PolicyInformationPointAttributeBrokerStressTests {

    private static final int NUMBER_OF_PIPS     = 10;
    private static final int CONSUMERS_BASELINE = 2_000;
    private static final int CONSUMERS_LARGE    = 10_000;
    private static final int DEPS_PER_CONSUMER  = 8;
    private static final int EMITTER_THREADS    = 8;

    private static final Duration TEST_BUDGET    = Duration.ofSeconds(30);
    private static final Duration CHURN_DURATION = Duration.ofSeconds(5);

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private PolicyInformationPointAttributeBroker broker;
    private List<StressPip>                       pips;

    @BeforeEach
    void setUp() {
        broker = new PolicyInformationPointAttributeBroker();
        pips   = loadFreshPipFleet();
    }

    @AfterEach
    void tearDown() {
        broker.close();
    }

    private List<StressPip> loadFreshPipFleet() {
        val fleet = List.of(new Pip00(), new Pip01(), new Pip02(), new Pip03(), new Pip04(), new Pip05(), new Pip06(),
                new Pip07(), new Pip08(), new Pip09());
        for (val pip : fleet) {
            broker.load(pip);
        }
        return new ArrayList<>(fleet);
    }

    @Test
    @DisplayName("baseline: thousands of subscriptions with many deps each receive emissions from every PIP")
    void baselineThroughput() {
        val recorders = openConsumers(CONSUMERS_BASELINE);

        emitOneRoundFromEveryPipAttribute();

        Awaitility.await().atMost(TEST_BUDGET)
                .untilAsserted(() -> assertThat(recorders)
                        .as("every consumer should fire at least once after the emission round")
                        .allMatch(r -> r.callbacks() >= 1));

        closeAll(recorders);
        assertEveryPipReachedZeroBackings();
    }

    @Test
    @DisplayName("scale: 10000 subscriptions tolerate concurrent emitter threads without deadlock")
    void highVolumeWithConcurrentEmitters() throws Exception {
        val recorders = openConsumers(CONSUMERS_LARGE);

        // Run the chaos storm for a fixed wall-clock window. The number of emissions
        // that fit inside this window is platform-dependent and irrelevant to the
        // contract. What the test verifies is (a) no deadlock under heavy concurrent
        // dispatch, (b) graceful drain when the emitters are signalled to stop, and
        // (c) the deterministic coverage round below reaches every consumer.
        val emitters = runEmittersFor(EMITTER_THREADS, CHURN_DURATION);
        assertThat(emitters.await(TEST_BUDGET.toSeconds(), TimeUnit.SECONDS))
                .as("emitter threads must drain after the storm window closes").isTrue();

        // The concurrent emitters chose attributes at random and may have missed some.
        // A deterministic coverage round after the storm guarantees every attribute
        // has at least one value so every consumer (which gates on all deps fulfilled)
        // fires at least once.
        emitOneRoundFromEveryPipAttribute();

        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> assertThat(recorders)
                .as("every consumer should have fired at least once").allMatch(r -> r.callbacks() >= 1));

        closeAll(recorders);
    }

    @Test
    @DisplayName("hot-swap under load: subscriptions survive replacing every PIP with a fresh instance")
    void hotSwapUnderLoad() throws Exception {
        val recorders = openConsumers(CONSUMERS_BASELINE);

        // Establish a baseline emission so every consumer has fired at least once.
        emitOneRoundFromEveryPipAttribute();
        Awaitility.await().atMost(TEST_BUDGET)
                .untilAsserted(() -> assertThat(recorders.stream().mapToLong(Recorder::callbacks).sum())
                        .isGreaterThanOrEqualTo(CONSUMERS_BASELINE));

        val firesBefore = new HashMap<Recorder, Long>();
        for (val rec : recorders) {
            firesBefore.put(rec, rec.callbacks());
        }

        // Swap every PIP. Active invocations rebind to the new instance. Pair
        // old handle to fresh instance by namespace because broker.catalog()
        // returns an unordered Set.
        val byNamespace = new HashMap<String, PipHandle>();
        for (val h : broker.catalog()) {
            byNamespace.put(h.pipName(), h);
        }
        val freshFleet = List.of(new Pip00(), new Pip01(), new Pip02(), new Pip03(), new Pip04(), new Pip05(),
                new Pip06(), new Pip07(), new Pip08(), new Pip09());
        for (val pip : freshFleet) {
            val ns = pip.getClass().getAnnotation(PolicyInformationPoint.class).name();
            broker.swap(byNamespace.get(ns), pip);
        }
        pips = new ArrayList<>(freshFleet);

        // Emit from the fresh fleet. Every consumer should fire again.
        emitOneRoundFromEveryPipAttribute();

        Awaitility.await().atMost(TEST_BUDGET)
                .untilAsserted(() -> assertThat(recorders).as("every consumer should fire again after the hot swap")
                        .allMatch(r -> r.callbacks() > firesBefore.get(r)));

        closeAll(recorders);
    }

    @Test
    @DisplayName("swap under load: PIPs swapped while emissions and dep-churn run leak nothing on evicted instances")
    void swapUnderConcurrentEmissionsAndChurn() throws Exception {
        // Track every instance the broker has ever served from, including the initial
        // fleet and every fresh one introduced by a swap. After all consumers close,
        // each instance must have opens == closes (every slot it handed out got
        // released).
        val allInstances = new CopyOnWriteArrayList<StressPip>(pips);
        val recorders    = openChurningConsumers(CONSUMERS_BASELINE);

        val stop           = new AtomicBoolean();
        val swapFailures   = new AtomicLong();
        val swapsCompleted = new AtomicLong();

        val swapper = Thread.ofVirtual().name("stress-swapper").start(() -> {
            val rng = new Random(99);
            while (!stop.get()) {
                try {
                    val handles = new ArrayList<>(broker.catalog());
                    if (handles.isEmpty()) {
                        // Inherently time-based: paces the churn rate, there is no condition to await.
                        Thread.sleep(20);
                        continue;
                    }
                    val target = handles.get(rng.nextInt(handles.size()));
                    val fresh  = newInstanceFor(target.pipName());
                    broker.swap(target, fresh);
                    allInstances.add(fresh);
                    swapsCompleted.incrementAndGet();
                    // Inherently time-based: paces the churn rate between swaps, no condition to
                    // await.
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    swapFailures.incrementAndGet();
                }
            }
        });

        // Concurrent emissions for the whole duration of the swap cycles. The
        // broker uses a fair ReentrantLock so tight emit loops cannot starve the
        // swapper.
        val emitterDone = new CountDownLatch(EMITTER_THREADS);
        for (int t = 0; t < EMITTER_THREADS; t++) {
            val threadIndex = t;
            Thread.ofVirtual().name("stress-emit-swap-" + t).start(() -> {
                val  rng = new Random(3000L + threadIndex);
                long n   = 0;
                while (!stop.get()) {
                    try {
                        val pip  = pips.get(rng.nextInt(pips.size()));
                        val attr = StressPip.ATTRIBUTES.get(rng.nextInt(StressPip.ATTRIBUTES.size()));
                        pip.emit(attr, Value.of(threadIndex * 1_000_000L + n));
                        n++;
                    } catch (RuntimeException ignored) {
                        // A swap may briefly leave the pips list referencing an instance whose
                        // handle has been replaced. Emitting on the old instance is a no-op
                        // because the broker has migrated away from it.
                    }
                }
                emitterDone.countDown();
            });
        }

        // Stress duration: drive the swapper for a fixed wall-clock window. The number
        // of swaps that fit inside this window is platform-dependent and irrelevant to
        // the contract. What matters is that the swaps that did happen leave the
        // broker and every evicted PIP instance in a clean state (opens == closes).
        Thread.sleep(CHURN_DURATION.toMillis());
        stop.set(true);
        swapper.join(TEST_BUDGET.toMillis());
        assertThat(emitterDone.await(TEST_BUDGET.toSeconds(), TimeUnit.SECONDS))
                .as("emitter threads must drain inside the budget").isTrue();

        closeAll(recorders);

        try {
            Awaitility.await().atMost(TEST_BUDGET)
                    .until(() -> allInstances.stream().mapToLong(p -> p.opens() - p.closes()).sum() == 0);
        } catch (ConditionTimeoutException e) {
            val leaks = new ArrayList<String>();
            for (int i = 0; i < allInstances.size(); i++) {
                val instance = allInstances.get(i);
                val delta    = instance.opens() - instance.closes();
                if (delta != 0) {
                    leaks.add(String.format("idx=%d ns=%s opens=%d closes=%d delta=%d", i, instance.instanceTag(),
                            instance.opens(), instance.closes(), delta));
                }
            }
            throw new AssertionError(String.format("leak (swaps=%d, instances=%d): %s", swapsCompleted.get(),
                    allInstances.size(), leaks), e);
        }
        assertThat(swapFailures.get()).as("no swap should fail under load").isZero();
    }

    @Test
    @DisplayName("load/unload under load: routing flips cleanly between repository fallback and PIPs")
    void loadUnloadFlipsBetweenRepositoryAndPipUnderLoad() throws Exception {
        // Swap to a broker that has a repository fallback so unmatched invocations
        // route to it. Start with no PIPs loaded so every initial dep is
        // repository-backed.
        broker.close();
        val repository = new InMemoryAttributeRepository();
        broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository);
        pips.clear();

        val recorders = openRoutingConsumers(CONSUMERS_BASELINE);

        val stop         = new AtomicBoolean();
        val loadFailures = new AtomicLong();
        val allInstances = new CopyOnWriteArrayList<StressPip>();
        // Maps namespace to its currently-loaded handle. Concurrent map because the
        // loader thread mutates it while the emitter thread reads it.
        val loaded = new ConcurrentHashMap<String, PipHandle>();

        val loader = Thread.ofVirtual().name("stress-load-cycle").start(() -> {
            val rng = new Random(33);
            while (!stop.get()) {
                try {
                    val pipIndex = rng.nextInt(NUMBER_OF_PIPS);
                    val ns       = namespaceFor(pipIndex);
                    val handle   = loaded.remove(ns);
                    if (handle != null) {
                        handle.unload();
                    } else {
                        val fresh = newInstanceFor(ns);
                        allInstances.add(fresh);
                        loaded.put(ns, broker.load(fresh));
                    }
                    // Inherently time-based: paces the load/unload churn rate, no condition to
                    // await.
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    loadFailures.incrementAndGet();
                }
            }
        });

        // Background: keep repository entries fresh for every key consumers depend on.
        // Until a PIP is loaded for that key, this is the only value the consumer sees.
        val repoPublisher = Thread.ofVirtual().name("stress-repo-pub").start(() -> {
            long n = 0;
            while (!stop.get()) {
                for (int pipIndex = 0; pipIndex < NUMBER_OF_PIPS; pipIndex++) {
                    for (val attr : StressPip.ATTRIBUTES) {
                        val fqn = fqn(pipIndex, attr);
                        repository.publish(RepositoryKey.fromInvocation(invocation(fqn)), Value.of("repo-" + n));
                    }
                }
                n++;
                try {
                    // Inherently time-based: paces the repository-refresh rate, no condition to
                    // await.
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        // Background: emit from whichever PIPs are currently loaded.
        val emitterDone = new CountDownLatch(EMITTER_THREADS);
        for (int t = 0; t < EMITTER_THREADS; t++) {
            val threadIndex = t;
            Thread.ofVirtual().name("stress-pip-emit-" + t).start(() -> {
                val  rng = new Random(4000L + threadIndex);
                long n   = 0;
                while (!stop.get()) {
                    val instances = new ArrayList<>(loaded.keySet());
                    if (!instances.isEmpty()) {
                        val ns       = instances.get(rng.nextInt(instances.size()));
                        val instance = findInstance(allInstances, ns);
                        if (instance != null) {
                            val attr = StressPip.ATTRIBUTES.get(rng.nextInt(StressPip.ATTRIBUTES.size()));
                            instance.emit(attr, Value.of("pip-" + threadIndex + "-" + n));
                            n++;
                        }
                    }
                    try {
                        // Inherently time-based: paces the emitter rate, no condition to await.
                        Thread.sleep(15);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                emitterDone.countDown();
            });
        }

        // Stress duration: drive load/unload churn for a fixed wall-clock window.
        // The cycle count is platform-dependent. What the test pins is the final
        // correctness state: after unloading every PIP, all consumers must observe
        // the repository sentinel for every dep. After reloading every PIP, all
        // consumers must observe the PIP sentinel. No PIP instance leaks slots.
        Thread.sleep(CHURN_DURATION.toMillis());
        stop.set(true);
        loader.join(TEST_BUDGET.toMillis());
        repoPublisher.join(TEST_BUDGET.toMillis());
        assertThat(emitterDone.await(TEST_BUDGET.toSeconds(), TimeUnit.SECONDS))
                .as("emitter threads must drain inside the budget").isTrue();

        // Force terminal state: unload every PIP. Every active invocation should now
        // be repository-backed.
        for (val handle : loaded.values()) {
            handle.unload();
        }
        loaded.clear();

        // Publish a sentinel value to every key. Every consumer must observe it for
        // every dep, proving the broker routed the unload-migrated active invocations
        // back to the repository.
        val sentinelRepo = Value.of("FINAL-REPO");
        for (int pipIndex = 0; pipIndex < NUMBER_OF_PIPS; pipIndex++) {
            for (val attr : StressPip.ATTRIBUTES) {
                repository.publish(RepositoryKey.fromInvocation(invocation(fqn(pipIndex, attr))), sentinelRepo);
            }
        }
        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> {
            for (val rec : recorders) {
                for (val key : rec.deps) {
                    assertThat(rec.latestValues.get(key))
                            .as("after unload every consumer dep must observe the repository sentinel")
                            .isEqualTo(sentinelRepo);
                }
            }
        });

        // Now load every PIP and emit a distinctive value from each. Consumers should
        // observe the PIP value, proving the broker promoted the repository-backed
        // active invocations to PIP-backed ones.
        val freshFleet = newFleet();
        allInstances.addAll(freshFleet);
        val finalHandles = new ArrayList<PipHandle>();
        for (val pip : freshFleet) {
            finalHandles.add(broker.load(pip));
        }
        val sentinelPip = Value.of("FINAL-PIP");
        for (val pip : freshFleet) {
            pip.emitAll(sentinelPip);
        }
        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> {
            for (val rec : recorders) {
                for (val key : rec.deps) {
                    assertThat(rec.latestValues.get(key))
                            .as("after load every consumer dep must observe the PIP sentinel").isEqualTo(sentinelPip);
                }
            }
        });

        closeAll(recorders);
        for (val h : finalHandles) {
            h.unload();
        }
        repository.close();

        assertThat(loadFailures.get()).as("no load/unload should fail under load").isZero();
        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> {
            for (val instance : allInstances) {
                assertThat(instance.opens() - instance.closes()).as("PIP instance %s leaked %d unclosed slots",
                        instance.instanceTag(), instance.opens() - instance.closes()).isZero();
            }
        });
    }

    @Test
    @DisplayName("dep-set churn: consumers that rotate their deps never stall, refcounts settle to zero on close")
    void dependencySetChurn() {
        val recorders = new ArrayList<ChurningRecorder>(CONSUMERS_BASELINE);
        val handles   = new ArrayList<AttributeBroker.Subscription>(CONSUMERS_BASELINE);
        val rng       = new Random(42);

        for (int i = 0; i < CONSUMERS_BASELINE; i++) {
            val initial = randomDeps(rng, DEPS_PER_CONSUMER);
            val rec     = new ChurningRecorder(initial, rng);
            recorders.add(rec);
            handles.add(broker.open("churn-" + i, initial, rec));
        }

        // Drive churn by emitting. Each consumer callback returns a freshly rotated dep
        // set,
        // forcing the broker to repeatedly diff and apply changes.
        for (int round = 0; round < 5; round++) {
            final int currentRound = round;
            for (val pip : pips) {
                pip.emitAll(Value.of("round-" + currentRound));
            }
            Awaitility.await().atMost(TEST_BUDGET).until(() -> recorders.stream().mapToLong(ChurningRecorder::callbacks)
                    .sum() >= (currentRound + 1L) * CONSUMERS_BASELINE);
        }

        for (val h : handles) {
            h.close();
        }

        // After every consumer has released its deps, no PIP should have lingering
        // active invocations.
        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> {
            for (val pip : pips) {
                assertThat(pip.liveBackingsTotal())
                        .as("PIP %s should have no live active invocations after close", pip.instanceTag()).isZero();
            }
        });
    }

    // helpers ---------------------------------------------------------------

    private List<Recorder> openConsumers(int count) {
        val rng       = new Random(7);
        val recorders = new ArrayList<Recorder>(count);
        for (int i = 0; i < count; i++) {
            val deps = randomDeps(rng, DEPS_PER_CONSUMER);
            val rec  = new Recorder(deps);
            rec.subscription = broker.open("c-" + i, deps, rec);
            recorders.add(rec);
        }
        return recorders;
    }

    private List<ChurningRecorder> openChurningConsumers(int count) {
        val rng       = new Random(11);
        val recorders = new ArrayList<ChurningRecorder>(count);
        for (int i = 0; i < count; i++) {
            val deps = randomDeps(rng, DEPS_PER_CONSUMER);
            val rec  = new ChurningRecorder(deps, rng);
            rec.subscription = broker.open("cc-" + i, deps, rec);
            recorders.add(rec);
        }
        return recorders;
    }

    private List<RoutingRecorder> openRoutingConsumers(int count) {
        val rng       = new Random(23);
        val recorders = new ArrayList<RoutingRecorder>(count);
        for (int i = 0; i < count; i++) {
            val deps = randomDeps(rng, DEPS_PER_CONSUMER);
            val rec  = new RoutingRecorder(deps);
            rec.subscription = broker.open("rc-" + i, deps, rec);
            recorders.add(rec);
        }
        return recorders;
    }

    private static String namespaceFor(int pipIndex) {
        return "p" + (pipIndex < 10 ? "0" + pipIndex : pipIndex);
    }

    private static String fqn(int pipIndex, String attribute) {
        return namespaceFor(pipIndex) + '.' + attribute;
    }

    private static StressPip newInstanceFor(String namespace) {
        val factory = PIP_FACTORIES.get(namespace);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown stress PIP namespace: " + namespace);
        }
        return factory.get();
    }

    private static List<StressPip> newFleet() {
        val fleet = new ArrayList<StressPip>(NUMBER_OF_PIPS);
        for (int i = 0; i < NUMBER_OF_PIPS; i++) {
            fleet.add(newInstanceFor(namespaceFor(i)));
        }
        return fleet;
    }

    private static StressPip findInstance(List<StressPip> instances, String namespace) {
        // Walk in reverse so we pick the most recently registered instance for the
        // namespace: under cycling load/unload only that one is currently catalogued.
        for (int i = instances.size() - 1; i >= 0; i--) {
            val candidate = instances.get(i);
            if (candidate.instanceTag().equals(namespace)) {
                return candidate;
            }
        }
        return null;
    }

    private static final Map<String, Supplier<StressPip>> PIP_FACTORIES;
    static {
        val factories = new HashMap<String, Supplier<StressPip>>();
        factories.put("p00", Pip00::new);
        factories.put("p01", Pip01::new);
        factories.put("p02", Pip02::new);
        factories.put("p03", Pip03::new);
        factories.put("p04", Pip04::new);
        factories.put("p05", Pip05::new);
        factories.put("p06", Pip06::new);
        factories.put("p07", Pip07::new);
        factories.put("p08", Pip08::new);
        factories.put("p09", Pip09::new);
        PIP_FACTORIES = Map.copyOf(factories);
    }

    private Set<SubscriptionKey> randomDeps(Random rng, int count) {
        val keys = new HashSet<SubscriptionKey>(count);
        while (keys.size() < count) {
            val pipIndex = rng.nextInt(NUMBER_OF_PIPS);
            val attrName = StressPip.ATTRIBUTES.get(rng.nextInt(StressPip.ATTRIBUTES.size()));
            val fqn      = "p" + (pipIndex < 10 ? "0" + pipIndex : pipIndex) + "." + attrName;
            keys.add(new SubscriptionKey(invocation(fqn), false));
        }
        return keys;
    }

    private void emitOneRoundFromEveryPipAttribute() {
        val counter = new AtomicLong();
        for (val pip : pips) {
            for (val attr : StressPip.ATTRIBUTES) {
                pip.emit(attr, Value.of(counter.incrementAndGet()));
            }
        }
    }

    private CountDownLatch runEmittersFor(int threads, Duration window) {
        val done = new CountDownLatch(threads);
        val stop = new AtomicBoolean();
        Thread.ofVirtual().name("stress-emitter-stopper").start(() -> {
            try {
                // Inherently time-based: the stress window is a fixed duration, not a condition
                // to await.
                Thread.sleep(window.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stop.set(true);
        });
        for (int t = 0; t < threads; t++) {
            val threadIndex = t;
            Thread.ofVirtual().name("stress-emitter-" + t).start(() -> {
                val  rng = new Random(1000L + threadIndex);
                long n   = 0;
                while (!stop.get()) {
                    val pip  = pips.get(rng.nextInt(pips.size()));
                    val attr = StressPip.ATTRIBUTES.get(rng.nextInt(StressPip.ATTRIBUTES.size()));
                    pip.emit(attr, Value.of(threadIndex * 1_000_000L + n));
                    n++;
                }
                done.countDown();
            });
        }
        return done;
    }

    private void closeAll(List<? extends Recorder> recorders) {
        for (val r : recorders) {
            r.subscription.close();
        }
    }

    private void assertEveryPipReachedZeroBackings() {
        Awaitility.await().atMost(TEST_BUDGET).untilAsserted(() -> {
            for (val pip : pips) {
                assertThat(pip.liveBackingsTotal())
                        .as("PIP %s should have no live active invocations", pip.instanceTag()).isZero();
            }
        });
    }

    private static AttributeFinderInvocation invocation(String fqn) {
        return new AttributeFinderInvocation("test-pdp", "default", fqn, List.of(), Duration.ofSeconds(10),
                Duration.ofSeconds(30), Duration.ofMillis(50), 1L, false, EMPTY_CTX);
    }

    // recorder types --------------------------------------------------------

    private class Recorder implements Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> {
        final Set<SubscriptionKey>            deps;
        final AtomicInteger                   fires = new AtomicInteger();
        volatile AttributeBroker.Subscription subscription;

        Recorder(Set<SubscriptionKey> deps) {
            this.deps = deps;
        }

        long callbacks() {
            return fires.get();
        }

        @Override
        public Set<SubscriptionKey> apply(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
            fires.incrementAndGet();
            return deps;
        }
    }

    private class ChurningRecorder extends Recorder {
        private final Random         rng;
        private Set<SubscriptionKey> nextDeps;

        ChurningRecorder(Set<SubscriptionKey> initial, Random rng) {
            super(initial);
            this.rng      = rng;
            this.nextDeps = initial;
        }

        @Override
        public synchronized Set<SubscriptionKey> apply(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
            fires.incrementAndGet();
            val deps = nextDeps;
            // Prepare the next round's deps so the broker has to apply a non-trivial diff.
            nextDeps = randomDeps(rng, DEPS_PER_CONSUMER);
            return deps;
        }
    }

    /**
     * Recorder that captures the most recent value seen per dep key. Used by the
     * routing test to assert which source (PIP or repository fallback) is feeding
     * each consumer after a load/unload transition.
     */
    private class RoutingRecorder extends Recorder {
        final Map<SubscriptionKey, Value> latestValues = new ConcurrentHashMap<>();

        RoutingRecorder(Set<SubscriptionKey> deps) {
            super(deps);
        }

        @Override
        public Set<SubscriptionKey> apply(Map<SubscriptionKey, AttributeSnapshot> snapshot) {
            snapshot.forEach((key, snap) -> latestValues.put(key, snap.value()));
            return super.apply(snapshot);
        }
    }

    // 10 namespaced PIPs ----------------------------------------------------

    @PolicyInformationPoint(name = "p00")
    public static final class Pip00 extends StressPip {
        public Pip00() {
            super("p00");
        }
    }

    @PolicyInformationPoint(name = "p01")
    public static final class Pip01 extends StressPip {
        public Pip01() {
            super("p01");
        }
    }

    @PolicyInformationPoint(name = "p02")
    public static final class Pip02 extends StressPip {
        public Pip02() {
            super("p02");
        }
    }

    @PolicyInformationPoint(name = "p03")
    public static final class Pip03 extends StressPip {
        public Pip03() {
            super("p03");
        }
    }

    @PolicyInformationPoint(name = "p04")
    public static final class Pip04 extends StressPip {
        public Pip04() {
            super("p04");
        }
    }

    @PolicyInformationPoint(name = "p05")
    public static final class Pip05 extends StressPip {
        public Pip05() {
            super("p05");
        }
    }

    @PolicyInformationPoint(name = "p06")
    public static final class Pip06 extends StressPip {
        public Pip06() {
            super("p06");
        }
    }

    @PolicyInformationPoint(name = "p07")
    public static final class Pip07 extends StressPip {
        public Pip07() {
            super("p07");
        }
    }

    @PolicyInformationPoint(name = "p08")
    public static final class Pip08 extends StressPip {
        public Pip08() {
            super("p08");
        }
    }

    @PolicyInformationPoint(name = "p09")
    public static final class Pip09 extends StressPip {
        public Pip09() {
            super("p09");
        }
    }
}
