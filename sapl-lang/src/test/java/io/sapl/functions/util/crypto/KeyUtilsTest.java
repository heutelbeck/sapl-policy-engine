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
import lombok.SneakyThrows;
import lombok.val;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.stream.Stream;

import static io.sapl.functions.util.crypto.CryptoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyUtilsTest {

    @BeforeAll
    static void setupBouncyCastle() {
        Security.addProvider(new BouncyCastleProvider());
    }

    /* parsePublicKey Tests */

    @ParameterizedTest
    @MethodSource("provideKeysForParsing")
    void parsePublicKey_withValidKey_succeeds(String algorithm, PublicKey expectedKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        val pemKey = generatePemFromPublicKey(expectedKey);

        val parsedKey = KeyUtils.parsePublicKey(pemKey, algorithm);

        assertNotNull(parsedKey);
        assertEquals(expectedKey.getAlgorithm(), parsedKey.getAlgorithm());
        assertArrayEquals(expectedKey.getEncoded(), parsedKey.getEncoded());
    }

    @Test
    void parsePublicKey_withRsaKey_returnsRsaPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        val keyPair = generateRsaKeyPair(2048);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKey(pemKey, ALGORITHM_RSA);

        assertInstanceOf(RSAPublicKey.class, parsedKey);
        val rsaKey = (RSAPublicKey) parsedKey;
        assertEquals(2048, rsaKey.getModulus().bitLength());
    }

    @Test
    void parsePublicKey_withEcP256Key_returnsEcPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        val keyPair = generateEcKeyPair(CURVE_SECP256R1);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKey(pemKey, ALGORITHM_EC);

        assertInstanceOf(ECPublicKey.class, parsedKey);
    }

    @Test
    void parsePublicKey_withEd25519Key_returnsEdEcPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        val keyPair = generateEd25519KeyPair();
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKey(pemKey, ALGORITHM_EDDSA);

        assertInstanceOf(EdECPublicKey.class, parsedKey);
    }

    @Test
    void parsePublicKey_withInvalidAlgorithm_throwsException() {
        val keyPair = generateRsaKeyPair(2048);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        assertThrows(NoSuchAlgorithmException.class, () -> KeyUtils.parsePublicKey(pemKey, "INVALID_ALGORITHM"));
    }

    @Test
    void parsePublicKey_withWrongAlgorithm_throwsException() {
        val keyPair = generateRsaKeyPair(2048);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        assertThrows(InvalidKeySpecException.class, () -> KeyUtils.parsePublicKey(pemKey, ALGORITHM_EC));
    }

    @Test
    void parsePublicKey_withInvalidPem_throwsException() {
        val invalidPem = "-----BEGIN PUBLIC KEY-----\nINVALID_BASE64\n-----END PUBLIC KEY-----";

        val exception = assertThrows(PolicyEvaluationException.class,
                () -> KeyUtils.parsePublicKey(invalidPem, ALGORITHM_RSA));

        assertTrue(exception.getMessage().contains("Invalid Base64"));
    }

    @Test
    void parsePublicKey_withMalformedPem_throwsException() {
        val malformedPem = "not a pem key at all";

        assertThrows(InvalidKeySpecException.class, () -> KeyUtils.parsePublicKey(malformedPem, ALGORITHM_RSA));
    }

    /* parsePublicKeyWithAlgorithmDetection Tests */

    @ParameterizedTest
    @MethodSource("provideKeysForAlgorithmDetection")
    void parsePublicKeyWithAlgorithmDetection_withValidKey_detectsAlgorithm(PublicKey expectedKey) {
        val pemKey = generatePemFromPublicKey(expectedKey);

        val parsedKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(pemKey);

        assertNotNull(parsedKey);
        assertEquals(expectedKey.getAlgorithm(), parsedKey.getAlgorithm());
        assertArrayEquals(expectedKey.getEncoded(), parsedKey.getEncoded());
    }

    @Test
    void parsePublicKeyWithAlgorithmDetection_withRsaKey_detectsRsa() {
        val keyPair = generateRsaKeyPair(2048);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(pemKey);

        assertInstanceOf(RSAPublicKey.class, parsedKey);
    }

    @Test
    void parsePublicKeyWithAlgorithmDetection_withEcKey_detectsEc() {
        val keyPair = generateEcKeyPair(CURVE_SECP256R1);
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(pemKey);

        assertInstanceOf(ECPublicKey.class, parsedKey);
    }

    @Test
    void parsePublicKeyWithAlgorithmDetection_withEd25519Key_detectsEdDsa() {
        val keyPair = generateEd25519KeyPair();
        val pemKey  = generatePemFromPublicKey(keyPair.getPublic());

        val parsedKey = KeyUtils.parsePublicKeyWithAlgorithmDetection(pemKey);

        assertInstanceOf(EdECPublicKey.class, parsedKey);
    }

    @Test
    void parsePublicKeyWithAlgorithmDetection_withInvalidKey_throwsException() {
        val invalidPem = "-----BEGIN PUBLIC KEY-----\nINVALID\n-----END PUBLIC KEY-----";

        assertThrows(PolicyEvaluationException.class, () -> KeyUtils.parsePublicKeyWithAlgorithmDetection(invalidPem));
    }

    /* tryParseWithMultipleAlgorithms Tests */

    @Test
    void tryParseWithMultipleAlgorithms_withRsaKey_succeeds() {
        val keyPair = generateRsaKeyPair(2048);
        val keySpec = new X509EncodedKeySpec(keyPair.getPublic().getEncoded());

        val parsedKey = KeyUtils.tryParseWithMultipleAlgorithms(keySpec, ALGORITHM_RSA, ALGORITHM_EC);

        assertInstanceOf(RSAPublicKey.class, parsedKey);
    }

    @Test
    void tryParseWithMultipleAlgorithms_withEcKeyAsSecondAlgorithm_succeeds() {
        val keyPair = generateEcKeyPair(CURVE_SECP256R1);
        val keySpec = new X509EncodedKeySpec(keyPair.getPublic().getEncoded());

        val parsedKey = KeyUtils.tryParseWithMultipleAlgorithms(keySpec, ALGORITHM_RSA, ALGORITHM_EC);

        assertInstanceOf(ECPublicKey.class, parsedKey);
    }

    @Test
    void tryParseWithMultipleAlgorithms_withNoMatchingAlgorithm_throwsException() {
        val keyPair = generateRsaKeyPair(2048);
        val keySpec = new X509EncodedKeySpec(keyPair.getPublic().getEncoded());

        val exception = assertThrows(PolicyEvaluationException.class,
                () -> KeyUtils.tryParseWithMultipleAlgorithms(keySpec, ALGORITHM_EC, ALGORITHM_EDDSA));

        assertTrue(exception.getMessage().contains("Unsupported key type"));
        assertTrue(exception.getMessage().contains("EC, EdDSA"));
    }

    /* getKeySize Tests */

    @ParameterizedTest
    @ValueSource(ints = { 1024, 2048, 3072, 4096 })
    void getKeySize_withRsaKeys_returnsCorrectSize(int keySize) {
        val keyPair = generateRsaKeyPair(keySize);

        val actualSize = KeyUtils.getKeySize(keyPair.getPublic());

        assertEquals(keySize, actualSize);
    }

    @ParameterizedTest
    @MethodSource("provideEcCurvesWithBitLengths")
    void getKeySize_withEcKeys_returnsCorrectSize(String curveName, int expectedBits) {
        val keyPair = generateEcKeyPair(curveName);

        val actualSize = KeyUtils.getKeySize(keyPair.getPublic());

        assertEquals(expectedBits, actualSize);
    }

    @Test
    void getKeySize_withEd25519Key_returns256() {
        val keyPair = generateEd25519KeyPair();

        val actualSize = KeyUtils.getKeySize(keyPair.getPublic());

        assertEquals(EDDSA_KEY_SIZE_BITS, actualSize);
    }

    @Test
    void getKeySize_withUnknownKeyType_returnsZero() {
        val unknownKey = new PublicKey() {
            @Override
            public String getAlgorithm() {
                return "UNKNOWN";
            }

            @Override
            public String getFormat() {
                return "UNKNOWN";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }
        };

        val actualSize = KeyUtils.getKeySize(unknownKey);

        assertEquals(0, actualSize);
    }

    /* extractEcCurveName Tests */

    @ParameterizedTest
    @MethodSource("provideEcCurvesForNameExtraction")
    void extractEcCurveName_withStandardCurves_returnsStandardizedName(String curveName, String expectedName) {
        val keyPair = generateEcKeyPairWithDefaultProvider(curveName);
        val ecKey   = (ECPublicKey) keyPair.getPublic();

        val extractedName = KeyUtils.extractEcCurveName(ecKey);

        assertEquals(expectedName, extractedName);
    }

    /* getJwkCurveName Tests */

    @ParameterizedTest
    @MethodSource("provideEcCurvesForJwkNames")
    void getJwkCurveName_withStandardCurves_returnsJwkName(String curveName, String expectedJwkName) {
        val keyPair = generateEcKeyPairWithDefaultProvider(curveName);
        val ecKey   = (ECPublicKey) keyPair.getPublic();

        val jwkName = KeyUtils.getJwkCurveName(ecKey);

        assertEquals(expectedJwkName, jwkName);
    }

    /* Test Data Providers */

    private static Stream<Arguments> provideKeysForParsing() {
        return Stream.of(Arguments.of(ALGORITHM_RSA, generateRsaKeyPair(2048).getPublic()),
                Arguments.of(ALGORITHM_EC, generateEcKeyPair(CURVE_SECP256R1).getPublic()),
                Arguments.of(ALGORITHM_EDDSA, generateEd25519KeyPair().getPublic()));
    }

    private static Stream<Arguments> provideKeysForAlgorithmDetection() {
        return Stream.of(Arguments.of(generateRsaKeyPair(2048).getPublic()),
                Arguments.of(generateEcKeyPair(CURVE_SECP256R1).getPublic()),
                Arguments.of(generateEcKeyPair(CURVE_SECP384R1).getPublic()),
                Arguments.of(generateEd25519KeyPair().getPublic()));
    }

    private static Stream<Arguments> provideEcCurvesWithBitLengths() {
        return Stream.of(Arguments.of(CURVE_SECP256R1, EC_P256_BITS), Arguments.of(CURVE_PRIME256V1, EC_P256_BITS),
                Arguments.of(CURVE_SECP384R1, EC_P384_BITS), Arguments.of(CURVE_SECP521R1, EC_P521_BITS));
    }

    private static Stream<Arguments> provideEcCurvesForNameExtraction() {
        return Stream.of(Arguments.of(CURVE_SECP256R1, CURVE_SECP256R1), Arguments.of(CURVE_SECP384R1, CURVE_SECP384R1),
                Arguments.of(CURVE_SECP521R1, CURVE_SECP521R1));
    }

    private static Stream<Arguments> provideEcCurvesForJwkNames() {
        return Stream.of(Arguments.of(CURVE_SECP256R1, CURVE_P256_JWK), Arguments.of(CURVE_SECP384R1, CURVE_P384_JWK),
                Arguments.of(CURVE_SECP521R1, CURVE_P521_JWK));
    }

    /* Test Helper Methods */

    @SneakyThrows
    private static KeyPair generateRsaKeyPair(int keySize) {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_RSA);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

    @SneakyThrows
    private static KeyPair generateEcKeyPair(String curveName) {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EC, "BC");
        val ecSpec           = new ECGenParameterSpec(curveName);
        keyPairGenerator.initialize(ecSpec);
        return keyPairGenerator.generateKeyPair();
    }

    @SneakyThrows
    private static KeyPair generateEcKeyPairWithDefaultProvider(String curveName) {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EC);
        val ecSpec           = new ECGenParameterSpec(curveName);
        keyPairGenerator.initialize(ecSpec);
        return keyPairGenerator.generateKeyPair();
    }

    @SneakyThrows
    private static KeyPair generateEd25519KeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_EDDSA);
        return keyPairGenerator.generateKeyPair();
    }

    private static String generatePemFromPublicKey(PublicKey publicKey) {
        return PemUtils.encodePublicKeyPem(publicKey.getEncoded());
    }
}
