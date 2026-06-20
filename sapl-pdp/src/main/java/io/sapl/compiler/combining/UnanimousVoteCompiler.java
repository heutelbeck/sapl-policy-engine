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
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.index.IndexFactory;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.compiler.policyset.CompiledPolicySet;
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
                true, ctx);
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

    /**
     * Constructs the snapshot-driven coverage voter for a UNANIMOUS PDP.
     * Mirrors {@link #compileCoverageVoter} but at PDP level: no target
     * gate, output wrapped in {@link Coverage.PdpCoverage}.
     */
    public static CoverageVoter compilePdpCoverageVoter(List<? extends CompiledDocument> compiledDocuments,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            boolean strictMode) {
        return new UnanimousPdpCoverageVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling,
                strictMode);
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean strictMode, boolean completeOutcome,
            CompilationContext ctx) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        var accumulatorVote = UnanimousVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata,
                strictMode);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            if (completeOutcome) {
                accumulatorVote = CombiningUtils.completeSetOutcomeFromVotes(accumulatorVote,
                        classified.foldableVotes());
            }
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        val index = IndexFactory.createIndex(classified.allIndexedPolicies(), ctx);

        if (classified.streamPolicies().isEmpty()) {
            return new PureUnanimousVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                    strictMode, completeOutcome);
        }
        return new StreamUnanimousVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                strictMode, completeOutcome);
    }

    record PureUnanimousVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean strictMode,
            boolean completeOutcome) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            // At the set level (completeOutcome) the could-have-been of a
            // short-circuited INDETERMINATE must include the un-folded matched
            // policies' potential, which matchKleeneWhile cannot expose. Use a full
            // match and complete the outcome. At the PDP level the outcome is
            // unused, so keep the matchKleeneWhile hard short-circuit.
            val vote = completeOutcome
                    ? combineMatchedAndComplete(accumulatorVote, index, voterMetadata, strictMode, ctx)
                    : combinePureVoters(accumulatorVote, index, voterMetadata, strictMode, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, VoterMetadata voterMetadata,
            boolean strictMode, EvaluationContext ctx) {
        var holder = new Vote[] { accumulatorVote };
        index.matchKleeneWhile(ctx, stepResult -> {
            for (val errorMatch : stepResult.errorMatches()) {
                // A pure-path candidate has no streaming section, so an applicability
                // error is terminal and the policy is INDETERMINATE.
                holder[0] = UnanimousVoteCombiner.combineVotes(holder[0],
                        Vote.error(errorMatch.error(), errorMatch.document().metadata()), voterMetadata, strictMode);
            }
            for (val document : stepResult.trueMatches()) {
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

    private static Vote combineMatchedAndComplete(Vote accumulatorVote, PolicyIndex index, VoterMetadata voterMetadata,
            boolean strictMode, EvaluationContext ctx) {
        val result   = index.matchKleene(ctx);
        val matching = result.trueMatches();
        var vote     = accumulatorVote;
        for (val errorMatch : result.errorMatches()) {
            // A pure-path candidate has no streaming section, so an applicability
            // error is terminal and the policy is INDETERMINATE.
            vote = UnanimousVoteCombiner.combineVotes(vote,
                    Vote.error(errorMatch.error(), errorMatch.document().metadata()), voterMetadata, strictMode);
        }
        for (var i = 0; i < matching.size(); i++) {
            val  voter = matching.get(i).voter();
            Vote newVote;
            if (voter instanceof Vote constantVote) {
                newVote = constantVote;
            } else {
                newVote = ((PureVoter) voter).vote(ctx);
            }
            vote = UnanimousVoteCombiner.combineVotes(vote, newVote, voterMetadata, strictMode);
            if (UnanimousVoteCombiner.isTerminal(vote, strictMode)) {
                return CombiningUtils.completeSetOutcome(vote, matching.subList(i + 1, matching.size()));
            }
        }
        return vote;
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
            boolean strictMode,
            boolean completeOutcome) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps         = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(8);
            val result       = index.matchKleene(ctx);
            val matching     = result.trueMatches();
            var combinedVote = accumulatorVote;
            for (val errorMatch : result.errorMatches()) {
                val document = errorMatch.document();
                // A set has no streaming section to dominate a target error, so an erroring
                // set is terminal INDETERMINATE.
                if (document instanceof CompiledPolicySet) {
                    combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote,
                            Vote.error(errorMatch.error(), document.metadata()), voterMetadata, strictMode);
                    continue;
                }
                // The body voter abstains exactly when the streaming section is FALSE, which
                // dominates the pure error and yields NOT_APPLICABLE. Otherwise the error
                // stands and the policy is INDETERMINATE. The pure section is not re-evaluated.
                val sub  = document.voter().evaluate(ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    return new VoteResult(null, deps);
                }
                Vote contribution;
                if (vote.authorizationDecision().decision() == Decision.NOT_APPLICABLE) {
                    contribution = vote;
                } else {
                    contribution = Vote.error(errorMatch.error(), document.metadata());
                }
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, contribution, voterMetadata,
                        strictMode);
            }
            for (var i = 0; i < matching.size(); i++) {
                val sub  = matching.get(i).voter().evaluate(ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    return new VoteResult(null, deps);
                }
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, vote, voterMetadata, strictMode);
                if (UnanimousVoteCombiner.isTerminal(combinedVote, strictMode)) {
                    if (completeOutcome) {
                        combinedVote = CombiningUtils.completeSetOutcome(combinedVote,
                                matching.subList(i + 1, matching.size()));
                    }
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
            for (var i = 0; i < policies.size(); i++) {
                val sub = policies.get(i).coverageVoter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.voteResult().dependencies());
                if (sub.voteResult().vote() == null) {
                    val partial = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                perPolicyCoverage.add(sub.coverage());
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(), voterMetadata,
                        strictMode);
                if (UnanimousVoteCombiner.isTerminal(combinedVote, strictMode)) {
                    combinedVote = CombiningUtils.completeSetOutcome(combinedVote,
                            policies.subList(i + 1, policies.size()));
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
     * Snapshot-driven PDP-level coverage voter for UNANIMOUS combining.
     * Walks all compiled documents per snapshot round, calls each one's
     * {@link CompiledDocument#coverageVoter()}, combines votes via
     * {@link UnanimousVoteCombiner}, and assembles a
     * {@link Coverage.PdpCoverage} from the per-document results.
     * Short-circuits when {@code isTerminal} returns true under the given
     * strictness. No target gate.
     */
    record UnanimousPdpCoverageVoter(
            List<? extends CompiledDocument> documents,
            VoterMetadata voterMetadata,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            boolean strictMode) implements CoverageVoter {

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
                combinedVote = UnanimousVoteCombiner.combineVotes(combinedVote, sub.voteResult().vote(), voterMetadata,
                        strictMode);
                if (UnanimousVoteCombiner.isTerminal(combinedVote, strictMode)) {
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
