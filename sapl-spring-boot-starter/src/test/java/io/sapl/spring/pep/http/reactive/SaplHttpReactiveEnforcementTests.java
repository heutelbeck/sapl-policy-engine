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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import io.sapl.api.pdp.AuthorizationSubscription;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
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
import io.sapl.spring.pep.http.MutableHttpRequest;
import io.sapl.spring.pep.http.MutableHttpResponse;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * End-to-end reactive test of the SAPL HTTP authorization chain. Real
 * Spring Boot context wired through {@link SaplServerHttpSecurityConfigurer},
 * real controller, real {@link WebTestClient}. The PDP is the only mock.
 */
@SpringBootTest(classes = SaplHttpReactiveEnforcementTests.TestApp.class, properties = "spring.main.web-application-type=reactive")
class SaplHttpReactiveEnforcementTests {

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
    ApplicationContext context;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    Probes probes;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        probes.reset();
        client = WebTestClient.bindToApplicationContext(context).apply(
                org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                .configureClient().build();
    }

    @Nested
    @DisplayName("Decision outcomes")
    class DecisionOutcomes {

        @Test
        @DisplayName("Authenticated PERMIT, no obligations: 200, body returned")
        @WithMockUser
        void givenPermitThenOk() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("hello");
        }

        @Test
        @DisplayName("Authenticated DENY: 403 default body")
        @WithMockUser
        void givenAuthenticatedDenyThen403() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("Permit-path signals")
    class PermitSignals {

        @Test
        @DisplayName("DecisionSignal audit obligation: handler fires once on every decision")
        @WithMockUser
        void givenAuditObligationThenHandlerFires() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(AUDIT_LOG)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk();
            assertThat(probes.auditCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HttpRequestSignal observation obligation: handler captures the inbound request path")
        @WithMockUser
        void givenRequestObservationObligationThenHandlerCapturesRequest() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(CAPTURE_REQUEST)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk();
            assertThat(probes.observedPath()).isEqualTo("/hello");
        }

        @Test
        @DisplayName("HttpRequestMutationSignal obligation: controller sees the obligation-injected header")
        @WithMockUser
        void givenRequestHeaderInjectionObligationThenControllerSeesHeader() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(INJECT_HEADER)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/echo-tenant").exchange().expectStatus().isOk().expectBody(String.class)
                    .isEqualTo("krynn");
        }

        @Test
        @DisplayName("HttpResponseSignal observation obligation: handler observes the post-controller status")
        @WithMockUser
        void givenResponseObservationObligationThenHandlerObservesStatus() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(OBSERVE_STATUS)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk();
            assertThat(probes.observedStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("HttpResponseSignal mutation obligation: client receives the obligation-added header")
        @WithMockUser
        void givenResponseHeaderObligationThenClientReceivesHeader() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(SET_HEADER)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk().expectHeader()
                    .valueEquals("X-Trace-Id", "abc-123");
        }

