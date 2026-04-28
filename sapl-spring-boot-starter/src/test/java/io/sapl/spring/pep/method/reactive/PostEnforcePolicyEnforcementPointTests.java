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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end tests for the reactive @PostEnforce PEP, mirroring
 * {@code io.sapl.spring.pep.method.blocking.PostEnforcePolicyEnforcementPointTests}
 * scenario-for-scenario so the two PEPs are directly comparable.
 * <p>
 * Same Miskatonic University Library scenario, same obligation set, same
 * expectations. Reactive-specific additions: empty-Mono coverage (RAP that
 * completes empty must still consult the PDP), Mono&lt;Void&gt; coverage
 * (mirrors blocking void semantics), and a Mono.error variant of the
 * RAP-exception case.
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

    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(5);

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
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(NECRONOMICON_PASSAGE).verifyComplete();
        }

        @Test
        @DisplayName("Permit returns the full Pnakotic catalog")
        void whenPermitAndListThenReturnsFullCatalog() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(archive.listAccessionedTomes()).expectNext(ACCESSIONED_TOMES).verifyComplete();
        }

        @Test
        @DisplayName("Permit returns the unaltered sanity census")
        void whenPermitAndMapThenReturnsSanityCensus() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(archive.tabulateSanityScores()).expectNext(SANITY_SCORES).verifyComplete();
        }
    }

    @Nested
    @DisplayName("The librarian substitutes a redacted facsimile in place of the original text")
    class ResourceSubstitution {

        @Test
        @DisplayName("Resource value replaces the scalar incantation")
        void whenResourcePresentThenReplacesIncantation() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithResource(Value.of(REDACTED_BY_LIBRARIAN))));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(REDACTED_BY_LIBRARIAN).verifyComplete();
        }

        @Test
        @DisplayName("Resource list replaces the entire catalog with a single sanitised entry")
        void whenResourceListPresentThenReplacesCatalog() {
            val replacement = Value.ofArray(Value.of(CATALOG_SEALED_BY_LIBRARIAN));
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithResource(replacement)));

            StepVerifier.create(archive.listAccessionedTomes()).expectNext(List.of(CATALOG_SEALED_BY_LIBRARIAN))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Incompatible resource type fails as access denied with a post-invocation message")
        void whenResourceTypeIsIncompatibleThenAccessDenied() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithResource(Value.of("not-a-number"))));

            StepVerifier.create(archive.countCthulhuCultMembers()).expectErrorSatisfies(err -> assertThat(err)
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("DENY with a substitute resource still denies; substitution cannot save a denial")
        void whenDenyWithResourceThenStillAccessDenied() {
            val deny = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of(REDACTED_BY_LIBRARIAN));
            when(pdp.decideOnce(any())).thenReturn(Mono.just(deny));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectErrorSatisfies(
                    err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining("not PERMIT"))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Decisions other than PERMIT: the wards refuse to open the case")
    class DenialDecisions {

        @Test
        @DisplayName("DENY raises AccessDeniedException")
        void whenDenyThenAccessDenied() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectErrorSatisfies(
                    err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining("DENY"))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("INDETERMINATE raises AccessDeniedException")
        void whenIndeterminateThenAccessDenied() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.INDETERMINATE));

            StepVerifier
                    .create(archive.fetchNecronomiconPassage()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("INDETERMINATE"))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("NOT_APPLICABLE raises AccessDeniedException")
        void whenNotApplicableThenAccessDenied() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.NOT_APPLICABLE));

            StepVerifier
                    .create(archive.fetchNecronomiconPassage()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("NOT_APPLICABLE"))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Obligation handlers fire on every applicable signal as the tome is delivered")
    class ObligationHandlers {

        @Test
        @DisplayName("OutputSignal Runner: Dr. Armitage notes every access in the keeper's journal")
        void whenOutputRunnerObligationThenLoggedAndPassesThrough() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.ARMITAGE_LOGS_ACCESS)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(NECRONOMICON_PASSAGE).verifyComplete();
            assertThat(journal.armitageLogEntries).hasValue(1);
        }

        @Test
        @DisplayName("OutputSignal Consumer: Wilmarth examines the passage without altering it")
        void whenOutputConsumerObligationThenObservesValue() {
            when(pdp.decideOnce(any()))
                    .thenReturn(Mono.just(decisionWithObligation(Obligation.WILMARTH_EXAMINES_ENTRY)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(NECRONOMICON_PASSAGE).verifyComplete();
            assertThat(journal.lastWilmarthInspection).hasValue(NECRONOMICON_PASSAGE);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the librarian redacts the passage before handing it over")
        void whenOutputMapperObligationThenRedactsEntry() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.LIBRARIAN_REDACTS)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(REDACTED_BY_LIBRARIAN).verifyComplete();
        }

        @Test
        @DisplayName("DecisionSignal Runner: the Dean countersigns each decision before it takes effect")
        void whenDecisionRunnerObligationThenDeanCountersigns() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.DEAN_COUNTERSIGNS)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(NECRONOMICON_PASSAGE).verifyComplete();
            assertThat(journal.deanCountersignatures).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Obligation-handler failures: the warding ritual collapses; access is denied")
    class ObligationFailures {

        @Test
        @DisplayName("DecisionSignal obligation Runner: a cultist sabotages the decision")
        void whenDecisionObligationFailsThenAccessDenied() {
            when(pdp.decideOnce(any()))
                    .thenReturn(Mono.just(decisionWithObligation(Obligation.CULTIST_SABOTAGES_DECISION)));

            StepVerifier.create(archive.fetchNecronomiconPassage())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("OutputSignal Mapper: the gate refuses to open while the redaction is being prepared")
        void whenOutputMapperObligationFailsThenAccessDeniedPostInvocation() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.GATE_REFUSES_TO_OPEN)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectErrorSatisfies(err -> assertThat(err)
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining(POST_INVOCATION_MESSAGE_FRAGMENT))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("OutputSignal Consumer: the reader is overtaken by madness mid-examination")
        void whenOutputConsumerObligationFailsThenAccessDenied() {
            when(pdp.decideOnce(any()))
                    .thenReturn(Mono.just(decisionWithObligation(Obligation.MADNESS_OVERTAKES_READER)));

            StepVerifier.create(archive.fetchNecronomiconPassage())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Advice handlers are noted but never bar access; a faculty memo is just a memo")
    class AdviceHandlers {

        @Test
        @DisplayName("Failing advice does not deny access; the cultist's interference is dismissed")
        void whenAdviceFailsThenAccessStillPermitted() {
            when(pdp.decideOnce(any()))
                    .thenReturn(Mono.just(decisionWithAdvice(Obligation.CULTIST_SABOTAGES_DECISION)));

            StepVerifier.create(archive.fetchNecronomiconPassage()).expectNext(NECRONOMICON_PASSAGE).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Errors on the way out may be rewritten by what the policy summons")
    class ErrorSignalHandlers {

        @Test
        @DisplayName("ErrorSignal Mapper<Throwable>: Nyarlathotep consumes the original exception and substitutes its own")
        void whenErrorSignalMapperThenMappedExceptionPropagates() {
            when(pdp.decideOnce(any()))
                    .thenReturn(Mono.just(decisionWithObligationAndDeny(Obligation.NYARLATHOTEP_REWRITES_THROWABLE)));

            StepVerifier.create(archive.fetchNecronomiconPassage())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(CRAWLING_CHAOS_CONSUMED_THE_THROWABLE))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("ErrorSignal Runner: the Keeper of the Library logs every error before it leaves the archive")
        void whenErrorSignalRunnerObligationThenKeeperLogs() {
            val deny = new AuthorizationDecision(Decision.DENY, arrayOf(Obligation.KEEPER_LOGS_ERRORS),
                    Value.EMPTY_ARRAY, Value.UNDEFINED);
            when(pdp.decideOnce(any())).thenReturn(Mono.just(deny));

            StepVerifier.create(archive.fetchNecronomiconPassage())
                    .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(AccessDeniedException.class))
                    .verify(STEP_TIMEOUT);
            assertThat(journal.keeperErrorLogs).hasValue(1);
        }
    }

    @Nested
    @DisplayName("RAP exceptions in PostEnforce: when the rite goes wrong before the seal can intercept")
    class RapExceptions {

        @Test
        @DisplayName("Throwing during proceed() escapes unmapped because the plan does not yet exist")
        void whenRapProceedThrowsThenPropagatesUnmapped() {
            StepVerifier
                    .create(archive.openTheGateOfYogSothoth()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Yog-Sothoth stirs"))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("Mono.error returned from the protected method also escapes unmapped")
        void whenRapReturnsMonoErrorThenPropagatesUnmapped() {
            StepVerifier
                    .create(archive.invokeShubNiggurath()).expectErrorSatisfies(err -> assertThat(err)
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Shub-Niggurath emerges"))
                    .verify(STEP_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Empty Mono is still authorised: an empty result must not bypass policy")
    class EmptyMonoStillAuthorised {

        @Test
        @DisplayName("Empty Mono consults the PDP and applies output-side obligations")
        void whenRapReturnsEmptyMonoThenPdpIsConsultedAndObligationsApply() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.ARMITAGE_LOGS_ACCESS)));

            StepVerifier.create(archive.fetchPnakoticManuscript()).verifyComplete();

            verify(pdp).decideOnce(any());
            assertThat(journal.armitageLogEntries).hasValue(1);
        }

        @Test
        @DisplayName("Empty Mono with DENY raises AccessDeniedException")
        void whenRapReturnsEmptyMonoAndPdpDeniesThenAccessDenied() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier.create(archive.fetchPnakoticManuscript()).expectErrorSatisfies(
                    err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining("DENY"))
                    .verify(STEP_TIMEOUT);
        }

        @Test
        @DisplayName("Empty Mono with resource substitution materialises the resource value")
        void whenRapReturnsEmptyMonoAndResourcePresentThenMaterialises() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithResource(Value.of(REDACTED_BY_LIBRARIAN))));

            StepVerifier.create(archive.fetchPnakoticManuscript()).expectNext(REDACTED_BY_LIBRARIAN).verifyComplete();
        }

        @Test
        @DisplayName("Empty Mono fires DecisionSignal handlers")
        void whenRapReturnsEmptyMonoAndDecisionRunnerObligationThenRuns() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.DEAN_COUNTERSIGNS)));

            StepVerifier.create(archive.fetchPnakoticManuscript()).verifyComplete();

            assertThat(journal.deanCountersignatures).hasValue(1);
        }
    }

    @Nested
    @DisplayName("Mono<Void> warding rite: side-effect-only methods are still authorised")
    class MonoVoidRite {

        @Test
        @DisplayName("Mono<Void> consults the PDP and the side effect runs on PERMIT")
        void whenMonoVoidPermitThenPdpConsultedAndSideEffectRuns() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.PERMIT));

            StepVerifier.create(archive.closeTheWardedDoor()).verifyComplete();

            verify(pdp).decideOnce(any());
            assertThat(journal.wardingRiteSideEffects).hasValue(1);
        }

        @Test
        @DisplayName("Mono<Void> with DENY raises AccessDeniedException; the side effect still runs because the RAP completed before enforcement")
        void whenMonoVoidDenyThenAccessDeniedAfterSideEffect() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(AuthorizationDecision.DENY));

            StepVerifier.create(archive.closeTheWardedDoor()).expectErrorSatisfies(
                    err -> assertThat(err).isInstanceOf(AccessDeniedException.class).hasMessageContaining("DENY"))
                    .verify(STEP_TIMEOUT);
            assertThat(journal.wardingRiteSideEffects).hasValue(1);
        }

        @Test
        @DisplayName("Mono<Void> fires DecisionSignal Runner")
        void whenMonoVoidAndDecisionRunnerObligationThenRuns() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.DEAN_COUNTERSIGNS)));

            StepVerifier.create(archive.closeTheWardedDoor()).verifyComplete();

            assertThat(journal.deanCountersignatures).hasValue(1);
        }

        @Test
        @DisplayName("Mono<Void> skips OutputSignal Mappers (Maybe.absent has no value to transform)")
        void whenMonoVoidAndOutputMapperObligationThenMapperSkips() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.LIBRARIAN_REDACTS)));

            StepVerifier.create(archive.closeTheWardedDoor()).verifyComplete();
            // The Mapper would have substituted REDACTED_BY_LIBRARIAN if it ran;
            // it skipped because the OutputSignal value is Maybe.absent for void.
        }

        @Test
        @DisplayName("Mono<Void> fires OutputSignal Runners (no value needed for run-only handlers)")
        void whenMonoVoidAndOutputRunnerObligationThenRunnerFires() {
            when(pdp.decideOnce(any())).thenReturn(Mono.just(decisionWithObligation(Obligation.ARMITAGE_LOGS_ACCESS)));

            StepVerifier.create(archive.closeTheWardedDoor()).verifyComplete();

            assertThat(journal.armitageLogEntries).hasValue(1);
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

    @Service
    static class MiskatonicArchive {

        @PostEnforce
        public Mono<String> fetchNecronomiconPassage() {
            return Mono.just(NECRONOMICON_PASSAGE);
        }

        @PostEnforce
        public Mono<String> fetchPnakoticManuscript() {
            return Mono.empty();
        }

        @PostEnforce
        public Mono<List<String>> listAccessionedTomes() {
            return Mono.just(ACCESSIONED_TOMES);
        }

        @PostEnforce
        public Mono<Map<String, Integer>> tabulateSanityScores() {
            return Mono.just(SANITY_SCORES);
        }

        @PostEnforce
        public Mono<Integer> countCthulhuCultMembers() {
            return Mono.just(7);
        }

        @PostEnforce
        public Mono<String> openTheGateOfYogSothoth() {
            throw new IllegalStateException("Yog-Sothoth stirs and the rite is consumed");
        }

        @PostEnforce
        public Mono<String> invokeShubNiggurath() {
            return Mono.error(new IllegalStateException("Shub-Niggurath emerges from the woods"));
        }

        private final ArchivistJournal journalForRites;

        MiskatonicArchive(ArchivistJournal journal) {
            this.journalForRites = journal;
        }

        @PostEnforce
        public Mono<Void> closeTheWardedDoor() {
            return Mono.fromRunnable(journalForRites.wardingRiteSideEffects::incrementAndGet);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveSaplMethodSecurity
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

        private Obligation() {
            // constants only
        }
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
            for (val s : supportedSignals) {
                if (s instanceof ValueSignalType<?> v && OutputSignal.class.equals(v.type())) {
                    return List.of(new ScopedConstraintHandler(handler, s, 30));
                }
            }
            return List.of();
        }
    }
}
