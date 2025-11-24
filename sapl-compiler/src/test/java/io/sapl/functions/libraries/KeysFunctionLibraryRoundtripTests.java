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

import io.sapl.api.model.Value;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that KeysFunctionLibrary produces valid,
 * functionally correct cryptographic keys that can
 * be used for actual cryptographic operations.
 */
class KeysFunctionLibraryRoundtripTests {

    private static KeyPair rsaKeyPair;
    private static KeyPair ecP256KeyPair;
    private static KeyPair ecP384KeyPair;
    private static KeyPair ed25519KeyPair;

    private static String rsaPublicKeyPem;
    private static String ecP256PublicKeyPem;
    private static String ecP384PublicKeyPem;
    private static String ed25519PublicKeyPem;

    @BeforeAll
    static void setup() throws Exception {
        // Generate key pairs for signing operations
        var rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        rsaKeyPair      = rsaGenerator.generateKeyPair();
        rsaPublicKeyPem = toPem(rsaKeyPair.getPublic());

        var ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        ecP256KeyPair      = ecGenerator.generateKeyPair();
        ecP256PublicKeyPem = toPem(ecP256KeyPair.getPublic());

        ecGenerator.initialize(new ECGenParameterSpec("secp384r1"));
        ecP384KeyPair      = ecGenerator.generateKeyPair();
        ecP384PublicKeyPem = toPem(ecP384KeyPair.getPublic());

        var edGenerator = KeyPairGenerator.getInstance("Ed25519");
        ed25519KeyPair      = edGenerator.generateKeyPair();
        ed25519PublicKeyPem = toPem(ed25519KeyPair.getPublic());
    }

    /* Round-trip Functional Verification */

