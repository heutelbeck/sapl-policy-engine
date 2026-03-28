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
package io.sapl.node.it.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import tools.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Integration tests for the RSocket protobuf transport with all authentication
 * methods.
 * <p>
 * Validates that the RSocket endpoint (port 7000) accepts connections and
 * returns correct authorization decisions using the same authentication
 * mechanisms as the HTTP endpoint: no-auth, basic auth, API key, and
 * OAuth2/JWT.
 * <p>
 * Each test starts a SAPL Node Docker container with RSocket enabled and the
 * appropriate authentication configuration, then connects via the protobuf
 * RSocket client.
 */
@Testcontainers
@DisplayName("RSocket Transport Integration Tests")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RSocketTransportIT extends BaseIntegrationTest {

    private static final int    RSOCKET_PORT  = 7000;
    private static final String POLICIES_PATH = "it/policies/single-pdp/";

    private static final String BASIC_USERNAME       = "mpI3KjU7n1";
    private static final String BASIC_SECRET         = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
    private static final String BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
    private static final String API_KEY              = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String API_KEY_ENCODED      = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String DEFAULT_PDP_ID       = "default";

    private static final AuthorizationSubscription PERMIT_SUBSCRIPTION = AuthorizationSubscription.of("Willi", "eat",
            "apple");
    private static final AuthorizationSubscription DENY_SUBSCRIPTION   = AuthorizationSubscription.of("Nobody",
            "destroy", "everything");

    private GenericContainer<?> createRSocketContainer(String policiesPath) {
        return createSaplNodeContainerWithoutTls(policiesPath).withExposedPorts(SAPL_SERVER_PORT, RSOCKET_PORT)
                .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT));
    }

    private int getRSocketPort(GenericContainer<?> container) {
        return container.getMappedPort(RSOCKET_PORT);
    }

    @Nested
    @DisplayName("No Authentication")
    class NoAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT when policy matches over RSocket without auth")
        void whenNoAuthAndPolicyMatchesThenPermit() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("returns DENY when policy does not match over RSocket without auth")
        void whenNoAuthAndPolicyDoesNotMatchThenDeny() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).build();

                StepVerifier.create(pdp.decide(DENY_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }
    }

    @Nested
    @DisplayName("Basic Authentication")
    class BasicAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT with valid basic credentials over RSocket")
        void whenValidBasicCredentialsThenPermit() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).basicAuth(BASIC_USERNAME, BASIC_SECRET).build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("rejects connection with invalid basic credentials over RSocket")
        void whenInvalidBasicCredentialsThenError() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).basicAuth(BASIC_USERNAME, "wrongPassword").build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.INDETERMINATE)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }
    }

    @Nested
    @DisplayName("API Key Authentication")
    class ApiKeyAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT with valid API key over RSocket")
        void whenValidApiKeyThenPermit() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                    .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-apikey-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", API_KEY_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).apiKey(API_KEY).build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("rejects connection with invalid API key over RSocket")
        void whenInvalidApiKeyThenError() {
            try (val container = createRSocketContainer(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                    .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-apikey-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", API_KEY_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(getRSocketPort(container)).apiKey("sapl_invalidKeyThatWillNotMatchAnything123456")
                        .build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.INDETERMINATE)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }
    }

    @Nested
    @DisplayName("OAuth2/JWT Authentication")
    class OAuth2JwtAuthenticationTests {

        private static final Path   OAUTH_POLICIES_DIR = Path.of("examples/docker/with-keycloak/policies")
                .toAbsolutePath();
        private static final Path   REALM_EXPORT       = Path
                .of("examples/docker/with-keycloak/keycloak/realm-export.json").toAbsolutePath();
        private static final String KEYCLOAK_IMAGE     = "quay.io/keycloak/keycloak:25.0";

        @Test
        @DisplayName("returns PERMIT with valid JWT over RSocket")
        void whenValidJwtThenPermit() {
            try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
                keycloak.start();

                try (val saplNode = createSaplNodeOnNetwork(network)) {
                    saplNode.start();

                    val token        = acquireToken(keycloak, "default-user", "default123");
                    val pdp          = RemotePolicyDecisionPoint.builder().rsocket().host(saplNode.getHost())
                            .port(saplNode.getMappedPort(RSOCKET_PORT)).apiKey(token).build();
                    val subscription = AuthorizationSubscription.of("user", "read", "document");

                    StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                            .verify(Duration.ofSeconds(30));
                }
            }
        }

        @Test
        @DisplayName("returns DENY with valid JWT when policy does not match over RSocket")
        void whenValidJwtAndPolicyDoesNotMatchThenDeny() {
            try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
                keycloak.start();

                try (val saplNode = createSaplNodeOnNetwork(network)) {
                    saplNode.start();

                    val token        = acquireToken(keycloak, "default-user", "default123");
                    val pdp          = RemotePolicyDecisionPoint.builder().rsocket().host(saplNode.getHost())
                            .port(saplNode.getMappedPort(RSOCKET_PORT)).apiKey(token).build();
                    val subscription = AuthorizationSubscription.of("user", "delete", "secret");

                    StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                            .verify(Duration.ofSeconds(30));
                }
            }
        }

        private KeycloakContainer createKeycloakContainer(Network network) {
            return new KeycloakContainer(KEYCLOAK_IMAGE).withNetwork(network).withNetworkAliases("keycloak")
                    .withCopyFileToContainer(MountableFile.forHostPath(REALM_EXPORT),
                            "/opt/keycloak/data/import/realm-export.json")
                    .withEnv("KC_HOSTNAME", "http://keycloak:8080").withEnv("KC_HOSTNAME_STRICT_BACKCHANNEL", "false")
                    .withStartupTimeout(Duration.ofMinutes(5));
        }

        private GenericContainer<?> createSaplNodeOnNetwork(Network network) {
            return createSaplNodeContainer().withNetwork(network).withExposedPorts(SAPL_SERVER_PORT, RSOCKET_PORT)
                    .withFileSystemBind(OAUTH_POLICIES_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                    .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "false")
                    .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "true")
                    .withEnv("IO_SAPL_NODE_OAUTH_PDPIDCLAIM", "sapl_pdp_id")
                    .withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false")
                    .withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default")
                    .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI",
                            "http://keycloak:8080/realms/sapl-demo")
                    .withEnv("SERVER_SSL_ENABLED", "false").withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                    .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT));
        }

        private String acquireToken(KeycloakContainer keycloak, String username, String password) {
            val tokenUrl = keycloak.getAuthServerUrl() + "/realms/sapl-demo/protocol/openid-connect/token";

            val response = WebClient.create().post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "password").with("client_id", "sapl-client")
                            .with("client_secret", "sapl-client-secret").with("username", username)
                            .with("password", password))
                    .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(30));

            return response.get("access_token").asString();
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Routing")
    class MultiTenantRoutingTests {

        private static final String MULTI_TENANT_POLICIES = "it/policies/multi-tenant/";

        @Test
        @DisplayName("different RSocket connections route to different tenants")
        void whenDifferentCredentialsThenDifferentTenants() {
            try (val container = createSaplNodeContainer()
                    .withClasspathResourceMapping(MULTI_TENANT_POLICIES, "/pdp/data/", BindMode.READ_ONLY)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                    .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                    .withEnv("SERVER_SSL_ENABLED", "false").withExposedPorts(SAPL_SERVER_PORT, RSOCKET_PORT)
                    .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                    .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT))
                    .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "prod-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", "prodUser")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)
                    .withEnv("IO_SAPL_NODE_USERS_1_ID", "staging-client")
                    .withEnv("IO_SAPL_NODE_USERS_1_PDPID", "staging")
                    .withEnv("IO_SAPL_NODE_USERS_1_BASIC_USERNAME", "stagingUser")
                    .withEnv("IO_SAPL_NODE_USERS_1_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val rsocketPort  = container.getMappedPort(RSOCKET_PORT);
                val host         = container.getHost();
                val subscription = AuthorizationSubscription.of("anyone", "anything", "anywhere");

                val prodPdp = RemotePolicyDecisionPoint.builder().rsocket().host(host).port(rsocketPort)
                        .basicAuth("prodUser", BASIC_SECRET).build();

                StepVerifier.create(prodPdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));

                val stagingPdp = RemotePolicyDecisionPoint.builder().rsocket().host(host).port(rsocketPort)
                        .basicAuth("stagingUser", BASIC_SECRET).build();

                StepVerifier.create(stagingPdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }
    }

}
