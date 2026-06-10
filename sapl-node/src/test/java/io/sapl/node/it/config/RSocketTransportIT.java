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

import javax.net.ssl.SSLException;

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
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
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
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class RSocketTransportIT extends BaseIntegrationTest {

    private static final int    RSOCKET_PORT  = 7000;
    private static final String POLICIES_PATH = "it/policies/single-pdp/";

    private static final String BASIC_USERNAME       = "mpI3KjU7n1";
    private static final String BASIC_SECRET         = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
    private static final String BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
    private static final String API_KEY              = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String API_KEY_ENCODED      = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String DEFAULT_PDP_ID       = "default";

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);

    private static final AuthorizationSubscription PERMIT_SUBSCRIPTION = AuthorizationSubscription.of("Willi", "eat",
            "apple");
    private static final AuthorizationSubscription DENY_SUBSCRIPTION   = AuthorizationSubscription.of("Nobody",
            "destroy", "everything");

    private GenericContainer<?> createRSocketContainer(String policiesPath) {
        return createSaplNodeContainerWithoutTls(policiesPath).withExposedPorts(SAPL_SERVER_PORT, RSOCKET_PORT)
                .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT));
    }

    private GenericContainer<?> withNoAuth(GenericContainer<?> container) {
        return container.withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true");
    }

    private GenericContainer<?> withBasicUser(GenericContainer<?> container, String id, String pdpId, String username,
            String encodedSecret) {
        return container.withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                .withEnv("IO_SAPL_NODE_USERS_0_ID", id).withEnv("IO_SAPL_NODE_USERS_0_PDPID", pdpId)
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", username)
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", encodedSecret);
    }

    private GenericContainer<?> withApiKeyUser(GenericContainer<?> container, String id, String pdpId,
            String encodedApiKey) {
        return container.withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                .withEnv("IO_SAPL_NODE_USERS_0_ID", id).withEnv("IO_SAPL_NODE_USERS_0_PDPID", pdpId)
                .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", encodedApiKey);
    }

    private ReactivePolicyDecisionPoint connectNoAuth(GenericContainer<?> container) {
        return RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                .port(container.getMappedPort(RSOCKET_PORT)).build();
    }

    private ReactivePolicyDecisionPoint connectBasic(GenericContainer<?> container, String username, String password) {
        return RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                .port(container.getMappedPort(RSOCKET_PORT)).basicAuth(username, password).build();
    }

    private ReactivePolicyDecisionPoint connectApiKey(GenericContainer<?> container, String apiKey) {
        return RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                .port(container.getMappedPort(RSOCKET_PORT)).apiKey(apiKey).build();
    }

    private void expectDecision(ReactivePolicyDecisionPoint pdp, AuthorizationSubscription subscription,
            AuthorizationDecision expected) {
        StepVerifier.create(pdp.decide(subscription)).expectNext(expected).thenCancel().verify(STEP_TIMEOUT);
    }

    @Nested
    @DisplayName("No Authentication")
    class NoAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT when policy matches over RSocket without auth")
        void whenNoAuthAndPolicyMatchesThenPermit() {
            try (val container = withNoAuth(createRSocketContainer(POLICIES_PATH))) {
                container.start();
                expectDecision(connectNoAuth(container), PERMIT_SUBSCRIPTION, AuthorizationDecision.PERMIT);
            }
        }

        @Test
        @DisplayName("returns DENY when policy does not match over RSocket without auth")
        void whenNoAuthAndPolicyDoesNotMatchThenDeny() {
            try (val container = withNoAuth(createRSocketContainer(POLICIES_PATH))) {
                container.start();
                expectDecision(connectNoAuth(container), DENY_SUBSCRIPTION, AuthorizationDecision.DENY);
            }
        }
    }

    @Nested
    @DisplayName("Basic Authentication")
    class BasicAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT with valid basic credentials over RSocket")
        void whenValidBasicCredentialsThenPermit() {
            try (val container = withBasicUser(createRSocketContainer(POLICIES_PATH), "test-basic-client",
                    DEFAULT_PDP_ID, BASIC_USERNAME, BASIC_SECRET_ENCODED)) {
                container.start();
                expectDecision(connectBasic(container, BASIC_USERNAME, BASIC_SECRET), PERMIT_SUBSCRIPTION,
                        AuthorizationDecision.PERMIT);
            }
        }

        @Test
        @DisplayName("rejects connection with invalid basic credentials over RSocket")
        void whenInvalidBasicCredentialsThenError() {
            try (val container = withBasicUser(createRSocketContainer(POLICIES_PATH), "test-basic-client",
                    DEFAULT_PDP_ID, BASIC_USERNAME, BASIC_SECRET_ENCODED)) {
                container.start();
                expectDecision(connectBasic(container, BASIC_USERNAME, "wrongPassword"), PERMIT_SUBSCRIPTION,
                        AuthorizationDecision.INDETERMINATE);
            }
        }
    }

    @Nested
    @DisplayName("API Key Authentication")
    class ApiKeyAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT with valid API key over RSocket")
        void whenValidApiKeyThenPermit() {
            try (val container = withApiKeyUser(createRSocketContainer(POLICIES_PATH), "test-apikey-client",
                    DEFAULT_PDP_ID, API_KEY_ENCODED)) {
                container.start();
                expectDecision(connectApiKey(container, API_KEY), PERMIT_SUBSCRIPTION, AuthorizationDecision.PERMIT);
            }
        }

        @Test
        @DisplayName("rejects connection with invalid API key over RSocket")
        void whenInvalidApiKeyThenError() {
            try (val container = withApiKeyUser(createRSocketContainer(POLICIES_PATH), "test-apikey-client",
                    DEFAULT_PDP_ID, API_KEY_ENCODED)) {
                container.start();
                expectDecision(connectApiKey(container, "sapl_invalidKeyThatWillNotMatchAnything123456"),
                        PERMIT_SUBSCRIPTION, AuthorizationDecision.INDETERMINATE);
            }
        }
    }

    @Nested
    @DisplayName("OAuth2/JWT Authentication")
    class OAuth2JwtAuthenticationTests {

        private static final Path   OAUTH_POLICIES_DIR = requireExists(
                Path.of("examples/docker/with-keycloak/policies").toAbsolutePath());
        private static final Path   REALM_EXPORT       = requireExists(
                Path.of("examples/docker/with-keycloak/keycloak/realm-export.json").toAbsolutePath());
        private static final String KEYCLOAK_IMAGE     = "quay.io/keycloak/keycloak:25.0";

        private static Path requireExists(Path path) {
            if (!java.nio.file.Files.exists(path)) {
                throw new IllegalStateException("RSocketTransportIT requires the sapl-node module's working directory; "
                        + "expected to find " + path + ". Run from the sapl-node module root, e.g. "
                        + "'mvn -pl sapl-node test' from the engine repo root.");
            }
            return path;
        }

        @Test
        @DisplayName("returns PERMIT with valid JWT over RSocket")
        void whenValidJwtThenPermit() {
            runOauthTest("default-user", "default123", AuthorizationSubscription.of("user", "read", "document"),
                    AuthorizationDecision.PERMIT);
        }

        @Test
        @DisplayName("returns DENY with valid JWT when policy does not match over RSocket")
        void whenValidJwtAndPolicyDoesNotMatchThenDeny() {
            runOauthTest("default-user", "default123", AuthorizationSubscription.of("user", "delete", "secret"),
                    AuthorizationDecision.DENY);
        }

        private void runOauthTest(String username, String password, AuthorizationSubscription subscription,
                AuthorizationDecision expected) {
            try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
                keycloak.start();
                try (val saplNode = createSaplNodeOnNetwork(network)) {
                    saplNode.start();
                    val token = acquireToken(keycloak, username, password);
                    val pdp   = RemotePolicyDecisionPoint.builder().rsocket().host(saplNode.getHost())
                            .port(saplNode.getMappedPort(RSOCKET_PORT)).apiKey(token).build();
                    expectDecision(pdp, subscription, expected);
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

            if (response == null) {
                throw new IllegalStateException("Keycloak token endpoint at " + tokenUrl + " returned no body");
            }
            val accessToken = response.get("access_token");
            if (accessToken == null) {
                throw new IllegalStateException(
                        "Keycloak token endpoint response did not contain 'access_token': " + response);
            }
            return accessToken.asString();
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

                val subscription = AuthorizationSubscription.of("anyone", "anything", "anywhere");
                expectDecision(connectBasic(container, "prodUser", BASIC_SECRET), subscription,
                        AuthorizationDecision.DENY);
                expectDecision(connectBasic(container, "stagingUser", BASIC_SECRET), subscription,
                        AuthorizationDecision.PERMIT);
            }
        }
    }

    @Nested
    @DisplayName("TLS Termination (SSL Bundle)")
    class TlsTests {

        private GenericContainer<?> createRSocketTlsContainer() {
            return createSaplNodeContainerWithoutTls(POLICIES_PATH).withExposedPorts(SAPL_SERVER_PORT, RSOCKET_PORT)
                    .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                    .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT))
                    .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEY_ALIAS", "netty")
                    .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEY_PASSWORD", "changeme")
                    .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_LOCATION", "file:/pdp/data/keystore.p12")
                    .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_PASSWORD", "changeme")
                    .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_TYPE", "PKCS12")
                    .withEnv("SAPL_PDP_RSOCKET_SSL_BUNDLE", "saplbundle").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true");
        }

        private ReactivePolicyDecisionPoint connectRsocketTls(GenericContainer<?> container) {
            try {
                return RemotePolicyDecisionPoint.builder().rsocket().host(container.getHost())
                        .port(container.getMappedPort(RSOCKET_PORT)).withUnsecureSSL().build();
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to build insecure SSL context for RSocket TLS test", e);
            }
        }

        @Test
        @DisplayName("returns PERMIT when policy matches over RSocket TLS via shared SSL bundle")
        void whenRsocketTlsAndPolicyMatchesThenPermit() {
            try (val container = createRSocketTlsContainer()) {
                container.start();
                expectDecision(connectRsocketTls(container), PERMIT_SUBSCRIPTION, AuthorizationDecision.PERMIT);
            }
        }

        @Test
        @DisplayName("returns DENY when policy does not match over RSocket TLS")
        void whenRsocketTlsAndPolicyDoesNotMatchThenDeny() {
            try (val container = createRSocketTlsContainer()) {
                container.start();
                expectDecision(connectRsocketTls(container), DENY_SUBSCRIPTION, AuthorizationDecision.DENY);
            }
        }

        @Test
        @DisplayName("rejects plain-TCP connection when RSocket server requires TLS")
        void whenPlainConnectionAgainstTlsServerThenIndeterminate() {
            try (val container = createRSocketTlsContainer()) {
                container.start();
                expectDecision(connectNoAuth(container), PERMIT_SUBSCRIPTION, AuthorizationDecision.INDETERMINATE);
            }
        }
    }

}
