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
package io.sapl.attributeapi.attributes.service;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributeapi.attributes.backend.AttributeEntry;
import io.sapl.attributeapi.attributes.backend.AttributeKey;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
public class AttributeApiService {
    private final AttributeStore                 store;
    private final AttributeApiSecurityProperties securityProperties;

    public boolean publish(String entity, String attribute, AttributePublishRequest body, @Nullable String pdpId) {
        List<Value> arguments   = body.getArguments() == null ? List.of()
                : body.getArguments().stream().map(ValueJsonMarshaller::fromJsonNode).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        Value       value       = ValueJsonMarshaller.fromJsonNode(body.getValue());
        Long        ttl         = body.getTtl();

        var sig = new AttributeKey(entityValue, attribute, arguments);

        if (ttl == null || ttl <= 0) {
            return store.publish(sig, value, resolvePdpId(pdpId));
        } else {
            return store.publish(sig, value, Duration.ofSeconds(ttl), resolvePdpId(pdpId));
        }
    }

    public void delete(String entity, String attribute, List<String> rawArgs, @Nullable String pdpId) {
        List<Value> arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;

        store.remove(new AttributeKey(entityValue, attribute, arguments), resolvePdpId(pdpId));
    }

    public JsonNode get(String entity, String attribute, List<String> rawArgs, @Nullable String pdpId) {
        List<Value> arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        Value       value       = store.get(new AttributeKey(entityValue, attribute, arguments), resolvePdpId(pdpId));

        if (value == Value.UNDEFINED)
            throw new NoSuchElementException();

        return ValueJsonMarshaller.toJsonNodeLenient(value);
    }

    public List<JsonNode> getAll(@Nullable String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("limit must be strictly positive.");
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("offset must not be negative.");
        }

        String resolvedPdpId = resolvePdpId(pdpId);

        return store.getAll(resolvedPdpId, limit, offset).stream().map(this::toJsonNode).toList();
    }

    public long count(@Nullable String pdpId) {
        String resolvedPdpId = resolvePdpId(pdpId);
        return store.count(resolvedPdpId);
    }

    private String resolvePdpId(@Nullable String pdpId) {
        return pdpId == null || pdpId.isBlank() ? securityProperties.getDefaultTenantId() : pdpId;
    }

    // Converts a query-parameter string into a SAPL value.
    // Falls back to a plain text value if the string is not valid JSON.
    private Value fromString(String data) {
        Value parsed = ValueJsonMarshaller.json(data);
        return parsed instanceof ErrorValue ? Value.of(data) : parsed;
    }

    private JsonNode toJsonNode(AttributeEntry entry) {
        var key    = entry.key();
        var object = ObjectValue.builder().put("entity", key.entity() != null ? key.entity() : Value.NULL)
                .put("name", Value.of(key.name())).put("arguments", Value.ofArray(key.arguments()))
                .put("value", entry.value()).build();

        return ValueJsonMarshaller.toJsonNodeLenient(object);
    }
}
