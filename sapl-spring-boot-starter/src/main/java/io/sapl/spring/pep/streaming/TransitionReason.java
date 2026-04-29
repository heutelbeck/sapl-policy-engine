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

/**
 * Payload metadata. <strong>Not part of the FSM's state set, input
 * alphabet, or output alphabet</strong> — used as a payload field of
 * certain states ({@link State.Denying}) and certain outputs
 * ({@link Emission.EmitTransition}) to carry the cause of a transition
 * forward for observability and (in the access-aware variant) for
 * client notification.
 * <p>
 * Used in two places:
 * <ul>
 * <li>Carried by {@link State.Denying} as metadata describing why the
 * subscription is currently denied.</li>
 * <li>Carried by {@link Emission.EmitTransition} as the payload describing
 * what just changed (used by
 * {@link io.sapl.spring.method.metadata.StreamMode#ACCESS_AWARE}
 * to inform the client).</li>
 * </ul>
 * <p>
 * The state machine treats all deny reasons as equivalent for transition
 * purposes — per-mode tables branch on {@code permitting -> denying}, not
 * on which kind of denial. The reason is preserved so observability,
 * logging, and (future) per-reason behaviour have access to it.
 * <p>
 * The set of reasons mirrors how the existing one-shot PEPs treat
 * decisions. Any non-PERMIT decision yields {@link DecisionDenied}. A
 * PERMIT whose decision-scoped enforcement (DecisionSignal handlers,
 * including planner-inserted failure substitutes for unresolvable
 * obligations) reports {@code failureState=true} yields
 * {@link PermitNotEnforceable}. The PEP cannot distinguish "planner
 * inserted a substitute" from "real handler threw" from outside the plan,
 * both are the same operational condition. We could not honor the permit.
 *
 * @since 4.1.0
 */
public sealed interface TransitionReason {

    /**
     * The PDP returned a non-PERMIT decision (DENY, INDETERMINATE, or
     * NOT_APPLICABLE). The plan's decision-scoped obligations have already
     * been discharged before this reason is constructed; the reason itself
     * carries only the originating decision for observability.
     */
    record DecisionDenied(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * The PDP returned PERMIT but the decision-scoped enforcement (the
     * plan's {@code DecisionSignal} handlers) failed — either because the
     * planner inserted a synthetic failure substitute for an unresolvable
     * or ambiguous constraint, or because a real handler threw. From the
     * PEP's perspective these are the same operational condition: the
     * permit cannot be honored, so the request is treated as denied.
     */
    record PermitNotEnforceable(AuthorizationDecision decision) implements TransitionReason {}

    /**
     * A per-item obligation handler failed when enforcing this specific
     * item. The plan itself is still valid; only this item's enforcement
     * could not be honored. Variant policy decides whether this is
     * terminal ({@link io.sapl.spring.method.metadata.StreamMode#TILL_DENIED})
     * or transitions to denying
     * ({@link io.sapl.spring.method.metadata.StreamMode#DROP_WHILE_DENIED},
     * {@link io.sapl.spring.method.metadata.StreamMode#ACCESS_AWARE}).
     */
    record ItemEnforcementFailed(Object item, Throwable throwable) implements TransitionReason {}

    /**
     * Symmetric inverse of the deny reasons: a permitting decision
     * established access. Emitted by
     * {@link io.sapl.spring.method.metadata.StreamMode#ACCESS_AWARE} on
     * any non-Permitting -> Permitting transition (initial grant from
     * Pending or recovery from Denying), gated by the annotation's
     * {@code signalAccessGranted} flag.
     */
    record Granted(AuthorizationDecision decision) implements TransitionReason {}
}
