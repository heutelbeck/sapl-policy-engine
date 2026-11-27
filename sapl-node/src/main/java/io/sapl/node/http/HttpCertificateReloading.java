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
package io.sapl.node.http;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Serves a renewed certificate on the HTTP API without a restart. Spring Boot
 * reloads an SSL bundle with {@code reload-on-update} when its files change,
 * but its Jetty server does not take the update. This customizer hands each
 * update of the bundle of {@code server.ssl.bundle} to the TLS context of the
 * Jetty connector, which serves new connections with it. Open connections keep
 * their certificate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class HttpCertificateReloading implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    private static final String INFO_CERTIFICATE_RELOADED     = "Reloaded the TLS certificate of the HTTP API.";
    private static final String WARN_CERTIFICATE_NOT_RELOADED = "The TLS certificate of the HTTP API cannot be reloaded: {}. The node keeps serving the previous certificate.";

    private final Environment                environment;
    private final ObjectProvider<SslBundles> sslBundles;

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        val ssl     = Binder.get(environment).bind("server.ssl", Ssl.class).orElse(null);
        val bundles = sslBundles.getIfAvailable();
        if (bundles == null || !Ssl.isEnabled(ssl) || !StringUtils.hasText(ssl.getBundle())) {
            return;
        }
        val bundleName = ssl.getBundle();
        factory.addServerCustomizers(server -> followUpdates(server, bundles, bundleName));
    }

    static void followUpdates(Server server, SslBundles bundles, String bundleName) {
        for (val connector : server.getConnectors()) {
            val tls = connector.getConnectionFactory(SslConnectionFactory.class);
            if (tls != null) {
                val contextFactory = tls.getSslContextFactory();
                bundles.addBundleUpdateHandler(bundleName, bundle -> reload(contextFactory, bundle));
            }
        }
    }

    // A renewed certificate that cannot be used leaves the served one in place,
    // so a broken file never takes TLS down.
    static void reload(SslContextFactory.Server contextFactory, SslBundle bundle) {
        try {
            contextFactory.reload(factory -> configure(factory, bundle));
            log.info(INFO_CERTIFICATE_RELOADED);
        } catch (Exception e) {
            log.warn(WARN_CERTIFICATE_NOT_RELOADED, e.getMessage());
        }
    }

    // The settings of the stores that Spring Boot gives the factory when the
    // server is created. The protocol, ciphers and client authentication stay.
    private static void configure(SslContextFactory factory, SslBundle bundle) {
        val stores = bundle.getStores();
        val key    = bundle.getKey();
        factory.setKeyStore(stores.getKeyStore());
        factory.setKeyStorePassword(stores.getKeyStorePassword());
        factory.setKeyManagerPassword(key.getPassword());
        factory.setCertAlias(key.getAlias());
        factory.setTrustStore(stores.getTrustStore());
    }

}
