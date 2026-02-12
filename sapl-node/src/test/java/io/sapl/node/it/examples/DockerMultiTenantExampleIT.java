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
 * Smoke tests for the Docker multi-tenant example
 * ({@code examples/docker/multi-tenant/}).
 * <p>
 * Mirrors the docker-compose.yml: MULTI_DIRECTORY, basic auth,
 * rejectOnMissingPdpId=true. Tenant-a has a restrictive policy (deny true),
 * tenant-b has a permissive policy (permit true).
 */
@Testcontainers
@DisplayName("Docker Multi-Tenant Example Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DockerMultiTenantExampleIT extends BaseIntegrationTest {

    private static final Path POLICIES_DIR = Path.of("examples/docker/multi-tenant/policies").toAbsolutePath();

    private static final String TENANT_A_USERNAME    = "xwuUaRD65G";
    private static final String TENANT_A_SECRET      = "3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_";
    private static final String TENANT_B_USERNAME    = "tenant-b-user";
    private static final String TENANT_B_SECRET      = "3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_";
    private static final String BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$sNTVjma/BQZb5dzyIVCS3Q$c1Cy8OfyiEar4iv3Soxycc2jaOTJy6vV7gcMm+/jSRY";

    private GenericContainer<?> createExampleContainer() {
        return createSaplNodeContainer().withFileSystemBind(POLICIES_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "MULTI_DIRECTORY")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true").withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "true")
                .withEnv("IO_SAPL_NODE_USERS_0_ID", "tenant-a-client").withEnv("IO_SAPL_NODE_USERS_0_PDPID", "tenant-a")
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", TENANT_A_USERNAME)
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)
                .withEnv("IO_SAPL_NODE_USERS_1_ID", "tenant-b-client").withEnv("IO_SAPL_NODE_USERS_1_PDPID", "tenant-b")
                .withEnv("IO_SAPL_NODE_USERS_1_BASIC_USERNAME", TENANT_B_USERNAME)
                .withEnv("IO_SAPL_NODE_USERS_1_BASIC_SECRET", BASIC_SECRET_ENCODED)
                .withEnv("SERVER_SSL_ENABLED", "false");
    }

    @Nested
    @DisplayName("Tenant A (restrictive: deny true)")
    class TenantATests {

        @Test
        @DisplayName("denies all access via restrictive policy")
        void whenUserReadsDataThenDeny() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .basicAuth(TENANT_A_USERNAME, TENANT_A_SECRET).build();
                val subscription = AuthorizationSubscription.of("user", "read", "data");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("Tenant B (permissive: permit true)")
    class TenantBTests {

        @Test
        @DisplayName("permits all access via permissive policy")
        void whenUserReadsDataThenPermit() {
            try (val container = createExampleContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .basicAuth(TENANT_B_USERNAME, TENANT_B_SECRET).build();
                val subscription = AuthorizationSubscription.of("user", "read", "data");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

}
