/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

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
		String ch = encodedPubKey.substring(encodedPubKey.length() / 2, encodedPubKey.length() / 2 + 1);
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
	static KeyPair generateRSAKeyPair() {
		KeyPair          keyPair;
		KeyPairGenerator keyGen  = KeyPairGenerator.getInstance("RSA");
		keyPair = keyGen.genKeyPair();
		return keyPair;
	}

}
