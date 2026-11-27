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
package io.sapl.extensions.attributerepository.postgres;

import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.model.*;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.HashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;

/**
 * Repository implementation that persists attributes in PostgreSQL and
 * maintains an In-Memory representation by using the
 * {@link InMemoryAttributeRepository} synchronized with PostgreSQL
 * LISTEN/NOTIFY mechanism.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a
 * disconnect happens because LISTEN/NOTIFY needs a constant connection for the
 * event stream. The current implementation performs a full re-sync when the
 * connection is established again.
 */
@Slf4j
public final class PostgresAttributeRepository implements AttributeRepository {
    private static final String ERROR_FOREIGN_PDP_ID                = "Assignment for pdpId '%s' rejected by repository for pdpId '%s'.";
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

    private static final String CREATE_TABLE_SQL = """
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

    private static final String SCHEDULE_TTL_CLEANUP_SQL = """
            SELECT cron.schedule(
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
                $$
            )
            """;

    private static final String NOTIFY_SQL      = "SELECT pg_notify('attribute_changes', :payload)";
    private static final String LISTEN_SQL      = "LISTEN attribute_changes";
    private static final String FIELD_PDP_ID    = "pdpId";
    private static final String FIELD_NAME      = "name";
    private static final String FIELD_ENTITY    = "entity";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_VALUE     = "value";

    private static final Duration                       LIVENESS_TIMEOUT         = Duration.ofSeconds(15);
    private static final Duration                       HEARTBEAT_INTERVAL       = Duration.ofSeconds(5);
    private static final Duration                       CONNECT_TIMEOUT          = Duration.ofSeconds(5);
    private static final Duration                       QUERY_TIMEOUT            = Duration.ofSeconds(30);
    private static final Duration                       LOAD_IDLE_TIMEOUT        = Duration.ofSeconds(5);
    private static final Duration                       RELOAD_RETRY_DELAY       = Duration.ofSeconds(1);
    private static final Duration                       RECONNECT_RETRY_INTERVAL = Duration.ofSeconds(1);
    private static final String                         HEARTBEAT_PDP_ID         = "__heartbeat__";
    private final AtomicReference<PostgresqlConnection> connection               = new AtomicReference<>();
    private final AtomicReference<Disposable>           heartbeat                = new AtomicReference<>();
    private final AtomicReference<Disposable>           notificationSubscription = new AtomicReference<>();
    private final ConnectionFactory                     connectionFactory;
    private final ReentrantLock                         reloadLock               = new ReentrantLock();
    private volatile boolean                            closed                   = false;
    private final AtomicBoolean                         disconnected             = new AtomicBoolean(false);
    private final AtomicReference<Disposable>           reloadRetry              = new AtomicReference<>();
    private final DatabaseClient                        client;
    private final TransactionalOperator                 transactions;
    private final String                                pdpId;
    private final String                                getAllSql;
    private final String                                upsertSql;
    private final String                                deleteSql;
    private Map<RepositoryKey, ViewEntry>               lastView                 = Map.of();
    private final Set<RepositoryKey>                    knownKeys                = ConcurrentHashMap.newKeySet();

    private record DBEntry(String name, String entity, String arguments, String value, OffsetDateTime expiresAt) {}

    private record ViewEntry(Value value, @Nullable Instant expiresAt) {}

    private final InMemoryAttributeRepository internalRepository;

    /**
     * Creates the object for the Postgres attribute repository to communicate with
     * the database. Listen to the channels, prepares common statements, creates the
     * internal cache and loads all attributes.
     *
     * @param client The Postgres client to execute commands with.
     * @param connectionFactory The Postgres connection.
     * @param pdpId The pdp is this attribute repository is for.
     * @param table The table that contains the attributes.
     */
    public PostgresAttributeRepository(DatabaseClient client,
            ConnectionFactory connectionFactory,
            String pdpId,
            String table) {

        this.client            = client;
        this.connectionFactory = connectionFactory;
        this.pdpId             = pdpId;
        this.transactions      = TransactionalOperator
                .create(new R2dbcTransactionManager(client.getConnectionFactory()));

        client.sql(CREATE_TABLE_SQL.replace("{table}", table)).then().block();

        // Updated if job already exists
        try {
            client.sql(SCHEDULE_TTL_CLEANUP_SQL.replace("{table}", table)).then().block();
        } catch (RuntimeException e) {
            log.warn(WARN_TTL_CLEANUP, pdpId, e.getMessage());
        }

        this.getAllSql = "SELECT name, entity, arguments, value, expires_at FROM " + table
                + " WHERE pdp_id = :pdpId AND (expires_at IS NULL OR expires_at > :now)";

        this.upsertSql = "INSERT INTO " + table + " (pdp_id, name, entity, arguments, value, expires_at) "
                + "VALUES (:pdpId, :name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt) "
                + "ON CONFLICT (pdp_id, name, entity, arguments) "
                + "DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at";

        this.deleteSql = "DELETE FROM " + table + " WHERE pdp_id = :pdpId AND name = :name "
                + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb)";

        // Attribute loading
        this.internalRepository = new InMemoryAttributeRepository();
        connectAndListen();
        loadFromDB();
    }

    private void connectAndListen() {
        // @formatter:off
		establishConnection();

		notificationSubscription.set(Flux.defer(() -> connection
				.get().getNotifications())
				.mapNotNull(Notification::getParameter)
				.timeout(LIVENESS_TIMEOUT)
				.filter(this::isForThisPdp)
				.onBackpressureLatest()
				.publishOn(Schedulers.boundedElastic(), 1)
				.retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(0))
						.filter(throwable -> !closed)
						.doBeforeRetry(this::handleNotificationStreamRetry))
				.subscribe(this::handleNotification, error -> log.error(ERROR_RECONNECT_GIVEN_UP, pdpId, error)));

		// Start a hearbeat interval to monitor the connection on a different channel
		heartbeat.set(Flux.interval(HEARTBEAT_INTERVAL, Schedulers.boundedElastic())
				.filter(tick -> !closed)
				.concatMap(tick -> client.sql(NOTIFY_SQL)
						.bind("payload",
								ValueJsonMarshaller.toJsonString(
										ObjectValue.builder().put(FIELD_PDP_ID, Value.of(HEARTBEAT_PDP_ID)).build()))
						.then().timeout(CONNECT_TIMEOUT).onErrorResume(e -> Mono.empty()))
				.subscribe());
		// @formatter:on
    }

    private void establishConnection() {
        PostgresqlConnection newConnection = Objects.requireNonNull(
                Mono.from(connectionFactory.create()).cast(PostgresqlConnection.class).timeout(CONNECT_TIMEOUT).block(),
                ERROR_NO_CONNECTION_FROM_FACTORY + pdpId + "'");
        Mono.from(newConnection.createStatement(LISTEN_SQL).execute()).timeout(CONNECT_TIMEOUT).block();

        PostgresqlConnection previous = this.connection.getAndSet(newConnection);

        if (previous != null) {
            Mono.from(previous.close()).subscribe(v -> {},
                    e -> log.debug(DEBUG_STALE_CONNECTION_CLOSE_FAILED, pdpId, e.getMessage()));
        }
    }

    @Override
    public void close() {
        closed = true;
        internalRepository.close();
        heartbeat.get().dispose();
        notificationSubscription.get().dispose();

        var retry = reloadRetry.get();
        if (retry != null) {
            retry.dispose();
        }

        Mono.from(connection.get().close()).timeout(QUERY_TIMEOUT).block();
    }

    // @formatter:off
	private void loadFromDB() {
		reloadLock.lock();

		try {
			var newView = new HashMap<RepositoryKey, ViewEntry>();
			var now  = OffsetDateTime.now(ZoneOffset.UTC);
			var rows = Objects.requireNonNull(client.sql(getAllSql).bind(FIELD_PDP_ID, pdpId).bind("now", now)
					.map(row -> new DBEntry(
							row.get(FIELD_NAME, String.class),
							row.get(FIELD_ENTITY, String.class),
							row.get(FIELD_ARGUMENTS, String.class),
							row.get(FIELD_VALUE, String.class),
							row.get("expires_at", OffsetDateTime.class)))
					.all().timeout(LOAD_IDLE_TIMEOUT).collectList().block());

			// View of the current state in DB
			for (var row : rows) {
				newView.put(toKey(row), toViewEntry(row));
			}

			// Build the changes of the current known keys to bound values
			var changes = new ArrayList<AttributeAssignment>();
			for (var item : newView.entrySet()) {
				var key   = item.getKey();
				var entry = item.getValue();
				var old   = lastView.get(key);

				if (old == null || !old.equals(entry)) {
					var ttl = entry.expiresAt() != null ? Duration.between(now.toInstant(), entry.expiresAt()) : null;
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
			knownKeys.addAll(newView.keySet());
		} finally {
			reloadLock.unlock();
		}
	}
	// @formatter:on

    private ViewEntry toViewEntry(DBEntry row) {
        var value     = ValueJsonMarshaller.json(row.value());
        var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;
        return new ViewEntry(value, expiresAt);
    }

    private RepositoryKey toKey(DBEntry row) {
        var entity = row.entity != null ? ValueJsonMarshaller.json(row.entity) : null;
        return new RepositoryKey(entity, row.name(), jsonToValues(row.arguments()), pdpId);
    }

    // Generates the upsert spec for the SQL query
    private DatabaseClient.GenericExecuteSpec upsertSpec(RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;

        // @formatter:off
		var upsertSpec = client.sql(upsertSql)
	            .bind(FIELD_PDP_ID, pdpId)
	            .bind(FIELD_NAME, key.name())
	            .bind(FIELD_ARGUMENTS, valuesToJson(key.arguments()))
	            .bind(FIELD_VALUE, ValueJsonMarshaller.toJsonString(value));
        // @formatter:on

        upsertSpec = expiresAt != null ? upsertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : upsertSpec.bindNull("expiresAt", OffsetDateTime.class);

        return entityJson != null ? upsertSpec.bind(FIELD_ENTITY, entityJson)
                : upsertSpec.bindNull(FIELD_ENTITY, String.class);
    }

    // Generates the delete spec for the SQL query
    private DatabaseClient.GenericExecuteSpec deleteSpec(@NonNull RepositoryKey key) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;

        // @formatter:off
		var deleteSpec = client.sql(deleteSql)
				.bind(FIELD_PDP_ID, pdpId)
				.bind(FIELD_NAME, key.name())
				.bind(FIELD_ARGUMENTS, valuesToJson(key.arguments()));
		// @formatter:on

        return entityJson != null ? deleteSpec.bind(FIELD_ENTITY, entityJson)
                : deleteSpec.bindNull(FIELD_ENTITY, String.class);
    }

    // Handles a notification from Postgres LISTEN/NOTIFY
    private void handleNotification(String payload) {
        try {
            loadFromDB();
        } catch (RuntimeException e) {
            log.error(ERROR_HANDLE_NOTIFICATION, pdpId, e);
            scheduleReloadRetry();
        }
    }

    private void handleNotificationStreamRetry(RetrySignal signal) {
        log.warn(WARN_RECONNECTING, pdpId, signal.failure().getMessage());
        if (disconnected.compareAndSet(false, true)) {
            markAsDisconnected();
        }
        reconnectAndReload();
    }

    // Handles the reconnect loop and load all attributes from the DB
    private void reconnectAndReload() {
        while (!closed) {
            try {
                establishConnection();
                disconnected.set(false);
                loadFromDB();
                return;
            } catch (Exception e) {
                disconnected.set(true);
                log.debug(DEBUG_RECONNECT_FAILED, pdpId, e.getMessage());
                try {
                    Thread.sleep(RECONNECT_RETRY_INTERVAL.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void markAsDisconnected() {
        reloadLock.lock();
        try {
            var error     = Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId));
            var changes   = new ArrayList<AttributeAssignment>();
            var errorView = new HashMap<RepositoryKey, ViewEntry>();

            for (var key : knownKeys) {
                changes.add(new AttributeAssignment.BoundValue(key, error, null));
                errorView.put(key, new ViewEntry(error, null));
            }
            internalRepository.publish(changes);
            lastView = errorView;

        } finally {
            reloadLock.unlock();
        }
    }

    private void scheduleReloadRetry() {
        if (closed || disconnected.get()) {
            return;
        }

        var retry = Mono.delay(RELOAD_RETRY_DELAY, Schedulers.boundedElastic()).subscribe(tick -> {
            try {
                loadFromDB();
            } catch (RuntimeException e) {
                log.error(ERROR_HANDLE_NOTIFICATION, pdpId, e);
                scheduleReloadRetry();
            }
        });

        // Avoid multiple reloads
        var previous = reloadRetry.getAndSet(retry);
        if (previous != null) {
            previous.dispose();
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

        var writes = Flux.fromIterable(assignments).concatMap(assignment -> switch (assignment) {
        case AttributeAssignment.BoundValue(var key, var value, var ttl) ->
            upsertSpec(key, value, ttl != null ? Instant.now().plus(ttl) : null).then();
        case AttributeAssignment.UnboundValue(var key)                   -> deleteSpec(key).then();
        });

        writes.then(client.sql(NOTIFY_SQL).bind("payload", notificationPayload()).then())
                .as(transactions::transactional).timeout(QUERY_TIMEOUT).block();

        // update your own view
        loadFromDB();
    }

    @Override
    public StateRegistration observeState(String pdpId, String configurationId, Consumer<RepositoryState> onUpdate) {
        return internalRepository.observeState(pdpId, configurationId, state -> {
            // While disconnected: every states becomes unavailable
            if (disconnected.get()) {
                onUpdate.accept(new RepositoryState(state.revision(), Map.of(),
                        Value.error(ERROR_BACKEND_DISCONNECTED.formatted(this.pdpId))));
            } else {
                onUpdate.accept(state);
            }
        });
    }

    private String notificationPayload() {
        return ValueJsonMarshaller.toJsonString(ObjectValue.builder().put(FIELD_PDP_ID, Value.of(pdpId)).build());
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static List<Value> jsonToValues(String json) {
        return (ArrayValue) ValueJsonMarshaller.json(json);
    }

    private boolean isForThisPdp(String payload) {
        try {
            var node = (ObjectValue) ValueJsonMarshaller.json(payload);
            return node.get(FIELD_PDP_ID) instanceof TextValue id && pdpId.equals(id.value());
        } catch (RuntimeException e) {
            log.error(ERROR_HANDLE_NOTIFICATION, pdpId, e);
            return false;
        }
    }
}
