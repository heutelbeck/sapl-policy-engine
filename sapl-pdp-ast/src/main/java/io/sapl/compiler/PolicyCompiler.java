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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CompiledDocument;
import io.sapl.ast.Policy;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@UtilityClass
public class PolicyCompiler {

    private static final AuthorizationDecision UNIMPLEMENTED = AuthorizationDecision.ofError("UNIMPLEMENTED!");

    public CompiledDocument compilePolicy(Policy policy, CompilationContext ctx) {
        val compiledTarget = policy.target() == null ? Value.TRUE
                : BooleanGuardCompiler.applyBooleanGuard(ExpressionCompiler.compile(policy.target(), ctx),
                        policy.target().location(), "Target expressions must evaluate to Boolean, but got %s.");

        return switch (compiledTarget) {
        case Value targetValue                                    ->
            compileWithConstantTarget(targetValue, policy, ctx);
        case PureOperator po when !po.isDependingOnSubscription() -> UNIMPLEMENTED; // fold and same as Value case
        case PureOperator po                                      -> UNIMPLEMENTED;
        case StreamOperator sto                                   ->
            AuthorizationDecision.ofError("Target expression must not contain attributes operators <>!.");
        };
    }

    private static CompiledDocument compileWithConstantTarget(Value targetValue, Policy policy,
            CompilationContext ctx) {
        if (Value.FALSE.equals(targetValue)) {
            return AuthorizationDecision.NOT_APPLICABLE;
        }
        return compilePolicyEvaluation(policy, ctx);
    }

    private static CompiledDocument compilePolicyEvaluation(Policy policy, CompilationContext ctx) {
        val compiledBody = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        return switch (compiledBody.bodyExpression()) {
        case Value bodyValue                                      -> compileConstraintsAndTransform(policy, ctx);
        case PureOperator po when !po.isDependingOnSubscription() -> UNIMPLEMENTED; // fold and same as Value case
        case PureOperator po                                      -> UNIMPLEMENTED;
        case StreamOperator sto                                   ->
            AuthorizationDecision.ofError("Target expression must not contain attributes operators <>!.");
        };
    }

    private static CompiledDocument compileConstraintsAndTransform(Policy policy, CompilationContext ctx) {
        val location    = policy.location();
        var obligations = ExpressionCompiler.fold(
                ArrayCompiler.buildFromCompiled(
                        policy.obligations().stream().map(o -> ExpressionCompiler.compile(o, ctx)).toList(), location),
                ctx);

        val advice    = ExpressionCompiler.fold(ArrayCompiler.buildFromCompiled(
                policy.advice().stream().map(a -> ExpressionCompiler.compile(a, ctx)).toList(), location), ctx);
        val transform = policy.transformation() == null ? Value.UNDEFINED
                : ExpressionCompiler.fold(ExpressionCompiler.compile(policy.transformation(), ctx), ctx);

        val constraintsAndTransforms = List.of(obligations, advice, transform);
        return UNIMPLEMENTED;
    }

}
