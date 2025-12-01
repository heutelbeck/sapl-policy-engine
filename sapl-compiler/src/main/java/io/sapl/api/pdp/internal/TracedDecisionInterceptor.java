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

import java.util.function.UnaryOperator;

/**
 * Intercepts traced decisions for logging, auditing, or modification.
 *
 * <p>
 * Interceptors form a chain that processes every traced decision before it is
 * returned to external consumers. Each interceptor can:
 * <ul>
 * <li>Log the decision and its metadata for audit purposes</li>
 * <li>Transform the decision (adding obligations, changing the verdict)</li>
 * <li>Pass through unchanged (for observation-only interceptors)</li>
 * </ul>
 *
 * <p>
 * Interceptors are ordered by priority (lower values execute first). This
 * allows, for example, logging interceptors to run before modification
 * interceptors, capturing the original decision.
 *
 * <p>
 * <strong>Security Note:</strong> Interceptors see full trace data including
 * attribute values and policy names. They are trusted code running within the
 * PDP deployment and should not expose this information to untrusted parties.
 *
 * <p>
 * Example implementation:
 *
 * <pre>
 * {
 *     &#64;code
 *     public class AuditLogInterceptor implements TracedDecisionInterceptor {
 *         &#64;Override
 *         public TracedDecision apply(TracedDecision decision) {
 *             auditLogger.log(decision.getMetadata());
 *             return decision; // pass through unchanged
 *         }
 *
 *         @Override
 *         public Integer getPriority() {
 *             return -100; // run early to capture original decision
 *         }
 *     }
 * }
 * </pre>
 *
 * @see TracedDecision
 */
@FunctionalInterface
public interface TracedDecisionInterceptor
        extends UnaryOperator<TracedDecision>, Comparable<TracedDecisionInterceptor> {

    /**
     * Returns the priority of this interceptor.
     *
     * <p>
     * Interceptors with lower priority values execute first. The default
     * priority is 0. Use negative values to run before default interceptors,
     * positive values to run after.
     *
     * @return the interceptor priority
     */
    default Integer getPriority() {
        return 0;
    }

    /**
     * Compares interceptors by priority for ordering.
     *
     * @param other the other interceptor
     * @return comparison result based on priority
     */
    @Override
    default int compareTo(TracedDecisionInterceptor other) {
        return getPriority().compareTo(other.getPriority());
    }
}
