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
package io.sapl.attributes.libraries.vnext;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.api.test.stream.TestTimeScheduler;
import lombok.val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@DisplayName("X509PolicyInformationPoint (vnext)")
class X509PolicyInformationPointTests {

    private static final String  CTHULHU_DN     = "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=US";
    private static final String  YOG_SOTHOTH_DN = "CN=Yog-Sothoth Time Services,O=Beyond the Gate,C=XX";
    private static final Instant NOW            = Instant.parse("2025-06-15T12:00:00Z");

    private static KeyPair keyPair;

    @BeforeAll
    static void setup() throws NoSuchAlgorithmException {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Nested
    @DisplayName("isCurrentlyValid")
    class IsCurrentlyValid {

        @Test
        @DisplayName("when cert is currently valid then emits true")
        void whenCertIsCurrentlyValidThenEmitsTrue()
                throws OperatorCreationException, CertificateException, IOException {
            val certPem   = toPem(
                    generateCertificate(CTHULHU_DN, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS)));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("when cert is expired then emits false and completes")
        void whenCertIsExpiredThenEmitsFalse() throws OperatorCreationException, CertificateException, IOException {
            val certPem   = toPem(generateCertificate(YOG_SOTHOTH_DN, NOW.minus(365, ChronoUnit.DAYS),
                    NOW.minus(1, ChronoUnit.DAYS)));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("when cert is not yet valid then emits false then true at notBefore")
        void whenCertIsNotYetValidThenEmitsFalseThenTrueAtNotBefore()
                throws OperatorCreationException, CertificateException, IOException {
            val notBefore = NOW.plus(10, ChronoUnit.SECONDS);
            val notAfter  = NOW.plus(60, ChronoUnit.SECONDS);
            val certPem   = toPem(generateCertificate(CTHULHU_DN, notBefore, notAfter));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                scheduler.advanceTo(notBefore);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("when cert is valid then emits true then false at notAfter")
        void whenCertIsValidThenEmitsTrueThenFalseAtNotAfter()
                throws OperatorCreationException, CertificateException, IOException {
            val notAfter  = NOW.plus(30, ChronoUnit.SECONDS);
            val certPem   = toPem(generateCertificate(CTHULHU_DN, NOW.minus(1, ChronoUnit.DAYS), notAfter));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                scheduler.advanceTo(notAfter);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("when cert is not yet valid then emits all three transitions")
        void whenCertIsNotYetValidThenEmitsAllTransitions()
                throws OperatorCreationException, CertificateException, IOException {
            val notBefore = NOW.plus(10, ChronoUnit.SECONDS);
            val notAfter  = NOW.plus(60, ChronoUnit.SECONDS);
            val certPem   = toPem(generateCertificate(CTHULHU_DN, notBefore, notAfter));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                scheduler.advanceTo(notBefore);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                scheduler.advanceTo(notAfter);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("when PEM is malformed then emits error")
        void whenCertPemIsMalformedThenEmitsError() {
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isCurrentlyValid(Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh"))) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                }).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("when cert is valid then isExpired emits false")
        void whenCertIsValidThenIsExpiredEmitsFalse()
                throws OperatorCreationException, CertificateException, IOException {
            val certPem   = toPem(
                    generateCertificate(CTHULHU_DN, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(365, ChronoUnit.DAYS)));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isExpired(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("when cert is expired then isExpired emits true")
        void whenCertIsExpiredThenIsExpiredEmitsTrue()
                throws OperatorCreationException, CertificateException, IOException {
            val certPem   = toPem(generateCertificate(YOG_SOTHOTH_DN, NOW.minus(365, ChronoUnit.DAYS),
                    NOW.minus(1, ChronoUnit.DAYS)));
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isExpired(Value.of(certPem))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("when PEM is malformed then isExpired emits error")
        void whenCertPemIsMalformedThenIsExpiredEmitsError() {
            val clock     = new MutableClock(NOW);
            val scheduler = new TestTimeScheduler(NOW);
            val sut       = new X509PolicyInformationPoint(clock, scheduler);

            try (val stream = sut.isExpired(Value.of("not a certificate"))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                }).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("store registration")
    class StoreRegistration {

        @Test
        @Disabled("TODO: enable when AttributeMethodSignatureProcessor accepts Stream<Value>")
        @DisplayName("loads under the x509 namespace without errors")
        void whenLoadedIntoStoreThenRegistersUnderX509Namespace() {
        }
    }

    private static X509Certificate generateCertificate(String subjectDn, Instant notBefore, Instant notAfter)
            throws OperatorCreationException, CertificateException, IOException {
        val subject     = new X500Name(subjectDn);
        val certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(notBefore), Date.from(notAfter), subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        val signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        val holder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
        val encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }

}
