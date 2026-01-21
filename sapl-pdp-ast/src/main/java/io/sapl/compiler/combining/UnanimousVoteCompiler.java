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

import static io.sapl.compiler.combining.CombiningUtils.asTypedList;
import static io.sapl.compiler.combining.CombiningUtils.classifyPoliciesByEvaluationStrategy;
import static io.sapl.compiler.combining.CombiningUtils.evaluateApplicability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.EvaluationContext;
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
 * <li><b>Normal mode:</b> Agreement on entitlement (PERMIT/DENY), constraints
 * merged</li>
 * <li><b>Strict mode:</b> Exact equality required (decision + all
 * constraints)</li>
 * </ul>
 */
@UtilityClass
public class UnanimousVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            boolean strictMode) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, defaultDecision, errorHandling, strictMode);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, defaultDecision,
                errorHandling, strictMode);
        return new VoterAndCoverage(voter, coverage);
    }

    /**
     * Compiles coverage stream for PDP-level usage (no target expression).
     */
    public static Flux<VoteWithCoverage> compileCoverageStream(List<? extends CompiledDocument> compiledPolicies,
            VoterMetadata voterMetadata, DefaultDecision defaultDecision, ErrorHandling errorHandling,
            boolean strictMode) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, defaultDecision, errorHandling, strictMode);
        return PolicySetUtil.compileCoverageStream(voterMetadata, null, Value.TRUE, bodyFactory);
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean strictMode) {
        Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory    = targetHit -> evaluateAllPoliciesForCoverage(
                compiledPolicies, targetHit, voterMetadata, defaultDecision, errorHandling, strictMode);
        val                                                  targetLocation = policySet.target() != null
                ? policySet.target().location()
                : null;
        return PolicySetUtil.compileCoverageStream(voterMetadata, targetLocation, isApplicable, bodyFactory);
    }

    private static Flux<VoteWithCoverage> evaluateAllPoliciesForCoverage(List<? extends CompiledDocument> policies,
            Coverage.TargetHit targetHit, VoterMetadata voterMetadata, DefaultDecision defaultDecision,
            ErrorHandling errorHandling, boolean strictMode) {

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

            val combinedVote = UnanimousVoteCombiner.combineMultipleVotes(votes, voterMetadata, strictMode);
            val finalVote    = combinedVote.finalizeVote(defaultDecision, errorHandling);
            val setCoverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, policyCoverages);

            return new VoteWithCoverage(finalVote, setCoverage);
        });
    }

    public static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision, ErrorHandling errorHandling, boolean strictMode) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = UnanimousVoteCombiner.combineMultipleVotes(classified.foldableVotes(), voterMetadata,
                strictMode);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return accumulatorVote.finalizeVote(defaultDecision, errorHandling);
        }

        if (classified.streamPolicies().isEmpty()) {
            return new PureUnanimousVoter(accumulatorVote, classified.purePolicies(), defaultDecision, errorHandling,
                    voterMetadata, strictMode);
        }
        return new StreamUnanimousVoter(accumulatorVote, classified.purePolicies(), classified.streamPolicies(),
                defaultDecision, errorHandling, voterMetadata, strictMode);
    }

    record PureUnanimousVoter(
            Vote accumulatorVote,
            List<CompiledDocument> documents,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean strictMode) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, documents, voterMetadata, strictMode, ctx);
            return vote.finalizeVote(defaultDecision, errorHandling);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, List<CompiledDocument> documents,
            VoterMetadata voterMetadata, boolean strictMode, EvaluationContext ctx) {
        var vote = accumulatorVote;
        for (val document : documents) {
            // Short-circuit on terminal INDETERMINATE
            if (UnanimousVoteCombiner.isTerminal(vote, strictMode)) {
                break;
            }
            val isApplicable = evaluateApplicability(document.isApplicable(), ctx);
            if (isApplicable instanceof ErrorValue error) {
                val errorVote = Vote.error(error, document.metadata());
                vote = UnanimousVoteCombiner.combineVotes(vote, errorVote, voterMetadata, strictMode);
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = UnanimousVoteCombiner.combineVotes(vote, newVote, voterMetadata, strictMode);
            }
        }
        return vote;
    }

    record StreamUnanimousVoter(
            Vote accumulatorVote,
            List<CompiledDocument> pureDocuments,
            List<CompiledDocument> streamDocuments,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata,
            boolean strictMode) implements StreamVoter {
        @Override
        public Flux<Vote> vote() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx  = ctxView.get(EvaluationContext.class);
                var pureVote = combinePureVoters(accumulatorVote, pureDocuments, voterMetadata, strictMode, evalCtx);

                // Short-circuit if already terminal - no need to evaluate streams
                if (UnanimousVoteCombiner.isTerminal(pureVote, strictMode)) {
                    return Flux.just(pureVote.finalizeVote(defaultDecision, errorHandling));
                }

                val streamVoters = new ArrayList<Flux<Vote>>(streamDocuments.size() + 1);
                streamVoters.add(Flux.empty());
                for (CompiledDocument document : streamDocuments) {
                    // Short-circuit on terminal INDETERMINATE
                    if (UnanimousVoteCombiner.isTerminal(pureVote, strictMode)) {
                        break;
                    }
                    val isApplicable = evaluateApplicability(document.isApplicable(), evalCtx);
                    if (isApplicable instanceof ErrorValue error) {
                        val errorVote = Vote.error(error, document.metadata());
                        pureVote = UnanimousVoteCombiner.combineVotes(pureVote, errorVote, voterMetadata, strictMode);
                    } else if (isApplicable instanceof BooleanValue(var b) && b) {
                        streamVoters.add(((StreamVoter) document.voter()).vote());
                    }
                }
                streamVoters.set(0, Flux.just(pureVote));
                return Flux
                        .combineLatest(streamVoters, votes -> UnanimousVoteCombiner
                                .combineMultipleVotes(asTypedList(votes), voterMetadata, strictMode))
                        .map(vote -> vote.finalizeVote(defaultDecision, errorHandling));
            });
        }
    }

}
