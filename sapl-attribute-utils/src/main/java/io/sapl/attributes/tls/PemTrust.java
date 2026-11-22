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
package io.sapl.attributes.tls;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import lombok.val;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;

/**
 * TLS trust anchors shared by operator-configured endpoints.
 *
 * @param trustedCaPem PEM-encoded X.509 certificate bundle
 * @param includeDefaultTrustRoots whether to add the platform trust anchors
 */
public record PemTrust(String trustedCaPem, boolean includeDefaultTrustRoots) {
    private static final String BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String END = "-----END CERTIFICATE-----";
    private static final String ERROR_INLINE = "Configure TLS trust in variables.tlsTrustProfiles and reference it with trustProfile.";
    private static final String ERROR_INVALID_BUNDLE = "trustedCaPem must contain a nonempty PEM bundle of X.509 certificates.";
    private static final String ERROR_INVALID_FLAG = "includeDefaultTrustRoots must be a boolean.";
    private static final String ERROR_NAMED_PROFILE = "Invalid variables.tlsTrustProfiles.%s: %s";
    private static final String ERROR_PROFILE = "A TLS trust profile must be a named object containing trustedCaPem and optionally includeDefaultTrustRoots.";
    private static final String ERROR_REFERENCE = "trustProfile must name an existing TLS trust profile.";
    private static final String ERROR_TLS_CONTEXT = "Cannot initialize the TLS trust context.";
    private static final String INCLUDE_DEFAULTS = "includeDefaultTrustRoots";
    private static final String PEM = "trustedCaPem";
    private static final String PROFILES = "tlsTrustProfiles";
    private static final String REFERENCE = "trustProfile";

    public PemTrust {
        if (trustedCaPem == null) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
    }

    /**
     * Resolves an endpoint's named trust profile.
     *
     * @param variables operator configuration variables
     * @param endpoint selected operator endpoint
     * @return the selected trust, or null to use ordinary platform trust
     * @throws IllegalArgumentException if the trust reference or profile is invalid
     */
    public static @Nullable PemTrust resolve(ObjectValue variables, ObjectValue endpoint) {
        if (endpoint.containsKey(PEM) || endpoint.containsKey(INCLUDE_DEFAULTS)) {
            throw new IllegalArgumentException(ERROR_INLINE);
        }
        if (!endpoint.containsKey(REFERENCE)) {
            return null;
        }
        if (!(endpoint.get(REFERENCE) instanceof TextValue(var name)) || name.isBlank()
                || !(variables.get(PROFILES) instanceof ObjectValue profiles)
                || !(profiles.get(name) instanceof ObjectValue profile)) {
            throw new IllegalArgumentException(ERROR_REFERENCE);
        }
        return fromProfile(profile);
    }

    /**
     * Validates every declared profile, including unused profiles, before configuration activation.
     *
     * @param variables operator configuration variables
     * @throws IllegalArgumentException if a declaration or any certificate is invalid
     */
    public static void validateProfiles(ObjectValue variables) {
        if (!variables.containsKey(PROFILES)) {
            return;
        }
        if (!(variables.get(PROFILES) instanceof ObjectValue profiles)) {
            throw new IllegalArgumentException(ERROR_PROFILE);
        }
        for (val item : profiles.entrySet()) {
            if (item.getKey().isBlank() || !(item.getValue() instanceof ObjectValue profile)) {
                throw new IllegalArgumentException(ERROR_PROFILE);
            }
            try {
                fromProfile(profile).trustManagerFactory();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(ERROR_NAMED_PROFILE.formatted(item.getKey(), e.getMessage()), e);
            }
        }
    }

    private static PemTrust fromProfile(ObjectValue profile) {
        if (!Set.of(PEM, INCLUDE_DEFAULTS).containsAll(profile.keySet())) {
            throw new IllegalArgumentException(ERROR_PROFILE);
        }
        if (profile.containsKey(INCLUDE_DEFAULTS) && !(profile.get(INCLUDE_DEFAULTS) instanceof BooleanValue)) {
            throw new IllegalArgumentException(ERROR_INVALID_FLAG);
        }
        if (!(profile.get(PEM) instanceof TextValue(var pem))) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
        return new PemTrust(pem, profile.get(INCLUDE_DEFAULTS) instanceof BooleanValue(var include) && include);
    }

    /**
     * Creates the JSSE trust manager factory for this bundle.
     *
     * @return initialized trust manager factory
     * @throws IllegalArgumentException if the bundle or trust configuration is invalid
     */
    public TrustManagerFactory trustManagerFactory() {
        try {
            val store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            addCertificates(store);
            if (includeDefaultTrustRoots) {
                val defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                defaults.init((KeyStore) null);
                for (val manager : defaults.getTrustManagers()) {
                    if (manager instanceof X509TrustManager x509) {
                        for (val certificate : x509.getAcceptedIssuers()) {
                            store.setCertificateEntry("default-" + store.size(), certificate);
                        }
                    }
                }
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            return factory;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE, e);
        }
    }

    private void addCertificates(KeyStore store) throws GeneralSecurityException {
        val factory = CertificateFactory.getInstance("X.509");
        if (trustedCaPem.chars().anyMatch(c -> c > 126 || (c < 32 && (c < 9 || c > 13)))) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
        val lines = trustedCaPem.lines().map(String::trim).filter(line -> !line.isEmpty()).iterator();
        while (lines.hasNext()) {
            if (!BEGIN.equals(lines.next())) {
                throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
            }
            store.setCertificateEntry("custom-" + store.size(), readCertificate(lines, factory));
        }
        if (store.size() == 0) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
    }

    private static Certificate readCertificate(Iterator<String> lines, CertificateFactory factory)
            throws CertificateException {
        val encoded = new StringBuilder();
        while (lines.hasNext()) {
            val line = lines.next();
            if (END.equals(line)) {
                return decodeCertificate(encoded.toString(), factory);
            }
            encoded.append(line);
        }
        throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
    }

    private static Certificate decodeCertificate(String encoded, CertificateFactory factory)
            throws CertificateException {
        final byte[] der;
        try {
            der = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE, e);
        }
        if (!Base64.getEncoder().encodeToString(der).equals(encoded)) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
        val cert = factory.generateCertificate(new ByteArrayInputStream(der));
        if (!Arrays.equals(der, cert.getEncoded())) {
            throw new IllegalArgumentException(ERROR_INVALID_BUNDLE);
        }
        return cert;
    }

    /**
     * Creates a TLS context using this entry's trust anchors.
     *
     * @return initialized TLS context
     * @throws IllegalArgumentException if the bundle or trust configuration is invalid
     */
    public SSLContext sslContext() {
        try {
            val context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory().getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(ERROR_TLS_CONTEXT, e);
        }
    }

    @Override
    public String toString() {
        return "PemTrust[includeDefaultTrustRoots=" + includeDefaultTrustRoots + "]";
    }
}
