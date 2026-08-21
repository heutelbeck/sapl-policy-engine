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
package io.sapl.attributeapi.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.sapl.attributeapi.auth.AttributeApiUserDetails;

class PdpIdJwtAuthenticationConverterTests {
    private static final String                   CLAIM_NAME = "pdpId";
    private final PdpIdJwtAuthenticationConverter converter  = new PdpIdJwtAuthenticationConverter(CLAIM_NAME);

    @Test
    @DisplayName("A JWT with the pdpId claim is converted into an authenticated token")
    void whenClaimPresentThenAuthenticationTokenIsGenerated() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user").claim(CLAIM_NAME, "aPdpId")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        var authentication = converter.convert(jwt);
        var principal      = (AttributeApiUserDetails) authentication.getPrincipal();

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(principal.getUsername()).isEqualTo("user");
        assertThat(principal.getPdpId()).isEqualTo("aPdpId");
    }

    @Test
    @DisplayName("reject if a JWT is missing the configured pdpId claim")
    void whenClaimMissingThenInvalidBearerTokenExceptionThrown() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user").claim("other", "value")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName("reject if a JWT has a blank pdpId claim")
    void whenClaimBlankThenInvalidBearerTokenExceptionThrown() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user-1").claim(CLAIM_NAME, "  ")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
    }
}
