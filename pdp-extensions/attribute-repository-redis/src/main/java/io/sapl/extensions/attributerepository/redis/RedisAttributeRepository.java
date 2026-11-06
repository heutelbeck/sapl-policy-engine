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
package io.sapl.extensions.attributerepository.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisChannelHandler;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Repository implementation that persists attributes in Redis and maintains an
 * In-Memory representation by using the {@link InMemoryAttributeRepository}
 * synchronized with Redis. Handles change event happening in the repository by
 * using Redis Pub/Sub.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a
 * disconnect happens because Pub/Sub needs a constant connection for the event
 * stream. The current implementation performs a full re-sync when the
 * connection is established again.
 */
@Slf4j
public final class RedisAttributeRepository implements AttributeRepository {
    private static final String ERROR_BACKEND_DISCONNECTED       = "The backend is currently disconnected for pdp with id %s.";
    private static final String ERROR_TTL_NOT_POSITIVE           = "TTL must be a strictly positive Duration.";
    private static final String UNDEFINED_STRING                 = "UNDEFINED";
    private static final String NOTIFY_KEYSPACE_EVENTS_PARAM     = "notify-keyspace-events";
    private static final String ERROR_KEYSPACE_NOTIFICATIONS_OFF = "Configure Redis to publish expired key events.";
    private static final String WARN_INVALID_BATCH               = "Ignoring a batch message that is not a JSON array: {}";
    private static final String ERROR_HANDLE_MESSAGE             = "Failed to apply a Redis message for pdp with id {}.";
    private static final String WARN_INVALID_BATCH_ENTRY         = "Ignoring an invalid batch entry without key: {}";

    private static final String FIELD_NAME      = "name";
    private static final String FIELD_ENTITY    = "entity";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_VALUE     = "value";
    private static final String FIELD_BATCH_KEY = "key";

    private static final String ATTRIBUTE_KEY_PREFIX   = "sapl:attribute:";
    private static final String CHANGES_CHANNEL_PREFIX = "sapl:changes:";
    private static final String BATCH_CHANNEL_PREFIX   = "sapl:batch:";
    private static final String ORDER_KEY_PREFIX       = "sapl:attribute:order:";
    private static final String SEQ_KEY_PREFIX         = "sapl:attribute:seq:";

    // Relevant for the internal cache
    private static final long PTTL_NO_EXPIRY = -1;
    private static final long PTTL_NO_KEY    = -2;

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private final RedisClient                                   client;
    private final StatefulRedisConnection<String, String>       connection;
    private final RedisCommands<String, String>                 commands;
    private final StatefulRedisPubSubConnection<String, String> pubsub;
    private final AtomicReference<Consumer<Runnable>>           batchRunner = new AtomicReference<>(Runnable::run);

    private final ExecutorService            worker         = Executors
            .newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final Map<String, RepositoryKey> knownRedisKeys = new ConcurrentHashMap<>();
    private final AtomicBoolean              closed         = new AtomicBoolean(false);

    private final String pdpId;
    private final String ownKeyPrefix;

    public RedisAttributeRepository(RedisClient client, String pdpId, int database) {
        this.client     = client;
        this.connection = client.connect();
        this.pubsub     = client.connectPubSub();
        this.commands   = connection.sync();
        this.pdpId      = pdpId;

        requireKeyspaceNotificationsEnabled();

        // Important for the internal Cache
        this.ownKeyPrefix       = ATTRIBUTE_KEY_PREFIX + pdpId + ":";
        this.internalRepository = new InMemoryAttributeRepository(key -> {});

        // Subscribe to needed channels
        pubsub.sync().psubscribe(CHANGES_CHANNEL_PREFIX + "*");
        pubsub.sync().psubscribe(BATCH_CHANNEL_PREFIX + pdpId);
        pubsub.sync().subscribe("__keyevent@" + database + "__:expired");
        registerAttributeMessageListener();
        registerConnectionStateListener();

        loadFromRedis();
    }

