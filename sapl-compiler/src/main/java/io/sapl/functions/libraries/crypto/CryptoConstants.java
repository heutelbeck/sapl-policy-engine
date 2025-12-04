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
package io.sapl.functions.libraries.crypto;

import lombok.experimental.UtilityClass;

/**
 * Constants used across cryptographic utility classes for consistent PEM
 * handling, algorithm naming, and error
 * messaging.
 */
@UtilityClass
public class CryptoConstants {

    /* PEM Markers */
    public static final String PEM_PUBLIC_KEY_BEGIN  = "-----BEGIN PUBLIC KEY-----";
    public static final String PEM_PUBLIC_KEY_END    = "-----END PUBLIC KEY-----";
    public static final String PEM_CERTIFICATE_BEGIN = "-----BEGIN CERTIFICATE-----";
    public static final String PEM_CERTIFICATE_END   = "-----END CERTIFICATE-----";
    public static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_PRIVATE_KEY_END   = "-----END PRIVATE KEY-----";

    /* Key Algorithms */
    public static final String ALGORITHM_RSA   = "RSA";
    public static final String ALGORITHM_EC    = "EC";
    public static final String ALGORITHM_EDDSA = "EdDSA";

    /* Signature Algorithms */
    public static final String ALGORITHM_RSA_SHA256   = "SHA256withRSA";
    public static final String ALGORITHM_RSA_SHA384   = "SHA384withRSA";
    public static final String ALGORITHM_RSA_SHA512   = "SHA512withRSA";
    public static final String ALGORITHM_ECDSA_SHA256 = "SHA256withECDSA";
    public static final String ALGORITHM_ECDSA_SHA384 = "SHA384withECDSA";
    public static final String ALGORITHM_ECDSA_SHA512 = "SHA512withECDSA";
    public static final String ALGORITHM_ED25519      = "Ed25519";

    /* JWK Key Types */
    public static final String JWK_KEY_TYPE_RSA = "RSA";
    public static final String JWK_KEY_TYPE_EC  = "EC";
    public static final String JWK_KEY_TYPE_OKP = "OKP";

    /* Elliptic Curves */
    public static final String CURVE_SECP256R1  = "secp256r1";
    public static final String CURVE_PRIME256V1 = "prime256v1";
    public static final String CURVE_SECP384R1  = "secp384r1";
    public static final String CURVE_SECP521R1  = "secp521r1";
    public static final String CURVE_ED25519    = "Ed25519";
    public static final String CURVE_P256_JWK   = "P-256";
    public static final String CURVE_P384_JWK   = "P-384";
    public static final String CURVE_P521_JWK   = "P-521";
    public static final String CURVE_UNKNOWN    = "unknown";

    /* Key Sizes */
    public static final int EDDSA_KEY_SIZE_BITS = 256;
    public static final int EC_P256_BITS        = 256;
    public static final int EC_P384_BITS        = 384;
    public static final int EC_P521_BITS        = 521;

    /* Certificate Factory */
    public static final String CERTIFICATE_TYPE_X509 = "X.509";

    public static final String ERROR_ALGORITHM_NOT_AVAILABLE = "Algorithm not available";
    public static final String ERROR_INVALID_BASE64          = "Invalid Base64 encoding";
    public static final String ERROR_INVALID_CERTIFICATE     = "Invalid certificate format";
    public static final String ERROR_INVALID_KEY_FORMAT      = "Invalid key format";
    public static final String ERROR_UNSUPPORTED_KEY_TYPE    = "Unsupported key type";
}
