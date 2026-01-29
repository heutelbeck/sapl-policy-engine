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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        mapper = JsonMapper.builder().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).build();

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
        StepVerifier.create(sut.authorize(AUTHENTICATION, ctx))
                .expectNextMatches(org.springframework.security.authorization.AuthorizationResult::isGranted)
                .verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_ObligationsFail_then_IsNotGranted() {
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        doThrow(new AccessDeniedException("")).when(bundle).handleOnDecisionConstraints();
        StepVerifier.create(sut.authorize(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted())
                .verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_PdpDeny_then_NotGranted() {
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
        StepVerifier.create(sut.authorize(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted())
                .verifyComplete();
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void when_PdpPermitWithResource_then_NotGranted() {
        final var objectNode = mapper.createObjectNode();
        final var decision   = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                ValueJsonMarshaller.fromJsonNode(objectNode));
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));
        StepVerifier.create(sut.authorize(AUTHENTICATION, ctx)).expectNextMatches(dec -> !dec.isGranted())
                .verifyComplete();
        verify(bundle, times(0)).handleOnDecisionConstraints();
    }

}
