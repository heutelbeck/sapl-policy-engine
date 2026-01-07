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

import static io.sapl.api.model.Value.of;
import static io.sapl.util.ExpressionTestUtil.assertCompilesTo;
import static io.sapl.util.ExpressionTestUtil.assertCompilesToError;
import static io.sapl.util.ExpressionTestUtil.assertPureEvaluatesTo;
import static io.sapl.util.ExpressionTestUtil.assertPureEvaluatesToError;
import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static io.sapl.util.ExpressionTestUtil.withVariables;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Tests for N-ary operator compilation: XOR (^), Sum (+), Product (*).
 * <p>
 * Verifies cost-stratified evaluation:
 * <ul>
 * <li>Values folded at compile time</li>
 * <li>Pures evaluated before stream subscription</li>
 * <li>Errors propagate early without unnecessary evaluation</li>
 * </ul>
 */
class NaryOperatorCompilerTests {

    @Nested
    class XorTests {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "true ^ true ^ true,   true", "true ^ true ^ false,  false", "true ^ false ^ true,  false",
                "true ^ false ^ false, true", "false ^ true ^ true,  false", "false ^ true ^ false, true",
                "false ^ false ^ true, true", "false ^ false ^ false, false", })
        void when_allValues_then_foldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class);
            assertThat(compiled).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void when_fourOperands_then_correctResult() {
            var compiled = compileExpression("true ^ true ^ true ^ true");
            assertThat(compiled).isEqualTo(Value.FALSE);
        }

        @Test
        void when_valueWithPure_then_returnsPureOperator() {
            var compiled = compileExpression("true ^ flag");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithPure_then_evaluatesCorrectly() {
            var ctx    = evalContextWithVariable("flag", Value.TRUE);
            var result = evaluateWithContext("true ^ flag ^ false", ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            var compiled = compileExpression("true ^ 5 ^ false");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) compiled).message()).contains("boolean");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var ctx    = evalContextWithVariable("notBoolean", of("hello"));
            var result = evaluateWithContext("true ^ notBoolean", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class SumTests {

        @Test
        void when_allValues_then_foldAtCompileTime() {
            var compiled = compileExpression("1 + 2 + 3");
            assertThat(compiled).isInstanceOf(Value.class);
            assertThat(compiled).isEqualTo(Value.of(6));
        }

        @Test
        void when_manyOperands_then_correctResult() {
            var compiled = compileExpression("1 + 2 + 3 + 4 + 5");
            assertThat(compiled).isEqualTo(Value.of(15));
        }

        @Test
        void when_decimals_then_correctResult() {
            var compiled = compileExpression("1.5 + 2.5 + 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        void when_valueWithPure_then_returnsPureOperator() {
            var compiled = compileExpression("1 + x + 3");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithPure_then_evaluatesCorrectly() {
            var ctx    = evalContextWithVariable("x", of(10));
            var result = evaluateWithContext("1 + x + 3", ctx);
            assertThat(result).isEqualTo(of(14));
        }

        @Test
        void when_multiplePures_then_evaluatesCorrectly() {
            var ctx    = evalContextWithVariables(Map.of("a", of(1), "b", of(2), "c", of(3)));
            var result = evaluateWithContext("a + b + c", ctx);
            assertThat(result).isEqualTo(of(6));
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            var compiled = compileExpression("1 + \"hello\" + 3");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) compiled).message()).contains("number");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var ctx    = evalContextWithVariable("notNumber", of("text"));
            var result = evaluateWithContext("1 + notNumber + 3", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_errorInValues_then_compileTimeError() {
            var compiled = compileExpression("undefined + 1 + 2");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class ProductTests {

        @Test
        void when_allValues_then_foldAtCompileTime() {
            var compiled = compileExpression("2 * 3 * 4");
            assertThat(compiled).isInstanceOf(Value.class);
            assertThat(compiled).isEqualTo(Value.of(24));
        }

        @Test
        void when_manyOperands_then_correctResult() {
            var compiled = compileExpression("1 * 2 * 3 * 4 * 5");
            assertThat(compiled).isEqualTo(Value.of(120));
        }

        @Test
        void when_includesZero_then_resultIsZero() {
            var compiled = compileExpression("5 * 0 * 100");
            assertThat(compiled).isEqualTo(Value.of(0));
        }

        @Test
        void when_decimals_then_correctResult() {
            var compiled = compileExpression("1.5 * 2.0 * 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("9.0"));
        }

        @Test
        void when_valueWithPure_then_returnsPureOperator() {
            var compiled = compileExpression("2 * x * 3");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithPure_then_evaluatesCorrectly() {
            var ctx    = evalContextWithVariable("x", of(5));
            var result = evaluateWithContext("2 * x * 3", ctx);
            assertThat(result).isEqualTo(of(30));
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            var compiled = compileExpression("2 * true * 3");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var array  = ArrayValue.builder().add(of(1)).add(of(2)).add(of(3)).build();
            var ctx    = evalContextWithVariable("notNumber", array);
            var result = evaluateWithContext("2 * notNumber", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_zeroTimesError_then_errorNotZero() {
            // 0 * undefined should be ERROR, not 0
            // Errors take precedence - we don't swallow errors via short-circuit
            var compiled = compileExpression("0 * undefined");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_zeroTimesPureError_then_errorNotZero() {
            // 0 * <error-variable> should be ERROR, not 0
            var ctx    = evalContextWithVariable("broken", Value.error("variable is broken"));
            var result = evaluateWithContext("0 * broken", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("broken");
        }

        @Test
        void when_zeroInMiddle_stillEvaluatesRest() {
            // 5 * 0 * broken should still evaluate broken and return error
            var ctx    = evalContextWithVariable("broken", Value.error("broken"));
            var result = evaluateWithContext("5 * 0 * broken", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class StrataTests {

        @Test
        void when_allValues_then_returnsValue() {
            var compiled = compileExpression("1 + 2 + 3");
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @Test
        void when_valuesAndPures_then_returnsPureOperator() {
            var compiled = compileExpression("1 + x + 3");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_onlyPures_then_returnsPureOperator() {
            var compiled = compileExpression("a + b + c");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_hasStream_then_returnsStreamOperator() {
            var compiled = compileExpression("1 + 2 + 3");
            assertThat(compiled).isNotInstanceOf(StreamOperator.class);
        }

        @Test
        void when_valueErrorFirst_then_noFurtherEvaluation() {
            var compiled = compileExpression("undefined + 1 + 2");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_valueErrorMiddle_then_stopsAtError() {
            var compiled = compileExpression("1 + undefined + 2");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class DependsOnSubscriptionTests {

        @Test
        void when_puresDependOnSubscription_then_resultDependsOnSubscription() {
            // IdentifierOperator for subscription-derived variables depends on subscription
            var compiled = compileExpression("1 + x + 2");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }

        @Test
        void when_puresDoNotDependOnSubscription_then_resultDoesNotDependOnSubscription() {
            // IdentifierOperator for subscription-derived variables depends on subscription
            var compiled = compileExpression("1 + x");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    class EvaluationTests {

        @Test
        void xor_pureEvaluatesToTrue() {
            assertPureEvaluatesTo("false ^ flag", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void xor_pureWithMultipleVariables() {
            assertPureEvaluatesTo("a ^ b ^ c", Map.of("a", Value.TRUE, "b", Value.FALSE, "c", Value.TRUE), Value.FALSE); // true
                                                                                                                         // ^
                                                                                                                         // false
                                                                                                                         // ^
                                                                                                                         // true
                                                                                                                         // =
                                                                                                                         // false
        }

        @Test
        void sum_pureWithSingleVariable() {
            assertPureEvaluatesTo("x + 5", Map.of("x", of(10)), of(15));
        }

        @Test
        void sum_pureWithMultipleVariables() {
            assertPureEvaluatesTo("a + b + c + 10", Map.of("a", of(1), "b", of(2), "c", of(3)), of(16));
        }

        @Test
        void product_pureWithSingleVariable() {
            assertPureEvaluatesTo("x * 4", Map.of("x", of(7)), of(28));
        }

        @Test
        void product_pureWithMultipleVariables() {
            assertPureEvaluatesTo("a * b * c * 2", Map.of("a", of(2), "b", of(3), "c", of(4)), of(48));
        }

        @Test
        void xor_pureTypeMismatchError() {
            assertPureEvaluatesToError("true ^ x", Map.of("x", of(123)));
        }

        @Test
        void sum_pureTypeMismatchError() {
            assertPureEvaluatesToError("1 + x", Map.of("x", of("not a number")));
        }

        @Test
        void product_pureTypeMismatchError() {
            assertPureEvaluatesToError("2 * x", Map.of("x", Value.TRUE));
        }

        @Test
        void xor_compileTimeFolding() {
            assertCompilesTo("true ^ false ^ true", Value.FALSE);
        }

        @Test
        void sum_compileTimeFolding() {
            assertCompilesTo("10 + 20 + 30", of(60));
        }

        @Test
        void product_compileTimeFolding() {
            assertCompilesTo("2 * 3 * 4 * 5", of(120));
        }

        @Test
        void xor_compileTimeError() {
            assertCompilesToError("true ^ \"string\" ^ false", "boolean");
        }

        @Test
        void sum_compileTimeError() {
            assertCompilesToError("1 + true + 3", "number");
        }

        @Test
        void product_compileTimeError() {
            assertCompilesToError("2 * \"text\" * 4", "number");
        }
    }

    @Nested
    class KeyStepDependentTests {

        @Test
        void when_valueWithPure_propertyAccess_then_returnsPureOperator() {
            var compiled = compileExpression("1 + (subject.x) + 3");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_multiplePures_propertyAccess_then_evaluatesCorrectly() {
            var ctx    = evalContextWithVariables(
                    Map.of("subject", ObjectValue.builder().put("a", of(1)).put("b", of(2)).put("c", of(3)).build()));
            var result = evaluateWithContext("(subject.a) + (subject.b) + (subject.c)", ctx);
            assertThat(result).isEqualTo(of(6));
        }
    }

    @Nested
    class StreamOperatorTests {

        @Test
        void when_valuesAndStream_then_returnsStreamOperator() {
            var broker   = testBroker("test.attr", of(10));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("1 + <test.attr> + 2", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void when_streamEmitsValue_then_combinesWithValues() {
            var broker   = testBroker("test.attr", of(10));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(13))) // 1 + 10 + 2
                    .verifyComplete();
        }

        @Test
        void when_multipleStreams_then_combineLatest() {
            var broker   = multiValueBroker(Map.of("a.attr", of(5), "b.attr", of(3)));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> + <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(8))) // 5 + 3
                    .verifyComplete();
        }

        @Test
        void when_streamWithProduct_then_multipliesCorrectly() {
            var broker   = testBroker("test.attr", of(7));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("2 * <test.attr> * 3", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(42))) // 2 * 7 * 3
                    .verifyComplete();
        }

        @Test
        void when_streamWithXor_then_xorsCorrectly() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true ^ <test.attr> ^ false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)) // true ^ true ^
                                                                                                        // false
                    .verifyComplete();
        }

        @Test
        void when_streamEmitsError_then_propagatesError() {
            var broker   = errorBroker("test.attr", "Stream error");
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("Stream error");
            }).verifyComplete();
        }

        @Test
        void when_streamWithTypeMismatch_then_returnsError() {
            var broker   = testBroker("test.attr", of("not a number"));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class))
                    .verifyComplete();
        }

        @Test
        void when_valuesAndPuresAndStream_then_allCombined() {
            var broker   = testBroker("test.attr", of(100));
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("x", of(10)));
            var compiled = compileExpressionWithBroker("1 + x + <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(111))) // 1 + 10 + 100
                    .verifyComplete();
        }

        @Test
        void when_zeroTimesErrorStream_then_errorNotZero() {
            // 0 * <error-stream> should be ERROR, not 0
            // Errors take precedence - streams are still subscribed even with 0
            var broker   = errorBroker("test.attr", "attribute failed");
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("0 * <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("attribute failed");
            }).verifyComplete();
        }
    }

    private static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

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

    private static EvaluationContext evalContextWithVariable(String name, Value value) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of(name, value), DEFAULT_FUNCTION_BROKER,
                DEFAULT_ATTRIBUTE_BROKER, () -> "test-timestamp");
    }

    private static EvaluationContext evalContextWithVariables(Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, DEFAULT_FUNCTION_BROKER,
                DEFAULT_ATTRIBUTE_BROKER, () -> "test-timestamp");
    }

    private static CompiledExpression evaluateWithContext(String source, EvaluationContext ctx) {
        var compiled = compileExpression(source);
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(ctx);
        default              -> compiled;
        };
    }

    private static CompiledExpression compileExpressionWithBroker(String source, AttributeBroker broker) {
        var compilationCtx = new CompilationContext(DEFAULT_FUNCTION_BROKER, broker);
        var expression     = io.sapl.util.ExpressionTestUtil.parseExpression(source);
        return ExpressionCompiler.compile(expression, compilationCtx);
    }

    private static EvaluationContext contextWithBroker(AttributeBroker broker) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of(), DEFAULT_FUNCTION_BROKER, broker,
                () -> "test-timestamp");
    }

    private static EvaluationContext contextWithBrokerAndVariables(AttributeBroker broker,
            Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, DEFAULT_FUNCTION_BROKER, broker,
                () -> "test-timestamp");
    }

    private static AttributeBroker testBroker(String expectedName, Value result) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.just(result);
                }
                return Flux.just(Value.error("Unknown attribute: %s", invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker multiValueBroker(Map<String, Value> attributeValues) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var value = attributeValues.get(invocation.attributeName());
                if (value != null) {
                    return Flux.just(value);
                }
                return Flux.just(Value.error("Unknown attribute: %s", invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker errorBroker(String expectedName, String errorMessage) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.just(Value.error(errorMessage));
                }
                return Flux.just(Value.error("Unknown attribute"));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }
}
