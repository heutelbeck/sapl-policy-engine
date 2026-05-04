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
package io.sapl.compiler.expressions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.ast.Expression;
import io.sapl.ast.FunctionCall;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiler for SAPL function calls.
 * <p>
 * Functions are synchronous operations that take arguments and return a value.
 * Unlike attribute finders, functions do not produce streams - they are pure
 * computations. The compiler classifies arguments at compile time and emits
 * one of three records:
 * <ul>
 * <li>{@link NoArgsFunction} - zero arguments</li>
 * <li>{@link PureFunction} - all arguments are Value/PureOperator</li>
 * <li>{@link StreamFunction} - at least one argument is a StreamOperator</li>
 * </ul>
 */
@UtilityClass
public class FunctionCallCompiler {

    private static final String ERROR_PURE_FUNCTION_RECEIVED_STREAM_OPERATOR = "PureFunction cannot contain StreamOperator. Indicates an implementation bug.";

    public static CompiledExpression compile(FunctionCall call, CompilationContext ctx) {
        return compile(call.name().full(), call.arguments(), call.location(), ctx);
    }

    public static CompiledExpression compile(String functionName, List<Expression> arguments, SourceLocation location,
            CompilationContext ctx) {
        if (arguments.isEmpty()) {
            return new NoArgsFunction(functionName, location);
        }

        val     compiled  = new ArrayList<CompiledExpression>(arguments.size());
        boolean hasStream = false;
        for (val argExpr : arguments) {
            val result = ExpressionCompiler.compile(argExpr, ctx);
            if (result instanceof ErrorValue err) {
                return err;
            }
            if (result instanceof StreamOperator) {
                hasStream = true;
            }
            compiled.add(result);
        }

        if (hasStream) {
            return new StreamFunction(functionName, compiled, location);
        }
        return new PureFunction(functionName, compiled, location);
    }

    /**
     * Walks all arguments via {@link StreamOperator#evalChild} accumulating
     * subscriptions from any stream children, holds the first
     * {@link ErrorValue} and returns it after the full walk, defers
     * {@code null} (incomplete child) to the end. Function invocation itself
     * is synchronous and adds no subscriptions. Precedence at the end:
     * error &gt; null &gt; function result.
     */
    private static ExpressionResult functionLookup(String functionName, List<? extends CompiledExpression> arguments,
            EvaluationContext ctx) {
        val     deps       = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(arguments.size());
        boolean seenNull   = false;
        Value   firstError = null;
        val     argValues  = new ArrayList<Value>(arguments.size());
        for (val arg : arguments) {
            val v = evalChild(arg, ctx, deps);
            if (v == null) {
                seenNull = true;
                continue;
            }
            if (v instanceof ErrorValue err) {
                if (firstError == null) {
                    firstError = err;
                }
                continue;
            }
            argValues.add(v);
        }
        if (firstError != null) {
            return new ExpressionResult(firstError, deps);
        }
        if (seenNull) {
            return new ExpressionResult(null, deps);
        }
        val result = ctx.functionBroker().evaluateFunction(new FunctionInvocation(functionName, argValues));
        return new ExpressionResult(result, deps);
    }

    /**
     * Function with no arguments - simplest case.
     */
    record NoArgsFunction(String functionName, SourceLocation location) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(NoArgsFunction.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return ctx.functionBroker().evaluateFunction(new FunctionInvocation(functionName, List.of()));
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, functionName.hashCode());
        }
    }

    /**
     * Function with at least one argument; every argument is a {@link Value}
     * or {@link PureOperator}. The {@link StreamOperator} branch in argument
     * dispatch is unreachable by construction and folds to an
     * {@link ErrorValue} defensively.
     */
    record PureFunction(String functionName, List<CompiledExpression> arguments, SourceLocation location)
            implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(PureFunction.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val argValues = new ArrayList<Value>(arguments.size());
            for (val arg : arguments) {
                val value = switch (arg) {
                case Value v                -> v;
                case PureOperator p         -> p.evaluate(ctx);
                case StreamOperator ignored -> Value.error(ERROR_PURE_FUNCTION_RECEIVED_STREAM_OPERATOR);
                };
                if (value instanceof ErrorValue) {
                    return value;
                }
                argValues.add(value);
            }
            return ctx.functionBroker().evaluateFunction(new FunctionInvocation(functionName, argValues));
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (val arg : arguments) {
                if (arg instanceof PureOperator p && p.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isRelativeExpression() {
            for (val arg : arguments) {
                if (arg instanceof PureOperator p && p.isRelativeExpression()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long semanticHash() {
            long hash = SemanticHashing.ordered(KIND, functionName.hashCode());
            for (val arg : arguments) {
                val argHash = switch (arg) {
                case Value v                -> (long) v.hashCode();
                case PureOperator p         -> p.semanticHash();
                case StreamOperator ignored -> (long) arg.hashCode();
                };
                hash = SemanticHashing.ordered(hash, argHash);
            }
            return SemanticHashing.ordered(hash, Objects.hashCode(location));
        }
    }

    /**
     * Function with at least one stream argument. {@link #evaluate} walks
     * all arguments via {@link StreamOperator#evalChild}, accumulating
     * subscriptions, and invokes the function broker once all arguments
     * resolve.
     */
    record StreamFunction(String functionName, List<CompiledExpression> arguments, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return functionLookup(functionName, arguments, ctx);
        }
    }

}
