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
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policyset.PolicySetUtil;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.sapl.compiler.combining.CombiningUtils.asTypedList;
import static io.sapl.compiler.combining.CombiningUtils.classifyPoliciesByEvaluationStrategy;
import static io.sapl.compiler.combining.CombiningUtils.evaluateApplicability;

@UtilityClass
public class PriorityVoteCompiler {
    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, priorityDecision, defaultDecision, errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, priorityDecision,
                defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    /**
     * Compiles coverage stream for PDP-level usage (no target expression).
     */
    public static Flux<VoteWithCoverage> compileCoverageStream(List<? extends CompiledDocument> compiledPolicies,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, priorityDecision, defaultDecision, errorHandling);
        return PolicySetUtil.compileCoverageStream(voterMetadata, null, Value.TRUE, bodyFactory);
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory    = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, priorityDecision, defaultDecision, errorHandling);
        val                                                  targetLocation = policySet.target() != null
                ? policySet.target().location()
                : null;
        return PolicySetUtil.compileCoverageStream(voterMetadata, targetLocation, isApplicable, bodyFactory);
    }

    /**
     * Evaluates all policies for coverage collection.
     * Unlike the optimized voter path, this evaluates ALL policies to provide
     * comprehensive coverage information for testing.
     */
    private static Flux<VoteWithCoverage> evaluateAllPoliciesForCoverage(List<? extends CompiledDocument> policies,
            Coverage.TargetHit targetHit, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {

        if (policies.isEmpty()) {
            val fallbackVote = Vote.abstain(voterMetadata).finalizeVote(defaultDecision, errorHandling);
            val coverage     = new Coverage.PolicySetCoverage(voterMetadata, targetHit, List.of());
            return Flux.just(new VoteWithCoverage(fallbackVote, coverage));
        }

        List<Flux<VoteWithCoverage>> coverageStreams = policies.stream().map(CompiledDocument::coverage).toList();

        return Flux.combineLatest(coverageStreams, results -> {
            val votes           = new ArrayList<Vote>(results.length);
            val policyCoverages = new ArrayList<Coverage.DocumentCoverage>(results.length);

            for (Object result : results) {
                val vwc = (VoteWithCoverage) result;
                votes.add(vwc.vote());
                policyCoverages.add(vwc.coverage());
            }

            val combinedVote = PriorityBasedVoteCombiner.combineMultipleVotes(votes, priorityDecision, voterMetadata);
            val finalVote    = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val setCoverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, policyCoverages);

            return new VoteWithCoverage(finalVote, setCoverage);
        });
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            Decision priorityDecision, DefaultDecision defaultDecision, ErrorHandling errorHandling) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = PriorityBasedVoteCombiner.combineMultipleVotes(classified.foldableVotes(),
                priorityDecision, voterMetadata);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        if (classified.streamPolicies().isEmpty()) {
            return new PurePriorityVoter(accumulatorVote, classified.purePolicies(), priorityDecision, defaultDecision,
                    errorHandling, voterMetadata);
        }
        return new StreamPriorityVoter(accumulatorVote, classified.purePolicies(), classified.streamPolicies(),
                priorityDecision, defaultDecision, errorHandling, voterMetadata);
    }

    record PurePriorityVoter(
            Vote accumulatorVote,
            List<CompiledDocument> documents,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, documents, priorityDecision, voterMetadata, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    record StreamPriorityVoter(
            Vote accumulatorVote,
            List<CompiledDocument> pureDocuments,
            List<CompiledDocument> streamDocuments,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements StreamVoter {
        @Override
        public Flux<Vote> vote() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx      = ctxView.get(EvaluationContext.class);
                var pureVote     = combinePureVoters(accumulatorVote, pureDocuments, priorityDecision, voterMetadata,
                        evalCtx);
                val streamVoters = new ArrayList<Flux<Vote>>(streamDocuments.size());
                for (CompiledDocument document : streamDocuments) {
                    val isApplicable = evaluateApplicability(document.isApplicable(), evalCtx);
                    if (isApplicable instanceof ErrorValue error) {
                        val errorVote = Vote.error(error, document.metadata());
                        pureVote = PriorityBasedVoteCombiner.combineVotes(pureVote, errorVote, priorityDecision,
                                voterMetadata);
                    } else if (isApplicable instanceof BooleanValue(var b) && b) {
                        streamVoters.add(((StreamVoter) document.voter()).vote());
                    }
                }
                if (streamVoters.isEmpty()) {
                    return Flux.just(pureVote.finalizeVote(defaultDecision, errorHandling));
                }
                val accumulator = pureVote;
                return Flux
                        .combineLatest(streamVoters,
                                votes -> PriorityBasedVoteCombiner.combineMultipleVotes(accumulator, asTypedList(votes),
                                        priorityDecision, voterMetadata))
                        .map(vote -> vote.finalizeVote(defaultDecision, errorHandling));
            });
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, List<CompiledDocument> documents,
            Decision priorityDecision, VoterMetadata voterMetadata, EvaluationContext ctx) {
        var vote = accumulatorVote;
        for (val document : documents) {
            val isApplicable = evaluateApplicability(document.isApplicable(), ctx);
            if (isApplicable instanceof ErrorValue error) {
                val errorVote = Vote.error(error, document.metadata());
                vote = PriorityBasedVoteCombiner.combineVotes(vote, errorVote, priorityDecision, voterMetadata);
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = PriorityBasedVoteCombiner.combineVotes(vote, newVote, priorityDecision, voterMetadata);
            }
        }
        return vote;
    }

}
