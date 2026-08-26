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
package io.sapl.attributeapi.attributes;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.lettuce.core.RedisClient;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
class AttributeStoreConfigurationTests {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Container
    static GenericContainer<?> redis = createRedisContainer();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
                for (var name : beanFactory.getBeanDefinitionNames()) {
                    beanFactory.getBeanDefinition(name).setLazyInit(true);
                }
            })).withUserConfiguration(AttributeStoreConfiguration.class);

    @Test
    @DisplayName("The configured Postgres attribute store is created and connected")
    void whenStoragePostgresThenPostgresAttributeStoreBeanExists() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attributes.storage=postgres",
                "io.sapl.attributes.postgres.host=" + postgres.getHost(),
                "io.sapl.attributes.postgres.port=" + postgres.getMappedPort(5432),
                "io.sapl.attributes.postgres.database=" + postgres.getDatabaseName(),
                "io.sapl.attributes.postgres.username=" + postgres.getUsername(),
                "io.sapl.attributes.postgres.password=" + postgres.getPassword()).run(context -> {
                    assertThat(context).hasSingleBean(AttributeStore.class);
                    assertThat(context.getBean(AttributeStore.class)).isInstanceOf(PostgresAttributeStore.class);
                });
    }

    @Test
    @DisplayName("The configured Mongo attribute store is created and connected")
    void whenStorageMongoThenMongoAttributeStoreBeanExists() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attributes.storage=mongo")
                .run(context -> {
                    assertThat(context).hasSingleBean(AttributeStore.class);
                    assertThat(context.getBean(AttributeStore.class)).isInstanceOf(MongoAttributeStore.class);
                });
    }

    @Test
    @DisplayName("No bean is created when the attribute is missing")
    void whenStorageNoneThenNoAttributeStoreBeanExists() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attributes.storage=none")
                .run(context -> assertThat(context).doesNotHaveBean(AttributeStore.class));
    }

    @Test
    @DisplayName("The configured Redis attribute store is created and connected")
    void whenStorageRedisThenRedisClientBeanExists() {
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attributes.storage=redis",
                "io.sapl.attributes.redis.host=" + redis.getHost(),
                "io.sapl.attributes.redis.port=" + redis.getMappedPort(6379)).run(context -> {
                    assertThat(context).hasBean("attributeApiRedisClient");
                    assertThat(context.getBean("attributeApiRedisClient", RedisClient.class)).isNotNull();
                });
    }

    private static GenericContainer<?> createRedisContainer() {
        GenericContainer<?> container = new GenericContainer<>("redis:8");
        container.withExposedPorts(6379);
        return container;
    }
}
