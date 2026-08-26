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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

public class RedisAttributeStore implements AttributeStore {
    private static final String REDIS_NAMESPACE_PREFIX = "sapl:attribute:";
    private static final String REDIS_CHANGES_PREFIX   = "sapl:changes:";
    private static final String REDIS_ORDER_PREFIX     = "sapl:attribute:order:";
    private static final String REDIS_SEQ_PREFIX       = "sapl:attribute:seq:";

    private static final String ERROR_TTL_NOT_POSITIVE = "TTL must be a strictly positive Duration.";
    private static final String UNDEFINED_STRING       = "UNDEFINED";

    private static final String NAME_FIELD      = "name";
    private static final String ENTITY_FIELD    = "entity";
    private static final String ARGUMENTS_FIELD = "arguments";
    private static final String VALUE_FIELD     = "value";

    private final RedisClient                             client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String>           commands;

    public RedisAttributeStore(RedisClient client) {
        this.client     = client;
        this.connection = client.connect();
        this.commands   = connection.sync();
    }

    @Override
    public boolean publish(AttributeKey key, Value value, String pdpId) {
        return publishInternal(key, value, null, pdpId);
    }

    @Override
    public boolean publish(AttributeKey key, Value value, Duration ttl, String pdpId) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        return publishInternal(key, value, ttl, pdpId);
    }

    private boolean publishInternal(AttributeKey key, @NonNull Value value, @Nullable Duration ttl, String pdpId) {
        String redisKey   = toRedisKey(key, pdpId);
        String redisValue = ValueJsonMarshaller.toJsonString(value);

        Map<String, String> fields = new HashMap<>();
        fields.put(NAME_FIELD, key.name());
        fields.put(ARGUMENTS_FIELD, valuesToJson(key.arguments()));
        fields.put(VALUE_FIELD, redisValue);
        if (key.entity() != null) {
            fields.put(ENTITY_FIELD, ValueJsonMarshaller.toJsonString(key.entity()));
        }

        // Redis + hset lacks a function to return if a key was new or not new. It just return how many fields are there
        // It causes two connections for now and should be considered. It's not an atomic action!!
        boolean created = commands.exists(redisKey) == 0;
        commands.hset(redisKey, fields);

        // Increment the sequence number within Redis, stores it add the current key to the sorted set (ZSET)
        if (created) {
            long sequence = commands.incr(getSequenceKey(pdpId));
            commands.zadd(getOrderKey(pdpId), sequence, redisKey);
        }

        if (ttl == null) {
            commands.persist(redisKey);
        } else {
            commands.expire(redisKey, ttl.toSeconds());
        }
        commands.publish(REDIS_CHANGES_PREFIX + redisKey, redisValue);

        return created;
    }

    @Override
    public boolean remove(AttributeKey key, String pdpId) {
        String redisKey = toRedisKey(key, pdpId);
        Long   deleted  = commands.del(redisKey);

        commands.publish(REDIS_CHANGES_PREFIX + redisKey, UNDEFINED_STRING);
        commands.zrem(getOrderKey(pdpId), redisKey);

        return deleted != null && deleted > 0;
    }

    @Override
    public Long count(String pdpId) {
        return (long) commands.keys(REDIS_NAMESPACE_PREFIX + pdpId + ":*").size();
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        var raw = commands.hget(toRedisKey(key, pdpId), VALUE_FIELD);

        return raw != null ? ValueJsonMarshaller.json(raw) : Value.UNDEFINED;
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        // Empty list if the limit is invalid or exactly 0 elements, limit == 0 included because Redis interprets it as
        // no limit = get all
        if (limit != null && limit <= 0) {
            return List.of();
        }

        String               orderKey = getOrderKey(pdpId);
        long                 cursor   = offset != null ? offset : 0;
        List<AttributeEntry> entries  = new ArrayList<>();

        boolean keysRemaining = false;

        // Run till there are keys remaining or no more keys to read
        while (!keysRemaining && (limit == null || entries.size() < limit)) {
            long         stop    = limit != null ? cursor + (limit - entries.size()) - 1 : -1;
            List<String> current = commands.zrange(orderKey, cursor, stop);
            keysRemaining = current.isEmpty() || limit == null;

            int validInCurrentBatch = 0;
            for (String redisKey : current) {
                Map<String, String> hash = commands.hgetall(redisKey);

                if (!hash.isEmpty()) {
                    entries.add(toAttributeEntry(hash));
                    validInCurrentBatch++;
                } else {
                    // Remove the entry from the sorted set if the entry is expired
                    commands.zrem(orderKey, redisKey);
                }
            }
            cursor += validInCurrentBatch;
        }
        return entries;
    }

    @Override
    public void close() {
        connection.close();
        client.close();
    }

    private String toRedisKey(AttributeKey key, String pdpId) {
        String entity    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : "null";
        String arguments = valuesToJson(key.arguments());

        return REDIS_NAMESPACE_PREFIX + pdpId + ":" + entity + ":" + key.name() + ":" + arguments;
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

    private String getOrderKey(String pdpId) {
        return REDIS_ORDER_PREFIX + pdpId;
    }

    private String getSequenceKey(String pdpId) {
        return REDIS_SEQ_PREFIX + pdpId;
    }
}
