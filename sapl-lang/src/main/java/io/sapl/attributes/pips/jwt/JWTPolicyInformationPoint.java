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
package io.sapl.attributes.pips.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Text;
import io.sapl.functions.util.jwt.JWTEncodingDecodingUtils;
import io.sapl.functions.util.jwt.JWTKeyProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Policy Information Point for validating and monitoring JSON Web Tokens.
 * Attributes depend on the JWT's validity, meaning they can change their state
 * over time according to the JWT's signature, maturity, and expiration.
 * Public keys must be fetched from the trusted authentication server for
 * validating signatures. For this purpose, the url and http method for fetching
 * public keys need to be specified in the pdp.json configuration file.
 */
@Slf4j
@PolicyInformationPoint(name = JWTPolicyInformationPoint.NAME, description = JWTPolicyInformationPoint.DESCRIPTION, pipDocumentation = JWTPolicyInformationPoint.DOCUMENTATION)
public class JWTPolicyInformationPoint {

    public static final String JWT_KEY                  = "jwt";
    public static final String NAME                     = JWT_KEY;
    public static final String DESCRIPTION              = "Policy Information Point for validating and monitoring JSON Web Tokens (JWT). Attributes update automatically based on token lifecycle events such as maturity and expiration.";
    public static final String PUBLIC_KEY_VARIABLES_KEY = "publicKeyServer";
    public static final String WHITELIST_VARIABLES_KEY  = "whitelist";

    public static final String DOCUMENTATION = """
            This Policy Information Point validates JSON Web Tokens and monitors their validity state over time.

            JWT tokens are validated against multiple criteria:

            **Signature Verification**

            Tokens must be signed with RS256 algorithm. Public keys for signature verification are sourced from:
            * A whitelist of trusted public keys configured in policy variables
            * A remote key server that provides public keys on demand

            **Time-based Validation**

            Tokens are validated against time claims:
            * `nbf` (not before): Token becomes valid at this timestamp
            * `exp` (expiration): Token becomes invalid at this timestamp

            Validity states transition automatically as time progresses, triggering policy re-evaluation when
            tokens become mature or expire.

            **Configuration**

            Configure the JWT PIP through policy variables in `pdp.json`:

            ```json
            {
              "algorithm": "DENY_UNLESS_PERMIT",
              "variables": {
                "jwt": {
                  "publicKeyServer": {
                    "uri": "http://authz-server:9000/public-key/{id}",
                    "method": "POST",
                    "keyCachingTtlMillis": 300000
                  },
                  "whitelist": {
                    "key-id-1": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
                    "key-id-2": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEB..."
                  }
                }
              }
            }
            ```

            **Public Key Server Configuration**

            * `uri`: URL template for fetching public keys. Use `{id}` placeholder for key ID
            * `method`: HTTP method for requests (GET or POST). Defaults to GET if omitted
            * `keyCachingTtlMillis`: Cache duration for retrieved keys in milliseconds. Defaults to 300000 (5 minutes)

            **Whitelist Configuration**

            The whitelist maps key IDs to Base64-encoded public keys. Whitelisted keys take precedence over
            the key server. Keys must be Base64 URL-safe encoded X.509 SubjectPublicKeyInfo structures.

            **Validity States**

            * `VALID`: Token signature is trusted and time claims are satisfied
            * `EXPIRED`: Token has passed its expiration time
            * `IMMATURE`: Token has not yet reached its not-before time
            * `NEVER_VALID`: Token's not-before time is after its expiration time
            * `UNTRUSTED`: Signature verification failed or public key unavailable
            * `INCOMPATIBLE`: Token uses unsupported algorithm or has critical parameters
            * `INCOMPLETE`: Required claims (key ID) are missing
            * `MALFORMED`: Token is not a valid JWT structure

            **Access Control Examples**

            Basic token validation:
            ```sapl
            policy "require_valid_jwt"
            permit
            where
              var token = subject.jwt;
              token.<jwt.valid>;
            ```

            Check specific validity state:
            ```sapl
            policy "allow_immature_tokens_for_testing"
            permit action == "test:access"
            where
              var token = subject.jwt;
              var state = token.<jwt.validity>;
              state == "VALID" || state == "IMMATURE";
            ```

            Grant access only when token is valid, deny when expired:
            ```sapl
            policy "time_sensitive_access"
            permit action == "document:read"
            where
              var token = subject.credentials.bearer;
              var state = token.<jwt.validity>;
              state == "VALID";

            obligation
              {
                "type": "logAccess",
                "tokenState": state
              }
            ```

            Reject untrusted or tampered tokens:
            ```sapl
            policy "reject_untrusted_tokens"
            deny
            where
              var token = subject.jwt;
              var state = token.<jwt.validity>;
              state == "UNTRUSTED" || state == "MALFORMED";
            ```

            **Reactive Behavior**

            The validity attributes are reactive streams that emit new values when the token's state changes.
            This triggers automatic policy re-evaluation without requiring the client to re-submit requests.

            Example timeline for a token with nbf=now+10s and exp=now+30s:
            * t=0s: Emits IMMATURE
            * t=10s: Emits VALID (policy re-evaluated)
            * t=30s: Emits EXPIRED (policy re-evaluated)
            """;

