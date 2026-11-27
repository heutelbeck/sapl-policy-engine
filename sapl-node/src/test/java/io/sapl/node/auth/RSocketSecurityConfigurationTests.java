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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.metadata.AuthMetadataCodec;
import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.UserEntry;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RSocketSecurityConfiguration honors auth-mode flags")
class RSocketSecurityConfigurationTests {

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private JwtDecoder jwtDecoder;

    private RSocketConnectionAuthenticator authenticatorFor(SaplNodeProperties properties) {
        return new RSocketSecurityConfiguration(properties, userLookupService, jwtDecoder,
                new AuthenticationCache(Duration.ofMinutes(5), Duration.ofSeconds(5), 100L))
                .rsocketConnectionAuthenticator(true);
    }

    private static io.netty.buffer.ByteBuf basicMetadata(String username, String password) {
        return AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, username.toCharArray(),
                password.toCharArray());
    }

    private static ConnectionSetupPayload setupWith(io.netty.buffer.ByteBuf metadata) {
        val setup = mock(ConnectionSetupPayload.class);
        when(setup.metadata()).thenReturn(metadata);
        return setup;
    }

    @Test
    @DisplayName("basic credentials are rejected without a user-store lookup when basic auth is disabled")
    void whenBasicAuthDisabledThenSimpleCredentialsRejectedWithoutLookup() {
        val properties = new SaplNodeProperties();
        properties.setAllowBasicAuth(false);
        val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, "alice".toCharArray(),
                "secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verifyNoInteractions(userLookupService);
    }

    @Test
    @DisplayName("API key is rejected without a user-store lookup when API key auth is disabled")
    void whenApiKeyAuthDisabledThenApiKeyRejectedWithoutLookup() {
        val properties = new SaplNodeProperties();
        properties.setAllowApiKeyAuth(false);
        val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, "sapl_secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verifyNoInteractions(userLookupService);
    }

    @Test
    @DisplayName("basic credentials are validated against the user store when basic auth is enabled")
    void whenBasicAuthEnabledThenUserStoreIsConsulted() {
        val properties = new SaplNodeProperties();
        properties.setAllowBasicAuth(true);
        when(userLookupService.verifyBasicCredentials("alice", "secret")).thenReturn(Optional.empty());
        val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, "alice".toCharArray(),
                "secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verify(userLookupService).verifyBasicCredentials("alice", "secret");
    }

    @Test
    @DisplayName("an unknown Basic user is rejected with the same generic message as a wrong password, so the message does not reveal which users exist")
    void whenBasicUsernameUnknownThenGenericErrorNotEchoingUsername() {
        val properties = new SaplNodeProperties();
        properties.setAllowBasicAuth(true);
        when(userLookupService.verifyBasicCredentials("ghost", "secret")).thenReturn(Optional.empty());
        val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, "ghost".toCharArray(),
                "secret".toCharArray());

        val result = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BadCredentialsException.class)
                        .hasMessage("Authentication failed.").hasMessageNotContaining("ghost"))
                .verify();
        verify(userLookupService).verifyBasicCredentials("ghost", "secret");
    }

    @Nested
    @DisplayName("when unauthenticated access is allowed")
    class AllowNoAuth {

        @Test
        @DisplayName("a connection with no credentials is accepted as the default PDP")
        void whenNoCredentialsThenDefaultPdp() {
            val properties = new SaplNodeProperties();
            properties.setAllowNoAuth(true);
            properties.setDefaultPdpId("default");

            val result = authenticatorFor(properties).authenticate(setupWith(Unpooled.EMPTY_BUFFER));

            StepVerifier.create(result).expectNextMatches(r -> "default".equals(r.pdpId())).verifyComplete();
            verifyNoInteractions(userLookupService);
        }

        @Test
        @DisplayName("a presented API key is still honored and routed to its tenant")
        void whenValidApiKeyPresentedThenRoutedToTenant() {
            val properties = new SaplNodeProperties();
            properties.setAllowNoAuth(true);
            properties.setAllowApiKeyAuth(true);
            val user = new UserEntry();
            user.setId("prod-client");
            user.setPdpId("production");
            when(userLookupService.findByApiKey("sapl_prod_secret")).thenReturn(Optional.of(user));
            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT,
                    "sapl_prod_secret".toCharArray());

            val result = authenticatorFor(properties).authenticate(setupWith(metadata));

            StepVerifier.create(result).expectNextMatches(r -> "production".equals(r.pdpId())).verifyComplete();
            verify(userLookupService).findByApiKey("sapl_prod_secret");
        }

        @Test
        @DisplayName("a presented but invalid credential is rejected, not silently defaulted")
        void whenInvalidApiKeyPresentedThenRejected() {
            val properties = new SaplNodeProperties();
            properties.setAllowNoAuth(true);
            properties.setAllowApiKeyAuth(true);
            when(userLookupService.findByApiKey("sapl_bad_secret")).thenReturn(Optional.empty());
            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT,
                    "sapl_bad_secret".toCharArray());

            val result = authenticatorFor(properties).authenticate(setupWith(metadata));

            StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        }
    }

    @Nested
    @DisplayName("the authentication cache")
    class AuthenticationCacheTests {

        @Test
        @DisplayName("a client that connects again with the same credentials costs one Argon2 verification")
        void whenSameCredentialsConnectAgainThenVerifiedOnce() {
            val properties = new SaplNodeProperties();
            properties.setAllowBasicAuth(true);
            val user = new UserEntry();
            user.setPdpId("arkham");
            when(userLookupService.verifyBasicCredentials("alice", "secret")).thenReturn(Optional.of(user));
            val authenticator = authenticatorFor(properties);

            for (var connection = 0; connection < 2; connection++) {
                StepVerifier.create(authenticator.authenticate(setupWith(basicMetadata("alice", "secret"))))
                        .expectNextMatches(result -> "arkham".equals(result.pdpId())).verifyComplete();
            }
            verify(userLookupService, times(1)).verifyBasicCredentials("alice", "secret");
        }

        @Test
        @DisplayName("a refused credential is not verified again while its failure lasts")
        void whenRefusedCredentialsConnectAgainThenVerifiedOnce() {
            val properties = new SaplNodeProperties();
            properties.setAllowBasicAuth(true);
            when(userLookupService.verifyBasicCredentials("mallory", "guess")).thenReturn(Optional.empty());
            val authenticator = authenticatorFor(properties);

            for (var connection = 0; connection < 2; connection++) {
                StepVerifier.create(authenticator.authenticate(setupWith(basicMetadata("mallory", "guess"))))
                        .expectError(BadCredentialsException.class).verify();
            }
            verify(userLookupService, times(1)).verifyBasicCredentials("mallory", "guess");
        }
    }

    @Nested
    @DisplayName("JWT expiry (exp claim)")
    class JwtExpiry {

        private static final Instant ISSUED_AT = Instant.parse("2026-02-13T00:00:00Z");

        private Mono<RSocketConnectionAuthenticator.AuthenticationResult> authenticateWith(
                SaplNodeProperties properties, Jwt jwt) {
            properties.setAllowOauth2Auth(true);
            when(jwtDecoder.decode("eyJ.token")).thenReturn(jwt);
            val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, "eyJ.token".toCharArray());
            return authenticatorFor(properties).authenticate(setupWith(metadata));
        }

        private static Jwt jwtWithoutExpiry() {
            return Jwt.withTokenValue("eyJ.token").header("alg", "RS256").claim("sapl_pdp_id", "tenant")
                    .issuedAt(ISSUED_AT).build();
        }

        @Test
        @DisplayName("a JWT without an exp claim is rejected by default (would grant a non-expiring connection)")
        void whenJwtWithoutExpiryAndNotAllowedThenRejected() {
            val result = authenticateWith(new SaplNodeProperties(), jwtWithoutExpiry());

            StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        }

        @Test
        @DisplayName("a JWT without an exp claim is accepted when allow-jwt-without-expiry=true")
        void whenJwtWithoutExpiryAndAllowedThenAccepted() {
            val properties = new SaplNodeProperties();
            properties.getOauth().setAllowJwtWithoutExpiry(true);

            val result = authenticateWith(properties, jwtWithoutExpiry());

            StepVerifier.create(result).expectNextMatches(r -> "tenant".equals(r.pdpId())).verifyComplete();
        }
    }
}
