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
package io.sapl.spring.constraints;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConstraintEnforcementServiceTests {

    private static final ObjectMapper MAPPER      = new ObjectMapper();
    private static final Value        CONSTRAINT  = Value.of("a constraint");
    private static final ArrayValue   CONSTRAINTS = ArrayValue.builder().add(CONSTRAINT).build();

    private static AuthorizationDecision permitWithObligation() {
        return new AuthorizationDecision(Decision.PERMIT, CONSTRAINTS, Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    private static AuthorizationDecision permitWithAdvice() {
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, CONSTRAINTS, Value.UNDEFINED);
    }

    private static AuthorizationDecision permitWithResource(Value resource) {
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, resource);
    }

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
    void when_noConstraints_then_AccessIsGranted() {
        final var service             = buildConstraintHandlerService();
        final var decision            = AuthorizationDecision.PERMIT;
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
    }

    @Test
    void when_resource_then_AccessIsGranted_and_valueIsReplaced() {
        final var service             = buildConstraintHandlerService();
        final var expected            = 69;
        final var decision            = permitWithResource(Value.of(expected));
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(expected).verifyComplete();
    }

    @Test
    void when_resource_and_typeMismatch_then_AccessIsDenied() {
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithResource(Value.of("not a number"));
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_obligation_and_noHandler_then_AccessIsDenied() {
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectError(AccessDeniedException.class).verify();
    }

    @Test
    void when_obligation_and_noHandler_then_bundleBuildThrowsAccessIsDenied() {
        final var service  = buildConstraintHandlerService();
        final var decision = permitWithObligation();
        assertThatThrownBy(() -> service.reactiveTypeBundleFor(decision, Integer.class))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_obligation_and_noHandler_but_ignored_then_bundleBuildDoesNotThrow() {
        final var service  = buildConstraintHandlerService();
        final var decision = permitWithObligation();
        assertThatCode(() -> service.reactiveTypeBundleFor(decision, Integer.class, CONSTRAINT))
                .doesNotThrowAnyException();
    }

    @Test
    void when_obligation_and_onDecisionHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_DECISION;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_accessManage_and_obligation_and_onDecisionHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider      = spy(new RunnableConstraintHandlerProvider() {

                                    @Override
                                    public boolean isResponsible(Value constraint) {
                                        return true;
                                    }

                                    @Override
                                    public Signal getSignal() {
                                        return Signal.ON_DECISION;
                                    }

                                    @Override
                                    public Runnable getHandler(Value constraint) {
                                        return this::run;
                                    }

                                    public void run() {
                                        // NOOP
                                    }
                                });
        final var providerNoRun = spy(new RunnableConstraintHandlerProvider() {

                                    @Override
                                    public boolean isResponsible(Value constraint) {
                                        return true;
                                    }

                                    @Override
                                    public Signal getSignal() {
                                        return Signal.ON_TERMINATE;
                                    }

                                    @Override
                                    public Runnable getHandler(Value constraint) {
                                        return this::run;
                                    }

                                    public void run() {
                                        // NOOP
                                    }
                                });
        globalRunnableProviders.add(provider);
        globalRunnableProviders.add(providerNoRun);
        final var service  = buildConstraintHandlerService();
        final var decision = permitWithObligation();
        final var bundle   = service.accessManagerBundleFor(decision);
        bundle.handleOnDecisionConstraints();
        verify(provider, times(1)).run();
        verify(providerNoRun, times(0)).run();
    }

    @Test
    void when_accessManage_and_obligation_and_noHandler_then_AccessDenied() {

        final var providerNoRun = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_TERMINATE;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }
        });
        globalRunnableProviders.add(providerNoRun);
        final var service  = buildConstraintHandlerService();
        final var decision = permitWithObligation();
        assertThatThrownBy(() -> service.accessManagerBundleFor(decision)).isInstanceOf(AccessDeniedException.class);
        verify(providerNoRun, times(0)).run();
    }

    @Test
    void when_obligation_and_onDecisionHandlerIsResponsible_andFails_then_AccessIsDenied() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_DECISION;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectError(AccessDeniedException.class).verify();
        verify(provider, times(1)).run();
    }

    @Test
    void when_advice_and_onDecisionHandlerIsResponsible_andFails_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_DECISION;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithAdvice();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_obligation_and_onCompleteHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

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
                // NOOP
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_obligation_and_onCancelHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

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
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped.take(1)).expectNext(1).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_obligation_and_onTerminateHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.ON_TERMINATE;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_obligation_and_afterTerminateHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RunnableConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Signal getSignal() {
                return Signal.AFTER_TERMINATE;
            }

            @Override
            public Runnable getHandler(Value constraint) {
                return this::run;
            }

            public void run() {
                // NOOP
            }
        });
        globalRunnableProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).run();
    }

    @Test
    void when_obligation_and_doOnNextHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

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
                // NOOP
            }

        });
        globalConsumerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(3)).accept(any());
    }

    @Test
    void when_obligation_and_doOnNextHandlerIsResponsible_andFailsOnTwo_then_AccessIsDeniedAfterFirst() {
        final var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

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
                if (i == 2)
                    throw new IllegalArgumentException("I FAILED TO OBLIGE");
            }

        });
        globalConsumerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1).expectError(AccessDeniedException.class).verify();
        verify(provider, times(2)).accept(any());
    }

    @Test
    void when_advice_and_doOnNextHandlerIsResponsible_andFailsOnTwo_then_AccessIsGranted() {
        final var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

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
                if (i == 2)
                    throw new IllegalArgumentException("I FAILED TO OBLIGE");
            }

        });
        globalConsumerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithAdvice();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(3)).accept(any());
    }

    @Test
    void when_obligation_and_doMapOnNextHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new MappingConstraintHandlerProvider<String>() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Class<String> getSupportedType() {
                return String.class;
            }

            @Override
            public UnaryOperator<String> getHandler(Value constraint) {
                return this::apply;
            }

            public String apply(String s) {
                return s + "A";
            }

        });
        globalMappingHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just("+", "-", "#");
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, String.class);
        StepVerifier.create(wrapped).expectNext("+A", "-A", "#A").verifyComplete();
        verify(provider, times(3)).apply(any());
    }

    @Test
    void when_obligation_and_doMapOnNextHandlersAreResponsible_andSucceeds_then_AccessIsGranted_and_ModifiedAccordingToPriority() {
        final var provider1 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return 100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    return s + "1";
                                }

                            });
        final var provider2 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return -100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    return s + "2";
                                }

                            });
        InOrder   inOrder   = inOrder(provider1, provider2);

        globalMappingHandlerProviders.add(provider2);
        globalMappingHandlerProviders.add(provider1);

        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just("+", "-", "#");
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, String.class);

        StepVerifier.create(wrapped).expectNext("+12", "-12", "#12").verifyComplete();

        inOrder.verify(provider1).apply(any());
        inOrder.verify(provider2).apply(any());
    }

    @Test
    void when_advice_and_doMapOnNextHandlersAreResponsible_andOneFails_then_AccessIsGranted_and_ModifiedByOnlyTheOtherHandler() {
        final var provider1 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return -100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    throw new IllegalStateException("I FAILED TO OBLIGE");
                                }

                            });
        final var provider2 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return 100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    return s + "2";
                                }

                            });
        InOrder   inOrder   = inOrder(provider1, provider2);

        globalMappingHandlerProviders.add(provider2);
        globalMappingHandlerProviders.add(provider1);

        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithAdvice();
        final var resourceAccessPoint = Flux.just("+", "-", "#");
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, String.class);

        StepVerifier.create(wrapped).expectNext("+2", "-2", "#2").verifyComplete();

        inOrder.verify(provider1).apply(any());
        inOrder.verify(provider2).apply(any());
    }

    @Test
    void when_obligation_and_doMapOnNextHandlersAreResponsible_andOneFails_then_AccessIsDeniedAfterFail() {
        final var provider1 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return 100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    if ("-".equals(s))
                                        throw new IllegalStateException("I FAILED TO OBLIGE");
                                    return s + "1";
                                }

                            });
        final var provider2 = spy(new MappingConstraintHandlerProvider<String>() {
                                @Override
                                public int getPriority() {
                                    return -100;
                                }

                                @Override
                                public boolean isResponsible(Value constraint) {
                                    return true;
                                }

                                @Override
                                public Class<String> getSupportedType() {
                                    return String.class;
                                }

                                @Override
                                public UnaryOperator<String> getHandler(Value constraint) {
                                    return this::apply;
                                }

                                public String apply(String s) {
                                    return s + "2";
                                }

                            });
        InOrder   inOrder   = inOrder(provider1, provider2);

        globalMappingHandlerProviders.add(provider2);
        globalMappingHandlerProviders.add(provider1);

        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just("+", "-", "#");
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, String.class);

        StepVerifier.create(wrapped).expectNext("+12").expectError(AccessDeniedException.class).verify();

        inOrder.verify(provider1).apply(any());
        inOrder.verify(provider2).apply(any());
    }

    @Test
    void when_obligation_and_doOnErrorHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                // NOOP
            }

        });
        globalErrorHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.concat(Flux.just(1),
                Flux.error(new IOException("I AM A FAILURE OF THE RAP")), Flux.just(3));
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1).expectError(IOException.class).verify();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_obligation_and_doOnErrorHandlerIsResponsible_andFails_then_errorIsMappedToAccessDenied() {
        final var provider = spy(new ErrorHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<Throwable> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Throwable t) {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalErrorHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.concat(Flux.just(1),
                Flux.error(new IOException("I AM A FAILURE OF THE RAP")), Flux.just(3));
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1).expectError(AccessDeniedException.class).verify();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_obligation_and_mapOnErrorHandlerIsResponsible_andSucceeds_then_ErrorIsMapped() {
        final var provider = spy(new ErrorMappingConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public UnaryOperator<Throwable> getHandler(Value constraint) {
                return this::apply;
            }

            public Throwable apply(Throwable t) {
                return new IllegalArgumentException("Original: " + t.getMessage(), t);
            }

        });
        globalErrorMappingHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.concat(Flux.just(1),
                Flux.error(new IOException("I AM A FAILURE OF THE RAP")), Flux.just(3));
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1).expectError(IllegalArgumentException.class).verify();
        verify(provider, times(1)).apply(any());
    }

    @Test
    void when_obligation_and_doSubscribeHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new SubscriptionHandlerProvider() {

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
        globalSubscriptionHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_obligation_and_doSubscribeHandlerIsResponsible_andFails_then_AccessIsDenied() {
        final var provider = spy(new SubscriptionHandlerProvider() {

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
        globalSubscriptionHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectError(AccessDeniedException.class).verify();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_obligation_and_onRequestHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        final var provider = spy(new RequestHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                // NOOP
            }

        });
        globalRequestHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_filterObligation_then_ElementsFiltered() {
        final var provider = spy(new FilterPredicateConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Predicate<Object> getHandler(Value constraint) {
                return this::test;
            }

            public boolean test(Object i) {
                return ((Integer) i) % 2 == 0;
            }

        });
        globalFilterPredicateProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3, 4, 5);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(2, 4).verifyComplete();
        verify(provider, times(5)).test(any());
    }

    @Test
    void when_obligation_and_onRequestHandlerIsResponsible_andFails_then_AccessIsDenied() {
        final var provider = spy(new RequestHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRequestHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithObligation();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        assertThrows(RuntimeException.class, wrapped::blockLast);
    }

    @Test
    void when_advice_and_onRequestHandlerIsResponsible_andFails_then_AccessIsGranted() {
        final var provider = spy(new RequestHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public LongConsumer getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(Long l) {
                throw new IllegalStateException("I FAILED TO OBLIGE");
            }

        });
        globalRequestHandlerProviders.add(provider);
        final var service             = buildConstraintHandlerService();
        final var decision            = permitWithAdvice();
        final var resourceAccessPoint = Flux.just(1, 2, 3);
        final var wrapped             = service.enforceConstraintsOfDecisionOnResourceAccessPoint(decision,
                resourceAccessPoint, Integer.class);
        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        verify(provider, times(1)).accept(any());
    }

    @Test
    void when_methodInvocationObligation_and_notReflectiveInvocation_then_AccessDenied() {
        final var provider = spy(new MethodInvocationConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(Value constraint) {
                return true;
            }

            @Override
            public Consumer<ReflectiveMethodInvocation> getHandler(Value constraint) {
                return this::accept;
            }

            public void accept(ReflectiveMethodInvocation invocation) {
                // NOOP
            }

        });
        globalInvocationHandlerProviders.add(provider);
        final var service  = buildConstraintHandlerService();
        final var decision = permitWithObligation();
        final var bundle   = service.blockingPreEnforceBundleFor(decision, Object.class);
        assertThrows(AccessDeniedException.class,
                () -> bundle.handleMethodInvocationHandlers(mock(MethodInvocation.class)));
        verify(provider, times(0)).accept(any());
    }

}
