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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A policy decision with trace information for debugging, auditing, and
 * interceptor processing.
 * <p>
 * This record holds three pieces of information:
 * <ul>
 * <li>{@code originalTrace} - The immutable TracedPdpDecision Value containing
 * the complete trace of how the decision
 * was derived, including PDP metadata, evaluated documents, and attribute
 * accesses.</li>
 * <li>{@code currentDecision} - The authorization decision that may be modified
 * by interceptors. Extracted from the
 * trace on creation, this value can be replaced when interceptors transform the
 * decision.</li>
 * <li>{@code modifications} - An audit trail of explanations from interceptors
 * that have modified the decision.</li>
 * </ul>
 * <p>
 * Example interceptor usage:
 *
 * <pre>{@code
 * TracedDecision original = pdp.decideTraced(subscription).blockFirst();
 * if (someCondition) {
 *     TracedDecision modified = original.modified(AuthorizationDecision.DENY,
 *             "Override: resource access denied due to maintenance window");
 * }
 * }</pre>
 *
 * @param originalTrace
 * the immutable TracedPdpDecision Value
 * @param currentDecision
 * the authorization decision (may be modified from original)
 * @param modifications
 * audit trail of interceptor modifications
 */
public record TracedDecision(
        @NonNull Value originalTrace,
        @NonNull AuthorizationDecision currentDecision,
        @NonNull List<String> modifications) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a TracedDecision from a TracedPdpDecision Value.
     * <p>
     * The authorization decision is extracted from the trace using
     * {@link TracedPdpDecision#toAuthorizationDecision(Value)}.
     *
     * @param trace
     * the TracedPdpDecision Value
     */
    public TracedDecision(Value trace) {
        this(trace, TracedPdpDecision.toAuthorizationDecision(trace), List.of());
    }

    /**
     * Creates a modified traced decision with an explanation for audit purposes.
     * <p>
     * The original trace is preserved unchanged - only the current decision and
     * modifications list are updated. This
     * allows interceptors to transform decisions while maintaining full
     * traceability.
     *
     * @param decision
     * the new authorization decision
     * @param explanation
     * why the decision was modified
     *
     * @return a new TracedDecision with the modified decision
     */
    public TracedDecision modified(AuthorizationDecision decision, String explanation) {
        var newModifications = new ArrayList<>(modifications);
        newModifications.add(explanation);
        return new TracedDecision(originalTrace, decision, Collections.unmodifiableList(newModifications));
    }

    /**
     * Returns the authorization decision.
     * <p>
     * This is an alias for {@link #currentDecision()} to maintain API
     * compatibility.
     *
     * @return the current authorization decision
     */
    public AuthorizationDecision authorizationDecision() {
        return currentDecision;
    }
}
