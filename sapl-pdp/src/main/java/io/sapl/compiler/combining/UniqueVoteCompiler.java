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
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.CompiledDocument;
import io.sapl.compiler.pdp.PureVoter;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.VoteWithCoverage;
import io.sapl.compiler.pdp.Voter;
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
 */
@UtilityClass
public class UniqueVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, defaultDecision, errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, defaultDecision,
                errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    /**
     * Compiles coverage stream for PDP-level usage (no target expression).
     */
    public static Flux<VoteWithCoverage> compileCoverageStream(List<? extends CompiledDocument> compiledPolicies,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, defaultDecision, errorHandling);
        return PolicySetUtil.compileCoverageStream(voterMetadata, null, Value.TRUE, bodyFactory);
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory    = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, defaultDecision, errorHandling);
        val                                                  targetLocation = policySet.target() != null
                ? policySet.target().location()
                : null;
        return PolicySetUtil.compileCoverageStream(voterMetadata, targetLocation, isApplicable, bodyFactory);
    }

    private static Flux<VoteWithCoverage> evaluateAllPoliciesForCoverage(List<? extends CompiledDocument> policies,
            Coverage.TargetHit targetHit, VoterMetadata voterMetadata, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {

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

            val combinedVote = UniqueVoteCombiner.combineMultipleVotes(votes, voterMetadata);
            val finalVote    = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val setCoverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, policyCoverages);

            return new VoteWithCoverage(finalVote, setCoverage);
        });
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = UniqueVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        if (classified.streamPolicies().isEmpty()) {
            return new PureUniqueVoter(accumulatorVote, classified.purePolicies(), defaultDecision, errorHandling,
                    voterMetadata);
        }
        return new StreamUniqueVoter(accumulatorVote, classified.purePolicies(), classified.streamPolicies(),
                defaultDecision, errorHandling, voterMetadata);
    }

    record PureUniqueVoter(
            Vote accumulatorVote,
            List<CompiledDocument> documents,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, documents, voterMetadata, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, List<CompiledDocument> documents,
            VoterMetadata voterMetadata, EvaluationContext ctx) {
        var vote = accumulatorVote;
        for (val document : documents) {
            // Short-circuit on INDETERMINATE - uniqueness cannot be determined
            if (vote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                break;
            }
            val isApplicable = evaluateApplicability(document.isApplicable(), ctx);
            if (isApplicable instanceof ErrorValue error) {
                val errorVote = Vote.error(error, document.metadata());
                vote = UniqueVoteCombiner.combineVotes(vote, errorVote, voterMetadata);
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = UniqueVoteCombiner.combineVotes(vote, newVote, voterMetadata);
            }
        }
        return vote;
    }

    record StreamUniqueVoter(
            Vote accumulatorVote,
            List<CompiledDocument> pureDocuments,
            List<CompiledDocument> streamDocuments,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements StreamVoter {
        @Override
        public Flux<Vote> vote() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx  = ctxView.get(EvaluationContext.class);
                var pureVote = combinePureVoters(accumulatorVote, pureDocuments, voterMetadata, evalCtx);

                // Short-circuit if already INDETERMINATE - no need to evaluate streams
                if (pureVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                    return Flux.just(pureVote.finalizeVote(defaultDecision, errorHandling));
                }

                val streamVoters = new ArrayList<Flux<Vote>>(streamDocuments.size());
                for (CompiledDocument document : streamDocuments) {
                    // Short-circuit on INDETERMINATE - uniqueness cannot be determined
                    if (pureVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
                        break;
                    }
                    val isApplicable = evaluateApplicability(document.isApplicable(), evalCtx);
                    if (isApplicable instanceof ErrorValue error) {
                        val errorVote = Vote.error(error, document.metadata());
                        pureVote = UniqueVoteCombiner.combineVotes(pureVote, errorVote, voterMetadata);
                    } else if (isApplicable instanceof BooleanValue(var b) && b) {
                        streamVoters.add(((StreamVoter) document.voter()).vote());
                    }
                }
                if (streamVoters.isEmpty()) {
                    return Flux.just(pureVote.finalizeVote(defaultDecision, errorHandling));
                }
                val accumulator = pureVote;
                return Flux
                        .combineLatest(streamVoters, votes -> UniqueVoteCombiner.combineMultipleVotes(accumulator,
                                asTypedList(votes), voterMetadata))
                        .map(vote -> vote.finalizeVote(defaultDecision, errorHandling));
            });
        }
    }

}
