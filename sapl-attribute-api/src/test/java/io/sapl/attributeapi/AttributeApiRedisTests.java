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
package io.sapl.attributeapi;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-no-auth=true", "io.sapl.attribute-api.allow-basic-auth=false",
        "io.sapl.attribute-api.allow-api-key-auth=false", "io.sapl.attribute-api.allow-oauth2-auth=false",
        "io.sapl.attributes.storage=redis" })
@Testcontainers
@DisabledOnOs(OS.WINDOWS)
class AttributeApiRedisTests extends AbstractAttributeApiTests {

    @Container
    static GenericContainer<?> redis = createRedisContainer();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("io.sapl.attributes.redis.host", redis::getHost);
        registry.add("io.sapl.attributes.redis.port", () -> redis.getMappedPort(6379));
    }

    @Override
    protected void cleanRepository() {
        try (RedisClient client = RedisClient.create(redisUri()); var connection = client.connect()) {
            connection.sync().flushall();
        }
    }

    private static RedisURI redisUri() {
        return RedisURI.create(redis.getHost(), redis.getMappedPort(6379));
    }

    private static GenericContainer<?> createRedisContainer() {
        GenericContainer<?> container = new GenericContainer<>("redis:8");
        container.withExposedPorts(6379);
        return container;
    }
}
