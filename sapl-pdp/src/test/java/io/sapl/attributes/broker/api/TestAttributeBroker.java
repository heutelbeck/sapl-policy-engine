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
package io.sapl.attributes.broker.api;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * In-memory {@link AttributeBroker} for tests, modelled on the
 * production PIP-based attribute lifecycle. Attribute names are either
 * registered (a PIP exists; the broker waits patiently for values via
 * {@link #publish}) or unregistered (no PIP; an unbound key materialises
 * immediately as {@link Value#UNDEFINED}). This mirrors the eventual
 * production behaviour where the attribute repository falls back to
 * UNDEFINED when no PIP is registered.
 * <p>
 * Gate semantic: a subscription's gate stays closed until every
 * declared dependency has a value in the mailbox. PIP-registered keys
 * stay unbound until a publish lands; unregistered keys are
 * auto-filled with UNDEFINED at the moment they enter a subscription's
 * dep set. The first state where every dep has a value opens the gate
 * and fires the callback.
 * <p>
 * Re-fire on dep growth: when a callback returns expanded dependencies,
 * unregistered new keys are auto-filled with UNDEFINED. If the gate
 * remains open after that step (or transitions to open), the callback
 * fires once more so the consumer observes the new mailbox state. If
 * the new deps include PIP-registered keys that have no value yet, the
 * gate closes and no fire happens until a publish completes the set.
 * <p>
 * State mutations and reads on the broker and its subscriptions are
 * guarded by an intrinsic lock on the broker. Callbacks fire outside
 * the lock so they may freely close their subscription or invoke other
 * broker-touching operations without re-entrance hazard.
 */
public final class TestAttributeBroker implements AttributeBroker {

    private static final String ERROR_INITIAL_DEPS_EMPTY     = "initialDependencies must not be empty";
    private static final String ERROR_RETURNED_DEPS_EMPTY    = "Subscription %s returned empty/null dependencies; close the subscription externally instead";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK  = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE = "subscriptionId already open: %s";

    private final Map<SubscriptionKey, AttributeSnapshot> mailbox        = new HashMap<>();
    private final Map<String, SubscriptionImpl>           subs           = new HashMap<>();
    private final Map<String, @Nullable Value>            registeredPips = new HashMap<>();

    /**
     * Register a PIP for {@code attributeName} without a primed value.
     * Subsequent {@code open()} calls whose dependency set contains a
     * key with this name will leave the key unbound (gate stays closed
     * for that key) until a publish lands.
     */
    public synchronized void register(String attributeName) {
        registeredPips.put(attributeName, null);
    }

    /**
     * Register a PIP for {@code attributeName} with an initial value.
     * Equivalent to {@link #register(String)} followed by an immediate
     * publish to every key with this name that enters a subscription's
     * dep set. Tests that call this before {@link #open(String, Set, Function)}
     * see the gate open in one step with the primed value already in
     * the snapshot.
     */
    public synchronized void register(String attributeName, Value initialValue) {
        registeredPips.put(attributeName, initialValue);
    }

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
            applyPipPolicyToDeps(initialDependencies);
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

    /**
     * For every key whose name is unregistered, auto-fill the mailbox
     * with {@link Value#UNDEFINED}. For every key whose name is
     * registered with a primed initial value, auto-fill the mailbox
     * with that value. Keys whose name is registered without a primed
     * value are left untouched (patient: wait for publish).
     *
     * @return {@code true} if any new entry was added to the mailbox
     */
    private boolean applyPipPolicyToDeps(Set<SubscriptionKey> keys) {
        val     now         = Instant.now();
        boolean mailboxGrew = false;
        for (val key : keys) {
            if (mailbox.containsKey(key)) {
                continue;
            }
            val name = key.invocation().attributeName();
            if (registeredPips.containsKey(name)) {
                val initial = registeredPips.get(name);
                if (initial != null) {
                    mailbox.put(key, new AttributeSnapshot(initial, now));
                    mailboxGrew = true;
                }
            } else {
                mailbox.put(key, new AttributeSnapshot(Value.UNDEFINED, now));
                mailboxGrew = true;
            }
        }
        return mailboxGrew;
    }

    @Override
    public synchronized void close() {
        for (val sub : subs.values()) {
            sub.closed = true;
        }
        subs.clear();
        mailbox.clear();
        registeredPips.clear();
    }

    /**
     * Test hook: simulate a value arrival for a SubscriptionKey. Updates
     * the mailbox (single slot, latest wins), opens the gate for any
     * subscription whose deps are now all fulfilled, and fires callbacks
     * outside the broker lock.
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
            synchronized (TestAttributeBroker.this) {
                closed = true;
                subs.remove(id);
                gcOrphanedMailboxEntries();
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
            synchronized (TestAttributeBroker.this) {
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
            boolean refire;
            synchronized (TestAttributeBroker.this) {
                if (closed) {
                    return;
                }
                if (newDeps.equals(deps)) {
                    refire = false;
                } else {
                    val added = new HashSet<>(newDeps);
                    added.removeAll(deps);
                    deps = new HashSet<>(newDeps);
                    applyPipPolicyToDeps(deps);
                    val nowFulfilled    = allDepsFulfilled();
                    val addedHasMailbox = added.stream().anyMatch(mailbox::containsKey);
                    refire   = addedHasMailbox && nowFulfilled;
                    gateOpen = nowFulfilled;
                }
            }
            if (refire) {
                fireCallback();
            }
        }
    }

    private void gcOrphanedMailboxEntries() {
        val referenced = new HashSet<SubscriptionKey>();
        for (val s : subs.values()) {
            referenced.addAll(s.deps);
        }
        mailbox.keySet().retainAll(referenced);
    }
}
