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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.sapl.node.testing.LogCapture;
import lombok.val;

/**
 * The HTTP API serves TLS, or plaintext only where the operator allowed it,
 * on every address including loopback. The plaintext warning names the source
 * of the opt-in, and an SSL bundle that is named but switched off by
 * {@code server.ssl.enabled=false} refuses the start instead of serving
 * plaintext unnoticed.
 */
@DisplayName("HttpTransportSecurity")
class HttpTransportSecurityTests {

    private static final String WARN_WITHOUT_TLS = "WARN Serving HTTP without TLS because of %s. Anyone on the network path can read and change credentials and authorization decisions. Use this only where TLS is terminated upstream, by a proxy or a service mesh, or in development.";

    @Test
    @DisplayName("without TLS and without the opt-in the start is refused with the ways to configure TLS or opt in")
    void whenNoTlsAndNoOptInThenStartIsRefused() {
        val sut = security(Map.of("server.address", "127.0.0.1"));

        assertThatThrownBy(sut::requireTlsOrPlaintextOptIn).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessage("SAPL Node refused to start. The HTTP API has no TLS.")
                .satisfies(error -> assertThat(((SaplStartupConfigurationException) error).getAction())
                        .contains("io.sapl.node.tls.certificate-chain").contains("io.sapl.node.tls.private-key")
                        .contains("server.ssl.bundle").contains("--no-tls").contains("io.sapl.node.allow-no-tls=true"));
    }

    @Test
    @DisplayName("without TLS and with the opt-in the start warns once and names the source")
    void whenNoTlsAndOptInThenWarningNamesSource() {
        val sut = security(Map.of("io.sapl.node.allow-no-tls", "true"));

        try (val log = LogCapture.of(TransportTls.class)) {
            sut.requireTlsOrPlaintextOptIn();

            assertThat(log.lines())
                    .containsExactly(WARN_WITHOUT_TLS.formatted("io.sapl.node.allow-no-tls=true in test"));
        }
    }

    @Test
    @DisplayName("server.ssl.enabled=false without a bundle is plaintext and needs the opt-in like no TLS at all")
    void whenSslDisabledWithoutBundleThenPlaintextNeedsOptIn() {
        val sut = security(Map.of("server.ssl.enabled", "false"));

        assertThatThrownBy(sut::requireTlsOrPlaintextOptIn).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessage("SAPL Node refused to start. The HTTP API has no TLS.");
    }

    @Test
    @DisplayName("a named SSL bundle switches TLS on without server.ssl.enabled, and nothing is logged")
    void whenBundleNamedThenTlsIsOnAndSilent() {
        val sut = security(Map.of("server.ssl.bundle", "sapl-node"));

        try (val log = LogCapture.of(TransportTls.class)) {
            sut.requireTlsOrPlaintextOptIn();

            assertThat(log.lines()).isEmpty();
        }
    }

    @Test
    @DisplayName("a bundle named while server.ssl.enabled is false refuses the start, even with the plaintext opt-in")
    void whenBundleNamedButSslDisabledThenStartIsRefused() {
        val sut = security(Map.of("server.ssl.bundle", "sapl-node", "server.ssl.enabled", "false",
                "io.sapl.node.allow-no-tls", "true"));

        assertThatThrownBy(sut::requireTlsOrPlaintextOptIn).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessage(
                        "SAPL Node refused to start. The HTTP API names the SSL bundle 'sapl-node', but server.ssl.enabled is false.")
                .satisfies(error -> assertThat(((SaplStartupConfigurationException) error).getAction())
                        .contains("server.ssl.enabled").contains("io.sapl.node.allow-no-tls=true"));
    }

    private static HttpTransportSecurity security(Map<String, String> settings) {
        val environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test", new HashMap<String, Object>(settings)));
        return new HttpTransportSecurity(environment);
    }

}
