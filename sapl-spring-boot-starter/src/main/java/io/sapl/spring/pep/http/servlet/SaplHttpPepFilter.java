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
package io.sapl.spring.pep.http.servlet;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.HttpRequestMutationSignal;
import io.sapl.spring.pep.constraints.Signal.HttpResponseSignal;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Servlet filter that fires the HTTP request-mutation and response signals
 * against the {@link EnforcementPlan} published by
 * {@link io.sapl.spring.manager.SaplAuthorizationManager}.
 * <p>
 * Sits immediately after Spring Security's {@code AuthorizationFilter}. On
 * every request that reaches it (the request was permitted by the
 * authorization manager) the filter:
 * <ol>
 * <li>Reads the active plan from the request attribute. If none is present
 * (the manager did not run) it passes through unchanged.</li>
 * <li>Wraps the request in a {@link ServletMutableHttpRequest} only when
 * the plan schedules at least one handler at
 * {@link HttpRequestMutationSignal}, fires the signal so handlers can
 * inject headers or attributes, and forwards the wrapped request down the
 * chain only when at least one handler actually mutated it; otherwise the
 * original request goes through and the wrapper is discarded.</li>
 * <li>Wraps the response in a {@link ServletMutableHttpResponse} only when
 * the plan schedules at least one handler at {@link HttpResponseSignal},
 * lets the chain write into the buffer, fires the signal so handlers can
 * read or replace status, headers, and body, then flushes the buffer to
 * the client.</li>
 * </ol>
 * <p>
 * Performance: response buffering captures every controller byte in
 * memory and re-emits it on commit, which is measurable for large or
 * streaming bodies. The filter therefore wraps only when at least one
 * handler is actually scheduled at the corresponding signal. The common
 * case (a permit decision with no HTTP signal handlers) runs against the
 * raw servlet request and response with no extra copy.
 * <p>
 * Obligation handler failures throw {@link AccessDeniedException}, caught
 * by Spring's exception-translation filter and routed to the configured
 * access-denied handler. {@link HttpResponseSignal} fires only on the
 * normal-return path; if the chain throws, the buffer is discarded and
 * the exception propagates so the standard error pipeline can produce its
 * own response.
 */
public class SaplHttpPepFilter extends OncePerRequestFilter {

    private static final String ERROR_REQUEST_MUTATION_OBLIGATION_FAILED = "Access Denied. An HTTP request-mutation obligation handler failed.";
    private static final String ERROR_RESPONSE_OBLIGATION_FAILED         = "Access Denied. An HTTP response obligation handler failed.";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        val plan = (EnforcementPlan) request.getAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE);
        if (plan == null) {
            chain.doFilter(request, response);
            return;
        }

        val requestHandlersScheduled  = !plan.entriesFor(HttpRequestMutationSignal.TYPE).isEmpty();
        val responseHandlersScheduled = !plan.entriesFor(HttpResponseSignal.TYPE).isEmpty();
        if (!requestHandlersScheduled && !responseHandlersScheduled) {
            chain.doFilter(request, response);
            return;
        }

        ServletRequest forwardedRequest = request;
        if (requestHandlersScheduled) {
            val mutableRequest = new ServletMutableHttpRequest(request);
            if (plan.execute(HttpRequestMutationSignal.of(mutableRequest), false).failureState()) {
                throw new AccessDeniedException(ERROR_REQUEST_MUTATION_OBLIGATION_FAILED);
            }
            if (mutableRequest.isModified()) {
                forwardedRequest = mutableRequest;
            }
        }

        if (!responseHandlersScheduled) {
            chain.doFilter(forwardedRequest, response);
            return;
        }

        val mutableResponse = new ServletMutableHttpResponse(response);
        chain.doFilter(forwardedRequest, mutableResponse);

        if (plan.execute(HttpResponseSignal.of(mutableResponse), false).failureState()) {
            throw new AccessDeniedException(ERROR_RESPONSE_OBLIGATION_FAILED);
        }
        mutableResponse.commit();
    }
}
