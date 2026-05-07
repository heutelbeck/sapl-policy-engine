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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.index.IndexFactory;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CoverageVoter;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.compiler.combining.CombiningUtils.classifyPoliciesByEvaluationStrategy;
import static io.sapl.compiler.policyset.PolicySetUtil.ERROR_UNEXPECTED_STREAM_IN_TARGET;

/**
 * Compiles policy sets using a priority combining algorithm.
 * <p>
 * The priority algorithm combines all applicable child votes via
 * {@link PriorityBasedVoteCombiner} with a fixed
 * {@link Decision priorityDecision} that wins ties (PERMIT, DENY, or
 * SUSPEND). Unlike UNIQUE and UNANIMOUS, no early termination is
 * possible: the highest-priority decision can only be determined after
 * every applicable child has voted.
 * <p>
 * Stream evaluation walks all matching policies sequentially per
 * snapshot round, combining each child vote into the running
 * accumulator. Returns an incomplete result when any child voter has
 * unbound dependencies in this snapshot.
 */
@UtilityClass
public class PriorityVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling, CompilationContext ctx) {
        val voter         = compileVoter(compiledPolicies, voterMetadata, priorityDecision, defaultDecision,
                errorHandling, ctx);
        val coverageVoter = compileCoverageVoter(policySet, isApplicable, compiledPolicies, voterMetadata,
                priorityDecision, defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverageVoter);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a PRIORITY policy
     * set. Walks the policies sequentially per snapshot round, combines
     * each child vote via {@link PriorityBasedVoteCombiner}, and assembles
     * a {@link Coverage.PolicySetCoverage} from the per-policy results.
     * No early termination: every applicable child contributes.
     */
    private static CoverageVoter compileCoverageVoter(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val targetLocation = policySet.target() != null ? policySet.target().location() : null;
        return new PriorityPolicySetCoverageVoter(isApplicable, targetLocation, compiledPolicies, voterMetadata,
                priorityDecision, defaultDecision, errorHandling);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a PRIORITY PDP.
     * Mirrors {@link #compileCoverageVoter} but at PDP level: no target
     * gate, output wrapped in {@link Coverage.PdpCoverage}.
     */
    public static CoverageVoter compilePdpCoverageVoter(List<? extends CompiledDocument> compiledDocuments,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        return new PriorityPdpCoverageVoter(compiledDocuments, voterMetadata, priorityDecision, defaultDecision,
                errorHandling);
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            Decision priorityDecision, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            CompilationContext ctx) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = PriorityBasedVoteCombiner.combineMultipleVotes(classified.foldableVotes(),
                priorityDecision, voterMetadata);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        val index = IndexFactory.createIndex(classified.allIndexedPolicies(), ctx);

        if (classified.streamPolicies().isEmpty()) {
            return new PurePriorityVoter(accumulatorVote, index, priorityDecision, defaultDecision, errorHandling,
                    voterMetadata);
        }
        return new StreamPriorityVoter(accumulatorVote, index, priorityDecision, defaultDecision, errorHandling,
                voterMetadata);
    }

    record PurePriorityVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, index, priorityDecision, voterMetadata, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, Decision priorityDecision,
            VoterMetadata voterMetadata, EvaluationContext ctx) {
        val result = index.match(ctx);
        var vote   = accumulatorVote;
        for (val errorVote : result.errorVotes()) {
            vote = PriorityBasedVoteCombiner.combineVotes(vote, errorVote, priorityDecision, voterMetadata);
        }
        for (val document : result.matchingDocuments()) {
            val  voter = document.voter();
            Vote newVote;
            if (voter instanceof Vote constantVote) {
                newVote = constantVote;
            } else {
                newVote = ((PureVoter) voter).vote(ctx);
            }
            vote = PriorityBasedVoteCombiner.combineVotes(vote, newVote, priorityDecision, voterMetadata);
        }
        return vote;
    }

