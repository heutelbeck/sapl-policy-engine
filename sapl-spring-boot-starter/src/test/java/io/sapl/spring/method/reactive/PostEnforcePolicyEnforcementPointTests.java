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
package io.sapl.spring.method.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.*;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.util.MethodInvocationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostEnforcePolicyEnforcementPointTests {

    private AuthorizationSubscriptionBuilderService subscriptionBuilderService;

    private MethodInvocation invocation;

    private ObjectMapper mapper;

    private Mono<Integer> resourceAccessPoint;

    private SaplAttribute defaultAttribute;

    private PolicyDecisionPoint pdp;

    List<RunnableConstraintHandlerProvider> globalRunnableProviders;

    List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

    List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

    List<RequestHandlerProvider> globalRequestHandlerProviders;

    List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

    List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

    List<ErrorHandlerProvider> globalErrorHandlerProviders;

    List<FilterPredicateConstraintHandlerProvider> globalFilterPredicateProviders;

    List<MethodInvocationConstraintHandlerProvider> globalInvocationHandlerProviders;

    @BeforeEach
    void beforeEach() {
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper.registerModule(module);
        subscriptionBuilderService = new AuthorizationSubscriptionBuilderService(
                new DefaultMethodSecurityExpressionHandler(), mapper);
        final var testClass = new TestClass();
        resourceAccessPoint = testClass.publicInteger();
        invocation          = MethodInvocationUtils.createFromClass(testClass, TestClass.class, "publicInteger", null,
                null);

        defaultAttribute                   = postEnforceAttributeFrom("'the subject'", "'the action'", "returnObject",
                "'the environment'", Integer.class);
        pdp                                = mock(PolicyDecisionPoint.class);
        globalRunnableProviders            = new LinkedList<>();
        globalConsumerProviders            = new LinkedList<>();
        globalSubscriptionHandlerProviders = new LinkedList<>();
        globalRequestHandlerProviders      = new LinkedList<>();
        globalMappingHandlerProviders      = new LinkedList<>();
        globalErrorMappingHandlerProviders = new LinkedList<>();
        globalErrorHandlerProviders        = new LinkedList<>();
        globalFilterPredicateProviders     = new LinkedList<>();
        globalInvocationHandlerProviders   = new LinkedList<>();
    }

    private ConstraintEnforcementService buildConstraintHandlerService() {
        return new ConstraintEnforcementService(globalRunnableProviders, globalConsumerProviders,
                globalSubscriptionHandlerProviders, globalRequestHandlerProviders, globalMappingHandlerProviders,
                globalErrorMappingHandlerProviders, globalErrorHandlerProviders, globalFilterPredicateProviders,
                globalInvocationHandlerProviders, mapper);
    }

    @Test
    void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.DENY);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var onErrorContinue = errorAndCauseConsumer();
        final var doOnError       = errorConsumer();
        final var sut             = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService,
                subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_Permit_AccessIsGranted() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT);
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute)
                .cast(Integer.class);
        StepVerifier.create(sut).expectNext(420).verifyComplete();
    }

    @Test
    void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() {
        final var handler = spy(new SubscriptionHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<Subscription> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Subscription s) {
                // NOOP
            }

        });
        this.globalSubscriptionHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = decisionFluxOnePermitWithObligation();
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute)
                .cast(Integer.class);

        StepVerifier.create(sut).expectNext(420).verifyComplete();

        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_PermitWithObligations_then_ObligationsAreApplied_and_AccessIsGranted() {
        final var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public UnaryOperator<Integer> getHandler(Value constraint) {
                return s -> s + ((NumberValue) constraint).value().intValue();
            }
        });
        this.globalMappingHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = decisionFluxOnePermitWithObligation();
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute)
                .cast(Integer.class);

        StepVerifier.create(sut).expectNext(10420).verifyComplete();

        verify(handler, times(1)).getHandler(any());
    }

    @Test
    void when_PermitWithObligations_and_oneObligationFails_thenAccessIsDeniedOnFailure() {
        final var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public UnaryOperator<Integer> getHandler(Value constraint) {
                return s -> {
                    throw new IllegalArgumentException("I FAILED TO OBLIGE");
                };
            }
        });
        this.globalMappingHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = decisionFluxOnePermitWithObligation();
        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var onErrorContinue = errorAndCauseConsumer();
        final var doOnError       = errorConsumer();
        final var sut             = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService,
                subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
        final var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public UnaryOperator<Integer> getHandler(Value constraint) {
                return s -> s + ((NumberValue) constraint).value().intValue();
            }
        });
        this.globalMappingHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var obligations        = Value.ofArray(Value.of(-69));
        final var decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.of(69)));
        final var onErrorContinue    = errorAndCauseConsumer();
        final var doOnError          = errorConsumer();

        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute)
                .cast(Integer.class);

        StepVerifier.create(sut).expectNext(0).verifyComplete();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(0)).accept(any());
    }

    @Test
    void when_PermitWithResource_and_typeMismatch_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {

        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, Value.of("I CAUSE A TYPE MISMATCH")));
        final var onErrorContinue    = errorAndCauseConsumer();
        final var doOnError          = errorConsumer();

        when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisions);
        final var sut = new PostEnforcePolicyEnforcementPoint(pdp, constraintsService, subscriptionBuilderService)
                .postEnforceOneDecisionOnResourceAccessPoint(resourceAccessPoint, invocation, defaultAttribute)
                .doOnError(doOnError).onErrorContinue(onErrorContinue).cast(Integer.class);

        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
        return Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of(10000)), Value.EMPTY_ARRAY,
                Value.UNDEFINED));
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<Throwable, Object> errorAndCauseConsumer() {
        return (BiConsumer<Throwable, Object>) mock(BiConsumer.class);
    }

    @SuppressWarnings("unchecked")
    private Consumer<Throwable> errorConsumer() {
        return (Consumer<Throwable>) mock(Consumer.class);
    }

    public static class TestClass {

        public Mono<Integer> publicInteger() {
            return Mono.just(420);
        }

    }

    @Getter
    public static class BadForJackson {
        private String bad;
    }

    private SaplAttribute postEnforceAttributeFrom(String subject, String action, String resource, String environment,
            Class<?> genericsType) {
        return new SaplAttribute(PostEnforce.class, toExpression(subject), toExpression(action), toExpression(resource),
                toExpression(environment), null, genericsType);
    }

    private static Expression toExpression(String expression) {
        return new SpelExpressionParser().parseExpression(expression);
    }
}
