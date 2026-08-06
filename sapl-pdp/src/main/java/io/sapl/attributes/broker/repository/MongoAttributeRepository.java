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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import com.mongodb.client.model.changestream.FullDocument;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j

public class MongoAttributeRepository implements AttributeRepository {
    private static final String ERROR_HANDLE_NOTIFICATION = "Error while handling attribute_changes notification for pdpId '{}'";

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);
    }

    private final ReactiveMongoTemplate mongo;
    private final String                collection;
    private final String                pdpId;

    public MongoAttributeRepository(ReactiveMongoTemplate mongo, String pdpId, String collection) {
        this.mongo              = mongo;
        this.pdpId              = pdpId;
        this.collection         = collection;
        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
        subscribeToChangeStream();
    }

    // Requires MongoDB replica set (even a single-node rs works: --replSet rs0)
    private void subscribeToChangeStream() {
        mongo.changeStream(Document.class)
                .withOptions(options -> options.fullDocumentLookup(FullDocument.UPDATE_LOOKUP))
                .watchCollection(collection).listen()
                .filter(event -> event.getBody() != null && pdpId.equals(event.getBody().getString("pdpId")))
                .publishOn(Schedulers.boundedElastic()).subscribe(event -> {
                    var doc = event.getBody();
                    var opType = event.getOperationType();
                    var entityJson = Objects.requireNonNull(doc).getString("entity");
                    var key = new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null,
                            doc.getString("name"), jsonToValues(doc.getString("arguments")), pdpId);

                    if (opType != null && "delete".equals(opType.getValue())) {
                        internalRepository.remove(key);
                        return;
                    }

                    var valueJson = doc.getString("value");

                    if (valueJson == null)
                        return;

                    var value = ValueJsonMarshaller.json(valueJson);
                    var dateField = doc.getDate("expiresAt");
                    var expiresAt = dateField != null ? dateField.toInstant() : null;

                    if (expiresAt != null) {
                        var remaining = Duration.between(Instant.now(), expiresAt);
                        if (!remaining.isNegative())
                            internalRepository.publish(key, value, remaining);
                    } else {
                        internalRepository.publish(key, value);
                    }
                }, error -> log.error(ERROR_HANDLE_NOTIFICATION, pdpId, error));
    }

    public void loadFromDB() {
        var query = new Query(Criteria.where("pdpId").is(pdpId));
        mongo.find(query, Document.class, collection).toStream().forEach(doc -> {
            var entityJson = doc.getString("entity");
            var argsJson   = doc.getString("arguments");
            var valueJson  = doc.getString("value");

            if (valueJson == null)
                return;

            var key       = new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null,
                    doc.getString("name"), jsonToValues(argsJson), pdpId);
            var value     = ValueJsonMarshaller.json(valueJson);
            var dateField = doc.getDate("expiresAt");
            var expiresAt = dateField != null ? dateField.toInstant() : null;

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
        });
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        mongo.remove(doMongoQuery(key), collection).block();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        internalRepository.publish(key, value);
        upsertToDB(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        internalRepository.publish(key, value, ttl);
        upsertToDB(key, value, Instant.now().plus(ttl));
    }

    private void upsertToDB(@NonNull RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var valueJson  = ValueJsonMarshaller.toJsonString(value);

        var update = new Update().set("pdpId", pdpId).set("name", key.name()).set("entity", entityJson)
                .set("arguments", argsJson).set("value", valueJson)
                .set("expiresAt", expiresAt != null ? Date.from(expiresAt) : null);

        mongo.upsert(doMongoQuery(key), update, collection).block();
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        internalRepository.remove(key);
        deleteFromDB(key);
    }

    private Query doMongoQuery(RepositoryKey key) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJson(key.arguments());
        var criteria   = Criteria.where("pdpId").is(pdpId).and("name").is(key.name()).and("entity").is(entityJson)
                .and("arguments").is(argsJson);

        return new Query(criteria);
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static List<Value> jsonToValues(String json) {
        if (json == null || json.isBlank())
            return List.of();

        return (ArrayValue) ValueJsonMarshaller.json(json);
    }
}
