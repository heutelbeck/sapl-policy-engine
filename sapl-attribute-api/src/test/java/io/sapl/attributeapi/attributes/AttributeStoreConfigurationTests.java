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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.lettuce.core.RedisClient;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;

class AttributeStoreConfigurationTests {

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
                "io.sapl.attributes.postgres.password=testsecret").run(context -> {
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
        contextRunner.withPropertyValues("io.sapl.attribute-api.enabled=true", "io.sapl.attributes.storage=redis")
                .run(context -> {
                    assertThat(context).hasBean("attributeApiRedisClient");
                    assertThat(context.getBean("attributeApiRedisClient", RedisClient.class)).isNotNull();
                });
    }
}
