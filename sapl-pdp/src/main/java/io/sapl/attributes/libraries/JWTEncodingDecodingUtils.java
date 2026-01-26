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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

@UtilityClass
public class JWTEncodingDecodingUtils {

    /**
     * Converts a Base64-encoded X509 key to an RSA public key.
     *
     * @param encodedKey the Base64-encoded key
     * @return the RSA public key, or empty if conversion fails
     */
    static Optional<RSAPublicKey> encodedX509ToRSAPublicKey(String encodedKey) {
        return decode(encodedKey).map(X509EncodedKeySpec::new).flatMap(JWTEncodingDecodingUtils::generatePublicKey);
    }

    /**
     * Extracts an RSA public key from a JSON text node.
     *
     * @param jsonNode the JSON node containing the key
     * @return the RSA public key, or empty if extraction fails
     */
    public static Optional<RSAPublicKey> jsonNodeToKey(JsonNode jsonNode) {
        if (!jsonNode.isTextual())
            return Optional.empty();

        return encodedX509ToRSAPublicKey(jsonNode.textValue());
    }

    /**
     * decodes a Base64 encoded string into bytes
     *
     * @param base64
     * encoded string
     *
     * @return bytes
     */
    private static Optional<byte[]> decode(String base64) {

        // ensure base64url encoding
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
     * generates an RSAPublicKey from an X509EncodedKeySpec
     *
     * @param x509Key
     * an X509EncodedKeySpec object
     *
     * @return the RSAPublicKey object
     */
    private static Optional<RSAPublicKey> generatePublicKey(X509EncodedKeySpec x509Key) {
        try {
            val kf        = KeyFactory.getInstance("RSA");
            val publicKey = (RSAPublicKey) kf.generatePublic(x509Key);
            return Optional.of(publicKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return Optional.empty();
        }
    }

}
