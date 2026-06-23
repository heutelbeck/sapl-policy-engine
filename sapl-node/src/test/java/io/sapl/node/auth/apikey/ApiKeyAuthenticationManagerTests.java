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
package io.sapl.node.auth.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import lombok.val;

/**
 * Specifications for {@link ApiKeyAuthenticationManager}.
 * <p>
 * The manager is the second stage of API key authentication. The first
 * stage ({@link ApiKeyService}) verifies the key against the user store
 * and produces a {@link SaplAuthenticationToken}. The manager runs
 * downstream and is the component Spring Security consults to mark the
 * authentication as trusted. The contract is therefore: only tokens of
 * the SAPL type the converter produces should be authenticated; any
 * other Authentication shape reaching this manager indicates a misrouted
 * filter chain and must not be granted authenticated status.
 */
@DisplayName("ApiKeyAuthenticationManager")
class ApiKeyAuthenticationManagerTests {

    private final ApiKeyAuthenticationManager manager = new ApiKeyAuthenticationManager();

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("a SaplAuthenticationToken is returned authenticated")
        void whenSaplAuthenticationTokenThenAuthenticated() {
            val token = new SaplAuthenticationToken(new SaplUser("alice", "tenant-a"), "raw-api-key");
            // Start unauthenticated so the assertion proves authenticate() did the work
            // rather than echoing the constructor's already-authenticated state.
            token.setAuthenticated(false);

            val result = manager.authenticate(token);

            assertThat(result).isNotNull().satisfies(r -> assertThat(r.isAuthenticated()).isTrue());
        }
    }

    @Nested
    @DisplayName("foreign Authentication shapes")
    class ForeignTokenRejection {

        @Test
        @DisplayName("a non-SAPL Authentication with non-null credentials must NOT be marked authenticated")
        void whenNonSaplAuthenticationThenNotAuthenticated() {
            val foreign = new ForeignAuthenticationToken("eve", "anything");

            val result = manager.authenticate(foreign);

            assertThat(result).satisfies(r -> assertThat(r.isAuthenticated()).isFalse());
        }
    }

    @Nested
    @DisplayName("null and credential-less inputs")
    class NullAndCredentialless {

        @Test
        @DisplayName("null Authentication is returned untouched")
        void whenNullAuthenticationThenReturnedUntouched() {
            assertThat(manager.authenticate(null)).isNull();
        }

        @Test
        @DisplayName("a SaplAuthenticationToken without credentials still ends up authenticated")
        void whenSaplTokenWithNullCredentialsThenStillAuthenticated() {
            val token = new SaplAuthenticationToken(new SaplUser("bob", "tenant-b"));

            val result = manager.authenticate(token);

            assertThat(result).isNotNull().satisfies(r -> assertThat(r.isAuthenticated()).isTrue());
        }
    }

    private static final class ForeignAuthenticationToken extends AbstractAuthenticationToken {

        private final transient Object principal;
        private final transient Object credentials;

        ForeignAuthenticationToken(Object principal, Object credentials) {
            super(List.of());
            this.principal   = principal;
            this.credentials = credentials;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public Object getCredentials() {
            return credentials;
        }
    }
}
