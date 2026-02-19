/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.libraries;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.proc.JWSVerifierFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.security.Key;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Policy Information Point for validating and monitoring JSON Web Tokens.
 * <p>
 * Tokens are read from subscription secrets for security. Public key
 * configuration is read from policy variables. The PIP supports all standard
 * JWS algorithms (RSA, EC, HMAC) via Nimbus DefaultJWSVerifierFactory.
 */
@Slf4j
@PolicyInformationPoint(name = JWTPolicyInformationPoint.NAME, description = JWTPolicyInformationPoint.DESCRIPTION, pipDocumentation = JWTPolicyInformationPoint.DOCUMENTATION)
public class JWTPolicyInformationPoint {

    public static final String CLOCK_SKEW_SECONDS_KEY         = "clockSkewSeconds";
    public static final String JWT_KEY                        = "jwt";
    public static final String MAX_TOKEN_LIFETIME_SECONDS_KEY = "maxTokenLifetimeSeconds";
    public static final String NAME                           = JWT_KEY;
    public static final String DESCRIPTION                    = "Policy Information Point for validating and monitoring JSON Web Tokens (JWT). Tokens are read securely from subscription secrets. Attributes update automatically based on token lifecycle events such as maturity and expiration.";
    public static final String SECRETS_KEY                    = "secretsKey";
    public static final String PUBLIC_KEY_VARIABLES_KEY       = "publicKeyServer";
    public static final String WHITELIST_VARIABLES_KEY        = "whitelist";

    static final long DEFAULT_CLOCK_SKEW_SECONDS   = 0L;
    static final long MAX_REASONABLE_EPOCH_SECONDS = 253_402_300_799L;

    public static final String DOCUMENTATION = """
            This Policy Information Point validates JSON Web Tokens and monitors their validity state over time.

            JWT tokens are read securely from subscription secrets, never from the policy evaluation context.
            Public key configuration is read from policy variables.

            **Token Access**

            Use the `<jwt.token>` environment attribute to access token data:

            ```sapl
            policy "require valid jwt"
            permit
              <jwt.token>.valid;

            policy "role check"
            permit action == "admin:action";
              "admin" in <jwt.token>.payload.roles;
              <jwt.token>.validity == "VALID";
            ```

            The attribute returns an object with:
            * `header`: Decoded JWT header (algorithm, key ID, etc.)
            * `payload`: Decoded JWT payload with time claims converted to ISO-8601
            * `valid`: Boolean indicating current validity
            * `validity`: Detailed validity state string

            **Signature Verification**

            Tokens are verified against all standard JWS algorithms:
            * RSA: RS256, RS384, RS512, PS256, PS384, PS512
            * ECDSA: ES256, ES384, ES512
            * HMAC: HS256, HS384, HS512

            Public keys for signature verification are sourced from:
            * A whitelist of trusted keys configured in policy variables
            * A remote key server that provides public keys on demand

            **Time-based Validation**

            Tokens are validated against time claims:
            * `nbf` (not before): Token becomes valid at this timestamp
            * `exp` (expiration): Token becomes invalid at this timestamp

            Validity states transition automatically, triggering policy re-evaluation.

            **Configuration**

            Configure through policy variables in `pdp.json`:

            ```json
            {
              "variables": {
                "jwt": {
                  "secretsKey": "jwt",
                  "clockSkewSeconds": 60,
                  "publicKeyServer": {
                    "uri": "http://authz-server:9000/public-key/{id}",
                    "method": "GET",
                    "keyCachingTtlMillis": 300000
                  },
                  "whitelist": {
                    "key-id-1": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
                  }
                }
              }
            }
            ```

            The `secretsKey` field specifies which key in subscription secrets holds the JWT token.
            Defaults to `"jwt"` if omitted.

            The `clockSkewSeconds` field specifies clock skew tolerance in seconds for time claim
            validation (RFC 7519 recommends allowing some leeway). Defaults to 0 (exact comparison)
            if omitted. Set to 60 for typical server deployments.

            The `maxTokenLifetimeSeconds` field specifies the maximum allowed token lifetime in seconds.
            If set and the token's lifetime (`exp` minus `iat`, or `exp` minus now if `iat` is absent)
            exceeds this value, the token is treated as `NEVER_VALID`. Defaults to 0 (disabled) if omitted.

            **Validity States**

            * `VALID`: Token signature is trusted and time claims are satisfied
            * `EXPIRED`: Token has passed its expiration time
            * `IMMATURE`: Token has not yet reached its not-before time
            * `NEVER_VALID`: Token's not-before time is after its expiration time
            * `UNTRUSTED`: Signature verification failed or public key unavailable
            * `INCOMPATIBLE`: Token has critical header parameters
            * `INCOMPLETE`: Required claims (key ID) are missing
            * `MALFORMED`: Token is not a valid JWT structure
            * `MISSING_TOKEN`: No token found under the configured secrets key

            **Limitations**

            * Only JWS (JSON Web Signature, RFC 7515) tokens are supported. JWE (JSON Web
              Encryption, RFC 7516) tokens are not supported. JWE encrypts the token payload
              for confidentiality and uses a fundamentally different structure (5 parts instead
              of 3) requiring private decryption keys. Attempting to use a JWE token will
              result in `MALFORMED` validity state. JWE adoption is low as most deployments
              rely on TLS for transport confidentiality.
            * Token revocation is not checked. JWTs are validated statelessly based on
              cryptographic signature and time claims only. A revoked token remains valid
              from this PIP's perspective until it expires. Applications requiring revocation
              checks should use OAuth2 Token Introspection (RFC 7662) at the application
              layer or a dedicated introspection PIP.
            * Audience (`aud`) and issuer (`iss`) claims are not validated by the PIP.
              These are exposed in `<jwt.token>.payload` for policy authors to check
              directly in policy conditions.
            """;

