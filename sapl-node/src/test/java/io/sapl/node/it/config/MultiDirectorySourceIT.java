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
 * Integration tests for SAPL Node with MultiDirectory configuration source.
 * <p>
 * IT-002: Validates multi-tenant routing where each subdirectory represents a
 * tenant with its own policies.
 */
@Testcontainers
@DisplayName("MultiDirectory Configuration Source Integration Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MultiDirectorySourceIT extends BaseIntegrationTest {

    private static final String MULTI_TENANT_POLICIES_PATH = "it/policies/multi-tenant/";

    private static final String PROD_USERNAME       = "prodUser123";
    private static final String PROD_SECRET         = "prodSecret!@#456SecurePass";
    private static final String PROD_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$w2qgxgZKruvqfUrnZkEmHA$tuqbaTgGKa3s1i9clCQZoJ8AJzyIG/DnA8lvq21G8UE";

    private static final String STAGING_USERNAME       = "stagingUser456";
    private static final String STAGING_SECRET         = "stagingSecret!@#789SecurePass";
    private static final String STAGING_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$Yr2cKJ/SlWNq7qa3Dzmc/A$iynfF89Dav4Vg2NKRctLFXgw+kzmWkkUGo6oHyXf4I0";

    private static final AuthorizationSubscription TEST_SUBSCRIPTION = AuthorizationSubscription.of("TestUser", "read",
            "data");

    @Nested
    @DisplayName("Multi-Tenant Routing")
    class MultiTenantRoutingTests {

        @Test
        @DisplayName("production user gets DENY from strict policy")
        void whenProductionUserRequestsThenReturnsDeny() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(MULTI_TENANT_POLICIES_PATH)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "production-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", PROD_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", PROD_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(PROD_USERNAME, PROD_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify();
            }
        }

        @Test
        @DisplayName("staging user gets PERMIT from permissive policy")
        void whenStagingUserRequestsThenReturnsPermit() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(MULTI_TENANT_POLICIES_PATH)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true").withEnv("IO_SAPL_NODE_USERS_0_ID", "staging-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "staging")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", STAGING_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", STAGING_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(STAGING_USERNAME, STAGING_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify();
            }
        }

        @Test
        @DisplayName("same request returns different decisions based on tenant")
        void whenSameRequestFromDifferentTenantsThenReturnsDifferentDecisions() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(MULTI_TENANT_POLICIES_PATH)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "production-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", PROD_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", PROD_SECRET_ENCODED)
                    .withEnv("IO_SAPL_NODE_USERS_1_ID", "staging-client")
                    .withEnv("IO_SAPL_NODE_USERS_1_PDPID", "staging")
                    .withEnv("IO_SAPL_NODE_USERS_1_BASIC_USERNAME", STAGING_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_1_BASIC_SECRET", STAGING_SECRET_ENCODED)) {
                container.start();

                val prodPdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(PROD_USERNAME, PROD_SECRET).withUnsecureSSL().build();

                val stagingPdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(STAGING_USERNAME, STAGING_SECRET).withUnsecureSSL().build();

                StepVerifier.create(prodPdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY)
                        .thenCancel().verify();

                StepVerifier.create(stagingPdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.PERMIT)
                        .thenCancel().verify();
            }
        }

    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("user cannot access policies from other tenant")
        void whenUserAccessesOtherTenantPoliciesThenDoesNotGetTheirDecision() throws SSLException {
            try (val container = createSaplNodeContainerWithTls(MULTI_TENANT_POLICIES_PATH)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                    .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "production-client")
                    .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", PROD_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", PROD_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(PROD_USERNAME, PROD_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNextMatches(decision -> {
                    // Production tenant should get DENY (from strict policy)
                    // NOT PERMIT (from staging's permissive policy)
                    return decision.decision() == AuthorizationDecision.DENY.decision();
                }).thenCancel().verify();
            }
        }

    }

}
