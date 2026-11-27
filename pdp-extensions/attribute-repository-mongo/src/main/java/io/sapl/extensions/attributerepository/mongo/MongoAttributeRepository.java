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
package io.sapl.extensions.attributerepository.mongo;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.broker.repository.RepositoryState;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.model.changestream.FullDocument;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ChangeStreamEvent;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.dao.DataAccessException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import com.mongodb.client.model.changestream.FullDocumentBeforeChange;
import com.mongodb.reactivestreams.client.ClientSession;

import org.springframework.data.mongodb.core.CollectionOptions;
import reactor.util.retry.Retry.RetrySignal;

/**
 * Repository implementation that persists attributes in MongoDB and maintains
 * an In-Memory representation by using the {@link InMemoryAttributeRepository}
 * synchronized with MongoDB change streams.
 *
 * The implementation sets all attribute to the state INDETERMINATE when a
 * disconnect happens because the change streams need a constant connection for
 * the event stream. The current implementation performs a full re-sync when the
 * connection is established again.
 */
@Slf4j
public final class MongoAttributeRepository implements AttributeRepository {
    private static final String WARN_RECONNECTING          = "Lost notification stream connection for pdpId '{}', reconnecting: {}";
    private static final String WARN_PRE_POST_IMAGES       = "Could not activate changeStreamPreAndPostImages for pdp id '{}' : {}. Activate it manually.";
    private static final String ERROR_RECONNECT_GIVEN_UP   = "Giving up reconnecting to notification stream for pdpId '{}' after repeated failures";
    private static final String ERROR_BACKEND_DISCONNECTED = "The backend is currently disconnected for pdp with id %s.";
    private static final String ERROR_RESYNC_FAILED        = "Resync attempt failed for pdp with id '{}' : {}";
    private static final String ERROR_HANDLE_CHANGE_EVENT  = "Error while handling a change stream event for pdpId '{}'";
    private static final String ERROR_FOREIGN_PDP_ID       = "Assignment for pdpId '%s' rejected by repository for pdpId '%s'.";

    private static final String FIELD_PDP_ID     = "pdpId";
    private static final String FIELD_NAME       = "name";
    private static final String FIELD_ENTITY     = "entity";
    private static final String FIELD_ARGUMENTS  = "arguments";
    private static final String FIELD_VALUE      = "value";
    private static final String FIELD_EXPIRES_AT = "expiresAt";

    private static final Duration LOAD_TIMEOUT       = Duration.ofMinutes(2);
    private static final Duration LOAD_IDLE_TIMEOUT  = Duration.ofSeconds(5);
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(1);

    private final InMemoryAttributeRepository internalRepository;
    private final ReactiveMongoTemplate       mongo;
    private final TransactionalOperator       transactions;
    private final ReentrantLock               reloadLock = new ReentrantLock();
    private final String                      collection;
    private final String                      pdpId;
    private Map<RepositoryKey, ViewEntry>     lastView   = Map.of();
    private final Set<RepositoryKey>          knownKeys  = ConcurrentHashMap.newKeySet();

    // Used to mark cached attributes as unavailable while disconnected
    private final AtomicBoolean               disconnected             = new AtomicBoolean(false);
    private final AtomicReference<Disposable> reloadRetry              = new AtomicReference<>();
    private final AtomicReference<Disposable> changeStreamSubscription = new AtomicReference<>();
    private final AtomicReference<Disposable> resyncSubscription       = new AtomicReference<>();
    private volatile boolean                  closed                   = false;

    private record ViewEntry(Value value, @Nullable Instant expiresAt) {}

    /**
     * Creates the object for the Mongo attribute repository to communicate with the
     * database. Connects to the change stream, creates the internal cache and loads
     * all attributes.
     *
     * @param mongo The Mongo template to communicate.
     * @param pdpId The pdp id for this attribute repository.
     * @param collection The collection to find the attribute repository.
     */
    public MongoAttributeRepository(ReactiveMongoTemplate mongo, String pdpId, String collection) {
        this.mongo              = mongo;
        this.transactions       = TransactionalOperator
                .create(new ReactiveMongoTransactionManager(mongo.getMongoDatabaseFactory()));
        this.pdpId              = pdpId;
        this.collection         = collection;
        this.internalRepository = new InMemoryAttributeRepository();
        configureChangeStreamPrePostImages();
        createIndexes();
        subscribeToChangeStream();
        loadFromDB();
    }

