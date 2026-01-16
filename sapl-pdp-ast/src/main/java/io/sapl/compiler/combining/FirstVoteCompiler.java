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
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SourceLocation;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policyset.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;

/**
 * Compiles policy sets using the first-vote combining algorithm.
 * <p>
 * The first-vote algorithm evaluates policies in order until one returns
 * a decision other than NOT_APPLICABLE. That decision becomes the final result.
 * If all policies return NOT_APPLICABLE, the set returns NOT_APPLICABLE.
 * <p>
 * The compiler produces three evaluation strata based on policy complexity:
 * <ul>
 * <li><b>Static short-circuit:</b> Leading policies with constant decisions are
 * evaluated at compile time. Returns immediately if any yields
 * non-NOT_APPLICABLE.</li>
 * <li><b>Pure evaluation:</b> When remaining policies require only subscription
 * data (no streaming attributes), produces a synchronous decision maker.</li>
 * <li><b>Stream evaluation:</b> When any policy requires streaming attributes,
 * builds a reverse-chained flux for lazy reactive evaluation.</li>
 * </ul>
 */
@UtilityClass
public class FirstVoteCompiler {

    public static DecisionMakerAndCoverage compilePolicySet(PolicySet policySet, List<CompiledPolicy> compiledPolicies,
            CompiledExpression isApplicable, PolicySetMetadata metadata) {
        val decisionMaker = compileDecisionMaker(compiledPolicies, metadata, policySet.location());
        val coverage      = compileCoverageStream(policySet, isApplicable, compiledPolicies, metadata);
        return new DecisionMakerAndCoverage(decisionMaker, coverage);
    }

    /**
     * Compiles the coverage stream for the policy set.
     * Delegates target evaluation to {@link PolicySetUtil} and provides a
     * first-vote body factory.
     */
    private static Flux<PolicySetDecisionWithCoverage> compileCoverageStream(PolicySet policySet,
            CompiledExpression isApplicable, List<CompiledPolicy> compiledPolicies, PolicySetMetadata metadata) {
        val bodyFactory = bodyCoverageFactory(compiledPolicies, metadata);
        return PolicySetUtil.compileCoverageStream(policySet, isApplicable, bodyFactory);
    }

    /**
     * Creates a factory for body coverage evaluation using first-vote
     * semantics.
     */
    private Function<Coverage.TargetHit, Flux<PolicySetDecisionWithCoverage>> bodyCoverageFactory(
            List<CompiledPolicy> policies, PolicySetMetadata metadata) {
        return targetHit -> evaluatePoliciesForCoverage(policies, 0,
                new Coverage.PolicySetCoverage(metadata, targetHit, List.of()), new ArrayList<>(), metadata);
    }

    /**
     * Recursively evaluates policies for coverage, accumulating results.
     * Stops at first non-NOT_APPLICABLE decision (first-vote semantics).
     */
    private static Flux<PolicySetDecisionWithCoverage> evaluatePoliciesForCoverage(List<CompiledPolicy> policies,
            int index, Coverage.PolicySetCoverage accumulatedCoverage, List<PolicyDecision> contributingDecisions,
            PolicySetMetadata metadata) {

        if (index >= policies.size()) {
            val decision = PolicySetDecision.notApplicable(metadata, contributingDecisions);
            return Flux.just(new PolicySetDecisionWithCoverage(decision, accumulatedCoverage));
        }

        return policies.get(index).coverage().switchMap(policyResult -> {
            val policyDecision  = policyResult.decision();
            val policyCoverage  = policyResult.coverage();
            val newCoverage     = accumulatedCoverage.with(policyCoverage);
            val newContributing = new ArrayList<>(contributingDecisions);
            newContributing.add(policyDecision);

            if (policyDecision.decision() == NOT_APPLICABLE) {
                return evaluatePoliciesForCoverage(policies, index + 1, newCoverage, newContributing, metadata);
            }

            val setDecision = PolicySetDecision.decision(policyDecision.authorizationDecision(), newContributing,
                    metadata);
            return Flux.just(new PolicySetDecisionWithCoverage(setDecision, newCoverage));
        });
    }

