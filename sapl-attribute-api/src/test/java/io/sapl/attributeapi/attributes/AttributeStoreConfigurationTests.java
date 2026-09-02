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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

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

    private static GenericContainer<?> createRedisContainer() {
        GenericContainer<?> container = new GenericContainer<>("redis:8");
        container.withExposedPorts(6379);
        return container;
    }
}
