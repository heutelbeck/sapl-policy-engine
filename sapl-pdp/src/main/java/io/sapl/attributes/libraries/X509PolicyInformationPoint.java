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
package io.sapl.attributes.libraries;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.libraries.crypto.CertificateUtils;
import io.sapl.functions.libraries.crypto.CryptoException;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;

/**
 * Policy Information Point for reactive X.509 certificate validity monitoring.
 * <p>
 * Provides entity attributes that emit boolean values reflecting the current
 * validity state of an X.509 certificate, automatically transitioning when the
 * certificate reaches its notBefore or notAfter boundaries.
 *
 * @see io.sapl.functions.libraries.X509FunctionLibrary
 */
@RequiredArgsConstructor
@PolicyInformationPoint(name = X509PolicyInformationPoint.NAME, description = X509PolicyInformationPoint.DESCRIPTION, pipDocumentation = X509PolicyInformationPoint.DOCUMENTATION)
public class X509PolicyInformationPoint {

    public static final String NAME        = "x509";
    public static final String DESCRIPTION = "Reactive certificate validity monitoring for X.509 certificates in mTLS and PKI scenarios.";

    public static final String DOCUMENTATION = """
            # X.509 Certificate Validity Monitoring

            This Policy Information Point reactively monitors the validity state of X.509
            certificates. Attributes emit boolean values that automatically update when
            a certificate transitions between validity states at its notBefore and notAfter
            boundaries.

            ## Validity States and Transitions

            A certificate progresses through up to three states:

            * **Not yet valid** (before notBefore): `isCurrentlyValid` emits `false`, `isExpired` emits `true`
            * **Valid** (between notBefore and notAfter): `isCurrentlyValid` emits `true`, `isExpired` emits `false`
            * **Expired** (after notAfter): `isCurrentlyValid` emits `false`, `isExpired` emits `true`

            State transitions happen automatically at the exact boundary instants with zero
            polling overhead. Each transition triggers policy re-evaluation.

            ## Reactive Behavior

            Example timeline for a certificate with notBefore=now+10s and notAfter=now+60s:
            * t=0s:  Emits false (not yet valid)
            * t=10s: Emits true (certificate became valid, policy re-evaluated)
            * t=60s: Emits false (certificate expired, policy re-evaluated)

            ## Usage Examples

            Require a valid client certificate for API access:
            ```sapl
            policy "require valid client cert"
            permit action == "api.call";
                subject.clientCertificate.<x509.isCurrentlyValid>;
            ```

            Deny access when the certificate has expired:
            ```sapl
            policy "reject expired certs"
            deny
                subject.clientCertificate.<x509.isExpired>;
            ```
            """;

    private static final String ERROR_FAILED_TO_PARSE_CERTIFICATE = "Failed to parse certificate: %s.";

    private final Clock clock;

    /**
     * Reactively monitors whether an X.509 certificate is currently within its
     * validity period.
     * <p>
     * Emits {@code true} when the current time is between the certificate's
     * notBefore and notAfter dates, and {@code false} otherwise. Automatically
     * transitions at the exact boundary instants.
     *
     * @param certPem the PEM-encoded X.509 certificate
     * @return a reactive stream of boolean values reflecting current validity
     */
    @Attribute(docs = """
            ```(TEXT certPem).<isCurrentlyValid>``` reactively monitors whether an X.509 certificate
            is currently within its validity period (between notBefore and notAfter).

            Emits `true` when the certificate is valid, `false` when it is not yet valid or has
            expired. Automatically transitions at the exact boundary instants, triggering policy
            re-evaluation with zero polling overhead.

            Example:
            ```sapl
            policy "require valid client cert"
            permit action == "api.call";
                subject.clientCertificate.<x509.isCurrentlyValid>;
            ```
            """)
    public Flux<Value> isCurrentlyValid(TextValue certPem) {
        try {
            val certificate = CertificateUtils.parseCertificate(certPem.value());
            val notBefore   = certificate.getNotBefore().toInstant();
            val notAfter    = certificate.getNotAfter().toInstant();
            val now         = clock.instant();

            if (now.isAfter(notAfter)) {
                return Flux.just(Value.FALSE);
            }

            if (now.isBefore(notBefore)) {
                val untilValid   = Duration.between(now, notBefore);
                val validitySpan = Duration.between(notBefore, notAfter);
                return Flux.concat(Mono.just(Value.FALSE), Mono.just(Value.TRUE).delayElement(untilValid),
                        Mono.just(Value.FALSE).delayElement(validitySpan));
            }

            val untilExpired = Duration.between(now, notAfter);
            return Flux.concat(Mono.just(Value.TRUE), Mono.just(Value.FALSE).delayElement(untilExpired));

        } catch (CertificateException | CryptoException exception) {
            return Flux.just(Value.error(ERROR_FAILED_TO_PARSE_CERTIFICATE.formatted(exception.getMessage())));
        }
    }

    /**
     * Reactively monitors whether an X.509 certificate has expired or is not
     * yet valid.
     * <p>
     * This is the logical inverse of {@link #isCurrentlyValid(TextValue)}.
     * Emits {@code true} when the certificate is outside its validity period,
     * and {@code false} when it is valid.
     *
     * @param certPem the PEM-encoded X.509 certificate
     * @return a reactive stream of boolean values reflecting expiration state
     */
    @Attribute(docs = """
            ```(TEXT certPem).<isExpired>``` reactively monitors whether an X.509 certificate
            has expired or is not yet valid. This is the inverse of `isCurrentlyValid`.

            Emits `true` when the certificate is outside its validity period (before notBefore
            or after notAfter), `false` when the certificate is valid. Automatically transitions
            at the exact boundary instants.

            Example:
            ```sapl
            policy "reject expired certs"
            deny
                subject.clientCertificate.<x509.isExpired>;
            ```
            """)
    public Flux<Value> isExpired(TextValue certPem) {
        return isCurrentlyValid(certPem).map(v -> v instanceof BooleanValue(var b) ? Value.of(!b) : v);
    }

}
