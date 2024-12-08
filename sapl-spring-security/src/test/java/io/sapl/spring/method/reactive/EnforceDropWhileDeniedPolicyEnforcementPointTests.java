/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

class EnforceDropWhileDeniedPolicyEnforcementPointTests {

    private static final ObjectMapper    MAPPER = new ObjectMapper();
    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;

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
    public static void beforeAll() {
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
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        sut.blockLast();
        assertThrows(IllegalStateException.class, sut::blockLast);
    }

    @Test
    void when_onlyOnePermit_thenAllSignalsGetThrough() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.create(sut).expectNext(1, 2, 3).verifyComplete();
    }

    @Test
    void when_onlyOnePermitWithResource_thenOnlyResourceGetThrough() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.numberNode(420)));
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.create(sut).expectNext(420).verifyComplete();
    }

    @Test
    void when_onlyOnePermitWithResourceTypeMismatch_thenAllDropped() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux
                .just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("NOT A NUMBER")));
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.create(sut).verifyComplete();
    }

    @Test
    void when_permit_thenPermitWithResourceThenPermit_thenAllSignalsGetThroughWhileNoResourceElseResource() {
        StepVerifier.withVirtualTime(
                this::scenario_when_permit_thenPermitWithResourceThenPermit_thenAllSignalsGetThroughWhileNoResourceElseResource)
                .thenAwait(Duration.ofMillis(3000L)).expectNext(0, 1, 69, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    private Flux<Integer> scenario_when_permit_thenPermitWithResourceThenPermit_thenAllSignalsGetThroughWhileNoResourceElseResource() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT,
                AuthorizationDecision.PERMIT.withResource(JSON.numberNode(69)), AuthorizationDecision.PERMIT)
                .delayElements(Duration.ofMillis(500L));
        final var data               = Flux.range(0, 10).delayElements(Duration.ofMillis(200L));
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_permit_thenPermitWithResourceThenPermit_typeMismatch_thenSignalsDuringMismatchGetDropped() {
        StepVerifier.withVirtualTime(
                this::scenario_when_permit_thenPermitWithResourceThenPermit_typeMismatch_thenSignalsDuringMismatchGetDropped)
                .thenAwait(Duration.ofMillis(3000L)).expectNext(0, 1, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    private Flux<Integer> scenario_when_permit_thenPermitWithResourceThenPermit_typeMismatch_thenSignalsDuringMismatchGetDropped() {
        final var constraintService = buildConstraintHandlerService();
        final var decisions         = Flux.just(AuthorizationDecision.PERMIT,
                AuthorizationDecision.PERMIT.withResource(JSON.textNode("NOT A NUMBER")), AuthorizationDecision.PERMIT)
                .delayElements(Duration.ofMillis(500L));
        final var data              = Flux.range(0, 10).delayElements(Duration.ofMillis(200L));
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintService, Integer.class);
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
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_onlyOneDeny_thenNoSignalsAndAndStaysSubscribedForPotentialFollowingNewDecisions() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.DENY);
        final var data               = Flux.just(1, 2, 3);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.withVirtualTime(() -> sut).expectSubscription().expectNoEvent(Duration.ofMillis(10L))
                .verifyTimeout(Duration.ofMillis(15L));
    }

    @Test
    void when_obligationsCannotBeBundled_thenSignalsDroppedStaysSubscribedForPotentialNewDecision() {
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.withVirtualTime(() -> sut).expectSubscription().expectNoEvent(Duration.ofMillis(10L))
                .verifyTimeout(Duration.ofMillis(25L));
    }

    @Test
    void when_obligationsCannotBeBundled_followPermitNoObligation_thenSignalsStartAfterSecondPermit() {
        final var decisions          = Flux
                .concat(decisionFluxOnePermitWithObligation(), Flux.just(AuthorizationDecision.PERMIT))
                .delayElements(Duration.ofMillis(50L));
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.withVirtualTime(() -> sut).expectSubscription().expectNoEvent(Duration.ofMillis(95L))
                .expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    @Test
    void when_onDecisionObligationsFails_followPermitNoObligation_thenSignalsStartAfterSecondPermit() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_DECISION;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = Flux
                .concat(decisionFluxOnePermitWithObligation(), Flux.just(AuthorizationDecision.PERMIT))
                .delayElements(Duration.ofMillis(50L));
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);
        StepVerifier.withVirtualTime(() -> sut).expectSubscription().expectNoEvent(Duration.ofMillis(95L))
                .expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    @Test
    void when_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDrop() {
        StepVerifier.withVirtualTime(this::scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDrop)
                .thenAwait(Duration.ofMillis(200L)).expectNext(1, 2).verifyComplete();
    }

    private Flux<Integer> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDrop() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
                .delayElements(Duration.ofMillis(50L));
        final var data               = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(20L));
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit() {
        StepVerifier
                .withVirtualTime(
                        this::scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit)
                .thenAwait(Duration.ofMillis(2000L)).expectNext(0, 1, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    private Flux<Integer> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit() {
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = Flux
                .just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY, AuthorizationDecision.PERMIT)
                .delayElements(Duration.ofMillis(50L));
        final var data               = Flux.range(0, 10).delayElements(Duration.ofMillis(20L));
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
    }

    @Test
    void when_constraintsPresent_thenTheseAreHandledAndUpdated() {
        StepVerifier.withVirtualTime(this::scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated)
                .thenAwait(Duration.ofMillis(1000L))
                .expectNext(10000, 10001, 10002, 10003, 10004, 50005, 50006, 50007, 50008, 50009).verifyComplete();
    }

    private Flux<Integer> scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated() {
        final var handler = new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public UnaryOperator<Integer> getHandler(JsonNode constraint) {
                return number -> number + constraint.asInt();
            }

        };
        globalMappingHandlerProviders.add(handler);
        final var constraintsService = buildConstraintHandlerService();
        final var decisions          = decisionFluxWithChangingAdvice().delayElements(Duration.ofMillis(270L));
        final var data               = Flux.range(0, 10).delayElements(Duration.ofMillis(50L));
        return EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);

    }

    @Test
    void when_handlerMapsToNull_thenElementsAreDropped() {
        final var handler = new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public UnaryOperator<Integer> getHandler(JsonNode constraint) {
                return number -> (number % 2 == 0) ? number : null;
            }

        };
        globalMappingHandlerProviders.add(handler);

        final var json            = JsonNodeFactory.instance;
        final var advicePlus10000 = json.numberNode(10000L);
        final var firstAdvice     = json.arrayNode();
        firstAdvice.add(advicePlus10000);

        final var decisions          = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
        final var constraintsService = buildConstraintHandlerService();

        final var data = Flux.range(0, 10);
        final var sut  = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut).expectNext(0, 2, 4, 6, 8).verifyComplete();
    }

    @Test
    void when_handlerCancel_thenHandlerIsCalled() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }

        });
        globalRunnableProviders.add(handler);
        final var json            = JsonNodeFactory.instance;
        final var advicePlus10000 = json.numberNode(10000L);
        final var firstAdvice     = json.arrayNode();
        firstAdvice.add(advicePlus10000);
        final var decisions          = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
        final var constraintsService = buildConstraintHandlerService();

        final var data = Flux.range(0, 10);
        final var sut  = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
                Integer.class);
        StepVerifier.create(sut.take(5)).expectNext(0, 1, 2, 3, 4).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_error_thenErrorMappedAndPropagated() {
        final var handler = spy(new ErrorMappingConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public UnaryOperator<Throwable> getHandler(JsonNode constraint) {
                return this::apply;
            }

            public Throwable apply(Throwable t) {
                return new IOException("LEGAL", t);
            }

        });
        globalErrorMappingHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4)
                .expectErrorMatches(error -> error instanceof IOException && "LEGAL".equals(error.getMessage()))
                .verify();

        verify(handler, times(1)).apply(any());
    }

    @Test
    void when_onNextObligationFails_thenAccessDeniedAndMatchingElementIsDropped() {
        final var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer i) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        globalConsumerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).verifyComplete();
        verify(handler, times(10)).accept(any());
    }

    @Test
    void when_onErrorObligationFails_thenAccessDeniedAndCompleteAsWeCannotRecoverFromDownstreamErrorsAnyhow() {
        final var handler = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        globalErrorHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(AccessDeniedException.class).verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_upstreamError_thenTerminateWithError() {

        final var decisions          = Flux.just(AuthorizationDecision.PERMIT);
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(RuntimeException.class).verify();
    }

    @Test
    void when_onSubscribeObligationFails_thenAllSignalsAreDropped() {
        final var handler = spy(new SubscriptionHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Subscription> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Subscription s) {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalSubscriptionHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onRequestObligationFailsForFirstDecisionButSucceedsForSecond_thenAllSignalsSent() {
        final var handler = spy(new RequestHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(JsonNode constraint) {
                return l -> {
                    if (constraint.asLong() == 10000L)
                        throw new RuntimeException("I FAILED TO OBLIGE");
                };
            }

        });
        globalRequestHandlerProviders.add(handler);
        final var decisions          = decisionFluxWithChangingAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(2)).getHandler(any());
    }

    @Test
    void when_onRequestObligationFails_thenImplicitlyAccessDeniedAndMessagesDropped() {
        final var handler = spy(new RequestHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }
        });
        globalRequestHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onCancelObligationFails_thenFluxIsJustComplete() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_onCompleteObligationFails_thenImplicitlyAccessDeniedButNothingHappensAsDenyHereOnlyDropsMessages() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_COMPLETE;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_onNextAdviceFails_thenAccessIsGranted() {
        final var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer i) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        globalConsumerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(10)).accept(any());
    }

    @Test
    void when_onErrorAdviceFails_thenOriginalErrorSignal() {
        final var handler = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }

        });
        globalErrorHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10).map(x -> {
                                         if (x == 5)
                                             throw new RuntimeException("ILLEGAL");
                                         return x;
                                     });
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectErrorMatches(err -> "ILLEGAL".equals(err.getMessage()))
                .verify();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onSubscribeAdviceFails_thenAccessGranted() {
        final var handler = spy(new SubscriptionHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Subscription> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Subscription s) {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalSubscriptionHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onRequestAdviceFails_thenAccessGranted() {
        final var handler = spy(new RequestHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new RuntimeException("I FAILED TO OBLIGE");
            }
        });
        globalRequestHandlerProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).accept(any());
    }

    @Test
    void when_onCancelAdviceFails_thenFluxIsJustComplete() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
    }

    @Test
    void when_onCompleteAdviceFails_thenAccessGranted() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_COMPLETE;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithAdvice();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
        verify(handler, times(1)).run();
    }

    @Test
    void when_onCancelObligationFailsByMissing_thenFluxIsJustComplete() {
        final var handler = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_CANCEL;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRunnableProviders.add(handler);
        final var decisions          = decisionFluxOnePermitWithObligation();
        final var constraintsService = buildConstraintHandlerService();
        final var data               = Flux.range(0, 10);
        final var sut                = EnforceDropWhileDeniedPolicyEnforcementPoint.of(decisions, data,
                constraintsService, Integer.class);

        StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
        verify(handler, times(1)).run();
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
        final var json       = JsonNodeFactory.instance;
        final var plus10000  = json.numberNode(10000L);
        final var obligation = json.arrayNode();
        obligation.add(plus10000);
        return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
    }

    public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
        final var json      = JsonNodeFactory.instance;
        final var plus10000 = json.numberNode(10000L);
        final var advice    = json.arrayNode();
        advice.add(plus10000);
        return Flux.just(AuthorizationDecision.PERMIT.withAdvice(advice));
    }

    public Flux<AuthorizationDecision> decisionFluxWithChangingAdvice() {
        final var json            = JsonNodeFactory.instance;
        final var advicePlus10000 = json.numberNode(10000L);
        final var advicePlus50000 = json.numberNode(50000L);
        final var firstAdvice     = json.arrayNode();
        firstAdvice.add(advicePlus10000);
        final var secondAdvice = json.arrayNode();
        secondAdvice.add(advicePlus50000);

        return Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice),
                AuthorizationDecision.PERMIT.withAdvice(secondAdvice));
    }

    public Flux<AuthorizationDecision> decisionFluxWithChangingObligations() {
        final var json      = JsonNodeFactory.instance;
        final var plus10000 = json.numberNode(10000L);
        final var plus50000 = json.numberNode(50000L);
        final var first     = json.arrayNode();
        first.add(plus10000);
        final var second = json.arrayNode();
        second.add(plus50000);

        return Flux.just(AuthorizationDecision.PERMIT.withObligations(first),
                AuthorizationDecision.PERMIT.withObligations(second));
    }

}
