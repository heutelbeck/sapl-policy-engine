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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.server.WebFilterChain;

import lombok.val;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@DisplayName("SaplUserContextFilter")
class SaplUserContextFilterTests {

    private SaplReactiveUserDetailsService userDetailsService;
    private SaplUserContextFilter          filter;

    @BeforeEach
    void setUp() {
        userDetailsService = mock(SaplReactiveUserDetailsService.class);
        filter             = new SaplUserContextFilter(userDetailsService);
    }

    @Nested
    @DisplayName("pdpId propagation")
    class PdpIdPropagationTests {

        @Test
        @DisplayName("propagates pdpId from SaplAuthenticationToken to context")
        void whenSaplAuthenticationToken_thenPropagatesPdpId() {
            val saplUser        = new SaplUser("user-1", "production");
            val authentication  = new SaplAuthenticationToken(saplUser);
            val securityContext = new SecurityContextImpl(authentication);

            val capturedPdpId = new AtomicReference<String>();
            val exchange      = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
            val chain         = createCapturingChain(capturedPdpId);

            filter.filter(exchange, chain).contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)))
                    .block();

            assertThat(capturedPdpId.get()).isEqualTo("production");
        }

        @Test
        @DisplayName("resolves pdpId from UserDetails via userDetailsService")
        void whenUserDetails_thenResolvesPdpIdViaService() {
            val userDetails     = User.builder().username("admin").password("pass").roles("USER").build();
            val authentication  = new PreAuthenticatedAuthenticationToken(userDetails, null);
            val securityContext = new SecurityContextImpl(authentication);
            val saplUser        = new SaplUser("user-1", "staging");

            when(userDetailsService.resolveSaplUser("admin")).thenReturn(Mono.just(saplUser));

            val capturedPdpId = new AtomicReference<String>();
            val exchange      = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
            val chain         = createCapturingChain(capturedPdpId);

            filter.filter(exchange, chain).contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)))
                    .block();

            assertThat(capturedPdpId.get()).isEqualTo("staging");
        }

        @Test
        @DisplayName("uses default pdpId when no authentication present")
        void whenNoAuthentication_thenUsesDefaultPdpId() {
            val securityContext = new SecurityContextImpl();

            val capturedPdpId = new AtomicReference<String>();
            val exchange      = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
            val chain         = createCapturingChain(capturedPdpId);

            filter.filter(exchange, chain).contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)))
                    .block();

            assertThat(capturedPdpId.get()).isEqualTo("default");
        }

        @Test
        @DisplayName("uses default pdpId when user not found")
        void whenUserNotFound_thenUsesDefaultPdpId() {
            val userDetails     = User.builder().username("unknown").password("pass").roles("USER").build();
            val authentication  = new PreAuthenticatedAuthenticationToken(userDetails, null);
            val securityContext = new SecurityContextImpl(authentication);

            when(userDetailsService.resolveSaplUser("unknown")).thenReturn(Mono.empty());

            val capturedPdpId = new AtomicReference<String>();
            val exchange      = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
            val chain         = createCapturingChain(capturedPdpId);

            filter.filter(exchange, chain).contextWrite(Context.of(SecurityContext.class, Mono.just(securityContext)))
                    .block();

            assertThat(capturedPdpId.get()).isEqualTo("default");
        }

    }

    private WebFilterChain createCapturingChain(AtomicReference<String> capturedPdpId) {
        return exchange -> Mono.deferContextual(ctx -> {
            ctx.getOrEmpty(SaplUserContextFilter.PDP_ID_KEY).ifPresent(pdpId -> capturedPdpId.set((String) pdpId));
            return Mono.empty();
        });
    }

}
