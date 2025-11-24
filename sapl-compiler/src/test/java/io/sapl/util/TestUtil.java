/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.ExpressionCompiler;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class TestUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Slf4j
    @PolicyInformationPoint(name = "test")
    public static class TestPip {
        @Attribute
        public Flux<Value> echo(Value entity) {
            log.debug("echo called with entity: {}", entity);
            return Flux.just(entity);
        }

        @EnvironmentAttribute
        public Flux<Value> sequence() {
            log.debug("sequence called - emitting [1, 2, 3]");
            return Flux.just(Value.of(1), Value.of(2), Value.of(3));
        }

        @Attribute
        public Flux<Value> counter(Value entity) {
            log.debug("counter called with entity: {}", entity);
            if (!(entity instanceof NumberValue startNum)) {
                return Flux.just(Value.error("counter requires a number argument"));
            }
            int startInt = startNum.value().intValue();
            return Flux.just(Value.of(startInt), Value.of(startInt + 1), Value.of(startInt + 2));
        }

        @Attribute
        public Flux<Value> changes(Value entity) {
            log.debug("changes called with entity: {}", entity);
            if (!(entity instanceof ObjectValue obj)) {
                return Flux.just(Value.error("changes requires an object argument"));
            }
            // Emit the object 3 times with an incrementing "version" field
            return Flux.just(ObjectValue.builder().putAll(obj).put("version", Value.of(1)).build(),
                    ObjectValue.builder().putAll(obj).put("version", Value.of(2)).build(),
                    ObjectValue.builder().putAll(obj).put("version", Value.of(3)).build());
        }

    }

    /**
     * Creates a new CompilationContext with fresh broker instances for test
     * isolation.
     */
    @SneakyThrows
    private CompilationContext createCompilationContext() {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockFunctionLibrary.class);

        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        attributeBroker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        attributeBroker.loadPolicyInformationPointLibrary(new TestPip());

        return new CompilationContext(functionBroker, attributeBroker);
    }

    /**
     * Creates a new EvaluationContext with fresh broker instances for test
     * isolation.
     */
    @SneakyThrows
    private EvaluationContext createEvaluationContext(AuthorizationSubscription authorizationSubscription) {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockFunctionLibrary.class);

        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        attributeBroker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        attributeBroker.loadPolicyInformationPointLibrary(new TestPip());

        return new EvaluationContext("testConfigurationId", "testSubscriptionId", authorizationSubscription,
                functionBroker, attributeBroker);
    }

    private EvaluationContext createEvaluationContext() {
        return createEvaluationContext(new AuthorizationSubscription(Value.of("Elric"), Value.of("slay"),
                Value.of("Moonglum"), Value.of("Tanelorn")));
    }

    /**
     * Asserts that a compiled expression evaluates to an expected value.
     * <p>
     * For expressions with attribute finders, only the first emitted value is
     * verified. The stream is canceled after
     * verification (broker keeps streams open).
     *
     * @param expression
     * the expression to compile and evaluate
     * @param expected
     * the expected value
     */
    public static void assertCompiledExpressionEvaluatesTo(String expression, Value expected) {
        val compiledExpression = compileExpression(expression);
        val evaluated          = evaluateExpression(compiledExpression, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(expected).thenCancel();
    }

    /**
     * Parses a JSON string into a Value object.
     * <p>
     * Convenience method for creating expected values in tests without using the
     * compiler.
     *
     * @param jsonString
     * the JSON string to parse
     * @param <T>
     * the expected Value type
     *
     * @return the parsed Value
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T extends Value> T json(String jsonString) {
        val node  = MAPPER.readTree(jsonString);
        val value = ValueJsonMarshaller.fromJsonNode(node);
        return (T) value;
    }

    /**
     * Asserts that an expression evaluates to an expected value.
     * <p>
     * Convenience method for comparing expression results in tests. The expected
     * value is parsed from JSON to avoid
     * testing the compiler against itself.
     *
     * @param actualExpression
     * the expression to evaluate
     * @param expectedJson
     * the JSON string representing the expected result
     */
    public static void assertExpressionsEqual(String actualExpression, String expectedJson) {
        assertThat(evaluate(actualExpression)).isEqualTo(json(expectedJson));
    }

    /**
     * Asserts that an expression evaluates to a specific Value.
     * <p>
     * Convenience method for comparing expression results to Value objects.
     *
     * @param actualExpression
     * the expression to evaluate
     * @param expectedValue
     * the expected Value object
     */
    public static void assertExpressionEvaluatesTo(String actualExpression, Value expectedValue) {
        assertThat(evaluate(actualExpression)).isEqualTo(expectedValue);
    }

    public static void assertExpressionAsStreamEmits(String actualExpression, String... expectedJson) {
        val actual = evaluateExpression(actualExpression);
        val values = new Value[expectedJson.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = json(expectedJson[i]);
        }
        StepVerifier.create(actual).expectNext(values).thenCancel().verify();
    }

    /**
     * Asserts that an expression evaluates to an error containing a specific
     * message.
     * <p>
     * Convenience method for testing error conditions.
     *
     * @param expression
     * the expression to evaluate
     * @param expectedMessageFragment
     * the expected error message fragment (case-insensitive)
     */
    public static void assertEvaluatesToError(String expression, String expectedMessageFragment) {
        val result = evaluate(expression);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message().toLowerCase()).contains(expectedMessageFragment.toLowerCase());
    }

    /**
     * Asserts that a compiled expression evaluates to an error containing a
     * specific message.
     * <p>
     * For expressions with attribute finders, only the first emitted value is
     * verified. The stream is canceled after
     * verification.
     *
     * @param expression
     * the expression to compile and evaluate
     * @param message
     * the expected error message fragment (case-insensitive)
     */
    public static void assertCompiledExpressionEvaluatesToErrorContaining(String expression, String message) {
        val compiledExpression = compileExpression(expression);
        val evaluated          = evaluateExpression(compiledExpression, createEvaluationContext());
        StepVerifier.create(evaluated).expectNextMatches(
                e -> e instanceof ErrorValue error && error.message().toLowerCase().contains(message.toLowerCase()))
                .thenCancel();
    }

    public static void assertExpressionCompilesToValue(String expression, Value expected) {
        val compiledExpression = compileExpression(expression);
        assertThat(compiledExpression).isEqualTo(expected);
    }

    @SneakyThrows
    private CompiledExpression compileExpression(String expression) {
        val parsedExpression = ParserUtil.expression(expression);
        return ExpressionCompiler.compileExpression(parsedExpression, createCompilationContext());
    }

    public Flux<Value> evaluateExpression(String expression) {
        val compiledExpression = compileExpression(expression);
        return evaluateExpression(compiledExpression, createEvaluationContext());
    }

    /**
     * Compiles and evaluates an expression to a single Value synchronously.
     * <p>
     * For testing convenience. If expression produces a stream, returns first
     * emitted value.
     *
     * @param expression
     * the expression string to evaluate
     *
     * @return the evaluated Value
     */
    public Value evaluate(String expression) {
        val compiledExpression = compileExpression(expression);
        return switch (compiledExpression) {
        case Value value                       -> value;
        case PureExpression pureExpression     -> pureExpression.evaluate(createEvaluationContext());
        case StreamExpression streamExpression -> streamExpression.stream()
                .contextWrite(ctx -> ctx.put(EvaluationContext.class, createEvaluationContext())).blockFirst();
        };
    }

    private Flux<Value> evaluateExpression(CompiledExpression expression, EvaluationContext evaluationContext) {
        return switch (expression) {
        case Value value                       -> Flux.just(value);
        case PureExpression pureExpression     -> Flux.just(pureExpression.evaluate(evaluationContext));
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
        };
    }

    /**
     * Creates a test case with description, expression, and expected values.
     * Automatically wraps expected values into
     * String[] for varargs compatibility.
     * <p>
     * This helper is used with parameterized tests to avoid verbose array wrapping
     * in test data.
     *
     * @param description
     * test case description
     * @param expression
     * the SAPL expression to test
     * @param expectedValues
     * the expected JSON values as strings
     *
     * @return Object[] containing description, expression, and expected values
     * array
     */
    public static Object[] testCase(String description, String expression, String... expectedValues) {
        return new Object[] { description, expression, expectedValues };
    }

    /**
     * Creates an error test case with description, expression, and error fragment.
     * <p>
     * This helper is used with parameterized error tests.
     *
     * @param description
     * test case description
     * @param expression
     * the SAPL expression that should produce an error
     * @param errorFragment
     * the expected error message fragment (case-insensitive)
     *
     * @return Object[] containing description, expression, and error fragment
     */
    public static Object[] errorCase(String description, String expression, String errorFragment) {
        return new Object[] { description, expression, errorFragment };
    }

}
