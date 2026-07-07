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

import static io.sapl.node.auth.SecurityConfiguration.jwtValidator;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.val;

/**
 * Pins the opt-in scope gate: with required scopes configured, a valid token
 * is no longer a blanket grant, it must carry a scope minted for PDP access.
 * Without the property, behavior is unchanged.
 */
@DisplayName("SecurityConfiguration required-scopes gate")
class SecurityConfigurationScopeTests {

    private static final String       ISSUER   = "https://idp.example.org/realms/sapl";
    private static final List<String> REQUIRED = List.of("sapl:pdp");

    private static Jwt.Builder baseJwt() {
        val now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user").issuer(ISSUER).issuedAt(now)
                .expiresAt(now.plusSeconds(300));
    }

    @Test
    @DisplayName("a token carrying a required scope in the space-delimited scope claim is accepted")
    void whenScopeClaimCarriesRequiredScopeThenAccepted() {
        val validator = jwtValidator(ISSUER, List.of(), REQUIRED, false);
        val jwt       = baseJwt().claim("scope", "openid profile sapl:pdp").build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token carrying a required scope in the scp array claim is accepted")
    void whenScpClaimCarriesRequiredScopeThenAccepted() {
        val validator = jwtValidator(ISSUER, List.of(), REQUIRED, false);
        val jwt       = baseJwt().claim("scp", List.of("openid", "sapl:pdp")).build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token with disjoint scopes is rejected")
    void whenScopesDisjointThenRejected() {
        val validator = jwtValidator(ISSUER, List.of(), REQUIRED, false);
        val jwt       = baseJwt().claim("scope", "openid profile email").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("a token without any scope claim is rejected when scopes are required")
    void whenScopeClaimMissingThenRejected() {
        val validator = jwtValidator(ISSUER, List.of(), REQUIRED, false);
        val jwt       = baseJwt().build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("with no required scopes configured a token without scopes is accepted, preserving prior behavior")
    void whenNoRequiredScopesThenScopeNotChecked() {
        val validator = jwtValidator(ISSUER, List.of(), List.of(), false);
        val jwt       = baseJwt().build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("audience and scope gates compose, both must pass")
    void whenAudienceAndScopeConfiguredThenBothEnforced() {
        val validator     = jwtValidator(ISSUER, List.of("sapl-pdp"), REQUIRED, false);
        val onlyAudience  = baseJwt().audience(List.of("sapl-pdp")).claim("scope", "openid").build();
        val onlyScope     = baseJwt().audience(List.of("other-api")).claim("scope", "sapl:pdp").build();
        val bothSatisfied = baseJwt().audience(List.of("sapl-pdp")).claim("scope", "sapl:pdp").build();
        assertThat(validator.validate(onlyAudience).hasErrors()).isTrue();
        assertThat(validator.validate(onlyScope).hasErrors()).isTrue();
        assertThat(validator.validate(bothSatisfied).hasErrors()).isFalse();
    }
}
