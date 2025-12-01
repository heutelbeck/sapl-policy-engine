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

import io.sapl.api.pdp.AuthorizationDecision;

/**
 * A policy decision with full trace metadata for debugging and auditing.
 *
 * <p>
 * This interface represents an authorization decision enriched with complete
 * evaluation context: which attributes were consulted, what errors occurred,
 * which policies matched, and timing information.
 *
 * <p>
 * The traced decision allows interceptors to:
 * <ul>
 * <li>Log detailed audit trails of authorization decisions</li>
 * <li>Debug policy evaluation by examining attribute values</li>
 * <li>Modify decisions based on trace information (with explanation)</li>
 * <li>Generate compliance reports</li>
 * </ul>
 *
 * <p>
 * <strong>Security Note:</strong> This is an internal API. The trace metadata
 * may contain sensitive information about policy structure and attribute
 * values. External consumers should use
 * {@link io.sapl.api.pdp.AuthorizationDecision} which forms the security
 * perimeter and does not expose trace information.
 *
 * @see TracedDecisionInterceptor
 * @see DecisionMetadata
 */
public interface TracedDecision {

    /**
     * Returns the authorization decision (the external-facing result).
     *
     * <p>
     * This is the decision that would be returned to external consumers via the
     * standard {@link io.sapl.api.pdp.PolicyDecisionPoint#decide} method.
     *
     * @return the authorization decision
     */
    AuthorizationDecision getAuthorizationDecision();

    /**
     * Returns the full metadata for this decision.
     *
     * <p>
     * The metadata includes all attribute invocations, errors, matching
     * documents, and timing information that contributed to this decision.
     *
     * @return the decision metadata
     */
    DecisionMetadata getMetadata();

    /**
     * Creates a modified version of this traced decision with a different
     * authorization decision and an explanation.
     *
     * <p>
     * This method is used by interceptors that need to modify decisions (for
     * example, to add obligations or change the decision based on external
     * factors). The explanation is recorded for audit purposes.
     *
     * @param decision the new authorization decision
     * @param explanation a human-readable explanation of why the decision was
     * modified (for audit trail)
     * @return a new TracedDecision with the modified decision
     */
    TracedDecision modified(AuthorizationDecision decision, String explanation);
}
