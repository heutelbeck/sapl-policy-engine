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
import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SubtemplateCompilerTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateExpression_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_subtemplateExpression_then_returnsExpected() {
        return Stream.of(
            // === Literals ===
            // Array with @ (relative value) - basic identity
            arguments("array with identity template", "[1, 2, 3] :: @",
                Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
            // Array with # (relative location/index)
            arguments("array with index only", "[10, 20, 30] :: #",
                Value.ofArray(Value.of(0), Value.of(1), Value.of(2))),
            // Empty array
            arguments("empty array with @ returns empty", "[] :: @", Value.EMPTY_ARRAY),
            arguments("empty array with # returns empty", "[] :: #", Value.EMPTY_ARRAY),
            // Scalar (non-array) values
            arguments("scalar with @ identity", "5 :: @", Value.of(5)),
            arguments("scalar with # (should be 0)", "5 :: #", Value.of(0)),
            arguments("scalar string with @", "\"hello\" :: @", Value.of("hello")),
            // Constant template (ignores @)
            arguments("constant template on array", "[1, 2, 3] :: 99",
                Value.ofArray(Value.of(99), Value.of(99), Value.of(99))),
            arguments("constant template on scalar", "5 :: 99", Value.of(99)),
            // String operations
            arguments("string array identity", "[\"a\", \"b\"] :: @",
                Value.ofArray(Value.of("a"), Value.of("b"))),
            // Null handling
            arguments("null template result", "[1, 2] :: null", Value.ofArray(Value.NULL, Value.NULL)),
            // Nested arrays
            arguments("nested array identity", "[[1, 2], [3, 4]] :: @",
                Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)), Value.ofArray(Value.of(3), Value.of(4)))),

            // === Objects ===
            arguments("object with value identity", "{\"a\": 1, \"b\": 2} :: @",
                Value.ofArray(Value.of(1), Value.of(2))),
            arguments("object with key only", "{\"foo\": 1, \"bar\": 2} :: #",
                Value.ofArray(Value.of("foo"), Value.of("bar"))),
            arguments("empty object returns empty array", "{} :: @", Value.EMPTY_ARRAY),
            arguments("empty object with key", "{} :: #", Value.EMPTY_ARRAY),

            // === Arithmetic ===
            arguments("array multiply each", "[1, 2, 3] :: (@ * 2)",
                Value.ofArray(Value.of(2), Value.of(4), Value.of(6))),
            arguments("array add constant", "[1, 2, 3] :: (@ + 10)",
                Value.ofArray(Value.of(11), Value.of(12), Value.of(13))),
            arguments("value plus index", "[10, 20, 30] :: (@ + #)",
                Value.ofArray(Value.of(10), Value.of(21), Value.of(32))),
            arguments("index times ten", "[5, 5, 5] :: (# * 10)",
                Value.ofArray(Value.of(0), Value.of(10), Value.of(20))),
            arguments("scalar multiply", "5 :: (@ * 3)", Value.of(15)),
            arguments("scalar with index", "100 :: (@ + #)", Value.of(100)),

            // === Object Access ===
            arguments("extract field from array of objects", "[{\"x\": 1}, {\"x\": 2}, {\"x\": 3}] :: @.x",
                Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
            arguments("nested field access", "[{\"a\": {\"b\": 1}}, {\"a\": {\"b\": 2}}] :: @.a.b",
                Value.ofArray(Value.of(1), Value.of(2))),

            // === Nested Subtemplate ===
            arguments("nested array transform", "[[1, 2], [3, 4]] :: (@ :: (@ * 2))",
                Value.ofArray(Value.ofArray(Value.of(2), Value.of(4)), Value.ofArray(Value.of(6), Value.of(8)))),

            // === Boolean Expressions ===
            arguments("compare each to threshold", "[1, 5, 10] :: (@ > 3)",
                Value.ofArray(Value.FALSE, Value.TRUE, Value.TRUE)),
            arguments("even index check", "[10, 20, 30, 40] :: (# == 0 || # == 2)",
                Value.ofArray(Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE)));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithSpecialValues_then_propagatesCorrectly(String description, Value left, Value right,
            Class<?> expectedType, Value expectedValue) {
        val compiler  = new SubtemplateCompiler();
        val ctx       = new CompilationContext(null, null);
        val leftExpr  = new Literal(left, TEST_LOCATION);
        val rightExpr = new Literal(right, TEST_LOCATION);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.SUBTEMPLATE, leftExpr, rightExpr, TEST_LOCATION);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isInstanceOf(expectedType);
        if (expectedValue != null) {
            assertThat(result).isEqualTo(expectedValue);
        }
    }

    private static Stream<Arguments> when_subtemplateWithSpecialValues_then_propagatesCorrectly() {
        return Stream.of(
                arguments("undefined parent propagates undefined", Value.UNDEFINED, Value.of(1), Value.class,
                        Value.UNDEFINED),
                arguments("error parent propagates error", Value.error("test error"), Value.of(1), ErrorValue.class,
                        null),
                arguments("error template propagates error", Value.of(5), Value.error("template error"),
                        ErrorValue.class, null));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_applyPureTemplate_then_returnsExpected(String description, Value parent, TestPureOperator template,
            Value expected) {
        val result = SubtemplateCompiler.applyPureTemplate(parent, template, emptyEvaluationContext());
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    private static final TestPureOperator LOCATION_TEMPLATE = new TestPureOperator(EvaluationContext::relativeLocation);
    private static final TestPureOperator VALUE_TEMPLATE    = new TestPureOperator(EvaluationContext::relativeValue);
    private static final TestPureOperator ERROR_TEMPLATE    = new TestPureOperator(
            ctx -> Value.error("template error"));

    private static Stream<Arguments> when_applyPureTemplate_then_returnsExpected() {
        val array  = Value.ofArray(Value.of(10), Value.of(20), Value.of(30));
        val scalar = Value.of(42);
        val error  = Value.error("parent error");

        return Stream.of(
                arguments("array with location template returns indices", array, LOCATION_TEMPLATE,
                        Value.ofArray(Value.of(0), Value.of(1), Value.of(2))),
                arguments("array with value template returns values", array, VALUE_TEMPLATE, array),
                arguments("scalar with location template returns zero", scalar, LOCATION_TEMPLATE, Value.of(0)),
                arguments("undefined parent returns undefined", Value.UNDEFINED, VALUE_TEMPLATE, Value.UNDEFINED),
                arguments("error parent returns error", error, VALUE_TEMPLATE, error),
                arguments("template returns error propagates", Value.ofArray(Value.of(1), Value.of(2)), ERROR_TEMPLATE,
                        Value.error("template error")));
    }

    @Test
    void when_applyPureTemplate_withObject_then_setsKeyCorrectly() {
        val obj      = obj("alpha", Value.of(1), "beta", Value.of(2));
        val template = new TestPureOperator(EvaluationContext::relativeLocation);

        val result = SubtemplateCompiler.applyPureTemplate(obj, template, emptyEvaluationContext());
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(2);
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_applyConstantTemplate_then_returnsExpected(String description, Value parent, Value template,
            Value expected) {
        val result = SubtemplateCompiler.applyConstantTemplate(parent, template);
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    private static Stream<Arguments> when_applyConstantTemplate_then_returnsExpected() {
        val array  = Value.ofArray(Value.of(10), Value.of(20), Value.of(30));
        val obj    = obj("a", Value.of(1), "b", Value.of(2));
        val error  = Value.error("parent error");
        val tmpl99 = Value.of(99);

        return Stream.of(
                arguments("array replicates template", array, tmpl99,
                        Value.ofArray(Value.of(99), Value.of(99), Value.of(99))),
                arguments("object replicates template", obj, Value.of("constant"),
                        Value.ofArray(Value.of("constant"), Value.of("constant"))),
                arguments("scalar returns template", Value.of(42), Value.of("replaced"), Value.of("replaced")),
                arguments("undefined parent returns undefined", Value.UNDEFINED, tmpl99, Value.UNDEFINED),
                arguments("error parent returns error", error, tmpl99, error),
                arguments("empty array returns empty", Value.EMPTY_ARRAY, tmpl99, Value.EMPTY_ARRAY),
                arguments("empty object returns empty", Value.EMPTY_OBJECT, tmpl99, Value.EMPTY_ARRAY));
    }

    @Test
    void when_subtemplateValuePure_isDependingOnSubscription_then_delegatesToTemplate() {
        val parent   = Value.of(5);
        val template = new TestPureOperator(EvaluationContext::relativeValue, true);

        val op = new SubtemplateCompiler.SubtemplateValuePure(parent, template, TEST_LOCATION);
        assertThat(op.isDependingOnSubscription()).isTrue();
    }

    @Test
    void when_subtemplatePureValue_isDependingOnSubscription_then_delegatesToParent() {
        val parent = new TestPureOperator(ctx -> Value.of(5), true);

        val op = new SubtemplateCompiler.SubtemplatePureValue(parent, Value.of(1), TEST_LOCATION);
        assertThat(op.isDependingOnSubscription()).isTrue();
    }

    @MethodSource
    @ParameterizedTest(name = "parent={0}, template={1} -> {2}")
    void when_subtemplatePurePure_isDependingOnSubscription_then_combinesParentAndTemplate(boolean parentDepends,
            boolean templateDepends, boolean expected) {
        val parent   = new TestPureOperator(ctx -> Value.of(5), parentDepends);
        val template = new TestPureOperator(EvaluationContext::relativeValue, templateDepends);
        val op       = new SubtemplateCompiler.SubtemplatePurePure(parent, template, TEST_LOCATION);

        assertThat(op.isDependingOnSubscription()).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplatePurePure_isDependingOnSubscription_then_combinesParentAndTemplate() {
        return Stream.of(arguments(true, true, true),    // Both depend -> true
                arguments(true, false, true),   // Only parent depends -> true
                arguments(false, true, true),   // Only template depends -> true
                arguments(false, false, false)); // Neither depends -> false
    }

    @Test
    void when_subtemplateValuePure_evaluate_then_appliesTemplateWithContext() {
        val parent   = Value.ofArray(Value.of(10), Value.of(20));
        val template = new TestPureOperator(ctx -> {
                         val value = (NumberValue) ctx.relativeValue();
                         val index = (NumberValue) ctx.relativeLocation();
                         return Value.of(value.value().intValue() + index.value().intValue());
                     });

        val op     = new SubtemplateCompiler.SubtemplateValuePure(parent, template, TEST_LOCATION);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(10), Value.of(21)));
    }

    @Test
    void when_subtemplatePureValue_evaluate_then_appliesConstantTemplate() {
        val arrayValue = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        val parent     = new TestPureOperator(ctx -> arrayValue);

        val op     = new SubtemplateCompiler.SubtemplatePureValue(parent, Value.of(99), TEST_LOCATION);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(99), Value.of(99), Value.of(99)));
    }

    @Test
    void when_subtemplatePurePure_evaluate_then_appliesTemplateWithContext() {
        val arrayValue = Value.ofArray(Value.of(5), Value.of(10));
        val parent     = new TestPureOperator(ctx -> arrayValue);

        val template = new TestPureOperator(ctx -> {
            val value = (NumberValue) ctx.relativeValue();
            return Value.of(value.value().intValue() * 2L);
        });

        val op     = new SubtemplateCompiler.SubtemplatePurePure(parent, template, TEST_LOCATION);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(10), Value.of(20)));
    }

}
