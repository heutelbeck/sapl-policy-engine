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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.OAuthConfig;
import io.sapl.node.SaplNodeProperties.UserEntry;
import io.sapl.node.auth.UserLookupService;
import io.sapl.node.auth.http.CachingHttpAuthHandler.Outcome;
import io.sapl.node.auth.http.CachingHttpAuthHandler.TtlExpiry;
import io.sapl.node.auth.http.HttpAuthHandler.HttpAuthResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;

@DisplayName("CachingHttpAuthHandler")
@ExtendWith(MockitoExtension.class)
class CachingHttpAuthHandlerTests {

    @Mock
    private SaplNodeProperties properties;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private JwtDecoder jwtDecoder;

    private static final String AUTHORIZATION = "Authorization";
    private static final String DEFAULT_PDP   = "default";
    private static final String TENANT_PDP    = "tenant-x";

    private CachingHttpAuthHandler handler() {
        return new CachingHttpAuthHandler(properties, userLookupService, jwtDecoder, Duration.ofMinutes(5),
                Duration.ofSeconds(5), 100L);
    }

    private CachingHttpAuthHandler handlerWithoutJwtDecoder() {
        return new CachingHttpAuthHandler(properties, userLookupService, null, Duration.ofMinutes(5),
                Duration.ofSeconds(5), 100L);
    }

    private static String basicHeader(String username, String password) {
        val raw     = (username + ':' + password).getBytes(StandardCharsets.UTF_8);
        val encoded = Base64.getEncoder().encodeToString(raw);
        return "Basic " + encoded;
    }

    private static UserEntry basicUser(String id, String pdpId, String username, String encodedSecret) {
        val basic = new BasicCredentials();
        basic.setUsername(username);
        basic.setSecret(encodedSecret);
        val entry = new UserEntry();
        entry.setId(id);
        entry.setPdpId(pdpId);
        entry.setBasic(basic);
        return entry;
    }

    private static UserEntry apiKeyUser(String id, String pdpId, String encodedKey) {
        val entry = new UserEntry();
        entry.setId(id);
        entry.setPdpId(pdpId);
        entry.setApiKey(encodedKey);
        return entry;
    }

    @Nested
    @DisplayName("no Authorization header")
    class NoAuthorizationHeader {

        @Test
        @DisplayName("missing header is rejected when allow-no-auth is false")
        void whenNoHeaderAndAllowNoAuthFalseThenAuthenticationRequired() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(null);
            when(properties.isAllowNoAuth()).thenReturn(false);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class)
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("blank header is treated like a missing header")
        void whenBlankHeaderAndAllowNoAuthFalseThenAuthenticationRequired() {
            when(request.getHeader(AUTHORIZATION)).thenReturn("   ");
            when(properties.isAllowNoAuth()).thenReturn(false);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("missing header is accepted when allow-no-auth is true and routes to the default PDP")
        void whenNoHeaderAndAllowNoAuthTrueThenDefaultPdp() {
            when(request.getHeader(AUTHORIZATION)).thenReturn(null);
            when(properties.isAllowNoAuth()).thenReturn(true);
            when(properties.getDefaultPdpId()).thenReturn(DEFAULT_PDP);

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(DEFAULT_PDP);
        }
    }

    @Nested
    @DisplayName("Basic authentication")
    class BasicAuthentication {

        @Test
        @DisplayName("valid Basic credentials route to the user's pdpId")
        void whenValidBasicCredentialsThenUserPdpId() {
            val header = basicHeader("alice", "secret");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(userLookupService.verifyBasicCredentials("alice", "secret"))
                    .thenReturn(Optional.of(basicUser("alice", TENANT_PDP, "alice", "encoded-secret")));

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(TENANT_PDP);
        }

