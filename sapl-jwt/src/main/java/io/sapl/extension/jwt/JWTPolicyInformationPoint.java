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

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

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
import io.sapl.extension.jwt.JWTKeyProvider.CachingException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Attributes obtained from JSON Web Tokens (JWT)
 * <p>
 * Attributes depend on the JWT's validity, meaning they can change their state
 * over time according to the JWT's signature, maturity, and expiration.
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
 *                                               "uri":    "http://authz-server:9000/public-key/{id}",
 *                                               "method": "POST",
 *                                               "keyCachingTtlMillis": 300000
 *                                             },
 *					        "whitelist" : {
 *								            "key id" : "public key"
 *					    		          }
 *	             }
 * }
 * }
 * }
 * </pre>
 */
@Slf4j
@PolicyInformationPoint(name = JWTPolicyInformationPoint.NAME, description = JWTPolicyInformationPoint.DESCRIPTION)
public class JWTPolicyInformationPoint {

    static final String JWT_KEY                  = "jwt";
    static final String NAME                     = JWT_KEY;
    static final String DESCRIPTION              = "Json Web Token Attributes. Attributes depend on the JWT's validity, meaning they can change their state over time according to the JWT's signature, maturity and expiration.";
    static final String PUBLIC_KEY_VARIABLES_KEY = "publicKeyServer";
    static final String WHITELIST_VARIABLES_KEY  = "whitelist";

    private static final String JWT_CONFIG_MISSING_ERROR = "The key 'jwt' with the configuration of public key server and key whitelist. All JWT tokens will be treated as if the signatures could not be validated.";

    private static final String VALIDITY_DOCS = "The token's validity state";

    /**
     * Possible states of validity a JWT can have
     */
    enum ValidityState {

        // the JWT is valid
        VALID

        // the JWT has expired
        , EXPIRED

        // the JWT expires before it becomes valid, so it is never valid
        , NEVER_VALID

        // the JWT will become valid in future
        , IMMATURE

        /*
         * the JWT's signature does not match <p> either the payload has been tampered
         * with, the public key could not be obtained, or the public key does not match
         * the signature
         */
        , UNTRUSTED

        /*
         * the JWT is incompatible <p> either an incompatible hashing algorithm has been
         * used or required fields do not have the correct format
         */
        , INCOMPATIBLE

        // the JWT is missing required fields
        , INCOMPLETE

        // the token is not a JWT
        , MALFORMED

    }

    private final JWTKeyProvider keyProvider;

    /**
     * Constructor
     *
     * @param jwtKeyProvider a JWTKeyProvider
     */
    public JWTPolicyInformationPoint(JWTKeyProvider jwtKeyProvider) {
        this.keyProvider = jwtKeyProvider;
    }

    /**
     * Checks the validity of a JWT token. Will update based on validity times of
     * the token.
     *
     * @param rawToken a raw JWT Token
     * @param variables SAPL variables
     * @return a TRUE Val, iff the token is valid.
     */
    @Attribute
    public Flux<Val> valid(@Text Val rawToken, Map<String, Val> variables) {
        return validityState(rawToken, variables).map(ValidityState.VALID::equals).map(Val::of);
    }

    /**
     * A JWT's validity
     * <p>
     * The validity may change over time as it becomes mature and then expires.
     *
     * @param rawToken object containing JWT
     * @param variables configuration variables
     * @return Flux representing the JWT's validity over time
     */
    @Attribute(docs = VALIDITY_DOCS)
    public Flux<Val> validity(@Text Val rawToken, Map<String, Val> variables) {
        return validityState(rawToken, variables).map(Object::toString).map(Val::of);
    }

    private Flux<ValidityState> validityState(@Text Val rawToken, Map<String, Val> variables) {

        if (rawToken == null || !rawToken.isTextual())
            return Flux.just(ValidityState.MALFORMED);

        SignedJWT    signedJwt;
        JWTClaimsSet claims;
        try {
            signedJwt = SignedJWT.parse(rawToken.getText());
            claims    = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return Flux.just(ValidityState.MALFORMED);
        }

        // ensure all required claims are well-formed
        if (!hasCompatibleClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPATIBLE);

        // ensure presence of all required claims
        if (!hasRequiredClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPLETE);

        return validateSignature(signedJwt, variables).flatMapMany(isValid -> {

            if (Boolean.FALSE.equals(isValid))
                return Flux.just(ValidityState.UNTRUSTED);

            return validateTime(claims);
        });
    }

    private Mono<Boolean> validateSignature(SignedJWT signedJwt, Map<String, Val> variables) {

        var jwtConfig = variables.get(JWT_KEY);
        if (jwtConfig == null || !jwtConfig.isDefined()) {
            log.error(JWT_CONFIG_MISSING_ERROR);
            return Mono.just(Boolean.FALSE);
        }

        var keyId = signedJwt.getHeader().getKeyID();

        Mono<RSAPublicKey> publicKey       = null;
        var                whitelist       = jwtConfig.get().get(WHITELIST_VARIABLES_KEY);
        var                isFromWhitelist = false;
        if (whitelist != null && whitelist.get(keyId) != null) {
            var key = JWTEncodingDecodingUtils.jsonNodeToKey(whitelist.get(keyId));
            if (key.isPresent()) {
                publicKey       = Mono.just(key.get());
                isFromWhitelist = true;
            }
        }

        if (publicKey == null) {
            var jPublicKeyServer = jwtConfig.get().get(PUBLIC_KEY_VARIABLES_KEY);

            if (jPublicKeyServer == null)
                return Mono.just(Boolean.FALSE);

            try {
                publicKey = keyProvider.provide(keyId, jPublicKeyServer);
            } catch (CachingException e) {
                log.error(e.getLocalizedMessage());
                publicKey = Mono.empty();
            }
        }

        return publicKey.map(signatureOfTokenIsValid(keyId, signedJwt, isFromWhitelist)).defaultIfEmpty(Boolean.FALSE);
    }

    private Function<RSAPublicKey, Boolean> signatureOfTokenIsValid(String keyId, SignedJWT signedJwt,
            boolean isFromWhitelist) {
        return publicKey -> {
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            try {
                var isValid = signedJwt.verify(verifier);
                if (isValid && !isFromWhitelist)
                    keyProvider.cache(keyId, publicKey);
                return isValid;
            } catch (JOSEException e) {
                // erroneous signatures or data are treated same as failed verifications
                return Boolean.FALSE;
            }
        };
    }

    /**
     * Verifies token validity based on time
     *
     * @param claims JWT claims
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
            return Flux.just(ValidityState.NEVER_VALID);

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
    private boolean hasRequiredClaims(SignedJWT jwt) {

        // verify presence of key ID
        String kid = jwt.getHeader().getKeyID();
        return kid != null && !kid.isBlank();

        // JWT contains all required claims
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

        // verify absence of incompatible
        // critical parameters present, need to check for compatibility
        // now: no critical parameters compatible, return false
        // done this way in order to cover all possible cases with tests (eg. null &&
        // isEmpty() not testable)
        return header.getCriticalParams() == null;

        // all claims are compatible with requirements
    }

}
