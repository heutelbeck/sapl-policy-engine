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

import io.netty.buffer.ByteBufAllocator;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.metadata.AuthMetadataCodec;
import io.sapl.node.SaplNodeProperties;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RSocketSecurityConfiguration honors auth-mode flags")
class RSocketSecurityConfigurationTests {

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RSocketConnectionAuthenticator authenticatorFor(SaplNodeProperties properties) {
        return new RSocketSecurityConfiguration(properties, userLookupService, passwordEncoder, null)
                .rsocketConnectionAuthenticator(true);
    }

    private static ConnectionSetupPayload setupWith(io.netty.buffer.ByteBuf metadata) {
        val setup = org.mockito.Mockito.mock(ConnectionSetupPayload.class);
        when(setup.metadata()).thenReturn(metadata);
        return setup;
    }

    @Test
    @DisplayName("basic credentials are rejected without a user-store lookup when basic auth is disabled")
    void whenBasicAuthDisabledThenSimpleCredentialsRejectedWithoutLookup() {
        val properties = new SaplNodeProperties();
        properties.setAllowBasicAuth(false);
        val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, "alice".toCharArray(),
                "secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verifyNoInteractions(userLookupService);
    }

    @Test
    @DisplayName("API key is rejected without a user-store lookup when API key auth is disabled")
    void whenApiKeyAuthDisabledThenApiKeyRejectedWithoutLookup() {
        val properties = new SaplNodeProperties();
        properties.setAllowApiKeyAuth(false);
        val metadata = AuthMetadataCodec.encodeBearerMetadata(ByteBufAllocator.DEFAULT, "sapl_secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verifyNoInteractions(userLookupService);
    }

    @Test
    @DisplayName("basic credentials are validated against the user store when basic auth is enabled")
    void whenBasicAuthEnabledThenUserStoreIsConsulted() {
        val properties = new SaplNodeProperties();
        properties.setAllowBasicAuth(true);
        when(userLookupService.findByBasicUsername("alice")).thenReturn(Optional.empty());
        val metadata = AuthMetadataCodec.encodeSimpleMetadata(ByteBufAllocator.DEFAULT, "alice".toCharArray(),
                "secret".toCharArray());
        val result   = authenticatorFor(properties).authenticate(setupWith(metadata));

        StepVerifier.create(result).expectError(BadCredentialsException.class).verify();
        verify(userLookupService).findByBasicUsername("alice");
    }
}
