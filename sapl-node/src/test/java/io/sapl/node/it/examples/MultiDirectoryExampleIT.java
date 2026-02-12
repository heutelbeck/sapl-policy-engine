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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Smoke tests for the local multi-directory example
 * ({@code examples/local/multidirectory/}).
 * <p>
 * Mounts the real example tenant directories into a sapl-node container
 * configured as MULTI_DIRECTORY with three tenants: default (permitall),
 * production (admin/read/deny-delete with PRIORITY_DENY), and staging
 * (permit-false with PRIORITY_PERMIT, default DENY).
 */
@Testcontainers
@DisplayName("Multi-Directory Example Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MultiDirectoryExampleIT extends BaseIntegrationTest {

    private static final Path TENANTS_DIR = Path.of("examples/local/multidirectory/tenants").toAbsolutePath();

    private static final String PRODUCTION_API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String PRODUCTION_API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

    private static final String STAGING_API_KEY         = "sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f";
    private static final String STAGING_API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$EATdeVYu9zNEnS6cnr8x+A$z+sdYbjvms6rFXhCJ6C5a3FtnKu0NBmMSAo9KVIZ42k";

    private GenericContainer<?> createExampleContainer() {
        return createSaplNodeContainer().withFileSystemBind(TENANTS_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true").withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false")
                .withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default").withEnv("IO_SAPL_NODE_USERS_0_ID", "production-client")
                .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", PRODUCTION_API_KEY_ENCODED)
                .withEnv("IO_SAPL_NODE_USERS_1_ID", "staging-client").withEnv("IO_SAPL_NODE_USERS_1_PDPID", "staging")
                .withEnv("IO_SAPL_NODE_USERS_1_APIKEY", STAGING_API_KEY_ENCODED).withEnv("SERVER_SSL_ENABLED", "false");
    }

    @Nested
    @DisplayName("Default Tenant (no-auth, permitall)")
    class DefaultTenantTests {

        @Test
        @DisplayName("permits any request via permitall policy")
        void whenAnyRequestThenPermit() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val subscription = AuthorizationSubscription.of("anyone", "anything", "anything");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("Production Tenant (PRIORITY_DENY)")
    class ProductionTenantTests {

        @Test
        @DisplayName("admin read is permitted by admin-access and read-access policies")
        void whenAdminReadsThenPermit() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(PRODUCTION_API_KEY).build();
                val subscription = AuthorizationSubscription.of("admin", "read", "data");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("admin delete is denied by strict-policy with PRIORITY_DENY")
        void whenAdminDeletesThenDeny() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(PRODUCTION_API_KEY).build();
                val subscription = AuthorizationSubscription.of("admin", "delete", "database");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("Staging Tenant (PRIORITY_PERMIT, permit-false)")
    class StagingTenantTests {

        @Test
        @DisplayName("alice read is denied because permit-false never matches and default is DENY")
        void whenAliceReadsThenDeny() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(STAGING_API_KEY).build();
                val subscription = AuthorizationSubscription.of("alice", "read", "document");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

}
