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

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policy.PolicyMetadata;
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
 * <li><b>Applicability chaining:</b> Wraps decision makers with target
 * expression evaluation.</li>
 * <li><b>Coverage stream building:</b> Chains target evaluation with body
 * coverage for testing.</li>
 * <li><b>Decision maker lifting:</b> Converts any decision maker type to
 * streams or evaluates in pure context.</li>
 * </ul>
 */
@UtilityClass
public class PolicySetUtil {

    public static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE = "Unexpected isApplicable type. Indicates implementation bug.";
    public static final String ERROR_STREAM_IN_PURE_CONTEXT        = "Stream decision maker in pure context. Indicates implementation bug.";
    public static final String ERROR_UNEXPECTED_STREAM_IN_TARGET   = "Unexpected Stream Operator in target expression. Indicates implementation bug.";

    /**
     * Wraps a decision maker with applicability checking based on the target
     * expression type.
     *
     * @param isApplicable the compiled target expression determining applicability
     * @param decisionMaker the decision maker from the combining algorithm
     * @param metadata the policy set metadata
     * @return a decision maker that combines applicability and decision evaluation
     */
    public static DecisionMaker compileApplicabilityAndDecision(CompiledExpression isApplicable,
            DecisionMaker decisionMaker, PolicySetMetadata metadata) {
        return switch (isApplicable) {
        case ErrorValue error                                                      ->
            PolicySetDecision.error(error, metadata, List.of());
        case BooleanValue(var b) when b                                            -> decisionMaker;
        case BooleanValue ignored                                                  ->
            PolicySetDecision.notApplicable(metadata, List.of());
        case PureOperator po when decisionMaker instanceof StreamDecisionMaker sdm ->
            new PureApplicabilityStreamPolicySet(po, sdm, metadata);
        case PureOperator po                                                       ->
            new ApplicabilityCheckingPurePolicySet(po, decisionMaker, metadata);
        case StreamOperator so                                                     ->
            new ApplicabilityCheckingStreamPolicySet(so, decisionMaker, metadata);
        default                                                                    ->
            PolicySetDecision.error(new ErrorValue(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), metadata, List.of());
        };
    }

