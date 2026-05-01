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

/**
 * Payload metadata describing why the FSM crossed a state boundary.
 * Carried by {@link State.Suspended} and {@link State.Terminated} as
 * the cause of the current state, and by
 * {@link Emission.EmitTransition} so subscribers (when the PEP is
 * configured with {@code signalTransitions = true}) can react
 * differently per cause.
 * <p>
 * Six concrete reasons, grouped by direction of the transition they
 * describe:
 * <ul>
 * <li>Toward suspended: {@link PolicySuspended} (explicit
 * {@code Decision.SUSPEND}), {@link EvaluationError}
 * ({@code INDETERMINATE}), {@link NoPolicyApplicable}
 * ({@code NOT_APPLICABLE}), {@link PermitNotEnforceable} (PERMIT but
 * decision-scoped handlers failed), {@link ItemEnforcementFailed}
 * (per-item handler failed under PERMIT).</li>
 * <li>Toward terminated: {@link DecisionDenied} (explicit
 * {@code Decision.DENY}). Also {@link ItemEnforcementFailed} when the
 * PEP is configured with
 * {@code terminateOnItemEnforcementFailure = true}.</li>
 * <li>Toward permitting: {@link Granted} (initial grant from pending
 * or resume from suspended).</li>
 * </ul>
 *
 * @since 4.1.0
 */
public sealed interface TransitionReason {

    /**
     * The PDP returned an explicit {@code Decision.DENY}. Terminal:
     * the subscription ends with
     * {@link org.springframework.security.access.AccessDeniedException
     * AccessDeniedException}.
     */
    record DecisionDenied(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * The PDP returned {@code Decision.SUSPEND}: the policy explicitly
     * paused this access. The subscription is preserved and items are
     * dropped silently until a later {@code Decision.PERMIT} resumes
     * the flow.
     */
    record PolicySuspended(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * The PDP returned {@code Decision.INDETERMINATE}: policy
     * evaluation encountered an error (often transient, e.g. PIP
     * timeout or network blip). Routed to suspended for resilience;
     * a later successful evaluation may resume the flow. Operators
     * who want hard fail-closed semantics on evaluation errors set
     * the combining algorithm's error handling to {@code propagate}
     * with a default decision of {@code deny}.
     */
    record EvaluationError(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * The PDP returned {@code Decision.NOT_APPLICABLE}: no policy
     * matched and the combining algorithm's default decision is
     * {@code abstain}. Routed to suspended on the same rationale as
     * {@link EvaluationError}: streaming subscriptions benefit from
     * surviving transient policy gaps. Operators who want hard
     * fail-closed semantics set the combining algorithm's default
     * decision to {@code deny}.
     */
    record NoPolicyApplicable(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * The PDP returned {@code Decision.PERMIT} but the plan's
     * decision-scoped enforcement (the {@code DecisionSignal}
     * handlers, including planner-inserted failure substitutes for
     * unresolvable or ambiguous constraints) reported
     * {@code failureState=true}. The permit cannot be honored. Routed
     * to suspended; a future decision may carry obligations the PEP
     * can fulfil.
     */
    record PermitNotEnforceable(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * A per-item obligation handler failed when enforcing a single
     * item under an otherwise-valid PERMIT. The PEP's
     * {@code terminateOnItemEnforcementFailure} flag controls the
     * downstream behaviour: {@code true} terminates the subscription;
     * {@code false} (default) transitions to suspended and waits for
     * a fresh decision.
     *
     * @param payload the item whose enforcement failed
     * @param throwable the cause of the failure, if available
     */
    record ItemEnforcementFailed(Object payload, @Nullable Throwable throwable) implements TransitionReason {}

    /**
     * The PEP entered or resumed permitting state. Emitted on the
     * pending-to-permitting transition (initial grant) and the
     * suspended-to-permitting transition (resume). Plan replacement
     * (permitting-to-permitting) is silent.
     */
    record Granted(AuthorizationDecision decision) implements TransitionReason {}
}
