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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerSslBundle;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Warns when the TLS certificate of an API expires within
 * {@link #WARNING_PERIOD}, at the start and once a day, until it is renewed.
 * Of a chain, the certificate that expires first decides, as the chain is no
 * longer valid from then on. The check reads the bundles as they are at the
 * time, so a reloaded certificate is checked the next day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CertificateExpiryWarning implements DisposableBean {

    /**
     * How long before its expiry a certificate is reported, the default of
     * Spring Boot's SSL health indicator.
     */
    static final Duration WARNING_PERIOD = Duration.ofDays(14);

    private static final Duration CHECK_INTERVAL = Duration.ofDays(1);

    private static final String WARN_CERTIFICATE_EXPIRED = "The TLS certificate of the %s API expired on %s. Clients refuse it until it is renewed.";
    private static final String WARN_CERTIFICATE_EXPIRES = "The TLS certificate of the %s API expires on %s, %s. Renew it before then.";

    private final Environment                environment;
    private final ObjectProvider<SslBundles> sslBundles;

    // The thread is started with the first task, at the start of the node.
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        val thread = new Thread(runnable, "sapl-certificate-expiry");
        thread.setDaemon(true);
        return thread;
    });

    @EventListener
    void onReady(ApplicationReadyEvent event) {
        warnOfExpiringCertificates();
        scheduler.scheduleAtFixedRate(this::warnOfExpiringCertificates, CHECK_INTERVAL.toMinutes(),
                CHECK_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    // Runs on the scheduler thread, where an exception would end the daily
    // check, so every failure is logged and the check runs again tomorrow.
    void warnOfExpiringCertificates() {
        try {
            val now = Instant.now();
            for (val entry : bundlesByApi().entrySet()) {
                val bundle = entry.getValue();
                earliestExpiry(bundle.getStores().getKeyStore(), bundle.getKey().getAlias())
                        .flatMap(expiry -> warning(entry.getKey(), expiry, now)).ifPresent(log::warn);
            }
        } catch (RuntimeException | KeyStoreException e) {
            log.debug("The expiry of the TLS certificates could not be checked: {}", e.getMessage());
        }
    }

    private Map<String, SslBundle> bundlesByApi() {
        val bundles = new LinkedHashMap<String, SslBundle>();
        val ssl     = Binder.get(environment).bind("server.ssl", Ssl.class).orElse(null);
        val all     = sslBundles.getIfAvailable();
        if (Ssl.isEnabled(ssl)) {
            bundles.put("HTTP", WebServerSslBundle.get(ssl, all));
        }
        val rsocketBundle = environment.getProperty("sapl.pdp.rsocket.ssl.bundle");
        if (all != null && environment.getProperty("sapl.pdp.rsocket.enabled", Boolean.class, true)
                && StringUtils.hasText(rsocketBundle)) {
            bundles.put("RSocket", all.getBundle(rsocketBundle));
        }
        return bundles;
    }

    /**
     * The warning of a certificate, if it expires within the warning period or
     * has expired.
     *
     * @param api the name of the API
     * @param expiry the end of the validity of the certificate
     * @param now the current time
     * @return the warning, or empty if the expiry is further away
     */
    static Optional<String> warning(String api, Instant expiry, Instant now) {
        val remaining = Duration.between(now, expiry);
        val date      = expiry.truncatedTo(ChronoUnit.SECONDS);
        if (remaining.isNegative()) {
            return Optional.of(WARN_CERTIFICATE_EXPIRED.formatted(api, date));
        }
        if (remaining.compareTo(WARNING_PERIOD) > 0) {
            return Optional.empty();
        }
        return Optional.of(WARN_CERTIFICATE_EXPIRES.formatted(api, date, inDays(remaining)));
    }

    private static String inDays(Duration remaining) {
        val days = remaining.toDays();
        if (days == 0) {
            return "in less than a day";
        }
        return days == 1 ? "in 1 day" : "in " + days + " days";
    }

    /**
     * The earliest end of validity of the certificates of a key store.
     *
     * @param keyStore the key store
     * @param alias the alias of the key whose chain is served, or null for
     * every key
     * @return the earliest expiry, or empty if the key store holds no
     * certificate
     * @throws KeyStoreException if the key store is not loaded
     */
    static Optional<Instant> earliestExpiry(KeyStore keyStore, @Nullable String alias) throws KeyStoreException {
        val     aliases  = alias != null ? List.of(alias) : Collections.list(keyStore.aliases());
        Instant earliest = null;
        for (val name : aliases) {
            val chain = keyStore.getCertificateChain(name);
            if (chain == null) {
                continue;
            }
            for (val certificate : chain) {
                if (certificate instanceof X509Certificate x509) {
                    val expiry = x509.getNotAfter().toInstant();
                    if (earliest == null || expiry.isBefore(earliest)) {
                        earliest = expiry;
                    }
                }
            }
        }
        return Optional.ofNullable(earliest);
    }

}
