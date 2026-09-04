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
import lombok.extern.slf4j.Slf4j;

import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of the attribute store for a MongoDB backend.
 */
@Slf4j
public class MongoAttributeStore implements AttributeStore {
    private static final String ERROR_TTL_NOT_POSITIVE = "TTL must be a strictly positive Duration.";
    private static final String WARN_PRE_POST_IMAGES   = "Could not activate changeStreamPreAndPostImages for collection '{}' : {}. Activate it manually.";

    private static final String PDP_ID_FIELD     = "pdpId";
    private static final String NAME_FIELD       = "name";
    private static final String ENTITY_FIELD     = "entity";
    private static final String ARGUMENTS_FIELD  = "arguments";
    private static final String VALUE_FIELD      = "value";
    private static final String EXPIRES_AT_FIELD = "expiresAt";
    private static final String ID_FIELD         = "_id";

    private final ReactiveMongoTemplate mongo;
    private final String                collection;

    public MongoAttributeStore(ReactiveMongoTemplate mongo, String collection) {
        this.mongo      = mongo;
        this.collection = collection;
        configureChangeStreamPrePostImages();
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
    public boolean remove(AttributeKey key, String pdpId) {
        return deleteFromDB(key, pdpId);
    }

    @Override
    public Long count(String pdpId) {
        var query = new Query(Criteria.where(PDP_ID_FIELD).is(pdpId));
        query.addCriteria(new Criteria().orOperator(Criteria.where(EXPIRES_AT_FIELD).isNull(),
                Criteria.where(EXPIRES_AT_FIELD).gt(Instant.now())));

        return mongo.count(query, collection).block();
    }

    @Override
    public Value get(AttributeKey key, String pdpId) {
        var query = doMongoQuery(key, pdpId);
        query.addCriteria(new Criteria().orOperator(Criteria.where(EXPIRES_AT_FIELD).isNull(),
                Criteria.where(EXPIRES_AT_FIELD).gt(Instant.now())));

        var document = mongo.findOne(query, Document.class, collection).block();

        if (document == null)
            return Value.UNDEFINED;

        var valueJson = document.getString(VALUE_FIELD);

        return valueJson != null ? ValueJsonMarshaller.json(valueJson) : Value.UNDEFINED;
    }

    @Override
    public List<AttributeEntry> getAll(String pdpId, @Nullable Integer limit, @Nullable Integer offset) {
        var query = new Query(Criteria.where(PDP_ID_FIELD).is(pdpId));

        query.addCriteria(new Criteria().orOperator(Criteria.where(EXPIRES_AT_FIELD).isNull(),
                Criteria.where(EXPIRES_AT_FIELD).gt(Instant.now())));
        query.with(Sort.by(Sort.Direction.ASC, ID_FIELD));

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
        // Noop: The used Mongo implementation is a Spring-managed bean shared across the application
        // The class is not responsible for it's lifecycle
    }

    private boolean deleteFromDB(@NonNull AttributeKey key, String pdpId) {
        DeleteResult result = mongo.remove(doMongoQuery(key, pdpId), collection).block();
        return result != null && result.getDeletedCount() > 0;
    }

    private boolean upsertToDB(@NonNull AttributeKey key, Value value, @Nullable Instant expiresAt, String pdpId) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var valueJson  = ValueJsonMarshaller.toJsonString(value);

        var update = new Update().set(PDP_ID_FIELD, pdpId).set(NAME_FIELD, key.name()).set(ENTITY_FIELD, entityJson)
                .set(ARGUMENTS_FIELD, argsJson).set(VALUE_FIELD, valueJson).set(EXPIRES_AT_FIELD, expiresAt);

        UpdateResult result = mongo.upsert(doMongoQuery(key, pdpId), update, collection).block();
        return result != null && result.getUpsertedId() != null;
    }

    private Query doMongoQuery(AttributeKey key, String pdpId) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var criteria   = Criteria.where(PDP_ID_FIELD).is(pdpId).and(NAME_FIELD).is(key.name()).and(ENTITY_FIELD)
                .is(entityJson).and(ARGUMENTS_FIELD).is(argsJson);

        return new Query(criteria);
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static AttributeEntry mapDocument(Document document) {
        String name         = document.getString(NAME_FIELD);
        String entityRaw    = document.getString(ENTITY_FIELD);
        String argumentsRaw = document.getString(ARGUMENTS_FIELD);
        String valueRaw     = document.getString(VALUE_FIELD);

        Value       entity    = entityRaw != null ? ValueJsonMarshaller.json(entityRaw) : null;
        List<Value> arguments = argumentsRaw != null && ValueJsonMarshaller.json(argumentsRaw) instanceof ArrayValue a
                ? a
                : List.of();
        Value       value     = ValueJsonMarshaller.json(Objects.requireNonNull(valueRaw));

        return new AttributeEntry(new AttributeKey(entity, Objects.requireNonNull(name), arguments), value);
    }

    // Check if the Mongo collection exists with the right configuration
    private void configureChangeStreamPrePostImages() {
        try {
            var exists = Boolean.TRUE.equals(mongo.collectionExists(collection).block());

            if (exists) {
                // The db command collMod adds the changeStreamPreAndPostImages to the collection.
                // In other words: create a snapshot before and after an change stream event
                mongo.executeCommand(new Document("collMod", collection).append("changeStreamPreAndPostImages",
                        new Document("enabled", true))).block();
            } else {
                // If the collection does not exist: create it.
                mongo.createCollection(collection,
                        CollectionOptions.empty()
                                .changeStream(CollectionOptions.CollectionChangeStreamOptions.preAndPostImages(true)))
                        .block();
            }
        } catch (DataAccessException e) {
            log.warn(WARN_PRE_POST_IMAGES, collection, e.getMessage());
        }
    }
}
