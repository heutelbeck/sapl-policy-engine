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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
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
 * Smoke tests for the local bundles example
 * ({@code examples/local/bundles/}).
 * <p>
 * Tests the BUNDLES config type with Ed25519-signed bundles and per-tenant key
 * isolation. The authorization tests mirror the multi-directory example since
 * the bundles contain identical policies. Bundle security tests validate that
 * unsigned bundles are rejected for signed tenants and accepted for
 * unsigned-tenants.
 */
@Testcontainers
@DisplayName("Bundles Example Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class BundlesExampleIT extends BaseIntegrationTest {

    private static final Path BUNDLES_DIR  = Path.of("examples/local/bundles/bundles").toAbsolutePath();
    private static final Path UNSIGNED_DIR = Path.of("examples/local/bundles/unsigned").toAbsolutePath();

    private static final String PRODUCTION_API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String PRODUCTION_API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

    private static final String STAGING_API_KEY         = "sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f";
    private static final String STAGING_API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$EATdeVYu9zNEnS6cnr8x+A$z+sdYbjvms6rFXhCJ6C5a3FtnKu0NBmMSAo9KVIZ42k";

    private static final String BUNDLE_SECURITY_JSON = """
            {\
            "io.sapl.pdp.embedded.bundle-security":{\
            "keys":{\
            "default-key":"MCowBQYDK2VwAyEA4osaQaUcjIK+ljvqWZY3UPq14PTenc5kG+MK6ORQizc=",\
            "production-key":"MCowBQYDK2VwAyEAlmVHWMLuFL3JBp37WPCQHnbsdC/nd6n7MfYzfSJfcus=",\
            "staging-key":"MCowBQYDK2VwAyEAdVrnc/Tqa8tHuG159r0fNwHACqMmNwOe1UIeYA6U/ck="\
            },\
            "tenants":{\
            "default":["default-key"],\
            "production":["production-key"],\
            "staging":["staging-key"]\
            },\
            "unsigned-tenants":["staging"]\
            }\
            }""";

    private GenericContainer<?> createBundleContainer(String bundlesPath) {
        return createSaplNodeContainer().withFileSystemBind(bundlesPath, "/pdp/data/bundles/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "BUNDLES")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data/bundles")
                .withEnv("SPRING_APPLICATION_JSON", BUNDLE_SECURITY_JSON).withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                .withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true").withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false")
                .withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default").withEnv("IO_SAPL_NODE_USERS_0_ID", "production-client")
                .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "production")
                .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", PRODUCTION_API_KEY_ENCODED)
                .withEnv("IO_SAPL_NODE_USERS_1_ID", "staging-client").withEnv("IO_SAPL_NODE_USERS_1_PDPID", "staging")
                .withEnv("IO_SAPL_NODE_USERS_1_APIKEY", STAGING_API_KEY_ENCODED).withEnv("SERVER_SSL_ENABLED", "false");
    }

    @Nested
    @DisplayName("Authorization (signed bundles)")
    class AuthorizationTests {

        @Test
        @DisplayName("default tenant permits all via permitall bundle")
        void whenDefaultTenantAnyRequestThenPermit() {
            try (val container = createBundleContainer(BUNDLES_DIR.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val subscription = AuthorizationSubscription.of("anyone", "anything", "anything");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("production tenant: admin read is permitted")
        void whenProductionAdminReadsThenPermit() {
            try (val container = createBundleContainer(BUNDLES_DIR.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(PRODUCTION_API_KEY).build();
                val subscription = AuthorizationSubscription.of("admin", "read", "data");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("production tenant: admin delete is denied by PRIORITY_DENY")
        void whenProductionAdminDeletesThenDeny() {
            try (val container = createBundleContainer(BUNDLES_DIR.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(PRODUCTION_API_KEY).build();
                val subscription = AuthorizationSubscription.of("admin", "delete", "database");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("staging tenant: alice read is denied (permit false, default DENY)")
        void whenStagingAliceReadsThenDeny() {
            try (val container = createBundleContainer(BUNDLES_DIR.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(STAGING_API_KEY).build();
                val subscription = AuthorizationSubscription.of("alice", "read", "document");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("Bundle Security")
    class BundleSecurityTests {

        @Test
        @DisplayName("unsigned production bundle is rejected (production requires signing)")
        void whenUnsignedProductionBundleThenIndeterminate(@TempDir Path tempDir) throws IOException {
            val bundlesDir = tempDir.resolve("bundles");
            Files.createDirectories(bundlesDir);
            Files.copy(BUNDLES_DIR.resolve("default.saplbundle"), bundlesDir.resolve("default.saplbundle"));
            Files.copy(BUNDLES_DIR.resolve("staging.saplbundle"), bundlesDir.resolve("staging.saplbundle"));
            Files.copy(UNSIGNED_DIR.resolve("production-unsigned.saplbundle"),
                    bundlesDir.resolve("production.saplbundle"), StandardCopyOption.REPLACE_EXISTING);

            try (val container = createBundleContainer(bundlesDir.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(PRODUCTION_API_KEY).build();
                val subscription = AuthorizationSubscription.of("admin", "read", "data");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("unsigned staging bundle is accepted (staging is in unsigned-tenants)")
        void whenUnsignedStagingBundleThenPoliciesWork(@TempDir Path tempDir) throws IOException {
            val bundlesDir = tempDir.resolve("bundles");
            Files.createDirectories(bundlesDir);
            Files.copy(BUNDLES_DIR.resolve("default.saplbundle"), bundlesDir.resolve("default.saplbundle"));
            Files.copy(BUNDLES_DIR.resolve("production.saplbundle"), bundlesDir.resolve("production.saplbundle"));
            Files.copy(UNSIGNED_DIR.resolve("production-unsigned.saplbundle"), bundlesDir.resolve("staging.saplbundle"),
                    StandardCopyOption.REPLACE_EXISTING);

            try (val container = createBundleContainer(bundlesDir.toString())) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(STAGING_API_KEY).build();
                val subscription = AuthorizationSubscription.of("alice", "read", "document");

                StepVerifier.create(pdp.decide(subscription))
                        .expectNextMatches(
                                decision -> decision.decision() != AuthorizationDecision.INDETERMINATE.decision())
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

    }

}
