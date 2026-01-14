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

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.CompiledPolicy;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policy.PolicyMetadata;
import io.sapl.compiler.policy.PurePolicyBody;
import io.sapl.compiler.policy.StreamPolicyBody;
import io.sapl.compiler.policyset.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@UtilityClass
public class FirstApplicableCompiler {

    private static final String ERROR_TARGET_NOT_BOOLEAN = "Target was not Boolean. Should not happen. Indicates implementation bug.";
    private static final String ERROR_TARGET_WAS_STREAM  = "Target was Stream. Should not happen. Indicates implementation bug.";

    public static CompiledPolicySet compilePolicySet(PolicySet policySet, CompiledExpression targetExpression,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies) {

        val coverageStream = compilePolicySetCoverageStream(policySet, policySetMetadata, policies);

        val maybeShortCircuitBody = shortCircuitIfPredetermined(policySetMetadata, policies);
        if (maybeShortCircuitBody.isPresent()) {
            return new CompiledPolicySet(targetExpression, maybeShortCircuitBody.get(), coverageStream);
        }

        val maybePureBody = pureBodyIfPoliciesPure(targetExpression, policySetMetadata, policies, policySet);
        return maybePureBody
                .map(policySetBody -> new CompiledPolicySet(targetExpression, policySetBody, coverageStream))
                .orElseGet(() -> new CompiledPolicySet(targetExpression,
                        streamBody(targetExpression, policySetMetadata, policies, policySet), coverageStream));
    }

    private Value evaluateTarget(CompiledExpression currentPolicyTargetExpression, EvaluationContext ctx) {
        return switch (currentPolicyTargetExpression) {
        case Value matchValue                  -> matchValue;
        case PureOperator pureTargetExpression -> pureTargetExpression.evaluate(ctx);
        case StreamOperator ignored            -> Value.error(ERROR_TARGET_WAS_STREAM);
        };
    }

    public static Flux<PolicySetDecisionWithCoverage> compilePolicySetCoverageStream(PolicySet policySet,
            final PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies) {
        Function<Coverage.PolicySetCoverage, Flux<PolicySetDecisionWithCoverage>> bodyStreamMap = coverage -> Flux
                .just(new PolicySetDecisionWithCoverage(PolicySetDecision.notApplicable(policySetMetadata, List.of()),
                        coverage));
        for (var i = policies.size() - 1; i >= 0; i--) {
            val currentPolicy                 = policies.get(i);
            val currentPolicyInAst            = policySet.policies().get(i);
            val policyMetadata                = currentPolicyInAst.metadata();
            val nextNextPoliciesBodyStreamMap = bodyStreamMap; // Final lift for Lambda
            bodyStreamMap = previousAggregatedCoverage -> Flux.deferContextual(ctxView -> {
                val                           evalCtx          = ctxView.get(EvaluationContext.class);
                Coverage.TargetHit            targetHit        = new Coverage.BlankTargetHit();
                var                           currentMatch     = switch (currentPolicy.targetExpression()) {
                                                               case Value matchValue                  -> matchValue;
                                                               case PureOperator pureTargetExpression -> {
                                                                   val match                 = pureTargetExpression
                                                                           .evaluate(evalCtx);
                                                                   val currentTargetLocation = currentPolicyInAst
                                                                           .target().location();
                                                                   targetHit = new Coverage.TargetResult(match,
                                                                           currentTargetLocation);
                                                                   yield match;
                                                               }
                                                               case StreamOperator ignored            ->
                                                                   Value.error(ERROR_TARGET_WAS_STREAM);
                                                               };
                final Coverage.PolicyCoverage policyCoverage   = new Coverage.PolicyCoverage(
                        currentPolicyInAst.metadata(), targetHit, null);
                val                           currentTargetHit = targetHit; // Final lift for Lambda
                return switch (currentMatch) {
                // Target FALSE -> evaluate next policy in order
                case BooleanValue(var b) when !b -> {
                    val newCoverage = previousAggregatedCoverage.with(policyCoverage);
                    yield nextNextPoliciesBodyStreamMap.apply(newCoverage);
                }
                case BooleanValue trueIgnored    ->
                    currentPolicy.coverageStream().switchMap(currentPoliciesDecisionWithCoverage -> {
                                                     val currentPolicyDecision     = currentPoliciesDecisionWithCoverage
                                                             .decision();
                                                     val decision                  = currentPolicyDecision.decision();
                                                     val policyCoverageWithHit     = currentPoliciesDecisionWithCoverage
                                                             .coverage().withHit(currentTargetHit);
                                                     val currentAggregatedCoverage = previousAggregatedCoverage
                                                             .with(policyCoverageWithHit);
                                                     if (decision == Decision.NOT_APPLICABLE) {
                                                         return nextNextPoliciesBodyStreamMap
                                                                 .apply(currentAggregatedCoverage);
                                                     }
                                                     val setDecision = new PolicySetDecision(
                                                             currentPolicyDecision.decision(),
                                                             currentPolicyDecision.obligations(),
                                                             currentPolicyDecision.advice(),
                                                             currentPolicyDecision.resource(), null, policySetMetadata,
                                                             List.of(currentPolicyDecision));
                                                     return Flux.just(new PolicySetDecisionWithCoverage(setDecision,
                                                             currentAggregatedCoverage));
                                                 });
                case ErrorValue error            -> {
                    val policyDecision = PolicyDecision.error(error, policyMetadata);
                    val newCoverage    = previousAggregatedCoverage.with(policyCoverage);
                    val newDecision    = PolicySetDecision.error(error, policySetMetadata, List.of(policyDecision));
                    yield Flux.just(new PolicySetDecisionWithCoverage(newDecision, newCoverage));
                }
                default                          -> {
                    val error          = Value.error(ERROR_TARGET_NOT_BOOLEAN);
                    val policyDecision = PolicyDecision.error(error, policyMetadata);
                    val newCoverage    = previousAggregatedCoverage.with(policyCoverage);
                    val newDecision    = PolicySetDecision.error(error, policySetMetadata, List.of(policyDecision));
                    yield Flux.just(new PolicySetDecisionWithCoverage(newDecision, newCoverage));
                }
                };
            });
        }
        val emptyCoverage = new Coverage.PolicySetCoverage(policySetMetadata, null, List.of());
        return bodyStreamMap.apply(emptyCoverage);
    }

