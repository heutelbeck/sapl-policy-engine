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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.SaplNodeProperties.BasicCredentials;
import io.sapl.node.SaplNodeProperties.UserEntry;
import lombok.val;

@DisplayName("UserLookupService")
@ExtendWith(MockitoExtension.class)
class UserLookupServiceTests {

    private static final String          ENCODED_API_KEY  = "$argon2id$v=19$m=16384,t=2,p=1$FttHTp38SkUUzUA4cA5Epg$QjzIAdvmNGP0auVlkCDpjrgr2LHeM5ul0BYLr7QKwBM";
    private static final String          RAW_API_KEY      = "sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j";
    private static final PasswordEncoder PASSWORD_ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Mock
    private SaplNodeProperties properties;

    private UserLookupService service;

    @BeforeEach
    void setUp() {
        service = new UserLookupService(properties, PASSWORD_ENCODER);
    }

    @Nested
    @DisplayName("findByBasicUsername")
    class FindByBasicUsernameTests {

        @Test
        @DisplayName("returns user when username matches")
        void whenUsernameMatchesThenReturnsUser() {
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
        void whenUsernameNotFoundThenReturnsEmpty() {
            when(properties.getUsers()).thenReturn(List.of());

            val result = service.findByBasicUsername("unknown");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when username is null")
        void whenUsernameNullThenReturnsEmpty() {
            val result = service.findByBasicUsername(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips users without basic credentials")
        void whenUserHasNoBasicCredentialsThenSkips() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setApiKey(ENCODED_API_KEY);

            when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByBasicUsername("admin");

            assertThat(result).isEmpty();
        }

    }

    @Nested
    @DisplayName("verifyBasicCredentials")
    class VerifyBasicCredentialsTests {

        private UserEntry alice(String encodedSecret) {
            val basic = new BasicCredentials();
            basic.setUsername("alice");
            basic.setSecret(encodedSecret);
            val entry = new UserEntry();
            entry.setId("alice");
            entry.setPdpId("production");
            entry.setBasic(basic);
            return entry;
        }

        @Test
        @DisplayName("returns the user on a correct password")
        void whenPasswordCorrectThenReturnsUser() {
            val real = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
            val sut  = new UserLookupService(properties, real);
            when(properties.getUsers()).thenReturn(List.of(alice(real.encode("secret"))));

            assertThat(sut.verifyBasicCredentials("alice", "secret")).isPresent()
                    .hasValueSatisfying(user -> assertThat(user.getPdpId()).isEqualTo("production"));
        }

        @Test
        @DisplayName("an unknown username and a known username with a wrong password both fail and cost the same single verification, so timing cannot enumerate usernames")
        void whenUnknownVersusWrongPasswordThenSameVerificationCountAndEmpty() {
            val real    = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
            val counter = new CountingPasswordEncoder(real);
            val sut     = new UserLookupService(properties, counter);
            when(properties.getUsers()).thenReturn(List.of(alice(real.encode("secret"))));

            assertThat(sut.verifyBasicCredentials("alice", "wrong")).isEmpty();
            val wrongPassword = counter.matchInvocations.getAndSet(0);

            assertThat(sut.verifyBasicCredentials("ghost", "whatever")).isEmpty();
            val unknownUser = counter.matchInvocations.get();

            assertThat(wrongPassword).isEqualTo(unknownUser).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByApiKey")
    class FindByApiKeyTests {

        @Test
        @DisplayName("returns user via O(1) index when api-key-id is configured")
        void whenApiKeyIdIndexedThenReturnsUserViaFastPath() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setPdpId("staging");
            userEntry.setApiKey(ENCODED_API_KEY);
            userEntry.setApiKeyId("7A7ByyQd6U");

            when(properties.getApiKeyIdIndex()).thenReturn(Map.of("7A7ByyQd6U", userEntry));

            val result = service.findByApiKey(RAW_API_KEY);

            assertThat(result).isPresent().hasValueSatisfying(user -> {
                assertThat(user.getId()).isEqualTo("api-user");
                assertThat(user.getPdpId()).isEqualTo("staging");
            });
        }

        @Test
        @DisplayName("returns empty when the key's api-key-id is not in the index")
        void whenApiKeyIdNotInIndexThenReturnsEmpty() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setPdpId("staging");
            userEntry.setApiKey(ENCODED_API_KEY);
            userEntry.setApiKeyId("DIFFERENT");

            when(properties.getApiKeyIdIndex()).thenReturn(Map.of("DIFFERENT", userEntry));

            val result = service.findByApiKey(RAW_API_KEY);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("an entry without api-key-id is not authenticated, even if its key matches")
        void whenEntryHasNoApiKeyIdThenReturnsEmpty() {
            val userEntry = new UserEntry();
            userEntry.setId("api-user");
            userEntry.setApiKey(ENCODED_API_KEY);

            when(properties.getApiKeyIdIndex()).thenReturn(Map.of());
            // Lookup uses only the api-key-id index, never the user list. An entry
            // absent from the index is ignored even when its key matches.
            lenient().when(properties.getUsers()).thenReturn(List.of(userEntry));

            val result = service.findByApiKey(RAW_API_KEY);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the API key is malformed (no sapl_ prefix)")
        void whenApiKeyMalformedThenReturnsEmpty() {
            val result = service.findByApiKey("wrong-api-key");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when API key is null")
        void whenApiKeyNullThenReturnsEmpty() {
            val result = service.findByApiKey(null);

            assertThat(result).isEmpty();
        }

    }

    @Nested
    @DisplayName("constant-time API-key padding")
    class ApiKeyPaddingTests {

        @Test
        @DisplayName("a configured-but-wrong api-key-id and an unknown api-key-id cost the same number of Argon2 verifications, so timing cannot enumerate configured ids")
        void whenApiKeyIdPresentButWrongVersusAbsentThenSameVerificationCount() {
            val real    = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
            val counter = new CountingPasswordEncoder(real);
            val sut     = new UserLookupService(properties, counter);

            val entry = new UserEntry();
            entry.setApiKeyId("7A7ByyQd6U");
            entry.setApiKey(real.encode("a-different-secret"));
            when(properties.getApiKeyIdIndex()).thenReturn(Map.of("7A7ByyQd6U", entry));
            sut.findByApiKey(RAW_API_KEY);
            val presentButWrong = counter.matchInvocations.getAndSet(0);

            when(properties.getApiKeyIdIndex()).thenReturn(Map.of());
            sut.findByApiKey(RAW_API_KEY);
            val absent = counter.matchInvocations.get();

            assertThat(presentButWrong).isEqualTo(absent).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("toSaplUser")
    class ToSaplUserTests {

        @Test
        @DisplayName("converts UserEntry to SaplUser")
        void whenUserEntryThenConvertsTOSaplUser() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId("production");

            val result = service.toSaplUser(userEntry);

            assertThat(result.id()).isEqualTo("user-1");
            assertThat(result.pdpId()).isEqualTo("production");
        }

        @Test
        @DisplayName("defaults pdpId when null")
        void whenPdpIdNullThenDefaults() {
            val userEntry = new UserEntry();
            userEntry.setId("user-1");
            userEntry.setPdpId(null);

            val result = service.toSaplUser(userEntry);

            assertThat(result.pdpId()).isEqualTo("default");
        }

    }

    /**
     * Delegating encoder that counts {@code matches} invocations so a test can
     * assert that the same number of Argon2 verifications run regardless of
     * whether an api-key-id is configured.
     */
    private static final class CountingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate;
        private final AtomicInteger   matchInvocations = new AtomicInteger();

        private CountingPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchInvocations.incrementAndGet();
            return delegate.matches(rawPassword, encodedPassword);
        }
    }

}