    private static final String ERROR_JWT_CONFIG_MISSING = "The key 'jwt' with the configuration of public key server and key whitelist is missing from variables. All JWT tokens will be treated as if the signatures could not be validated.";

    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;
    private static final JsonMapper      MAPPER = JsonMapper.builder().build();

    private static final JWSVerifierFactory VERIFIER_FACTORY = new DefaultJWSVerifierFactory();

    /**
     * Possible states of validity a JWT can have.
     */
    public enum ValidityState {
        /** The JWT is valid. */
        VALID,
        /** The JWT has expired. */
        EXPIRED,
        /** The JWT expires before it becomes valid, so it is never valid. */
        NEVER_VALID,
        /** The JWT will become valid in the future. */
        IMMATURE,
        /** The JWT's signature does not match. */
        UNTRUSTED,
        /** The JWT has critical header parameters. */
        INCOMPATIBLE,
        /** The JWT is missing required fields. */
        INCOMPLETE,
        /** The token is not a JWT. */
        MALFORMED,
        /** No token was found under the configured secrets key. */
        MISSING_TOKEN
    }

    private final JWTKeyProvider keyProvider;

    /**
     * Constructor.
     *
     * @param jwtKeyProvider a JWTKeyProvider
     */
    public JWTPolicyInformationPoint(JWTKeyProvider jwtKeyProvider) {
        this.keyProvider = jwtKeyProvider;
    }

    /**
     * Reads a JWT from subscription secrets using the configured default key and
     * returns a reactive stream of token data including header, payload, validity
     * state, and a boolean valid flag.
     *
     * @param ctx the attribute access context
     * @return a reactive stream of ObjectValue containing token data
     */
    @EnvironmentAttribute(docs = """
            ```<jwt.token>``` reads a JWT from subscription secrets using the configured default secrets key
            and returns an object containing the decoded token data and its current validity state.

            The returned object has the structure:
            ```json
            {
              "header": { "kid": "key-1", "alg": "RS256" },
              "payload": { "sub": "user123", "roles": ["admin"], "exp": "2026-02-15T..." },
              "valid": true,
              "validity": "VALID"
            }
            ```

            Time claims (nbf, exp, iat) are converted from epoch seconds to ISO-8601 timestamps.

            The stream re-emits automatically when the token transitions between validity states
            (IMMATURE -> VALID -> EXPIRED).

            Example:
            ```sapl
            policy "require valid jwt"
            permit
              <jwt.token>.valid;
            ```

            Example with claims:
            ```sapl
            policy "admin access"
            permit action == "admin:action";
              "admin" in <jwt.token>.payload.roles;
            ```
            """)
    public Flux<Value> token(AttributeAccessContext ctx) {
        val secretsKey = resolveSecretsKey(ctx);
        return tokenFromSecrets(secretsKey, ctx);
    }

