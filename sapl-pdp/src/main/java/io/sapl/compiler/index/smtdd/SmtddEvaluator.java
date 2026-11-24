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

import com.heutelbeck.rustregex4j.RegexError;
import com.heutelbeck.rustregex4j.SetMatches;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.EqualityBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.RegexBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
import io.sapl.compiler.util.LinearRegex;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.BitSet;
import java.util.List;

/**
 * Evaluates an SMTDD against a request context.
 * <p>
 * Traversal is top-down: first through EqualityBranch nodes (one
 * HashMap lookup per branch), then through RegexBranch nodes (one search
 * with the set of patterns per branch), then through BinaryDecision nodes
 * (one predicate evaluation per node). An equality branch and a binary
 * decision continue with one child. A regex branch continues with its
 * default child and with the child of every pattern that matches, and the
 * matched formulas of all these paths are united. Errors are accumulated
 * from the precomputed affected-formulas sets.
 */
@UtilityClass
class SmtddEvaluator {

    private static final String ERROR_REGEX_SET_SEARCH_FAILED = "Matching the regular expressions of the policy index failed: %s.";

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
        val traversal = new Traversal(binaryOrder, ctx);
        traversal.follow(root);
        traversal.matched.andNot(traversal.errored);
        return new EvaluationResult(traversal.matched, traversal.errored, traversal.firstError);
    }

    /** The state of one evaluation, shared by all paths that a regex branch opens. */
    private static final class Traversal {

        private final BinaryVariableOrder binaryOrder;
        private final EvaluationContext   ctx;
        private final BitSet              matched = new BitSet();
        private final BitSet              errored = new BitSet();
        private ErrorValue                firstError;

        Traversal(BinaryVariableOrder binaryOrder, EvaluationContext ctx) {
            this.binaryOrder = binaryOrder;
            this.ctx         = ctx;
        }

        void follow(SmtddNode root) {
            var node = root;
            while (!(node instanceof Terminal(BitSet matchedByTerminal))) {
                node = switch (node) {
                case EqualityBranch branch   -> childOf(branch);
                case RegexBranch branch      -> defaultChildAfterMatchingPatterns(branch);
                case BinaryDecision decision -> childOf(decision);
                case Terminal terminal       -> terminal;
                };
            }
            matched.or(matchedByTerminal);
        }

        private SmtddNode childOf(EqualityBranch branch) {
            val result = ctx.evaluate(branch.operand());
            if (result instanceof ErrorValue error) {
                failed(error, branch.affectedFormulas());
                return branch.errorChild();
            }
            if (!(result instanceof TextValue || result instanceof UndefinedValue)) {
                errored.or(branch.keyFormulas());
            }
            val child = branch.branches().get(result);
            return child != null ? child : branch.defaultChild();
        }

        private SmtddNode childOf(BinaryDecision decision) {
            val result = ctx.evaluate(binaryOrder.predicateAt(decision.level()).operator());
            return switch (result) {
            case ErrorValue error           -> {
                failed(error, binaryOrder.erroredFormulas(decision.level()));
                yield decision.errorChild();
            }
            case BooleanValue(var b) when b -> decision.trueChild();
            case BooleanValue ignored       -> decision.falseChild();
            default                         -> {
                // Non-boolean (type error now that the body type-check is gone):
                // route to the error branch and reconcile through Kleene naive.
                errored.or(binaryOrder.erroredFormulas(decision.level()));
                yield decision.errorChild();
            }
            };
        }

        /**
         * Follows the children of all patterns that match the operand. A value
         * that is no text matches no pattern, as for the operator {@code =~}. If
         * the operand errors or the engine cannot search the text, the formulas
         * of the branch are suspect and no child is followed.
         */
        private SmtddNode defaultChildAfterMatchingPatterns(RegexBranch branch) {
            val result = ctx.evaluate(branch.operand());
            if (result instanceof ErrorValue error) {
                failed(error, branch.affectedFormulas());
            } else if (result instanceof TextValue(var text)) {
                switch (branch.patterns().matches(text)) {
                case SetMatches(var matching) -> followMatching(branch, matching);
                case RegexError error         ->
                    failed(Value.error(ERROR_REGEX_SET_SEARCH_FAILED.formatted(LinearRegex.describe(error))),
                            branch.affectedFormulas());
                }
            }
            return branch.defaultChild();
        }

        private void followMatching(RegexBranch branch, List<Integer> matching) {
            for (val pattern : matching) {
                follow(branch.children().get(pattern));
            }
            if (branch.conjunctions().isEmpty()) {
                return;
            }
            // A formula that demands several patterns was routed to the child of one
            // of them and holds only if all of them match.
            val matchingPatterns = new BitSet();
            matching.forEach(matchingPatterns::set);
            branch.conjunctions().forEach((formula, demanded) -> {
                val missing = (BitSet) demanded.clone();
                missing.andNot(matchingPatterns);
                if (!missing.isEmpty()) {
                    matched.clear(formula);
                }
            });
        }

        private void failed(ErrorValue error, BitSet affectedFormulas) {
            if (firstError == null) {
                firstError = error;
            }
            errored.or(affectedFormulas);
        }
    }

}
