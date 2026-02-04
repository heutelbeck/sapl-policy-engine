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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.val;

@DisplayName("UserLookupService")
class UserLookupServiceTests {

    private static final String          ENCODED_API_KEY  = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String          RAW_API_KEY      = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final PasswordEncoder PASSWORD_ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    private SaplNodeProperties properties;
    private UserLookupService  service;

    @BeforeEach
    void setUp() {
        properties = mock(SaplNodeProperties.class);
        service    = new UserLookupService(properties, PASSWORD_ENCODER);
    }

    @Nested
    @DisplayName("findByBasicUsername")
    class FindByBasicUsernameTests {

        @Test
        @DisplayName("returns user when username matches")
        void whenUsernameMatches_thenReturnsUser() {
            val basic = new BasicCredentials();
            basic.setUsername("admin");
            basic.setSecret("encoded-secret");

            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");
            userEntry.setBasic(basic);

            when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByBasicUsername("admin");

            assertThat(result).isPresent().hasValueSatisfying(user -> {
                assertThat(user.getId()).isEqualTo("user-1");
                assertThat(user.getPdpId()).isEqualTo("production");
            });
        }

        @Test
        @DisplayName("returns empty when username not found")
        void whenUsernameNotFound_thenReturnsEmpty() {
            when(properties.getUsers()).thenReturn(List.of());

            val result = service.findByBasicUsername("unknown");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when username is null")
        void whenUsernameNull_thenReturnsEmpty() {
            val result = service.findByBasicUsername(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips users without basic credentials")
        void whenUserHasNoBasicCredentials_thenSkips() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setApiKey(ENCODED_API_KEY);

            when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByBasicUsername("admin");

            assertThat(result).isEmpty();
        }

    }

    @Nested
    @DisplayName("findByApiKey")
    class FindByApiKeyTests {

        @Test
        @DisplayName("returns user when API key matches")
        void whenApiKeyMatches_thenReturnsUser() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setPdpId("staging");
            userEntry.setApiKey(ENCODED_API_KEY);

            when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByApiKey(RAW_API_KEY);

            assertThat(result).isPresent().hasValueSatisfying(user -> {
                assertThat(user.getId()).isEqualTo("api-user");
                assertThat(user.getPdpId()).isEqualTo("staging");
            });
        }

        @Test
        @DisplayName("returns empty when API key not found")
        void whenApiKeyNotFound_thenReturnsEmpty() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setApiKey(ENCODED_API_KEY);

            when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByApiKey("wrong-api-key");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when API key is null")
        void whenApiKeyNull_thenReturnsEmpty() {
            val result = service.findByApiKey(null);

            assertThat(result).isEmpty();
        }

    }

    @Nested
    @DisplayName("toSaplUser")
    class ToSaplUserTests {

        @Test
        @DisplayName("converts UserEntry to SaplUser")
        void whenUserEntry_thenConvertsTOSaplUser() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");

            val result = service.toSaplUser(userEntry);

            assertThat(result.id()).isEqualTo("user-1");
            assertThat(result.pdpId()).isEqualTo("production");
        }

        @Test
        @DisplayName("defaults pdpId when null")
        void whenPdpIdNull_thenDefaults() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId(null);

            val result = service.toSaplUser(userEntry);

            assertThat(result.pdpId()).isEqualTo("default");
        }

    }

}
