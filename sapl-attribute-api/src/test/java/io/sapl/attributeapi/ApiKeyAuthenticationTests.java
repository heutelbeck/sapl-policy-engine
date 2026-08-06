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
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E tests for API key authentication in the standalone attribute API.
 * Raw key "testkey123" was hashed with SHA-256 to produce the hash configured
 * below (sha256sum on the raw key value).
 */
@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-api-key-auth=true", "io.sapl.attribute-api.users[0].id=tenant-a-user",
        "io.sapl.attribute-api.users[0].tenant-id=tenant-a",
        "io.sapl.attribute-api.users[0].key.hash=87d452521c9a7f5c9052ae6190e900a46e2a2df5f144158c2fc20b797adb470b",
        "io.sapl.attributes.storage=none" })
@AutoConfigureMockMvc
@Testcontainers
@Import(ApiKeyAuthenticationTests.Config.class)
class ApiKeyAuthenticationTests {

    private static final String VALID_KEY_HEADER   = "Bearer sapl_testkey123";
    private static final String UNKNOWN_KEY_HEADER = "Bearer sapl_doesnotexist";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RedisClient.create(redisUri()).connect().sync().flushall();
    }

    private static RedisURI redisUri() {
        return RedisURI.create(redis.getHost(), redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("Valid API key authenticates and can publish and read an attribute")
    void validApiKeySucceeds() throws Exception {
        mockMvc.perform(post("/api/attributes/sapl.test.apikey").header(HttpHeaders.AUTHORIZATION, VALID_KEY_HEADER)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_apikey", "ttl": 60 }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/attributes/sapl.test.apikey").header(HttpHeaders.AUTHORIZATION, VALID_KEY_HEADER))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("test_apikey"));
    }

    @Test
    @DisplayName("Unknown API key is rejected")
    void unknownApiKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/attributes/sapl.test.apikey").header(HttpHeaders.AUTHORIZATION, UNKNOWN_KEY_HEADER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Missing Authorization header is rejected")
    void missingHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/api/attributes/sapl.test.apikey")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authorization header without the sapl_ API key prefix is rejected")
    void wrongPrefixIsRejected() throws Exception {
        mockMvc.perform(get("/api/attributes/sapl.test.apikey").header(HttpHeaders.AUTHORIZATION, "Bearer sometoken"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class Config {
        @Bean
        AttributeStore attributeStore() {
            return new RedisAttributeStore(RedisClient.create(redisUri()));
        }
    }
}
