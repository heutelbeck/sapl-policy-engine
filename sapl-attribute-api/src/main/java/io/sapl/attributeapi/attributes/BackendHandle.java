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
package io.sapl.attributeapi.attributes;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BackendHandle {
    private static final String ERROR_UNAVAILABLE = "The service is currently unavailable";

    // To avoid reconnect burst when the backend is down
    private static final Duration RETRY_COOLDOWN = Duration.ofSeconds(5);

    // Supplier defines how to create something but the creation happens later
    private final Supplier<AttributeStore>        builder;
    private final ReentrantLock                   lock        = new ReentrantLock();
    private final AtomicReference<AttributeStore> store       = new AtomicReference<>();
    private final AtomicReference<Instant>        lastFailure = new AtomicReference<>();

    public BackendHandle(Supplier<AttributeStore> builder) {
        this.builder = builder;
    }

    /**
     * Determines if an attribute store can be build. The reconnect retry stops
     * for few seconds if the creation just failed.
     *
     * @param name The name of the attribute store backend
     * @return A valid attribute store
     */
    public AttributeStore resolveOrThrow(String name) {
        var current = store.get();

        // Store exists already
        if (current != null)
            return current;

        var lastFailureAt = lastFailure.get();
        if (lastFailureAt != null && Duration.between(lastFailureAt, Instant.now()).compareTo(RETRY_COOLDOWN) < 0) {
            throw unavailable();
        }

        lock.lock();
        try {
            current = store.get();

            // Try again if the store has changed in the meantime
            if (current != null)
                return current;

            try {
                current = builder.get();
                store.set(current);
                return current;
            } catch (RuntimeException e) {
                lastFailure.set(Instant.now());
                log.debug("Backend '{}' failed to connect: {}", name, e.getMessage(), e);
                throw unavailable();
            }
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        var current = store.get();
        if (current != null) {
            current.close();
        }
    }

    /**
     * Discards the currently resolved store. Start the retry cooldown again when
     * a store that was available first is later unreachable.
     */
    public void invalidate() {
        store.set(null);
        lastFailure.set(Instant.now());
    }

    private AttributeBackendUnavailableException unavailable() {
        return new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
    }
}
