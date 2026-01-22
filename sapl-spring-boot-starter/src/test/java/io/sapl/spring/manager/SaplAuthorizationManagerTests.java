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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SaplAuthorizationManagerTests {

    private ObjectMapper                       mapper;
    private PolicyDecisionPoint                pdp;
    private ConstraintEnforcementService       constraintHandlers;
    private BlockingConstraintHandlerBundle<?> bundle;
    private Authentication                     authentication;

    @BeforeEach
    void setUpMocks() {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        SimpleModule module = new SimpleModule();
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        mapper.registerModule(module);
        authentication     = mock(Authentication.class);
        pdp                = mock(PolicyDecisionPoint.class);
        constraintHandlers = mock(ConstraintEnforcementService.class);
        bundle             = mock(BlockingConstraintHandlerBundle.class);
        doReturn(bundle).when(constraintHandlers).accessManagerBundleFor(any());
    }

    @Test
    void whenPermit_thenGranted() {
        final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        final var ctx = mock(RequestAuthorizationContext.class);
        assertThat(sut.check(() -> authentication, ctx))
                .matches(org.springframework.security.authorization.AuthorizationDecision::isGranted);
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void whenIndeterminate_thenDenied() {
        final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
        final var ctx = mock(RequestAuthorizationContext.class);
        assertThat(sut.check(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

    @Test
    void whenNullDecision_thenDenied() {
        final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());
        final var ctx = mock(RequestAuthorizationContext.class);
        assertThat(sut.check(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
    }

    @Test
    void whenHasResource_thenDenied() {
        final var sut        = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
        final var objectNode = mapper.createObjectNode();
        final var decision   = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                ValueJsonMarshaller.fromJsonNode(objectNode));
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));
        final var ctx = mock(RequestAuthorizationContext.class);
        assertThat(sut.check(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
    }

    @Test
    void whenObligationsFail_thenAccessDenied() {
        final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        doThrow(new AccessDeniedException("")).when(bundle).handleOnDecisionConstraints();

        final var ctx = mock(RequestAuthorizationContext.class);
        assertThat(sut.check(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
        verify(bundle, times(1)).handleOnDecisionConstraints();
    }

}
