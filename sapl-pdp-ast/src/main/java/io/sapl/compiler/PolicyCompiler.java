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
import java.util.concurrent.CompletableFuture;

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
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_NOT_ARRAY                 = "Unexpected Error: advice must return an array, but I got: %s.";
    private static final String ERROR_BODY_RELATIVE_ACCESSOR           = "The policy body contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_NOT_ARRAY            = "Unexpected Error: obligations must return an array, but I got: %s.";
    private static final String ERROR_TARGET_NOT_BOOLEAN               = "Target expressions must evaluate to Boolean, but got %s.";
    private static final String ERROR_TARGET_RELATIVE_ACCESSOR         = "The target expression contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_TARGET_STATIC_ERROR              = "The target expression statically evaluates to an error: %s.";
    private static final String ERROR_TARGET_STREAM_OPERATOR           = "Target expression must not contain attributes operators <>!.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_UNEXPECTED_CONSTRAINT_TYPE       = "Unexpected error: obligations or advice did not evaluate to an Array. Got: obligations=%s and advice=%s. Indicates an implementation bug.";

    private static final AuthorizationDecision UNIMPLEMENTED = AuthorizationDecision.ofError("UNIMPLEMENTED!");

    /**
     * Compiles a policy AST into an executable CompiledDocument.
     *
     * @param policy the policy AST to compile
     * @param ctx the compilation context for variable and function resolution
     * @return a CompiledDocument that can evaluate the policy
     * @throws SaplCompilerException if the policy contains static errors
     */
    public CompiledDocument compilePolicy(Policy policy, CompilationContext ctx) {
        val compiledTarget = policy.target() == null ? Value.TRUE
                : BooleanGuardCompiler.applyBooleanGuard(ExpressionCompiler.compile(policy.target(), ctx),
                        policy.target().location(), ERROR_TARGET_NOT_BOOLEAN);
        if (compiledTarget instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TARGET_STATIC_ERROR.formatted(error), policy.target().location());
        }
        return switch (compiledTarget) {
        case Value targetValue                                    ->
            compileWithConstantTarget(policy, targetValue, ctx);
        case PureOperator po when !po.isDependingOnSubscription() ->
            throw new SaplCompilerException(ERROR_TARGET_RELATIVE_ACCESSOR, policy.target().location());
        case PureOperator pureTarget                              -> compilePolicyEvaluation(policy, pureTarget, ctx);
        case StreamOperator sto                                   ->
            throw new SaplCompilerException(ERROR_TARGET_STREAM_OPERATOR, policy.target().location());
        };
    }

    private static CompiledDocument compileWithConstantTarget(Policy policy, Value targetValue,
            CompilationContext ctx) {
        if (Value.FALSE.equals(targetValue)) {
            return AuthorizationDecision.NOT_APPLICABLE;
        }
        return compilePolicyEvaluation(policy, targetValue, ctx);
    }

    private static CompiledDocument compilePolicyEvaluation(Policy policy, CompiledExpression targetExpression,
            CompilationContext ctx) {
        val compiledBody = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        return switch (compiledBody.bodyExpression()) {
        case Value bodyValue                                      ->
            compileConstraintsAndTransform(policy, targetExpression, bodyValue, ctx);
        case PureOperator po when !po.isDependingOnSubscription() ->
            throw new SaplCompilerException(ERROR_BODY_RELATIVE_ACCESSOR, policy.body().location());
        case PureOperator pureBody                                ->
            compileConstraintsAndTransform(policy, targetExpression, pureBody, ctx);
        case StreamOperator sto                                   -> UNIMPLEMENTED;
        };
    }

    private static CompiledDocument compileConstraintsAndTransform(Policy policy, CompiledExpression targetExpression,
            CompiledExpression compiledBody, CompilationContext ctx) {
        if (compiledBody instanceof ErrorValue error) {
            return AuthorizationDecision.ofError(error);
        }
        if (Value.FALSE.equals(compiledBody)) {
            return AuthorizationDecision.NOT_APPLICABLE;
        }

        val location = policy.location();
        val decision = policy.entitlement() == Entitlement.PERMIT ? Decision.PERMIT : Decision.DENY;
        val isSimple = policy.obligations().isEmpty() && policy.advice().isEmpty() && policy.transformation() == null;

        if (isSimple && compiledBody instanceof PureOperator pureBody) {
            return new SimplePurePolicy(targetExpression, new AuthorizationDecision(decision, Value.EMPTY_ARRAY,
                    Value.EMPTY_ARRAY, Value.UNDEFINED, Value.UNDEFINED), pureBody);
        }

        val obligations = compileConstraintArray(policy.obligations(), location, "Obligation", ctx);
        if (obligations instanceof ErrorValue error) {
            return AuthorizationDecision.ofError(error);
        }

        val advice = compileConstraintArray(policy.advice(), location, "Advice", ctx);
        if (advice instanceof ErrorValue error) {
            return AuthorizationDecision.ofError(error);
        }

        var resource = policy.transformation() == null ? Value.UNDEFINED
                : ExpressionCompiler.compile(policy.transformation(), ctx);
        if (resource instanceof ErrorValue error) {
            return AuthorizationDecision.ofError(error);
        }
        if (resource instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_RELATIVE_ACCESSOR, location);
        }
        resource = ExpressionCompiler.fold(resource, ctx);

        if (obligations instanceof StreamOperator || advice instanceof StreamOperator
                || resource instanceof StreamOperator) {
            return UNIMPLEMENTED;
        }

        if (compiledBody instanceof PureOperator || obligations instanceof PureOperator
                || advice instanceof PureOperator || resource instanceof PureOperator) {
            return new PurePolicy(targetExpression, decision, compiledBody, obligations, advice, resource, location);
        }

        // Here compiledBody must be Value.TRUE, and obligations/advice must be
        // ArrayValue
        if (!(obligations instanceof ArrayValue) || !(advice instanceof ArrayValue)) {
            throw new SaplCompilerException(ERROR_UNEXPECTED_CONSTRAINT_TYPE.formatted(obligations, advice), location);
        }
        return new AuthorizationDecision(decision, (ArrayValue) obligations, (ArrayValue) advice, (Value) resource,
                Value.UNDEFINED);
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

    record StreamPolicy(CompiledExpression targetExpression, AuthorizationDecision decision, StreamOperator body)
            implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                            contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return new TracedAuthorizationDecision(AuthorizationDecision.NOT_APPLICABLE,
                            contributingAttributes);
                }
                return new TracedAuthorizationDecision(decision, contributingAttributes);
            });
        }
    }

    record StreamValuePolicy(
            CompiledExpression targetExpression,
            Decision decision,
            StreamOperator body,
            ArrayValue obligations,
            ArrayValue advice,
            Value resource) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().map(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                            contributingAttributes);
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return new TracedAuthorizationDecision(AuthorizationDecision.NOT_APPLICABLE,
                            contributingAttributes);
                }
                return new TracedAuthorizationDecision(
                        new AuthorizationDecision(decision, obligations, advice, resource, Value.UNDEFINED),
                        contributingAttributes);
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
            SourceLocation policyLocation) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue              = tracedBodyValue.value();
                val contributingAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                            contributingAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(new TracedAuthorizationDecision(AuthorizationDecision.NOT_APPLICABLE,
                            contributingAttributes));
                }
                return Flux.deferContextual(ctxView -> {
                    val evalCtx          = ctxView.get(EvaluationContext.class);
                    val obligationsValue = obligations.evaluate(evalCtx);
                    if (obligationsValue instanceof ErrorValue error) {
                        return Flux.just(AuthorizationDecision.ofError(error));
                    }
                    if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                        return Flux.just(AuthorizationDecision.ofError(Value.errorAt(policyLocation,
                                ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue))));
                    }
                    val adviceValue = advice.evaluate(evalCtx);
                    if (adviceValue instanceof ErrorValue error) {
                        return Flux.just(AuthorizationDecision.ofError(error));
                    }
                    if (!(adviceValue instanceof ArrayValue adviceArray)) {
                        return Flux.just(AuthorizationDecision
                                .ofError(Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue))));
                    }
                    val resourceValue = resource.evaluate(evalCtx);
                    if (resourceValue instanceof ErrorValue error) {
                        return Flux.just(AuthorizationDecision.ofError(error));
                    }
                    return Flux.just(new AuthorizationDecision(decision, obligationsArray, adviceArray, resourceValue,
                            Value.UNDEFINED));
                }).map(d -> new TracedAuthorizationDecision(d, contributingAttributes));
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
            SourceLocation policyLocation) implements StreamDocument {
        @Override
        public Flux<TracedAuthorizationDecision> stream() {
            return body.stream().switchMap(tracedBodyValue -> {
                val bodyValue      = tracedBodyValue.value();
                val bodyAttributes = tracedBodyValue.contributingAttributes();
                if (bodyValue instanceof ErrorValue error) {
                    return Flux.just(
                            new TracedAuthorizationDecision(AuthorizationDecision.ofError(error), bodyAttributes));
                }
                if (Value.FALSE.equals(bodyValue)) {
                    return Flux.just(
                            new TracedAuthorizationDecision(AuthorizationDecision.NOT_APPLICABLE, bodyAttributes));
                }
                return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(), merged -> {
                    val tracedObligationsValue = (TracedValue) merged[0];
                    val obligationsValue       = tracedObligationsValue.value();
                    val contributingAttributes = new ArrayList<>(bodyAttributes);
                    contributingAttributes.addAll(tracedObligationsValue.contributingAttributes());
                    if (obligationsValue instanceof ErrorValue error) {
                        return new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                                contributingAttributes);
                    }
                    if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                        return new TracedAuthorizationDecision(
                                AuthorizationDecision.ofError(Value.errorAt(policyLocation,
                                        ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue))),
                                contributingAttributes);
                    }
                    val tracedAdviceValue = (TracedValue) merged[1];
                    val adviceValue       = tracedAdviceValue.value();
                    contributingAttributes.addAll(tracedAdviceValue.contributingAttributes());
                    if (adviceValue instanceof ErrorValue error) {
                        return new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                                contributingAttributes);
                    }
                    if (!(adviceValue instanceof ArrayValue adviceArray)) {
                        return new TracedAuthorizationDecision(AuthorizationDecision.ofError(
                                Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(obligationsValue))),
                                contributingAttributes);
                    }
                    val tracedResourceValue = (TracedValue) merged[2];
                    val resourceValue       = tracedResourceValue.value();
                    contributingAttributes.addAll(tracedResourceValue.contributingAttributes());
                    if (resourceValue instanceof ErrorValue error) {
                        return new TracedAuthorizationDecision(AuthorizationDecision.ofError(error),
                                contributingAttributes);
                    }
                    val actualDecision = new AuthorizationDecision(decision, obligationsArray, adviceArray,
                            resourceValue, Value.UNDEFINED);
                    return new TracedAuthorizationDecision(actualDecision, contributingAttributes);
                });
            });
        }
    }

    record SimplePurePolicy(CompiledExpression targetExpression, AuthorizationDecision decision, PureOperator body)
            implements PureDocument {
        @Override
        public AuthorizationDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body.evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return AuthorizationDecision.ofError(error);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return AuthorizationDecision.NOT_APPLICABLE;
            }
            return decision;
        }
    }

    record PurePolicy(
            CompiledExpression targetExpression,
            Decision decision,
            CompiledExpression body,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            SourceLocation policyLocation) implements PureDocument {
        @Override
        public AuthorizationDecision evaluateBody(EvaluationContext ctx) {
            val bodyValue = body instanceof Value vb ? vb : ((PureOperator) body).evaluate(ctx);
            if (bodyValue instanceof ErrorValue error) {
                return AuthorizationDecision.ofError(error);
            }
            if (Value.FALSE.equals(bodyValue)) {
                return AuthorizationDecision.NOT_APPLICABLE;
            }
            val obligationsValue = obligations instanceof Value vb ? vb : ((PureOperator) obligations).evaluate(ctx);
            if (obligationsValue instanceof ErrorValue error) {
                return AuthorizationDecision.ofError(error);
            }
            if (!(obligationsValue instanceof ArrayValue obligationsArray)) {
                return AuthorizationDecision.ofError(
                        Value.errorAt(policyLocation, ERROR_OBLIGATIONS_NOT_ARRAY.formatted(obligationsValue)));
            }
            val adviceValue = advice instanceof Value vb ? vb : ((PureOperator) advice).evaluate(ctx);
            if (adviceValue instanceof ErrorValue error) {
                return AuthorizationDecision.ofError(error);
            }
            if (!(adviceValue instanceof ArrayValue adviceArray)) {
                return AuthorizationDecision
                        .ofError(Value.errorAt(policyLocation, ERROR_ADVICE_NOT_ARRAY.formatted(adviceValue)));
            }
            val resourceValue = resource instanceof Value vb ? vb : ((PureOperator) resource).evaluate(ctx);
            if (resourceValue instanceof ErrorValue error) {
                return AuthorizationDecision.ofError(error);
            }
            return new AuthorizationDecision(decision, obligationsArray, adviceArray, resourceValue, Value.UNDEFINED);
        }
    }

}
