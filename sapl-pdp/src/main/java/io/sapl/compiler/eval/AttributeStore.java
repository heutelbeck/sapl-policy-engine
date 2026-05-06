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
import java.util.function.Function;

/**
 * Multi-tenant view over the attribute layer for evaluator instances.
 * Many concurrent consumers (one per authorization subscription) hold
 * a {@link Subscription} obtained via
 * {@link #open(String, Set, Function)}; the store deduplicates backing
 * PIP subscriptions across consumers and routes value updates to the
 * consumers that depend on the changed key.
 * <p>
 * The consumer interacts with the store through a single function
 * callback. On each fire the store invokes the callback with the
 * fulfilled snapshot for the consumer's currently subscribed keys; the
 * callback evaluates against the snapshot and returns the dependency
 * set its next evaluation pass will read. The store diffs the returned
 * set against the previous one and updates backing PIP subscriptions
 * accordingly. The {@link Subscription} handle is a pure lifecycle
 * marker, with a single {@link Subscription#close()} method.
 * <p>
 * The first callback for a given dependency set fires only when every
 * declared key has a value in its mailbox (the all-deps-fulfilled
 * gate). The consumer never observes a partial snapshot. After the gate
 * first opens it stays open for the dep set, firing on each subsequent
 * value change; a callback whose return value adds a key with an empty
 * mailbox re-closes the gate until that key fills.
 * <p>
 * {@link io.sapl.api.model.ErrorValue} counts as a fulfilled value: a
 * PIP that fails materialises an {@code ErrorValue} in the mailbox, the
 * gate opens, and the consumer's evaluator handles the error per its
 * own semantics.
 *
 * @since 4.2.0
 */
public interface AttributeStore extends AutoCloseable {

    /**
     * Opens a per-consumer subscription. Initial dependencies and the
     * trigger callback are wired atomically so the first update never
     * races a not-yet-installed callback. If every key in
     * {@code initialDependencies} already has a value in its mailbox at
     * this point, the callback fires synchronously before this method
     * returns; otherwise the callback fires later, when the last
     * unfulfilled key receives its first value.
     * <p>
     * The {@code subscriptionId} is caller-supplied and must uniquely
     * identify this consumer for the lifetime of this store. The same
     * id surfaces in store-side telemetry and metrics so operators can
     * correlate store activity with the originating authorization
     * subscription.
     * <p>
     * The {@code onUpdate} callback receives the fulfilled snapshot
     * (the gate guarantees an entry per current dep) and must return a
     * non-empty {@link Set} of the SubscriptionKeys its next evaluation
     * pass will read. The store applies the diff against the previous
     * dep set: backing PIP subscriptions are reference-counted across
     * consumers; freshly added keys open backing subscriptions, dropped
     * keys release them. Returning an empty set is illegal — consumers
     * who want to stop must call {@link Subscription#close()}
     * externally.
     *
     * @param subscriptionId non-blank identifier; must not collide with
     * any open subscription on this store
     * @param initialDependencies the SubscriptionKeys the consumer's
     * first evaluation pass will read; must be non-empty (consumers
     * with no streaming dependencies do not interact with the store at
     * all)
     * @param onUpdate fired when a fulfilled snapshot is available;
     * receives the snapshot, returns the next dep set;
     * per-subscription serialised (never invoked concurrently with
     * itself for the same {@code subscriptionId}); fires outside any
     * internal store lock; an empty returned set or a {@code null}
     * returned set causes the store to fail the subscription with an
     * {@link IllegalStateException} on the firing thread
     * @return per-consumer lifecycle handle for releasing the
     * subscription
     * @throws IllegalArgumentException if {@code subscriptionId} is
     * blank or already in use, or if {@code initialDependencies} is
     * empty
     */
    Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate);

    /**
     * Releases every open subscription and the backing PIP state. No
     * further callbacks fire after this returns.
     */
    @Override
    void close();

    /**
     * Per-consumer lifecycle handle. The subscription's behaviour
     * (snapshot reads, dependency updates) lives entirely in the
     * callback wired at {@link AttributeStore#open(String, Set, Function)}
     * time; this handle is solely for releasing the subscription when
     * the consumer is done.
     *
     * @since 4.2.0
     */
    interface Subscription extends AutoCloseable {

        /**
         * Releases this consumer's dependencies and unregisters the
         * trigger callback. Idempotent; safe to call from any thread.
         * After {@code close()} returns the store will not invoke the
         * callback again. Backing PIP subscriptions whose refcount
         * falls to zero are released by the store.
         */
        @Override
        void close();
    }
}
