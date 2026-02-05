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
package io.sapl.node.it;

import java.time.Duration;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

import io.sapl.api.pdp.AuthorizationSubscription;

/**
 * Base class for SAPL Node integration tests providing shared container
 * configuration, test fixtures, and utility methods.
 */
public abstract class BaseIntegrationTest {

    protected static final int             SAPL_SERVER_PORT    = 8443;
    protected static final String          SAPL_SERVER_IMAGE   = "ghcr.io/heutelbeck/sapl-node:4.0.0-SNAPSHOT";
    protected static final ImagePullPolicy NEVER_PULL          = imageName -> false;
    protected static final Duration        CONTAINER_STARTUP   = Duration.ofMinutes(2);
    protected static final String          STARTUP_LOG_PATTERN = ".*Started SaplNodeApplication.*\\n";

    protected static final String BASIC_USERNAME       = "testUser123";
    protected static final String BASIC_SECRET         = "testSecret!@#456SecurePass";
    protected static final String BASIC_SECRET_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$abc123def456$encodedHashValueHere";

    protected static final String API_KEY         = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    protected static final String API_KEY_ENCODED = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";

    protected static final AuthorizationSubscription PERMIT_SUBSCRIPTION = AuthorizationSubscription.of("Willi", "eat",
            "apple");
    protected static final AuthorizationSubscription DENY_SUBSCRIPTION   = AuthorizationSubscription.of("Willi",
            "destroy", "world");

    /**
     * Creates a base SAPL Node container with common configuration.
     *
     * @return configured container ready for additional customization
     */
    protected GenericContainer<?> createSaplNodeContainer() {
        return new GenericContainer<>(DockerImageName.parse(SAPL_SERVER_IMAGE)).withImagePullPolicy(NEVER_PULL)
                .withExposedPorts(SAPL_SERVER_PORT)
                .waitingFor(Wait.forLogMessage(STARTUP_LOG_PATTERN, 1).withStartupTimeout(CONTAINER_STARTUP));
    }

    /**
     * Creates a SAPL Node container with TLS enabled using the test keystore.
     *
     * @param policiesPath classpath resource path for policies
     * @return configured container with TLS
     */
    protected GenericContainer<?> createSaplNodeContainerWithTls(String policiesPath) {
        return createSaplNodeContainer().withClasspathResourceMapping(policiesPath, "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("SERVER_SSL_KEYSTORETYPE", "PKCS12")
                .withEnv("SERVER_SSL_KEYSTORE", "/pdp/data/keystore.p12")
                .withEnv("SERVER_SSL_KEYSTOREPASSWORD", "changeme").withEnv("SERVER_SSL_KEYPASSWORD", "changeme")
                .withEnv("SERVER_SSL_KEYALIAS", "netty");
    }

    /**
     * Creates a SAPL Node container without TLS for simpler testing.
     *
     * @param policiesPath classpath resource path for policies
     * @return configured container without TLS
     */
    protected GenericContainer<?> createSaplNodeContainerWithoutTls(String policiesPath) {
        return createSaplNodeContainer().withClasspathResourceMapping(policiesPath, "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("SERVER_SSL_ENABLED", "false");
    }

    /**
     * Gets the HTTP base URL for a running container.
     *
     * @param container the running container
     * @return HTTP URL string
     */
    protected String getHttpBaseUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
    }

    /**
     * Gets the HTTPS base URL for a running container.
     *
     * @param container the running container
     * @return HTTPS URL string
     */
    protected String getHttpsBaseUrl(GenericContainer<?> container) {
        return "https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_PORT);
    }

}
