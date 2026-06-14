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
import io.sapl.spring.pep.streaming.MealyMachine.DenyKind;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason;
import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link StreamingPipeline#classify}, the pre-classification
 * function that turns a raw PDP
 * {@link AuthorizationDecision} (plus the outcome of decision-scoped
 * enforcement) into the {@link Event} variant the
 * FSM consumes.
 * <p>
 * Under the strict fail-closed discipline, only an explicit
 * {@code Decision.SUSPEND} produces a {@link PdpSuspend}.
 * Every other non-PERMIT outcome and a PERMIT whose decision-scoped enforcement
 * failed produce a {@link PdpDeny}
 * carrying a discriminating {@link DenyKind} for diagnostics.
 */
class StreamingPipelineClassifyTests {

    private static final EnforcementPlan PLAN = new EnforcementPlan(Map.of());

    private static StreamingPipeline pipeline() {
        return new StreamingPipeline(false, Flux.empty(), d -> PLAN, Flux::empty, false);
    }

    @ParameterizedTest(name = "{0} (failed={1}) -> {2}")
    @MethodSource("verbCases")
    void classifyProducesExpectedEventForVerbAndFailedFlag(Decision verb, boolean failed,
            Class<? extends Event> expectedEventType) {
        AuthorizationDecision decision = decisionFor(verb);

        Event event = pipeline().classify(decision, PLAN, failed);

        assertThat(event).isInstanceOf(expectedEventType);
    }

    @ParameterizedTest(name = "{0} (failed={1}) -> DenyKind {2}")
    @MethodSource("denyCases")
    void classifyTagsDenyKindForDenyingInputs(Decision verb, boolean failed, DenyKind expectedKind) {
        AuthorizationDecision decision = decisionFor(verb);

        Event event = pipeline().classify(decision, PLAN, failed);

        assertThat(event).isInstanceOfSatisfying(PdpDeny.class, ev -> assertThat(ev.kind()).isEqualTo(expectedKind));
    }

    @Test
    void classifySuspendCarriesTransitionReasonWithDecision() {
        AuthorizationDecision decision = AuthorizationDecision.SUSPEND;

        Event event = pipeline().classify(decision, PLAN, false);

        assertThat(event).isInstanceOfSatisfying(PdpSuspend.class,
                ev -> assertThat(ev.reason()).isInstanceOfSatisfying(TransitionReason.Suspended.class,
                        r -> assertThat(r.decision()).isEqualTo(decision)));
    }

    @Test
    void classifyPermitProducesPdpPermit() {
        AuthorizationDecision decision = AuthorizationDecision.PERMIT;

        Event event = pipeline().classify(decision, PLAN, false);

        assertThat(event).isInstanceOf(PdpPermit.class);
    }

    static Stream<Arguments> verbCases() {
        return Stream.of(arguments(Decision.PERMIT, false, PdpPermit.class),
                arguments(Decision.PERMIT, true, PdpDeny.class), arguments(Decision.SUSPEND, false, PdpSuspend.class),
                arguments(Decision.SUSPEND, true, PdpSuspend.class),
                arguments(Decision.INDETERMINATE, false, PdpDeny.class),
                arguments(Decision.NOT_APPLICABLE, false, PdpDeny.class),
                arguments(Decision.DENY, false, PdpDeny.class), arguments(Decision.DENY, true, PdpDeny.class));
    }

    static Stream<Arguments> denyCases() {
        return Stream.of(arguments(Decision.PERMIT, true, DenyKind.PERMIT_NOT_ENFORCEABLE),
                arguments(Decision.INDETERMINATE, false, DenyKind.INDETERMINATE),
                arguments(Decision.NOT_APPLICABLE, false, DenyKind.NO_POLICY_APPLICABLE),
                arguments(Decision.DENY, false, DenyKind.POLICY_DENIED),
                arguments(Decision.DENY, true, DenyKind.POLICY_DENIED));
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
