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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.OAuthConfig;
import lombok.val;
import reactor.test.StepVerifier;

@DisplayName("SaplJwtAuthenticationConverter")
class SaplJwtAuthenticationConverterTests {

    private SaplNodeProperties             properties;
    private OAuthConfig                    oauthConfig;
    private SaplJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        properties  = mock(SaplNodeProperties.class);
        oauthConfig = new OAuthConfig();
        when(properties.getOauth()).thenReturn(oauthConfig);
        converter = new SaplJwtAuthenticationConverter(properties);
    }

    private Jwt createJwt(String subject, Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).claims(c -> c.putAll(claims))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    @Nested
    @DisplayName("with pdpId claim present")
    class WithPdpIdClaimTests {

        @Test
        @DisplayName("extracts pdpId from JWT claim")
        void whenPdpIdClaimPresent_thenExtractsPdpId() {
            val jwt = createJwt("user-1", Map.of("sapl_pdp_id", "production"));

            StepVerifier.create(converter.convert(jwt)).assertNext(auth -> {
                assertThat(auth).isInstanceOf(SaplJwtAuthenticationToken.class);
                val saplAuth = (SaplJwtAuthenticationToken) auth;
                assertThat(saplAuth.getName()).isEqualTo("user-1");
                assertThat(saplAuth.getPdpId()).isEqualTo("production");
            }).verifyComplete();
        }

        @Test
        @DisplayName("uses custom claim name from config")
        void whenCustomClaimName_thenUsesIt() {
            oauthConfig.setPdpIdClaim("custom_pdp");
            val jwt = createJwt("user-1", Map.of("custom_pdp", "custom-pdp-id"));

            StepVerifier.create(converter.convert(jwt)).assertNext(auth -> {
                val saplAuth = (SaplJwtAuthenticationToken) auth;
                assertThat(saplAuth.getPdpId()).isEqualTo("custom-pdp-id");
            }).verifyComplete();
        }

    }

    @Nested
    @DisplayName("with pdpId claim missing")
    class WithoutPdpIdClaimTests {

        @Test
        @DisplayName("rejects token when rejectOnMissingPdpId is true")
        void whenRejectOnMissingTrue_thenRejectsToken() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(true);
            val jwt = createJwt("user-1", Map.of());

            StepVerifier
                    .create(converter.convert(jwt)).expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(InvalidBearerTokenException.class).hasMessageContaining("sapl_pdp_id"))
                    .verify();
        }

        @Test
        @DisplayName("uses default pdpId when rejectOnMissingPdpId is false")
        void whenRejectOnMissingFalse_thenUsesDefault() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(false);
            when(properties.getDefaultPdpId()).thenReturn("fallback");
            val jwt = createJwt("user-1", Map.of());

            StepVerifier.create(converter.convert(jwt)).assertNext(auth -> {
                val saplAuth = (SaplJwtAuthenticationToken) auth;
                assertThat(saplAuth.getPdpId()).isEqualTo("fallback");
            }).verifyComplete();
        }

        @Test
        @DisplayName("treats blank claim as missing")
        void whenBlankClaim_thenTreatsAsMissing() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(false);
            when(properties.getDefaultPdpId()).thenReturn("fallback");
            val jwt = createJwt("user-1", Map.of("sapl_pdp_id", "  "));

            StepVerifier.create(converter.convert(jwt)).assertNext(auth -> {
                val saplAuth = (SaplJwtAuthenticationToken) auth;
                assertThat(saplAuth.getPdpId()).isEqualTo("fallback");
            }).verifyComplete();
        }

    }

}
