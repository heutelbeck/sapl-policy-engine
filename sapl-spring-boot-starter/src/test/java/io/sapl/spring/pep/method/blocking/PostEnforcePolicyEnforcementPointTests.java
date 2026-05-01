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

import lombok.experimental.UtilityClass;
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
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandler.Consumer;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.Signal.ErrorSignal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.SignalType.ValueSignalType;
import lombok.val;

/**
 * End-to-end tests for the blocking @PostEnforce PEP.
 * <p>
 * The scenario is the Miskatonic University Library archive: a service that
 * lends out forbidden texts only after the librarian (the PEP) consults the
 * library policy (the PDP) about what the visitor may take home and what the
 * archive must do on the way out.
 */
@SpringBootTest(classes = PostEnforcePolicyEnforcementPointTests.MiskatonicArchiveTestApp.class)
@WithMockUser(username = "armitage", roles = "FACULTY")
class PostEnforcePolicyEnforcementPointTests {

    private static final String NECRONOMICON_PASSAGE                  = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn";
    private static final String REDACTED_BY_LIBRARIAN                 = "[REDACTED BY ORDER OF THE MISKATONIC LIBRARIAN]";
    private static final String CATALOG_SEALED_BY_LIBRARIAN           = "[CATALOG SEALED BY ORDER OF THE LIBRARIAN]";
    private static final String CRAWLING_CHAOS_CONSUMED_THE_THROWABLE = "the Crawling Chaos has consumed your exception";

    private static final String POST_INVOCATION_MESSAGE_FRAGMENT = "post-invocation";

    private static final String DE_VERMIS_MYSTERIIS = "De Vermis Mysteriis";
    private static final String CULTES_DES_GOULES   = "Cultes des Goules";
    private static final String LIBER_IVONIS        = "Liber Ivonis";

    private static final String DR_ARMITAGE = "Armitage";
    private static final String DR_WILMARTH = "Wilmarth";

    private static final List<String>         ACCESSIONED_TOMES = List.of(DE_VERMIS_MYSTERIIS, CULTES_DES_GOULES,
            LIBER_IVONIS);
    private static final Map<String, Integer> SANITY_SCORES     = Map.of(DR_ARMITAGE, 87, DR_WILMARTH, 42);

    @Autowired
    MiskatonicArchive archive;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    ArchivistJournal journal;

    @BeforeEach
    void resetJournal() {
        journal.reset();
    }

    @Nested
    @DisplayName("Permitted access without obligations: the tome leaves the archive unaltered")
    class PermittedWithoutObligations {

        @Test
        @DisplayName("Permit returns the forbidden incantation verbatim")
        void whenPermitAndScalarThenReturnsOriginalIncantation() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(NECRONOMICON_PASSAGE);
        }

        @Test
        @DisplayName("Permit returns the full Pnakotic catalog")
        void whenPermitAndListThenReturnsFullCatalog() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = archive.listAccessionedTomes();

