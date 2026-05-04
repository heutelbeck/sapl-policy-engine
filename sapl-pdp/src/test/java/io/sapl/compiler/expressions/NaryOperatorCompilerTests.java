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
import static io.sapl.util.SaplTesting.evaluate;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.singleValueAttributeBroker;
import static io.sapl.util.SaplTesting.testContext;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;

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
@DisplayName("NaryOperatorCompiler")
class NaryOperatorCompilerTests {

    @Nested
    class XorTests {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "true ^ true ^ true,   true", "true ^ true ^ false,  false", "true ^ false ^ true,  false",
                "true ^ false ^ false, true", "false ^ true ^ true,  false", "false ^ true ^ false, true",
                "false ^ false ^ true, true", "false ^ false ^ false, false" })
        void whenAllValuesThenFoldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void whenFourOperandsThenCorrectResult() {
            assertCompilesTo("true ^ true ^ true ^ true", Value.FALSE);
        }

        @Test
        void whenValueWithSubscriptionElementThenReturnsPureOperator() {
            assertThat(compileExpression("true ^ subject")).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenValueWithVariableThenEvaluatesCorrectly() {
            assertEvaluatesTo("true ^ flag ^ false", Map.of("flag", Value.TRUE), Value.FALSE);
        }

        @Test
        void whenTypeMismatchInValuesThenCompileTimeError() {
            assertCompilesToError("true ^ 5 ^ false", "boolean");
        }

        @Test
        void whenTypeMismatchInPureThenRuntimeError() {
            var result = evaluateExpression("true ^ notBoolean", testContext(Map.of("notBoolean", of("hello"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class SumTests {

        @Test
        void whenAllValuesThenFoldAtCompileTime() {
            assertCompilesTo("1 + 2 + 3", of(6));
        }

        @Test
        void whenManyOperandsThenCorrectResult() {
            assertCompilesTo("1 + 2 + 3 + 4 + 5", of(15));
        }

        @Test
        void whenDecimalsThenCorrectResult() {
            var compiled = compileExpression("1.5 + 2.5 + 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        void whenValueWithSubscriptionElementThenReturnsPureOperator() {
            assertThat(compileExpression("1 + subject + 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenValueWithVariableThenEvaluatesCorrectly() {
            assertEvaluatesTo("1 + x + 3", Map.of("x", of(10)), of(14));
        }

        @Test
        void whenMultipleVariablesThenEvaluatesCorrectly() {
            assertEvaluatesTo("a + b + c", Map.of("a", of(1), "b", of(2), "c", of(3)), of(6));
        }

        @Test
        void whenTypeMismatchInValuesThenCompileTimeError() {
            assertCompilesToError("1 + \"hello\" + 3", "number");
        }

        @Test
        void whenTypeMismatchInPureThenRuntimeError() {
            var result = evaluateExpression("1 + notNumber + 3", testContext(Map.of("notNumber", of("text"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenErrorInValuesThenCompileTimeError() {
            assertThat(compileExpression("undefined + 1 + 2")).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class ProductTests {

        @Test
        void whenAllValuesThenFoldAtCompileTime() {
            assertCompilesTo("2 * 3 * 4", of(24));
        }

        @Test
        void whenManyOperandsThenCorrectResult() {
            assertCompilesTo("1 * 2 * 3 * 4 * 5", of(120));
        }

        @Test
        void whenIncludesZeroThenResultIsZero() {
            assertCompilesTo("5 * 0 * 100", of(0));
        }

        @Test
        void whenDecimalsThenCorrectResult() {
            var compiled = compileExpression("1.5 * 2.0 * 3.0");
            assertThat(compiled).isInstanceOf(NumberValue.class);
            assertThat(((NumberValue) compiled).value()).isEqualByComparingTo(new BigDecimal("9.0"));
        }

        @Test
        void whenValueWithSubscriptionElementThenReturnsPureOperator() {
            assertThat(compileExpression("2 * subject * 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenValueWithVariableThenEvaluatesCorrectly() {
            assertEvaluatesTo("2 * x * 3", Map.of("x", of(5)), of(30));
        }

        @Test
        void whenTypeMismatchInValuesThenCompileTimeError() {
            assertThat(compileExpression("2 * true * 3")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenTypeMismatchInPureThenRuntimeError() {
            var array  = ArrayValue.builder().add(of(1)).add(of(2)).add(of(3)).build();
            var result = evaluateExpression("2 * notNumber", testContext(Map.of("notNumber", array)));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenZeroTimesErrorThenErrorNotZero() {
            // 0 * undefined should be ERROR, not 0
            assertThat(compileExpression("0 * undefined")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenZeroTimesPureErrorThenErrorNotZero() {
            var result = evaluateExpression("0 * broken",
                    testContext(Map.of("broken", Value.error("variable is broken"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("broken");
        }

        @Test
        void whenZeroInMiddleStillEvaluatesRest() {
            var result = evaluateExpression("5 * 0 * broken", testContext(Map.of("broken", Value.error("broken"))));
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class StrataTests {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({ "1 + 2 + 3,   Value", "1 + subject + 3,   PureOperator",
                "subject + action + resource,   PureOperator" })
        void whenExpressionThenReturnsExpectedType(String expr, String expectedType) {
            var compiled = compileExpression(expr);
            if ("Value".equals(expectedType)) {
                assertThat(compiled).isInstanceOf(Value.class);
            } else {
                assertThat(compiled).isInstanceOf(PureOperator.class);
            }
        }

        @Test
        void whenValueErrorFirstThenNoFurtherEvaluation() {
            assertThat(compileExpression("undefined + 1 + 2")).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenValueErrorMiddleThenStopsAtError() {
            assertThat(compileExpression("1 + undefined + 2")).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class DependsOnSubscriptionTests {

        @Test
        void whenSubscriptionElementThenResultDependsOnSubscription() {
            var compiled = compileExpression("1 + subject + 2");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    class EvaluationTests {

        @Test
        void xorVariableEvaluatesToTrue() {
            assertEvaluatesTo("false ^ flag", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void xorMultipleVariables() {
            assertEvaluatesTo("a ^ b ^ c", Map.of("a", Value.TRUE, "b", Value.FALSE, "c", Value.TRUE), Value.FALSE);
        }

        @Test
        void sumSingleVariable() {
            assertEvaluatesTo("x + 5", Map.of("x", of(10)), of(15));
        }

        @Test
        void sumMultipleVariables() {
            assertEvaluatesTo("a + b + c + 10", Map.of("a", of(1), "b", of(2), "c", of(3)), of(16));
        }

        @Test
        void productSingleVariable() {
            assertEvaluatesTo("x * 4", Map.of("x", of(7)), of(28));
        }

        @Test
        void productMultipleVariables() {
            assertEvaluatesTo("a * b * c * 2", Map.of("a", of(2), "b", of(3), "c", of(4)), of(48));
        }

        @Test
        void xorVariableTypeMismatchError() {
            assertEvaluatesToError("true ^ x", Map.of("x", of(123)));
        }

        @Test
        void sumVariableTypeMismatchError() {
            assertEvaluatesToError("1 + x", Map.of("x", of("not a number")));
        }

        @Test
        void productVariableTypeMismatchError() {
            assertEvaluatesToError("2 * x", Map.of("x", Value.TRUE));
        }

        @Test
        void xorCompileTimeFolding() {
            assertCompilesTo("true ^ false ^ true", Value.FALSE);
        }

        @Test
        void sumCompileTimeFolding() {
            assertCompilesTo("10 + 20 + 30", of(60));
        }

        @Test
        void productCompileTimeFolding() {
            assertCompilesTo("2 * 3 * 4 * 5", of(120));
        }

        @Test
        void xorCompileTimeError() {
            assertCompilesToError("true ^ \"string\" ^ false", "boolean");
        }

        @Test
        void sumCompileTimeError() {
            assertCompilesToError("1 + true + 3", "number");
        }

        @Test
        void productCompileTimeError() {
            assertCompilesToError("2 * \"text\" * 4", "number");
        }
    }

    @Nested
    class KeyStepDependentTests {

        @Test
        void whenValueWithPurePropertyAccessThenReturnsPureOperator() {
            assertThat(compileExpression("1 + (subject.x) + 3")).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenMultipleSubscriptionElementPropertyAccessThenEvaluatesCorrectly() {
            var subjectValue = ObjectValue.builder().put("a", of(1)).put("b", of(2)).put("c", of(3)).build();
            assertPureEvaluatesToWithSubject("(subject.a) + (subject.b) + (subject.c)", subjectValue, of(6));
        }
    }

    @Nested
    class StreamOperatorTests {

        @Test
        void whenValuesAndStreamThenReturnsStreamOperator() {
            var broker   = attributeBroker("test.attr", of(10));
            var compiled = compileExpression("1 + <test.attr> + 2", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void whenStreamEmitsValueThenCombinesWithValues() {
            var value = evaluate("1 + <test.attr> + 2").with("test.attr", of(10)).value();
            assertThat(value).isEqualTo(of(13));
        }

        @Test
        void whenMultipleStreamsThenCombineLatest() {
            var value = evaluate("<a.attr> + <b.attr>").with("a.attr", of(5)).with("b.attr", of(3)).value();
            assertThat(value).isEqualTo(of(8));
        }

        @Test
        void whenStreamWithProductThenMultipliesCorrectly() {
            var value = evaluate("2 * <test.attr> * 3").with("test.attr", of(7)).value();
            assertThat(value).isEqualTo(of(42));
        }

        @Test
        void whenStreamWithXorThenXorsCorrectly() {
            var value = evaluate("true ^ <test.attr> ^ false").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        void whenStreamEmitsErrorThenPropagatesError() {
            var value = evaluate("1 + <test.attr> + 2").with("test.attr", Value.error("Stream errors")).value();
            assertThat(value).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) value).message()).contains("Stream errors");
        }

        @Test
        void whenStreamWithTypeMismatchThenReturnsError() {
            var value = evaluate("1 + <test.attr> + 2").with("test.attr", of("not a number")).value();
            assertThat(value).isInstanceOf(ErrorValue.class);
        }

        @Test
        void whenValuesAndPuresAndStreamThenAllCombined() {
            var value = evaluate("1 + x + <test.attr>").withVariables(Map.of("x", of(10))).with("test.attr", of(100))
                    .value();
            assertThat(value).isEqualTo(of(111));
        }

        @Test
        void whenZeroTimesErrorStreamThenErrorNotZero() {
            var value = evaluate("0 * <test.attr>").with("test.attr", Value.error("attribute failed")).value();
            assertThat(value).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) value).message()).contains("attribute failed");
        }
    }

}
