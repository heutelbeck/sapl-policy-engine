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

import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.model.*;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import java.util.concurrent.locks.ReentrantLock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

/**
 * Repository implementation that persists attributes in PostgreSQL and maintains an In-Memory representation
 * by using the {@link InMemoryAttributeRepository} synchronized with PostgreSQL LISTEN/NOTIFY mechanism.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a disconnect happens because LISTEN/NOTIFY
 * needs a constant connection for the event stream. The current implementation performs a full re-sync when
 * the connection is established again.
 */
@Slf4j
public final class PostgresAttributeRepository implements AttributeRepository {
    private static final String ERROR_BACKEND_DISCONNECTED          = "The backend is currently disconnected for pdp with id %s.";
    private static final String ERROR_HANDLE_NOTIFICATION           = "Error while handling attribute_changes notification for pdpId '{}'";
    private static final String WARN_RECONNECTING                   = "Lost notification stream connection for pdpId '{}', reconnecting: {}";
    private static final String ERROR_RECONNECT_GIVEN_UP            = "Giving up reconnecting to notification stream for pdpId '{}' after repeated failures";
    private static final String DEBUG_STALE_CONNECTION_CLOSE_FAILED = "Error closing stale connection for pdpId '{}': {}";
    private static final String DEBUG_RECONNECT_FAILED              = "Reconnect attempt failed for pdpId '{}': {}";
    private static final String ERROR_NO_CONNECTION_FROM_FACTORY    = "Connection factory returned no connection for pdpId '";
    private static final String WARN_TTL_CLEANUP                    = "Could not schedule pg_cron TTL cleanup job. Install pg_cron extension"
            + "or grant right to the user. Expired attributes will still be filtered"
            + "at query time but Postgres-side cleanup will not happen for pdp with id {} : {}";
    private static final String CREATE_TABLE_SQL                    = """
            CREATE TABLE IF NOT EXISTS {table} (
                  id         BIGINT      GENERATED ALWAYS AS IDENTITY,
                  pdp_id     TEXT        NOT NULL,
                  name       TEXT        NOT NULL,
                  entity     JSONB,
                  arguments  JSONB       NOT NULL DEFAULT '[]',
                  value      JSONB       NOT NULL,
                  expires_at TIMESTAMPTZ,
                  CONSTRAINT {table}_pdp_id_name_entity_arguments_key
                      UNIQUE NULLS NOT DISTINCT (pdp_id, name, entity, arguments)
                  )
            """;
    private static final String SCHEDULE_TTL_CLEANUP_SQL            = """
            SELECT cron.schedule_in_database(
                'ttl-cleanup-{table}',
                '* * * * *',
                $$
                WITH deleted AS (
                    DELETE FROM {table}
                    WHERE expires_at < now()
                    RETURNING pdp_id, name, entity, arguments
                )
                SELECT pg_notify(
                    'attribute_changes',
                    json_build_object(
                        'pdpId', pdp_id,
                        'name', name,
                        'entity', entity,
                        'arguments', arguments
                    )::text
                )
                FROM deleted
                $$,
                current_database()
            )
            """;

    private static final String NOTIFY_SQL = "SELECT pg_notify('attribute_changes', :payload)";
    private static final String LISTEN_SQL = "LISTEN attribute_changes";

    private static final String FIELD_PDP_ID    = "pdpId";
    private static final String FIELD_NAME      = "name";
    private static final String FIELD_ENTITY    = "entity";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_VALUE     = "value";

    private static final Duration LIVENESS_TIMEOUT   = Duration.ofSeconds(15);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final String   HEARTBEAT_PDP_ID   = "__heartbeat__";

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    // Connection may be interrupted and needs to be replaced immediately
    private final AtomicReference<PostgresqlConnection> connection = new AtomicReference<>();
    private final AtomicReference<Disposable>           heartbeat  = new AtomicReference<>();
    private final ConnectionFactory                     connectionFactory;
    private final ReentrantLock                         reloadLock = new ReentrantLock();
    private volatile boolean                            closed     = false;

