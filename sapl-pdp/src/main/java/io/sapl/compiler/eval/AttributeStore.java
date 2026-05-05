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
package io.sapl.compiler.eval;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;

import java.util.Map;
import java.util.Set;

/**
 * Per-subscription, snapshot-driven view over the attribute broker.
 * <p>
 * The store decouples the snapshot evaluator from Reactor: the evaluator
 * reads attributes via {@link #snapshot()} and declares its current
 * dependency set via {@link #update(Set)}. The store is responsible for
 * subscribing to newly added keys, releasing dropped ones, and parking
 * the latest value of each subscribed key in a single-slot mailbox.
 * <p>
 * The store fires the {@link #onUpdate(Runnable)} callback whenever a
 * subscribed mailbox accepts a new value. The trigger loop reacts by
 * scheduling the next evaluation round; back-pressure is implicit in
 * the single-slot semantic (newer values overwrite older ones, snapshot
 * captures the latest value at the moment of read).
 *
 * @since 4.2.0
 */
public interface AttributeStore extends AutoCloseable {

    /**
     * Registers the trigger callback fired whenever a subscribed mailbox
     * accepts a new value. May be invoked from any thread; implementations
     * must be safe to call concurrently with {@link #update(Set)} and
     * {@link #snapshot()}. At most one callback is held; later calls
     * replace the previous callback.
     */
    void onUpdate(Runnable trigger);

    /**
     * Declares the current dependency set produced by the latest
     * evaluation round. The store subscribes to keys present in
     * {@code currentDependencies} but not yet subscribed, releases keys
     * subscribed but absent from the new set, and leaves shared keys
     * untouched. Implementations may fire the trigger callback
     * synchronously when an added key already has a cached value.
     *
     * @param currentDependencies the SubscriptionKeys the next evaluation
     * round will read from {@link #snapshot()}
     */
    void update(Set<SubscriptionKey> currentDependencies);

    /**
     * Returns the latest snapshot map. Each entry pairs a currently
     * subscribed key with the most recent value its mailbox holds.
     * Keys whose mailbox has not yet received a value are absent from
     * the map. The returned map is suitable for binding via
     * {@link io.sapl.api.model.EvaluationContext#withSnapshot(Map)}.
     */
    Map<SubscriptionKey, AttributeSnapshot> snapshot();

    /**
     * Releases every active subscription and clears all mailboxes.
     * No further trigger callbacks will fire after this call returns.
     */
    @Override
    void close();
}