    /**
     * Stream voter for PRIORITY evaluation. Walks all matching policies
     * sequentially per snapshot round, combines via
     * {@link PriorityBasedVoteCombiner}. No early termination. Every
     * applicable child must vote so the highest priority can be
     * determined. Returns an incomplete result when any child voter has
     * unbound dependencies in this snapshot.
     */
    record StreamPriorityVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps         = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(8);
            val result       = index.match(ctx);
            var combinedVote = accumulatorVote;
            for (val errorVote : result.errorVotes()) {
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, errorVote, priorityDecision,
                        voterMetadata);
            }
            for (val document : result.matchingDocuments()) {
                val sub = document.voter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (sub.vote() == null) {
                    return new VoteResult(null, deps);
                }
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, sub.vote(), priorityDecision,
                        voterMetadata);
            }
            return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
        }
    }

    /**
     * Snapshot-driven coverage voter for PRIORITY evaluation. Mirrors
     * {@link StreamPriorityVoter} but emits {@link VoteResultWithCoverage}:
     * walks policies sequentially per snapshot round, evaluates each via
     * {@code coverageVoter().evaluate(ctx)}, combines votes via
     * {@link PriorityBasedVoteCombiner}, and assembles a
     * {@link Coverage.PolicySetCoverage} from the per-policy results.
     * No early termination.
     * <p>
     * The target / {@code isApplicable} gate is evaluated inline. Same
     * dispatch as the production voter.
     */
    record PriorityPolicySetCoverageVoter(
            CompiledExpression isApplicable,
            @Nullable SourceLocation targetLocation,
            List<? extends CompiledDocument> policies,
            VoterMetadata voterMetadata,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling) implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            Value targetMatch;
            if (isApplicable instanceof Value v) {
                targetMatch = v;
            } else if (isApplicable instanceof PureOperator p) {
                targetMatch = p.evaluate(ctx);
            } else {
                val coverage = new Coverage.PolicySetCoverage(voterMetadata, Coverage.NO_TARGET_HIT, List.of());
                val vote     = Vote.error(Value.error(ERROR_UNEXPECTED_STREAM_IN_TARGET), voterMetadata);
                return new VoteResultWithCoverage(new VoteResult(vote, Map.of()), coverage);
            }
            val targetHit = targetLocation == null ? Coverage.BLANK_TARGET_HIT
                    : new Coverage.TargetResult(targetMatch, targetLocation);
            if (targetMatch instanceof ErrorValue err) {
                val coverage = new Coverage.PolicySetCoverage(voterMetadata, targetHit, List.of());
                return new VoteResultWithCoverage(new VoteResult(Vote.error(err, voterMetadata), Map.of()), coverage);
            }
            if (Value.FALSE.equals(targetMatch)) {
                val coverage = new Coverage.PolicySetCoverage(voterMetadata, targetHit, List.of());
                return new VoteResultWithCoverage(new VoteResult(Vote.abstain(voterMetadata), Map.of()), coverage);
            }

            val deps              = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(policies.size());
            val perPolicyCoverage = new ArrayList<Coverage.DocumentCoverage>(policies.size());
            var combinedVote      = Vote.abstain(voterMetadata);
            for (val policy : policies) {
                val sub = policy.coverageVoter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.voteResult().dependencies());
                if (sub.voteResult().vote() == null) {
                    val partial = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                perPolicyCoverage.add(sub.coverage());
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(),
                        priorityDecision, voterMetadata);
            }
            val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val coverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
            return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
        }
    }

    /**
     * Snapshot-driven PDP-level coverage voter for PRIORITY combining.
     * Walks all compiled documents per snapshot round, calls each one's
     * {@link CompiledDocument#coverageVoter()}, combines votes via
     * {@link PriorityBasedVoteCombiner}, and assembles a
     * {@link Coverage.PdpCoverage} from the per-document results. No
     * target gate (PDPs apply universally). No early termination: every
     * applicable child must vote to determine the highest priority.
     */
    record PriorityPdpCoverageVoter(
            List<? extends CompiledDocument> documents,
            VoterMetadata voterMetadata,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling) implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val deps                = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(documents.size());
            val perDocumentCoverage = new ArrayList<Coverage.DocumentCoverage>(documents.size());
            var combinedVote        = Vote.abstain(voterMetadata);
            for (val document : documents) {
                val sub = document.coverageVoter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.voteResult().dependencies());
                if (sub.voteResult().vote() == null) {
                    val partial = new Coverage.PdpCoverage(voterMetadata, perDocumentCoverage);
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                perDocumentCoverage.add(sub.coverage());
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(),
                        priorityDecision, voterMetadata);
            }
            val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val coverage  = new Coverage.PdpCoverage(voterMetadata, perDocumentCoverage);
            return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
        }
    }
}
