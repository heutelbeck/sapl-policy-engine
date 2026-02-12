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
import org.testcontainers.junit.jupiter.Testcontainers;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Smoke tests for the single-directory local example
 * ({@code examples/local/singledirectory/}).
 * <p>
 * Mounts the real example policy files into a sapl-node container and validates
 * the documented authorization behavior. If someone edits an example policy,
 * these tests break.
 * <p>
 * Policies: permitall (permit), policy_A (deny WILLI/foo), policy_B (permit
 * WILLI/foo). Algorithm: PRIORITY_DENY, default DENY.
 */
@Testcontainers
@DisplayName("Single Directory Example Smoke Tests")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class SingleDirectoryExampleIT extends BaseIntegrationTest {

    private static final Path EXAMPLE_DIR = Path.of("examples/local/singledirectory").toAbsolutePath();

    @Test
    @DisplayName("alice reading bar is permitted by permitall policy")
    void whenAliceReadsBarThenPermit() {
        try (val container = createSaplNodeContainer()
                .withFileSystemBind(EXAMPLE_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                .withEnv("SERVER_SSL_ENABLED", "false")) {
            container.start();

            val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container)).build();
            val subscription = AuthorizationSubscription.of("alice", "read", "bar");

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                    .verify(Duration.ofSeconds(30));
        }
    }

    @Test
    @DisplayName("WILLI reading foo is denied by PRIORITY_DENY algorithm")
    void whenWilliReadsFooThenDeny() {
        try (val container = createSaplNodeContainer()
                .withFileSystemBind(EXAMPLE_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "true")
                .withEnv("SERVER_SSL_ENABLED", "false")) {
            container.start();

            val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(container)).build();
            val subscription = AuthorizationSubscription.of("WILLI", "read", "foo");

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                    .verify(Duration.ofSeconds(30));
        }
    }

}
