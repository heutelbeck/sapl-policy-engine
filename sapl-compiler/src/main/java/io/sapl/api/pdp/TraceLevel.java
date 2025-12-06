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

/**
 * Controls the granularity of trace information gathered during policy
 * evaluation.
 * <p>
 * Trace level is a compile-time decision that affects the generated evaluation
 * expressions. COVERAGE level provides additional data for test analysis at the
 * cost of increased memory allocation and processing overhead.
 * <p>
 * To change trace level, push a new PDP configuration which triggers
 * recompilation of policies with the new level.
 */
public enum TraceLevel {

    /**
     * Standard tracing of evaluated policies. Default behavior.
     * <p>
     * Traces include:
     * <ul>
     * <li>Policies that matched (target evaluated to true)</li>
     * <li>Policies that evaluated but resulted in NOT_APPLICABLE</li>
     * <li>Policies that errored during evaluation</li>
     * <li>Attribute access records</li>
     * <li>Error details including target expression errors</li>
     * <li>Aggregate counts (totalPolicies/totalDocuments) for completeness
     * proof</li>
     * </ul>
     * <p>
     * Non-matching policies (target evaluated to false) are not individually
     * listed, but their count is included in the aggregate totals.
     */
    STANDARD,

    /**
     * Full expression-level coverage tracking for test analysis.
     * <p>
     * In addition to STANDARD tracing, records per-evaluation coverage data:
     * <ul>
     * <li>Expression evaluation records with source locations</li>
     * <li>Branch coverage for conditional expressions</li>
     * <li>Function invocation tracking</li>
     * <li>Attribute access patterns</li>
     * </ul>
     * <p>
     * Coverage data is emitted per-evaluation. Aggregation across evaluations is
     * the responsibility of the coverage collector (separation of concerns).
     * <p>
     * Significant overhead - intended for test frameworks, not production.
     */
    COVERAGE;

    /**
     * Returns whether this trace level includes coverage data.
     *
     * @return true for COVERAGE level only
     */
    public boolean includesCoverage() {
        return this == COVERAGE;
    }
}
