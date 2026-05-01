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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

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
import io.sapl.spring.pep.http.MutableHttpRequest;
import io.sapl.spring.pep.http.MutableHttpResponse;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SaplHttpPepWebFilter")
class SaplHttpPepWebFilterTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final Set<SignalType> SUPPORTED_SIGNALS = Set.of(Signal.DecisionSignal.SIGNAL_TYPE,
            Signal.HttpRequestSignal.SIGNAL_TYPE, Signal.HttpRequestMutationSignal.SIGNAL_TYPE,
            Signal.HttpResponseSignal.SIGNAL_TYPE);

    private final SaplHttpPepWebFilter filter = new SaplHttpPepWebFilter();

    @Nested
    @DisplayName("Pass-through behaviour")
    class PassThrough {

        @Test
        @DisplayName("when no plan is present, the chain runs against the original exchange")
        void noPlan() {
            val            exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/r"));
            val            seen     = new AtomicReference<ServerWebExchange>();
            WebFilterChain chain    = e -> {
                                        seen.set(e);
                                        return Mono.empty();
                                    };
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(seen.get()).isSameAs(exchange);
        }

        @Test
        @DisplayName("plan with no HTTP signal handlers: chain bypasses the wrappers entirely")
        void planWithoutHttpHandlersBypassesWrappers() {
            val            plan     = planFor(permitWith("audit"), runnerProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            val            seen     = new AtomicReference<ServerWebExchange>();
            WebFilterChain chain    = e -> {
                                        seen.set(e);
                                        return Mono.empty();
                                    };
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(seen.get()).isSameAs(exchange);
        }

        @Test
        @DisplayName("only response handlers scheduled: chain receives the original request, wrapped response")
        void onlyResponseHandlersBypassRequestWrapper() {
            val            plan     = planFor(permitWith("stamp"), responseStampProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            val            seen     = new AtomicReference<ServerWebExchange>();
            WebFilterChain chain    = e -> {
                                        seen.set(e);
                                        return Mono.empty();
                                    };
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(seen.get()).satisfies(forwarded -> {
                assertThat(forwarded.getRequest()).isSameAs(exchange.getRequest());
                assertThat(forwarded.getResponse()).isInstanceOf(ReactiveMutableHttpResponse.class);
            });
        }

        @Test
        @DisplayName("request handler that does not mutate: chain receives the original request")
        void requestHandlerWithoutMutationDiscardsWrapper() {
            val            plan     = planFor(permitWith("noop"), noopRequestObserverProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            val            seen     = new AtomicReference<ServerWebExchange>();
            WebFilterChain chain    = e -> {
                                        seen.set(e);
                                        return Mono.empty();
                                    };
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(seen.get().getRequest()).isSameAs(exchange.getRequest());
        }
    }

    @Nested
    @DisplayName("Request mutation")
    class RequestMutation {

        @Test
        @DisplayName("a header set by an obligation is visible to the downstream chain")
        void headerInjected() {
            val            plan       = planFor(permitWith("inject"), injectHeaderProvider());
            val            exchange   = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            val            seenHeader = new AtomicReference<String>();
            WebFilterChain chain      = e -> {
                                          seenHeader.set(e.getRequest().getHeaders().getFirst("X-Tenant"));
                                          return Mono.empty();
                                      };
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(seenHeader.get()).isEqualTo("krynn");
        }

        @Test
        @DisplayName("a failing request-mutation obligation propagates AccessDeniedException")
        void failureThrows() {
            val            plan     = planFor(permitWith("boom"), failingRequestProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            WebFilterChain chain    = e -> Mono.empty();
            StepVerifier.create(filter.filter(exchange, chain)).expectError(AccessDeniedException.class).verify();
        }
    }

    @Nested
    @DisplayName("Response signal")
    class Response {

        @Test
        @DisplayName("an obligation can rewrite the controller-produced body before commit")
        void rewriteBody() {
            val            plan     = planFor(permitWith("rewrite"), rewriteBodyProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            val            factory  = new DefaultDataBufferFactory();
            WebFilterChain chain    = e -> e.getResponse()
                    .writeWith(Mono.just(factory.wrap("ORIGINAL".getBytes(StandardCharsets.UTF_8))));
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("REWRITTEN");
        }

        @Test
        @DisplayName("an obligation can add headers visible on the underlying response")
        void addHeader() {
            val            plan     = planFor(permitWith("stamp"), responseStampProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            WebFilterChain chain    = e -> e.getResponse().setComplete();
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace")).isEqualTo("abc");
        }

        @Test
        @DisplayName("a failing response obligation propagates AccessDeniedException and does not commit a body")
        void failureThrows() {
            val            plan     = planFor(permitWith("boom"), failingResponseProvider());
            val            exchange = withPlan(MockServerWebExchange.from(MockServerHttpRequest.get("/r")), plan);
            WebFilterChain chain    = e -> e.getResponse().setComplete();
            StepVerifier.create(filter.filter(exchange, chain)).expectError(AccessDeniedException.class).verify();
        }
    }

    private static MockServerWebExchange withPlan(MockServerWebExchange exchange, EnforcementPlan plan) {
        exchange.getAttributes().put(HttpEnforcementContext.PLAN_ATTRIBUTE, plan);
        return exchange;
    }

    private static EnforcementPlan planFor(AuthorizationDecision decision, ConstraintHandlerProvider provider) {
        val planner = new EnforcementPlanner(List.of(provider), MAPPER);
        return planner.plan(decision, SUPPORTED_SIGNALS);
    }

    private static AuthorizationDecision permitWith(String obligationType) {
        val obligation = Value.ofObject(Map.of("type", Value.of(obligationType)));
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static ConstraintHandlerProvider runnerProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "audit")) {
                return List.of();
            }
            ConstraintHandler.Runner h = () -> {};
            return List.of(new ScopedConstraintHandler(h, Signal.DecisionSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider responseStampProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "stamp")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setHeader("X-Trace", "abc");
            return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider noopRequestObserverProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "noop")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpRequest> h = req -> { /* observe only, no mutation */ };
            return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider injectHeaderProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "inject")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpRequest> h = req -> req.setHeader("X-Tenant", "krynn");
            return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider failingRequestProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "boom")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpRequest> h = req -> {
                throw new IllegalStateException("nope");
            };
            return List.of(new ScopedConstraintHandler(h, Signal.HttpRequestMutationSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider failingResponseProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "boom")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> {
                throw new IllegalStateException("nope");
            };
            return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
        };
    }

    private static ConstraintHandlerProvider rewriteBodyProvider() {
        return (constraint, supportedSignals) -> {
            if (!ConstraintHandlerProvider.constraintIsOfType(constraint, "rewrite")) {
                return List.of();
            }
            ConstraintHandler.Consumer<MutableHttpResponse> h = resp -> resp.setBody("REWRITTEN");
            return List.of(new ScopedConstraintHandler(h, Signal.HttpResponseSignal.SIGNAL_TYPE, 0));
        };
    }
}
