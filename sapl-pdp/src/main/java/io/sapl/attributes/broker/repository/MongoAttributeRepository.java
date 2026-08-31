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
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import java.util.HashSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Repository implementation that persists attributes in MongoDB and maintains an In-Memory representation
 * by using the {@link InMemoryAttributeRepository} synchronized with MongoDB change streams.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a disconnect happens because the
 * change streams need a constant connection for the event stream. The current implementation performs a full
 * re-sync when the connection is established again.
 */
@Slf4j
public final class MongoAttributeRepository implements AttributeRepository {
    private static final String WARN_RECONNECTING          = "Lost notification stream connection for pdpId '{}', reconnecting: {}";
    private static final String ERROR_RECONNECT_GIVEN_UP   = "Giving up reconnecting to notification stream for pdpId '{}' after repeated failures";
    private static final String ERROR_BACKEND_DISCONNECTED = "The backend is currently disconnected for pdp with id %s.";
    private static final String ERROR_RESYNC_FAILED        = "Resync attempt failed for pdp with id '{}' : {}";

    private static final String FIELD_PDP_ID     = "pdpId";
    private static final String FIELD_NAME       = "name";
    private static final String FIELD_ENTITY     = "entity";
    private static final String FIELD_ARGUMENTS  = "arguments";
    private static final String FIELD_VALUE      = "value";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);

        void close();
    }

    private final ReactiveMongoTemplate mongo;
    private final String                collection;
    private final String                pdpId;

    // Used to mark cached attributes as unavailable while disconnected
    private final AtomicBoolean               disconnected             = new AtomicBoolean(false);
    private final AtomicReference<Disposable> changeStreamSubscription = new AtomicReference<>();
    private final AtomicReference<Disposable> resyncSubscription       = new AtomicReference<>();
    private volatile boolean                  closed                   = false;

    public MongoAttributeRepository(ReactiveMongoTemplate mongo, String pdpId, String collection) {
        this.mongo              = mongo;
        this.pdpId              = pdpId;
        this.collection         = collection;
        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
        subscribeToChangeStream();
    }

    @Override
    public void close() {
        closed = true;
        var subscription = changeStreamSubscription.get();
        if (subscription != null) {
            subscription.dispose();
        }
        var resync = resyncSubscription.get();
        if (resync != null) {
            resync.dispose();
        }
        internalRepository.close();
    }

    // The subscription to the change streams needs a MongoDB replica set activated.
    // Replica sets are also working with a single and are used together with change streams
    private void subscribeToChangeStream() {
        changeStreamSubscription.set(openChangeStream().filter(this::matchesPdpId)
                .publishOn(Schedulers.boundedElastic()).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30)).filter(throwable -> !closed).doBeforeRetry(signal -> {
                            log.warn(WARN_RECONNECTING, pdpId, signal.failure().getMessage());
                            if (disconnected.compareAndSet(false, true)) {
                                for (var key : internalRepository.knownKeys()) {
                                    internalRepository.publish(key,
                                            Value.error(ERROR_BACKEND_DISCONNECTED.formatted(pdpId)));
                                }
                                resyncWithRetry();
                            }
                        }))
                .subscribe(this::handleChangeStreamEvent, error -> log.error(ERROR_RECONNECT_GIVEN_UP, pdpId, error)));
    }

    private void resyncWithRetry() {
        resyncSubscription.set(Mono.fromRunnable(this::loadFromDB)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .scheduler(Schedulers.boundedElastic()).filter(throwable -> !closed))
                .doOnSuccess(v -> disconnected.set(false)).subscribeOn(Schedulers.boundedElastic())
                .subscribe(v -> {}, error -> log.error(ERROR_RESYNC_FAILED, pdpId, error.getMessage())));
    }

    // Opens the change stream when the first subscription is happening
    private Flux<ChangeStreamEvent<Document>> openChangeStream() {
        return Flux.defer(() -> mongo.changeStream(Document.class)
                .withOptions(options -> options.fullDocumentLookup(FullDocument.UPDATE_LOOKUP))
                .watchCollection(collection).listen());
    }

    private boolean matchesPdpId(ChangeStreamEvent<Document> event) {
        var body = event.getBody();
        return body != null && pdpId.equals(body.getString(FIELD_PDP_ID));
    }

    private void handleChangeStreamEvent(ChangeStreamEvent<Document> event) {
        var doc = Objects.requireNonNull(event.getBody());
        var key = keyFromDocument(doc);

        if (isDeleteEvent(event)) {
            internalRepository.remove(key);
            return;
        }

        publishFromDocument(key, doc);
    }

    private static boolean isDeleteEvent(ChangeStreamEvent<Document> event) {
        var opType = event.getOperationType();
        return opType != null && "delete".equals(opType.getValue());
    }

    private RepositoryKey keyFromDocument(Document doc) {
        var entityJson = doc.getString(FIELD_ENTITY);
        return new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null,
                doc.getString(FIELD_NAME), jsonToValues(doc.getString(FIELD_ARGUMENTS)), pdpId);
    }

    private void publishFromDocument(RepositoryKey key, Document doc) {
        var valueJson = doc.getString(FIELD_VALUE);
        if (valueJson == null)
            return;

        var value     = ValueJsonMarshaller.json(valueJson);
        var expiresAt = expiryFromDocument(doc);

        if (expiresAt == null) {
            internalRepository.publish(key, value);
            return;
        }

        var remaining = Duration.between(Instant.now(), expiresAt);
        if (!remaining.isNegative())
            internalRepository.publish(key, value, remaining);
    }

    private static Instant expiryFromDocument(Document doc) {
        var dateField = doc.getDate(FIELD_EXPIRES_AT);
        return dateField != null ? dateField.toInstant() : null;
    }

    // Loads all attributes from the backend again. Adds current keys and removes stale keys
    private void loadFromDB() {
        var query    = new Query(Criteria.where(FIELD_PDP_ID).is(pdpId));
        var seenKeys = new HashSet<RepositoryKey>();

        mongo.find(query, Document.class, collection).toStream().forEach(doc -> restoreDocument(doc, seenKeys));

        removeStaleKeys(seenKeys);
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

        var update = new Update().set(FIELD_PDP_ID, pdpId).set(FIELD_NAME, key.name()).set(FIELD_ENTITY, entityJson)
                .set(FIELD_ARGUMENTS, argsJson).set(FIELD_VALUE, valueJson).set(FIELD_EXPIRES_AT, expiresAt);

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
        var criteria   = Criteria.where(FIELD_PDP_ID).is(pdpId).and(FIELD_NAME).is(key.name()).and(FIELD_ENTITY)
                .is(entityJson).and(FIELD_ARGUMENTS).is(argsJson);

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

    private void restoreDocument(Document doc, Set<RepositoryKey> seenKeys) {
        var entityJson = doc.getString(FIELD_ENTITY);
        var argsJson   = doc.getString(FIELD_ARGUMENTS);
        var valueJson  = doc.getString(FIELD_VALUE);

        if (valueJson == null)
            return;

        var key       = new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null,
                doc.getString(FIELD_NAME), jsonToValues(argsJson), pdpId);
        var value     = ValueJsonMarshaller.json(valueJson);
        var expiresAt = expiryFromDocument(doc);

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

    private void removeStaleKeys(Set<RepositoryKey> seenKeys) {
        // The InMemoryAttributeRepository functions as cache and needs to be cleaned up after a re-sync
        for (var staleKey : internalRepository.knownKeys()) {
            if (!seenKeys.contains(staleKey)) {
                internalRepository.remove(staleKey);
            }
        }
    }
}
