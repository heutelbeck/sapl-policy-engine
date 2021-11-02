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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Map;

import okhttp3.mockwebserver.MockWebServer;

public class KeyTestUtility {

	/**
	 * @return an RSA key pair
	 */
	static KeyPair keyPair() {
		KeyPair keyPair = null;
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyPair = keyGen.genKeyPair();
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return keyPair;
	}
	
	/**
	 * @return a mock web server used for testing public key requests
	 */
	static MockWebServer testServer(String keyPath, KeyPair keyPair) {
		Map<String, String> mockServerKeys = Map.of(KeyTestUtility.kid(keyPair), KeyTestUtility.base64Url(keyPair));
		MockWebServer server = new MockWebServer();
		server.setDispatcher(new TestMockServerDispatcher(keyPath, mockServerKeys));
		return server;
	}

	/**
	 * @return the public key's hash code
	 */
	static String kid(KeyPair keyPair) {
		return String.valueOf(keyPair.hashCode());
	}

	/**
	 * @return the private key
	 */
	static PrivateKey privateKey(KeyPair keyPair) {
		return keyPair.getPrivate();
	}

	/**
	 * @return Base64 url-safe encoding of public key
	 */
	static String base64Url(KeyPair keyPair) {
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