    /**
     * Reads a JWT from subscription secrets using the specified key name and
     * returns a reactive stream of token data.
     *
     * @param ctx the attribute access context
     * @param secretsKeyName the key name in subscription secrets
     * @return a reactive stream of ObjectValue containing token data
     */
    @EnvironmentAttribute(docs = """
            ```<jwt.token(TEXT secretsKeyName)>``` reads a JWT from subscription secrets using the specified
            key name and returns an object containing the decoded token data and its current validity state.

            This overload allows reading tokens stored under a custom key in subscription secrets.

            Example:
            ```sapl
            policy "access token check"
            permit
              <jwt.token("accessToken")>.valid;
            ```
            """)
    public Flux<Value> token(AttributeAccessContext ctx, TextValue secretsKeyName) {
        val key = secretsKeyName != null ? secretsKeyName.value() : JWT_KEY;
        return tokenFromSecrets(key, ctx);
    }

    private String resolveSecretsKey(AttributeAccessContext ctx) {
        val jwtConfig = ctx.variables().get(JWT_KEY);
        if (jwtConfig instanceof ObjectValue jwtConfigObj) {
            val secretsKeyValue = jwtConfigObj.get(SECRETS_KEY);
            if (secretsKeyValue instanceof TextValue(var value)) {
                return value;
            }
        }
        return JWT_KEY;
    }

    private long resolveClockSkewSeconds(AttributeAccessContext ctx) {
        val jwtConfig = ctx.variables().get(JWT_KEY);
        if (jwtConfig instanceof ObjectValue jwtConfigObj) {
            val skewValue = jwtConfigObj.get(CLOCK_SKEW_SECONDS_KEY);
            if (skewValue instanceof NumberValue(var value)) {
                return value.longValue();
            }
        }
        return DEFAULT_CLOCK_SKEW_SECONDS;
    }

    private long resolveMaxTokenLifetimeSeconds(AttributeAccessContext ctx) {
        val jwtConfig = ctx.variables().get(JWT_KEY);
        if (jwtConfig instanceof ObjectValue jwtConfigObj) {
            val maxValue = jwtConfigObj.get(MAX_TOKEN_LIFETIME_SECONDS_KEY);
            if (maxValue instanceof NumberValue(var value)) {
                return value.longValue();
            }
        }
        return 0L;
    }

    private Flux<Value> tokenFromSecrets(String secretsKey, AttributeAccessContext ctx) {
        val tokenValue = ctx.subscriptionSecrets().get(secretsKey);

        if (!(tokenValue instanceof TextValue(String value)))
            return Flux.just(buildTokenValue(null, ValidityState.MISSING_TOKEN));

        SignedJWT signedJwt;
        try {
            signedJwt = SignedJWT.parse(value);
        } catch (ParseException e) {
            return Flux.just(buildTokenValue(null, ValidityState.MALFORMED));
        }

        val parsedToken = extractParsedToken(signedJwt);
        return validityState(signedJwt, ctx).map(state -> buildTokenValue(parsedToken, state));
    }

    private Value buildTokenValue(ParsedToken parsedToken, ValidityState state) {
        val builder = ObjectValue.builder();

        if (parsedToken != null) {
            builder.put("header", parsedToken.header());
            builder.put("payload", parsedToken.payload());
        } else {
            builder.put("header", Value.EMPTY_OBJECT);
            builder.put("payload", Value.EMPTY_OBJECT);
        }

        builder.put("valid", Value.of(ValidityState.VALID == state));
        builder.put("validity", Value.of(state.toString()));

        return builder.build();
    }

