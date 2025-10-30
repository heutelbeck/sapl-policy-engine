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

import java.util.Map;

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
     * Returns all stored attributes.
     * <p>
     * Used during startup to recover timeout schedules. This method is called
     * once during application initialization and must complete before the
     * application accepts any connections.
     * <p>
     * Implementations should stream results efficiently for large datasets.
     * For heap storage, this is a simple iteration. For file-based storage,
     * this reads and deserializes the entire file. For database storage, this
     * executes a SELECT * query.
     *
     * @return Flux of all stored attributes with their keys
     */
    Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll();

}
