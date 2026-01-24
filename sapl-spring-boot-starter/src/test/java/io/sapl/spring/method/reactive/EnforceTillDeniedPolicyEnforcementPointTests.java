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
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnforceTillDeniedPolicyEnforcementPointTests {

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

    @BeforeAll
    static void beforeAll() {
        // this eliminates excessive logging of dropped errors in case of onErrorStop()
        // downstream.
        Hooks.onErrorDropped(err -> {});
    }

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
    void when_subscribingTwice_Fails() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        sut.blockLast();
        assertThrows(IllegalStateException.class, sut::blockLast);
    }

    @Test
    void when_onlyOnePermit_thenAllSignalsGetThrough() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut).expectNext(1, 2, 3).verifyComplete();
    }

    @Test
    void when_permitWithResource_thenReplaceAndComplete() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux
                .just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.of(69)));
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut).expectNext(69).verifyComplete();
    }

    @Test
    void when_permitWithResource_typeMismatch_thenDenyAndComplete() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.EMPTY_ARRAY, Value.of("NOT A NUMBER")));
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_endlessPermits_thenAllSignalsGetThrough() {
        StepVerifier.withVirtualTime(this::scenario_when_endlessPermits_thenAllSignalsGetThrough)
                .thenAwait(Duration.ofMillis(300L)).expectNext(1, 2, 3).verifyComplete();
    }

    private Flux<Integer> scenario_when_endlessPermits_thenAllSignalsGetThrough() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT).repeat()
                .delayElements(Duration.ofMillis(5L));
        final var data               = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(30L));
        return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_onlyOneDeny_thenNoSignalsAndAccessDenied() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.DENY);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_onlyOneDeny_thenNoSignalsAndAccessDeniedOnErrorContinueDoesNotLeakData() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.DENY);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut.onErrorContinue((a, b) -> {})).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_firstPermitThenDeny_thenSignalsPassThroughTillDenied() {
        StepVerifier.withVirtualTime(this::scenario_firstPermitThenDeny_thenSignalsPassThroughTillDenied)
                .thenAwait(Duration.ofMillis(200L)).expectNext(1, 2).expectError(AccessDeniedException.class).verify();
    }

    private Flux<Integer> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDenied() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
                .delayElements(Duration.ofMillis(50L));
        final var data               = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(20L));
        return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_constraintsPresent_thenTheseAreHandledAndUpdated() {
        StepVerifier.withVirtualTime(this::scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated)
                .thenAwait(Duration.ofMillis(1000L))
                .expectNext(10000, 10001, 10002, 10003, 10004, 50005, 50006, 50007, 50008, 50009).verifyComplete();
    }

    Flux<Integer> scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated() {
        final var handler = new MappingConstraintHandlerProvider<Integer>() {

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
                return number -> number + ((NumberValue) constraint).value().intValue();
            }

        };
        globalMappingHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = decisionFluxWithChangingAdvice().delayElements(Duration.ofMillis(270L));
        final var data               = Flux.range(0, 10).delayElements(Duration.ofMillis(50L));
        return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);

    }

    @Test
    void when_handlerMapsToNull_thenElementsAreDropped() {
        final var handler = new MappingConstraintHandlerProvider<Integer>() {

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
                return number -> (number % 2 == 0) ? number : null;
            }

        };
        globalMappingHandlerProviders.add(handler);
        final var advicePlus10000 = Value.of(10000L);

        final var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.ofArray(advicePlus10000), Value.UNDEFINED));
        final var constraintsService = buildConstraintHandlerService();

        final var data = Flux.range(0, 10);
        final var sut  = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
        StepVerifier.create(sut).expectNext(0, 2, 4, 6, 8).verifyComplete();

    }

    @Test
    void when_handlerCancel_thenHandlerIsCalled() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }

        });
        globalRunnableProviders.add(handler);
        final var advicePlus10000    = Value.of(10000L);
        final var decisions          = Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
                Value.ofArray(advicePlus10000), Value.UNDEFINED));
        final var constraintsService = buildConstraintHandlerService();

        final var data = Flux.range(0, 10);
        final var sut  = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
        StepVerifier.create(sut.take(5)).expectNext(0, 1, 2, 3, 4).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_error_thenErrorMappedAndPropagated() {
        final var handler = spy(new ErrorMappingConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public UnaryOperator<Throwable> getHandler(Value constraint) {
                return this::apply;
            }

            public Throwable apply(Throwable t) {
                return new IOException("LEGAL", t);
            }

        });
        this.globalErrorMappingHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4)
                .expectErrorMatches(error -> error instanceof IOException && "LEGAL".equals(error.getMessage()))
                .verify();

        verify(handler, times(1)).apply(any());
    }

    @Test
    void when_onNextObligationFails_thenAccessDenied() {
        final var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public Consumer<Integer> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Integer i) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        this.globalConsumerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onErrorObligationFails_thenAccessDenied() {
        final var handler = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        this.globalErrorHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(AccessDeniedException.class).verify();
        verify(handler, times(2)).accept(any());
    }

    @Test
    void when_onSubscribeObligationFails_thenAccessDenied() {
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
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalSubscriptionHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onRequestObligationFails_thenAccessDenied() {
        final var handler = spy(new RequestHandlerProvider() {
            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }
        });
        this.globalRequestHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectError(RuntimeException.class).verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onCompleteObligationFails_thenAccessDenied() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_COMPLETE;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).expectError(AccessDeniedException.class)
                .verify();
        verify(handler, times(1)).run();
    }

    @Test
    void when_onNextAdviceFails_thenAccessIsGranted() {
        final var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public Consumer<Integer> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Integer i) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        this.globalConsumerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(10)).accept(any());
    }

    @Test
    void when_onErrorAdviceFails_thenOriginalErrorSignal() {
        final var handler = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        this.globalErrorHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectErrorMatches(err -> "ILLEGAL".equals(err.getMessage()))
                .verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onSubscribeAdviceFails_thenAccessGranted() {
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
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalSubscriptionHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onRequestAdviceFails_thenAccessGranted() {
        final var handler = spy(new RequestHandlerProvider() {
            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }
        });
        this.globalRequestHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onCancelAdviceFails_thenFluxIsJustComplete() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    @Test
    void when_onCompleteAdviceFails_thenAccessGranted() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_COMPLETE;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_onNextObligationFailsByMissing_thenAccessDenied() {
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_onCancelObligationFails_thenFluxIsJustComplete() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        this.globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);

        StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
        verify(handler, times(1)).run();
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
        final var obligation = Value.of(10000L);
        return Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED));
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
        final var advice = Value.of(10000L);
        return Flux.just(
                new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.ofArray(advice), Value.UNDEFINED));
    }

    public Flux<AuthorizationDecision> decisionFluxWithChangingAdvice() {
        final var advicePlus10000 = Value.of(10000L);
        final var advicePlus50000 = Value.of(50000L);

        return Flux.just(
                new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.ofArray(advicePlus10000),
                        Value.UNDEFINED),
                new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.ofArray(advicePlus50000),
                        Value.UNDEFINED));
    }

}