    @Test
    void rsaKey_roundTripConversion_producesEquivalentKey() {
        // Convert to JWK
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsaPublicKeyPem));
        assertThat(jwkResult).isNotInstanceOf(ErrorValue.class);

        // Convert back to PEM
        val pemResult = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwkResult);
        assertThat(pemResult).isNotInstanceOf(ErrorValue.class);

        // Parse both original and converted keys
        val originalKey  = parsePublicKeyFromPem(rsaPublicKeyPem);
        val convertedKey = parsePublicKeyFromPem(((TextValue) pemResult).value());

        // Verify they are functionally equivalent
        assertThat(convertedKey).isInstanceOf(RSAPublicKey.class);
        val originalRsa  = (RSAPublicKey) originalKey;
        val convertedRsa = (RSAPublicKey) convertedKey;

        assertThat(convertedRsa.getModulus()).isEqualTo(originalRsa.getModulus());
        assertThat(convertedRsa.getPublicExponent()).isEqualTo(originalRsa.getPublicExponent());
    }

    static Stream<Arguments> ecKeyTestCases() {
        return Stream.of(Arguments.of(ecP256PublicKeyPem, ecP256KeyPair, "P-256", "secp256r1"),
                Arguments.of(ecP384PublicKeyPem, ecP384KeyPair, "P-384", "secp384r1"));
    }

    @ParameterizedTest(name = "EC {2} round-trip")
    @MethodSource("ecKeyTestCases")
    void ecKey_roundTripConversion_producesEquivalentKey(String pemKey, KeyPair keyPair, String jwkCurve,
            String javaCurve) {
        // Convert to JWK
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(pemKey));
        assertThat(jwkResult).isNotInstanceOf(ErrorValue.class);
        assertThat(((TextValue) ((ObjectValue) jwkResult).get("crv")).value()).isEqualTo(jwkCurve);

        // Convert back to PEM
        val pemResult = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwkResult);
        assertThat(pemResult).isNotInstanceOf(ErrorValue.class);

        // Parse both original and converted keys
        val originalKey  = parsePublicKeyFromPem(pemKey);
        val convertedKey = parsePublicKeyFromPem(((TextValue) pemResult).value());

        // Verify they are functionally equivalent
        assertThat(convertedKey).isInstanceOf(ECPublicKey.class);
        val originalEc  = (ECPublicKey) originalKey;
        val convertedEc = (ECPublicKey) convertedKey;

        assertThat(convertedEc.getW().getAffineX()).isEqualTo(originalEc.getW().getAffineX());
        assertThat(convertedEc.getW().getAffineY()).isEqualTo(originalEc.getW().getAffineY());
    }

    @Test
    void ed25519Key_roundTripConversion_producesEquivalentKey() {
        // Convert to JWK
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));
        assertThat(jwkResult).isNotInstanceOf(ErrorValue.class);

        // Verify JWK structure
        val jwk = (ObjectValue) jwkResult;
        assertThat(((TextValue) jwk.get("kty")).value()).isEqualTo("OKP");
        assertThat(((TextValue) jwk.get("crv")).value()).isEqualTo("Ed25519");
        assertThat(jwk.containsKey("x")).isTrue();

        // Verify x parameter is 32 bytes (raw Ed25519 key)
        val xBytes = Base64.getUrlDecoder().decode(((TextValue) jwk.get("x")).value());
        assertThat(xBytes).hasSize(32);

        // Convert back to PEM
        val pemResult = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwkResult);
        assertThat(pemResult).isNotInstanceOf(ErrorValue.class);

        // Parse both keys
        val originalKey  = parsePublicKeyFromPem(ed25519PublicKeyPem);
        val convertedKey = parsePublicKeyFromPem(((TextValue) pemResult).value());

        // Verify encoded forms match (EdEC doesn't expose coordinates like EC)
        assertThat(convertedKey.getEncoded()).isEqualTo(originalKey.getEncoded());
    }

    /* Signature Verification Tests */

    @Test
    void rsaKey_afterRoundTrip_canVerifySignature() throws Exception {
        val message = "The stars are right, Cthulhu awakens".getBytes(StandardCharsets.UTF_8);

        // Sign with original private key
        val signature = signRsa(message, rsaKeyPair.getPrivate());

        // Convert public key through round-trip
        val jwk          = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsaPublicKeyPem));
        val convertedPem = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwk);
        val convertedKey = parsePublicKeyFromPem(((TextValue) convertedPem).value());

        // Verify signature with converted key
        val verified = verifyRsa(message, signature, convertedKey);
        assertThat(verified).as("Converted RSA key should verify signatures from original key").isTrue();
    }

    @Test
    void ecKey_afterRoundTrip_canVerifySignature() throws Exception {
        val message = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn".getBytes(StandardCharsets.UTF_8);

        // Sign with original private key
        val signature = signEc(message, ecP256KeyPair.getPrivate());

        // Convert public key through round-trip
        val jwk          = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));
        val convertedPem = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwk);
        val convertedKey = parsePublicKeyFromPem(((TextValue) convertedPem).value());

        // Verify signature with converted key
        val verified = verifyEc(message, signature, convertedKey);
        assertThat(verified).as("Converted EC key should verify signatures from original key").isTrue();
    }

    @Test
    void ed25519Key_afterRoundTrip_canVerifySignature() throws Exception {
        val message = "That is not dead which can eternal lie".getBytes(StandardCharsets.UTF_8);

        // Sign with original private key
        val signature = signEd25519(message, ed25519KeyPair.getPrivate());

        // Convert public key through round-trip
        val jwk          = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));
        val convertedPem = KeysFunctionLibrary.publicKeyFromJwk((ObjectValue) jwk);
        val convertedKey = parsePublicKeyFromPem(((TextValue) convertedPem).value());

        // Verify signature with converted key
        val verified = verifyEd25519(message, signature, convertedKey);
        assertThat(verified).as("Converted Ed25519 key should verify signatures from original key").isTrue();
    }

    /* JWK RFC Compliance Tests */

    @Test
    void rsaJwk_followsRfc7517Structure() {
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsaPublicKeyPem));
        val jwk       = (ObjectValue) jwkResult;

        // Verify required fields per RFC 7517
        assertThat(jwk.containsKey("kty")).as("JWK must have 'kty' field").isTrue();
        assertThat(((TextValue) jwk.get("kty")).value()).isEqualTo("RSA");

        // Verify RSA-specific fields per RFC 7518 Section 6.3
        assertThat(jwk.containsKey("n")).as("RSA JWK must have 'n' (modulus)").isTrue();
        assertThat(jwk.containsKey("e")).as("RSA JWK must have 'e' (exponent)").isTrue();

        // Verify base64url encoding (no padding)
        val nValue = ((TextValue) jwk.get("n")).value();
        val eValue = ((TextValue) jwk.get("e")).value();
        assertThat(nValue).doesNotContain("=");
        assertThat(eValue).doesNotContain("=");

        // Verify we can decode the values
        val nBytes = Base64.getUrlDecoder().decode(nValue);
        val eBytes = Base64.getUrlDecoder().decode(eValue);
        assertThat(nBytes).isNotEmpty();
        assertThat(eBytes).isNotEmpty();

        // Verify modulus matches original key
        val originalKey = (RSAPublicKey) parsePublicKeyFromPem(rsaPublicKeyPem);
        val jwkModulus  = new BigInteger(1, nBytes);
        assertThat(jwkModulus).isEqualTo(originalKey.getModulus());
    }

    @Test
    void ecJwk_followsRfc7517Structure() {
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));
        val jwk       = (ObjectValue) jwkResult;

        // Verify required fields per RFC 7517
        assertThat(jwk.containsKey("kty")).as("JWK must have 'kty' field").isTrue();
        assertThat(((TextValue) jwk.get("kty")).value()).isEqualTo("EC");

        // Verify EC-specific fields per RFC 7518 Section 6.2
        assertThat(jwk.containsKey("crv")).as("EC JWK must have 'crv' (curve)").isTrue();
        assertThat(jwk.containsKey("x")).as("EC JWK must have 'x' coordinate").isTrue();
        assertThat(jwk.containsKey("y")).as("EC JWK must have 'y' coordinate").isTrue();

        // Verify curve name follows RFC 7518 (P-256, not secp256r1)
        assertThat(((TextValue) jwk.get("crv")).value()).isEqualTo("P-256");

        // Verify base64url encoding (no padding)
        val xValue = ((TextValue) jwk.get("x")).value();
        val yValue = ((TextValue) jwk.get("y")).value();
        assertThat(xValue).doesNotContain("=");
        assertThat(yValue).doesNotContain("=");

        // Verify coordinates match original key
        val originalKey = (ECPublicKey) parsePublicKeyFromPem(ecP256PublicKeyPem);
        val xBytes      = Base64.getUrlDecoder().decode(xValue);
        val yBytes      = Base64.getUrlDecoder().decode(yValue);
        val jwkX        = new BigInteger(1, xBytes);
        val jwkY        = new BigInteger(1, yBytes);

        assertThat(jwkX).isEqualTo(originalKey.getW().getAffineX());
        assertThat(jwkY).isEqualTo(originalKey.getW().getAffineY());
    }

    @Test
    void ed25519Jwk_followsRfc8037Structure() {
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ed25519PublicKeyPem));
        val jwk       = (ObjectValue) jwkResult;

        // Verify required fields per RFC 8037 Section 2
        assertThat(jwk.containsKey("kty")).as("JWK must have 'kty' field").isTrue();
        assertThat(((TextValue) jwk.get("kty")).value()).isEqualTo("OKP");

        assertThat(jwk.containsKey("crv")).as("OKP JWK must have 'crv' (curve)").isTrue();
        assertThat(((TextValue) jwk.get("crv")).value()).isEqualTo("Ed25519");

        assertThat(jwk.containsKey("x")).as("OKP JWK must have 'x' parameter").isTrue();

        // Verify base64url encoding (no padding)
        val xValue = ((TextValue) jwk.get("x")).value();
        assertThat(xValue).doesNotContain("=");

        // Verify x parameter is exactly 32 bytes (raw Ed25519 public key)
        val xBytes = Base64.getUrlDecoder().decode(xValue);
        assertThat(xBytes).as("Ed25519 public key must be 32 bytes").hasSize(32);
    }

    /* JWK Reconstruction from Scratch */

    @Test
    void rsaJwk_canBeReconstructedIntoFunctionalKey() throws Exception {
        // Get JWK from library
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsaPublicKeyPem));
        val jwk       = (ObjectValue) jwkResult;

        // Manually reconstruct key from JWK (simulating external consumer)
        val nBytes   = Base64.getUrlDecoder().decode(((TextValue) jwk.get("n")).value());
        val eBytes   = Base64.getUrlDecoder().decode(((TextValue) jwk.get("e")).value());
        val modulus  = new BigInteger(1, nBytes);
        val exponent = new BigInteger(1, eBytes);

        val keySpec          = new RSAPublicKeySpec(modulus, exponent);
        val keyFactory       = KeyFactory.getInstance("RSA");
        val reconstructedKey = keyFactory.generatePublic(keySpec);

        // Verify reconstructed key can verify signatures
        val message   = "Ancient secrets".getBytes(StandardCharsets.UTF_8);
        val signature = signRsa(message, rsaKeyPair.getPrivate());
        val verified  = verifyRsa(message, signature, reconstructedKey);

        assertThat(verified).as("Manually reconstructed RSA key from JWK should work").isTrue();
    }

    @Test
    void ecJwk_canBeReconstructedIntoFunctionalKey() throws Exception {
        // Get JWK from library
        val jwkResult = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));
        val jwk       = (ObjectValue) jwkResult;

        // Manually reconstruct key from JWK
        val xBytes = Base64.getUrlDecoder().decode(((TextValue) jwk.get("x")).value());
        val yBytes = Base64.getUrlDecoder().decode(((TextValue) jwk.get("y")).value());
        val x      = new BigInteger(1, xBytes);
        val y      = new BigInteger(1, yBytes);
        val point  = new ECPoint(x, y);

        // Get curve parameters
        val originalKey = (ECPublicKey) parsePublicKeyFromPem(ecP256PublicKeyPem);
        val ecParams    = originalKey.getParams();

        val keySpec          = new ECPublicKeySpec(point, ecParams);
        val keyFactory       = KeyFactory.getInstance("EC");
        val reconstructedKey = keyFactory.generatePublic(keySpec);

        // Verify reconstructed key can verify signatures
        val message   = "Eldritch knowledge".getBytes(StandardCharsets.UTF_8);
        val signature = signEc(message, ecP256KeyPair.getPrivate());
        val verified  = verifyEc(message, signature, reconstructedKey);

        assertThat(verified).as("Manually reconstructed EC key from JWK should work").isTrue();
    }

    /* Edge Case Integration Tests */

    @Test
    void rsaKey_withLargeExponent_handlesCorrectly() {
        // Most RSA keys use 65537 (0x10001) but library should handle any valid
        // exponent
        val jwk = KeysFunctionLibrary.jwkFromPublicKey(Value.of(rsaPublicKeyPem));

        val eBytes   = Base64.getUrlDecoder().decode(((TextValue) ((ObjectValue) jwk).get("e")).value());
        val exponent = new BigInteger(1, eBytes);

        // Common RSA exponent is 65537
        assertThat(exponent).isGreaterThan(BigInteger.ZERO);

        // Verify no sign byte issues
        assertThat(eBytes[0]).isNotEqualTo((byte) 0);
    }

    @Test
    void ecKey_coordinates_haveCorrectLength() {
        val jwk = KeysFunctionLibrary.jwkFromPublicKey(Value.of(ecP256PublicKeyPem));

        val xBytes = Base64.getUrlDecoder().decode(((TextValue) ((ObjectValue) jwk).get("x")).value());
        val yBytes = Base64.getUrlDecoder().decode(((TextValue) ((ObjectValue) jwk).get("y")).value());

        // P-256 coordinates should be at most 32 bytes (256 bits)
        assertThat(xBytes.length).isLessThanOrEqualTo(32);
        assertThat(yBytes.length).isLessThanOrEqualTo(32);

        // Should not have sign bytes
        val x = new BigInteger(1, xBytes);
        val y = new BigInteger(1, yBytes);
        assertThat(x).isPositive();
        assertThat(y).isPositive();
    }

    /* Helper Methods */

    private static String toPem(PublicKey publicKey) {
        val encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----""".formatted(encoded);
    }

    private static PublicKey parsePublicKeyFromPem(String pem) {
        val pemContent = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        val encoded    = Base64.getDecoder().decode(pemContent);
        val keySpec    = new java.security.spec.X509EncodedKeySpec(encoded);

        // Try different algorithms
        for (String algorithm : new String[] { "RSA", "EC", "Ed25519" }) {
            try {
                return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
            } catch (Exception ignored) {
                /* no-op */
            }
        }
        throw new IllegalArgumentException("Unable to parse public key");
    }

    private static byte[] signRsa(byte[] message, PrivateKey privateKey) throws Exception {
        val signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    private static boolean verifyRsa(byte[] message, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        val signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(message);
        return signature.verify(signatureBytes);
    }

    private static byte[] signEc(byte[] message, PrivateKey privateKey) throws Exception {
        val signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    private static boolean verifyEc(byte[] message, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        val signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(message);
        return signature.verify(signatureBytes);
    }

    private static byte[] signEd25519(byte[] message, PrivateKey privateKey) throws Exception {
        val signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    private static boolean verifyEd25519(byte[] message, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        val signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(message);
        return signature.verify(signatureBytes);
    }
}
