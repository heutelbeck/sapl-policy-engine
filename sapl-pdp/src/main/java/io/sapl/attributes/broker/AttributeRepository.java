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
 * Repository of tenant-scoped attribute values. Producers publish an ordered
 * list of assignments as one change. A broker observes complete source states
 * for a PDP configuration and evaluates its active keys from one such state.
 * Single-key operations project these same writes and observations.
 * <p>
 * Absence is {@link Value#UNDEFINED}. A key with no published entry,
 * an entry that has been explicitly removed, and an entry whose TTL
 * has expired are observationally identical: the observer receives
 * {@code UNDEFINED}.
 *
 * @since 4.1.0
 */
public interface AttributeRepository extends AutoCloseable {

    /**
     * Publishes a final unavailable state to live observations, then releases
     * registrations and owned resources. Idempotent. Deliveries already in
     * flight may finish after closure.
     */
    @Override
    void close();

    /**
     * Publishes one atomic change to one repository tenant. Assignments are
     * applied in list order. Only the final binding of each key is visible.
     * An empty list leaves the state unchanged.
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
     * Atomically captures the current state and registers for complete successor
     * states. Callbacks may overlap, arrive out of revision order or start before
     * this method returns. Consumers install a state only if its revision is newer
     * than the last state they installed, including the initial state. No callback
     * needs to finish for registration to return.
     *
     * @param pdpId tenant whose bindings are observed
     * @param configurationId configuration used to resolve the source
     * @param onUpdate receives complete successor states outside the source lock
     * @return the initial state and its observation handle
     */
    StateRegistration observeState(String pdpId, String configurationId, Consumer<RepositoryState> onUpdate);

    /** A source registration and the state captured when it was installed. */
    interface StateRegistration extends Registration {
        /** @return the initial state captured atomically with registration */
        RepositoryState initialState();

        /**
         * Detaches this observation without waiting for an in-flight callback.
         * Such a callback may still reach the consumer after detachment.
         */
        @Override
        void close();
    }

    /**
     * Registers {@code onValue} to receive value changes for
     * {@code invocation}. The current value is delivered synchronously
     * via {@code onValue} before this method returns; subsequent
     * changes deliver until the returned handle is closed.
     * <p>
     * {@code onValue} fires outside any internal lock of this
     * repository. Concurrent fires are serialised per registration so
     * the observer sees changes in order.
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
