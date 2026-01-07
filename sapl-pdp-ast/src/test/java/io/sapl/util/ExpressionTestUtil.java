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

import java.util.List;
import java.util.Map;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
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
import io.sapl.functions.DefaultFunctionBroker;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

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
     * Mock function library for testing extended filters.
     * Returns "***" for any input, mimicking the marker behavior.
     */
    @FunctionLibrary(name = "mock", description = "Mock functions for testing")
    public static class MockFunctionLibrary {
        private static final Value MARKER = Value.of("***");

        @Function
        public static Value func() {
            return MARKER;
        }

        @Function
        public static Value func(Value arg) {
            return MARKER;
        }

        @Function
        public static Value func(Value arg1, Value arg2) {
            return MARKER;
        }

        @Function
        public static Value func(Value arg1, Value arg2, Value arg3) {
            return MARKER;
        }
    }

    /** Default function broker for tests - includes mock function library */
    private static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER;

    static {
        DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();
        DEFAULT_FUNCTION_BROKER.loadStaticFunctionLibrary(MockFunctionLibrary.class);
    }

    /**
     * Default attribute broker for tests - returns error for any attribute lookup
     */
    private static final AttributeBroker DEFAULT_ATTRIBUTE_BROKER = new AttributeBroker() {
        @Override
        public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
            return Flux.just(Value.error("No attribute finder registered for: " + invocation.attributeName()));
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of();
        }
    };

    /**
     * Creates an empty compilation context for testing.
     * Uses default brokers that return errors for unknown functions/attributes.
     */
    public static CompilationContext emptyCompilationContext() {
        return new CompilationContext(DEFAULT_FUNCTION_BROKER, DEFAULT_ATTRIBUTE_BROKER);
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
     * Uses default brokers that return errors for unknown functions/attributes.
     */
    public static EvaluationContext emptyEvaluationContext() {
        return new EvaluationContext(null, null, null, null, Map.of(), DEFAULT_FUNCTION_BROKER,
                DEFAULT_ATTRIBUTE_BROKER, () -> "test-timestamp");
    }

    /**
     * Creates an evaluation context with the given variables.
     * Uses default brokers that return errors for unknown functions/attributes.
     *
     * @param variables the variables to include
     * @return the evaluation context
     */
    public static EvaluationContext withVariables(Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, DEFAULT_FUNCTION_BROKER,
                DEFAULT_ATTRIBUTE_BROKER, () -> "test-timestamp");
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
