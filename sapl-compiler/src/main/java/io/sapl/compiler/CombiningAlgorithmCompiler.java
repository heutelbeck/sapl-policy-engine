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

import static io.sapl.api.pdp.internal.TracedPolicyDecision.*;

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import io.sapl.api.pdp.internal.TracedPolicySetDecision;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compiles SAPL combining algorithms into executable expressions that produce
 * traced decisions.
 * <p>
 * Supports six combining algorithms: deny-unless-permit, deny-overrides,
 * permit-overrides, permit-unless-deny,
 * only-one-applicable, and first-applicable. Each algorithm determines how
 * multiple policy decisions are combined into
 * a single authorization decision.
 * <p>
 * The compiler produces traced decisions that include:
 * <ul>
 * <li>The combined authorization decision (decision, obligations, advice,
 * resource)</li>
 * <li>Traces of all evaluated policies/documents</li>
 * <li>Context metadata (set name/algorithm or PDP metadata)</li>
 * </ul>
 * <p>
 * For policy set level (called from SaplCompiler): produces
 * TracedPolicySetDecision For PDP level (called from
 * DynamicPolicyDecisionPoint): produces TracedPdpDecision
 */
@UtilityClass
public class CombiningAlgorithmCompiler {

    private static final String TARGET_EXPRESSION_TYPE_ERROR = "Target expression must return Boolean, but was: %s.";

    // ========== Traced Decision Accumulator ==========

    /**
     * Accumulator for combining multiple traced policy decisions according to
     * algorithm semantics. Collects both the
     * combining result and the individual traces.
     */
    private static class TracedDecisionAccumulator {
        private final CombiningAlgorithmLogic logic;

        Decision    entitlement;
        Value       resource          = Value.UNDEFINED;
        boolean     hasResource       = false;
        List<Value> permitObligations = new ArrayList<>();
        List<Value> permitAdvice      = new ArrayList<>();
        List<Value> denyObligations   = new ArrayList<>();
        List<Value> denyAdvice        = new ArrayList<>();
        List<Value> tracedPolicies    = new ArrayList<>();

        TracedDecisionAccumulator(CombiningAlgorithmLogic logic) {
            this.logic       = logic;
            this.entitlement = logic.getDefaultDecision();
        }

        void addTracedPolicy(Value tracedPolicy) {
            if (tracedPolicy == null || tracedPolicy instanceof UndefinedValue) {
                return; // Skip null and non-matching policies (UNDEFINED sentinel)
            }

            tracedPolicies.add(tracedPolicy);

            val decision    = getDecision(tracedPolicy);
            val obligations = getObligations(tracedPolicy);
            val advice      = getAdvice(tracedPolicy);
            val policyRes   = getResource(tracedPolicy);

            // Update entitlement based on algorithm-specific logic
            entitlement = logic.combineDecisions(entitlement, decision);

            // Handle resource transformations
            if (!(policyRes instanceof UndefinedValue)) {
                if (hasResource) {
                    entitlement = logic.handleTransformationUncertainty(entitlement);
                } else {
                    resource    = policyRes;
                    hasResource = true;
                }
            }

            // Collect obligations and advice by decision type
            if (decision == Decision.PERMIT) {
                permitObligations.addAll(obligations);
                permitAdvice.addAll(advice);
            } else if (decision == Decision.DENY) {
                denyObligations.addAll(obligations);
                denyAdvice.addAll(advice);
            }
        }

        List<Value> getFinalObligations() {
            return selectByDecision(entitlement, permitObligations, denyObligations);
        }

        List<Value> getFinalAdvice() {
            return selectByDecision(entitlement, permitAdvice, denyAdvice);
        }

        private List<Value> selectByDecision(Decision decision, List<Value> permitList, List<Value> denyList) {
            return switch (decision) {
            case PERMIT -> permitList;
            case DENY   -> denyList;
            default     -> List.of();
            };
        }

