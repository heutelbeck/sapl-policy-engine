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
import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisChannelHandler;
import java.net.SocketAddress;

/**
 * Repository implementation that persists attributes in Redis and handles change event happening in the repository
 * by using Redis Pub/Sub.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a disconnect happens because
 * Redis Pub/Sub need a constant connection for the event stream and Redis keys are always requested
 * online.
 */
@Slf4j
public final class RedisAttributeRepository implements AttributeRepository {
    private static final String ERROR_BACKEND_DISCONNECTED       = "The backend is currently disconnected for pdp with id %s.";
    private static final String ERROR_TTL_NOT_POSITIVE           = "TTL must be a strictly positive Duration.";
    private static final String ERROR_CLOSED                     = "Repository is closed.";
    private static final String UNDEFINED_STRING                 = "UNDEFINED";
    private static final String NOTIFY_KEYSPACE_EVENTS_PARAM     = "notify-keyspace-events";
    private static final String ERROR_KEYSPACE_NOTIFICATIONS_OFF = "Configure Redis to publish expired key events.";
    private static final String WARN_OBSERVER_THREW_EXCEPTION    = "Observere for key '{}' threw an exception during notification. "
            + "The value was not delivered to observer {}";
    private static final String WARN_RESYNC_KEY_FAILED           = "Resync failed for key '{}' after reconnect. "
            + "The key may remain stale until the next reconnect {}";
    private static final String ERROR_RESYNC_FAILED              = "Unexpected failure while resyncing observers afte a reconnect to Redis backend.";

    private static final String FIELD_NAME      = "name";
    private static final String FIELD_ENTITY    = "entity";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_VALUE     = "value";

    private static final String ATTRIBUTE_KEY_PREFIX   = "sapl:attribute:";
    private static final String CHANGES_CHANNEL_PREFIX = "sapl:changes:";
    private static final String ORDER_KEY_PREFIX       = "sapl:attribute:order:";
    private static final String SEQ_KEY_PREFIX         = "sapl:attribute:seq:";

    private final RedisClient                                   client;
    private final StatefulRedisConnection<String, String>       connection;
    private final RedisCommands<String, String>                 commands;
    private final StatefulRedisPubSubConnection<String, String> pubsub;
    private final ExecutorService                               resyncExecutor = Executors
            .newVirtualThreadPerTaskExecutor();
    private final ReentrantLock                                 lock           = new ReentrantLock(true);
    private final Map<String, Set<Consumer<Value>>>             observersByKey = new HashMap<>();

    private final String pdpId;
    private boolean      closed = false;

    public RedisAttributeRepository(RedisClient client, String pdpId, int database) {
        this.client     = client;
        this.connection = client.connect();
        this.pubsub     = client.connectPubSub();
        this.commands   = connection.sync();
        this.pdpId      = pdpId;

        requireKeyspaceNotificationsEnabled();

        // Subscribe to needed channels
        pubsub.sync().psubscribe(CHANGES_CHANNEL_PREFIX + "*");
        pubsub.sync().subscribe("__keyevent@" + database + "__:expired");

        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                notifyObservers(message, Value.UNDEFINED);
            }

            @Override
            public void message(String pattern, String channel, String message) {
                // Regex: sapl:changes:* -> All change events that are made on attributes
                String redisKey = channel.substring(CHANGES_CHANNEL_PREFIX.length());
                Value  value    = UNDEFINED_STRING.equals(message) ? Value.UNDEFINED
                        : ValueJsonMarshaller.json(message);

                notifyObservers(redisKey, value);
            }
        });

        // Monitor the current connection and reconnect if the connection was interrupted
        pubsub.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
                // Creates a new thread that is executed by the re-sync executer. The main event loop threads doesn't
                // block that way.
                CompletableFuture.runAsync(RedisAttributeRepository.this::resyncObservers, resyncExecutor)
                        .exceptionally(e -> {
                            log.error(ERROR_RESYNC_FAILED, e);
                            return null;
                        });
            }

            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                List<String> keys;
                lock.lock();
                try {
                    keys = new ArrayList<>(observersByKey.keySet());
                } finally {
                    lock.unlock();
                }
                for (String redisKey : keys) {
                    notifyObservers(redisKey, Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId)));
                }
            }
        });
    }

    private void requireKeyspaceNotificationsEnabled() {
        String flags = commands.configGet(NOTIFY_KEYSPACE_EVENTS_PARAM).getOrDefault(NOTIFY_KEYSPACE_EVENTS_PARAM, "");
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
            observersByKey.clear();
        } finally {
            lock.unlock();
        }

        pubsub.sync().unsubscribe();
        pubsub.sync().punsubscribe();

        // Closes the executer properly instead of waiting too long. Warning in Sonar
        resyncExecutor.shutdown();
        try {
            if (!resyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                resyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            resyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

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
        fields.put(FIELD_NAME, key.name());
        fields.put(FIELD_ARGUMENTS, valuesToJson(key.arguments()));
        fields.put(FIELD_VALUE, redisValue);

        if (key.entity() != null) {
            fields.put(FIELD_ENTITY, ValueJsonMarshaller.toJsonString(key.entity()));
        }

        boolean created = commands.exists(redisKey) == 0;
        commands.hset(redisKey, fields);

        if (created) {
            long sequence = commands.incr(getSequenceKKey());
            commands.zadd(getOrderKey(), sequence, redisKey);
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

        commands.del(toRedisKey(key));
        commands.publish(CHANGES_CHANNEL_PREFIX + toRedisKey(key), UNDEFINED_STRING);
        commands.zrem(getOrderKey(), redisKey);
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
                String raw = commands.hget(redisKey, FIELD_VALUE);
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

        return ATTRIBUTE_KEY_PREFIX + pdpId + ":" + entity + ":" + key.name() + ":" + arguments;
    }

    private String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private void notifyObservers(String redisKey, Value value) {
        List<Consumer<Value>> toFire;
        lock.lock();
        try {
            if (closed) {
                return;
            }

            var bucket = observersByKey.get(redisKey);
            toFire = bucket != null ? new ArrayList<>(bucket) : List.of();
        } finally {
            lock.unlock();
        }

        for (Consumer<Value> callback : toFire) {
            try {
                callback.accept(value);
            } catch (RuntimeException e) {
                log.warn(WARN_OBSERVER_THREW_EXCEPTION, redisKey, e.getMessage(), e);
            }
        }
    }

    private void resyncObservers() {
        List<String> keysToResync;
        lock.lock();
        try {
            keysToResync = new ArrayList<>(observersByKey.keySet());
        } finally {
            lock.unlock();
        }

        for (String redisKey : keysToResync) {
            try {
                String raw   = commands.hget(redisKey, FIELD_VALUE);
                Value  value = (raw == null || UNDEFINED_STRING.equals(raw)) ? Value.UNDEFINED
                        : ValueJsonMarshaller.json(raw);
                notifyObservers(redisKey, value);
            } catch (RuntimeException e) {
                log.warn(WARN_RESYNC_KEY_FAILED, redisKey, e.getMessage(), e);
            }
        }
    }

    private String getOrderKey() {
        return ORDER_KEY_PREFIX + pdpId;
    }

    private String getSequenceKKey() {
        return SEQ_KEY_PREFIX + pdpId;
    }
}
