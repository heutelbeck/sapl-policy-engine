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
import io.sapl.ast.Entitlement;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.compiler.ast.DocumentType;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.CompiledPolicy;
import io.sapl.compiler.targetexpression.TargetExpressionCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_NOT_ARRAY                 = "Unexpected Error: advice must return an array, but I got: %s.";
    private static final String ERROR_ADVICE_STATIC_ERROR              = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_BODY_RELATIVE_ACCESSOR           = "The policy body contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_BODY_STATIC_ERROR                = "Policy body statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_NOT_ARRAY            = "Unexpected Error: obligations must return an array, but I got: %s.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR         = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR      = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_CONSTRAINT_TYPE       = "Unexpected error: obligations or advice did not evaluate to an Array. Got: obligations=%s and advice=%s. Indicates an implementation bug.";
    private static final String ERROR_UNEXPECTED_LIFT                  = "Unexpected expression type during stratum lifting: %s. Indicates an implementation bug.";
    private static final String ERROR_TARGET_TYPE                      = "Target Expression. Indicates an implementation bug.";

    private record CompiledPolicyComponents(
            CompiledExpression target,
            CompiledPolicyBody body,
            CompiledConstraints constraints) {}

    /**
     * Compiles a policy AST into an executable PolicyBody.
     *
     * @param policy the policy AST to compile
     * @param ctx the compilation context for variable and function resolution
     * @return a PolicyBody that can evaluate the policy
     * @throws SaplCompilerException if the policy contains static errors
     */
    public CompiledPolicy compilePolicy(Policy policy, PureOperator schemaValidator, CompilationContext ctx) {
        val decisionSource = new PolicyMetadata(DocumentType.POLICY, policy.name(), policy.pdpId(),
                policy.configurationId(), policy.documentId(), null);
        val compiledTarget = TargetExpressionCompiler.compileTargetExpression(policy.target(), schemaValidator, ctx);
        val compiledBody   = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        val constraints    = compileConstraints(policy, policy.location(), ctx);
        val components     = new CompiledPolicyComponents(compiledTarget, compiledBody, constraints);
        val decisionMaker  = switch (compiledTarget) {
                           case Value ignored          -> compileWithConstantTarget(policy, components, decisionSource);
                           case PureOperator ignored   -> compilePolicyEvaluation(policy, components, decisionSource);
                           case StreamOperator ignored ->
                               throw new SaplCompilerException(ERROR_TARGET_TYPE, policy.location());
                           };
        val coverageStream = assembleDecisionWithCoverage(policy, components, decisionSource);
        return new CompiledPolicy(compiledTarget, decisionMaker, coverageStream);
    }

    private Flux<PolicyDecisionWithCoverage> assembleDecisionWithCoverage(Policy policy,
            CompiledPolicyComponents components, PolicyMetadata policyMetadata) {
        val bodyCoverage = components.body().coverageStream();
        val c            = components.constraints();
        val decision     = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val location     = policy.location();
        val isSimple     = policy.obligations().isEmpty() && policy.advice().isEmpty()
                && policy.transformation() == null;

        // Lift all constraints to streams - no optimization needed for coverage path
        val obligationsStream = liftToStream(c.obligations());
        val adviceStream      = liftToStream(c.advice());
        val resourceStream    = liftToStream(c.resource());

        return bodyCoverage.switchMap(bodyResult -> {
            val bodyValue         = bodyResult.value();
            val bodyAttrs         = bodyResult.contributingAttributes();
            val bodyConditionHits = bodyResult.bodyCoverage();

            if (bodyValue instanceof ErrorValue error) {
                return Flux.just(withCoverage(PolicyDecision.tracedError(error, policyMetadata, bodyAttrs), policy,
                        bodyConditionHits));
            }
            if (Value.FALSE.equals(bodyValue)) {
                return Flux.just(withCoverage(PolicyDecision.tracedNotApplicable(policyMetadata, bodyAttrs), policy,
                        bodyConditionHits));
            }
            if (isSimple) {
                return Flux.just(withCoverage(PolicyDecision.tracedSimpleDecision(decision, policyMetadata, bodyAttrs),
                        policy, bodyConditionHits));
            }

            // Body is TRUE - combine constraint streams
            return Flux.combineLatest(obligationsStream.stream(), adviceStream.stream(), resourceStream.stream(),
                    merged -> withCoverage(
                            buildFromConstraintStreams(merged, decision, bodyAttrs, location, policyMetadata), policy,
                            bodyConditionHits));
        });
    }

    private static PolicyDecisionWithCoverage withCoverage(PolicyDecision decision, Policy policy,
            Coverage.BodyCoverage bodyCoverage) {
        val policyCoverage = new Coverage.PolicyCoverage(policy.name(), null, null, null, bodyCoverage);
        return new PolicyDecisionWithCoverage(decision, new Coverage(List.of(policyCoverage)));
    }

    private static PolicyBody compileWithConstantTarget(Policy policy, CompiledPolicyComponents components,
            PolicyMetadata policyMetadata) {
        if (Value.FALSE.equals(components.target())) {
            return PolicyDecision.notApplicable(policyMetadata);
        }
        return compilePolicyEvaluation(policy, components, policyMetadata);
    }

    private static PolicyBody compilePolicyEvaluation(Policy policy, CompiledPolicyComponents components,
            PolicyMetadata policyMetadata) {
        return switch (components.body().bodyExpression()) {
        case Value bodyValue                                      ->
            compileConstraintsAndTransform(policy, components, bodyValue, policyMetadata);
        case PureOperator po when !po.isDependingOnSubscription() ->
            throw new SaplCompilerException(ERROR_BODY_RELATIVE_ACCESSOR, policy.body().location());
        case PureOperator pureBody                                ->
            compileConstraintsAndTransform(policy, components, pureBody, policyMetadata);
        case StreamOperator streamBody                            ->
            compileStreamPolicyConstraints(policy, components, streamBody, policyMetadata);
        };
    }

    private static PolicyBody compileConstraintsAndTransform(Policy policy, CompiledPolicyComponents components,
            CompiledExpression compiledBody, PolicyMetadata policyMetadata) {
        if (compiledBody instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_BODY_STATIC_ERROR.formatted(error), policy.body().location());
        }
        if (Value.FALSE.equals(compiledBody)) {
            return PolicyDecision.notApplicable(policyMetadata);
        }

        val location         = policy.location();
        val decision         = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val isSimple         = policy.obligations().isEmpty() && policy.advice().isEmpty()
                && policy.transformation() == null;
        val targetExpression = components.target();
        val c                = components.constraints();

        if (isSimple && compiledBody instanceof PureOperator pureBody) {
            return new SimplePurePolicyBody(targetExpression, PolicyDecision.simpleDecision(decision, policyMetadata),
                    PolicyDecision.notApplicable(policyMetadata), pureBody, policyMetadata);
        }

        if (c.obligations() instanceof StreamOperator || c.advice() instanceof StreamOperator
                || c.resource() instanceof StreamOperator) {
            val pureBody = liftToPure(compiledBody, location);
            return new PureStreamPolicyBody(targetExpression, decision, pureBody, liftToStream(c.obligations()),
                    liftToStream(c.advice()), liftToStream(c.resource()), location, policyMetadata);
        }

        if (compiledBody instanceof PureOperator || c.obligations() instanceof PureOperator
                || c.advice() instanceof PureOperator || c.resource() instanceof PureOperator) {
            return new PurePolicyBody(targetExpression, decision, compiledBody, c.obligations(), c.advice(),
                    c.resource(), location, policyMetadata);
        }

        // Here compiledBody must be Value.TRUE, and obligations/advice must be
        // ArrayValue
        if (!(c.obligations() instanceof ArrayValue) || !(c.advice() instanceof ArrayValue)) {
            throw new SaplCompilerException(ERROR_UNEXPECTED_CONSTRAINT_TYPE.formatted(c.obligations(), c.advice()),
                    location);
        }
        return new PolicyDecision(decision, (ArrayValue) c.obligations(), (ArrayValue) c.advice(), (Value) c.resource(),
                null, policyMetadata, null);
    }

    private static PolicyBody compileStreamPolicyConstraints(Policy policy, CompiledPolicyComponents components,
            StreamOperator streamBody, PolicyMetadata policyMetadata) {
        val location         = policy.location();
        val decision         = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val isSimple         = policy.obligations().isEmpty() && policy.advice().isEmpty()
                && policy.transformation() == null;
        val targetExpression = components.target();
        val c                = components.constraints();

        if (isSimple) {
            return new StreamPolicyBody(targetExpression, decision, streamBody, policyMetadata);
        }

        // Determine highest stratum and lift all constraints to it
        boolean hasStream = c.obligations() instanceof StreamOperator || c.advice() instanceof StreamOperator
                || c.resource() instanceof StreamOperator;
        boolean hasPure   = c.obligations() instanceof PureOperator || c.advice() instanceof PureOperator
                || c.resource() instanceof PureOperator;

        if (hasStream) {
            return new StreamStreamPolicyBody(targetExpression, decision, streamBody, liftToStream(c.obligations()),
                    liftToStream(c.advice()), liftToStream(c.resource()), location, policyMetadata);
        }
        if (hasPure) {
            return new StreamPurePolicyBody(targetExpression, decision, streamBody,
                    liftToPure(c.obligations(), location), liftToPure(c.advice(), location),
                    liftToPure(c.resource(), location), location, policyMetadata);
        }
        // All constraints are Values
        if (!(c.obligations() instanceof ArrayValue) || !(c.advice() instanceof ArrayValue)) {
            throw new SaplCompilerException(ERROR_UNEXPECTED_CONSTRAINT_TYPE.formatted(c.obligations(), c.advice()),
                    location);
        }
        return new StreamValuePolicyBody(targetExpression, decision, streamBody, (ArrayValue) c.obligations(),
                (ArrayValue) c.advice(), (Value) c.resource(), policyMetadata);
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

    record CompiledConstraints(
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
        return new CompiledConstraints(obligations, advice, ExpressionCompiler.fold(resource, ctx));
    }

    private static PureOperator liftToPure(CompiledExpression expr, SourceLocation location) {
        return switch (expr) {
        case Value v        -> new ConstantPure(v, location);
        case PureOperator p -> p;
        default             -> throw new SaplCompilerException(ERROR_UNEXPECTED_LIFT.formatted(expr), location);
        };
    }

    private static StreamOperator liftToStream(CompiledExpression expr) {
        return switch (expr) {
        case Value v          -> new ConstantStream(v);
        case PureOperator p   -> new PureToStream(p);
        case StreamOperator s -> s;
        };
    }

    private static PolicyDecision buildFromConstraintStreams(Object[] merged, Decision decision,
            List<AttributeRecord> baseAttributes, SourceLocation location, PolicyMetadata policyMetadata) {
        val tracedObligationsValue = (TracedValue) merged[0];
        val obligationsValue       = tracedObligationsValue.value();
        val contributingAttributes = new ArrayList<>(baseAttributes);
        contributingAttributes.addAll(tracedObligationsValue.contributingAttributes());
        if (obligationsValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
            return PolicyDecision.tracedError(
                    Value.errorAt(location, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)), policyMetadata,
                    contributingAttributes);
        }
        val tracedAdviceValue = (TracedValue) merged[1];
        val adviceValue       = tracedAdviceValue.value();
        contributingAttributes.addAll(tracedAdviceValue.contributingAttributes());
        if (adviceValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        if (!(adviceValue instanceof ArrayValue adviceArray)) {
            return PolicyDecision.tracedError(Value.errorAt(location, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)),
                    policyMetadata, contributingAttributes);
        }
        val tracedResourceValue = (TracedValue) merged[2];
        val resourceValue       = tracedResourceValue.value();
        contributingAttributes.addAll(tracedResourceValue.contributingAttributes());
        if (resourceValue instanceof ErrorValue error) {
            return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
        }
        return new PolicyDecision(decision, obligationsArray, adviceArray, resourceValue, Value.UNDEFINED,
                policyMetadata, contributingAttributes);
    }

    record ConstantPure(Value value, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return value;
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }
    }

    record ConstantStream(Value value) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.just(new TracedValue(value, List.of()));
        }
    }

    record PureToStream(PureOperator pure) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx = ctxView.get(EvaluationContext.class);
                return Flux.just(new TracedValue(pure.evaluate(evalCtx), List.of()));
            });
        }
    }

    record StreamPolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.StreamPolicyBody {
        @Override
        public Flux<PolicyDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return PolicyDecision.tracedNotApplicable(policyMetadata, contributingAttributes);
                }
                return PolicyDecision.tracedSimpleDecision(decision, policyMetadata, contributingAttributes);
            });
        }
    }

    record StreamValuePolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            ArrayValue obligations,
            ArrayValue advice,
            Value resource,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.StreamPolicyBody {
        @Override
        public Flux<PolicyDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return PolicyDecision.tracedError(error, policyMetadata, contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return PolicyDecision.tracedNotApplicable(policyMetadata, contributingAttributes);
                }
                return new PolicyDecision(decision, obligations, advice, resource, Value.UNDEFINED, policyMetadata,
                        contributingAttributes);
            });
        }
    }

    record StreamPurePolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            PureOperator obligations,
            PureOperator advice,
            PureOperator resource,
            SourceLocation policyLocation,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.StreamPolicyBody {
        @Override
        public Flux<PolicyDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(PolicyDecision.tracedError(error, policyMetadata, contributingAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(PolicyDecision.tracedNotApplicable(policyMetadata, contributingAttributes));
                }
                return Flux
                        .deferContextual(
                                ctxView -> evaluateConstraints(ctxView.get(EvaluationContext.class), policyMetadata))
                        .map(d -> d.with(contributingAttributes));
            });
        }

        private Flux<PolicyDecision> evaluateConstraints(EvaluationContext evalCtx, PolicyMetadata policyMetadata) {
            val obligationsValue = obligations.evaluate(evalCtx);
            if (obligationsValue instanceof ErrorValue error) {
                return Flux.just(PolicyDecision.error(error, policyMetadata));
            }
            if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                return Flux.just(PolicyDecision.error(
                        Value.errorAt(policyLocation, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)),
                        policyMetadata));
            }
            val adviceValue = advice.evaluate(evalCtx);
            if (adviceValue instanceof ErrorValue error) {
                return Flux.just(PolicyDecision.error(error, policyMetadata));
            }
            if (!(adviceValue instanceof ArrayValue adviceArray)) {
                return Flux.just(PolicyDecision.error(
                        Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)), policyMetadata));
            }
            val resourceValue = resource.evaluate(evalCtx);
            if (resourceValue instanceof ErrorValue error) {
                return Flux.just(PolicyDecision.error(error, policyMetadata));
            }
            return Flux.just(new PolicyDecision(decision, obligationsArray, adviceArray, resourceValue, null,
                    policyMetadata, null));
        }
    }

    record PureStreamPolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            PureOperator body,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            SourceLocation policyLocation,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.StreamPolicyBody {
        @Override
        public Flux<PolicyDecision> stream() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx   = ctxView.get(EvaluationContext.class);
                val bodyValue = body.evaluate(evalCtx);
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(PolicyDecision.error(error, policyMetadata));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(PolicyDecision.notApplicable(policyMetadata));
                }
                return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                        merged -> buildFromConstraintStreams(merged, decision, List.of(), policyLocation,
                                policyMetadata));
            });
        }
    }

    record StreamStreamPolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            SourceLocation policyLocation,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.StreamPolicyBody {
        @Override
        public Flux<PolicyDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue      = tracedBodyValue.value();
                val bodyAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(PolicyDecision.tracedError(error, policyMetadata, bodyAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(PolicyDecision.tracedNotApplicable(policyMetadata, bodyAttributes));
                }
                return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                        merged -> buildFromConstraintStreams(merged, decision, bodyAttributes, policyLocation,
                                policyMetadata));
            });
        }
    }

    record SimplePurePolicyBody(
            CompiledExpression targetExpression,
            PolicyDecision applicableDecision,
            PolicyDecision notApplicableDecision,
            PureOperator body,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.PurePolicyBody {

        @Override
        public PolicyDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body.evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, policyMetadata);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return notApplicableDecision;
            }
            return applicableDecision;
        }
    }

    record PurePolicyBody(
            CompiledExpression targetExpression,
            Decision decision,
            CompiledExpression body,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            SourceLocation policyLocation,
            PolicyMetadata policyMetadata) implements io.sapl.compiler.policy.PurePolicyBody {
        @Override
        public PolicyDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body instanceof Value vb ? vb : ((PureOperator) body).evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, policyMetadata);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return PolicyDecision.notApplicable(policyMetadata);
            }
            val obligationsValue = obligations instanceof Value vb ? vb : ((PureOperator) obligations).evaluate(ctx);
            if (obligationsValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, policyMetadata);
            }
            if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                return PolicyDecision.error(
                        Value.errorAt(policyLocation, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)),
                        policyMetadata);
            }
            val adviceValue = advice instanceof Value vb ? vb : ((PureOperator) advice).evaluate(ctx);
            if (adviceValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, policyMetadata);
            }
            if (!(adviceValue instanceof ArrayValue adviceArray)) {
                return PolicyDecision.error(
                        Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)), policyMetadata);
            }
            val resourceValue = resource instanceof Value vb ? vb : ((PureOperator) resource).evaluate(ctx);
            if (resourceValue instanceof ErrorValue error) {
                return PolicyDecision.error(error, policyMetadata);
            }
            return new PolicyDecision(decision, obligationsArray, adviceArray, resourceValue, Value.UNDEFINED,
                    policyMetadata, null);
        }
    }

}
