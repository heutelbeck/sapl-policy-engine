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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import io.sapl.ast.Expression;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.util.Stratum;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test utilities for parsing, compiling and evaluating SAPL expressions.
 */
@UtilityClass
public class ExpressionTestUtil {

    /**
     * Standard test source location for AST nodes in tests.
     */
    public static final SourceLocation TEST_LOCATION = new SourceLocation("test", "", 0, 0, 1, 1, 1, 1);

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
     * Compiles a SAPL expression string with a custom attribute broker.
     * Uses default function broker.
     *
     * @param source the expression source code
     * @param attrBroker the attribute broker
     * @return the compiled expression
     */
    public static CompiledExpression compileExpression(String source, AttributeBroker attrBroker) {
        var ctx = new CompilationContext(DEFAULT_FUNCTION_BROKER, attrBroker);
        return compileExpression(source, ctx);
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

    /**
     * Asserts that the result is an ErrorValue.
     *
     * @param result the compiled expression result
     */
    public static void assertIsError(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    /**
     * Asserts that the result is an ErrorValue containing all specified message
     * fragments (case-insensitive).
     *
     * @param result the compiled expression result
     * @param fragments the message fragments to check for
     */
    public static void assertIsErrorContaining(CompiledExpression result, String... fragments) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        var message = ((ErrorValue) result).message().toLowerCase();
        for (var fragment : fragments) {
            assertThat(message).contains(fragment.toLowerCase());
        }
    }

    /**
     * Returns the error message from an ErrorValue result.
     *
     * @param result the compiled expression result (must be ErrorValue)
     * @return the error message
     */
    public static String errorMessage(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        return ((ErrorValue) result).message();
    }

    /**
     * Verifies a StreamOperator's emissions using StepVerifier.
     *
     * @param op the stream operator
     * @param ctx the evaluation context
     * @param assertions consumers that assert on each TracedValue emission
     */
    @SafeVarargs
    public static void verifyStream(StreamOperator op, EvaluationContext ctx, Consumer<TracedValue>... assertions) {
        var                            stream   = op.stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.Step<TracedValue> verifier = StepVerifier.create(stream);
        for (var assertion : assertions) {
            verifier = verifier.assertNext(assertion);
        }
        verifier.verifyComplete();
    }

    /**
     * Verifies a StreamOperator emits a single value matching the expected.
     *
     * @param op the stream operator
     * @param ctx the evaluation context
     * @param expected the expected value
     */
    public static void verifyStreamEmits(StreamOperator op, EvaluationContext ctx, Value expected) {
        verifyStream(op, ctx, tv -> assertThat(tv.value()).isEqualTo(expected));
    }

    /**
     * Verifies a StreamOperator emits values matching the expected sequence.
     *
     * @param op the stream operator
     * @param ctx the evaluation context
     * @param expected the expected values in order
     */
    public static void verifyStreamEmits(StreamOperator op, EvaluationContext ctx, Value... expected) {
        @SuppressWarnings("unchecked")
        Consumer<TracedValue>[] assertions = new Consumer[expected.length];
        for (int i = 0; i < expected.length; i++) {
            final var exp = expected[i];
            assertions[i] = tv -> assertThat(tv.value()).isEqualTo(exp);
        }
        verifyStream(op, ctx, assertions);
    }

    /**
     * Verifies a StreamOperator emits a single error containing the message
     * fragment.
     *
     * @param op the stream operator
     * @param ctx the evaluation context
     * @param messageFragment the expected error message fragment
     */
    public static void verifyStreamEmitsError(StreamOperator op, EvaluationContext ctx, String messageFragment) {
        verifyStream(op, ctx, tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) tv.value()).message()).contains(messageFragment);
        });
    }

    /**
     * Determines the stratum of a compiled expression.
     *
     * @param compiled the compiled expression
     * @return the stratum, or null if error
     */
    public static Stratum getStratum(CompiledExpression compiled) {
        if (compiled instanceof ErrorValue) {
            return null;
        }
        if (compiled instanceof Value) {
            return Stratum.VALUE;
        }
        if (compiled instanceof PureOperator p) {
            return p.isDependingOnSubscription() ? Stratum.PURE_SUB : Stratum.PURE_NON_SUB;
        }
        if (compiled instanceof StreamOperator) {
            return Stratum.STREAM;
        }
        return null;
    }

    /**
     * Asserts that a compiled expression matches the expected stratum.
     *
     * @param compiled the compiled expression
     * @param expected the expected stratum
     */
    public static void assertStratum(CompiledExpression compiled, Stratum expected) {
        switch (expected) {
        case VALUE        -> assertThat(compiled).isInstanceOf(Value.class);
        case PURE_NON_SUB -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isFalse();
        }
        case PURE_SUB     -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
        case STREAM       -> assertThat(compiled).isInstanceOf(StreamOperator.class);
        }
    }

    /**
     * Computes the expected output stratum based on input strata.
     * <ul>
     * <li>All 1 or 2 -> 1 (constant folded)</li>
     * <li>Any 3, no 4 -> 3</li>
     * <li>Any 4 -> 4</li>
     * </ul>
     */
    public static Stratum expectedStratum(Stratum... inputs) {
        int maxLevel = 1;
        for (var s : inputs) {
            if (s.level > maxLevel) {
                maxLevel = s.level;
            }
        }
        if (maxLevel <= 2) {
            return Stratum.VALUE; // Constant folded
        }
        return maxLevel == 3 ? Stratum.PURE_SUB : Stratum.STREAM;
    }

    /**
     * Creates a compilation context with specified function libraries loaded.
     *
     * @param libraries the function library classes to load
     * @return the compilation context
     */
    public static CompilationContext compilationContextWithFunctions(Class<?>... libraries) {
        var broker = new DefaultFunctionBroker();
        for (var lib : libraries) {
            broker.loadStaticFunctionLibrary(lib);
        }
        return new CompilationContext(broker, DEFAULT_ATTRIBUTE_BROKER);
    }

    /**
     * Creates a compilation context with function libraries and attribute broker.
     *
     * @param attrBroker the attribute broker
     * @param libraries the function library classes to load
     * @return the compilation context
     */
    public static CompilationContext compilationContextWithFunctions(AttributeBroker attrBroker,
            Class<?>... libraries) {
        var broker = new DefaultFunctionBroker();
        for (var lib : libraries) {
            broker.loadStaticFunctionLibrary(lib);
        }
        return new CompilationContext(broker, attrBroker);
    }

    /**
     * A test implementation of PureOperator for use in unit tests.
     * Allows creating custom evaluation logic with configurable subscription
     * dependency.
     */
    public record TestPureOperator(
            java.util.function.Function<EvaluationContext, Value> evaluator,
            boolean isDependingOnSubscription) implements PureOperator {

        /**
         * Creates a TestPureOperator that does not depend on subscription.
         *
         * @param evaluator the evaluation function
         */
        public TestPureOperator(java.util.function.Function<EvaluationContext, Value> evaluator) {
            this(evaluator, false);
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluator.apply(ctx);
        }

        @Override
        public SourceLocation location() {
            return TEST_LOCATION;
        }
    }

}