        @Test
        @DisplayName("Basic credential without colon is rejected as bad credentials, not 500")
        void whenBasicHeaderHasNoColonThenBadCredentials() {
            val header = "Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8));
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("Basic credentials with unknown username are rejected")
        void whenBasicUsernameUnknownThenAuthenticationFailed() {
            val header = basicHeader("ghost", "anything");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(userLookupService.verifyBasicCredentials("ghost", "anything")).thenReturn(Optional.empty());

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("Basic credentials with wrong password are rejected")
        void whenBasicPasswordWrongThenAuthenticationFailed() {
            val header = basicHeader("alice", "wrong");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(userLookupService.verifyBasicCredentials("alice", "wrong")).thenReturn(Optional.empty());

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("Basic header is rejected when allow-basic is false even if credentials would otherwise be valid")
        void whenBasicDisabledThenRejectedRegardlessOfValidity() {
            val header = basicHeader("alice", "secret");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(false);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
            verify(userLookupService, never()).verifyBasicCredentials(anyString(), anyString());
        }

        @Test
        @DisplayName("malformed Basic credential (invalid Base64) surfaces as HttpAuthenticationException, not 500")
        void whenBasicHeaderIsNotValidBase64ThenHttpAuthenticationException() {
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(request.getHeader(AUTHORIZATION)).thenReturn("Basic !!!not-base64!!!");

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class)
                    .hasMessageContaining("Authentication failed");
        }
    }

    @Nested
    @DisplayName("API key authentication")
    class ApiKeyAuthentication {

        @Test
        @DisplayName("valid API key routes to the user's pdpId")
        void whenValidApiKeyThenUserPdpId() {
            val rawKey = "sapl_kid_secret";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + rawKey);
            when(properties.isAllowApiKeyAuth()).thenReturn(true);
            when(userLookupService.findByApiKey(rawKey))
                    .thenReturn(Optional.of(apiKeyUser("svc", TENANT_PDP, "encoded")));

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(TENANT_PDP);
        }

        @Test
        @DisplayName("unknown API key is rejected")
        void whenUnknownApiKeyThenRejected() {
            val rawKey = "sapl_kid_secret";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + rawKey);
            when(properties.isAllowApiKeyAuth()).thenReturn(true);
            when(userLookupService.findByApiKey(rawKey)).thenReturn(Optional.empty());

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("API key prefix is rejected when allow-api-key is false (does not fall through to JWT path)")
        void whenApiKeyDisabledThenRejected() {
            val rawKey = "sapl_kid_secret";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + rawKey);
            when(properties.isAllowApiKeyAuth()).thenReturn(false);
            when(properties.isAllowOauth2Auth()).thenReturn(false);

            val sut = new CachingHttpAuthHandler(properties, userLookupService, null, Duration.ofMinutes(5),
                    Duration.ofSeconds(5), 100L);

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
            verify(userLookupService, never()).findByApiKey(anyString());
        }
    }

    @Nested
    @DisplayName("OAuth2 / JWT authentication")
    class JwtAuthentication {

        private static final Instant ISSUED_AT  = Instant.parse("2026-02-13T00:00:00Z");
        private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(60);

        @Test
        @DisplayName("valid JWT with pdpId claim routes to that pdpId")
        void whenValidJwtThenClaimedPdpId() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            val oauth = new OAuthConfig();
            oauth.setPdpIdClaim("sapl_pdp_id");
            when(properties.getOauth()).thenReturn(oauth);
            val jwt = Jwt.withTokenValue(token).header("alg", "RS256").claim("sapl_pdp_id", TENANT_PDP)
                    .issuedAt(ISSUED_AT).expiresAt(EXPIRES_AT).build();
            when(jwtDecoder.decode(token)).thenReturn(jwt);

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(TENANT_PDP);
        }

