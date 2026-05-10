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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.*;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.compiler.policyset.PolicySetUtil;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.ABSTAIN;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.compiler.policyset.PolicySetUtil.ERROR_UNEXPECTED_STREAM_IN_TARGET;
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
 * produces a snapshot-driven voter that walks the policies sequentially
 * per round, stopping at the first non-NOT_APPLICABLE result. Tail
 * policies are not subscribed when an earlier policy resolves
 * applicability; their deps only enter the dependency set if the
 * snapshot round actually reaches them.</li>
 * </ul>
 */
@UtilityClass
public class FirstVoteCompiler {

    public static VoterAndCoverage compilePolicySet(PolicySet policySet, List<CompiledPolicy> compiledPolicies,
            CompiledExpression isApplicable, VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision, CombiningAlgorithm.ErrorHandling errorHandling) {
        val voter         = compileVoter(compiledPolicies, voterMetadata, policySet.location(), defaultDecision,
                errorHandling);
        val coverageVoter = compileCoverageVoter(policySet, isApplicable, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverageVoter);
    }

    /**
     * Constructs the snapshot-driven coverage voter for a FIRST policy set.
     * Walks the policies sequentially per snapshot round, applies the same
     * FIRST stop-on-non-NOT_APPLICABLE semantic as the production voter,
     * and assembles a {@link Coverage.PolicySetCoverage} from the per-policy
     * results.
     */
    private static CoverageVoter compileCoverageVoter(PolicySet policySet, CompiledExpression isApplicable,
            List<CompiledPolicy> compiledPolicies, VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision, CombiningAlgorithm.ErrorHandling errorHandling) {
        val targetLocation = policySet.target() != null ? policySet.target().location() : null;
        return new FirstPolicySetCoverageVoter(isApplicable, targetLocation, compiledPolicies, voterMetadata,
                defaultDecision, errorHandling);
    }

    /**
     * Compiles policies into a vote maker using stratified evaluation.
     * <p>
     * Applies three optimization levels:
     * <ol>
     * <li>Static short-circuit for leading constant policies</li>
     * <li>Pure vote maker when all remaining policies are non-streaming</li>
     * <li>Stream vote maker that walks policies sequentially per snapshot
     * round, stopping at the first non-NOT_APPLICABLE child vote</li>
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

        // 3. Stream fallback - sequential lazy walk in evaluate(ctx)
        return new FirstVoteStreamPolicySet(contributingVotes, remainingPolicies, voterMetadata, defaultDecision,
                errorHandling);
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
     * Stream vote maker for first evaluation with at least one streaming
     * policy. Walks the policies sequentially per snapshot round, stopping
     * at the first non-NOT_APPLICABLE child vote. Tail policies are not
     * subscribed when an earlier policy resolves applicability; their deps
     * only enter the dependency set if the snapshot round actually reaches
     * them. The store re-fires this voter when its current dep set changes.
     *
     * @param contributingVotes leading static NOT_APPLICABLE decisions
     * @param policies remaining policies requiring runtime evaluation
     * @param voterMetadata the policy set voterMetadata
     * @param defaultDecision the fallback when all policies are NOT_APPLICABLE
     * @param errorHandling how to handle INDETERMINATE votes
     */
    record FirstVoteStreamPolicySet(
            List<Vote> contributingVotes,
            List<CompiledPolicy> policies,
            VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps     = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(policies.size());
            val allVotes = new ArrayList<>(contributingVotes);
            for (val policy : policies) {
                val sub = policy.applicabilityAndVote().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.dependencies());
                if (sub.vote() == null) {
                    return new VoteResult(null, deps);
                }
                allVotes.add(sub.vote());
                if (sub.vote().authorizationDecision().decision() != NOT_APPLICABLE) {
                    val combined = Vote.combinedVote(sub.vote().authorizationDecision(), voterMetadata, allVotes,
                            sub.vote().outcome());
                    return new VoteResult(finalizeVote(combined, errorHandling, voterMetadata), deps);
                }
            }
            return new VoteResult(getFallbackVote(allVotes, voterMetadata, defaultDecision), deps);
        }
    }

    /**
     * Snapshot-driven coverage voter for first evaluation. Mirrors the
     * production-side {@link FirstVoteStreamPolicySet} but emits
     * {@link VoteResultWithCoverage}: walks policies sequentially per
     * snapshot round, evaluates each via {@code coverageVoter().evaluate(ctx)},
     * stops at the first non-NOT_APPLICABLE result, and assembles a
     * {@link Coverage.PolicySetCoverage} from the per-policy
     * {@link Coverage.DocumentCoverage} results. Tail policies that are
     * skipped contribute no deps and no coverage.
     * <p>
     * The target / {@code isApplicable} gate is evaluated inline. For
     * non-TRUE targets the result is the appropriate non-applicable or
     * error vote with an empty body coverage. A {@link StreamOperator}
     * target indicates an implementation bug and yields an error.
     */
    record FirstPolicySetCoverageVoter(
            CompiledExpression isApplicable,
            @Nullable SourceLocation targetLocation,
            List<CompiledPolicy> policies,
            VoterMetadata voterMetadata,
            CombiningAlgorithm.DefaultDecision defaultDecision,
            CombiningAlgorithm.ErrorHandling errorHandling) implements CoverageVoter {

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
            val allVotes          = new ArrayList<Vote>();
            val perPolicyCoverage = new ArrayList<Coverage.DocumentCoverage>();
            for (val policy : policies) {
                val sub = policy.coverageVoter().evaluate(ctx);
                StreamOperator.mergeDependencies(deps, sub.voteResult().dependencies());
                if (sub.voteResult().vote() == null) {
                    val partial = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                val policyVote = sub.voteResult().vote();
                allVotes.add(policyVote);
                perPolicyCoverage.add(sub.coverage());
                if (policyVote.authorizationDecision().decision() != NOT_APPLICABLE) {
                    val combined  = Vote.combinedVote(policyVote.authorizationDecision(), voterMetadata, allVotes,
                            policyVote.outcome());
                    val finalVote = finalizeVote(combined, errorHandling, voterMetadata);
                    val coverage  = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
                    return new VoteResultWithCoverage(new VoteResult(finalVote, deps), coverage);
                }
            }
            val fallback = getFallbackVote(allVotes, voterMetadata, defaultDecision);
            val coverage = new Coverage.PolicySetCoverage(voterMetadata, targetHit, perPolicyCoverage);
            return new VoteResultWithCoverage(new VoteResult(fallback, deps), coverage);
        }
    }

    /**
     * Finalizes a vote by applying error handling.
     * <p>
     * With {@code errors abstain}, INDETERMINATE is converted to NOT_APPLICABLE
     * via {@link Vote#abstain}. With {@code errors propagate}, INDETERMINATE
     * is preserved.
     *
     * @param vote the combined vote
     * @param errorHandling how to handle INDETERMINATE
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
