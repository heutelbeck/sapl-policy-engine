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
package io.sapl.compiler.index.smtdd;

import java.util.BitSet;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.EqualityBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Evaluates an SMTDD against a request context.
 * <p>
 * Traversal is top-down: first through EqualityBranch nodes (one
 * HashMap lookup per branch), then through BinaryDecision nodes
 * (one predicate evaluation per node). Errors are accumulated
 * from the precomputed affected-formulas sets.
 */
@UtilityClass
class SmtddEvaluator {

    /**
     * Result of evaluating an SMTDD.
     *
     * @param matched formula indices that are satisfied
     * @param errored formula indices that encountered errors
     * @param firstError the first ErrorValue encountered, or null
     */
    record EvaluationResult(BitSet matched, BitSet errored, ErrorValue firstError) {}

    /**
     * Evaluates the SMTDD against the given context.
     *
     * @param root the SMTDD root node
     * @param binaryOrder the variable order for binary decision predicates
     * @param ctx the evaluation context
     * @return matched and errored formula indices
     */
    static EvaluationResult evaluate(SmtddNode root, BinaryVariableOrder binaryOrder, EvaluationContext ctx) {
        val        accumulatedErrors = new BitSet();
        ErrorValue firstError        = null;
        var        node              = root;

        // Traverse equality branches and binary decisions
        while (!(node instanceof Terminal(BitSet matchedByTerminal))) {
            switch (node) {
            case EqualityBranch(var operand, var branches, var defaultChild, var errorChild, var affected) -> {
                val result = operand.evaluate(ctx);
                if (result instanceof ErrorValue error) {
                    if (firstError == null) {
                        firstError = error;
                    }
                    accumulatedErrors.or(affected);
                    node = errorChild;
                } else {
                    val branch = branches.get(result);
                    node = branch != null ? branch : defaultChild;
                }
            }
            case BinaryDecision(int level, var trueChild, var falseChild, var errorChild)                  -> {
                val predicate = binaryOrder.predicateAt(level);
                val result    = predicate.operator().evaluate(ctx);
                if (result instanceof ErrorValue error) {
                    if (firstError == null) {
                        firstError = error;
                    }
                    accumulatedErrors.or(binaryOrder.erroredFormulas(level));
                    node = errorChild;
                } else if (result instanceof BooleanValue(var b) && b) {
                    node = trueChild;
                } else {
                    node = falseChild;
                }
            }
            case Terminal ignored                                                                          ->
                { /* handled by while condition */ }
            }
        }

        val matched = (BitSet) matchedByTerminal.clone();
        matched.andNot(accumulatedErrors);
        return new EvaluationResult(matched, accumulatedErrors, firstError);
    }

}
