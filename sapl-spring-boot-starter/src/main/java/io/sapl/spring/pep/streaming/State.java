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
 * together describe the entire lifecycle of one subscription.
 * <p>
 * Routing through the FSM is driven by the PDP decision verb, not by an
 * annotation flag:
 * <ul>
 * <li>{@link Pending} — initial state, no decision has arrived.</li>
 * <li>{@link Permitting} — current decision is PERMIT and decision-scoped
 * enforcement succeeded; data items flow.</li>
 * <li>{@link Suspended} — current decision is SUSPEND, INDETERMINATE,
 * NOT_APPLICABLE, or PERMIT-with-failed-enforcement; subscription is
 * preserved, items are dropped silently, a later PERMIT resumes the
 * flow.</li>
 * <li>{@link Terminated} — absorbing; reached on RAP completion, RAP
 * error, downstream cancellation, PDP error, or an explicit DENY
 * decision.</li>
 * </ul>
 *
 * @since 4.1.0
 */
public sealed interface State {

    /**
     * No PDP decision has arrived yet. The pipeline is subscribed to the
     * PDP. Singleton.
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
     * @param terminateOnItemEnforcementFailure whether a subsequent
     * per-item enforcement failure terminates the subscription (true)
     * or transitions to {@link Suspended} (false). Stamped from the
     * triggering {@link Event.PdpPermit} so a future mid-stream change
     * (the PDP may someday supply this hint) takes effect on the next
     * permit.
     */
    record Permitting(
            EnforcementPlan plan,
            AuthorizationDecision lastDecision,
            boolean terminateOnItemEnforcementFailure) implements State {}

    /**
     * The PDP returned a non-DENY non-PERMIT decision (SUSPEND,
     * INDETERMINATE, NOT_APPLICABLE) or PERMIT with failed
     * decision-scoped enforcement. Subscription is preserved; items
     * arriving from the RAP are dropped silently. The next PERMIT
     * decision transitions back to {@link Permitting}; an explicit
     * DENY transitions to {@link Terminated}.
     *
     * @param plan the active plan for this decision (always present;
     * may be empty if the decision carried no constraints)
     * @param lastDecision the decision that put us here
     * @param reason why we are suspended (policy SUSPEND, evaluation
     * error, no policy applicable, permit not enforceable, item
     * enforcement failed)
     */
    record Suspended(EnforcementPlan plan, AuthorizationDecision lastDecision, TransitionReason reason)
            implements State {}

    /**
     * Absorbing state. Reached on RAP completion, RAP error, downstream
     * cancellation, PDP error, or an explicit DENY decision (or per-item
     * enforcement failure when the PEP is configured with
     * {@code terminateOnItemEnforcementFailure = true}). No further
     * events are processed.
     *
     * @param reason why we terminated; {@code null} for normal completion
     * or cancellation, non-null for error/deny terminations.
     */
    record Terminated(@Nullable TransitionReason reason) implements State {}
}
