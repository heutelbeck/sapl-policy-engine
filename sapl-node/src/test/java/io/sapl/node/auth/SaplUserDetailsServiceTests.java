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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.val;

@DisplayName("SaplUserDetailsService")
@ExtendWith(MockitoExtension.class)
class SaplUserDetailsServiceTests {

    @Mock
    private UserLookupService userLookupService;

    private SaplUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new SaplUserDetailsService(userLookupService);
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsernameTests {

        @Test
        @DisplayName("returns UserDetails when user found")
        void whenUserFoundThenReturnsUserDetails() {
            val basic = new BasicCredentials();
            basic.setUsername("admin");
            basic.setSecret("encoded-secret");

            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");
            userEntry.setBasic(basic);

            when(userLookupService.findByBasicUsername("admin")).thenReturn(Optional.of(userEntry));

            val userDetails = service.loadUserByUsername("admin");
            assertThat(userDetails.getUsername()).isEqualTo("admin");
            assertThat(userDetails.getPassword()).isEqualTo("encoded-secret");
            assertThat(userDetails.getAuthorities()).extracting(auth -> auth.getAuthority())
                    .containsExactly("ROLE_PDP_CLIENT");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException when user not found")
        void whenUserNotFoundThenThrowsException() {
            when(userLookupService.findByBasicUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                    .isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("resolveSaplUser")
    class ResolveSaplUserTests {

        @Test
        @DisplayName("returns SaplUser when user found")
        void whenUserFoundThenReturnsSaplUser() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");

            val basic = new BasicCredentials();
            basic.setUsername("admin");
            userEntry.setBasic(basic);

            val saplUser = new SaplUser("user-1", "production");

            when(userLookupService.findByBasicUsername("admin")).thenReturn(Optional.of(userEntry));
            when(userLookupService.toSaplUser(userEntry)).thenReturn(saplUser);

            val result = service.resolveSaplUser("admin");
            assertThat(result).isPresent().get().satisfies(user -> {
                assertThat(user.id()).isEqualTo("user-1");
                assertThat(user.pdpId()).isEqualTo("production");
            });
        }

        @Test
        @DisplayName("returns empty when user not found")
        void whenUserNotFoundThenReturnsEmpty() {
            when(userLookupService.findByBasicUsername("unknown")).thenReturn(Optional.empty());

            assertThat(service.resolveSaplUser("unknown")).isEmpty();
        }
    }
}
