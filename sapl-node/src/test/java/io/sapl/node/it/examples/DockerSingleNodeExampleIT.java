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
 * Smoke tests for the Docker single-node example
 * ({@code examples/docker/single-node/}).
 * <p>
 * Mirrors the docker-compose.yml configuration: basic auth only, no-auth
 * disabled, SSL disabled. Policy: permit-read-documents (action==read AND
 * resource==document). Algorithm: PRIORITY_PERMIT, default DENY.
 */
@Testcontainers
@DisplayName("Docker Single-Node Example Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DockerSingleNodeExampleIT extends BaseIntegrationTest {

    private static final Path POLICIES_DIR = Path.of("examples/docker/single-node/policies").toAbsolutePath();

    private static final String EXAMPLE_BASIC_USERNAME       = "xwuUaRD65G";
    private static final String EXAMPLE_BASIC_SECRET         = "3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_";
    private static final String EXAMPLE_BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$sNTVjma/BQZb5dzyIVCS3Q$c1Cy8OfyiEar4iv3Soxycc2jaOTJy6vV7gcMm+/jSRY";

    private GenericContainer<?> createExampleContainer() {
        return createSaplNodeContainer().withFileSystemBind(POLICIES_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true").withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "false").withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default")
                .withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false").withEnv("IO_SAPL_NODE_USERS_0_ID", "demo-client")
                .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", EXAMPLE_BASIC_USERNAME)
                .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", EXAMPLE_BASIC_SECRET_ENCODED)
                .withEnv("SERVER_SSL_ENABLED", "false");
    }

    @Test
    @DisplayName("read document is permitted with valid basic auth")
    void whenReadDocumentWithBasicAuthThenPermit() {
        try (val container = createExampleContainer()) {
            container.start();

            val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                    .basicAuth(EXAMPLE_BASIC_USERNAME, EXAMPLE_BASIC_SECRET).build();
            val subscription = AuthorizationSubscription.of("user", "read", "document");

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                    .verify(Duration.ofSeconds(30));
        }
    }

    @Test
    @DisplayName("delete secret is denied (no matching policy, default DENY)")
    void whenDeleteSecretWithBasicAuthThenDeny() {
        try (val container = createExampleContainer()) {
            container.start();

            val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                    .basicAuth(EXAMPLE_BASIC_USERNAME, EXAMPLE_BASIC_SECRET).build();
            val subscription = AuthorizationSubscription.of("user", "delete", "secret");

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                    .verify(Duration.ofSeconds(30));
        }
    }

    @Test
    @DisplayName("no auth is rejected when no-auth is disabled")
    void whenNoAuthThenIndeterminate() {
        try (val container = createExampleContainer()) {
            container.start();

            val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container)).build();
            val subscription = AuthorizationSubscription.of("user", "read", "document");

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.INDETERMINATE).thenCancel()
                    .verify(Duration.ofSeconds(30));
        }
    }

}
