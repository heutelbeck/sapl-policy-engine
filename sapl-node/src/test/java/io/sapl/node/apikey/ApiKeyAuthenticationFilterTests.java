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
package io.sapl.node.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;

import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Specifications for {@link ApiKeyAuthenticationFilter}.
 * <p>
 * The filter is responsible for two contracts: when the converter
 * recognises a SAPL API key it must place the authenticated principal
 * into the {@link SecurityContextHolder} so downstream handlers see it,
 * and it must always invoke the rest of the chain so requests without a
 * SAPL API key fall through to other authentication mechanisms.
 */
@DisplayName("ApiKeyAuthenticationFilter")
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTests {

    @Mock
    private AuthenticationConverter converter;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("authenticated request")
    class Authenticated {

        @Test
        @DisplayName("a recognised API key sets the SecurityContext to the manager's authenticated result")
        void whenConverterReturnsTokenThenContextHoldsAuthenticatedPrincipal() throws Exception {
            val saplToken = new SaplAuthenticationToken(new SaplUser("alice", "tenant-a"));
            when(converter.convert(request)).thenReturn(saplToken);
            when(authenticationManager.authenticate(saplToken)).thenReturn(saplToken);
            val filter = new ApiKeyAuthenticationFilter(converter, authenticationManager);

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(saplToken);
            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("no API key")
    class NoApiKey {

        @Test
        @DisplayName("request without an API key leaves SecurityContext untouched and still calls the chain")
        void whenConverterReturnsNullThenContextUntouchedAndChainCalled() throws Exception {
            when(converter.convert(request)).thenReturn(null);
            val filter = new ApiKeyAuthenticationFilter(converter, authenticationManager);

            filter.doFilter(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(authenticationManager, never()).authenticate(org.mockito.ArgumentMatchers.any());
            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("converter throws")
    class ConverterThrows {

        @Test
        @DisplayName("an ApiKeyAuthenticationException from the converter propagates and the chain is NOT invoked")
        void whenConverterThrowsThenChainNotInvoked() throws Exception {
            when(converter.convert(request)).thenThrow(new ApiKeyAuthenticationException("rejected"));
            val filter = new ApiKeyAuthenticationFilter(converter, authenticationManager);

            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(ApiKeyAuthenticationException.class);
            verifyNoChainInvocation();
        }

        private void verifyNoChainInvocation() throws Exception {
            verify(chain, never()).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
