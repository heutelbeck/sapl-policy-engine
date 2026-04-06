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

import java.io.Serial;
import java.io.Serializable;

import io.sapl.api.SaplVersion;

/**
 * Compile-time tuning flags for the SAPL policy compiler. Controls indexing
 * strategy, IN-operator unrolling, and AUTO-mode index selection thresholds.
 * <p>
 * All fields are optional in pdp.json; missing fields default to
 * {@link #defaults()}.
 *
 * @param indexing policy index strategy name (e.g., "AUTO", "NAIVE",
 * "CANONICAL", "MTBDD").
 * Resolved to an implementation by the index factory in sapl-pdp.
 * @param unrollInOperator whether to unroll {@code EXPR in [a, b, c]} into
 * equality chains for improved index matching
 * @param minPoliciesForCanonical minimum policy count before AUTO mode
 * considers the canonical index
 * @param minSharingForCanonical minimum average formulas-per-predicate ratio
 * for AUTO mode to keep the canonical index
 * @param maxPolicyDocuments maximum number of policy documents loaded from
 * a directory or bundle. Safety limit against excessive file counts.
 */
public record CompilerFlags(
        String indexing,
        boolean unrollInOperator,
        int minPoliciesForCanonical,
        double minSharingForCanonical,
        int maxPolicyDocuments) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final int DEFAULT_MAX_POLICY_DOCUMENTS = 10_000;

    /**
     * @return default compiler flags (AUTO indexing, no unrolling, thresholds
     * 10 policies / 1.5 sharing ratio, 10000 max documents)
     */
    public static CompilerFlags defaults() {
        return new CompilerFlags("AUTO", false, 10, 1.5, DEFAULT_MAX_POLICY_DOCUMENTS);
    }

}
