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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;

import java.io.Serial;
import java.io.Serializable;

/**
 * Records a single condition evaluation hit for coverage tracking.
 * <p>
 * Each hit captures:
 * <ul>
 * <li>statementId: 0-based index into the policy body statements (compatible
 * with sapl-coverage-api)</li>
 * <li>result: whether the condition evaluated to true or false</li>
 * <li>line: 1-based source line number (for visualization in editors)</li>
 * </ul>
 * <p>
 * Used only when trace level is COVERAGE. At STANDARD trace level, condition
 * hits are not recorded for zero overhead.
 *
 * @param statementId
 * the 0-based index of the statement in the policy body
 * @param result
 * the boolean result of the condition evaluation
 * @param line
 * the 1-based source line number where the condition appears
 */
public record ConditionHit(int statementId, boolean result, int line) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Converts this condition hit to a Value for inclusion in the trace.
     *
     * @return an ObjectValue with statementId, result, and line fields
     */
    public Value toValue() {
        return ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(statementId))
                .put(TraceFields.RESULT, Value.of(result)).put(TraceFields.LINE, Value.of(line)).build();
    }
}
