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
 * Complete metadata for a policy decision for auditing and debugging.
 *
 * @param pdpId PDP instance identifier (for clustered deployments)
 * @param configurationId policy configuration version identifier
 * @param subscriptionId identifier for correlating streaming decisions
 * @param subscription the evaluated authorization subscription
 * @param timestamp when this decision was produced
 * @param attributes all attribute invocations that contributed to this decision
 * @param errors errors that occurred during evaluation
 * @param documentDecisions map from document name to its intermediate decision
 * (before combining)
 * @param combiningAlgorithm algorithm used to combine policy results
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
