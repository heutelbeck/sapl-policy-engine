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

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.policybody.PolicyBodyCompiler;
import io.sapl.compiler.policy.policybody.TracedPolicyBodyResultAndCoverage;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_STATIC_ERROR              = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR         = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR      = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE    = "Unexpected isApplicable type. Indicates implementation bug.";

    public CompiledPolicy compilePolicy(Policy policy, CompilationContext ctx) {
        ctx.resetForNextPolicy();
        val metadata                 = policy.metadata();
        val compiledBody             = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        val isApplicable             = compiledBody.bodyExpression();
        val decisionMaker            = compileDecisionMaker(policy, metadata, ctx);
        val coverage                 = assembleDecisionWithCoverage(compiledBody.coverageStream(), decisionMaker,
                metadata);
        val applicabilityAndDecision = compileApplicabilityAndDecision(isApplicable, decisionMaker, metadata);
        return new CompiledPolicy(isApplicable, decisionMaker, applicabilityAndDecision, coverage, metadata);
    }

    private DecisionMaker compileApplicabilityAndDecision(CompiledExpression isApplicable, DecisionMaker decision,
            PolicyMetadata metadata) {
        return switch (isApplicable) {
        case ErrorValue error           -> PolicyDecision.error(error, metadata);
        case BooleanValue(var b) when b -> decision;
        case BooleanValue ignored       -> PolicyDecision.notApplicable(metadata);
        case PureOperator po            -> PolicyDecision.error(Value.error("Unimplemented"), metadata);
        case StreamOperator so          -> PolicyDecision.error(Value.error("Unimplemented"), metadata);
        default                         ->
            PolicyDecision.error(Value.error(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), metadata);
        };
    }

    private static DecisionMaker compileDecisionMaker(Policy policy, PolicyMetadata metadata, CompilationContext ctx) {
        val constraints = compileConstraints(policy, policy.location(), ctx);
        val decision    = policy.entitlement().decision();
        return switch (constraints.nature()) {
        case CONSTANT -> new PolicyDecision(decision, (ArrayValue) constraints.obligations(),
                (ArrayValue) constraints.advice(), (Value) constraints.resource(), null, metadata, List.of());
        case PURE     -> new SimplePureDecisionMaker(decision, constraints.obligations(), constraints.advice(),
                constraints.resource(), metadata);
        case STREAM   ->
            new SimpleStreamDecisionMaker(decision, OperatorLiftUtil.liftToStream(constraints.obligations()),
                    OperatorLiftUtil.liftToStream(constraints.advice()),
                    OperatorLiftUtil.liftToStream(constraints.resource()), metadata);
        };
    }

    public record SimplePureDecisionMaker(
            Decision decision,
            CompiledExpression obligations,
            CompiledExpression PureOperator,
            CompiledExpression resource,
            PolicyMetadata metadata) implements PureDecisionMaker {
        @Override
        public PolicyDecision decide(List<AttributeRecord> bodyContributions, EvaluationContext ctx) {
            val obligationsArray = evaluate(obligations, ctx);
            if (obligationsArray instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            val adviceArray = evaluate(obligations, ctx);
            if (adviceArray instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            val resourceValue = evaluate(resource, ctx);
            if (resourceValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, metadata);
            }
            return new PolicyDecision(decision, (ArrayValue) obligationsArray, (ArrayValue) adviceArray, resourceValue,
                    Value.UNDEFINED, metadata, bodyContributions);
        }

        private Value evaluate(CompiledExpression expression, EvaluationContext ctx) {
            if (expression instanceof Value v) {
                return v;
            } else {
                return ((PureOperator) expression).evaluate(ctx);
            }
        }
    }

    public record SimpleStreamDecisionMaker(
            Decision decision,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            PolicyMetadata metadata) implements StreamDecisionMaker {
        @Override
        public Flux<PolicyDecision> decide(List<AttributeRecord> bodyContributions) {
            return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                    merged -> buildFromConstraintStreams(merged, decision, bodyContributions, metadata));
        }
    }

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
            case PolicyDecision pd       -> Flux.just(new PolicyDecisionWithCoverage(pd, policyCoverage));
            case PureDecisionMaker pdm   -> Flux.deferContextual(ctxView -> Flux.just(new PolicyDecisionWithCoverage(
                    pdm.decide(bodyAttrs, ctxView.get(EvaluationContext.class)), policyCoverage)));
            case StreamDecisionMaker sdm -> sdm.decide(bodyAttrs)
                    .map(policyDecision -> new PolicyDecisionWithCoverage(policyDecision, policyCoverage));
            };
        });
    }

    private static PolicyDecisionWithCoverage withCoverage(PolicyDecision decision, PolicyMetadata decisionSource,
            Coverage.BodyCoverage bodyCoverage) {
        val policyCoverage = new Coverage.PolicyCoverage(decisionSource, bodyCoverage);
        return new PolicyDecisionWithCoverage(decision, policyCoverage);
    }

    private static CompiledExpression compileConstraintArray(List<Expression> expressions, SourceLocation location,
            String name, CompilationContext ctx) {
        var result = ArrayCompiler.buildFromCompiled(
                expressions.stream().map(e -> ExpressionCompiler.compile(e, ctx)).toList(), location);
        if (result instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_CONSTRAINT_RELATIVE_ACCESSOR.formatted(name), location);
        }
        return ExpressionCompiler.fold(result, ctx);
    }

    public enum Nature {
        CONSTANT,
        PURE,
        STREAM
    }

    public record CompiledConstraints(
            Nature nature,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource) {}

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
        return new PolicyDecision(decision, (ArrayValue) obligationsValue, (ArrayValue) adviceValue, resourceValue,
                Value.UNDEFINED, policyMetadata, contributingAttributes);
    }
}
