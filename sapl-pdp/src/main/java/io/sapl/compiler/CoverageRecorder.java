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
package io.sapl.compiler;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.pdp.internal.ConditionHit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects condition hits during policy evaluation for coverage tracking.
 * <p>
 * Created per policy at compile time and captured by condition expression
 * wrappers. The hits list is cleared before
 * each evaluation and collected when building the traced decision.
 * <p>
 * This design scopes coverage collection to the policy level rather than
 * threading it through EvaluationContext,
 * keeping the context immutable.
 */
public class CoverageRecorder {

    private final List<ConditionHit> hits = new CopyOnWriteArrayList<>();

    /**
     * Records a condition hit with full position data.
     *
     * @param statementId
     * the 0-based index of the statement in the policy body
     * @param result
     * the boolean result of the condition evaluation
     * @param location
     * the source location of the condition (may be null)
     */
    public void recordHit(int statementId, boolean result, SourceLocation location) {
        int startLine = location != null ? location.line() : 0;
        int endLine   = location != null ? location.endLine() : 0;
        int startChar = location != null ? location.start() : 0;
        int endChar   = location != null ? location.end() : 0;
        hits.add(new ConditionHit(statementId, result, startLine, endLine, startChar, endChar));
    }

    /**
     * Returns collected hits and clears the list for the next evaluation.
     *
     * @return list of condition hits from the current evaluation
     */
    public List<ConditionHit> collectAndClear() {
        var result = List.copyOf(hits);
        hits.clear();
        return result;
    }

    /**
     * Clears recorded hits without returning them.
     */
    public void clear() {
        hits.clear();
    }
}
