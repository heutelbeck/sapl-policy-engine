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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import io.sapl.spring.pep.http.MutableHttpResponse;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SaplAccessDeniedHandler")
class SaplAccessDeniedHandlerTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(Signal.DecisionSignal.SIGNAL_TYPE,
            Signal.HttpDenialSignal.SIGNAL_TYPE);

    private static final AccessDeniedException DENIED = new AccessDeniedException("denied");

    private final SaplAccessDeniedHandler handler = new SaplAccessDeniedHandler();

    @Nested
    @DisplayName("Fallback paths")
    class Fallback {

        @Test
        @DisplayName("no plan attribute: falls back to Spring's default 403")
        void noPlan() throws Exception {
            val request  = new MockHttpServletRequest("GET", "/r");
            val response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("plan present but no handler scheduled at HttpDenialSignal: falls back to 403")
        void planWithoutDenialHandler() throws Exception {
            val plan     = planFor(denyWith("none"), provider(constraint -> List.of()));
            val request  = requestFor(plan);
            val response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("denial handler is a Runner that does not shape the response: falls back to 403")
        void runnerOnlyDoesNotShape() throws Exception {
            ConstraintHandler.Runner h        = () -> { /* logs only */ };
            val                      plan     = planFor(denyWith("audit"),
                    provider(constraint -> ConstraintHandlerProvider.constraintIsOfType(constraint, "audit")
                            ? List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0))
                            : List.of()));
            val                      request  = requestFor(plan);
            val                      response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("denial obligation handler throws: falls back to 403")
        void handlerFailureFallsBack() throws Exception {
            ConstraintHandler.Consumer<MutableHttpResponse> h        = resp -> {
                                                                         throw new IllegalStateException("nope");
                                                                     };
            val                                             plan     = planFor(denyWith("boom"),
                    provider(constraint -> ConstraintHandlerProvider.constraintIsOfType(constraint, "boom")
                            ? List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0))
                            : List.of()));
            val                                             request  = requestFor(plan);
            val                                             response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Shaping the deny response")
    class Shaping {

        @Test
        @DisplayName("handler that writes a custom body and status commits both to the underlying response")
        void writesCustomBody() throws Exception {
            ConstraintHandler.Consumer<MutableHttpResponse> h        = resp -> {
                                                                         resp.setStatusCode(451);
                                                                         resp.writeBody("text/plain;charset=UTF-8",
                                                                                 "denied by policy");
                                                                     };
            val                                             plan     = planFor(denyWith("custom"),
                    provider(constraint -> ConstraintHandlerProvider.constraintIsOfType(constraint, "custom")
                            ? List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0))
                            : List.of()));
            val                                             request  = requestFor(plan);
            val                                             response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(451);
            assertThat(response.getContentAsString()).isEqualTo("denied by policy");
            assertThat(response.getContentType()).startsWith("text/plain");
        }

        @Test
        @DisplayName("handler that issues a redirect commits a 302 with Location header")
        void redirect() throws Exception {
            ConstraintHandler.Consumer<MutableHttpResponse> h        = resp -> {
                                                                         resp.setStatusCode(302);
                                                                         resp.setHeader("Location", "/login");
                                                                     };
            val                                             plan     = planFor(denyWith("redir"),
                    provider(constraint -> ConstraintHandlerProvider.constraintIsOfType(constraint, "redir")
                            ? List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0))
                            : List.of()));
            val                                             request  = requestFor(plan);
            val                                             response = new MockHttpServletResponse();
            handler.handle(request, response, DENIED);
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeader("Location")).isEqualTo("/login");
        }
    }

    private static MockHttpServletRequest requestFor(EnforcementPlan plan) {
        val request = new MockHttpServletRequest("GET", "/r");
        request.setAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE, plan);
        return request;
    }

    private static EnforcementPlan planFor(AuthorizationDecision decision, ConstraintHandlerProvider provider) {
        val planner = new EnforcementPlanner(List.of(provider), MAPPER);
        return planner.plan(decision, SUPPORTED_SIGNALS);
    }

    private static AuthorizationDecision denyWith(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.DENY, Value.ofArray(obligation), Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    @FunctionalInterface
    private interface ProviderBody {
        List<ScopedConstraintHandler> handlers(Value constraint);
    }

    private static ConstraintHandlerProvider provider(ProviderBody body) {
        return (constraint, supportedSignals) -> body.handlers(constraint);
    }
}
