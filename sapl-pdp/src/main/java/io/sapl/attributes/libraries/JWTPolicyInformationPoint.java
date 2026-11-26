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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
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
import io.sapl.api.stream.Stream;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.stream.Streams;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.attributes.tls.PemTrust;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.Key;
import java.security.PublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;

/**
 * Policy Information Point for validating and monitoring JSON Web Tokens.
 * <p>
 * Tokens are read from subscription secrets for security. Public key
 * configuration is read from policy variables. The PIP supports RSA, EC, and
 * HMAC via Nimbus DefaultJWSVerifierFactory, plus EdDSA (Ed25519, Ed448) via a
 * JDK-native {@link EdDsaJwsVerifier}.
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
            * `payload`: Decoded JWT payload with exact JSON numbers. Whole-second `nbf`, `exp`, and `iat`
              claims from 1970 through 9999 are displayed as ISO-8601 UTC text. Fractional and out-of-range
              claims remain numbers. This display conversion does not change signature verification or
              time-claim validation.
            * `valid`: Boolean indicating current validity
            * `validity`: Detailed validity state string

            **Signature Verification**

            Tokens are verified against all standard JWS algorithms:
            * RSA: RS256, RS384, RS512, PS256, PS384, PS512
            * ECDSA: ES256, ES384, ES512
            * HMAC: HS256, HS384, HS512
            * EdDSA: Ed25519 and Ed448

            Public keys for signature verification are sourced from:
            * A whitelist of trusted keys configured in policy variables
            * A remote key server that provides public keys on demand

            **Time-based Validation**

            Tokens are validated against time claims:
            * `nbf` (not before): Token becomes valid at this timestamp
            * `exp` (expiration): Token becomes invalid at this timestamp

            Validity states transition automatically, triggering policy re-evaluation.
            Future maturity and expiration transitions are scheduled at absolute millisecond boundaries,
            without a fixed lifetime cutoff. A boundary must be representable by the engine's clock;
            an unsupported time is an error. Closing the stream cancels pending timers. After the
            final transition, or when no future transition exists, the token stream completes.

            **Configuration**

            Configure through policy variables in `pdp.json`:

            ```json
            {
              "variables": {
                "jwt": {
                  "secretsKey": "jwt",
                  "clockSkewSeconds": 60,
                  "publicKeyServer": {
                    "uri": "https://authz-server:9000/public-key/{kid}",
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

            The `publicKeyServer` field configures a remote endpoint that serves public keys on demand,
            keyed by the token's key ID. The URI must use `https`: a key fetched over plain `http` can be
            substituted by a network attacker who could then forge tokens this PIP would accept as trusted.
            A non-`https` URI is rejected and the token is treated as untrusted. For local development only,
            set `"allowInsecureHttp": true` in `publicKeyServer` to permit an `http` URI. This is logged with
            a prominent warning and must never be used in production. TLS authenticates the key server and
            protects the keys in transit. Keys in the `whitelist` are configured locally and are not affected.

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

            ## Configure TLS trust

            The JWT PIP reads its endpoint from the public PDP configuration. For a private CA, start with the following `pdp.json` settings and install the referenced profile before deployment. Multiple endpoints and PIPs can reference the same profile.

            ```json
            {
              "variables": {
                "jwt": {
                  "publicKeyServer": {
                    "uri": "https://issuer.example.org/keys/{kid}",
                    "method": "GET",
                    "trustProfile": "site-ca"
                  }
                }
              }
            }
            ```

            The operator sets a fixed host. The token's `kid` selects a signing key on that host. The CA authenticates the HTTPS server, and the fetched public key verifies the JWT signature. Changing the effective trust settings requires a fresh lookup before a cached key can be used with the new settings. Local `whitelist` keys work offline.

            The PEP supplies the JWT in the authorization subscription's `secrets` object and the policy reads `<jwt.token>`. No PDP credential is required for this key lookup.

            ### Obtain and verify the CA certificate

            Ask the administrator of the JWT signing-key server or its HTTPS reverse proxy for its public CA certificate and confirm its SHA-256 fingerprint through your organization's PKI or another authenticated channel. A PEM file contains a `-----BEGIN CERTIFICATE-----` block. If the administrator provides a binary DER certificate, convert it first.

            ```sh
            openssl x509 -inform DER -in issuer.cer -out ca.pem
            ```

            If the CA is already in an approved Java truststore, export its trusted-certificate alias. `keytool` prompts for the store password.

            ```sh
            keytool -exportcert -rfc -keystore truststore.p12 -alias issuing-ca -file ca.pem
            ```

            Check the identity and fingerprint of each certificate before installing it.

            ```sh
            openssl x509 -in ca.pem -noout -subject -issuer -fingerprint -sha256
            ```

            ### Install the named profile

            In the following command, `pdp.input.json` is the complete configuration with the endpoint settings above. `jq --rawfile` reads the public certificate including its PEM delimiters and line breaks. The resulting `pdp.json` keeps the other configuration fields.

            ```sh
            jq --rawfile ca ca.pem '.variables.tlsTrustProfiles["site-ca"] = {trustedCaPem: $ca, includeDefaultTrustRoots: false}' pdp.input.json > pdp.json
            ```

            | Endpoint setting | Trusted roots |
            | --- | --- |
            | Omit `trustProfile` | Runtime defaults. |
            | Select a profile with `includeDefaultTrustRoots` absent or `false` | Only the CA certificates in that profile. |
            | Select a profile with `includeDefaultTrustRoots: true` | Profile certificates and runtime defaults. |

            A named profile requires a nonempty `trustedCaPem` string. Each PEM block must contain a public X.509 certificate. The Java client uses the JVM trust configuration by default. The certificate must match the server's hostname. An invalid bundle, an unknown profile, or a profile on a plaintext endpoint prevents the new PDP configuration from taking effect.

            For a CA rollover, deploy the old and new CA certificates together as consecutive PEM blocks in the profile. After the service switches certificates, remove the retired CA and deploy the configuration again.
            """;

    private static final String ERROR_JWT_CONFIG_MISSING       = "The key 'jwt' with the configuration of public key server and key whitelist is missing from variables. All JWT tokens will be treated as if the signatures could not be validated.";
    private static final String ERROR_KEY_PROVIDER_CONFIG_FAIL = "Key provider rejected the key-server configuration: {}";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

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
    private final Clock          clock;
    private final TimeScheduler  scheduler;

    /**
     * Constructor.
     *
     * @param keyProvider the JWT key provider
     * @param clock the clock used for time-claim validation and boundary scheduling
     * @param scheduler the scheduler used for IMMATURE -> VALID -> EXPIRED
     * transitions
     */
    public JWTPolicyInformationPoint(JWTKeyProvider keyProvider, Clock clock, TimeScheduler scheduler) {
        this.keyProvider = keyProvider;
        this.clock       = clock;
        this.scheduler   = scheduler;
    }

    /**
     * Reads a JWT from subscription secrets using the configured
     * default secrets key and emits an {@link ObjectValue} carrying the
     * decoded header, payload, a boolean {@code valid} flag, and the
     * current {@link ValidityState}. The stream re-emits as the token
     * transitions through IMMATURE, VALID, and EXPIRED.
     *
     * @param ctx the attribute access context (variables, subscription secrets)
     * @return a stream of token-state {@link ObjectValue}s
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
    public Stream<Value> token(AttributeAccessContext ctx) {
        return tokenFromSecrets(resolveSecretsKey(ctx), ctx);
    }

    /**
     * Reads a JWT from subscription secrets using {@code secretsKeyName}
     * as the key, otherwise behaves like {@link #token(AttributeAccessContext)}.
     *
     * @param ctx the attribute access context (variables, subscription secrets)
     * @param secretsKeyName the name of the secrets entry holding the token.
     * Falls back to the default {@code "jwt"} key when {@code null}
     * @return a stream of token-state {@link ObjectValue}s
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
    public Stream<Value> token(AttributeAccessContext ctx, TextValue secretsKeyName) {
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

    private Stream<Value> tokenFromSecrets(String secretsKey, AttributeAccessContext ctx) {
        val tokenValue = ctx.subscriptionSecrets().get(secretsKey);

        if (!(tokenValue instanceof TextValue(String value))) {
            return Streams.just(buildTokenValue(null, ValidityState.MISSING_TOKEN));
        }

        SignedJWT signedJwt;
        try {
            signedJwt = SignedJWT.parse(value);
        } catch (ParseException e) {
            return Streams.just(buildTokenValue(null, ValidityState.MALFORMED));
        }

        val parsedToken = extractParsedToken(signedJwt);
        // Defer signature validation (which may perform a blocking key-server
        // fetch) off the invoke() body. invoke() runs under the broker's lock, so
        // blocking here would stall every attribute subscription PDP-wide. The
        // deferred factory runs on a virtual thread once the stream is consumed.
        return Streams.defer(() -> Streams.map(validityStateStream(signedJwt, ctx),
                state -> buildTokenValue(parsedToken, asState(state))));
    }

    private static ValidityState asState(Value v) {
        return v instanceof TextValue(var name) ? ValidityState.valueOf(name) : ValidityState.MALFORMED;
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
        val payload     = ValueJsonMarshaller.json(signedJwt.getPayload().toString());
        val headerValue = ValueJsonMarshaller
                .fromJsonNode(MAPPER.convertValue(signedJwt.getHeader().toJSONObject(), JsonNode.class));
        if (!(payload instanceof ObjectValue object)) {
            return new ParsedToken(headerValue, Value.NULL);
        }

        val builder = ObjectValue.builder().putAll(object);
        ifPresentReplaceEpochFieldWithIsoTime(object, builder, "nbf");
        ifPresentReplaceEpochFieldWithIsoTime(object, builder, "exp");
        ifPresentReplaceEpochFieldWithIsoTime(object, builder, "iat");
        return new ParsedToken(headerValue, builder.build());
    }

    private void ifPresentReplaceEpochFieldWithIsoTime(ObjectValue payload, ObjectValue.Builder builder, String key) {
        if (!(payload.get(key) instanceof NumberValue(var number)) || number.signum() < 0
                || number.compareTo(BigDecimal.valueOf(MAX_REASONABLE_EPOCH_SECONDS)) > 0
                || number.stripTrailingZeros().scale() > 0) {
            return;
        }

        builder.put(key, Value.of(Instant.ofEpochSecond(number.longValue()).toString()));
    }

    private Stream<Value> validityStateStream(SignedJWT signedJwt, AttributeAccessContext ctx) {
        JWTClaimsSet claims;
        try {
            claims = signedJwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return Streams.just(stateValue(ValidityState.MALFORMED));
        }

        if (!hasCompatibleClaims(signedJwt)) {
            return Streams.just(stateValue(ValidityState.INCOMPATIBLE));
        }

        if (!hasRequiredClaims(signedJwt)) {
            return Streams.just(stateValue(ValidityState.INCOMPLETE));
        }

        val signatureValid = validateSignature(signedJwt, ctx);
        if (!signatureValid) {
            return Streams.just(stateValue(ValidityState.UNTRUSTED));
        }

        val clockSkewSeconds        = resolveClockSkewSeconds(ctx);
        val maxTokenLifetimeSeconds = resolveMaxTokenLifetimeSeconds(ctx);
        return validateTime(claims, clockSkewSeconds, maxTokenLifetimeSeconds);
    }

    private static Value stateValue(ValidityState state) {
        return Value.of(state.toString());
    }

    private boolean validateSignature(SignedJWT signedJwt, AttributeAccessContext ctx) {
        val jwtConfig = ctx.variables().get(JWT_KEY);
        if (!(jwtConfig instanceof ObjectValue jwtConfigObj)) {
            log.error(ERROR_JWT_CONFIG_MISSING);
            return false;
        }

        val keyId = signedJwt.getHeader().getKeyID();

        val whitelist = jwtConfigObj.get(WHITELIST_VARIABLES_KEY);
        if (whitelist instanceof ObjectValue whitelistObj && whitelistObj.containsKey(keyId)) {
            val key = JWTEncodingDecodingUtils.jsonNodeToKey(ValueJsonMarshaller.toJsonNode(whitelistObj.get(keyId)));
            if (key.isPresent()) {
                return verifySignatureOf(signedJwt, key.get());
            }
        }

        if (!(jwtConfigObj.get(PUBLIC_KEY_VARIABLES_KEY) instanceof ObjectValue server)) {
            return false;
        }
        try {
            val provider = keyProvider.withTrust(PemTrust.resolve(ctx.variables(), server));
            val config   = ValueJsonMarshaller.toJsonNode(server);
            val ttl      = JWTKeyProvider.cachingTtlMillis(config);
            val key      = provider.provide(keyId, config);
            if (key.isEmpty() || !verifySignatureOf(signedJwt, key.get())) {
                return false;
            }
            val uri = JWTKeyProvider.resolveKeyServerUri(config, keyId);
            if (uri != null) {
                provider.cache(uri, keyId, key.get(), ttl);
            }
            return true;
        } catch (JWTKeyProvider.CachingException | IllegalArgumentException e) {
            log.error(ERROR_KEY_PROVIDER_CONFIG_FAIL, e.getLocalizedMessage());
            return false;
        }
    }

    private static boolean verifySignatureOf(SignedJWT signedJwt, Key key) {
        try {
            return signedJwt.verify(verifierFor(signedJwt.getHeader(), key));
        } catch (JOSEException e) {
            return false;
        }
    }

    /**
     * Builds the JWS verifier for the token's algorithm. RSA, EC, and HMAC are
     * delegated to Nimbus' {@link DefaultJWSVerifierFactory}; EdDSA is handled by
     * {@link EdDsaJwsVerifier} (JDK-native, Ed25519 and Ed448), which Nimbus'
     * default factory does not cover without Google Tink.
     */
    private static JWSVerifier verifierFor(JWSHeader header, Key key) throws JOSEException {
        if (JWSAlgorithm.EdDSA.equals(header.getAlgorithm()) && key instanceof PublicKey publicKey) {
            return new EdDsaJwsVerifier(publicKey);
        }
        return VERIFIER_FACTORY.createJWSVerifier(header, key);
    }

    private Stream<Value> validateTime(JWTClaimsSet claims, long clockSkewSeconds, long maxTokenLifetimeSeconds) {
        val notBeforeDate  = claims.getNotBeforeTime();
        val expirationDate = claims.getExpirationTime();
        val notBefore      = null == notBeforeDate ? null : notBeforeDate.toInstant();
        val expirationTime = null == expirationDate ? null : expirationDate.toInstant();
        val now            = clock.instant();
        val skewMillis     = clockSkewSeconds * 1000L;

        if (isNeverValid(notBefore, expirationTime, claims, now, maxTokenLifetimeSeconds)) {
            return Streams.just(stateValue(ValidityState.NEVER_VALID));
        }

        val expWithSkew = null != expirationTime ? saturatingAdd(expirationTime.toEpochMilli(), skewMillis) : 0L;
        val nbfWithSkew = null != notBefore ? notBefore.toEpochMilli() - skewMillis : 0L;

        if (null != expirationTime && expWithSkew < now.toEpochMilli()) {
            return Streams.just(stateValue(ValidityState.EXPIRED));
        }

        return buildValidityTimeline(notBefore, expirationTime, now, nbfWithSkew, expWithSkew);
    }

    private static boolean isNeverValid(Instant notBefore, Instant expirationTime, JWTClaimsSet claims, Instant now,
            long maxTokenLifetimeSeconds) {
        if (null != notBefore && null != expirationTime && notBefore.isAfter(expirationTime)) {
            return true;
        }

        if (maxTokenLifetimeSeconds > 0 && null != expirationTime) {
            val issueDate         = claims.getIssueTime();
            val referenceMillis   = null != issueDate ? issueDate.toInstant().toEpochMilli() : now.toEpochMilli();
            val lifetimeMillis    = expirationTime.toEpochMilli() - referenceMillis;
            val maxLifetimeMillis = maxTokenLifetimeSeconds * 1000L;
            return lifetimeMillis > maxLifetimeMillis;
        }
        return false;
    }

    private Stream<Value> buildValidityTimeline(Instant notBefore, Instant expirationTime, Instant now,
            long nbfWithSkew, long expWithSkew) {
        if (null != notBefore && nbfWithSkew > now.toEpochMilli()) {
            val nbfInstant = Instant.ofEpochMilli(nbfWithSkew);
            if (null == expirationTime) {
                return Streams.concat(Streams.just(stateValue(ValidityState.IMMATURE)),
                        Streams.scheduledAt(stateValue(ValidityState.VALID), nbfInstant, scheduler));
            }
            val expInstant = Instant.ofEpochMilli(expWithSkew);
            return Streams.concat(Streams.just(stateValue(ValidityState.IMMATURE)),
                    Streams.scheduledAt(stateValue(ValidityState.VALID), nbfInstant, scheduler),
                    Streams.scheduledAt(stateValue(ValidityState.EXPIRED), expInstant, scheduler));
        }

        if (null == expirationTime) {
            return Streams.just(stateValue(ValidityState.VALID));
        }

        return Streams.concat(Streams.just(stateValue(ValidityState.VALID)),
                Streams.scheduledAt(stateValue(ValidityState.EXPIRED), Instant.ofEpochMilli(expWithSkew), scheduler));
    }

    private static long saturatingAdd(long a, long b) {
        val result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
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