    // Set to true when trying to reconnect to the backend
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    private final DatabaseClient client;
    private final String         pdpId;

    // Built once per instance since they embed the (dynamic, per-instance) table name.
    private final String getAllSql;
    private final String getSql;
    private final String upsertSql;
    private final String deleteSql;

    private record DBEntry(String name, String entity, String arguments, String value, OffsetDateTime expiresAt) {}

    public PostgresAttributeRepository(DatabaseClient client,
            ConnectionFactory connectionFactory,
            String pdpId,
            String table) {

        this.client            = client;
        this.connectionFactory = connectionFactory;
        this.pdpId             = pdpId;

        client.sql(CREATE_TABLE_SQL.replace("{table}", table)).then().block();

        // If the scheduler job already exists, it is handled as an upsert and updated in place, not duplicated
        try {
            client.sql(SCHEDULE_TTL_CLEANUP_SQL.replace("{table}", table)).then().block();
        } catch (RuntimeException e) {
            log.warn(WARN_TTL_CLEANUP, pdpId, e.getMessage());
        }

        this.getAllSql = "SELECT name, entity, arguments, value, expires_at FROM " + table + " WHERE pdp_id = :pdpId";
        this.getSql    = "SELECT name, entity, arguments, value, expires_at FROM " + table + " "
                + "WHERE pdp_id = :pdpId AND name = :name AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb)";
        // ON CONFLICT triggers the unique constraint in the db if the value already exists.
        // Indexes: "attributes_pdp_id_name_entity_arguments_key" UNIQUE CONSTRAINT, btree
        // (pdp_id, name, entity, arguments) NULLS NOT DISTINCT
        // DO UPDATE executes an update statement instead. This logic implements a real
        // upsert and an atomic execution. The atomic execution is important to have the
        // same Decision if a multi node setup is used.
        this.upsertSql = "INSERT INTO " + table + " (pdp_id, name, entity, arguments, value, expires_at) "
                + "VALUES (:pdpId, :name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt) "
                + "ON CONFLICT (pdp_id, name, entity, arguments) "
                + "DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at";
        this.deleteSql = "DELETE FROM " + table + " WHERE pdp_id = :pdpId AND name = :name "
                + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb)";

