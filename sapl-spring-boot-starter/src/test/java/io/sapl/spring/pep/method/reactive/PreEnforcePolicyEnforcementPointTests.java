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
package io.sapl.spring.pep.method.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandler.Consumer;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.AfterTerminationSignal;
import io.sapl.spring.pep.constraints.Signal.CancelSignal;
import io.sapl.spring.pep.constraints.Signal.CompleteSignal;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.Signal.SubscriptionSignal;
import io.sapl.spring.pep.constraints.Signal.TerminationSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end tests for the reactive @PreEnforce PEP. Mirrors the blocking
 * PreEnforce scenarios on the Mono path so the two PEPs are directly
 * comparable, and adds reactive-only groups for Mono empty handling, Mono&lt;
 * Void&gt;, Flux publisher-level OutputSignal dispatch, and the reactive
 * lifecycle signals (Subscription, Cancel, Complete, Terminate,
 * AfterTerminate).
 * <p>
 * Same Ankh-Morpork City Watch scenario as the blocking twin: the Watch
 * (PEP) consults the Patrician (PDP) before acting on warrants.
 */
@SpringBootTest(classes = PreEnforcePolicyEnforcementPointTests.AnkhMorporkTestApp.class)
@WithMockUser(username = "vimes", roles = "WATCH_COMMANDER")
class PreEnforcePolicyEnforcementPointTests {

    private static final String INNOCENT_SUSPECT_INITIALLY_NAMED = "Lord Vetinari";
    private static final String CARCER_DUN_THE_REAL_CULPRIT      = "Carcer Dun";
    private static final String ARREST_PREFIX                    = "Arrested: ";

    private static final String CASE_FILE_BODY     = "Witness saw a man in black on the Brass Bridge.";
    private static final String CASE_FILE_REDACTED = "[CASE FILE SEALED BY THE PATRICIAN]";

    private static final String DEATH_CLAIMS_THE_EXCEPTION = "DEATH HAS CLAIMED YOUR EXCEPTION";

    private static final String PRE_INVOCATION_MESSAGE_FRAGMENT  = "pre-invocation";
    private static final String POST_INVOCATION_MESSAGE_FRAGMENT = "post-invocation";

    private static final String CASE_LURKING_LIONS     = "Case 1: lurking lions outside the menagerie";
    private static final String CASE_STOLEN_TURNIP     = "Case 2: stolen turnip in Sator Square";
    private static final String CASE_VANISHED_ASSASSIN = "Case 3: vanished assassin from the Guild";

    private static final List<String> OPEN_CASES = List.of(CASE_LURKING_LIONS, CASE_STOLEN_TURNIP,
            CASE_VANISHED_ASSASSIN);

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    WatchHouse watch;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    PatricianLogbook logbook;

    @BeforeEach
    void resetLogbook() {
        logbook.reset();
    }

    @Nested
    @DisplayName("Mono - permitted requests without obligations")
    class MonoPermittedWithoutObligations {

        @Test
        @DisplayName("Permit returns the suspect named in the original warrant")
        void whenPermitAndScalarThenReturnsArrestedSuspect() {
            permit();

            StepVerifier.create(watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED))
                    .expectNext(ARREST_PREFIX + INNOCENT_SUSPECT_INITIALLY_NAMED).verifyComplete();
        }

        @Test
        @DisplayName("Permit returns the case file verbatim")
        void whenPermitAndZeroArgScalarThenReturnsCaseFile() {
            permit();

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
        }

