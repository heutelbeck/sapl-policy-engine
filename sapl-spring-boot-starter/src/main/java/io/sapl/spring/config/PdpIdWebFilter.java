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
package io.sapl.spring.config;

import static io.sapl.spring.tenant.DefaultReactiveTenantResolver.REACTOR_CONTEXT_PDP_ID_KEY;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.spring.tenant.DefaultReactiveTenantResolver;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * WebFilter that resolves the authenticated user's PDP id once per request and
 * propagates it through the Reactor
 * Context. Cooperates with {@link DefaultReactiveTenantResolver}, which reads
 * the same key on the consumer side. The
 * PDP itself never reads the Reactor Context. Tenant resolution happens here,
 * in application infrastructure.
 * <p>
 * Uses the provided {@link PdpIdAuthenticationExtractor} to extract the id from
 * the current authentication. The
 * extracted id is written under
 * {@link DefaultReactiveTenantResolver#REACTOR_CONTEXT_PDP_ID_KEY}; a missing
 * authentication or empty extraction falls back to
 * {@link ReactivePolicyDecisionPoint#DEFAULT_PDP_ID}.
 * {@link ReactiveTenantResolver} implementations downstream consume the value.
 */
@RequiredArgsConstructor
public class PdpIdWebFilter implements WebFilter {

    private final PdpIdAuthenticationExtractor extractor;

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
            var authentication = securityContext.getAuthentication();
            if (authentication == null) {
                return Mono.just(ReactivePolicyDecisionPoint.DEFAULT_PDP_ID);
            }
            return extractor.extractPdpId(authentication).defaultIfEmpty(ReactivePolicyDecisionPoint.DEFAULT_PDP_ID);
        }).defaultIfEmpty(ReactivePolicyDecisionPoint.DEFAULT_PDP_ID)
                .flatMap(pdpId -> chain.filter(exchange).contextWrite(Context.of(REACTOR_CONTEXT_PDP_ID_KEY, pdpId)));
    }

}
