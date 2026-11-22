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
package io.sapl.api.pdp;

import java.time.Instant;

/**
 * Receives {@link TracedDecision}s for single subscriptions and groups. Implementations
 * are observation plugins (logging, metrics, audit). The PDP catches and
 * logs any exception thrown from {@link #onDecision}; misbehaving
 * interceptors must not affect authorization correctness.
 * <p>
 * Observe-only by design. Decisions cannot be mutated through this surface.
 * For pre- or post-evaluation transformation, register a separate
 * transformer plugin (not part of the current API).
 *
 * @since 4.1.0
 */
@FunctionalInterface
public interface DecisionInterceptor {

    /**
     * For a single subscription, observes the evaluation before the PDP
     * suppresses equal public decisions. For a group, callbacks start when
     * every item has a decision. The interceptor receives one call for each
     * item in the first complete bundle, then one call per item whose
     * {@link AuthorizationDecision} changed since the previous bundle.
     * Changes to obligations, advice or the returned resource count even when
     * the decision verb stays the same. Items reported from the same round
     * share its completion timestamp.
     *
     * @param decision the decision and its trace
     * @param timestamp wall-clock timestamp at which the PDP produced
     * the decision
     * @param subscriptionId the PDP-generated identifier for this subscription lifetime.
     * Each element of a multi-subscription has its own identifier, shared
     * by its decision and lifecycle callbacks. The public decision bundle
     * uses the element identifier supplied by the caller
     * @param authorizationSubscription the subscription the PDP
     * evaluated to produce the decision
     */
    void onDecision(TracedDecision decision, Instant timestamp, String subscriptionId,
            AuthorizationSubscription authorizationSubscription);
}
