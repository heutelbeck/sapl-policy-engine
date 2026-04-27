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

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.Signal.HttpDenialSignal;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import io.sapl.spring.pep.http.servlet.ServletMutableHttpResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;

/**
 * Servlet {@link AccessDeniedHandler} that fires {@link HttpDenialSignal}
 * against the {@link EnforcementPlan} published by
 * {@link SaplAuthorizationManager} so policy obligations can shape the deny
 * response (status, headers, body, redirect).
 * <p>
 * Falls back to Spring Security's default 403 behaviour when no plan is
 * present, when no handler claims the denial (no entries scheduled at the
 * denial signal, or the registered handlers leave the buffered response
 * untouched), or when an obligation handler fails. Otherwise the buffered
 * response shaped by the handlers is committed to the client.
 */
public class SaplAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException, ServletException {
        val plan = (EnforcementPlan) request.getAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE);
        if (plan == null || plan.entriesFor(HttpDenialSignal.TYPE).isEmpty()) {
            fallback.handle(request, response, denied);
            return;
        }

        val mutableResponse = new ServletMutableHttpResponse(response);
        val result          = plan.execute(HttpDenialSignal.of(mutableResponse), false);
        if (result.failureState() || !mutableResponse.isModified()) {
            fallback.handle(request, response, denied);
            return;
        }
        mutableResponse.commit();
    }
}