            assertThat(result).containsExactlyElementsOf(ACCESSIONED_TOMES);
        }

        @Test
        @DisplayName("Permit returns the unaltered sanity census")
        void whenPermitAndMapThenReturnsSanityCensus() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = archive.tabulateSanityScores();

            assertThat(result).containsAllEntriesOf(SANITY_SCORES);
        }

        @Test
        @DisplayName("Permit returns null when the requested manuscript was lost in the Innsmouth raid")
        void whenPermitAndMissingManuscriptThenReturnsNull() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            val result = archive.fetchPnakoticManuscript();

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Permit allows closing the warded door; the rite runs and returns nothing")
        void whenPermitAndVoidThenRiteCompletes() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            archive.closeTheWardedDoor();

            assertThat(journal.wardingRiteSideEffects).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Void warding rite: side-effect-only methods are still authorised")
    class VoidRite {

        @Test
        @DisplayName("Void method consults the PDP and the side effect runs on PERMIT")
        void whenVoidPermitThenPdpConsultedAndSideEffectRuns() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

            archive.closeTheWardedDoor();

            verify(pdp).decideOnceBlocking(any());
            assertThat(journal.wardingRiteSideEffects).hasValue(1);
        }

        @Test
        @DisplayName("Void method with DENY raises AccessDeniedException; side effect already ran (post-invocation gate)")
        void whenVoidDenyThenAccessDeniedAfterSideEffect() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::closeTheWardedDoor)
                    .withMessageContaining("DENY");
            assertThat(journal.wardingRiteSideEffects).hasValue(1);
        }

        @Test
        @DisplayName("Void method fires DecisionSignal Runner")
        void whenVoidAndDecisionRunnerObligationThenRuns() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.DEAN_COUNTERSIGNS));

            archive.closeTheWardedDoor();

            assertThat(journal.deanCountersignatures).hasValue(1);
        }

        @Test
        @DisplayName("Void method skips OutputSignal Mappers (Maybe.absent has no value to transform)")
        void whenVoidAndOutputMapperObligationThenMapperSkips() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.LIBRARIAN_REDACTS));

            archive.closeTheWardedDoor();

            // Mapper would have substituted REDACTED_BY_LIBRARIAN if it ran; it skipped
            // because the OutputSignal value is Maybe.absent for void. The PEP still
            // consulted the PDP — verifying that proves the obligation was processed.
            verify(pdp).decideOnceBlocking(any());
        }

        @Test
        @DisplayName("Void method fires OutputSignal Runners (no value needed for run-only handlers)")
        void whenVoidAndOutputRunnerObligationThenRunnerFires() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.ARMITAGE_LOGS_ACCESS));

            archive.closeTheWardedDoor();

            assertThat(journal.armitageLogEntries).hasValue(1);
        }
    }

    @Nested
    @DisplayName("The librarian substitutes a redacted facsimile in place of the original text")
    class ResourceSubstitution {

        @Test
        @DisplayName("Resource value replaces the scalar incantation")
        void whenResourcePresentThenReplacesIncantation() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(Value.of(REDACTED_BY_LIBRARIAN)));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(REDACTED_BY_LIBRARIAN);
        }

        @Test
        @DisplayName("Resource value materialises in place of a manuscript believed lost")
        void whenNullRapAndResourcePresentThenMaterialises() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(Value.of(REDACTED_BY_LIBRARIAN)));

            val result = archive.fetchPnakoticManuscript();

            assertThat(result).isEqualTo(REDACTED_BY_LIBRARIAN);
        }

        @Test
        @DisplayName("Resource list replaces the entire catalog with a single sanitised entry")
        void whenResourceListPresentThenReplacesCatalog() {
            val replacement = Value.ofArray(Value.of(CATALOG_SEALED_BY_LIBRARIAN));
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(replacement));

            val result = archive.listAccessionedTomes();

            assertThat(result).containsExactly(CATALOG_SEALED_BY_LIBRARIAN);
        }

        @Test
        @DisplayName("Incompatible resource type fails as access denied with a post-invocation message")
        void whenResourceTypeIsIncompatibleThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithResource(Value.of("not-a-number")));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::countCthulhuCultMembers)
                    .withMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("DENY with a substitute resource still denies; substitution cannot save a denial")
        void whenDenyWithResourceThenStillAccessDenied() {
            val deny = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(REDACTED_BY_LIBRARIAN));
            when(pdp.decideOnceBlocking(any())).thenReturn(deny);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("not PERMIT");
        }

        @Test
        @DisplayName("SUSPEND with a substitute resource still denies; one-shot PEPs cannot pause, so substitution cannot save a suspension")
        void whenSuspendWithResourceThenStillAccessDenied() {
            val suspend = new AuthorizationDecision(Decision.SUSPEND, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(REDACTED_BY_LIBRARIAN));
            when(pdp.decideOnceBlocking(any())).thenReturn(suspend);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("not PERMIT");
        }
    }

    @Nested
    @DisplayName("Decisions other than PERMIT: the wards refuse to open the case")
    class DenialDecisions {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.DENY);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("DENY");
        }

        @Test
        @DisplayName("INDETERMINATE raises AccessDeniedException")
        void whenIndeterminateThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.INDETERMINATE);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("INDETERMINATE");
        }

        @Test
        @DisplayName("NOT_APPLICABLE raises AccessDeniedException")
        void whenNotApplicableThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.NOT_APPLICABLE);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("NOT_APPLICABLE");
        }

        @Test
        @DisplayName("SUSPEND raises AccessDeniedException; one-shot PEPs cannot suspend, so the wards stay shut")
        void whenSuspendThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.SUSPEND);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining("SUSPEND");
        }
    }

    @Nested
    @DisplayName("Obligation handlers fire on every applicable signal as the tome is delivered")
    class ObligationHandlers {

        @Test
        @DisplayName("OutputSignal Runner: Dr. Armitage notes every access in the keeper's journal")
        void whenOutputRunnerObligationThenLoggedAndPassesThrough() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.ARMITAGE_LOGS_ACCESS));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(NECRONOMICON_PASSAGE);
            assertThat(journal.armitageLogEntries).hasValue(1);
        }

        @Test
        @DisplayName("OutputSignal Consumer: Wilmarth examines the passage without altering it")
        void whenOutputConsumerObligationThenObservesValue() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.WILMARTH_EXAMINES_ENTRY));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(NECRONOMICON_PASSAGE);
            assertThat(journal.lastWilmarthInspection).hasValue(NECRONOMICON_PASSAGE);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the librarian redacts the passage before handing it over")
        void whenOutputMapperObligationThenRedactsEntry() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.LIBRARIAN_REDACTS));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(REDACTED_BY_LIBRARIAN);
        }

        @Test
        @DisplayName("DecisionSignal Runner: the Dean countersigns each decision before it takes effect")
        void whenDecisionRunnerObligationThenDeanCountersigns() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.DEAN_COUNTERSIGNS));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(NECRONOMICON_PASSAGE);
            assertThat(journal.deanCountersignatures).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Obligation-handler failures: the warding ritual collapses; access is denied")
    class ObligationFailures {

        @Test
        @DisplayName("DecisionSignal obligation Runner: a cultist sabotages the decision")
        void whenDecisionObligationFailsThenAccessDenied() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligation(Obligation.CULTIST_SABOTAGES_DECISION));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the gate refuses to open while the redaction is being prepared")
        void whenOutputMapperObligationFailsThenAccessDeniedPostInvocation() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.GATE_REFUSES_TO_OPEN));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT);
        }

        @Test
        @DisplayName("OutputSignal Consumer: the reader is overtaken by madness mid-examination")
        void whenOutputConsumerObligationFailsThenAccessDenied() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithObligation(Obligation.MADNESS_OVERTAKES_READER));

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage);
        }
    }

    @Nested
    @DisplayName("Advice handlers are noted but never bar access; a faculty memo is just a memo")
    class AdviceHandlers {

        @Test
        @DisplayName("Failing advice does not deny access; the cultist's interference is dismissed")
        void whenAdviceFailsThenAccessStillPermitted() {
            when(pdp.decideOnceBlocking(any())).thenReturn(decisionWithAdvice(Obligation.CULTIST_SABOTAGES_DECISION));

            val result = archive.fetchNecronomiconPassage();

            assertThat(result).isEqualTo(NECRONOMICON_PASSAGE);
        }
    }

    @Nested
    @DisplayName("Errors on the way out may be rewritten by what the policy summons")
    class ErrorSignalHandlers {

        @Test
        @DisplayName("ErrorSignal Mapper<Throwable>: Nyarlathotep consumes the original exception and substitutes its own")
        void whenErrorSignalMapperThenMappedExceptionPropagates() {
            when(pdp.decideOnceBlocking(any()))
                    .thenReturn(decisionWithObligationAndDeny(Obligation.NYARLATHOTEP_REWRITES_THROWABLE));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(archive::fetchNecronomiconPassage)
                    .withMessageContaining(CRAWLING_CHAOS_CONSUMED_THE_THROWABLE);
        }

        @Test
        @DisplayName("ErrorSignal Runner: the Keeper of the Library logs every error before it leaves the archive")
        void whenErrorSignalRunnerObligationThenKeeperLogs() {
            val deny = new AuthorizationDecision(Decision.DENY, arrayOf(Obligation.KEEPER_LOGS_ERRORS),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            when(pdp.decideOnceBlocking(any())).thenReturn(deny);

            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(archive::fetchNecronomiconPassage);
            assertThat(journal.keeperErrorLogs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("RAP exceptions in PostEnforce: when the rite goes wrong before the seal can intercept")
    class RapExceptions {

        @Test
        @DisplayName("Opening the Gate of Yog-Sothoth throws before the PDP is consulted; the exception escapes unmapped")
        void whenRapThrowsThenPropagatesUnmapped() {
            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(archive::openTheGateOfYogSothoth)
                    .withMessageContaining("Yog-Sothoth stirs");
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

    static class MiskatonicArchive {

        @PostEnforce
        public String fetchNecronomiconPassage() {
            return NECRONOMICON_PASSAGE;
        }

        @PostEnforce
        public String fetchPnakoticManuscript() {
            return null;
        }

        @PostEnforce
        public List<String> listAccessionedTomes() {
            return ACCESSIONED_TOMES;
        }

        @PostEnforce
        public Map<String, Integer> tabulateSanityScores() {
            return SANITY_SCORES;
        }

        @PostEnforce
        public Integer countCthulhuCultMembers() {
            return 7;
        }

        private final ArchivistJournal journalForRites;

        MiskatonicArchive(ArchivistJournal journal) {
            this.journalForRites = journal;
        }

        @PostEnforce
        public void closeTheWardedDoor() {
            journalForRites.wardingRiteSideEffects.incrementAndGet();
        }

        @PostEnforce
        public String openTheGateOfYogSothoth() {
            throw new IllegalStateException("Yog-Sothoth stirs and the rite is consumed");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableSaplMethodSecurity
    static class MiskatonicArchiveTestApp {

        @Bean
        MiskatonicArchive miskatonicArchive(ArchivistJournal journal) {
            return new MiskatonicArchive(journal);
        }

        @Bean
        ArchivistJournal archivistJournal() {
            return new ArchivistJournal();
        }

        @Bean
        TestConstraintHandlerProvider testConstraintHandlerProvider(ArchivistJournal journal) {
            return new TestConstraintHandlerProvider(journal);
        }
    }

    @UtilityClass
    static final class Obligation {
        static final String ARMITAGE_LOGS_ACCESS            = "miskatonic:armitageLogsAccess";
        static final String WILMARTH_EXAMINES_ENTRY         = "miskatonic:wilmarthExaminesEntry";
        static final String LIBRARIAN_REDACTS               = "miskatonic:librarianRedactsOutput";
        static final String DEAN_COUNTERSIGNS               = "miskatonic:deanCountersignsDecision";
        static final String CULTIST_SABOTAGES_DECISION      = "miskatonic:cultistSabotagesDecision";
        static final String GATE_REFUSES_TO_OPEN            = "miskatonic:gateRefusesToOpenMapper";
        static final String MADNESS_OVERTAKES_READER        = "miskatonic:madnessOvertakesReaderConsumer";
        static final String NYARLATHOTEP_REWRITES_THROWABLE = "miskatonic:nyarlathotepRewritesThrowable";
        static final String KEEPER_LOGS_ERRORS              = "miskatonic:keeperLogsErrors";
    }

    static class ArchivistJournal {

        final AtomicInteger           armitageLogEntries     = new AtomicInteger();
        final AtomicInteger           deanCountersignatures  = new AtomicInteger();
        final AtomicInteger           keeperErrorLogs        = new AtomicInteger();
        final AtomicInteger           wardingRiteSideEffects = new AtomicInteger();
        final AtomicReference<Object> lastWilmarthInspection = new AtomicReference<>();

        void reset() {
            armitageLogEntries.set(0);
            deanCountersignatures.set(0);
            keeperErrorLogs.set(0);
            wardingRiteSideEffects.set(0);
            lastWilmarthInspection.set(null);
        }
    }

    static class TestConstraintHandlerProvider implements ConstraintHandlerProvider {

        private static final String SEAL_HAS_BROKEN = "the warding seal has broken";

        private final ArchivistJournal journal;

        TestConstraintHandlerProvider(ArchivistJournal journal) {
            this.journal = journal;
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
            case Obligation.ARMITAGE_LOGS_ACCESS            ->
                outputAt(supportedSignals, (Runner) journal.armitageLogEntries::incrementAndGet);
            case Obligation.WILMARTH_EXAMINES_ENTRY         ->
                outputAt(supportedSignals, (Consumer<Object>) journal.lastWilmarthInspection::set);
            case Obligation.LIBRARIAN_REDACTS               ->
                outputAt(supportedSignals, (Mapper<Object>) ignored -> REDACTED_BY_LIBRARIAN);
            case Obligation.DEAN_COUNTERSIGNS               ->
                List.of(new ScopedConstraintHandler((Runner) journal.deanCountersignatures::incrementAndGet,
                        DecisionSignal.SIGNAL_TYPE, 0));
            case Obligation.CULTIST_SABOTAGES_DECISION      -> List.of(new ScopedConstraintHandler((Runner) () -> {
                                                            throw new IllegalStateException(SEAL_HAS_BROKEN);
                                                        },
                    DecisionSignal.SIGNAL_TYPE, 0));
            case Obligation.GATE_REFUSES_TO_OPEN            -> outputAt(supportedSignals, (Mapper<Object>) ignored -> {
                                                            throw new IllegalStateException(SEAL_HAS_BROKEN);
                                                        });
            case Obligation.MADNESS_OVERTAKES_READER        ->
                outputAt(supportedSignals, (Consumer<Object>) ignored -> {
                                                                throw new IllegalStateException(SEAL_HAS_BROKEN);
                                                            });
            case Obligation.NYARLATHOTEP_REWRITES_THROWABLE -> List.of(new ScopedConstraintHandler(
                    (Mapper<Throwable>) ignored -> new IllegalStateException(CRAWLING_CHAOS_CONSUMED_THE_THROWABLE),
                    ErrorSignal.SIGNAL_TYPE, 0));
            case Obligation.KEEPER_LOGS_ERRORS              ->
                List.of(new ScopedConstraintHandler((Runner) journal.keeperErrorLogs::incrementAndGet,
                        ErrorSignal.SIGNAL_TYPE, 0));
            default                                         -> List.of();
            };
        }

        private static List<ScopedConstraintHandler> outputAt(Set<SignalType> supportedSignals,
                ConstraintHandler<?> handler) {
            return SignalType.findIn(supportedSignals, OutputSignal.class)
                    .<List<ScopedConstraintHandler>>map(s -> List.of(new ScopedConstraintHandler(handler, s, 30)))
                    .orElse(List.of());
        }
    }
}
