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
import io.sapl.attributeapi.attributes.dto.AttributeBatchChange;
import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Attribute API Service. The layer between the HTTP controller and the concrete
 * attribute store. Validates the arguments and put it either to the attribute
 * store or rejects it with an error.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@EnableConfigurationProperties(AttributeApiSecurityProperties.class)
public class AttributeApiService {
    private static final String ERROR_LIMIT_NOT_POSITIVE  = "Limit must be strictly positive.";
    private static final String ERROR_OFFSET_NOT_POSITIVE = "Offset must be strictly positive.";
    private static final String ERROR_ARGUMENTS_LIMIT     = "The amount of arguments within the request is greater than the configured maximum";
    private static final String ERROR_LIMIT_TOO_HIGH      = "The set limit within the request is greater than the configure maximum";
    private static final String NAME_FIELD                = "name";
    private static final String ENTITY_FIELD              = "entity";
    private static final String ARGUMENTS_FIELD           = "arguments";
    private static final String VALUE_FIELD               = "value";

    private final AttributeStore                 store;
    private final AttributeApiSecurityProperties securityProperties;

    /**
     * Publishes an attribute to the attribute store. A missing or negative TTL
     * stores the value without expiry.
     *
     * @param entity the entity as JSON or {@code null}.
     * @param attribute the fully qualified attribute name.
     * @param requestArgs the attribute arguments as JSON strings.
     * @param body the body and the optional TTL in seconds.
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp
     * id.
     * @return {@code true} if the attribute was created. {@code false} if it was
     * updated.
     * @throws IllegalArgumentException if there are more arguments than
     * {@code max-arguments} allows.
     */
    public boolean publish(String entity, String attribute, List<String> requestArgs, AttributePublishRequest body,
            @Nullable String pdpId) {
        if (!checkArgumentsLimit(requestArgs)) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_LIMIT);
        }

        List<Value> arguments   = toArgumentValues(requestArgs);
        Value       entityValue = toEntityValue(entity);
        Value       value       = ValueJsonMarshaller.fromJsonNode(body.value());
        Long        ttl         = body.ttl();
        var         key         = new AttributeKey(entityValue, attribute, arguments);

        if (ttl == null || ttl <= 0) {
            return store.publish(key, value, resolvePdpId(pdpId));
        } else {
            return store.publish(key, value, Duration.ofSeconds(ttl), resolvePdpId(pdpId));
        }
    }

    /**
     * Deletes an attribute from the attribute store.
     *
     * @param entity the entity as JSON or {@code null}.
     * @param attribute the fully qualified attribute name.
     * @param rawArgs the attribute arguments as JSON strings.
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp
     * id.
     * @throws IllegalArgumentException if there are more arguments than
     * {@code max-arguments} allows.
     */
    public void delete(String entity, String attribute, List<String> rawArgs, @Nullable String pdpId) {
        if (!checkArgumentsLimit(rawArgs)) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_LIMIT);
        }

        List<Value> arguments   = toArgumentValues(rawArgs);
        Value       entityValue = toEntityValue(entity);

        boolean removed = store.remove(new AttributeKey(entityValue, attribute, arguments), resolvePdpId(pdpId));

        if (!removed)
            throw new NoSuchElementException();
    }

    /**
     * Get an attribute from the attribute store.
     *
     * @param entity the entity as JSON or {@code null}.
     * @param attribute the fully qualified attribute name.
     * @param rawArgs the attribute arguments as JSON strings.
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp
     * id.
     * @return the attribute as {@code JsonNode}.
     * @throws IllegalArgumentException if there are more arguments than
     * {@code max-arguments} allows.
     * @throws NoSuchElementException if there was no attribute found in the
     * store.
     */
    public JsonNode get(String entity, String attribute, List<String> rawArgs, @Nullable String pdpId) {
        if (!checkArgumentsLimit(rawArgs)) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_LIMIT);
        }

        List<Value> arguments   = toArgumentValues(rawArgs);
        Value       entityValue = toEntityValue(entity);
        Value       value       = store.get(new AttributeKey(entityValue, attribute, arguments), resolvePdpId(pdpId));

        if (value == Value.UNDEFINED)
            throw new NoSuchElementException();

        return ValueJsonMarshaller.toJsonNodeLenient(value);
    }

    /**
     * Get all attributes from the store.
     *
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp id.
     * @param limit the maximum number of elements that are returned.
     * @param offset the position to start the request from.
     * @return a list of a attribute keys and attributes as {@code JsonNode}.
     * @throws IllegalArgumentException if the limit or offset were negativ or the
     * limit too high.
     */
    public List<JsonNode> getAll(@Nullable String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException(ERROR_LIMIT_NOT_POSITIVE);
        }

        if (limit != null && limit > securityProperties.getMaxLimit()) {
            throw new IllegalArgumentException(ERROR_LIMIT_TOO_HIGH);
        }

        if (offset != null && offset < 0) {
            throw new IllegalArgumentException(ERROR_OFFSET_NOT_POSITIVE);
        }

        String  resolvedPdpId = resolvePdpId(pdpId);
        Integer queryLimit    = limit != null ? limit : securityProperties.getMaxLimit();

        return store.getAll(resolvedPdpId, queryLimit, offset).stream().map(this::toJsonNode).toList();
    }

    /**
     * Applies that were made with a HTTP patch to the store as one request.
     *
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp id.
     * @param changes a list of attribute changes that were made via the PATCH
     * endpoint
     */
    public void patchBatchChange(String pdpId, List<AttributeBatchChange> changes) {
        var storeChanges = changes.stream().map(this::toStoreChange).toList();
        store.applyAll(storeChanges, resolvePdpId(pdpId));
    }

    // Converts the HTTP format AttributeBatchChange into the internal format Change
    // (Value)
    private AttributeStore.Change toStoreChange(AttributeBatchChange change) {
        var entity    = change.entity() == null ? null : ValueJsonMarshaller.fromJsonNode(change.entity());
        var arguments = change.arguments() == null ? List.<Value>of()
                : change.arguments().stream().map(ValueJsonMarshaller::fromJsonNode).toList();
        var key       = new AttributeKey(entity, change.name(), arguments);

        if (change.op() == AttributeBatchChange.Operation.DELETE) {
            return new AttributeStore.Change(key, null, null);
        }
        var ttl = change.ttl() == null ? null : Duration.ofSeconds(change.ttl());

        return new AttributeStore.Change(key, ValueJsonMarshaller.fromJsonNode(change.value()), ttl);
    }

    /**
     * Counts the attribute of a pdp id in the store.
     *
     * @param pdpId the pdp id resolved by auth or {@code null} if default pdp id.
     * @return The amount of attributes count in the store.
     */
    public long count(@Nullable String pdpId) {
        String resolvedPdpId = resolvePdpId(pdpId);
        return store.count(resolvedPdpId);
    }

    // Helper: resolves the pdp id and falls back to default pdp id if no auth
    private String resolvePdpId(@Nullable String pdpId) {
        return pdpId == null || pdpId.isBlank() ? securityProperties.getDefaultPdpId() : pdpId;
    }

    // Converts a query-parameter string into a SAPL value.
    // Falls back to a plain text value if the string is not valid JSON.
    private Value fromString(String data) {
        Value parsed = ValueJsonMarshaller.json(data);
        return parsed instanceof ErrorValue ? Value.of(data) : parsed;
    }

    // @formatter:off
    private JsonNode toJsonNode(AttributeEntry entry) {
        var key    = entry.key();
        var object = ObjectValue.builder()
        		.put(ENTITY_FIELD, key.entity() != null ? key.entity() : Value.NULL)
                .put(NAME_FIELD, Value.of(key.name()))
                .put(ARGUMENTS_FIELD, Value.ofArray(key.arguments()))
                .put(VALUE_FIELD, entry.value()).build();

        return ValueJsonMarshaller.toJsonNodeLenient(object);
    }
    // @formatter:on

    private Value toEntityValue(String entity) {
        return (entity != null && !entity.isBlank() ? Value.of(entity) : null);
    }

    private @NonNull List<Value> toArgumentValues(List<String> arguments) {
        return (arguments == null ? List.of() : arguments.stream().map(this::fromString).toList());
    }

    private boolean checkArgumentsLimit(List<?> arguments) {
        return arguments == null || arguments.size() <= securityProperties.getMaxArguments();
    }
}
