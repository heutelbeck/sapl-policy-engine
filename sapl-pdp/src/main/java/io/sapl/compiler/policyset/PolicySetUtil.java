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

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.Outcome;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.policy.CompiledPolicy;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.Map;

/**
 * Shared utilities for policy set compilation.
 * <p>
 * Provides common building blocks used by combining algorithm compilers:
 * <ul>
 * <li><b>Applicability chaining:</b> Wraps vote makers with target
 * expression evaluation.</li>
 * <li><b>Vote maker lifting:</b> Evaluates vote makers in pure context.</li>
 * <li><b>Fallback construction:</b> Builds fallback votes from the
 * default-decision setting when no policies were applicable.</li>
 * </ul>
 */
@UtilityClass
public class PolicySetUtil {

    public static final String ERROR_STREAM_IN_PURE_CONTEXT        = "Stream vote maker in pure context. Indicates implementation bug.";
    public static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE = "Unexpected isApplicable type. Indicates implementation bug.";
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
        default                                                    ->
            Vote.error(new ErrorValue(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), voterMetadata);
        };
    }

    /**
     * Evaluates a policy in pure context, expecting non-streaming vote maker.
     *
     * @param policy the compiled policy to evaluate
     * @param ctx the evaluation context
     * @param location source location for error reporting
     * @return the policy vote
     */
    public static Vote evaluatePure(CompiledPolicy policy, EvaluationContext ctx, SourceLocation location) {
        return switch (policy.applicabilityAndVote()) {
        case Vote d              -> d;
        case PureVoter p         -> p.vote(ctx);
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
        public Vote vote(EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return Vote.error(error, voterMetadata);
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (voter) {
                case Vote pd             -> pd;
                case PureVoter pdm       -> pdm.vote(ctx);
                case StreamVoter ignored -> Vote.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT), voterMetadata);
                };
            }
            return Vote.abstain(voterMetadata);
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
        public VoteResult evaluate(EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return new VoteResult(Vote.error(error, voterMetadata), Map.of());
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return streamVoter.evaluate(ctx);
            }
            return new VoteResult(Vote.abstain(voterMetadata), Map.of());
        }
    }
}
