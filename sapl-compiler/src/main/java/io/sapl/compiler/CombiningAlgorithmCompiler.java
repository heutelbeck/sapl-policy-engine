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

/**
 * Compiles SAPL combining algorithms into executable expressions.
 * <p>
 * Supports six combining algorithms: deny-unless-permit, deny-overrides,
 * permit-overrides, permit-unless-deny,
 * only-one-applicable, and first-applicable. Each algorithm determines how
 * multiple policy decisions are combined into
 * a single authorization decision.
 * <p>
 * The compiler produces either a {@link PureExpression} (when all policy
 * decisions are pure/constant) or a
 * {@link StreamExpression} (when any policy decision is reactive). Pure
 * expressions evaluate synchronously; stream
 * expressions use {@code Flux.combineLatest} to react to changes in any
 * policy's decision.
 */
@UtilityClass
public class CombiningAlgorithmCompiler {

    // ========== Common Data Structures ==========

    /**
     * Holds the result of evaluating a single policy: decision, resource
     * transformation, obligations, and advice.
     */
    private record PolicyEvaluation(Decision decision, Value resource, ArrayValue obligations, ArrayValue advice) {
        static PolicyEvaluation notApplicable() {
            return new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY);
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
            return AuthorizationDecisionUtil.buildDecision(entitlement, finalObligations, finalAdvice, resource);
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

        val decisionAttr = decisionObj.get(AuthorizationDecisionUtil.FIELD_DECISION);
        if (!(decisionAttr instanceof TextValue decisionText)) {
            return PolicyEvaluation.notApplicable();
        }

        Decision policyDecision;
        try {
            policyDecision = Decision.valueOf(decisionText.value());
        } catch (IllegalArgumentException e) {
            return PolicyEvaluation.notApplicable();
        }

        val resource    = decisionObj.get(AuthorizationDecisionUtil.FIELD_RESOURCE);
        val obligations = decisionObj.get(AuthorizationDecisionUtil.FIELD_OBLIGATIONS);
        val advice      = decisionObj.get(AuthorizationDecisionUtil.FIELD_ADVICE);

        val obligationsArray = obligations instanceof ArrayValue arr ? arr : Value.EMPTY_ARRAY;
        val adviceArray      = advice instanceof ArrayValue arr ? arr : Value.EMPTY_ARRAY;

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
    private static Flux<PolicyEvaluation> createPolicyFlux(CompiledPolicy policy, boolean preMatched) {
        if (preMatched) {
            return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression())
                    .map(CombiningAlgorithmCompiler::extractPolicyEvaluation);
        }
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
     *
     * @param compiledPolicies
     * policies to combine
     * @param logic
     * algorithm-specific combining logic
     * @param preMatched
     * if true, skip match expression evaluation (policies already matched by PRP)
     */
    private static CompiledExpression genericCombiningAlgorithm(List<CompiledPolicy> compiledPolicies,
            CombiningAlgorithmLogic logic, boolean preMatched) {

        if (compiledPolicies.isEmpty()) {
            return getDefaultDecisionConstant(logic.getDefaultDecision());
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateGenericPure(compiledPolicies, logic, ctx, preMatched), true);
        } else {
            return new StreamExpression(buildGenericStream(compiledPolicies, logic, preMatched));
        }
    }

    private static Value getDefaultDecisionConstant(Decision decision) {
        return switch (decision) {
        case NOT_APPLICABLE -> AuthorizationDecisionUtil.NOT_APPLICABLE;
        case INDETERMINATE  -> AuthorizationDecisionUtil.INDETERMINATE;
        case DENY           -> AuthorizationDecisionUtil.DENY;
        case PERMIT         -> AuthorizationDecisionUtil.PERMIT;
        };
    }

    private static Value evaluateGenericPure(List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic,
            EvaluationContext ctx, boolean preMatched) {

        val accumulator = new DecisionAccumulator(logic.getDefaultDecision());

        for (val policy : compiledPolicies) {
            if (!preMatched && !evaluateMatch(policy, ctx)) {
                continue;
            }
            val evaluation = evaluatePolicyDecision(policy, ctx);
            accumulator.addPolicyEvaluation(evaluation, logic);
        }

        return accumulator.buildFinalDecision();
    }

