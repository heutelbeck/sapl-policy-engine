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
package io.sapl.attributeapi.attributes.backend;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.BackendHandle;

public final class RoutingAttributeStore implements AttributeStore {
    private static final String ERROR_UNAVAILABLE   = "The service is currently unavailable";
    private static final String ERROR_UNKNOWN_PDPID = "No attribute backend configured for pdpId '%s'.";

    // Embedded case: load the backend configuration of the pdp through the node bean
    private final Function<String, AttributeStore> resolver;
    private final Consumer<String>                 invalidator;
    private final Runnable                         closeAll;

    public RoutingAttributeStore(Function<String, AttributeStore> resolver,
            Consumer<String> invalidator,
            Runnable closeAll) {
        this.resolver    = resolver;
        this.invalidator = invalidator;
        this.closeAll    = closeAll;
    }

    private AttributeStore resolve(String pdpId) {
        return resolver.apply(pdpId);
    }

    private void invalidate(String pdpId) {
        invalidator.accept(pdpId);
    }

    @Override
    public boolean publish(AttributeKey key, Value value, String pdpId) {
        try {
            return resolve(pdpId).publish(key, value, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public boolean publish(AttributeKey key, Value value, Duration ttl, String pdpId) {
        try {
            return resolve(pdpId).publish(key, value, ttl, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public boolean remove(AttributeKey key, String pdpId) {
        try {
            return resolve(pdpId).remove(key, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public Long count(String pdpId) {
        try {
            return resolve(pdpId).count(pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        try {
            return resolve(pdpId).get(key, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        try {
            return resolve(pdpId).getAll(pdpId, limit, offset);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE, e);
        }
    }

    @Override
    public void close() {
        closeAll.run();
    }

    /**
     * Constructs the store if the api server runs in a standalone mode with the SAPL Node.
     *
     * @param handlesByBackendName The connection settings
     * @param pdpIdToBackendName The registration of pdp id to backend name
     * @return The router object for the given configuration
     */
    public static RoutingAttributeStore forBackends(Map<String, BackendHandle> handlesByBackendName,
            Map<String, String> pdpIdToBackendName) {
        Function<String, AttributeStore> resolver    = pdpId -> resolveViaBackendName(pdpId, handlesByBackendName,
                pdpIdToBackendName);
        Consumer<String>                 invalidator = pdpId -> invalidateViaBackendName(pdpId, handlesByBackendName,
                pdpIdToBackendName);

        return new RoutingAttributeStore(resolver, invalidator,
                () -> handlesByBackendName.values().forEach(BackendHandle::close));
    }

    // Helper method to resolve the backend name for the given pdp id
    private static AttributeStore resolveViaBackendName(String pdpId, Map<String, BackendHandle> handlesByBackendName,
            Map<String, String> pdpIdToBackendName) {
        var backendName = pdpIdToBackendName.get(pdpId);

        if (backendName == null) {
            throw new IllegalArgumentException(ERROR_UNKNOWN_PDPID.formatted(pdpId));
        }

        return handlesByBackendName.get(backendName).resolveOrThrow(backendName);
    }

    // Helper method to marks the connection as interrupted, so that a reconnect is tried instead of using a dead
    // connection
    private static void invalidateViaBackendName(String pdpId, Map<String, BackendHandle> handlesByBackendName,
            Map<String, String> pdpIdToBackendName) {
        var backendName = pdpIdToBackendName.get(pdpId);

        if (backendName != null) {
            handlesByBackendName.get(backendName).invalidate();
        }
    }

}
