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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.ExpressionTestUtil.*;
import io.sapl.api.model.PureOperator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ExpressionCompilerTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileExpression_then_returnsExpectedValue(String description, String expression,
            CompiledExpression expected) {
        val actual = evaluateExpression(expression);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileExpression_then_returnsExpectedValue() {
        return Stream.of(
                // Literals
                arguments("integer", "42", Value.of(42)), arguments("decimal", "2.5", Value.of(2.5)),
                arguments("zero", "0", Value.of(0)), arguments("string", "\"hello\"", Value.of("hello")),
                arguments("empty string", "\"\"", Value.of("")), arguments("true", "true", Value.TRUE),
                arguments("false", "false", Value.FALSE), arguments("null", "null", Value.NULL),
                arguments("undefined", "undefined", Value.UNDEFINED),
                // Parenthesized
                arguments("parenthesized integer", "(42)", Value.of(42)),
                arguments("parenthesized string", "(\"test\")", Value.of("test")),
                arguments("parenthesized boolean", "(true)", Value.TRUE),
                arguments("nested parentheses", "((42))", Value.of(42)),
                arguments("deeply nested", "(((false)))", Value.FALSE));
    }

    // ========== Subscription Elements ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileSubscriptionElement_then_returnsSubscriptionValue(String description, String expression,
            CompiledExpression expected) {
        val ctx    = subscriptionContext();
        val actual = evaluateExpression(expression, ctx);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileSubscriptionElement_then_returnsSubscriptionValue() {
        return Stream.of(arguments("subject", "subject", Value.of("alice")),
                arguments("action", "action", Value.of("read")),
                arguments("resource", "resource", Value.of("document")),
                arguments("environment", "environment", Value.of("production")));
    }

    private static EvaluationContext subscriptionContext() {
        val subscription = new AuthorizationSubscription(Value.of("alice"), Value.of("read"), Value.of("document"),
                Value.of("production"));
        return new EvaluationContext(null, null, null, subscription, null, null);
    }

    // ========== Unary Operations ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileUnaryOperation_then_returnsResult(String description, String expression, Object expected) {
        val actual = evaluateExpression(expression);
        if (expected instanceof Class<?> c)
            assertThat(actual).isInstanceOf(c);
        else
            assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileUnaryOperation_then_returnsResult() {
        return Stream.of(
                // Logical NOT
                arguments("not true", "!true", Value.FALSE), arguments("not false", "!false", Value.TRUE),
                arguments("double negation", "!!true", Value.TRUE), arguments("not integer", "!5", ErrorValue.class),
                arguments("not string", "!\"text\"", ErrorValue.class),
                arguments("not null", "!null", ErrorValue.class),
                // Unary minus
                arguments("negate positive", "-5", Value.of(-5)), arguments("negate negative", "-(-5)", Value.of(5)),
                arguments("negate zero", "-0", Value.of(0)), arguments("negate decimal", "-2.5", Value.of(-2.5)),
                arguments("negate boolean", "-true", ErrorValue.class),
                arguments("negate string", "-\"text\"", ErrorValue.class),
                arguments("negate null", "-null", ErrorValue.class),
                // Unary plus
                arguments("plus positive", "+5", Value.of(5)), arguments("plus negative", "+(-5)", Value.of(-5)),
                arguments("plus zero", "+0", Value.of(0)), arguments("plus boolean", "+true", ErrorValue.class),
                arguments("plus string", "+\"text\"", ErrorValue.class),
                arguments("plus null", "+null", ErrorValue.class));
    }

    // ========== Array Expressions ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileArrayExpression_then_returnsResult(String description, String expression, Object expected) {
        val actual = evaluateExpression(expression);
        if (expected instanceof Class<?> c)
            assertThat(actual).isInstanceOf(c);
        else
            assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileArrayExpression_then_returnsResult() {
        return Stream.of(
                // Empty and simple
                arguments("empty array", "[]", Value.EMPTY_ARRAY),
                arguments("single element", "[1]", Value.ofArray(Value.of(1))),
                arguments("two elements", "[1, 2]", Value.ofArray(Value.of(1), Value.of(2))),
                arguments("three elements", "[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                // Mixed types
                arguments("mixed types", "[1, \"a\", true]", Value.ofArray(Value.of(1), Value.of("a"), Value.TRUE)),
                // Nested
                arguments("nested array", "[[1, 2], [3, 4]]",
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                                Value.ofArray(Value.of(3), Value.of(4)))),
                // Undefined handling
                arguments("undefined dropped", "[1, undefined, 2]", Value.ofArray(Value.of(1), Value.of(2))),
                arguments("all undefined", "[undefined, undefined]", Value.EMPTY_ARRAY),
                // Expressions as elements
                arguments("expression elements", "[!false, -5]", Value.ofArray(Value.TRUE, Value.of(-5))),
                // Error propagation
                arguments("error propagates", "[1, !5, 2]", ErrorValue.class));
    }

    // ========== Object Expressions ==========

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_compileObjectExpression_then_returnsResult(String description, String expression, Object expected) {
        val actual = evaluateExpression(expression);
        if (expected instanceof Class<?> c)
            assertThat(actual).isInstanceOf(c);
        else
            assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> when_compileObjectExpression_then_returnsResult() {
        return Stream.of(
                // Empty and simple
                arguments("empty object", "{}", Value.EMPTY_OBJECT),
                arguments("single property", "{a: 1}", obj("a", Value.of(1))),
                arguments("two properties", "{a: 1, b: 2}", obj("a", Value.of(1), "b", Value.of(2))),
                // Mixed value types
                arguments("mixed value types", "{n: 1, s: \"x\", b: true}",
                        obj("n", Value.of(1), "s", Value.of("x"), "b", Value.TRUE)),
                // Nested
                arguments("nested object", "{outer: {inner: 1}}", obj("outer", obj("inner", Value.of(1)))),
                arguments("object with array", "{arr: [1, 2]}", obj("arr", Value.ofArray(Value.of(1), Value.of(2)))),
                // Undefined handling
                arguments("undefined value dropped", "{a: 1, b: undefined, c: 2}",
                        obj("a", Value.of(1), "c", Value.of(2))),
                arguments("all undefined values", "{a: undefined, b: undefined}", Value.EMPTY_OBJECT),
                // Expressions as values
                arguments("expression values", "{neg: -5, not: !false}", obj("neg", Value.of(-5), "not", Value.TRUE)),
                // Error propagation
                arguments("error propagates", "{a: 1, b: !5, c: 2}", ErrorValue.class));
    }

    // ========== Constant Folding Tests ==========

    @MethodSource("constantFoldingCases")
    @ParameterizedTest(name = "constant folding: {0}")
    void when_allLiterals_then_constantFoldsToValue(String description, String expression, Value expected) {
        val compiled = compileExpression(expression);
        assertThat(compiled).as("should constant-fold to Value, not PureOperator").isInstanceOf(Value.class)
                .isEqualTo(expected);
    }

    private static Stream<Arguments> constantFoldingCases() {
        return Stream.of(
                // Arrays
                arguments("empty array", "[]", Value.EMPTY_ARRAY),
                arguments("single literal", "[1]", Value.ofArray(Value.of(1))),
                arguments("multiple literals", "[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("mixed literal types", "[1, \"a\", true, null]",
                        Value.ofArray(Value.of(1), Value.of("a"), Value.TRUE, Value.NULL)),
                arguments("nested literal arrays", "[[1, 2], [3, 4]]",
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                                Value.ofArray(Value.of(3), Value.of(4)))),
                arguments("array with undefined dropped", "[1, undefined, 2]", Value.ofArray(Value.of(1), Value.of(2))),
                // Objects
                arguments("empty object", "{}", Value.EMPTY_OBJECT),
                arguments("single property", "{a: 1}", obj("a", Value.of(1))),
                arguments("multiple properties", "{a: 1, b: 2, c: 3}",
                        obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3))),
                arguments("mixed value types", "{n: 1, s: \"x\", b: true, nil: null}",
                        obj("n", Value.of(1), "s", Value.of("x"), "b", Value.TRUE, "nil", Value.NULL)),
                arguments("nested literal objects", "{outer: {inner: 1}}", obj("outer", obj("inner", Value.of(1)))),
                arguments("object with undefined dropped", "{a: 1, b: undefined, c: 2}",
                        obj("a", Value.of(1), "c", Value.of(2))),
                // Mixed nesting
                arguments("object with array value", "{arr: [1, 2]}",
                        obj("arr", Value.ofArray(Value.of(1), Value.of(2)))),
                arguments("array with object element", "[{a: 1}, {b: 2}]",
                        Value.ofArray(obj("a", Value.of(1)), obj("b", Value.of(2)))));
    }

    // ========== Pure Operator Tests (with variable references) ==========

    @MethodSource("pureOperatorCases")
    @ParameterizedTest(name = "pure operator: {0}")
    void when_containsVariableReference_then_returnsPureOperatorWithCorrectValue(String description, String expression,
            Value varValue, Value expected) {
        val compiled = compileExpression(expression);
        assertThat(compiled).as("should be PureOperator when containing variable reference")
                .isInstanceOf(PureOperator.class);

        val ctx    = withVariables(java.util.Map.of("x", varValue));
        val result = ((PureOperator) compiled).evaluate(ctx);
        assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> pureOperatorCases() {
        return Stream.of(
                // Arrays with variable
                arguments("array with single var", "[x]", Value.of(42), Value.ofArray(Value.of(42))),
                arguments("array with var and literal", "[1, x, 3]", Value.of(2),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("array with undefined var dropped", "[1, x, 3]", Value.UNDEFINED,
                        Value.ofArray(Value.of(1), Value.of(3))),
                arguments("array with var at start", "[x, 2, 3]", Value.of(1),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("array with var at end", "[1, 2, x]", Value.of(3),
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                // Objects with variable
                arguments("object with single var value", "{a: x}", Value.of(42), obj("a", Value.of(42))),
                arguments("object with var and literal", "{a: 1, b: x, c: 3}", Value.of(2),
                        obj("a", Value.of(1), "b", Value.of(2), "c", Value.of(3))),
                arguments("object with undefined var dropped", "{a: 1, b: x, c: 3}", Value.UNDEFINED,
                        obj("a", Value.of(1), "c", Value.of(3))),
                // Nested with variable
                arguments("nested array with var", "[[x, 2], [3, 4]]", Value.of(1),
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                                Value.ofArray(Value.of(3), Value.of(4)))),
                arguments("nested object with var", "{outer: {inner: x}}", Value.of(99),
                        obj("outer", obj("inner", Value.of(99)))),
                arguments("object with array containing var", "{arr: [x, 2]}", Value.of(1),
                        obj("arr", Value.ofArray(Value.of(1), Value.of(2)))));
    }

}
