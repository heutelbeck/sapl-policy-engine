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
package io.sapl.attributes.broker;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.AttributeAssignment.UnboundValue;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import lombok.val;
import lombok.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Repository of tenant-scoped attribute bindings. Multiple assignments in one
 * publication are atomic. Readers observe complete, immutable source views
 * through {@link #observeState}; single-key {@link #observe} calls are a
 * convenience projection of that same contract.
 * <p>
 * The read contract preserves which attribute values belong together. For a
 * lazy policy requiring A and B, both {@code (A=true, B=false)} and a later
 * atomic {@code (A=false, B=true)} make the condition false. Combining the old A
 * with the new B would incorrectly make it true. A complete source view lets
 * the broker supply the new A and B together even when B is discovered only
 * after evaluating A. The next evaluation can reread A=false and drop B.
 * <p>
 * The repository supplies coherent views. The broker installs all affected
 * bindings under the lock it also uses to capture an evaluation's inputs, so a
 * round sees the complete update or the preceding view. The evaluator determines
 * the dependencies of that round. Dropping a dependency changes that consumer's
 * observation, not the repository's bindings.
 * <p>
 * In an available view, a missing binding is {@link Value#UNDEFINED}, whether
 * it was never published, removed or expired. SAPL null remains a bound value.
 * If the source cannot establish its state, it reports
 * {@link RepositoryState#unavailable()} instead of claiming that bindings are
 * absent or continuing to serve previously trusted values.
 *
 * @since 4.1.0
 */
public interface AttributeRepository extends AutoCloseable {

    /**
     * Closes the repository and its owned resources. Live state observations
     * receive a final unavailable view with a newer revision, so the broker
     * invalidates cached bindings instead of retaining an old grant. Subsequent
     * observations start unavailable. Idempotent. Callbacks already in flight
     * may finish after this method returns.
     */
    @Override
    void close();

    /**
     * Publishes assignments to one tenant. Multiple assignments are applied
     * atomically. List order determines the final binding of each key; an empty
     * list leaves the state unchanged.
     *
     * @param assignments bindings and removals
     * @throws IllegalArgumentException if the publication spans tenants
     */
    void publish(@NonNull List<AttributeAssignment> assignments);

    /**
     * Publishes a value with no TTL. The value stays in the
     * repository until explicitly {@linkplain #remove(RepositoryKey)
     * removed} or overwritten by another {@code publish}.
     *
     * @param key the repository key
     * @param value the value to publish
     */
    default void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        publish(List.of(new BoundValue(key, value, null)));
    }

    /**
     * Publishes a value with a time-to-live. The value is removed
     * after {@code ttl} elapses; subscribers then observe
     * {@link Value#UNDEFINED}. Republishing the same key cancels any
     * pending expiry on the prior entry.
     *
     * @param key the repository key
     * @param value the value to publish
     * @param ttl strictly positive duration after which the value
     * expires; {@link Duration#ZERO} and negative durations are
     * rejected with {@link IllegalArgumentException}
     */
    default void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        publish(List.of(new BoundValue(key, value, ttl)));
    }

    /**
     * Removes the entry under {@code key}, if any. Subscribers
     * observe {@link Value#UNDEFINED}. No-op when the key has no
     * entry.
     *
     * @param key the repository key
     */
    default void remove(@NonNull RepositoryKey key) {
        publish(List.of(new UnboundValue(key)));
    }

    /**
     * Observes the source selected for a tenant and PDP configuration. Repository
     * implementations must provide the following guarantees:
     * <ol>
     * <li><b>Complete, coherent views.</b> Each available state contains all
     * current bindings of that tenant in the selected source at one committed
     * point, and no bindings from other tenants. Independent reads must not
     * combine values that never coexisted in one committed source state. A map
     * containing only changed keys would make omitted bindings appear absent
     * and could place a lazily discovered value beside an older value from
     * another state. Internally, an implementation may assemble complete views
     * from ordered committed changes.</li>
     * <li><b>No gap at registration.</b> Capturing the initial state and enrolling
     * the observer share one observation boundary. A concurrent change must be
     * covered by the initial state or a successor notification. Reading first
     * and subscribing later could lose a revocation in between. The returned
     * {@link StateRegistration#initialState()} is the fixed view captured at
     * that boundary, not a fresh read on each call.</li>
     * <li><b>Ordered revisions.</b> Revisions order source views, not callback
     * arrival. A change of view has a higher revision within this registration;
     * equal revisions identify the same view. Revisions need not be consecutive
     * or comparable with those of another registration, and they are not
     * observation timestamps. A newer complete view reflects the combined
     * result of intervening changes, so it may replace older notifications
     * without losing changes to other keys.</li>
     * <li><b>Notification without lock inversion.</b> Invoke {@code onUpdate}
     * after releasing locks needed to publish, register or detach. Callbacks may
     * overlap, arrive out of revision order or begin before this method returns. Do not
     * wait for callbacks running on other threads before returning: the broker
     * can hold its routing lock while registering, and a callback can need that
     * same lock. The initial state is available through the returned handle
     * without waiting for an initial callback.</li>
     * <li><b>Changes remain observable.</b> While a registration is active,
     * notify it of source changes or a newer complete view containing their
     * effects. Coalescing may skip intermediate views; this is not an audit log
     * and promises no particular number of callbacks, evaluations or decisions.
     * Binding generations remain stable across unrelated changes and change on
     * rebinding, as specified by {@link RepositoryState.Binding}.</li>
     * <li><b>Unknown state is unavailable.</b> If the implementation loses a
     * trustworthy view, for example after a connection failure or a gap in its
     * backend change feed, notify an unavailable state with a newer revision.
     * An empty available map would falsely confirm absence; retaining the old
     * view could retain an obsolete grant. Recovery requires a complete,
     * trustworthy successor view and observation of its subsequent changes.
     * A numeric revision gap alone does not imply lost data.</li>
     * </ol>
     * Consumers compare the initial state and all callbacks within the same
     * registration and install only a newer revision than the one already
     * accepted. A successor callback may arrive before the initial state is
     * consumed. Consumers also reject callbacks from retired registrations,
     * since {@link StateRegistration#close()} permits in-flight delivery.
     * These guarantees apply to this observation of one routed source; they do
     * not establish a transaction across independently selected sources.
     *
     * @param pdpId tenant whose bindings are observed
     * @param configurationId configuration used to select the backing source;
     * not an additional field of an attribute's binding key
     * @param onUpdate receives complete available or unavailable successor views
     * @return the captured initial view and its observation handle
     */
    StateRegistration observeState(String pdpId, String configurationId, Consumer<RepositoryState> onUpdate);

    /**
     * One source observation and its captured initial state. Returning the state
     * with the handle gives the caller a starting view without a separate read
     * or a wait for the notification callback.
     */
    interface StateRegistration extends Registration {
        /**
         * Returns the fixed state captured atomically with observer enrollment.
         * A newer callback may already have arrived; consumers must not replace
         * that newer accepted view with this initial one.
         *
         * @return the captured available or unavailable initial state
         */
        RepositoryState initialState();

        /**
         * Detaches only this observation. Idempotent and thread-safe. It must
         * not wait for callbacks to finish: the broker may retire a registration
         * while holding a lock that an in-flight callback needs. Such a callback
         * may still reach the consumer, which must reject it after retirement.
         */
        @Override
        void close();
    }

    /**
     * Observes one attribute through {@link #observeState}. Before this method
     * returns, {@code onValue} is called synchronously with its captured initial
     * value or a newer accepted value. Subsequent callbacks are serialized per
     * registration and may coalesce superseded source views. Republishing the
     * same value can produce another callback because its binding generation
     * changes.
     * <p>
     * Callbacks run outside repository locks. Closing the handle detaches this
     * observation; a callback already in flight may still finish. Independent
     * single-key observations do not give a consumer an atomic multi-key view.
     * The broker uses {@code observeState} directly to retain that guarantee.
     *
     * @param invocation the invocation to observe
     * @param onValue receives the current value and subsequent changes;
     * superseded states may be coalesced
     * @return idempotent, thread-safe handle that unregisters
     * {@code onValue} when closed
     */
    default Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue) {
        val projection   = new RepositoryValueProjection(RepositoryKey.fromInvocation(invocation), onValue);
        val registration = observeState(invocation.pdpId(), invocation.configurationId(), projection);
        try {
            projection.start(registration.initialState());
        } catch (RuntimeException failure) {
            registration.close();
            throw failure;
        }
        return () -> {
            projection.close();
            registration.close();
        };
    }

    /**
     * Observer registration handle. Narrowed {@link AutoCloseable}
     * whose {@link #close()} does not throw checked exceptions.
     */
    interface Registration extends AutoCloseable {

        /**
         * Unregisters the observer. Idempotent. Safe to call from any
         * thread.
         */
        @Override
        void close();
    }
}
