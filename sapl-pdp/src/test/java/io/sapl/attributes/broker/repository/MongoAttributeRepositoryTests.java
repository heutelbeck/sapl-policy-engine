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
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("MongoAttributeRepository")
class MongoAttributeRepositoryTests {

    @Container
    static MongoDBContainer          mongo    = new MongoDBContainer("mongo:8.0");
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
    }

    private static RepositoryKey key(String name) {
        return new RepositoryKey(null, name, List.of(), "test-tenant");
    }

    private static AttributeFinderInvocation invocation(String fqn) {
        return new AttributeFinderInvocation("test-tenant", "test-tenant", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    @Nested
    @DisplayName("when a value is published")
    class WhenValueIsPublished {

        @Test
        @DisplayName("then observe returns it immediately")
        void thenGetReturnsIt() {
            repository.publish(key("sapl.test.attr"), Value.of("test"));
            repository.observe(invocation("sapl.test.attr"), received::add);

            assertThat(received.getFirst()).isEqualTo(Value.of("test"));
        }

        @Test
        @DisplayName("value survives a repository restart (loadFromDB)")
        void thenItSurvivesRestart() {
            repository.publish(key("sapl.test.persist"), Value.of(42L));

            val client2   = MongoClients.create(mongo.getConnectionString());
            val template2 = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client2, "sapl"));
            try (val repo2 = new MongoAttributeRepository(template2, "test-tenant", "attributes")) {
                repo2.observe(invocation("sapl.test.persist"), received::add);
                assertThat(received.getFirst()).isEqualTo(Value.of(42L));
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

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.getLast().equals(Value.UNDEFINED));
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
            assertThat(received.getFirst()).isEqualTo(Value.of("1"));

            repository.publish(key("sapl.test.overwrite"), Value.of("2"), Duration.ofSeconds(120));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.getLast().equals(Value.of("2")));
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

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.getLast().equals(Value.UNDEFINED));
        }
    }
}
