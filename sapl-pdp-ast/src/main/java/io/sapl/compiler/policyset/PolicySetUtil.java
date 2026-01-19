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
package io.sapl.compiler.policyset;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.*;
import io.sapl.compiler.policy.CompiledPolicy;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Function;

/**
 * Shared utilities for policy set compilation.
 * <p>
 * Provides common building blocks used by combining algorithm compilers:
 * <ul>
 * <li><b>Applicability chaining:</b> Wraps vote makers with target
 * expression evaluation.</li>
 * <li><b>Coverage stream building:</b> Chains target evaluation with body
 * coverage for testing.</li>
 * <li><b>Decision maker lifting:</b> Converts any vote maker type to
 * streams or evaluates in pure context.</li>
 * </ul>
 */
@UtilityClass
public class PolicySetUtil {

    public static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE = "Unexpected isApplicable type. Indicates implementation bug.";
    public static final String ERROR_STREAM_IN_PURE_CONTEXT        = "Stream vote maker in pure context. Indicates implementation bug.";
    public static final String ERROR_UNEXPECTED_STREAM_IN_TARGET   = "Unexpected Stream Operator in target expression. Indicates implementation bug.";

    /**
     * Wraps a vote maker with applicability checking based on the target
     * expression type.
     *
     * @param isApplicable the compiled target expression determining applicability
     * @param voter the vote maker from the combining algorithm
     * @param voterMetadata the policy set voterMetadata
     * @return a vote maker that combines applicability and vote evaluation
     */
    public static Voter compileApplicabilityAndVoter(CompiledExpression isApplicable, Voter voter,
            VoterMetadata voterMetadata) {
        return switch (isApplicable) {
        case ErrorValue error                                      -> Vote.error(error, voterMetadata);
        case BooleanValue(var b) when b                            -> voter;
        case BooleanValue ignored                                  -> Vote.abstain(voterMetadata);
        case PureOperator po when voter instanceof StreamVoter sdm ->
            new PureApplicabilityStreamPolicySet(po, sdm, voterMetadata);
        case PureOperator po                                       ->
            new ApplicabilityCheckingPurePolicySet(po, voter, voterMetadata);
        case StreamOperator so                                     ->
            new ApplicabilityCheckingStreamPolicySet(so, voter, voterMetadata);
        default                                                    ->
            Vote.error(new ErrorValue(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), voterMetadata);
        };
    }

