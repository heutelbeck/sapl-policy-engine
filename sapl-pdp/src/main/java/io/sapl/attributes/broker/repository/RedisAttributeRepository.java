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
package io.sapl.attributes.broker.repository;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class RedisAttributeRepository implements AttributeRepository {
    private static final String ERROR_TTL_NOT_POSITIVE           = "TTL must be a strictly positive Duration.";
    private static final String ERROR_CLOSED                     = "Repository is closed.";
    private static final String UNDEFINED_STRING                 = "UNDEFINED";
    private static final String NOTIFY_KEYSPACE_EVENTS_PARAM     = "notify-keyspace-events";
    private static final String ERROR_KEYSPACE_NOTIFICATIONS_OFF = "Configure Redis to publish expired key events.";

    private static final String CHANGES_CHANNEL_PREFIX = "sapl:changes:";

    private final ReentrantLock                                 lock = new ReentrantLock(true);
    private final RedisClient                                   client;
    private final StatefulRedisConnection<String, String>       connection;
    private final RedisCommands<String, String>                 cli;
    private final StatefulRedisPubSubConnection<String, String> pubsub;

    private final Map<String, Set<Consumer<Value>>> observersByKey = new HashMap<>();

    private final String pdpId;

    private boolean closed = false;

    public RedisAttributeRepository(RedisClient client, String pdpId, int database) {
        this.client     = client;
        this.connection = client.connect();
        this.pubsub     = client.connectPubSub();
        this.cli        = connection.sync();
        this.pdpId      = pdpId;

        requireKeyspaceNotificationsEnabled();

        // Subscribe to needed channels
        pubsub.sync().psubscribe(CHANGES_CHANNEL_PREFIX + "*");
        pubsub.sync().subscribe("__keyevent@" + database + "__:expired");

        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                // expired-Events: message = der abgelaufene Redis-Key
                notifyObservers(message, Value.UNDEFINED);
            }

            @Override
            public void message(String pattern, String channel, String message) {
                // sapl:changes:* → Wertänderungen
                String redisKey = channel.substring(CHANGES_CHANNEL_PREFIX.length());
                Value  value    = UNDEFINED_STRING.equals(message) ? Value.UNDEFINED
                        : ValueJsonMarshaller.json(message);

                notifyObservers(redisKey, value);
            }
        });
    }

    private void requireKeyspaceNotificationsEnabled() {
        String flags = cli.configGet(NOTIFY_KEYSPACE_EVENTS_PARAM).getOrDefault(NOTIFY_KEYSPACE_EVENTS_PARAM, "");
        if (!flags.contains("E") || !(flags.contains("x") || flags.contains("A"))) {
            throw new IllegalStateException(ERROR_KEYSPACE_NOTIFICATIONS_OFF.formatted(flags));
        }
    }

    @Override
    public void close() {
        lock.lock();

        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            lock.unlock();
        }

        pubsub.sync().unsubscribe();
        pubsub.sync().punsubscribe();
        connection.close();
        client.close();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        publishInternal(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        publishInternal(key, value, ttl);
    }

    private void publishInternal(@NonNull RepositoryKey key, @NonNull Value value, @Nullable Duration ttl) {
        String redisKey   = toRedisKey(key);
        String redisValue = ValueJsonMarshaller.toJsonString(value);

        Map<String, String> fields = new HashMap<>();
        fields.put("name", key.name());
        fields.put("arguments", valuesToJson(key.arguments()));
        fields.put("value", redisValue);

        if (key.entity() != null) {
            fields.put("entity", ValueJsonMarshaller.toJsonString(key.entity()));
        }

        cli.hset(redisKey, fields);

        if (ttl == null) {
            cli.persist(redisKey);
        } else {
            cli.expire(redisKey, ttl.toSeconds());
        }

        cli.publish(CHANGES_CHANNEL_PREFIX + redisKey, redisValue);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        cli.del(toRedisKey(key));
        cli.publish(CHANGES_CHANNEL_PREFIX + toRedisKey(key), UNDEFINED_STRING);
    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue) {
        RepositoryKey key = RepositoryKey.fromInvocation(invocation);

        String redisKey = toRedisKey(key);
        Value  initial;

        lock.lock();

        try {

            if (closed) {
                initial = Value.error(ERROR_CLOSED); // do not register observer in error case
            } else {
                // Register callback for future changes
                observersByKey.computeIfAbsent(redisKey, k -> new HashSet<>()).add(onValue);
                String raw = cli.hget(redisKey, "value");
                initial = (raw == null || UNDEFINED_STRING.equals(raw)) ? Value.UNDEFINED
                        : ValueJsonMarshaller.json(raw);
            }
        } finally {
            lock.unlock();
        }

        // Deliver current value immediately
        onValue.accept(initial);

        // Return a registration to remove the observer
        return () -> {
            lock.lock();

            try {
                var bucket = observersByKey.get(redisKey);
                if (bucket != null)
                    bucket.remove(onValue);  // No-Op wenn nie registriert
            } finally {
                lock.unlock();
            }
        };
    }

    private String toRedisKey(RepositoryKey key) {
        String entity    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : "null";
        String arguments = valuesToJson(key.arguments());

        return "sapl:attribute:" + pdpId + ":" + entity + ":" + key.name() + ":" + arguments;
    }

    private String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private void notifyObservers(String redisKey, Value value) {
        List<Consumer<Value>> toFire;
        lock.lock();
        try {
            var bucket = observersByKey.get(redisKey);
            toFire = bucket != null ? new ArrayList<>(bucket) : List.of();
        } finally {
            lock.unlock();
        }

        toFire.forEach(callback -> callback.accept(value));
    }
}
