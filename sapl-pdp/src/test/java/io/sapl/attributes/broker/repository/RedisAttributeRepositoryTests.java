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

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@DisplayName("RedisAttributeRepository")
class RedisAttributeRepositoryTests {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:8"));

    @BeforeAll
    static void enableKeyspaceNotifications() {
        val setupClient = RedisClient.create(redis.getRedisURI());
        setupClient.connect().sync().configSet("notify-keyspace-events", "Ex");
        setupClient.shutdown();
    }

    private RedisClient              client;
    private RedisAttributeRepository repository;
    private final List<Value>        received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        client     = RedisClient.create(redis.getRedisURI());
        repository = new RedisAttributeRepository(client, "test-tenant", 0);
        received.clear();
    }

    @AfterEach
    void tearDown() {
        client.connect().sync().flushall();
        repository.close();
    }

    private static RepositoryKey key(String name) {
        return new RepositoryKey(null, name, List.of(), "test-tenant");
    }

    private static AttributeFinderInvocation invocation(String name) {
        return new AttributeFinderInvocation("test-tenant", "test-tenant", name, List.of(), Duration.ofSeconds(1),
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

    private RedisAttributeRepository newRepository(String pdpId) {
        return new RedisAttributeRepository(RedisClient.create(redis.getRedisURI()), pdpId, 0);
    }

    private static String redisKey(String pdpId, String name) {
        return "sapl:attribute:" + pdpId + ":null:" + name + ":[]";
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
        @DisplayName("value survives a restart")
        void thenItSurvivesRestart() {
            repository.publish(key("sapl.test.persist"), Value.of(42L));

            val client2 = RedisClient.create(redis.getRedisURI());
            try (val repo2 = new RedisAttributeRepository(client2, "test-tenant", 0)) {
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
    }

    @Nested
    @DisplayName("overwrite existing attribute")
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
        @DisplayName("after TTL expires, a fresh observe returns UNDEFINED")
        void thenObserverReceivesUndefinedAfterExpiry() {
            repository.publish(key("sapl.test.ttl"), Value.of("temp"), Duration.ofSeconds(1));
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                received.clear();
                repository.observe(invocation("sapl.test.ttl"), received::add);
                assertThat(firstReceived()).isEqualTo(Value.UNDEFINED);
            });
        }
    }

    @Nested
    @DisplayName("pub/sub observer")
    class PubSubObserver {

        @Test
        @DisplayName("when value is publihed from another instance then observer is notified")
        void observerNotifiedFromOtherInstance() {
            repository.observe(invocation("sapl.test.observe"), received::add);
            received.clear();

            val client2 = RedisClient.create(redis.getRedisURI());
            try (val repo2 = new RedisAttributeRepository(client2, "test-tenant", 0)) {
                repo2.publish(key("sapl.test.observe"), Value.of("from-other-node"));
            }

            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains(Value.of("from-other-node")));
        }
    }

    @Nested
    @DisplayName("when the configured database index is not 0")
    class WhenDatabaseIndexIsNonDefault {

        @Test
        @DisplayName("observer still receives UNDEFINED when a TTL expires")
        void observerNotifiedOfExpiryOnNonDefaultDatabase() {
            val baseUri = RedisURI.create(redis.getRedisURI());
            val uri     = RedisURI.Builder.redis(baseUri.getHost(), baseUri.getPort()).withDatabase(1).build();
            val client1 = RedisClient.create(uri);

            try (val repo1 = new RedisAttributeRepository(client1, "test-tenant", 1)) {
                repo1.observe(invocation("sapl.test.db1"), received::add);
                received.clear(); // discard initial UNDEFINED

                repo1.publish(key("sapl.test.db1"), Value.of("temp"), Duration.ofSeconds(1));

                Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains(Value.UNDEFINED));
            }
        }
    }

    @Nested
    @DisplayName("when notify-keyspace-events is not configured on the Redis server")
    class WhenKeyspaceNotificationsAreDisabled {
        @Test
        @DisplayName("construction fails fast with a clear error instead of starting up silently")
        void constructorRejectsMissingKeyspaceNotifications() {
            try (val setupClient = RedisClient.create(redis.getRedisURI());
                    val testClient = RedisClient.create(redis.getRedisURI())) {
                setupClient.connect().sync().configSet("notify-keyspace-events", "");

                try {
                    assertThatThrownBy(() -> createRepository(testClient)).isInstanceOf(IllegalStateException.class);
                } finally {
                    setupClient.connect().sync().configSet("notify-keyspace-events", "Ex");
                }
            }
        }

        @Test
        @DisplayName("construction fails when then flag for keyevents(E) is missing")
        void keyeventFlagIsMissing() {
            try (val setupClient = RedisClient.create(redis.getRedisURI());
                    val testClient = RedisClient.create(redis.getRedisURI())) {
                setupClient.connect().sync().configSet("notify-keyspace-events", "x");

                try {
                    assertThatThrownBy(() -> createRepository(testClient)).isInstanceOf(IllegalStateException.class);
                } finally {
                    setupClient.connect().sync().configSet("notify-keyspace-events", "Ex");
                }
            }
        }

        @Test
        @DisplayName("construction fails when then flag for for expired events (x) is missing")
        void expiredEventFlagIsMissing() {
            try (val setupClient = RedisClient.create(redis.getRedisURI());
                    val testClient = RedisClient.create(redis.getRedisURI())) {
                setupClient.connect().sync().configSet("notify-keyspace-events", "E");

                try {
                    assertThatThrownBy(() -> createRepository(testClient)).isInstanceOf(IllegalStateException.class);
                } finally {
                    setupClient.connect().sync().configSet("notify-keyspace-events", "Ex");
                }
            }
        }

        @Test
        @DisplayName("construction is sucessfull when the generic flag (A) is used with the keyevent flag (E)")
        void genericFlagIsUsed() {
            try (val setupClient = RedisClient.create(redis.getRedisURI());
                    val testClient = RedisClient.create(redis.getRedisURI())) {
                setupClient.connect().sync().configSet("notify-keyspace-events", "EA");

                try {
                    assertThat(createRepository(testClient)).isInstanceOf(RedisAttributeRepository.class);
                } finally {
                    setupClient.connect().sync().configSet("notify-keyspace-events", "Ex");
                }
            }
        }

        private static RedisAttributeRepository createRepository(RedisClient client) {
            return new RedisAttributeRepository(client, "test-tenant", 0);
        }
    }

    @Nested
    @DisplayName("when TTL is zero or negative")
    class WhenTtlIsInvalid {
        private static final String PDP_ID = "test-tenant";
        private static final String NAME   = "sapl.test.attribute";
        private final RepositoryKey key    = new RepositoryKey(null, NAME, List.of(), PDP_ID);
        private final Value         value  = Value.of("test");

        @ParameterizedTest
        @ValueSource(longs = { 0, -1 })
        @DisplayName("then publishing ist rejected")
        void thenPublishingIsRejected(long seconds) {
            val ttl = Duration.ofSeconds(seconds);
            assertThatThrownBy(() -> repository.publish(key, value, ttl)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TTL must be a strictly positive Duration.");
        }
    }

    @Nested
    @DisplayName("when the repository is closed")
    class WhenRepositoryIsClosed {
        private static final String PDP_ID = "test-tenant";
        private static final String NAME   = "sapl.test.attribute";

        @Test
        @DisplayName("then observe delivers an error")
        void thenObserveDeliversError() {
            val closedRepo = newRepository(PDP_ID);
            closedRepo.close();
            closedRepo.observe(invocation(PDP_ID, null, NAME, List.of()), received::add);
            assertThat(firstReceived()).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    @DisplayName("When a repository key has an entity and arguments")
    class WhenKeyHasEntityAndArguments {
        private final Value         entity = Value.of("alice");
        private final List<Value>   args   = List.of(Value.of(1), Value.of("test"));
        private static final String NAME   = "sapl.test.attribute";
        private static final String PDP_ID = "test-tenant";

        private final RepositoryKey key   = new RepositoryKey(entity, NAME, args, PDP_ID);
        private final Value         value = Value.of("test");

        @Test
        @DisplayName("then another node restores the value with entity and arguments")
        void thenAnotherNodeReadsValueWithEntityAndArguments() {
            repository.publish(key, value);
            try (val repo2 = newRepository(PDP_ID)) {
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
            try (val repo2 = newRepository(PDP_ID)) {
                repo2.observe(invocation(PDP_ID, entity, NAME, args), received::add);
                repository.publish(key, value);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value));
            }
        }
    }

    @Nested
    @DisplayName("when the pub/sub connection is killed")
    class WhenPubSubConnectionIsKilled {
        private static final String PDP_ID   = "test-tenant";
        private static final String NAME     = "sapl.test.attribute";
        private final String        redisKey = redisKey(PDP_ID, NAME);
        private final RepositoryKey key      = new RepositoryKey(null, NAME, List.of(), PDP_ID);
        private final Value         value1   = Value.of("value1");
        private final Value         value2   = Value.of("value2");

        @Test
        @DisplayName("then observer receives an error and is updated after a resync")
        void thenObserverReceivesErrorAndUpdatedAfterResync() {
            repository.publish(key, value1);
            repository.observe(invocation(PDP_ID, null, NAME, List.of()), received::add);
            abortConnection(commands -> commands.hset(redisKey, "value", ValueJsonMarshaller.toJsonString(value2)));
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> lastReceived().equals(value2));
            assertThat(received).anyMatch(ErrorValue.class::isInstance);
        }

        @Test
        @DisplayName("then a key that was deleted during downtime is undefined after resync")
        void thenDeletedKeyDuringDowntimeIsUndefined() {
            repository.publish(key, value1);
            repository.observe(invocation(PDP_ID, null, NAME, List.of()), received::add);
            abortConnection(commands -> commands.del(redisKey));
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }

        private void abortConnection(Consumer<RedisCommands<String, String>> command) {
            val adminCLI = RedisClient.create(redis.getRedisURI());
            try (val connection = adminCLI.connect()) {
                command.accept(connection.sync());
                connection.sync().clientKill(KillArgs.Builder.typePubsub());
            } finally {
                adminCLI.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("when the order index is maintained")
    class WhenOrderIndexIsMaintained {
        private static final String PDP_ID    = "test-tenant";
        private static final String ORDER_KEY = "sapl:attribute:order:" + PDP_ID;
        private static final String NAME1     = "sapl.test.attribute1";
        private static final String NAME2     = "sapl.test.attribute2";
        private final RepositoryKey key1      = new RepositoryKey(null, NAME1, List.of(), PDP_ID);
        private final RepositoryKey key2      = new RepositoryKey(null, NAME2, List.of(), PDP_ID);

        @Test
        @DisplayName("then new keys are in insertion order")
        void thenNewKeysAreInInsertionOrder() {
            repository.publish(key1, Value.of(1));
            repository.publish(key2, Value.of(2));
            assertThat(order()).containsExactly(redisKey(PDP_ID, NAME1), redisKey(PDP_ID, NAME2));
        }

        @Test
        @DisplayName("then republishing keeps the original position")
        void thenRepublishingKeepsTheOriginalPosition() {
            repository.publish(key1, Value.of(1));
            repository.publish(key2, Value.of(2));
            repository.publish(key1, Value.of(3));
            assertThat(order()).containsExactly(redisKey(PDP_ID, NAME1), redisKey(PDP_ID, NAME2));
        }

        @Test
        @DisplayName("then removed keys leave the order index")
        void thenRemovedKeysLeaveTheOrderIndexCorrect() {
            repository.publish(key1, Value.of(1));
            repository.publish(key2, Value.of(2));
            repository.remove(key1);
            assertThat(order()).containsExactly(redisKey(PDP_ID, NAME2));
        }

        private List<String> order() {
            return client.connect().sync().zrange(ORDER_KEY, 0, -1);
        }
    }

    @Nested
    @DisplayName("when the registration is closed")
    class WhenRegistrationIsClosed {
        private static final String NAME  = "sapl.test";
        private final Value         value = Value.of("test");

        @Test
        @DisplayName("")
        void thenClosedObserverReceivesNoFurtherValues() {
            val closedObserver = new CopyOnWriteArrayList<Value>();
            repository.observe(invocation(NAME), closedObserver::add).close();
            repository.observe(invocation(NAME), received::add);

            val client2 = RedisClient.create(redis.getRedisURI());
            try (val repo2 = new RedisAttributeRepository(client2, "test-tenant", 0)) {
                repo2.publish(key(NAME), value);
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value));
            }

            assertThat(closedObserver).containsExactly(Value.UNDEFINED);
        }
    }
}
