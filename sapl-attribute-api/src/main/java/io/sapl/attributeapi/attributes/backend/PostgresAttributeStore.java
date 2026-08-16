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

import io.r2dbc.spi.Readable;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

public class PostgresAttributeStore implements AttributeStore {
    private static final String PDP_ID_FIELD           = "pdpId";
    private static final String NAME_FIELD             = "name";
    private static final String ENTITY_FIELD           = "entity";
    private static final String ARGUMENTS_FIELD        = "arguments";
    private static final String VALUE_FIELD            = "value";
    private static final String ERROR_TTL_NOT_POSITIVE = "TTL must be a strictly positive Duration.";
    private static final String NOTIFY_SQL             = "SELECT pg_notify('attribute_changes', :payload)";

    private final DatabaseClient client;

    private final String countSql;
    private final String getSql;
    private final String getAllSql;
    private final String upsertSql;
    private final String deleteSql;

    public PostgresAttributeStore(DatabaseClient client, String table) {
        this.client = client;

        this.countSql  = "SELECT count(*) FROM " + table
                + " WHERE pdp_id = :pdpId AND (expires_at IS NULL OR expires_at > NOW())";
        this.getSql    = "SELECT value FROM " + table + " WHERE pdp_id = :pdpId AND name = :name "
                + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb) AND (expires_at IS NULL OR expires_at > NOW())";
        this.getAllSql = "SELECT name, entity, arguments, value FROM " + table + " WHERE pdp_id = :pdpId "
                + "AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY name, entity, arguments LIMIT :limit OFFSET :offset";
        // ON CONFLICT triggers the unique constraint in the db if the value already
        // exists
        // Indexes:
        // "attributes_pdp_id_name_entity_arguments_key" UNIQUE CONSTRAINT, btree
        // (pdp_id, name, entity, arguments) NULLS NOT DISTINCT
        // DO UPDATE executes an update statement instead. This logic implements a real
        // upsert and an atomic execution
        // The atomic execution is important to have the same Decision if a multi node
        // setup is used
        // xmax is an internal variable from Postgres to track if a row was ~ deleted, updated, ...
        this.upsertSql = "INSERT INTO " + table + " (pdp_id, name, entity, arguments, value, expires_at) "
                + "VALUES (:pdpId, :name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt) "
                + "ON CONFLICT (pdp_id, name, entity, arguments) "
                + "DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at "
                + "RETURNING (xmax = 0) AS inserted";
        this.deleteSql = "DELETE FROM " + table + " WHERE pdp_id = :pdpId AND name = :name "
                + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb)";
    }

    @Override
    public boolean publish(AttributeKey key, Value value, String pdpId) {
        return upsertToDB(key, value, null, pdpId);
    }

    @Override
    public boolean publish(AttributeKey key, Value value, Duration ttl, String pdpId) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        return upsertToDB(key, value, Instant.now().plus(ttl), pdpId);
    }

    @Override
    public void remove(AttributeKey key, String pdpId) {
        deleteFromDB(key, pdpId);
    }

    @Override
    public Long count(String pdpId) {
        var spec = client.sql(countSql).bind(PDP_ID_FIELD, pdpId);
        return spec.map(row -> Objects.requireNonNull(row.get(0, Long.class))).one().block();
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client.sql(getSql).bind(PDP_ID_FIELD, pdpId).bind(NAME_FIELD, key.name()).bind(ARGUMENTS_FIELD,
                argumentsJson);

        return bindEntity(spec, entityJson).map(r -> {
            String raw = r.get(VALUE_FIELD, String.class);
            return raw != null ? ValueJsonMarshaller.json(raw) : Value.UNDEFINED;
        }).one().blockOptional().orElse(Value.UNDEFINED);
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        var spec = client.sql(getAllSql).bind(PDP_ID_FIELD, pdpId);

        spec = limit != null ? spec.bind("limit", limit) : spec.bindNull("limit", Integer.class);
        spec = offset != null ? spec.bind("offset", offset) : spec.bindNull("offset", Integer.class);

        return spec.map(PostgresAttributeStore::mapRow).all().collectList().block();
    }

    @Override
    public void close() {
        // Noop: The used Postgres implementation is a Spring-managed bean shared across the application
        // The class is not responsible for it's lifecycle
    }

    private void notifyPdp(@NonNull AttributeKey key, String pdpId) {
        var payload = ValueJsonMarshaller.toJsonString(
                ObjectValue.builder().put(PDP_ID_FIELD, Value.of(pdpId)).put(NAME_FIELD, Value.of(key.name()))
                        .put(ENTITY_FIELD, key.entity() != null ? key.entity() : Value.NULL)
                        .put(ARGUMENTS_FIELD, Value.ofArray(key.arguments())).build());

        client.sql(NOTIFY_SQL).bind("payload", payload).then().block();
    }

    private boolean upsertToDB(@NonNull AttributeKey key, Value value, @Nullable Instant expiresAt, String pdpId) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var valueJson     = ValueJsonMarshaller.toJsonString(value);

        var upsertSpec = client.sql(upsertSql).bind(PDP_ID_FIELD, pdpId).bind(NAME_FIELD, key.name())
                .bind(ARGUMENTS_FIELD, argumentsJson).bind(VALUE_FIELD, valueJson);

        upsertSpec = expiresAt != null ? upsertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : upsertSpec.bindNull("expiresAt", OffsetDateTime.class);

        Boolean inserted = bindEntity(upsertSpec, entityJson).map(row -> row.get("inserted", Boolean.class)).one()
                .block();

        notifyPdp(key, pdpId);
        return Boolean.TRUE.equals(inserted);
    }

    private void deleteFromDB(@NonNull AttributeKey key, String pdpId) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var spec          = client.sql(deleteSql).bind(PDP_ID_FIELD, pdpId).bind(NAME_FIELD, key.name())
                .bind(ARGUMENTS_FIELD, argumentsJson);

        bindEntity(spec, entityJson).then().block();
        notifyPdp(key, pdpId);
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static AttributeEntry mapRow(Readable row) {
        String name         = row.get(NAME_FIELD, String.class);
        String entityRaw    = row.get(ENTITY_FIELD, String.class);
        String argumentsRaw = row.get(ARGUMENTS_FIELD, String.class);
        String valueRaw     = row.get(VALUE_FIELD, String.class);

        Value       entity    = entityRaw != null ? ValueJsonMarshaller.json(entityRaw) : null;
        List<Value> arguments = argumentsRaw != null && ValueJsonMarshaller.json(argumentsRaw) instanceof ArrayValue a
                ? a
                : List.of();
        Value       value     = ValueJsonMarshaller.json(Objects.requireNonNull(valueRaw));

        return new AttributeEntry(new AttributeKey(entity, Objects.requireNonNull(name), arguments), value);
    }

    private DatabaseClient.GenericExecuteSpec bindEntity(DatabaseClient.GenericExecuteSpec spec, String entityAsJson) {
        return (entityAsJson != null ? spec.bind(ENTITY_FIELD, entityAsJson)
                : spec.bindNull(ENTITY_FIELD, String.class));
    }

}
