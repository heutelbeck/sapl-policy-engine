/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class X509FunctionLibraryTests {

    private static String validCertPem;
    private static String expiredCertPem;
    private static String certWithSansPem;

    @BeforeAll
    static void setup() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();

        var testCertificate = generateCertificate(keyPair, "CN=Test Certificate,O=Test Org,C=US", false, false);
        validCertPem = toPem(testCertificate);

        var expiredCert = generateCertificate(keyPair, "CN=Expired,O=Test Org,C=US", true, false);
        expiredCertPem = toPem(expiredCert);

        var certWithSans = generateCertificate(keyPair, "CN=example.com,O=Test,C=US", false, true);
        certWithSansPem = toPem(certWithSans);
    }

    /* Parse Certificate Tests */

    @Test
    void parseCertificate_whenValidPem_returnsStructuredObject() {
        var result = X509FunctionLibrary.parseCertificate(Val.of(validCertPem));
        assertThat(result.isDefined()).isTrue();

        var jsonNode = result.get();
        assertThat(jsonNode.has("subject")).isTrue();
        assertThat(jsonNode.has("issuer")).isTrue();
        assertThat(jsonNode.has("serialNumber")).isTrue();
        assertThat(jsonNode.has("notBefore")).isTrue();
        assertThat(jsonNode.has("notAfter")).isTrue();
    }

    @Test
    void parseCertificate_whenInvalidPem_returnsError() {
        var result = X509FunctionLibrary.parseCertificate(Val.of("invalid certificate data"));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void parseCertificate_extractsSubject() {
        var result = X509FunctionLibrary.parseCertificate(Val.of(validCertPem));
        assertThat(result.get().get("subject").asText()).contains("Test Certificate");
    }

    @Test
    void parseCertificate_extractsSerialNumber() {
        var result = X509FunctionLibrary.parseCertificate(Val.of(validCertPem));
        assertThat(result.get().get("serialNumber").asText()).isNotEmpty();
    }

    /* Extract Field Tests */

    @ParameterizedTest
    @MethodSource("provideFieldExtractors")
    void extractField_whenValidCert_returnsExpectedValue(Function<Val, Val> extractor, String expectedContent,
            String fieldDescription) {
        var result = extractor.apply(Val.of(validCertPem));
        assertThat(result.isDefined()).as("%s should be defined", fieldDescription).isTrue();
        assertThat(result.getText()).as("%s should contain expected content", fieldDescription)
                .contains(expectedContent);
    }

    @ParameterizedTest
    @MethodSource("provideFieldExtractors")
    void extractField_whenInvalidCert_returnsError(Function<Val, Val> extractor, String expectedContent,
            String fieldDescription) {
        var result = extractor.apply(Val.of("invalid"));
        assertThat(result.isError()).as("%s extraction should fail for invalid cert", fieldDescription).isTrue();
    }

    private static Stream<Arguments> provideFieldExtractors() {
        return Stream.of(
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSubjectDn, "Test Certificate", "Subject DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractIssuerDn, "Test Certificate", "Issuer DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSerialNumber, "", "Serial Number"));
    }

    /* Extract Validity Date Tests */

    @ParameterizedTest
    @MethodSource("provideValidityDateExtractors")
    void extractValidityDate_whenValidCert_returnsIsoDate(Function<Val, Val> extractor, String fieldDescription) {
        var result = extractor.apply(Val.of(validCertPem));
        assertThat(result.isDefined()).as("%s should be defined", fieldDescription).isTrue();
        assertThat(result.getText()).as("%s should be ISO-8601 format", fieldDescription).endsWith("Z").contains("T");
    }

    @ParameterizedTest
    @MethodSource("provideValidityDateExtractors")
    void extractValidityDate_whenInvalidCert_returnsError(Function<Val, Val> extractor, String fieldDescription) {
        var result = extractor.apply(Val.of("invalid"));
        assertThat(result.isError()).as("%s extraction should fail for invalid cert", fieldDescription).isTrue();
    }

    private static Stream<Arguments> provideValidityDateExtractors() {
        return Stream.of(arguments((Function<Val, Val>) X509FunctionLibrary::extractNotBefore, "NotBefore"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractNotAfter, "NotAfter"));
    }

    /* Extract Fingerprint Tests */

    @ParameterizedTest
    @CsvSource({ "SHA-256,  64", "SHA-1,    40", "SHA-512,  128" })
    void extractFingerprint_whenValidAlgorithm_computesFingerprint(String algorithm, int expectedLength) {
        var result = X509FunctionLibrary.extractFingerprint(Val.of(validCertPem), Val.of(algorithm));
        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).hasSize(expectedLength);
    }

    @Test
    void extractFingerprint_whenInvalidAlgorithm_returnsError() {
        var result = X509FunctionLibrary.extractFingerprint(Val.of(validCertPem), Val.of("INVALID-ALG"));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void extractFingerprint_isConsistent() {
        var result1 = X509FunctionLibrary.extractFingerprint(Val.of(validCertPem), Val.of("SHA-256"));
        var result2 = X509FunctionLibrary.extractFingerprint(Val.of(validCertPem), Val.of("SHA-256"));
        assertThat(result1.getText()).isEqualTo(result2.getText());
    }

    /* Extract Subject Alternative Names Tests */

    @Test
    void extractSubjectAltNames_whenNoSans_returnsEmptyArray() {
        var result = X509FunctionLibrary.extractSubjectAltNames(Val.of(validCertPem));
        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
    }

    @Test
    void extractSubjectAltNames_whenCertHasSans_returnsSanList() {
        var result = X509FunctionLibrary.extractSubjectAltNames(Val.of(certWithSansPem));
        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().isEmpty()).isFalse();

        var firstSan = result.get().get(0);
        assertThat(firstSan.has("type")).isTrue();
        assertThat(firstSan.has("value")).isTrue();
    }

    @Test
    void extractSubjectAltNames_whenInvalidCert_returnsError() {
        var result = X509FunctionLibrary.extractSubjectAltNames(Val.of("invalid"));
        assertThat(result.isError()).isTrue();
    }

    /* Validity Check Tests */

    @ParameterizedTest
    @CsvSource({ "valid,   false, 'valid certificate should not be expired'",
            "expired, true,  'expired certificate should be expired'" })
    void isExpired_whenVariousCerts_returnsExpectedResult(String certType, boolean expected, String scenario) {
        var certPem = "valid".equals(certType) ? validCertPem : expiredCertPem;
        var result  = X509FunctionLibrary.isExpired(Val.of(certPem));
        assertThat(result.getBoolean()).as(scenario).isEqualTo(expected);
    }

    @Test
    void isExpired_whenInvalidCert_returnsError() {
        var result = X509FunctionLibrary.isExpired(Val.of("invalid"));
        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "1,    DAYS,  true,  'within validity period'", "-365, DAYS,  false, 'before validity start'",
            "400,  DAYS,  false, 'after validity end'" })
    void isValidAt_whenVariousTimestamps_returnsExpectedResult(long amount, ChronoUnit unit, boolean expected,
            String scenario) {
        var timestamp = Instant.now().plus(amount, unit).toString();
        var result    = X509FunctionLibrary.isValidAt(Val.of(validCertPem), Val.of(timestamp));
        assertThat(result.getBoolean()).as(scenario).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-timestamp", "invalid-format", "2024-13-45T99:99:99Z" })
    void isValidAt_whenInvalidTimestamp_returnsError(String timestamp) {
        var result = X509FunctionLibrary.isValidAt(Val.of(validCertPem), Val.of(timestamp));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void isValidAt_whenInvalidCert_returnsError() {
        var timestamp = Instant.now().toString();
        var result    = X509FunctionLibrary.isValidAt(Val.of("invalid"), Val.of(timestamp));
        assertThat(result.isError()).isTrue();
    }

    /* Helper Methods */

    private static X509Certificate generateCertificate(KeyPair keyPair, String subjectDn, boolean expired,
            boolean withSans) throws Exception {
        var now       = Instant.now();
        var notBefore = expired ? now.minus(365, ChronoUnit.DAYS) : now.minus(1, ChronoUnit.DAYS);
        var notAfter  = expired ? now.minus(1, ChronoUnit.DAYS) : now.plus(365, ChronoUnit.DAYS);

        var subject = new X500Name(subjectDn);

        var certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(notBefore), Date.from(notAfter), subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        if (withSans) {
            var sans = new GeneralNames(new GeneralName[] { new GeneralName(GeneralName.dNSName, "example.com"),
                    new GeneralName(GeneralName.dNSName, "www.example.com"),
                    new GeneralName(GeneralName.iPAddress, "192.168.1.1") });
            certBuilder.addExtension(Extension.subjectAlternativeName, false, sans);
        }

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var holder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static String toPem(X509Certificate certificate) throws Exception {
        var encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }
}
