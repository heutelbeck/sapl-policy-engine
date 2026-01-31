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

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.ObjectMapperAutoConfiguration;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaplAuthorizationManagerTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperAutoConfiguration.class));

    @Test
    void whenPermit_thenGranted() {
        contextRunner.run(context -> {
            final var mapper             = context.getBean(JsonMapper.class);
            final var pdp                = mock(PolicyDecisionPoint.class);
            final var constraintHandlers = mock(ConstraintEnforcementService.class);
            final var bundle             = mock(BlockingConstraintHandlerBundle.class);
            final var authentication     = new TestingAuthenticationToken("user", "password", "ROLE_USER");
            final var request            = new MockHttpServletRequest("GET", "/test");

            doReturn(bundle).when(constraintHandlers).accessManagerBundleFor(any());
            when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

            final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
            final var ctx = new RequestAuthorizationContext(request);
            assertThat(sut.authorize(() -> authentication, ctx))
                    .matches(org.springframework.security.authorization.AuthorizationResult::isGranted);
            verify(bundle, times(1)).handleOnDecisionConstraints();
        });
    }

    @Test
    void whenIndeterminate_thenDenied() {
        contextRunner.run(context -> {
            final var mapper             = context.getBean(JsonMapper.class);
            final var pdp                = mock(PolicyDecisionPoint.class);
            final var constraintHandlers = mock(ConstraintEnforcementService.class);
            final var bundle             = mock(BlockingConstraintHandlerBundle.class);
            final var authentication     = new TestingAuthenticationToken("user", "password", "ROLE_USER");
            final var request            = new MockHttpServletRequest("GET", "/test");

            doReturn(bundle).when(constraintHandlers).accessManagerBundleFor(any());
            when(pdp.decide((AuthorizationSubscription) any()))
                    .thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));

            final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
            final var ctx = new RequestAuthorizationContext(request);
            assertThat(sut.authorize(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
            verify(bundle, times(1)).handleOnDecisionConstraints();
        });
    }

    @Test
    void whenNullDecision_thenDenied() {
        contextRunner.run(context -> {
            final var mapper             = context.getBean(JsonMapper.class);
            final var pdp                = mock(PolicyDecisionPoint.class);
            final var constraintHandlers = mock(ConstraintEnforcementService.class);
            final var authentication     = new TestingAuthenticationToken("user", "password", "ROLE_USER");
            final var request            = new MockHttpServletRequest("GET", "/test");

            doReturn(mock(BlockingConstraintHandlerBundle.class)).when(constraintHandlers)
                    .accessManagerBundleFor(any());
            when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());

            final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
            final var ctx = new RequestAuthorizationContext(request);
            assertThat(sut.authorize(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
        });
    }

    @Test
    void whenHasResource_thenDenied() {
        contextRunner.run(context -> {
            final var mapper             = context.getBean(JsonMapper.class);
            final var pdp                = mock(PolicyDecisionPoint.class);
            final var constraintHandlers = mock(ConstraintEnforcementService.class);
            final var authentication     = new TestingAuthenticationToken("user", "password", "ROLE_USER");
            final var request            = new MockHttpServletRequest("GET", "/test");

            doReturn(mock(BlockingConstraintHandlerBundle.class)).when(constraintHandlers)
                    .accessManagerBundleFor(any());
            final var objectNode = mapper.createObjectNode();
            final var decision   = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    ValueJsonMarshaller.fromJsonNode(objectNode));
            when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(decision));

            final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
            final var ctx = new RequestAuthorizationContext(request);
            assertThat(sut.authorize(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
        });
    }

    @Test
    void whenObligationsFail_thenAccessDenied() {
        contextRunner.run(context -> {
            final var mapper             = context.getBean(JsonMapper.class);
            final var pdp                = mock(PolicyDecisionPoint.class);
            final var constraintHandlers = mock(ConstraintEnforcementService.class);
            final var bundle             = mock(BlockingConstraintHandlerBundle.class);
            final var authentication     = new TestingAuthenticationToken("user", "password", "ROLE_USER");
            final var request            = new MockHttpServletRequest("GET", "/test");

            doReturn(bundle).when(constraintHandlers).accessManagerBundleFor(any());
            when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
            doThrow(new AccessDeniedException("")).when(bundle).handleOnDecisionConstraints();

            final var sut = new SaplAuthorizationManager(pdp, constraintHandlers, mapper);
            final var ctx = new RequestAuthorizationContext(request);
            assertThat(sut.authorize(() -> authentication, ctx)).matches(dec -> !dec.isGranted());
            verify(bundle, times(1)).handleOnDecisionConstraints();
        });
    }

}
