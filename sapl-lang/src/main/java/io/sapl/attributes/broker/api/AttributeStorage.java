/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Storage backend for persisting attribute values.
 * <p>
 * All operations return Mono or Flux for reactive composition.
 * Implementations may be synchronous (heap, file) or truly reactive (R2DBC).
 * <p>
 * This interface handles persistent storage only. Runtime coordination
 * (sinks, subscribers, sequence numbers) remains in the repository layer.
 * <p>
 * Implementations must be thread-safe for concurrent access.
 */
public interface AttributeStorage {

    /**
     * Retrieves persisted attribute by key.
     *
     * @param key the attribute key
     * @return Mono with the persisted attribute, or empty if not found
     */
    Mono<PersistedAttribute> get(AttributeKey key);

    /**
     * Persists an attribute value.
     * <p>
     * Overwrites the value if the key already exists.
     * The returned Mono completes when the write is durable
     * (flushed to disk, committed to database, etc.).
     *
     * @param key the attribute key
     * @param value the attribute to persist
     * @return Mono that completes when write is durable
     */
    Mono<Void> put(AttributeKey key, PersistedAttribute value);

    /**
     * Removes a persisted attribute.
     * <p>
     * No-op if the key does not exist.
     * The returned Mono completes when removal is durable.
     *
     * @param key the attribute key to remove
     * @return Mono that completes when removal is durable
     */
    Mono<Void> remove(AttributeKey key);

    /**
     * Lists all persisted attribute keys.
     * <p>
     * Used for recovery on startup. May include expired entries.
     * The repository filters expired attributes during recovery.
     *
     * @return Flux of all persisted keys
     */
    Flux<AttributeKey> getAllKeys();

    /**
     * Returns current count of persisted attributes.
     * <p>
     * Used for capacity enforcement.
     *
     * @return Mono with current size
     */
    Mono<Integer> size();

    /**
     * Closes storage and releases resources.
     * <p>
     * Should flush any pending writes before completing.
     * Default implementation does nothing.
     *
     * @return Mono that completes when storage is closed
     */
    default Mono<Void> close() {
        return Mono.empty();
    }
}
