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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.apikey.ApiKeyService;
import io.sapl.node.boot.SaplStartupConfigurationException;
import lombok.val;

/**
 * Pins the fail-fast contract of the OAuth access configuration: a malformed
 * pdp-id-claim path or a blank required scope refuses startup with an
 * operator-facing message instead of silently rejecting every token at
 * runtime.
 */
@DisplayName("SecurityConfiguration OAuth validation")
@ExtendWith(MockitoExtension.class)
class SecurityConfigurationOauthValidationTests {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private UserLookupService userLookupService;

    private SecurityConfiguration configurationWith(String pdpIdClaim, List<String> requiredScopes) {
        val properties = new SaplNodeProperties();
        properties.getOauth().setPdpIdClaim(pdpIdClaim);
        properties.getOauth().setRequiredScopes(requiredScopes);
        return new SecurityConfiguration(apiKeyService, properties, userLookupService);
    }

    @Test
    @DisplayName("a well-formed claim path and non-blank scopes pass validation")
    void whenConfigurationWellFormedThenStartupPasses() {
        val sut = configurationWith("resource_access.sapl.tenant", List.of("sapl:pdp"));
        assertThatCode(sut::validateOauthAccessConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a malformed pdp-id-claim path refuses startup with an operator message")
    void whenClaimPathMalformedThenStartupRefused() {
        val sut = configurationWith("resource_access..tenant", List.of());
        assertThatThrownBy(sut::validateOauthAccessConfiguration).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessageContaining("pdp-id-claim");
    }

    @Test
    @DisplayName("a blank required scope refuses startup with an operator message")
    void whenRequiredScopeBlankThenStartupRefused() {
        val sut = configurationWith("sapl_pdp_id", List.of("sapl:pdp", " "));
        assertThatThrownBy(sut::validateOauthAccessConfiguration).isInstanceOf(SaplStartupConfigurationException.class)
                .hasMessageContaining("required-scopes");
    }
}
