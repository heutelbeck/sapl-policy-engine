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
package io.sapl.attributeapi.auth.oauth2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.sapl.attributeapi.AttributeApiApplication;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;

@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-oauth2-auth=true", "io.sapl.attributes.storage=none" })
@AutoConfigureMockMvc
@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@Import(OAuth2Tests.Config.class)
class OAuth2Tests {
    @Container
    static GenericContainer<?> redis = createRedisContainer();

    @Container
    static KeycloakContainer keycloak = createKeycloakContainer();

    private static GenericContainer<?> createRedisContainer() {
        GenericContainer<?> container = new GenericContainer<>("redis:8");
        container.withExposedPorts(6379);
        return container;
    }

    private static KeycloakContainer createKeycloakContainer() {
        KeycloakContainer container = new KeycloakContainer("quay.io/keycloak/keycloak:26.7");
        container.withRealmImportFile("keycloak/realm-export.json");
        return container;
    }

    @DynamicPropertySource
    static void oauth2Props(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/test-realm");
    }

    @TestConfiguration
    static class Config {
        @Bean
        AttributeStore attributeStore() {
            return new RedisAttributeStore(RedisClient.create(redisUri()));
        }
    }

    @BeforeEach
    void setUp() {
        RedisClient.create(redisUri()).connect().sync().flushall();
    }

    private static RedisURI redisUri() {
        return RedisURI.create(redis.getHost(), redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A valid token as JWT from Keycloak authenticates the user")
    void whenTokenIsValidThenUserIsAuthenticated() throws Exception {
        var token = getToken("test-api-client", "test-api-client-secret");

        mockMvc.perform(put("/api/attributes/sapl.test.oauth2").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_oauth2", "ttl": 60 }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/attributes/sapl.test.oauth2").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("test_oauth2"));
    }

    @Test
    @DisplayName("An valid token is unauthorized when the set claim name for the pdp id is missing")
    void whenTokenClaimIsMissingThePdpIdClaimThenHttpUnauthorized() throws Exception {
        var token = getToken("test-no-pdp-id", "test-no-pdp-id-secret");
        mockMvc.perform(put("/api/attributes/sapl.test.oauth2").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_oauth2", "ttl": 60 }
                        """)).andExpect(status().isUnauthorized());
    }

    private String getToken(String clientId, String clientSecret) throws IOException, InterruptedException {
        var tokenURL = keycloak.getAuthServerUrl() + "/realms/test-realm/protocol/openid-connect/token";
        var form     = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
        var request  = HttpRequest.newBuilder(URI.create(tokenURL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        var json     = JsonMapper.builder().build().readTree(response.body());
        return json.get("access_token").asString();
    }
}
