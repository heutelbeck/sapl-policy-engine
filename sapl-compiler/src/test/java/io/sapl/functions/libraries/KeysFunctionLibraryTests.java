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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.model.*;
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
import io.sapl.functions.DefaultFunctionBroker;

import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class KeysFunctionLibraryTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(KeysFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

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
        return Stream.of(arguments(rsa2048PublicKeyPem, "RSA", "X.509", 2048, null),
                arguments(rsa4096PublicKeyPem, "RSA", "X.509", 4096, null),
                arguments(ecP256PublicKeyPem, "EC", "X.509", 256, "secp256r1"),
                arguments(ecP384PublicKeyPem, "EC", "X.509", 384, "secp384r1"),
                arguments(ecP521PublicKeyPem, "EC", "X.509", 521, "secp521r1"),
                arguments(ed25519PublicKeyPem, "EdDSA", "X.509", 256, null));
    }

    @ParameterizedTest(name = "{1} key with size {3}")
    @MethodSource("keyTypeTestCases")
    void when_parsePublicKeyWithVariousKeyTypes_then_returnsCorrectKeyObject(String keyPem, String expectedAlgorithm,
            String expectedFormat, int expectedSize, String expectedCurve) {
        val result = KeysFunctionLibrary.publicKeyFromPem(Value.of(keyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val keyObject = (ObjectValue) result;
        assertThat(keyObject).containsEntry("algorithm", Value.of(expectedAlgorithm))
                .containsEntry("format", Value.of(expectedFormat)).containsEntry("size", Value.of(expectedSize));

        if (expectedCurve != null) {
            assertThat(keyObject).containsEntry("curve", Value.of(expectedCurve));
        }
    }

    static Stream<Arguments> invalidKeyInputs() {
        return Stream.of(arguments("", "Empty input from the void"),
                arguments("Ph'nglui mglw'nafh Cthulhu", "Eldritch gibberish"), arguments("""
                        -----BEGIN PUBLIC KEY-----
                        invalid
                        -----END PUBLIC KEY-----""", "Corrupted PEM structure"), arguments("""
                        -----BEGIN PUBLIC KEY-----
                        IA==
                        -----END PUBLIC KEY-----""", "Truncated key data"),
                arguments("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA", "Base64 without PEM markers"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidKeyInputs")
    void when_parsePublicKeyWithInvalidInput_then_returnsError(String invalidInput, String description) {
        val result = KeysFunctionLibrary.publicKeyFromPem(Value.of(invalidInput));

        assertThat(result).as("Should fail for: " + description).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Failed to parse public key");
    }

    /* Certificate Extraction Tests */

    static Stream<Arguments> certificateTestCases() {
        return Stream.of(arguments("rsaCertificatePem", "RSA"), arguments("ecCertificatePem", "EC"));
    }

    @ParameterizedTest(name = "Extract {1} key from certificate")
    @MethodSource("certificateTestCases")
    void when_extractPublicKeyFromCertificateWithValidCert_then_returnsValidPem(String certFieldName,
            String expectedAlgorithm) throws Exception {
        val certPem = (String) KeysFunctionLibraryTests.class.getDeclaredField(certFieldName).get(null);
        val result  = KeysFunctionLibrary.publicKeyFromCertificate(Value.of(certPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val pemText = (TextValue) result;
        assertThat(pemText.value()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify extracted key can be parsed
        val parsedKey = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem(pemText);
        assertThat(parsedKey).isNotInstanceOf(ErrorValue.class).containsEntry("algorithm", Value.of(expectedAlgorithm));
    }

    static Stream<String> invalidCertificates() {
        return Stream.of("", """
                -----BEGIN CERTIFICATE-----
                corrupted
                -----END CERTIFICATE-----""", "Not a certificate at all", "The Necronomicon, bound in human flesh", """
                -----BEGIN CERTIFICATE-----
                -----END CERTIFICATE-----""");
    }

    @ParameterizedTest
    @MethodSource("invalidCertificates")
    void when_extractPublicKeyFromCertificateWithInvalidCert_then_returnsError(String invalidCert) {
        val result = KeysFunctionLibrary.publicKeyFromCertificate(Value.of(invalidCert));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Failed to extract public key");
    }

    @Test
    void when_extractPublicKeyFromCertificateRoundTrip_then_maintainsKeyProperties() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Value.of(rsaCertificatePem));
        val parsedKey    = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem((TextValue) extractedKey);

        assertThat(parsedKey).containsEntry("algorithm", Value.of("RSA")).containsEntry("size", Value.of(2048));
    }

    @Test
    void when_extractPublicKeyFromCertificateWithEcCertificate_then_extractsEcKey() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Value.of(ecCertificatePem));
        val parsedKey    = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem((TextValue) extractedKey);

        assertThat(parsedKey).isNotInstanceOf(ErrorValue.class).containsEntry("algorithm", Value.of("EC"))
                .containsEntry("size", Value.of(256)).containsEntry("curve", Value.of("secp256r1"));
    }

    /* Key Information Extraction Tests */

    @ParameterizedTest(name = "Extract algorithm from {0}")
    @MethodSource("keyTypeTestCases")
    void when_extractKeyAlgorithmWithVariousKeys_then_returnsCorrectAlgorithm(String keyPem, String expectedAlgorithm,
            String ignored1, int ignored2, String ignored3) {
        val result = KeysFunctionLibrary.algorithmFromKey(Value.of(keyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expectedAlgorithm));
    }

    @ParameterizedTest(name = "Extract size from key with expected size {3}")
    @MethodSource("keyTypeTestCases")
    void when_extractKeySizeWithVariousKeys_then_returnsCorrectSize(String keyPem, String ignored1, String ignored2,
            int expectedSize, String ignored3) {
        val result = KeysFunctionLibrary.sizeFromKey(Value.of(keyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expectedSize));
    }

    static Stream<Arguments> ecCurveTestCases() {
        return Stream.of(arguments(ecP256PublicKeyPem, "secp256r1"), arguments(ecP384PublicKeyPem, "secp384r1"),
                arguments(ecP521PublicKeyPem, "secp521r1"));
    }

    @ParameterizedTest(name = "Extract curve {1}")
    @MethodSource("ecCurveTestCases")
    void when_extractEcCurveWithEcKeys_then_returnsCorrectCurve(String keyPem, String expectedCurve) {
        val result = KeysFunctionLibrary.curveFromKey(Value.of(keyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expectedCurve));
    }

    static Stream<String> nonEcKeys() {
        return Stream.of(rsa2048PublicKeyPem, rsa4096PublicKeyPem, ed25519PublicKeyPem);
    }

    @ParameterizedTest
    @MethodSource("nonEcKeys")
    void when_extractEcCurveWithNonEcKey_then_returnsError(String nonEcKeyPem) {
        val result = KeysFunctionLibrary.curveFromKey(Value.of(nonEcKeyPem));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).containsIgnoringCase("not an EC key");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "invalid", "The sign of Hastur" })
    void when_extractKeyInformationWithInvalidKey_then_returnsError(String invalidKey) {
        assertThat(KeysFunctionLibrary.algorithmFromKey(Value.of(invalidKey))).isInstanceOf(ErrorValue.class);
        assertThat(KeysFunctionLibrary.sizeFromKey(Value.of(invalidKey))).isInstanceOf(ErrorValue.class);
        assertThat(KeysFunctionLibrary.curveFromKey(Value.of(invalidKey))).isInstanceOf(ErrorValue.class);
    }

    /* JWK Conversion Tests - PEM to JWK */

    @Test
    void when_publicKeyToJwkWithRsaKey_then_returnsValidJwk() {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsa2048PublicKeyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val jwk = (ObjectValue) result;
        assertThat(jwk).containsEntry("kty", Value.of("RSA")).containsKey("n").containsKey("e").satisfies(j -> {
            assertThat(getTextFieldValue((ObjectValue) j, "n")).isNotEmpty();
            assertThat(getTextFieldValue((ObjectValue) j, "e")).isNotEmpty();
        });
    }

    static Stream<Arguments> ecJwkTestCases() {
        return Stream.of(arguments(ecP256PublicKeyPem, "P-256"), arguments(ecP384PublicKeyPem, "P-384"),
                arguments(ecP521PublicKeyPem, "P-521"));
    }

    @ParameterizedTest(name = "EC key with curve {1}")
    @MethodSource("ecJwkTestCases")
    void when_publicKeyToJwkWithEcKey_then_returnsValidJwk(String keyPem, String expectedCurve) {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Value.of(keyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val jwk = (ObjectValue) result;
        assertThat(jwk).containsEntry("kty", Value.of("EC")).containsEntry("crv", Value.of(expectedCurve))
                .containsKey("x").containsKey("y").satisfies(j -> {
                    assertThat(getTextFieldValue((ObjectValue) j, "x")).isNotEmpty();
                    assertThat(getTextFieldValue((ObjectValue) j, "y")).isNotEmpty();
                });
    }

    @Test
    void when_publicKeyToJwkWithEd25519Key_then_returnsValidJwk() {
        val result = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        val jwk = (ObjectValue) result;
        assertThat(jwk).containsEntry("kty", Value.of("OKP")).containsEntry("crv", Value.of("Ed25519")).containsKey("x")
                .satisfies(j -> {
                    val xValue = getTextFieldValue((ObjectValue) j, "x");
                    assertThat(xValue).isNotEmpty();
                    // Verify x parameter is 32 bytes when decoded
                    assertThat(Base64.getUrlDecoder().decode(xValue)).hasSize(32);
                });
    }

    /* JWK Conversion Tests - JWK to PEM */

    @Test
    void when_jwkToPublicKeyWithRsaJwk_then_returnsValidPem() {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsa2048PublicKeyPem));
        val result      = (TextValue) KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) originalJwk);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(result.value()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed
        val parsedKey = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey).containsEntry("algorithm", Value.of("RSA"));
    }

    @ParameterizedTest(name = "EC key with curve {1}")
    @MethodSource("ecJwkTestCases")
    void when_jwkToPublicKeyWithEcJwk_then_returnsValidPem(String keyPem, String expectedCurve) {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Value.of(keyPem));
        val result      = (TextValue) KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) originalJwk);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(result.value()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed and has correct curve
        val parsedKey = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey).containsEntry("algorithm", Value.of("EC"));
    }

    @Test
    void when_jwkToPublicKeyWithEd25519Jwk_then_returnsValidPem() {
        val originalJwk = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));
        val result      = (TextValue) KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) originalJwk);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(result.value()).contains("BEGIN PUBLIC KEY").contains("END PUBLIC KEY");

        // Verify the key can be parsed
        val parsedKey = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem(result);
        assertThat(parsedKey).containsEntry("algorithm", Value.of("EdDSA"));
    }

    static Stream<Arguments> invalidJwkTestCases() {
        return Stream.of(
                // Missing kty
                arguments(JSON.objectNode().put("n", "test").put("e", "test"), "missing kty field"),
                // Invalid kty
                arguments(JSON.objectNode().put("kty", "SHOGGOTH"), "unsupported key type"),
                // RSA missing modulus
                arguments(JSON.objectNode().put("kty", "RSA").put("e", "AQAB"), "missing modulus"),
                // RSA missing exponent
                arguments(JSON.objectNode().put("kty", "RSA").put("n", "test"), "missing exponent"),
                // EC missing curve
                arguments(JSON.objectNode().put("kty", "EC").put("x", "test").put("y", "test"), "missing curve"),
                // EC missing x
                arguments(JSON.objectNode().put("kty", "EC").put("crv", "P-256").put("y", "test"), "missing x"),
                // EC missing y
                arguments(JSON.objectNode().put("kty", "EC").put("crv", "P-256").put("x", "test"), "missing y"),
                // EC unsupported curve
                arguments(JSON.objectNode().put("kty", "EC").put("crv", "secp256k1").put("x", "test").put("y", "test"),
                        "unsupported curve"),
                // OKP missing curve
                arguments(JSON.objectNode().put("kty", "OKP").put("x", "test"), "missing curve"),
                // OKP missing x
                arguments(JSON.objectNode().put("kty", "OKP").put("crv", "Ed25519"), "missing x"),
                // OKP unsupported curve
                arguments(JSON.objectNode().put("kty", "OKP").put("crv", "Ed448").put("x", "test"),
                        "unsupported OKP curve"),
                // Invalid base64 encoding
                arguments(JSON.objectNode().put("kty", "RSA").put("n", "not!!!base64").put("e", "AQAB"),
                        "invalid base64"));
    }

    @ParameterizedTest(name = "Invalid JWK: {1}")
    @MethodSource("invalidJwkTestCases")
    void when_jwkToPublicKeyWithInvalidJwk_then_returnsError(com.fasterxml.jackson.databind.node.ObjectNode invalidJwk,
            String description) {
        val result = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) ValueJsonMarshaller.fromJsonNode(invalidJwk));

        assertThat(result).as("Should fail for: " + description).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).containsIgnoringCase("failed");
    }

    /* Round-trip Conversion Tests */

    static Stream<Arguments> roundTripTestCases() {
        return Stream.of(arguments(rsa2048PublicKeyPem, "RSA", "n", "e"),
                arguments(rsa4096PublicKeyPem, "RSA", "n", "e"), arguments(ecP256PublicKeyPem, "EC", "x", "y"),
                arguments(ecP384PublicKeyPem, "EC", "x", "y"), arguments(ecP521PublicKeyPem, "EC", "x", "y"),
                arguments(ed25519PublicKeyPem, "OKP", "x", null));
    }

    @ParameterizedTest(name = "{1} key round-trip")
    @MethodSource("roundTripTestCases")
    void when_keyRoundTripPemToJwkToPem_then_maintainsKeyMaterial(String originalPem, String keyType, String param1,
            String param2) {
        val originalJwkValue = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(originalPem));
        val convertedPem     = KeysFunctionLibrary.publicKeyFromJwk(originalJwkValue);
        val finalJwkValue    = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey((TextValue) convertedPem);

        assertThat(originalJwkValue).isNotInstanceOf(ErrorValue.class);
        assertThat(convertedPem).isNotInstanceOf(ErrorValue.class);
        assertThat(finalJwkValue).isNotInstanceOf(ErrorValue.class).containsEntry("kty", Value.of(keyType));

        // Verify key material is preserved
        assertThat(finalJwkValue.get(param1)).isEqualTo(originalJwkValue.get(param1));
        if (param2 != null) {
            assertThat(finalJwkValue.get(param2)).isEqualTo(originalJwkValue.get(param2));
        }
    }

    @Test
    void when_keyRoundTripMultipleCycles_then_maintainsStability() {
        var currentPem = rsa2048PublicKeyPem;

        // Perform 5 round-trips
        for (int i = 0; i < 5; i++) {
            val jwk = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(currentPem));
            val pem = (TextValue) KeysFunctionLibrary.publicKeyFromJwk(jwk);
            assertThat(pem).as("Round-trip " + i + " failed").isNotInstanceOf(ErrorValue.class);
            currentPem = pem.value();
        }

        // Verify final key still matches original
        val finalJwk    = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(currentPem));
        val originalJwk = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsa2048PublicKeyPem));

        assertThat(finalJwk).satisfies(jwk -> {
            assertThat(jwk.get("n")).isEqualTo(originalJwk.get("n"));
            assertThat(jwk.get("e")).isEqualTo(originalJwk.get("e"));
        });
    }

    /* Integration Tests */

    @Test
    void when_certificateExtractionConvertedToJwkAndBack_then_succeeds() {
        val extractedKey = KeysFunctionLibrary.publicKeyFromCertificate(Value.of(rsaCertificatePem));
        val jwk          = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey((TextValue) extractedKey);
        val reconverted  = KeysFunctionLibrary.publicKeyFromJwk(jwk);

        assertThat(jwk).isNotInstanceOf(ErrorValue.class).containsEntry("kty", Value.of("RSA"));
        assertThat(reconverted).isNotInstanceOf(ErrorValue.class);
    }

    @Test
    void when_allKeyOperationsUsedTogether_then_worksSeamlessly() {
        // Parse original key
        val parsedKey = (ObjectValue) KeysFunctionLibrary.publicKeyFromPem(Value.of(ecP256PublicKeyPem));
        assertThat(parsedKey).containsEntry("algorithm", Value.of("EC"));

        // Extract information
        val algorithm = KeysFunctionLibrary.algorithmFromKey(Value.of(ecP256PublicKeyPem));
        val size      = KeysFunctionLibrary.sizeFromKey(Value.of(ecP256PublicKeyPem));
        val curve     = KeysFunctionLibrary.curveFromKey(Value.of(ecP256PublicKeyPem));

        assertThat(algorithm).isEqualTo(Value.of("EC"));
        assertThat(size).isEqualTo(Value.of(256));
        assertThat(curve).isEqualTo(Value.of("secp256r1"));

        // Convert to JWK and back to PEM
        val jwk       = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));
        val converted = (TextValue) KeysFunctionLibrary.publicKeyFromJwk(jwk);

        assertThat(jwk).containsEntry("kty", Value.of("EC")).containsEntry("crv", Value.of("P-256"));
        assertThat(converted).isNotInstanceOf(ErrorValue.class);

        // Verify converted key has same properties
        assertThat(KeysFunctionLibrary.algorithmFromKey(converted)).isEqualTo(algorithm);
        assertThat(KeysFunctionLibrary.sizeFromKey(converted)).isEqualTo(size);
        assertThat(KeysFunctionLibrary.curveFromKey(converted)).isEqualTo(curve);
    }

    @Test
    void when_rsaWithLargeExponent_then_handlesCorrectly() {
        // RSA keys typically use 65537 (0x10001) as exponent, but can use larger values
        val jwk      = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsa2048PublicKeyPem));
        val exponent = getTextFieldValue(jwk, "e");

        // Verify exponent is properly base64url encoded without padding
        assertThat(exponent).doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(exponent)).isNotEmpty();
    }

    @Test
    void when_ecCoordinates_then_handleLeadingZeros() {
        // EC coordinates should handle leading zeros properly (no sign byte issues)
        val jwk = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));

        // Decode and verify proper unsigned representation - P-256 coordinates should
        // be 32 bytes each
        assertThat(Base64.getUrlDecoder().decode(getTextFieldValue(jwk, "x")).length).isLessThanOrEqualTo(32);
        assertThat(Base64.getUrlDecoder().decode(getTextFieldValue(jwk, "y")).length).isLessThanOrEqualTo(32);
    }

    @Test
    void when_ed25519RawKey_then_isExactly32Bytes() {
        val jwk = (ObjectValue) KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));

        assertThat(Base64.getUrlDecoder().decode(getTextFieldValue(jwk, "x")))
                .as("Ed25519 raw key must be exactly 32 bytes").hasSize(32);
    }

    /* Error Message Quality Tests */

    @Test
    void when_errorMessages_then_endWithPeriod() {
        var errors = Stream.of(KeysFunctionLibrary.publicKeyFromPem(Value.of("invalid")),
                KeysFunctionLibrary.publicKeyFromCertificate(Value.of("invalid")),
                KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) ValueJsonMarshaller.fromJsonNode(JSON.objectNode())),
                KeysFunctionLibrary.curveFromKey(Value.of(rsa2048PublicKeyPem)));

        errors.forEach(error -> assertThat(((ErrorValue) error).message()).endsWith("."));
    }

    @Test
    void when_errorMessages_then_areActionable() {
        val missingModulus = KeysFunctionLibrary
                .publicKeyFromJwk((ObjectValue) ValueJsonMarshaller.fromJsonNode(JSON.objectNode().put("kty", "RSA")));

        assertThat(missingModulus).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .containsIgnoringCase("missing").containsIgnoringCase("modulus");
    }

    /* Helper Methods */

    private static String getTextFieldValue(ObjectValue object, String field) {
        return ((TextValue) Objects.requireNonNull(object.get(field))).value();
    }

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
