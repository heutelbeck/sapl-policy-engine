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

import static io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer.saplHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.http.MutableHttpRequest;
import io.sapl.spring.pep.http.MutableHttpResponse;
import lombok.val;

/**
 * End-to-end servlet test of the SAPL HTTP authorization chain. Real Spring
 * Boot context wired through {@link SaplHttpSecurityConfigurer}, real
 * controller, real {@link MockMvc}. The PDP is the only mock.
 */
@SpringBootTest(classes = SaplHttpServletEnforcementTests.TestApp.class)
@AutoConfigureMockMvc
class SaplHttpServletEnforcementTests {

    private static final String AUDIT_LOG       = "auditLog";
    private static final String CAPTURE_REQUEST = "captureRequest";
    private static final String INJECT_HEADER   = "injectRequestHeader";
    private static final String OBSERVE_STATUS  = "observeStatus";
    private static final String SET_HEADER      = "setResponseHeader";
    private static final String CUSTOM_DENY     = "customDeny";
    private static final String REWRITE_BODY    = "rewriteResponseBody";
    private static final String REQUEST_FAIL    = "failOnRequestMutation";
    private static final String REDIRECT_DENY   = "redirectDeny";
    private static final String AUDIT_AND_STAMP = "auditAndStamp";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    Probes probes;

    @BeforeEach
    void resetProbes() {
        probes.reset();
    }

    @Nested
    @DisplayName("Decision outcomes")
    class DecisionOutcomes {

        @Test
        @DisplayName("Authenticated PERMIT, no obligations: 200, body returned")
        @WithMockUser
        void givenPermitThenOk() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            mockMvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(content().string("hello"));
        }

