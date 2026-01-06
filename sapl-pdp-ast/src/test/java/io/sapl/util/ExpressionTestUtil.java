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
package io.sapl.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.ast.Expression;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.ExpressionCompiler;
import io.sapl.compiler.ast.AstTransformer;
import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Test utilities for parsing, compiling and evaluating SAPL expressions.
 */
@UtilityClass
public class ExpressionTestUtil {

    private static final AstTransformer TRANSFORMER = new AstTransformer();

    /**
     * Parses a SAPL expression string into an AST Expression.
     *
     * @param source the expression source code
     * @return the parsed Expression AST node
     * @throws IllegalArgumentException if parsing fails
     */
    public static Expression parseExpression(String source) {
        var ctx = ParserUtil.expression(source);
        return (Expression) TRANSFORMER.visit(ctx);
    }

    /**
     * Compiles a SAPL expression string with an empty compilation context.
     * Use this for testing compile-time behavior.
     *
     * @param source the expression source code
     * @return the compiled expression (Value, PureOperator, or StreamOperator)
     */
    public static CompiledExpression compileExpression(String source) {
        return compileExpression(source, emptyCompilationContext());
    }

    /**
     * Compiles a SAPL expression string with the given compilation context.
     *
     * @param source the expression source code
     * @param ctx the compilation context (brokers, imports)
     * @return the compiled expression
     */
    public static CompiledExpression compileExpression(String source, CompilationContext ctx) {
        var expression = parseExpression(source);
        return ExpressionCompiler.compile(expression, ctx);
    }

    /**
     * Compiles and evaluates a SAPL expression string with an empty context.
     * For literals, returns the Value directly.
     * For pure expressions, evaluates with an empty EvaluationContext.
     *
     * @param source the expression source code
     * @return the compiled/evaluated result
     */
    public static CompiledExpression evaluateExpression(String source) {
        var compiled = compileExpression(source);
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(emptyEvaluationContext());
        default              -> compiled; // StreamOperator returned as-is
        };
    }

    /**
     * Compiles and evaluates a SAPL expression with the given evaluation context.
     * For pure expressions, evaluates with the provided context.
     * For streams, returns the StreamOperator for further testing.
     *
     * @param source the expression source code
     * @param ctx the evaluation context (variables, subscription, broker)
     * @return the compiled/evaluated result
     */
    public static CompiledExpression evaluateExpression(String source, EvaluationContext ctx) {
        var compiled = compileExpression(source, compilationContextFrom(ctx));
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(ctx);
        default              -> compiled; // StreamOperator returned as-is
        };
    }

    /**
     * Creates an empty compilation context for testing.
     * No brokers, no imports.
     */
    public static CompilationContext emptyCompilationContext() {
        return new CompilationContext(null, null);
    }

    /**
     * Creates a compilation context from an evaluation context.
     * Extracts the broker for compile-time attribute resolution.
     */
    public static CompilationContext compilationContextFrom(EvaluationContext evalCtx) {
        return new CompilationContext(evalCtx.functionBroker(), evalCtx.attributeBroker());
    }

    /**
     * Creates an empty evaluation context for testing.
     * No variables, no brokers.
     */
    public static EvaluationContext emptyEvaluationContext() {
        return new EvaluationContext(null, null, null, null, Map.of(), null, null, () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with the given variables.
     *
     * @param variables the variables to include
     * @return the evaluation context
     */
    public static EvaluationContext withVariables(Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, null, null, () -> "test-timestamp");
    }

    /**
     * Builds an ObjectValue from key-value pairs.
     * Usage: obj("a", Value.of(1), "b", Value.of(2))
     *
     * @param keysAndValues alternating keys (String) and values (Value)
     * @return the ObjectValue
     */
    public static Value obj(Object... keysAndValues) {
        var builder = io.sapl.api.model.ObjectValue.builder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            builder.put((String) keysAndValues[i], (Value) keysAndValues[i + 1]);
        }
        return builder.build();
    }

    /**
     * Builds an ArrayValue from values.
     * Usage: array(Value.of(1), Value.of(2), Value.of(3))
     */
    public static ArrayValue array(Value... values) {
        var builder = ArrayValue.builder();
        for (var v : values) {
            builder.add(v);
        }
        return builder.build();
    }

    /**
     * Asserts that expression compiles to a Value equal to expected.
     */
    public static void assertCompilesTo(String source, Value expected) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(Value.class);
        assertThat(compiled).isEqualTo(expected);
    }

    /**
     * Asserts that expression compiles to a PureOperator that evaluates to
     * expected.
     */
    public static void assertPureEvaluatesTo(String source, Map<String, Value> variables, Value expected) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        var ctx    = withVariables(variables);
        var result = ((PureOperator) compiled).evaluate(ctx);
        assertThat(result).isEqualTo(expected);
    }

    /**
     * Asserts that expression compiles to an error containing the given message.
     */
    public static void assertCompilesToError(String source, String errorMessageContains) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) compiled).message()).contains(errorMessageContains);
    }

    /**
     * Asserts that expression compiles to a PureOperator that evaluates to an
     * error.
     */
    public static void assertPureEvaluatesToError(String source, Map<String, Value> variables) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        var ctx    = withVariables(variables);
        var result = ((PureOperator) compiled).evaluate(ctx);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    /**
     * Asserts that expression compiles to a specific type.
     */
    public static void assertCompilesTo(String source, Class<? extends CompiledExpression> expectedType) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(expectedType);
    }

    /**
     * Asserts expression compiles to PureOperator with specified dependency status.
     */
    public static void assertPureDependsOnSubscription(String source, boolean expected) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).isDependingOnSubscription()).isEqualTo(expected);
    }

}
