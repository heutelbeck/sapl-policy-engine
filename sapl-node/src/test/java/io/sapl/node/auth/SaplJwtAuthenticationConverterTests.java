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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.OAuthConfig;
import lombok.val;

@DisplayName("SaplJwtAuthenticationConverter")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaplJwtAuthenticationConverterTests {

    private static final Instant REFERENCE = Instant.parse("2025-01-01T00:00:00Z");

    @Mock
    private SaplNodeProperties properties;

    private OAuthConfig                    oauthConfig;
    private SaplJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        oauthConfig = new OAuthConfig();
        when(properties.getOauth()).thenReturn(oauthConfig);
        converter = new SaplJwtAuthenticationConverter(properties);
    }

    private Jwt createJwt(String subject, Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).claims(c -> c.putAll(claims))
                .issuedAt(REFERENCE).expiresAt(REFERENCE.plusSeconds(3600)).build();
    }

    @Nested
    @DisplayName("with pdpId claim present")
    class WithPdpIdClaimTests {

        @Test
        @DisplayName("extracts pdpId from JWT claim")
        void whenPdpIdClaimPresentThenExtractsPdpId() {
            val jwt  = createJwt("user-1", Map.of("sapl_pdp_id", "production"));
            val auth = converter.convert(jwt);
            assertThat(auth).isInstanceOf(SaplJwtAuthenticationToken.class);
            val saplAuth = (SaplJwtAuthenticationToken) auth;
            assertThat(saplAuth.getName()).isEqualTo("user-1");
            assertThat(saplAuth.getPdpId()).isEqualTo("production");
        }

        @Test
        @DisplayName("uses custom claim name from config")
        void whenCustomClaimNameThenUsesIt() {
            oauthConfig.setPdpIdClaim("custom_pdp");
            val jwt      = createJwt("user-1", Map.of("custom_pdp", "custom-pdp-id"));
            val saplAuth = (SaplJwtAuthenticationToken) converter.convert(jwt);
            assertThat(saplAuth.getPdpId()).isEqualTo("custom-pdp-id");
        }
    }

    @Nested
    @DisplayName("with pdpId claim missing")
    class WithoutPdpIdClaimTests {

        @Test
        @DisplayName("rejects token when rejectOnMissingPdpId is true")
        void whenRejectOnMissingTrueThenRejectsToken() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(true);
            val jwt = createJwt("user-1", Map.of());

            assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class)
                    .hasMessageContaining("sapl_pdp_id");
        }

        @Test
        @DisplayName("uses default pdpId when rejectOnMissingPdpId is false")
        void whenRejectOnMissingFalseThenUsesDefault() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(false);
            when(properties.getDefaultPdpId()).thenReturn("fallback");
            val jwt      = createJwt("user-1", Map.of());
            val saplAuth = (SaplJwtAuthenticationToken) converter.convert(jwt);
            assertThat(saplAuth.getPdpId()).isEqualTo("fallback");
        }

        @Test
        @DisplayName("treats blank claim as missing")
        void whenBlankClaimThenTreatsAsMissing() {
            when(properties.isRejectOnMissingPdpId()).thenReturn(false);
            when(properties.getDefaultPdpId()).thenReturn("fallback");
            val jwt      = createJwt("user-1", Map.of("sapl_pdp_id", "  "));
            val saplAuth = (SaplJwtAuthenticationToken) converter.convert(jwt);
            assertThat(saplAuth.getPdpId()).isEqualTo("fallback");
        }
    }
}
