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

import io.sapl.api.model.Value;
import lombok.NonNull;

import java.time.Duration;

/**
 * Writer-side surface for a value-store that complements the
 * catalog-backed {@link AttributeStore}. Producers push values into a
 * repository keyed by {@link RepositoryKey}; readers see those values
 * through the same {@link AttributeStore} surface the catalog-backed
 * store exposes.
 * <p>
 * Three methods. {@code void} returns: the writer learns nothing about
 * prior state. A producer that needs to read or compare prior values
 * should subscribe through the reader surface.
 * <p>
 * Absence is {@link Value#UNDEFINED}. A key with no published entry,
 * an entry that has been explicitly removed, and an entry whose TTL
 * has expired are observationally identical from the reader surface:
 * the subscription emits {@code UNDEFINED}.
 *
 * @since 4.1.0
 */
public interface AttributeRepository {

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
}
