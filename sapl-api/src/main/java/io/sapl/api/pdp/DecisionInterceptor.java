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
 * Observes every {@link TracedDecision} produced by the PDP. Implementations
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
     * Called once per decision emitted by the PDP.
     *
     * @param decision the decision and its trace
     * @param timestamp wall-clock timestamp at which the PDP produced
     * the decision
     * @param subscriptionId the per-evaluation subscription identifier
     * @param authorizationSubscription the subscription the PDP
     * evaluated to produce the decision
     */
    void onDecision(TracedDecision decision, Instant timestamp, String subscriptionId,
            AuthorizationSubscription authorizationSubscription);
}
