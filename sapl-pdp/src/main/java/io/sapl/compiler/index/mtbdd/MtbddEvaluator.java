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
package io.sapl.compiler.index.mtbdd;

import java.util.BitSet;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Evaluates an MTBDD against a request context by traversing from
 * root to leaf, evaluating one predicate per decision node.
 * <p>
 * The traversal follows exactly one path through the diagram.
 * When a predicate errors, two things happen:
 * <ol>
 * <li>All formulas referencing that predicate are added to the
 * errored set (looked up from the precomputed array in
 * {@link VariableOrder})</li>
 * <li>The traversal follows the error edge to continue resolving
 * formulas that do NOT reference the errored predicate</li>
 * </ol>
 * The result is the terminal's matched/errored sets combined with
 * any errors accumulated during traversal.
 */
@UtilityClass
class MtbddEvaluator {

    /**
     * Result of evaluating an MTBDD: which formulas matched, which errored,
     * and the first ErrorValue encountered (for error vote construction).
     *
     * @param matched formula indices that are satisfied
     * @param errored formula indices that encountered predicate errors
     * @param firstError the first ErrorValue from predicate evaluation, or null
     */
    record EvaluationResult(BitSet matched, BitSet errored, ErrorValue firstError) {}

    /**
     * Evaluates the MTBDD against the given context.
     *
     * @param root the MTBDD root node
     * @param order the variable order (for predicate operators and error lookup)
     * @param ctx the evaluation context
     * @return matched and errored formula indices with the first error value
     */
    static EvaluationResult evaluate(MtbddNode root, VariableOrder order, EvaluationContext ctx) {
        val        accumulatedErrors = new BitSet();
        ErrorValue firstError        = null;
        var        node              = root;

        while (node instanceof Decision(int level, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild)) {
            val predicate = order.predicateAt(level);
            val result    = predicate.operator().evaluate(ctx);

            if (result instanceof ErrorValue error) {
                if (firstError == null) {
                    firstError = error;
                }
                accumulatedErrors.or(order.erroredFormulas(level));
                node = errorChild;
            } else if (result instanceof BooleanValue(var b) && b) {
                node = trueChild;
            } else {
                node = falseChild;
            }
        }

        val terminal = (Terminal) node;
        val matched  = (BitSet) terminal.matched().clone();
        matched.andNot(accumulatedErrors);
        return new EvaluationResult(matched, accumulatedErrors, firstError);
    }

    /**
     * Convenience: evaluates a single-formula MTBDD and returns a simple
     * Value result. For testing individual formulas.
     *
     * @param root the per-formula MTBDD root
     * @param order the variable order
     * @param ctx the evaluation context
     * @return TRUE if matched, FALSE if not matched, the original ErrorValue if
     * errored
     */
    static Value evaluateSingle(MtbddNode root, VariableOrder order, EvaluationContext ctx) {
        val result = evaluate(root, order, ctx);
        if (result.firstError() != null) {
            return result.firstError();
        }
        if (!result.matched().isEmpty()) {
            return Value.TRUE;
        }
        return Value.FALSE;
    }

}
