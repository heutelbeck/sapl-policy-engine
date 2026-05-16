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
package io.sapl.attributes.broker.layered;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * {@link AttributeBroker} that composes a {@code primary} broker and a
 * {@code fallback} broker with strict priority. For each consumer key
 * the layered broker maintains two value slots, one fed by each inner
 * broker, and emits the highest-priority non-{@link Value#UNDEFINED}
 * value to the consumer. When the primary slot is
 * {@code UNDEFINED}, the fallback's value wins. When both are
 * {@code UNDEFINED}, the consumer sees {@code UNDEFINED}.
 * <p>
 * The composition is realised through per-key, singleton-dependency
 * shallow subscriptions on the inner stores. Each shallow callback
 * updates its slot in the layered subscription; the combiner
 * recomputes the resolved snapshot and fires the consumer's callback
 * when the snapshot changes (and the all-deps gate is open).
 * <p>
 * The layered broker's gate opens for a consumer when every dependency
 * key has emitted at least once from both inner stores. Adding a key
 * via the consumer's dependency diff temporarily reopens the gate as
 * a new slot fills; emissions for the new key drive the gate closed
 * again immediately.
 * <p>
 * Close behaviour is cascading: closing the layered broker closes both
 * inner stores. {@link AttributeBroker#close} on the inner stores is
 * idempotent, so subsequent owner-driven closes are no-ops.
 *
 * @since 4.1.0
 */
@Slf4j
public final class LayeredAttributeBroker implements AttributeBroker {

    private static final String ERROR_DEPS_EMPTY             = "initialDependencies must not be empty";
    private static final String ERROR_RETURNED_DEPS_INVALID  = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK  = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE = "subscriptionId already open: %s";
    private static final String WARN_CALLBACK_THREW          = "Consumer {} onUpdate threw: {}";
    private static final String WARN_INNER_CLOSE_THREW       = "Inner broker close threw: {}";

    private final AttributeBroker primary;
    private final AttributeBroker fallback;

    private final Object                           lock      = new Object();
    private final Map<String, LayeredSubscription> consumers = new HashMap<>();
    private boolean                                closed    = false;

    public LayeredAttributeBroker(@NonNull AttributeBroker primary, @NonNull AttributeBroker fallback) {
        this.primary  = primary;
        this.fallback = fallback;
    }

    @Override
    public Subscription open(@NonNull String subscriptionId, @NonNull Set<SubscriptionKey> initialDependencies,
            @NonNull Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        if (subscriptionId.isBlank()) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_BLANK);
        }
        if (initialDependencies.isEmpty()) {
            throw new IllegalArgumentException(ERROR_DEPS_EMPTY);
        }

        LayeredSubscription consumer;
        synchronized (lock) {
            if (consumers.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            consumer = new LayeredSubscription(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            consumers.put(subscriptionId, consumer);
        }
        consumer.start();
        return consumer;
    }

    @Override
    public void close() {
        List<LayeredSubscription> toClose;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed  = true;
            toClose = new ArrayList<>(consumers.values());
            consumers.clear();
        }
        for (val c : toClose) {
            c.markClosed();
        }
        safeClose(primary);
        safeClose(fallback);
    }

    private static void safeClose(AttributeBroker broker) {
        try {
            broker.close();
        } catch (RuntimeException e) {
            log.warn(WARN_INNER_CLOSE_THREW, e.getMessage(), e);
        }
    }

    /**
     * Per-key value slots. The primary and fallback shallow
     * subscription handles, and the latest snapshot received from
     * each inner broker. {@code null} snapshots indicate "no emission
     * yet"; the gate stays closed until both are non-null.
     */
    private static final class Slot {
        @Nullable
        AttributeSnapshot primarySnapshot;
        @Nullable
        AttributeSnapshot fallbackSnapshot;
        @Nullable
        Subscription      primaryHandle;
        @Nullable
        Subscription      fallbackHandle;
    }

    private final class LayeredSubscription implements Subscription {

        private final String                                                                  id;
        private final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        private final ReentrantLock                                                           callbackLock = new ReentrantLock();
        private final Map<SubscriptionKey, Slot>                                              slots        = new HashMap<>();
        private Set<SubscriptionKey>                                                          currentDeps;
        private Map<SubscriptionKey, AttributeSnapshot>                                       lastEmitted  = Map.of();
        private boolean                                                                       closed       = false;

        LayeredSubscription(String id,
                Set<SubscriptionKey> currentDeps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id          = id;
            this.currentDeps = currentDeps;
            this.onUpdate    = onUpdate;
        }

        /**
         * Opens shallow subs for every initial dep on both inner
         * stores. Either inner broker may fire its shallow callback
         * synchronously during open; in that case the gate may open
         * and the consumer callback may fire before this method
         * returns, fulfilling the {@code AttributeBroker.open} contract.
         */
        void start() {
            List<SubscriptionKey> initialKeys;
            synchronized (lock) {
                initialKeys = new ArrayList<>(currentDeps);
                for (val key : initialKeys) {
                    slots.put(key, new Slot());
                }
            }
            for (val key : initialKeys) {
                openShallowSubs(key);
            }
        }

        @Override
        public void close() {
            List<Subscription> handlesToClose;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed = true;
                consumers.remove(id);
                handlesToClose = collectHandles();
                slots.clear();
            }
            for (val h : handlesToClose) {
                safeCloseSubscription(h);
            }
        }

        void markClosed() {
            List<Subscription> handlesToClose;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                closed         = true;
                handlesToClose = collectHandles();
                slots.clear();
            }
            for (val h : handlesToClose) {
                safeCloseSubscription(h);
            }
        }

        private List<Subscription> collectHandles() {
            val handles = new ArrayList<Subscription>(slots.size() * 2);
            for (val slot : slots.values()) {
                if (slot.primaryHandle != null) {
                    handles.add(slot.primaryHandle);
                }
                if (slot.fallbackHandle != null) {
                    handles.add(slot.fallbackHandle);
                }
            }
            return handles;
        }

        private void openShallowSubs(SubscriptionKey key) {
            val          primaryShallowId  = id + "/p/" + UUID.randomUUID();
            val          fallbackShallowId = id + "/f/" + UUID.randomUUID();
            Subscription primaryHandle;
            Subscription fallbackHandle;
            primaryHandle  = primary.open(primaryShallowId, Set.of(key),
                    snapshot -> onShallowUpdate(key, snapshot.get(key), Source.PRIMARY));
            fallbackHandle = fallback.open(fallbackShallowId, Set.of(key),
                    snapshot -> onShallowUpdate(key, snapshot.get(key), Source.FALLBACK));
            synchronized (lock) {
                val slot = slots.get(key);
                if (slot == null) {
                    // Subscription was closed between opening primary and
                    // opening fallback; tear them down right away.
                    safeCloseSubscription(primaryHandle);
                    safeCloseSubscription(fallbackHandle);
                    return;
                }
                slot.primaryHandle  = primaryHandle;
                slot.fallbackHandle = fallbackHandle;
            }
        }

        private Set<SubscriptionKey> onShallowUpdate(SubscriptionKey key, @Nullable AttributeSnapshot snapshot,
                Source source) {
            if (snapshot == null) {
                return Set.of(key);
            }
            boolean shouldFire = false;
            synchronized (lock) {
                val slot = slots.get(key);
                if (slot == null || closed) {
                    return Set.of(key);
                }
                if (source == Source.PRIMARY) {
                    slot.primarySnapshot = snapshot;
                } else {
                    slot.fallbackSnapshot = snapshot;
                }
                if (isGateOpenLocked()) {
                    val combined = computeCombinedLocked();
                    if (!combined.equals(lastEmitted)) {
                        lastEmitted = combined;
                        shouldFire  = true;
                    }
                }
            }
            if (shouldFire) {
                fireCallback();
            }
            return Set.of(key);
        }

        private boolean isGateOpenLocked() {
            for (val key : currentDeps) {
                val slot = slots.get(key);
                if (slot == null || slot.primarySnapshot == null || slot.fallbackSnapshot == null) {
                    return false;
                }
            }
            return true;
        }

        private Map<SubscriptionKey, AttributeSnapshot> computeCombinedLocked() {
            val combined = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(currentDeps.size());
            for (val key : currentDeps) {
                val slot = slots.get(key);
                if (slot == null) {
                    continue;
                }
                AttributeSnapshot resolved;
                if (slot.primarySnapshot != null && slot.primarySnapshot.value() != Value.UNDEFINED) {
                    resolved = slot.primarySnapshot;
                } else if (slot.fallbackSnapshot != null) {
                    resolved = slot.fallbackSnapshot;
                } else {
                    resolved = slot.primarySnapshot;
                }
                if (resolved != null) {
                    combined.put(key, resolved);
                }
            }
            return Map.copyOf(combined);
        }

        private void fireCallback() {
            callbackLock.lock();
            try {
                Map<SubscriptionKey, AttributeSnapshot>                                 snapshot;
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> callback;
                synchronized (lock) {
                    if (closed) {
                        return;
                    }
                    snapshot = lastEmitted;
                    callback = onUpdate;
                }
                Set<SubscriptionKey> newDeps;
                try {
                    newDeps = callback.apply(snapshot);
                } catch (RuntimeException e) {
                    log.warn(WARN_CALLBACK_THREW, id, e.getMessage(), e);
                    return;
                }
                if (newDeps == null || newDeps.isEmpty()) {
                    throw new IllegalStateException(ERROR_RETURNED_DEPS_INVALID.formatted(id));
                }
                applyDepDiff(newDeps);
            } finally {
                callbackLock.unlock();
            }
        }

        private void applyDepDiff(Set<SubscriptionKey> newDeps) {
            List<SubscriptionKey> toOpen;
            List<Subscription>    toClose;
            boolean               refire = false;
            synchronized (lock) {
                if (closed) {
                    return;
                }
                if (newDeps.equals(currentDeps)) {
                    return;
                }
                val added = new HashSet<>(newDeps);
                added.removeAll(currentDeps);
                val removed = new HashSet<>(currentDeps);
                removed.removeAll(newDeps);

                toOpen  = new ArrayList<>(added);
                toClose = new ArrayList<>(added.size() + removed.size());
                for (val key : added) {
                    slots.put(key, new Slot());
                }
                for (val key : removed) {
                    val slot = slots.remove(key);
                    if (slot != null) {
                        if (slot.primaryHandle != null) {
                            toClose.add(slot.primaryHandle);
                        }
                        if (slot.fallbackHandle != null) {
                            toClose.add(slot.fallbackHandle);
                        }
                    }
                }
                currentDeps = new HashSet<>(newDeps);
                if (isGateOpenLocked()) {
                    val combined = computeCombinedLocked();
                    if (!combined.equals(lastEmitted)) {
                        lastEmitted = combined;
                        refire      = true;
                    }
                }
            }
            for (val h : toClose) {
                safeCloseSubscription(h);
            }
            for (val key : toOpen) {
                openShallowSubs(key);
            }
            if (refire) {
                fireCallback();
            }
        }
    }

    private enum Source {
        PRIMARY,
        FALLBACK
    }

    private static void safeCloseSubscription(Subscription subscription) {
        try {
            subscription.close();
        } catch (RuntimeException e) {
            log.debug("Shallow subscription close threw: {}", e.getMessage());
        }
    }
}