    private void requireKeyspaceNotificationsEnabled() {
        String flags = commands.configGet(NOTIFY_KEYSPACE_EVENTS_PARAM).getOrDefault(NOTIFY_KEYSPACE_EVENTS_PARAM, "");
        if (!flags.contains("E") || !(flags.contains("x") || flags.contains("A"))) {
            throw new IllegalStateException(ERROR_KEYSPACE_NOTIFICATIONS_OFF.formatted(flags));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        internalRepository.close();

        pubsub.sync().unsubscribe();
        pubsub.sync().punsubscribe();
        worker.shutdownNow();

        connection.close();
        client.close();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        internalRepository.publish(key, value);
        publishInternal(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        internalRepository.publish(key, value, ttl);
        publishInternal(key, value, ttl);
    }

    private void publishInternal(@NonNull RepositoryKey key, @NonNull Value value, @Nullable Duration ttl) {
        String redisKey   = toRedisKey(key);
        String redisValue = ValueJsonMarshaller.toJsonString(value);

        knownRedisKeys.put(redisKey, key);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_NAME, key.name());
        fields.put(FIELD_ARGUMENTS, valuesToJson(key.arguments()));
        fields.put(FIELD_VALUE, redisValue);

        if (key.entity() != null) {
            fields.put(FIELD_ENTITY, ValueJsonMarshaller.toJsonString(key.entity()));
        }

        boolean created = commands.exists(redisKey) == 0;
        commands.hset(redisKey, fields);

        if (created) {
            Long sequence = commands.incr(getSequenceKKey());
            commands.zadd(getOrderKey(), sequence.doubleValue(), redisKey);
        }

        if (ttl == null) {
            commands.persist(redisKey);
        } else {
            commands.expire(redisKey, ttl.toSeconds());
        }

        commands.publish(CHANGES_CHANNEL_PREFIX + redisKey, redisValue);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        String redisKey = toRedisKey(key);

        internalRepository.remove(key);
        knownRedisKeys.remove(redisKey);
        commands.del(toRedisKey(key));
        commands.publish(CHANGES_CHANNEL_PREFIX + toRedisKey(key), UNDEFINED_STRING);
        commands.zrem(getOrderKey(), redisKey);
    }

    private String toRedisKey(RepositoryKey key) {
        String entity    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : "null";
        String arguments = valuesToJson(key.arguments());

        return ATTRIBUTE_KEY_PREFIX + pdpId + ":" + entity + ":" + key.name() + ":" + arguments;
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private String getOrderKey() {
        return ORDER_KEY_PREFIX + pdpId;
    }

    private String getSequenceKKey() {
        return SEQ_KEY_PREFIX + pdpId;
    }

    private void registerAttributeMessageListener() {
        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                // __keyevent@<db>__:expired, the message is the Redis key
                runOnWorker(() -> applyRedisKey(message));
            }

            @Override
            public void message(String pattern, String channel, String message) {
                if (channel.startsWith(BATCH_CHANNEL_PREFIX)) {
                    runOnWorker(() -> handleBatch(message));
                    return;
                }
                // Regex: sapl:changes:* -> All change events that are made on attributes
                String redisKey = channel.substring(CHANGES_CHANNEL_PREFIX.length());
                runOnWorker(() -> applyRedisKey(redisKey));
            }

        });
    }

    private void handleBatch(String message) {
        if (!(ValueJsonMarshaller.json(message) instanceof ArrayValue entries)) {
            log.warn(WARN_INVALID_BATCH, message);
            return;
        }

        var redisKeys = new ArrayList<String>();
        for (var entry : entries) {
            if (entry instanceof ObjectValue object && object.get(FIELD_BATCH_KEY) instanceof TextValue(var redisKey)) {
                redisKeys.add(redisKey);
            } else {
                log.warn(WARN_INVALID_BATCH_ENTRY, entry);
            }
        }
        batchRunner.get().accept(() -> redisKeys.forEach(this::applyRedisKey));
    }

    private void registerConnectionStateListener() {
        // Monitor the current connection and reconnect if the connection was
        // interrupted
        pubsub.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
                runOnWorker(RedisAttributeRepository.this::loadFromRedis);
            }

            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                if (closed.get()) {
                    return;
                }
                var keys = new HashSet<>(internalRepository.knownKeys());
                keys.addAll(internalRepository.observedKeys());

                for (var key : keys) {
                    internalRepository.publish(key, Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId)));
                }
            }
        });
    }

    // Loads all attribute of the pdp id into the cache
    private void loadFromRedis() {
        var seenKeys = new HashSet<RepositoryKey>();

        // Add all keys that are found by zrange
        for (String redisKey : commands.zrange(getOrderKey(), 0, -1)) {
            var key = applyRedisKey(redisKey);
            if (key != null) {
                seenKeys.add(key);
            }
        }

        // Remove stale keys after re-sync (cleanup)
        for (var staleKey : internalRepository.knownKeys()) {
            if (!seenKeys.contains(staleKey)) {
                internalRepository.remove(staleKey);
                knownRedisKeys.values().remove(staleKey);
            }
        }
    }

    private @Nullable RepositoryKey applyRedisKey(String redisKey) {
        if (!redisKey.startsWith(ownKeyPrefix)) {
            return null;
        }

        Map<String, String> hashKeys = commands.hgetall(redisKey);
        long                ttl      = commands.pttl(redisKey);

        if (hashKeys.isEmpty() || ttl == PTTL_NO_KEY) {
            var knownKey = knownRedisKeys.remove(redisKey);
            if (knownKey != null) {
                internalRepository.remove(knownKey);
            }
            return null;
        }

        var entityJson = hashKeys.get(FIELD_ENTITY);
        var entity     = entityJson != null ? ValueJsonMarshaller.json(entityJson) : null;
        var key        = new RepositoryKey(entity, hashKeys.get(FIELD_NAME),
                jsonToValues(hashKeys.get(FIELD_ARGUMENTS)), pdpId);
        var raw        = hashKeys.get(FIELD_VALUE);
        var value      = (raw == null || UNDEFINED_STRING.equals(raw)) ? Value.UNDEFINED
                : ValueJsonMarshaller.json(raw);

        knownRedisKeys.put(redisKey, key);

        if (ttl == PTTL_NO_EXPIRY) {
            internalRepository.publish(key, value);
        } else {
            internalRepository.publish(key, value, Duration.ofMillis(ttl));
        }
        return key;
    }

    private void runOnWorker(Runnable task) {
        if (closed.get()) {
            return;
        }
        worker.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                if (!closed.get()) {
                    log.error(ERROR_HANDLE_MESSAGE, pdpId, e);
                }
            }
        });
    }

    private static List<Value> jsonToValues(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return (ArrayValue) ValueJsonMarshaller.json(json);
    }

    @Override
    public void setBatchRunner(Consumer<Runnable> batchRunner) {
        this.batchRunner.set(batchRunner);
    }

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);

        void close();
    }
}
