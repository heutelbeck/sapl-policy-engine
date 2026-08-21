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
package io.sapl.attributeapi.auth.apikey;

import io.sapl.attributeapi.auth.AttributeApiSecurityProperties;
import io.sapl.attributeapi.auth.AttributeApiSecurityProperties.UserEntry;
import io.sapl.attributeapi.auth.apikey.ApiKeyAuthenticationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationServiceTests {
    private static final String RAW_KEY = "sapl_Gd405gc3Ri_e4lrobcCGJFVhfR81kZ7wh0GC9ch9DiD";

    private AttributeApiSecurityProperties properties;
    private PasswordEncoder                realEncoder;

    @BeforeEach
    void setUp() {
        realEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String encodedKey = realEncoder.encode(RAW_KEY);

        var user = new UserEntry();
        user.setId("sapl-api-user-01");
        user.setPdpId("service-api-01");
        user.setApiKeyId("Gd405gc3Ri");
        user.setApiKey(encodedKey);

        properties = new AttributeApiSecurityProperties();
        properties.setUsers(List.of(user));
        properties.afterPropertiesSet();
    }

    @Nested
    class ApiKeyFinderTests {
        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidKeys")
        @DisplayName("An invalid key should return nothing")
        void whenKeyIsInvalidThenReturnEmpty(String key) throws Exception {
            var service = new ApiKeyAuthenticationService(properties, realEncoder);
            assertThat(service.findByApiKey(key)).isEmpty();
        }

        private static Stream<Arguments> invalidKeys() {
            return Stream.of(Arguments.of(Named.of("null key", null)),
                    Arguments.of(Named.of("missing sapl_ prefix", "justAnApiKey_butWrong")),
                    Arguments.of(Named.of("wrong secret for a known key id", "sapl_Gd405gc3Ri_wrongSecret")));
        }

        @Test
        @DisplayName("An empty key should return nothing")
        void whenKeyIdIsUnknownThenDummyVerficationRunsOnce() throws Exception {
            // Spy wrapper to see what calls are made
            var fakeEncoder = spy(realEncoder);
            var service     = new ApiKeyAuthenticationService(properties, fakeEncoder);

            assertThat(service.findByApiKey("sapl_keyIsInvalid_InConfiguration")).isEmpty();
            verify(fakeEncoder, times(1)).matches(any(), any()); // exactly one call happened
        }

        @Test
        @DisplayName("A valid key returns a valid user")
        void whenKeyIsValidThenReturnTheUser() throws Exception {
            var service = new ApiKeyAuthenticationService(properties, realEncoder);
            var result  = service.findByApiKey(RAW_KEY);

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("sapl-api-user-01");
            assertThat(result.get().getPdpId()).isEqualTo("service-api-01");
        }

        @Test
        @DisplayName("A cache hit avoids a second argon2 verify")
        void whenCacheHitThenSecondArgon2VerifyIsAvoided() throws Exception {
            var fakeEncoder = spy(realEncoder);
            var service     = new ApiKeyAuthenticationService(properties, fakeEncoder);

            service.findByApiKey(RAW_KEY);
            service.findByApiKey(RAW_KEY);

            verify(fakeEncoder, times(1)).matches(any(), any());
        }
    }
}
