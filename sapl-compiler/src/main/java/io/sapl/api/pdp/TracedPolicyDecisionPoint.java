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
 * Extension of {@link PolicyDecisionPoint} providing traced decisions with
 * evaluation metadata for debugging and auditing.
 *
 * <p>
 * <strong>Security:</strong> Traced decisions expose policy structure and
 * attribute values. Do not expose to untrusted parties.
 */
public interface TracedPolicyDecisionPoint extends PolicyDecisionPoint {

    /**
     * Evaluates a subscription returning traced decisions with evaluation metadata.
     *
     * @param authorizationSubscription the subscription to evaluate
     * @return stream of traced decisions
     */
    Flux<TracedDecision> decideTraced(AuthorizationSubscription authorizationSubscription);
}
