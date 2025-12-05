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
 * expressions. Higher levels provide more detailed tracing at the cost of
 * increased memory allocation and processing overhead.
 * <p>
 * To change trace level, push a new PDP configuration which triggers
 * recompilation of policies with the new level.
 */
public enum TraceLevel {

    /**
     * No trace metadata gathered. Returns TracedDecision with empty trace.
     * <p>
     * Use for high-throughput production scenarios where tracing is handled by
     * external systems or not required. The API remains consistent
     * (TracedDecision), but trace data is empty.
     */
    NONE,

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
     * </ul>
     * <p>
     * Non-matching policies (target evaluated to false) are not included.
     */
    STANDARD,

    /**
     * Audit-level tracing includes all policies, even non-matching ones.
     * <p>
     * In addition to STANDARD tracing, includes:
     * <ul>
     * <li>Non-matching policy names with {@code matched: false}</li>
     * <li>Summary counts of evaluated vs matched policies</li>
     * </ul>
     * <p>
     * Use for compliance audits requiring proof that all policies were considered.
     * Non-matching policies appear with minimal footprint (name only).
     */
    AUDIT,

    /**
     * Full expression-level coverage tracking for test analysis.
     * <p>
     * In addition to AUDIT tracing, records per-evaluation coverage data:
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
     * Returns whether this trace level includes non-matching policies.
     *
     * @return true for AUDIT and COVERAGE levels
     */
    public boolean includesNonMatching() {
        return this == AUDIT || this == COVERAGE;
    }

    /**
     * Returns whether this trace level includes coverage data.
     *
     * @return true for COVERAGE level only
     */
    public boolean includesCoverage() {
        return this == COVERAGE;
    }

    /**
     * Returns whether this trace level produces any trace data.
     *
     * @return false for NONE, true for all other levels
     */
    public boolean producesTrace() {
        return this != NONE;
    }
}