        // Attribute loading
        connectAndListen();
        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
    }

    // Reconnect-Fix: Connections needs to be re-intialized after a disconnect. Before that it was only loaded once
    // during start time
    private void connectAndListen() {
        establishConnection();
        Flux.defer(() -> connection.get().getNotifications()).mapNotNull(Notification::getParameter)
                .timeout(LIVENESS_TIMEOUT).publishOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .filter(throwable -> !closed).doBeforeRetry(signal -> {
                            log.warn(WARN_RECONNECTING, pdpId, signal.failure().getMessage());
                            if (signal.totalRetries() > 0 && disconnected.compareAndSet(false, true)) {
                                for (var key : internalRepository.knownKeys()) {
                                    internalRepository.publish(key,
                                            Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId)));
                                }
                            }
                            try {
                                establishConnection();
                                disconnected.set(false);
                                loadFromDB();
                            } catch (Exception e) {
                                log.debug(DEBUG_RECONNECT_FAILED, pdpId, e.getMessage());
                            }
                        }))
                .subscribe(this::handleNotification, error -> log.error(ERROR_RECONNECT_GIVEN_UP, pdpId, error));

        // Start the heartbeat subscription to monitor on a different subscription channel if a disconnect did happen
        heartbeat.set(Flux.interval(HEARTBEAT_INTERVAL, Schedulers.boundedElastic()).filter(tick -> !closed)
                .concatMap(tick -> client.sql(NOTIFY_SQL)
                        .bind("payload",
                                ValueJsonMarshaller.toJsonString(
                                        ObjectValue.builder().put(FIELD_PDP_ID, Value.of(HEARTBEAT_PDP_ID)).build()))
                        .then().onErrorResume(e -> Mono.empty()))
                .subscribe());
    }

    private void establishConnection() {
        PostgresqlConnection newConnection = Objects.requireNonNull(
                Mono.from(connectionFactory.create()).cast(PostgresqlConnection.class).block(),
                ERROR_NO_CONNECTION_FROM_FACTORY + pdpId + "'");
        Mono.from(newConnection.createStatement(LISTEN_SQL).execute()).block();

        PostgresqlConnection previous = this.connection.getAndSet(newConnection);

        if (previous != null) {
            Mono.from(previous.close()).subscribe(v -> {},
                    e -> log.debug(DEBUG_STALE_CONNECTION_CLOSE_FAILED, pdpId, e.getMessage()));
        }
    }

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);

        void close();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        internalRepository.publish(key, value);
        upsertToDB(key, value, null);
        notifyOthers(key);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        internalRepository.publish(key, value, ttl);
        upsertToDB(key, value, Instant.now().plus(ttl));
        notifyOthers(key);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        internalRepository.remove(key);
        deleteFromDB(key);
        notifyOthers(key);
    }

    @Override
    public void close() {
        closed = true;
        internalRepository.close();
        var heartbeatStatus = heartbeat.get();
        if (heartbeatStatus != null) {
            heartbeatStatus.dispose();
        }

        Mono.from(connection.get().close()).block();
    }

    private void loadFromDB() {
        reloadLock.lock();
        try {
            var rows = client.sql(getAllSql).bind(FIELD_PDP_ID, pdpId)
                    .map(row -> new DBEntry(row.get(FIELD_NAME, String.class), row.get(FIELD_ENTITY, String.class),
                            row.get(FIELD_ARGUMENTS, String.class), row.get(FIELD_VALUE, String.class),
                            row.get("expires_at", OffsetDateTime.class)))
                    .all().collectList().block();

            if (rows == null)
                return;

            var seenKeys = new HashSet<RepositoryKey>();

            for (var row : rows) {
                restoreKey(row, seenKeys);
            }

            removeStaleKeys(seenKeys);
        } finally {
            reloadLock.unlock();
        }
    }

    private void upsertToDB(@NonNull RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var valueJson     = ValueJsonMarshaller.toJsonString(value);

        var upsertSpec = client.sql(upsertSql).bind(FIELD_PDP_ID, pdpId).bind(FIELD_NAME, key.name())
                .bind(FIELD_ARGUMENTS, argumentsJson).bind(FIELD_VALUE, valueJson);

        upsertSpec = expiresAt != null ? upsertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : upsertSpec.bindNull("expiresAt", OffsetDateTime.class);

        (entityJson != null ? upsertSpec.bind(FIELD_ENTITY, entityJson)
                : upsertSpec.bindNull(FIELD_ENTITY, String.class)).then().block();
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client.sql(deleteSql).bind(FIELD_PDP_ID, pdpId).bind(FIELD_NAME, key.name()).bind(FIELD_ARGUMENTS,
                argumentsJson);
        (entityJson != null ? spec.bind(FIELD_ENTITY, entityJson) : spec.bindNull(FIELD_ENTITY, String.class)).then()
                .block();
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static List<Value> jsonToValues(String json) {
        if (json == null || json.isBlank())
            return List.of();
        return (ArrayValue) ValueJsonMarshaller.json(json);
    }

    private void notifyOthers(RepositoryKey key) {
        // The select is an alternative way to trigger the NOTIFY attribute_changes
        // 'payload' or pg_notify function in Postgres
        client.sql(NOTIFY_SQL).bind("payload", keyToPayload(key)).then().block();
    }

    private void handleNotification(String payload) {
        reloadLock.lock();
        try {
            handleNotificationLocked(payload);
        } catch (Exception e) {
            log.error(ERROR_HANDLE_NOTIFICATION, pdpId, e);
        } finally {
            reloadLock.unlock();
        }
    }

    private void handleNotificationLocked(String payload) {
        var node              = (ObjectValue) ValueJsonMarshaller.json(payload);
        var notificationPdpId = ((TextValue) Objects.requireNonNull(node.get(FIELD_PDP_ID))).value();

        // Ignore notifications for other pdpIds
        if (!pdpId.equals(notificationPdpId)) {
            return;
        }

        var key        = payloadToKey(node);
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;

        // Query the key from the notification to decide if it's an update or delete
        var spec = client.sql(getSql).bind(FIELD_PDP_ID, pdpId).bind(FIELD_NAME, key.name()).bind(FIELD_ARGUMENTS,
                valuesToJson(key.arguments()));

        var row = (entityJson != null ? spec.bind(FIELD_ENTITY, entityJson) : spec.bindNull(FIELD_ENTITY, String.class))
                .map(r -> new DBEntry(r.get(FIELD_NAME, String.class), r.get(FIELD_ENTITY, String.class),
                        r.get(FIELD_ARGUMENTS, String.class), r.get(FIELD_VALUE, String.class),
                        r.get("expires_at", OffsetDateTime.class)))
                .one().block();

        // if: Key deleted, also delete in cache, else: Key is updated, publish key again in cache
        if (row == null) {
            internalRepository.remove(key);
        } else {
            var value     = ValueJsonMarshaller.json(row.value());
            var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;

            if (expiresAt != null) {
                var ttl = Duration.between(Instant.now(), expiresAt);
                if (!ttl.isNegative())
                    internalRepository.publish(key, value, ttl);
            } else {
                internalRepository.publish(key, value);
            }
        }
    }

    private String keyToPayload(RepositoryKey key) {
        return ValueJsonMarshaller.toJsonString(
                ObjectValue.builder().put(FIELD_PDP_ID, Value.of(pdpId)).put(FIELD_NAME, Value.of(key.name()))
                        .put(FIELD_ENTITY, key.entity() != null ? key.entity() : Value.NULL)
                        .put(FIELD_ARGUMENTS, Value.ofArray(key.arguments())).build());
    }

    private RepositoryKey payloadToKey(ObjectValue node) {
        var name      = ((TextValue) Objects.requireNonNull(node.get(FIELD_NAME))).value();
        var entityVal = node.get(FIELD_ENTITY);
        var entity    = entityVal == Value.NULL ? null : entityVal;
        var arguments = ((ArrayValue) Objects.requireNonNull(node.get(FIELD_ARGUMENTS))).stream().toList();

        return new RepositoryKey(entity, name, arguments, pdpId);
    }

    private void removeStaleKeys(Set<RepositoryKey> seenKeys) {
        // The InMemoryAttributeRepository functions as cache and needs to be cleaned up after a re-sync
        for (var staleKey : internalRepository.knownKeys()) {
            if (!seenKeys.contains(staleKey)) {
                internalRepository.remove(staleKey);
            }
        }
    }

    private void restoreKey(DBEntry row, Set<RepositoryKey> seenKeys) {
        var key       = new RepositoryKey(row.entity() != null ? ValueJsonMarshaller.json(row.entity()) : null,
                row.name(), jsonToValues(row.arguments()), pdpId);
        var value     = ValueJsonMarshaller.json(row.value());
        var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;

        if (expiresAt == null) {
            internalRepository.publish(key, value);
            seenKeys.add(key);
            return;
        }

        var remainingTTL = Duration.between(Instant.now(), expiresAt);
        if (remainingTTL.isNegative()) {
            deleteFromDB(key);
            return;
        }

        internalRepository.publish(key, value, remainingTTL);
        seenKeys.add(key);
    }
}
