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
import io.sapl.api.stream.Stream;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Streams;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.functions.libraries.crypto.CertificateUtils;
import io.sapl.functions.libraries.crypto.CryptoException;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Policy Information Point for reactive X.509 certificate validity monitoring.
 * <p>
 * Provides entity attributes that emit boolean values reflecting the current
 * validity state of an X.509 certificate, automatically transitioning when the
 * certificate becomes valid or expires.
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

            Both attributes emit their current value as soon as an observer opens.

            | Certificate time | `isCurrentlyValid` | `isExpired` |
            | --- | --- | --- |
            | Before `notBefore` | false | false |
            | From `notBefore` through `notAfter`, including both endpoints | true | false |
            | After `notAfter` | false | true |

            A continuing validity observer receives `true` at `notBefore`. Expiry takes effect
            one nanosecond after `notAfter`, when validity becomes false and expiration becomes true.

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

    // Each individual timer delay must fit in signed nanoseconds even for year-9999 certificates.
    private static final Duration TIMER_HORIZON = Duration.ofDays(100L * 365);

    private final Clock         clock;
    private final TimeScheduler scheduler;

    /**
     * Reactively monitors whether an X.509 certificate is currently within its
     * validity period.
     * <p>
     * Emits {@code true} when the current time is between the certificate's
     * notBefore and notAfter dates, including both endpoints, and {@code false} otherwise.
     *
     * @param certPem the PEM-encoded X.509 certificate
     * @return a reactive stream of boolean values reflecting current validity
     */
    @Attribute(docs = """
            ```(TEXT certPem).<isCurrentlyValid>``` reactively monitors whether an X.509 certificate
            is currently within its validity period from notBefore through notAfter.

            Emits `true` when the certificate is valid, `false` when it is not yet valid or has
            expired. Both endpoints are included.

            Example:
            ```sapl
            policy "require valid client cert"
            permit action == "api.call";
                subject.clientCertificate.<x509.isCurrentlyValid>;
            ```
            """)
    public Stream<Value> isCurrentlyValid(TextValue certPem) {
        return validityStream(certPem, false);
    }

    /**
     * Monitors whether the current instant is strictly after the certificate's notAfter.
     *
     * @param certPem the PEM-encoded X.509 certificate
     * @return a stream that becomes true when the certificate expires
     */
    @Attribute(docs = """
            ```(TEXT certPem).<isExpired>``` is true only after the certificate's `notAfter`
            instant. It is false before the validity period begins and throughout the validity period.

            ```sapl
            policy "reject expired certs"
            deny
                subject.clientCertificate.<x509.isExpired>;
            ```
            """)
    public Stream<Value> isExpired(TextValue certPem) {
        return validityStream(certPem, true);
    }

    private Stream<Value> validityStream(TextValue certPem, boolean expiry) {
        try {
            val certificate = CertificateUtils.parseCertificate(certPem.value());
            val start       = certificate.getNotBefore().toInstant();
            val end         = certificate.getNotAfter().toInstant();
            val now         = clock.instant();
            if (now.isAfter(end)) {
                return Streams.just(Value.of(expiry));
            }
            if (!expiry && end.isBefore(start)) {
                return Streams.just(Value.FALSE);
            }
            Stream<Value> initial = Streams.just(Value.of(!expiry && !now.isBefore(start)));
            if (!expiry && now.isBefore(start)) {
                initial = Streams.concat(initial, atBoundary(Value.TRUE, start, now));
            }
            val expiredAt = end.plusNanos(1);
            return Streams.concat(initial, atBoundary(Value.of(expiry), expiredAt, now));
        } catch (CertificateException | CryptoException exception) {
            return Streams.error(ERROR_FAILED_TO_PARSE_CERTIFICATE.formatted(exception.getMessage()));
        }
    }

    private Stream<Value> atBoundary(Value state, Instant boundary, Instant now) {
        if (Duration.between(now, boundary).compareTo(TIMER_HORIZON) <= 0) {
            return Streams.scheduledAt(state, boundary, scheduler);
        }
        val delivered = new AtomicBoolean();
        return Streams.fromBlockingSource(() -> {
            if (delivered.getAndSet(true)) {
                return null;
            }
            var cursor = now;
            while (Duration.between(cursor, boundary).compareTo(TIMER_HORIZON) > 0) {
                val recheck = cursor.plus(TIMER_HORIZON);
                try (val wait = Streams.scheduledAt(state, recheck, scheduler)) {
                    wait.awaitNext();
                }
                val observed = clock.instant();
                cursor = observed.isAfter(recheck) ? observed : recheck;
            }
            try (val wait = Streams.scheduledAt(state, boundary, scheduler)) {
                return wait.awaitNext();
            }
        });
    }

}
