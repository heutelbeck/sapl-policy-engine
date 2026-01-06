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

import io.sapl.api.model.*;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Literal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SubtemplateCompilerTests {

    private static final SourceLocation TEST_LOC = new SourceLocation("test", "", 0, 0, 1, 1, 1, 1);

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithLiterals_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplateWithLiterals_then_returnsExpected() {
        return Stream.of(
                // Array with @ (relative value) - basic identity
                arguments("array with identity template", "[1, 2, 3] :: @",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),

                // Array with # (relative location/index) - NEW FEATURE
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
                arguments("string array identity", "[\"a\", \"b\"] :: @", Value.ofArray(Value.of("a"), Value.of("b"))),

                // Null handling
                arguments("null template result", "[1, 2] :: null", Value.ofArray(Value.NULL, Value.NULL)),

                // Nested arrays
                arguments("nested array identity", "[[1, 2], [3, 4]] :: @", Value
                        .ofArray(Value.ofArray(Value.of(1), Value.of(2)), Value.ofArray(Value.of(3), Value.of(4)))));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithObjects_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplateWithObjects_then_returnsExpected() {
        return Stream.of(
                // Object iteration returns array of transformed values
                arguments("object with value identity", "{\"a\": 1, \"b\": 2} :: @",
                        Value.ofArray(Value.of(1), Value.of(2))),

                // Object with # (key as string) - NEW FEATURE
                arguments("object with key only", "{\"foo\": 1, \"bar\": 2} :: #",
                        Value.ofArray(Value.of("foo"), Value.of("bar"))),

                // Empty object
                arguments("empty object returns empty array", "{} :: @", Value.EMPTY_ARRAY),
                arguments("empty object with key", "{} :: #", Value.EMPTY_ARRAY));
    }

    @Test
    void when_subtemplateWithUndefined_then_propagatesUndefined() {
        val compiler = new SubtemplateCompiler();
        val ctx      = new CompilationContext(null, null, null);

        val leftExpr  = new Literal(Value.UNDEFINED, TEST_LOC);
        val rightExpr = new Literal(Value.of(1), TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.SUBTEMPLATE, leftExpr, rightExpr, TEST_LOC);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void when_subtemplateWithError_then_propagatesError() {
        val compiler = new SubtemplateCompiler();
        val ctx      = new CompilationContext(null, null, null);

        val error     = Value.error("test error");
        val leftExpr  = new Literal(error, TEST_LOC);
        val rightExpr = new Literal(Value.of(1), TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.SUBTEMPLATE, leftExpr, rightExpr, TEST_LOC);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_subtemplateTemplateIsError_then_propagatesError() {
        val compiler = new SubtemplateCompiler();
        val ctx      = new CompilationContext(null, null, null);

        val error     = Value.error("template error");
        val leftExpr  = new Literal(Value.of(5), TEST_LOC);
        val rightExpr = new Literal(error, TEST_LOC);
        val binaryOp  = new BinaryOperator(BinaryOperatorType.SUBTEMPLATE, leftExpr, rightExpr, TEST_LOC);

        val result = compiler.compile(binaryOp, ctx);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_applyPureTemplate_withArray_then_setsIndexCorrectly() {
        val array    = Value.ofArray(Value.of(10), Value.of(20), Value.of(30));
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeLocation());

        val result = SubtemplateCompiler.applyPureTemplate(array, template, ctx);
        assertThat(result).isEqualTo(Value.ofArray(Value.of(0), Value.of(1), Value.of(2)));
    }

    @Test
    void when_applyPureTemplate_withArray_then_setsValueCorrectly() {
        val array    = Value.ofArray(Value.of(10), Value.of(20), Value.of(30));
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeValue());

        val result = SubtemplateCompiler.applyPureTemplate(array, template, ctx);
        assertThat(result).isEqualTo(Value.ofArray(Value.of(10), Value.of(20), Value.of(30)));
    }

    @Test
    void when_applyPureTemplate_withObject_then_setsKeyCorrectly() {
        val obj      = obj("alpha", Value.of(1), "beta", Value.of(2));
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeLocation());

        val result = SubtemplateCompiler.applyPureTemplate(obj, template, ctx);
        // Should contain the keys as values
        assertThat(result).isInstanceOf(ArrayValue.class);
        val arr = (ArrayValue) result;
        assertThat(arr.size()).isEqualTo(2);
        // Keys: "alpha" and "beta" (order may vary based on ObjectValue implementation)
    }

    @Test
    void when_applyPureTemplate_withScalar_then_setsIndexToZero() {
        val scalar   = Value.of(42);
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeLocation());

        val result = SubtemplateCompiler.applyPureTemplate(scalar, template, ctx);
        assertThat(result).isEqualTo(Value.of(0));
    }

    @Test
    void when_applyPureTemplate_withUndefinedParent_then_returnsUndefined() {
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeValue());

        val result = SubtemplateCompiler.applyPureTemplate(Value.UNDEFINED, template, ctx);
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void when_applyPureTemplate_withErrorParent_then_returnsError() {
        val error    = Value.error("parent error");
        val ctx      = emptyEvaluationContext();
        val template = new TestPureOperator(evalCtx -> evalCtx.relativeValue());

        val result = SubtemplateCompiler.applyPureTemplate(error, template, ctx);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_applyPureTemplate_templateReturnsError_then_propagatesError() {
        val array = Value.ofArray(Value.of(1), Value.of(2));
        val ctx   = emptyEvaluationContext();

        // Template that always returns error
        val template = new TestPureOperator(evalCtx -> Value.error("template error"));

        val result = SubtemplateCompiler.applyPureTemplate(array, template, ctx);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_applyConstantTemplate_withArray_then_replicatesTemplate() {
        val array    = Value.ofArray(Value.of(10), Value.of(20), Value.of(30));
        val template = Value.of(99);

        val result = SubtemplateCompiler.applyConstantTemplate(array, template);
        assertThat(result).isEqualTo(Value.ofArray(Value.of(99), Value.of(99), Value.of(99)));
    }

    @Test
    void when_applyConstantTemplate_withObject_then_replicatesTemplate() {
        val obj      = obj("a", Value.of(1), "b", Value.of(2));
        val template = Value.of("constant");

        val result = SubtemplateCompiler.applyConstantTemplate(obj, template);
        assertThat(result).isEqualTo(Value.ofArray(Value.of("constant"), Value.of("constant")));
    }

    @Test
    void when_applyConstantTemplate_withScalar_then_returnsTemplate() {
        val scalar   = Value.of(42);
        val template = Value.of("replaced");

        val result = SubtemplateCompiler.applyConstantTemplate(scalar, template);
        assertThat(result).isEqualTo(Value.of("replaced"));
    }

    @Test
    void when_applyConstantTemplate_withUndefinedParent_then_returnsUndefined() {
        val result = SubtemplateCompiler.applyConstantTemplate(Value.UNDEFINED, Value.of(99));
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void when_applyConstantTemplate_withErrorParent_then_returnsError() {
        val error  = Value.error("parent error");
        val result = SubtemplateCompiler.applyConstantTemplate(error, Value.of(99));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_applyConstantTemplate_withEmptyArray_then_returnsEmptyArray() {
        val result = SubtemplateCompiler.applyConstantTemplate(Value.EMPTY_ARRAY, Value.of(99));
        assertThat(result).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_applyConstantTemplate_withEmptyObject_then_returnsEmptyArray() {
        val result = SubtemplateCompiler.applyConstantTemplate(Value.EMPTY_OBJECT, Value.of(99));
        assertThat(result).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_subtemplateValuePure_isDependingOnSubscription_then_delegatesToTemplate() {
        val parent   = Value.of(5);
        val template = new TestPureOperator(ctx -> ctx.relativeValue(), true);

        val op = new SubtemplateCompiler.SubtemplateValuePure(parent, template, TEST_LOC);
        assertThat(op.isDependingOnSubscription()).isTrue();
    }

    @Test
    void when_subtemplatePureValue_isDependingOnSubscription_then_delegatesToParent() {
        val parent = new TestPureOperator(ctx -> Value.of(5), true);

        val op = new SubtemplateCompiler.SubtemplatePureValue(parent, Value.of(1), TEST_LOC);
        assertThat(op.isDependingOnSubscription()).isTrue();
    }

    @Test
    void when_subtemplatePurePure_isDependingOnSubscription_then_combinesParentAndTemplate() {
        val parentDepends      = new TestPureOperator(ctx -> Value.of(5), true);
        val templateDepends    = new TestPureOperator(ctx -> ctx.relativeValue(), true);
        val parentNotDepends   = new TestPureOperator(ctx -> Value.of(5), false);
        val templateNotDepends = new TestPureOperator(ctx -> ctx.relativeValue(), false);

        // Both depend
        assertThat(new SubtemplateCompiler.SubtemplatePurePure(parentDepends, templateDepends, TEST_LOC)
                .isDependingOnSubscription()).isTrue();
        // Only parent depends
        assertThat(new SubtemplateCompiler.SubtemplatePurePure(parentDepends, templateNotDepends, TEST_LOC)
                .isDependingOnSubscription()).isTrue();
        // Only template depends
        assertThat(new SubtemplateCompiler.SubtemplatePurePure(parentNotDepends, templateDepends, TEST_LOC)
                .isDependingOnSubscription()).isTrue();
        // Neither depends
        assertThat(new SubtemplateCompiler.SubtemplatePurePure(parentNotDepends, templateNotDepends, TEST_LOC)
                .isDependingOnSubscription()).isFalse();
    }

    @Test
    void when_subtemplateValuePure_evaluate_then_appliesTemplateWithContext() {
        val parent   = Value.ofArray(Value.of(10), Value.of(20));
        val template = new TestPureOperator(ctx -> {
                         val value = (NumberValue) ctx.relativeValue();
                         val index = (NumberValue) ctx.relativeLocation();
                         return Value.of(value.value().intValue() + index.value().intValue());
                     });

        val op     = new SubtemplateCompiler.SubtemplateValuePure(parent, template, TEST_LOC);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(10), Value.of(21)));
    }

    @Test
    void when_subtemplatePureValue_evaluate_then_appliesConstantTemplate() {
        val parent = new TestPureOperator(ctx -> Value.ofArray(Value.of(1), Value.of(2), Value.of(3)));

        val op     = new SubtemplateCompiler.SubtemplatePureValue(parent, Value.of(99), TEST_LOC);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(99), Value.of(99), Value.of(99)));
    }

    @Test
    void when_subtemplatePurePure_evaluate_then_appliesTemplateWithContext() {
        val parent = new TestPureOperator(ctx -> Value.ofArray(Value.of(5), Value.of(10)));

        val template = new TestPureOperator(ctx -> {
            val value = (NumberValue) ctx.relativeValue();
            return Value.of(value.value().intValue() * 2);
        });

        val op     = new SubtemplateCompiler.SubtemplatePurePure(parent, template, TEST_LOC);
        val result = op.evaluate(emptyEvaluationContext());

        assertThat(result).isEqualTo(Value.ofArray(Value.of(10), Value.of(20)));
    }

    // =========================================================================
    // DISABLED TESTS - Require arithmetic/complex expression support
    // These tests demonstrate the EXPECTED behavior once all expression
    // compilers are fully implemented. Enable once Sum/Product are working.
    // =========================================================================

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithArithmetic_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplateWithArithmetic_then_returnsExpected() {
        // NOTE: Parentheses required! The :: operator binds tighter than arithmetic,
        // so "[1,2,3] :: @ * 2" parses as "([1,2,3] :: @) * 2", not "[1,2,3] :: (@ *
        // 2)"
        return Stream.of(
                // Array element arithmetic
                arguments("array multiply each", "[1, 2, 3] :: (@ * 2)",
                        Value.ofArray(Value.of(2), Value.of(4), Value.of(6))),
                arguments("array add constant", "[1, 2, 3] :: (@ + 10)",
                        Value.ofArray(Value.of(11), Value.of(12), Value.of(13))),

                // Combined @ and # arithmetic
                arguments("value plus index", "[10, 20, 30] :: (@ + #)",
                        Value.ofArray(Value.of(10), Value.of(21), Value.of(32))),
                arguments("index times ten", "[5, 5, 5] :: (# * 10)",
                        Value.ofArray(Value.of(0), Value.of(10), Value.of(20))),

                // Scalar with arithmetic
                arguments("scalar multiply", "5 :: (@ * 3)", Value.of(15)),
                arguments("scalar with index", "100 :: (@ + #)", Value.of(100)));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithObjectAccess_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplateWithObjectAccess_then_returnsExpected() {
        return Stream.of(
                // Object field projection
                arguments("extract field from array of objects", "[{\"x\": 1}, {\"x\": 2}, {\"x\": 3}] :: @.x",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),

                // Nested access
                arguments("nested field access", "[{\"a\": {\"b\": 1}}, {\"a\": {\"b\": 2}}] :: @.a.b",
                        Value.ofArray(Value.of(1), Value.of(2))));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_nestedSubtemplate_then_returnsExpected(String description, String expression, Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_nestedSubtemplate_then_returnsExpected() {
        // Nested subtemplates: outer :: operates on arrays, inner :: on elements
        return Stream.of(arguments("nested array transform", "[[1, 2], [3, 4]] :: (@ :: (@ * 2))",
                Value.ofArray(Value.ofArray(Value.of(2), Value.of(4)), Value.ofArray(Value.of(6), Value.of(8)))));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_subtemplateWithBooleanExpressions_then_returnsExpected(String description, String expression,
            Value expected) {
        val result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> when_subtemplateWithBooleanExpressions_then_returnsExpected() {
        // NOTE: Parentheses required for comparison/logical ops (same precedence rule
        // as arithmetic)
        return Stream.of(
                // Boolean comparison on each element
                arguments("compare each to threshold", "[1, 5, 10] :: (@ > 3)",
                        Value.ofArray(Value.FALSE, Value.TRUE, Value.TRUE)),

                // Index-based filtering logic
                arguments("even index check", "[10, 20, 30, 40] :: (# == 0 || # == 2)",
                        Value.ofArray(Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE)));
    }

    // Helper class for testing that implements PureOperator properly
    private record TestPureOperator(
            java.util.function.Function<EvaluationContext, Value> evaluator,
            boolean isDependingOnSubscription) implements PureOperator {

        TestPureOperator(java.util.function.Function<EvaluationContext, Value> evaluator) {
            this(evaluator, false);
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluator.apply(ctx);
        }

        @Override
        public SourceLocation location() {
            return TEST_LOC;
        }
    }

}
