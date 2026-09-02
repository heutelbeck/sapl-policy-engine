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
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.BackendHandle;

public final class RoutingAttributeStore implements AttributeStore {
    private static final String ERROR_UNAVAILABLE   = "The service is currently unavailable";
    private static final String ERROR_UNKNOWN_PDPID = "No attribute backend configured for pdpId '%s'.";

    private final Map<String, BackendHandle> handlesByBackendName;
    private final Map<String, String>        pdpIdToBackendName;

    public RoutingAttributeStore(Map<String, BackendHandle> handlesByBackendName,
            Map<String, String> pdpIdToBackendName) {
        this.handlesByBackendName = handlesByBackendName;
        this.pdpIdToBackendName   = pdpIdToBackendName;
    }

    private AttributeStore resolve(String pdpId) {
        var backendName = pdpIdToBackendName.get(pdpId);

        if (backendName == null) {
            throw new IllegalArgumentException(ERROR_UNKNOWN_PDPID.formatted(pdpId));
        }

        return handlesByBackendName.get(backendName).resolveOrThrow(backendName);
    }

    private void invalidate(String pdpId) {
        var backendName = pdpIdToBackendName.get(pdpId);
        if (backendName != null) {
            handlesByBackendName.get(backendName).invalidate();
        }
    }

    @Override
    public boolean publish(AttributeKey key, Value value, String pdpId) {
        try {
            return resolve(pdpId).publish(key, value, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public boolean publish(AttributeKey key, Value value, Duration ttl, String pdpId) {
        try {
            return resolve(pdpId).publish(key, value, ttl, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public boolean remove(AttributeKey key, String pdpId) {
        try {
            return resolve(pdpId).remove(key, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public Long count(String pdpId) {
        try {
            return resolve(pdpId).count(pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        try {
            return resolve(pdpId).get(key, pdpId);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        try {
            return resolve(pdpId).getAll(pdpId, limit, offset);
        } catch (DataAccessException | RedisConnectionException | RedisCommandTimeoutException e) {
            invalidate(pdpId);
            throw new AttributeBackendUnavailableException(ERROR_UNAVAILABLE);
        }
    }

    @Override
    public void close() {
        handlesByBackendName.values().forEach(BackendHandle::close);
    }

}
