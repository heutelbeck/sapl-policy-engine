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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for SAPL Node with Remote Bundle configuration source.
 * <p>
 * A WireMock container serves bundles over HTTP on a shared Docker network.
 * The SAPL Node container fetches bundles via the {@code REMOTE_BUNDLES}
 * configuration source. Bundles are created programmatically using
 * {@link BundleBuilder} for transparent test intent.
 */
@Testcontainers
@DisplayName("Remote Bundle Source Integration Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class RemoteBundleSourceIT extends BaseIntegrationTest {

    private static final String WIREMOCK_IMAGE      = "wiremock/wiremock:3.12.0";
    private static final int    WIREMOCK_PORT       = 8080;
    private static final String BUNDLE_SERVER_ALIAS = "bundle-server";
    private static final String DEFAULT_PDP_ID      = "default";

    private static final String PERMIT_POLICY = """
            policy "permit-all"
            permit true;
            """;

    private static final String DENY_POLICY = """
            policy "deny-all"
            deny true;
            """;

    private static final AuthorizationSubscription TEST_SUBSCRIPTION = AuthorizationSubscription.of("user", "read",
            "document");

    @Nested
    @DisplayName("Bootstrap")
    class BootstrapTests {

        @Test
        @DisplayName("fetches remote bundle at startup and serves correct decisions")
        void whenNodeStartsWithRemoteBundleThenServesPermit() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("permit-all", PERMIT_POLICY).build();

            try (val network = Network.newNetwork(); val wiremock = createWireMockContainer(network)) {
                wiremock.start();
                configureBundleStub(wiremock, DEFAULT_PDP_ID, bundle, "\"v1\"");

                try (val saplNode = createRemoteBundleNode(network)) {
                    saplNode.start();

                    val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode)).build();

                    StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                            .thenCancel().verify(Duration.ofSeconds(30));
                }
            }
        }

    }

    @Nested
    @DisplayName("Policy Hot-Reload via Polling")
    class HotReloadTests {

        @Test
        @DisplayName("streaming subscriber receives updated decision when bundle changes on server")
        void whenBundleChangesOnServerThenStreamingSubscriberGetsUpdate() {
            val denyBundle   = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("deny-all", DENY_POLICY).build();
            val permitBundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("permit-all", PERMIT_POLICY).build();

            try (val network = Network.newNetwork(); val wiremock = createWireMockContainer(network)) {
                wiremock.start();
                configureBundleStub(wiremock, DEFAULT_PDP_ID, denyBundle, "\"v1\"");

                try (val saplNode = createRemoteBundleNode(network)) {
                    saplNode.start();

                    val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode)).build();

                    StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY)
                            .then(() -> {
                                clearAllStubs(wiremock);
                                configureBundleStub(wiremock, DEFAULT_PDP_ID, permitBundle, "\"v2\"");
                            })
                            .expectNextMatches(
                                    decision -> decision.decision() == AuthorizationDecision.PERMIT.decision())
                            .thenCancel().verify(Duration.ofSeconds(60));
                }
            }
        }

    }

    @Nested
    @DisplayName("Error Resilience")
    class ErrorResilienceTests {

        @Test
        @DisplayName("continues serving last-known bundle when server becomes unavailable")
        void whenServerReturnsErrorsThenNodeContinuesWithLastKnownBundle() {
            val bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT)
                    .withPolicy("permit-all", PERMIT_POLICY).build();

            try (val network = Network.newNetwork(); val wiremock = createWireMockContainer(network)) {
                wiremock.start();
                configureBundleStub(wiremock, DEFAULT_PDP_ID, bundle, "\"v1\"");

                try (val saplNode = createRemoteBundleNode(network)) {
                    saplNode.start();

                    val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode)).build();

                    StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                            .thenCancel().verify(Duration.ofSeconds(30));

                    clearAllStubs(wiremock);
                    configureErrorStub(wiremock, DEFAULT_PDP_ID, 500);

                    await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(15))
                            .pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
                                val decision = pdp.decide(TEST_SUBSCRIPTION).blockFirst(Duration.ofSeconds(5));
                                assertThat(decision).isNotNull().extracting(AuthorizationDecision::decision)
                                        .isEqualTo(AuthorizationDecision.PERMIT.decision());
                            });
                }
            }
        }

    }

    private GenericContainer<?> createWireMockContainer(Network network) {
        return new GenericContainer<>(DockerImageName.parse(WIREMOCK_IMAGE)).withNetwork(network)
                .withNetworkAliases(BUNDLE_SERVER_ALIAS).withExposedPorts(WIREMOCK_PORT)
                .waitingFor(Wait.forHttp("/__admin/mappings").forPort(WIREMOCK_PORT).forStatusCode(200));
    }

    private GenericContainer<?> createRemoteBundleNode(Network network) {
        return createSaplNodeContainer().withNetwork(network)
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "REMOTE_BUNDLES")
                .withEnv("IO_SAPL_PDP_EMBEDDED_REMOTEBUNDLES_BASEURL",
                        "http://" + BUNDLE_SERVER_ALIAS + ":" + WIREMOCK_PORT + "/bundles")
                .withEnv("IO_SAPL_PDP_EMBEDDED_REMOTEBUNDLES_PDPIDS_0", DEFAULT_PDP_ID)
                .withEnv("IO_SAPL_PDP_EMBEDDED_REMOTEBUNDLES_POLLINTERVAL", "2s")
                .withEnv("IO_SAPL_PDP_EMBEDDED_REMOTEBUNDLES_FIRSTBACKOFF", "200ms")
                .withEnv("IO_SAPL_PDP_EMBEDDED_REMOTEBUNDLES_MAXBACKOFF", "1s")
                .withEnv("IO_SAPL_PDP_EMBEDDED_BUNDLESECURITY_ALLOWUNSIGNED", "true")
                .withEnv("IO_SAPL_PDP_EMBEDDED_BUNDLESECURITY_ACCEPTRISKS", "true")
                .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true").withEnv("SERVER_SSL_ENABLED", "false");
    }

    private void configureBundleStub(GenericContainer<?> wiremock, String pdpId, byte[] bundle, String etag) {
        val base64Bundle = Base64.getEncoder().encodeToString(bundle);
        val mapper       = JsonMapper.shared();
        val stubJson     = mapper.valueToTree(Map.of("request", Map.of("method", "GET", "urlPath", "/bundles/" + pdpId),
                "response", Map.of("status", 200, "base64Body", base64Bundle, "headers",
                        Map.of("Content-Type", "application/octet-stream", "ETag", etag))));

        wireMockPost(wiremock, "/__admin/mappings", stubJson.toString());
    }

    private void configureErrorStub(GenericContainer<?> wiremock, String pdpId, int statusCode) {
        val mapper   = JsonMapper.shared();
        val stubJson = mapper.valueToTree(Map.of("request", Map.of("method", "GET", "urlPath", "/bundles/" + pdpId),
                "response", Map.of("status", statusCode)));

        wireMockPost(wiremock, "/__admin/mappings", stubJson.toString());
    }

    private void clearAllStubs(GenericContainer<?> wiremock) {
        WebClient.create().delete().uri(wireMockAdminUrl(wiremock) + "/__admin/mappings").retrieve().toBodilessEntity()
                .block(Duration.ofSeconds(5));
    }

    private void wireMockPost(GenericContainer<?> wiremock, String path, String body) {
        WebClient.create().post().uri(wireMockAdminUrl(wiremock) + path).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).retrieve().toBodilessEntity().block(Duration.ofSeconds(5));
    }

    private String wireMockAdminUrl(GenericContainer<?> wiremock) {
        return "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(WIREMOCK_PORT);
    }

}