    private static Flux<Value> buildGenericStream(List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic,
            boolean preMatched) {

        val policyFluxes = compiledPolicies.stream().map(policy -> createPolicyFlux(policy, preMatched)).toList();

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

    /**
     * Compiles deny-unless-permit algorithm. Default: DENY. Any PERMIT yields
     * PERMIT. Multiple resource transformations
     * yield DENY.
     *
     * @param compiledPolicies
     * policies to combine
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression denyUnlessPermit(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_UNLESS_PERMIT, false);
    }

    /**
     * Compiles deny-unless-permit algorithm for pre-matched policies. Use when
     * policies have already been filtered by
     * the PRP.
     *
     * @param compiledPolicies
     * policies to combine (already matched)
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression denyUnlessPermitPreMatched(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_UNLESS_PERMIT, true);
    }

    /**
     * Compiles deny-overrides algorithm. Default: NOT_APPLICABLE. DENY overrides
     * all. INDETERMINATE propagates unless
     * DENY present. Multiple resource transformations yield INDETERMINATE (unless
     * already DENY).
     *
     * @param compiledPolicies
     * policies to combine
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression denyOverrides(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_OVERRIDES, false);
    }

    /**
     * Compiles deny-overrides algorithm for pre-matched policies. Use when policies
     * have already been filtered by the
     * PRP.
     *
     * @param compiledPolicies
     * policies to combine (already matched)
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression denyOverridesPreMatched(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, DENY_OVERRIDES, true);
    }

    /**
     * Compiles permit-overrides algorithm. Default: NOT_APPLICABLE. PERMIT
     * overrides all. INDETERMINATE propagates
     * unless PERMIT present. Multiple resource transformations yield INDETERMINATE.
     *
     * @param compiledPolicies
     * policies to combine
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression permitOverrides(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_OVERRIDES, false);
    }

    /**
     * Compiles permit-overrides algorithm for pre-matched policies. Use when
     * policies have already been filtered by the
     * PRP.
     *
     * @param compiledPolicies
     * policies to combine (already matched)
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression permitOverridesPreMatched(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_OVERRIDES, true);
    }

    /**
     * Compiles permit-unless-deny algorithm. Default: PERMIT. Any DENY yields DENY.
     * Multiple resource transformations
     * yield DENY.
     *
     * @param compiledPolicies
     * policies to combine
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression permitUnlessDeny(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_UNLESS_DENY, false);
    }

    /**
     * Compiles permit-unless-deny algorithm for pre-matched policies. Use when
     * policies have already been filtered by
     * the PRP.
     *
     * @param compiledPolicies
     * policies to combine (already matched)
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression permitUnlessDenyPreMatched(List<CompiledPolicy> compiledPolicies) {
        return genericCombiningAlgorithm(compiledPolicies, PERMIT_UNLESS_DENY, true);
    }

    /**
     * Compiles only-one-applicable algorithm. Returns the single applicable
     * policy's decision. If zero applicable:
     * NOT_APPLICABLE. If multiple applicable or any INDETERMINATE: INDETERMINATE.
     *
     * @param compiledPolicies
     * policies to combine
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression onlyOneApplicable(List<CompiledPolicy> compiledPolicies) {
        return onlyOneApplicableInternal(compiledPolicies, false);
    }

    /**
     * Compiles only-one-applicable algorithm for pre-matched policies. Use when
     * policies have already been filtered by
     * the PRP.
     *
     * @param compiledPolicies
     * policies to combine (already matched)
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression onlyOneApplicablePreMatched(List<CompiledPolicy> compiledPolicies) {
        return onlyOneApplicableInternal(compiledPolicies, true);
    }

    private static CompiledExpression onlyOneApplicableInternal(List<CompiledPolicy> compiledPolicies,
            boolean preMatched) {
        if (compiledPolicies.isEmpty()) {
            return AuthorizationDecisionUtil.NOT_APPLICABLE;
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateOnlyOneApplicablePure(compiledPolicies, ctx, preMatched), true);
        }
        return new StreamExpression(buildOnlyOneApplicableStream(compiledPolicies, preMatched));
    }

    private static Value evaluateOnlyOneApplicablePure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx,
            boolean preMatched) {
        val accumulator = new OnlyOneApplicableAccumulator();
        for (val policy : compiledPolicies) {
            if (preMatched || evaluateMatch(policy, ctx)) {
                accumulator.addEvaluation(evaluatePolicyDecision(policy, ctx));
            }
        }
        return accumulator.buildFinalDecision();
    }

    private static Flux<Value> buildOnlyOneApplicableStream(List<CompiledPolicy> compiledPolicies, boolean preMatched) {
        val policyFluxes = compiledPolicies.stream().map(policy -> createPolicyFlux(policy, preMatched)).toList();
        return Flux.combineLatest(policyFluxes, evaluations -> {
            val accumulator = new OnlyOneApplicableAccumulator();
            for (val evaluation : evaluations) {
                accumulator.addEvaluation((PolicyEvaluation) evaluation);
            }
            return accumulator.buildFinalDecision();
        });
    }

    private static class OnlyOneApplicableAccumulator {
        private int     applicableCount;
        private boolean hasIndeterminate;
        private Value   applicableDecision;

        void addEvaluation(PolicyEvaluation evaluation) {
            if (evaluation.decision == Decision.NOT_APPLICABLE) {
                return;
            }
            applicableCount++;
            if (evaluation.decision == Decision.INDETERMINATE) {
                hasIndeterminate = true;
            }
            if (applicableDecision == null) {
                applicableDecision = AuthorizationDecisionUtil.buildDecision(evaluation.decision,
                        List.copyOf(evaluation.obligations), List.copyOf(evaluation.advice), evaluation.resource);
            }
        }

        Value buildFinalDecision() {
            if (hasIndeterminate || applicableCount > 1) {
                return AuthorizationDecisionUtil.INDETERMINATE;
            }
            return applicableCount == 1 ? applicableDecision : AuthorizationDecisionUtil.NOT_APPLICABLE;
        }
    }

    /**
     * Compiles first-applicable algorithm. Returns the decision of the first
     * applicable policy (in document order) that
     * is not NOT_APPLICABLE. If no applicable policy found: NOT_APPLICABLE. Invalid
     * decision structures yield
     * INDETERMINATE.
     *
     * @param compiledPolicies
     * policies to combine, evaluated in order
     *
     * @return compiled expression producing the combined decision
     */
    public static CompiledExpression firstApplicable(List<CompiledPolicy> compiledPolicies) {
        if (compiledPolicies.isEmpty()) {
            return AuthorizationDecisionUtil.NOT_APPLICABLE;
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateFirstApplicablePure(compiledPolicies, ctx), true);
        }
        return new StreamExpression(buildFirstApplicableStream(compiledPolicies));
    }

