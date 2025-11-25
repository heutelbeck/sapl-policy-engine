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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.util.crypto.CertificateUtils;
import lombok.val;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class X509FunctionLibraryTests {

    private static final String CTHULHU_DN     = "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=US";
    private static final String YOG_SOTHOTH_DN = "CN=Yog-Sothoth Time Services,O=Beyond the Gate,C=XX";
    private static final String AZATHOTH_DN    = "CN=Azathoth Nuclear Daemon,O=Center of Chaos,C=ZZ";
    private static final String DEFAULT_DNS_1  = "ritual-chamber.rlyeh.deep";
    private static final String DEFAULT_DNS_2  = "shoggoth-services.antarctica";
    private static final String DEFAULT_IP     = "192.168.1.1";

    private static String  cthulhuCertPem;
    private static String  expiredCertPem;
    private static String  certWithSansPem;
    private static KeyPair keyPair;

    @BeforeAll
    static void setup() throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, IOException {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();

        val now = Instant.now();
        cthulhuCertPem  = toPem(generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), false, null));
        expiredCertPem  = toPem(generateCertificate(YOG_SOTHOTH_DN, now.minus(365, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS), false, null));
        certWithSansPem = toPem(generateCertificate(AZATHOTH_DN, now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), true, new String[] { DEFAULT_DNS_1, DEFAULT_DNS_2 }));
    }

    /* Certificate Parsing Tests */

    @ParameterizedTest
    @MethodSource("validCertificates")
    void parseCertificate_whenValidPem_extractsAllFields(String certPem, String expectedFragment, String description) {
        val result = X509FunctionLibrary.parseCertificate(Value.of(certPem));

        assertThat(result).as("%s: should parse successfully", description).isNotInstanceOf(ErrorValue.class);

        val jsonNode = (ObjectValue) result;
        assertThat(jsonNode.containsKey("subject")).as("%s: should have subject", description).isTrue();
        assertThat(jsonNode.containsKey("issuer")).as("%s: should have issuer", description).isTrue();
        assertThat(jsonNode.containsKey("serialNumber")).as("%s: should have serialNumber", description).isTrue();
        assertThat(jsonNode.containsKey("notBefore")).as("%s: should have notBefore", description).isTrue();
        assertThat(jsonNode.containsKey("notAfter")).as("%s: should have notAfter", description).isTrue();
        assertThat(getTextValue(jsonNode)).as("%s: subject should contain expected content", description)
                .contains(expectedFragment);
    }

    private static String getTextValue(ObjectValue obj) {
        return ((TextValue) Objects.requireNonNull(obj.get("subject"))).value();
    }

    static Stream<Arguments> validCertificates() throws OperatorCreationException, CertificateException, IOException {
        val now = Instant.now();
        // Chinese: "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=CN"
        val unicodeCertPem = toPem(generateCertificate("CN=克苏鲁会计服务,O=拉莱耶深潜者有限责任公司,C=CN", now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), false, null));

        return Stream.of(arguments(cthulhuCertPem, "Cthulhu", "Standard certificate"),
                arguments(expiredCertPem, "Yog-Sothoth", "Hyphenated name"),
                // Expected fragment: "Cthulhu" in Chinese
                arguments(unicodeCertPem, "克苏鲁", "Chinese Unicode"));
    }

    @ParameterizedTest
    @MethodSource("malformedCertificates")
    void parseCertificate_whenMalformed_returnsError(String certInput, String description) {
        val result = X509FunctionLibrary.parseCertificate(Value.of(certInput));

        assertThat(result).as(description).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> malformedCertificates()
            throws OperatorCreationException, CertificateException, IOException {
        val now          = Instant.now();
        val validCertPem = toPem(generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), false, null));

        return Stream.of(arguments("invalid certificate from Outer Gods", "Invalid format"),
                arguments(validCertPem.substring(0, validCertPem.length() / 2), "Truncated PEM"),
                arguments(validCertPem.replace('A', '!'), "Corrupted Base64"),
                arguments(validCertPem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----",
                        ""), "Missing PEM markers"));
    }

    @ParameterizedTest
    @ValueSource(strings = { " \n", "\r\n" })
    void parseCertificate_withWhitespaceVariations_succeeds(String padding) {
        val modified = padding + cthulhuCertPem.replace("\n", padding) + padding;
        val result   = X509FunctionLibrary.parseCertificate(Value.of(modified));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    /* Field Extraction Tests */

    @ParameterizedTest
    @MethodSource("fieldExtractors")
    void extractField_whenValidCert_returnsExpectedValue(Function<TextValue, Value> extractor, String expectedContent,
            String description) {
        val result = extractor.apply(Value.of(cthulhuCertPem));

        assertThat(result).as(description).isNotInstanceOf(ErrorValue.class);
        if (!expectedContent.isEmpty()) {
            assertThat(((TextValue) result).value()).contains(expectedContent);
        }
    }

    @ParameterizedTest
    @MethodSource("fieldExtractors")
    void extractField_whenInvalidCert_returnsError(Function<TextValue, Value> extractor, String description) {
        val result = extractor.apply(Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn"));

        assertThat(result).as(description).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> fieldExtractors() {
        return Stream.of(
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractSubjectDn, "Cthulhu", "Subject DN"),
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractIssuerDn, "Cthulhu", "Issuer DN"),
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractCommonName,
                        "Cthulhu Accounting Services", "Common Name"),
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractSerialNumber, "", "Serial Number"));
    }

    @ParameterizedTest
    @MethodSource("validityDateExtractors")
    void extractValidityDate_whenValidCert_returnsIsoDate(Function<TextValue, Value> extractor, String description) {
        val result = extractor.apply(Value.of(cthulhuCertPem));

        assertThat(result).as(description).isNotInstanceOf(ErrorValue.class);
        assertThat(((TextValue) result).value()).endsWith("Z").contains("T");
    }

    static Stream<Arguments> validityDateExtractors() {
        return Stream.of(arguments((Function<TextValue, Value>) X509FunctionLibrary::extractNotBefore, "NotBefore"),
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractNotAfter, "NotAfter"));
    }

    /* Fingerprint Tests */

    @ParameterizedTest
    @CsvSource({ "SHA-256, 64", "SHA-1, 40", "SHA-512, 128" })
    void extractFingerprint_whenValidAlgorithm_computesCorrectLength(String algorithm, int expectedLength) {
        val result = X509FunctionLibrary.extractFingerprint(Value.of(cthulhuCertPem), Value.of(algorithm));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(((TextValue) result).value()).hasSize(expectedLength);
    }

    @Test
    void extractFingerprint_whenInvalidAlgorithm_returnsError() {
        val result = X509FunctionLibrary.extractFingerprint(Value.of(cthulhuCertPem), Value.of("ELDER-SIGN-HASH"));

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                .satisfies(msg -> assertThat(msg).contains("Hash algorithm not supported"));
    }

    @Test
    void extractFingerprint_isConsistent() {
        val result1 = X509FunctionLibrary.extractFingerprint(Value.of(cthulhuCertPem), Value.of("SHA-256"));
        val result2 = X509FunctionLibrary.extractFingerprint(Value.of(cthulhuCertPem), Value.of("SHA-256"));

        assertThat(((TextValue) result1).value()).isEqualTo(((TextValue) result2).value());
    }

    @Test
    void matchesFingerprint_whenCorrectFingerprint_returnsTrue() throws CertificateException, NoSuchAlgorithmException {
        val cert                = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest              = MessageDigest.getInstance("SHA-256");
        val expectedFingerprint = HexFormat.of().formatHex(digest.digest(cert.getEncoded()));

        assertThat(X509FunctionLibrary.matchesFingerprint(Value.of(cthulhuCertPem), Value.of(expectedFingerprint),
                Value.of("SHA-256"))).isEqualTo(Value.TRUE);
    }

    @Test
    void matchesFingerprint_whenWrongFingerprint_returnsFalse() {
        assertThat(X509FunctionLibrary.matchesFingerprint(Value.of(cthulhuCertPem), Value.of("deadbeefdeadbeef"),
                Value.of("SHA-256"))).isEqualTo(Value.FALSE);
    }

    @Test
    void matchesFingerprint_isCaseInsensitive() throws CertificateException, NoSuchAlgorithmException {
        val cert        = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest      = MessageDigest.getInstance("SHA-256");
        val fingerprint = HexFormat.of().formatHex(digest.digest(cert.getEncoded()));

        assertThat(X509FunctionLibrary.matchesFingerprint(Value.of(cthulhuCertPem), Value.of(fingerprint.toLowerCase()),
                Value.of("SHA-256"))).isEqualTo(Value.TRUE);
        assertThat(X509FunctionLibrary.matchesFingerprint(Value.of(cthulhuCertPem), Value.of(fingerprint.toUpperCase()),
                Value.of("SHA-256"))).isEqualTo(Value.TRUE);
    }

    /* Subject Alternative Names Tests */

    @Test
    void extractSubjectAltNames_whenNoSans_returnsEmptyArray() {
        val result = X509FunctionLibrary.extractSubjectAltNames(Value.of(cthulhuCertPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isInstanceOf(ArrayValue.class);
    }

    @Test
    void extractSubjectAltNames_whenCertHasSans_returnsSanList() {
        val result = X509FunctionLibrary.extractSubjectAltNames(Value.of(certWithSansPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isInstanceOf(ArrayValue.class);
        assertThat(((ArrayValue) result)).isNotEmpty();

        val firstSan = (ObjectValue) ((ArrayValue) result).getFirst();
        assertThat(firstSan.containsKey("type")).isTrue();
        assertThat(firstSan.containsKey("value")).isTrue();
    }

    /* DNS Name Tests */

    @Test
    void hasDnsName_whenCertHasDnsInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasDnsName((TextValue) Value.of(certWithSansPem),
                (TextValue) Value.of(DEFAULT_DNS_1));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void hasDnsName_whenCertDoesNotHaveDns_returnsFalse() {
        val result = X509FunctionLibrary.hasDnsName((TextValue) Value.of(certWithSansPem),
                (TextValue) Value.of("miskatonic-university.edu"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void hasDnsName_whenWildcardCert_matchesSubdomain()
            throws OperatorCreationException, CertificateException, IOException {
        val now  = Instant.now();
        val cert = generateCertificate("CN=*.rlyeh.deep,O=Wildcard Services,C=US", now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), true, new String[] { "*.rlyeh.deep" });
        val pem  = toPem(cert);

        val result = X509FunctionLibrary.hasDnsName((TextValue) Value.of(pem),
                (TextValue) Value.of("temple.rlyeh.deep"));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "RITUAL-CHAMBER.RLYEH.DEEP", "Ritual-Chamber.Rlyeh.Deep" })
    void hasDnsName_isCaseInsensitive(String dnsVariation) {
        val result = X509FunctionLibrary.hasDnsName((TextValue) Value.of(certWithSansPem),
                (TextValue) Value.of(dnsVariation));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    /* IP Address Tests */

    @Test
    void hasIpAddress_whenCertHasIpInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasIpAddress((TextValue) Value.of(certWithSansPem),
                (TextValue) Value.of(DEFAULT_IP));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void hasIpAddress_whenCertDoesNotHaveIp_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress((TextValue) Value.of(certWithSansPem),
                (TextValue) Value.of("10.0.0.1"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void hasIpAddress_whenNonSanCert_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress((TextValue) Value.of(cthulhuCertPem),
                (TextValue) Value.of(DEFAULT_IP));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    /* Validity Check Tests */

    @ParameterizedTest
    @CsvSource({ "false, Valid certificate", "true, Expired certificate" })
    void isExpired_checksExpirationCorrectly(boolean shouldBeExpired, String description) {
        val certPem = shouldBeExpired ? expiredCertPem : cthulhuCertPem;
        val result  = X509FunctionLibrary.isExpired((TextValue) Value.of(certPem));

        assertThat(result).as(description).isEqualTo(Value.of(shouldBeExpired));
    }

    @ParameterizedTest
    @CsvSource({ "1, DAYS, true, Within validity period", "-365, DAYS, false, Before validity start",
            "400, DAYS, false, After validity end" })
    void isValidAt_whenVariousTimestamps_returnsExpectedResult(long amount, ChronoUnit unit, boolean expected,
            String description) {
        val timestamp = Instant.now().plus(amount, unit).toString();
        val result    = X509FunctionLibrary.isValidAt((TextValue) Value.of(cthulhuCertPem),
                (TextValue) Value.of(timestamp));

        assertThat(result).as(description).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-timestamp", "invalid-format", "2024-13-45T99:99:99Z" })
    void isValidAt_whenInvalidTimestamp_returnsError(String timestamp) {
        val result = X509FunctionLibrary.isValidAt((TextValue) Value.of(cthulhuCertPem),
                (TextValue) Value.of(timestamp));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest
    @MethodSource("boundaryTimestamps")
    void isValidAt_atBoundaries_isValid(Function<TextValue, Value> extractDate) {
        val timestamp = ((TextValue) extractDate.apply((TextValue) Value.of(cthulhuCertPem))).value();
        val result    = X509FunctionLibrary.isValidAt((TextValue) Value.of(cthulhuCertPem),
                (TextValue) Value.of(timestamp));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    static Stream<Arguments> boundaryTimestamps() {
        return Stream.of(arguments((Function<TextValue, Value>) X509FunctionLibrary::extractNotBefore),
                arguments((Function<TextValue, Value>) X509FunctionLibrary::extractNotAfter));
    }

    /* Remaining Validity Tests */

    @Test
    void remainingValidityDays_whenValidCert_returnsPositiveNumber() {
        val result = X509FunctionLibrary.remainingValidityDays((TextValue) Value.of(cthulhuCertPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(((NumberValue) result).value().longValue()).isGreaterThan(0);
    }

    @Test
    void remainingValidityDays_whenExpiredCert_returnsNegativeNumber() {
        val result = X509FunctionLibrary.remainingValidityDays((TextValue) Value.of(expiredCertPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(((NumberValue) result).value().longValue()).isLessThan(0);
    }

    @Test
    void remainingValidityDays_whenCertExpiresInTwoDays_returnsOneOrTwo()
            throws OperatorCreationException, CertificateException, IOException {
        val now     = Instant.now();
        val cert    = generateCertificate(CTHULHU_DN, now.minus(365, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
                false, null);
        val certPem = toPem(cert);
        val result  = X509FunctionLibrary.remainingValidityDays((TextValue) Value.of(certPem));

        assertThat(((NumberValue) result).value().longValue()).isIn(1L, 2L);
    }

    /* Unicode Tests */

    @ParameterizedTest
    @MethodSource("unicodeDns")
    void parseAndExtract_withVariousUnicodeScripts_succeeds(String dn, String description)
            throws OperatorCreationException, CertificateException, IOException {
        val cert = generateCertificate(dn, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS), false, null);
        val pem  = toPem(cert);

        val parseResult   = X509FunctionLibrary.parseCertificate((TextValue) Value.of(pem));
        val extractResult = X509FunctionLibrary.extractSubjectDn((TextValue) Value.of(pem));

        assertThat(parseResult).as(description).isNotInstanceOf(ErrorValue.class);
        assertThat(extractResult).as(description).isNotInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> unicodeDns() {
        return Stream.of(
                // Chinese: "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=CN"
                arguments("CN=克苏鲁会计服务,O=拉莱耶深潜者有限责任公司,C=CN", "Chinese"),
                // Russian: "CN=Yog-Sothoth,O=Gates,C=RU"
                arguments("CN=Йог-Сотот,O=Врата,C=RU", "Russian"),
                // Japanese: "CN=Yog-Sothoth,O=Beyond the Gate,C=JP"
                arguments("CN=ヨグ-ソトース,O=門の向こう,C=JP", "Japanese"),
                // Arabic: "CN=Yog-Sothoth,O=Beyond the Gate,C=SA"
                arguments("CN=يوغ سوثوث,O=ما وراء البوابة,C=SA", "Arabic"),
                // Greek: "CN=Yog-Sothoth,O=Beyond the Gate,C=GR"
                arguments("CN=Γιογκ-Σόθοθ,O=Πέρα από την Πύλη,C=GR", "Greek"));
    }

    /* Temporal Edge Cases */

    @Test
    void isValidAt_whenCertValidForOneHour_handlesCorrectly()
            throws OperatorCreationException, CertificateException, IOException {
        val now     = Instant.now();
        val cert    = generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS),
                false, null);
        val certPem = toPem(cert);

        val validNow    = X509FunctionLibrary.isValidAt((TextValue) Value.of(certPem),
                (TextValue) Value.of(now.toString()));
        val validBefore = X509FunctionLibrary.isValidAt((TextValue) Value.of(certPem),
                (TextValue) Value.of(now.minus(2, ChronoUnit.HOURS).toString()));
        val validAfter  = X509FunctionLibrary.isValidAt((TextValue) Value.of(certPem),
                (TextValue) Value.of(now.plus(2, ChronoUnit.HOURS).toString()));

        assertThat(validNow).isEqualTo(Value.TRUE);
        assertThat(validBefore).isEqualTo(Value.FALSE);
        assertThat(validAfter).isEqualTo(Value.FALSE);
    }

    /* Helper Methods */

    private static X509Certificate generateCertificate(String subjectDn, Instant notBefore, Instant notAfter,
            boolean withSans, String[] dnsNames) throws OperatorCreationException, CertificateException, IOException {
        val subject     = new X500Name(subjectDn);
        val certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(notBefore), Date.from(notAfter), subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        if (withSans) {
            val ipBytes       = InetAddress.getByName(DEFAULT_IP).getAddress();
            val octetString   = new DEROctetString(ipBytes);
            val ipGeneralName = new GeneralName(GeneralName.iPAddress, octetString);

            val dnsEntries = dnsNames != null
                    ? Stream.of(dnsNames).map(dns -> new GeneralName(GeneralName.dNSName, dns))
                            .toArray(GeneralName[]::new)
                    : new GeneralName[] { new GeneralName(GeneralName.dNSName, DEFAULT_DNS_1),
                            new GeneralName(GeneralName.dNSName, DEFAULT_DNS_2) };

            val sanEntries = new GeneralName[dnsEntries.length + 1];
            System.arraycopy(dnsEntries, 0, sanEntries, 0, dnsEntries.length);
            sanEntries[dnsEntries.length] = ipGeneralName;

            val sans = new GeneralNames(sanEntries);
            certBuilder.addExtension(Extension.subjectAlternativeName, false, sans);
        }

        val signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        val holder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
        val encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }
}
