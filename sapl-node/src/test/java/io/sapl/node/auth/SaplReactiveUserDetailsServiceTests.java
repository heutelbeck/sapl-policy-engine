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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.val;
import reactor.test.StepVerifier;

@DisplayName("SaplReactiveUserDetailsService")
class SaplReactiveUserDetailsServiceTests {

    private UserLookupService              userLookupService;
    private SaplReactiveUserDetailsService service;

    @BeforeEach
    void setUp() {
        userLookupService = mock(UserLookupService.class);
        service           = new SaplReactiveUserDetailsService(userLookupService);
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsernameTests {

        @Test
        @DisplayName("returns UserDetails when user found")
        void whenUserFound_thenReturnsUserDetails() {
            val basic = new BasicCredentials();
            basic.setUsername("admin");
            basic.setSecret("encoded-secret");

            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");
            userEntry.setBasic(basic);

            when(userLookupService.findByBasicUsername("admin")).thenReturn(Optional.of(userEntry));

            StepVerifier.create(service.findByUsername("admin")).assertNext(userDetails -> {
                assertThat(userDetails.getUsername()).isEqualTo("admin");
                assertThat(userDetails.getPassword()).isEqualTo("encoded-secret");
                assertThat(userDetails.getAuthorities()).extracting(auth -> auth.getAuthority())
                        .containsExactly("ROLE_PDP_CLIENT");
            }).verifyComplete();
        }

        @Test
        @DisplayName("throws UsernameNotFoundException when user not found")
        void whenUserNotFound_thenThrowsException() {
            when(userLookupService.findByBasicUsername("unknown")).thenReturn(Optional.empty());

            StepVerifier.create(service.findByUsername("unknown")).expectErrorSatisfies(error -> assertThat(error)
                    .isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("unknown")).verify();
        }

    }

    @Nested
    @DisplayName("resolveSaplUser")
    class ResolveSaplUserTests {

        @Test
        @DisplayName("returns SaplUser when user found")
        void whenUserFound_thenReturnsSaplUser() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");

            val basic = new BasicCredentials();
            basic.setUsername("admin");
            userEntry.setBasic(basic);

            val saplUser = new SaplUser("user-1", "production");

            when(userLookupService.findByBasicUsername("admin")).thenReturn(Optional.of(userEntry));
            when(userLookupService.toSaplUser(userEntry)).thenReturn(saplUser);

            StepVerifier.create(service.resolveSaplUser("admin")).assertNext(user -> {
                assertThat(user.id()).isEqualTo("user-1");
                assertThat(user.pdpId()).isEqualTo("production");
            }).verifyComplete();
        }

        @Test
        @DisplayName("returns empty when user not found")
        void whenUserNotFound_thenReturnsEmpty() {
            when(userLookupService.findByBasicUsername("unknown")).thenReturn(Optional.empty());

            StepVerifier.create(service.resolveSaplUser("unknown")).verifyComplete();
        }

    }

}
