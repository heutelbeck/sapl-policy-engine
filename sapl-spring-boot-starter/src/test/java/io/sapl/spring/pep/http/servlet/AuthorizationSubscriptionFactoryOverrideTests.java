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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.serialization.SaplServletJacksonModule;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates that the {@link AuthorizationSubscriptionFactory} extension
 * point shapes the subscription that reaches the PDP. These are not unit
 * tests of the factory itself: they assert that a non-default factory's
 * output is what the manager subscribes the PDP with. That is the contract
 * the extension point exists to provide.
 */
class AuthorizationSubscriptionFactoryOverrideTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule())
            .addModule(new SaplServletJacksonModule()).build();

    private PolicyDecisionPoint pdp;

    @BeforeEach
    void beforeEach() {
        pdp = mock(PolicyDecisionPoint.class);
        when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);
    }

    @Test
    @DisplayName("Default factory subscribes with serialized request on action and resource")
    void defaultFactoryShapeMatchesContract() {
        val captured = subscribeAndCapture(new DefaultAuthorizationSubscriptionFactory(MAPPER));

        assertThat(captured.action()).isNotEqualTo(Value.UNDEFINED);
        assertThat(captured.resource()).isNotEqualTo(Value.UNDEFINED);
        assertThat(captured.environment()).isEqualTo(Value.UNDEFINED);
    }

    @Test
    @DisplayName("Custom factory replaces the subscription shape passed to the PDP")
    void customFactoryShapeReachesPdp() {
        AuthorizationSubscriptionFactory minimal = (auth, request) -> AuthorizationSubscription.of(auth.getName(),
                request.getMethod(), request.getRequestURI(), MAPPER);

        val captured = subscribeAndCapture(minimal);

        assertThat(captured.subject()).isEqualTo(Value.of("alice"));
        assertThat(captured.action()).isEqualTo(Value.of("GET"));
        assertThat(captured.resource()).isEqualTo(Value.of("/orders/42"));
    }

    @Test
    @DisplayName("Factory receives the resolved authentication, not the raw supplier")
    void factoryReceivesResolvedAuthentication() {
        val                              seen     = new AtomicReference<Authentication>();
        AuthorizationSubscriptionFactory recorder = (auth, request) -> {
                                                      seen.set(auth);
                                                      return AuthorizationSubscription.of(auth.getName(), "x", "y",
                                                              MAPPER);
                                                  };

        subscribeAndCapture(recorder);

        assertThat(seen.get()).isNotNull().extracting(Authentication::getName).isEqualTo("alice");
    }

    @Test
    @DisplayName("Factory is consulted exactly once per authorize call")
    void factoryIsCalledOnce() {
        val                              invocations = new int[1];
        AuthorizationSubscriptionFactory counter     = (auth, request) -> {
                                                         invocations[0]++;
                                                         return AuthorizationSubscription.of(auth.getName(), "x", "y",
                                                                 MAPPER);
                                                     };

        subscribeAndCapture(counter);

        assertThat(invocations[0]).isEqualTo(1);
        verify(pdp).decideOnceBlocking(any());
        verify(pdp, never()).decide(any(AuthorizationSubscription.class));
    }

    private AuthorizationSubscription subscribeAndCapture(AuthorizationSubscriptionFactory factory) {
        val planner = new EnforcementPlanner(java.util.List.of(), MAPPER);
        val manager = new SaplAuthorizationManager(pdp, planner, factory);
        val request = new MockHttpServletRequest("GET", "/orders/42");
        val auth    = (Authentication) new UsernamePasswordAuthenticationToken("alice", "pw",
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        manager.authorize(() -> auth, new RequestAuthorizationContext(request));

        val captor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
        verify(pdp).decideOnceBlocking(captor.capture());
        return captor.getValue();
    }
}
