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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class MongoAttributeStore implements AttributeStore {
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";
    private static final String ERROR_PDP_ID_IS_EMPTY  = "pdpId must be resolved before reaching the store";

    private final ReactiveMongoTemplate mongo;
    private final String                collection;

    public MongoAttributeStore(ReactiveMongoTemplate mongo, String collection) {
        this.mongo      = mongo;
        this.collection = collection;
    }

    @Override
    public void publish(AttributeKey key, Value value, String pdpId) {
        upsertToDB(key, value, null, pdpId);
    }

    @Override
    public void publish(AttributeKey key, Value value, Duration ttl, String pdpId) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        upsertToDB(key, value, Instant.now().plus(ttl), pdpId);
    }

    @Override
    public void remove(AttributeKey signature, String pdpId) {
        deleteFromDB(signature, pdpId);
    }

    @Override
    public Long count(String pdpId) {
        Objects.requireNonNull(pdpId, ERROR_PDP_ID_IS_EMPTY);

        var query = new Query(Criteria.where("pdpId").is(pdpId));
        query.addCriteria(new Criteria().orOperator(Criteria.where("expiresAt").isNull(),
                Criteria.where("expiresAt").gt(new Date())));
        return mongo.count(query, collection).block();
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        var query = doMongoQuery(key, pdpId);
        query.addCriteria(new Criteria().orOperator(Criteria.where("expiresAt").isNull(),
                Criteria.where("expiresAt").gt(new Date())));
        var document = mongo.findOne(query, Document.class, collection).block();
        if (document == null)
            return Value.UNDEFINED;
        var valueJson = document.getString("value");
        return valueJson != null ? ValueJsonMarshaller.json(valueJson) : Value.UNDEFINED;
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        Objects.requireNonNull(pdpId, ERROR_PDP_ID_IS_EMPTY);

        var query = new Query(Criteria.where("pdpId").is(pdpId));

        query.addCriteria(new Criteria().orOperator(Criteria.where("expiresAt").isNull(),
                Criteria.where("expiresAt").gt(new Date())));
        query.with(Sort.by(Sort.Direction.ASC, "name", "entity", "arguments"));

        if (offset != null) {
            query.skip(offset);
        }

        if (limit != null) {
            query.limit(limit);
        }

        return mongo.find(query, Document.class, collection).map(MongoAttributeStore::mapDocument).collectList()
                .block();
    }

    @Override
    public void close() {

    }

    private void deleteFromDB(@NonNull AttributeKey key, String pdpId) {
        mongo.remove(doMongoQuery(key, pdpId), collection).block();
    }

    private void upsertToDB(@NonNull AttributeKey key, Value value, @Nullable Instant expiresAt, String pdpId) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var valueJson  = ValueJsonMarshaller.toJsonString(value);

        var update = new Update().set("pdpId", pdpId).set("name", key.name()).set("entity", entityJson)
                .set("arguments", argsJson).set("value", valueJson)
                .set("expiresAt", expiresAt != null ? Date.from(expiresAt) : null);

        mongo.upsert(doMongoQuery(key, pdpId), update, collection).block();
    }

    private Query doMongoQuery(AttributeKey key, String pdpId) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var criteria   = Criteria.where("pdpId").is(pdpId).and("name").is(key.name()).and("entity").is(entityJson)
                .and("arguments").is(argsJson);

        return new Query(criteria);
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static AttributeEntry mapDocument(Document document) {
        String name         = document.getString("name");
        String entityRaw    = document.getString("entity");
        String argumentsRaw = document.getString("arguments");
        String valueRaw     = document.getString("value");

        Value       entity    = entityRaw != null ? ValueJsonMarshaller.json(entityRaw) : null;
        List<Value> arguments = argumentsRaw != null && ValueJsonMarshaller.json(argumentsRaw) instanceof ArrayValue a
                ? a
                : List.of();
        Value       value     = valueRaw != null ? ValueJsonMarshaller.json(valueRaw) : Value.UNDEFINED;

        return new AttributeEntry(new AttributeKey(entity, name, arguments), value);
    }
}
