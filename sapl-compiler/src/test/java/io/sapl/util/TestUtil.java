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

import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pip.Attribute;
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
    private static final DefaultFunctionBroker  FUNCTION_BROKER      = new DefaultFunctionBroker();
    private static final AttributeRepository    ATTRIBUTE_REPOSITORY = new InMemoryAttributeRepository(
            Clock.systemUTC());
    private static final CachingAttributeBroker ATTRIBUTE_BROKER     = new CachingAttributeBroker(ATTRIBUTE_REPOSITORY);

    static {
        ATTRIBUTE_BROKER.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        ATTRIBUTE_BROKER.loadPolicyInformationPointLibrary(new TestPip());
        try {
            FUNCTION_BROKER.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
            FUNCTION_BROKER.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
            FUNCTION_BROKER.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Slf4j
    @PolicyInformationPoint(name = "test")
    public static class TestPip {
        @Attribute
        public Flux<Value> echo(Value entity) {
            log.debug("echo called with entity: {}", entity);
            return Flux.just(entity, Value.of("hello world")).delayElements(Duration.ofMillis(30));
        }

    }

    private CompilationContext createCompilationContext() {
        return new CompilationContext(FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    private EvaluationContext createEvaluationContext(AuthorizationSubscription authorizationSubscription) {
        return new EvaluationContext("testConfigurationId", "testSubscriptionId", authorizationSubscription,
                FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    private EvaluationContext createEvaluationContext() {
        return createEvaluationContext(new AuthorizationSubscription(Value.of("Elric"), Value.of("slay"),
                Value.of("Moonglum"), Value.of("Tanelorn")));
    }

    public static void assertCompiledExpressionEvaluatesTo(String expression, Value expected) {
        val compiledExpression = compileExpression(expression);
        val evaluated          = evaluateExpression(compiledExpression, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(expected).verifyComplete();
    }

    public static void assertCompiledExpressionEvaluatesToErrorContaining(String expression, String message) {
        val compiledExpression = compileExpression(expression);
        val evaluated          = evaluateExpression(compiledExpression, createEvaluationContext());
        StepVerifier.create(evaluated).expectNextMatches(
                e -> e instanceof ErrorValue error && error.message().toLowerCase().contains(message.toLowerCase()))
                .verifyComplete();
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
     * @param expression the expression string to evaluate
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

}
