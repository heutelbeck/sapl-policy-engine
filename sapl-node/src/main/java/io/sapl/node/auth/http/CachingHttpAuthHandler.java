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

import static io.sapl.node.auth.JwtClaimPaths.resolveStringClaim;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.AuthenticationCache;
import io.sapl.node.auth.AuthenticationCache.Outcome;
import io.sapl.node.auth.UserLookupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Per-request authentication for the bypass-Spring HTTP PDP endpoint. The
 * outcome of an {@code Authorization} header is kept in the
 * {@link AuthenticationCache} that the RSocket endpoint shares, so that
 * line-rate request throughput does not pay an Argon2 verification per
 * request.
 */
@Slf4j
public final class CachingHttpAuthHandler implements HttpAuthHandler {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BASIC_PREFIX  = "Basic ";
    private static final String SAPL_PREFIX   = "sapl_";

    private static final String ERROR_BAD_CREDENTIALS = "Authentication failed.";
    private static final String ERROR_NO_CREDENTIALS  = "Authentication is required.";

    private static final String WARN_JWT_NO_EXPIRY_ACCEPTED = "Accepting a JWT without an 'exp' claim because io.sapl.node.oauth.allow-jwt-without-expiry=true. This grants non-expiring access to the PDP.";
    private static final String WARN_JWT_NO_EXPIRY_REJECTED = "Rejected a JWT without an 'exp' claim. It would grant non-expiring access. Set io.sapl.node.oauth.allow-jwt-without-expiry=true to accept (insecure).";

    private static final String TRANSPORT = "http";

    private final SaplNodeProperties   properties;
    private final UserLookupService    userLookupService;
    private final @Nullable JwtDecoder jwtDecoder;
    private final HttpAuthResult       defaultPdpResult;
    private final AuthenticationCache  cache;

    public CachingHttpAuthHandler(SaplNodeProperties properties,
            UserLookupService userLookupService,
            @Nullable JwtDecoder jwtDecoder,
            AuthenticationCache cache) {
        this.properties        = properties;
        this.userLookupService = userLookupService;
        this.jwtDecoder        = jwtDecoder;
        this.defaultPdpResult  = new HttpAuthResult(properties.getDefaultPdpId(), null);
        this.cache             = cache;
    }

    @Override
    public HttpAuthResult authenticate(HttpServletRequest request) {
        if (hasSeveralAuthorizationHeaders(request)) {
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        val header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            if (properties.isAllowNoAuth()) {
                return defaultPdpResult;
            }
            throw new HttpAuthenticationException(ERROR_NO_CREDENTIALS);
        }
        val outcome = cache.outcomeOf(TRANSPORT, header.getBytes(StandardCharsets.UTF_8), () -> resolveOutcome(header));
        return switch (outcome) {
        case Outcome.Success(var pdpId, var expiresAt) -> new HttpAuthResult(pdpId, expiresAt);
        case Outcome.Failure(var message)              -> throw new HttpAuthenticationException(message);
        };
    }

    // More than one Authorization header is ambiguous: it is not clear which
    // credentials count, so the request fails closed.
    private static boolean hasSeveralAuthorizationHeaders(HttpServletRequest request) {
        val headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers == null || !headers.hasMoreElements()) {
            return false;
        }
        headers.nextElement();
        return headers.hasMoreElements();
    }

    private Outcome resolveOutcome(String header) {
        try {
            return verify(header);
        } catch (HttpAuthenticationException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    private Outcome.Success verify(String header) {
        if (header.startsWith(BEARER_PREFIX + SAPL_PREFIX)) {
            if (properties.isAllowApiKeyAuth()) {
                return verifyApiKey(header.substring(BEARER_PREFIX.length()));
            }
            throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
        }
        if (header.startsWith(BASIC_PREFIX) && properties.isAllowBasicAuth()) {
            return verifyBasic(header.substring(BASIC_PREFIX.length()));
        }
        if (header.startsWith(BEARER_PREFIX) && properties.isAllowOauth2Auth()) {
            return verifyJwt(header.substring(BEARER_PREFIX.length()));
        }
        throw new HttpAuthenticationException(ERROR_BAD_CREDENTIALS);
    }

    private Outcome.Success verifyApiKey(String apiKey) {
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
        return new Outcome.Success(userOpt.get().getPdpId(), null);
    }

    private Outcome.Success verifyBasic(String credentials) {
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
        return new Outcome.Success(userOpt.get().getPdpId(), null);
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
        val pdpIdValue = resolveStringClaim(jwt, pdpIdClaim);
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
            return new Outcome.Success(properties.getDefaultPdpId(), expiresAt);
        }
        return new Outcome.Success(pdpIdValue, expiresAt);
    }
}
