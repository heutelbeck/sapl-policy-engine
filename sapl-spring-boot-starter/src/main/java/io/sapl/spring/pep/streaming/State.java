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

import org.jspecify.annotations.Nullable;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;

/**
 * The state set of the streaming PEP's FSM. Sealed into four cases that
 * together describe the entire lifecycle of one subscription. Each case
 * carries the runtime context it needs (the active enforcement plan, the
 * deny reason for observability, the binding deny strategy from the
 * triggering decision).
 * <p>
 * The initial state is {@link Pending#INSTANCE}; {@link Terminated} is
 * the absorbing state. Operationally the FSM is realized as a
 * multi-output Mealy machine
 * <code>M = (S, s&#x2080;, &#x3A3;, &#x39B;, &#x3B4;, &#x3BB;)</code>
 * with one combined step function returning
 * {@code (State', List<Emission>)}.
 * <p>
 * Mode (the deny strategy as a boolean {@code hardDeny}) lives only on
 * non-terminal post-decision states ({@link Permitting}, {@link Denying}),
 * established by the triggering decision. {@link Pending} is mode-less
 * (no decision has arrived to bind a strategy). {@link Reachable} is the
 * union of states for which a binding mode exists.
 *
 * @since 4.1.0
 */
public sealed interface State {

    /**
     * No PDP decision has arrived yet. The pipeline is subscribed to the
     * PDP and (depending on lazy-vs-eager configuration) may or may not
     * have subscribed to the RAP. Carries no mode: the operational mode is
     * established by the first decision, not asserted by Pending. Singleton.
     */
    record Pending() implements State {

        public static final Pending INSTANCE = new Pending();
    }

    /**
     * The current decision permits and the {@link EnforcementPlan} is
     * usable. Per-item enforcement runs against {@code plan}; lifecycle
     * signals fire against it as long as it remains current.
     *
     * @param plan the active plan for this decision
     * @param lastDecision the decision that put us here
     * @param hardDeny the binding deny strategy from the triggering
     * decision: {@code true} means a subsequent deny terminates the
     * subscription, {@code false} means the subscription survives a deny
     * and may resume on later PERMIT
     */
    record Permitting(EnforcementPlan plan, AuthorizationDecision lastDecision, boolean hardDeny) implements State {}

    /**
     * The current decision denies access (or per-item enforcement failed
     * under a permit, or decision-scoped enforcement failed). Reachable
     * only when the binding {@code hardDeny} is {@code false}; under
     * {@code hardDeny == true} the FSM transitions directly to
     * {@link Terminated} on any deny-shaped event.
     * <p>
     * {@code plan} is always present. The planner builds a plan for every
     * PDP decision regardless of its kind (PERMIT / DENY / INDETERMINATE /
     * NOT_APPLICABLE) for non-PERMIT decisions the plan typically carries
     * deny-side obligations (audit, custom error response, redirect, ...).
     * When a decision has no constraints, the plan is empty but still
     * present.
     *
     * @param plan the active plan for this decision (always present;
     * possibly empty)
     * @param lastDecision the decision that put us here
     * @param reason why we are denied (decision-deny, plan inadmissible,
     * decision-scoped handler failure, item-enforcement failure)
     * @param hardDeny the binding deny strategy from the triggering
     * decision; preserved here so a subsequent item-enforcement failure
     * uses the same strategy
     */
    record Denying(EnforcementPlan plan, AuthorizationDecision lastDecision, TransitionReason reason, boolean hardDeny)
            implements State {}

    /**
     * Absorbing state. Reached on RAP completion, RAP error, downstream
     * cancellation, PDP error, or any deny-shaped event when the binding
     * {@code hardDeny} was {@code true}. No further events are processed.
     *
     * @param reason why we terminated; {@code null} for normal completion
     * or cancellation, non-null for error/deny terminations.
     */
    record Terminated(@Nullable TransitionReason reason) implements State {}
}