        @Test
        @DisplayName("Permit returns the open-cases list unchanged")
        void whenPermitAndContainerThenReturnsOpenCases() {
            permit();

            StepVerifier.create(watch.listOpenCasesMono()).expectNext(OPEN_CASES).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Mono - resource substitution")
    class MonoResourceSubstitution {

        @Test
        @DisplayName("Resource value replaces the scalar arrest report")
        void whenResourcePresentThenReplacesArrestReport() {
            decide(decisionWithResource(Value.of(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT)));

            StepVerifier.create(watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED))
                    .expectNext(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT).verifyComplete();
        }

        @Test
        @DisplayName("Resource value materialises in place of an empty Mono")
        void whenEmptyMonoAndResourcePresentThenMaterialises() {
            decide(decisionWithResource(Value.of(CASE_FILE_REDACTED)));

            StepVerifier.create(watch.confiscateLostScroll()).expectNext(CASE_FILE_REDACTED).verifyComplete();
        }

        @Test
        @DisplayName("DENY with a substitute resource still denies")
        void whenDenyWithResourceThenStillAccessDenied() {
            decide(new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(CASE_FILE_REDACTED)));

            StepVerifier.create(watch.openCaseFile()).expectErrorSatisfies(
                    err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining("not PERMIT"))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Mono - decisions other than PERMIT")
    class MonoDenialDecisions {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            decide(AuthorizationDecision.DENY);

            assertAccessDenied(watch.openCaseFile(), "DENY");
        }

        @Test
        @DisplayName("INDETERMINATE raises AccessDeniedException")
        void whenIndeterminateThenAccessDenied() {
            decide(AuthorizationDecision.INDETERMINATE);

            assertAccessDenied(watch.openCaseFile(), "INDETERMINATE");
        }

        @Test
        @DisplayName("NOT_APPLICABLE raises AccessDeniedException")
        void whenNotApplicableThenAccessDenied() {
            decide(AuthorizationDecision.NOT_APPLICABLE);

            assertAccessDenied(watch.openCaseFile(), "NOT_APPLICABLE");
        }
    }

    @Nested
    @DisplayName("Mono - signal handlers fire on every applicable signal")
    class MonoSignalHandlers {

        @Test
        @DisplayName("DecisionSignal Runner: the Patrician countersigns each warrant")
        void whenDecisionRunnerObligationThenPatricianCountersigns() {
            decide(decisionWithObligation(Obligation.PATRICIAN_COUNTERSIGNS));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.patricianCountersignatures).hasValue(1);
        }

