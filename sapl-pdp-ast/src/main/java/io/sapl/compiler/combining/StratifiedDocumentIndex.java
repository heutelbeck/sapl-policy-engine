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
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.pdp.PureVoter;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.policy.CompiledPolicy;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-compiled index organizing policies into evaluation strata for priority
 * voting algorithms.
 * <p>
 * Stratification enables lazy evaluation optimization: evaluate cheaper strata
 * first, potentially short-circuiting expensive streaming evaluation.
 * <p>
 * <b>Strata (ordered by evaluation cost):</b>
 * <ol>
 * <li><b>Constant:</b> Pre-folded at index creation time into a single
 * CombinedVote. Policies with constant FALSE isApplicable are eliminated.</li>
 * <li><b>Pure:</b> Policies requiring only subscription data (no streaming
 * attributes). Split by outcome and constraint presence.</li>
 * <li><b>Stream:</b> Policies requiring streaming attribute evaluation. Split
 * by outcome and constraint presence.</li>
 * </ol>
 * <p>
 * Within each stratum, policies are further classified into 6 buckets by
 * outcome (PERMIT, DENY, PERMIT_OR_DENY) and constraint presence
 * (with/without).
 * This enables fine-grained skip optimization based on accumulated decision.
 * <p>
 * <b>Elimination:</b> Policies with constant FALSE isApplicable are eliminated
 * entirely - they can never match.
 *
 * @param accumulatorVote pre-folded constant stratum result
 * @param purePermitWithConstraints pure PERMIT policies with constraints
 * @param purePermitWithoutConstraints pure PERMIT policies without constraints
 * @param pureDenyWithConstraints pure DENY policies with constraints
 * @param pureDenyWithoutConstraints pure DENY policies without constraints
 * @param pureMixedWithConstraints pure PERMIT_OR_DENY policies with constraints
 * @param pureMixedWithoutConstraints pure PERMIT_OR_DENY policies without
 * constraints
 * @param streamPermitWithConstraints stream PERMIT policies with constraints
 * @param streamPermitWithoutConstraints stream PERMIT policies without
 * constraints
 * @param streamDenyWithConstraints stream DENY policies with constraints
 * @param streamDenyWithoutConstraints stream DENY policies without constraints
 * @param streamMixedWithConstraints stream PERMIT_OR_DENY policies with
 * constraints
 * @param streamMixedWithoutConstraints stream PERMIT_OR_DENY policies without
 * constraints
 * @param runtimePolicyCount total number of policies requiring runtime
 * evaluation
 */
