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
package io.sapl.node.it.examples;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
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
 * Smoke tests for the Docker Keycloak OAuth2 example
 * ({@code examples/docker/with-keycloak/}).
 * <p>
 * Starts a real Keycloak container with the example realm-export.json, then a
 * sapl-node container on the same Docker network. Acquires tokens via Resource
 * Owner Password grant and validates authorization decisions. Policy:
 * oauth2-permit-read (action==read AND resource==document). Algorithm:
 * PRIORITY_PERMIT, default DENY.
 */
@Testcontainers
@DisplayName("Keycloak OAuth2 Example Smoke Tests")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KeycloakOAuth2ExampleIT extends BaseIntegrationTest {

    private static final Path   POLICIES_DIR   = Path.of("examples/docker/with-keycloak/policies").toAbsolutePath();
    private static final Path   REALM_EXPORT   = Path.of("examples/docker/with-keycloak/keycloak/realm-export.json")
            .toAbsolutePath();
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:25.0";

    @Test
    @DisplayName("default-user reading document is permitted via oauth2-permit-read policy")
    void whenDefaultUserReadsDocumentThenPermit() {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val token        = acquireToken(keycloak, "default-user", "default123");
                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode))
                        .apiKey(token).build();
                val subscription = AuthorizationSubscription.of("user", "read", "document");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }
    }

    @Test
    @DisplayName("default-user deleting secret is denied (no matching policy, default DENY)")
    void whenDefaultUserDeletesSecretThenDeny() {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val token        = acquireToken(keycloak, "default-user", "default123");
                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode))
                        .apiKey(token).build();
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
        return createSaplNodeContainer().withNetwork(network)
                .withFileSystemBind(POLICIES_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "true").withEnv("IO_SAPL_NODE_OAUTH_PDPIDCLAIM", "sapl_pdp_id")
                .withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false").withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default")
                .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI", "http://keycloak:8080/realms/sapl-demo")
                .withEnv("SERVER_SSL_ENABLED", "false");
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
