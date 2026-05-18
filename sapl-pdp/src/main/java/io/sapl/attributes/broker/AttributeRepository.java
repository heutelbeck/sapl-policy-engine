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
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.NonNull;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Repository of attribute values: a write surface for producers and a
 * narrow single-key read surface for the PIP broker's fallback path.
 * Producers push values into the repository keyed by
 * {@link RepositoryKey}. Observers register a per-invocation listener
 * via {@link #observe} and receive the current value plus every
 * subsequent change.
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
     * Releases every observer registration and any internal resources.
     * Idempotent. No further callbacks fire after this returns.
     */
    @Override
    void close();

    /**
     * Publishes a value with no TTL. The value stays in the
     * repository until explicitly {@linkplain #remove(RepositoryKey)
     * removed} or overwritten by another {@code publish}.
     *
     * @param key the repository key
     * @param value the value to publish
     */
    void publish(@NonNull RepositoryKey key, @NonNull Value value);

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
    void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl);

    /**
     * Removes the entry under {@code key}, if any. Subscribers
     * observe {@link Value#UNDEFINED}. No-op when the key has no
     * entry.
     *
     * @param key the repository key
     */
    void remove(@NonNull RepositoryKey key);

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
     * @param onValue receives the current value and every subsequent
     * change
     * @return idempotent, thread-safe handle that unregisters
     * {@code onValue} when closed
     */
    Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue);

    /**
     * Observer registration handle. Narrowed {@link AutoCloseable}
     * whose {@link #close()} does not throw checked exceptions.
     */
    interface Registration extends AutoCloseable {

        /**
         * Unregisters the observer. Idempotent; safe to call from any
         * thread.
         */
        @Override
        void close();
    }
}
