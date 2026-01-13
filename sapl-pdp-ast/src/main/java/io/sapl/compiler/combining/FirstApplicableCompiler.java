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
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.pdp.CompiledPolicy;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policy.PurePolicyBody;
import io.sapl.compiler.policy.StreamPolicyBody;
import io.sapl.compiler.policyset.PolicySetBody;
import io.sapl.compiler.policyset.PolicySetDecision;
import io.sapl.compiler.policyset.PolicySetDecisionWithCoverage;
import io.sapl.compiler.policyset.PolicySetMetadata;
import io.sapl.compiler.policyset.PurePolicySetBody;
import io.sapl.compiler.policyset.StreamPolicySetBody;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@UtilityClass
public class FirstApplicableCompiler {

    private static final String ERROR_TARGET_NOT_BOOLEAN = "Target was not Boolean. Should not happen. Indicates implementation bug.";
    private static final String ERROR_TARGET_WAS_STREAM  = "Target was Stream. Should not happen. Indicates implementation bug.";

    public static CompiledPolicySet compilePolicySet(PolicySet policySet, CompiledExpression targetExpression,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies, CompilationContext ctx) {

        val coverageStream = compilePolicySetCoverageStream(policySet, targetExpression, policySetMetadata, policies,
                ctx);

        val maybeShortCircuitBody = shortCircuitIfPredetermined(policySetMetadata, policies);
        if (maybeShortCircuitBody.isPresent()) {
            return new CompiledPolicySet(targetExpression, maybeShortCircuitBody.get(), coverageStream);
        }

        val maybePureBody = pureBodyIfPoliciesPure(targetExpression, policySetMetadata, policies);
        return maybePureBody
                .map(policySetBody -> new CompiledPolicySet(targetExpression, policySetBody, coverageStream))
                .orElseGet(() -> new CompiledPolicySet(targetExpression,
                        streamBody(targetExpression, policySetMetadata, policies), coverageStream));
    }

    public static Flux<PolicySetDecisionWithCoverage> compilePolicySetCoverageStream(PolicySet policySet,
            CompiledExpression targetExpression, PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies,
            CompilationContext ctx) {
        // TODO: Implement
        return Flux.empty();
    }

    private static Optional<PolicySetBody> pureBodyIfPoliciesPure(CompiledExpression targetExpression,
            PolicySetMetadata policySetMetadata, List<CompiledPolicy> policies) {
        for (CompiledPolicy policy : policies) {
            if (policy.policyBody() instanceof StreamPolicyBody) {
                return Optional.empty();
            }
        }
        return Optional.of(new PureFirstApplicablePolicySetBody(policySetMetadata, targetExpression, policies));
    }

    record PureFirstApplicablePolicySetBody(
            PolicySetMetadata policySetMetadata,
            CompiledExpression targetExpression,
            List<CompiledPolicy> policies) implements PurePolicySetBody {
        @Override
        public PolicySetDecision evaluateBody(EvaluationContext ctx) {
            for (val policy : policies) {
                Value match = null;
                if (policy.targetExpression() instanceof Value matchValue) {
                    match = matchValue;
                } else if (policy.targetExpression() instanceof PureOperator pureTargetExpression) {
                    match = pureTargetExpression.evaluate(ctx);
                }
                if (match instanceof ErrorValue error) {
                    return PolicySetDecision.error(error, policySetMetadata, List.of());
                } else if (match instanceof BooleanValue(var matches) && matches) {
                    val            body     = policy.policyBody();
                    PolicyDecision decision = null;
                    if (body instanceof PolicyDecision policyDecision) {
                        decision = policyDecision;
                    } else if (body instanceof PurePolicyBody purePolicyBody) {
                        decision = purePolicyBody.evaluateBody(ctx);
                    }
                    if (decision != null && decision.decision() != Decision.NOT_APPLICABLE) {
                        return new PolicySetDecision(decision.decision(), decision.obligations(), decision.advice(),
                                decision.resource(), null, policySetMetadata, List.of());
                    }
                }
            }
            return PolicySetDecision.notApplicable(policySetMetadata, List.of());
        }
    }

    private static PolicySetBody streamBody(CompiledExpression targetExpression, PolicySetMetadata policySetMetadata,
            List<CompiledPolicy> policies) {
        var bodyStream = Flux.just(PolicySetDecision.notApplicable(policySetMetadata, List.of()));
        for (var i = policies.size() - 1; i >= 0; i--) {
            val policy                  = policies.get(i);
            val policyTargetExpression  = policy.targetExpression();
            val nextBodyStream          = policy.policyBody().toStream();
            val finalPreviousBodyStream = bodyStream;
            bodyStream = Flux.deferContextual(ctxView -> {
                val evalCtx = ctxView.get(EvaluationContext.class);
                var match   = switch (policyTargetExpression) {
                            case Value matchValue                  -> matchValue;
                            case PureOperator pureTargetExpression -> pureTargetExpression.evaluate(evalCtx);
                            case StreamOperator ignored            -> Value.error(ERROR_TARGET_WAS_STREAM);
                            };
                return switch (match) {
                case BooleanValue(var b) when !b     -> finalPreviousBodyStream;
                case BooleanValue forSureTrueIgnored -> nextBodyStream.switchMap(decision -> {
                                                     if (decision.decision() == Decision.NOT_APPLICABLE) {
                                                         return finalPreviousBodyStream;
                                                     }
                                                     return Flux.just(new PolicySetDecision(decision.decision(),
                                                             decision.obligations(), decision.advice(),
                                                             decision.resource(), null, policySetMetadata,
                                                             List.of(decision)));
                                                 });
                case ErrorValue error                ->
                    Flux.just(PolicySetDecision.error(error, policySetMetadata, List.of()));
                default                              -> Flux.just(
                        PolicySetDecision.error(Value.error(ERROR_TARGET_NOT_BOOLEAN), policySetMetadata, List.of()));
                };
            });
        }
        return new StreamPolicySetBody(targetExpression, bodyStream);
    }

    private static Optional<PolicySetBody> shortCircuitIfPredetermined(PolicySetMetadata policySetMetadata,
            List<CompiledPolicy> policies) {
        // Detect if we have a short-circuit
        for (CompiledPolicy policy : policies) {
            val policyTarget = policy.targetExpression();
            val policyBody   = policy.policyBody();
            switch (policyTarget) {
            case ErrorValue error                                                            -> {
                return Optional.of(PolicySetDecision.error(error, policySetMetadata, List.of()));
            }
            case BooleanValue(var t) when t && policyBody instanceof PolicyDecision decision -> {
                return Optional.of(new PolicySetDecision(decision.decision(), decision.obligations(), decision.advice(),
                        decision.resource(), null, policySetMetadata, List.of(decision)));
            }
            default                                                                          -> {
                return Optional.empty();
            }
            }
        }
        return Optional.empty();
    }

}
