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
package io.sapl.node.auth.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.UserLookupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Per-request authentication for the bypass-Spring HTTP PDP endpoint, with
 * Caffeine-cached results so that line-rate request throughput does not pay
 * an Argon2 verification per request.
 * <p>
 * Cache key is the SHA-256 hash of the {@code Authorization} header (so the
 * raw header is not stored in cache memory). Positive entries expire after
 * {@code positiveTtl} (or sooner if the credential is a JWT and its
 * {@code exp} claim is closer). Negative entries expire after
 * {@code negativeTtl} so a transient lookup miss is short-lived without
 * giving an attacker an Argon2 oracle for every brute-force probe.
 */
@Slf4j
public final class CachingHttpAuthHandler implements HttpAuthHandler {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BASIC_PREFIX  = "Basic ";
    private static final String SAPL_PREFIX   = "sapl_";

    private static final String ERROR_BAD_CREDENTIALS = "Authentication failed.";
    private static final String ERROR_NO_CREDENTIALS  = "Authentication is required.";

    private static final String WARN_JWT_NO_EXPIRY_ACCEPTED = "Accepting a JWT without an 'exp' claim because io.sapl.node.oauth.allow-jwt-without-expiry=true. This grants non-expiring access to the PDP.";
    private static final String WARN_JWT_NO_EXPIRY_REJECTED = "Rejected a JWT without an 'exp' claim. It would grant non-expiring access; set io.sapl.node.oauth.allow-jwt-without-expiry=true to accept (insecure).";

    private final SaplNodeProperties     properties;
    private final UserLookupService      userLookupService;
    private final @Nullable JwtDecoder   jwtDecoder;
    private final HttpAuthResult         defaultPdpResult;
    private final Cache<String, Outcome> cache;

    public CachingHttpAuthHandler(SaplNodeProperties properties,
            UserLookupService userLookupService,
            @Nullable JwtDecoder jwtDecoder,
            Duration positiveTtl,
            Duration negativeTtl,
            long maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
        this.properties        = properties;
        this.userLookupService = userLookupService;
        this.jwtDecoder        = jwtDecoder;
        this.defaultPdpResult  = new HttpAuthResult(properties.getDefaultPdpId(), null);
        this.cache             = Caffeine.newBuilder().maximumSize(maxSize)
                .expireAfter(new TtlExpiry(positiveTtl, negativeTtl)).build();
    }

