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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import lombok.val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("X509PolicyInformationPoint")
class X509PolicyInformationPointTests {

    private static final String CTHULHU_DN     = "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=US";
    private static final String YOG_SOTHOTH_DN = "CN=Yog-Sothoth Time Services,O=Beyond the Gate,C=XX";

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
            val now     = Instant.parse("2025-06-15T12:00:00Z");
            val certPem = toPem(
                    generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS)));
            val clock   = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isCurrentlyValid(Value.of(certPem)).take(1)).expectNext(Value.TRUE)
                    .verifyComplete();
        }

        @Test
        @DisplayName("when cert is expired then emits false and completes")
        void whenCertIsExpiredThenEmitsFalse() throws OperatorCreationException, CertificateException, IOException {
            val now     = Instant.parse("2025-06-15T12:00:00Z");
            val certPem = toPem(generateCertificate(YOG_SOTHOTH_DN, now.minus(365, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS)));
            val clock   = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isCurrentlyValid(Value.of(certPem))).expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("when cert is not yet valid then emits false then true at notBefore")
        void whenCertIsNotYetValidThenEmitsFalseThenTrueAtNotBefore()
                throws OperatorCreationException, CertificateException, IOException {
            val now       = Instant.parse("2025-06-15T12:00:00Z");
            val notBefore = now.plus(10, ChronoUnit.SECONDS);
            val notAfter  = now.plus(60, ChronoUnit.SECONDS);
            val certPem   = toPem(generateCertificate(CTHULHU_DN, notBefore, notAfter));
            val clock     = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.<Value>withVirtualTime(() -> sut.isCurrentlyValid(Value.of(certPem))).expectNext(Value.FALSE)
                    .thenAwait(Duration.ofSeconds(10)).expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("when cert is valid then emits true then false at notAfter")
        void whenCertIsValidThenEmitsTrueThenFalseAtNotAfter()
                throws OperatorCreationException, CertificateException, IOException {
            val now      = Instant.parse("2025-06-15T12:00:00Z");
            val notAfter = now.plus(30, ChronoUnit.SECONDS);
            val certPem  = toPem(generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.DAYS), notAfter));
            val clock    = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.<Value>withVirtualTime(() -> sut.isCurrentlyValid(Value.of(certPem))).expectNext(Value.TRUE)
                    .thenAwait(Duration.ofSeconds(30)).expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("when cert is not yet valid then emits all three transitions")
        void whenCertIsNotYetValidThenEmitsAllTransitions()
                throws OperatorCreationException, CertificateException, IOException {
            val now       = Instant.parse("2025-06-15T12:00:00Z");
            val notBefore = now.plus(10, ChronoUnit.SECONDS);
            val notAfter  = now.plus(60, ChronoUnit.SECONDS);
            val certPem   = toPem(generateCertificate(CTHULHU_DN, notBefore, notAfter));
            val clock     = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.<Value>withVirtualTime(() -> sut.isCurrentlyValid(Value.of(certPem))).expectNext(Value.FALSE)
                    .thenAwait(Duration.ofSeconds(10)).expectNext(Value.TRUE).thenAwait(Duration.ofSeconds(50))
                    .expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("when PEM is malformed then emits error")
        void whenCertPemIsMalformedThenEmitsError() {
            val clock = mock(Clock.class);
            val sut   = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isCurrentlyValid(Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh")))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("when cert is valid then isExpired emits false")
        void whenCertIsValidThenIsExpiredEmitsFalse()
                throws OperatorCreationException, CertificateException, IOException {
            val now     = Instant.parse("2025-06-15T12:00:00Z");
            val certPem = toPem(
                    generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS)));
            val clock   = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isExpired(Value.of(certPem)).take(1)).expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("when cert is expired then isExpired emits true")
        void whenCertIsExpiredThenIsExpiredEmitsTrue()
                throws OperatorCreationException, CertificateException, IOException {
            val now     = Instant.parse("2025-06-15T12:00:00Z");
            val certPem = toPem(generateCertificate(YOG_SOTHOTH_DN, now.minus(365, ChronoUnit.DAYS),
                    now.minus(1, ChronoUnit.DAYS)));
            val clock   = mock(Clock.class);
            when(clock.instant()).thenReturn(now);
            val sut = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isExpired(Value.of(certPem))).expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("when PEM is malformed then isExpired emits error")
        void whenCertPemIsMalformedThenIsExpiredEmitsError() {
            val clock = mock(Clock.class);
            val sut   = new X509PolicyInformationPoint(clock);

            StepVerifier.create(sut.isExpired(Value.of("not a certificate")))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("broker loading")
    class BrokerLoading {

        @Test
        @DisplayName("when loaded into broker then registers under x509 namespace")
        void whenLoadedIntoBrokerThenRegistersUnderX509Namespace() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);
            val pip        = new X509PolicyInformationPoint(Clock.systemUTC());

            broker.loadPolicyInformationPointLibrary(pip);

            assertThat(broker.getLoadedLibraryNames()).contains("x509");
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
