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

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * The rule of both APIs: an API serves TLS, or plaintext only where the
 * operator allowed it with {@code --no-tls} or
 * {@code io.sapl.node.allow-no-tls=true}. The rule is the same on every
 * address, loopback included, and every start in plaintext warns and names the
 * source of the opt-in.
 */
@Slf4j
public final class TransportTls {

    private static final String ERROR_NO_TLS = "SAPL Node refused to start. The %s API has no TLS.";

    private static final String WARN_WITHOUT_TLS = "Serving {} without TLS because of {}. Anyone on the network path can read and change credentials and authorization decisions. Use this only where TLS is terminated upstream, by a proxy or a service mesh, or in development.";

    private static final String ACTION_NO_TLS = """
            Configure one certificate for both APIs with PEM files:

              io.sapl.node.tls.certificate-chain: /etc/sapl/tls/server.pem
              io.sapl.node.tls.private-key: /etc/sapl/tls/server.key

            or name an SSL bundle of spring.ssl.bundle for an API in
            server.ssl.bundle (HTTP) or sapl.pdp.rsocket.ssl.bundle (RSocket).
            Where TLS is terminated upstream, by a proxy or a service mesh,
            allow plaintext with --no-tls or io.sapl.node.allow-no-tls=true.""";

    private TransportTls() {
    }

    /**
     * Refuses an API without TLS unless plaintext is allowed, and warns of an
     * API that serves plaintext.
     *
     * @param api the name of the API, HTTP or RSocket
     * @param tls whether the API serves TLS
     * @param plaintextSource the source of the plaintext opt-in, or empty if
     * plaintext is not allowed
     * @throws SaplStartupConfigurationException if the API has no TLS and
     * plaintext is not allowed
     */
    public static void requireTlsOrPlaintextOptIn(String api, boolean tls, Optional<String> plaintextSource) {
        if (tls) {
            return;
        }
        final var source = plaintextSource
                .orElseThrow(() -> new SaplStartupConfigurationException(ERROR_NO_TLS.formatted(api), ACTION_NO_TLS));
        log.warn(WARN_WITHOUT_TLS, api, source);
    }

}
