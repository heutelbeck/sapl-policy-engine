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
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
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
 * {@code {"algorithm": "DENY_UNLESS_PERMIT",
 *	"variables": {
 *		"publicKeyServer": {
 *			  "uri": "http://localhost:8090/public-key/{id}"
 *			, "method": "POST"
 *		}
 *	}
 * }
 * }
 * </pre>
 */
@Slf4j
@PolicyInformationPoint(name = JWTPolicyInformationPoint.NAME, description = JWTPolicyInformationPoint.DESCRIPTION)
public class JWTPolicyInformationPoint {

	static final String NAME = "jwt";
	static final String DESCRIPTION = "Json Web Token Attributes. Attributes depend on the JWT's validity, meaning they can change their state over time according to the JWT's signature, maturity and expiration.";

	private static final String NO_PUBLICKEY_URI_WARNING = "URI to fetch public key is unknown. Specify {\"{}\":{\"{}\":\"https://path/to/public/keys/{id}\", \"{}\":\"GET|POST\"}} under variables in pdp.json configuration file to enable JWT signature validation";

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

	private final JWTLibraryService jwtService;

	private final WebClient webClient;

	/**
	 * Constructor
	 * 
	 * @param jwtService provider of services for processing Json Web Tokens
	 * @param mapper     object mapper for mapping objects to Json
	 * @param builder    mutable builder for creating a web client
	 */
	public JWTPolicyInformationPoint(JWTLibraryService jwtService, WebClient.Builder builder) {
		this.jwtService = jwtService;
		this.webClient = builder.build();
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
	public Flux<Val> validity(Val value, Map<String, JsonNode> variables) {

		Val jwtCandidate = jwtService.resolveToken(value);
		if (jwtCandidate.isUndefined())
			return Flux.just(Val.of(ValidityState.MALFORMED.toString()));
		final String jwt = jwtCandidate.getText();

		// ensure this is a well formed jwt
		if (!isProperJwt(jwt))
			return Flux.just(Val.of(ValidityState.MALFORMED.toString()));

		// ensure presence of all required claims
		if (!hasRequiredClaims(jwt))
			return Flux.just(Val.of(ValidityState.INCOMPLETE.toString()));

		// ensure all required claims are well formed
		if (!hasCompatibleClaims(jwt))
			return Flux.just(Val.of(ValidityState.INCOMPATIBLE.toString()));

		// if uri to fetch public key is known, validate signature and time
		if (variables != null) {
			JsonNode jPublicKeyServer = variables.get(PUBLICKEY_VARIABLES_KEY);
			if (jPublicKeyServer != null) {
				JsonNode jUri = jPublicKeyServer.get(PUBLICKEY_URI_KEY);
				if (jUri != null) {
					String sUri = jUri.textValue();
					String sMethod = "GET";
					JsonNode jMethod = jPublicKeyServer.get(PUBLICKEY_METHOD_KEY);
					if (jMethod != null && jMethod.isTextual()) {
						sMethod = jMethod.textValue();
					}
					return fetchPublicKey(keyID(jwt), sUri, sMethod).flatMapMany(validateSignatureAndTime(jwt))
							.switchIfEmpty(Flux.just(Val.of(ValidityState.UNTRUSTED.toString())));
				}
			}
		}

		// if uri to fetch public key is unknown, only validate time
		log.warn(NO_PUBLICKEY_URI_WARNING, PUBLICKEY_VARIABLES_KEY, PUBLICKEY_URI_KEY, PUBLICKEY_METHOD_KEY);
		return validateTime(jwt);
	}

	/**
	 * helper function to allow mapping of public key to signature validation
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return Function to validate signature
	 */
	private Function<RSAPublicKey, Flux<Val>> validateSignatureAndTime(String jwt) {
		return publicKey -> doValidateSignatureAndTime(jwt, publicKey);
	}

	/**
	 * verifies a jwt's content to match its signature decrypted with the public key
	 * 
	 * @param jwt       base64 encoded header.body.signature triplet
	 * @param publicKey key to decrypt the signature
	 * @return Flux containing IMMATURE, VALID, and/or EXPIRED if signature is
	 *         valid, UNTRUSTED otherwise
	 */
	private Flux<Val> doValidateSignatureAndTime(String jwt, RSAPublicKey publicKey) {

		SignedJWT signedJwt = jwtService.signedJwt(jwt);

		// verify signature
		boolean isVerified = false;
		JWSVerifier verifier = new RSASSAVerifier(publicKey);
		try {
			isVerified = signedJwt.verify(verifier);
		} catch (JOSEException | IllegalStateException | NullPointerException e) {
			// erroneous signatures or data are treated same as failed verifications
		}

		if (!isVerified) {
			return Flux.just(Val.of(ValidityState.UNTRUSTED.toString()));
		}

		// continue validating time if signature is valid
		return validateTime(jwt);
	}

	/**
	 * Verifies token validity based on time
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return Flux containing IMMATURE, VALID, and/or EXPIRED
	 */
	private Flux<Val> validateTime(String jwt) {

		JWTClaimsSet claims = jwtService.claims(jwt);

		// java.util.Date and jwt NumericDate values are based on EPOCH
		// (number of seconds since 1970-01-01T00:00:00Z UTC)
		// and are therefore safe to compare
		Date nbf = claims.getNotBeforeTime();
		Date exp = claims.getExpirationTime();
		Date now = new Date();

		// sanity check
		if (nbf != null && exp != null && nbf.getTime() > exp.getTime())
			return Flux.just(Val.of(ValidityState.NEVERVALID.toString()));

		// verify expiration
		if (exp != null && exp.getTime() < now.getTime()) {
			return Flux.just(Val.of(ValidityState.EXPIRED.toString()));
		}

		// verify maturity
		if (nbf != null && nbf.getTime() > now.getTime()) {

			if (exp == null) {
				// the token is not valid yet but will be in future
				return Flux.concat(Mono.just(Val.of(ValidityState.IMMATURE.toString())),
						Mono.just(Val.of(ValidityState.VALID.toString()))
								.delayElement(Duration.ofMillis(nbf.getTime() - now.getTime())));
			} else {
				// the token is not valid yet but will be in future and then expire
				return Flux.concat(Mono.just(Val.of(ValidityState.IMMATURE.toString())),
						Mono.just(Val.of(ValidityState.VALID.toString()))
								.delayElement(Duration.ofMillis(nbf.getTime() - now.getTime())),
						Mono.just(Val.of(ValidityState.EXPIRED.toString()))
								.delayElement(Duration.ofMillis(exp.getTime() - nbf.getTime())));
			}
		}

		// at this point the token is definitely mature
		// (either nbf==null or nbf<=now)
		if (exp == null) {
			// the token is eternally valid (no expiration)
			return Flux.just(Val.of(ValidityState.VALID.toString()));
		} else {
			// the token is valid now but will expire in future
			return Flux.concat(Mono.just(Val.of(ValidityState.VALID.toString())),
					Mono.just(Val.of(ValidityState.EXPIRED.toString()))
							.delayElement(Duration.ofMillis(exp.getTime() - now.getTime())));
		}

	}

	/**
	 * Verifies token to be parseable and well formed JWT
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return true if the token is a proper JWT
	 */
	private boolean isProperJwt(String jwt) {
		JWSHeader header = jwtService.header(jwt);

		// verify token type
		JOSEObjectType tokenType = header.getType();
		if (tokenType == null)
			return false;

		if (!"JWT".equalsIgnoreCase(tokenType.getType()))
			return false;

		// jwt is well formed
		return true;
	}

	/**
	 * checks if token contains all required claims
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return true if the token contains all required claims
	 */
	private boolean hasRequiredClaims(String jwt) {

		JWSHeader header = jwtService.header(jwt);

		// no need to verify presence of algorithm,
		// without alg in header jwt could not have been resolved

		// verify presence of key ID
		String kid = header.getKeyID();
		if (kid == null || kid.length() == 0)
			return false;

		JWTClaimsSet claims = jwtService.claims(jwt);

		// verify presence of token identifier
		String jwtId = claims.getJWTID();
		if (jwtId == null || jwtId.length() == 0)
			return false;

		// verify presence of issuer
		if (null == claims.getIssuer())
			return false;

		// verify presence of subject
		if (null == claims.getSubject())
			return false;

		// verify presence of authorities
		if (null == claims.getClaim(JWTLibraryService.AUTHORITIES_KEY))
			return false;

		// jwt contains all required claims
		return true;
	}

	/**
	 * checks if claims meet requirements
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return true all claims meet requirements
	 */
	private boolean hasCompatibleClaims(String jwt) {

		JWSHeader header = jwtService.header(jwt);

		// verify correct algorithm
		if (!"RS256".equalsIgnoreCase(header.getAlgorithm().getName()))
			return false;

		JWTClaimsSet claims = jwtService.claims(jwt);

		// verify type of authorities
		try {
			claims.getStringListClaim(JWTLibraryService.AUTHORITIES_KEY);
		} catch (ParseException e) {
			return false;
		}

		// all claims are compatible with requirements
		return true;
	}

	/**
	 * Extracts key ID from jwt's header
	 * 
	 * @param jwt base64 encoded header.body.signature triplet
	 * @return key ID
	 */
	private String keyID(String jwt) {
		return jwtService.header(jwt).getKeyID();
	}

	/**
	 * Fetches public key from remote authentication server
	 * 
	 * @param kid                    ID of public key to fetch
	 * @param publicKeyURI           uri to request the public key
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

		return response.bodyToMono(String.class) // base64
				.onErrorResume(e -> Mono.empty()) // any error is treated as no response
				.map(this::decode) // byte[]
				.filter(Optional::isPresent).map(Optional::get).map(X509EncodedKeySpec::new) // X509EncodedKeySpec
				.map(this::generatePublicKey) // PublicKey
				.filter(Optional::isPresent).map(Optional::get); // value or empty
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
