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
package io.sapl.compiler.combining;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.PureVoter;
import io.sapl.compiler.pdp.VoterMetadata;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.policy.CompiledPolicy;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pre-compiled index organizing policies into evaluation strata for priority
 * voting algorithms.
 * <p>
 * Stratification enables lazy evaluation optimization: evaluate cheaper strata
 * first, potentially short-circuiting expensive streaming evaluation.
 * <p>
 * <b>Strata (ordered by evaluation cost):</b>
 * <ol>
 * <li><b>Constant:</b> Pre-folded at index creation time into a single optional
 * CombinedVote. Policies with constant FALSE isApplicable are
 * eliminated.</li>
 * <li><b>Pure:</b> Policies requiring only subscription data (no streaming
 * attributes). Split by constraint presence - "with" evaluated first for skip
 * optimization.</li>
 * <li><b>Stream:</b> Policies requiring streaming attribute evaluation. Split
 * by constraint presence - "with" evaluated first for skip optimization.</li>
 * </ol>
 * <p>
 * <b>Elimination:</b> Policies with constant FALSE isApplicable are eliminated
 * entirely - they can never match.
 *
 * @param foldedConstant pre-folded constant stratum result with full
 * voterMetadata
 * @param pureWithConstraints pure evaluation with constraints (evaluated first)
 * @param pureWithoutConstraints pure evaluation without constraints
 * @param streamWithConstraints streaming evaluation with constraints (evaluated
 * first)
 * @param streamWithoutConstraints streaming evaluation without constraints
 */
public record StratifiedDocumentIndex(
        Optional<Vote> foldedConstant,
        List<CompiledPolicy> pureWithConstraints,
        List<CompiledPolicy> pureWithoutConstraints,
        List<CompiledPolicy> streamWithConstraints,
        List<CompiledPolicy> streamWithoutConstraints) {

    /**
     * Creates a stratified index from a list of compiled policies.
     * <p>
     * Classification rules:
     * <ul>
     * <li>isApplicable constant FALSE: eliminated</li>
     * <li>isApplicable constant (TRUE/ERROR) + voter constant: fold</li>
     * <li>isApplicable constant TRUE + voter pure: pure stratum</li>
     * <li>isApplicable pure + voter constant/pure: pure stratum</li>
     * <li>voter stream OR isApplicable stream: stream stratum</li>
     * </ul>
     * <p>
     * Note: Default vote and error handling are NOT applied here. They are
     * final transformations applied by the combining algorithm after all strata
     * are combined at runtime.
     *
     * @param policies the compiled policies to index
     * @param priority the priority vote type (PERMIT or DENY)
     * @param voterMetadata the policy set voterMetadata for the folded vote
     * @return a new stratified index
     */
    public static StratifiedDocumentIndex of(List<CompiledPolicy> policies, Decision priority,
            VoterMetadata voterMetadata) {

        var foldableDecisions        = new ArrayList<Vote>();
        var pureWithConstraints      = new ArrayList<CompiledPolicy>();
        var pureWithoutConstraints   = new ArrayList<CompiledPolicy>();
        var streamWithConstraints    = new ArrayList<CompiledPolicy>();
        var streamWithoutConstraints = new ArrayList<CompiledPolicy>();

        for (var policy : policies) {
            val isApplicable = policy.isApplicable();
            val voter        = policy.voter();

            // Check isApplicable type
            if (isApplicable instanceof Value constantApplicable) {
                // Constant isApplicable
                if (constantApplicable instanceof BooleanValue(var b) && !b) {
                    // Constant FALSE - eliminate entirely
                    continue;
                }
                // Constant TRUE or ERROR - check voter
                if (voter instanceof Vote decision) {
                    // Constant + constant - fold
                    foldableDecisions.add(decision);
                } else if (voter instanceof PureVoter) {
                    // Constant TRUE + pure - pure stratum
                    addToStratum(policy, pureWithConstraints, pureWithoutConstraints);
                } else {
                    // Constant TRUE + stream - stream stratum
                    addToStratum(policy, streamWithConstraints, streamWithoutConstraints);
                }
            } else if (voter instanceof StreamVoter) {
                // Pure/stream isApplicable + stream voter - stream stratum
                addToStratum(policy, streamWithConstraints, streamWithoutConstraints);
            } else {
                // Pure isApplicable + constant/pure voter - pure stratum
                addToStratum(policy, pureWithConstraints, pureWithoutConstraints);
            }
        }

        // Fold constant stratum
        val foldedConstant = DecisionCombiner.fold(foldableDecisions, priority, voterMetadata);

        return new StratifiedDocumentIndex(foldedConstant, List.copyOf(pureWithConstraints),
                List.copyOf(pureWithoutConstraints), List.copyOf(streamWithConstraints),
                List.copyOf(streamWithoutConstraints));
    }

    private static void addToStratum(CompiledPolicy policy, List<CompiledPolicy> withConstraints,
            List<CompiledPolicy> withoutConstraints) {
        if (policy.hasConstraints()) {
            withConstraints.add(policy);
        } else {
            withoutConstraints.add(policy);
        }
    }

    /**
     * @return true if the constant stratum yielded a decisive result
     */
    public boolean hasConstantDecisiveResult() {
        return foldedConstant.map(d -> {
            val decision = d.authorizationDecision().decision();
            return decision == Decision.PERMIT || decision == Decision.DENY;
        }).orElse(false);
    }

    /**
     * @param priority the priority vote type (PERMIT or DENY)
     * @return true if constant stratum has a vote matching the priority
     */
    public boolean hasConstantPriorityDecision(Decision priority) {
        return foldedConstant.map(d -> d.authorizationDecision().decision() == priority).orElse(false);
    }

    /**
     * @return true if there are any policies requiring streaming evaluation
     */
    public boolean hasStreamingPolicies() {
        return !streamWithConstraints.isEmpty() || !streamWithoutConstraints.isEmpty();
    }

    /**
     * @return true if there are any policies requiring pure evaluation
     */
    public boolean hasPurePolicies() {
        return !pureWithConstraints.isEmpty() || !pureWithoutConstraints.isEmpty();
    }

    /**
     * @return true if there are policies with constraints in pure or stream strata
     */
    public boolean hasRuntimeConstraints() {
        return !pureWithConstraints.isEmpty() || !streamWithConstraints.isEmpty();
    }

    /**
     * @return total number of runtime policies (pure + stream)
     */
    public int runtimePolicyCount() {
        return pureWithConstraints.size() + pureWithoutConstraints.size() + streamWithConstraints.size()
                + streamWithoutConstraints.size();
    }

    /**
     * @return true if no runtime evaluation is needed (all constant or eliminated)
     */
    public boolean isFullyConstant() {
        return runtimePolicyCount() == 0;
    }
}
