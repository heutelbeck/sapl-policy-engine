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
import org.testcontainers.utility.MountableFile;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Smoke tests for the default development configuration
 * ({@code config/application.yml}).
 * <p>
 * Validates that the out-of-the-box configuration shipped with the sapl-node
 * module works correctly when run via {@code mvn spring-boot:run}. Tests basic
 * auth and API key auth with the documented demo credentials. The policies-path
 * and config-path are overridden because the config's {@code ~/sapl} does not
 * exist in the container.
 */
@Testcontainers
@DisplayName("Default Config Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DefaultConfigExampleIT extends BaseIntegrationTest {

    private static final Path CONFIG_FILE = Path.of("config/application.yml").toAbsolutePath();

    private static final String BASIC_USERNAME = "xwuUaRD65G";
    private static final String BASIC_SECRET   = "3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_";
    private static final String API_KEY_1      = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String API_KEY_2      = "sapl_oCR3QQ8fhD_XYs3x1dQ3M1NM9FJLjPHlwd1NXiMdZ1f";

    private GenericContainer<?> createDefaultConfigContainer() {
        return createSaplNodeContainer()
                .withCopyFileToContainer(MountableFile.forHostPath(CONFIG_FILE), "/pdp/config/application.yml")
                .withClasspathResourceMapping("it/policies/single-pdp", "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                .withEnv("IO_SAPL_PDP_EMBEDDED_CONFIGPATH", "/pdp/data").withEnv("SERVER_PORT", "8443")
                .withEnv("SERVER_ADDRESS", "0.0.0.0");
    }

    @Nested
    @DisplayName("Basic Authentication")
    class BasicAuthTests {

        @Test
        @DisplayName("basic auth with demo credentials permits matching request")
        void whenBasicAuthWithDemoCredentialsThenPermit() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).build();
                val subscription = AuthorizationSubscription.of("Willi", "eat", "apple");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("basic auth with demo credentials denies non-matching request")
        void whenBasicAuthNonMatchingRequestThenDeny() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).build();
                val subscription = AuthorizationSubscription.of("user", "delete", "secret");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("API Key Authentication")
    class ApiKeyAuthTests {

        @Test
        @DisplayName("API key 1 permits matching request")
        void whenApiKey1MatchingRequestThenPermit() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(API_KEY_1).build();
                val subscription = AuthorizationSubscription.of("Willi", "eat", "apple");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

        @Test
        @DisplayName("API key 2 permits matching request")
        void whenApiKey2MatchingRequestThenPermit() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .apiKey(API_KEY_2).build();
                val subscription = AuthorizationSubscription.of("Willi", "eat", "apple");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }

    }

    @Nested
    @DisplayName("No Authentication")
    class NoAuthTests {

        @Test
        @DisplayName("no auth is rejected (allowNoAuth is false in default config)")
        void whenNoAuthThenIndeterminate() {
            try (val container = createDefaultConfigContainer()) {
                container.start();

                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container))
                        .build();
                val subscription = AuthorizationSubscription.of("Willi", "eat", "apple");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

    }

}