    private static final String JWT_CONFIG_MISSING_ERROR = "The key 'jwt' with the configuration of public key server and key whitelist. All JWT tokens will be treated as if the signatures could not be validated.";

    /**
     * Possible states of validity a JWT can have
     */
    public enum ValidityState {
        /**
         * The JWT is valid
         */
        VALID,
        /**
         * The JWT has expired
         */
        EXPIRED,
        /**
         * The JWT expires before it becomes valid, so it is never valid
         */
        NEVER_VALID,
        /**
         * The JWT will become valid in future
         */
        IMMATURE,
        /**
         * The JWT's signature does not match. Either the payload has been tampered
         * with, the public key could not be obtained, or the public key does not match
         * the signature
         */
        UNTRUSTED,
        /**
         * The JWT is incompatible. Either an incompatible hashing algorithm has been
         * used or required fields do not have the correct format
         */
        INCOMPATIBLE,
        /**
         * The JWT is missing required fields
         */
        INCOMPLETE,
        /**
         * The token is not a JWT
         */
        MALFORMED
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
    @Attribute(docs = """
            ```(TEXT jwt).<valid>``` validates a JWT token and returns true if the token is currently valid.

            This attribute takes the JWT token as the left-hand input and returns a boolean stream that
            updates automatically as the token transitions between validity states.

            A token is considered valid when:
            * Signature verification succeeds with a trusted public key
            * Current time is within the token's validity period (after nbf, before exp)
            * Token structure and claims meet requirements (RS256 algorithm, key ID present)

            The attribute returns `true` only when the validity state is VALID. All other states
            (EXPIRED, IMMATURE, UNTRUSTED, etc.) result in `false`.

            Example:
            ```sapl
            policy "api_access"
            permit action == "api:call"
            where
              var token = subject.credentials.bearer;
              token.<jwt.valid>;
            ```

            Example with token extracted from authorization header:
            ```sapl
            policy "rest_api_access"
            permit action.http.method == "GET"
            where
              var authHeader = resource.http.headers.Authorization;
              var token = authHeader.substring(7);
              token.<jwt.valid>;
            ```
            """)
    public Flux<Val> valid(@Text Val rawToken, Map<String, Val> variables) {
        return validityState(rawToken, variables).map(ValidityState.VALID::equals).map(Val::of);
    }

    /**
     * A JWT's validity state over time.
     * The validity may change over time as it becomes mature and then expires.
     *
     * @param rawToken object containing JWT
     * @param variables configuration variables
     * @return Flux representing the JWT's validity over time
     */
    @Attribute(docs = """
            ```(TEXT jwt).<validity>``` returns the current validity state of a JWT token as a text value.

            This attribute provides detailed information about why a token is or is not valid. The stream
            emits new states as the token lifecycle progresses, enabling policies to react to state changes.

            Possible return values:
            * `VALID`: Token is currently valid and trusted
            * `EXPIRED`: Token validity period has ended
            * `IMMATURE`: Token validity period has not yet begun
            * `NEVER_VALID`: Token configuration is invalid (nbf after exp)
            * `UNTRUSTED`: Signature verification failed or key unavailable
            * `INCOMPATIBLE`: Unsupported algorithm or critical parameters
            * `INCOMPLETE`: Required claims missing (e.g., key ID)
            * `MALFORMED`: Invalid JWT structure

            Example checking for multiple acceptable states:
            ```sapl
            policy "grace_period_access"
            permit action == "service:use"
            where
              var token = subject.jwt;
              var state = token.<jwt.validity>;
              state == "VALID" || state == "IMMATURE";
            ```

            Example with state-specific obligations:
            ```sapl
            policy "monitored_access"
            permit action == "resource:access"
            where
              var token = subject.credentials.jwt;
              var state = token.<jwt.validity>;
              state == "VALID" || state == "IMMATURE";

            obligation
              {
                "type": "auditLog",
                "tokenState": state,
                "userId": token.<jwt.parseJwt>.payload.sub
              }
            ```

            Example denying specific invalid states:
            ```sapl
            policy "deny_tampered_tokens"
            deny
            where
              var token = subject.jwt;
              var state = token.<jwt.validity>;
              state == "UNTRUSTED" || state == "MALFORMED" || state == "INCOMPATIBLE";

            obligation
              {
                "type": "securityAlert",
                "reason": "Invalid token detected",
                "state": state
              }
            ```

            Example handling expiration gracefully:
            ```sapl
            policy "token_refresh_hint"
            permit action == "api:call"
            where
              var token = subject.jwt;
              var state = token.<jwt.validity>;
              state == "VALID";

            advice
              {
                "type": "tokenStatus",
                "message": state == "VALID" ? "Token valid" : "Token refresh required"
              }
            ```
            """)
    public Flux<Val> validity(@Text Val rawToken, Map<String, Val> variables) {
        return validityState(rawToken, variables).map(Object::toString).map(Val::of);
    }

