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
 * Smoke tests for the out-of-the-box default configuration.
 * <p>
 * Validates that the default application.yml shipped inside the sapl-node image
 * works correctly: no TLS, no authentication required (allowNoAuth: true),
 * DIRECTORY policy source. Policies are mounted from test resources.
 */
@Testcontainers
@DisplayName("Default Config Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DefaultConfigExampleIT extends BaseIntegrationTest {

    private GenericContainer<?> createDefaultConfigContainer() {
        return createSaplNodeContainer()
                .withClasspathResourceMapping("it/policies/single-pdp", "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                .withEnv("IO_SAPL_PDP_EMBEDDED_CONFIGPATH", "/pdp/data").withEnv("SERVER_SSL_ENABLED", "false");
    }

    @Nested
    @DisplayName("No Authentication (default)")
    class NoAuthTests {

        @Test
        @DisplayName("unauthenticated request is permitted when policy matches")
        void whenNoAuthAndPolicyMatchesThenPermit() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val subscription = AuthorizationSubscription.of("Willi", "eat", "apple");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("unauthenticated request is denied when policy does not match")
        void whenNoAuthAndPolicyDoesNotMatchThenDeny() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val subscription = AuthorizationSubscription.of("user", "delete", "secret");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

}
