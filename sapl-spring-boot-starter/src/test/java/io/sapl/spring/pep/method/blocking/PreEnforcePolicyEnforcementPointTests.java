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
package io.sapl.spring.pep.method.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandler.Consumer;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.InputSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;

/**
 * End-to-end tests for the blocking @PreEnforce PEP.
 * <p>
 * The scenario is the Ankh-Morpork City Watch handling action requests on
 * suspects. Before the Watch (the PEP) acts on a warrant, the Patrician's
 * office (the PDP) decides whether the request is sanctioned, what must be
 * done, and on whose authority. Sgt. Colon takes notes, Carrot may rewrite
 * the warrant to name the actual perpetrator, the Librarian redacts the
 * resulting case file, and Death himself sometimes claims an exception on its
 * way out.
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

    private static final String SGT_COLON  = "Colon";
    private static final String CPL_NOBBS  = "Nobbs";
    private static final String CPT_CARROT = "Carrot";

    private static final List<String>         OPEN_CASES     = List.of(CASE_LURKING_LIONS, CASE_STOLEN_TURNIP,
            CASE_VANISHED_ASSASSIN);
    private static final Map<String, Integer> WATCH_SALARIES = Map.of(SGT_COLON, 30, CPL_NOBBS, 25, CPT_CARROT, 35);

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
    @DisplayName("Permitted requests without obligations: the warrant is executed unchanged")
    class PermittedWithoutObligations {

        @Test
        @DisplayName("Permit returns the suspect named in the original warrant")
        void whenPermitAndScalarThenReturnsArrestedSuspect() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED);

            assertThat(result).isEqualTo(ARREST_PREFIX + INNOCENT_SUSPECT_INITIALLY_NAMED);
        }

        @Test
        @DisplayName("Permit returns the case file verbatim")
        void whenPermitAndZeroArgScalarThenReturnsCaseFile() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
        }

        @Test
        @DisplayName("Permit returns the full open-cases list")
        void whenPermitAndListThenReturnsOpenCases() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = watch.listOpenCases();

            assertThat(result).containsExactlyElementsOf(OPEN_CASES);
        }

        @Test
        @DisplayName("Permit returns the unaltered Watch salary roll")
        void whenPermitAndMapThenReturnsSalaryRoll() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = watch.accountWatchSalaries();

            assertThat(result).containsAllEntriesOf(WATCH_SALARIES);
        }

        @Test
        @DisplayName("Permit returns null when the requested scroll has been misplaced by Nobby")
        void whenPermitAndMissingScrollThenReturnsNull() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = watch.confiscateLostScroll();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Permit allows escorting Drumknott home; the rite returns nothing")
        void whenPermitAndVoidThenEscortCompletes() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            watch.escortDrumknottHome();

            // PreEnforce on void still consults the PDP, satisfied by the mock.
            verify(pdp).decideOnceBlocking(any());
        }
    }

    @Nested
    @DisplayName("The Patrician substitutes the resource: a different name is handed back")
    class ResourceSubstitution {

        @Test
        @DisplayName("Resource value replaces the scalar arrest report")
        void whenResourcePresentThenReplacesArrestReport() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithResource(Value.of(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT)));

            val result = watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED);

            assertThat(result).isEqualTo(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT);
        }

        @Test
        @DisplayName("Resource value materialises in place of a misplaced scroll")
        void whenNullRapAndResourcePresentThenMaterialises() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(Value.of(CASE_FILE_REDACTED)));

            val result = watch.confiscateLostScroll();

            assertThat(result).isEqualTo(CASE_FILE_REDACTED);
        }

        @Test
        @DisplayName("Resource list replaces the entire open-cases ledger")
        void whenResourceListPresentThenReplacesLedger() {
            val replacement = Value.ofArray(Value.of(CASE_VANISHED_ASSASSIN));
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(replacement));

            val result = watch.listOpenCases();

            assertThat(result).containsExactly(CASE_VANISHED_ASSASSIN);
        }

        @Test
        @DisplayName("Incompatible resource type fails as access denied (post-invocation)")
        void whenResourceTypeIsIncompatibleThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(Value.of("not-a-number")));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::countAvailableConstables)
                    .withMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("DENY with a substitute resource still denies; substitution cannot save a denial")
        void whenDenyWithResourceThenStillAccessDenied() {
            val deny = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(CASE_FILE_REDACTED));
            when(pdp.decideOnceBlocking(any())).thenReturn(deny);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("not PERMIT");
        }

        @Test
        @DisplayName("SUSPEND with a substitute resource still denies; the Watch cannot pause, so substitution cannot save a suspension")
        void whenSuspendWithResourceThenStillAccessDenied() {
            val suspend = new AuthorizationDecision(Decision.SUSPEND, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(CASE_FILE_REDACTED));
            when(pdp.decideOnceBlocking(any())).thenReturn(suspend);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("not PERMIT");
        }
    }

    @Nested
    @DisplayName("Decisions other than PERMIT: the Patrician declines to authorise")
    class DenialDecisions {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("DENY");
        }

        @Test
        @DisplayName("INDETERMINATE raises AccessDeniedException")
        void whenIndeterminateThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.INDETERMINATE);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("INDETERMINATE");
        }

        @Test
        @DisplayName("NOT_APPLICABLE raises AccessDeniedException")
        void whenNotApplicableThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.NOT_APPLICABLE);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("NOT_APPLICABLE");
        }

        @Test
        @DisplayName("SUSPEND raises AccessDeniedException; one-shot PEPs cannot suspend, so the warrant is refused outright")
        void whenSuspendThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.SUSPEND);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining("SUSPEND");
        }
    }

    @Nested
    @DisplayName("DecisionSignal handlers fire before the warrant is executed")
    class DecisionSignalHandlers {

        @Test
        @DisplayName("DecisionSignal Runner: the Patrician countersigns each warrant before it is acted upon")
        void whenDecisionRunnerObligationThenPatricianCountersigns() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.PATRICIAN_COUNTERSIGNS));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
            assertThat(logbook.patricianCountersignatures).hasValue(1);
        }
    }

    @Nested
    @DisplayName("InputSignal handlers fire on the way in: arguments may be observed or rewritten")
    class InputSignalHandlers {

        @Test
        @DisplayName("InputSignal Runner: Sgt. Colon notes the request before it is dispatched")
        void whenInputRunnerObligationThenColonNotes() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.COLON_NOTES_REQUEST));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
            assertThat(logbook.colonNotes).hasValue(1);
        }

        @Test
        @DisplayName("InputSignal Consumer: Carrot inspects the warrant for irregularities")
        void whenInputConsumerObligationThenCarrotInspects() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.CARROT_INSPECTS_WARRANT));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
            assertThat(logbook.carrotInspections).hasValue(1);
        }

        @Test
        @DisplayName("InputSignal Mapper: Nobby rewrites the suspect name on the warrant before the arrest")
        void whenInputMapperObligationThenNobbyRewritesArrestTarget() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.NOBBY_REWRITES_WARRANT));

            val result = watch.arrestSuspect(INNOCENT_SUSPECT_INITIALLY_NAMED);

            assertThat(result).isEqualTo(ARREST_PREFIX + CARCER_DUN_THE_REAL_CULPRIT);
        }
    }

    @Nested
    @DisplayName("OutputSignal handlers fire on the way out: result may be observed or transformed")
    class OutputSignalHandlers {

        @Test
        @DisplayName("OutputSignal Runner: Angua audits each closed case")
        void whenOutputRunnerObligationThenAnguaAudits() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.ANGUA_AUDITS_OUTPUT));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
            assertThat(logbook.anguaAudits).hasValue(1);
        }

        @Test
        @DisplayName("OutputSignal Consumer: Detritus eyeballs the report on its way out")
        void whenOutputConsumerObligationThenDetritusObserves() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.DETRITUS_EYEBALLS_OUTPUT));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
            assertThat(logbook.lastDetritusObservation).hasValue(CASE_FILE_BODY);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the Unseen University Librarian redacts the case file before release")
        void whenOutputMapperObligationThenLibrarianRedacts() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.LIBRARIAN_REDACTS_OUTPUT));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_REDACTED);
        }
    }

    @Nested
    @DisplayName("Pre-invocation obligation failures: the warrant collapses before action")
    class PreInvocationObligationFailures {

        @Test
        @DisplayName("DecisionSignal obligation Runner failure: the Bursar collapses before signing")
        void whenDecisionObligationFailsThenAccessDeniedPreInvocation() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligation(Obligation.BURSAR_COLLAPSES_DECISION));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining(PRE_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("InputSignal obligation Runner failure: Nobby loses the paperwork before dispatch")
        void whenInputObligationFailsThenAccessDeniedPreInvocation() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligation(Obligation.NOBBY_LOSES_PAPERWORK_INPUT));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining(PRE_INVOCATION_MESSAGE_FRAGMENT);
        }
    }

    @Nested
    @DisplayName("Post-invocation obligation failures: the report breaks on the way out")
    class PostInvocationObligationFailures {

        @Test
        @DisplayName("OutputSignal Mapper failure: Carrot drops the evidence; access denied (post-invocation)")
        void whenOutputMapperObligationFailsThenAccessDeniedPostInvocation() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligation(Obligation.CARROT_DROPS_EVIDENCE_MAPPER));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("OutputSignal Consumer failure: Rincewind panics mid-observation; access denied")
        void whenOutputConsumerObligationFailsThenAccessDenied() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligation(Obligation.RINCEWIND_PANICS_CONSUMER));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile);
        }
    }

    @Nested
    @DisplayName("Advice handlers are noted but never bar action; a clerk's grumble is just a grumble")
    class AdviceHandlers {

        @Test
        @DisplayName("Failing advice does not deny access")
        void whenAdviceFailsThenAccessStillPermitted() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithAdvice(Obligation.BURSAR_COLLAPSES_DECISION));

            val result = watch.openCaseFile();

            assertThat(result).isEqualTo(CASE_FILE_BODY);
        }
    }

    @Nested
    @DisplayName("ErrorSignal handlers transform errors before they leave the Watch House")
    class ErrorSignalHandlers {

        @Test
        @DisplayName("ErrorSignal Mapper<Throwable>: Death personally claims the exception")
        void whenErrorSignalMapperOnDenyThenMappedExceptionPropagates() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligationAndDeny(Obligation.DEATH_CLAIMS_EXCEPTION));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(watch::openCaseFile)
                    .withMessageContaining(DEATH_CLAIMS_THE_EXCEPTION);
        }

        @Test
        @DisplayName("ErrorSignal Runner: the Patrician's clerks log every failed warrant")
        void whenErrorSignalRunnerObligationOnDenyThenClerksLog() {
            val deny = new AuthorizationDecision(Decision.DENY, arrayOf(Obligation.CLERKS_LOG_FAILED_WARRANT),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            when(pdp.decideOnceBlocking(any())).thenReturn(deny);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(watch::openCaseFile);
            assertThat(logbook.clerkErrorLogs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("RAP exceptions in PreEnforce: Death may claim them too because the plan exists")
    class RapExceptions {

        @Test
        @DisplayName("Provoking the Luggage throws; without an ErrorSignal Mapper the original exception escapes")
        void whenRapThrowsAndNoErrorMapperThenOriginalExceptionPropagates() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(watch::provokeTheLuggage)
                    .withMessageContaining("Luggage bites");
        }

        @Test
        @DisplayName("Provoking the Luggage throws; an ErrorSignal Mapper rewrites the exception on the way out")
        void whenRapThrowsAndErrorMapperPresentThenMappedExceptionPropagates() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.DEATH_CLAIMS_EXCEPTION));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(watch::provokeTheLuggage)
                    .withMessageContaining(DEATH_CLAIMS_THE_EXCEPTION);
        }
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

    static class WatchHouse {

        @PreEnforce
        public String arrestSuspect(String suspectName) {
            return ARREST_PREFIX + suspectName;
        }

        @PreEnforce
        public String openCaseFile() {
            return CASE_FILE_BODY;
        }

        @PreEnforce
        public String confiscateLostScroll() {
            return null;
        }

        @PreEnforce
        public List<String> listOpenCases() {
            return OPEN_CASES;
        }

        @PreEnforce
        public Map<String, Integer> accountWatchSalaries() {
            return WATCH_SALARIES;
        }

        @PreEnforce
        public Integer countAvailableConstables() {
            return 12;
        }

        @PreEnforce
        public void escortDrumknottHome() {
            // observable side effect: Drumknott is escorted; nothing returned
        }

        @PreEnforce
        public String provokeTheLuggage() {
            throw new IllegalStateException("the Luggage bites the rite and the rite is consumed");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableSaplMethodSecurity
    static class AnkhMorporkTestApp {

        @Bean
        WatchHouse watchHouse() {
            return new WatchHouse();
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
        final AtomicReference<Object> lastDetritusObservation    = new AtomicReference<>();

        void reset() {
            patricianCountersignatures.set(0);
            colonNotes.set(0);
            carrotInspections.set(0);
            anguaAudits.set(0);
            clerkErrorLogs.set(0);
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