    /**
     * Compiles a coverage stream by chaining target evaluation with body coverage.
     * <p>
     * Handles target expression types analogously to
     * {@link #compileApplicabilityAndDecision}: static values short-circuit,
     * pure operators defer evaluation, and stream operators in targets are errors.
     *
     * @param policySet the policy set being compiled
     * @param isApplicable the compiled target expression
     * @param bodyFactory factory producing coverage stream when target matches
     * @return flux emitting decisions with coverage information
     */
    public static Flux<PolicySetDecisionWithCoverage> compileCoverageStream(PolicySet policySet,
            CompiledExpression isApplicable,
            Function<Coverage.TargetHit, Flux<PolicySetDecisionWithCoverage>> bodyFactory) {
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
            var decision = PolicySetDecision.error(Value.error(ERROR_UNEXPECTED_STREAM_IN_TARGET), policySet.metadata(),
                    List.of());
            return Flux.just(new PolicySetDecisionWithCoverage(decision, coverage));
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
    private static Flux<PolicySetDecisionWithCoverage> coverageStreamFromMatch(PolicySet policySet,
            Function<Coverage.TargetHit, Flux<PolicySetDecisionWithCoverage>> bodyFactory, Value match,
            SourceLocation targetLocation) {
        var targetHit = new Coverage.TargetResult(match, targetLocation);
        if (Value.TRUE.equals(match)) {
            return bodyFactory.apply(targetHit);
        } else {
            var               coverage = new Coverage.PolicySetCoverage(policySet.metadata(), targetHit, List.of());
            PolicySetDecision decision;
            if (Value.FALSE.equals(match)) {
                decision = PolicySetDecision.notApplicable(policySet.metadata(), List.of());
            } else {
                decision = PolicySetDecision.error((ErrorValue) match, policySet.metadata(), List.of());
            }
            return Flux.just(new PolicySetDecisionWithCoverage(decision, coverage));
        }
    }

    /**
     * Lifts any decision maker type to a flux for uniform stream handling.
     *
     * @param dm the decision maker to lift
     * @param priorAttributes attributes from prior evaluation
     * @return flux emitting policy decisions
     */
    public static Flux<PolicyDecision> toStream(DecisionMaker dm, List<AttributeRecord> priorAttributes) {
        return switch (dm) {
        case PDPDecision d         -> Flux.just((PolicyDecision) d);
        case PureDecisionMaker p   -> Flux.deferContextual(ctxView -> {
                                   val ctx = ctxView.get(EvaluationContext.class);
                                   return Flux.just((PolicyDecision) p.decide(priorAttributes, ctx));
                               });
        case StreamDecisionMaker s -> s.decide(priorAttributes).map(d -> (PolicyDecision) d);
        };
    }

    /**
     * Evaluates a policy in pure context, expecting non-streaming decision maker.
     *
     * @param policy the compiled policy to evaluate
     * @param priorAttributes attributes from prior evaluation
     * @param ctx the evaluation context
     * @param location source location for error reporting
     * @return the policy decision
     */
    public static PolicyDecision evaluatePure(CompiledPolicy policy, List<AttributeRecord> priorAttributes,
            EvaluationContext ctx, SourceLocation location) {
        return switch (policy.applicabilityAndDecision()) {
        case PDPDecision d               -> (PolicyDecision) d;
        case PureDecisionMaker p         -> (PolicyDecision) p.decide(priorAttributes, ctx);
        case StreamDecisionMaker ignored ->
            PolicyDecision.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT, null, location), policy.metadata());
        };
    }

    /**
     * Creates the fallback decision based on the default decision setting.
     * <p>
     * Used when all policies are NOT_APPLICABLE (clean exhaustion, no errors).
     *
     * @param contributingDecisions the policy decisions that contributed to this
     * result
     * @param metadata the policy set metadata
     * @param defaultDecision the configured default decision
     * @return the fallback policy set decision
     */
    public static PolicySetDecision getFallbackDecision(List<PolicyDecision> contributingDecisions,
            PolicySetMetadata metadata, DefaultDecision defaultDecision) {
        return switch (defaultDecision) {
        case ABSTAIN -> PolicySetDecision.notApplicable(metadata, contributingDecisions);
        case DENY    -> PolicySetDecision.tracedDecision(AuthorizationDecision.DENY, metadata, contributingDecisions);
        case PERMIT  -> PolicySetDecision.tracedDecision(AuthorizationDecision.PERMIT, metadata, contributingDecisions);
        };
    }

    /**
     * Decision maker for pure applicability check with non-streaming decision.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy set metadata
     */
    record ApplicabilityCheckingPurePolicySet(
            PureOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicySetMetadata metadata) implements PureDecisionMaker {

        @Override
        public PDPDecision decide(List<AttributeRecord> knownContributions, EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return PolicySetDecision.error(error, metadata, List.of());
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (decisionMaker) {
                case PDPDecision pd              -> pd;
                case PureDecisionMaker pdm       -> pdm.decide(knownContributions, ctx);
                case StreamDecisionMaker ignored ->
                    PolicySetDecision.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT), metadata, List.of());
                };
            }
            return PolicySetDecision.notApplicable(metadata, List.of());
        }
    }

    /**
     * Decision maker for streaming applicability check.
     *
     * @param isApplicable the stream operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy set metadata
     */
    record ApplicabilityCheckingStreamPolicySet(
            StreamOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicySetMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return isApplicable.stream().switchMap(tracedApplicability -> {
                val applicabilityValue = tracedApplicability.value();
                if (applicabilityValue instanceof ErrorValue error) {
                    return Flux.just(PolicySetDecision.error(error, metadata, List.of()));
                }
                if (applicabilityValue instanceof BooleanValue(var b) && b) {
                    return switch (decisionMaker) {
                    case PDPDecision pd          -> Flux.just(pd);
                    case PureDecisionMaker pdm   -> Flux.deferContextual(
                            ctxView -> Flux.just(pdm.decide(knownContributions, ctxView.get(EvaluationContext.class))));
                    case StreamDecisionMaker sdm -> sdm.decide(knownContributions);
                    };
                }
                return Flux.just(PolicySetDecision.notApplicable(metadata, List.of()));
            });
        }
    }

    /**
     * Decision maker for pure applicability check with streaming decision.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param streamDecisionMaker the streaming decision maker
     * @param metadata the policy set metadata
     */
    record PureApplicabilityStreamPolicySet(
            PureOperator isApplicable,
            StreamDecisionMaker streamDecisionMaker,
            PolicySetMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return Flux.deferContextual(ctxView -> {
                val ctx                 = ctxView.get(EvaluationContext.class);
                val applicabilityResult = isApplicable.evaluate(ctx);
                if (applicabilityResult instanceof ErrorValue error) {
                    return Flux.just(PolicySetDecision.error(error, metadata, List.of()));
                }
                if (applicabilityResult instanceof BooleanValue(var b) && b) {
                    return streamDecisionMaker.decide(knownContributions);
                }
                return Flux.just(PolicySetDecision.notApplicable(metadata, List.of()));
            });
        }
    }
}
