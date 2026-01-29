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

import tools.jackson.databind.ObjectMapper;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreEnforcePolicyEnforcementPointTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                globalInvocationHandlerProviders, MAPPER);
    }

    @Test
    void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() throws Throwable {
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = Flux.just(AuthorizationDecision.DENY);
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(AccessDeniedException.class).verify();

        // onErrorContinue is only invoked, if there is a recoverable operator upstream
        // here there is no cause event from the RAP that could be handed over to the
        // errorAndCauseConsumer
        verify(onErrorContinue, times(0)).accept(any(), any());
        // the error can still be consumed via doOnError
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_Permit_AccessIsGranted() throws Throwable {
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = Flux.just(AuthorizationDecision.PERMIT);
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
                .verifyComplete();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(0)).accept(any());
    }

    @Test
    void when_PermitAndInvocationFails_thenFailureInStream() throws Throwable {
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = Flux.just(AuthorizationDecision.PERMIT);
        final var resourceAccessPoint = mock(ReflectiveMethodInvocation.class);
        doThrow(new IllegalArgumentException()).when(resourceAccessPoint).proceed();
        final var onErrorContinue = errorAndCauseConsumer();
        final var doOnError       = errorConsumer();
        final var sut             = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(IllegalArgumentException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_PermitMethodInvocationObligationFail_thenAccessDenied() throws Throwable {
        final var failingHandler = new MethodInvocationConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<ReflectiveMethodInvocation> getHandler(Value constraint) {
                return invocation -> {
                    throw new IllegalArgumentException();
                };
            }

        };
        globalInvocationHandlerProviders.add(failingHandler);
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = decisionFluxOnePermitWithObligation();
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_PermitMethodInvocationAdviceFail_thenAccessGranted() throws Throwable {
        final var failingHandler = new MethodInvocationConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<ReflectiveMethodInvocation> getHandler(Value constraint) {
                return invocation -> {
                    throw new IllegalArgumentException();
                };
            }

        };
        globalInvocationHandlerProviders.add(failingHandler);
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = decisionFluxOnePermitWithAdvice();
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
                .verifyComplete();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(0)).accept(any());
    }

    @Test
    void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() throws Throwable {
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
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = decisionFluxOnePermitWithObligation();
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
                .verifyComplete();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(0)).accept(any());
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_PermitWithObligations_and_oneObligationFailsMidStream_thenAccessIsDeniedOnFailure_notRecoverable_noLeaks()
            throws Throwable {
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
                    if (s == 2)
                        throw new IllegalArgumentException("I FAILED TO OBLIGE");
                    return s + ((NumberValue) constraint).value().intValue();
                };
            }
        });
        this.globalMappingHandlerProviders.add(handler);
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = decisionFluxOnePermitWithObligation();
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(10001)
                .expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    @Test
    void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() throws Throwable {
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
        final var constraintsService  = buildConstraintHandlerService();
        final var obligations         = Value.ofArray(Value.of(420));
        final var decisions           = Flux
                .just(new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.of(69)));
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(489).verifyComplete();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(0)).accept(any());
    }

    @Test
    void when_PermitWithResource_and_typeMismatch_thenAccessIsDenied() throws Throwable {
        final var constraintsService  = buildConstraintHandlerService();
        final var decisions           = Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, Value.of("I CAUSE A TYPE MISMATCH")));
        final var resourceAccessPoint = resourceAccessPointInvocation();
        final var onErrorContinue     = errorAndCauseConsumer();
        final var doOnError           = errorConsumer();
        final var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
                resourceAccessPoint, Integer.class);

        StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
                .expectError(AccessDeniedException.class).verify();

        verify(onErrorContinue, times(0)).accept(any(), any());
        verify(doOnError, times(1)).accept(any());
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
        return Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of(10000)), Value.EMPTY_ARRAY,
                Value.UNDEFINED));
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
        return Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.ofArray(Value.of(10000)),
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

    public ReflectiveMethodInvocation resourceAccessPointInvocation() throws Throwable {
        final var mock = mock(ReflectiveMethodInvocation.class);
        when(mock.proceed()).thenReturn(Flux.just(1, 2, 3));
        return mock;
    }
}
