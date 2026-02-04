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

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * WebFilter that propagates the authenticated user's pdpId into the Reactor
 * Context.
 * <p>
 * This allows downstream components (like DynamicPolicyDecisionPoint) to access
 * the
 * pdpId without explicit parameter passing.
 */
@RequiredArgsConstructor
public class SaplUserContextFilter implements WebFilter {

    /**
     * Context key for the PDP ID.
     */
    public static final String PDP_ID_KEY = "sapl.pdp.id";

    private static final String DEFAULT_PDP_ID = "default";

    private final SaplReactiveUserDetailsService userDetailsService;

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
            val authentication = securityContext.getAuthentication();
            if (authentication == null) {
                return Mono.just(DEFAULT_PDP_ID);
            }
            return extractPdpId(authentication).defaultIfEmpty(DEFAULT_PDP_ID);
        }).defaultIfEmpty(DEFAULT_PDP_ID)
                .flatMap(pdpId -> chain.filter(exchange).contextWrite(Context.of(PDP_ID_KEY, pdpId)));
    }

    private Mono<String> extractPdpId(Authentication authentication) {
        if (authentication instanceof SaplAuthenticationToken saplAuth) {
            return Mono.just(saplAuth.getPdpId());
        }

        val principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetailsService.resolveSaplUser(userDetails.getUsername()).map(SaplUser::pdpId);
        }

        return Mono.just(DEFAULT_PDP_ID);
    }

}