        @Test
        @DisplayName("JWT without an exp claim is rejected by default (would grant non-expiring access)")
        void whenJwtWithoutExpiryAndNotAllowedThenRejected() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            when(properties.getOauth()).thenReturn(new OAuthConfig());
            val jwt = Jwt.withTokenValue(token).header("alg", "RS256").claim("sapl_pdp_id", TENANT_PDP)
                    .issuedAt(ISSUED_AT).build();
            when(jwtDecoder.decode(token)).thenReturn(jwt);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("JWT without an exp claim is accepted when allow-jwt-without-expiry=true")
        void whenJwtWithoutExpiryAndAllowedThenAccepted() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            val oauth = new OAuthConfig();
            oauth.setAllowJwtWithoutExpiry(true);
            when(properties.getOauth()).thenReturn(oauth);
            val jwt = Jwt.withTokenValue(token).header("alg", "RS256").claim("sapl_pdp_id", TENANT_PDP)
                    .issuedAt(ISSUED_AT).build();
            when(jwtDecoder.decode(token)).thenReturn(jwt);

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(TENANT_PDP);
        }

        @Test
        @DisplayName("JWT decoder failure is reported as authentication failed (no stack leak to client)")
        void whenJwtDecoderFailsThenAuthenticationFailed() {
            val token = "broken.jwt.token";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            when(jwtDecoder.decode(token)).thenThrow(new JwtException("signature mismatch"));

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("JWT without pdpId claim and rejectOnMissingPdpId=true is rejected")
        void whenJwtMissingPdpIdAndRejectOnMissingThenRejected() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            val oauth = new OAuthConfig();
            oauth.setPdpIdClaim("sapl_pdp_id");
            when(properties.getOauth()).thenReturn(oauth);
            when(properties.isRejectOnMissingPdpId()).thenReturn(true);
            val jwt = Jwt.withTokenValue(token).header("alg", "RS256").claim("sub", "user").issuedAt(ISSUED_AT)
                    .expiresAt(EXPIRES_AT).build();
            when(jwtDecoder.decode(token)).thenReturn(jwt);

            val sut = handler();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }

        @Test
        @DisplayName("JWT without pdpId claim falls back to default pdpId when reject-on-missing is false")
        void whenJwtMissingPdpIdAndAllowMissingThenDefaultPdp() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);
            val oauth = new OAuthConfig();
            oauth.setPdpIdClaim("sapl_pdp_id");
            when(properties.getOauth()).thenReturn(oauth);
            when(properties.isRejectOnMissingPdpId()).thenReturn(false);
            when(properties.getDefaultPdpId()).thenReturn(DEFAULT_PDP);
            val jwt = Jwt.withTokenValue(token).header("alg", "RS256").claim("other", "value").issuedAt(ISSUED_AT)
                    .expiresAt(EXPIRES_AT).build();
            when(jwtDecoder.decode(token)).thenReturn(jwt);

            val sut = handler();

            assertThat(sut.authenticate(request).pdpId()).isEqualTo(DEFAULT_PDP);
        }

        @Test
        @DisplayName("Bearer token is rejected when no JwtDecoder is configured even with allow-oauth2 enabled")
        void whenNoJwtDecoderConfiguredThenBearerRejected() {
            val token = "eyJhbGciOiJSUzI1NiJ9.payload.sig";
            when(request.getHeader(AUTHORIZATION)).thenReturn("Bearer " + token);
            when(properties.isAllowOauth2Auth()).thenReturn(true);

            val sut = handlerWithoutJwtDecoder();

            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("cache reuse")
    class CacheReuse {

        @Test
        @DisplayName("identical Basic header in two consecutive calls verifies the credentials only once")
        void whenSameBasicHeaderTwiceThenCredentialsVerifiedOnce() {
            val header = basicHeader("alice", "secret");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(userLookupService.verifyBasicCredentials("alice", "secret"))
                    .thenReturn(Optional.of(basicUser("alice", TENANT_PDP, "alice", "encoded-secret")));

            val sut = handler();
            sut.authenticate(request);
            sut.authenticate(request);

            verify(userLookupService, times(1)).verifyBasicCredentials(any(), any());
        }

        @Test
        @DisplayName("identical failed Basic header in two consecutive calls hits the cache and only verifies once")
        void whenSameInvalidBasicHeaderTwiceThenSecondCallStillFailsFromCache() {
            val header = basicHeader("alice", "wrong");
            when(request.getHeader(AUTHORIZATION)).thenReturn(header);
            when(properties.isAllowBasicAuth()).thenReturn(true);
            when(userLookupService.verifyBasicCredentials("alice", "wrong")).thenReturn(Optional.empty());

            val sut = handler();
            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);
            assertThatThrownBy(() -> sut.authenticate(request)).isInstanceOf(HttpAuthenticationException.class);

            verify(userLookupService, times(1)).verifyBasicCredentials(any(), any());
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @ParameterizedTest
        @ValueSource(longs = { 0L, -1L })
        @DisplayName("rejects non-positive maxSize")
        void whenMaxSizeNotPositiveThenThrows(long maxSize) {
            val positiveTtl = Duration.ofMinutes(5);
            val negativeTtl = Duration.ofSeconds(5);
            assertThatThrownBy(() -> new CachingHttpAuthHandler(properties, userLookupService, null, positiveTtl,
                    negativeTtl, maxSize)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxSize");
        }

        @Test
        @DisplayName("accepts positive maxSize")
        void whenMaxSizePositiveThenConstructs() {
            assertThatCode(() -> new CachingHttpAuthHandler(properties, userLookupService, null, Duration.ofMinutes(5),
                    Duration.ofSeconds(5), 100L)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("TtlExpiry")
    class TtlExpiryTests {

        private static final Duration POSITIVE = Duration.ofMinutes(5);
        private static final Duration NEGATIVE = Duration.ofSeconds(5);

        @Test
        @DisplayName("non-JWT success uses the configured positive TTL")
        void whenSuccessHasNoExpiryThenPositiveTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val outcome = new Outcome.Success(new HttpAuthResult("default"), null);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("JWT success uses the token's exp when it is closer than positive TTL")
        void whenJwtExpiresBeforePositiveTtlThenTtlIsShortened() {
            val expiry           = new TtlExpiry(POSITIVE, NEGATIVE);
            val thirtyOutFromNow = Instant.now().plusSeconds(30);
            val outcome          = new Outcome.Success(new HttpAuthResult("default"), thirtyOutFromNow);

            val ttl = expiry.expireAfterCreate("k", outcome, 0L);

            assertThat(ttl).isLessThan(POSITIVE.toNanos()).isGreaterThan(Duration.ofSeconds(25).toNanos())
                    .isLessThanOrEqualTo(Duration.ofSeconds(30).toNanos());
        }

        @Test
        @DisplayName("JWT success uses positive TTL when token exp is further away")
        void whenJwtExpiresAfterPositiveTtlThenPositiveTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val farOut  = Instant.now().plus(Duration.ofHours(1));
            val outcome = new Outcome.Success(new HttpAuthResult("default"), farOut);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("JWT success with an already-elapsed exp expires immediately")
        void whenJwtExpiryAlreadyPastThenTtlIsZero() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val past    = Instant.now().minusSeconds(10);
            val outcome = new Outcome.Success(new HttpAuthResult("default"), past);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isZero();
        }

        @Test
        @DisplayName("JWT success with an exp beyond the nanosecond range does not throw and clamps to the positive TTL")
        void whenJwtExpiryBeyondNanoRangeThenPositiveTtlApplies() {
            val expiry    = new TtlExpiry(POSITIVE, NEGATIVE);
            val farBeyond = Instant.now().plus(Duration.ofDays(365L * 1000));
            val outcome   = new Outcome.Success(new HttpAuthResult("default"), farBeyond);

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(POSITIVE.toNanos());
        }

        @Test
        @DisplayName("failure uses the configured negative TTL")
        void whenFailureThenNegativeTtlApplies() {
            val expiry  = new TtlExpiry(POSITIVE, NEGATIVE);
            val outcome = new Outcome.Failure("denied");

            assertThat(expiry.expireAfterCreate("k", outcome, 0L)).isEqualTo(NEGATIVE.toNanos());
        }
    }
}
