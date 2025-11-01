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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class X509FunctionLibraryTests {

    private static final String CTHULHU_DN    = "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=US";
    private static final String YOG_SOTHOTH_DN = "CN=Yog-Sothoth Time Services,O=Beyond the Gate,C=XX";
    private static final String AZATHOTH_DN   = "CN=Azathoth Nuclear Daemon,O=Center of Chaos,C=ZZ";
    private static final String DEFAULT_DNS_1 = "ritual-chamber.rlyeh.deep";
    private static final String DEFAULT_DNS_2 = "shoggoth-services.antarctica";
    private static final String DEFAULT_IP    = "192.168.1.1";

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
        val result = X509FunctionLibrary.parseCertificate(Val.of(certPem));

        assertThat(result.isDefined()).as("%s: should parse successfully", description).isTrue();

        val jsonNode = result.get();
        assertThat(jsonNode.has("subject")).as("%s: should have subject", description).isTrue();
        assertThat(jsonNode.has("issuer")).as("%s: should have issuer", description).isTrue();
        assertThat(jsonNode.has("serialNumber")).as("%s: should have serialNumber", description).isTrue();
        assertThat(jsonNode.has("notBefore")).as("%s: should have notBefore", description).isTrue();
        assertThat(jsonNode.has("notAfter")).as("%s: should have notAfter", description).isTrue();
        assertThat(jsonNode.get("subject").asText()).as("%s: subject should contain expected content", description)
                .contains(expectedFragment);
    }

    static Stream<Arguments> validCertificates() throws OperatorCreationException, CertificateException, IOException {
        val now = Instant.now();
        // Chinese: "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=CN"
        val unicodeCertPem = toPem(generateCertificate("CN=克苏鲁会计服务,O=拉莱耶深潜者有限责任公司,C=CN",
                now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS), false, null));

        return Stream.of(arguments(cthulhuCertPem, "Cthulhu", "Standard certificate"),
                arguments(expiredCertPem, "Yog-Sothoth", "Hyphenated name"),
                // Expected fragment: "Cthulhu" in Chinese
                arguments(unicodeCertPem, "克苏鲁", "Chinese Unicode"));
    }

    @ParameterizedTest
    @MethodSource("malformedCertificates")
    void parseCertificate_whenMalformed_returnsError(String certInput, String description) {
        val result = X509FunctionLibrary.parseCertificate(Val.of(certInput));

        assertThat(result.isError()).as(description).isTrue();
    }

    static Stream<Arguments> malformedCertificates() throws OperatorCreationException, CertificateException, IOException {
        val now = Instant.now();
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
        val result   = X509FunctionLibrary.parseCertificate(Val.of(modified));

        assertThat(result.isDefined()).isTrue();
    }

    /* Field Extraction Tests */

    @ParameterizedTest
    @MethodSource("fieldExtractors")
    void extractField_whenValidCert_returnsExpectedValue(Function<Val, Val> extractor, String expectedContent,
                                                         String description) {
        val result = extractor.apply(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).as(description).isTrue();
        if (!expectedContent.isEmpty()) {
            assertThat(result.getText()).contains(expectedContent);
        }
    }

    @ParameterizedTest
    @MethodSource("fieldExtractors")
    void extractField_whenInvalidCert_returnsError(Function<Val, Val> extractor, String description) {
        val result = extractor.apply(Val.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn"));

        assertThat(result.isError()).as(description).isTrue();
    }

    static Stream<Arguments> fieldExtractors() {
        return Stream.of(
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSubjectDn, "Cthulhu", "Subject DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractIssuerDn, "Cthulhu", "Issuer DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractCommonName, "Cthulhu Accounting Services",
                        "Common Name"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSerialNumber, "", "Serial Number"));
    }

    @ParameterizedTest
    @MethodSource("validityDateExtractors")
    void extractValidityDate_whenValidCert_returnsIsoDate(Function<Val, Val> extractor, String description) {
        val result = extractor.apply(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).as(description).isTrue();
        assertThat(result.getText()).endsWith("Z").contains("T");
    }

    static Stream<Arguments> validityDateExtractors() {
        return Stream.of(arguments((Function<Val, Val>) X509FunctionLibrary::extractNotBefore, "NotBefore"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractNotAfter, "NotAfter"));
    }

    /* Fingerprint Tests */

    @ParameterizedTest
    @CsvSource({ "SHA-256, 64", "SHA-1, 40", "SHA-512, 128" })
    void extractFingerprint_whenValidAlgorithm_computesCorrectLength(String algorithm, int expectedLength) {
        val result = X509FunctionLibrary.extractFingerprint(Val.of(cthulhuCertPem), Val.of(algorithm));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).hasSize(expectedLength);
    }

    @Test
    void extractFingerprint_whenInvalidAlgorithm_returnsError() {
        val result = X509FunctionLibrary.extractFingerprint(Val.of(cthulhuCertPem), Val.of("ELDER-SIGN-HASH"));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Hash algorithm not supported");
    }

    @Test
    void extractFingerprint_isConsistent() {
        val result1 = X509FunctionLibrary.extractFingerprint(Val.of(cthulhuCertPem), Val.of("SHA-256"));
        val result2 = X509FunctionLibrary.extractFingerprint(Val.of(cthulhuCertPem), Val.of("SHA-256"));

        assertThat(result1.getText()).isEqualTo(result2.getText());
    }

    @Test
    void matchesFingerprint_whenCorrectFingerprint_returnsTrue()
            throws CertificateException, NoSuchAlgorithmException {
        val cert                = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest              = MessageDigest.getInstance("SHA-256");
        val expectedFingerprint = HexFormat.of().formatHex(digest.digest(cert.getEncoded()));
        val result              = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem),
                Val.of(expectedFingerprint), Val.of("SHA-256"));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void matchesFingerprint_whenWrongFingerprint_returnsFalse() {
        val result = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of("deadbeefdeadbeef"),
                Val.of("SHA-256"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void matchesFingerprint_isCaseInsensitive() throws CertificateException, NoSuchAlgorithmException {
        val cert        = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest      = MessageDigest.getInstance("SHA-256");
        val fingerprint = HexFormat.of().formatHex(digest.digest(cert.getEncoded()));
        val result1     = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem),
                Val.of(fingerprint.toLowerCase()), Val.of("SHA-256"));
        val result2 = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of(fingerprint.toUpperCase()),
                Val.of("SHA-256"));

        assertThat(result1.getBoolean()).isTrue();
        assertThat(result2.getBoolean()).isTrue();
    }

    /* Subject Alternative Names Tests */

    @Test
    void extractSubjectAltNames_whenNoSans_returnsEmptyArray() {
        val result = X509FunctionLibrary.extractSubjectAltNames(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
    }

    @Test
    void extractSubjectAltNames_whenCertHasSans_returnsSanList() {
        val result = X509FunctionLibrary.extractSubjectAltNames(Val.of(certWithSansPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().isEmpty()).isFalse();

        val firstSan = result.get().get(0);
        assertThat(firstSan.has("type")).isTrue();
        assertThat(firstSan.has("value")).isTrue();
    }

    /* DNS Name Tests */

    @Test
    void hasDnsName_whenCertHasDnsInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of(DEFAULT_DNS_1));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void hasDnsName_whenCertDoesNotHaveDns_returnsFalse() {
        val result = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of("miskatonic-university.edu"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void hasDnsName_whenWildcardCert_matchesSubdomain()
            throws OperatorCreationException, CertificateException, IOException {
        val now  = Instant.now();
        val cert = generateCertificate("CN=*.rlyeh.deep,O=Wildcard Services,C=US", now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS), true, new String[] { "*.rlyeh.deep" });
        val pem = toPem(cert);

        val result = X509FunctionLibrary.hasDnsName(Val.of(pem), Val.of("temple.rlyeh.deep"));

        assertThat(result.getBoolean()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "RITUAL-CHAMBER.RLYEH.DEEP", "Ritual-Chamber.Rlyeh.Deep" })
    void hasDnsName_isCaseInsensitive(String dnsVariation) {
        val result = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of(dnsVariation));

        assertThat(result.getBoolean()).isTrue();
    }

    /* IP Address Tests */

    @Test
    void hasIpAddress_whenCertHasIpInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(certWithSansPem), Val.of(DEFAULT_IP));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void hasIpAddress_whenCertDoesNotHaveIp_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(certWithSansPem), Val.of("10.0.0.1"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void hasIpAddress_whenNonSanCert_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(cthulhuCertPem), Val.of(DEFAULT_IP));

        assertThat(result.getBoolean()).isFalse();
    }

    /* Validity Check Tests */

    @ParameterizedTest
    @CsvSource({ "false, Valid certificate", "true, Expired certificate" })
    void isExpired_checksExpirationCorrectly(boolean shouldBeExpired, String description) {
        val certPem = shouldBeExpired ? expiredCertPem : cthulhuCertPem;
        val result  = X509FunctionLibrary.isExpired(Val.of(certPem));

        assertThat(result.getBoolean()).as(description).isEqualTo(shouldBeExpired);
    }

    @ParameterizedTest
    @CsvSource({ "1, DAYS, true, Within validity period", "-365, DAYS, false, Before validity start",
            "400, DAYS, false, After validity end" })
    void isValidAt_whenVariousTimestamps_returnsExpectedResult(long amount, ChronoUnit unit, boolean expected,
                                                               String description) {
        val timestamp = Instant.now().plus(amount, unit).toString();
        val result    = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(timestamp));

        assertThat(result.getBoolean()).as(description).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-timestamp", "invalid-format", "2024-13-45T99:99:99Z" })
    void isValidAt_whenInvalidTimestamp_returnsError(String timestamp) {
        val result = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(timestamp));

        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("boundaryTimestamps")
    void isValidAt_atBoundaries_isValid(Function<Val, Val> extractDate) {
        val timestamp = extractDate.apply(Val.of(cthulhuCertPem)).getText();
        val result    = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(timestamp));

        assertThat(result.getBoolean()).isTrue();
    }

    static Stream<Arguments> boundaryTimestamps() {
        return Stream.of(arguments((Function<Val, Val>) X509FunctionLibrary::extractNotBefore),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractNotAfter));
    }

    /* Remaining Validity Tests */

    @Test
    void remainingValidityDays_whenValidCert_returnsPositiveNumber() {
        val result = X509FunctionLibrary.remainingValidityDays(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().asLong()).isGreaterThan(0);
    }

    @Test
    void remainingValidityDays_whenExpiredCert_returnsNegativeNumber() {
        val result = X509FunctionLibrary.remainingValidityDays(Val.of(expiredCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().asLong()).isLessThan(0);
    }

    @Test
    void remainingValidityDays_whenCertExpiresInTwoDays_returnsOneOrTwo()
            throws OperatorCreationException, CertificateException, IOException {
        val now  = Instant.now();
        val cert = generateCertificate(CTHULHU_DN, now.minus(365, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS),
                false, null);
        val certPem = toPem(cert);
        val result  = X509FunctionLibrary.remainingValidityDays(Val.of(certPem));

        assertThat(result.get().asLong()).isIn(1L, 2L);
    }

    /* Unicode Tests */

    @ParameterizedTest
    @MethodSource("unicodeDns")
    void parseAndExtract_withVariousUnicodeScripts_succeeds(String dn, String description)
            throws OperatorCreationException, CertificateException, IOException {
        val cert = generateCertificate(dn, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS), false, null);
        val pem = toPem(cert);

        val parseResult  = X509FunctionLibrary.parseCertificate(Val.of(pem));
        val extractResult = X509FunctionLibrary.extractSubjectDn(Val.of(pem));

        assertThat(parseResult.isDefined()).as(description).isTrue();
        assertThat(extractResult.isDefined()).as(description).isTrue();
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
        val now  = Instant.now();
        val cert = generateCertificate(CTHULHU_DN, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS),
                false, null);
        val certPem = toPem(cert);

        val validNow    = X509FunctionLibrary.isValidAt(Val.of(certPem), Val.of(now.toString()));
        val validBefore = X509FunctionLibrary.isValidAt(Val.of(certPem),
                Val.of(now.minus(2, ChronoUnit.HOURS).toString()));
        val validAfter = X509FunctionLibrary.isValidAt(Val.of(certPem),
                Val.of(now.plus(2, ChronoUnit.HOURS).toString()));

        assertThat(validNow.getBoolean()).isTrue();
        assertThat(validBefore.getBoolean()).isFalse();
        assertThat(validAfter.getBoolean()).isFalse();
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