    private static Optional<PolicySetBody> pureBodyIfPoliciesPure(CompiledExpression targetExpression,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies, PolicySet policySet) {
        if (policies.stream().anyMatch(p -> p.policyBody() instanceof StreamPolicyBody)) {
            return Optional.empty();
        }
        return Optional
                .of(new PureFirstApplicablePolicySetBody(policySetMetadata, targetExpression, policies, policySet));
    }

    record PureFirstApplicablePolicySetBody(
            PolicySetMetadata policySetMetadata,
            CompiledExpression targetExpression,
            List<CompiledPolicy> policies,
            PolicySet policySet) implements PurePolicySetBody {
        @Override
        public PolicySetDecision evaluateBody(EvaluationContext ctx) {
            for (var i = 0; i < policies.size(); i++) {
                val policy         = policies.get(i);
                val policyInAst    = policySet.policies().get(i);
                val policyMetadata = policyInAst.metadata();
                val match          = evaluateTarget(policy.targetExpression(), ctx);
                if (match instanceof ErrorValue error) {
                    val policyDecision = PolicyDecision.error(error, policyMetadata);
                    return PolicySetDecision.error(error, policySetMetadata, List.of(policyDecision));
                } else if (match instanceof BooleanValue(var matches) && matches) {
                    val decision = switch (policy.policyBody()) {
                    case PolicyDecision policyDecision -> policyDecision;
                    case PurePolicyBody purePolicyBody -> purePolicyBody.evaluateBody(ctx);
                    default                            -> null;
                    };
                    if (decision != null && decision.decision() != Decision.NOT_APPLICABLE) {
                        return new PolicySetDecision(decision.decision(), decision.obligations(), decision.advice(),
                                decision.resource(), null, policySetMetadata, List.of(decision));
                    }
                }
            }
            return PolicySetDecision.notApplicable(policySetMetadata, List.of());
        }
    }

    private static PolicySetBody streamBody(CompiledExpression targetExpression, PolicySetMetadata policySetMetadata,
            List<CompiledPolicy> policies, PolicySet policySet) {
        var bodyStream = Flux.just(PolicySetDecision.notApplicable(policySetMetadata, List.of()));
        for (var i = policies.size() - 1; i >= 0; i--) {
            val policy                  = policies.get(i);
            val policyMetadata          = policySet.policies().get(i).metadata();
            val nextBodyStream          = policy.policyBody().toStream();
            val finalPreviousBodyStream = bodyStream;
            bodyStream = Flux.deferContextual(ctxView -> {
                val evalCtx = ctxView.get(EvaluationContext.class);
                var match   = evaluateTarget(policy.targetExpression(), evalCtx);
                return switch (match) {
                case BooleanValue(var b) when !b -> finalPreviousBodyStream;
                case BooleanValue trueIgnored    -> nextBodyStream.switchMap(decision -> {
                                                 if (decision.decision() == Decision.NOT_APPLICABLE) {
                                                     return finalPreviousBodyStream;
                                                 }
                                                 return Flux.just(new PolicySetDecision(decision.decision(),
                                                         decision.obligations(), decision.advice(), decision.resource(),
                                                         null, policySetMetadata, List.of(decision)));
                                             });
                case ErrorValue error            -> {
                    val policyDecision = PolicyDecision.error(error, policyMetadata);
                    yield Flux.just(PolicySetDecision.error(error, policySetMetadata, List.of(policyDecision)));
                }
                default                          -> {
                    val error          = Value.error(ERROR_TARGET_NOT_BOOLEAN);
                    val policyDecision = PolicyDecision.error(error, policyMetadata);
                    yield Flux.just(PolicySetDecision.error(error, policySetMetadata, List.of(policyDecision)));
                }
                };
            });
        }
        return new StreamPolicySetBody(targetExpression, bodyStream);
    }

    private static Optional<PolicySetBody> shortCircuitIfPredetermined(PolicySetMetadata policySetMetadata,
            List<CompiledPolicy> policies) {
        for (CompiledPolicy policy : policies) {
            val policyTarget = policy.targetExpression();
            val policyBody   = policy.policyBody();
            switch (policyTarget) {
            case ErrorValue error                                                            -> {
                return Optional.of(PolicySetDecision.error(error, policySetMetadata, List.of()));
            }
            case BooleanValue(var t) when t && policyBody instanceof PolicyDecision decision -> {
                // DO NOT MERGE WITH GUARD! That would completely change the logic! This is the
                // simple version already.
                if (decision.decision() != Decision.NOT_APPLICABLE) {
                    return Optional.of(new PolicySetDecision(decision.decision(), decision.obligations(),
                            decision.advice(), decision.resource(), null, policySetMetadata, List.of(decision)));
                }
            }
            default                                                                          -> {
                return Optional.empty();
            }
            }
        }
        return Optional.empty();
    }

}
