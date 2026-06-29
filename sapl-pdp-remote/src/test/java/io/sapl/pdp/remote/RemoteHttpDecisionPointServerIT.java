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
package io.sapl.pdp.remote;

import static org.awaitility.Awaitility.await;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.net.URLEncoder;

import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Timeout(value = 5, unit = TimeUnit.MINUTES)
@DirtiesContext
@Testcontainers
@SpringBootTest
@ActiveProfiles(profiles = "quiet")
class RemoteHttpDecisionPointServerIT {
    private static final int             SAPL_SERVER_PORT  = 8080;
    private static final String          SAPL_SERVER_IMAGE = System.getProperty("sapl.node.image",
            "ghcr.io/heutelbeck/sapl-node:4.1.2");
    private static final ImagePullPolicy NEVER_PULL        = imageName -> false;
    private static final String          OAUTH2_ISSUER_ID  = "default";
    private static final String          OAUTH2_CLIENT_ID  = "0oa62xybztegSdqtZ5d7";
    private static final String          OAUTH2_SECRET     = "v6WUqDre1B4WMejey-6sklb5kZW7C5RB2iftv_sq";
    private static final String          OAUTH2_SCOPE      = "sapl";
    private static final JsonMapper      JSON_MAPPER       = JsonMapper.builder().build();

    final AuthorizationSubscription permittedSubscription = AuthorizationSubscription.of("Willi", "eat", "apple");

    @SpringBootConfiguration
    static class TestConfiguration {
    }

