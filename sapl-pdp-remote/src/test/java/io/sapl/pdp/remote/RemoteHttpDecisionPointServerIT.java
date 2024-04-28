/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
@DirtiesContext
@Testcontainers
@SpringBootTest
@ActiveProfiles(profiles = "quiet")
class RemoteHttpDecisionPointServerIT {
    private static final int    SAPL_SERVER_PORT = 8443;
    private static final String SAPL_SERVER_LT   = "ghcr.io/heutelbeck/sapl-server-lt:3.0.0-SNAPSHOT";

    final AuthorizationSubscription permittedSubscription = AuthorizationSubscription.of("Willi", "eat", "apple");

    final AuthorizationSubscription deniedSubscription = AuthorizationSubscription.of("Willi", "eat", "ice");

    @SpringBootConfiguration
    static class TestConfiguration {
    }

    private void requestDecision(PolicyDecisionPoint pdp) {
        StepVerifier.create(pdp.decide(permittedSubscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                .verify();
        StepVerifier.create(pdp.decide(deniedSubscription)).expectNext(AuthorizationDecision.NOT_APPLICABLE)
                .thenCancel().verify();
    }

    // HTTP Protocol
    @Test
    void whenRequestingDecisionFromHttpPdp_withNoAuth_thenDecisionIsProvided() {
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
        // @formatter:off
                var container = baseContainer.withImagePullPolicy(PullPolicy.neverPull())
                        .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                        .withEnv("io_sapl_server-lt_allowNoAuth", "true")
                        .withEnv("spring_rsocket_server_ssl_enabled", "false")
                        .withEnv("server_ssl_enabled", "false")
                        .withEnv("io_sapl_pdp_embedded_print-trace","true")
                        .withEnv("io_sapl_pdp_embedded_print-text-report","true")
                        .withExposedPorts(SAPL_SERVER_PORT)
                        .waitingFor(Wait.forLogMessage(".*Started SAPLServerLTApplication.*\\n", 1))) {
        // @formatter:on
            container.start();
            log.debug("connecting to: " + "http://" + container.getHost() + ":"
                    + container.getMappedPort(SAPL_SERVER_PORT));
            var pdp = RemotePolicyDecisionPoint.builder().http()
                    .baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT)).build();
            requestDecision(pdp);
        }
    }

    // HTTPS Protocol
    @Test
    void whenRequestingDecisionFromHttpsPdp_withNoAuth_thenDecisionIsProvided() throws SSLException {
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
                var container = saplServerWithTls(baseContainer).withEnv("io_sapl_server-lt_allowNoAuth", "true")) {
            container.start();
            var pdp = RemotePolicyDecisionPoint.builder().http()
                    .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT))
                    .withUnsecureSSL().build();
            requestDecision(pdp);
            container.stop();
        }
    }

    private GenericContainer<?> saplServerWithTls(GenericContainer<?> baseContainer) {
        // @formatter:off
        return baseContainer.withImagePullPolicy(PullPolicy.neverPull())
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withClasspathResourceMapping("keystore.p12", "/pdp/data/keystore.p12", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_PORT)
                .waitingFor(Wait.forLogMessage(".*Started SAPLServerLTApplication.*\\n", 1))
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data")
                .withEnv("spring_rsocket_server_address", "0.0.0.0")
                .withEnv("spring_rsocket_server_ssl_key-store-type", "PKCS12")
                .withEnv("spring_rsocket_server_ssl_key-store", "/pdp/data/keystore.p12")
                .withEnv("spring_rsocket_server_ssl_key-store-password", "changeme")
                .withEnv("spring_rsocket_server_ssl_key-password", "changeme")
                .withEnv("spring_rsocket_server_ssl_key-alias", "netty")
                .withEnv("server_ssl_key-store-type", "PKCS12")
                .withEnv("server_ssl_key-store", "/pdp/data/keystore.p12")
                .withEnv("server_ssl_key-store-password", "changeme")
                .withEnv("server_ssl_key-password", "changeme")
                .withEnv("server_ssl_key-alias", "netty");
        // @formatter:on
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withBasicAuth_thenDecisionIsProvided() throws SSLException {
        var key           = "mpI3KjU7n1";
        var secret        = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
        var encodedSecret = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
                var container = saplServerWithTls(baseContainer).withEnv("io_sapl_server-lt_allowNoAuth", "true")
                        .withEnv("io_sapl_server-lt_allowBasicAuth", "true").withEnv("io_sapl_server-lt_key", key)
                        .withEnv("io_sapl_server-lt_secret", encodedSecret)) {
            container.start();
            var pdp = RemotePolicyDecisionPoint.builder().http()
                    .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT))
                    .basicAuth(key, secret).withUnsecureSSL().build();
            requestDecision(pdp);
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withInvalidBasicAuth_thenIndeterminateDecisionIsProvided() throws SSLException {
        var key           = "mpI3KjU7n1";
        var secret = "incalidSecret";
        var encodedSecret = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
             var container = saplServerWithTls(baseContainer).withEnv("io_sapl_server-lt_allowNoAuth", "true")
                     .withEnv("io_sapl_server-lt_allowBasicAuth", "true").withEnv("io_sapl_server-lt_key", key)
                     .withEnv("io_sapl_server-lt_secret", encodedSecret)) {
            container.start();
            var pdp = RemotePolicyDecisionPoint.builder().http()
                    .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT))
                    .basicAuth(key, secret).withUnsecureSSL().build();
            StepVerifier.create(pdp.decide(permittedSubscription)).expectNext(AuthorizationDecision.INDETERMINATE).thenCancel()
                    .verify();
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withApiKeyAuth_thenDecisionIsProvided() throws SSLException {
        var SAPL_API_KEY = "abD12344cdefDuwg8721abD12344cdefDuwg8721";
        try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
                var container = saplServerWithTls(baseContainer).withEnv("io_sapl_server-lt_allowApiKeyAuth", "true")
                        .withEnv("io_sapl_server-lt_allowedApiKeys", SAPL_API_KEY)) {
            container.start();
            var pdp = RemotePolicyDecisionPoint.builder().http()
                    .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT))
                    .apiKey(SAPL_API_KEY).withUnsecureSSL().build();
            requestDecision(pdp);
            container.stop();
        }
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withOauth2Auth_thenDecisionIsProvided() throws SSLException {
        try (var oauthBaseContainer = new GenericContainer<>(
                DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:2.1.0"));
                var oauth2Container = oauthBaseContainer.withExposedPorts(8080).waitingFor(Wait.forListeningPort())) {
            oauth2Container.start();

            try (var baseContainer = new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_LT));
                    var container = saplServerWithTls(baseContainer)
                            .withEnv("io_sapl_server-lt_allowOauth2Auth", "True")
                            .withExtraHost("auth-host", "host-gateway")
                            .withEnv("spring_security_oauth2_resourceserver_jwt_issuer-uri",
                                    "http://auth-host:" + oauth2Container.getMappedPort(8080) + "/default")) {
                container.start();

                var clientRegistrationRepository = new ReactiveClientRegistrationRepository() {
                                                     @Override
                                                     public Mono<ClientRegistration> findByRegistrationId(
                                                             String registrationId) {
                                                         return Mono
                                                                 .just(ClientRegistration.withRegistrationId("saplPdp")
                                                                         .tokenUri("http://auth-host:"
                                                                                 + oauth2Container.getMappedPort(8080)
                                                                                 + "/default/token")
                                                                         .clientId("0oa62xybztegSdqtZ5d7")
                                                                         .clientSecret(
                                                                                 "v6WUqDre1B4WMejey-6sklb5kZW7C5RB2iftv_sq")
                                                                         .authorizationGrantType(
                                                                                 AuthorizationGrantType.CLIENT_CREDENTIALS)
                                                                         .scope("sapl").build());
                                                     }
                                                 };
                var pdp                          = RemotePolicyDecisionPoint.builder().http()
                        .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT))
                        .withUnsecureSSL().oauth2(clientRegistrationRepository, "saplPdp").build();
                requestDecision(pdp);
                oauth2Container.stop();
                container.stop();
            }
        }
    }
}
