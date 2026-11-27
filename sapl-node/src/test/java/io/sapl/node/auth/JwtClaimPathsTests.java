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

import static io.sapl.node.auth.JwtClaimPaths.requireValidPath;
import static io.sapl.node.auth.JwtClaimPaths.resolveStringClaim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.val;

@DisplayName("JwtClaimPaths")
class JwtClaimPathsTests {

    private static Jwt jwtWithClaim(String name, Object value) {
        val now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).claim(name, value).build();
    }

    @Test
    @DisplayName("a flat top-level claim resolves as before")
    void whenTopLevelClaimThenResolved() {
        val jwt = jwtWithClaim("sapl_pdp_id", "tenant-a");
        assertThat(resolveStringClaim(jwt, "sapl_pdp_id")).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("a dot-separated path resolves into nested claim objects")
    void whenNestedPathThenResolved() {
        val jwt = jwtWithClaim("resource_access", Map.of("sapl", Map.of("tenant", "tenant-b")));
        assertThat(resolveStringClaim(jwt, "resource_access.sapl.tenant")).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("a top-level claim whose name contains dots wins over path interpretation")
    void whenDottedTopLevelClaimThenExactNameWins() {
        val now = Instant.now();
        val jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).claim("custom.tenant", "flat-value")
                .claim("custom", Map.of("tenant", "nested-value")).build();
        assertThat(resolveStringClaim(jwt, "custom.tenant")).isEqualTo("flat-value");
    }

    @Test
    @DisplayName("scalar non-string leaves resolve to their string form")
    void whenScalarLeafThenStringForm() {
        val numericJwt = jwtWithClaim("org", Map.of("tenant", 42));
        val booleanJwt = jwtWithClaim("org", Map.of("flag", true));
        assertThat(resolveStringClaim(numericJwt, "org.tenant")).isEqualTo("42");
        assertThat(resolveStringClaim(booleanJwt, "org.flag")).isEqualTo("true");
    }

    @Test
    @DisplayName("missing segments and structured leaves resolve to null")
    void whenPathDoesNotResolveToScalarThenNull() {
        val jwt = jwtWithClaim("resource_access", Map.of("sapl", Map.of("tenant", "tenant-b")));
        assertThat(resolveStringClaim(jwt, "resource_access.other.tenant")).isNull();
        assertThat(resolveStringClaim(jwt, "resource_access.sapl")).isNull();
        assertThat(resolveStringClaim(jwt, "resource_access.sapl.tenant.deeper")).isNull();
        assertThat(resolveStringClaim(jwt, "absent")).isNull();
    }

    @Test
    @DisplayName("a list in the path is not traversed and resolves to null")
    void whenListInPathThenNull() {
        val jwt = jwtWithClaim("groups", List.of(Map.of("tenant", "tenant-c")));
        assertThat(resolveStringClaim(jwt, "groups.tenant")).isNull();
    }

    @ParameterizedTest(name = "path ''{0}'' is rejected")
    @NullSource
    @ValueSource(strings = { "", "   ", ".", ".tenant", "tenant.", "a..b", "a. .b" })
    @DisplayName("malformed claim paths are rejected by validation")
    void whenPathMalformedThenValidationRejects(String path) {
        assertThatThrownBy(() -> requireValidPath(path)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Claim path");
    }

    @ParameterizedTest(name = "path ''{0}'' is accepted")
    @ValueSource(strings = { "sapl_pdp_id", "a.b", "resource_access.sapl.tenant" })
    @DisplayName("well-formed claim names and paths pass validation")
    void whenPathWellFormedThenValidationAccepts(String path) {
        assertThatCode(() -> requireValidPath(path)).doesNotThrowAnyException();
    }
}
