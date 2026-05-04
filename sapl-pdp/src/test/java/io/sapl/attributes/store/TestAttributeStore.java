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

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.compiler.eval.AttributeStore;
import lombok.val;

/**
 * In-memory {@link AttributeStore} for tests. Lets tests drive snapshot
 * evolution by calling {@link #publish(SubscriptionKey, Value)} or the
 * by-name convenience overload, and observe trigger callbacks via the
 * registered {@link Runnable}.
 * <p>
 * Single-threaded by intent: tests fire {@code publish} calls from the
 * test thread, the trigger callback fires inline. This mirrors the
 * production single-slot mailbox semantic without the threading.
 */
public final class TestAttributeStore implements AttributeStore {

    private final Map<SubscriptionKey, AttributeSnapshot> mailbox    = new HashMap<>();
    private final Set<SubscriptionKey>                    subscribed = new HashSet<>();
    private final AtomicReference<@Nullable Runnable>     trigger    = new AtomicReference<>();

    @Override
    public void onUpdate(Runnable trigger) {
        this.trigger.set(trigger);
    }

    @Override
    public void update(Set<SubscriptionKey> currentDependencies) {
        subscribed.clear();
        subscribed.addAll(currentDependencies);
        mailbox.keySet().retainAll(subscribed);
    }

    @Override
    public Map<SubscriptionKey, AttributeSnapshot> snapshot() {
        return Map.copyOf(mailbox);
    }

    @Override
    public void close() {
        subscribed.clear();
        mailbox.clear();
        trigger.set(null);
    }

    /**
     * Test hook: simulate a broker emission for {@code key}. Fires the
     * trigger callback iff {@code key} is currently subscribed.
     */
    public void publish(SubscriptionKey key, Value value) {
        mailbox.put(key, new AttributeSnapshot(value, Instant.now()));
        if (subscribed.contains(key)) {
            fireTrigger();
        }
    }

    /**
     * Test hook: publish {@code value} to every currently subscribed key
     * whose {@code invocation.attributeName()} equals {@code attributeName}.
     * Useful when a test does not need to construct the full invocation.
     */
    public void publishByName(String attributeName, Value value) {
        for (val key : new HashSet<>(subscribed)) {
            if (key.invocation().attributeName().equals(attributeName)) {
                publish(key, value);
            }
        }
    }

    /** Test introspection: the keys currently subscribed. */
    public Set<SubscriptionKey> subscribedKeys() {
        return Set.copyOf(subscribed);
    }

    private void fireTrigger() {
        val callback = trigger.get();
        if (callback != null) {
            callback.run();
        }
    }
}
