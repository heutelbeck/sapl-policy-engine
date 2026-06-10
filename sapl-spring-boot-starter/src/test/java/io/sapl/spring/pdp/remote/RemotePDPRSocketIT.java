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
package io.sapl.spring.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.remote.ProtobufRemoteReactivePolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * End-to-end integration tests for the {@code rsocket} transport in the SAPL
 * Spring Boot Starter. Spins up a SAPL Node
 * container with RSocket enabled, configures the starter via Spring properties,
 * then asserts that the autowired
 * {@link ReactivePolicyDecisionPoint} bean talks to the container over the
 * configured transport.
 */
@Testcontainers
@DisplayName("RemotePDP Starter RSocket Integration Tests")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class RemotePDPRSocketIT {

    private static final int             RSOCKET_PORT      = 7000;
    private static final int             HTTP_PORT         = 8080;
    private static final String          SAPL_SERVER_IMAGE = "ghcr.io/heutelbeck/sapl-node:4.1.0-SNAPSHOT";
    private static final ImagePullPolicy NEVER_PULL        = imageName -> false;
    private static final Duration        STARTUP           = Duration.ofMinutes(2);
    private static final String          STARTUP_LOG       = ".*SAPL Node ready.*\\n";
    private static final String          POLICIES_PATH     = "policies-rsocket/";
    private static final Duration        STEP_TIMEOUT      = Duration.ofSeconds(30);

    private static final String API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final String API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

    private static final AuthorizationSubscription PERMIT_SUBSCRIPTION = AuthorizationSubscription.of("Willi", "eat",
            "apple");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemotePDPAutoConfiguration.class));

    private GenericContainer<?> baseContainer() {
        return new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE)).withImagePullPolicy(NEVER_PULL)
                .withExposedPorts(HTTP_PORT, RSOCKET_PORT).withEnv("SERVER_ADDRESS", "0.0.0.0")
                .withEnv("SERVER_PORT", String.valueOf(HTTP_PORT)).withEnv("SERVER_SSL_ENABLED", "false")
                .withEnv("IO_SAPL_PDP_EMBEDDED_PDPCONFIGTYPE", "DIRECTORY")
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data")
                .withClasspathResourceMapping(POLICIES_PATH, "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("SAPL_PDP_RSOCKET_ENABLED", "true")
                .withEnv("SAPL_PDP_RSOCKET_PORT", String.valueOf(RSOCKET_PORT))
                .withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "true")
                .withEnv("IO_SAPL_NODE_USERS_0_ID", "test-apikey-client")
                .withEnv("IO_SAPL_NODE_USERS_0_PDPID", "default")
                .withEnv("IO_SAPL_NODE_USERS_0_APIKEY", API_KEY_ENCODED)
                .waitingFor(Wait.forLogMessage(STARTUP_LOG, 1).withStartupTimeout(STARTUP));
    }

    private GenericContainer<?> withTlsBundle(GenericContainer<?> container) {
        return container.withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEY_ALIAS", "netty")
                .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEY_PASSWORD", "changeme")
                .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_LOCATION", "file:/pdp/data/keystore.p12")
                .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_PASSWORD", "changeme")
                .withEnv("SPRING_SSL_BUNDLE_JKS_SAPLBUNDLE_KEYSTORE_TYPE", "PKCS12")
                .withEnv("SAPL_PDP_RSOCKET_SSL_BUNDLE", "saplbundle");
    }

    private void runWithPdp(String[] properties, AuthorizationDecision expected) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProtobufRemoteReactivePolicyDecisionPoint.class);
            val pdp = context.getBean(ReactivePolicyDecisionPoint.class);
            StepVerifier.create(pdp.decide(PERMIT_SUBSCRIPTION)).expectNext(expected).thenCancel().verify(STEP_TIMEOUT);
        });
    }

    @Nested
    @DisplayName("Plain RSocket (no TLS)")
    class PlainTcpTests {

        @Test
        @DisplayName("autowires bean and returns PERMIT over plain rsocket")
        void whenPlainRsocketAndPolicyMatchesThenPermit() {
            try (val container = baseContainer()) {
                container.start();
                val properties = new String[] { "io.sapl.pdp.remote.enabled=true", "io.sapl.pdp.remote.type=rsocket",
                        "io.sapl.pdp.remote.host=" + container.getHost(),
                        "io.sapl.pdp.remote.port=" + container.getMappedPort(RSOCKET_PORT),
                        "io.sapl.pdp.remote.bearer-token=" + API_KEY };
                runWithPdp(properties, AuthorizationDecision.PERMIT);
            }
        }
    }

    @Nested
    @DisplayName("RSocket TLS via shared SSL bundle")
    class TlsTests {

        @Test
        @DisplayName("autowires bean and returns PERMIT over rsocket with TLS (ignoreCertificates=true)")
        void whenRsocketTlsAndPolicyMatchesThenPermit() {
            try (val container = withTlsBundle(baseContainer())) {
                container.start();
                val properties = new String[] { "io.sapl.pdp.remote.enabled=true", "io.sapl.pdp.remote.type=rsocket",
                        "io.sapl.pdp.remote.host=" + container.getHost(),
                        "io.sapl.pdp.remote.port=" + container.getMappedPort(RSOCKET_PORT),
                        "io.sapl.pdp.remote.bearer-token=" + API_KEY, "io.sapl.pdp.remote.ignoreCertificates=true" };
                runWithPdp(properties, AuthorizationDecision.PERMIT);
            }
        }
    }
}
