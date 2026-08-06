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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

// Using R2DBC because it's reactive. JPA/Hibernate would block
// R2DBC offers persistent DB connections. A consistent connection
// is necessary because we need Postgres Pub/Sub
@Slf4j

public class PostgresAttributeRepository implements AttributeRepository {
    private static final String ERROR_HANDLE_NOTIFICATION = "Error while handling attribute_changes notification for pdpId '{}'";

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private final DatabaseClient       client;
    private final PostgresqlConnection connection;
    private final String               table;
    private final String               pdpId;

    private record DBEntry(String name, String entity, String arguments, String value, OffsetDateTime expiresAt) {}

    public PostgresAttributeRepository(DatabaseClient client,
            ConnectionFactory connection,
            String pdpId,
            String table) {

        this.client = client;
        this.pdpId  = pdpId;
        this.table  = table;
        // Connect to the right channel to receive changes
        this.connection = Mono.from(connection.create()).cast(PostgresqlConnection.class).block();

        Mono.from(Objects.requireNonNull(this.connection).createStatement("LISTEN attribute_changes").execute())
                .subscribe();

        // boundedElastic --> allowing a thread pool with blocking operations
        this.connection.getNotifications().mapNotNull(Notification::getParameter).publishOn(Schedulers.boundedElastic())
                .subscribe(this::handleNotification, error -> log.error(ERROR_HANDLE_NOTIFICATION, pdpId, error));

        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
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
        internalRepository.close();
        Mono.from(connection.close()).block();
    }

    public void loadFromDB() {
        var rows = client
                .sql("SELECT name, entity, arguments, value, expires_at FROM " + table + " WHERE pdp_id = :pdpId")
                .bind("pdpId", pdpId)
                .map(row -> new DBEntry(row.get("name", String.class), row.get("entity", String.class),
                        row.get("arguments", String.class), row.get("value", String.class),
                        row.get("expires_at", OffsetDateTime.class)))
                .all().collectList().block();

        if (rows == null)
            return;

        for (var row : rows) {
            var key       = new RepositoryKey(row.entity() != null ? ValueJsonMarshaller.json(row.entity()) : null,
                    row.name(), jsonToValues(row.arguments()), pdpId);
            var value     = ValueJsonMarshaller.json(row.value());
            var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;

            if (expiresAt != null) {
                var remainingTTL = Duration.between(Instant.now(), expiresAt);
                if (!remainingTTL.isNegative()) {
                    internalRepository.publish(key, value, remainingTTL);
                } else {
                    deleteFromDB(key);
                }
            } else {
                internalRepository.publish(key, value);
            }
        }
    }

    private void upsertToDB(@NonNull RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var valueJson     = ValueJsonMarshaller.toJsonString(value);

        // ON CONFLICT triggers the unique constraint in the db if the value already
        // exists
        // Indexes:
        // "attributes_pdp_id_name_entity_arguments_key" UNIQUE CONSTRAINT, btree
        // (pdp_id, name, entity, arguments) NULLS NOT DISTINCT
        // DO UPDATE executes an update statement instead. This logic implements a real
        // upsert and an atomic execution
        // The atomic execution is important to have the same Decision if a multi node
        // setup is used
        var upsertSpec = client.sql("INSERT INTO " + table + " (pdp_id, name, entity, arguments, value, expires_at) "
                + "VALUES (:pdpId, :name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt) "
                + "ON CONFLICT (pdp_id, name, entity, arguments) "
                + "DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at").bind("pdpId", pdpId)
                .bind("name", key.name()).bind("arguments", argumentsJson).bind("value", valueJson);

        upsertSpec = expiresAt != null ? upsertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : upsertSpec.bindNull("expiresAt", OffsetDateTime.class);

        (entityJson != null ? upsertSpec.bind("entity", entityJson) : upsertSpec.bindNull("entity", String.class))
                .then().block();
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client
                .sql("DELETE FROM " + table + " " + "WHERE pdp_id = :pdpId AND name = :name "
                        + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                        + "AND arguments = CAST(:arguments AS jsonb)")
                .bind("pdpId", pdpId).bind("name", key.name()).bind("arguments", argumentsJson);
        (entityJson != null ? spec.bind("entity", entityJson) : spec.bindNull("entity", String.class)).then().block();
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
        client.sql("SELECT pg_notify('attribute_changes', :payload)").bind("payload", keyToPayload(key)).then().block();
    }

    private void handleNotification(String payload) {
        var node              = (ObjectValue) ValueJsonMarshaller.json(payload);
        var notificationPdpId = ((TextValue) Objects.requireNonNull(node.get("pdpId"))).value();

        if (!pdpId.equals(notificationPdpId)) {
            return; // notification is for a different pdpId, not relevant to this repository
        }

        var key        = payloadToKey(node);
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;

        var spec = client.sql("SELECT name, entity, arguments, value, expires_at FROM " + table + " "
                + "WHERE pdp_id = :pdpId AND name = :name " + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb)").bind("pdpId", pdpId).bind("name", key.name())
                .bind("arguments", valuesToJson(key.arguments()));

        var row = (entityJson != null ? spec.bind("entity", entityJson) : spec.bindNull("entity", String.class))
                .map(r -> new DBEntry(r.get("name", String.class), r.get("entity", String.class),
                        r.get("arguments", String.class), r.get("value", String.class),
                        r.get("expires_at", OffsetDateTime.class)))
                .one().block();

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
        return ValueJsonMarshaller.toJsonString(ObjectValue.builder().put("pdpId", Value.of(pdpId))
                .put("name", Value.of(key.name())).put("entity", key.entity() != null ? key.entity() : Value.NULL)
                .put("arguments", Value.ofArray(key.arguments())).build());
    }

    private RepositoryKey payloadToKey(ObjectValue node) {
        var name      = ((TextValue) Objects.requireNonNull(node.get("name"))).value();
        var entityVal = node.get("entity");
        var entity    = entityVal == Value.NULL ? null : entityVal;
        var arguments = ((ArrayValue) Objects.requireNonNull(node.get("arguments"))).stream().toList();

        return new RepositoryKey(entity, name, arguments, pdpId);
    }
}