    private Flux<ValidityState> validityState(@Text Val rawToken, Map<String, Val> variables) {

        if (null == rawToken || !rawToken.isTextual())
            return Flux.just(ValidityState.MALFORMED);

        SignedJWT    signedJwt;
        JWTClaimsSet claims;
        try {
            signedJwt = SignedJWT.parse(rawToken.getText());
            claims    = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return Flux.just(ValidityState.MALFORMED);
        }

        if (!hasCompatibleClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPATIBLE);

        if (!hasRequiredClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPLETE);

        return validateSignature(signedJwt, variables).flatMapMany(isValid -> {

            if (Boolean.FALSE.equals(isValid))
                return Flux.just(ValidityState.UNTRUSTED);

            return validateTime(claims);
        });
    }

    private Mono<Boolean> validateSignature(SignedJWT signedJwt, Map<String, Val> variables) {

        final var jwtConfig = variables.get(JWT_KEY);
        if (null == jwtConfig || !jwtConfig.isDefined()) {
            log.error(JWT_CONFIG_MISSING_ERROR);
            return Mono.just(Boolean.FALSE);
        }

        final var keyId = signedJwt.getHeader().getKeyID();

        Mono<RSAPublicKey> publicKey       = null;
        final var          whitelist       = jwtConfig.get().get(WHITELIST_VARIABLES_KEY);
        var                isFromWhitelist = false;
        if (null != whitelist && null != whitelist.get(keyId)) {
            final var key = JWTEncodingDecodingUtils.jsonNodeToKey(whitelist.get(keyId));
            if (key.isPresent()) {
                publicKey       = Mono.just(key.get());
                isFromWhitelist = true;
            }
        }

        if (null == publicKey) {
            final var jPublicKeyServer = jwtConfig.get().get(PUBLIC_KEY_VARIABLES_KEY);

            if (null == jPublicKeyServer)
                return Mono.just(Boolean.FALSE);

            try {
                publicKey = keyProvider.provide(keyId, jPublicKeyServer);
            } catch (JWTKeyProvider.CachingException e) {
                log.error(e.getLocalizedMessage());
                publicKey = Mono.empty();
            }
        }

        return publicKey.map(signatureOfTokenIsValid(keyId, signedJwt, isFromWhitelist)).defaultIfEmpty(Boolean.FALSE);
    }

    private Function<RSAPublicKey, Boolean> signatureOfTokenIsValid(String keyId, SignedJWT signedJwt,
            boolean isFromWhitelist) {
        return publicKey -> {
            final var verifier = new RSASSAVerifier(publicKey);
            try {
                final var isValid = signedJwt.verify(verifier);
                if (isValid && !isFromWhitelist)
                    keyProvider.cache(keyId, publicKey);
                return isValid;
            } catch (JOSEException e) {
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

        Date notBefore      = claims.getNotBeforeTime();
        Date expirationTime = claims.getExpirationTime();
        Date now            = new Date();

        if (null != notBefore && null != expirationTime && notBefore.getTime() > expirationTime.getTime())
            return Flux.just(ValidityState.NEVER_VALID);

        if (null != expirationTime && expirationTime.getTime() < now.getTime()) {
            return Flux.just(ValidityState.EXPIRED);
        }

        if (null != notBefore && notBefore.getTime() > now.getTime()) {
            if (null == expirationTime) {
                return Flux.concat(Mono.just(ValidityState.IMMATURE), Mono.just(ValidityState.VALID)
                        .delayElement(Duration.ofMillis(notBefore.getTime() - now.getTime())));
            } else {
                return Flux.concat(Mono.just(ValidityState.IMMATURE),
                        Mono.just(ValidityState.VALID)
                                .delayElement(Duration.ofMillis(notBefore.getTime() - now.getTime())),
                        Mono.just(ValidityState.EXPIRED)
                                .delayElement(Duration.ofMillis(expirationTime.getTime() - notBefore.getTime())));
            }
        }

        if (null == expirationTime) {
            return Flux.just(ValidityState.VALID);
        } else {
            return Flux.concat(Mono.just(ValidityState.VALID), Mono.just(ValidityState.EXPIRED)
                    .delayElement(Duration.ofMillis(expirationTime.getTime() - now.getTime())));
        }

    }

    /**
     * Checks if token contains all required claims
     *
     * @param jwt base64 encoded header.body.signature triplet
     * @return true if the token contains all required claims
     */
    private boolean hasRequiredClaims(SignedJWT jwt) {

        String keyId = jwt.getHeader().getKeyID();
        return null != keyId && !keyId.isBlank();
    }

    /**
     * Checks if claims meet requirements
     *
     * @param jwt JWT
     * @return true all claims meet requirements
     */
    private boolean hasCompatibleClaims(SignedJWT jwt) {

        JWSHeader header = jwt.getHeader();

        if (!"RS256".equalsIgnoreCase(header.getAlgorithm().getName()))
            return false;

        return null == header.getCriticalParams();
    }

}