    @Override
    public HttpAuthResult authenticate(HttpServletRequest request) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            if (properties.isAllowNoAuth()) {
                return defaultPdpResult;
            }
            throw new HttpAuthenticationException(ERROR_NO_CREDENTIALS);
        }
        val key     = sha256(header);
        val outcome = cache.get(key, k -> resolveOutcome(header));
        return switch (outcome) {
        case Outcome.Success(var result, var ignored) -> result;
        case Outcome.Failure(var message)             -> throw new HttpAuthenticationException(message);
        };
    }

    private Outcome resolveOutcome(String header) {
        try {
            return verify(header);
        } catch (HttpAuthenticationException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    private Outcome.Success verify(String header) {
        if (header.startsWith(BEARER_PREFIX + SAPL_PREFIX) && properties.isAllowApiKeyAuth()) {
            return new Outcome.Success(verifyApiKey(header.substring(BEARER_PREFIX.length())), null);
        }
        if (header.startsWith(BASIC_PREFIX) && properties.isAllowBasicAuth()) {
            return new Outcome.Success(verifyBasic(header.substring(BASIC_PREFIX.length())), null);
        }
        if (header.startsWith(BEARER_PREFIX) && properties.isAllowOauth2Auth()) {
            return verifyJwt(header.substring(BEARER_PREFIX.length()));
        }
        throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
    }

    private HttpAuthResult verifyApiKey(String apiKey) {
        // Defense in depth: the call site already gates on the sapl_ prefix,
        // but enforce it here too so a future caller can't accidentally route
        // a non-prefixed token through the API-key path.
        if (!apiKey.startsWith(SAPL_PREFIX)) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        val userOpt = userLookupService.findByApiKey(apiKey);
        if (userOpt.isEmpty()) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        return new HttpAuthResult(userOpt.get().getPdpId(), null);
    }

    private HttpAuthResult verifyBasic(String credentials) {
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(credentials);
        } catch (IllegalArgumentException e) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS, e);
        }
        val text  = new String(decoded, StandardCharsets.UTF_8);
        val colon = text.indexOf(':');
        if (colon < 0) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        val username = text.substring(0, colon);
        val password = text.substring(colon + 1);
        val userOpt  = userLookupService.verifyBasicCredentials(username, password);
        if (userOpt.isEmpty()) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        return new HttpAuthResult(userOpt.get().getPdpId(), null);
    }

    private Outcome.Success verifyJwt(String token) {
        if (jwtDecoder == null) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException | JwtDecoderInitializationException e) {
            // JwtDecoderInitializationException (a RuntimeException, not a
            // JwtException) is thrown when the issuer is unreachable at decode time.
            // Fail closed rather than letting it escape as a 500.
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS, e);
        }
        val pdpIdClaim = properties.getOauth().getPdpIdClaim();
        val pdpIdValue = jwt.getClaimAsString(pdpIdClaim);
        val expiresAt  = jwt.getExpiresAt();
        if (expiresAt == null) {
            if (!properties.getOauth().isAllowJwtWithoutExpiry()) {
                log.warn(WARN_JWT_NO_EXPIRY_REJECTED);
                throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
            }
            log.warn(WARN_JWT_NO_EXPIRY_ACCEPTED);
        }
        if (pdpIdValue == null || pdpIdValue.isBlank()) {
            if (properties.isRejectOnMissingPdpId()) {
                throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
            }
            return new Outcome.Success(new HttpAuthResult(properties.getDefaultPdpId(), expiresAt), expiresAt);
        }
        return new Outcome.Success(new HttpAuthResult(pdpIdValue, expiresAt), expiresAt);
    }

    private static String sha256(String header) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(header.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Sealed outcome carried by the cache. Splitting positive and negative
     * cases lets the {@link Expiry} policy give them different TTLs.
     * <p>
     * A successful JWT verification carries the token's {@code exp} claim so
     * the cache can evict the entry no later than the token expires. Other
     * credentials carry {@code null} and fall back to {@code positiveTtl}.
     */
    sealed interface Outcome {

        record Success(HttpAuthResult result, @Nullable Instant expiresAt) implements Outcome {}

        record Failure(String message) implements Outcome {}
    }

    static final class TtlExpiry implements Expiry<String, Outcome> {

        private final long positiveNanos;
        private final long negativeNanos;

        TtlExpiry(Duration positive, Duration negative) {
            this.positiveNanos = positive.toNanos();
            this.negativeNanos = negative.toNanos();
        }

        @Override
        public long expireAfterCreate(@NonNull String key, @NonNull Outcome value, long currentTime) {
            return ttl(value);
        }

        @Override
        public long expireAfterUpdate(@NonNull String key, @NonNull Outcome value, long currentTime,
                long currentDuration) {
            return ttl(value);
        }

        @Override
        public long expireAfterRead(@NonNull String key, @NonNull Outcome value, long currentTime,
                long currentDuration) {
            return currentDuration;
        }

        private long ttl(Outcome value) {
            return switch (value) {
            case Outcome.Failure ignored                               -> negativeNanos;
            case Outcome.Success(var result, var exp) when exp != null -> ttlUntil(exp);
            case Outcome.Success ignored                               -> positiveNanos;
            };
        }

        // Clamp to [0, positiveNanos] so a far-future exp does not overflow
        // Duration.toNanos().
        private long ttlUntil(Instant exp) {
            val remaining = Duration.between(Instant.now(), exp);
            if (remaining.isNegative()) {
                return 0L;
            }
            if (remaining.compareTo(Duration.ofNanos(positiveNanos)) >= 0) {
                return positiveNanos;
            }
            return remaining.toNanos();
        }
    }
}
