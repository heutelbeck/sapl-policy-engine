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

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Text;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Attributes obtained from Json Web Tokens (JWT)
 * <p>
 * Attributes depend on the JWT's validity, meaning they can change their state
 * over time according to the JWT's signature, maturity and expiration.
 * <p>
 * Public keys must be fetched from the trusted authentication server for
 * validating signatures. For this purpose, the url and http method for fetching
 * public keys need to be specified in the {@code pdp.json} configuration file
 * as in the following example:
 *
 * <pre>
 * {@code 
 * {"algorithm": "DENY_UNLESS_PERMIT",
 * 	"variables": {
 *				   "jwt": {
 *		                    "publicKeyServer": {
 *                                               "uri":    "http://authz-server:9000/public-key/{id}"
 *                                               "method": "POST"
 *                                             },
 *					        "whitelist" : {
 *								            "key id" : "public key"
 *					    		          },
 *					        "blacklist" : {
 *					                        "key id" : "public key"
 *					    		          }
 *	             }
 * }
 * }
 * </pre>
 */
@Slf4j
@PolicyInformationPoint(name = JWTPolicyInformationPoint.NAME, description = JWTPolicyInformationPoint.DESCRIPTION)
public class JWTPolicyInformationPoint {

	private static final String JWT_CONFIG_MISSING_ERROR = "The key 'jwt' with the configuration of public key server and key whillist, blacklist is missing. All JWT tokens will be treated as if the signatures could not be validated.";
	private static final String JWT_KEY = "jwt";
	static final String NAME = JWT_KEY;
	static final String DESCRIPTION = "Json Web Token Attributes. Attributes depend on the JWT's validity, meaning they can change their state over time according to the JWT's signature, maturity and expiration.";

	private static final String VALIDITY_DOCS = "The token's validity state";

	static final String PUBLICKEY_VARIABLES_KEY = "publicKeyServer";
	static final String PUBLICKEY_URI_KEY = "uri";
	static final String PUBLICKEY_METHOD_KEY = "method";

	/**
	 * Possible states of validity a JWT can have
	 */
	enum ValidityState {

		/**
		 * the JWT is valid
		 */
		VALID

		/**
		 * the JWT has expired
		 */
		, EXPIRED

		/**
		 * the JWT expires before it becomes valid, so it is never valid
		 */
		, NEVERVALID

		/**
		 * the JWT will become valid in future
		 */
		, IMMATURE

		/**
		 * the JWT's signature does not match
		 * <p>
		 * either the payload has been tampered with, the public key could not be
		 * obtained, or the public key does not match the signature
		 */
		, UNTRUSTED

		/**
		 * the JWT is incompatible
		 * <p>
		 * either an incompatible hashing algorithm has been used or required fields do
		 * not have the correct format
		 */
		, INCOMPATIBLE

		/**
		 * the JWT is missing required fields
		 */
		, INCOMPLETE

		/**
		 * the token is not a JWT
		 */
		,MALFORMED;

	}

	private final WebClient webClient;

	/**
	 * Constructor
	 * 
	 * @param mapper  object mapper for mapping objects to Json
	 * @param builder mutable builder for creating a web client
	 */
	public JWTPolicyInformationPoint(WebClient.Builder builder) {
		this.webClient = builder.build();
	}

	@Attribute
	public Flux<Val> valid(@Text Val rawToken, Map<String, JsonNode> variables) {
		return validityState(rawToken, variables).map(ValidityState.VALID::equals).map(Val::of);
	}

	/**
	 * A JWT's validity
	 * <p>
	 * The validity may change over time as it becomes mature and then expires.
	 * 
	 * @param value     object containing JWT
	 * @param variables configuration variables
	 * @return Flux representing the JWT's validity over time
	 */
	@Attribute(docs = VALIDITY_DOCS)
	public Flux<Val> validity(@Text Val rawToken, Map<String, JsonNode> variables) {
		return validityState(rawToken, variables).map(Object::toString).map(Val::of);
	}

	private Flux<ValidityState> validityState(@Text Val rawToken, Map<String, JsonNode> variables) {
		
		if (rawToken == null || !rawToken.isTextual())
			return Flux.just(ValidityState.MALFORMED);
			
		SignedJWT signedJwt;
		JWTClaimsSet claims;
		try {
			signedJwt = SignedJWT.parse(rawToken.getText());
			claims = signedJwt.getJWTClaimsSet();
		} catch (ParseException e) {
			return Flux.just(ValidityState.MALFORMED);
		}

		// ensure presence of all required claims
		if (!hasRequiredClaims(signedJwt, claims))
			return Flux.just(ValidityState.INCOMPLETE);

		// ensure all required claims are well formed
		if (!hasCompatibleClaims(signedJwt))
			return Flux.just(ValidityState.INCOMPATIBLE);

		return validateSignature(signedJwt, variables).flatMapMany(isValid -> {
			if (!isValid)
				return Flux.just(ValidityState.UNTRUSTED);

			return validateTime(claims);
		});
	}

	private Mono<Boolean> validateSignature(SignedJWT signedJwt, Map<String, JsonNode> variables) {

		var jwtConfig = variables.get(JWT_KEY);
		if (jwtConfig == null) {
			log.error(JWT_CONFIG_MISSING_ERROR);
			return Mono.just(Boolean.FALSE);
		}

		var keyId = signedJwt.getHeader().getKeyID();

		Mono<RSAPublicKey> publicKey = null;
		var whitelist = jwtConfig.get("whitelist");
		if (whitelist != null && whitelist.get(keyId) != null) {
			var key = jsonNodeToKey(whitelist.get(keyId));
			if (key.isPresent())
				publicKey = Mono.just(key.get());
		}

		if (publicKey == null) {
			var jPublicKeyServer = jwtConfig.get(PUBLICKEY_VARIABLES_KEY);

			if (jPublicKeyServer == null)
				return Mono.just(Boolean.FALSE);

			var jUri = jPublicKeyServer.get(PUBLICKEY_URI_KEY);
			if (jUri == null)
				return Mono.just(Boolean.FALSE);

			var sMethod = "GET";
			JsonNode jMethod = jPublicKeyServer.get(PUBLICKEY_METHOD_KEY);
			if (jMethod != null && jMethod.isTextual()) {
				sMethod = jMethod.textValue();
			}

			var sUri = jUri.textValue();

			publicKey = fetchPublicKey(signedJwt.getHeader().getKeyID(), sUri, sMethod);
		}

		return publicKey.map(signatureOfTokenIsValid(signedJwt)).switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	private Function<RSAPublicKey, Boolean> signatureOfTokenIsValid(SignedJWT signedJwt) {
		return publicKey -> {
			JWSVerifier verifier = new RSASSAVerifier(publicKey);
			try {
				signedJwt.verify(verifier);
			} catch (JOSEException | IllegalStateException | NullPointerException e) {
				// erroneous signatures or data are treated same as failed verifications
				return Boolean.FALSE;
			}
			return Boolean.TRUE;
		};
	}

	private Optional<RSAPublicKey> jsonNodeToKey(JsonNode jsonNode) {
		if (!jsonNode.isTextual())
			return Optional.empty();

		return stringToKey(jsonNode.textValue());
	}

	private Optional<RSAPublicKey> stringToKey(String encodedKey) {
		return decode(encodedKey).map(X509EncodedKeySpec::new).flatMap(this::generatePublicKey);
	}

	/**
	 * Verifies token validity based on time
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return Flux containing IMMATURE, VALID, and/or EXPIRED
	 */
	private Flux<ValidityState> validateTime(JWTClaimsSet claims) {

		// java.util.Date and jwt NumericDate values are based on EPOCH
		// (number of seconds since 1970-01-01T00:00:00Z UTC)
		// and are therefore safe to compare
		Date nbf = claims.getNotBeforeTime();
		Date exp = claims.getExpirationTime();
		Date now = new Date();

		// sanity check
		if (nbf != null && exp != null && nbf.getTime() > exp.getTime())
			return Flux.just(ValidityState.NEVERVALID);

		// verify expiration
		if (exp != null && exp.getTime() < now.getTime()) {
			return Flux.just(ValidityState.EXPIRED);
		}

		// verify maturity
		if (nbf != null && nbf.getTime() > now.getTime()) {

			if (exp == null) {
				// the token is not valid yet but will be in future
				return Flux.concat(Mono.just(ValidityState.IMMATURE),
						Mono.just(ValidityState.VALID).delayElement(Duration.ofMillis(nbf.getTime() - now.getTime())));
			} else {
				// the token is not valid yet but will be in future and then expire
				return Flux.concat(Mono.just(ValidityState.IMMATURE),
						Mono.just(ValidityState.VALID).delayElement(Duration.ofMillis(nbf.getTime() - now.getTime())),
						Mono.just(ValidityState.EXPIRED)
								.delayElement(Duration.ofMillis(exp.getTime() - nbf.getTime())));
			}
		}

		// at this point the token is definitely mature
		// (either nbf==null or nbf<=now)
		if (exp == null) {
			// the token is eternally valid (no expiration)
			return Flux.just(ValidityState.VALID);
		} else {
			// the token is valid now but will expire in future
			return Flux.concat(Mono.just(ValidityState.VALID),
					Mono.just(ValidityState.EXPIRED).delayElement(Duration.ofMillis(exp.getTime() - now.getTime())));
		}

	}

	/**
	 * checks if token contains all required claims
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return true if the token contains all required claims
	 */
	private boolean hasRequiredClaims(SignedJWT jwt, JWTClaimsSet claims) {

		// verify presence of key ID
		String kid = jwt.getHeader().getKeyID();
		if (kid == null || kid.isBlank())
			return false;

		// verify presence of issuer
		if (null == claims.getIssuer())
			return false;

		// verify presence of subject
		if (null == claims.getSubject())
			return false;

		// JWT contains all required claims
		return true;
	}

	/**
	 * checks if claims meet requirements
	 * 
	 * @param jwt JWT
	 * @return true all claims meet requirements
	 */
	private boolean hasCompatibleClaims(SignedJWT jwt) {

		JWSHeader header = jwt.getHeader();

		// verify correct algorithm
		if (!"RS256".equalsIgnoreCase(header.getAlgorithm().getName()))
			return false;

		// all claims are compatible with requirements
		return true;
	}

	/**
	 * Fetches public key from remote authentication server
	 * 
	 * @param kid                    ID of public key to fetch
	 * @param publicKeyURI           URI to request the public key
	 * @param publicKeyRequestMethod HTTP request method: GET or POST
	 * @return public key or empty
	 */
	private Mono<RSAPublicKey> fetchPublicKey(String kid, String publicKeyURI, String publicKeyRequestMethod) {
		ResponseSpec response;
		if ("post".equalsIgnoreCase(publicKeyRequestMethod)) {
			// POST request
			response = webClient.post().uri(publicKeyURI, kid).retrieve();
		} else {
			// default GET request
			response = webClient.get().uri(publicKeyURI, kid).retrieve();
		}

		return response.bodyToMono(String.class).map(this::stringToKey).filter(Optional::isPresent).map(Optional::get);
	}

	/**
	 * decodes a Base64 encoded string into bytes
	 * 
	 * @param base64 encoded string
	 * @return bytes
	 */
	private Optional<byte[]> decode(String base64) {

		// ensure base64url encoding
		base64 = base64.replaceAll("\\+", "-").replaceAll("/", "_").replaceAll(",", "_");

		try {
			byte[] bytes = Base64.getUrlDecoder().decode(base64);
			return Optional.of(bytes);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/**
	 * generates an RSAPublicKey from an X509EncodedKeySpec
	 * 
	 * @param x509Key an X509EncodedKeySpec object
	 * @return the RSAPublicKey object
	 */
	private Optional<RSAPublicKey> generatePublicKey(X509EncodedKeySpec x509Key) {
		try {
			KeyFactory kf = KeyFactory.getInstance("RSA");
			RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(x509Key);
			return Optional.of(publicKey);
		} catch (NullPointerException | NoSuchAlgorithmException | InvalidKeySpecException e) {
			return Optional.empty();
		}
	}

}
