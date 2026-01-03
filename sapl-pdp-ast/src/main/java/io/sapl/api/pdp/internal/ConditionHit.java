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
 * <li>startLine/endLine: 1-based source line range (for visualization)</li>
 * <li>startChar/endChar: 0-based character offsets (for precise
 * highlighting)</li>
 * </ul>
 * <p>
 * Used only when trace level is COVERAGE. At STANDARD trace level, condition
 * hits are not recorded for zero overhead.
 *
 * @param statementId
 * the 0-based index of the statement in the policy body
 * @param result
 * the boolean result of the condition evaluation
 * @param startLine
 * the 1-based starting line number where the condition begins
 * @param endLine
 * the 1-based ending line number where the condition ends
 * @param startChar
 * the 0-based starting character offset in the document
 * @param endChar
 * the 0-based ending character offset in the document (exclusive)
 */
public record ConditionHit(int statementId, boolean result, int startLine, int endLine, int startChar, int endChar)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Converts this condition hit to a Value for inclusion in the trace.
     *
     * @return an ObjectValue with statementId, result, and position fields
     */
    public Value toValue() {
        return ObjectValue.builder().put(TraceFields.STATEMENT_ID, Value.of(statementId))
                .put(TraceFields.RESULT, Value.of(result)).put(TraceFields.START_LINE, Value.of(startLine))
                .put(TraceFields.END_LINE, Value.of(endLine)).put(TraceFields.START_CHAR, Value.of(startChar))
                .put(TraceFields.END_CHAR, Value.of(endChar)).build();
    }
}
