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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("SaplAuthenticationToken")
class SaplAuthenticationTokenTests {

    private static final SaplUser TEST_USER = new SaplUser("user-1", "production");

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates token with user and credentials")
        void whenUserAndCredentials_thenCreatesToken() {
            val credentials = "secret";
            val token       = new SaplAuthenticationToken(TEST_USER, credentials);

            assertThat(token.getPrincipal()).isEqualTo(TEST_USER);
            assertThat(token.getCredentials()).isEqualTo(credentials);
            assertThat(token.isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("creates token without credentials")
        void whenUserOnly_thenCreatesTokenWithNullCredentials() {
            val token = new SaplAuthenticationToken(TEST_USER);

            assertThat(token.getPrincipal()).isEqualTo(TEST_USER);
            assertThat(token.getCredentials()).isNull();
        }

        @Test
        @DisplayName("throws when user is null")
        void whenNullUser_thenThrows() {
            assertThatThrownBy(() -> new SaplAuthenticationToken(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SaplUser must not be null");
        }

    }

    @Nested
    @DisplayName("Authentication interface")
    class AuthenticationInterfaceTests {

        @Test
        @DisplayName("returns correct name from user id")
        void whenGetName_thenReturnsUserId() {
            val token = new SaplAuthenticationToken(TEST_USER);

            assertThat(token.getName()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("returns correct pdpId")
        void whenGetPdpId_thenReturnsPdpId() {
            val token = new SaplAuthenticationToken(TEST_USER);

            assertThat(token.getPdpId()).isEqualTo("production");
        }

        @Test
        @DisplayName("returns PDP_CLIENT authority")
        void whenGetAuthorities_thenReturnsPdpClientRole() {
            val token = new SaplAuthenticationToken(TEST_USER);

            assertThat(token.getAuthorities()).hasSize(1).extracting(auth -> auth.getAuthority())
                    .containsExactly("ROLE_PDP_CLIENT");
        }

        @Test
        @DisplayName("setAuthenticated changes authentication state")
        void whenSetAuthenticated_thenChangesState() {
            val token = new SaplAuthenticationToken(TEST_USER);
            assertThat(token.isAuthenticated()).isTrue();

            token.setAuthenticated(false);

            assertThat(token.isAuthenticated()).isFalse();
        }

    }

}
