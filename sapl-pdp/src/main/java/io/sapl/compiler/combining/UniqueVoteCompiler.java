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
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.index.IndexFactory;
import io.sapl.compiler.index.PolicyIndex;
import lombok.experimental.UtilityClass;
import lombok.val;
import io.sapl.compiler.policy.ObservedVoter;
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.functions.DefaultFunctionBroker;

import java.util.HashMap;
import java.util.List;

import static io.sapl.compiler.combining.CombiningUtils.classifyPoliciesByEvaluationStrategy;

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
        val voter = compileVoter(compiledPolicies, voterMetadata, defaultDecision, errorHandling, true, ctx);
        return VoterAndCoverage.forSet(voter, policySet, isApplicable, voterMetadata, compiledPolicies);
    }

    /**
     * Compiles the coverage voter using the default compiler options. A compiled PDP
     * exposes the voter for its own configuration through its coverageVoter accessor.
     */
    public static CoverageVoter compilePdpCoverageVoter(List<? extends CompiledDocument> compiledDocuments,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val ctx   = new CompilationContext(new DefaultFunctionBroker());
        val voter = compileVoter(compiledDocuments, voterMetadata, defaultDecision, errorHandling, false, ctx);
        return ObservedVoter.pdp(voter, voterMetadata, compiledDocuments);
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean completeOutcome,
            CompilationContext ctx) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        var accumulatorVote = UniqueVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata);

        if (completeOutcome) {
            accumulatorVote = CombiningUtils.completeSetOutcomeFromVotes(accumulatorVote, classified.foldableVotes());
        }
        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        val index = IndexFactory.createVoterIndex(classified.allIndexedPolicies(), ctx);

        if (classified.streamPolicies().isEmpty()) {
            return new PureUniqueVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                    completeOutcome);
        }
        return new StreamUniqueVoter(accumulatorVote, index, defaultDecision, errorHandling, voterMetadata,
                completeOutcome);
    }

    record PureUniqueVoter(
            Vote accumulatorVote,
            PolicyIndex index,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean completeOutcome) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            // At the set level (completeOutcome) the could-have-been of a
            // short-circuited INDETERMINATE must include the un-folded matched
            // policies' potential, which matchKleeneWhile cannot expose. Use a full
            // match and complete the outcome. At the PDP level the outcome is
            // unused, so keep the matchKleeneWhile hard short-circuit.
            val vote = completeOutcome ? combineMatchedAndComplete(accumulatorVote, index, voterMetadata, ctx)
                    : combinePureVoters(accumulatorVote, index, voterMetadata, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }

        private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, VoterMetadata voterMetadata,
                EvaluationContext ctx) {
            var holder = new Vote[] { accumulatorVote };
            index.matchKleeneWhile(ctx, stepResult -> {
                for (val errorMatch : stepResult.errorMatches()) {
                    // A pure-path candidate has no streaming section, so an applicability
                    // error is terminal and the policy is INDETERMINATE.
                    holder[0] = UniqueVoteCombiner.combineVotes(holder[0],
                            Vote.error(errorMatch.error(), errorMatch.document().metadata()), voterMetadata);
                }
                for (val document : stepResult.trueMatches()) {
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

        private static Vote combineMatchedAndComplete(Vote accumulatorVote, PolicyIndex index,
                VoterMetadata voterMetadata, EvaluationContext ctx) {
            val result   = index.matchKleene(ctx);
            val matching = result.trueMatches();
            var vote     = accumulatorVote;
            for (val errorMatch : result.errorMatches()) {
                // A pure-path candidate has no streaming section, so an applicability
                // error is terminal and the policy is INDETERMINATE.
                vote = UniqueVoteCombiner.combineVotes(vote,
                        Vote.error(errorMatch.error(), errorMatch.document().metadata()), voterMetadata, true);
            }
            for (var i = 0; i < matching.size(); i++) {
                val  voter = matching.get(i).voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = UniqueVoteCombiner.combineVotes(vote, newVote, voterMetadata, true);
                if (vote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                    return CombiningUtils.completeSetOutcome(vote, matching.subList(i + 1, matching.size()));
                }
            }
            return vote;
        }
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
            VoterMetadata voterMetadata,
            boolean completeOutcome) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps         = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(8);
            val result       = index.matchKleene(ctx);
            val matching     = result.trueMatches();
            var combinedVote = accumulatorVote;
            for (val errorMatch : result.errorMatches()) {
                val sub  = errorMatch.document().evaluateApplicabilityError(errorMatch.error(), ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    ObservedVoter.completedBeforePending(ctx, combinedVote.contributingVotes());
                    return new VoteResult(null, deps);
                }
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, vote, voterMetadata, completeOutcome);
            }
            for (var i = 0; i < matching.size(); i++) {
                val sub  = matching.get(i).voter().evaluate(ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    ObservedVoter.completedBeforePending(ctx, combinedVote.contributingVotes());
                    return new VoteResult(null, deps);
                }
                combinedVote = UniqueVoteCombiner.combineVotes(combinedVote, vote, voterMetadata, completeOutcome);
                if (combinedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
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

}