    @Override
    public void close() {
        closed = true;
        changeStreamSubscription.get().dispose();

        var resync = resyncSubscription.get();
        if (resync != null) {
            resync.dispose();
        }

        var retry = reloadRetry.get();
        if (retry != null) {
            retry.dispose();
        }
        internalRepository.close();
    }

    // The subscription to the change streams needs a MongoDB replica set activated.
    // Replica sets are also working with a single node and are used together with
    // change streams.
    private void subscribeToChangeStream() {
        changeStreamSubscription.set(openChangeStream(collection).filter(this::isForPdpId).onBackpressureLatest()
                .publishOn(Schedulers.boundedElastic(), 1)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .transientErrors(true).filter(throwable -> !closed)
                        .doBeforeRetry(this::handleChangeStreamRetry))
                .subscribe(this::processChangeStreamEvent, error -> log.error(ERROR_RECONNECT_GIVEN_UP, pdpId, error)));
    }

    // Handle retry logic on a disconnect. Invalidates and resyncs cache again.
    private void handleChangeStreamRetry(RetrySignal signal) {
        log.warn(WARN_RECONNECTING, pdpId, signal.failure().getMessage());
        if (disconnected.compareAndSet(false, true)) {
            markAsDisconnected();
            resyncFromMongoWithRetry();
        }
    }

    // Reconnects with retry-backoff logic. Loads all attributes from the DB
    private void resyncFromMongoWithRetry() {
        resyncSubscription.set(Mono.fromRunnable(this::reloadAfterDisconnect)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .scheduler(Schedulers.boundedElastic()).filter(throwable -> !closed))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(v -> {}, error -> log.error(ERROR_RESYNC_FAILED, pdpId, error.getMessage())));
    }

    // Disconnect handling to set the right state
    private void reloadAfterDisconnect() {
        disconnected.set(false);
        try {
            loadFromDB();
        } catch (RuntimeException e) {
            disconnected.set(true);
            throw e;
        }
    }

    // Open change stream when first subscription. Activates pre-images for deletes
    private Flux<ChangeStreamEvent<Document>> openChangeStream(String watchedCollection) {
        return Flux.defer(() -> mongo.changeStream(Document.class)
                .withOptions(options -> options.fullDocumentLookup(FullDocument.UPDATE_LOOKUP)
                        .fullDocumentBeforeChangeLookup(FullDocumentBeforeChange.WHEN_AVAILABLE))
                .watchCollection(watchedCollection).listen());
    }

    // Filters events, so that events are only handled when the pdp id matches with
    // the config of this object
    private boolean isForPdpId(ChangeStreamEvent<Document> event) {
        var body = isDeleteEvent(event) ? event.getBodyBeforeChange() : event.getBody();
        return body != null && pdpId.equals(body.getString(FIELD_PDP_ID));
    }

    // Handles change stream event for publish/delete events from a transaction
    private void processChangeStreamEvent(ChangeStreamEvent<Document> event) {
        try {
            loadFromDB();
        } catch (RuntimeException e) {
            log.error(ERROR_HANDLE_CHANGE_EVENT, pdpId, e);
            scheduleReloadRetry();
        }
    }

    // Check if the event is a delete event to distinguish the behaviour in handling
    private static boolean isDeleteEvent(ChangeStreamEvent<Document> event) {
        var opType = event.getOperationType();
        return opType != null && "delete".equals(opType.getValue());
    }

    // Converter to convert a Mongo document into an repository key object
    private RepositoryKey toKey(Document doc) {
        var entityJson = doc.getString(FIELD_ENTITY);
        return new RepositoryKey(entityJson != null ? ValueJsonMarshaller.json(entityJson) : null,
                doc.getString(FIELD_NAME), jsonArrayToValues(doc.getString(FIELD_ARGUMENTS)), pdpId);
    }

    private ViewEntry toViewEntry(Document doc) {
        var value     = doc.getString(FIELD_VALUE);
        var expiresAt = doc.getDate(FIELD_EXPIRES_AT);
        return new ViewEntry(ValueJsonMarshaller.json(value), expiresAt != null ? expiresAt.toInstant() : null);
    }

    // Loads all attributes again. Adds current and removes stale keys
    private void loadFromDB() {
        reloadLock.lock();

        try {
            var newView = new HashMap<RepositoryKey, ViewEntry>();
            var now     = Instant.now();
            var query   = new Query(new Criteria().andOperator(Criteria.where(FIELD_PDP_ID).is(pdpId), new Criteria()
                    .orOperator(Criteria.where(FIELD_EXPIRES_AT).is(null), Criteria.where(FIELD_EXPIRES_AT).gt(now))));

            // Snapshot session. Get all documents at once from a certain snapshot
            var docs = Objects.requireNonNull(mongo.withSession(ClientSessionOptions.builder().snapshot(true).build())
                    .execute(ops -> ops.find(query, Document.class, collection), ClientSession::close)
                    .timeout(LOAD_IDLE_TIMEOUT).collectList().block());

            // View of the current state in DB
            for (var doc : docs) {
                newView.put(toKey(doc), toViewEntry(doc));
            }

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
            knownKeys.addAll(newView.keySet());
        } finally {
            reloadLock.unlock();
        }
    }

    // Builder for the correct query
    private Query buildMongoQuery(RepositoryKey key) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argsJson   = valuesToJsonArray(key.arguments());
        var criteria   = Criteria.where(FIELD_PDP_ID).is(pdpId).and(FIELD_NAME).is(key.name()).and(FIELD_ENTITY)
                .is(entityJson).and(FIELD_ARGUMENTS).is(argsJson);

        return new Query(criteria);
    }

    // Check if collection exists with the right configuration
    private void configureChangeStreamPrePostImages() {
        try {
            var exists = Boolean.TRUE.equals(mongo.collectionExists(collection).block());

            if (exists) {
                // collMod adds the changeStreamPreAndPostImages
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
            log.warn(WARN_PRE_POST_IMAGES, pdpId, e.getMessage());
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
        case AttributeAssignment.BoundValue(var key, var value, var ttl) -> mongo.upsert(buildMongoQuery(key),
                updateFor(key, value, ttl != null ? Instant.now().plus(ttl) : null), collection).then();
        case AttributeAssignment.UnboundValue(var key)                   ->
            mongo.remove(buildMongoQuery(key), collection).then();
        });

        writes.then().as(transactions::transactional).timeout(LOAD_TIMEOUT).block();

        // update your own view
        loadFromDB();
    }

    // Sets the update statement properly
    private Update updateFor(RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        return new Update().set(FIELD_PDP_ID, pdpId).set(FIELD_NAME, key.name()).set(FIELD_ENTITY, entityJson)
                .set(FIELD_ARGUMENTS, valuesToJsonArray(key.arguments()))
                .set(FIELD_VALUE, ValueJsonMarshaller.toJsonString(value)).set(FIELD_EXPIRES_AT, expiresAt);
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

    // Create the necessary Mongo indexes if missing (e.g. on startup)
    private void createIndexes() {
        mongo.indexOps(collection)
                .createIndex(new Index().on(FIELD_PDP_ID, Sort.Direction.ASC).on(FIELD_NAME, Sort.Direction.ASC)
                        .on(FIELD_ENTITY, Sort.Direction.ASC).on(FIELD_ARGUMENTS, Sort.Direction.ASC).unique())
                .block();
        mongo.indexOps(collection)
                .createIndex(new Index().on(FIELD_EXPIRES_AT, Sort.Direction.ASC).expire(Duration.ZERO)).block();
    }

    private void scheduleReloadRetry() {
        if (closed || disconnected.get()) {
            return;
        }

        var retry = Mono.delay(RELOAD_RETRY_DELAY, Schedulers.boundedElastic()).subscribe(tick -> {
            try {
                loadFromDB();
            } catch (RuntimeException e) {
                log.error(ERROR_HANDLE_CHANGE_EVENT, pdpId, e);
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

    private static String valuesToJsonArray(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

    private static List<Value> jsonArrayToValues(String json) {
        return (ArrayValue) ValueJsonMarshaller.json(json);
    }
}
