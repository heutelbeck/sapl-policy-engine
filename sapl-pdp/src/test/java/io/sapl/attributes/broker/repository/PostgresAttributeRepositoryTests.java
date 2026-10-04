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

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@DisplayName("PostgresAttributeRepository")
class PostgresAttributeRepositoryTests {
    // Create a custom image to do the necessary administrative steps for pg_cron / NOTIFY
    private static final Future<String> PG_CRON_IMAGE = new ImageFromDockerfile()
            .withDockerfile(Path.of("src/test/resources/pg_cron/Dockerfile"));

    @Container
    static PostgreSQLContainer postgres;

    private DatabaseClient              client;
    private PostgresAttributeRepository repository;
    private final List<Value>           received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        val config            = PostgresqlConnectionConfiguration.builder().host(postgres.getHost())
                .port(postgres.getMappedPort(5432)).database(postgres.getDatabaseName())
                .username(postgres.getUsername()).password(postgres.getPassword()).build();
        val connectionFactory = new PostgresqlConnectionFactory(config);

        client     = DatabaseClient.create(connectionFactory);
        repository = new PostgresAttributeRepository(client, connectionFactory, "test-tenant", "attributes");
        received.clear();
    }

    @AfterEach
    void tearDown() {
        repository.close();
        client.sql("TRUNCATE TABLE attributes").then().block();
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
    
    private PostgresAttributeRepository newRepository(String pdpId, String tableName) {
    	val config = PostgresqlConnectionConfiguration.builder().host(postgres.getHost())
                .port(postgres.getMappedPort(5432)).database(postgres.getDatabaseName())
                .username(postgres.getUsername()).password(postgres.getPassword()).build();
    	val factory = new PostgresqlConnectionFactory(config);
    	return new PostgresAttributeRepository(DatabaseClient.create(factory), factory, pdpId, tableName);
    }

    private Value firstReceived() {
        return received.getFirst();
    }

    private Value lastReceived() {
        return received.getLast();
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
            repository.close();

            try (val repo2 = newRepository("test-tenant", "attributes")) {
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
        @DisplayName("observer receives UNDEFINED after expiry")
        void thenObserverReceivesUndefinedAfterExpiry() {
            repository.observe(invocation("sapl.test.ttl"), received::add);
            repository.publish(key("sapl.test.ttl"), Value.of("temp"), Duration.ofSeconds(1));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }
    }

    @Nested
    @DisplayName("when the notification connection is interrupted")
    class WhenConnectionIsInterrupted {
    	private final String        name       = "sapl.test.attribute";
        private final String        pdpId      = "test-tenant";
        private final RepositoryKey key        = new RepositoryKey(null, name, List.of(), pdpId);
        private final Value         value      = Value.of("test");
        private final String        disconnect = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE 'LISTEN%' AND pid <> pg_backend_pid()";
        
        @Test
        @DisplayName("then repository reconnects and catches up on changes missed during the downtime")
        void thenObserverEventuallyReceivesChangesMissedDuringdowntime() {
            try (val repo2 = newRepository("test-tenant", "attributes")) {
                repository.observe(invocation("sapl.test.reconnect"), received::add);
                repo2.publish(key("sapl.test.reconnect"), Value.of("before-downtime"));
                Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(() -> lastReceived().equals(Value.of("before-downtime")));

                client.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                        + "WHERE query LIKE 'LISTEN%' AND pid <> pg_backend_pid()").then().block();

                repo2.publish(key("sapl.test.reconnect"), Value.of("during-downtime"));

                Awaitility.await().atMost(Duration.ofSeconds(60))
                        .until(() -> lastReceived().equals(Value.of("during-downtime")));
            }
        }
        
        @Test
        @DisplayName("then the observers received an error while the backend is disconnected")
        void thenObserversReceivedErrorWhileBackendIsDisconnected() {
        	repository.publish(key, value);
        	repository.observe(invocation(pdpId, null, name, List.of()), received::add);
        	client.sql(disconnect).then().block();
        	Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> received.stream().anyMatch(ErrorValue.class::isInstance));
        }
        
        @Test
        @DisplayName("then a deleted row without a pub/sub notification is deleted after the reconnect resync")
        void thenRowWithoutNotificationIsDeletedAfterReconnectResync() {
        	repository.publish(key, value);
        	repository.observe(invocation(pdpId, null, name, List.of()), received::add);
        	client.sql("DELETE FROM attributes WHERE name = :name").bind("name", name).then().block();
        	client.sql(disconnect).then().block();
        	Awaitility.await().atMost(Duration.ofSeconds(60)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }
    }

    @Nested
    @DisplayName("When the pg_cron cleanup is activated")
    class whenPgCronCleanUpIsActivated {

        private static final String EXPIRE_CRON_TEST_ENTRY_SQL = "UPDATE attributes SET expires_at = now() - interval '1 minute' WHERE name = 'sapl.test.cron'";

        private static final String DELETE_AND_NOTIFY_SQL = """
                WITH deleted AS (
                    DELETE FROM attributes
                    WHERE expires_at < now()
                    RETURNING pdp_id, name, entity, arguments
                )
                SELECT pg_notify(
                    'attribute_changes',
                    json_build_object(
                        'pdpId', pdp_id,
                        'name', name,
                        'entity', entity,
                        'arguments', arguments
                    )::text
                )
                FROM deleted
                """;
        
        private final String pdpId = "tenant1";
        private final String table = "attributes";

        @Test
        @DisplayName("then cronjob is registered on construction")
        void thenCronjobIsRegisteredOnConstruction() {
            var jobs = client.sql("SELECT count(*) FROM cron.job WHERE jobname = 'ttl-cleanup-attributes'")
                    .map(row -> row.get(0, Long.class)).one().block();
            assertThat(jobs).isEqualTo(1L);
        }

        @Test
        @DisplayName("then an expired entry is deleted by the scheduler and notify is send to repository")
        void thenExpiredEntryIsDeletedAndSchedulerNotifiesRepository() {
            // Publish a key without a TTL, so that no cleanup task is created internally
            repository.publish(key("sapl.test.cron"), Value.of("crontest"));
            repository.observe(invocation("sapl.test.cron"), received::add);
            assertThat(firstReceived()).isEqualTo(Value.of("crontest"));

            // Set a TTL of 1 minute for the attribute, so that the scheduler removes it after it's expired
            client.sql(EXPIRE_CRON_TEST_ENTRY_SQL).then().block();

            // Execute the job manually to avoid wait times
            client.sql(DELETE_AND_NOTIFY_SQL).then().block();

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
        }

        @Test
        @DisplayName("then an observer of a second pdp received an Value.UNDEFINED")
        void thenAnObserverOfASecondNodeReceivesUndefinedAfterCleanup() {
            // Publish a key without a TTL, so that no cleanup task is created internally
            repository.publish(key("sapl.test.cron"), Value.of("crontest"));

            try (val repo2 = newRepository("test-tenant", "attributes")) {
                // The second repository never added the key above but knows it through the loadFromDB
                repo2.observe(invocation("sapl.test.cron"), received::add);
                assertThat(firstReceived()).isEqualTo(Value.of("crontest"));

                // Set expire
                client.sql(EXPIRE_CRON_TEST_ENTRY_SQL).then().block();

                // Execute the job manually to avoid wait times
                client.sql(DELETE_AND_NOTIFY_SQL).then().block();

                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("then a Postgres instance without access to pg_cron logs a warning")
        void thenPostgresInstanceWithoutAccessToPgCronLogsWarning() {
            try (PostgreSQLContainer container2 = new PostgreSQLContainer("postgres:18.6")) {
                container2.start();

                val containerConfig2 = PostgresqlConnectionConfiguration.builder().host(container2.getHost())
                        .port(container2.getMappedPort(5432)).database(container2.getDatabaseName())
                        .username(container2.getUsername()).password(container2.getPassword()).build();

                val sqlConnection2 = new PostgresqlConnectionFactory(containerConfig2);
                val sqlClient2     = DatabaseClient.create(sqlConnection2);

                try (val repo2 = new PostgresAttributeRepository(sqlClient2, sqlConnection2, "tenant-2",
                        "attributes")) {
                    repo2.publish(key("sapl.test.nocron"), Value.of("works-as-expected"));
                    repo2.observe(invocation("sapl.test.nocron"), received::add);
                    assertThat(firstReceived()).isEqualTo(Value.of("works-as-expected"));
                }
            }
        }
        
        @Test
        @DisplayName("then a repated construction doesn't duplicate the cron job")
        void thenRepeatedConstructionDoesNotDuplicateCronJob() {
        	newRepository(pdpId, table).close();
            val jobs = client.sql("SELECT count(*) FROM cron.job WHERE jobname = 'ttl-cleanup-attributes'")
                    .map(row -> row.get(0, Long.class)).one().block();
            assertThat(jobs).isEqualTo(1L);
        }
    }
    
    @Nested
    @DisplayName("When a repository key has an entity and arguments")
    class WhenKeyHasEntityAndArguments{ 	
    	private final Value         entity = Value.of("alice");
    	private final List<Value>   args   = List.of(Value.of(1), Value.of("test"));
    	private final String        name   = "sapl.test.attribute";
    	private final String        pdpId  = "test-tenant";
    	private final String        table  = "attributes";
    	
    	private final RepositoryKey key    = new RepositoryKey(entity, name, args, pdpId);
    	private final Value         value  = Value.of("test");
    	
    	@Test
    	@DisplayName("then another node restores the value with entity and arguments")
    	void thenAnotherNodeReadsValueWithEntityAndArguments() {
    		repository.publish(key, value);
    		try (val repo2 = newRepository(pdpId, table)) {
    	        repo2.observe(invocation(pdpId, entity, name, args), received::add);
    	        assertThat(firstReceived()).isEqualTo(value);
    	    }
    	}
    	
    	@Test
    	@DisplayName("then keys with different arguments are distinct")
    	void thenKeysWithDifferentArgumentsAreDistinct() {
    		repository.publish(key, value);
    		repository.observe(invocation(pdpId, entity, name, List.of(Value.of("other"))), received::add);
    		assertThat(firstReceived()).isEqualTo(Value.UNDEFINED);
    	}
    	
    	@Test
    	@DisplayName("then another node received updates for the same key")
    	void thenAnotherNodeReceivesUpdatesForTheSameKey() {
    		try (val repo2 = newRepository(pdpId, table)){
    			repo2.observe(invocation(pdpId, entity, name, args), received::add);
    			repository.publish(key, value);
    			Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value));
    		}
    	}
    }
    
    @Nested
    @DisplayName("when another pdp id uses the same table")
    class WhenAnotherPdpIdUsesTheSameTable {
    	private final String        name       = "sapl.test.attribute";
        private final String        pdpId1     = "tenant01";
        private final String        pdpId2     = "tenant02";
        private final String        table      = "attributes";
        private final RepositoryKey key1       = new RepositoryKey(null, name, List.of(), pdpId1);
        private final RepositoryKey key2       = new RepositoryKey(null, name, List.of(), pdpId2);
        private final Value         value1     = Value.of("value1");
        private final Value         value2     = Value.of("value2");
        
        @Test
        @DisplayName("then notifications of another pdp id are ignored")
        void thenNotificationsOfOtherPdpIdAreIgnored() {
        	try (val repo2 = newRepository(pdpId2, table)){
        		repo2.observe(invocation(pdpId2, null, name, List.of()), received::add);
        		repository.publish(key1, value1);
        		repo2.publish(key2, value2);
        		Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> lastReceived().equals(value2));
        		assertThat(received).doesNotContain(value1);
        	}
        }
        
        @Test
        @DisplayName("then a restart of a repository only restores values of it's own pdp id")
        void thenRestartOnlyLoadsOwnKeys() {
        	repository.publish(key1, value1);
        	try(val repo2 = newRepository(pdpId2, table)){
        		repo2.observe(invocation(pdpId2, null, name, List.of()), received::add);
        		assertThat(firstReceived()).isEqualTo(Value.UNDEFINED);
        	}
        }
    }
    
    @Nested
    @DisplayName("when restoring the keys from a database")
    class WhenRestoringKeysFromADatabase {
    	private final String        pdpId      = "test-tenant";
        private final String        table      = "attributes";
        private final String        attribute1 = "sapl.test.attribute1";
        private final String        attribute2 = "sapl.test.attribute2";
        private final RepositoryKey key1       = new RepositoryKey(null, attribute1, List.of(), pdpId);
        private final RepositoryKey key2       = new RepositoryKey(null, attribute2, List.of(), pdpId);
        private final Value         value      = Value.of("test");
        
        @Test
        @DisplayName("then the remaining TTL is applied after a restart")
        void thenRemainingTTLIsAppliedToKey() {
        	repository.publish(key1, value, Duration.ofSeconds(3));
        	try(val repo2 = newRepository(pdpId, table)){
        		repo2.observe(invocation(pdpId, null, attribute1, List.of()), received::add);
        		assertThat(firstReceived()).isEqualTo(value);
        		Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> lastReceived().equals(Value.UNDEFINED));
        	}
        }
        
        @Test
        @DisplayName("then expired rows are deleted from the table while reloading")
        void thenExpiredRowsAreDeletedWhileReloading() {
        	repository.publish(key2, value, Duration.ofSeconds(60));
        	client.sql("UPDATE attributes SET expires_at = now() - interval '1 minute' WHERE name = :name").bind("name", attribute2).then().block();
        	try(val repo2 = newRepository(pdpId, table)){
        		long rows = client.sql("SELECT count(*) FROM attributes WHERE name = :name").bind("name", attribute2).map(row -> row.get(0, Long.class)).one().block();
        		assertThat(rows).isZero();
        	}
        }
    }
    
    static {
        try {
            PostgreSQLContainer container = new PostgreSQLContainer(
                    DockerImageName.parse(PG_CRON_IMAGE.get()).asCompatibleSubstituteFor("postgres"));
            container.withDatabaseName("test");
            container.withUsername("test");
            container.withPassword("test");
            container.withCommand("postgres", "-c", "shared_preload_libraries=pg_cron", "-c",
                    "cron.database_name=test");
            container.withInitScript("pg_cron/init.sql");
            postgres = container;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