    private record ParsedToken(Value header, Value payload) {}

    private ParsedToken extractParsedToken(SignedJWT signedJwt) {
        val payload = MAPPER.convertValue(signedJwt.getPayload().toJSONObject(), JsonNode.class);

        ifPresentReplaceEpochFieldWithIsoTime(payload, "nbf");
        ifPresentReplaceEpochFieldWithIsoTime(payload, "exp");
        ifPresentReplaceEpochFieldWithIsoTime(payload, "iat");

        val headerValue  = ValueJsonMarshaller
                .fromJsonNode(MAPPER.convertValue(signedJwt.getHeader().toJSONObject(), JsonNode.class));
        val payloadValue = ValueJsonMarshaller.fromJsonNode(payload);
        return new ParsedToken(headerValue, payloadValue);
    }

    private void ifPresentReplaceEpochFieldWithIsoTime(JsonNode payload, String key) {
        if (!(payload.isObject() && payload.has(key) && payload.get(key).isNumber()))
            return;

        val epochSeconds = payload.get(key).asLong();
        if (epochSeconds < 0 || epochSeconds > MAX_REASONABLE_EPOCH_SECONDS)
            return;

        val isoString = Instant.ofEpochSecond(epochSeconds).toString();
        ((ObjectNode) payload).set(key, JSON.stringNode(isoString));
    }

    private Flux<ValidityState> validityState(SignedJWT signedJwt, AttributeAccessContext ctx) {
        JWTClaimsSet claims;
        try {
            claims = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return Flux.just(ValidityState.MALFORMED);
        }

        if (!hasCompatibleClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPATIBLE);

        if (!hasRequiredClaims(signedJwt))
            return Flux.just(ValidityState.INCOMPLETE);

        val clockSkewSeconds        = resolveClockSkewSeconds(ctx);
        val maxTokenLifetimeSeconds = resolveMaxTokenLifetimeSeconds(ctx);
        return validateSignature(signedJwt, ctx).flatMapMany(isValid -> {

            if (Boolean.FALSE.equals(isValid))
                return Flux.just(ValidityState.UNTRUSTED);

            return validateTime(claims, clockSkewSeconds, maxTokenLifetimeSeconds);
        });
    }

    private Mono<Boolean> validateSignature(SignedJWT signedJwt, AttributeAccessContext ctx) {

        val jwtConfig = ctx.variables().get(JWT_KEY);
        if (!(jwtConfig instanceof ObjectValue jwtConfigObj)) {
            log.error(ERROR_JWT_CONFIG_MISSING);
            return Mono.just(Boolean.FALSE);
        }

        val keyId = signedJwt.getHeader().getKeyID();

        Mono<Key> publicKey       = null;
        val       whitelist       = jwtConfigObj.get(WHITELIST_VARIABLES_KEY);
        var       isFromWhitelist = false;
        if (whitelist instanceof ObjectValue whitelistObj && whitelistObj.containsKey(keyId)) {
            val keyValue = whitelistObj.get(keyId);
            val key      = JWTEncodingDecodingUtils.jsonNodeToKey(ValueJsonMarshaller.toJsonNode(keyValue));
            if (key.isPresent()) {
                publicKey       = Mono.just(key.get());
                isFromWhitelist = true;
            }
        }

        if (null == publicKey) {
            val jPublicKeyServer = jwtConfigObj.get(PUBLIC_KEY_VARIABLES_KEY);

            if (null == jPublicKeyServer)
                return Mono.just(Boolean.FALSE);

            try {
                publicKey = keyProvider.provide(keyId, ValueJsonMarshaller.toJsonNode(jPublicKeyServer));
            } catch (JWTKeyProvider.CachingException e) {
                log.error(e.getLocalizedMessage());
                publicKey = Mono.empty();
            }
        }

        return publicKey.map(signatureOfTokenIsValid(keyId, signedJwt, isFromWhitelist)).defaultIfEmpty(Boolean.FALSE);
    }

