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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static io.sapl.node.auth.SecurityConfiguration.audienceValidator;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfiguration JWT audience validation")
class SecurityConfigurationAudienceTests {

    private static final String       ISSUER  = "https://idp.example.org/realms/sapl";
    private static final List<String> ALLOWED = List.of("sapl-pdp");

    private static Jwt jwtWithAudience(List<String> audience) {
        val now     = Instant.now();
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("user").issuer(ISSUER).issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (audience != null) {
            builder = builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    @DisplayName("a token whose audience is in the allowlist is accepted")
    void whenAudienceInAllowlistThenAccepted() {
        val validator = audienceValidator(ISSUER, ALLOWED);

        val result = validator.validate(jwtWithAudience(List.of("sapl-pdp")));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token minted for a different resource server (foreign audience) is rejected")
    void whenAudienceNotInAllowlistThenRejected() {
        val validator = audienceValidator(ISSUER, ALLOWED);

        val result = validator.validate(jwtWithAudience(List.of("other-api")));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("a token without an audience claim is rejected when an allowlist is configured")
    void whenAudienceMissingAndAllowlistConfiguredThenRejected() {
        val validator = audienceValidator(ISSUER, ALLOWED);

        val result = validator.validate(jwtWithAudience(null));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("with an empty allowlist the audience is not checked, preserving the default validation")
    void whenAllowlistEmptyThenAudienceNotChecked() {
        val validator = audienceValidator(ISSUER, List.of());

        val result = validator.validate(jwtWithAudience(List.of("other-api")));

        assertThat(result.hasErrors()).isFalse();
    }
}
