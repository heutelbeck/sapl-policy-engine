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
import io.sapl.api.pdp.internal.TracedPdpDecision;
import io.sapl.api.pdp.internal.TracedPolicyDecision;
import io.sapl.api.pdp.internal.TracedPolicySetDecision;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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
            if (tracedPolicy == null) {
                return;
            }

            tracedPolicies.add(tracedPolicy);

            val decision    = TracedPolicyDecision.getDecision(tracedPolicy);
            val obligations = TracedPolicyDecision.getObligations(tracedPolicy);
            val advice      = TracedPolicyDecision.getAdvice(tracedPolicy);
            val policyRes   = TracedPolicyDecision.getResource(tracedPolicy);

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

        Value buildSetDecision(String setName, String algorithm) {
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(entitlement)
                    .obligations(getFinalObligations()).advice(getFinalAdvice()).resource(resource)
                    .policies(tracedPolicies).build();
        }

        Value buildPdpDecision(EvaluationContext ctx, String algorithm) {
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(entitlement)
                    .obligations(getFinalObligations()).advice(getFinalAdvice()).resource(resource)
                    .documents(tracedPolicies).build();
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
        return new MatchResult.Error(Value.error("Target expression must return Boolean, but was: " + matches));
    }

    /**
     * Evaluates a policy's decision expression which now returns a
     * TracedPolicyDecision.
     */
    private static Value evaluateTracedPolicy(CompiledPolicy policy, EvaluationContext ctx) {
        return evalValueOrPure(policy.decisionExpression(), ctx);
    }

    /**
     * Creates an INDETERMINATE traced policy for target errors.
     *
     * @param policyName
     * the name of the policy whose target failed
     * @param entitlement
     * the policy's declared entitlement (PERMIT or DENY), or "UNKNOWN" if not
     * available
     * @param error
     * the error that occurred during target evaluation
     *
     * @return a TracedPolicyDecision Value with INDETERMINATE decision and error
     * details
     */
    private static Value createIndeterminateTrace(String policyName, String entitlement, ErrorValue error) {
        return TracedPolicyDecision.builder().name(policyName).entitlement(entitlement).decision(Decision.INDETERMINATE)
                .targetError(error).build();
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

    private static Flux<Value> createTracedPolicyFlux(CompiledPolicy policy, boolean preMatched) {
        if (preMatched) {
            return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression());
        }
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
                    : Value.error("Target expression must return Boolean, but was: " + matches);
            return Flux.just(createIndeterminateTrace(policy.name(), "UNKNOWN", error));
        });
    }

    // ========== Set-Level Combining (from SaplCompiler) ==========

    /**
     * Compiles deny-unless-permit algorithm for a policy set.
     */
    public static CompiledExpression denyUnlessPermit(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, DENY_UNLESS_PERMIT, false);
    }

    /**
     * Compiles deny-overrides algorithm for a policy set.
     */
    public static CompiledExpression denyOverrides(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, DENY_OVERRIDES, false);
    }

    /**
     * Compiles permit-overrides algorithm for a policy set.
     */
    public static CompiledExpression permitOverrides(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, PERMIT_OVERRIDES, false);
    }

    /**
     * Compiles permit-unless-deny algorithm for a policy set.
     */
    public static CompiledExpression permitUnlessDeny(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return genericSetCombining(setName, algorithm, compiledPolicies, PERMIT_UNLESS_DENY, false);
    }

    /**
     * Compiles only-one-applicable algorithm for a policy set.
     */
    public static CompiledExpression onlyOneApplicable(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return onlyOneApplicableSet(setName, algorithm, compiledPolicies, false);
    }

    /**
     * Compiles first-applicable algorithm for a policy set.
     */
    public static CompiledExpression firstApplicable(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {
        return firstApplicableSet(setName, algorithm, compiledPolicies);
    }

    private static CompiledExpression genericSetCombining(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic, boolean preMatched) {

        if (compiledPolicies.isEmpty()) {
            return buildEmptySetDecision(setName, algorithm, logic.getDefaultDecision());
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(
                    ctx -> evaluateGenericSetPure(setName, algorithm, compiledPolicies, logic, ctx, preMatched), true);
        } else {
            return new StreamExpression(buildGenericSetStream(setName, algorithm, compiledPolicies, logic, preMatched));
        }
    }

    private static Value buildEmptySetDecision(String setName, String algorithm, Decision decision) {
        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(decision).build();
    }

    private static Value evaluateGenericSetPure(String setName, String algorithm, List<CompiledPolicy> compiledPolicies,
            CombiningAlgorithmLogic logic, EvaluationContext ctx, boolean preMatched) {

        val accumulator = new TracedDecisionAccumulator(logic);

        for (val policy : compiledPolicies) {
            if (!preMatched) {
                val matchResult = evaluateMatch(policy, ctx);
                if (matchResult instanceof MatchResult.Error errorResult) {
                    accumulator
                            .addTracedPolicy(createIndeterminateTrace(policy.name(), "UNKNOWN", errorResult.error()));
                    continue;
                }
                if (matchResult instanceof MatchResult.NoMatch) {
                    continue;
                }
            }
            val tracedPolicy = evaluateTracedPolicy(policy, ctx);
            accumulator.addTracedPolicy(tracedPolicy);
        }

        return accumulator.buildSetDecision(setName, algorithm);
    }

    private static Flux<Value> buildGenericSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, CombiningAlgorithmLogic logic, boolean preMatched) {

        val policyFluxes = compiledPolicies.stream()
                .map(policy -> createTracedPolicyFlux(policy, preMatched).defaultIfEmpty(Value.UNDEFINED)) // Placeholder
                                                                                                           // for
                                                                                                           // non-matching
                .toList();

        return Flux.combineLatest(policyFluxes, tracedPolicies -> {
            val accumulator = new TracedDecisionAccumulator(logic);

            for (val traced : tracedPolicies) {
                if (traced instanceof Value v && !(v instanceof UndefinedValue)) {
                    accumulator.addTracedPolicy(v);
                }
            }

            return accumulator.buildSetDecision(setName, algorithm);
        });
    }

    // ========== Only-One-Applicable (Set Level) ==========

    private static CompiledExpression onlyOneApplicableSet(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, boolean preMatched) {

        if (compiledPolicies.isEmpty()) {
            return buildEmptySetDecision(setName, algorithm, Decision.NOT_APPLICABLE);
        }

        if (allPureOrConstant(compiledPolicies)) {
            return new PureExpression(
                    ctx -> evaluateOnlyOneApplicableSetPure(setName, algorithm, compiledPolicies, ctx, preMatched),
                    true);
        }
        return new StreamExpression(buildOnlyOneApplicableSetStream(setName, algorithm, compiledPolicies, preMatched));
    }

    private static Value evaluateOnlyOneApplicableSetPure(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, EvaluationContext ctx, boolean preMatched) {

        List<Value> applicableTraces = new ArrayList<>();
        boolean     hasIndeterminate = false;

        for (val policy : compiledPolicies) {
            if (preMatched) {
                val traced   = evaluateTracedPolicy(policy, ctx);
                val decision = TracedPolicyDecision.getDecision(traced);
                if (decision != Decision.NOT_APPLICABLE) {
                    applicableTraces.add(traced);
                    if (decision == Decision.INDETERMINATE) {
                        hasIndeterminate = true;
                    }
                }
            } else {
                val matchResult = evaluateMatch(policy, ctx);
                if (matchResult instanceof MatchResult.Error errorResult) {
                    applicableTraces.add(createIndeterminateTrace(policy.name(), "UNKNOWN", errorResult.error()));
                    hasIndeterminate = true;
                } else if (matchResult instanceof MatchResult.Match) {
                    val traced   = evaluateTracedPolicy(policy, ctx);
                    val decision = TracedPolicyDecision.getDecision(traced);
                    if (decision != Decision.NOT_APPLICABLE) {
                        applicableTraces.add(traced);
                        if (decision == Decision.INDETERMINATE) {
                            hasIndeterminate = true;
                        }
                    }
                }
            }
        }

        return buildOnlyOneApplicableResult(setName, algorithm, applicableTraces, hasIndeterminate);
    }

    private static Flux<Value> buildOnlyOneApplicableSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies, boolean preMatched) {

        val policyFluxes = compiledPolicies.stream()
                .map(policy -> createTracedPolicyFlux(policy, preMatched).defaultIfEmpty(Value.UNDEFINED)).toList();

        return Flux.combineLatest(policyFluxes, tracedPolicies -> {
            List<Value> applicableTraces = new ArrayList<>();
            boolean     hasIndeterminate = false;

            for (val traced : tracedPolicies) {
                if (traced instanceof Value v && !(v instanceof UndefinedValue)) {
                    val decision = TracedPolicyDecision.getDecision(v);
                    if (decision != Decision.NOT_APPLICABLE) {
                        applicableTraces.add(v);
                        if (decision == Decision.INDETERMINATE) {
                            hasIndeterminate = true;
                        }
                    }
                }
            }

            return buildOnlyOneApplicableResult(setName, algorithm, applicableTraces, hasIndeterminate);
        });
    }

    private static Value buildOnlyOneApplicableResult(String setName, String algorithm, List<Value> applicableTraces,
            boolean hasIndeterminate) {

        if (hasIndeterminate || applicableTraces.size() > 1) {
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.INDETERMINATE)
                    .policies(applicableTraces).build();
        }

        if (applicableTraces.size() == 1) {
            val traced = applicableTraces.getFirst();
            return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm)
                    .decision(TracedPolicyDecision.getDecision(traced))
                    .obligations(TracedPolicyDecision.getObligations(traced))
                    .advice(TracedPolicyDecision.getAdvice(traced)).resource(TracedPolicyDecision.getResource(traced))
                    .policies(applicableTraces).build();
        }

        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.NOT_APPLICABLE)
                .build();
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

        List<Value> evaluatedTraces = new ArrayList<>();

        for (val policy : compiledPolicies) {
            val matchResult = evaluateMatch(policy, ctx);

            if (matchResult instanceof MatchResult.Error errorResult) {
                evaluatedTraces.add(createIndeterminateTrace(policy.name(), "UNKNOWN", errorResult.error()));
                return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm)
                        .decision(Decision.INDETERMINATE).policies(evaluatedTraces).build();
            }

            if (matchResult instanceof MatchResult.NoMatch) {
                continue;
            }

            val traced   = evaluateTracedPolicy(policy, ctx);
            val decision = TracedPolicyDecision.getDecision(traced);
            evaluatedTraces.add(traced);

            if (decision != Decision.NOT_APPLICABLE) {
                return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(decision)
                        .obligations(TracedPolicyDecision.getObligations(traced))
                        .advice(TracedPolicyDecision.getAdvice(traced))
                        .resource(TracedPolicyDecision.getResource(traced)).policies(evaluatedTraces).build();
            }
        }

        return TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(Decision.NOT_APPLICABLE)
                .policies(evaluatedTraces).build();
    }

    private static Flux<Value> buildFirstApplicableSetStream(String setName, String algorithm,
            List<CompiledPolicy> compiledPolicies) {

        // Build from last to first, using switchMap chain
        Flux<FirstApplicableState> stateFlux = Flux.just(new FirstApplicableState(Decision.NOT_APPLICABLE, List.of()));

        for (var i = compiledPolicies.size() - 1; i >= 0; i--) {
            val policy            = compiledPolicies.get(i);
            val fallbackStateFlux = stateFlux;

            stateFlux = ExpressionCompiler.compiledExpressionToFlux(policy.matchExpression()).switchMap(matches -> {
                if (matches instanceof BooleanValue matchesBool) {
                    if (!matchesBool.value()) {
                        return fallbackStateFlux;
                    }
                    return ExpressionCompiler.compiledExpressionToFlux(policy.decisionExpression())
                            .switchMap(traced -> {
                                val decision = TracedPolicyDecision.getDecision(traced);
                                if (decision == Decision.NOT_APPLICABLE) {
                                    return fallbackStateFlux.map(state -> state.prependTrace(traced));
                                }
                                return Flux.just(new FirstApplicableState(decision, List.of(traced)));
                            });
                }
                // Target evaluation failed - create trace with error details
                val traces = new ArrayList<Value>();
                val error  = matches instanceof ErrorValue err ? err
                        : Value.error("Target expression must return Boolean, but was: " + matches);
                traces.add(createIndeterminateTrace(policy.name(), "UNKNOWN", error));
                return Flux.just(new FirstApplicableState(Decision.INDETERMINATE, traces));
            });
        }

        return stateFlux.map(
                state -> TracedPolicySetDecision.builder().name(setName).algorithm(algorithm).decision(state.decision())
                        .obligations(state.decision() != Decision.NOT_APPLICABLE && !state.traces().isEmpty()
                                ? TracedPolicyDecision.getObligations(state.traces().getFirst())
                                : Value.EMPTY_ARRAY)
                        .advice(state.decision() != Decision.NOT_APPLICABLE && !state.traces().isEmpty()
                                ? TracedPolicyDecision.getAdvice(state.traces().getFirst())
                                : Value.EMPTY_ARRAY)
                        .resource(state.decision() != Decision.NOT_APPLICABLE && !state.traces().isEmpty()
                                ? TracedPolicyDecision.getResource(state.traces().getFirst())
                                : Value.UNDEFINED)
                        .policies(state.traces()).build());
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
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression denyUnlessPermitPreMatched(String algorithm, List<CompiledPolicy> documents) {
        return genericPdpCombining(algorithm, documents, DENY_UNLESS_PERMIT);
    }

    /**
     * Compiles deny-overrides algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "deny-overrides")
     * @param documents
     * pre-matched policy documents to combine
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression denyOverridesPreMatched(String algorithm, List<CompiledPolicy> documents) {
        return genericPdpCombining(algorithm, documents, DENY_OVERRIDES);
    }

    /**
     * Compiles permit-overrides algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "permit-overrides")
     * @param documents
     * pre-matched policy documents to combine
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression permitOverridesPreMatched(String algorithm, List<CompiledPolicy> documents) {
        return genericPdpCombining(algorithm, documents, PERMIT_OVERRIDES);
    }

    /**
     * Compiles permit-unless-deny algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "permit-unless-deny")
     * @param documents
     * pre-matched policy documents to combine
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression permitUnlessDenyPreMatched(String algorithm, List<CompiledPolicy> documents) {
        return genericPdpCombining(algorithm, documents, PERMIT_UNLESS_DENY);
    }

    /**
     * Compiles only-one-applicable algorithm for PDP-level document combining.
     *
     * @param algorithm
     * the algorithm name in SAPL syntax (e.g., "only-one-applicable")
     * @param documents
     * pre-matched policy documents to combine
     *
     * @return compiled expression producing TracedPdpDecision
     */
    public static CompiledExpression onlyOneApplicablePreMatched(String algorithm, List<CompiledPolicy> documents) {
        return onlyOneApplicablePdp(algorithm, documents);
    }

    private static CompiledExpression genericPdpCombining(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic) {

        if (documents.isEmpty()) {
            return new PureExpression(ctx -> buildEmptyPdpDecision(ctx, algorithm, logic.getDefaultDecision()), true);
        }

        if (allPureOrConstant(documents)) {
            return new PureExpression(ctx -> evaluateGenericPdpPure(algorithm, documents, logic, ctx), true);
        } else {
            return new StreamExpression(buildGenericPdpStream(algorithm, documents, logic));
        }
    }

    private static Value buildEmptyPdpDecision(EvaluationContext ctx, String algorithm, Decision decision) {
        return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                .timestamp(ctx.timestamp()).algorithm(algorithm).decision(decision).build();
    }

    private static Value evaluateGenericPdpPure(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic, EvaluationContext ctx) {

        val accumulator = new TracedDecisionAccumulator(logic);

        for (val document : documents) {
            // At PDP level, documents are pre-matched, so evaluate directly
            val tracedDocument = evaluateTracedPolicy(document, ctx);
            accumulator.addTracedPolicy(tracedDocument);
        }

        return accumulator.buildPdpDecision(ctx, algorithm);
    }

    private static Flux<Value> buildGenericPdpStream(String algorithm, List<CompiledPolicy> documents,
            CombiningAlgorithmLogic logic) {

        val documentFluxes = documents.stream()
                .map(doc -> ExpressionCompiler.compiledExpressionToFlux(doc.decisionExpression())).toList();

        return Flux.combineLatest(documentFluxes, tracedDocuments -> tracedDocuments)
                .flatMap(tracedDocuments -> Mono.deferContextual(reactorCtx -> {
                    val ctx = reactorCtx.get(EvaluationContext.class);
                    val accumulator = new TracedDecisionAccumulator(logic);

                    for (val traced : tracedDocuments) {
                        if (traced instanceof Value v) {
                            accumulator.addTracedPolicy(v);
                        }
                    }

                    return Mono.just(accumulator.buildPdpDecision(ctx, algorithm));
                }));
    }

    // ========== Only-One-Applicable (PDP Level) ==========

    private static CompiledExpression onlyOneApplicablePdp(String algorithm, List<CompiledPolicy> documents) {

        if (documents.isEmpty()) {
            return new PureExpression(ctx -> buildEmptyPdpDecision(ctx, algorithm, Decision.NOT_APPLICABLE), true);
        }

        if (allPureOrConstant(documents)) {
            return new PureExpression(ctx -> evaluateOnlyOneApplicablePdpPure(algorithm, documents, ctx), true);
        }
        return new StreamExpression(buildOnlyOneApplicablePdpStream(algorithm, documents));
    }

    private static Value evaluateOnlyOneApplicablePdpPure(String algorithm, List<CompiledPolicy> documents,
            EvaluationContext ctx) {

        List<Value> applicableTraces = new ArrayList<>();
        boolean     hasIndeterminate = false;

        for (val document : documents) {
            val traced   = evaluateTracedPolicy(document, ctx);
            val decision = TracedPolicyDecision.getDecision(traced);

            if (decision != Decision.NOT_APPLICABLE) {
                applicableTraces.add(traced);
                if (decision == Decision.INDETERMINATE) {
                    hasIndeterminate = true;
                }
            }
        }

        return buildOnlyOneApplicablePdpResult(ctx, algorithm, applicableTraces, hasIndeterminate);
    }

    private static Flux<Value> buildOnlyOneApplicablePdpStream(String algorithm, List<CompiledPolicy> documents) {

        val documentFluxes = documents.stream()
                .map(doc -> ExpressionCompiler.compiledExpressionToFlux(doc.decisionExpression())).toList();

        return Flux.combineLatest(documentFluxes, tracedDocuments -> tracedDocuments)
                .flatMap(tracedDocuments -> Mono.deferContextual(reactorCtx -> {
                    val ctx              = reactorCtx.get(EvaluationContext.class);
                    List<Value> applicableTraces = new ArrayList<>();
                    boolean hasIndeterminate = false;

                    for (val traced : tracedDocuments) {
                        if (traced instanceof Value v) {
                            val decision = TracedPolicyDecision.getDecision(v);
                            if (decision != Decision.NOT_APPLICABLE) {
                                applicableTraces.add(v);
                                if (decision == Decision.INDETERMINATE) {
                                    hasIndeterminate = true;
                                }
                            }
                        }
                    }

                    return Mono
                            .just(buildOnlyOneApplicablePdpResult(ctx, algorithm, applicableTraces, hasIndeterminate));
                }));
    }

    private static Value buildOnlyOneApplicablePdpResult(EvaluationContext ctx, String algorithm,
            List<Value> applicableTraces, boolean hasIndeterminate) {

        if (hasIndeterminate || applicableTraces.size() > 1) {
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(Decision.INDETERMINATE)
                    .documents(applicableTraces).build();
        }

        if (applicableTraces.size() == 1) {
            val traced = applicableTraces.getFirst();
            return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                    .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                    .timestamp(ctx.timestamp()).algorithm(algorithm).decision(TracedPolicyDecision.getDecision(traced))
                    .obligations(TracedPolicyDecision.getObligations(traced))
                    .advice(TracedPolicyDecision.getAdvice(traced)).resource(TracedPolicyDecision.getResource(traced))
                    .documents(applicableTraces).build();
        }

        return TracedPdpDecision.builder().pdpId(ctx.pdpId()).configurationId(ctx.configurationId())
                .subscriptionId(ctx.subscriptionId()).subscription(ctx.authorizationSubscription())
                .timestamp(ctx.timestamp()).algorithm(algorithm).decision(Decision.NOT_APPLICABLE).build();
    }
}
