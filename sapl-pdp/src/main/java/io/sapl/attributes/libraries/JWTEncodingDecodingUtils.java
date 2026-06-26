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

import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.JsonNode;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

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
     * Extracts a trust-anchor key from a JSON text node. Only asymmetric (X509)
     * public keys are accepted. A trust anchor whose bytes fail X509 parsing is
     * rejected and never reinterpreted as a symmetric secret. Building an HMAC
     * key from a failed asymmetric anchor would let an attacker forge an HS256
     * token signed with the same (often public) bytes, so the conversion fails
     * closed instead.
     *
     * @param jsonNode the JSON node containing the key
     * @return the public key, or empty if it cannot be parsed as X509
     */
    public static Optional<Key> jsonNodeToKey(JsonNode jsonNode) {
        if (!jsonNode.isString()) {
            return Optional.empty();
        }
        return encodedX509ToPublicKey(jsonNode.asString());
    }

    /**
     * Decodes a Base64 encoded string into bytes.
     *
     * @param base64 encoded string
     * @return bytes
     */
    private static Optional<byte[]> decode(String base64) {
        base64 = base64.replace('+', '-').replace('/', '_').replace(',', '_');

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
