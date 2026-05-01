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
package io.sapl.spring.pep.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.SuspendKind;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason;
import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link StreamingPipeline#classify}, the
 * pre-classification function that turns a raw PDP
 * {@link AuthorizationDecision} (plus the outcome of decision-scoped
 * enforcement) into the {@link Event} variant the FSM consumes.
 * <p>
 * Spec source: "Pre-classification A" diagram in
 * {@code notes/4.1.0-notes/streaming-pep-mealy-diagrams.md}. Each
 * decision-verb / failed-flag combination is one test case; the
 * combined cardinality is six (PERMIT splits on the failed flag,
 * the other four verbs ignore it).
 * <p>
 * Tests assert the event variant produced and, for {@code PdpSuspend},
 * the carried {@link SuspendKind}. The kind is observable contract
 * (it surfaces to the subscriber via the {@code EmitTransition}
 * message), so it is part of the spec; field introspection beyond
 * that is avoided.
 */
class StreamingPipelineClassifyTests {

    private static final EnforcementPlan PLAN = new EnforcementPlan(Map.of());

    private static StreamingPipeline pipelineWithTerminateFlag(boolean terminate) {
        return new StreamingPipeline(terminate, false, Flux.empty(), d -> PLAN, Flux::empty, false);
    }

    @ParameterizedTest(name = "{0} (failed={1}) -> {2}")
    @MethodSource("verbCases")
    void classifyProducesExpectedEventForVerbAndFailedFlag(Decision verb, boolean failed,
            Class<? extends Event> expectedEventType) {
        StreamingPipeline     pipeline = pipelineWithTerminateFlag(false);
        AuthorizationDecision decision = decisionFor(verb);

        Event event = pipeline.classify(decision, PLAN, failed);

        assertThat(event).isInstanceOf(expectedEventType);
    }

    @ParameterizedTest(name = "{0} (failed={1}) -> SuspendKind {2}")
    @MethodSource("suspendCases")
    void classifyTagsSuspendKindForSuspendingInputs(Decision verb, boolean failed, SuspendKind expectedKind) {
        StreamingPipeline     pipeline = pipelineWithTerminateFlag(false);
        AuthorizationDecision decision = decisionFor(verb);

        Event event = pipeline.classify(decision, PLAN, failed);

        assertThat(event).isInstanceOfSatisfying(PdpSuspend.class,
                ev -> assertThat(ev.reason()).isInstanceOfSatisfying(TransitionReason.Suspended.class,
                        r -> assertThat(r.kind()).isEqualTo(expectedKind)));
    }

    @Test
    void permitCarriesPipelineTerminateFlagWhenTrue() {
        StreamingPipeline     pipeline = pipelineWithTerminateFlag(true);
        AuthorizationDecision decision = AuthorizationDecision.PERMIT;

        Event event = pipeline.classify(decision, PLAN, false);

        assertThat(event).isInstanceOfSatisfying(PdpPermit.class,
                ev -> assertThat(ev.terminateOnItemEnforcementFailure()).isTrue());
    }

    @Test
    void permitCarriesPipelineTerminateFlagWhenFalse() {
        StreamingPipeline     pipeline = pipelineWithTerminateFlag(false);
        AuthorizationDecision decision = AuthorizationDecision.PERMIT;

        Event event = pipeline.classify(decision, PLAN, false);

        assertThat(event).isInstanceOfSatisfying(PdpPermit.class,
                ev -> assertThat(ev.terminateOnItemEnforcementFailure()).isFalse());
    }

    @Test
    void denyDoesNotCarryThePerItemFlagBecauseItIsIrrelevant() {
        StreamingPipeline pipeline = pipelineWithTerminateFlag(true);

        Event event = pipeline.classify(AuthorizationDecision.DENY, PLAN, false);

        // Behavioural assertion only: DENY produces a PdpDeny, regardless
        // of the per-item flag. The flag is for permitting + per-item
        // failure routing; DENY terminates outright.
        assertThat(event).isInstanceOf(PdpDeny.class);
    }

    static Stream<Arguments> verbCases() {
        return Stream.of(arguments(Decision.PERMIT, false, PdpPermit.class),
                arguments(Decision.PERMIT, true, PdpSuspend.class),
                arguments(Decision.SUSPEND, false, PdpSuspend.class),
                arguments(Decision.SUSPEND, true, PdpSuspend.class),
                arguments(Decision.INDETERMINATE, false, PdpSuspend.class),
                arguments(Decision.NOT_APPLICABLE, false, PdpSuspend.class),
                arguments(Decision.DENY, false, PdpDeny.class), arguments(Decision.DENY, true, PdpDeny.class));
    }

    static Stream<Arguments> suspendCases() {
        return Stream.of(arguments(Decision.PERMIT, true, SuspendKind.PERMIT_NOT_ENFORCEABLE),
                arguments(Decision.SUSPEND, false, SuspendKind.POLICY_SUSPENDED),
                arguments(Decision.INDETERMINATE, false, SuspendKind.EVALUATION_ERROR),
                arguments(Decision.NOT_APPLICABLE, false, SuspendKind.NO_POLICY_APPLICABLE));
    }

    private static AuthorizationDecision decisionFor(Decision verb) {
        return switch (verb) {
        case PERMIT         -> AuthorizationDecision.PERMIT;
        case DENY           -> AuthorizationDecision.DENY;
        case SUSPEND        -> AuthorizationDecision.SUSPEND;
        case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
        case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }
}
