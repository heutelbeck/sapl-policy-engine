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
package io.sapl.attributes.libraries;

import tools.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

@UtilityClass
public class JWTEncodingDecodingUtils {

    private static final String[] ASYMMETRIC_ALGORITHMS = { "RSA", "EC", "EdDSA" };

    /**
     * Converts a Base64-encoded X509 key to a public key. Attempts RSA, EC, then
     * EdDSA.
     *
     * @param encodedKey the Base64-encoded key
     * @return the public key, or empty if conversion fails
     */
    static Optional<Key> encodedX509ToPublicKey(String encodedKey) {
        return decode(encodedKey).map(X509EncodedKeySpec::new).flatMap(JWTEncodingDecodingUtils::generatePublicKey);
    }

    /**
     * Decodes a Base64-encoded string into bytes and wraps them as an HMAC secret
     * key.
     *
     * @param encodedKey the Base64-encoded symmetric key
     * @return the secret key, or empty if decoding fails
     */
    static Optional<Key> encodedToSecretKey(String encodedKey) {
        return decode(encodedKey).map(bytes -> new SecretKeySpec(bytes, "HMAC"));
    }

    /**
     * Extracts a key from a JSON text node. Tries asymmetric (X509) first,
     * then falls back to symmetric (HMAC). This order is safe because X509
     * has well-defined ASN.1 structure that raw bytes never accidentally
     * match.
     *
     * @param jsonNode the JSON node containing the key
     * @return the key, or empty if extraction fails
     */
    public static Optional<Key> jsonNodeToKey(JsonNode jsonNode) {
        if (!jsonNode.isString())
            return Optional.empty();

        val encoded    = jsonNode.asString();
        val asymmetric = encodedX509ToPublicKey(encoded);
        if (asymmetric.isPresent())
            return asymmetric;
        return encodedToSecretKey(encoded);
    }

    /**
     * Decodes a Base64 encoded string into bytes.
     *
     * @param base64 encoded string
     * @return bytes
     */
    private static Optional<byte[]> decode(String base64) {
        val pattern = "\\+";
        base64 = base64.replace(pattern, "-").replace('/', '_').replace(',', '_');

        try {
            val bytes = Base64.getUrlDecoder().decode(base64);
            return Optional.of(bytes);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Generates a public key from an X509EncodedKeySpec. Tries RSA, EC, then EdDSA.
     *
     * @param x509Key an X509EncodedKeySpec object
     * @return the public key
     */
    private static Optional<Key> generatePublicKey(X509EncodedKeySpec x509Key) {
        for (val algorithm : ASYMMETRIC_ALGORITHMS) {
            try {
                val kf        = KeyFactory.getInstance(algorithm);
                val publicKey = (PublicKey) kf.generatePublic(x509Key);
                return Optional.of(publicKey);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | ClassCastException e) {
                // try next algorithm
            }
        }
        return Optional.empty();
    }

}
