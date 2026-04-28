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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.HttpRequestMutationSignal;
import io.sapl.spring.pep.constraints.Signal.HttpResponseSignal;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Reactive mirror of {@link io.sapl.spring.pep.http.servlet.SaplHttpPepFilter}.
 * Reads the {@link EnforcementPlan} published by
 * {@link ReactiveSaplAuthorizationManager} on the exchange attribute, then
 * fires the HTTP request-mutation and response signals around the
 * downstream chain.
 * <p>
 * Wraps only when the active plan schedules at least one handler at the
 * corresponding signal. The common case (a permit decision with no HTTP
 * signal handlers) runs against the raw exchange with no extra copy.
 * Response wrapping captures the controller body in memory and re-emits
 * it on commit; routes that intentionally stream large payloads should
 * not register response-signal handlers.
 * <p>
 * Obligation handler failures propagate as {@link AccessDeniedException},
 * which the configured
 * {@link io.sapl.spring.pep.http.reactive.SaplServerAccessDeniedHandler}
 * converts into a deny response.
 */
public class SaplHttpPepWebFilter implements WebFilter {

    private static final String ERROR_REQUEST_MUTATION_OBLIGATION_FAILED = "Access Denied. An HTTP request-mutation obligation handler failed.";
    private static final String ERROR_RESPONSE_OBLIGATION_FAILED         = "Access Denied. An HTTP response obligation handler failed.";

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        val plan = exchange.<EnforcementPlan>getAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE);
        if (plan == null) {
            return chain.filter(exchange);
        }

        val requestHandlersScheduled  = !plan.entriesFor(HttpRequestMutationSignal.SIGNAL_TYPE).isEmpty();
        val responseHandlersScheduled = !plan.entriesFor(HttpResponseSignal.SIGNAL_TYPE).isEmpty();
        if (!requestHandlersScheduled && !responseHandlersScheduled) {
            return chain.filter(exchange);
        }

        var forwarded = exchange;
        if (requestHandlersScheduled) {
            val mutableRequest = new ReactiveMutableHttpRequest(exchange.getRequest());
            if (plan.execute(HttpRequestMutationSignal.of(mutableRequest), false).failureState()) {
                return Mono.error(new AccessDeniedException(ERROR_REQUEST_MUTATION_OBLIGATION_FAILED));
            }
            forwarded = mutableRequest.applyTo(exchange);
        }

        if (!responseHandlersScheduled) {
            return chain.filter(forwarded);
        }

        val mutableResponse = new ReactiveMutableHttpResponse(forwarded.getResponse());
        val withResponse    = forwarded.mutate().response(mutableResponse).build();
        return chain.filter(withResponse).then(Mono.defer(() -> {
            if (plan.execute(HttpResponseSignal.of(mutableResponse), false).failureState()) {
                return Mono.error(new AccessDeniedException(ERROR_RESPONSE_OBLIGATION_FAILED));
            }
            return mutableResponse.commit();
        }));
    }
}
