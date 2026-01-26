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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;

import static io.sapl.api.model.Value.of;
import static io.sapl.util.SaplTesting.assertCompilesTo;
import static io.sapl.util.SaplTesting.assertCompilesToError;
import static io.sapl.util.SaplTesting.assertEvaluatesTo;
import static io.sapl.util.SaplTesting.assertEvaluatesToError;
import static io.sapl.util.SaplTesting.assertPureEvaluatesToWithSubject;
import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.errorAttributeBroker;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.singleValueAttributeBroker;
import static io.sapl.util.SaplTesting.testContext;
import static org.assertj.core.api.Assertions.assertThat;

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
                "false ^ false ^ true, true", "false ^ false ^ false, false" })
        void when_allValues_then_foldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void when_fourOperands_then_correctResult() {
            assertCompilesTo("true ^ true ^ true ^ true", Value.FALSE);
        }

        @Test
        void when_valueWithSubscriptionElement_then_returnsPureOperator() {
            assertThat(compileExpression("true ^ subject")).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithVariable_then_evaluatesCorrectly() {
            assertEvaluatesTo("true ^ flag ^ false", Map.of("flag", Value.TRUE), Value.FALSE);
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            assertCompilesToError("true ^ 5 ^ false", "boolean");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var result = evaluateExpression("true ^ notBoolean", testContext(Map.of("notBoolean", of("hello"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class SumTests {

        @Test
        void when_allValues_then_foldAtCompileTime() {
            assertCompilesTo("1 + 2 + 3", of(6));
        }

        @Test
        void when_manyOperands_then_correctResult() {
            assertCompilesTo("1 + 2 + 3 + 4 + 5", of(15));
        }

        @Test
        void when_decimals_then_correctResult() {
            var compiled = compileExpression("1.5 + 2.5 + 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        void when_valueWithSubscriptionElement_then_returnsPureOperator() {
            assertThat(compileExpression("1 + subject + 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithVariable_then_evaluatesCorrectly() {
            assertEvaluatesTo("1 + x + 3", Map.of("x", of(10)), of(14));
        }

        @Test
        void when_multipleVariables_then_evaluatesCorrectly() {
            assertEvaluatesTo("a + b + c", Map.of("a", of(1), "b", of(2), "c", of(3)), of(6));
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            assertCompilesToError("1 + \"hello\" + 3", "number");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var result = evaluateExpression("1 + notNumber + 3", testContext(Map.of("notNumber", of("text"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_errorInValues_then_compileTimeError() {
            assertThat(compileExpression("undefined + 1 + 2")).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class ProductTests {

        @Test
        void when_allValues_then_foldAtCompileTime() {
            assertCompilesTo("2 * 3 * 4", of(24));
        }

        @Test
        void when_manyOperands_then_correctResult() {
            assertCompilesTo("1 * 2 * 3 * 4 * 5", of(120));
        }

        @Test
        void when_includesZero_then_resultIsZero() {
            assertCompilesTo("5 * 0 * 100", of(0));
        }

        @Test
        void when_decimals_then_correctResult() {
            var compiled = compileExpression("1.5 * 2.0 * 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("9.0"));
        }

        @Test
        void when_valueWithSubscriptionElement_then_returnsPureOperator() {
            assertThat(compileExpression("2 * subject * 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithVariable_then_evaluatesCorrectly() {
            assertEvaluatesTo("2 * x * 3", Map.of("x", of(5)), of(30));
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            assertThat(compileExpression("2 * true * 3")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            var array  = ArrayValue.builder().add(of(1)).add(of(2)).add(of(3)).build();
            var result = evaluateExpression("2 * notNumber", testContext(Map.of("notNumber", array)));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_zeroTimesError_then_errorNotZero() {
            // 0 * undefined should be ERROR, not 0
            assertThat(compileExpression("0 * undefined")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_zeroTimesPureError_then_errorNotZero() {
            var result = evaluateExpression("0 * broken",
                    testContext(Map.of("broken", Value.error("variable is broken"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("broken");
        }

        @Test
        void when_zeroInMiddle_stillEvaluatesRest() {
            var result = evaluateExpression("5 * 0 * broken", testContext(Map.of("broken", Value.error("broken"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class StrataTests {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({ "1 + 2 + 3,   Value", "1 + subject + 3,   PureOperator",
                "subject + action + resource,   PureOperator" })
        void when_expression_then_returnsExpectedType(String expr, String expectedType) {
            var compiled = compileExpression(expr);
            if ("Value".equals(expectedType)) {
                assertThat(compiled).isInstanceOf(Value.class);
            } else {
                assertThat(compiled).isInstanceOf(PureOperator.class);
            }
        }

        @Test
        void when_valueErrorFirst_then_noFurtherEvaluation() {
            assertThat(compileExpression("undefined + 1 + 2")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_valueErrorMiddle_then_stopsAtError() {
            assertThat(compileExpression("1 + undefined + 2")).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class DependsOnSubscriptionTests {

        @Test
        void when_subscriptionElement_then_resultDependsOnSubscription() {
            var compiled = compileExpression("1 + subject + 2");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    class EvaluationTests {

        @Test
        void xor_variableEvaluatesToTrue() {
            assertEvaluatesTo("false ^ flag", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void xor_multipleVariables() {
            assertEvaluatesTo("a ^ b ^ c", Map.of("a", Value.TRUE, "b", Value.FALSE, "c", Value.TRUE), Value.FALSE);
        }

        @Test
        void sum_singleVariable() {
            assertEvaluatesTo("x + 5", Map.of("x", of(10)), of(15));
        }

        @Test
        void sum_multipleVariables() {
            assertEvaluatesTo("a + b + c + 10", Map.of("a", of(1), "b", of(2), "c", of(3)), of(16));
        }

        @Test
        void product_singleVariable() {
            assertEvaluatesTo("x * 4", Map.of("x", of(7)), of(28));
        }

        @Test
        void product_multipleVariables() {
            assertEvaluatesTo("a * b * c * 2", Map.of("a", of(2), "b", of(3), "c", of(4)), of(48));
        }

        @Test
        void xor_variableTypeMismatchError() {
            assertEvaluatesToError("true ^ x", Map.of("x", of(123)));
        }

        @Test
        void sum_variableTypeMismatchError() {
            assertEvaluatesToError("1 + x", Map.of("x", of("not a number")));
        }

        @Test
        void product_variableTypeMismatchError() {
            assertEvaluatesToError("2 * x", Map.of("x", Value.TRUE));
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
            assertThat(compileExpression("1 + (subject.x) + 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_multipleSubscriptionElement_propertyAccess_then_evaluatesCorrectly() {
            var subjectValue = ObjectValue.builder().put("a", of(1)).put("b", of(2)).put("c", of(3)).build();
            assertPureEvaluatesToWithSubject("(subject.a) + (subject.b) + (subject.c)", subjectValue, of(6));
        }
    }

    @Nested
    class StreamOperatorTests {

        @Test
        void when_valuesAndStream_then_returnsStreamOperator() {
            var broker   = attributeBroker("test.attr", of(10));
            var compiled = compileExpression("1 + <test.attr> + 2", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void when_streamEmitsValue_then_combinesWithValues() {
            var broker   = attributeBroker("test.attr", of(10));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(13))).verifyComplete();
        }

        @Test
        void when_multipleStreams_then_combineLatest() {
            var broker   = singleValueAttributeBroker(Map.of("a.attr", of(5), "b.attr", of(3)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> + <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(8))).verifyComplete();
        }

        @Test
        void when_streamWithProduct_then_multipliesCorrectly() {
            var broker   = attributeBroker("test.attr", of(7));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("2 * <test.attr> * 3", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(42))).verifyComplete();
        }

        @Test
        void when_streamWithXor_then_xorsCorrectly() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true ^ <test.attr> ^ false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void when_streamEmitsError_then_propagatesError() {
            var broker   = errorAttributeBroker("test.attr", "Stream errors");
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("Stream errors");
            }).verifyComplete();
        }

        @Test
        void when_streamWithTypeMismatch_then_returnsError() {
            var broker   = attributeBroker("test.attr", of("not a number"));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("1 + <test.attr> + 2", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class))
                    .verifyComplete();
        }

        @Test
        void when_valuesAndPuresAndStream_then_allCombined() {
            var broker   = attributeBroker("test.attr", of(100));
            var ctx      = testContext(broker, Map.of("x", of(10)));
            var compiled = compileExpression("1 + x + <test.attr>", ctx.compilationContext());

            var evalCtx = ctx.evaluationContext();
            var stream  = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(of(111))).verifyComplete();
        }

        @Test
        void when_zeroTimesErrorStream_then_errorNotZero() {
            var broker   = errorAttributeBroker("test.attr", "attribute failed");
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("0 * <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("attribute failed");
            }).verifyComplete();
        }
    }

}
