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
        val voter = compileVoter(compiledPolicies, voterMetadata, priorityDecision, defaultDecision, errorHandling,
                ctx);
        return VoterAndCoverage.forSet(voter, policySet, isApplicable, voterMetadata, compiledPolicies);
    }

    /**
     * Compiles the coverage voter using the default compiler options. A compiled PDP
     * exposes the voter for its own configuration through its coverageVoter accessor.
     */
    public static CoverageVoter compilePdpCoverageVoter(List<? extends CompiledDocument> compiledDocuments,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        val ctx   = new CompilationContext(new DefaultFunctionBroker());
        val voter = compileVoter(compiledDocuments, voterMetadata, priorityDecision, defaultDecision, errorHandling,
                ctx);
        return ObservedVoter.pdp(voter, voterMetadata, compiledDocuments);
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

        val index = IndexFactory.createVoterIndex(classified.allIndexedPolicies(), ctx);

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

        private static Vote combinePureVoters(Vote accumulatorVote, PolicyIndex index, Decision priorityDecision,
                VoterMetadata voterMetadata, EvaluationContext ctx) {
            val result = index.matchKleene(ctx);
            var vote   = accumulatorVote;
            for (val document : result.trueMatches()) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = PriorityBasedVoteCombiner.combineVotes(vote, newVote, priorityDecision, voterMetadata);
            }
            // A pure-path candidate has no streaming section, so an applicability error is
            // terminal. The policy or set is INDETERMINATE.
            for (val errorMatch : result.errorMatches()) {
                val errorVote = Vote.error(errorMatch.error(), errorMatch.document().metadata());
                vote = PriorityBasedVoteCombiner.combineVotes(vote, errorVote, priorityDecision, voterMetadata);
            }
            return vote;
        }
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
            val result       = index.matchKleene(ctx);
            var combinedVote = accumulatorVote;
            for (val document : result.trueMatches()) {
                val sub  = document.voter().evaluate(ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    ObservedVoter.completedBeforePending(ctx, combinedVote.contributingVotes());
                    return new VoteResult(null, deps);
                }
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, vote, priorityDecision,
                        voterMetadata);
            }
            for (val errorMatch : result.errorMatches()) {
                val sub  = errorMatch.document().evaluateApplicabilityError(errorMatch.error(), ctx);
                val vote = sub.vote();
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (vote == null) {
                    ObservedVoter.completedBeforePending(ctx, combinedVote.contributingVotes());
                    return new VoteResult(null, deps);
                }
                combinedVote = PriorityBasedVoteCombiner.combineVotes(combinedVote, vote, priorityDecision,
                        voterMetadata);
            }
            return new VoteResult(combinedVote.finalizeVote(defaultDecision, errorHandling), deps);
        }
    }

}
