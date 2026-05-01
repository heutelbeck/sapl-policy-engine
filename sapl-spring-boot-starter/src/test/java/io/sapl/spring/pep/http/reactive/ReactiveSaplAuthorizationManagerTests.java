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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.Signal.HttpRequestSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.http.HttpEnforcementContext;
import io.sapl.spring.serialization.SaplReactiveJacksonModule;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ReactiveSaplAuthorizationManagerTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule())
            .addModule(new SaplReactiveJacksonModule()).build();

    private static final String CAPTURE_REQUEST = "captureRequest";

    private PolicyDecisionPoint pdp;

    @BeforeEach
    void beforeEach() {
        pdp = mock(PolicyDecisionPoint.class);
    }

    private ReactiveSaplAuthorizationManager managerWith(ConstraintHandlerProvider... providers) {
        val planner = new EnforcementPlanner(List.of(providers), MAPPER);
        return new ReactiveSaplAuthorizationManager(pdp, planner,
                new DefaultReactiveAuthorizationSubscriptionFactory(MAPPER));
    }

    private static AuthorizationContext exchangeContext() {
        val request  = MockServerHttpRequest.get("/orders/42").header("X-Tenant", "krynn").build();
        val exchange = MockServerWebExchange.from(request);
        return new AuthorizationContext(exchange);
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
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(managerWith().authorize(Mono.just(userAuthentication()), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isTrue()).verifyComplete();
        }

        @Test
        @DisplayName("DENY decision blocks access")
        void givenDenyThenBlock() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

            StepVerifier.create(managerWith().authorize(Mono.just(userAuthentication()), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isFalse()).verifyComplete();
        }

        @Test
        @DisplayName("Empty PDP stream falls back to DENY")
        void givenEmptyDecisionStreamThenDeny() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());

            StepVerifier.create(managerWith().authorize(Mono.just(userAuthentication()), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isFalse()).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Anonymous fallback")
    class AnonymousFallback {

        @Test
        @DisplayName("Empty authentication Mono is replaced with an anonymous token (no NPE)")
        void givenEmptyAuthenticationThenAnonymousFallback() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(managerWith().authorize(Mono.empty(), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isTrue()).verifyComplete();
        }
    }

    @Nested
    @DisplayName("HttpRequestSignal")
    class RequestSignal {

        @Test
        @DisplayName("Handler attached to HttpRequestSignal receives the reactive request")
        void givenHandlerOnRequestSignalThenReceivesRequest() {
            val captured = new AtomicReference<HttpRequest>();
            val provider = capturingProvider(captured);
            when(pdp.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(permitWithObligation(CAPTURE_REQUEST)));

            StepVerifier.create(managerWith(provider).authorize(Mono.just(userAuthentication()), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isTrue()).verifyComplete();

            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().getURI().getPath()).isEqualTo("/orders/42");
            assertThat(captured.get().getHeaders().getFirst("X-Tenant")).isEqualTo("krynn");
        }

        @Test
        @DisplayName("Obligation-handler failure during the request signal denies the request")
        void givenHandlerThrowsThenDeny() {
            val provider = throwingProvider();
            when(pdp.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(permitWithObligation(CAPTURE_REQUEST)));

            StepVerifier.create(managerWith(provider).authorize(Mono.just(userAuthentication()), exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isFalse()).verifyComplete();
        }
    }

    private static Authentication userAuthentication() {
        return new UsernamePasswordAuthenticationToken("raistlin", "credentials",
                AuthorityUtils.createAuthorityList("ROLE_BLACK_ROBE"));
    }

    private static ConstraintHandlerProvider capturingProvider(AtomicReference<HttpRequest> sink) {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, CAPTURE_REQUEST)) {
                return List.of();
            }
            if (!supportedSignals.contains(HttpRequestSignal.SIGNAL_TYPE)) {
                return List.of();
            }
            ConstraintHandler.Consumer<HttpRequest> handler = sink::set;
            return List.of(new ScopedConstraintHandler(handler, HttpRequestSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider throwingProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, CAPTURE_REQUEST)) {
                return List.of();
            }
            if (!supportedSignals.contains(HttpRequestSignal.SIGNAL_TYPE)) {
                return List.of();
            }
            ConstraintHandler.Consumer<HttpRequest> handler = req -> {
                throw new IllegalStateException("audit handler failed");
            };
            return List.of(new ScopedConstraintHandler(handler, HttpRequestSignal.SIGNAL_TYPE, 0));
        };
    }

    @Nested
    @DisplayName("Downstream wiring contract")
    class DownstreamWiring {

        @Test
        @DisplayName("Stores the EnforcementPlan on the exchange attribute keyed by HttpEnforcementContext.PLAN_ATTRIBUTE")
        void storesPlanOnExchangeAttribute() {
            when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
            val request  = MockServerHttpRequest.get("/orders/42").build();
            val exchange = MockServerWebExchange.from(request);
            val context  = new AuthorizationContext(exchange);

            StepVerifier.create(managerWith().authorize(Mono.just(userAuthentication()), context))
                    .assertNext(result -> assertThat(result.isGranted()).isTrue()).verifyComplete();

            assertThat(exchange.<Object>getAttribute(HttpEnforcementContext.PLAN_ATTRIBUTE))
                    .isInstanceOf(EnforcementPlan.class);
        }

        @Test
        @DisplayName("Advertises the full HTTP signal set so downstream filters and handlers are admissible at planning")
        void advertisesFullSignalSet() {
            val                       captured                 = new AtomicReference<Set<SignalType>>();
            ConstraintHandlerProvider capturingSignalsProvider = (constraint, supportedSignals) -> {
                                                                   if (!ConstraintHandlerProvider.constraintIsOfType(
                                                                           constraint, CAPTURE_REQUEST)) {
                                                                       return List.of();
                                                                   }
                                                                   captured.set(supportedSignals);
                                                                   ConstraintHandler.Runner noop = () -> {};
                                                                   return List.of(new ScopedConstraintHandler(noop,
                                                                           Signal.DecisionSignal.SIGNAL_TYPE, 0));
                                                               };
            when(pdp.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(permitWithObligation(CAPTURE_REQUEST)));

            StepVerifier
                    .create(managerWith(capturingSignalsProvider).authorize(Mono.just(userAuthentication()),
                            exchangeContext()))
                    .assertNext(result -> assertThat(result.isGranted()).isTrue()).verifyComplete();

            assertThat(captured.get()).containsExactlyInAnyOrder(Signal.DecisionSignal.SIGNAL_TYPE,
                    HttpRequestSignal.SIGNAL_TYPE, Signal.HttpRequestMutationSignal.SIGNAL_TYPE,
                    Signal.HttpResponseSignal.SIGNAL_TYPE, Signal.HttpDenialSignal.SIGNAL_TYPE);
        }
    }
}
