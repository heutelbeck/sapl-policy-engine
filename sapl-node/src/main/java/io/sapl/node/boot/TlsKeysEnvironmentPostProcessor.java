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

import java.util.HashMap;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import lombok.val;

/**
 * Serves one TLS certificate on both APIs. The PEM files of
 * {@code io.sapl.node.tls.certificate-chain} and
 * {@code io.sapl.node.tls.private-key} become the SSL bundle
 * {@value #BUNDLE}, reloaded when the files change, and both
 * {@code server.ssl.bundle} and {@code sapl.pdp.rsocket.ssl.bundle} name it.
 * <p>
 * The settings are added with the lowest precedence, so that an API that
 * names a bundle of its own keeps it. An HTTP key store configured directly
 * under {@code server.ssl} is kept as well, as Spring refuses a bundle beside
 * it.
 */
public class TlsKeysEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * The name of the SSL bundle of the shared certificate.
     */
    public static final String BUNDLE = "sapl-node";

    static final String CERTIFICATE_CHAIN = "io.sapl.node.tls.certificate-chain";
    static final String PRIVATE_KEY       = "io.sapl.node.tls.private-key";

    private static final String BUNDLE_PREFIX   = "spring.ssl.bundle.pem." + BUNDLE + ".";
    private static final String PROPERTY_SOURCE = "saplNodeTls";

    private static final String ERROR_TLS_INCOMPLETE  = "SAPL Node refused to start. The TLS of both APIs needs both io.sapl.node.tls.certificate-chain and io.sapl.node.tls.private-key.";
    private static final String ACTION_TLS_INCOMPLETE = """
            Configure both PEM files:

              io.sapl.node.tls.certificate-chain: /etc/sapl/tls/server.pem
              io.sapl.node.tls.private-key: /etc/sapl/tls/server.key

            or remove both and name an SSL bundle for each API in
            server.ssl.bundle and sapl.pdp.rsocket.ssl.bundle.""";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        val binder = Binder.get(environment);
        val chain  = binder.bind(CERTIFICATE_CHAIN, String.class).orElse(null);
        val key    = binder.bind(PRIVATE_KEY, String.class).orElse(null);
        if (!StringUtils.hasText(chain) && !StringUtils.hasText(key)) {
            return;
        }
        if (!StringUtils.hasText(chain) || !StringUtils.hasText(key)) {
            throw new SaplStartupConfigurationException(ERROR_TLS_INCOMPLETE, ACTION_TLS_INCOMPLETE);
        }
        val settings = new HashMap<String, Object>();
        settings.put(BUNDLE_PREFIX + "keystore.certificate", "file:" + chain.trim());
        settings.put(BUNDLE_PREFIX + "keystore.private-key", "file:" + key.trim());
        settings.put(BUNDLE_PREFIX + "reload-on-update", "true");
        settings.put("sapl.pdp.rsocket.ssl.bundle", BUNDLE);
        if (!hasDirectHttpKeyStore(binder)) {
            settings.put("server.ssl.bundle", BUNDLE);
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE, settings));
    }

    private static boolean hasDirectHttpKeyStore(Binder binder) {
        return binder.bind("server.ssl.key-store", String.class).isBound()
                || binder.bind("server.ssl.certificate", String.class).isBound();
    }

}
