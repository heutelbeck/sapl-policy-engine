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
package io.sapl.spring.pep.http.reactive;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.HttpDenialSignal;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Reactive {@link ServerAccessDeniedHandler} that fires
 * {@link HttpDenialSignal} against the {@link EnforcementPlan} published by
 * {@link ReactiveSaplAuthorizationManager} so policy obligations can shape
 * the deny response (status, headers, body, redirect).
 * <p>
 * Falls back to a Spring default 403 response when no plan is present,
 * when no handler claims the denial, or when an obligation handler fails.
 * Otherwise commits the buffered response shaped by the handlers.
 */
public class SaplServerAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ServerAccessDeniedHandler fallback = new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN);

    @Override
    public @NonNull Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull AccessDeniedException denied) {
        val plan = exchange.<EnforcementPlan>getAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE);
        if (plan == null || plan.entriesFor(HttpDenialSignal.TYPE).isEmpty()) {
            return fallback.handle(exchange, denied);
        }
        val mutableResponse = new ReactiveMutableHttpResponse(exchange.getResponse());
        val result          = plan.execute(HttpDenialSignal.of(mutableResponse), false);
        if (result.failureState() || !mutableResponse.isModified()) {
            return fallback.handle(exchange, denied);
        }
        return mutableResponse.commit();
    }
}