    private static Value evaluateFirstApplicablePure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        for (val policy : compiledPolicies) {
            if (!evaluateMatch(policy, ctx)) {
                continue;
            }
            val decision       = evalValueOrPure(policy.decisionExpression(), ctx);
            val decisionResult = processFirstApplicableDecision(decision);
            if (decisionResult != null) {
                return decisionResult;
            }
        }
        return AuthorizationDecisionUtil.NOT_APPLICABLE;
    }

    private static Value processFirstApplicableDecision(Value decision) {
        val decisionEnum = AuthorizationDecisionUtil.extractDecision(decision);
        if (decisionEnum == null) {
            return AuthorizationDecisionUtil.INDETERMINATE;
        }
        if (decisionEnum == Decision.NOT_APPLICABLE) {
            return null;
        }
        return decision;
    }

    private static Flux<Value> buildFirstApplicableStream(List<CompiledPolicy> compiledPolicies) {
        var decisionStream = Flux.just(AuthorizationDecisionUtil.NOT_APPLICABLE);
        for (var i = compiledPolicies.size() - 1; i >= 0; i--) {
            decisionStream = buildFirstApplicablePolicyStream(compiledPolicies.get(i), decisionStream);
        }
        return decisionStream;
    }

    private static Flux<Value> buildFirstApplicablePolicyStream(CompiledPolicy policy, Flux<Value> fallbackStream) {
        val matchFlux = ExpressionCompiler.compiledExpressionToFlux(policy.matchExpression());
        return matchFlux.switchMap(matches -> {
            if (!(matches instanceof BooleanValue matchesBool)) {
                return Flux.just(AuthorizationDecisionUtil.INDETERMINATE);
            }
            if (!matchesBool.value()) {
                return fallbackStream;
            }
            return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression())
                    .switchMap(decision -> mapFirstApplicableDecision(decision, fallbackStream));
        });
    }

    private static Flux<Value> mapFirstApplicableDecision(Value decision, Flux<Value> fallbackStream) {
        val decisionEnum = AuthorizationDecisionUtil.extractDecision(decision);
        if (decisionEnum == null) {
            return Flux.just(AuthorizationDecisionUtil.INDETERMINATE);
        }
        if (decisionEnum == Decision.NOT_APPLICABLE) {
            return fallbackStream;
        }
        return Flux.just(decision);
    }
}
