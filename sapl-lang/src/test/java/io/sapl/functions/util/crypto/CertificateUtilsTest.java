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
package io.sapl.functions.util.crypto;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;

import static io.sapl.functions.util.crypto.CryptoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class CertificateUtilsTest {

    @BeforeAll
    static void setupBouncyCastle() {
        Security.addProvider(new BouncyCastleProvider());
    }

    /* parseCertificate Tests */

    @Test
    void parseCertificate_withPemFormat_succeeds() throws CertificateEncodingException {
        val certificate    = generateSelfSignedCertificate("CN=Test");
        val pemCertificate = convertToPem(certificate);

        val parsedCertificate = CertificateUtils.parseCertificate(pemCertificate);

        assertNotNull(parsedCertificate);
        assertEquals(certificate.getSubjectX500Principal(), parsedCertificate.getSubjectX500Principal());
        assertArrayEquals(certificate.getEncoded(), parsedCertificate.getEncoded());
    }

    @Test
    void parseCertificate_withDerFormat_succeeds() {
        val certificate    = generateSelfSignedCertificate("CN=Test");
        val derCertificate = convertToDer(certificate);

        val parsedCertificate = CertificateUtils.parseCertificate(derCertificate);

        assertNotNull(parsedCertificate);
        assertEquals(certificate.getSubjectX500Principal(), parsedCertificate.getSubjectX500Principal());
    }

    @Test
    void parseCertificate_withPemFormatWithWhitespace_succeeds() {
        val certificate       = generateSelfSignedCertificate("CN=Test");
        val pemWithWhitespace = "\n\n  " + convertToPem(certificate) + "  \n\n";

        val parsedCertificate = CertificateUtils.parseCertificate(pemWithWhitespace);

        assertNotNull(parsedCertificate);
        assertEquals(certificate.getSubjectX500Principal(), parsedCertificate.getSubjectX500Principal());
    }

    @ParameterizedTest
    @MethodSource("provideCertificatesWithDifferentSubjects")
    void parseCertificate_withDifferentSubjects_preservesSubject(String subject) {
        val certificate    = generateSelfSignedCertificate(subject);
        val pemCertificate = convertToPem(certificate);

        val parsedCertificate = CertificateUtils.parseCertificate(pemCertificate);

        assertEquals(certificate.getSubjectX500Principal().getName(),
                parsedCertificate.getSubjectX500Principal().getName());
    }

    @Test
    void parseCertificate_withInvalidPem_throwsException() {
        val invalidPem = "-----BEGIN CERTIFICATE-----\nINVALID_BASE64\n-----END CERTIFICATE-----";

        val exception = assertThrows(PolicyEvaluationException.class,
                () -> CertificateUtils.parseCertificate(invalidPem));

        assertTrue(exception.getMessage().contains("Invalid Base64")
                || exception.getMessage().contains("Failed to parse"));
    }

    @Test
    void parseCertificate_withMalformedCertificate_throwsException() {
        val malformedCert = "This is not a certificate";

        assertThrows(PolicyEvaluationException.class, () -> CertificateUtils.parseCertificate(malformedCert));
    }

    @Test
    void parseCertificate_withEmptyString_throwsException() {
        assertThrows(PolicyEvaluationException.class, () -> CertificateUtils.parseCertificate(""));
    }

    @Test
    void parseCertificate_withPartialPem_throwsException() {
        val partialPem = "-----BEGIN CERTIFICATE-----\naGVsbG8=\n";

        assertThrows(PolicyEvaluationException.class, () -> CertificateUtils.parseCertificate(partialPem));
    }

    /* encodeCertificate Tests */

    @Test
    void encodeCertificate_withValidCertificate_returnsEncodedBytes() throws CertificateEncodingException {
        val certificate = generateSelfSignedCertificate("CN=Test");

        val encodedBytes = CertificateUtils.encodeCertificate(certificate);

        assertNotNull(encodedBytes);
        assertTrue(encodedBytes.length > 0);
        assertArrayEquals(certificate.getEncoded(), encodedBytes);
    }

    @Test
    void encodeCertificate_resultCanBeParsedBack() throws CertificateEncodingException {
        val originalCertificate = generateSelfSignedCertificate("CN=Test");

        val encodedBytes        = CertificateUtils.encodeCertificate(originalCertificate);
        val base64Encoded       = Base64.getEncoder().encodeToString(encodedBytes);
        val reparsedCertificate = CertificateUtils.parseCertificate(base64Encoded);

        assertEquals(originalCertificate.getSubjectX500Principal(), reparsedCertificate.getSubjectX500Principal());
        assertArrayEquals(originalCertificate.getEncoded(), reparsedCertificate.getEncoded());
    }

    @ParameterizedTest
    @MethodSource("provideCertificatesWithDifferentKeyAlgorithms")
    void encodeCertificate_withDifferentKeyAlgorithms_succeeds(String algorithm) {
        val certificate = generateCertificateWithKeyAlgorithm(algorithm);

        val encodedBytes = CertificateUtils.encodeCertificate(certificate);

        assertNotNull(encodedBytes);
        assertTrue(encodedBytes.length > 0);
    }

    /* extractSubjectAlternativeNames Tests */

    @Test
    void extractSubjectAlternativeNames_withNullSans_returnsNull() {
        val certificate = generateSelfSignedCertificate("CN=Test");

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNull(sans);
    }

    @Test
    void extractSubjectAlternativeNames_withDnsNames_returnsCorrectSans() {
        val dnsNames    = new String[] { "example.com", "www.example.com", "api.example.com" };
        val certificate = generateCertificateWithSans(dnsNames, GeneralName.dNSName);

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNotNull(sans);
        assertEquals(3, sans.size());

        val sanValues = sans.stream().map(san -> san.get(1).toString()).toList();

        for (val dnsName : dnsNames) {
            assertTrue(sanValues.contains(dnsName), "Should contain DNS name: " + dnsName);
        }
    }

    @Test
    void extractSubjectAlternativeNames_withIpAddresses_returnsCorrectSans() {
        val ipAddresses = new String[] { "192.168.1.1", "10.0.0.1" };
        val certificate = generateCertificateWithSans(ipAddresses, GeneralName.iPAddress);

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNotNull(sans);
        assertEquals(2, sans.size());

        for (val san : sans) {
            val sanType = (Integer) san.getFirst();
            assertEquals(GeneralName.iPAddress, sanType);
        }
    }

    @Test
    void extractSubjectAlternativeNames_withEmailAddresses_returnsCorrectSans() {
        val emails      = new String[] { "admin@example.com", "user@example.com" };
        val certificate = generateCertificateWithSans(emails, GeneralName.rfc822Name);

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNotNull(sans);
        assertEquals(2, sans.size());

        val sanValues = sans.stream().map(san -> san.get(1).toString()).toList();

        for (val email : emails) {
            assertTrue(sanValues.contains(email), "Should contain email: " + email);
        }
    }

    @Test
    void extractSubjectAlternativeNames_withUris_returnsCorrectSans() {
        val uris        = new String[] { "https://example.com", "https://api.example.com" };
        val certificate = generateCertificateWithSans(uris, GeneralName.uniformResourceIdentifier);

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNotNull(sans);
        assertEquals(2, sans.size());

        for (val san : sans) {
            val sanType = (Integer) san.getFirst();
            assertEquals(GeneralName.uniformResourceIdentifier, sanType);
        }
    }

    @Test
    void extractSubjectAlternativeNames_withMixedTypes_returnsAllSans() {
        val certificate = generateCertificateWithMixedSans();

        val sans = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertNotNull(sans);
        assertTrue(sans.size() >= 3);

        val sanTypes = sans.stream().map(san -> (Integer) san.getFirst()).toList();

        assertTrue(sanTypes.contains(GeneralName.dNSName));
        assertTrue(sanTypes.contains(GeneralName.rfc822Name));
    }

    /* getCertificateFactory Tests */

    @Test
    void getCertificateFactory_returnsValidFactory() {
        val factory = CertificateUtils.getCertificateFactory();

        assertNotNull(factory);
        assertEquals(CERTIFICATE_TYPE_X509, factory.getType());
    }

    @Test
    void getCertificateFactory_canGenerateCertificate() throws Exception {
        val factory     = CertificateUtils.getCertificateFactory();
        val certificate = generateSelfSignedCertificate("CN=Test");
        val bytes       = certificate.getEncoded();

        val inputStream       = new java.io.ByteArrayInputStream(bytes);
        val parsedCertificate = factory.generateCertificate(inputStream);

        assertNotNull(parsedCertificate);
        assertInstanceOf(X509Certificate.class, parsedCertificate);
    }

    /* Integration Tests */

    @Test
    void roundTrip_parseEncodeAndParseAgain_producesEquivalentCertificate() throws CertificateEncodingException {
        val originalCertificate = generateSelfSignedCertificate("CN=Integration Test");
        val pemCertificate      = convertToPem(originalCertificate);

        val parsed1 = CertificateUtils.parseCertificate(pemCertificate);
        val encoded = CertificateUtils.encodeCertificate(parsed1);
        val parsed2 = CertificateUtils.parseCertificate(Base64.getEncoder().encodeToString(encoded));

        assertEquals(parsed1.getSubjectX500Principal(), parsed2.getSubjectX500Principal());
        assertEquals(parsed1.getIssuerX500Principal(), parsed2.getIssuerX500Principal());
        assertEquals(parsed1.getSerialNumber(), parsed2.getSerialNumber());
        assertArrayEquals(parsed1.getEncoded(), parsed2.getEncoded());
    }

    /* Test Data Providers */

    private static Stream<Arguments> provideCertificatesWithDifferentSubjects() {
        return Stream.of(Arguments.of("CN=example.com"), Arguments.of("CN=example.com,O=Example Corp"),
                Arguments.of("CN=example.com,O=Example Corp,C=US"),
                Arguments.of("CN=*.example.com,O=Example,OU=IT,C=DE"),
                Arguments.of("CN=test,O=Test Organization,L=Berlin,ST=Berlin,C=DE"));
    }

    private static Stream<Arguments> provideCertificatesWithDifferentKeyAlgorithms() {
        return Stream.of(Arguments.of(ALGORITHM_RSA), Arguments.of(ALGORITHM_EC));
    }

    /* Test Helper Methods */

    private static X509Certificate generateSelfSignedCertificate(String subjectDn) {
        try {
            val keyPair = generateRsaKeyPair();
            return buildCertificate(subjectDn, keyPair, null);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate self-signed certificate", exception);
        }
    }

    private static X509Certificate generateCertificateWithKeyAlgorithm(String algorithm) {
        try {
            val keyPair = switch (algorithm) {
            case ALGORITHM_RSA -> generateRsaKeyPair();
            case ALGORITHM_EC  -> generateEcKeyPair();
            default            -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
            };
            return buildCertificate("CN=Test", keyPair, null);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate certificate", exception);
        }
    }

    private static X509Certificate generateCertificateWithSans(String[] sanValues, int sanType) {
        try {
            val keyPair      = generateRsaKeyPair();
            val generalNames = new GeneralName[sanValues.length];

            for (var i = 0; i < sanValues.length; i++) {
                generalNames[i] = new GeneralName(sanType, sanValues[i]);
            }

            val sans = new GeneralNames(generalNames);
            return buildCertificate("CN=Test", keyPair, sans);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate certificate with SANs", exception);
        }
    }

    private static X509Certificate generateCertificateWithMixedSans() {
        try {
            val keyPair      = generateRsaKeyPair();
            val generalNames = new GeneralName[] { new GeneralName(GeneralName.dNSName, "example.com"),
                    new GeneralName(GeneralName.rfc822Name, "admin@example.com"),
                    new GeneralName(GeneralName.uniformResourceIdentifier, "https://example.com") };
            val sans         = new GeneralNames(generalNames);
            return buildCertificate("CN=Test", keyPair, sans);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate certificate with mixed SANs", exception);
        }
    }

    private static X509Certificate buildCertificate(String subjectDn, KeyPair keyPair,
            GeneralNames subjectAlternativeNames) throws Exception {
        val now          = new Date();
        val notBefore    = new Date(now.getTime() - 86400000L);
        val notAfter     = new Date(now.getTime() + 31536000000L);
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis());
        val subject      = new X500Name(subjectDn);

        val certificateBuilder = new JcaX509v3CertificateBuilder(subject, serialNumber, notBefore, notAfter, subject,
                keyPair.getPublic());

        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        if (subjectAlternativeNames != null) {
            certificateBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames);
        }

        val signatureAlgorithm = keyPair.getPublic().getAlgorithm().equals(ALGORITHM_RSA) ? ALGORITHM_RSA_SHA256
                : ALGORITHM_ECDSA_SHA256;

        val contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC")
                .build(keyPair.getPrivate());

        val certificateHolder = certificateBuilder.build(contentSigner);

        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_RSA);
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Failed to generate RSA key pair", exception);
        }
    }

    private static KeyPair generateEcKeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EC);
            keyPairGenerator.initialize(256);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("Failed to generate EC key pair", exception);
        }
    }

    private static String convertToPem(X509Certificate certificate) {
        try {
            val encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
            return PEM_CERTIFICATE_BEGIN + '\n' + encoded + '\n' + PEM_CERTIFICATE_END;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to convert certificate to PEM", exception);
        }
    }

    private static String convertToDer(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (Exception exception) {
            throw new RuntimeException("Failed to convert certificate to DER", exception);
        }
    }
}
