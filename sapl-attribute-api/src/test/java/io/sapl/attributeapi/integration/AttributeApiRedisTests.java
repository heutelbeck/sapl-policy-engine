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
package io.sapl.attributeapi.integration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.sapl.attributeapi.AttributeApiApplication;
import jakarta.ws.rs.core.MediaType;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-no-auth=true", "io.sapl.attribute-api.allow-basic-auth=false",
        "io.sapl.attribute-api.allow-api-key-auth=false", "io.sapl.attribute-api.allow-oauth2-auth=false",
        "io.sapl.attributes.backends.redis-test.type=redis", "io.sapl.attributes.tenants.default=redis-test" })
@Testcontainers
@DisabledOnOs(OS.WINDOWS)
class AttributeApiRedisTests extends AbstractAttributeApiTests {

    @Container
    static GenericContainer<?> redis = createRedisContainer();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("io.sapl.attributes.backends.redis-test.redis.host", redis::getHost);
        registry.add("io.sapl.attributes.backends.redis-test.redis.port", () -> redis.getMappedPort(6379));
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

    @Test
    @DisplayName("Concurrent PUT requests to create the same new attribute result in excatly one HTTP 201 created. HSETNX works properly.")
    void whenNewAttributeIsPublishedParallelThenOnlyOneRequestReportsCreated() throws Exception {
        // ThreadPool with tasks to avoid creating them manually
        int             parallelRequests = 15;
        ExecutorService executor         = Executors.newFixedThreadPool(parallelRequests);
        CountDownLatch  startSignal      = new CountDownLatch(1);

        // Create the tasks and let the threads waits till the countdown is done
        List<Callable<Integer>> tasks = IntStream.range(0, parallelRequests).<Callable<Integer>>mapToObj(i -> () -> {
            startSignal.await();
            MvcResult result = mockMvc.perform(put("/api/attributes/sapl.test/sapl.test.parallel").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"value\": \"request-%d\", \"ttl\": 600 }".formatted(i))).andReturn();
            return result.getResponse().getStatus();
        }).toList();

        List<Future<Integer>> futures = tasks.stream().map(executor::submit).toList();

        // Start the parallel requests. The above counter goes down from 1 to 0
        startSignal.countDown();

        List<Integer> httpCodes = new ArrayList<>(futures.size());
        for (Future<Integer> future : futures) {
            httpCodes.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(httpCodes).filteredOn(code -> code == HttpStatus.CREATED.value()).hasSize(1);
        assertThat(httpCodes).filteredOn(code -> code == HttpStatus.OK.value()).hasSize(parallelRequests - 1);
    }
}
