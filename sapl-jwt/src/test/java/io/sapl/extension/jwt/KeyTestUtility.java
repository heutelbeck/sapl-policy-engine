/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extension.jwt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

import lombok.experimental.UtilityClass;
import okhttp3.mockwebserver.MockWebServer;

@UtilityClass
class KeyTestUtility {

    static final String MD5 = "MD5";

    static final String RSA = "RSA";

    /**
     * @return an invalid RSA public key
     */
    static RSAPublicKey generateInvalidRSAPublicKey() {
        return new InvalidRSAPublicKey();
    }

    /**
     * @return a mock web server used for testing public key requests
     */
    static MockWebServer testServer(KeyPair keyPair) throws NoSuchAlgorithmException, IOException {
        Map<String, String> mockServerKeys = Map.of(KeyTestUtility.kid(keyPair),
                Base64DataUtil.encodePublicKeyToBase64URLPrimary(keyPair));
        MockWebServer       server         = new MockWebServer();
        server.setDispatcher(new TestMockServerDispatcher("/public-keys/", mockServerKeys));
        return server;
    }

    /**
     * @return a mock web server used for testing public key requests
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static MockWebServer testServer(Set<KeyPair> keyPairs) {
        Map<String, String> mockServerKeys = new HashMap<>();
        keyPairs.forEach(keyPair -> {
            try {
                mockServerKeys.put(KeyTestUtility.kid(keyPair),
                        Base64DataUtil.encodePublicKeyToBase64URLPrimary(keyPair));
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new IllegalStateException("Failed create mock server", e);
            }
        });
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new TestMockServerDispatcher("/public-keys/", mockServerKeys));
        return server;
    }

    /**
     * @return the public key's hash code
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    static String kid(KeyPair keyPair) throws NoSuchAlgorithmException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(keyPair.getPublic().getEncoded());
        outputStream.write(keyPair.getPrivate().getEncoded());
        return Base64.getUrlEncoder().encodeToString(MessageDigest.getInstance(MD5).digest(outputStream.toByteArray()))
                .replaceAll("=", "");
    }

    /**
     * @param keyPair
     * @return a predicate that evaluates to true iff it's input is of type
     *         RSAPublicKey and matches the public key of the supplied keyPair
     */
    static Predicate<Object> keyValidator(KeyPair keyPair) {
        return publicKey -> {
            if (!(publicKey instanceof RSAPublicKey))
                return false;

            RSAPublicKey pubKey = (RSAPublicKey) publicKey;
            return areKeysEqual(pubKey, keyPair);
        };
    }

    static boolean areKeysEqual(RSAPublicKey publicKey, KeyPair keyPair) {
        if (!RSA.equals(keyPair.getPublic().getAlgorithm()))
            return false;
        RSAPublicKey other = (RSAPublicKey) keyPair.getPublic();
        return areKeysEqual(publicKey, other);
    }

    static boolean areKeysEqual(RSAPublicKey keyA, RSAPublicKey keyB) {
        return keyA.getModulus().equals(keyB.getModulus()) && keyA.getPublicExponent().equals(keyB.getPublicExponent());
    }

    private static class InvalidRSAPublicKey implements RSAPublicKey {

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
