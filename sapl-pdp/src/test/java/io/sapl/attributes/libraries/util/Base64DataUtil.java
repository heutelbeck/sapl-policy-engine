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
package io.sapl.attributes.libraries.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

@UtilityClass
public class Base64DataUtil {
    /**
     * @return Base64 URL safe encoding of public key
     */
    static String encodePublicKeyToBase64URLPrimary(KeyPair keyPair) {
        return Base64.getUrlEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * @return Base64 basic encoding of public key
     */
    static String base64Basic(String encodedPubKey) {
        return Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(encodedPubKey));
    }

    /**
     * @return invalid Base64 encoding of public Key
     */
    static String base64Invalid(String encodedPubKey) {
        val ch = encodedPubKey.substring(encodedPubKey.length() / 2, encodedPubKey.length() / 2 + 1);
        return encodedPubKey.replaceAll(ch, "#");
    }

    /**
     * @return Base64 url-safe encoding of bogus key
     */
    static String base64Bogus() {
        return Base64.getUrlEncoder().encodeToString("ThisIsAVeryBogusPublicKey".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return an RSA key pair
     */
    @SneakyThrows
    public static KeyPair generateRSAKeyPair() {
        val keyGen = KeyPairGenerator.getInstance("RSA");
        return keyGen.genKeyPair();
    }

    /**
     * @return an EC key pair using P-256 curve
     */
    @SneakyThrows
    public static KeyPair generateECKeyPair() {
        val keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        return keyGen.generateKeyPair();
    }

    /**
     * @param keyLengthBytes the key length in bytes (32 for HS256, 48 for HS384, 64
     * for HS512)
     * @return a random HMAC secret key
     */
    @SneakyThrows
    public static SecretKey generateHmacKey(int keyLengthBytes) {
        val keyGen = KeyGenerator.getInstance("HmacSHA256");
        keyGen.init(keyLengthBytes * 8);
        return keyGen.generateKey();
    }

}
