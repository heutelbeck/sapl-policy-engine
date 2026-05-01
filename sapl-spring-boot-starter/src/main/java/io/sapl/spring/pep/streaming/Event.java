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
 * The input alphabet of the streaming PEP's FSM. Sealed into eight cases
 * covering the three input domains: PDP-side decision events, RAP-side
 * stream events, and downstream subscriber-side lifecycle events.
 * <p>
 * The pipeline pre-classifies PDP decisions: it receives a raw
 * {@link AuthorizationDecision}, builds the {@link EnforcementPlan}
 * (always succeeds; substitutes are baked into the plan), runs the plan's
 * decision-scoped handlers, and emits the appropriate event variant by
 * decision verb:
 * <ul>
 * <li>{@code PERMIT} + decision-scoped OK → {@link PdpPermit}</li>
 * <li>{@code PERMIT} + decision-scoped failed → {@link PdpSuspend} with
 * reason {@link TransitionReason.PermitNotEnforceable}</li>
 * <li>{@code SUSPEND} → {@link PdpSuspend} with reason
 * {@link TransitionReason.PolicySuspended}</li>
 * <li>{@code INDETERMINATE} → {@link PdpSuspend} with reason
 * {@link TransitionReason.EvaluationError}</li>
 * <li>{@code NOT_APPLICABLE} → {@link PdpSuspend} with reason
 * {@link TransitionReason.NoPolicyApplicable}</li>
 * <li>{@code DENY} → {@link PdpDeny}</li>
 * </ul>
 *
 * @since 4.1.0
 */
public sealed interface Event {

    /**
     * The PDP returned PERMIT and decision-scoped enforcement succeeded.
     * The state machine transitions to {@link State.Permitting} carrying
     * the plan and the {@code terminateOnItemEnforcementFailure} flag
     * (stamped from the annotation by the pipeline at event-construction
     * time so a future PDP-supplied override could change it
     * mid-stream).
     */
    record PdpPermit(AuthorizationDecision decision, EnforcementPlan plan, boolean terminateOnItemEnforcementFailure)
            implements Event {}

    /**
     * The PDP returned a non-DENY non-PERMIT decision (SUSPEND,
     * INDETERMINATE, NOT_APPLICABLE), or PERMIT with failed
     * decision-scoped enforcement. The state machine transitions to
     * {@link State.Suspended} carrying the plan and the discriminating
     * {@link TransitionReason}.
     */
    record PdpSuspend(AuthorizationDecision decision, EnforcementPlan plan, TransitionReason reason) implements Event {}

    /**
     * The PDP returned an explicit DENY. The state machine transitions
     * to {@link State.Terminated} and emits a terminal
     * {@link Emission.EmitError}.
     */
    record PdpDeny(AuthorizationDecision decision, EnforcementPlan plan) implements Event {}

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
     * {@link Emission.StayQuiet}, and obligation failure to either
     * {@link State.Terminated} (when the binding
     * {@code terminateOnItemEnforcementFailure} on the current
     * {@link State.Permitting} is true) or {@link State.Suspended}
     * (otherwise).
     */
    record RapItem(Object payload, EnforcementResult<Object> enforcementResult) implements Event {}

    /**
     * The protected method (or the wrapping pipeline) raised. Terminal.
     */
    record RapError(Throwable throwable) implements Event {}

    /**
     * The protected method completed normally. Terminal.
     */
    record RapComplete() implements Event {

        public static final RapComplete INSTANCE = new RapComplete();
    }

    /**
     * The downstream subscriber cancelled. Terminal.
     */
    record Cancel() implements Event {

        public static final Cancel INSTANCE = new Cancel();
    }
}
