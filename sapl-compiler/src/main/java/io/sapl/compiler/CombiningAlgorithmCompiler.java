/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class CombiningAlgorithmCompiler {

    // ========== Constants for Common Decision Objects ==========

    private static final Value NOT_APPLICABLE_DECISION = SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE,
            List.of(), List.of(), Value.UNDEFINED);

    private static final Value INDETERMINATE_DECISION = SaplCompiler.buildDecisionObject(Decision.INDETERMINATE,
            List.of(), List.of(), Value.UNDEFINED);

    private static final Value DENY_DECISION = SaplCompiler.buildDecisionObject(Decision.DENY, List.of(), List.of(),
            Value.UNDEFINED);

    private static final Value PERMIT_DECISION = SaplCompiler.buildDecisionObject(Decision.PERMIT, List.of(), List.of(),
            Value.UNDEFINED);

    // ========== Common Data Structures ==========

    /**
     * Holds the result of evaluating a single policy: decision, resource
     * transformation, obligations, and advice.
     */
    private record PolicyEvaluation(Decision decision, Value resource, ArrayValue obligations, ArrayValue advice) {
        static PolicyEvaluation notApplicable() {
            return new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED, new ArrayValue(List.of(), false),
                    new ArrayValue(List.of(), false));
        }
    }

    /**
     * Accumulator for combining multiple policy decisions according to algorithm
     * semantics.
     */
    private static class DecisionAccumulator {
        Decision    entitlement;
        Value       resource          = Value.UNDEFINED;
        boolean     hasResource       = false;
        List<Value> permitObligations = new ArrayList<>();
        List<Value> permitAdvice      = new ArrayList<>();
        List<Value> denyObligations   = new ArrayList<>();
        List<Value> denyAdvice        = new ArrayList<>();

        DecisionAccumulator(Decision defaultDecision) {
            this.entitlement = defaultDecision;
        }

        void addPolicyEvaluation(PolicyEvaluation eval, CombiningAlgorithmLogic logic) {
            // Update entitlement based on algorithm-specific logic
            entitlement = logic.combineDecisions(entitlement, eval.decision);

            // Handle resource transformations
            if (!(eval.resource instanceof UndefinedValue)) {
                if (hasResource) {
                    // Transformation uncertainty
                    entitlement = logic.handleTransformationUncertainty(entitlement);
                } else {
                    resource    = eval.resource;
                    hasResource = true;
                }
            }

            // Collect obligations and advice by decision type
            if (eval.decision == Decision.PERMIT) {
                permitObligations.addAll(eval.obligations);
                permitAdvice.addAll(eval.advice);
            } else if (eval.decision == Decision.DENY) {
                denyObligations.addAll(eval.obligations);
                denyAdvice.addAll(eval.advice);
            }
        }

        Value buildFinalDecision() {
            val finalObligations = selectObligationsAdvice(entitlement, permitObligations, denyObligations);
            val finalAdvice      = selectObligationsAdvice(entitlement, permitAdvice, denyAdvice);
            return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
        }

        private List<Value> selectObligationsAdvice(Decision decision, List<Value> permitList, List<Value> denyList) {
            return switch (decision) {
            case PERMIT -> permitList;
            case DENY   -> denyList;
            default     -> new ArrayList<>();
            };
        }
    }

    /**
     * Algorithm-specific logic for combining decisions and handling uncertainty.
     */
    private interface CombiningAlgorithmLogic {
        Decision combineDecisions(Decision current, Decision newDecision);

        Decision handleTransformationUncertainty(Decision current);

        Decision getDefaultDecision();
    }

    // ========== Algorithm-Specific Logic Implementations ==========

    private static final CombiningAlgorithmLogic DENY_UNLESS_PERMIT = new CombiningAlgorithmLogic() {
        public Decision combineDecisions(Decision current, Decision newDecision) {
            if (newDecision == Decision.PERMIT)
                return Decision.PERMIT;
            if (newDecision == Decision.DENY && current == Decision.DENY)
                return Decision.DENY;
            return current;
        }

        public Decision handleTransformationUncertainty(Decision current) {
            return Decision.DENY;
        }

        public Decision getDefaultDecision() {
            return Decision.DENY;
        }
    };

    private static final CombiningAlgorithmLogic DENY_OVERRIDES = new CombiningAlgorithmLogic() {
        public Decision combineDecisions(Decision current, Decision newDecision) {
            if (newDecision == Decision.DENY)
                return Decision.DENY;
            if (newDecision == Decision.INDETERMINATE && current != Decision.DENY)
                return Decision.INDETERMINATE;
            if (newDecision == Decision.PERMIT && current == Decision.NOT_APPLICABLE)
                return Decision.PERMIT;
            return current;
        }

        public Decision handleTransformationUncertainty(Decision current) {
            return current == Decision.DENY ? Decision.DENY : Decision.INDETERMINATE;
        }

        public Decision getDefaultDecision() {
            return Decision.NOT_APPLICABLE;
        }
    };

    private static final CombiningAlgorithmLogic PERMIT_OVERRIDES = new CombiningAlgorithmLogic() {
        public Decision combineDecisions(Decision current, Decision newDecision) {
            if (newDecision == Decision.PERMIT)
                return Decision.PERMIT;
            if (newDecision == Decision.INDETERMINATE && current != Decision.PERMIT)
                return Decision.INDETERMINATE;
            if (newDecision == Decision.DENY && current == Decision.NOT_APPLICABLE)
                return Decision.DENY;
            return current;
        }

        public Decision handleTransformationUncertainty(Decision current) {
            return Decision.INDETERMINATE;
        }

        public Decision getDefaultDecision() {
            return Decision.NOT_APPLICABLE;
        }
    };

    private static final CombiningAlgorithmLogic PERMIT_UNLESS_DENY = new CombiningAlgorithmLogic() {
        public Decision combineDecisions(Decision current, Decision newDecision) {
            return newDecision == Decision.DENY ? Decision.DENY : current;
        }

        public Decision handleTransformationUncertainty(Decision current) {
            return Decision.DENY;
        }

        public Decision getDefaultDecision() {
            return Decision.PERMIT;
        }
    };

    // ========== Common Helper Methods ==========

    /**
     * Evaluates a match expression (pure or constant).
     */
    private static boolean evaluateMatch(CompiledPolicy policy, EvaluationContext ctx) {
        val matches = evalValueOrPure(policy.matchExpression(), ctx);
        return matches instanceof BooleanValue boolValue && boolValue.value();
    }

    /**
     * Evaluates a policy's decision expression and extracts PolicyEvaluation.
     */
    private static PolicyEvaluation evaluatePolicyDecision(CompiledPolicy policy, EvaluationContext ctx) {
        val decision = evalValueOrPure(policy.decisionExpression(), ctx);
        return extractPolicyEvaluation(decision);
    }

    /**
     * Extracts decision, resource, obligations, and advice from a decision object.
     */
    private static PolicyEvaluation extractPolicyEvaluation(Value decisionValue) {
        if (!(decisionValue instanceof ObjectValue decisionObj)) {
            return PolicyEvaluation.notApplicable();
        }

        val decisionAttr = decisionObj.get("decision");
        if (!(decisionAttr instanceof TextValue decisionText)) {
            return PolicyEvaluation.notApplicable();
        }

        Decision policyDecision;
        try {
            policyDecision = Decision.valueOf(decisionText.value());
        } catch (IllegalArgumentException e) {
            return PolicyEvaluation.notApplicable();
        }

        val resource    = decisionObj.get("resource");
        val obligations = decisionObj.get("obligations");
        val advice      = decisionObj.get("advice");

        val obligationsArray = obligations instanceof ArrayValue arr ? arr : new ArrayValue(List.of(), false);
        val adviceArray      = advice instanceof ArrayValue arr ? arr : new ArrayValue(List.of(), false);

        return new PolicyEvaluation(policyDecision, resource, obligationsArray, adviceArray);
    }

    /**
     * Checks if all policies have pure or constant decision expressions.
     */
    private static boolean allPureOrConstant(List<CompiledPolicy> policies) {
        return policies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);
    }

    private static Value evalValueOrPure(CompiledExpression e, EvaluationContext ctx) {
        if (e instanceof Value v) {
            return v;
        }
        return ((PureExpression) e).evaluate(ctx);
    }

    /**
     * Creates a Flux that evaluates a single policy and returns PolicyEvaluation.
     */
    private static Flux<PolicyEvaluation> createPolicyFlux(CompiledPolicy policy) {
        val matchFlux = ExpressionCompiler.compiledExpressionToFlux(policy.matchExpression());
        return matchFlux.switchMap(matches -> {
            if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                return Flux.just(PolicyEvaluation.notApplicable());
            }
            return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression())
                    .map(CombiningAlgorithmCompiler::extractPolicyEvaluation);
        });
    }

    // ========== Generic Combining Algorithm Implementation ==========

    /**
     * Generic implementation for combining algorithms that follow the standard
     * pattern.
     */
    private static CompiledExpression genericCombiningAlgorithm(List<CompiledPolicy> compiledPolicies,
            CombiningAlgorithmLogic logic) {

        if (compiledPolicies.isEmpty()) {
            return getDefaultDecisionConstant(logic.getDefaultDecision());
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateGenericPure(compiledPolicies, logic, ctx), true);
        } else {
            return new StreamExpression(buildGenericStream(compiledPolicies, logic));
        }
    }

    private static Value getDefaultDecisionConstant(Decision decision) {
        return switch (decision) {
        case NOT_APPLICABLE -> NOT_APPLICABLE_DECISION;
        case INDETERMINATE  -> INDETERMINATE_DECISION;
        case DENY           -> DENY_DECISION;
        case PERMIT         -> PERMIT_DECISION;
        };
    }

    private static Value evaluateGenericPure(List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic,
            EvaluationContext ctx) {

        val accumulator = new DecisionAccumulator(logic.getDefaultDecision());

        for (val policy : compiledPolicies) {
            if (!evaluateMatch(policy, ctx)) {
                continue;
            }
            val evaluation = evaluatePolicyDecision(policy, ctx);
            accumulator.addPolicyEvaluation(evaluation, logic);
        }

        return accumulator.buildFinalDecision();
    }

    private static Flux<Value> buildGenericStream(List<CompiledPolicy> compiledPolicies,
            CombiningAlgorithmLogic logic) {

        val policyFluxes = compiledPolicies.stream().map(CombiningAlgorithmCompiler::createPolicyFlux).toList();

        return Flux.combineLatest(policyFluxes, evaluations -> {
            val accumulator = new DecisionAccumulator(logic.getDefaultDecision());

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;
                if (policyEval.decision != Decision.NOT_APPLICABLE) {
                    accumulator.addPolicyEvaluation(policyEval, logic);
                }
            }

            return accumulator.buildFinalDecision();
        });
    }

    // ========== Public API: Combining Algorithms ==========

    public static CompiledExpression denyUnlessPermit(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_UNLESS_PERMIT);
    }

    public static CompiledExpression denyOverrides(List<CompiledPolicy> compiledPolicies, CompilationContext context) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_OVERRIDES);
    }

    public static CompiledExpression permitOverrides(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_OVERRIDES);
    }

    public static CompiledExpression permitUnlessDeny(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_UNLESS_DENY);
    }

    public static CompiledExpression onlyOneApplicable(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return NOT_APPLICABLE_DECISION;
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateOnlyOneApplicablePure(compiledPolicies, ctx), true);
        } else {
            return new StreamExpression(buildOnlyOneApplicableStream(compiledPolicies));
        }
    }

    private static Value evaluateOnlyOneApplicablePure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   applicableCount    = 0;
        var   hasIndeterminate   = false;
        Value applicableDecision = null;

        for (val policy : compiledPolicies) {
            if (!evaluateMatch(policy, ctx)) {
                continue;
            }

            val evaluation = evaluatePolicyDecision(policy, ctx);
            if (evaluation.decision != Decision.NOT_APPLICABLE) {
                applicableCount++;
                if (evaluation.decision == Decision.INDETERMINATE) {
                    hasIndeterminate = true;
                }
                if (applicableDecision == null) {
                    applicableDecision = SaplCompiler.buildDecisionObject(evaluation.decision,
                            List.copyOf(evaluation.obligations), List.copyOf(evaluation.advice), evaluation.resource);
                }
            }
        }

        if (hasIndeterminate || applicableCount > 1) {
            return INDETERMINATE_DECISION;
        }

        return applicableCount == 1 ? applicableDecision : NOT_APPLICABLE_DECISION;
    }

    private static Flux<Value> buildOnlyOneApplicableStream(List<CompiledPolicy> compiledPolicies) {
        val policyFluxes = compiledPolicies.stream().map(CombiningAlgorithmCompiler::createPolicyFlux).toList();

        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   applicableCount    = 0;
            var   hasIndeterminate   = false;
            Value applicableDecision = null;

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;
                if (policyEval.decision != Decision.NOT_APPLICABLE) {
                    applicableCount++;
                    if (policyEval.decision == Decision.INDETERMINATE) {
                        hasIndeterminate = true;
                    }
                    if (applicableDecision == null) {
                        applicableDecision = SaplCompiler.buildDecisionObject(policyEval.decision,
                                List.copyOf(policyEval.obligations), List.copyOf(policyEval.advice),
                                policyEval.resource);
                    }
                }
            }

            if (hasIndeterminate || applicableCount > 1) {
                return INDETERMINATE_DECISION;
            }

            return applicableCount == 1 ? applicableDecision : NOT_APPLICABLE_DECISION;
        });
    }

    // ========== First-Applicable (Special Case) ==========

    public static CompiledExpression firstApplicable(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return NOT_APPLICABLE_DECISION;
        }

        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            return new PureExpression(ctx -> {
                for (val policy : compiledPolicies) {
                    if (!evaluateMatch(policy, ctx)) {
                        continue;
                    }

                    val decision = evalValueOrPure(policy.decisionExpression(), ctx);
                    if (!(decision instanceof ObjectValue objectValue)) {
                        return INDETERMINATE_DECISION;
                    }

                    val decisionAttribute = objectValue.get("decision");
                    if (!(decisionAttribute instanceof TextValue textValue)) {
                        return INDETERMINATE_DECISION;
                    }

                    try {
                        val d = Decision.valueOf(textValue.value());
                        if (d == Decision.NOT_APPLICABLE) {
                            continue;
                        }
                        return decision;
                    } catch (IllegalArgumentException e) {
                        return INDETERMINATE_DECISION;
                    }
                }
                return NOT_APPLICABLE_DECISION;
            }, true);
        }

        var decisionStream = Flux.just(NOT_APPLICABLE_DECISION);
        for (var i = compiledPolicies.size() - 1; i >= 0; i--) {
            val compiledPolicy = compiledPolicies.get(i);
            val matchFlux      = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val previousStream = decisionStream;
            decisionStream = matchFlux.switchMap(matches -> {
                if (!(matches instanceof BooleanValue matchesBool)) {
                    return Flux.just(INDETERMINATE_DECISION);
                }
                if (matchesBool.value()) {
                    return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                            .switchMap(decision -> {
                                if (!(decision instanceof ObjectValue objectValue)) {
                                    return Flux.just(INDETERMINATE_DECISION);
                                }
                                val decisionAttribute = objectValue.get("decision");
                                if (!(decisionAttribute instanceof TextValue textValue)) {
                                    return Flux.just(INDETERMINATE_DECISION);
                                }
                                try {
                                    val d = Decision.valueOf(textValue.value());
                                    if (d == Decision.NOT_APPLICABLE) {
                                        return previousStream;
                                    }
                                    return Flux.just(decision);
                                } catch (IllegalArgumentException e) {
                                    return Flux.just(INDETERMINATE_DECISION);
                                }
                            });
                }
                return previousStream;
            });
        }
        return new StreamExpression(decisionStream);
    }
}
