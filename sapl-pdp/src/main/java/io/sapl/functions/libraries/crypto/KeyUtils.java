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
package io.sapl.functions.libraries.crypto;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_EC;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_EDDSA;
import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_RSA;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P256_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P384_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_P521_JWK;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_PRIME256V1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP256R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP384R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_SECP521R1;
import static io.sapl.functions.libraries.crypto.CryptoConstants.CURVE_UNKNOWN;
import static io.sapl.functions.libraries.crypto.CryptoConstants.EC_P256_BITS;
import static io.sapl.functions.libraries.crypto.CryptoConstants.EC_P384_BITS;
import static io.sapl.functions.libraries.crypto.CryptoConstants.EC_P521_BITS;
import static io.sapl.functions.libraries.crypto.CryptoConstants.EDDSA_KEY_SIZE_BITS;

/**
 * Utilities for working with cryptographic keys. Provides methods for parsing
 * public keys from PEM format and
 * extracting key voterMetadata.
 */
@UtilityClass
public class KeyUtils {

    private static final String ERROR_UNSUPPORTED_KEY_TYPE = "Unsupported key type or invalid key format. Tried algorithms: %s.";

    /**
     * Parses a PEM-encoded public key using the specified algorithm.
     *
     * @param pemKey
     * the PEM-encoded public key string
     * @param keyAlgorithm
     * the key algorithm (RSA, EC, or EdDSA)
     *
     * @return the parsed PublicKey
     *
     * @throws CryptoException
     * if parsing fails
     */
    public static PublicKey parsePublicKey(String pemKey, String keyAlgorithm)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        val keyBytes   = PemUtils.decodePublicKeyPem(pemKey);
        val keySpec    = new X509EncodedKeySpec(keyBytes);
        val keyFactory = KeyFactory.getInstance(keyAlgorithm);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Attempts to parse a public key by trying multiple algorithms in sequence.
     * Tries RSA, EC, and EdDSA algorithms
     * until one succeeds.
     *
     * @param pemKey
     * the PEM-encoded public key string
     *
     * @return the parsed PublicKey
     *
     * @throws CryptoException
     * if all algorithms fail
     */
    public static PublicKey parsePublicKeyWithAlgorithmDetection(String pemKey) {
        val keyBytes = PemUtils.decodePublicKeyPem(pemKey);
        val keySpec  = new X509EncodedKeySpec(keyBytes);

        return tryParseWithMultipleAlgorithms(keySpec, ALGORITHM_RSA, ALGORITHM_EC, ALGORITHM_EDDSA);
    }

    /**
     * Attempts to decode a public key using multiple algorithms.
     *
     * @param keySpec
     * the X509EncodedKeySpec to decode
     * @param algorithms
     * the algorithms to try in order
     *
     * @return the decoded PublicKey
     *
     * @throws CryptoException
     * if all algorithms fail
     */
    public static PublicKey tryParseWithMultipleAlgorithms(X509EncodedKeySpec keySpec, String... algorithms) {
        for (val algorithm : algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException ignored) {
                // Try next algorithm
            }
        }

        throw new CryptoException(ERROR_UNSUPPORTED_KEY_TYPE.formatted(String.join(", ", algorithms)));
    }

    /**
     * Gets the key size in bits for a public key.
     *
     * @param publicKey
     * the public key
     *
     * @return the key size in bits
     */
    public static int getKeySize(PublicKey publicKey) {
        return switch (publicKey) {
        case RSAPublicKey rsaKey   -> rsaKey.getModulus().bitLength();
        case ECPublicKey ecKey     -> ecKey.getParams().getOrder().bitLength();
        case EdECPublicKey edEcKey -> EDDSA_KEY_SIZE_BITS;
        default                    -> 0;
        };
    }

    /**
     * Extracts the standardized curve name from EC key parameters.
     *
     * @param ecKey
     * the EC public key
     *
     * @return the standardized curve name (secp256r1, secp384r1, secp521r1, or
     * "unknown")
     */
    public static String extractEcCurveName(ECPublicKey ecKey) {
        val parametersString = ecKey.getParams().toString();
        if (parametersString.contains(CURVE_SECP256R1) || parametersString.contains(CURVE_PRIME256V1)) {
            return CURVE_SECP256R1;
        }
        if (parametersString.contains(CURVE_SECP384R1)) {
            return CURVE_SECP384R1;
        }
        if (parametersString.contains(CURVE_SECP521R1)) {
            return CURVE_SECP521R1;
        }
        return CURVE_UNKNOWN;
    }

    /**
     * Gets the JWK curve name for an EC public key based on bit length.
     *
     * @param ecKey
     * the EC public key
     *
     * @return the JWK curve name (P-256, P-384, P-521, or "unknown")
     */
    public static String getJwkCurveName(ECPublicKey ecKey) {
        val bitLength = ecKey.getParams().getOrder().bitLength();
        return switch (bitLength) {
        case EC_P256_BITS -> CURVE_P256_JWK;
        case EC_P384_BITS -> CURVE_P384_JWK;
        case EC_P521_BITS -> CURVE_P521_JWK;
        default           -> CURVE_UNKNOWN;
        };
    }
}
