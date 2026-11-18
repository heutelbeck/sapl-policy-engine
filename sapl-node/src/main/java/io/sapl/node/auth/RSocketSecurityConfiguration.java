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
package io.sapl.node.auth;

import static io.sapl.node.auth.JwtClaimPaths.resolveStringClaim;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.security.oauth2.jwt.Jwt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.metadata.AuthMetadataCodec;
import io.sapl.node.auth.AuthenticationCache.Outcome;
import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.UserLookupService;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator.AuthenticationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Provides the {@link RSocketConnectionAuthenticator} bean for the protobuf
 * RSocket endpoint. Mirrors the HTTP security configuration: supports basic
 * auth, API key (bearer token), and OAuth2/JWT using the same user store.
 * <p>
 * For bearer tokens, JWT validation is attempted first (if OAuth2 is enabled).
 * If the token is not a valid JWT, it falls back to API key lookup. This
 * allows both authentication methods to use the same RSocket BEARER metadata
 * type.
 * <p>
 * JWT connections include the token's {@code exp} claim in the authentication
 * result, enabling the server to dispose the connection when the token expires.
 * <p>
 * The outcome of the credentials of a connection is kept in the
 * {@link AuthenticationCache} that the HTTP endpoint shares, so that a client
 * that connects again does not cost an Argon2 verification each time.
 * <p>
 * The authenticator bean is only created when
 * {@code sapl.pdp.rsocket.enabled=true}. If {@code io.sapl.node.allow-no-auth}
 * is true, all connections are accepted with the default PDP ID.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RSocketSecurityConfiguration {

    private static final String SAPL_API_KEY_PREFIX = "sapl_";
    private static final String TRANSPORT           = "rsocket";

    private static final String ERROR_AUTH_FAILED       = "Authentication failed.";
    private static final String ERROR_INVALID_BEARER    = "Invalid bearer token.";
    private static final String ERROR_MISSING_PDP_CLAIM = "JWT missing required claim: %s.";
    private static final String ERROR_NO_CREDENTIALS    = "No authentication credentials in setup frame.";

    private static final String WARN_JWT_NO_EXPIRY_ACCEPTED = "Accepting a JWT without an 'exp' claim because io.sapl.node.oauth.allow-jwt-without-expiry=true. This grants a non-expiring connection to the PDP.";
    private static final String WARN_JWT_NO_EXPIRY_REJECTED = "Rejected a JWT without an 'exp' claim. It would grant a non-expiring connection. Set io.sapl.node.oauth.allow-jwt-without-expiry=true to accept (insecure).";

    private final SaplNodeProperties   properties;
    private final UserLookupService    userLookupService;
    private final @Nullable JwtDecoder jwtDecoder;
    private final AuthenticationCache  authenticationCache;

    @Bean
    @Nullable
    RSocketConnectionAuthenticator rsocketConnectionAuthenticator(
            @Value("${sapl.pdp.rsocket.enabled:true}") boolean rsocketEnabled) {
        if (!rsocketEnabled) {
            return null;
        }
        val allowNoAuth = properties.isAllowNoAuth();
        return setup -> Mono.defer(() -> authenticateSetup(setup, allowNoAuth));
    }

    private Mono<AuthenticationResult> authenticateSetup(ConnectionSetupPayload setup, boolean allowNoAuth) {
        val metadata = setup.metadata();
        if (metadata.readableBytes() == 0) {
            // No credentials maps to the default PDP only when anonymous access is allowed.
            return allowNoAuth ? Mono.just(new AuthenticationResult(properties.getDefaultPdpId(), null))
                    : Mono.error(new BadCredentialsException(ERROR_NO_CREDENTIALS));
        }
        val credentials = ByteBufUtil.getBytes(metadata);
        return Mono.fromCallable(() -> authenticationCache.outcomeOf(TRANSPORT, credentials, () -> verify(credentials)))
                .flatMap(outcome -> switch (outcome) {
                case Outcome.Success(var pdpId, var expiresAt) -> Mono.just(new AuthenticationResult(pdpId, expiresAt));
                case Outcome.Failure(var message)      ->
                    Mono.<AuthenticationResult>error(new BadCredentialsException(message));
                });
    }

    // Never throws: a refusal is an outcome, which the cache keeps for its
    // negative lifetime, so that a repeated guess does not cost a verification.
    private Outcome verify(byte[] credentials) {
        val metadata = Unpooled.wrappedBuffer(credentials);
        try {
            val authType = AuthMetadataCodec.readWellKnownAuthType(metadata);
            return switch (authType) {
            case SIMPLE ->
                properties.isAllowBasicAuth() ? verifyBasic(metadata) : new Outcome.Failure(ERROR_AUTH_FAILED);
            case BEARER -> verifyBearer(metadata);
            default     -> new Outcome.Failure(ERROR_AUTH_FAILED);
            };
        } catch (RuntimeException e) {
            log.debug("RSocket setup auth failed: {}", e.getMessage());
            return new Outcome.Failure(ERROR_AUTH_FAILED);
        }
    }

    private Outcome verifyBasic(ByteBuf metadata) {
        val usernameBuf = AuthMetadataCodec.readUsername(metadata);
        val passwordBuf = AuthMetadataCodec.readPassword(metadata);
        val username    = usernameBuf.toString(StandardCharsets.UTF_8);
        val password    = passwordBuf.toString(StandardCharsets.UTF_8);
        // Constant-time verification, so timing does not disclose whether a username
        // exists.
        return userLookupService.verifyBasicCredentials(username, password)
                .<Outcome>map(user -> new Outcome.Success(user.getPdpId(), null))
                .orElseGet(() -> new Outcome.Failure(ERROR_AUTH_FAILED));
    }

    private Outcome verifyBearer(ByteBuf metadata) {
        val token = new String(AuthMetadataCodec.readBearerTokenAsCharArray(metadata));

        // Route by prefix: sapl_ marks API keys, JWTs lack it. Prevents
        // a JWT signature failure from silently retrying as an API key.
        if (token.startsWith(SAPL_API_KEY_PREFIX)) {
            if (!properties.isAllowApiKeyAuth()) {
                return new Outcome.Failure(ERROR_AUTH_FAILED);
            }
            return verifyApiKey(token);
        }
        if (jwtDecoder != null && properties.isAllowOauth2Auth()) {
            return verifyJwt(jwtDecoder.decode(token));
        }
        return new Outcome.Failure(ERROR_AUTH_FAILED);
    }

    private Outcome verifyJwt(Jwt jwt) {
        val expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            if (!properties.getOauth().isAllowJwtWithoutExpiry()) {
                log.warn(WARN_JWT_NO_EXPIRY_REJECTED);
                return new Outcome.Failure(ERROR_AUTH_FAILED);
            }
            log.warn(WARN_JWT_NO_EXPIRY_ACCEPTED);
        }
        val pdpIdClaim = properties.getOauth().getPdpIdClaim();
        val pdpIdValue = resolveStringClaim(jwt, pdpIdClaim);

        if (pdpIdValue == null || pdpIdValue.isBlank()) {
            if (properties.isRejectOnMissingPdpId()) {
                return new Outcome.Failure(ERROR_MISSING_PDP_CLAIM.formatted(pdpIdClaim));
            }
            log.debug("RSocket JWT auth: no {} claim, using default pdpId", pdpIdClaim);
            return new Outcome.Success(properties.getDefaultPdpId(), expiresAt);
        }

        log.debug("RSocket JWT auth: pdpId={}, expires={}", pdpIdValue, expiresAt);
        return new Outcome.Success(pdpIdValue, expiresAt);
    }

    private Outcome verifyApiKey(String token) {
        val userOpt = userLookupService.findByApiKey(token);
        if (userOpt.isEmpty()) {
            return new Outcome.Failure(ERROR_INVALID_BEARER);
        }
        val user = userOpt.get();
        log.debug("RSocket API key auth: user={}, pdpId={}", user.getId(), user.getPdpId());
        return new Outcome.Success(user.getPdpId(), null);
    }

}
