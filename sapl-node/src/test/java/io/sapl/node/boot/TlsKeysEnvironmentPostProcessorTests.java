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
package io.sapl.node.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import lombok.val;

/**
 * One TLS configuration serves both APIs: the PEM certificate chain and key of
 * {@code io.sapl.node.tls} become an SSL bundle that HTTP and RSocket use,
 * reloaded when the files change. An API that names an SSL bundle of its own
 * keeps it.
 */
@DisplayName("TlsKeysEnvironmentPostProcessor")
class TlsKeysEnvironmentPostProcessorTests {

    private static final String CHAIN = "/etc/sapl/tls/server.pem";
    private static final String KEY   = "/etc/sapl/tls/server.key";

    private static final Map<String, String> BOTH_KEYS = Map.of("io.sapl.node.tls.certificate-chain", CHAIN,
            "io.sapl.node.tls.private-key", KEY);

    @Test
    @DisplayName("the PEM files become a reloading bundle that both APIs serve")
    void whenBothKeysAreSetThenBothApisServeTheReloadingBundle() {
        val environment = processed(BOTH_KEYS);

        assertThat(Stream.of("spring.ssl.bundle.pem.sapl-node.keystore.certificate",
                "spring.ssl.bundle.pem.sapl-node.keystore.private-key",
                "spring.ssl.bundle.pem.sapl-node.reload-on-update", "server.ssl.bundle", "sapl.pdp.rsocket.ssl.bundle")
                .map(environment::getProperty))
                .containsExactly("file:" + CHAIN, "file:" + KEY, "true", "sapl-node", "sapl-node");
    }

    static Stream<Arguments> overrides() {
        return Stream.of(arguments("server.ssl.bundle", "http-bundle", "http-bundle", "sapl-node"),
                arguments("sapl.pdp.rsocket.ssl.bundle", "rsocket-bundle", "sapl-node", "rsocket-bundle"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overrides")
    @DisplayName("an API that names a bundle of its own keeps it, the other serves the shared one")
    void whenOneApiNamesItsOwnBundleThenItOverridesTheSharedOne(String key, String bundle, String http,
            String rsocket) {
        val settings = new HashMap<>(BOTH_KEYS);
        settings.put(key, bundle);

        val environment = processed(settings);

        assertThat(Stream.of("server.ssl.bundle", "sapl.pdp.rsocket.ssl.bundle").map(environment::getProperty))
                .containsExactly(http, rsocket);
    }

    @Test
    @DisplayName("an HTTP key store configured directly under server.ssl is kept and not mixed with the bundle")
    void whenHttpHasDirectKeyStoreThenNoBundleIsNamedForHttp() {
        val settings = new HashMap<>(BOTH_KEYS);
        settings.put("server.ssl.key-store", "file:/etc/sapl/keystore.p12");

        val environment = processed(settings);

        assertThat(Stream.of("server.ssl.bundle", "sapl.pdp.rsocket.ssl.bundle").map(environment::getProperty))
                .containsExactly(null, "sapl-node");
    }

    @Test
    @DisplayName("without the keys nothing changes, so each API is configured as before")
    void whenNoKeysThenNothingIsAdded() {
        val environment = processed(Map.of());

        assertThat(Stream.of("server.ssl.bundle", "sapl.pdp.rsocket.ssl.bundle").map(environment::getProperty))
                .containsExactly(null, null);
    }

    @Test
    @DisplayName("the keys are read from environment variables as Spring spells them, as in a container")
    void whenKeysAreEnvironmentVariablesThenBothApisServeTheBundle() {
        val environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("IO_SAPL_NODE_TLS_CERTIFICATECHAIN", CHAIN, "IO_SAPL_NODE_TLS_PRIVATEKEY", KEY)));

        new TlsKeysEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(Stream.of("spring.ssl.bundle.pem.sapl-node.keystore.certificate", "server.ssl.bundle")
                .map(environment::getProperty)).containsExactly("file:" + CHAIN, "sapl-node");
    }

    @ParameterizedTest(name = "only {0}")
    @MethodSource("singleKeys")
    @DisplayName("one of the two files alone refuses the start and names both keys")
    void whenOnlyOneKeyIsSetThenStartIsRefused(String key) {
        val environment = environment(Map.of(key, CHAIN));
        val sut         = new TlsKeysEnvironmentPostProcessor();
        val application = new SpringApplication();

        assertThatThrownBy(() -> sut.postProcessEnvironment(environment, application))
                .isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessageContaining("io.sapl.node.tls.certificate-chain")
                .hasMessageContaining("io.sapl.node.tls.private-key");
    }

    static Stream<String> singleKeys() {
        return BOTH_KEYS.keySet().stream();
    }

    private static StandardEnvironment processed(Map<String, String> settings) {
        val environment = environment(settings);
        new TlsKeysEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    private static StandardEnvironment environment(Map<String, String> settings) {
        val environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources()
                .addFirst(new MapPropertySource("application.yml", new HashMap<String, Object>(settings)));
        return environment;
    }

}
