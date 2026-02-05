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
package io.sapl.node.it.hotreload;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Integration tests for SAPL Node hot-reload functionality with Directory
 * configuration source.
 * <p>
 * IT-004: Validates that policy file changes are detected and new requests get
 * updated decisions. IT-005: Validates that streaming subscriptions receive
 * updated decisions when policies change.
 */
@Testcontainers
@DisplayName("Directory Hot-Reload Integration Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DirectoryHotReloadIT extends BaseIntegrationTest {

    private static final String BASIC_USERNAME       = "mpI3KjU7n1";
    private static final String BASIC_SECRET         = "haTPcbYA8Dwkl91$)gG42S)UG98eF!*m";
    private static final String BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$lZK1zPNtAe3+JnT37cGDMg$PSLftgfXXjXDOTY87cCg63F+O+sd/5aeW4m1MFZgSoM";

    private static final AuthorizationSubscription TEST_SUBSCRIPTION = AuthorizationSubscription.of("User", "read",
            "document");

    private static final String PDP_JSON = """
            {
              "algorithm": {
                "votingMode": "PRIORITY_PERMIT",
                "defaultDecision": "DENY",
                "errorHandling": "PROPAGATE"
              }
            }
            """;

    private static final String DENY_POLICY = """
            policy "deny-all"
            deny true;
            """;

    private static final String PERMIT_POLICY = """
            policy "permit-all"
            permit true;
            """;

    @Nested
    @DisplayName("New Requests After Policy Change")
    class NewRequestsAfterPolicyChangeTests {

        @Test
        @DisplayName("IT-004: new request gets updated decision after policy file change")
        void whenPolicyChangedThenNewRequestGetsUpdatedDecision(@TempDir Path tempDir) throws IOException {
            val policiesDir = tempDir.resolve("policies");
            Files.createDirectories(policiesDir);
            Files.writeString(policiesDir.resolve("pdp.json"), PDP_JSON);
            Files.writeString(policiesDir.resolve("policy.sapl"), DENY_POLICY);
            copyKeystoreToDirectory(policiesDir);

            try (val container = createSaplNodeContainer()
                    .withFileSystemBind(policiesDir.toString(), "/pdp/data/", BindMode.READ_WRITE)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                    .withEnv("SERVER_SSL_KEYSTORETYPE", "PKCS12")
                    .withEnv("SERVER_SSL_KEYSTORE", "/pdp/data/keystore.p12")
                    .withEnv("SERVER_SSL_KEYSTOREPASSWORD", "changeme").withEnv("SERVER_SSL_KEYPASSWORD", "changeme")
                    .withEnv("SERVER_SSL_KEYALIAS", "netty").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-client").withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify();

                Files.writeString(policiesDir.resolve("policy.sapl"), PERMIT_POLICY);

                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
                    val decision = pdp.decide(TEST_SUBSCRIPTION).blockFirst();
                    assert decision != null && decision.decision() == AuthorizationDecision.PERMIT.decision();
                });
            }
        }

    }

    @Nested
    @DisplayName("Streaming Subscriptions During Policy Change")
    class StreamingSubscriptionsDuringPolicyChangeTests {

        @Test
        @DisplayName("IT-005: streaming subscriber receives updated decision when policy changes")
        void whenPolicyChangedThenStreamingSubscriberReceivesUpdate(@TempDir Path tempDir) throws IOException {
            val policiesDir = tempDir.resolve("policies");
            Files.createDirectories(policiesDir);
            Files.writeString(policiesDir.resolve("pdp.json"), PDP_JSON);
            Files.writeString(policiesDir.resolve("policy.sapl"), DENY_POLICY);
            copyKeystoreToDirectory(policiesDir);

            try (val container = createSaplNodeContainer()
                    .withFileSystemBind(policiesDir.toString(), "/pdp/data/", BindMode.READ_WRITE)
                    .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                    .withEnv("SERVER_SSL_KEYSTORETYPE", "PKCS12")
                    .withEnv("SERVER_SSL_KEYSTORE", "/pdp/data/keystore.p12")
                    .withEnv("SERVER_SSL_KEYSTOREPASSWORD", "changeme").withEnv("SERVER_SSL_KEYPASSWORD", "changeme")
                    .withEnv("SERVER_SSL_KEYALIAS", "netty").withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "true")
                    .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-client").withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_USERNAME", BASIC_USERNAME)
                    .withEnv("IO_SAPL_NODE_USERS_0_BASIC_SECRET", BASIC_SECRET_ENCODED)) {
                container.start();

                val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpsBaseUrl(container))
                        .basicAuth(BASIC_USERNAME, BASIC_SECRET).withUnsecureSSL().build();

                StepVerifier.create(pdp.decide(TEST_SUBSCRIPTION)).expectNext(AuthorizationDecision.DENY).then(() -> {
                    try {
                        Files.writeString(policiesDir.resolve("policy.sapl"), PERMIT_POLICY);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).expectNextMatches(decision -> decision.decision() == AuthorizationDecision.PERMIT.decision())
                        .thenCancel().verify(Duration.ofSeconds(30));
            }
        }

    }

    private void copyKeystoreToDirectory(Path targetDir) throws IOException {
        val keystorePath = Path.of("src/test/resources/keystore.p12");
        Files.copy(keystorePath, targetDir.resolve("keystore.p12"));
    }

}
