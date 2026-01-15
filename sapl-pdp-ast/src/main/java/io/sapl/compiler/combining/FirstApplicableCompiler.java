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

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policy.SchemaValidatorCompiler;
import io.sapl.compiler.policyset.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class FirstApplicableCompiler {

    public static final String ERROR_NO_POLICIES_WERE_FOUND_IN_THE_POLICY_SET = "No remainingPolicies were found in the policySet";
    public static final String ERROR_STREAM_IN_PURE_CONTEXT                   = "Stream decision maker in pure context. This indicates an implementation bug in the combining algorithm.";

    public static CompiledPolicySet compilePolicySet(PolicySet policySet, CompilationContext ctx) {
        val metadata         = policySet.metadata();
        val location         = policySet.location();
        val compiledPolicies = policySet.policies().stream().map(policy -> PolicyCompiler.compilePolicy(policy, ctx))
                .toList();
        if (compiledPolicies.isEmpty()) {
            throw new SaplCompilerException(ERROR_NO_POLICIES_WERE_FOUND_IN_THE_POLICY_SET, location);
        }
        val decisionOnly             = compileDecisionMaker(compiledPolicies, metadata, location);
        val schemaValidator          = SchemaValidatorCompiler.compileValidator(policySet.match(), ctx);
        val isApplicable             = TargetExpressionCompiler.compileTargetExpression(policySet.target(),
                schemaValidator, ctx);
        val applicabilityAndDecision = ApplicabilityChainCompiler.compileApplicabilityAndDecision(isApplicable,
                decisionOnly, metadata);
        val coverage                 = compileCoverageStream(compiledPolicies, metadata);
        return new CompiledPolicySet(isApplicable, decisionOnly, applicabilityAndDecision, coverage, metadata);
    }

    private static Flux<PolicySetDecisionWithCoverage> compileCoverageStream(List<CompiledPolicy> policies,
            PolicySetMetadata metadata) {
        return evaluatePoliciesForCoverage(policies, 0,
                new Coverage.PolicySetCoverage(metadata, Coverage.NO_TARGET_HIT, List.of()), new ArrayList<>(),
                metadata);
    }

    private static Flux<PolicySetDecisionWithCoverage> evaluatePoliciesForCoverage(List<CompiledPolicy> policies,
            int index, Coverage.PolicySetCoverage accumulatedCoverage, List<PolicyDecision> contributingDecisions,
            PolicySetMetadata metadata) {

        if (index >= policies.size()) {
            val decision = PolicySetDecision.notApplicable(metadata, contributingDecisions);
            return Flux.just(new PolicySetDecisionWithCoverage(decision, accumulatedCoverage));
        }

        return policies.get(index).coverage().switchMap(policyResult -> {
            val decision        = policyResult.decision();
            val policyCoverage  = policyResult.coverage();
            val newCoverage     = accumulatedCoverage.with(policyCoverage);
            val newContributing = new ArrayList<>(contributingDecisions);
            newContributing.add(decision);

            if (decision.authorizationDecision().decision() == Decision.NOT_APPLICABLE) {
                return evaluatePoliciesForCoverage(policies, index + 1, newCoverage, newContributing, metadata);
            }

            val setDecision = PolicySetDecision.decision(decision.authorizationDecision(), newContributing, metadata);
            return Flux.just(new PolicySetDecisionWithCoverage(setDecision, newCoverage));
        });
    }

    private static DecisionMaker compileDecisionMaker(List<CompiledPolicy> policies, PolicySetMetadata metadata,
            SourceLocation location) {
        // 1. Short-circuit: collect static decisions, return first non-NOT_APPLICABLE
        val contributingDecisions = new ArrayList<PolicyDecision>();
        int firstNonStatic        = 0;
        for (var policy : policies) {
            if (!(policy.applicabilityAndDecision() instanceof PolicyDecision decision)) {
                break; // non-static, stop short-circuit scan
            }
            contributingDecisions.add(decision);
            if (decision.authorizationDecision().decision() != Decision.NOT_APPLICABLE) {
                return PolicySetDecision.decision(decision.authorizationDecision(), contributingDecisions, metadata);
            }
            firstNonStatic++;
        }

        // All remainingPolicies were static NOT_APPLICABLE
        if (firstNonStatic == policies.size()) {
            return PolicySetDecision.notApplicable(metadata, contributingDecisions);
        }

        // Trim leading static NOT_APPLICABLE for remaining evaluation
        val remainingPolicies = policies.subList(firstNonStatic, policies.size());

        // 2. Check if all remaining remainingPolicies are pure/static (no streams)
        val allPure = remainingPolicies.stream().map(CompiledPolicy::applicabilityAndDecision)
                .noneMatch(StreamDecisionMaker.class::isInstance);
        if (allPure) {
            return new FirstApplicablePurePolicySet(contributingDecisions, remainingPolicies, metadata, location);
        }

        // 3. Stream fallback - build reverse chain for lazy evaluation
        return new FirstApplicableStreamPolicySet(
                buildReverseChain(remainingPolicies, contributingDecisions, metadata));
    }

    private static Flux<PDPDecision> buildReverseChain(List<CompiledPolicy> policies,
            List<PolicyDecision> staticDecisions, PolicySetMetadata metadata) {
        Flux<PDPDecision> decisionChain = Flux.just(PolicySetDecision.notApplicable(metadata, staticDecisions));
        for (int i = policies.size() - 1; i >= 0; i--) {
            val policy            = policies.get(i);
            val decisionChainTail = decisionChain;
            decisionChain = toStream(policy.applicabilityAndDecision(), List.of()).switchMap(currentDecision -> {
                val contributingDecisions = new ArrayList<>(staticDecisions);
                contributingDecisions.add(currentDecision);
                if (currentDecision.authorizationDecision().decision() == Decision.NOT_APPLICABLE) {
                    return decisionChainTail
                            .map(decisionAtTail -> ((PolicySetDecision) decisionAtTail).with(currentDecision));
                }
                return Flux.just(PolicySetDecision.decision(currentDecision.authorizationDecision(),
                        contributingDecisions, metadata));
            });
        }
        return decisionChain;
    }

    private static PolicyDecision evaluatePure(CompiledPolicy policy, List<AttributeRecord> priorAttributes,
            EvaluationContext ctx, SourceLocation location) {
        return switch (policy.applicabilityAndDecision()) {
        case PDPDecision d               -> (PolicyDecision) d;
        case PureDecisionMaker p         -> (PolicyDecision) p.decide(priorAttributes, ctx);
        case StreamDecisionMaker ignored ->
            PolicyDecision.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT, null, location), policy.metadata());
        };
    }

    private static Flux<PolicyDecision> toStream(DecisionMaker dm, List<AttributeRecord> priorAttributes) {
        return switch (dm) {
        case PDPDecision d         -> Flux.just((PolicyDecision) d);
        case PureDecisionMaker p   -> Flux.deferContextual(ctxView -> {
                                   val ctx = ctxView.get(EvaluationContext.class);
                                   return Flux.just((PolicyDecision) p.decide(priorAttributes, ctx));
                               });
        case StreamDecisionMaker s -> s.decide(priorAttributes).map(d -> (PolicyDecision) d);
        };
    }

    record FirstApplicablePurePolicySet(
            List<PolicyDecision> staticDecisions,
            List<CompiledPolicy> policies,
            PolicySetMetadata metadata,
            SourceLocation location) implements PureDecisionMaker {

        @Override
        public PDPDecision decide(List<AttributeRecord> priorAttributes, EvaluationContext ctx) {
            val allDecisions = new ArrayList<>(staticDecisions);
            for (var policy : policies) {
                val decision = evaluatePure(policy, priorAttributes, ctx, location);
                allDecisions.add(decision);
                if (decision.authorizationDecision().decision() != Decision.NOT_APPLICABLE) {
                    return PolicySetDecision.decision(decision.authorizationDecision(), allDecisions, metadata);
                }
            }
            return PolicySetDecision.notApplicable(metadata, allDecisions);
        }
    }

    record FirstApplicableStreamPolicySet(Flux<PDPDecision> chain) implements StreamDecisionMaker {
        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return chain.map(decision -> ((PolicySetDecision) decision).with(knownContributions));
        }
    }
}
