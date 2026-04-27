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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.serialization.SaplReactiveJacksonModule;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates that the reactive {@link ReactiveAuthorizationSubscriptionFactory}
 * extension point shapes the subscription that reaches the PDP, and that
 * the factory is genuinely reactive (a {@link Mono} returning the
 * subscription is awaited before the PDP is called).
 */
class ReactiveAuthorizationSubscriptionFactoryOverrideTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule())
            .addModule(new SaplReactiveJacksonModule()).build();

    private PolicyDecisionPoint pdp;

    @BeforeEach
    void beforeEach() {
        pdp = mock(PolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
    }

    @Test
    @DisplayName("Default factory subscribes with serialized request on action and resource")
    void defaultFactoryShapeMatchesContract() {
        val captured = subscribeAndCapture(new DefaultReactiveAuthorizationSubscriptionFactory(MAPPER));

        assertThat(captured.action()).isNotEqualTo(Value.UNDEFINED);
        assertThat(captured.resource()).isNotEqualTo(Value.UNDEFINED);
        assertThat(captured.environment()).isEqualTo(Value.UNDEFINED);
    }

    @Test
    @DisplayName("Custom factory replaces the subscription shape passed to the PDP")
    void customFactoryShapeReachesPdp() {
        ReactiveAuthorizationSubscriptionFactory minimal = (auth, exchange) -> Mono
                .just(AuthorizationSubscription.of(auth.getName(), exchange.getRequest().getMethod().name(),
                        exchange.getRequest().getURI().getPath(), MAPPER));

        val captured = subscribeAndCapture(minimal);

        assertThat(captured.subject()).isEqualTo(Value.of("alice"));
        assertThat(captured.action()).isEqualTo(Value.of("GET"));
        assertThat(captured.resource()).isEqualTo(Value.of("/orders/42"));
    }

    @Test
    @DisplayName("Async factory chain is awaited before the PDP call")
    void asyncFactoryIsAwaited() {
        ReactiveAuthorizationSubscriptionFactory async = (auth, exchange) -> Mono
                .just(AuthorizationSubscription.of(auth.getName(), "async-action", "async-resource", MAPPER))
                .delayElement(java.time.Duration.ofMillis(20));

        val captured = subscribeAndCapture(async);

        assertThat(captured.action()).isEqualTo(Value.of("async-action"));
        assertThat(captured.resource()).isEqualTo(Value.of("async-resource"));
    }

    @Test
    @DisplayName("Factory receives the resolved authentication, not the raw Mono")
    void factoryReceivesResolvedAuthentication() {
        val                                      seen     = new AtomicReference<Authentication>();
        ReactiveAuthorizationSubscriptionFactory recorder = (auth, exchange) -> {
                                                              seen.set(auth);
                                                              return Mono.just(AuthorizationSubscription
                                                                      .of(auth.getName(), "x", "y", MAPPER));
                                                          };

        subscribeAndCapture(recorder);

        assertThat(seen.get()).isNotNull().extracting(Authentication::getName).isEqualTo("alice");
    }

    private AuthorizationSubscription subscribeAndCapture(ReactiveAuthorizationSubscriptionFactory factory) {
        val planner  = new EnforcementPlanner(java.util.List.of(), MAPPER);
        val manager  = new ReactiveSaplAuthorizationManager(pdp, planner, factory);
        val request  = MockServerHttpRequest.get("/orders/42").build();
        val exchange = MockServerWebExchange.from(request);
        val auth     = (Authentication) new UsernamePasswordAuthenticationToken("alice", "pw",
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        AuthorizationResult result = manager.authorize(Mono.just(auth), new AuthorizationContext(exchange)).block();
        assertThat(result).isNotNull();

        val captor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
        verify(pdp).decide(captor.<AuthorizationSubscription>capture());
        return captor.getValue();
    }
}