        Value buildSetDecision(String setName, String algorithm, int totalPolicies) {
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(entitlement)
                    .totalPolicies(totalPolicies).obligations(getFinalObligations()).advice(getFinalAdvice())
                    .resource(resource).policies(tracedPolicies).build();
        }

        Value buildPdpDecision(EvaluationContext ctx, String algorithm, int totalDocuments) {
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(entitlement)
                    .totalDocuments(totalDocuments).obligations(getFinalObligations()).advice(getFinalAdvice())
                    .resource(resource).documents(tracedPolicies).build();
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
     * Result of evaluating a match expression. Carries the error value when
     * evaluation fails.
     */
    private sealed interface MatchResult {
        record Match() implements MatchResult {}

        record NoMatch() implements MatchResult {}

        record Error(ErrorValue error) implements MatchResult {}
    }

    private static final MatchResult MATCH    = new MatchResult.Match();
    private static final MatchResult NO_MATCH = new MatchResult.NoMatch();

    private static MatchResult evaluateMatch(CompiledPolicy policy, EvaluationContext ctx) {
        val matches = evalValueOrPure(policy.matchExpression(), ctx);
        if (matches instanceof BooleanValue boolValue) {
            return boolValue.value() ? MATCH : NO_MATCH;
        }
        if (matches instanceof ErrorValue error) {
            return new MatchResult.Error(error);
        }
        // Non-boolean, non-error result (e.g., a number or string) - create synthetic
        // error
        return new MatchResult.Error(Value.error(TARGET_EXPRESSION_TYPE_ERROR.formatted(matches)));
    }

    /**
     * Evaluates a policy's decision expression which now returns a
     */
    private static Value evaluateTracedPolicy(CompiledPolicy policy, EvaluationContext ctx) {
        return evalValueOrPure(policy.decisionExpression(), ctx);
    }

    /**
     * Evaluates a policy and returns its traced decision, or UNDEFINED if the
     * target doesn't match. This mirrors the
     * semantics of {@link #createTracedPolicyFlux} for pure evaluation paths.
     *
     * @param policy
     * the compiled policy to evaluate
     * @param context
     * the evaluation context
     *
     * @return traced policy decision, or Value.UNDEFINED if target doesn't match
     */
    private static Value evaluateTracedPolicyOrSkip(CompiledPolicy policy, EvaluationContext context) {
        return switch (evaluateMatch(policy, context)) {
        case MatchResult.Error(ErrorValue error) ->
            createIndeterminateTrace(policy.name(), policy.entitlement(), error);
        case MatchResult.Match ignored           -> evaluateTracedPolicy(policy, context);
        case MatchResult.NoMatch ignored         -> Value.UNDEFINED;
        };
    }

    /**
     * Creates an INDETERMINATE traced policy for target errors.
     *
     * @param policyName
     * the name of the policy whose target failed
     * @param entitlement
     * the policy's declared entitlement (PERMIT or DENY)
     * @param error
     * the error that occurred during target evaluation
     *
     * @return a TracedPolicyDecision Value with INDETERMINATE decision and error
     * details
     */
    private static Value createIndeterminateTrace(String policyName, String entitlement, ErrorValue error) {
        return builder().name(policyName).entitlement(entitlement).decision(Decision.INDETERMINATE).targetError(error)
                .build();
    }

    private static boolean allPureOrConstant(List<CompiledPolicy> policies) {
        return policies.stream()
                .allMatch(p -> isPureOrConstant(p.decisionExpression()) && isPureOrConstant(p.matchExpression()));
    }

    private static boolean isPureOrConstant(CompiledExpression expression) {
        return expression instanceof Value || expression instanceof PureExpression;
    }

    private static Value evalValueOrPure(CompiledExpression e, EvaluationContext ctx) {
        if (e instanceof Value v) {
            return v;
        }
        return ((PureExpression) e).evaluate(ctx);
    }

    private static Flux<Value> createTracedPolicyFlux(CompiledPolicy policy) {
        val matchFlux = ExpressionCompiler.compiledExpressionToFlux(policy.matchExpression());
        return matchFlux.switchMap(matches -> {
            if (matches instanceof BooleanValue matchesBool) {
                if (!matchesBool.value()) {
                    return Flux.empty(); // NOT_APPLICABLE - don't include in trace
                }
                return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression());
            }
            // Target evaluation failed - create trace with error details
            val error = matches instanceof ErrorValue err ? err
                    : Value.error(TARGET_EXPRESSION_TYPE_ERROR.formatted(matches));
            return Flux.just(createIndeterminateTrace(policy.name(), policy.entitlement(), error));
        });
    }

