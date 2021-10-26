package io.sapl.extension.jwt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;

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
	static String base64Basic(KeyPair keyPair) {
		return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()).toString();
	}

	/**
	 * @return invalid Base64 encoding of public Key
	 */
	static String base64Invalid(KeyPair keyPair) {
		String encoded = base64Url(keyPair);
		String ch = encoded.substring(0, 1);
		return encoded.replaceAll(ch, "#");
	}

	/**
	 * @return Base64 url-safe encoding of bogus key
	 */
	static String base64Bogus() {
		return Base64.getUrlEncoder().encodeToString("ThisIsABogusPublicKey".getBytes()).toString();
	}

}