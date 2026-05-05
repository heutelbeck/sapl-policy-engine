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

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.compiler.eval.AttributeStore;
import lombok.val;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * In-memory {@link AttributeStore} for tests. Drives snapshot evolution
 * via {@link #publish(SubscriptionKey, Value)} and the by-name overload;
 * fires per-subscription callbacks when a published value flips a
 * subscription's gate from closed to open or arrives for an
 * already-gated subscription.
 * <p>
 * Gate semantic: a subscription's gate stays closed until every
 * declared dependency has a published value in the mailbox. The first
 * publish that completes the dep set opens the gate and fires the
 * callback once. Subsequent publishes to any subscribed key fire the
 * callback again with the latest snapshot.
 * <p>
 * State mutations and reads on the store and its subscriptions are
 * guarded by an intrinsic lock on the store. Callbacks fire outside the
 * lock so they may freely close their subscription or invoke other
 * store-touching operations without re-entrance hazard.
 * Per-subscription serialisation is achieved by collecting the set of
 * subscriptions whose gate is open under the store lock then firing
 * each callback sequentially (single thread of fire per publish call).
 */
public final class TestAttributeStore implements AttributeStore {

    private static final String ERROR_INITIAL_DEPS_EMPTY     = "initialDependencies must not be empty";
    private static final String ERROR_RETURNED_DEPS_EMPTY    = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK  = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE = "subscriptionId already open: %s";

    private final Map<SubscriptionKey, AttributeSnapshot> mailbox = new HashMap<>();
    private final Map<String, SubscriptionImpl>           subs    = new HashMap<>();

    @Override
    public Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_BLANK);
        }
        if (initialDependencies == null || initialDependencies.isEmpty()) {
            throw new IllegalArgumentException(ERROR_INITIAL_DEPS_EMPTY);
        }
        SubscriptionImpl sub;
        boolean          fireImmediately;
        synchronized (this) {
            if (subs.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            sub = new SubscriptionImpl(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            subs.put(subscriptionId, sub);
            fireImmediately = sub.allDepsFulfilled();
            if (fireImmediately) {
                sub.gateOpen = true;
            }
        }
        if (fireImmediately) {
            sub.fireCallback();
        }
        return sub;
    }

    @Override
    public synchronized void close() {
        for (val sub : subs.values()) {
            sub.closed = true;
        }
        subs.clear();
        mailbox.clear();
    }

    /**
     * Test hook: simulate a value arrival for a SubscriptionKey. Updates
     * the mailbox (single slot, latest wins), opens the gate for any
     * subscription whose deps are now all fulfilled, and fires callbacks
     * outside the store lock.
     */
    public void publish(SubscriptionKey key, Value value) {
        List<SubscriptionImpl> toFire;
        synchronized (this) {
            mailbox.put(key, new AttributeSnapshot(value, Instant.now()));
            toFire = new ArrayList<>();
            for (val sub : subs.values()) {
                if (!sub.deps.contains(key)) {
                    continue;
                }
                if (!sub.gateOpen && sub.allDepsFulfilled()) {
                    sub.gateOpen = true;
                    toFire.add(sub);
                } else if (sub.gateOpen) {
                    toFire.add(sub);
                }
            }
        }
        for (val sub : toFire) {
            sub.fireCallback();
        }
    }

    /**
     * Test hook: publish a value to every currently subscribed key whose
     * {@code invocation.attributeName()} equals {@code attributeName}.
     */
    public void publishByName(String attributeName, Value value) {
        Set<SubscriptionKey> targets;
        synchronized (this) {
            targets = new HashSet<>();
            for (val sub : subs.values()) {
                for (val key : sub.deps) {
                    if (key.invocation().attributeName().equals(attributeName)) {
                        targets.add(key);
                    }
                }
            }
        }
        for (val key : targets) {
            publish(key, value);
        }
    }

    /** Test introspection: ids of subscriptions currently open. */
    public synchronized Set<String> openSubscriptions() {
        return Set.copyOf(subs.keySet());
    }

    /**
     * Test introspection: every key currently subscribed across every open
     * subscription.
     */
    public synchronized Set<SubscriptionKey> subscribedKeys() {
        val all = new HashSet<SubscriptionKey>();
        for (val sub : subs.values()) {
            all.addAll(sub.deps);
        }
        return all;
    }

    private final class SubscriptionImpl implements Subscription {
        final String                                                                  id;
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          deps;
        boolean                                                                       gateOpen;
        boolean                                                                       closed;

        SubscriptionImpl(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id       = id;
            this.deps     = deps;
            this.onUpdate = onUpdate;
        }

        @Override
        public void close() {
            synchronized (TestAttributeStore.this) {
                closed = true;
                subs.remove(id);
            }
        }

        boolean allDepsFulfilled() {
            for (val key : deps) {
                if (!mailbox.containsKey(key)) {
                    return false;
                }
            }
            return true;
        }

        Map<SubscriptionKey, AttributeSnapshot> currentSnapshot() {
            val result = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(deps.size());
            for (val key : deps) {
                val v = mailbox.get(key);
                if (v != null) {
                    result.put(key, v);
                }
            }
            return Map.copyOf(result);
        }

        void fireCallback() {
            Map<SubscriptionKey, AttributeSnapshot>                                 snapshot;
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> cb;
            synchronized (TestAttributeStore.this) {
                if (closed) {
                    return;
                }
                snapshot = currentSnapshot();
                cb       = onUpdate;
            }
            val newDeps = cb.apply(snapshot);
            if (newDeps == null || newDeps.isEmpty()) {
                throw new IllegalStateException(ERROR_RETURNED_DEPS_EMPTY.formatted(id));
            }
            synchronized (TestAttributeStore.this) {
                if (closed) {
                    return;
                }
                if (!newDeps.equals(deps)) {
                    deps     = new HashSet<>(newDeps);
                    gateOpen = allDepsFulfilled();
                }
            }
        }
    }
}
