/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReactiveSaplAuthorizationManagerTests {

    private static final Mono<Authentication> AUTHENTICATION = Mono
            .just(new TestingAuthenticationToken("user", "password", "ROLE_1", "ROLE_2"));

    private ObjectMapper                       mapper;
    private PolicyDecisionPoint                pdp;
    private BlockingConstraintHandlerBundle<?> bundle;
    private ReactiveSaplAuthorizationManager   sut;
    private AuthorizationContext               ctx;

    @BeforeEach
    void setUpMocks() {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        SimpleModule module = new SimpleModule();
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper.registerModule(module);

        pdp = mock(PolicyDecisionPoint.class);
        final var constraintHandlers = mock(ConstraintEnforcementService.class);

        bundle = mock(BlockingConstraintHandlerBundle.class);
        doReturn(bundle).when(constraintHandlers).accessManagerBundleFor(any());

        sut = new ReactiveSaplAuthorizationManager(pdp, constraintHandlers, mapper);

        ctx = mock(AuthorizationContext.class);
        final var request  = MockServerHttpRequest.get("http://localhost").build();
        final var exchange = MockServerWebExchange.from(request);
        doReturn(exchange).when(ctx).getExchange();
    }

    @Test
    void when_PdpPermit_then_IsGranted() {
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        StepVerifier.create(sut.check(AUTHENTICATION, ctx))
                .expectNextMatches(org.springframework.security.authorization.AuthorizationDecision::isGranted)
                .verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_ObligationsFail_then_IsNotGranted() {
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        doThrow(new AccessDeniedException("")).when(bundle).handleOnDecisionConstraints();
        StepVerifier.create(sut.check(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted()).verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_PdpDeny_then_NotGranted() {
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
        StepVerifier.create(sut.check(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted()).verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_PdpPermitWithResource_then_NotGranted() {
        when(pdp.decide((AuthorizationSubscription) any()))
                .thenReturn(Flux.just(AuthorizationDecision.PERMIT.withResource(mapper.createObjectNode())));
        StepVerifier.create(sut.check(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted()).verifyComplete();
        verify(bundle, times(0)).handleOnDecisionConstraints();
    }

}
