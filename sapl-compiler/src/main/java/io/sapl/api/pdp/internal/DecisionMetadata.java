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
package io.sapl.api.pdp.internal;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Complete metadata for a policy decision, including all information needed for
 * auditing, debugging, and compliance reporting.
 *
 * <p>
 * This record captures the full context of how an authorization decision was
 * made:
 * <ul>
 * <li>The subscription that triggered the decision</li>
 * <li>All attribute values that were retrieved during evaluation</li>
 * <li>Any errors that occurred during evaluation</li>
 * <li>Which policy documents matched and contributed to the decision</li>
 * <li>The combining algorithm used to combine multiple policy results</li>
 * </ul>
 *
 * <p>
 * For streaming decisions where values update over time, each decision emission
 * includes ALL attribute values that were active at decision time. This avoids
 * the need for historical lookups when debugging a specific decision.
 *
 * <p>
 * This is an internal API for use by PDP implementations and trusted tooling.
 * External consumers should use {@link io.sapl.api.pdp.AuthorizationDecision}
 * which does not expose trace information.
 *
 * @param pdpId identifier of the PDP instance that produced this decision,
 * useful in clustered deployments for debugging
 * @param configurationId identifier of the PDP configuration (policy set
 * version) active when this decision was made
 * @param subscriptionId a unique identifier for correlating decisions with
 * subscriptions in streaming scenarios
 * @param subscription the authorization subscription that was evaluated
 * @param timestamp when this decision was produced
 * @param attributes all attribute invocations that contributed to this
 * decision
 * @param errors any errors that occurred during evaluation (these
 * are also present in the decision if they caused
 * INDETERMINATE, but collected here for convenience)
 * @param documentDecisions map from document name to its intermediate decision
 * object (before combining). The Value is the decision
 * object with decision, obligations, advice, and resource
 * fields. Documents that did not match are not included.
 * @param combiningAlgorithm the algorithm used to combine results from multiple
 * policies (e.g., "deny-overrides", "permit-overrides")
 */
public record DecisionMetadata(
        @NonNull String pdpId,
        @NonNull String configurationId,
        @NonNull String subscriptionId,
        @NonNull AuthorizationSubscription subscription,
        @NonNull Instant timestamp,
        @NonNull List<AttributeRecord> attributes,
        @NonNull List<ErrorValue> errors,
        @NonNull Map<String, Value> documentDecisions,
        @NonNull String combiningAlgorithm) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
