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
package io.sapl.spring.tenant;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import reactor.core.publisher.Mono;

/**
 * Default {@link ReactiveTenantResolver}: reads the tenant id from the Reactor
 * Context under
 * {@link #REACTOR_CONTEXT_PDP_ID_KEY}, falling back to
 * {@link ReactivePolicyDecisionPoint#DEFAULT_PDP_ID} if no value
 * is present. The Reactor Context is typically populated by
 * {@link io.sapl.spring.config.PdpIdWebFilter} once per
 * request.
 */
public final class DefaultReactiveTenantResolver implements ReactiveTenantResolver {

    /**
     * Reactor Context key for the PDP identifier. Cooperates with
     * {@link io.sapl.spring.config.PdpIdWebFilter}, which
     * writes the authenticated tenant's id under this key.
     */
    public static final String REACTOR_CONTEXT_PDP_ID_KEY = "sapl.pdp.id";

    @Override
    public Mono<String> resolve() {
        return Mono.deferContextual(ctx -> Mono
                .just(ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, ReactivePolicyDecisionPoint.DEFAULT_PDP_ID)));
    }
}
