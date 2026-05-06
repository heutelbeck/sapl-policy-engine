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
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.compiler.combining.CombiningUtils.classifyPoliciesByEvaluationStrategy;
import static io.sapl.compiler.policyset.PolicySetUtil.ERROR_UNEXPECTED_STREAM_IN_TARGET;

/**
 * Compiles policy sets using the unanimous combining algorithm.
 * <p>
 * The unanimous algorithm requires all applicable policies to agree:
 * <ul>
 * <li><b>Zero applicable:</b> Returns NOT_APPLICABLE (or default decision)</li>
 * <li><b>All agree:</b> Returns merged decision with combined constraints</li>
 * <li><b>Disagreement:</b> Returns INDETERMINATE(PERMIT_OR_DENY)</li>
 * </ul>
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>Normal mode:</b> Agreement on effect (PERMIT/DENY), constraints
 * merged</li>
 * <li><b>Strict mode:</b> Exact equality required (decision + all
 * constraints)</li>
 * </ul>
 * <p>
 * Stream evaluation walks all matching policies sequentially per
 * snapshot round, combining each child vote via
 * {@link UnanimousVoteCombiner}. Short-circuits when
 * {@code UnanimousVoteCombiner.isTerminal} returns true (a state from
 * which further combinations cannot change the outcome).
 */
@UtilityClass
public class UnanimousVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            boolean strictMode, CompilationContext ctx) {
        val voter         = compileVoter(compiledPolicies, voterMetadata, defaultDecision, errorHandling, strictMode,
                ctx);
        val coverageVoter = compileCoverageVoter(policySet, isApplicable, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling, strictMode);
        return new VoterAndCoverage(voter, coverageVoter);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a UNANIMOUS
     * policy set. Walks the policies sequentially per snapshot round,
     * combines each child vote via {@link UnanimousVoteCombiner}, and
     * assembles a {@link Coverage.PolicySetCoverage} from the per-policy
     * results. Short-circuits when {@code isTerminal} returns true.
     */
    private static CoverageVoter compileCoverageVoter(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean strictMode) {
        val targetLocation = policySet.target() != null ? policySet.target().location() : null;
        return new UnanimousPolicySetCoverageVoter(isApplicable, targetLocation, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling, strictMode);
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean strictMode, CompilationContext ctx) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = UnanimousVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata,
                strictMode);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        val index = IndexFactory.createIndex(classified.allIndexedPolicies(), ctx);

        if (classified.streamPolicies().isEmpty()) {
            return new PureUnanimousVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                    strictMode);
        }
        return new StreamUnanimousVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                strictMode);
    }

    record PureUnanimousVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean strictMode) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, index, voterMetadata, strictMode, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, VoterMetadata voterMetadata,
            boolean strictMode, EvaluationContext ctx) {
        var holder = new Vote[] { accumulatorVote };
        index.matchWhile(ctx, stepResult -> {
            for (val errorVote : stepResult.errorVotes()) {
                holder[0] = UnanimousVoteCombiner.combineVotes(holder[0], errorVote, voterMetadata, strictMode);
            }
            for (val document : stepResult.matchingDocuments()) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                holder[0] = UnanimousVoteCombiner.combineVotes(holder[0], newVote, voterMetadata, strictMode);
            }
            return !UnanimousVoteCombiner.isTerminal(holder[0], strictMode);
        });
        return holder[0];
    }

    /**
     * Stream voter for UNANIMOUS evaluation. Walks all matching policies
     * sequentially per snapshot round, combines via
     * {@link UnanimousVoteCombiner}, and short-circuits when the current
     * combined vote is terminal under the given strictness. Returns an
     * incomplete result when any child voter has unbound dependencies in
     * this snapshot.
     */
    record StreamUnanimousVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean strictMode) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps         = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(8);
            val result       = index.match(ctx);
            var combinedVote = accumulatorVote;
            for (val errorVote : result.errorVotes()) {
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, errorVote, voterMetadata, strictMode);
            }
            for (val document : result.matchingDocuments()) {
                val sub = document.voter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (sub.vote() == null) {
                    return new VoteResult(null, deps);
                }
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, sub.vote(), voterMetadata, strictMode);
                if (UnanimousVoteCombiner.isTerminal(combinedVote, strictMode)) {
                    return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
                }
            }
            return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
        }
    }

    /**
     * Snapshot-driven coverage voter for UNANIMOUS evaluation. Mirrors
     * {@link StreamUnanimousVoter} but emits {@link VoteResultWithCoverage}:
     * walks policies sequentially per snapshot round, evaluates each via
     * {@code coverageVoter().evaluate(ctx)}, combines votes via
     * {@link UnanimousVoteCombiner}, and assembles a
     * {@link Coverage.PolicySetCoverage} from the per-policy results.
     * Short-circuits when {@code isTerminal} returns true under the given
     * strictness.
     * <p>
     * The target / {@code isApplicable} gate is evaluated inline. Same
     * dispatch as the production voter.
     */
    record UnanimousPolicySetCoverageVoter(
            CompiledExpression isApplicable,
            @Nullable SourceLocation targetLocation,
            List<? extends CompiledDocument> policies,
            VoterMetadata voterMetadata,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            boolean strictMode) implements CoverageVoter {

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
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(), voterMetadata,
                        strictMode);
                if (UnanimousVoteCombiner.isTerminal(combinedVote, strictMode)) {
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
}
