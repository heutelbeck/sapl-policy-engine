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
package io.sapl.vaadin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vaadin.flow.component.UI;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinSignals;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Vaadin constraint enforcement service")
class VaadinConstraintEnforcementServiceTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final Value CONSTRAINT = Value.of("constraint");

    private static final AuthorizationDecision PERMIT_WITH_OBLIGATION = new AuthorizationDecision(Decision.PERMIT,
            Value.ofArray(CONSTRAINT), Value.EMPTY_ARRAY, Value.UNDEFINED);

    private static final AuthorizationDecision PERMIT_WITH_ADVICE = new AuthorizationDecision(Decision.PERMIT,
            Value.EMPTY_ARRAY, Value.ofArray(CONSTRAINT), Value.UNDEFINED);

    private UI ui;

    private VaadinPep pep;

    private VaadinConstraintEnforcementService service;

    @BeforeEach
    void setUp() {
        ui      = UIMock.getMockedUI();
        pep     = mock(VaadinPep.class);
        service = new VaadinConstraintEnforcementService(List.of(), List.of(), MAPPER);
    }

    private void expectDecision(AuthorizationDecision input, AuthorizationDecision expected) {
        StepVerifier.create(service.enforceConstraintsOfDecision(input, ui, pep)).expectNext(expected).verifyComplete();
    }

    private void expectDeny(AuthorizationDecision input) {
        expectDecision(input, AuthorizationDecision.DENY);
    }

    private static ConstraintHandlerProvider providerOf(ConstraintHandler<?> handler, SignalType signal) {
        return (constraint, supportedSignals) -> CONSTRAINT.equals(constraint) && supportedSignals.contains(signal)
                ? List.of(new ScopedConstraintHandler(handler, signal, 0))
                : List.of();
    }

    private static VaadinFunctionConstraintHandlerProvider functionProviderOf(Function<UI, Mono<Boolean>> function) {
        return VaadinFunctionConstraintHandlerProvider.of(CONSTRAINT::equals, function);
    }

    @Nested
    @DisplayName("without constraints")
    class WithoutConstraints {

        @Test
        void whenDecisionHasNoConstraintsThenDecisionIsReturnedUnchanged() {
            expectDecision(AuthorizationDecision.PERMIT, AuthorizationDecision.PERMIT);
        }

        @Test
        void whenDecisionHasNoConstraintsThenNoProviderIsConsulted() {
            var provider = mock(ConstraintHandlerProvider.class);
            service.addGlobalConstraintHandlerProvider(provider);
            var functionProvider = mock(VaadinFunctionConstraintHandlerProvider.class);
            service.addGlobalVaadinFunctionProvider(functionProvider);

            expectDecision(AuthorizationDecision.PERMIT, AuthorizationDecision.PERMIT);

            verifyNoInteractions(provider, functionProvider);
        }

        @Test
        void whenDecisionCarriesReplacementResourceThenAccessIsDenied() {
            var withResource = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("replacement"));

            expectDeny(withResource);
        }
    }

    @Nested
    @DisplayName("unhandled constraints")
    class UnhandledConstraints {

        @Test
        void whenObligationHasNoHandlerThenAccessIsDenied() {
            expectDeny(PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenAdviceHasNoHandlerThenDecisionIsReturnedUnchanged() {
            expectDecision(PERMIT_WITH_ADVICE, PERMIT_WITH_ADVICE);
        }

        @Test
        void whenObligationIsClaimedByProviderAndVaadinFunctionThenAccessIsDenied() {
            ConstraintHandler.Runner runner = () -> {};
            service.addGlobalConstraintHandlerProvider(providerOf(runner, DecisionSignal.SIGNAL_TYPE));
            service.addGlobalVaadinFunctionProvider(functionProviderOf(ignored -> Mono.just(Boolean.TRUE)));

            expectDeny(PERMIT_WITH_OBLIGATION);
        }
    }

    @Nested
    @DisplayName("decision and UI signal handlers")
    class SignalHandlers {

        @Test
        void whenGlobalRunnerHandlesObligationThenRunnerRunsAndDecisionIsReturned() {
            var                      ran    = new AtomicReference<Boolean>(Boolean.FALSE);
            ConstraintHandler.Runner runner = () -> ran.set(Boolean.TRUE);
            service.addGlobalConstraintHandlerProvider(providerOf(runner, DecisionSignal.SIGNAL_TYPE));

            expectDecision(PERMIT_WITH_OBLIGATION, PERMIT_WITH_OBLIGATION);

            assertThat(ran).hasValue(Boolean.TRUE);
        }

        @Test
        void whenLocalConsumerHandlesObligationThenConsumerReceivesTheUiAndDecisionIsReturned() {
            var                            received = new AtomicReference<UI>();
            ConstraintHandler.Consumer<UI> consumer = received::set;
            when(pep.getLocalConstraintHandlerProviders())
                    .thenReturn(List.of(providerOf(consumer, VaadinSignals.UI_SIGNAL)));

            expectDecision(PERMIT_WITH_OBLIGATION, PERMIT_WITH_OBLIGATION);

            assertThat(received).hasValue(ui);
        }

        @Test
        void whenDecisionConsumerHandlesObligationThenConsumerReceivesTheDecision() {
            var                                               received = new AtomicReference<AuthorizationDecision>();
            ConstraintHandler.Consumer<AuthorizationDecision> consumer = received::set;
            service.addGlobalConstraintHandlerProvider(providerOf(consumer, DecisionSignal.SIGNAL_TYPE));

            expectDecision(PERMIT_WITH_OBLIGATION, PERMIT_WITH_OBLIGATION);

            assertThat(received).hasValue(PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenObligationHandlerFailsThenAccessIsDenied() {
            ConstraintHandler.Runner failing = () -> {
                throw new IllegalStateException("Handler failed.");
            };
            service.addGlobalConstraintHandlerProvider(providerOf(failing, DecisionSignal.SIGNAL_TYPE));

            expectDeny(PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenAdviceHandlerFailsThenDecisionIsReturnedUnchanged() {
            ConstraintHandler.Runner failing = () -> {
                throw new IllegalStateException("Handler failed.");
            };
            service.addGlobalConstraintHandlerProvider(providerOf(failing, DecisionSignal.SIGNAL_TYPE));

            expectDecision(PERMIT_WITH_ADVICE, PERMIT_WITH_ADVICE);
        }

        @Test
        void whenProviderClaimsUnsupportedSignalThenObligationIsDenied() {
            ConstraintHandler.Runner  runner   = () -> {};
            ConstraintHandlerProvider provider = (constraint, supportedSignals) -> List
                    .of(new ScopedConstraintHandler(runner, ErrorSignal.SIGNAL_TYPE, 0));
            service.addGlobalConstraintHandlerProvider(provider);

            expectDeny(PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenProviderIsConsultedThenItSeesTheSupportedSignals() {
            var                       seen     = new AtomicReference<Set<SignalType>>();
            ConstraintHandlerProvider provider = (constraint, supportedSignals) -> {
                                                   seen.set(supportedSignals);
                                                   return List.of();
                                               };
            service.addGlobalConstraintHandlerProvider(provider);

            expectDecision(PERMIT_WITH_ADVICE, PERMIT_WITH_ADVICE);

            assertThat(seen.get()).isEqualTo(VaadinSignals.SUPPORTED_SIGNALS);
        }
    }

    @Nested
    @DisplayName("Vaadin functions")
    class VaadinFunctions {

        @Test
        void whenAllFunctionsAgreeThenDecisionIsReturned() {
            service.addGlobalVaadinFunctionProvider(functionProviderOf(ignored -> Mono.just(Boolean.TRUE)));
            when(pep.getLocalVaadinFunctionProvider())
                    .thenReturn(List.of(functionProviderOf(ignored -> Mono.just(Boolean.TRUE))));

            expectDecision(PERMIT_WITH_OBLIGATION, PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenFunctionReceivesTheUiThenItIsTheUiOfTheEnforcement() {
            var received = new AtomicReference<UI>();
            service.addGlobalVaadinFunctionProvider(functionProviderOf(current -> {
                received.set(current);
                return Mono.just(Boolean.TRUE);
            }));

            expectDecision(PERMIT_WITH_OBLIGATION, PERMIT_WITH_OBLIGATION);

            assertThat(received).hasValue(ui);
        }

        @Test
        void whenFunctionRunsAfterSignalHandlersThenSignalHandlersHaveRunFirst() {
            var                       order           = new StringBuilder();
            ConstraintHandler.Runner  runner          = () -> order.append("runner ");
            var                       otherConstraint = Value.of("other");
            ConstraintHandlerProvider provider        = (constraint,
                    supportedSignals) -> otherConstraint.equals(constraint)
                            ? List.of(new ScopedConstraintHandler(runner, DecisionSignal.SIGNAL_TYPE, 0))
                            : List.of();
            service.addGlobalConstraintHandlerProvider(provider);
            service.addGlobalVaadinFunctionProvider(functionProviderOf(ignored -> {
                order.append("function");
                return Mono.just(Boolean.TRUE);
            }));
            var twoObligations = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(CONSTRAINT, otherConstraint),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);

            expectDecision(twoObligations, twoObligations);

            assertThat(order).hasToString("runner function");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("disagreeingFunctions")
        void whenAnyFunctionDoesNotAgreeThenAccessIsDenied(String name, AuthorizationDecision decision,
                Function<UI, Mono<Boolean>> function) {
            service.addGlobalVaadinFunctionProvider(functionProviderOf(function));

            expectDeny(decision);
        }

        static Stream<Arguments> disagreeingFunctions() {
            return Stream.of(
                    arguments("obligation function returns false", PERMIT_WITH_OBLIGATION,
                            (Function<UI, Mono<Boolean>>) ignored -> Mono.just(Boolean.FALSE)),
                    arguments("advice function returns false", PERMIT_WITH_ADVICE,
                            (Function<UI, Mono<Boolean>>) ignored -> Mono.just(Boolean.FALSE)),
                    arguments("obligation function completes empty", PERMIT_WITH_OBLIGATION,
                            (Function<UI, Mono<Boolean>>) ignored -> Mono.empty()),
                    arguments("obligation function errors", PERMIT_WITH_OBLIGATION,
                            (Function<UI, Mono<Boolean>>) ignored -> Mono
                                    .error(new IllegalStateException("Function failed."))),
                    arguments("obligation function throws", PERMIT_WITH_OBLIGATION,
                            (Function<UI, Mono<Boolean>>) ignored -> {
                                throw new IllegalStateException("Function failed.");
                            }),
                    arguments("obligation function returns null", PERMIT_WITH_OBLIGATION,
                            (Function<UI, Mono<Boolean>>) ignored -> null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("failingAdviceFunctions")
        void whenAdviceFunctionFailsThenDecisionIsReturnedUnchanged(String name, Function<UI, Mono<Boolean>> function) {
            service.addGlobalVaadinFunctionProvider(functionProviderOf(function));

            expectDecision(PERMIT_WITH_ADVICE, PERMIT_WITH_ADVICE);
        }

        static Stream<Arguments> failingAdviceFunctions() {
            return Stream.of(
                    arguments("advice function errors",
                            (Function<UI, Mono<Boolean>>) ignored -> Mono
                                    .error(new IllegalStateException("Function failed."))),
                    arguments("advice function throws", (Function<UI, Mono<Boolean>>) ignored -> {
                        throw new IllegalStateException("Function failed.");
                    }), arguments("advice function returns null", (Function<UI, Mono<Boolean>>) ignored -> null));
        }

        @Test
        void whenProviderReturnsNoHandlerForObligationThenAccessIsDenied() {
            var provider = mock(VaadinFunctionConstraintHandlerProvider.class);
            when(provider.isResponsible(any())).thenReturn(true);
            when(provider.getHandler(any())).thenReturn(null);
            service.addGlobalVaadinFunctionProvider(provider);

            expectDeny(PERMIT_WITH_OBLIGATION);
        }

        @Test
        void whenProviderReturnsNoHandlerForAdviceThenDecisionIsReturnedUnchanged() {
            var provider = mock(VaadinFunctionConstraintHandlerProvider.class);
            when(provider.isResponsible(any())).thenReturn(true);
            when(provider.getHandler(any())).thenReturn(null);
            service.addGlobalVaadinFunctionProvider(provider);

            expectDecision(PERMIT_WITH_ADVICE, PERMIT_WITH_ADVICE);

            verify(provider).getHandler(CONSTRAINT);
        }

        @Test
        void whenFunctionAnswersLaterThenDecisionArrivesWithTheAnswer() {
            var answer = Mono.delay(Duration.ofMillis(50)).map(ignored -> Boolean.TRUE);
            service.addGlobalVaadinFunctionProvider(functionProviderOf(ignored -> answer));

            StepVerifier.create(service.enforceConstraintsOfDecision(PERMIT_WITH_OBLIGATION, ui, pep))
                    .expectNext(PERMIT_WITH_OBLIGATION).expectComplete().verify(TIMEOUT);
        }
    }

}