    private void requestDecision(ReactivePolicyDecisionPoint pdp) {
        StepVerifier.create(pdp.decide(permittedSubscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                .verify(Duration.ofSeconds(45));
    }

    private static final String WARMUP_BODY = "{\"subject\":\"_\",\"action\":\"_\",\"resource\":\"_\"}";

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Test-only trust-all manager. Every client certificate is accepted.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Test-only trust-all manager. The node's self-signed certificate is accepted.
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    /**
     * Issues one raw decision request with a generous timeout to absorb the node's
     * cold-start cost.
     * Any HTTP response counts as warm. A transport error or timeout fails the
     * test.
     */
    private void warmUpServer(String baseUrl, String authorizationHeader) {
        sendWarmUpRequest(baseUrl, authorizationHeader, false);
    }

    private void warmUpAuthenticatedServer(String baseUrl, GenericContainer<?> oauth2Container) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            val token = fetchAccessToken(oauth2TokenUri(oauth2Container));
            sendWarmUpRequest(baseUrl, "Bearer " + token, true);
        });
    }

    private void sendWarmUpRequest(String baseUrl, String authorizationHeader, boolean requireSuccess) {
        try {
            val sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { TRUST_ALL }, null);
            val client  = HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(30)).build();
            val request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/pdp/decide-once"))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(WARMUP_BODY));
            if (authorizationHeader != null) {
                request.header("Authorization", authorizationHeader);
            }
            val response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (requireSuccess && response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Server warm-up failed with HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Server warm-up precondition failed for " + baseUrl, e);
        }
    }

    private static String basicAuthHeader(String username, String secret) {
        return "Basic "
                + Base64.getEncoder().encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static void waitForOauth2Server(GenericContainer<?> oauth2Container) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            requireHttpOk(oauth2IssuerHostUrl(oauth2Container) + "/.well-known/openid-configuration");
            fetchAccessToken(oauth2TokenUri(oauth2Container));
        });
    }

    private static void requireHttpOk(String url) {
        try {
            val request  = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request,
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new AssertionError(
                        "OAuth2 readiness probe failed with HTTP " + response.statusCode() + " for " + url);
            }
        } catch (IOException e) {
            throw new AssertionError("OAuth2 readiness probe failed for " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("OAuth2 readiness probe interrupted for " + url, e);
        }
    }

    private static String fetchAccessToken(String tokenUri) {
        try {
            val form     = "grant_type=client_credentials&client_id=" + urlEncode(OAUTH2_CLIENT_ID) + "&client_secret="
                    + urlEncode(OAUTH2_SECRET) + "&scope=" + urlEncode(OAUTH2_SCOPE);
            val request  = HttpRequest.newBuilder(URI.create(tokenUri)).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AssertionError(
                        "OAuth2 token probe failed with HTTP " + response.statusCode() + " for " + tokenUri);
            }
            val accessTokenNode = JSON_MAPPER.readTree(response.body()).get("access_token");
            if (accessTokenNode == null) {
                throw new AssertionError("OAuth2 token probe returned no access_token");
            }
            val accessToken = accessTokenNode.asString();
            if (accessToken == null || accessToken.isBlank()) {
                throw new AssertionError("OAuth2 token probe returned no access_token");
            }
            return accessToken;
        } catch (IOException e) {
            throw new AssertionError("OAuth2 token probe failed for " + tokenUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("OAuth2 token probe interrupted for " + tokenUri, e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String oauth2IssuerHostUrl(GenericContainer<?> oauth2Container) {
        return "http://" + oauth2Container.getHost() + ":" + oauth2Container.getMappedPort(8080) + "/"
                + OAUTH2_ISSUER_ID;
    }

    private static String oauth2TokenUri(GenericContainer<?> oauth2Container) {
        return oauth2IssuerHostUrl(oauth2Container) + "/token";
    }

    private static String oauth2JsonConfig(String issuerUrl) {
        return """
                {"interactiveLogin":false,"tokenCallbacks":[{"issuerId":"%s","requestMappings":[{"requestParam":"grant_type","match":"client_credentials","claims":{"iss":"%s"}}]}]}
                """
                .formatted(OAUTH2_ISSUER_ID, issuerUrl);
    }

    // HTTP Protocol
    @Test
    void whenRequestingDecisionFromHttpPdpWithNoAuthThenDecisionIsProvided() {
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
        // @formatter:off
                val container = baseContainer.withImagePullPolicy(NEVER_PULL)
                        .withClasspathResourceMapping("policies/", "/pdp/data/", BindMode.READ_ONLY)
                        .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "DIRECTORY")
                        .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                        .withEnv("SERVER_SSL_ENABLED", "false")
                        .withEnv("SERVER_ADDRESS", "0.0.0.0")
                        .withEnv("IO_SAPL_PDP_EMBEDDED_PRINTTRACE","true")
                        .withEnv("IO_SAPL_PDP_EMBEDDED_PRINTTEXTREPORT","true")
                        .withExposedPorts(SAPL_SERVER_PORT)
                        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("sapl-node"))
                        .waitingFor(new WaitAllStrategy()
                                .withStrategy(Wait.forLogMessage(".*SAPL Node ready.*\\n", 1))
                                .withStrategy(Wait.forListeningPort())
                                .withStartupTimeout(Duration.ofMinutes(2)))) {
        // @formatter:on
            container.start();
            val baseUrl = "http://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
            warmUpServer(baseUrl, null);
            val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl).build();
            requestDecision(pdp);
        }
    }

    // HTTPS Protocol
    @Test
    void whenRequestingDecisionFromHttpsPdpWithNoAuthThenDecisionIsProvided() throws SSLException {
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
                val container = saplServerWithTls(baseContainer).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")) {
            container.start();
            val baseUrl = "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
            warmUpServer(baseUrl, null);
            val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl).withUnsecureSSL().build();
            requestDecision(pdp);
            container.stop();
        }
    }

    private GenericContainer<?> saplServerWithTls(GenericContainer<?> baseContainer) {
        // @formatter:off
        return baseContainer.withImagePullPolicy(NEVER_PULL)
                .withClasspathResourceMapping("policies/", "/pdp/data/", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_PORT)
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("sapl-node"))
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forLogMessage(".*SAPL Node ready.*\\n", 1))
                        .withStrategy(Wait.forListeningPort())
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "DIRECTORY")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                .withEnv("SERVER_ADDRESS", "0.0.0.0")
                .withEnv("SERVER_SSL_ENABLED", "true")
                .withEnv("SERVER_SSL_KEYSTORETYPE", "PKCS12")
                .withEnv("SERVER_SSL_KEYSTORE", "/pdp/data/keystore.p12")
                .withEnv("SERVER_SSL_KEYSTOREPASSWORD", "changeme")
                .withEnv("SERVER_SSL_KEYPASSWORD", "changeme")
                .withEnv("SERVER_SSL_KEYALIAS", "netty");
        // @formatter:on
    }

    @Test
    void whenRequestingDecisionFromHttpsPdpWithBasicAuthThenDecisionIsProvided() throws SSLException {
        val username      = "mpI3KjU7n1";
        val secret        = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
        val encodedSecret = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
                val container = saplServerWithTls(baseContainer).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                        .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                        .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                        .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                        .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", username)
                        .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", encodedSecret)) {
            container.start();
            val baseUrl = "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
            warmUpServer(baseUrl, basicAuthHeader(username, secret));
            val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl).basicAuth(username, secret)
                    .withUnsecureSSL().build();
            requestDecision(pdp);
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdpWithInvalidBasicAuthThenIndeterminateDecisionIsProvided()
            throws SSLException {
        val username      = "mpI3KjU7n1";
        val secret        = "invalidSecret";
        val encodedSecret = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
                val container = saplServerWithTls(baseContainer).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                        .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                        .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                        .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                        .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", username)
                        .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", encodedSecret)) {
            container.start();
            val baseUrl = "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
            warmUpServer(baseUrl, basicAuthHeader(username, secret));
            val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl).basicAuth(username, secret)
                    .withUnsecureSSL().build();
            StepVerifier.create(pdp.decide(permittedSubscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                    .thenCancel().verify(Duration.ofSeconds(30));
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdpWithApiKeyAuthThenDecisionIsProvided() throws SSLException {
        val apiKey        = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
        val encodedApiKey = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
                val container = saplServerWithTls(baseContainer).withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                        .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-apikey-client")
                        .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                        .withEnv("IO_SAPL_NODE_USERS_0_APIKEYID", "7A7ByyQd6U")
                        .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", encodedApiKey)) {
            container.start();
            val baseUrl = "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
            warmUpServer(baseUrl, "Bearer " + apiKey);
            val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl).apiKey(apiKey).withUnsecureSSL()
                    .build();
            requestDecision(pdp);
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdpWithOauth2AuthThenDecisionIsProvided() throws SSLException {
        val issuerUrl = "http://auth-host:8080/" + OAUTH2_ISSUER_ID;
        try (var network = Network.newNetwork();
                var oauthBaseContainer = new GenericContainer<>(
                        DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:2.1.0"));
                val oauth2Container = oauthBaseContainer.withNetwork(network).withNetworkAliases("auth-host")
                        .withExposedPorts(8080).withEnv("JSON_CONFIG", oauth2JsonConfig(issuerUrl))
                        .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))) {
            oauth2Container.start();
            waitForOauth2Server(oauth2Container);

            try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE));
                    val container = saplServerWithTls(baseContainer).withNetwork(network)
                            .withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "true")
                            .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI",
                                    "http://auth-host:8080/default")) {
                container.start();
                val baseUrl = "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
                warmUpAuthenticatedServer(baseUrl, oauth2Container);

                val clientRegistrationRepository = new ReactiveClientRegistrationRepository() {
                                                     @Override
                                                     public Mono<ClientRegistration> findByRegistrationId(
                                                             String registrationId) {
                                                         return Mono.just(ClientRegistration
                                                                 .withRegistrationId("saplPdp")
                                                                 .tokenUri(oauth2TokenUri(oauth2Container))
                                                                 .clientId(OAUTH2_CLIENT_ID).clientSecret(OAUTH2_SECRET)
                                                                 .authorizationGrantType(
                                                                         AuthorizationGrantType.CLIENT_CREDENTIALS)
                                                                 .scope(OAUTH2_SCOPE).build());
                                                     }
                                                 };
                val pdp                          = RemotePolicyDecisionPoint.builder().http().baseUrl(baseUrl)
                        .withUnsecureSSL().oauth2(clientRegistrationRepository, "saplPdp").build();
                requestDecision(pdp);
                oauth2Container.stop();
                container.stop();
            }
        }
    }
}
