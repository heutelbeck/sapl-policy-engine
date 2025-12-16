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
package io.sapl.attributes.libraries.util;

import io.sapl.api.SaplVersion;
import lombok.experimental.UtilityClass;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@UtilityClass
public class KeyTestUtility {

    static final String MD5 = "MD5";
    static final String RSA = "RSA";

    /**
     * @return an invalid RSA public key
     */
    public static RSAPublicKey generateInvalidRSAPublicKey() {
        return new InvalidRSAPublicKey();
    }

    /**
     * @param keyPair
     * the key pair
     *
     * @return a mock web server used for testing public key requests
     *
     * @throws NoSuchAlgorithmException
     * in case the specified algorithm is unavailable
     * @throws IOException
     * on IO errors
     */
    public static MockWebServer testServer(KeyPair keyPair) throws NoSuchAlgorithmException, IOException {
        val mockServerKeys = Map.of(KeyTestUtility.kid(keyPair),
                Base64DataUtil.encodePublicKeyToBase64URLPrimary(keyPair));
        val server         = new MockWebServer();
        server.setDispatcher(new TestMockServerDispatcher("/public-keys/", mockServerKeys));
        return server;
    }

    /**
     * @param keyPairs
     * key pairs
     *
     * @return a mock web server used for testing public key requests
     *
     * @throws IllegalStateException
     * upon server creation failure.
     */
    public static MockWebServer testServer(Set<KeyPair> keyPairs) {
        val mockServerKeys = new HashMap<String, String>();
        keyPairs.forEach(keyPair -> {
            try {
                mockServerKeys.put(KeyTestUtility.kid(keyPair),
                        Base64DataUtil.encodePublicKeyToBase64URLPrimary(keyPair));
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new IllegalStateException("Failed create mock server", e);
            }
        });
        val server = new MockWebServer();
        server.setDispatcher(new TestMockServerDispatcher("/public-keys/", mockServerKeys));
        return server;
    }

    /**
     * @param keyPair
     * a key pair
     *
     * @return the public key's hash code
     *
     * @throws NoSuchAlgorithmException
     * in case the specified algorithm is unavailable
     * @throws IOException
     * in IO errors
     */
    public static String kid(KeyPair keyPair) throws NoSuchAlgorithmException, IOException {
        val outputStream = new ByteArrayOutputStream();
        outputStream.write(keyPair.getPublic().getEncoded());
        outputStream.write(keyPair.getPrivate().getEncoded());
        return Base64.getUrlEncoder().encodeToString(MessageDigest.getInstance(MD5).digest(outputStream.toByteArray()))
                .replaceAll("=", "");
    }

    /**
     * @param keyPair
     * a key pair
     *
     * @return a predicate that evaluates to true iff it's input is of type
     * RSAPublicKey and matches the public key of
     * the supplied keyPair
     */
    static Predicate<Object> keyValidator(KeyPair keyPair) {
        return publicKey -> {
            if (!(publicKey instanceof RSAPublicKey pubKey))
                return false;

            return areKeysEqual(pubKey, keyPair);
        };
    }

    static boolean areKeysEqual(RSAPublicKey publicKey, KeyPair keyPair) {
        if (!RSA.equals(keyPair.getPublic().getAlgorithm()))
            return false;
        val other = (RSAPublicKey) keyPair.getPublic();
        return areKeysEqual(publicKey, other);
    }

    static boolean areKeysEqual(RSAPublicKey keyA, RSAPublicKey keyB) {
        return keyA.getModulus().equals(keyB.getModulus()) && keyA.getPublicExponent().equals(keyB.getPublicExponent());
    }

    private static class InvalidRSAPublicKey implements RSAPublicKey {

        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        @Override
        public String getAlgorithm() {
            return "NotRSA";
        }

        @Override
        public String getFormat() {
            // No encoding supported
            return null;
        }

        @Override
        public byte[] getEncoded() {
            // No encoding supported
            return null;
        }

        @Override
        public BigInteger getModulus() {
            return BigInteger.TEN;
        }

        @Override
        public BigInteger getPublicExponent() {
            return BigInteger.TEN;
        }

    }

}