public record StratifiedDocumentIndex(
        Vote accumulatorVote,
        List<CompiledPolicy> purePermitWithConstraints,
        List<CompiledPolicy> purePermitWithoutConstraints,
        List<CompiledPolicy> pureDenyWithConstraints,
        List<CompiledPolicy> pureDenyWithoutConstraints,
        List<CompiledPolicy> pureMixedWithConstraints,
        List<CompiledPolicy> pureMixedWithoutConstraints,
        List<CompiledPolicy> streamPermitWithConstraints,
        List<CompiledPolicy> streamPermitWithoutConstraints,
        List<CompiledPolicy> streamDenyWithConstraints,
        List<CompiledPolicy> streamDenyWithoutConstraints,
        List<CompiledPolicy> streamMixedWithConstraints,
        List<CompiledPolicy> streamMixedWithoutConstraints,
        int runtimePolicyCount) {

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
     * Within each stratum, policies are further classified by outcome
     * (PERMIT, DENY, PERMIT_OR_DENY) and constraint presence.
     *
     * @param policies the compiled policies to index
     * @param priorityDecision the priority vote type (PERMIT or DENY)
     * @param voterMetadata the policy set voterMetadata for the folded vote
     * @return a new stratified index
     */
    public static StratifiedDocumentIndex of(List<CompiledPolicy> policies, Decision priorityDecision,
            VoterMetadata voterMetadata) {

        var foldableVotes                  = new ArrayList<Vote>();
        var purePermitWithConstraints      = new ArrayList<CompiledPolicy>();
        var purePermitWithoutConstraints   = new ArrayList<CompiledPolicy>();
        var pureDenyWithConstraints        = new ArrayList<CompiledPolicy>();
        var pureDenyWithoutConstraints     = new ArrayList<CompiledPolicy>();
        var pureMixedWithConstraints       = new ArrayList<CompiledPolicy>();
        var pureMixedWithoutConstraints    = new ArrayList<CompiledPolicy>();
        var streamPermitWithConstraints    = new ArrayList<CompiledPolicy>();
        var streamPermitWithoutConstraints = new ArrayList<CompiledPolicy>();
        var streamDenyWithConstraints      = new ArrayList<CompiledPolicy>();
        var streamDenyWithoutConstraints   = new ArrayList<CompiledPolicy>();
        var streamMixedWithConstraints     = new ArrayList<CompiledPolicy>();
        var streamMixedWithoutConstraints  = new ArrayList<CompiledPolicy>();

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
                    foldableVotes.add(decision);
                } else if (voter instanceof PureVoter) {
                    // Constant TRUE + pure - pure stratum
                    addToPureStratum(policy, purePermitWithConstraints, purePermitWithoutConstraints,
                            pureDenyWithConstraints, pureDenyWithoutConstraints, pureMixedWithConstraints,
                            pureMixedWithoutConstraints);
                } else {
                    // Constant TRUE + stream - stream stratum
                    addToStreamStratum(policy, streamPermitWithConstraints, streamPermitWithoutConstraints,
                            streamDenyWithConstraints, streamDenyWithoutConstraints, streamMixedWithConstraints,
                            streamMixedWithoutConstraints);
                }
            } else if (voter instanceof StreamVoter) {
                // Pure/stream isApplicable + stream voter - stream stratum
                addToStreamStratum(policy, streamPermitWithConstraints, streamPermitWithoutConstraints,
                        streamDenyWithConstraints, streamDenyWithoutConstraints, streamMixedWithConstraints,
                        streamMixedWithoutConstraints);
            } else {
                // Pure isApplicable + constant/pure voter - pure stratum
                addToPureStratum(policy, purePermitWithConstraints, purePermitWithoutConstraints,
                        pureDenyWithConstraints, pureDenyWithoutConstraints, pureMixedWithConstraints,
                        pureMixedWithoutConstraints);
            }
        }

        // Fold constant stratum
        val foldedConstant     = PriorityBasedVoteCombiner.combineMultipleVotes(foldableVotes, priorityDecision,
                voterMetadata);
        val runtimePolicyCount = purePermitWithConstraints.size() + purePermitWithoutConstraints.size()
                + pureDenyWithConstraints.size() + pureDenyWithoutConstraints.size() + pureMixedWithConstraints.size()
                + pureMixedWithoutConstraints.size() + streamPermitWithConstraints.size()
                + streamPermitWithoutConstraints.size() + streamDenyWithConstraints.size()
                + streamDenyWithoutConstraints.size() + streamMixedWithConstraints.size()
                + streamMixedWithoutConstraints.size();

        return new StratifiedDocumentIndex(foldedConstant, purePermitWithConstraints, purePermitWithoutConstraints,
                pureDenyWithConstraints, pureDenyWithoutConstraints, pureMixedWithConstraints,
                pureMixedWithoutConstraints, streamPermitWithConstraints, streamPermitWithoutConstraints,
                streamDenyWithConstraints, streamDenyWithoutConstraints, streamMixedWithConstraints,
                streamMixedWithoutConstraints, runtimePolicyCount);
    }

    private static void addToPureStratum(CompiledPolicy policy, List<CompiledPolicy> permitWith,
            List<CompiledPolicy> permitWithout, List<CompiledPolicy> denyWith, List<CompiledPolicy> denyWithout,
            List<CompiledPolicy> mixedWith, List<CompiledPolicy> mixedWithout) {
        addToBucket(policy, policy.outcome(), policy.hasConstraints(), permitWith, permitWithout, denyWith, denyWithout,
                mixedWith, mixedWithout);
    }

    private static void addToStreamStratum(CompiledPolicy policy, List<CompiledPolicy> permitWith,
            List<CompiledPolicy> permitWithout, List<CompiledPolicy> denyWith, List<CompiledPolicy> denyWithout,
            List<CompiledPolicy> mixedWith, List<CompiledPolicy> mixedWithout) {
        addToBucket(policy, policy.outcome(), policy.hasConstraints(), permitWith, permitWithout, denyWith, denyWithout,
                mixedWith, mixedWithout);
    }

    private static void addToBucket(CompiledPolicy policy, Outcome outcome, boolean hasConstraints,
            List<CompiledPolicy> permitWith, List<CompiledPolicy> permitWithout, List<CompiledPolicy> denyWith,
            List<CompiledPolicy> denyWithout, List<CompiledPolicy> mixedWith, List<CompiledPolicy> mixedWithout) {
        switch (outcome) {
        case PERMIT         -> {
            if (hasConstraints) {
                permitWith.add(policy);
            } else {
                permitWithout.add(policy);
            }
        }
        case DENY           -> {
            if (hasConstraints) {
                denyWith.add(policy);
            } else {
                denyWithout.add(policy);
            }
        }
        case PERMIT_OR_DENY -> {
            if (hasConstraints) {
                mixedWith.add(policy);
            } else {
                mixedWithout.add(policy);
            }
        }
        }
    }

    /**
     * @return true if there are any policies requiring streaming evaluation
     */
    public boolean hasStreamingPolicies() {
        return !streamPermitWithConstraints.isEmpty() || !streamPermitWithoutConstraints.isEmpty()
                || !streamDenyWithConstraints.isEmpty() || !streamDenyWithoutConstraints.isEmpty()
                || !streamMixedWithConstraints.isEmpty() || !streamMixedWithoutConstraints.isEmpty();
    }

    /**
     * @return true if there are any policies requiring pure evaluation
     */
    public boolean hasPurePolicies() {
        return !purePermitWithConstraints.isEmpty() || !purePermitWithoutConstraints.isEmpty()
                || !pureDenyWithConstraints.isEmpty() || !pureDenyWithoutConstraints.isEmpty()
                || !pureMixedWithConstraints.isEmpty() || !pureMixedWithoutConstraints.isEmpty();
    }

    /**
     * @return true if there are policies with constraints in pure or stream strata
     */
    public boolean hasRuntimeConstraints() {
        return !purePermitWithConstraints.isEmpty() || !pureDenyWithConstraints.isEmpty()
                || !pureMixedWithConstraints.isEmpty() || !streamPermitWithConstraints.isEmpty()
                || !streamDenyWithConstraints.isEmpty() || !streamMixedWithConstraints.isEmpty();
    }

    /**
     * @return true if no runtime evaluation is needed (all constant or eliminated)
     */
    public boolean isFullyConstant() {
        return runtimePolicyCount() == 0;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return pure stratum policies with priority outcome and constraints
     */
    public List<CompiledPolicy> purePriorityWithConstraints(Decision priority) {
        return priority == Decision.PERMIT ? purePermitWithConstraints : pureDenyWithConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return pure stratum policies with priority outcome without constraints
     */
    public List<CompiledPolicy> purePriorityWithoutConstraints(Decision priority) {
        return priority == Decision.PERMIT ? purePermitWithoutConstraints : pureDenyWithoutConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return pure stratum policies with non-priority outcome and constraints
     */
    public List<CompiledPolicy> pureNonPriorityWithConstraints(Decision priority) {
        return priority == Decision.PERMIT ? pureDenyWithConstraints : purePermitWithConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return pure stratum policies with non-priority outcome without constraints
     */
    public List<CompiledPolicy> pureNonPriorityWithoutConstraints(Decision priority) {
        return priority == Decision.PERMIT ? pureDenyWithoutConstraints : purePermitWithoutConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return stream stratum policies with priority outcome and constraints
     */
    public List<CompiledPolicy> streamPriorityWithConstraints(Decision priority) {
        return priority == Decision.PERMIT ? streamPermitWithConstraints : streamDenyWithConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return stream stratum policies with priority outcome without constraints
     */
    public List<CompiledPolicy> streamPriorityWithoutConstraints(Decision priority) {
        return priority == Decision.PERMIT ? streamPermitWithoutConstraints : streamDenyWithoutConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return stream stratum policies with non-priority outcome and constraints
     */
    public List<CompiledPolicy> streamNonPriorityWithConstraints(Decision priority) {
        return priority == Decision.PERMIT ? streamDenyWithConstraints : streamPermitWithConstraints;
    }

    /**
     * @param priority the priority decision (PERMIT or DENY)
     * @return stream stratum policies with non-priority outcome without constraints
     */
    public List<CompiledPolicy> streamNonPriorityWithoutConstraints(Decision priority) {
        return priority == Decision.PERMIT ? streamDenyWithoutConstraints : streamPermitWithoutConstraints;
    }
}
