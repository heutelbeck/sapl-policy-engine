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

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Integration tests for SAPL Node with Directory configuration source.
 * <p>
 * IT-001: Validates single-PDP directory source with various authentication
 * methods.
 */
@Testcontainers
@DisplayName("Directory Configuration Source Integration Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DirectorySourceIT extends BaseIntegrationTest {

    private static final String POLICIES_PATH = "it/policies/single-pdp/";

    private static final String                    BASIC_USERNAME       = "mpI3KjU7n1";
    private static final String                    BASIC_SECRET         = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
    private static final String                    BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";
    private static final String                    API_KEY              = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String                    API_KEY_ENCODED      = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String                    DEFAULT_PDP_ID       = "default";
    private static final AuthorizationSubscription PERMIT_SUBSCRIPTION  = AuthorizationSubscription.of("Willi", "eat",
            "apple");

    @Nested
    @DisplayName("No Authentication")
    class NoAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT when policy matches and no auth required")
        void whenNoAuthRequiredAndPolicyMatchesThenReturnsPermit() {
            try (val container = createSaplNodeContainerWithoutTls(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH",
                    "true")) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container)).build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify();
            }
        }

        @Test
        @DisplayName("returns DENY when policy does not match and no auth required")
        void whenNoAuthRequiredAndPolicyDoesNotMatchThenReturnsDeny() {
            try (val container = createSaplNodeContainerWithoutTls(POLICIES_PATH).withEnv("IO_SAPL_NODE_ALLOWNOAUTH",
                    "true")) {
                container.start();

                val pdp                  = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val nonMatchSubscription = AuthorizationSubscription.of("Nobody", "destroy", "everything");

                StepVerifier.create(pdp.decide(nonMatchSubscription)).expectNext(AuthorizationDecision.DENY)
                        .thenCancel().verify();
            }
        }

    }

    @Nested
    @DisplayName("Basic Authentication")
    class BasicAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT when valid credentials provided")
        void whenValidBasicCredentialsThenReturnsPermit() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(POLICIES_PATH)
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify();
            }
        }

        @Test
        @DisplayName("returns INDETERMINATE when invalid credentials provided")
        void whenInvalidBasicCredentialsThenReturnsIndeterminate() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(POLICIES_PATH)
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-basic-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, "wrongPassword").withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.INDETERMINATE)
                        .thenCancel().verify();
            }
        }

    }

    @Nested
    @DisplayName("API Key Authentication")
    class ApiKeyAuthenticationTests {

        @Test
        @DisplayName("returns PERMIT when valid API key provided")
        void whenValidApiKeyThenReturnsPermit() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(POLICIES_PATH)
                    .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-apikey-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", API_KEY_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container)).apiKey(API_KEY)
                        .withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify();
            }
        }

    }

    @Nested
    @DisplayName("PDP ID Routing")
    class PdpIdRoutingTests {

        @Test
        @DisplayName("uses correct pdpId from user configuration")
        void whenUserHasPdpIdConfiguredThenUsesCorrectPdp() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(POLICIES_PATH)
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true").withEnv("IO_SAPL_NODE_USERS_0_ID", "test-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).withUnsecureSSL().build();

                val decision = pdp.decide(PERMIT_SUBSCRIPTION).blockFirst();

                assertThat(decision).isNotNull();
                assertThat(decision.decision()).isEqualTo(AuthorizationDecision.PERMIT.decision());
            }
        }

        @Test
        @DisplayName("uses defaultPdpId when user has no explicit pdpId")
        void whenUserHasNoPdpIdThenUsesDefault() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(POLICIES_PATH)
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true").withEnv("IO_SAPL_NODE_DEFAULTPDPID", DEFAULT_PDP_ID)
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-client-no-pdpid")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify();
            }
        }

    }

}
