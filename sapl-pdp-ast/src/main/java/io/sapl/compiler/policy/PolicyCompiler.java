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
package io.sapl.compiler.policy;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policy.policybody.PolicyBodyCompiler;
import io.sapl.compiler.policy.policybody.TracedPolicyBodyResultAndCoverage;
import io.sapl.compiler.util.Nature;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Compiles SAPL policies into executable decision makers.
 * <p>
 * A {@link CompiledPolicy} provides four entry points:
 * <ul>
 * <li>{@code isApplicable} - Evaluates only the policy body for applicability
 * checking.</li>
 * <li>{@code decisionMaker} - Evaluates constraints assuming applicability
 * (e.g., the PDP after determining applicability).</li>
 * <li>{@code applicabilityAndDecision} - Combined applicability and constraint
 * evaluation (e.g., in policy sets walking the contained polices).</li>
 * <li>{@code coverage} - Stream emitting decisions with coverage data for
 * testing.</li>
 * </ul>
 */
@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_STATIC_ERROR              = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR         = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR      = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE    = "Unexpected isApplicable type. Indicates implementation bug.";

    /**
     * Compiles a policy AST into an executable compiled policy.
     *
     * @param policy the policy AST node
     * @param ctx the compilation context
     * @return the compiled policy with decision makers and coverage streams
     */
    public CompiledPolicy compilePolicy(Policy policy, CompilationContext ctx) {
        ctx.resetForNextPolicy();
        val metadata                 = policy.metadata();
        val compiledBody             = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        val isApplicable             = compiledBody.bodyExpression();
        val decisionMaker            = compileDecisionMaker(policy, metadata, ctx);
        val coverage                 = assembleDecisionWithCoverage(compiledBody.coverageStream(), decisionMaker,
                metadata);
        val hasConstraints           = hasConstraints(policy);
        val applicabilityAndDecision = compileApplicabilityAndDecision(isApplicable, decisionMaker, metadata);
        return new CompiledPolicy(isApplicable, decisionMaker, applicabilityAndDecision, coverage, metadata,
                hasConstraints);
    }

    /**
     * @return true if the policy has obligations, advice, or a transformation
     */
    private static boolean hasConstraints(Policy policy) {
        return !policy.obligations().isEmpty() || !policy.advice().isEmpty() || policy.transformation() != null;
    }

    /**
     * Wraps the decision maker with applicability checking based on the body
     * expression type.
     * Used by policy sets walking contained policies.
     *
     * @param isApplicable the compiled body expression determining applicability
     * @param decision the decision maker for constraints evaluation
     * @param metadata the policy metadata
     * @return a decision maker that combines applicability and constraint
     * evaluation
     */
    private DecisionMaker compileApplicabilityAndDecision(CompiledExpression isApplicable, DecisionMaker decision,
            PolicyMetadata metadata) {
        return switch (isApplicable) {
        case ErrorValue error                                                 -> PolicyDecision.error(error, metadata);
        case BooleanValue(var b) when b                                       -> decision;
        case BooleanValue ignored                                             -> PolicyDecision.notApplicable(metadata);
        case PureOperator po when decision instanceof StreamDecisionMaker sdm ->
            new PureBodyStreamConstraintsDecisionMaker(po, sdm, metadata);
        case PureOperator po                                                  ->
            new ApplicabilityCheckingPureDecisionMaker(po, decision, metadata);
        case StreamOperator so                                                ->
            new ApplicabilityCheckingStreamDecisionMaker(so, decision, metadata);
        default                                                               ->
            PolicyDecision.error(Value.error(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), metadata);
        };
    }

    /**
     * Compiles the policy constraints into a decision maker based on their nature.
     * Used by the PDP after determining applicability.
     *
     * @param policy the policy AST node
     * @param metadata the policy metadata
     * @param ctx the compilation context
     * @return a constant, pure, or stream decision maker
     */
    private static DecisionMaker compileDecisionMaker(Policy policy, PolicyMetadata metadata, CompilationContext ctx) {
        val constraints = compileConstraints(policy, policy.location(), ctx);
        val decision    = policy.entitlement().decision();
        return switch (constraints.nature()) {
        case CONSTANT -> PolicyDecision.tracedDecision(decision, (ArrayValue) constraints.obligations(),
                (ArrayValue) constraints.advice(), (Value) constraints.resource(), metadata, List.of());
        case PURE     -> new SimplePureDecisionMaker(decision, constraints.obligations(), constraints.advice(),
                constraints.resource(), metadata);
        case STREAM   ->
            new SimpleStreamDecisionMaker(decision, OperatorLiftUtil.liftToStream(constraints.obligations()),
                    OperatorLiftUtil.liftToStream(constraints.advice()),
                    OperatorLiftUtil.liftToStream(constraints.resource()), metadata);
        };
    }

    /**
     * Compiles policy constraints (obligations, advice, transformation).
     *
     * @param policy the policy AST node
     * @param location the source location for error reporting
     * @param ctx the compilation context
     * @return compiled constraints with their determined nature
     */
    private static CompiledConstraints compileConstraints(Policy policy, SourceLocation location,
            CompilationContext ctx) {
        val obligations = compileConstraintArray(policy.obligations(), location, "Obligation", ctx);
        if (obligations instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_OBLIGATIONS_STATIC_ERROR.formatted(error), location);
        }
        val advice = compileConstraintArray(policy.advice(), location, "Advice", ctx);
        if (advice instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_ADVICE_STATIC_ERROR.formatted(error), location);
        }
        var resource = policy.transformation() == null ? Value.UNDEFINED
                : ExpressionCompiler.compile(policy.transformation(), ctx);
        if (resource instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_STATIC_ERROR.formatted(error), location);
        }
        if (resource instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_RELATIVE_ACCESSOR, location);
        }
        var nature = Nature.CONSTANT;
        if (obligations instanceof StreamOperator || advice instanceof StreamOperator
                || resource instanceof StreamOperator) {
            nature = Nature.STREAM;
        } else if (obligations instanceof PureOperator || advice instanceof PureOperator
                || resource instanceof PureOperator) {
            nature = Nature.PURE;
        }
        return new CompiledConstraints(nature, obligations, advice, ExpressionCompiler.fold(resource, ctx));
    }

    /**
     * Compiles a list of constraint expressions into an array expression.
     *
     * @param expressions the constraint expressions
     * @param location the source location for error reporting
     * @param name the constraint type name for error messages
     * @param ctx the compilation context
     * @return the compiled array expression
     */
    private static CompiledExpression compileConstraintArray(List<Expression> expressions, SourceLocation location,
            String name, CompilationContext ctx) {
        var result = ArrayCompiler.buildFromCompiled(
                expressions.stream().map(e -> ExpressionCompiler.compile(e, ctx)).toList(), location);
        if (result instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_CONSTRAINT_RELATIVE_ACCESSOR.formatted(name), location);
        }
        return ExpressionCompiler.fold(result, ctx);
    }

    /**
     * Creates a coverage-tracking decision stream from body coverage and decision
     * maker.
     *
     * @param bodyCoverage the body coverage stream
     * @param decisionMaker the decision maker
     * @param policyMetadata the policy metadata
     * @return a flux emitting policy decisions with coverage information
     */
    private static Flux<PolicyDecisionWithCoverage> assembleDecisionWithCoverage(
            Flux<TracedPolicyBodyResultAndCoverage> bodyCoverage, DecisionMaker decisionMaker,
            PolicyMetadata policyMetadata) {
        return bodyCoverage.switchMap(bodyResult -> {
            val bodyValue         = bodyResult.value().value();
            val bodyAttrs         = bodyResult.value().contributingAttributes();
            val bodyConditionHits = bodyResult.bodyCoverage();
            val policyCoverage    = new Coverage.PolicyCoverage(policyMetadata, bodyConditionHits);
            if (bodyValue instanceof ErrorValue error) {
                return Flux.just(withCoverage(PolicyDecision.tracedError(error, policyMetadata, bodyAttrs),
                        policyMetadata, bodyConditionHits));
            }
            if (Value.FALSE.equals(bodyValue)) {
                return Flux.just(withCoverage(PolicyDecision.tracedNotApplicable(policyMetadata, bodyAttrs),
                        policyMetadata, bodyConditionHits));
            }
            return switch (decisionMaker) {
            case PDPDecision pd          ->
                Flux.just(new PolicyDecisionWithCoverage((PolicyDecision) pd, policyCoverage));
            case PureDecisionMaker pdm   -> Flux.deferContextual(ctxView -> Flux.just(new PolicyDecisionWithCoverage(
                    (PolicyDecision) pdm.decide(bodyAttrs, ctxView.get(EvaluationContext.class)), policyCoverage)));
            case StreamDecisionMaker sdm -> sdm.decide(bodyAttrs).map(
                    policyDecision -> new PolicyDecisionWithCoverage((PolicyDecision) policyDecision, policyCoverage));
            };
        });
    }

    /**
     * Wraps a policy decision with coverage information.
     *
     * @param decision the policy decision
     * @param decisionSource the policy metadata
     * @param bodyCoverage the body coverage data
     * @return the decision with coverage
     */
    private static PolicyDecisionWithCoverage withCoverage(PolicyDecision decision, PolicyMetadata decisionSource,
            Coverage.BodyCoverage bodyCoverage) {
        val policyCoverage = new Coverage.PolicyCoverage(decisionSource, bodyCoverage);
        return new PolicyDecisionWithCoverage(decision, policyCoverage);
    }

    /**
     * Builds a policy decision from merged constraint stream values.
     *
     * @param merged the merged stream values [obligations, advice, resource]
     * @param decision the entitlement decision
     * @param baseAttributes the contributing attributes from body evaluation
     * @param policyMetadata the policy metadata
     * @return the assembled policy decision
     */
    private static PolicyDecision buildFromConstraintStreams(Object[] merged, Decision decision,
            List<AttributeRecord> baseAttributes, PolicyMetadata policyMetadata) {
        val tracedObligationsValue = (TracedValue) merged[0];
        val obligationsValue       = tracedObligationsValue.value();
        val contributingAttributes = new ArrayList<>(baseAttributes);
        contributingAttributes.addAll(tracedObligationsValue.contributingAttributes());
        if (obligationsValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        val tracedAdviceValue = (TracedValue) merged[1];
        val adviceValue       = tracedAdviceValue.value();
        contributingAttributes.addAll(tracedAdviceValue.contributingAttributes());
        if (adviceValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        val tracedResourceValue = (TracedValue) merged[2];
        val resourceValue       = tracedResourceValue.value();
        contributingAttributes.addAll(tracedResourceValue.contributingAttributes());
        if (resourceValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        return PolicyDecision.tracedDecision(decision, (ArrayValue) obligationsValue, (ArrayValue) adviceValue,
                resourceValue, policyMetadata, contributingAttributes);
    }

    /**
     * Compiled policy constraints with their nature classification.
     *
     * @param nature the complexity classification
     * @param obligations the compiled obligations array
     * @param advice the compiled advice array
     * @param resource the compiled transformation expression
     */
    public record CompiledConstraints(
            Nature nature,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource) {}

    /**
     * Decision maker for policies with pure constraints requiring runtime
     * evaluation.
     *
     * @param decision the entitlement decision
     * @param obligations the obligations expression
     * @param advice the advice expression
     * @param resource the transformation expression
     * @param metadata the policy metadata
     */
    public record SimplePureDecisionMaker(
            Decision decision,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            PolicyMetadata metadata) implements PureDecisionMaker {

        @Override
        public PolicyDecision decide(List<AttributeRecord> bodyContributions, EvaluationContext ctx) {
            val obligationsArray = evaluate(obligations, ctx);
            if (obligationsArray instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            val adviceArray = evaluate(advice, ctx);
            if (adviceArray instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            val resourceValue = evaluate(resource, ctx);
            if (resourceValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            return PolicyDecision.tracedDecision(decision, (ArrayValue) obligationsArray, (ArrayValue) adviceArray,
                    resourceValue, metadata, bodyContributions);
        }

        private Value evaluate(CompiledExpression expression, EvaluationContext ctx) {
            if (expression instanceof Value v) {
                return v;
            } else {
                return ((PureOperator) expression).evaluate(ctx);
            }
        }
    }

    /**
     * Decision maker for policies with streaming constraints.
     *
     * @param decision the entitlement decision
     * @param obligations the obligations stream operator
     * @param advice the advice stream operator
     * @param resource the transformation stream operator
     * @param metadata the policy metadata
     */
    public record SimpleStreamDecisionMaker(
            Decision decision,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            PolicyMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                    merged -> buildFromConstraintStreams(merged, decision, knownContributions, metadata));
        }
    }

    /**
     * Decision maker for pure applicability check with non-streaming constraints.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy metadata
     */
    public record ApplicabilityCheckingPureDecisionMaker(
            PureOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicyMetadata metadata) implements PureDecisionMaker {

        @Override
        public PDPDecision decide(List<AttributeRecord> bodyContributions, EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (decisionMaker) {
                case PDPDecision pd              -> pd;
                case PureDecisionMaker pdm       -> pdm.decide(bodyContributions, ctx);
                case StreamDecisionMaker ignored ->
                    throw new IllegalStateException("StreamDecisionMaker in pure applicability context");
                };
            }
            return PolicyDecision.notApplicable(metadata);
        }
    }

    /**
     * Decision maker for streaming applicability check.
     *
     * @param isApplicable the stream operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy metadata
     */
    public record ApplicabilityCheckingStreamDecisionMaker(
            StreamOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicyMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return isApplicable.stream().switchMap(tracedApplicability -> {
                val applicabilityValue = tracedApplicability.value();
                if (applicabilityValue instanceof ErrorValue error) {
                    return Flux.just(PolicyDecision.error(error, metadata));
                }
                if (applicabilityValue instanceof BooleanValue(var b) && b) {
                    return switch (decisionMaker) {
                    case PDPDecision pd          -> Flux.just(pd);
                    case PureDecisionMaker pdm   -> Flux.deferContextual(
                            ctxView -> Flux.just(pdm.decide(knownContributions, ctxView.get(EvaluationContext.class))));
                    case StreamDecisionMaker sdm -> sdm.decide(knownContributions);
                    };
                }
                return Flux.just(PolicyDecision.notApplicable(metadata));
            });
        }
    }

    /**
     * Decision maker for pure applicability check with streaming constraints.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param streamDecisionMaker the streaming decision maker for constraints
     * @param metadata the policy metadata
     */
    public record PureBodyStreamConstraintsDecisionMaker(
            PureOperator isApplicable,
            StreamDecisionMaker streamDecisionMaker,
            PolicyMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return Flux.deferContextual(ctxView -> {
                val ctx                 = ctxView.get(EvaluationContext.class);
                val applicabilityResult = isApplicable.evaluate(ctx);
                if (applicabilityResult instanceof ErrorValue error) {
                    return Flux.just(PolicyDecision.error(error, metadata));
                }
                if (applicabilityResult instanceof BooleanValue(var b) && b) {
                    return streamDecisionMaker.decide(knownContributions);
                }
                return Flux.just(PolicyDecision.notApplicable(metadata));
            });
        }
    }
}
