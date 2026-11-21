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

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Applies the TLS rule of {@link TransportTls} to the HTTP API before its
 * server binds. Spring Boot serves TLS as soon as any {@code server.ssl}
 * setting exists, such as {@code server.ssl.bundle}, unless
 * {@code server.ssl.enabled} is false. A bundle that is named while TLS is
 * switched off would serve plaintext unnoticed, so it refuses the start.
 */
@Component
@RequiredArgsConstructor
class HttpTransportSecurity implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    private static final String ERROR_BUNDLE_DISABLED  = "SAPL Node refused to start. The HTTP API names the SSL bundle '%s', but server.ssl.enabled is false.";
    private static final String ACTION_BUNDLE_DISABLED = """
            Remove server.ssl.enabled: false to serve HTTPS with the bundle.
            To serve HTTP in plaintext instead, remove the bundle from
            server.ssl.bundle (or io.sapl.node.tls.* if it names the PEM
            files of both APIs) and allow plaintext with --no-tls or
            io.sapl.node.allow-no-tls=true.""";

    private final Environment environment;

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        requireTlsOrPlaintextOptIn();
    }

    void requireTlsOrPlaintextOptIn() {
        val ssl = Binder.get(environment).bind("server.ssl", Ssl.class).orElse(null);
        if (ssl != null && !ssl.isEnabled() && StringUtils.hasText(ssl.getBundle())) {
            throw new SaplStartupConfigurationException(ERROR_BUNDLE_DISABLED.formatted(ssl.getBundle()),
                    ACTION_BUNDLE_DISABLED);
        }
        TransportTls.requireTlsOrPlaintextOptIn("HTTP", Ssl.isEnabled(ssl), OptIn.NO_TLS.source(environment));
    }

}
