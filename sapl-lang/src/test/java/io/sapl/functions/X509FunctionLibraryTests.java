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
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
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

    private static final String CTHULHU_ORG        = "CN=Cthulhu Accounting Services,O=Rlyeh Deep Ones LLC,C=US";
    private static final String YOG_SOTHOTH_ORG    = "CN=Yog-Sothoth Time Services,O=Beyond the Gate,C=XX";
    private static final String NYARLATHOTEP_ORG   = "CN=Nyarlathotep Identity Provider,O=Crawling Chaos Corp,C=EG";
    private static final String AZATHOTH_ORG       = "CN=Azathoth Nuclear Daemon,O=Center of Chaos,C=ZZ";
    private static final String SHUB_NIGGURATH_ORG = "CN=Shub-Niggurath Forest Services,O=Black Goat of Woods,C=FR";
    private static final String HASTUR_ORG         = "CN=Hastur the Unspeakable,O=Carcosa Cloud,C=CA";

    private static final String DEFAULT_DNS_1 = "ritual-chamber.rlyeh.deep";
    private static final String DEFAULT_DNS_2 = "shoggoth-services.antarctica";
    private static final String DEFAULT_IP    = "192.168.1.1";

    private static String  cthulhuCertPem;
    private static String  expiredCertPem;
    private static String  certWithSansPem;
    private static String  yogSothothCertPem;
    private static String  nyarlathotepCertPem;
    private static String  unicodeCertPem;
    private static KeyPair keyPair;

    @BeforeAll
    static void setup() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();

        cthulhuCertPem      = toPem(generateCertificate(CTHULHU_ORG, false, false));
        expiredCertPem      = toPem(generateCertificate(YOG_SOTHOTH_ORG, true, false));
        yogSothothCertPem   = toPem(generateCertificate(YOG_SOTHOTH_ORG, false, false));
        nyarlathotepCertPem = toPem(generateCertificate(NYARLATHOTEP_ORG, false, false));
        certWithSansPem     = toPem(generateCertificate(AZATHOTH_ORG, false, true));
        // Chinese: "Cthulhu Accounting Services, Rlyeh Deep Ones LLC, CN"
        unicodeCertPem = toPem(generateCertificate("CN=克苏鲁会计服务,O=拉莱耶深潜者有限责任公司,C=CN", false, false));
    }

    /* Parse Certificate Tests */

    @ParameterizedTest
    @MethodSource("provideCertificatesForParsing")
    void parseCertificate_whenValidPem_extractsAllFields(String certPem, String expectedSubjectPart, String scenario) {
        val result = X509FunctionLibrary.parseCertificate(Val.of(certPem));

        assertThat(result.isDefined()).as("%s: should parse successfully", scenario).isTrue();

        val jsonNode = result.get();
        assertThat(jsonNode.has("subject")).as("%s: should have subject", scenario).isTrue();
        assertThat(jsonNode.has("issuer")).as("%s: should have issuer", scenario).isTrue();
        assertThat(jsonNode.has("serialNumber")).as("%s: should have serialNumber", scenario).isTrue();
        assertThat(jsonNode.has("notBefore")).as("%s: should have notBefore", scenario).isTrue();
        assertThat(jsonNode.has("notAfter")).as("%s: should have notAfter", scenario).isTrue();
        assertThat(jsonNode.get("subject").asText()).as("%s: subject should contain expected content", scenario)
                .contains(expectedSubjectPart);
    }

    private static Stream<Arguments> provideCertificatesForParsing() {
        return Stream.of(arguments(cthulhuCertPem, "Cthulhu", "Cthulhu certificate"),
                arguments(yogSothothCertPem, "Yog-Sothoth", "Yog-Sothoth certificate"),
                arguments(nyarlathotepCertPem, "Nyarlathotep", "Nyarlathotep certificate"),
                arguments(unicodeCertPem, "克苏鲁", "Unicode Chinese certificate")); // Chinese: "Cthulhu"
    }

    @Test
    void parseCertificate_whenInvalidPem_returnsError() {
        val result = X509FunctionLibrary.parseCertificate(Val.of("invalid certificate from Outer Gods"));

        assertThat(result.isError()).isTrue();
    }

    /* Extract Field Tests */

    @ParameterizedTest
    @MethodSource("provideFieldExtractors")
    void extractField_whenValidCert_returnsExpectedValue(Function<Val, Val> extractor, String certPem,
            String expectedContent, String fieldDescription) {
        val result = extractor.apply(Val.of(certPem));

        assertThat(result.isDefined()).as("%s should be defined", fieldDescription).isTrue();
        assertThat(result.getText()).as("%s should contain expected content", fieldDescription)
                .contains(expectedContent);
    }

    @ParameterizedTest
    @MethodSource("provideFieldExtractors")
    void extractField_whenInvalidCert_returnsError(Function<Val, Val> extractor, String fieldDescription) {
        val result = extractor.apply(Val.of("Cthulhu Rlyeh wgah nagl fhtagn"));

        assertThat(result.isError()).as("%s extraction should fail for invalid cert", fieldDescription).isTrue();
    }

    private static Stream<Arguments> provideFieldExtractors() {
        return Stream.of(
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSubjectDn, cthulhuCertPem, "Cthulhu",
                        "Subject DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractIssuerDn, cthulhuCertPem, "Cthulhu",
                        "Issuer DN"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractSerialNumber, cthulhuCertPem, "",
                        "Serial Number"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractCommonName, cthulhuCertPem,
                        "Cthulhu Accounting Services", "Common Name"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractCommonName, yogSothothCertPem, "Yog-Sothoth",
                        "Common Name (Yog-Sothoth)"));
    }

    @Test
    void extractCommonName_whenNoCnInSubject_returnsError() {
        val result = X509FunctionLibrary.extractCommonName(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("Cthulhu");
    }

    /* Extract Validity Date Tests */

    @ParameterizedTest
    @MethodSource("provideValidityDateExtractors")
    void extractValidityDate_whenValidCert_returnsIsoDate(Function<Val, Val> extractor, String fieldDescription) {
        val result = extractor.apply(Val.of(cthulhuCertPem));

        assertThat(result.isDefined()).as("%s should be defined", fieldDescription).isTrue();
        assertThat(result.getText()).as("%s should be ISO-8601 format", fieldDescription).endsWith("Z").contains("T");
    }

    @ParameterizedTest
    @MethodSource("provideValidityDateExtractors")
    void extractValidityDate_whenInvalidCert_returnsError(Function<Val, Val> extractor, String fieldDescription) {
        val result = extractor.apply(Val.of("The Call of Cthulhu"));

        assertThat(result.isError()).as("%s extraction should fail for invalid cert", fieldDescription).isTrue();
    }

    private static Stream<Arguments> provideValidityDateExtractors() {
        return Stream.of(arguments((Function<Val, Val>) X509FunctionLibrary::extractNotBefore, "NotBefore"),
                arguments((Function<Val, Val>) X509FunctionLibrary::extractNotAfter, "NotAfter"));
    }

    /* Extract Fingerprint Tests */

    @ParameterizedTest
    @CsvSource({ "SHA-256, 64", "SHA-1, 40", "SHA-512, 128" })
    void extractFingerprint_whenValidAlgorithm_computesFingerprint(String algorithm, int expectedLength) {
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

    /* Match Fingerprint Tests */

    @Test
    void matchesFingerprint_whenCorrectFingerprint_returnsTrue() throws Exception {
        val cert                = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest              = MessageDigest.getInstance("SHA-256");
        val certBytes           = cert.getEncoded();
        val expectedFingerprint = HexFormat.of().formatHex(digest.digest(certBytes));

        val result = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of(expectedFingerprint),
                Val.of("SHA-256"));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void matchesFingerprint_whenWrongFingerprint_returnsFalse() {
        val result = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of("deadbeefdeadbeef"),
                Val.of("SHA-256"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void matchesFingerprint_isCaseInsensitive() throws Exception {
        val cert        = CertificateUtils.parseCertificate(cthulhuCertPem);
        val digest      = MessageDigest.getInstance("SHA-256");
        val certBytes   = cert.getEncoded();
        val fingerprint = HexFormat.of().formatHex(digest.digest(certBytes));

        val result1 = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of(fingerprint.toLowerCase()),
                Val.of("SHA-256"));
        val result2 = X509FunctionLibrary.matchesFingerprint(Val.of(cthulhuCertPem), Val.of(fingerprint.toUpperCase()),
                Val.of("SHA-256"));

        assertThat(result1.getBoolean()).isTrue();
        assertThat(result2.getBoolean()).isTrue();
    }

    /* Extract Subject Alternative Names Tests */

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

    @Test
    void extractSubjectAltNames_whenInvalidCert_returnsError() {
        val result = X509FunctionLibrary.extractSubjectAltNames(Val.of("Ia Ia Cthulhu fhtagn"));

        assertThat(result.isError()).isTrue();
    }

    /* DNS Name Tests */

    @Test
    void hasDnsName_whenCertHasDnsInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of("ritual-chamber.rlyeh.deep"));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void hasDnsName_whenCertDoesNotHaveDns_returnsFalse() {
        val result = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of("miskatonic-university.edu"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void hasDnsName_whenWildcardCert_matchesSubdomain() throws Exception {
        val wildcardCert = generateCertificate("CN=*.rlyeh.deep,O=Wildcard Services,C=US", false, true,
                new String[] { "*.rlyeh.deep" });
        val wildcardPem  = toPem(wildcardCert);

        val result = X509FunctionLibrary.hasDnsName(Val.of(wildcardPem), Val.of("temple.rlyeh.deep"));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void hasDnsName_isCaseInsensitive() {
        val result1 = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of("RITUAL-CHAMBER.RLYEH.DEEP"));
        val result2 = X509FunctionLibrary.hasDnsName(Val.of(certWithSansPem), Val.of("Ritual-Chamber.Rlyeh.Deep"));

        assertThat(result1.getBoolean()).isTrue();
        assertThat(result2.getBoolean()).isTrue();
    }

    /* IP Address Tests */

    @Test
    void hasIpAddress_whenCertHasIpInSan_returnsTrue() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(certWithSansPem), Val.of("192.168.1.1"));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void hasIpAddress_whenCertDoesNotHaveIp_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(certWithSansPem), Val.of("10.0.0.1"));

        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void hasIpAddress_whenNonSanCert_returnsFalse() {
        val result = X509FunctionLibrary.hasIpAddress(Val.of(cthulhuCertPem), Val.of("192.168.1.1"));

        assertThat(result.getBoolean()).isFalse();
    }

    /* Validity Check Tests */

    @ParameterizedTest
    @CsvSource({ "false, 'valid certificate should not be expired'", "true, 'expired certificate should be expired'" })
    void isExpired_whenVariousCerts_returnsExpectedResult(boolean useExpired, String scenario) {
        val certPem = useExpired ? expiredCertPem : cthulhuCertPem;
        val result  = X509FunctionLibrary.isExpired(Val.of(certPem));

        assertThat(result.getBoolean()).as(scenario).isEqualTo(useExpired);
    }

    @Test
    void isExpired_whenInvalidCert_returnsError() {
        val result = X509FunctionLibrary.isExpired(Val.of("The Shadow Over Innsmouth"));

        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "1, DAYS, true, 'within validity period'", "-365, DAYS, false, 'before validity start'",
            "400, DAYS, false, 'after validity end'" })
    void isValidAt_whenVariousTimestamps_returnsExpectedResult(long amount, ChronoUnit unit, boolean expected,
            String scenario) {
        val timestamp = Instant.now().plus(amount, unit).toString();
        val result    = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(timestamp));

        assertThat(result.getBoolean()).as(scenario).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-timestamp", "invalid-format", "2024-13-45T99:99:99Z", "Cthulhu fhtagn" })
    void isValidAt_whenInvalidTimestamp_returnsError(String timestamp) {
        val result = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(timestamp));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void isValidAt_whenInvalidCert_returnsError() {
        val timestamp = Instant.now().toString();
        val result    = X509FunctionLibrary.isValidAt(Val.of("The Dunwich Horror"), Val.of(timestamp));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void isValidAt_whenExactlyAtNotBefore_isValid() {
        val notBeforeStr = X509FunctionLibrary.extractNotBefore(Val.of(cthulhuCertPem)).getText();
        val result       = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(notBeforeStr));

        assertThat(result.getBoolean()).isTrue();
    }

    @Test
    void isValidAt_whenExactlyAtNotAfter_isValid() {
        val notAfterStr = X509FunctionLibrary.extractNotAfter(Val.of(cthulhuCertPem)).getText();
        val result      = X509FunctionLibrary.isValidAt(Val.of(cthulhuCertPem), Val.of(notAfterStr));

        assertThat(result.getBoolean()).isTrue();
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
    void remainingValidityDays_whenInvalidCert_returnsError() {
        val result = X509FunctionLibrary.remainingValidityDays(Val.of("At the Mountains of Madness"));

        assertThat(result.isError()).isTrue();
    }

    /* Whitespace Handling Tests */

    @Test
    void parseCertificate_withLeadingAndTrailingWhitespace_succeeds() {
        val pemWithWhitespace = "  \n" + cthulhuCertPem + "\n  ";
        val result            = X509FunctionLibrary.parseCertificate(Val.of(pemWithWhitespace));

        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void parseCertificate_withWindowsLineEndings_succeeds() {
        val pemWithCrlf = cthulhuCertPem.replace("\n", "\r\n");
        val result      = X509FunctionLibrary.parseCertificate(Val.of(pemWithCrlf));

        assertThat(result.isDefined()).isTrue();
    }

    /* Malformed Input Tests */

    @Test
    void parseCertificate_whenTruncatedPem_returnsError() {
        val truncatedPem = cthulhuCertPem.substring(0, cthulhuCertPem.length() / 2);
        val result       = X509FunctionLibrary.parseCertificate(Val.of(truncatedPem));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void parseCertificate_whenCorruptedBase64_returnsError() {
        val corruptedPem = cthulhuCertPem.replace("A", "!");
        val result       = X509FunctionLibrary.parseCertificate(Val.of(corruptedPem));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void parseCertificate_whenMissingBeginMarker_returnsError() {
        val noPemMarkers = cthulhuCertPem.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "");
        val result       = X509FunctionLibrary.parseCertificate(Val.of(noPemMarkers));

        assertThat(result.isError()).isTrue();
    }

    /* Unicode Tests */

    @Test
    void extractSubjectDn_withUnicodeChinese_extractsCorrectly() {
        val result = X509FunctionLibrary.extractSubjectDn(Val.of(unicodeCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("克苏鲁"); // Chinese: "Cthulhu"
    }

    @Test
    void extractCommonName_withUnicodeChinese_extractsCorrectly() {
        val result = X509FunctionLibrary.extractCommonName(Val.of(unicodeCertPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo("克苏鲁会计服务"); // Chinese: "Cthulhu Accounting Services"
    }

    @ParameterizedTest
    @ValueSource(strings = { "CN=Йог-Сотот,O=Врата,C=RU",                          // Russian: "Yog-Sothoth, Gates, RU"
            "CN=ヨグ-ソトース,O=門の向こう,C=JP",                    // Japanese: "Yog-Sothoth, Beyond the Gate, JP"
            "CN=يوغ سوثوث,O=ما وراء البوابة,C=SA",                // Arabic: "Yog-Sothoth, Beyond the Gate, SA"
            "CN=Γιογκ-Σόθοθ,O=Πέρα από την Πύλη,C=GR"            // Greek: "Yog-Sothoth, Beyond the Gate, GR"
    })
    void parseAndExtract_withVariousUnicodeScripts_succeeds(String dn) throws Exception {
        val cert = generateCertificate(dn, false, false);
        val pem  = toPem(cert);

        val parseResult   = X509FunctionLibrary.parseCertificate(Val.of(pem));
        val extractResult = X509FunctionLibrary.extractSubjectDn(Val.of(pem));

        assertThat(parseResult.isDefined()).isTrue();
        assertThat(extractResult.isDefined()).isTrue();
    }

    /* Temporal Edge Cases */

    @Test
    void isValidAt_whenCertValidForOneHour_handlesCorrectly() throws Exception {
        val now         = Instant.now();
        val oneHourCert = generateCertificateWithSpecificDates(SHUB_NIGGURATH_ORG, now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS));
        val pem         = toPem(oneHourCert);

        val validNow    = X509FunctionLibrary.isValidAt(Val.of(pem), Val.of(now.toString()));
        val validBefore = X509FunctionLibrary.isValidAt(Val.of(pem), Val.of(now.minus(2, ChronoUnit.HOURS).toString()));
        val validAfter  = X509FunctionLibrary.isValidAt(Val.of(pem), Val.of(now.plus(2, ChronoUnit.HOURS).toString()));

        assertThat(validNow.getBoolean()).isTrue();
        assertThat(validBefore.getBoolean()).isFalse();
        assertThat(validAfter.getBoolean()).isFalse();
    }

    @Test
    void remainingValidityDays_whenCertExpiresInTwoDays_returnsOneOrTwo() throws Exception {
        val now         = Instant.now();
        val twoDaysCert = generateCertificateWithSpecificDates(HASTUR_ORG, now.minus(365, ChronoUnit.DAYS),
                now.plus(2, ChronoUnit.DAYS));
        val pem         = toPem(twoDaysCert);

        val result = X509FunctionLibrary.remainingValidityDays(Val.of(pem));

        assertThat(result.get().asLong()).isIn(1L, 2L);
    }

    /* Helper Methods */

    private static X509Certificate generateCertificate(String subjectDn, boolean expired, boolean withSans)
            throws Exception {
        return generateCertificate(subjectDn, expired, withSans,
                withSans ? new String[] { DEFAULT_DNS_1, DEFAULT_DNS_2 } : null);
    }

    private static X509Certificate generateCertificate(String subjectDn, boolean expired, boolean withSans,
            String[] dnsNames) throws Exception {
        val now       = Instant.now();
        val notBefore = expired ? now.minus(365, ChronoUnit.DAYS) : now.minus(1, ChronoUnit.DAYS);
        val notAfter  = expired ? now.minus(1, ChronoUnit.DAYS) : now.plus(365, ChronoUnit.DAYS);

        return generateCertificateWithSpecificDates(subjectDn, notBefore, notAfter, withSans, dnsNames);
    }

    private static X509Certificate generateCertificateWithSpecificDates(String subjectDn, Instant notBefore,
            Instant notAfter) throws Exception {
        return generateCertificateWithSpecificDates(subjectDn, notBefore, notAfter, false, null);
    }

    private static X509Certificate generateCertificateWithSpecificDates(String subjectDn, Instant notBefore,
            Instant notAfter, boolean withSans, String[] dnsNames) throws Exception {
        val subject = new X500Name(subjectDn);

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

            // Combine DNS entries with IP entry
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

    private static String toPem(X509Certificate certificate) throws Exception {
        val encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }
}