        @Test
        @DisplayName("HttpResponseSignal body rewrite: client receives the obligation-replaced body")
        @WithMockUser
        void givenResponseBodyRewriteObligationThenClientReceivesRewrittenBody() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(REWRITE_BODY)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk().expectBody(String.class)
                    .isEqualTo("REWRITTEN");
        }

        @Test
        @DisplayName("Multi-handler bundle: one obligation produces audit on DecisionSignal and header on HttpResponseSignal")
        @WithMockUser
        void givenMultiHandlerObligationThenBothHandlersFire() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(AUDIT_AND_STAMP)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isOk().expectHeader()
                    .valueEquals("X-Audit", "stamped");
            assertThat(probes.auditCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("HttpRequestMutationSignal failure: routed back through the deny handler as 403")
        @WithMockUser
        void givenRequestMutationFailureObligationThen403() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(permitWith(REQUEST_FAIL)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("Deny-path signal")
    class DenySignal {

        @Test
        @DisplayName("HttpDenialSignal obligation: handler writes a custom 451 body")
        @WithMockUser
        void givenCustomDenyObligationThenHandlerShapesResponse() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(denyWith(CUSTOM_DENY)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isEqualTo(451).expectBody(String.class)
                    .isEqualTo("denied by policy");
        }

        @Test
        @DisplayName("HttpDenialSignal redirect obligation: handler issues 302 with Location header")
        @WithMockUser
        void givenRedirectDenyObligationThen302WithLocation() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(denyWith(REDIRECT_DENY)));
            client.mutateWith(
                    org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser())
                    .get().uri("/hello").exchange().expectStatus().isEqualTo(HttpStatus.FOUND).expectHeader()
                    .valueEquals("Location", "/access-denied");
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
    @EnableWebFluxSecurity
    static class TestApp {

        @Bean
        SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ApplicationContext ctx) {
            SaplServerHttpSecurityConfigurer.apply(http, ctx);
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(withDefaults()).build();
        }

        @Bean
        org.springframework.security.core.userdetails.MapReactiveUserDetailsService userDetailsService() {
            @SuppressWarnings("deprecation")
            org.springframework.security.core.userdetails.UserDetails user = org.springframework.security.core.userdetails.User
                    .withDefaultPasswordEncoder().username("user").password("user").roles("USER").build();
            return new org.springframework.security.core.userdetails.MapReactiveUserDetailsService(user);
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
        private final AtomicInteger            auditCount     = new AtomicInteger();
        private final AtomicReference<String>  observedPath   = new AtomicReference<>();
        private final AtomicReference<Integer> observedStatus = new AtomicReference<>();

        void incrementAudit() {
            auditCount.incrementAndGet();
        }

        int auditCount() {
            return auditCount.get();
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
            auditCount.set(0);
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
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, AUDIT_LOG)) {
                ConstraintHandler.Runner h = probes::incrementAudit;
                return List.of(new ScopedConstraintHandler(h, Signal.DecisionSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, CAPTURE_REQUEST)) {
                if (!supportedSignals.contains(Signal.HttpRequestSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<HttpRequest> h = req -> probes.observePath(req.getURI().getPath());
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, INJECT_HEADER)) {
                if (!supportedSignals.contains(Signal.HttpRequestMutationSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpRequest> h = req -> req.setHeader("X-Tenant", "krynn");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, OBSERVE_STATUS)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> probes
                        .observeStatus(resp.getStatusCode().value());
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, SET_HEADER)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setHeader("X-Trace-Id", "abc-123");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, CUSTOM_DENY)) {
                if (!supportedSignals.contains(Signal.HttpDenialSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                    resp.setStatusCode(451);
                    resp.writeBody("text/plain;charset=UTF-8", "denied by policy");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, REWRITE_BODY)) {
                if (!supportedSignals.contains(Signal.HttpResponseSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setBody("REWRITTEN");
                return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, REQUEST_FAIL)) {
                if (!supportedSignals.contains(Signal.HttpRequestMutationSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpRequest> h = req -> {
                    throw new IllegalStateException("request mutation refused by handler");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, REDIRECT_DENY)) {
                if (!supportedSignals.contains(Signal.HttpDenialSignal.SIGNAL_TYPE)) {
                    return List.of();
                }
                ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                    resp.setStatusCode(302);
                    resp.setHeader("Location", "/access-denied");
                };
                return List.of(new ScopedConstraintHandler(h, Signal.HttpDenialSignal.SIGNAL_TYPE, 0));
            }
            if (ConstraintHandlerProvider.constraintIsOfType(constraint, AUDIT_AND_STAMP)) {
                ConstraintHandler.Runner                        audit = probes::incrementAudit;
                ConstraintHandler.Consumer<MutableHttpResponse> stamp = resp -> resp.setHeader("X-Audit", "stamped");
                return List.of(new ScopedConstraintHandler(audit, Signal.DecisionSignal.SIGNAL_TYPE, 0),
                        new ScopedConstraintHandler(stamp, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
            }
            return List.of();
        }
    }
}
