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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementResult;

/**
 * The input alphabet of the streaming PEP's FSM. Sealed into nine cases
 * that cover the three input domains: PDP-side events, RAP-side events,
 * and downstream subscriber-side events.
 * <p>
 * The PDP-side decision events ({@link PdpPermit}, {@link PdpDeny}) are
 * pre-classified by the Reactor adapter: the adapter receives a raw
 * {@link AuthorizationDecision}, builds the {@link EnforcementPlan}
 * (always succeeds; substitutes are baked into the plan), executes the
 * plan's {@code DecisionSignal} handlers, and emits {@link PdpPermit} only
 * when {@code decision.decision() == PERMIT} and the decision-scoped
 * enforcement reported {@code failureState=false}. Anything else maps to
 * {@link PdpDeny} with the appropriate {@link TransitionReason}.
 * <p>
 * Both decision events carry the binding {@code hardDeny} for that
 * decision. Today the value is always derived from the annotation; in a
 * future release {@code AuthorizationDecision} may carry an override and
 * the adapter resolves
 * {@code !decision.survivesDeny().orElse(annotationSurvivesDeny)}.
 * The state machine consults {@code hardDeny} only on deny-shaped
 * transitions; a mid-stream change of strategy is effective immediately on
 * the transition that brought it.
 *
 * @since 4.1.0
 */
public sealed interface Event {

    /**
     * The PDP returned PERMIT and the plan's decision-scoped enforcement
     * succeeded ({@code failureState == false}). The state machine
     * transitions into {@link State.Permitting} with this event's plan and
     * binds {@code hardDeny} for subsequent deny-shaped events.
     */
    record PdpPermit(AuthorizationDecision decision, EnforcementPlan plan, boolean hardDeny) implements Event {}

    /**
     * The decision was non-PERMIT, OR PERMIT with failed decision-scoped
     * enforcement. With {@code hardDeny == true} the state machine
     * transitions to {@link State.Terminated} and emits a terminal
     * {@link Emission.EmitError}. With {@code hardDeny == false} it
     * transitions to {@link State.Denying} carrying the plan and reason.
     */
    record PdpDeny(AuthorizationDecision decision, EnforcementPlan plan, TransitionReason reason, boolean hardDeny)
            implements Event {}

    /**
     * The PDP's decision flux completed normally. No further decisions
     * will arrive. The current state is preserved; the machine continues
     * to gate items against the last-known plan until the RAP completes
     * or terminates by another route.
     */
    record PdpComplete() implements Event {
        public static final PdpComplete INSTANCE = new PdpComplete();
    }

    /**
     * The PDP's decision flux raised. Treated as terminal: emit the
     * throwable downstream and transition to {@link State.Terminated}.
     */
    record PdpError(Throwable throwable) implements Event {}

    /**
     * The protected method emitted an item. Per-item enforcement has
     * already been attempted by the adapter; {@code enforcementResult}
     * carries both the post-mapper value and the {@code failureState}
     * boolean. The state machine routes successful items to
     * {@link Emission.Emit}, null-mapper-result to
     * {@link Emission.StayQuiet}, and obligation-failure to the deny path
     * with {@link TransitionReason.ItemEnforcementFailed}; the deny path
     * consults the binding {@code hardDeny} from the current
     * {@link State.Permitting} state.
     */
    record RapItem(Object payload, EnforcementResult<Object> enforcementResult) implements Event {}

    /**
     * The protected method (or the wrapping pipeline) raised. Terminal
     * across all variants.
     */
    record RapError(Throwable throwable) implements Event {}

    /**
     * The protected method completed normally. Terminal across all
     * variants.
     */
    record RapComplete() implements Event {
        public static final RapComplete INSTANCE = new RapComplete();
    }

    /**
     * The downstream subscriber cancelled. Terminal across all variants.
     */
    record Cancel() implements Event {
        public static final Cancel INSTANCE = new Cancel();
    }

    /**
     * The downstream subscriber requested {@code n} items. Forwarded for
     * lifecycle-handler firing (the plan's {@code SubscriptionSignal}
     * handlers), no state change.
     */
    record Request(long demand) implements Event {}
}
