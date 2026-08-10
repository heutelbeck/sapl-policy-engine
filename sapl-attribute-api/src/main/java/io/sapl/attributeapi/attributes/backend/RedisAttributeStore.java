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

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

public class RedisAttributeStore implements AttributeStore {
    private static final String REDIS_NAMESPACE_PREFIX = "sapl:attribute:";
    private static final String REDIS_CHANGES_PREFIX   = "sapl:changes:";

    private static final String ERROR_TTL_NOT_POSITIVE = "TTL must be a strictly positive Duration.";
    private static final String UNDEFINED_STRING       = "UNDEFINED";
    private static final String ERROR_PDP_ID_IS_EMPTY  = "PDP-ID must be resolved before reaching the store";

    private static final String NAME_FIELD      = "name";
    private static final String ENTITY_FIELD    = "entity";
    private static final String ARGUMENTS_FIELD = "arguments";
    private static final String VALUE_FIELD     = "value";

    private final RedisClient                             client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String>           cli;

    public RedisAttributeStore(RedisClient client) {
        this.client     = client;
        this.connection = client.connect();
        this.cli        = connection.sync();
    }

    @Override
    public boolean publish(AttributeKey signature, Value value, String pdpId) {
        return publishInternal(signature, value, null, pdpId);
    }

    @Override
    public boolean publish(AttributeKey signature, Value value, Duration ttl, String pdpId) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        return publishInternal(signature, value, ttl, pdpId);
    }

    private boolean publishInternal(AttributeKey signature, @NonNull Value value, @Nullable Duration ttl,
            String pdpId) {
        String redisKey   = toRedisKey(signature, pdpId);
        String redisValue = ValueJsonMarshaller.toJsonString(value);

        Map<String, String> fields = new HashMap<>();
        fields.put(NAME_FIELD, signature.name());
        fields.put(ARGUMENTS_FIELD, valuesToJson(signature.arguments()));
        fields.put(VALUE_FIELD, redisValue);
        if (signature.entity() != null) {
            fields.put(ENTITY_FIELD, ValueJsonMarshaller.toJsonString(signature.entity()));
        }

        // Redis + hset lacks a function to return if a key was new or not new. It just return how many fields are there
        // It causes two connections for now and should be considered. It's not an atomic action!!
        boolean created = cli.exists(redisKey) == 0;
        cli.hset(redisKey, fields);

        if (ttl == null) {
            cli.persist(redisKey);
        } else {
            cli.expire(redisKey, ttl.toSeconds());
        }
        cli.publish(REDIS_CHANGES_PREFIX + redisKey, redisValue);

        return created;
    }

    @Override
    public void remove(AttributeKey signature, String pdpId) {
        String redisKey = toRedisKey(signature, pdpId);
        cli.del(redisKey);
        cli.publish(REDIS_CHANGES_PREFIX + redisKey, UNDEFINED_STRING);
    }

    @Override
    public Long count(String pdpId) {
        Objects.requireNonNull(pdpId, ERROR_PDP_ID_IS_EMPTY);
        return (long) cli.keys(REDIS_NAMESPACE_PREFIX + pdpId + ":*").size();
    }

    @Override
    public Value get(AttributeKey signature, String pdpId) {
        var raw = cli.hget(toRedisKey(signature, pdpId), VALUE_FIELD);

        return raw != null ? ValueJsonMarshaller.json(raw) : Value.UNDEFINED;
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        Objects.requireNonNull(pdpId, ERROR_PDP_ID_IS_EMPTY);

        String pattern = REDIS_NAMESPACE_PREFIX + pdpId + ":*";

        // Sort the list of keys to guarantee a deterministic output for limit/offset
        List<String> keys = cli.keys(pattern).stream().sorted().toList();

        // set the right offset as start point, check if the start exceeds the List limit and set the end point
        int start = offset != null ? offset : 0;
        if (start >= keys.size()) {
            return List.of();
        }
        long end = limit != null ? Math.min((long) start + limit, keys.size()) : keys.size();

        // return the keys with/without limit/offset operations
        // Downcast is safe because keys.size() is always an integer and will be in Math.min the lowest value if
        // start+limit is overflowing
        return keys.subList(start, (int) end).stream().map(cli::hgetall).filter(hash -> !hash.isEmpty())
                .map(RedisAttributeStore::toAttributeEntry).toList();
    }

    @Override
    public void close() {
        connection.close();
        client.close();
    }

    private String toRedisKey(AttributeKey signature, String pdpId) {
        String entity    = signature.entity() != null ? ValueJsonMarshaller.toJsonString(signature.entity()) : "null";
        String arguments = valuesToJson(signature.arguments());

        return REDIS_NAMESPACE_PREFIX + pdpId + ":" + entity + ":" + signature.name() + ":" + arguments;
    }

    private String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static AttributeEntry toAttributeEntry(Map<String, String> hash) {
        String name         = hash.get(NAME_FIELD);
        String entityRaw    = hash.get(ENTITY_FIELD);
        String argumentsRaw = hash.get(ARGUMENTS_FIELD);
        String valueRaw     = hash.get(VALUE_FIELD);

        Value       entity    = entityRaw != null ? ValueJsonMarshaller.json(entityRaw) : null;
        List<Value> arguments = argumentsRaw != null && ValueJsonMarshaller.json(argumentsRaw) instanceof ArrayValue a
                ? a
                : List.of();
        Value       value     = valueRaw != null ? ValueJsonMarshaller.json(valueRaw) : Value.UNDEFINED;

        return new AttributeEntry(new AttributeKey(entity, name, arguments), value);
    }
}