    /**
     * Compiles policies into a decision maker using stratified evaluation.
     * <p>
     * Applies three optimization levels:
     * <ol>
     * <li>Static short-circuit for leading constant policies</li>
     * <li>Pure decision maker when all remaining policies are non-streaming</li>
     * <li>Stream decision maker with reverse-chained flux otherwise</li>
     * </ol>
     */
    private static DecisionMaker compileDecisionMaker(List<CompiledPolicy> policies, PolicySetMetadata metadata,
            SourceLocation location) {
        // 1. Short-circuit: collect static decisions, return first non-NOT_APPLICABLE
        val contributingDecisions = new ArrayList<PolicyDecision>();
        int firstNonStatic        = 0;
        for (var policy : policies) {
            if (!(policy.applicabilityAndDecision() instanceof PolicyDecision policyDecision)) {
                break; // non-static, stop short-circuit scan
            }
            contributingDecisions.add(policyDecision);
            if (policyDecision.decision() != NOT_APPLICABLE) {
                return PolicySetDecision.decision(policyDecision.authorizationDecision(), contributingDecisions,
                        metadata);
            }
            firstNonStatic++;
        }

        // All policies were static NOT_APPLICABLE
        if (firstNonStatic == policies.size()) {
            return PolicySetDecision.notApplicable(metadata, contributingDecisions);
        }

        // Trim leading static NOT_APPLICABLE for remaining evaluation
        val remainingPolicies = policies.subList(firstNonStatic, policies.size());

        // 2. Check if all remaining policies are pure/static (no streams)
        val allPure = remainingPolicies.stream().map(CompiledPolicy::applicabilityAndDecision)
                .noneMatch(StreamDecisionMaker.class::isInstance);
        if (allPure) {
            return new FirstVotePurePolicySet(contributingDecisions, remainingPolicies, metadata, location);
        }

        // 3. Stream fallback - build reverse chain for lazy evaluation
        return new FirstVoteStreamPolicySet(buildReverseChain(remainingPolicies, contributingDecisions, metadata));
    }

    /**
     * Builds a reverse-chained flux for lazy streaming evaluation.
     * <p>
     * Constructs the chain from last to first policy. Each policy's flux
     * switches to the tail chain on NOT_APPLICABLE, enabling lazy evaluation
     * that stops at the first applicable policy.
     */
    private static Flux<PDPDecision> buildReverseChain(List<CompiledPolicy> policies,
            List<PolicyDecision> contributingDecisions, PolicySetMetadata metadata) {
        Flux<PDPDecision> decisionChain = Flux.just(PolicySetDecision.notApplicable(metadata, contributingDecisions));
        for (int i = policies.size() - 1; i >= 0; i--) {
            val policy            = policies.get(i);
            val decisionChainTail = decisionChain;
            decisionChain = PolicySetUtil.toStream(policy.applicabilityAndDecision(), List.of())
                    .switchMap(currentDecision -> {
                        if (currentDecision.decision() == NOT_APPLICABLE) {
                            return decisionChainTail
                                    .map(decisionAtTail -> ((PolicySetDecision) decisionAtTail).with(currentDecision));
                        }
                        val allDecisions = new ArrayList<>(contributingDecisions);
                        allDecisions.add(currentDecision);
                        return Flux.just(PolicySetDecision.decision(currentDecision.authorizationDecision(),
                                allDecisions, metadata));
                    });
        }
        return decisionChain;
    }

    /**
     * Pure decision maker for first-vote evaluation without streaming
     * policies.
     * <p>
     * Evaluates policies sequentially at runtime, returning the first
     * non-NOT_APPLICABLE decision.
     *
     * @param contributingDecisions leading static NOT_APPLICABLE decisions
     * @param policies remaining policies requiring runtime evaluation
     * @param metadata the policy set metadata
     * @param location source location for error reporting
     */
    record FirstVotePurePolicySet(
            List<PolicyDecision> contributingDecisions,
            List<CompiledPolicy> policies,
            PolicySetMetadata metadata,
            SourceLocation location) implements PureDecisionMaker {

        @Override
        public PDPDecision decide(List<AttributeRecord> priorAttributes, EvaluationContext ctx) {
            val allDecisions = new ArrayList<>(contributingDecisions);
            for (var policy : policies) {
                val policyDecision = PolicySetUtil.evaluatePure(policy, priorAttributes, ctx, location);
                allDecisions.add(policyDecision);
                if (policyDecision.decision() != NOT_APPLICABLE) {
                    return PolicySetDecision.decision(policyDecision.authorizationDecision(), allDecisions, metadata);
                }
            }
            return PolicySetDecision.notApplicable(metadata, allDecisions);
        }
    }

    /**
     * Stream decision maker wrapping a pre-built reverse chain.
     * <p>
     * The chain is constructed at compile time; this record provides the
     * {@link StreamDecisionMaker} interface and merges known attribute
     * contributions into the result.
     *
     * @param chain the pre-built reverse-chained flux
     */
    record FirstVoteStreamPolicySet(Flux<PDPDecision> chain) implements StreamDecisionMaker {
        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return chain.map(decision -> ((PolicySetDecision) decision).with(knownContributions));
        }
    }
}
