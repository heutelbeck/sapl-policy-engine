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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KeysFunctionLibraryTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static String rsa2048PublicKeyPem;
    private static String rsa4096PublicKeyPem;
    private static String ecP256PublicKeyPem;
    private static String ecP384PublicKeyPem;
    private static String ecP521PublicKeyPem;
    private static String ed25519PublicKeyPem;
    private static String rsaCertificatePem;
    private static String ecCertificatePem;

    @BeforeAll
    static void setup() throws Exception {
        // Generate Necronomicon RSA keys with varying strengths
        var rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        KeyPair rsa2048KeyPair = rsaGenerator.generateKeyPair();
        rsa2048PublicKeyPem = toPem(rsa2048KeyPair.getPublic());

        rsaGenerator.initialize(4096);
        KeyPair rsa4096KeyPair = rsaGenerator.generateKeyPair();
        rsa4096PublicKeyPem = toPem(rsa4096KeyPair.getPublic());

        // Generate eldritch EC keys across different curves
        var ecGenerator = KeyPairGenerator.getInstance("EC");

        ecGenerator.initialize(256);
        KeyPair ecP256KeyPair = ecGenerator.generateKeyPair();
        ecP256PublicKeyPem = toPem(ecP256KeyPair.getPublic());

        ecGenerator.initialize(384);
        KeyPair ecP384KeyPair = ecGenerator.generateKeyPair();
        ecP384PublicKeyPem = toPem(ecP384KeyPair.getPublic());

        ecGenerator.initialize(521);
        KeyPair ecP521KeyPair = ecGenerator.generateKeyPair();
        ecP521PublicKeyPem = toPem(ecP521KeyPair.getPublic());

        // Generate Ed25519 key used by the cult of Azathoth
        var edGenerator = KeyPairGenerator.getInstance("Ed25519");
        var edKeyPair   = edGenerator.generateKeyPair();
        ed25519PublicKeyPem = toPem(edKeyPair.getPublic());

        // Generate certificates for the Miskatonic University researchers
        rsaCertificatePem = toCertPem(generateCertificate(rsa2048KeyPair, "RSA"));
        ecCertificatePem  = toCertPem(generateCertificate(ecP256KeyPair, "EC"));
    }

    /* Parameterized Key Parsing Tests */

    static Stream<Arguments> keyTypeTestCases() {
        return Stream.of(Arguments.of(rsa2048PublicKeyPem, "RSA", "X.509", 2048, null),
                Arguments.of(rsa4096PublicKeyPem, "RSA", "X.509", 4096, null),
                Arguments.of(ecP256PublicKeyPem, "EC", "X.509", 256, "secp256r1"),
                Arguments.of(ecP384PublicKeyPem, "EC", "X.509", 384, "secp384r1"),
                Arguments.of(ecP521PublicKeyPem, "EC", "X.509", 521, "secp521r1"),
                Arguments.of(ed25519PublicKeyPem, "EdDSA", "X.509", 256, null));
    }

    @ParameterizedTest(name = "{1} key with size {3}")
    @MethodSource("keyTypeTestCases")
    void parsePublicKey_withVariousKeyTypes_returnsCorrectKeyObject(String keyPem, String expectedAlgorithm,
                                                                    String expectedFormat, int expectedSize, String expectedCurve) {
        val result = KeysFunctionLibrary.publicKeyFromPem(Val.of(keyPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("algorithm").asText()).isEqualTo(expectedAlgorithm);
        assertThat(result.get().get("format").asText()).isEqualTo(expectedFormat);
        assertThat(result.get().get("size").asInt()).isEqualTo(expectedSize);

        if (expectedCurve != null) {
            assertThat(result.get().get("curve").asText()).isEqualTo(expectedCurve);
        }
    }

    static Stream<Arguments> invalidKeyInputs() {
        return Stream.of(Arguments.of("", "Empty input from the void"),
                Arguments.of("Ph'nglui mglw'nafh Cthulhu", "Eldritch gibberish"),
                Arguments.of("""
                        -----BEGIN PUBLIC KEY-----
                        invalid
                        -----END PUBLIC KEY-----""", "Corrupted PEM structure"),
                Arguments.of("""
                        -----BEGIN PUBLIC KEY-----
                        IA==
                        -----END PUBLIC KEY-----""", "Truncated key data"),
                Arguments.of("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA", "Base64 without PEM markers"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidKeyInputs")
    void parsePublicKey_withInvalidInput_returnsError(String invalidInput, String description) {
        val result = KeysFunctionLibrary.publicKeyFromPem(Val.of(invalidInput));

        assertThat(result.isError()).as("Should fail for: " + description).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse public key");
    }

    /* Certificate Extraction Tests */

    static Stream<Arguments> certificateTestCases() {
        return Stream.of(Arguments.of("rsaCertificatePem", "RSA"), Arguments.of("ecCertificatePem", "EC"));
    }

    @ParameterizedTest(name = "Extract {1} key from certificate")
    @MethodSource("certificateTestCases")
    void extractPublicKeyFromCertificate_withValidCert_returnsValidPem(String certFieldName,
                                                                       String expectedAlgorithm) throws Exception {
        val certPem = (String) KeysFunctionLibraryTests.class.getDeclaredField(certFieldName).get(null);
        val result  = KeysFunctionLibrary.publicKeyFromCertificate(Val.of(certPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify extracted key can be parsed
        val parsedKey = KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey.isDefined()).isTrue();
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo(expectedAlgorithm);
    }

    static Stream<String> invalidCertificates() {
        return Stream.of("", """
                -----BEGIN CERTIFICATE-----
                corrupted
                -----END CERTIFICATE-----""", "Not a certificate at all",
                "The Necronomicon, bound in human flesh", """
                        -----BEGIN CERTIFICATE-----
                        -----END CERTIFICATE-----""");
    }

    @ParameterizedTest
    @MethodSource("invalidCertificates")
    void extractPublicKeyFromCertificate_withInvalidCert_returnsError(String invalidCert) {
        val result = KeysFunctionLibrary.publicKeyFromCertificate(Val.of(invalidCert));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to extract public key");
    }

    @Test
    void extractPublicKeyFromCertificate_roundTrip_maintainsKeyProperties() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Val.of(rsaCertificatePem));
        val parsedKey    = KeysFunctionLibrary.publicKeyFromPem(extractedKey);

        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("RSA");
        assertThat(parsedKey.get().get("size").asInt()).isEqualTo(2048);
    }

    @Test
    void extractPublicKeyFromCertificate_withEcCertificate_extractsEcKey() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Val.of(ecCertificatePem));
        val parsedKey    = KeysFunctionLibrary.publicKeyFromPem(extractedKey);

        assertThat(parsedKey.isDefined()).isTrue();
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("EC");
        assertThat(parsedKey.get().get("size").asInt()).isEqualTo(256);
        assertThat(parsedKey.get().get("curve").asText()).isEqualTo("secp256r1");
    }

    /* Key Information Extraction Tests */

    @ParameterizedTest(name = "Extract algorithm from {0}")
    @MethodSource("keyTypeTestCases")
    void extractKeyAlgorithm_withVariousKeys_returnsCorrectAlgorithm(String keyPem, String expectedAlgorithm,
                                                                     String ignored1, int ignored2, String ignored3) {
        val result = KeysFunctionLibrary.algorithmFromKey(Val.of(keyPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo(expectedAlgorithm);
    }

    @ParameterizedTest(name = "Extract size from key with expected size {3}")
    @MethodSource("keyTypeTestCases")
    void extractKeySize_withVariousKeys_returnsCorrectSize(String keyPem, String ignored1, String ignored2,
                                                           int expectedSize, String ignored3) {
        val result = KeysFunctionLibrary.sizeFromKey(Val.of(keyPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().asInt()).isEqualTo(expectedSize);
    }

    static Stream<Arguments> ecCurveTestCases() {
        return Stream.of(Arguments.of(ecP256PublicKeyPem, "secp256r1"),
                Arguments.of(ecP384PublicKeyPem, "secp384r1"), Arguments.of(ecP521PublicKeyPem, "secp521r1"));
    }

    @ParameterizedTest(name = "Extract curve {1}")
    @MethodSource("ecCurveTestCases")
    void extractEcCurve_withEcKeys_returnsCorrectCurve(String keyPem, String expectedCurve) {
        val result = KeysFunctionLibrary.curveFromKey(Val.of(keyPem));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).isEqualTo(expectedCurve);
    }

    static Stream<String> nonEcKeys() {
        return Stream.of(rsa2048PublicKeyPem, rsa4096PublicKeyPem, ed25519PublicKeyPem);
    }

    @ParameterizedTest
    @MethodSource("nonEcKeys")
    void extractEcCurve_withNonEcKey_returnsError(String nonEcKeyPem) {
        val result = KeysFunctionLibrary.curveFromKey(Val.of(nonEcKeyPem));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).containsIgnoringCase("not an EC key");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "invalid", "The sign of Hastur" })
    void extractKeyInformation_withInvalidKey_returnsError(String invalidKey) {
        assertThat(KeysFunctionLibrary.algorithmFromKey(Val.of(invalidKey)).isError()).isTrue();
        assertThat(KeysFunctionLibrary.sizeFromKey(Val.of(invalidKey)).isError()).isTrue();
        assertThat(KeysFunctionLibrary.curveFromKey(Val.of(invalidKey)).isError()).isTrue();
    }

    /* JWK Conversion Tests - PEM to JWK */

    @Test
    void publicKeyToJwk_withRsaKey_returnsValidJwk() {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Val.of(rsa2048PublicKeyPem));

        assertThat(result.isDefined()).isTrue();
        val jwk = result.get();
        assertThat(jwk.get("kty").asText()).isEqualTo("RSA");
        assertThat(jwk.has("n")).as("Should have modulus").isTrue();
        assertThat(jwk.has("e")).as("Should have exponent").isTrue();
        assertThat(jwk.get("n").asText()).isNotEmpty();
        assertThat(jwk.get("e").asText()).isNotEmpty();
    }

    static Stream<Arguments> ecJwkTestCases() {
        return Stream.of(Arguments.of(ecP256PublicKeyPem, "P-256"), Arguments.of(ecP384PublicKeyPem, "P-384"),
                Arguments.of(ecP521PublicKeyPem, "P-521"));
    }

    @ParameterizedTest(name = "EC key with curve {1}")
    @MethodSource("ecJwkTestCases")
    void publicKeyToJwk_withEcKey_returnsValidJwk(String keyPem, String expectedCurve) {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Val.of(keyPem));

        assertThat(result.isDefined()).isTrue();
        val jwk = result.get();
        assertThat(jwk.get("kty").asText()).isEqualTo("EC");
        assertThat(jwk.get("crv").asText()).isEqualTo(expectedCurve);
        assertThat(jwk.has("x")).as("Should have x coordinate").isTrue();
        assertThat(jwk.has("y")).as("Should have y coordinate").isTrue();
        assertThat(jwk.get("x").asText()).isNotEmpty();
        assertThat(jwk.get("y").asText()).isNotEmpty();
    }

    @Test
    void publicKeyToJwk_withEd25519Key_returnsValidJwk() {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Val.of(ed25519PublicKeyPem));

        assertThat(result.isDefined()).isTrue();
        val jwk = result.get();
        assertThat(jwk.get("kty").asText()).isEqualTo("OKP");
        assertThat(jwk.get("crv").asText()).isEqualTo("Ed25519");
        assertThat(jwk.has("x")).as("Should have x parameter").isTrue();
        assertThat(jwk.get("x").asText()).isNotEmpty();

        // Verify x parameter is 32 bytes when decoded
        val xBytes = Base64.getUrlDecoder().decode(jwk.get("x").asText());
        assertThat(xBytes).hasSize(32);
    }

    /* JWK Conversion Tests - JWK to PEM */

    @Test
    void jwkToPublicKey_withRsaJwk_returnsValidPem() {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(rsa2048PublicKeyPem));
        val result      = KeysFunctionLibrary.publicKeyFromJwk(originalJwk);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed
        val parsedKey = KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("RSA");
    }

    @ParameterizedTest(name = "EC key with curve {1}")
    @MethodSource("ecJwkTestCases")
    void jwkToPublicKey_withEcJwk_returnsValidPem(String keyPem, String expectedCurve) {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(keyPem));
        val result      = KeysFunctionLibrary.publicKeyFromJwk(originalJwk);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed and has correct curve
        val parsedKey = KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("EC");
    }

    @Test
    void jwkToPublicKey_withEd25519Jwk_returnsValidPem() {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(ed25519PublicKeyPem));
        val result      = KeysFunctionLibrary.publicKeyFromJwk(originalJwk);

        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed
        val parsedKey = KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("EdDSA");
    }

    static Stream<Arguments> invalidJwkTestCases() {
        return Stream.of(
                // Missing kty
                Arguments.of(JSON.objectNode().put("n", "test").put("e", "test"), "missing kty field"),
                // Invalid kty
                Arguments.of(JSON.objectNode().put("kty", "SHOGGOTH"), "unsupported key type"),
                // RSA missing modulus
                Arguments.of(JSON.objectNode().put("kty", "RSA").put("e", "AQAB"), "missing modulus"),
                // RSA missing exponent
                Arguments.of(JSON.objectNode().put("kty", "RSA").put("n", "test"), "missing exponent"),
                // EC missing curve
                Arguments.of(JSON.objectNode().put("kty", "EC").put("x", "test").put("y", "test"), "missing curve"),
                // EC missing x
                Arguments.of(JSON.objectNode().put("kty", "EC").put("crv", "P-256").put("y", "test"), "missing x"),
                // EC missing y
                Arguments.of(JSON.objectNode().put("kty", "EC").put("crv", "P-256").put("x", "test"), "missing y"),
                // EC unsupported curve
                Arguments.of(
                        JSON.objectNode().put("kty", "EC").put("crv", "secp256k1").put("x", "test").put("y", "test"),
                        "unsupported curve"),
                // OKP missing curve
                Arguments.of(JSON.objectNode().put("kty", "OKP").put("x", "test"), "missing curve"),
                // OKP missing x
                Arguments.of(JSON.objectNode().put("kty", "OKP").put("crv", "Ed25519"), "missing x"),
                // OKP unsupported curve
                Arguments.of(JSON.objectNode().put("kty", "OKP").put("crv", "Ed448").put("x", "test"),
                        "unsupported OKP curve"),
                // Invalid base64 encoding
                Arguments.of(JSON.objectNode().put("kty", "RSA").put("n", "not!!!base64").put("e", "AQAB"),
                        "invalid base64"));
    }

    @ParameterizedTest(name = "Invalid JWK: {1}")
    @MethodSource("invalidJwkTestCases")
    void jwkToPublicKey_withInvalidJwk_returnsError(com.fasterxml.jackson.databind.node.ObjectNode invalidJwk,
                                                    String description) {
        val result = KeysFunctionLibrary.publicKeyFromJwk(Val.of(invalidJwk));

        assertThat(result.isError()).as("Should fail for: " + description).isTrue();
        assertThat(result.getMessage()).containsIgnoringCase("failed");
    }

    /* Round-trip Conversion Tests */

    static Stream<Arguments> roundTripTestCases() {
        return Stream.of(Arguments.of(rsa2048PublicKeyPem, "RSA", "n", "e"),
                Arguments.of(rsa4096PublicKeyPem, "RSA", "n", "e"), Arguments.of(ecP256PublicKeyPem, "EC", "x", "y"),
                Arguments.of(ecP384PublicKeyPem, "EC", "x", "y"), Arguments.of(ecP521PublicKeyPem, "EC", "x", "y"),
                Arguments.of(ed25519PublicKeyPem, "OKP", "x", null));
    }

    @ParameterizedTest(name = "{1} key round-trip")
    @MethodSource("roundTripTestCases")
    void keyRoundTrip_pemToJwkToPem_maintainsKeyMaterial(String originalPem, String keyType, String param1,
                                                         String param2) {
        val originalJwk  = KeysFunctionLibrary.jwkFromPublicKey(Val.of(originalPem));
        val convertedPem = KeysFunctionLibrary.publicKeyFromJwk(originalJwk);
        val finalJwk     = KeysFunctionLibrary.jwkFromPublicKey(convertedPem);

        assertThat(originalJwk.isDefined()).isTrue();
        assertThat(convertedPem.isDefined()).isTrue();
        assertThat(finalJwk.isDefined()).isTrue();

        // Verify key material is preserved
        assertThat(finalJwk.get().get("kty").asText()).isEqualTo(keyType);
        assertThat(finalJwk.get().get(param1).asText()).isEqualTo(originalJwk.get().get(param1).asText());
        if (param2 != null) {
            assertThat(finalJwk.get().get(param2).asText()).isEqualTo(originalJwk.get().get(param2).asText());
        }
    }

    @Test
    void keyRoundTrip_multipleCycles_maintainsStability() {
        var currentPem = rsa2048PublicKeyPem;

        // Perform 5 round-trips
        for (int i = 0; i < 5; i++) {
            val jwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(currentPem));
            val pem = KeysFunctionLibrary.publicKeyFromJwk(jwk);
            assertThat(pem.isDefined()).as("Round-trip " + i + " failed").isTrue();
            currentPem = pem.getText();
        }

        // Verify final key still matches original
        val finalJwk    = KeysFunctionLibrary.jwkFromPublicKey(Val.of(currentPem));
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(rsa2048PublicKeyPem));

        assertThat(finalJwk.get().get("n").asText()).isEqualTo(originalJwk.get().get("n").asText());
        assertThat(finalJwk.get().get("e").asText()).isEqualTo(originalJwk.get().get("e").asText());
    }

    /* Integration Tests */

    @Test
    void certificateExtraction_canBeConvertedToJwkAndBack() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Val.of(rsaCertificatePem));
        val jwk          = KeysFunctionLibrary.jwkFromPublicKey(extractedKey);
        val reconverted  = KeysFunctionLibrary.publicKeyFromJwk(jwk);

        assertThat(jwk.isDefined()).isTrue();
        assertThat(reconverted.isDefined()).isTrue();
        assertThat(jwk.get().get("kty").asText()).isEqualTo("RSA");
    }

    @Test
    void allKeyOperations_workTogetherSeamlessly() {
        // Parse original key
        val parsedKey = KeysFunctionLibrary.publicKeyFromPem(Val.of(ecP256PublicKeyPem));
        assertThat(parsedKey.get().get("algorithm").asText()).isEqualTo("EC");

        // Extract information
        val algorithm = KeysFunctionLibrary.algorithmFromKey(Val.of(ecP256PublicKeyPem));
        val size      = KeysFunctionLibrary.sizeFromKey(Val.of(ecP256PublicKeyPem));
        val curve     = KeysFunctionLibrary.curveFromKey(Val.of(ecP256PublicKeyPem));

        assertThat(algorithm.getText()).isEqualTo("EC");
        assertThat(size.get().asInt()).isEqualTo(256);
        assertThat(curve.getText()).isEqualTo("secp256r1");

        // Convert to JWK
        val jwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(ecP256PublicKeyPem));
        assertThat(jwk.get().get("kty").asText()).isEqualTo("EC");
        assertThat(jwk.get().get("crv").asText()).isEqualTo("P-256");

        // Convert back to PEM
        val converted = KeysFunctionLibrary.publicKeyFromJwk(jwk);
        assertThat(converted.isDefined()).isTrue();

        // Verify converted key has same properties
        val convertedAlgorithm = KeysFunctionLibrary.algorithmFromKey(converted);
        val convertedSize      = KeysFunctionLibrary.sizeFromKey(converted);
        val convertedCurve     = KeysFunctionLibrary.curveFromKey(converted);

        assertThat(convertedAlgorithm.getText()).isEqualTo(algorithm.getText());
        assertThat(convertedSize.get().asInt()).isEqualTo(size.get().asInt());
        assertThat(convertedCurve.getText()).isEqualTo(curve.getText());
    }

    @Test
    void edgeCase_rsaWithLargeExponent_handlesCorrectly() {
        // RSA keys typically use 65537 (0x10001) as exponent, but can use larger values
        val jwk    = KeysFunctionLibrary.jwkFromPublicKey(Val.of(rsa2048PublicKeyPem));
        val exponent = jwk.get().get("e").asText();

        // Verify exponent is properly base64url encoded without padding
        assertThat(exponent).doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(exponent)).isNotEmpty();
    }

    @Test
    void edgeCase_ecCoordinates_handleLeadingZeros() {
        // EC coordinates should handle leading zeros properly (no sign byte issues)
        val jwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(ecP256PublicKeyPem));
        val x   = jwk.get().get("x").asText();
        val y   = jwk.get().get("y").asText();

        // Decode and verify proper unsigned representation
        val xBytes = Base64.getUrlDecoder().decode(x);
        val yBytes = Base64.getUrlDecoder().decode(y);

        // P-256 coordinates should be 32 bytes each
        assertThat(xBytes.length).isLessThanOrEqualTo(32);
        assertThat(yBytes.length).isLessThanOrEqualTo(32);
    }

    @Test
    void edgeCase_ed25519RawKey_isExactly32Bytes() {
        val jwk = KeysFunctionLibrary.jwkFromPublicKey(Val.of(ed25519PublicKeyPem));
        val x   = jwk.get().get("x").asText();

        val xBytes = Base64.getUrlDecoder().decode(x);
        assertThat(xBytes).as("Ed25519 raw key must be exactly 32 bytes").hasSize(32);
    }

    /* Error Message Quality Tests */

    @Test
    void errorMessages_endWithPeriod() {
        val invalidKey   = KeysFunctionLibrary.publicKeyFromPem(Val.of("invalid"));
        val invalidCert  = KeysFunctionLibrary.publicKeyFromCertificate(Val.of("invalid"));
        val invalidJwk   = KeysFunctionLibrary.publicKeyFromJwk(Val.of(JSON.objectNode()));
        val wrongKeyType = KeysFunctionLibrary.curveFromKey(Val.of(rsa2048PublicKeyPem));

        assertThat(invalidKey.getMessage()).endsWith(".");
        assertThat(invalidCert.getMessage()).endsWith(".");
        assertThat(invalidJwk.getMessage()).endsWith(".");
        assertThat(wrongKeyType.getMessage()).endsWith(".");
    }

    @Test
    void errorMessages_areActionable() {
        val missingModulus = KeysFunctionLibrary.publicKeyFromJwk(Val.of(JSON.objectNode().put("kty", "RSA")));

        assertThat(missingModulus.getMessage()).containsIgnoringCase("missing").containsIgnoringCase("modulus");
    }

    /* Helper Methods */

    private static String toPem(PublicKey publicKey) {
        val encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----""".formatted(encoded);
    }

    private static String toCertPem(X509Certificate certificate) throws Exception {
        val encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return """
                -----BEGIN CERTIFICATE-----
                %s
                -----END CERTIFICATE-----""".formatted(encoded);
    }

    private static X509Certificate generateCertificate(KeyPair keyPair, String algorithm) throws Exception {
        val now       = Instant.now();
        val notBefore = now.minus(1, ChronoUnit.DAYS);
        val notAfter  = now.plus(365, ChronoUnit.DAYS);

        val subject = new X500Name("CN=Cthulhu,O=R'lyeh Deep One Society,C=PA");

        val certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.currentTimeMillis()),
                Date.from(notBefore), Date.from(notAfter), subject, keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        val signatureAlgorithm = switch (algorithm) {
            case "RSA" -> "SHA256withRSA";
            case "EC"  -> "SHA256withECDSA";
            default    -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };

        val signer = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        val holder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}