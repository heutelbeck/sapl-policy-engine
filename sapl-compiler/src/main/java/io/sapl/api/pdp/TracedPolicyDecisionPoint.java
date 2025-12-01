/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.pdp.internal.TracedDecision;
import reactor.core.publisher.Flux;

/**
 * Extension of {@link PolicyDecisionPoint} that provides access to traced
 * decisions with full evaluation metadata.
 *
 * <p>
 * This interface is intended for internal tooling that needs access to trace
 * information, such as:
 * <ul>
 * <li>The SAPL Playground for interactive policy debugging</li>
 * <li>Audit logging systems that need complete decision context</li>
 * <li>Testing frameworks that verify policy behavior</li>
 * </ul>
 *
 * <p>
 * <strong>Security Warning:</strong> The traced decision contains sensitive
 * information including:
 * <ul>
 * <li>Policy document names and structure</li>
 * <li>Attribute values retrieved from PIPs</li>
 * <li>Detailed error messages that may reveal implementation details</li>
 * </ul>
 *
 * <p>
 * Consumers of this interface must ensure that trace information is not exposed
 * to untrusted parties. For external authorization requests, use the standard
 * {@link PolicyDecisionPoint#decide} method which returns only
 * {@link AuthorizationDecision} without trace information.
 *
 * @see PolicyDecisionPoint
 * @see TracedDecision
 */
public interface TracedPolicyDecisionPoint extends PolicyDecisionPoint {

    /**
     * Evaluates an authorization subscription and returns a continuous stream of
     * traced decisions. Each traced decision includes both the authorization
     * decision and complete evaluation metadata.
     *
     * <p>
     * New traced decisions are emitted whenever the authorization context changes
     * (e.g., due to attribute updates or policy changes).
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return a flux of traced decisions with full metadata
     */
    Flux<TracedDecision> decideTraced(AuthorizationSubscription authorizationSubscription);
}
