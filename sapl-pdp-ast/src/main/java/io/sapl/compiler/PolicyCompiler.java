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
package io.sapl.compiler;

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.*;
import io.sapl.api.pdp.*;
import io.sapl.api.pdp.traced.AttributeRecord;
import io.sapl.ast.Entitlement;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_NOT_ARRAY                 = "Unexpected Error: advice must return an array, but I got: %s.";
    private static final String ERROR_ADVICE_STATIC_ERROR              = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_BODY_RELATIVE_ACCESSOR           = "The policy body contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_BODY_STATIC_ERROR                = "Policy body statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_NOT_ARRAY            = "Unexpected Error: obligations must return an array, but I got: %s.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR         = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_TARGET_NOT_BOOLEAN               = "Target expressions must evaluate to Boolean, but got %s.";
    private static final String ERROR_TARGET_RELATIVE_ACCESSOR         = "The target expression contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_TARGET_STATIC_ERROR              = "The target expression statically evaluates to an error: %s.";
    private static final String ERROR_TARGET_STREAM_OPERATOR           = "Target expression must not contain attributes operators <>!.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR      = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_CONSTRAINT_TYPE       = "Unexpected error: obligations or advice did not evaluate to an Array. Got: obligations=%s and advice=%s. Indicates an implementation bug.";
    private static final String ERROR_UNEXPECTED_LIFT                  = "Unexpected expression type during stratum lifting: %s. Indicates an implementation bug.";

    /**
     * Compiles a policy AST into an executable CompiledDocument.
     *
     * @param policy the policy AST to compile
     * @param ctx the compilation context for variable and function resolution
     * @return a CompiledDocument that can evaluate the policy
     * @throws SaplCompilerException if the policy contains static errors
     */
    public CompiledDocument compilePolicy(Policy policy, CompilationContext ctx) {
        val decisionSource = new DecisionSource(SourceType.POLICY, policy.name(), ctx.getPdpId(),
                ctx.getConfigurationId(), null);
        val compiledTarget = policy.target() == null ? Value.TRUE
                : BooleanGuardCompiler.applyBooleanGuard(ExpressionCompiler.compile(policy.target(), ctx),
                        policy.target().location(), ERROR_TARGET_NOT_BOOLEAN);
        if (compiledTarget instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TARGET_STATIC_ERROR.formatted(error), policy.target().location());
        }
        return switch (compiledTarget) {
        case Value targetValue                                    ->
            compileWithConstantTarget(policy, targetValue, decisionSource, ctx);
        case PureOperator po when !po.isDependingOnSubscription() ->
            throw new SaplCompilerException(ERROR_TARGET_RELATIVE_ACCESSOR, policy.target().location());
        case PureOperator pureTarget                              ->
            compilePolicyEvaluation(policy, pureTarget, decisionSource, ctx);
        case StreamOperator ignored                               ->
            throw new SaplCompilerException(ERROR_TARGET_STREAM_OPERATOR, policy.target().location());
        };
    }

    private static CompiledDocument compileWithConstantTarget(Policy policy, Value targetValue,
            DecisionSource decisionSource, CompilationContext ctx) {
        if (Value.FALSE.equals(targetValue)) {
            return AuditableAuthorizationDecision.notApplicable(decisionSource);
        }
        return compilePolicyEvaluation(policy, targetValue, decisionSource, ctx);
    }

    private static CompiledDocument compilePolicyEvaluation(Policy policy, CompiledExpression targetExpression,
            DecisionSource decisionSource, CompilationContext ctx) {
        val compiledBody = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        return switch (compiledBody.bodyExpression()) {
        case Value bodyValue                                      ->
            compileConstraintsAndTransform(policy, targetExpression, bodyValue, decisionSource, ctx);
        case PureOperator po when !po.isDependingOnSubscription() ->
            throw new SaplCompilerException(ERROR_BODY_RELATIVE_ACCESSOR, policy.body().location());
        case PureOperator pureBody                                ->
            compileConstraintsAndTransform(policy, targetExpression, pureBody, decisionSource, ctx);
        case StreamOperator streamBody                            ->
            compileStreamPolicyConstraints(policy, targetExpression, streamBody, decisionSource, ctx);
        };
    }

    private static CompiledDocument compileConstraintsAndTransform(Policy policy, CompiledExpression targetExpression,
            CompiledExpression compiledBody, DecisionSource decisionSource, CompilationContext ctx) {
        if (compiledBody instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_BODY_STATIC_ERROR.formatted(error), policy.body().location());
        }
        if (Value.FALSE.equals(compiledBody)) {
            return AuditableAuthorizationDecision.notApplicable(decisionSource);
        }

        val location = policy.location();
        val decision = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val isSimple = policy.obligations().isEmpty() && policy.advice().isEmpty() && policy.transformation() == null;

        if (isSimple && compiledBody instanceof PureOperator pureBody) {
            return new SimplePurePolicy(targetExpression,
                    AuditableAuthorizationDecision.simpleDecision(decision, decisionSource),
                    AuditableAuthorizationDecision.notApplicable(decisionSource), pureBody, decisionSource);
        }

        val c = compileConstraints(policy, location, ctx);

        if (c.obligations() instanceof StreamOperator || c.advice() instanceof StreamOperator
                || c.resource() instanceof StreamOperator) {
            val pureBody = liftToPure(compiledBody, location);
            return new PureStreamPolicy(targetExpression, decision, pureBody, liftToStream(c.obligations()),
                    liftToStream(c.advice()), liftToStream(c.resource()), location, decisionSource);
        }

        if (compiledBody instanceof PureOperator || c.obligations() instanceof PureOperator
                || c.advice() instanceof PureOperator || c.resource() instanceof PureOperator) {
            return new PurePolicy(targetExpression, decision, compiledBody, c.obligations(), c.advice(), c.resource(),
                    location, decisionSource);
        }

        // Here compiledBody must be Value.TRUE, and obligations/advice must be
        // ArrayValue
        if (!(c.obligations() instanceof ArrayValue) || !(c.advice() instanceof ArrayValue)) {
            throw new SaplCompilerException(ERROR_UNEXPECTED_CONSTRAINT_TYPE.formatted(c.obligations(), c.advice()),
                    location);
        }
        return new AuditableAuthorizationDecision(decision, (ArrayValue) c.obligations(), (ArrayValue) c.advice(),
                (Value) c.resource(), null, decisionSource, null);
    }

    private static CompiledDocument compileStreamPolicyConstraints(Policy policy, CompiledExpression targetExpression,
            StreamOperator streamBody, DecisionSource decisionSource, CompilationContext ctx) {
        val location = policy.location();
        val decision = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val isSimple = policy.obligations().isEmpty() && policy.advice().isEmpty() && policy.transformation() == null;

        if (isSimple) {
            return new StreamPolicy(targetExpression,
                    AuditableAuthorizationDecision.simpleDecision(decision, decisionSource),
                    AuditableAuthorizationDecision.notApplicable(decisionSource), streamBody, decisionSource);
        }

        val c = compileConstraints(policy, location, ctx);

        // Determine highest stratum and lift all constraints to it
        boolean hasStream = c.obligations() instanceof StreamOperator || c.advice() instanceof StreamOperator
                || c.resource() instanceof StreamOperator;
        boolean hasPure   = c.obligations() instanceof PureOperator || c.advice() instanceof PureOperator
                || c.resource() instanceof PureOperator;

        if (hasStream) {
            return new StreamStreamPolicy(targetExpression, decision, streamBody, liftToStream(c.obligations()),
                    liftToStream(c.advice()), liftToStream(c.resource()), location, decisionSource);
        }
        if (hasPure) {
            return new StreamPurePolicy(targetExpression, decision, streamBody, liftToPure(c.obligations(), location),
                    liftToPure(c.advice(), location), liftToPure(c.resource(), location), location, decisionSource);
        }
        // All constraints are Values
        if (!(c.obligations() instanceof ArrayValue) || !(c.advice() instanceof ArrayValue)) {
            throw new SaplCompilerException(ERROR_UNEXPECTED_CONSTRAINT_TYPE.formatted(c.obligations(), c.advice()),
                    location);
        }
        return new StreamValuePolicy(targetExpression, decision, streamBody, (ArrayValue) c.obligations(),
                (ArrayValue) c.advice(), (Value) c.resource(), decisionSource);
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

    private static TracedAuthorizationDecision buildFromConstraintStreams(Object[] merged, Decision decision,
            List<AttributeRecord> baseAttributes, SourceLocation location, DecisionSource decisionSource) {
        val tracedObligationsValue = (TracedValue) merged[0];
        val obligationsValue       = tracedObligationsValue.value();
        val contributingAttributes = new ArrayList<>(baseAttributes);
        contributingAttributes.addAll(tracedObligationsValue.contributingAttributes());
        if (obligationsValue instanceof ErrorValue error) {
            return new TracedAuthorizationDecision(AuditableAuthorizationDecision.ofError(error, decisionSource),
                    contributingAttributes);
        }
        if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
            return new TracedAuthorizationDecision(AuditableAuthorizationDecision.ofError(
                    Value.errorAt(location, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)), decisionSource),
                    contributingAttributes);
        }
        val tracedAdviceValue = (TracedValue) merged[1];
        val adviceValue       = tracedAdviceValue.value();
        contributingAttributes.addAll(tracedAdviceValue.contributingAttributes());
        if (adviceValue instanceof ErrorValue error) {
            return new TracedAuthorizationDecision(AuditableAuthorizationDecision.ofError(error, decisionSource),
                    contributingAttributes);
        }
        if (!(adviceValue instanceof ArrayValue adviceArray)) {
            return new TracedAuthorizationDecision(
                    AuditableAuthorizationDecision.ofError(
                            Value.errorAt(location, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)), decisionSource),
                    contributingAttributes);
        }
        val tracedResourceValue = (TracedValue) merged[2];
        val resourceValue       = tracedResourceValue.value();
        contributingAttributes.addAll(tracedResourceValue.contributingAttributes());
        if (resourceValue instanceof ErrorValue error) {
            return new TracedAuthorizationDecision(AuditableAuthorizationDecision.ofError(error, decisionSource),
                    contributingAttributes);
        }
        val actualDecision = new AuditableAuthorizationDecision(decision, obligationsArray, adviceArray, resourceValue,
                Value.UNDEFINED, decisionSource, null);
        return new TracedAuthorizationDecision(actualDecision, contributingAttributes);
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

    record StreamPolicy(
            CompiledExpression targetExpression,
            AuditableAuthorizationDecision applicableDecision,
            AuditableAuthorizationDecision notApplicableDecision,
            StreamOperator body,
            DecisionSource decisionSource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.ofError(error, decisionSource), contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return new TracedAuthorizationDecision(notApplicableDecision, contributingAttributes);
                }
                return new TracedAuthorizationDecision(applicableDecision, contributingAttributes);
            });
        }
    }

    record StreamValuePolicy(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            ArrayValue obligations,
            ArrayValue advice,
            Value resource,
            DecisionSource decisionSource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.ofError(error, decisionSource), contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return new TracedAuthorizationDecision(AuditableAuthorizationDecision.notApplicable(decisionSource),
                            contributingAttributes);
                }
                return new TracedAuthorizationDecision(new AuditableAuthorizationDecision(decision, obligations, advice,
                        resource, Value.UNDEFINED, decisionSource, contributingAttributes), contributingAttributes);
            });
        }
    }

    record StreamPurePolicy(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            PureOperator obligations,
            PureOperator advice,
            PureOperator resource,
            SourceLocation policyLocation,
            DecisionSource decisionSource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.ofError(error, decisionSource), contributingAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.notApplicable(decisionSource), contributingAttributes));
                }
                return Flux
                        .deferContextual(
                                ctxView -> evaluateConstraints(ctxView.get(EvaluationContext.class), decisionSource))
                        .map(d -> new TracedAuthorizationDecision(d, contributingAttributes));
            });
        }

        private Flux<AuditableAuthorizationDecision> evaluateConstraints(EvaluationContext evalCtx,
                DecisionSource decisionSource) {
            val obligationsValue = obligations.evaluate(evalCtx);
            if (obligationsValue instanceof ErrorValue error) {
                return Flux.just(AuditableAuthorizationDecision.ofError(error, decisionSource));
            }
            if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                return Flux.just(AuditableAuthorizationDecision.ofError(
                        Value.errorAt(policyLocation, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)),
                        decisionSource));
            }
            val adviceValue = advice.evaluate(evalCtx);
            if (adviceValue instanceof ErrorValue error) {
                return Flux.just(AuditableAuthorizationDecision.ofError(error, decisionSource));
            }
            if (!(adviceValue instanceof ArrayValue adviceArray)) {
                return Flux.just(AuditableAuthorizationDecision.ofError(
                        Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)), decisionSource));
            }
            val resourceValue = resource.evaluate(evalCtx);
            if (resourceValue instanceof ErrorValue error) {
                return Flux.just(AuditableAuthorizationDecision.ofError(error, decisionSource));
            }
            return Flux.just(new AuditableAuthorizationDecision(decision, obligationsArray, adviceArray, resourceValue,
                    null, decisionSource, null));
        }
    }

    record PureStreamPolicy(
            CompiledExpression targetExpression,
            Decision decision,
            PureOperator body,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            SourceLocation policyLocation,
            DecisionSource decisionSource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx   = ctxView.get(EvaluationContext.class);
                val bodyValue = body.evaluate(evalCtx);
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.ofError(error, decisionSource), List.of()));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.notApplicable(decisionSource), List.of()));
                }
                return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                        merged -> buildFromConstraintStreams(merged, decision, List.of(), policyLocation,
                                decisionSource));
            });
        }
    }

    record StreamStreamPolicy(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            SourceLocation policyLocation,
            DecisionSource decisionSource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue      = tracedBodyValue.value();
                val bodyAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.ofError(error, decisionSource), bodyAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(new TracedAuthorizationDecision(
                            AuditableAuthorizationDecision.notApplicable(decisionSource), bodyAttributes));
                }
                return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                        merged -> buildFromConstraintStreams(merged, decision, bodyAttributes, policyLocation,
                                decisionSource));
            });
        }
    }

    record SimplePurePolicy(
            CompiledExpression targetExpression,
            AuditableAuthorizationDecision applicableDecision,
            AuditableAuthorizationDecision notApplicableDecision,
            PureOperator body,
            DecisionSource decisionSource) implements PureDocument {

        @Override
        public AuditableAuthorizationDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body.evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return AuditableAuthorizationDecision.ofError(error, decisionSource);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return notApplicableDecision;
            }
            return applicableDecision;
        }
    }

    record PurePolicy(
            CompiledExpression targetExpression,
            Decision decision,
            CompiledExpression body,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            SourceLocation policyLocation,
            DecisionSource decisionSource) implements PureDocument {
        @Override
        public AuditableAuthorizationDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body instanceof Value vb ? vb : ((PureOperator) body).evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return AuditableAuthorizationDecision.ofError(error, decisionSource);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return AuditableAuthorizationDecision.notApplicable(decisionSource);
            }
            val obligationsValue = obligations instanceof Value vb ? vb : ((PureOperator) obligations).evaluate(ctx);
            if (obligationsValue instanceof ErrorValue error) {
                return AuditableAuthorizationDecision.ofError(error, decisionSource);
            }
            if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                return AuditableAuthorizationDecision.ofError(
                        Value.errorAt(policyLocation, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)),
                        decisionSource);
            }
            val adviceValue = advice instanceof Value vb ? vb : ((PureOperator) advice).evaluate(ctx);
            if (adviceValue instanceof ErrorValue error) {
                return AuditableAuthorizationDecision.ofError(error, decisionSource);
            }
            if (!(adviceValue instanceof ArrayValue adviceArray)) {
                return AuditableAuthorizationDecision.ofError(
                        Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)), decisionSource);
            }
            val resourceValue = resource instanceof Value vb ? vb : ((PureOperator) resource).evaluate(ctx);
            if (resourceValue instanceof ErrorValue error) {
                return AuditableAuthorizationDecision.ofError(error, decisionSource);
            }
            return new AuditableAuthorizationDecision(decision, obligationsArray, adviceArray, resourceValue,
                    Value.UNDEFINED, decisionSource, null);
        }
    }

}
