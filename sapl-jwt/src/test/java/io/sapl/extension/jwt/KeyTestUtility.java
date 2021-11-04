/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import okhttp3.mockwebserver.MockWebServer;

public class KeyTestUtility {

	private static final String MD5 = "MD5";

	private static final String RSA = "RSA";

	/**
	 * @return an RSA key pair
	 */
	static KeyPair generateRSAKeyPair() {
		KeyPair keyPair = null;
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA);
			keyPair = keyGen.genKeyPair();
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return keyPair;
	}

	/**
	 * @return a mock web server used for testing public key requests
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	static MockWebServer testServer(String keyPath, KeyPair keyPair) throws NoSuchAlgorithmException, IOException {
		Map<String, String> mockServerKeys = Map.of(KeyTestUtility.kid(keyPair),
				KeyTestUtility.encodePublicKeyToBase64URLPrimary(keyPair));
		MockWebServer server = new MockWebServer();
		server.setDispatcher(new TestMockServerDispatcher(keyPath, mockServerKeys));
		return server;
	}

	/**
	 * @return a mock web server used for testing public key requests
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	static MockWebServer testServer(String keyPath, Set<KeyPair> keyPairs) {
		Map<String, String> mockServerKeys = new HashMap<String, String>();
		keyPairs.forEach(keyPair -> {
			try {
				mockServerKeys.put(KeyTestUtility.kid(keyPair),
						KeyTestUtility.encodePublicKeyToBase64URLPrimary(keyPair));
			}
			catch (NoSuchAlgorithmException | IOException e) {
				e.printStackTrace();
			}
		});
		MockWebServer server = new MockWebServer();
		server.setDispatcher(new TestMockServerDispatcher(keyPath, mockServerKeys));
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
	 * @return a predicate that evaluates to true iff it's input is of type RSAPublicKey
	 * and matches the public key of the supplied keyPair
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
		if (!keyPair.getPublic().getAlgorithm().equals(RSA))
			return false;
		RSAPublicKey other = (RSAPublicKey) keyPair.getPublic();
		return areKeysEqual(publicKey, other);
	}

	static boolean areKeysEqual(RSAPublicKey keyA, RSAPublicKey keyB) {
		return keyA.getModulus().equals(keyB.getModulus()) && keyA.getPublicExponent().equals(keyB.getPublicExponent());
	}

	/**
	 * @return Base64 url-safe encoding of public key
	 */
	static String encodePublicKeyToBase64URLPrimary(KeyPair keyPair) {
		return Base64.getUrlEncoder().encodeToString(keyPair.getPublic().getEncoded()).toString();
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
		String ch = encodedPubKey.substring(encodedPubKey.length() / 2, encodedPubKey.length() / 2 + 1);
		return encodedPubKey.replaceAll(ch, "#");
	}

	/**
	 * @return Base64 url-safe encoding of bogus key
	 */
	static String base64Bogus() {
		return Base64.getUrlEncoder().encodeToString("ThisIsAVeryBogusPublicKey".getBytes()).toString();
	}

}