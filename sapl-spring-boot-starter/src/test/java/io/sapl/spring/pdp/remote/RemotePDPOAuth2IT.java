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
package io.sapl.spring.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Integration tests for OAuth2 client_credentials authentication through the
 * starter on both HTTP and RSocket transports. Starts a Keycloak container with
 * a service-account client and a SAPL Node container configured for OAuth2 JWT
 * resource-server validation; the autowired remote PDP authenticates by
 * minting a JWT via Spring's {@code OAuth2AuthorizedClientManager}.
 */
@Testcontainers
@DisplayName("RemotePDP Starter OAuth2 Integration Tests")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@TestInstance(Lifecycle.PER_CLASS)
class RemotePDPOAuth2IT {

    private static final int             RSOCKET_PORT      = 7000;
    private static final int             HTTP_PORT         = 8080;
    private static final String          SAPL_SERVER_IMAGE = "ghcr.io/heutelbeck/sapl-node:4.1.0-SNAPSHOT";
    private static final String          KEYCLOAK_IMAGE    = "quay.io/keycloak/keycloak:25.0";
    private static final String          REALM             = "sapl-it";
    private static final String          CLIENT_ID         = "sapl-pdp-client";
    private static final String          CLIENT_SECRET     = "sapl-pdp-secret";
    private static final String          REGISTRATION_ID   = "sapl-pdp";
    private static final String          POLICIES_PATH     = "policies-rsocket/";
    private static final ImagePullPolicy NEVER_PULL        = imageName -> false;
    private static final Duration        STARTUP           = Duration.ofMinutes(2);
    private static final String          STARTUP_LOG       = ".*SAPL Node ready.*\\n";
    private static final Duration        STEP_TIMEOUT      = Duration.ofSeconds(45);

    private static final AuthorizationSubscription PERMIT_SUBSCRIPTION = AuthorizationSubscription.of("Willi", "eat",
            "apple");

    private Network network;

    private KeycloakContainer keycloak;

    private GenericContainer<?> saplNode;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(RemotePDPAutoConfiguration.class, ReactiveOAuth2ClientAutoConfiguration.class));

    @BeforeAll
    void startContainers() {
        network  = Network.newNetwork();
        keycloak = new KeycloakContainer(KEYCLOAK_IMAGE).withNetwork(network).withNetworkAliases("keycloak")
                .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak-realm-oauth2-it.json"),
                        "/opt/keycloak/data/import/realm-export.json")
                .withEnv("KC_HOSTNAME", "http://keycloak:8080").withEnv("KC_HOSTNAME_STRICT_BACKCHANNEL", "false")
                .withStartupTimeout(Duration.ofMinutes(5));
        keycloak.start();

        saplNode = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE)).withImagePullPolicy(NEVER_PULL)
                .withNetwork(network).withNetworkAliases("sapl-node").withExposedPorts(HTTP_PORT, RSOCKET_PORT)
                .withEnv("SERVER_ADDRESS", "0.0.0.0").withEnv("SERVER_PORT", String.valueOf(HTTP_PORT))
                .withEnv("SERVER_SSL_ENABLED", "false").withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "DIRECTORY")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                .withClasspathResourceMapping(POLICIES_PATH, "/pdp/data/",
                        org.testcontainers.containers.BindMode.READ_ONLY)
                .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT))
                .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "true")
                .withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false").withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default")
                .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI", "http://keycloak:8080/realms/" + REALM)
                .waitingFor(Wait.forLogMessage(STARTUP_LOG, 1).withStartupTimeout(STARTUP));
        saplNode.start();
    }

    @AfterAll
    void stopContainers() {
        if (saplNode != null) {
            saplNode.stop();
        }
        if (keycloak != null) {
            keycloak.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    private String[] oauth2Properties(String transportType, int port) {
        val tokenUri = keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token";
        return new String[] { "io.sapl.pdp.remote.enabled=true", "io.sapl.pdp.remote.type=" + transportType,
                "io.sapl.pdp.remote.host=" + ("http".equals(transportType) ? "http://" + saplNode.getHost() + ":" + port
                        : saplNode.getHost()),
                "io.sapl.pdp.remote.port=" + port,
                "io.sapl.pdp.remote.oauth2.client-registration-id=" + REGISTRATION_ID,
                "spring.security.oauth2.client.registration." + REGISTRATION_ID + ".client-id=" + CLIENT_ID,
                "spring.security.oauth2.client.registration." + REGISTRATION_ID + ".client-secret=" + CLIENT_SECRET,
                "spring.security.oauth2.client.registration." + REGISTRATION_ID
                        + ".authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.provider." + REGISTRATION_ID + ".token-uri=" + tokenUri };
    }

    private void runWithPdp(String[] properties, AuthorizationDecision expected) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            val pdp = context.getBean(ReactivePolicyDecisionPoint.class);
            StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(expected).thenCancel().verify(STEP_TIMEOUT);
        });
    }

    @Nested
    @DisplayName("HTTP with OAuth2 client_credentials")
    class HttpTests {

        @Test
        @DisplayName("autowires bean and returns PERMIT over HTTP with minted JWT")
        void whenHttpOauth2AndPolicyMatchesThenPermit() {
            val port       = saplNode.getMappedPort(HTTP_PORT);
            val properties = oauth2Properties("http", port);
            runWithPdp(properties, AuthorizationDecision.PERMIT);
        }
    }

    @Nested
    @DisplayName("RSocket with OAuth2 client_credentials")
    class RSocketTests {

        @Test
        @DisplayName("autowires bean and returns PERMIT over RSocket with minted JWT in setup frame")
        void whenRSocketOauth2AndPolicyMatchesThenPermit() {
            val port       = saplNode.getMappedPort(RSOCKET_PORT);
            val properties = oauth2Properties("rsocket", port);
            runWithPdp(properties, AuthorizationDecision.PERMIT);
        }
    }
}
