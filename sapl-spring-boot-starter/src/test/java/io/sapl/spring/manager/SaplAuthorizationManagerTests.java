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
package io.sapl.spring.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.HttpRequestSignal;
import io.sapl.spring.pep.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.serialization.SaplServletJacksonModule;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SaplAuthorizationManagerTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule())
            .addModule(new SaplServletJacksonModule()).build();

    private static final String CAPTURE_REQUEST = "captureRequest";

    private PolicyDecisionPoint pdp;

    @BeforeEach
    void beforeEach() {
        pdp = mock(PolicyDecisionPoint.class);
    }

    private SaplAuthorizationManager managerWith(ConstraintHandlerProvider... providers) {
        val planner = new EnforcementPlanner(List.of(providers), MAPPER);
        return new SaplAuthorizationManager(pdp, planner, MAPPER);
    }

    private static RequestAuthorizationContext context(MockHttpServletRequest request) {
        return new RequestAuthorizationContext(request);
    }

    private static MockHttpServletRequest sampleRequest() {
        val request = new MockHttpServletRequest("GET", "/orders/42");
        request.addHeader("X-Tenant", "krynn");
        return request;
    }

    private static AuthorizationDecision permitWithObligation(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    @Nested
    @DisplayName("Decision outcomes")
    class DecisionOutcomes {

        @Test
        @DisplayName("PERMIT decision allows access")
        void givenPermitThenAllow() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);
            val auth   = userAuthentication();
            val result = managerWith().authorize(() -> auth, context(sampleRequest()));
            assertThat(result.isGranted()).isTrue();
        }

        @Test
        @DisplayName("DENY decision blocks access")
        void givenDenyThenBlock() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);
            val auth   = userAuthentication();
            val result = managerWith().authorize(() -> auth, context(sampleRequest()));
            assertThat(result.isGranted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Anonymous fallback")
    class AnonymousFallback {

        @Test
        @DisplayName("Null authentication is replaced with an anonymous token (no NPE)")
        void givenNullAuthenticationThenAnonymousFallback() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);
            AuthorizationResult result = managerWith().authorize(() -> null, context(sampleRequest()));
            assertThat(result.isGranted()).isTrue();
        }
    }

    @Nested
    @DisplayName("HttpRequestSignal")
    class RequestSignal {

        @Test
        @DisplayName("Handler attached to HttpRequestSignal receives the wrapped request")
        void givenHandlerOnRequestSignalThenReceivesWrappedRequest() {
            val captured = new AtomicReference<HttpRequest>();
            val provider = capturingProvider(captured);
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWithObligation(CAPTURE_REQUEST));

            val auth   = userAuthentication();
            val result = managerWith(provider).authorize(() -> auth, context(sampleRequest()));

            assertThat(result.isGranted()).isTrue();
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().getURI().getPath()).isEqualTo("/orders/42");
            assertThat(captured.get().getHeaders().getFirst("X-Tenant")).isEqualTo("krynn");
        }

        @Test
        @DisplayName("Obligation-handler failure during the request signal denies the request")
        void givenHandlerThrowsThenDeny() {
            val provider = throwingProvider();
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWithObligation(CAPTURE_REQUEST));

            val auth   = userAuthentication();
            val result = managerWith(provider).authorize(() -> auth, context(sampleRequest()));

            assertThat(result.isGranted()).isFalse();
        }
    }

    private static Authentication userAuthentication() {
        return new UsernamePasswordAuthenticationToken("raistlin", "credentials",
                AuthorityUtils.createAuthorityList("ROLE_BLACK_ROBE"));
    }

    private static ConstraintHandlerProvider capturingProvider(AtomicReference<HttpRequest> sink) {
        return (constraint, supportedSignals) -> {
            if (!ConstraintResponsibility.isResponsible(constraint, CAPTURE_REQUEST)) {
                return List.of();
            }
            if (!supportedSignals.contains(HttpRequestSignal.TYPE)) {
                return List.of();
            }
            ConstraintHandler.Consumer<HttpRequest> handler = sink::set;
            return List.of(new ScopedConstraintHandler(handler, HttpRequestSignal.TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider throwingProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintResponsibility.isResponsible(constraint, CAPTURE_REQUEST)) {
                return List.of();
            }
            if (!supportedSignals.contains(HttpRequestSignal.TYPE)) {
                return List.of();
            }
            ConstraintHandler.Consumer<HttpRequest> handler = req -> {
                throw new IllegalStateException("audit handler failed");
            };
            return List.of(new ScopedConstraintHandler(handler, HttpRequestSignal.TYPE, 0));
        };
    }
}