    private Function<Key, Boolean> signatureOfTokenIsValid(String keyId, SignedJWT signedJwt, boolean isFromWhitelist) {
        return key -> {
            try {
                JWSVerifier verifier = VERIFIER_FACTORY.createJWSVerifier(signedJwt.getHeader(), key);
                val         isValid  = signedJwt.verify(verifier);
                if (isValid && !isFromWhitelist)
                    keyProvider.cache(keyId, key);
                return isValid;
            } catch (JOSEException e) {
                return Boolean.FALSE;
            }
        };
    }

    private Flux<ValidityState> validateTime(JWTClaimsSet claims, long clockSkewSeconds, long maxTokenLifetimeSeconds) {
        val notBefore      = claims.getNotBeforeTime();
        val expirationTime = claims.getExpirationTime();
        val now            = new Date();
        val skewMillis     = clockSkewSeconds * 1000L;

        if (isNeverValid(notBefore, expirationTime, claims, now, maxTokenLifetimeSeconds))
            return Flux.just(ValidityState.NEVER_VALID);

        val expWithSkew = null != expirationTime ? saturatingAdd(expirationTime.getTime(), skewMillis) : 0L;
        val nbfWithSkew = null != notBefore ? notBefore.getTime() - skewMillis : 0L;

        if (null != expirationTime && expWithSkew < now.getTime())
            return Flux.just(ValidityState.EXPIRED);

        return buildValidityTimeline(notBefore, expirationTime, now, nbfWithSkew, expWithSkew);
    }

    private static boolean isNeverValid(Date notBefore, Date expirationTime, JWTClaimsSet claims, Date now,
            long maxTokenLifetimeSeconds) {
        if (null != notBefore && null != expirationTime && notBefore.getTime() > expirationTime.getTime())
            return true;

        if (maxTokenLifetimeSeconds > 0 && null != expirationTime) {
            val issueTime         = claims.getIssueTime();
            val referenceMillis   = null != issueTime ? issueTime.getTime() : now.getTime();
            val lifetimeMillis    = expirationTime.getTime() - referenceMillis;
            val maxLifetimeMillis = maxTokenLifetimeSeconds * 1000L;
            return lifetimeMillis > maxLifetimeMillis;
        }
        return false;
    }

    private static Flux<ValidityState> buildValidityTimeline(Date notBefore, Date expirationTime, Date now,
            long nbfWithSkew, long expWithSkew) {
        if (null != notBefore && nbfWithSkew > now.getTime()) {
            val nbfDelay = nbfWithSkew - now.getTime();
            if (null == expirationTime) {
                return Flux.concat(Mono.just(ValidityState.IMMATURE),
                        Mono.just(ValidityState.VALID).delayElement(Duration.ofMillis(nbfDelay)));
            }
            val expDelay = expWithSkew - nbfWithSkew;
            return Flux.concat(Mono.just(ValidityState.IMMATURE),
                    Mono.just(ValidityState.VALID).delayElement(Duration.ofMillis(nbfDelay)),
                    Mono.just(ValidityState.EXPIRED).delayElement(Duration.ofMillis(expDelay)));
        }

        if (null == expirationTime)
            return Flux.just(ValidityState.VALID);

        return validThenExpiredAfterDelay(expWithSkew - now.getTime());
    }

    private static Flux<ValidityState> validThenExpiredAfterDelay(long delayMillis) {
        if (delayMillis > MAX_REASONABLE_EPOCH_SECONDS * 1000L)
            return Flux.just(ValidityState.VALID);
        return Flux.concat(Mono.just(ValidityState.VALID),
                Mono.just(ValidityState.EXPIRED).delayElement(Duration.ofMillis(delayMillis)));
    }

    private static long saturatingAdd(long a, long b) {
        val result = a + b;
        if (((a ^ result) & (b ^ result)) < 0)
            return Long.MAX_VALUE;
        return result;
    }

    private boolean hasRequiredClaims(SignedJWT jwt) {
        val keyId = jwt.getHeader().getKeyID();
        return null != keyId && !keyId.isBlank();
    }

    private boolean hasCompatibleClaims(SignedJWT jwt) {
        val header = jwt.getHeader();
        return null == header.getCriticalParams();
    }

}