        @Test
        @DisplayName("InputSignal Runner: Sgt. Colon notes the request before dispatch")
        void whenInputRunnerObligationThenColonNotes() {
            decide(decisionWithObligation(Obligation.COLON_NOTES_REQUEST));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.colonNotes).hasValue(1);
        }

        @Test
        @DisplayName("InputSignal Consumer: Carrot inspects the warrant for irregularities")
        void whenInputConsumerObligationThenCarrotInspects() {
            decide(decisionWithObligation(Obligation.CARROT_INSPECTS_WARRANT));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.carrotInspections).hasValue(1);
        }

        @Test
        @DisplayName("InputSignal Mapper: Nobby rewrites the suspect name on the warrant before the arrest")
        void whenInputMapperObligationThenNobbyRewritesArrestTarget() {
            decide(decisionWithObligation(Obligation.NOBBY_REWRITES_WARRANT));

            StepVerifier.create(watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED))
                    .expectNext(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT).verifyComplete();
        }

        @Test
        @DisplayName("OutputSignal Runner: Angua audits each closed case")
        void whenOutputRunnerObligationThenAnguaAudits() {
            decide(decisionWithObligation(Obligation.ANGUA_AUDITS_OUTPUT));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.anguaAudits).hasValue(1);
        }

        @Test
        @DisplayName("OutputSignal Consumer: Detritus eyeballs the report")
        void whenOutputConsumerObligationThenDetritusObserves() {
            decide(decisionWithObligation(Obligation.DETRITUS_EYEBALLS_OUTPUT));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.lastDetritusObservation).hasValue(CASE_FILE_BODY);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the Librarian redacts the case file before release")
        void whenOutputMapperObligationThenLibrarianRedacts() {
            decide(decisionWithObligation(Obligation.LIBRARIAN_REDACTS_OUTPUT));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_REDACTED).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Mono - pre-invocation obligation failures")
    class MonoPreInvocationFailures {

        @Test
        @DisplayName("DecisionSignal obligation failure: access denied (pre-invocation)")
        void whenDecisionObligationFailsThenAccessDeniedPreInvocation() {
            decide(decisionWithObligation(Obligation.BURSAR_COLLAPSES_DECISION));

            assertAccessDenied(watch.openCaseFile(), PRE_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("InputSignal obligation failure: access denied (pre-invocation)")
        void whenInputObligationFailsThenAccessDeniedPreInvocation() {
            decide(decisionWithObligation(Obligation.NOBBY_LOSES_PAPERWORK_INPUT));

            assertAccessDenied(watch.openCaseFile(), PRE_INVOCATION_MESSAGE_FRAGMENT);
        }
    }

    @Nested
    @DisplayName("Mono - post-invocation obligation failures")
    class MonoPostInvocationFailures {

        @Test
        @DisplayName("OutputSignal Mapper failure: access denied (post-invocation)")
        void whenOutputMapperObligationFailsThenAccessDeniedPostInvocation() {
            decide(decisionWithObligation(Obligation.CARROT_DROPS_EVIDENCE_MAPPER));

            assertAccessDenied(watch.openCaseFile(), POST_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("OutputSignal Consumer failure: access denied")
        void whenOutputConsumerObligationFailsThenAccessDenied() {
            decide(decisionWithObligation(Obligation.RINCEWIND_PANICS_CONSUMER));

            StepVerifier.create(watch.openCaseFile())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Mono - advice handlers are noted but never bar action")
    class MonoAdviceHandlers {

        @Test
        @DisplayName("Failing advice does not deny access")
        void whenAdviceFailsThenAccessStillPermitted() {
            decide(decisionWithAdvice(Obligation.BURSAR_COLLAPSES_DECISION));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Mono - ErrorSignal handlers transform errors before they leave the Watch House")
    class MonoErrorSignalHandlers {

        @Test
        @DisplayName("ErrorSignal Mapper: Death personally claims the exception")
        void whenErrorSignalMapperOnDenyThenMappedExceptionPropagates() {
            decide(decisionWithObligationAndDeny(Obligation.DEATH_CLAIMS_EXCEPTION));

            StepVerifier
                    .create(watch.openCaseFile()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining(DEATH_CLAIMS_THE_EXCEPTION))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("ErrorSignal Runner: the Patrician's clerks log every failed warrant")
        void whenErrorSignalRunnerObligationOnDenyThenClerksLog() {
            decide(new AuthorizationDecision(Decision.DENY, arrayOf(Obligation.CLERKS_LOG_FAILED_WARRANT),
                    Value.EMPTY_ARRAY, Value.UNDEFINED));

            StepVerifier.create(watch.openCaseFile())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
            assertThat(logbook.clerkErrorLogs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Mono - RAP exceptions: routed through ErrorSignal because the plan exists")
    class MonoRapExceptions {

        @Test
        @DisplayName("RAP throws synchronously; without an ErrorSignal Mapper the original exception escapes")
        void whenRapThrowsAndNoErrorMapperThenOriginalExceptionPropagates() {
            permit();

            StepVerifier
                    .create(watch.provokeTheLuggage()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Luggage bites"))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("RAP throws synchronously; an ErrorSignal Mapper rewrites it on the way out")
        void whenRapThrowsAndErrorMapperPresentThenMappedExceptionPropagates() {
            decide(decisionWithObligation(Obligation.DEATH_CLAIMS_EXCEPTION));

            StepVerifier
                    .create(watch.provokeTheLuggage()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining(DEATH_CLAIMS_THE_EXCEPTION))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("RAP returns Mono.error; an ErrorSignal Mapper rewrites it on the way out")
        void whenRapReturnsMonoErrorAndErrorMapperPresentThenMapped() {
            decide(decisionWithObligation(Obligation.DEATH_CLAIMS_EXCEPTION));

            StepVerifier
                    .create(watch.invokeNuggan()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining(DEATH_CLAIMS_THE_EXCEPTION))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Mono - empty Mono is still authorised")
    class MonoEmpty {

        @Test
        @DisplayName("Empty Mono consults the PDP and applies output-side obligations")
        void whenEmptyMonoThenPdpConsultedAndObligationsApply() {
            decide(decisionWithObligation(Obligation.ANGUA_AUDITS_OUTPUT));

            StepVerifier.create(watch.confiscateLostScroll()).verifyComplete();

            verify(pdp).decideOnce(any());
            assertThat(logbook.anguaAudits).hasValue(1);
        }

        @Test
        @DisplayName("Empty Mono with DENY raises AccessDeniedException")
        void whenEmptyMonoAndDenyThenAccessDenied() {
            decide(AuthorizationDecision.DENY);

            assertAccessDenied(watch.confiscateLostScroll(), "DENY");
        }
    }

    @Nested
    @DisplayName("Mono<Void> - escort rite is authorised even when no value is emitted")
    class MonoVoidEscortRite {

        @Test
        @DisplayName("PERMIT: the rite runs and the PDP was consulted")
        void whenMonoVoidPermitThenSideEffectAndPdp() {
            permit();

            StepVerifier.create(watch.escortDrumknottHome()).verifyComplete();

            verify(pdp).decideOnce(any());
            assertThat(logbook.escortRiteSideEffects).hasValue(1);
        }

        @Test
        @DisplayName("DENY: AccessDeniedException; the side effect did not run because PreEnforce gates the proceed call")
        void whenMonoVoidDenyThenAccessDeniedAndNoSideEffect() {
            decide(AuthorizationDecision.DENY);

            assertAccessDenied(watch.escortDrumknottHome(), "DENY");
            assertThat(logbook.escortRiteSideEffects).hasValue(0);
        }

        @Test
        @DisplayName("Mono<Void> skips OutputSignal Mappers (Maybe.absent has no value to transform)")
        void whenMonoVoidAndOutputMapperObligationThenMapperSkips() {
            decide(decisionWithObligation(Obligation.LIBRARIAN_REDACTS_OUTPUT));

            StepVerifier.create(watch.escortDrumknottHome()).verifyComplete();
            // If the Mapper had run, it would have produced CASE_FILE_REDACTED.
        }

        @Test
        @DisplayName("Mono<Void> fires OutputSignal Runners")
        void whenMonoVoidAndOutputRunnerObligationThenRunnerFires() {
            decide(decisionWithObligation(Obligation.ANGUA_AUDITS_OUTPUT));

            StepVerifier.create(watch.escortDrumknottHome()).verifyComplete();
            assertThat(logbook.anguaAudits).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Flux - publisher-level OutputSignal dispatch")
    class FluxScenarios {

        @Test
        @DisplayName("Permit emits all items unchanged")
        void whenPermitAndFluxThenAllItemsEmitted() {
            permit();

            StepVerifier.create(watch.patrolBeats())
                    .expectNext(CASE_LURKING_LIONS, CASE_STOLEN_TURNIP, CASE_VANISHED_ASSASSIN).verifyComplete();
        }

        @Test
        @DisplayName("DENY raises AccessDeniedException without emitting any items")
        void whenDenyAndFluxThenAccessDenied() {
            decide(AuthorizationDecision.DENY);

            StepVerifier.create(watch.patrolBeats())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("OutputSignal Mapper receives the entire Flux and may transform the stream as a whole")
        void whenFluxOutputMapperThenReceivesEntirePublisher() {
            decide(decisionWithObligation(Obligation.WIZARDS_FILTER_PATROL_FLUX));

            StepVerifier.create(watch.patrolBeats()).expectNext(CASE_VANISHED_ASSASSIN).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Flux - jsonContentFilterPredicate via the production ContentFilterPredicateProvider")
    class FluxContentFilterPredicate {

        @Test
        @DisplayName("Conditional filter (dangerLevel >= 5) drops three of five suspects from the Flux")
        void whenJsonContentFilterPredicateThenElementsAreFilteredFromFlux() {
            val obligation = Value.ofObject(
                    Map.of("type", Value.of("jsonContentFilterPredicate"), "conditions", Value.ofArray(Value.ofObject(
                            Map.of("path", Value.of("$.dangerLevel"), "type", Value.of(">="), "value", Value.of(5))))));
            decide(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                    Value.UNDEFINED));

            StepVerifier.create(watch.dangerousSuspects()).expectNext(new Suspect("Carcer Dun", 9))
                    .expectNext(new Suspect("Mr Pin", 7)).expectNext(new Suspect("Mr Tulip", 8)).verifyComplete();
        }

        @Test
        @DisplayName("Same Flux without the predicate emits all five suspects unchanged")
        void whenNoPredicateThenAllSuspectsEmitted() {
            permit();

            StepVerifier.create(watch.dangerousSuspects()).expectNext(new Suspect("Carcer Dun", 9))
                    .expectNext(new Suspect("Mr Pin", 7)).expectNext(new Suspect("Mr Tulip", 8))
                    .expectNext(new Suspect("Foul Ole Ron", 1)).expectNext(new Suspect("CMOT Dibbler", 2))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Lifecycle signals: subscription, cancel, complete, terminate, after-terminate")
    class LifecycleSignals {

        @Test
        @DisplayName("SubscriptionSignal Runner fires on subscribe")
        void whenSubscriptionRunnerObligationThenFiresOnSubscribe() {
            decide(decisionWithObligation(Obligation.SUBSCRIPTION_RUNNER));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.subscriptionRuns).hasValue(1);
        }

        @Test
        @DisplayName("CompleteSignal Runner fires on successful completion")
        void whenCompleteRunnerObligationThenFiresOnComplete() {
            decide(decisionWithObligation(Obligation.COMPLETE_RUNNER));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.completeRuns).hasValue(1);
        }

        @Test
        @DisplayName("TerminateSignal Runner fires regardless of completion or error")
        void whenTerminateRunnerObligationThenFiresOnTerminate() {
            decide(decisionWithObligation(Obligation.TERMINATE_RUNNER));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.terminateRuns).hasValue(1);
        }

        @Test
        @DisplayName("AfterTerminateSignal Runner fires after termination")
        void whenAfterTerminateRunnerObligationThenFiresAfterTerminate() {
            decide(decisionWithObligation(Obligation.AFTER_TERMINATE_RUNNER));

            StepVerifier.create(watch.openCaseFile()).expectNext(CASE_FILE_BODY).verifyComplete();
            assertThat(logbook.afterTerminateRuns).hasValue(1);
        }

        @Test
        @DisplayName("CancelSignal Runner fires when the subscriber cancels")
        void whenCancelRunnerObligationThenFiresOnCancel() {
            decide(decisionWithObligation(Obligation.CANCEL_RUNNER));

            StepVerifier.create(watch.patrolBeats(), 1).expectNext(CASE_LURKING_LIONS).thenCancel()
                    .verify(STEP_TIMEOUT);
            assertThat(logbook.cancelRuns).hasValue(1);
        }
    }

    private void permit() {
        decide(AuthorizationDecision.PERMIT);
    }

    private void decide(AuthorizationDecision decision) {
        when(pdp.decideOnce(any())).thenReturn(Mono.just(decision));
    }

    private static void assertAccessDenied(Mono<?> publisher, String messageFragment) {
        StepVerifier.create(publisher).expectErrorSatisfies(
                err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining(messageFragment))
                .verify(STEP_TIMEOUT);
    }

    private static AuthorizationDecision decisionWithResource(Value resource) {
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, resource);
    }

    private static AuthorizationDecision decisionWithObligation(String type) {
        return new AuthorizationDecision(Decision.PERMIT, arrayOf(type), Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    private static AuthorizationDecision decisionWithAdvice(String type) {
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, arrayOf(type), Value.UNDEFINED);
    }

    private static AuthorizationDecision decisionWithObligationAndDeny(String type) {
        return new AuthorizationDecision(Decision.DENY, arrayOf(type), Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    private static ArrayValue arrayOf(String type) {
        return Value.ofArray(constraint(type));
    }

    private static ObjectValue constraint(String type) {
        return Value.ofObject(Map.of("type", Value.of(type)));
    }

    @Service
    static class WatchHouse {

        private final PatricianLogbook logbook;

        WatchHouse(PatricianLogbook logbook) {
            this.logbook = logbook;
        }

        @PreEnforce
        public Mono<String> arrestSuspect(String suspectName) {
            return Mono.just(ARREST_PREFIX + suspectName);
        }

        @PreEnforce
        public Mono<String> openCaseFile() {
            return Mono.just(CASE_FILE_BODY);
        }

        @PreEnforce
        public Mono<String> confiscateLostScroll() {
            return Mono.empty();
        }

        @PreEnforce
        public Mono<List<String>> listOpenCasesMono() {
            return Mono.just(OPEN_CASES);
        }

        @PreEnforce
        public Mono<Void> escortDrumknottHome() {
            return Mono.fromRunnable(logbook.escortRiteSideEffects::incrementAndGet);
        }

        @PreEnforce
        public Mono<String> provokeTheLuggage() {
            throw new IllegalStateException("the Luggage bites the rite and the rite is consumed");
        }

        @PreEnforce
        public Mono<String> invokeNuggan() {
            return Mono.error(new IllegalStateException("Nuggan smites the unbeliever"));
        }

        @PreEnforce
        public Flux<String> patrolBeats() {
            return Flux.just(CASE_LURKING_LIONS, CASE_STOLEN_TURNIP, CASE_VANISHED_ASSASSIN);
        }

        @PreEnforce
        public Flux<Suspect> dangerousSuspects() {
            return Flux.just(new Suspect("Carcer Dun", 9), new Suspect("Mr Pin", 7), new Suspect("Mr Tulip", 8),
                    new Suspect("Foul Ole Ron", 1), new Suspect("CMOT Dibbler", 2));
        }
    }

    public record Suspect(String name, int dangerLevel) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
    static class AnkhMorporkTestApp {

        @Bean
        WatchHouse watchHouse(PatricianLogbook logbook) {
            return new WatchHouse(logbook);
        }

        @Bean
        PatricianLogbook patricianLogbook() {
            return new PatricianLogbook();
        }

        @Bean
        TestConstraintHandlerProvider testConstraintHandlerProvider(PatricianLogbook logbook) {
            return new TestConstraintHandlerProvider(logbook);
        }
    }

    static final class Obligation {
        static final String PATRICIAN_COUNTERSIGNS       = "ankhmorpork:patricianCountersignsDecision";
        static final String COLON_NOTES_REQUEST          = "ankhmorpork:colonNotesRequestInput";
        static final String CARROT_INSPECTS_WARRANT      = "ankhmorpork:carrotInspectsWarrantInput";
        static final String NOBBY_REWRITES_WARRANT       = "ankhmorpork:nobbyRewritesWarrantInput";
        static final String ANGUA_AUDITS_OUTPUT          = "ankhmorpork:anguaAuditsOutput";
        static final String DETRITUS_EYEBALLS_OUTPUT     = "ankhmorpork:detritusEyeballsOutput";
        static final String LIBRARIAN_REDACTS_OUTPUT     = "ankhmorpork:librarianRedactsOutput";
        static final String BURSAR_COLLAPSES_DECISION    = "ankhmorpork:bursarCollapsesDecision";
        static final String NOBBY_LOSES_PAPERWORK_INPUT  = "ankhmorpork:nobbyLosesPaperworkInput";
        static final String CARROT_DROPS_EVIDENCE_MAPPER = "ankhmorpork:carrotDropsEvidenceMapper";
        static final String RINCEWIND_PANICS_CONSUMER    = "ankhmorpork:rincewindPanicsConsumer";
        static final String DEATH_CLAIMS_EXCEPTION       = "ankhmorpork:deathClaimsException";
        static final String CLERKS_LOG_FAILED_WARRANT    = "ankhmorpork:clerksLogFailedWarrant";
        static final String WIZARDS_FILTER_PATROL_FLUX   = "ankhmorpork:wizardsFilterPatrolFlux";
        static final String SUBSCRIPTION_RUNNER          = "ankhmorpork:dragonsBreathOnSubscribe";
        static final String CANCEL_RUNNER                = "ankhmorpork:rincewindRunsOnCancel";
        static final String COMPLETE_RUNNER              = "ankhmorpork:librarianMarksComplete";
        static final String TERMINATE_RUNNER             = "ankhmorpork:bursarSighsOnTerminate";
        static final String AFTER_TERMINATE_RUNNER       = "ankhmorpork:vetinariReadsAfterTerminate";

        private Obligation() {
            // constants only
        }
    }

    static class PatricianLogbook {

        final AtomicInteger           patricianCountersignatures = new AtomicInteger();
        final AtomicInteger           colonNotes                 = new AtomicInteger();
        final AtomicInteger           carrotInspections          = new AtomicInteger();
        final AtomicInteger           anguaAudits                = new AtomicInteger();
        final AtomicInteger           clerkErrorLogs             = new AtomicInteger();
        final AtomicInteger           escortRiteSideEffects      = new AtomicInteger();
        final AtomicInteger           subscriptionRuns           = new AtomicInteger();
        final AtomicInteger           cancelRuns                 = new AtomicInteger();
        final AtomicInteger           completeRuns               = new AtomicInteger();
        final AtomicInteger           terminateRuns              = new AtomicInteger();
        final AtomicInteger           afterTerminateRuns         = new AtomicInteger();
        final AtomicReference<Object> lastDetritusObservation    = new AtomicReference<>();

        void reset() {
            patricianCountersignatures.set(0);
            colonNotes.set(0);
            carrotInspections.set(0);
            anguaAudits.set(0);
            clerkErrorLogs.set(0);
            escortRiteSideEffects.set(0);
            subscriptionRuns.set(0);
            cancelRuns.set(0);
            completeRuns.set(0);
            terminateRuns.set(0);
            afterTerminateRuns.set(0);
            lastDetritusObservation.set(null);
        }
    }

    static class TestConstraintHandlerProvider implements ConstraintHandlerProvider {

        private static final String WARRANT_HAS_COLLAPSED = "the warrant has collapsed";

        private final PatricianLogbook logbook;

        TestConstraintHandlerProvider(PatricianLogbook logbook) {
            this.logbook = logbook;
        }

        @Override
        public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
            if (!(constraint instanceof ObjectValue obj)) {
                return List.of();
            }
            if (!(obj.get("type") instanceof TextValue(String type))) {
                return List.of();
            }
            return switch (type) {
            case Obligation.PATRICIAN_COUNTERSIGNS       ->
                List.of(new ScopedConstraintHandler((Runner) logbook.patricianCountersignatures::incrementAndGet,
                        DecisionSignal.SIGNAL_TYPE, 0));
            case Obligation.COLON_NOTES_REQUEST          ->
                List.of(new ScopedConstraintHandler((Runner) logbook.colonNotes::incrementAndGet,
                        InputSignal.SIGNAL_TYPE, 0));
            case Obligation.CARROT_INSPECTS_WARRANT      -> List.of(new ScopedConstraintHandler(
                    (Consumer<MethodInvocation>) ignored -> logbook.carrotInspections.incrementAndGet(),
                    InputSignal.SIGNAL_TYPE, 0));
            case Obligation.NOBBY_REWRITES_WARRANT       ->
                List.of(new ScopedConstraintHandler((Mapper<MethodInvocation>) inv -> {
                                                             if (inv.getArguments().length > 0) {
                                                                 inv.getArguments()[0] = CARCER_DUN_THE_REAL_CULPRIT;
                                                             }
                                                             return inv;
                                                         },
                        InputSignal.SIGNAL_TYPE, 0));
            case Obligation.ANGUA_AUDITS_OUTPUT          ->
                outputAt(supportedSignals, (Runner) logbook.anguaAudits::incrementAndGet);
            case Obligation.DETRITUS_EYEBALLS_OUTPUT     ->
                outputAt(supportedSignals, (Consumer<Object>) logbook.lastDetritusObservation::set);
            case Obligation.LIBRARIAN_REDACTS_OUTPUT     ->
                outputAt(supportedSignals, (Mapper<Object>) ignored -> CASE_FILE_REDACTED);
            case Obligation.BURSAR_COLLAPSES_DECISION    -> List.of(new ScopedConstraintHandler((Runner) () -> {
                                                         throw new IllegalStateException(WARRANT_HAS_COLLAPSED);
                                                     },
                    DecisionSignal.SIGNAL_TYPE, 0));
            case Obligation.NOBBY_LOSES_PAPERWORK_INPUT  -> List.of(new ScopedConstraintHandler((Runner) () -> {
                                                         throw new IllegalStateException(WARRANT_HAS_COLLAPSED);
                                                     },
                    InputSignal.SIGNAL_TYPE, 0));
            case Obligation.CARROT_DROPS_EVIDENCE_MAPPER -> outputAt(supportedSignals, (Mapper<Object>) ignored -> {
                                                         throw new IllegalStateException(WARRANT_HAS_COLLAPSED);
                                                     });
            case Obligation.RINCEWIND_PANICS_CONSUMER    -> outputAt(supportedSignals, (Consumer<Object>) ignored -> {
                                                         throw new IllegalStateException(WARRANT_HAS_COLLAPSED);
                                                     });
            case Obligation.DEATH_CLAIMS_EXCEPTION       -> List.of(new ScopedConstraintHandler(
                    (Mapper<Throwable>) ignored -> new IllegalStateException(DEATH_CLAIMS_THE_EXCEPTION),
                    ErrorSignal.SIGNAL_TYPE, 0));
            case Obligation.CLERKS_LOG_FAILED_WARRANT    ->
                List.of(new ScopedConstraintHandler((Runner) logbook.clerkErrorLogs::incrementAndGet,
                        ErrorSignal.SIGNAL_TYPE, 0));
            case Obligation.WIZARDS_FILTER_PATROL_FLUX   -> outputAt(supportedSignals,
                    (Mapper<Object>) flux -> ((Flux<?>) flux).filter(CASE_VANISHED_ASSASSIN::equals));
            case Obligation.SUBSCRIPTION_RUNNER          ->
                List.of(new ScopedConstraintHandler((Runner) logbook.subscriptionRuns::incrementAndGet,
                        SubscriptionSignal.SIGNAL_TYPE, 0));
            case Obligation.CANCEL_RUNNER                ->
                List.of(new ScopedConstraintHandler((Runner) logbook.cancelRuns::incrementAndGet,
                        CancelSignal.SIGNAL_TYPE, 0));
            case Obligation.COMPLETE_RUNNER              ->
                List.of(new ScopedConstraintHandler((Runner) logbook.completeRuns::incrementAndGet,
                        CompleteSignal.SIGNAL_TYPE, 0));
            case Obligation.TERMINATE_RUNNER             ->
                List.of(new ScopedConstraintHandler((Runner) logbook.terminateRuns::incrementAndGet,
                        TerminationSignal.SIGNAL_TYPE, 0));
            case Obligation.AFTER_TERMINATE_RUNNER       ->
                List.of(new ScopedConstraintHandler((Runner) logbook.afterTerminateRuns::incrementAndGet,
                        AfterTerminationSignal.SIGNAL_TYPE, 0));
            default                                      -> List.of();
            };
        }

        private static List<ScopedConstraintHandler> outputAt(Set<SignalType> supportedSignals,
                ConstraintHandler<?> handler) {
            for (val s : supportedSignals) {
                if (s instanceof ValueSignalType<?> v && OutputSignal.class.equals(v.type())) {
                    return List.of(new ScopedConstraintHandler(handler, s, 30));
                }
            }
            return List.of();
        }
    }
}
