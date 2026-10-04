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

import com.mongodb.reactivestreams.client.MongoClients;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@DisplayName("MongoAttributeRepository")
class MongoAttributeRepositoryTests {

    @Container
    static MongoDBContainer          mongo    = new MongoDBContainer("mongo:8.0").withReplicaSet();
    private MongoAttributeRepository repository;
    private final List<Value>        received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        val client   = MongoClients.create(mongo.getConnectionString());
        val template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "sapl"));
        repository = new MongoAttributeRepository(template, "test-tenant", "attributes");
        received.clear();
    }

    @AfterEach
    void tearDown() {
        repository.close();
        newTemplate().remove(new Query(), "attributes").block();
    }

    private static RepositoryKey key(String name) {
        return new RepositoryKey(null, name, List.of(), "test-tenant");
    }

    private static AttributeFinderInvocation invocation(String fqn) {
        return new AttributeFinderInvocation("test-tenant", "test-tenant", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static AttributeFinderInvocation invocation(String pdpId, Value entity, String name, List<Value> args) {
        return new AttributeFinderInvocation(pdpId, pdpId, name, entity, args, Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private Value firstReceived() {
        return received.getFirst();
    }

    private Value lastReceived() {
        return received.getLast();
    }

    private MongoAttributeRepository newRepository(String pdpId, String collection) {
        return new MongoAttributeRepository(newTemplate(), pdpId, collection);
    }

    private boolean preAndPostImagesEnabled(String collection) {
        val result = Objects.requireNonNull(newTemplate()
                .executeCommand(new Document("listCollections", 1).append("filter", new Document("name", collection)))
                .block());
        val first  = result.get("cursor", Document.class).getList("firstBatch", Document.class).getFirst();
        val images = first.get("options", Document.class).get("changeStreamPreAndPostImages", Document.class);
        return images != null && images.getBoolean("enabled", false);
    }

    private ReactiveMongoTemplate newTemplate() {
        return new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(MongoClients.create(mongo.getConnectionString()), "sapl"));
    }

    @Nested
    @DisplayName("when a value is published")
    class WhenValueIsPublished {

        @Test
        @DisplayName("then observe returns it immediately")
        void thenGetReturnsIt() {
            repository.publish(key("sapl.test.attr"), Value.of("test"));
            repository.observe(invocation("sapl.test.attr"), received::add);

            assertThat(firstReceived()).isEqualTo(Value.of("test"));
        }

        @Test
        @DisplayName("value survives a repository restart (loadFromDB)")
        void thenItSurvivesRestart() {
            repository.publish(key("sapl.test.persist"), Value.of(42L));

            val client2   = MongoClients.create(mongo.getConnectionString());
            val template2 = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client2, "sapl"));
            try (val repo2 = new MongoAttributeRepository(template2, "test-tenant", "attributes")) {
                repo2.observe(invocation("sapl.test.persist"), received::add);
                assertThat(firstReceived()).isEqualTo(Value.of(42L));
            }
        }
    }

    @Nested
    @DisplayName("when a value is removed")
    class WhenValueIsRemoved {

        @Test
        @DisplayName("then observer receives UNDEFINED")
        void thenGetReturnsUndefined() {
            repository.publish(key("sapl.test.remove"), Value.of("deleteIt"));
            repository.observe(invocation("sapl.test.remove"), received::add);
            repository.remove(key("sapl.test.remove"));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }

        @Test
        @DisplayName("then another node also observes UNDEFINED")
        void thenAnotherNodeObserverIsUndefined() {
            repository.publish(key("sapl.test.remove.two.nodes"), Value.of("deleteIt"));

            val client2   = MongoClients.create(mongo.getConnectionString());
            var template2 = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client2, "sapl"));

            try (val repo2 = new MongoAttributeRepository(template2, "test-tenant", "attributes")) {
                repo2.observe(invocation("sapl.test.remove.two.nodes"), received::add);
                assertThat(firstReceived()).isEqualTo(Value.of("deleteIt"));

                // Delete the attribute and wait a short period
                repository.remove(key("sapl.test.remove.two.nodes"));
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
            }
        }
    }

    @Nested
    @DisplayName("when an existing value is overwritten")
    class WhenAttributeIsOverwritten {

        @Test
        @DisplayName("observer receives the new value")
        void thenObserverReceivesNewValue() {
            repository.publish(key("sapl.test.overwrite"), Value.of("1"), Duration.ofSeconds(120));
            repository.observe(invocation("sapl.test.overwrite"), received::add);
            assertThat(firstReceived()).isEqualTo(Value.of("1"));

            repository.publish(key("sapl.test.overwrite"), Value.of("2"), Duration.ofSeconds(120));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.of("2")));
        }
    }

    @Nested
    @DisplayName("when ttl expires")
    class WhenTtlExpires {

        @Test
        @DisplayName("observer receives UNDEFINED after expiry")
        void thenObserverReceivesUndefinedAfterExpiry() {
            repository.observe(invocation("sapl.test.ttl"), received::add);
            repository.publish(key("sapl.test.ttl"), Value.of("temp"), Duration.ofSeconds(1));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }
    }

    @Nested
    @DisplayName("when the collection is prepared on startup")
    class WhenCollectionIsPrepared {
        private static final String PDP_ID      = "test-tenant";
        private static final String COLLECTION1 = "collection1";
        private static final String COLLECTION2 = "collection2";

        @Test
        @DisplayName("then a missing collection on startup is created with the pre- and post-images activated")
        void thenMissingCollectionIsCreatedWithPreAndPostImages() {
            try (val repo = newRepository(PDP_ID, COLLECTION1)) {
                assertThat(preAndPostImagesEnabled(COLLECTION1)).isTrue();
            }
        }

        @Test
        @DisplayName("then an existing collection will be extended with pre- and post-images")
        void thenExistingCollectionIsUpgraded() {
            newTemplate().createCollection(COLLECTION2).block();
            try (val repo = newRepository(PDP_ID, COLLECTION2)) {
                assertThat(preAndPostImagesEnabled(COLLECTION2)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("when another pdp id uses the same collection")
    class WhenAnotherPdpIDUsesTheSameCollection {
        private static final String COLLECTION = "attributes";
        private static final String PDP_ID1    = "test-tenant";
        private static final String PDP_ID2    = "tenant2";
        private static final String NAME1      = "sapl.test.attribute1";
        private static final String NAME2      = "sapl.test.attribute2";
        private final RepositoryKey key1       = new RepositoryKey(null, NAME1, List.of(), PDP_ID1);
        private final RepositoryKey key2       = new RepositoryKey(null, NAME1, List.of(), PDP_ID2);
        private final RepositoryKey key3       = new RepositoryKey(null, NAME2, List.of(), PDP_ID1);
        private final Value         value1     = Value.of("foreign");
        private final Value         value2     = Value.of("own");

        @Test
        @DisplayName("then change events of another pdp id are ignored")
        void thenChangeEventsOfOtherPdpIdAreIgnored() {
            try (val repo2 = newRepository(PDP_ID2, COLLECTION); val repo3 = newRepository(PDP_ID2, COLLECTION)) {
                repo2.observe(invocation(PDP_ID2, null, NAME1, List.of()), received::add);
                repository.publish(key1, value1);
                repo3.publish(key2, value2);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value2));
                assertThat(received).doesNotContain(value1);
            }
        }

        @Test
        @DisplayName("then a restart only loads values of it's own pdp id")
        void thenRestartOnlyLoadsOwnValues() {
            repository.publish(key3, value1);
            try (val repo2 = newRepository(PDP_ID2, COLLECTION)) {
                repo2.observe(invocation(PDP_ID2, null, NAME2, List.of()), received::add);
                assertThat(firstReceived()).isEqualTo(Value.UNDEFINED);
            }
        }
    }

    @Nested
    @DisplayName("When a repository key has an entity and arguments")
    class WhenKeyHasEntityAndArguments {
        private final Value         entity = Value.of("alice");
        private final List<Value>   args   = List.of(Value.of(1), Value.of("test"));
        private static final String NAME   = "sapl.test.attribute";
        private static final String PDP_ID = "test-tenant";
        private static final String TABLE  = "attributes";

        private final RepositoryKey key   = new RepositoryKey(entity, NAME, args, PDP_ID);
        private final Value         value = Value.of("test");

        @Test
        @DisplayName("then another node restores the value with entity and arguments")
        void thenAnotherNodeReadsValueWithEntityAndArguments() {
            repository.publish(key, value);
            try (val repo2 = newRepository(PDP_ID, TABLE)) {
                repo2.observe(invocation(PDP_ID, entity, NAME, args), received::add);
                assertThat(firstReceived()).isEqualTo(value);
            }
        }

        @Test
        @DisplayName("then keys with different arguments are distinct")
        void thenKeysWithDifferentArgumentsAreDistinct() {
            repository.publish(key, value);
            repository.observe(invocation(PDP_ID, entity, NAME, List.of(Value.of("other"))), received::add);
            assertThat(firstReceived()).isEqualTo(Value.UNDEFINED);
        }

        @Test
        @DisplayName("then another node received updates for the same key")
        void thenAnotherNodeReceivesUpdatesForTheSameKey() {
            try (val repo2 = newRepository(PDP_ID, TABLE)) {
                repo2.observe(invocation(PDP_ID, entity, NAME, args), received::add);
                repository.publish(key, value);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value));
            }
        }
    }

    @Nested
    @DisplayName("when restoring the keys from a database")
    class WhenRestoringKeysFromADatabase {
        private static final String PDP_ID     = "test-tenant";
        private static final String COLLECTION = "attributes";
        private static final String ATTRIBUTE1 = "sapl.test.attribute1";
        private static final String ATTRIBUTE2 = "sapl.test.attribute2";
        private final RepositoryKey key1       = new RepositoryKey(null, ATTRIBUTE1, List.of(), PDP_ID);
        private final Value         value      = Value.of("test");

        @Test
        @DisplayName("then the remaining TTL is applied after a restart")
        void thenRemainingTTLIsAppliedToKey() {
            repository.publish(key1, value, Duration.ofSeconds(3));
            try (val repo2 = newRepository(PDP_ID, COLLECTION)) {
                repo2.observe(invocation(PDP_ID, null, ATTRIBUTE1, List.of()), received::add);
                assertThat(firstReceived()).isEqualTo(value);
                Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> lastReceived().equals(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("then expired rows are deleted from the table while reloading")
        void thenExpiredRowsAreDeletedWhileReloading() {
            newTemplate().insert(new Document("pdpId", PDP_ID).append("name", ATTRIBUTE2).append("entity", null)
                    .append("arguments", "[]").append("value", "\"old\"")
                    .append("expiresAt", Date.from(Instant.now().minusSeconds(60))), COLLECTION).block();

            try (val repo2 = newRepository(PDP_ID, COLLECTION)) {
                Long count = newTemplate()
                        .count(new Query(Criteria.where("pdpId").is(PDP_ID).and("name").is(ATTRIBUTE2)), COLLECTION)
                        .block();
                assertThat(count).isZero();
            }
        }
    }

    @Nested
    @DisplayName("when a value with ttl is published on another node")
    class WhenTtlIsPublishedOnAnotherNode {
        private static final String PDP_ID     = "test-tenant";
        private static final String COLLECTION = "attributes";
        private static final String NAME       = "sapl.test.mongo.remote.ttl";
        private final RepositoryKey key        = new RepositoryKey(null, NAME, List.of(), PDP_ID);
        private final Value         value      = Value.of("temp");

        @Test
        @DisplayName("then the other node applies the ttl from the change event")
        void thenOtherNodeAppliesTtlFromChangeEvent() {
            try (val repo2 = newRepository(PDP_ID, COLLECTION)) {
                repo2.observe(invocation(PDP_ID, null, NAME, List.of()), received::add);
                repository.publish(key, value, Duration.ofSeconds(2));
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.contains(value));
                Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> lastReceived().equals(Value.UNDEFINED));
            }
        }
    }
}