        @Test
        @DisplayName("Authenticated DENY: 403 default body")
        @WithMockUser
        void givenAuthenticatedDenyThen403() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);

            mockMvc.perform(get("/hello")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Anonymous DENY: routed to authentication entry point (401), SAPL deny handler does not fire")
        void givenAnonymousDenyThenEntryPoint() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);

            mockMvc.perform(get("/hello")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Permit-path signals")
    class PermitSignals {

        @Test
        @DisplayName("DecisionSignal audit obligation: handler fires once on every decision")
        @WithMockUser
        void givenAuditObligationThenHandlerFires() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(AUDIT_LOG));

            mockMvc.perform(get("/hello")).andExpect(status().isOk());

            assertThat(probes.auditCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HttpRequestSignal observation obligation: handler captures the inbound request path")
        @WithMockUser
        void givenRequestObservationObligationThenHandlerCapturesRequest() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(CAPTURE_REQUEST));

            mockMvc.perform(get("/hello")).andExpect(status().isOk());

            assertThat(probes.observedPath()).isEqualTo("/hello");
        }

        @Test
        @DisplayName("HttpRequestMutationSignal obligation: controller sees the obligation-injected header")
        @WithMockUser
        void givenRequestHeaderInjectionObligationThenControllerSeesHeader() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(INJECT_HEADER));

            mockMvc.perform(get("/echo-tenant")).andExpect(status().isOk()).andExpect(content().string("krynn"));
        }

        @Test
        @DisplayName("HttpResponseSignal observation obligation: handler observes the post-controller status")
        @WithMockUser
        void givenResponseObservationObligationThenHandlerObservesStatus() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(OBSERVE_STATUS));

            mockMvc.perform(get("/hello")).andExpect(status().isOk());

            assertThat(probes.observedStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("HttpResponseSignal mutation obligation: client receives the obligation-added header")
        @WithMockUser
        void givenResponseHeaderObligationThenClientReceivesHeader() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(SET_HEADER));

            mockMvc.perform(get("/hello")).andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id", "abc-123"));
        }

        @Test
        @DisplayName("HttpResponseSignal body rewrite: client receives the obligation-replaced body")
        @WithMockUser
        void givenResponseBodyRewriteObligationThenClientReceivesRewrittenBody() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(REWRITE_BODY));

            mockMvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(content().string("REWRITTEN"));
        }

        @Test
        @DisplayName("Multi-handler bundle: one obligation produces audit on DecisionSignal and header on HttpResponseSignal")
        @WithMockUser
        void givenMultiHandlerObligationThenBothHandlersFire() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(AUDIT_AND_STAMP));

            mockMvc.perform(get("/hello")).andExpect(status().isOk()).andExpect(header().string("X-Audit", "stamped"));
            assertThat(probes.auditCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HttpRequestMutationSignal failure: routed back through the deny handler as 403")
        @WithMockUser
        void givenRequestMutationFailureObligationThen403() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(permitWith(REQUEST_FAIL));

            mockMvc.perform(get("/hello")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Deny-path signal")
    class DenySignal {

        @Test
        @DisplayName("HttpDenialSignal obligation: handler writes a custom 451 body")
        @WithMockUser
        void givenCustomDenyObligationThenHandlerShapesResponse() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(denyWith(CUSTOM_DENY));

            mockMvc.perform(get("/hello")).andExpect(status().is(451)).andExpect(content().string("denied by policy"));
        }

        @Test
        @DisplayName("HttpDenialSignal redirect obligation: handler issues 302 with Location header")
        @WithMockUser
        void givenRedirectDenyObligationThen302WithLocation() throws Exception {
            when(pdp.decideOnceBlocking(any())).thenReturn(denyWith(REDIRECT_DENY));

            mockMvc.perform(get("/hello")).andExpect(status().isFound())
                    .andExpect(header().string("Location", "/access-denied"));
        }
    }

    private static AuthorizationDecision permitWith(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static AuthorizationDecision denyWith(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.DENY, Value.ofArray(obligation), Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebSecurity
    static class TestApp {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {
            return http.with(saplHttp(), withDefaults()).csrf(AbstractHttpConfigurer::disable).httpBasic(withDefaults())
                    .build();
        }

        @Bean
        MockMvcBuilderCustomizer saplSecurityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
        }

        @Bean
        Probes probes() {
            return new Probes();
        }

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        ProbeProvider probeProvider(Probes probes) {
            return new ProbeProvider(probes);
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/hello")
        String hello() {
            return "hello";
        }

        @GetMapping("/echo-tenant")
        String echoTenant(@RequestHeader(name = "X-Tenant", required = false) String tenant) {
            return tenant == null ? "no-tenant" : tenant;
        }
    }

    static class Probes {
        private int                            auditCount     = 0;
        private final AtomicReference<String>  observedPath   = new AtomicReference<>();
        private final AtomicReference<Integer> observedStatus = new AtomicReference<>();

        synchronized void incrementAudit() {
            auditCount++;
        }

        synchronized int auditCount() {
            return auditCount;
        }

        void observePath(String path) {
            observedPath.set(path);
        }

        String observedPath() {
            return observedPath.get();
        }

        void observeStatus(int status) {
            observedStatus.set(status);
        }

        int observedStatus() {
            return observedStatus.get();
        }

        void reset() {
            auditCount = 0;
            observedPath.set(null);
            observedStatus.set(null);
        }
    }

    static class ProbeProvider implements ConstraintHandlerProvider {

        private final Probes probes;

        ProbeProvider(Probes probes) {
            this.probes = probes;
        }

        @Override
        public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
            if (ConstraintResponsibility.isResponsible(constraint, AUDIT_LOG)) {
                ConstraintHandler.Runner h = probes::incrementAudit;
                return List.of(new ScopedConstraintHandler(h, Signal.DecisionSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, CAPTURE_REQUEST)) {
                if (!supportedSignals.contains(Signal.HttpRequestSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<HttpRequest> h = req -> probes.observePath(req.getURI().getPath());
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, INJECT_HEADER)) {
                if (!supportedSignals.contains(Signal.HttpRequestMutationSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpRequest> h = req -> req.setHeader("X-Tenant", "krynn");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, OBSERVE_STATUS)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> probes
                        .observeStatus(resp.getStatusCode().value());
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, SET_HEADER)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setHeader("X-Trace-Id", "abc-123");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, CUSTOM_DENY)) {
                if (!supportedSignals.contains(Signal.HttpDenialSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                    resp.setStatusCode(451);
                    resp.writeBody("text/plain;charset=UTF-8", "denied by policy");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, REWRITE_BODY)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setBody("REWRITTEN");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, REQUEST_FAIL)) {
                if (!supportedSignals.contains(Signal.HttpRequestMutationSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpRequest> h = req -> {
                    throw new IllegalStateException("request mutation refused by handler");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, REDIRECT_DENY)) {
                if (!supportedSignals.contains(Signal.HttpDenialSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                    resp.setStatusCode(302);
                    resp.setHeader("Location", "/access-denied");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintResponsibility.isResponsible(constraint, AUDIT_AND_STAMP)) {
                ConstraintHandler.Runner                        audit = probes::incrementAudit;
                ConstraintHandler.Consumer<MutableHttpResponse> stamp = resp -> resp.setHeader("X-Audit", "stamped");
                return List.of(new ScopedConstraintHandler(audit, Signal.DecisionSignal.SIGNAL_TYPE, 0),
                        new ScopedConstraintHandler(stamp, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            return List.of();
        }
    }
}
