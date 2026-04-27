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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Servlet filter that fires the HTTP request-mutation and response
 * signals against the {@link EnforcementPlan} published by
 * {@link io.sapl.spring.manager.SaplAuthorizationManager}.
 * <p>
 * Sits immediately after Spring Security's {@code AuthorizationFilter}.
 * On every request that reaches it (the request was permitted by the
 * authorization manager) the filter:
 * <ol>
 * <li>Reads the active plan from the request attribute. If none is
 * present (the manager did not run) it passes through unchanged.</li>
 * <li>Wraps the request in a {@link ServletMutableHttpRequest} and fires
 * {@link HttpRequestMutationSignal} so handlers may inject headers or
 * attributes. Forwards the wrapped request down the chain.</li>
 * <li>After the chain returns and before the response is committed,
 * wraps the response in a {@link ServletMutableHttpResponse} and fires
 * {@link HttpResponseSignal}. Handlers may observe or modify the
 * response (status, headers, body).</li>
 * </ol>
 * Obligation handler failures throw {@link AccessDeniedException}, which
 * is caught by Spring's exception-translation filter and routed to the
 * configured access-denied handler.
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

        val mutableRequest = new ServletMutableHttpRequest(request);
        if (plan.execute(HttpRequestMutationSignal.of(mutableRequest), false).failureState()) {
            throw new AccessDeniedException(ERROR_REQUEST_MUTATION_OBLIGATION_FAILED);
        }

        chain.doFilter(mutableRequest, response);

        val mutableResponse = new ServletMutableHttpResponse(response);
        if (plan.execute(HttpResponseSignal.of(mutableResponse), false).failureState()) {
            throw new AccessDeniedException(ERROR_RESPONSE_OBLIGATION_FAILED);
        }
    }
}
