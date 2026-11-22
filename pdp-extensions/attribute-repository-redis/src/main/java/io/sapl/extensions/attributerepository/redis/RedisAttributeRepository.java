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
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.ZAddArgs;
import io.lettuce.core.RedisChannelHandler;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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
    private static final String ERROR_FOREIGN_PDP_ID             = "Assignment for pdpId '%s' rejected by repository for pdpId '%s'.";
    private static final String ERROR_LOAD_CONFLICT              = "Could not read a consistent view for pdp id %s after %d attempts.";
    private static final String NOTIFY_KEYSPACE_EVENTS_PARAM     = "notify-keyspace-events";
    private static final String ERROR_KEYSPACE_NOTIFICATIONS_OFF = "Configure Redis to publish expired key events.";
    private static final String ERROR_HANDLE_MESSAGE             = "Failed to apply a Redis message for pdp with id {}.";
    private static final String FIELD_NAME                       = "name";
    private static final String FIELD_ENTITY                     = "entity";
    private static final String FIELD_ARGUMENTS                  = "arguments";
    private static final String FIELD_VALUE                      = "value";
    private static final String ATTRIBUTE_KEY_PREFIX             = "sapl:attribute:";
    private static final String CHANGES_CHANNEL_PREFIX           = "sapl:changes:";
    private static final String BATCH_CHANNEL_PREFIX             = "sapl:batch:";
    private static final String ORDER_KEY_PREFIX                 = "sapl:attribute:order:";

    private final RedisClient                                   client;
    private final StatefulRedisConnection<String, String>       connection;
    private final StatefulRedisConnection<String, String>       readConnection;
    private final RedisCommands<String, String>                 readCommands;
    private final RedisCommands<String, String>                 commands;
    private final StatefulRedisPubSubConnection<String, String> pubsub;
    private final ExecutorService                               worker             = Executors
            .newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final ReentrantLock                                 reloadLock         = new ReentrantLock();
    private final AtomicBoolean                                 closed             = new AtomicBoolean(false);
    private final AtomicBoolean                                 reloadQueued       = new AtomicBoolean(false);
    private static final Duration                               RELOAD_RETRY_DELAY = Duration.ofSeconds(1);

    private final String pdpId;
    private final String pdpKeyPrefix;

    private Map<RepositoryKey, ViewEntry>     lastView = Map.of();
    private final InMemoryAttributeRepository internalRepository;

    // Internal cache TTL codes
    private static final long PTTL_NO_EXPIRY    = -1;
    private static final int  MAX_LOAD_ATTEMPTS = 10;

    private record ViewEntry(Value value, @Nullable Instant expiresAt) {}

    /**
     * Creates the object for the Redis attribute repository to communicate with the
     * database. Listen to the channels, creates the internal cache and loads all
     * attributes.
     *
     * @param client The Redis client to communicate with the database.
     * @param pdpId The pdp id this attribute repository is for.
     * @param database The Redis database to find the attributes.
     */
    public RedisAttributeRepository(RedisClient client, String pdpId, int database) {
        this.client         = client;
        this.connection     = client.connect();
        this.readConnection = client.connect();
        this.readCommands   = readConnection.sync();
        this.pubsub         = client.connectPubSub();
        this.commands       = connection.sync();
        this.pdpId          = pdpId;

        requireKeyspaceNotificationsEnabled();

        // Important for the internal Cache
        this.pdpKeyPrefix       = ATTRIBUTE_KEY_PREFIX + pdpId + ":";
        this.internalRepository = new InMemoryAttributeRepository();

        // Subscribe to needed channels
        pubsub.sync().psubscribe(CHANGES_CHANNEL_PREFIX + "*");
        pubsub.sync().psubscribe(BATCH_CHANNEL_PREFIX + pdpId);
        pubsub.sync().subscribe("__keyevent@" + database + "__:expired");
        registerAttributeMessageListener();
        registerConnectionStateListener();
        loadFromDB();
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
        readConnection.close();
        client.close();
    }

    // Helper to get the right Redis key for this instance
    private String toRedisKey(RepositoryKey key) {
        String entity    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : "null";
        String arguments = valuesToJson(key.arguments());

        return ATTRIBUTE_KEY_PREFIX + pdpId + ":" + entity + ":" + key.name() + ":" + arguments;
    }

    private void registerAttributeMessageListener() {
        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                // expired event
                if (message.startsWith(pdpKeyPrefix)) {
                    requestReload();
                }
            }

            @Override
            public void message(String pattern, String channel, String message) {
                // channel: sapl:batch:<pdpId> only for this pdp id
                if (channel.startsWith(BATCH_CHANNEL_PREFIX)) {
                    requestReload();
                    return;
                }

                // channeL sapl:change:<redisKey>. Shared by all pdp ids
                if (channel.startsWith(pdpKeyPrefix, CHANGES_CHANNEL_PREFIX.length())) {
                    requestReload();
                }
            }

        });
    }

    private void registerConnectionStateListener() {
        // Monitor current connection and reconnect if connection was interrupted
        pubsub.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
                requestReload();
            }

            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                runOnWorker(RedisAttributeRepository.this::markAsDisconnected);
            }
        });
    }

    // Loads all attribute of the pdp id into the cache
    private void loadFromDB() {
        reloadLock.lock();
        try {
            var now     = Instant.now();
            var newView = readView(now);

            // Build the changes of the current known keys to bound values
            var changes = new ArrayList<AttributeAssignment>();
            for (var item : newView.entrySet()) {
                var key   = item.getKey();
                var entry = item.getValue();
                var old   = lastView.get(key);

                if (old == null || !old.equals(entry)) {
                    var ttl = entry.expiresAt() != null ? Duration.between(now, entry.expiresAt()) : null;
                    changes.add(new AttributeAssignment.BoundValue(key, entry.value(), ttl));
                }
            }

            // Build the changes of the current known keys to unbound values
            for (var key : lastView.keySet()) {
                if (!newView.containsKey(key)) {
                    changes.add(new AttributeAssignment.UnboundValue(key));
                }
            }

            internalRepository.publish(changes);
            lastView = newView;
        } finally {
            reloadLock.unlock();
        }
    }

    // read the current "view" of attributes from Redis
    private Map<RepositoryKey, ViewEntry> readView(Instant now) {
        var read = readCommands;

        // Load max 10 times otherwise throw exception
        for (int i = 0; i < MAX_LOAD_ATTEMPTS; i++) {
            read.watch(getOrderKey());
            var redisKeys = read.zrange(getOrderKey(), 0, -1);

            // Start the MULTI/EXEC block
            read.multi();
            for (var redisKey : redisKeys) {
                read.hgetall(redisKey);
                read.pttl(redisKey);
            }
            TransactionResult result = read.exec();

            if (!result.wasDiscarded()) {
                var view = new HashMap<RepositoryKey, ViewEntry>();

                int index = 0;
                for (int j = 0; j < redisKeys.size(); j++) {
                    Map<String, String> hash = result.get(index++);
                    Long                pttl = result.get(index++);

                    if (!hash.isEmpty() && (pttl > 0 || pttl == PTTL_NO_EXPIRY)) {
                        view.put(toKey(hash), toViewEntry(hash, pttl, now));
                    }
                }
                return view;
            }
        }
        throw new IllegalStateException(ERROR_LOAD_CONFLICT.formatted(pdpId, MAX_LOAD_ATTEMPTS));
    }

    private RepositoryKey toKey(Map<String, String> hash) {
        var entityJson = hash.get(FIELD_ENTITY);
        return new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null, hash.get(FIELD_NAME),
                jsonToValues(hash.get(FIELD_ARGUMENTS)), pdpId);
    }

    private static ViewEntry toViewEntry(Map<String, String> hash, long pttl, Instant now) {
        var raw = hash.get(FIELD_VALUE);
        return new ViewEntry(raw == null ? Value.UNDEFINED : ValueJsonMarshaller.json(raw),
                pttl == PTTL_NO_EXPIRY ? null : now.plusMillis(pttl));
    }

    private void markAsDisconnected() {
        reloadLock.lock();
        try {
            var error     = Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId));
            var changes   = new ArrayList<AttributeAssignment>();
            var errorView = new HashMap<RepositoryKey, ViewEntry>();

            for (var key : lastView.keySet()) {
                changes.add(new AttributeAssignment.BoundValue(key, error, null));
                errorView.put(key, new ViewEntry(error, null));
            }
            internalRepository.publish(changes);
            lastView = errorView;

        } finally {
            reloadLock.unlock();
        }
    }

    // Redis worker == background jobs
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

    // queues the reload if multiple reloads occur
    private void requestReload() {
        if (reloadQueued.compareAndSet(false, true)) {
            runOnWorker(() -> {
                reloadQueued.set(false);
                try {
                    loadFromDB();
                } catch (RuntimeException e) {
                    log.error(ERROR_HANDLE_MESSAGE, pdpId, e);
                    CompletableFuture.delayedExecutor(RELOAD_RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                            .execute(this::requestReload);
                }
            });
        }
    }

    @Override
    public void publish(@NonNull List<AttributeAssignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }

        // Fallback: foreign assignments aren't executed
        for (var assignment : assignments) {
            if (!pdpId.equals(assignment.key().pdpId())) {
                throw new IllegalArgumentException(ERROR_FOREIGN_PDP_ID.formatted(assignment.key().pdpId(), pdpId));
            }
        }

        try (var tx = client.connect()) {
            var write = tx.sync();
            write.multi();

            for (var assignment : assignments) {
                switch (assignment) {
                case AttributeAssignment.BoundValue(var key, var value, var ttl) -> {
                    var redisKey = toRedisKey(key);

                    // Write key, keep with nx the position of existing keys
                    write.hset(redisKey, toFields(key, value));
                    write.zadd(getOrderKey(), ZAddArgs.Builder.nx(), System.currentTimeMillis(), redisKey);

                    if (ttl == null) {
                        write.persist(redisKey);
                    } else {
                        write.pexpire(redisKey, ttl.toMillis());
                    }
                }

                case AttributeAssignment.UnboundValue(var key) -> {
                    var redisKey = toRedisKey(key);
                    write.del(redisKey);
                    write.zrem(getOrderKey(), redisKey);
                }
                }
            }
            // Start the EXEC block
            write.publish(BATCH_CHANNEL_PREFIX + pdpId, pdpId);
            write.exec();
        }

        // update your own view
        loadFromDB();
    }

    @Override
    public StateRegistration observeState(String pdpId, String configurationId, Consumer<RepositoryState> onUpdate) {
        return internalRepository.observeState(pdpId, configurationId, onUpdate);
    }

    private static List<Value> jsonToValues(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return (ArrayValue) ValueJsonMarshaller.json(json);
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    // table of content for the Redis keys
    private String getOrderKey() {
        return ORDER_KEY_PREFIX + pdpId;
    }

    // Helper: converts into the right Redis fields
    private Map<String, String> toFields(RepositoryKey key, Value value) {
        var fields = new HashMap<String, String>();
        fields.put(FIELD_NAME, key.name());
        fields.put(FIELD_ARGUMENTS, valuesToJson(key.arguments()));
        fields.put(FIELD_VALUE, ValueJsonMarshaller.toJsonString(value));
        if (key.entity() != null) {
            fields.put(FIELD_ENTITY, ValueJsonMarshaller.toJsonString(key.entity()));
        }
        return fields;
    }
}
