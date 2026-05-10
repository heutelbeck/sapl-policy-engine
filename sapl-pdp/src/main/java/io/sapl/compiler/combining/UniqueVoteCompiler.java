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
 * Compiles policy sets using the unique (only-one-applicable) combining
 * algorithm.
 * <p>
 * The unique algorithm requires exactly one policy to be applicable:
 * <ul>
 * <li><b>Zero applicable:</b> Returns NOT_APPLICABLE (or default decision)</li>
 * <li><b>One applicable:</b> Returns that policy's decision</li>
 * <li><b>Multiple applicable:</b> Returns INDETERMINATE (configuration
 * error)</li>
 * </ul>
 * <p>
 * Stream evaluation walks all matching policies sequentially per
 * snapshot round, combining each child vote via
 * {@link UniqueVoteCombiner}. Once the combined vote becomes
 * INDETERMINATE (multiple applicables) the walk short-circuits.
 */
@UtilityClass
public class UniqueVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            CompilationContext ctx) {
        val voter         = compileVoter(compiledPolicies, voterMetadata, defaultDecision, errorHandling, ctx);
        val coverageVoter = compileCoverageVoter(policySet, isApplicable, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverageVoter);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a UNIQUE policy
     * set. Walks the policies sequentially per snapshot round, combines
     * each child vote via {@link UniqueVoteCombiner}, and assembles a
     * {@link Coverage.PolicySetCoverage} from the per-policy results.
     * Short-circuits at INDETERMINATE.
     */
    private static CoverageVoter compileCoverageVoter(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val targetLocation = policySet.target() != null ? policySet.target().location() : null;
        return new UniquePolicySetCoverageVoter(isApplicable, targetLocation, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a UNIQUE PDP.
     * Mirrors {@link #compileCoverageVoter} but at PDP level: no target
     * gate, output wrapped in {@link Coverage.PdpCoverage}.
     */
    public static CoverageVoter compilePdpCoverageVoter(List<? extends CompiledDocument> compiledDocuments,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        return new UniquePdpCoverageVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling);
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, CompilationContext ctx) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = UniqueVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        val index = IndexFactory.createIndex(classified.allIndexedPolicies(), ctx);

        if (classified.streamPolicies().isEmpty()) {
            return new PureUniqueVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata);
        }
        return new StreamUniqueVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata);
    }

    record PureUniqueVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, index, voterMetadata, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, VoterMetadata voterMetadata,
            EvaluationContext ctx) {
        var holder = new Vote[] { accumulatorVote };
        index.matchWhile(ctx, stepResult -> {
            for (val errorVote : stepResult.errorVotes()) {
                holder[0] = UniqueVoteCombiner.combineVotes(holder[0], errorVote, voterMetadata);
            }
            for (val document : stepResult.matchingDocuments()) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                holder[0] = UniqueVoteCombiner.combineVotes(holder[0], newVote, voterMetadata);
            }
            return holder[0].authorizationDecision().decision() != Decision.INDETERMINATE;
        });
        return holder[0];
    }

    /**
     * Stream voter for UNIQUE evaluation. Walks all matching policies
     * sequentially per snapshot round, combines via
     * {@link UniqueVoteCombiner}, and short-circuits at INDETERMINATE
     * (more than one applicable). Returns an incomplete result when any
     * child voter has unbound dependencies in this snapshot.
     */
    record StreamUniqueVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps         = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(8);
            val result       = index.match(ctx);
            var combinedVote = accumulatorVote;
            for (val errorVote : result.errorVotes()) {
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, errorVote, voterMetadata);
            }
            for (val document : result.matchingDocuments()) {
                val sub = document.voter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (sub.vote() == null) {
                    return new VoteResult(null, deps);
                }
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, sub.vote(), voterMetadata);
                if (combinedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                    return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
                }
            }
            return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
        }
    }

    /**
     * Snapshot-driven coverage voter for UNIQUE evaluation. Mirrors
     * {@link StreamUniqueVoter} but emits {@link VoteResultWithCoverage}:
     * walks policies sequentially per snapshot round, evaluates each via
     * {@code coverageVoter().evaluate(ctx)}, combines votes via
     * {@link UniqueVoteCombiner}, and assembles a
     * {@link Coverage.PolicySetCoverage} from the per-policy results.
     * Short-circuits at INDETERMINATE.
     * <p>
     * The target / {@code isApplicable} gate is evaluated inline. Same
     * dispatch as the production voter.
     */
    record UniquePolicySetCoverageVoter(
            CompiledExpression isApplicable,
            @Nullable SourceLocation targetLocation,
            List<? extends CompiledDocument> policies,
            VoterMetadata voterMetadata,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling) implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            final Value targetMatch;
            switch (isApplicable) {
            case Value v        -> targetMatch = v;
            case PureOperator p -> targetMatch = p.evaluate(ctx);
            default             -> {
                val coverage = new Coverage.PolicySetCoverage(voterMetadata, Coverage.NO_TARGET_HIT, List.of());
                val vote     = Vote.error(Value.error(ERROR_UNEXPECTED_STREAM_IN_TARGET), voterMetadata);
                return new VoteResultWithCoverage(new VoteResult(vote, Map.of()), coverage);
            }
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
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(), voterMetadata);
                if (combinedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                    val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
                    val coverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
                    return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
                }
            }
            val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val coverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
            return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
        }
    }

    /**
     * Snapshot-driven PDP-level coverage voter for UNIQUE combining.
     * Walks all compiled documents per snapshot round, calls each one's
     * {@link CompiledDocument#coverageVoter()}, combines votes via
     * {@link UniqueVoteCombiner}, and assembles a
     * {@link Coverage.PdpCoverage} from the per-document results.
     * Short-circuits at INDETERMINATE. No target gate.
     */
    record UniquePdpCoverageVoter(
            List<? extends CompiledDocument> documents,
            VoterMetadata voterMetadata,
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
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(), voterMetadata);
                if (combinedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                    val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
                    val coverage  = new Coverage.PdpCoverage(voterMetadata, perDocumentCoverage);
                    return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
                }
            }
            val finalVote = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val coverage  = new Coverage.PdpCoverage(voterMetadata, perDocumentCoverage);
            return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
        }
    }
}
