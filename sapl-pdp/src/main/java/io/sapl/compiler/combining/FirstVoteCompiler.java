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
import io.sapl.api.model.SourceLocation;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policyset.PolicySetUtil;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.ABSTAIN;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.compiler.policyset.PolicySetUtil.getFallbackVote;

/**
 * Compiles policy sets using the first combining algorithm.
 * <p>
 * The first algorithm evaluates policies in order until one returns
 * a vote other than NOT_APPLICABLE. That vote becomes the final result.
 * If all policies return NOT_APPLICABLE, the set returns NOT_APPLICABLE.
 * <p>
 * The compiler produces three evaluation strata based on policy complexity:
 * <ul>
 * <li><b>Static short-circuit:</b> Leading policies with constant decisions are
 * evaluated at compile time. Returns immediately if any yields
 * non-NOT_APPLICABLE.</li>
 * <li><b>Pure evaluation:</b> When remaining policies require only subscription
 * data (no streaming attributes), produces a synchronous vote maker.</li>
 * <li><b>Stream evaluation:</b> When any policy requires streaming attributes,
 * builds a reverse-chained flux for lazy reactive evaluation.</li>
 * </ul>
 */
@UtilityClass
public class FirstVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet, List<CompiledPolicy> compiledPolicies,
            CompiledExpression isApplicable, VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision, CombiningAlgorithm.ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, policySet.location(), defaultDecision,
                errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, defaultDecision,
                errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    /**
     * Compiles the coverage stream for the policy set.
     * Delegates target evaluation to {@link PolicySetUtil} and provides a
     * first body factory.
     */
    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<CompiledPolicy> compiledPolicies, VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision, CombiningAlgorithm.ErrorHandling errorHandling) {
        val bodyFactory    = bodyCoverageFactory(compiledPolicies, voterMetadata, defaultDecision, errorHandling);
        val targetLocation = policySet.target() != null ? policySet.target().location() : null;
        return PolicySetUtil.compileCoverageStream(voterMetadata, targetLocation, isApplicable, bodyFactory);
    }

    /**
     * Creates a factory for body coverage evaluation using first
     * semantics.
     */
    private Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyCoverageFactory(List<CompiledPolicy> policies,
            VoterMetadata voterMetadata, CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) {
        return targetHit -> evaluatePoliciesForCoverage(policies, 0,
                new Coverage.PolicySetCoverage(voterMetadata, targetHit, List.of()), new ArrayList<>(), voterMetadata,
                defaultDecision, errorHandling);
    }

    /**
     * Recursively evaluates policies for coverage, accumulating results.
     * Stops at first non-NOT_APPLICABLE vote (first semantics).
     */
    private static Flux<VoteWithCoverage> evaluatePoliciesForCoverage(List<CompiledPolicy> policies, int index,
            Coverage.PolicySetCoverage accumulatedCoverage, List<Vote> contributingVotes, VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision, CombiningAlgorithm.ErrorHandling errorHandling) {

        if (index >= policies.size()) {
            val fallbackVote = getFallbackVote(contributingVotes, voterMetadata, defaultDecision);
            return Flux.just(new VoteWithCoverage(fallbackVote, accumulatedCoverage));
        }

        return policies.get(index).coverage().switchMap(policyResult -> {
            val policyVote      = policyResult.vote();
            val policyCoverage  = policyResult.coverage();
            val newCoverage     = accumulatedCoverage.with(policyCoverage);
            val newContributing = new ArrayList<>(contributingVotes);
            newContributing.add(policyVote);
            if (policyVote.authorizationDecision().decision() == NOT_APPLICABLE) {
                return evaluatePoliciesForCoverage(policies, index + 1, newCoverage, newContributing, voterMetadata,
                        defaultDecision, errorHandling);
            }

            val setVote   = Vote.combinedVote(policyVote.authorizationDecision(), voterMetadata, newContributing,
                    policyVote.outcome());
            val finalized = finalizeVote(setVote, errorHandling, voterMetadata);
            return Flux.just(new VoteWithCoverage(finalized, newCoverage));
        });
    }

    /**
     * Compiles policies into a vote maker using stratified evaluation.
     * <p>
     * Applies three optimization levels:
     * <ol>
     * <li>Static short-circuit for leading constant policies</li>
     * <li>Pure vote maker when all remaining policies are non-streaming</li>
     * <li>Stream vote maker with reverse-chained flux otherwise</li>
     * </ol>
     */
    private static Voter compileVoter(List<CompiledPolicy> policies, VoterMetadata voterMetadata,
            SourceLocation location, CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) {
        // 1. Short-circuit: collect static decisions, return first non-NOT_APPLICABLE
        val contributingVotes = new ArrayList<Vote>();
        var firstNonStatic    = 0;
        for (var policy : policies) {
            if (!(policy.applicabilityAndVote() instanceof Vote policyVote)) {
                break; // non-static, stop short-circuit scan
            }
            contributingVotes.add(policyVote);
            if (policyVote.authorizationDecision().decision() != NOT_APPLICABLE) {
                val combined = Vote.combinedVote(policyVote.authorizationDecision(), voterMetadata, contributingVotes,
                        policyVote.outcome());
                return finalizeVote(combined, errorHandling, voterMetadata);
            }
            firstNonStatic++;
        }

        // All policies were static NOT_APPLICABLE
        if (firstNonStatic == policies.size()) {
            switch (defaultDecision) {
            case ABSTAIN -> {
                return Vote.abstain(voterMetadata, contributingVotes);
            }
            case DENY    -> {
                return Vote.combinedVote(AuthorizationDecision.DENY, voterMetadata, contributingVotes, Outcome.DENY);
            }
            case PERMIT  -> {
                return Vote.combinedVote(AuthorizationDecision.PERMIT, voterMetadata, contributingVotes,
                        Outcome.PERMIT);
            }
            }
        }

        // Trim leading static NOT_APPLICABLE for remaining evaluation
        val remainingPolicies = policies.subList(firstNonStatic, policies.size());

        // 2. Check if all remaining policies are pure/static (no streams)
        val allPure = remainingPolicies.stream().map(CompiledPolicy::applicabilityAndVote)
                .noneMatch(StreamVoter.class::isInstance);
        if (allPure) {
            return new FirstVotePurePolicySet(contributingVotes, remainingPolicies, voterMetadata, location,
                    defaultDecision, errorHandling);
        }

        // 3. Stream fallback - build reverse chain for lazy evaluation
        return new FirstVoteStreamPolicySet(
                buildReverseChain(remainingPolicies, contributingVotes, voterMetadata, defaultDecision, errorHandling));
    }

    /**
     * Builds a reverse-chained flux for lazy streaming evaluation.
     * <p>
     * Constructs the chain from last to first policy. Each policy's flux
     * switches to the tail chain on NOT_APPLICABLE, enabling lazy evaluation
     * that stops at the first applicable policy.
     */
    private static Flux<Vote> buildReverseChain(List<CompiledPolicy> policies, List<Vote> contributingVotes,
            VoterMetadata voterMetadata, CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) {
        val fallbackVote = getFallbackVote(contributingVotes, voterMetadata, defaultDecision);

        Flux<Vote> votingChain = Flux.just(fallbackVote);
        for (int i = policies.size() - 1; i >= 0; i--) {
            val policy        = policies.get(i);
            val voteChainTail = votingChain;
            votingChain = PolicySetUtil.toStream(policy.applicabilityAndVote()).switchMap(currentVote -> {
                val allVotes = new ArrayList<>(contributingVotes);
                allVotes.add(currentVote);
                if (currentVote.authorizationDecision().decision() == NOT_APPLICABLE) {
                    return voteChainTail.map(voteAtTail -> voteAtTail.withVote(currentVote));
                }

                val combined = Vote.combinedVote(currentVote.authorizationDecision(), voterMetadata, allVotes,
                        currentVote.outcome());
                return Flux.just(finalizeVote(combined, errorHandling, voterMetadata));
            });
        }
        return votingChain;
    }

    /**
     * Pure vote maker for first evaluation without streaming
     * policies.
     * <p>
     * Evaluates policies sequentially at runtime, returning the first
     * non-NOT_APPLICABLE vote.
     *
     * @param contributingVotes leading static NOT_APPLICABLE decisions
     * @param policies remaining policies requiring runtime evaluation
     * @param voterMetadata the policy set voterMetadata
     * @param location source location for error reporting
     */
    record FirstVotePurePolicySet(
            List<Vote> contributingVotes,
            List<CompiledPolicy> policies,
            VoterMetadata voterMetadata,
            SourceLocation location,
            CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) implements PureVoter {

        @Override
        public Vote vote(EvaluationContext ctx) {
            val allVotes = new ArrayList<>(contributingVotes);
            for (var policy : policies) {
                val policyVote = PolicySetUtil.evaluatePure(policy, ctx, location);
                allVotes.add(policyVote);
                if (policyVote.authorizationDecision().decision() != NOT_APPLICABLE) {
                    val combined = Vote.combinedVote(policyVote.authorizationDecision(), voterMetadata, allVotes,
                            policyVote.outcome());
                    return finalizeVote(combined, errorHandling, voterMetadata);
                }
            }
            return getFallbackVote(allVotes, voterMetadata, defaultDecision);
        }
    }

    /**
     * Stream vote maker wrapping a prebuilt reverse chain.
     * <p>
     * The chain is constructed at compile time; this record provides the
     * {@link StreamVoter} interface.
     *
     * @param chain the prebuilt reverse-chained flux
     */
    record FirstVoteStreamPolicySet(Flux<Vote> chain) implements StreamVoter {
        @Override
        public Flux<Vote> vote() {
            return chain;
        }
    }

    /**
     * Finalizes a vote by applying error handling.
     * <p>
     * With {@code errors abstain}, INDETERMINATE is converted to the default
     * decision. With {@code errors propagate}, INDETERMINATE is preserved.
     *
     * @param vote the combined vote
     * @param errorHandling how to handle INDETERMINATE
     * @param defaultDecision what to return when abstaining
     * @param voterMetadata metadata for the finalized vote
     * @return finalized vote
     */
    private static Vote finalizeVote(Vote vote, CombiningAlgorithm.ErrorHandling errorHandling,
            VoterMetadata voterMetadata) {
        if (errorHandling == ABSTAIN && vote.authorizationDecision().decision() == INDETERMINATE) {
            // Error abstains - return NOT_APPLICABLE (not the default decision)
            return Vote.abstain(voterMetadata, vote.contributingVotes());
        }
        return vote;
    }
}
