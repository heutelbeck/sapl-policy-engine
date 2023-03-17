package io.sapl.extension.jwt;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
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