    // ========== Set-Level Combining (from SaplCompiler) ==========

    /**
     * Compiles deny-unless-permit algorithm for a policy set.
     */
    public static CompiledExpression denyUnlessPermit(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, DENY_UNLESS_PERMIT);
    }

    /**
     * Compiles deny-overrides algorithm for a policy set.
     */
    public static CompiledExpression denyOverrides(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, DENY_OVERRIDES);
    }

    /**
     * Compiles permit-overrides algorithm for a policy set.
     */
    public static CompiledExpression permitOverrides(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, PERMIT_OVERRIDES);
    }

    /**
     * Compiles permit-unless-deny algorithm for a policy set.
     */
    public static CompiledExpression permitUnlessDeny(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, PERMIT_UNLESS_DENY);
    }

    /**
     * Compiles only-one-applicable algorithm for a policy set.
     */
    public static CompiledExpression onlyOneApplicable(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return onlyOneApplicableSet(setName, algorithm, compiledPolicies);
    }

    /**
     * Compiles first-applicable algorithm for a policy set.
     */
    public static CompiledExpression firstApplicable(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return firstApplicableSet(setName, algorithm, compiledPolicies);
    }

    private static CompiledExpression genericSetCombining(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic) {

        if (compiledPolicies.isEmpty()) {
            return buildEmptySetDecision(setName, algorithm, logic.getDefaultDecision());
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateGenericSetPure(setName, algorithm, compiledPolicies, logic, ctx),
                    true);
        } else {
            return new StreamExpression(buildGenericSetStream(setName, algorithm, compiledPolicies, logic));
        }
    }

    private static Value buildEmptySetDecision(String setName, String algorithm, Decision decision) {
        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(decision).totalPolicies(0)
                .build();
    }

    private static Value evaluateGenericSetPure(String setName, String algorithm, List<CompiledPolicy> compiledPolicies,
            CombiningAlgorithmLogic logic, EvaluationContext ctx) {

        val accumulator = new TracedDecisionAccumulator(logic);
        for (val policy : compiledPolicies) {
            accumulator.addTracedPolicy(evaluateTracedPolicyOrSkip(policy, ctx));
        }
        return accumulator.buildSetDecision(setName, algorithm, compiledPolicies.size());
    }

    private static Flux<Value> buildGenericSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic) {

        val policyFluxes  = compiledPolicies.stream()
                .map(policy -> createTracedPolicyFlux(policy).defaultIfEmpty(Value.UNDEFINED)).toList();
        val totalPolicies = compiledPolicies.size();

        return Flux.combineLatest(policyFluxes, tracedPolicies -> {
            val accumulator = new TracedDecisionAccumulator(logic);
            for (val traced : tracedPolicies) {
                accumulator.addTracedPolicy((Value) traced);
            }
            return accumulator.buildSetDecision(setName, algorithm, totalPolicies);
        });
    }

    // ========== Only-One-Applicable (Set Level) ==========

    private static CompiledExpression onlyOneApplicableSet(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {

        if (compiledPolicies.isEmpty()) {
            return buildEmptySetDecision(setName, algorithm, Decision.NOT_APPLICABLE);
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(
                    ctx -> evaluateOnlyOneApplicableSetPure(setName, algorithm, compiledPolicies, ctx), true);
        }
        return new StreamExpression(buildOnlyOneApplicableSetStream(setName, algorithm, compiledPolicies));
    }

    private static Value evaluateOnlyOneApplicableSetPure(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {

        val evaluatedTraces  = new ArrayList<Value>();
        val applicableTraces = new ArrayList<Value>();
        var hasIndeterminate = false;

        for (val policy : compiledPolicies) {
            val traced = evaluateTracedPolicyOrSkip(policy, ctx);
            if (traced instanceof UndefinedValue) {
                continue;
            }
            val decision = getDecision(traced);
            evaluatedTraces.add(traced);
            if (decision != Decision.NOT_APPLICABLE) {
                applicableTraces.add(traced);
                if (decision == Decision.INDETERMINATE) {
                    hasIndeterminate = true;
                }
            }
        }

        return buildOnlyOneApplicableResult(setName, algorithm, evaluatedTraces, applicableTraces, hasIndeterminate,
                compiledPolicies.size());
    }

    private static Flux<Value> buildOnlyOneApplicableSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {

        val policyFluxes  = compiledPolicies.stream()
                .map(policy -> createTracedPolicyFlux(policy).defaultIfEmpty(Value.UNDEFINED)).toList();
        val totalPolicies = compiledPolicies.size();

        return Flux.combineLatest(policyFluxes, tracedPolicies -> {
            val evaluatedTraces  = new ArrayList<Value>();
            val applicableTraces = new ArrayList<Value>();
            var hasIndeterminate = false;

            for (val traced : tracedPolicies) {
                if (traced instanceof UndefinedValue) {
                    continue;
                }
                val v        = (Value) traced;
                val decision = getDecision(v);
                evaluatedTraces.add(v);
                if (decision != Decision.NOT_APPLICABLE) {
                    applicableTraces.add(v);
                    if (decision == Decision.INDETERMINATE) {
                        hasIndeterminate = true;
                    }
                }
            }

            return buildOnlyOneApplicableResult(setName, algorithm, evaluatedTraces, applicableTraces, hasIndeterminate,
                    totalPolicies);
        });
    }

    private static Value buildOnlyOneApplicableResult(String setName, String algorithm, List<Value> evaluatedTraces,
            List<Value> applicableTraces, boolean hasIndeterminate, int totalPolicies) {

        if (hasIndeterminate || applicableTraces.size() > 1) {
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.INDETERMINATE)
                    .totalPolicies(totalPolicies).policies(evaluatedTraces).build();
        }

        if (applicableTraces.size() == 1) {
            val traced = applicableTraces.getFirst();
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(getDecision(traced))
                    .totalPolicies(totalPolicies).obligations(getObligations(traced)).advice(getAdvice(traced))
                    .resource(getResource(traced)).policies(evaluatedTraces).build();
        }

        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.NOT_APPLICABLE)
                .totalPolicies(totalPolicies).policies(evaluatedTraces).build();
    }

    // ========== First-Applicable (Set Level) ==========

    private static CompiledExpression firstApplicableSet(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {

        if (compiledPolicies.isEmpty()) {
            return buildEmptySetDecision(setName, algorithm, Decision.NOT_APPLICABLE);
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(ctx -> evaluateFirstApplicableSetPure(setName, algorithm, compiledPolicies, ctx),
                    true);
        }
        return new StreamExpression(buildFirstApplicableSetStream(setName, algorithm, compiledPolicies));
    }

    private static Value evaluateFirstApplicableSetPure(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {

        val evaluatedTraces = new ArrayList<Value>();
        val totalPolicies   = compiledPolicies.size();

        for (val policy : compiledPolicies) {
            val matchResult = evaluateMatch(policy, ctx);

            if (matchResult instanceof MatchResult.Error(ErrorValue error)) {
                evaluatedTraces.add(createIndeterminateTrace(policy.name(), policy.entitlement(), error));
                return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm)
                        .decision(Decision.INDETERMINATE).totalPolicies(totalPolicies).policies(evaluatedTraces)
                        .build();
            }

            if (matchResult instanceof MatchResult.NoMatch) {
                evaluatedTraces.add(createNoMatchTrace(policy.name()));
                continue;
            }

            val traced   = evaluateTracedPolicy(policy, ctx);
            val decision = getDecision(traced);
            evaluatedTraces.add(traced);

            if (decision != Decision.NOT_APPLICABLE) {
                return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(decision)
                        .totalPolicies(totalPolicies).obligations(getObligations(traced)).advice(getAdvice(traced))
                        .resource(getResource(traced)).policies(evaluatedTraces).build();
            }
        }

        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.NOT_APPLICABLE)
                .totalPolicies(totalPolicies).policies(evaluatedTraces).build();
    }

    private static Flux<Value> buildFirstApplicableSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {

        var stateFlux     = Flux.just(new FirstApplicableState(Decision.NOT_APPLICABLE, List.of()));
        val totalPolicies = compiledPolicies.size();

        for (var i = compiledPolicies.size() - 1; i >= 0; i--) {
            val policy            = compiledPolicies.get(i);
            val fallbackStateFlux = stateFlux;
            val noMatchTrace      = createNoMatchTrace(policy.name());

            stateFlux = ExpressionCompiler.compiledExpressionToFlux(policy.matchExpression())
                    .switchMap(matches -> evaluatePolicyMatch(matches, policy, fallbackStateFlux, noMatchTrace));
        }

        return stateFlux.map(state -> buildFirstApplicableSetResult(setName, algorithm, totalPolicies, state));
    }

    private static Flux<FirstApplicableState> evaluatePolicyMatch(Value matches, CompiledPolicy policy,
            Flux<FirstApplicableState> fallbackStateFlux, Value noMatchTrace) {

        if (!(matches instanceof BooleanValue matchesBool)) {
            val error = matches instanceof ErrorValue err ? err
                    : Value.error(TARGET_EXPRESSION_TYPE_ERROR.formatted(matches));
            val trace = createIndeterminateTrace(policy.name(), policy.entitlement(), error);
            return Flux.just(new FirstApplicableState(Decision.INDETERMINATE, List.of(trace)));
        }

        if (!matchesBool.value()) {
            return fallbackStateFlux.map(state -> state.prependTrace(noMatchTrace));
        }

        return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression()).switchMap(traced -> {
            val decision = getDecision(traced);
            if (decision == Decision.NOT_APPLICABLE) {
                return fallbackStateFlux.map(state -> state.prependTrace(traced));
            }
            return Flux.just(new FirstApplicableState(decision, List.of(traced)));
        });
    }

    private static Value buildFirstApplicableSetResult(String setName, String algorithm, int totalPolicies,
            FirstApplicableState state) {
        val hasApplicable = state.decision() != Decision.NOT_APPLICABLE && !state.traces().isEmpty();
        val firstTrace    = hasApplicable ? state.traces().getFirst() : null;

        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(state.decision())
                .totalPolicies(totalPolicies)
                .obligations(hasApplicable ? getObligations(firstTrace) : Value.EMPTY_ARRAY)
                .advice(hasApplicable ? getAdvice(firstTrace) : Value.EMPTY_ARRAY)
                .resource(hasApplicable ? getResource(firstTrace) : Value.UNDEFINED).policies(state.traces()).build();
    }

    private record FirstApplicableState(Decision decision, List<Value> traces) {
        FirstApplicableState prependTrace(Value trace) {
            val newTraces = new ArrayList<Value>();
            newTraces.add(trace);
            newTraces.addAll(traces);
            return new FirstApplicableState(decision, newTraces);
        }
    }

    // ========== PDP-Level Combining (from DynamicPolicyDecisionPoint) ==========

    /**
     * Compiles deny-unless-permit algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "deny-unless-permit")
     * @param documents
     * pre-matched policy documents to combine
     * @param totalDocuments
     * total number of documents in the PRP for completeness proof
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression denyUnlessPermitPreMatched(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {
        return genericPdpCombining(algorithm, documents, DENY_UNLESS_PERMIT, totalDocuments);
    }

    /**
     * Compiles deny-overrides algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "deny-overrides")
     * @param documents
     * pre-matched policy documents to combine
     * @param totalDocuments
     * total number of documents in the PRP for completeness proof
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression denyOverridesPreMatched(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {
        return genericPdpCombining(algorithm, documents, DENY_OVERRIDES, totalDocuments);
    }

    /**
     * Compiles permit-overrides algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "permit-overrides")
     * @param documents
     * pre-matched policy documents to combine
     * @param totalDocuments
     * total number of documents in the PRP for completeness proof
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression permitOverridesPreMatched(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {
        return genericPdpCombining(algorithm, documents, PERMIT_OVERRIDES, totalDocuments);
    }

    /**
     * Compiles permit-unless-deny algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "permit-unless-deny")
     * @param documents
     * pre-matched policy documents to combine
     * @param totalDocuments
     * total number of documents in the PRP for completeness proof
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression permitUnlessDenyPreMatched(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {
        return genericPdpCombining(algorithm, documents, PERMIT_UNLESS_DENY, totalDocuments);
    }

    /**
     * Compiles only-one-applicable algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "only-one-applicable")
     * @param documents
     * pre-matched policy documents to combine
     * @param totalDocuments
     * total number of documents in the PRP for completeness proof
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression onlyOneApplicablePreMatched(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {
        return onlyOneApplicablePdp(algorithm, documents, totalDocuments);
    }

    private static CompiledExpression genericPdpCombining(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic, int totalDocuments) {

        if (documents.isEmpty()) {
            return new PureExpression(
                    ctx -> buildEmptyPdpDecision(ctx, algorithm, logic.getDefaultDecision(), totalDocuments), true);
        }

        if (allPureOrConstant(documents)) {
            return new PureExpression(ctx -> evaluateGenericPdpPure(algorithm, documents, logic, ctx, totalDocuments),
                    true);
        } else {
            return new StreamExpression(buildGenericPdpStream(algorithm, documents, logic, totalDocuments));
        }
    }

    private static Value buildEmptyPdpDecision(EvaluationContext ctx, String algorithm, Decision decision,
            int totalDocuments) {
        return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                .timestamp(ctx.timestamp()).algorithm(algorithm).decision(decision).totalDocuments(totalDocuments)
                .build();
    }

    private static Value evaluateGenericPdpPure(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic, EvaluationContext ctx, int totalDocuments) {

        val accumulator = new TracedDecisionAccumulator(logic);

        for (val document : documents) {
            // At PDP level, documents are pre-matched, so evaluate directly
            val tracedDocument = evaluateTracedPolicy(document, ctx);
            accumulator.addTracedPolicy(tracedDocument);
        }

        return accumulator.buildPdpDecision(ctx, algorithm, totalDocuments);
    }

    private static Flux<Value> buildGenericPdpStream(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic, int totalDocuments) {

        val documentFluxes = documents.stream()
                .map(doc -> ExpressionCompiler.compiledExpressionToFlux(doc.decisionExpression())).toList();

        return Flux.combineLatest(documentFluxes, Arrays::asList)
                .flatMap(tracedDocuments -> Mono.deferContextual(
                        reactorCtx -> Mono.just(combinePdpDocuments(reactorCtx.get(EvaluationContext.class), algorithm,
                                tracedDocuments, logic, totalDocuments))));
    }

    private static Value combinePdpDocuments(EvaluationContext ctx, String algorithm, List<Object> tracedDocuments,
            CombiningAlgorithmLogic logic, int totalDocuments) {
        val accumulator = new TracedDecisionAccumulator(logic);
        for (val traced : tracedDocuments) {
            accumulator.addTracedPolicy((Value) traced);
        }
        return accumulator.buildPdpDecision(ctx, algorithm, totalDocuments);
    }

    // ========== Only-One-Applicable (PDP Level) ==========

    private static CompiledExpression onlyOneApplicablePdp(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {

        if (documents.isEmpty()) {
            return new PureExpression(
                    ctx -> buildEmptyPdpDecision(ctx, algorithm, Decision.NOT_APPLICABLE, totalDocuments), true);
        }

        if (allPureOrConstant(documents)) {
            return new PureExpression(
                    ctx -> evaluateOnlyOneApplicablePdpPure(algorithm, documents, ctx, totalDocuments), true);
        }
        return new StreamExpression(buildOnlyOneApplicablePdpStream(algorithm, documents, totalDocuments));
    }

    private static Value evaluateOnlyOneApplicablePdpPure(String algorithm, List<CompiledPolicy> documents,
            EvaluationContext ctx, int totalDocuments) {

        val evaluatedTraces  = new ArrayList<Value>(documents.size());
        val applicableTraces = new ArrayList<Value>(documents.size());
        var hasIndeterminate = false;

        for (val document : documents) {
            val traced   = evaluateTracedPolicy(document, ctx);
            val decision = getDecision(traced);
            evaluatedTraces.add(traced);

            if (decision != Decision.NOT_APPLICABLE) {
                applicableTraces.add(traced);
                if (decision == Decision.INDETERMINATE) {
                    hasIndeterminate = true;
                }
            }
        }

        return buildOnlyOneApplicablePdpResult(ctx, algorithm, evaluatedTraces, applicableTraces, hasIndeterminate,
                totalDocuments);
    }

    private static Flux<Value> buildOnlyOneApplicablePdpStream(String algorithm, List<CompiledPolicy> documents,
            int totalDocuments) {

        val documentFluxes = documents.stream()
                .map(doc -> ExpressionCompiler.compiledExpressionToFlux(doc.decisionExpression())).toList();

        return Flux.combineLatest(documentFluxes, Arrays::asList)
                .flatMap(tracedDocuments -> Mono.deferContextual(
                        reactorCtx -> Mono.just(combineOnlyOneApplicable(reactorCtx.get(EvaluationContext.class),
                                algorithm, tracedDocuments, totalDocuments))));
    }

    private static Value combineOnlyOneApplicable(EvaluationContext ctx, String algorithm, List<Object> tracedDocuments,
            int totalDocuments) {
        val evaluatedTraces  = new ArrayList<Value>();
        val applicableTraces = new ArrayList<Value>();
        var hasIndeterminate = false;

        for (val traced : tracedDocuments) {
            val v        = (Value) traced;
            val decision = getDecision(v);
            evaluatedTraces.add(v);
            if (decision == Decision.NOT_APPLICABLE) {
                continue;
            }
            applicableTraces.add(v);
            hasIndeterminate = hasIndeterminate || decision == Decision.INDETERMINATE;
        }

        return buildOnlyOneApplicablePdpResult(ctx, algorithm, evaluatedTraces, applicableTraces, hasIndeterminate,
                totalDocuments);
    }

    private static Value buildOnlyOneApplicablePdpResult(EvaluationContext ctx, String algorithm,
            List<Value> evaluatedTraces, List<Value> applicableTraces, boolean hasIndeterminate, int totalDocuments) {

        if (hasIndeterminate || applicableTraces.size() > 1) {
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(Decision.INDETERMINATE)
                    .totalDocuments(totalDocuments).documents(evaluatedTraces).build();
        }

        if (applicableTraces.size() == 1) {
            val traced = applicableTraces.getFirst();
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(getDecision(traced))
                    .totalDocuments(totalDocuments).obligations(getObligations(traced)).advice(getAdvice(traced))
                    .resource(getResource(traced)).documents(evaluatedTraces).build();
        }

        return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                .timestamp(ctx.timestamp()).algorithm(algorithm).decision(Decision.NOT_APPLICABLE)
                .totalDocuments(totalDocuments).documents(evaluatedTraces).build();
    }
}