    /**
     * Compiles a coverage stream by chaining target evaluation with body coverage.
     * <p>
     * Handles target expression types analogously to
     * {@link #compileApplicabilityAndVoter}: static values short-circuit,
     * pure operators defer evaluation, and stream operators in targets are errors.
     *
     * @param policySet the policy set being compiled
     * @param isApplicable the compiled target expression
     * @param bodyFactory factory producing coverage stream when target matches
     * @return flux emitting decisions with coverage information
     */
    public static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory) {
        if (policySet.target() == null) {
            return bodyFactory.apply(Coverage.BLANK_TARGET_HIT);
        }
        val targetLocation = policySet.target().location();
        switch (isApplicable) {
        case Value match             -> {
            return coverageStreamFromMatch(policySet, bodyFactory, match, targetLocation);
        }
        case StreamOperator ignored  -> {
            var coverage = new Coverage.PolicySetCoverage(policySet.metadata(), Coverage.NO_TARGET_HIT, List.of());
            var decision = Vote.error(Value.error(ERROR_UNEXPECTED_STREAM_IN_TARGET), policySet.metadata());
            return Flux.just(new VoteWithCoverage(decision, coverage));
        }
        case PureOperator pureTarget -> {
            return Flux.deferContextual(ctxView -> coverageStreamFromMatch(policySet, bodyFactory,
                    pureTarget.evaluate(ctxView.get(EvaluationContext.class)), targetLocation));
        }
        }
    }

    /**
     * Produces coverage stream from a resolved target match value.
     * <p>
     * TRUE continues to body evaluation, FALSE yields NOT_APPLICABLE,
     * errors yield INDETERMINATE. All cases record the target hit for coverage.
     */
    private static Flux<VoteWithCoverage> coverageStreamFromMatch(PolicySet policySet,
            Function<Coverage.TargetHit, Flux<VoteWithCoverage>> bodyFactory, Value match,
            SourceLocation targetLocation) {
        var targetHit = new Coverage.TargetResult(match, targetLocation);
        if (Value.TRUE.equals(match)) {
            return bodyFactory.apply(targetHit);
        } else {
            var  coverage = new Coverage.PolicySetCoverage(policySet.metadata(), targetHit, List.of());
            Vote vote;
            if (Value.FALSE.equals(match)) {
                vote = Vote.abstain(policySet.metadata());
            } else {
                vote = Vote.error((ErrorValue) match, policySet.metadata());
            }
            return Flux.just(new VoteWithCoverage(vote, coverage));
        }
    }

    /**
     * Lifts any vote maker type to a flux for uniform stream handling.
     *
     * @param voter the vote maker to lift
     * @param priorAttributes attributes from prior evaluation
     * @return flux emitting policy decisions
     */
    public static Flux<Vote> toStream(Voter voter, List<AttributeRecord> priorAttributes) {
        return switch (voter) {
        case Vote d        -> Flux.just(d);
        case PureVoter p   -> Flux.deferContextual(ctxView -> {
                           val ctx = ctxView.get(EvaluationContext.class);
                           return Flux.just(p.vote(priorAttributes, ctx));
                       });
        case StreamVoter s -> s.vote(priorAttributes);
        };
    }

    /**
     * Evaluates a policy in pure context, expecting non-streaming vote maker.
     *
     * @param policy the compiled policy to evaluate
     * @param priorAttributes attributes from prior evaluation
     * @param ctx the evaluation context
     * @param location source location for errors reporting
     * @return the policy vote
     */
    public static Vote evaluatePure(CompiledPolicy policy, List<AttributeRecord> priorAttributes, EvaluationContext ctx,
            SourceLocation location) {
        return switch (policy.applicabilityAndVote()) {
        case Vote d              -> d;
        case PureVoter p         -> p.vote(priorAttributes, ctx);
        case StreamVoter ignored ->
            Vote.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT, null, location), policy.metadata());
        };
    }

    /**
     * Creates the fallback vote based on the default vote setting.
     * <p>
     * Used when all policies are NOT_APPLICABLE (clean exhaustion, no errors).
     *
     * @param contributingVotes the policy votes that contributed to this result
     * @param voterMetadata the policy set voterMetadata
     * @param defaultDecision the configured default vote
     * @return the fallback policy set vote
     */
    public static Vote getFallbackVote(List<Vote> contributingVotes, VoterMetadata voterMetadata,
            DefaultDecision defaultDecision) {
        return switch (defaultDecision) {
        case ABSTAIN -> Vote.abstain(voterMetadata, contributingVotes);
        case DENY    -> Vote.combinedVote(AuthorizationDecision.DENY, voterMetadata, contributingVotes, Outcome.DENY);
        case PERMIT  ->
            Vote.combinedVote(AuthorizationDecision.PERMIT, voterMetadata, contributingVotes, Outcome.PERMIT);
        };
    }

    /**
     * Decision maker for pure applicability check with non-streaming vote.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param voter the underlying vote maker
     * @param voterMetadata the policy set voterMetadata
     */
    record ApplicabilityCheckingPurePolicySet(PureOperator isApplicable, Voter voter, VoterMetadata voterMetadata)
            implements PureVoter {

        @Override
        public Vote vote(List<AttributeRecord> knownContributions, EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return Vote.error(error, voterMetadata);
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (voter) {
                case Vote pd             -> pd;
                case PureVoter pdm       -> pdm.vote(knownContributions, ctx);
                case StreamVoter ignored -> Vote.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT), voterMetadata);
                };
            }
            return Vote.abstain(voterMetadata);
        }
    }

    /**
     * Decision maker for streaming applicability check.
     *
     * @param isApplicable the stream operator for applicability evaluation
     * @param voter the underlying vote maker
     * @param voterMetadata the policy set voterMetadata
     */
    record ApplicabilityCheckingStreamPolicySet(StreamOperator isApplicable, Voter voter, VoterMetadata voterMetadata)
            implements StreamVoter {

        @Override
        public Flux<Vote> vote(List<AttributeRecord> knownContributions) {
            return isApplicable.stream().switchMap(tracedApplicability -> {
                val applicabilityValue = tracedApplicability.value();
                if (applicabilityValue instanceof ErrorValue error) {
                    return Flux.just(Vote.error(error, voterMetadata));
                }
                if (applicabilityValue instanceof BooleanValue(var b) && b) {
                    return switch (voter) {
                    case Vote pd         -> Flux.just(pd);
                    case PureVoter pdm   -> Flux.deferContextual(
                            ctxView -> Flux.just(pdm.vote(knownContributions, ctxView.get(EvaluationContext.class))));
                    case StreamVoter sdm -> sdm.vote(knownContributions);
                    };
                }
                return Flux.just(Vote.abstain(voterMetadata, List.of()));
            });
        }
    }

    /**
     * Voter for pure applicability check with streaming vote.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param streamVoter the streaming vote maker
     * @param voterMetadata the policy set voterMetadata
     */
    record PureApplicabilityStreamPolicySet(
            PureOperator isApplicable,
            StreamVoter streamVoter,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public Flux<Vote> vote(List<AttributeRecord> knownContributions) {
            return Flux.deferContextual(ctxView -> {
                val ctx                 = ctxView.get(EvaluationContext.class);
                val applicabilityResult = isApplicable.evaluate(ctx);
                if (applicabilityResult instanceof ErrorValue error) {
                    return Flux.just(Vote.error(error, voterMetadata));
                }
                if (applicabilityResult instanceof BooleanValue(var b) && b) {
                    return streamVoter.vote(knownContributions);
                }
                return Flux.just(Vote.abstain(voterMetadata));
            });
        }
    }
}
