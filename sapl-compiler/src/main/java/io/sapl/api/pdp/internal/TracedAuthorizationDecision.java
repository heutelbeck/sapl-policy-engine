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
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link TracedDecision} that holds an authorization
 * decision along with its evaluation metadata and modification history.
 *
 * <p>
 * This implementation is immutable. The {@link #modified} method returns a new
 * instance with the updated decision and appended modification explanation.
 *
 * @param authorizationDecision the authorization decision
 * @param metadata the decision metadata
 * @param modifications list of modification explanations (empty if
 * decision was not modified by interceptors)
 */
public record TracedAuthorizationDecision(
        @NonNull AuthorizationDecision authorizationDecision,
        @NonNull DecisionMetadata metadata,
        @NonNull List<String> modifications) implements TracedDecision, Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a new traced authorization decision with no modifications.
     *
     * @param authorizationDecision the authorization decision
     * @param metadata the decision metadata
     */
    public TracedAuthorizationDecision(AuthorizationDecision authorizationDecision, DecisionMetadata metadata) {
        this(authorizationDecision, metadata, List.of());
    }

    @Override
    public AuthorizationDecision getAuthorizationDecision() {
        return authorizationDecision;
    }

    @Override
    public DecisionMetadata getMetadata() {
        return metadata;
    }

    @Override
    public TracedDecision modified(AuthorizationDecision decision, String explanation) {
        var newModifications = new ArrayList<>(modifications);
        newModifications.add(explanation);
        return new TracedAuthorizationDecision(decision, metadata, Collections.unmodifiableList(newModifications));
    }
}
