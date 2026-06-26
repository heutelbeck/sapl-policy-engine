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

import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static io.sapl.functions.libraries.crypto.CryptoConstants.*;

/**
 * Utilities for working with cryptographic keys. Provides methods for parsing
 * public keys from PEM format and
 * extracting key voterMetadata.
 */
@UtilityClass
public class KeyUtils {

    private static final String ERROR_REQUIRED_CURVE_UNAVAILABLE = "Required EC curve %s could not be initialised from the crypto provider.";
    private static final String ERROR_UNSUPPORTED_KEY_TYPE       = "Unsupported key type or invalid key format. Tried algorithms: %s.";

    private static final String CURVE_ED448         = "Ed448";
    private static final int    ED448_KEY_SIZE_BITS = 448;

    /**
     * Structured parameters of the supported NIST curves, keyed by their
     * standard name. Identifying a curve by comparing these full parameters is
     * provider-independent, unlike comparing the order bit length (secp256k1
     * shares P-256's 256-bit order) or substring-matching a provider-specific
     * {@code toString()}.
     */
    private static final String[] NIST_CURVE_NAMES = { CURVE_SECP256R1, CURVE_SECP384R1, CURVE_SECP521R1 };

    private static final List<NistCurve> NIST_CURVES = referenceNistCurves();

    private record NistCurve(String name, ECParameterSpec parameters) {}

    private static List<NistCurve> referenceNistCurves() {
        val curves = new ArrayList<NistCurve>();
        for (val standardName : NIST_CURVE_NAMES) {
            curves.add(new NistCurve(standardName, referenceCurve(standardName)));
        }
        return curves;
    }

    private static ECParameterSpec referenceCurve(String standardName) {
        try {
            val parameters = AlgorithmParameters.getInstance(ALGORITHM_EC);
            parameters.init(new ECGenParameterSpec(standardName));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            // A required curve is missing, so fail fast at class load on a broken crypto
            // environment.
            throw new IllegalStateException(ERROR_REQUIRED_CURVE_UNAVAILABLE.formatted(standardName), e);
        }
    }

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
        case EdECPublicKey edEcKey -> edEdsaKeySize(edEcKey);
        default                    -> 0;
        };
    }

    /**
     * Determines the security strength in bits of an EdDSA public key from its
     * named parameter set. Ed448 keys carry a larger parameter set than Ed25519
     * keys and must not be reported with the Ed25519 size.
     *
     * @param edEcKey
     * the EdDSA public key
     *
     * @return the key size in bits (256 for Ed25519, 448 for Ed448)
     */
    private static int edEdsaKeySize(EdECPublicKey edEcKey) {
        return CURVE_ED448.equals(edEcKey.getParams().getName()) ? ED448_KEY_SIZE_BITS : EDDSA_KEY_SIZE_BITS;
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
        val standardName = matchNistCurveName(ecKey);
        return standardName == null ? CURVE_UNKNOWN : standardName;
    }

    /**
     * Gets the JWK curve name for an EC public key.
     *
     * @param ecKey
     * the EC public key
     *
     * @return the JWK curve name (P-256, P-384, P-521, or "unknown")
     */
    public static String getJwkCurveName(ECPublicKey ecKey) {
        val standardName = matchNistCurveName(ecKey);
        if (standardName == null) {
            return CURVE_UNKNOWN;
        }
        return switch (standardName) {
        case CURVE_SECP256R1 -> CURVE_P256_JWK;
        case CURVE_SECP384R1 -> CURVE_P384_JWK;
        case CURVE_SECP521R1 -> CURVE_P521_JWK;
        default              -> CURVE_UNKNOWN;
        };
    }

    /**
     * Identifies a key's curve by comparing its full structured parameters
     * against the known NIST curves. Returns the matching standard name, or
     * {@code null} for any curve that is not a known NIST curve.
     */
    private static String matchNistCurveName(ECPublicKey ecKey) {
        val keySpec = ecKey.getParams();
        for (val curve : NIST_CURVES) {
            if (sameCurve(keySpec, curve.parameters())) {
                return curve.name();
            }
        }
        return null;
    }

    private static boolean sameCurve(ECParameterSpec a, ECParameterSpec b) {
        return a.getOrder().equals(b.getOrder()) && a.getCofactor() == b.getCofactor()
                && a.getCurve().equals(b.getCurve()) && a.getGenerator().equals(b.getGenerator());
    }
}
