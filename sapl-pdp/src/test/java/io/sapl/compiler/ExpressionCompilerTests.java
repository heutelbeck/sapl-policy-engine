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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.MathFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.util.TestUtil;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.TestUtil.assertCompiledExpressionEvaluatesTo;
import static io.sapl.util.TestUtil.assertCompiledExpressionEvaluatesToErrorContaining;
import static io.sapl.util.TestUtil.assertExpressionCompilesToValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive tests for ExpressionCompiler covering constant folding, pure
 * expression compilation, step operations, and streaming attribute finders.
 */
class ExpressionCompilerTests {

    private static final CompilationContext CONTEXT = new CompilationContext(null, null);
    private static CompilationContext       contextWithFunctions;
    private static EvaluationContext        evaluationContext;

    @BeforeAll
    static void setupFunctionBroker() {
        val broker = new DefaultFunctionBroker();
        broker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(MathFunctionLibrary.class);
        contextWithFunctions = new CompilationContext(broker, null);
        evaluationContext    = new EvaluationContext(null, null, null, null, broker, null);
    }

    // ==========================================================================
    // Literal Constant Folding Tests
    // ==========================================================================

    @ParameterizedTest(name = "literal: {0}")
    @MethodSource
    void whenLiteral_thenConstantFolds(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenLiteral_thenConstantFolds() {
        return Stream.of(
                // Boolean literals
                arguments("true", Value.TRUE), arguments("false", Value.FALSE),
                // Null and undefined
                arguments("null", Value.NULL), arguments("undefined", Value.UNDEFINED),
                // Integer literals
                arguments("0", Value.of(0)), arguments("1", Value.of(1)), arguments("42", Value.of(42)),
                arguments("-17", Value.of(-17)), arguments("123456789", Value.of(123456789)),
                // Decimal literals
                arguments("3.14", Value.of(new BigDecimal("3.14"))), arguments("0.5", Value.of(new BigDecimal("0.5"))),
                arguments("5.14159", Value.of(new BigDecimal("5.14159"))),
                arguments("100.001", Value.of(new BigDecimal("100.001"))),
                // String literals
                arguments("\"Stormbringer\"", Value.of("Stormbringer")), arguments("\"\"", Value.EMPTY_TEXT),
                arguments("\"Hello World\"", Value.of("Hello World")),
                arguments("\"special chars: @#$%\"", Value.of("special chars: @#$%")),
                // Escaped strings
                arguments("\"line1\\nline2\"", Value.of("line1\nline2")),
                arguments("\"tab\\there\"", Value.of("tab\there")),
                arguments("\"quote\\\"inside\"", Value.of("quote\"inside")),
                arguments("\"backslash\\\\here\"", Value.of("backslash\\here")),
                arguments("\"carriage\\rreturn\"", Value.of("carriage\rreturn")));
    }

    // ==========================================================================
    // Array Constant Folding Tests
    // ==========================================================================

    @ParameterizedTest(name = "array: {0}")
    @MethodSource
    void whenArray_thenConstantFolds(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenArray_thenConstantFolds() {
        return Stream.of(arguments("[]", Value.EMPTY_ARRAY), arguments("[42]", Value.ofArray(Value.of(42))),
                arguments("[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("[true, false, null]", Value.ofArray(Value.TRUE, Value.FALSE, Value.NULL)),
                arguments("[\"Elric\", \"Moonglum\", \"Dyvim Tvar\"]",
                        Value.ofArray(Value.of("Elric"), Value.of("Moonglum"), Value.of("Dyvim Tvar"))),
                arguments("[1, \"two\", true, null]",
                        Value.ofArray(Value.of(1), Value.of("two"), Value.TRUE, Value.NULL)),
                // Nested arrays
                arguments("[[1, 2], [3, 4]]",
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)),
                                Value.ofArray(Value.of(3), Value.of(4)))),
                arguments("[[1, 2], [3, 4], [5, 6]]",
                        Value.ofArray(Value.ofArray(Value.of(1), Value.of(2)), Value.ofArray(Value.of(3), Value.of(4)),
                                Value.ofArray(Value.of(5), Value.of(6)))),
                // Undefined filtered out
                arguments("[1, undefined, 2]", Value.ofArray(Value.of(1), Value.of(2))),
                arguments("[undefined, undefined, undefined]", Value.EMPTY_ARRAY));
    }

    // ==========================================================================
    // Object Constant Folding Tests
    // ==========================================================================

    @ParameterizedTest(name = "object: {0}")
    @MethodSource
    void whenObject_thenConstantFolds(String expression, Value expected) {
        val result = compileExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> whenObject_thenConstantFolds() {
        return Stream.of(arguments("{}", Value.EMPTY_OBJECT),
                arguments("{\"lord\": \"Arioch\"}", ObjectValue.builder().put("lord", Value.of("Arioch")).build()),
                arguments("{\"emperor\": \"Elric\", \"city\": \"Imrryr\"}",
                        ObjectValue.builder().put("emperor", Value.of("Elric")).put("city", Value.of("Imrryr"))
                                .build()),
                arguments("{\"clearance\": 3, \"authorized\": true}",
                        ObjectValue.builder().put("clearance", Value.of(3)).put("authorized", Value.TRUE).build()),
                // Nested objects
                arguments("{\"sword\": {\"name\": \"Stormbringer\", \"souls\": 999}}",
                        ObjectValue.builder()
                                .put("sword",
                                        ObjectValue.builder().put("name", Value.of("Stormbringer"))
                                                .put("souls", Value.of(999)).build())
                                .build()),
                // Unquoted keys
                arguments("{name: \"Hastur\"}", ObjectValue.builder().put("name", Value.of("Hastur")).build()),
                // Array values
                arguments("{\"cultists\": [\"Wilbur\", \"Lavinia\"]}",
                        ObjectValue.builder().put("cultists", Value.ofArray(Value.of("Wilbur"), Value.of("Lavinia")))
                                .build()),
                // Undefined properties filtered out
                arguments("{\"present\": 1, \"absent\": undefined}",
                        ObjectValue.builder().put("present", Value.of(1)).build()),
                arguments("{\"a\": undefined, \"b\": undefined}", Value.EMPTY_OBJECT));
    }

    // ==========================================================================
    // Arithmetic Operator Tests
    // ==========================================================================

    @ParameterizedTest(name = "arithmetic: {0}")
    @MethodSource
    void whenArithmeticOperator_thenConstantFolds(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenArithmeticOperator_thenConstantFolds() {
        return Stream.of(
                // Basic operations
                arguments("2 + 3", Value.of(5)), arguments("10 - 7", Value.of(3)), arguments("6 * 7", Value.of(42)),
                arguments("100 / 4", Value.of(25)), arguments("17 % 5", Value.of(2)), arguments("7 / 2", Value.of(3.5)),
                // Precedence
                arguments("2 + 3 * 4", Value.of(14)), arguments("(2 + 3) * 4", Value.of(20)),
                arguments("100 / (2 + 3) * 2", Value.of(40)),
                // Chained operations
                arguments("1 + 2 + 3 + 4", Value.of(10)), arguments("10 - 3 + 2", Value.of(9)),
                arguments("2 * 3 * 4", Value.of(24)), arguments("24 / 4 * 2", Value.of(12)),
                arguments("5+5-3", Value.of(7)),
                // Unary operators
                arguments("-5", Value.of(-5)), arguments("+42", Value.of(42)), arguments("--1", Value.of(1)),
                arguments("-(10 - 3)", Value.of(-7)), arguments("+(5 + 5)", Value.of(10)),
                // Various spacing patterns
                arguments("1+-1", Value.of(0)), arguments("1+ -1", Value.of(0)), arguments("1 + -1", Value.of(0)),
                arguments("1 + - 1", Value.of(0)), arguments("1+ +(2)", Value.of(3)),
                arguments("(1+2)*3.0", Value.of(9.0)),
                // Decimal arithmetic
                arguments("3.14 + 2.86", Value.of(6)),
                // String concatenation
                arguments("\"hello\" + \"world\"", Value.of("helloworld")),
                arguments("\"count: \" + 42", Value.of("count: 42")),
                arguments("\"value: \" + true", Value.of("value: true")));
    }

    @ParameterizedTest(name = "arithmetic error: {0}")
    @MethodSource
    void whenArithmeticError_thenReturnsError(String expression) {
        assertThat(compileExpression(expression)).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> whenArithmeticError_thenReturnsError() {
        return Stream.of(arguments("10 / 0"), arguments("100 / 0"), arguments("42 / (5 - 5)"), arguments("10 % 0"),
                arguments("17 % (3 - 3)"), arguments("-\"text\""), arguments("\"hello\" - 5"),
                arguments("\"hello\" * 2"));
    }

    // ==========================================================================
    // Comparison Operator Tests
    // ==========================================================================

    @ParameterizedTest(name = "comparison: {0}")
    @MethodSource
    void whenComparisonOperator_thenConstantFolds(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenComparisonOperator_thenConstantFolds() {
        return Stream.of(
                // Less than
                arguments("3 < 5", Value.TRUE), arguments("5 < 3", Value.FALSE), arguments("3 < 3", Value.FALSE),
                arguments("-1 < 0", Value.TRUE),
                // Less or equal
                arguments("3 <= 5", Value.TRUE), arguments("5 <= 3", Value.FALSE), arguments("3 <= 3", Value.TRUE),
                arguments("0 <= 0", Value.TRUE),
                // Greater than
                arguments("5 > 3", Value.TRUE), arguments("3 > 5", Value.FALSE), arguments("3 > 3", Value.FALSE),
                arguments("2 > 1", Value.TRUE),
                // Greater or equal
                arguments("5 >= 3", Value.TRUE), arguments("3 >= 5", Value.FALSE), arguments("5 >= 5", Value.TRUE),
                arguments("1 >= 1", Value.TRUE),
                // Equality
                arguments("42 == 42", Value.TRUE), arguments("42 == 17", Value.FALSE),
                arguments("42 != 17", Value.TRUE), arguments("42 != 42", Value.FALSE),
                arguments("\"Imrryr\" == \"Imrryr\"", Value.TRUE), arguments("\"Imrryr\" != \"Tanelorn\"", Value.TRUE),
                arguments("true == true", Value.TRUE), arguments("null == null", Value.TRUE),
                arguments("0 == 0", Value.TRUE),
                // Type mismatch
                arguments("42 == \"42\"", Value.FALSE), arguments("true == 1", Value.FALSE),
                // Complex value equality
                arguments("[1, 2, 3] == [1, 2, 3]", Value.TRUE), arguments("[1, 2, 3] == [1, 2, 4]", Value.FALSE),
                arguments("{ a: 1, b: 2 } == { a: 1, b: 2 }", Value.TRUE),
                // Element membership
                arguments("3 in [1, 2, 3, 4]", Value.TRUE), arguments("5 in [1, 2, 3, 4]", Value.FALSE),
                arguments("\"read\" in [\"read\", \"write\", \"delete\"]", Value.TRUE),
                arguments("null in [null, 1, 2]", Value.TRUE), arguments("null in [1, 2, 3]", Value.FALSE),
                arguments("true in [true, false]", Value.TRUE), arguments("false in [true, false]", Value.TRUE),
                // Object value containment
                arguments("\"Innsmouth\" in { name: \"Innsmouth\", population: 1000 }", Value.TRUE),
                arguments("1000 in { name: \"Innsmouth\", population: 1000 }", Value.TRUE),
                arguments("\"name\" in { name: \"Innsmouth\", population: 1000 }", Value.FALSE),
                // Regex matching
                arguments("\"hello\" =~ \"hel.*\"", Value.TRUE), arguments("\"hello\" =~ \"^h.*o$\"", Value.TRUE),
                arguments("\"hello\" =~ \"world\"", Value.FALSE),
                arguments("\"Stormbringer\" =~ \"^Storm.*\"", Value.TRUE),
                arguments("\"Stormbringer\" =~ \"^Mourn.*\"", Value.FALSE),
                arguments("\"chaos@law.com\" =~ \".*@.*\\\\.com\"", Value.TRUE),
                // Combined operations
                arguments("(5 > 3) && (10 < 20)", Value.TRUE), arguments("(5 + 3) * 2 == 16", Value.TRUE),
                arguments("(true || false) && (3 < 5)", Value.TRUE));
    }

    @ParameterizedTest(name = "comparison error: {0}")
    @MethodSource
    void whenComparisonError_thenReturnsError(String expression) {
        assertThat(compileExpression(expression)).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> whenComparisonError_thenReturnsError() {
        return Stream.of(arguments("\"hello\" < 5"), arguments("1 in 42"), arguments("42 =~ \"\\\\d+\""));
    }

    @Test
    void whenRegexMatchWithInvalidPattern_thenThrowsCompileTimeError() {
        assertThatThrownBy(() -> compileExpression("\"hello\" =~ \"[\"")).isInstanceOf(SaplCompilerException.class)
                .hasMessageContaining("Invalid regular expression");
    }

    // ==========================================================================
    // Boolean Operator Tests
    // ==========================================================================

    @ParameterizedTest(name = "boolean: {0}")
    @MethodSource
    void whenBooleanOperator_thenConstantFolds(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenBooleanOperator_thenConstantFolds() {
        return Stream.of(
                // Lazy AND
                arguments("true && true", Value.TRUE), arguments("true && false", Value.FALSE),
                arguments("false && true", Value.FALSE), arguments("false && false", Value.FALSE),
                // Lazy OR
                arguments("true || true", Value.TRUE), arguments("true || false", Value.TRUE),
                arguments("false || true", Value.TRUE), arguments("false || false", Value.FALSE),
                // Chained lazy OR
                arguments("true || false || false", Value.TRUE), arguments("false || false || true", Value.TRUE),
                arguments("false || false || false", Value.FALSE), arguments("false || true || false", Value.TRUE),
                // Chained lazy AND
                arguments("true && true && true", Value.TRUE), arguments("true && false && true", Value.FALSE),
                arguments("false && true && true", Value.FALSE), arguments("true && true && false", Value.FALSE),
                // Eager OR
                arguments("true | false", Value.TRUE), arguments("false | true", Value.TRUE),
                arguments("false | false", Value.FALSE),
                // Eager AND
                arguments("true & true", Value.TRUE), arguments("true & false", Value.FALSE),
                arguments("false & false", Value.FALSE),
                // XOR
                arguments("true ^ false", Value.TRUE), arguments("false ^ true", Value.TRUE),
                arguments("true ^ true", Value.FALSE), arguments("false ^ false", Value.FALSE),
                arguments("true ^ false ^ true", Value.FALSE), arguments("true ^ true ^ true", Value.TRUE),
                // NOT
                arguments("!true", Value.FALSE), arguments("!false", Value.TRUE), arguments("!!true", Value.TRUE),
                arguments("!!false", Value.FALSE), arguments("!(true || false)", Value.FALSE),
                arguments("!(false && true)", Value.TRUE), arguments("!false || false", Value.TRUE),
                arguments("!true && true", Value.FALSE),
                // Precedence (AND before OR)
                arguments("true || false && false", Value.TRUE), arguments("false && true || true", Value.TRUE),
                arguments("(true || false) && false", Value.FALSE),
                // Mixed lazy and eager
                arguments("(true || false) & (false | true)", Value.TRUE),
                arguments("(false && true) | (true && true)", Value.TRUE),
                arguments("(true || true) & false", Value.FALSE), arguments("(false && false) | true", Value.TRUE),
                // Complex nested
                arguments("(true && (false || true))", Value.TRUE),
                arguments("(false || (true && false))", Value.FALSE),
                arguments("((true || false) && (true || false))", Value.TRUE),
                arguments("((false && true) || (false && true))", Value.FALSE),
                // Short-circuit preventing errors
                arguments("true || (1/0 > 0)", Value.TRUE), arguments("false && (1/0 > 0)", Value.FALSE),
                arguments("false || true || (1/0 > 0) || (2/0 > 0)", Value.TRUE),
                arguments("true && false && (1/0 > 0) && (2/0 > 0)", Value.FALSE),
                // Comparison before boolean
                arguments("3 < 5 && 7 > 4", Value.TRUE), arguments("3 > 5 || 7 < 4", Value.FALSE),
                // Complex expressions
                arguments("(5 > 3) && (2 < 4) || false", Value.TRUE), arguments("!(3 > 5) && (1 == 1)", Value.TRUE));
    }

    @ParameterizedTest(name = "boolean error: {0}")
    @MethodSource
    void whenBooleanError_thenReturnsError(String expression) {
        assertThat(compileExpression(expression)).isInstanceOf(ErrorValue.class);
    }

    static Stream<Arguments> whenBooleanError_thenReturnsError() {
        return Stream.of(arguments("!42"), arguments("true && 42"), arguments("42 || false"),
                arguments("false || (1/0 > 0)"), arguments("true && (1/0 > 0)"),
                // Lazy OR type errors - left operand
                arguments("\"Stormbringer\" || true"), arguments("999 || false"), arguments("null || true"),
                arguments("[\"Imrryr\", \"Tanelorn\"] || false"), arguments("{\"lord\": \"Arioch\"} || true"),
                // Lazy OR type errors - right operand
                arguments("false || \"Mournblade\""), arguments("false || 666"), arguments("false || null"),
                arguments("false || [\"Melniboné\"]"), arguments("false || {\"city\": \"Nadsokor\"}"),
                // Lazy AND type errors - left operand
                arguments("\"Xiombarg\" && true"), arguments("777 && false"), arguments("null && true"),
                arguments("[\"Pyaray\", \"Mabelode\"] && false"), arguments("{\"sword\": \"Stormbringer\"} && true"),
                // Lazy AND type errors - right operand
                arguments("true && \"Young Kingdoms\""), arguments("true && 888"), arguments("true && null"),
                arguments("true && [\"Dragon Lords\"]"), arguments("true && {\"emperor\": \"Elric\"}"));
    }

    // ==========================================================================
    // Key and Index Step Tests
    // ==========================================================================

    @ParameterizedTest(name = "key access: {0}")
    @MethodSource
    void whenKeyAccess_thenReturnsCorrectValue(String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenKeyAccess_thenReturnsCorrectValue() {
        return Stream.of(
                // Dot access
                arguments("{\"weapon\": \"Stormbringer\"}.weapon", Value.of("Stormbringer")),
                arguments("{\"dragons\": 13, \"city\": \"Imrryr\"}.dragons", Value.of(13)),
                arguments("{ name: \"Cthulhu\", age: 1000 }.name", Value.of("Cthulhu")),
                arguments("{ name: \"Cthulhu\", age: 1000 }.age", Value.of(1000)),
                // Escaped key access
                arguments("{\"with-dash\": \"value\"}[\"with-dash\"]", Value.of("value")),
                arguments("{\"key.with.dots\": 42}[\"key.with.dots\"]", Value.of(42)),
                arguments("{ \"deep-one\": \"Dagon\" }.\"deep-one\"", Value.of("Dagon")),
                // Bracket key access
                arguments("{ name: \"Nyarlathotep\" }[\"name\"]", Value.of("Nyarlathotep")),
                // Chained access
                arguments("{\"outer\": {\"inner\": \"value\"}}.outer",
                        ObjectValue.builder().put("inner", Value.of("value")).build()),
                arguments("{\"outer\": {\"inner\": \"deep\"}}.outer.inner", Value.of("deep")),
                arguments("{ outer: { inner: \"secret\" } }.outer.inner", Value.of("secret")),
                // Missing key returns undefined
                arguments("{ name: \"Cthulhu\" }.missing", Value.UNDEFINED),
                // Key on non-object returns undefined
                arguments("[1, 2, 3].name", Value.UNDEFINED), arguments("\"text\".field", Value.UNDEFINED),
                arguments("42.property", Value.UNDEFINED), arguments("\"Stormbringer\".weapon", Value.UNDEFINED),
                arguments("true.realm", Value.UNDEFINED), arguments("[1, 2, 3].key", Value.UNDEFINED));
    }

    @ParameterizedTest(name = "index access: {0}")
    @MethodSource
    void whenIndexAccess_thenReturnsCorrectValue(String expression, Value expected) {
        val result = compileExpression(expression);
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    static Stream<Arguments> whenIndexAccess_thenReturnsCorrectValue() {
        return Stream.of(
                // Basic index
                arguments("[10, 20, 30, 40][0]", Value.of(10)), arguments("[10, 20, 30, 40][2]", Value.of(30)),
                arguments("[10, 20, 30, 40][3]", Value.of(40)),
                arguments("[\"Elric\", \"Moonglum\", \"Cymoril\"][1]", Value.of("Moonglum")),
                arguments("[\"first\", \"second\", \"third\"][0]", Value.of("first")),
                arguments("[\"first\", \"second\", \"third\"][1]", Value.of("second")),
                arguments("[\"first\", \"second\", \"third\"][2]", Value.of("third")),
                // Negative index
                arguments("[\"first\", \"second\", \"third\"][-1]", Value.of("third")),
                arguments("[\"first\", \"second\", \"third\"][-2]", Value.of("second")),
                // Nested index
                arguments("[[1, 2], [3, 4]][0][1]", Value.of(2)),
                // Out of bounds returns error
                arguments("[1, 2, 3][10]", Value.error("placeholder")),
                arguments("[1, 2, 3][-10]", Value.error("placeholder")),
                // Index on non-array returns undefined
                arguments("{ name: \"test\" }[0]", Value.UNDEFINED), arguments("\"text\"[0]", Value.UNDEFINED),
                arguments("42[0]", Value.UNDEFINED), arguments("\"Stormbringer\"[0]", Value.UNDEFINED),
                arguments("true[1]", Value.UNDEFINED), arguments("999[0]", Value.UNDEFINED),
                arguments("{\"key\": \"value\"}[0]", Value.UNDEFINED));
    }

    // ==========================================================================
    // Expression Subscript Tests
    // ==========================================================================

    @ParameterizedTest(name = "expression subscript: {0}")
    @MethodSource
    void whenExpressionSubscript_thenEvaluatesCorrectly(String expression, Value expected) {
        val result = compileExpressionWithFunctions(expression);
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    static Stream<Arguments> whenExpressionSubscript_thenEvaluatesCorrectly() {
        return Stream.of(arguments("[\"Imrryr\", \"Tanelorn\", \"Vilmir\"][(1 + 1)]", Value.of("Vilmir")),
                arguments("[10, 20, 30][((6 / 2) - 1)]", Value.of(30)),
                arguments("[0,1,2,3,4,5,6,7,8,9][(2+3)]", Value.of(5)),
                arguments("{ \"key\" : true }[(\"ke\"+\"y\")]", Value.TRUE),
                arguments("[\"a\", \"b\", \"c\"][(1 + 1)]", Value.of("c")),
                arguments("{ \"deep-one\": \"Dagon\" }[(string.concat(\"deep\", \"-one\"))]", Value.of("Dagon")),
                // Non-existent key returns undefined
                arguments("{ \"key\" : true }[(\"no_ke\"+\"y\")]", Value.UNDEFINED),
                arguments("{ \"key\" : true }[(5+2)]", Value.UNDEFINED),
                arguments("undefined[(1 + 1)]", Value.UNDEFINED),
                // String key on array returns undefined
                arguments("[0,1,2,3,4,5,6,7,8,9][(\"key\")]", Value.UNDEFINED),
                // Errors
                arguments("[1,2,3][(true)]", Value.error("placeholder")),
                arguments("[1,2,3][(1+100)]", Value.error("placeholder")),
                arguments("[1,2,3][(1 - 100)]", Value.error("placeholder")),
                arguments("[][(10/0)]", Value.error("placeholder")),
                arguments("[(10/0)][(2+2)]", Value.error("placeholder")),
                arguments("{ \"key\" : true }[(10/0)]", Value.error("placeholder")));
    }

    // ==========================================================================
    // Array Slicing Tests
    // ==========================================================================

    @ParameterizedTest(name = "slice: {0}")
    @MethodSource
    void whenArraySlicing_thenReturnsSlice(String description, String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenArraySlicing_thenReturnsSlice() {
        return Stream.of(
                arguments("basic [1:3]", "[10, 20, 30, 40, 50][1:3]", Value.ofArray(Value.of(20), Value.of(30))),
                arguments("start [:2]", "[0, 1, 2, 3, 4][:2]", Value.ofArray(Value.of(0), Value.of(1))),
                arguments("end [3:]", "[10, 20, 30, 40, 50][2:]",
                        Value.ofArray(Value.of(30), Value.of(40), Value.of(50))),
                arguments("start [:3]", "[10, 20, 30, 40, 50][:3]",
                        Value.ofArray(Value.of(10), Value.of(20), Value.of(30))),
                arguments("full [:]", "[0, 1, 2][:]", Value.ofArray(Value.of(0), Value.of(1), Value.of(2))),
                arguments("negative start [-2:]", "[0, 1, 2, 3, 4][-2:]", Value.ofArray(Value.of(3), Value.of(4))),
                arguments("negative end [:-1]", "[0, 1, 2, 3, 4][:-1]",
                        Value.ofArray(Value.of(0), Value.of(1), Value.of(2), Value.of(3))),
                arguments("step [::2]", "[10, 20, 30, 40, 50][: :2]",
                        Value.ofArray(Value.of(10), Value.of(30), Value.of(50))),
                arguments("start+step [1::2]", "[10, 20, 30, 40, 50][1: :2]",
                        Value.ofArray(Value.of(20), Value.of(40))),
                arguments("full+step [0:5:2]", "[0, 1, 2, 3, 4][0:5:2]",
                        Value.ofArray(Value.of(0), Value.of(2), Value.of(4))),
                arguments("empty [5:3]", "[0, 1, 2, 3, 4][5:3]", Value.EMPTY_ARRAY),
                arguments("non-array", "42[1:3]", Value.UNDEFINED),
                arguments("non-array2", "999[1:3]", Value.UNDEFINED),
                arguments("object", "{\"a\": 1}[0:2]", Value.UNDEFINED));
    }

    // ==========================================================================
    // Wildcard and Union Step Tests
    // ==========================================================================

    @ParameterizedTest(name = "wildcard/union: {0}")
    @MethodSource
    void whenWildcardOrUnion_thenReturnsCorrectValue(String description, String expression, Value expected) {
        val result = compileExpression(expression);
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else if (result instanceof ArrayValue arr && expected instanceof ArrayValue expArr) {
            // For wildcards on objects, order may vary
            assertThat(arr).hasSameSizeAs(expArr).containsAll(expArr);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    static Stream<Arguments> whenWildcardOrUnion_thenReturnsCorrectValue() {
        return Stream.of(
                // Wildcard on array
                arguments("array wildcard", "[1, 2, 3].*", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                // Wildcard on object
                arguments("object wildcard", "{\"a\": 1, \"b\": 2}.*",
                        ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build()),
                // Bracket wildcard
                arguments("bracket wildcard", "{ x: 10, y: 20 }[*]",
                        ArrayValue.builder().add(Value.of(10)).add(Value.of(20)).build()),
                // Index union
                arguments("index union", "[10, 20, 30, 40, 50][0, 2, 4]",
                        Value.ofArray(Value.of(10), Value.of(30), Value.of(50))),
                arguments("index union 2", "[\"a\", \"b\", \"c\", \"d\"][1, 3]",
                        Value.ofArray(Value.of("b"), Value.of("d"))),
                arguments("negative index union", "[\"first\", \"second\", \"third\"][-1, -3]",
                        Value.ofArray(Value.of("first"), Value.of("third"))),
                // Attribute union
                arguments("attribute union",
                        "{\"name\": \"Melniboné\", \"capital\": \"Imrryr\", \"age\": 10000}[\"name\", \"capital\"]",
                        Value.ofArray(Value.of("Melniboné"), Value.of("Imrryr"))),
                arguments("attribute union 2", "{\"realm\": \"Chaos\", \"lord\": \"Arioch\"}[\"realm\", \"lord\"]",
                        Value.ofArray(Value.of("Chaos"), Value.of("Arioch"))),
                // Missing keys in union
                arguments("union missing keys", "{\"weapon\": \"Stormbringer\"}[\"weapon\", \"missing\", \"nothere\"]",
                        Value.ofArray(Value.of("Stormbringer"))),
                // Errors
                arguments("non-array index union", "\"text\"[0, 1]", Value.error("placeholder")),
                arguments("non-object attr union", "[1, 2, 3][\"a\", \"b\"]", Value.error("placeholder")));
    }

    @ParameterizedTest(name = "index union out of bounds: {0}")
    @MethodSource
    void whenIndexUnionOutOfBounds_thenError(String expression, String expectedSubstring) {
        val result = compileExpression(expression);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(expectedSubstring);
    }

    static Stream<Arguments> whenIndexUnionOutOfBounds_thenError() {
        return Stream.of(arguments("[10, 20, 30][0, 10, 1]", "out of bounds"),
                arguments("[\"Elric\", \"Moonglum\"][-1, 0, 5]", "out of bounds"));
    }

    // ==========================================================================
    // Recursive Descent Step Tests
    // ==========================================================================

    @ParameterizedTest(name = "recursive: {0}")
    @MethodSource
    void whenRecursiveStep_thenFindsAllMatches(String description, String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenRecursiveStep_thenFindsAllMatches() {
        return Stream.of(
                arguments("key in nested", "{\"outer\": {\"inner\": {\"deep\": 42}}}..deep",
                        Value.ofArray(Value.of(42))),
                arguments("key in array", "[{\"x\": 1}, {\"y\": {\"x\": 2}}, {\"x\": 3}]..x",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("key at multiple levels", "{ id: 1, child: { id: 2, grandchild: { id: 3 } } }..id",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("key from array", "[{\"name\": \"Cthulhu\"}, {\"name\": \"Nyarlathotep\"}]..name",
                        Value.ofArray(Value.of("Cthulhu"), Value.of("Nyarlathotep"))),
                arguments("not found", "{ a: 1, b: 2 }..missing", Value.EMPTY_ARRAY),
                // Recursive index
                arguments("recursive index", "[[\"a\", \"b\"], [\"c\", \"d\"], [[\"e\", \"f\"]]]..[0]",
                        Value.ofArray(Value.ofArray(Value.of("a"), Value.of("b")), Value.of("a"), Value.of("c"),
                                Value.ofArray(Value.of("e"), Value.of("f")), Value.of("e"))),
                arguments("recursive negative index", "[[1, 2], [3, 4]]..[- 1]",
                        Value.ofArray(Value.ofArray(Value.of(3), Value.of(4)), Value.of(2), Value.of(4))));
    }

    @Test
    void whenRecursiveWildcard_thenCollectsAllValues() {
        val result = compileExpression("{ a: { b: 1 }, c: 2 }..*");
        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(3);
    }

    // ==========================================================================
    // Condition Step Tests
    // ==========================================================================

    @ParameterizedTest(name = "condition: {0}")
    @MethodSource
    void whenConditionStep_thenFiltersCorrectly(String description, String expression, Value expected) {
        assertThat(compileExpression(expression)).isEqualTo(expected);
    }

    static Stream<Arguments> whenConditionStep_thenFiltersCorrectly() {
        return Stream.of(
                // Array conditions with @
                arguments("filter > threshold", "[1, 2, 3, 4, 5][?(@ > 3)]", Value.ofArray(Value.of(4), Value.of(5))),
                arguments("filter < threshold", "[10, 20, 30, 40][?(@ < 30)]",
                        Value.ofArray(Value.of(10), Value.of(20))),
                arguments("filter by value", "[1, 5, 10, 15, 20][?(@ > 10)]",
                        Value.ofArray(Value.of(15), Value.of(20))),
                arguments("filter equality", "[\"Cthulhu\", \"Dagon\", \"Nyarlathotep\"][?(@ == \"Dagon\")]",
                        Value.ofArray(Value.of("Dagon"))),
                arguments("filter objects",
                        "[{name: \"Alice\", active: true}, {name: \"Bob\", active: false}][?(@.active == true)]",
                        Value.ofArray(Value.ofObject(Map.of("name", Value.of("Alice"), "active", Value.TRUE)))),
                // Array conditions with #
                arguments("filter by index < 2", "[\"a\", \"b\", \"c\", \"d\"][?(# < 2)]",
                        Value.ofArray(Value.of("a"), Value.of("b"))),
                arguments("filter by specific indices", "[10, 20, 30, 40, 50][?(# == 0 || # == 2 || # == 4)]",
                        Value.ofArray(Value.of(10), Value.of(30), Value.of(50))),
                arguments("filter first only", "[\"first\", \"second\", \"third\"][?(# == 0)]",
                        Value.ofArray(Value.of("first"))),
                arguments("filter >= 2", "[\"alpha\", \"beta\", \"gamma\"][?(# >= 2)]",
                        Value.ofArray(Value.of("gamma"))),
                arguments("middle elements", "[1, 2, 3, 4, 5][?(# > 0 && # < 4)]",
                        Value.ofArray(Value.of(2), Value.of(3), Value.of(4))),
                arguments("modulo 3", "[0, 1, 2, 3, 4, 5, 6, 7, 8][?(# % 3 == 0)]",
                        Value.ofArray(Value.of(0), Value.of(3), Value.of(6))),
                // Combined @ and #
                arguments("value and index", "[100, 5, 200, 10][?(@ > 50 && # < 3)]",
                        Value.ofArray(Value.of(100), Value.of(200))),
                arguments("value equals index", "[0, 1, 5, 3, 10][?(@ == #)]",
                        Value.ofArray(Value.of(0), Value.of(1), Value.of(3))),
                arguments("object field and index", "[{id: 0}, {id: 1}, {id: 5}, {id: 3}][?(@.id == #)]",
                        Value.ofArray(Value.ofObject(Map.of("id", Value.of(0))),
                                Value.ofObject(Map.of("id", Value.of(1))), Value.ofObject(Map.of("id", Value.of(3))))),
                // Edge cases
                arguments("no match", "[1, 2, 3][?(@ > 100)]", Value.EMPTY_ARRAY),
                arguments("all match", "[1, 2, 3][?(@ > 0)]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                // Object conditions
                arguments("object filter > 3", "{ a: 1, b: 5, c: 10 }[?(@ > 3)]",
                        Value.ofObject(Map.of("b", Value.of(5), "c", Value.of(10)))),
                arguments("object filter boolean", "{ active: true, disabled: false, enabled: true }[?(@ == true)]",
                        Value.ofObject(Map.of("active", Value.TRUE, "enabled", Value.TRUE))),
                arguments("object filter string",
                        "{ name: \"Cthulhu\", title: \"Great Old One\", location: \"R'lyeh\" }[?(@ == \"Cthulhu\")]",
                        Value.ofObject(Map.of("name", Value.of("Cthulhu")))),
                arguments("object no match", "{ a: 1, b: 2 }[?(@ > 100)]", Value.EMPTY_OBJECT),
                arguments("object all match", "{ x: 10, y: 20, z: 30 }[?(@ >= 10)]",
                        Value.ofObject(Map.of("x", Value.of(10), "y", Value.of(20), "z", Value.of(30)))),
                // Scalar conditions
                arguments("scalar true", "42[?(@ > 10)]", Value.of(42)),
                arguments("scalar string true", "\"Cthulhu\"[?(@ == \"Cthulhu\")]", Value.of("Cthulhu")),
                arguments("scalar bool true", "true[?(@ == true)]", Value.TRUE),
                arguments("scalar false", "42[?(@ < 10)]", Value.UNDEFINED),
                arguments("scalar string false", "\"Dagon\"[?(@ == \"Cthulhu\")]", Value.UNDEFINED),
                arguments("scalar bool false", "false[?(@ == true)]", Value.UNDEFINED),
                arguments("scalar # == 0", "\"test\"[?(# == 0)]", Value.of("test")),
                arguments("scalar # > 0", "\"test\"[?(# > 0)]", Value.UNDEFINED),
                arguments("scalar combined true", "100[?(@ > 50 && # == 0)]", Value.of(100)),
                arguments("scalar combined false", "100[?(@ > 50 && # > 0)]", Value.UNDEFINED),
                // Chained conditions
                arguments("chained conditions", "[1, 2, 3, 4, 5, 6, 7, 8][?(@ > 2)][?(@ < 7)]",
                        Value.ofArray(Value.of(3), Value.of(4), Value.of(5), Value.of(6))),
                arguments("slice then condition", "[10, 20, 30, 40, 50, 60][1:5][?(@ < 40)]",
                        Value.ofArray(Value.of(20), Value.of(30))),
                arguments("condition then slice", "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10][?(@ > 3)][0:3]",
                        Value.ofArray(Value.of(4), Value.of(5), Value.of(6))),
                // Empty collections
                arguments("empty array", "[][?(@ > 0)]", Value.EMPTY_ARRAY),
                arguments("empty object", "{}[?(@ > 0)]", Value.EMPTY_OBJECT));
    }

    // ==========================================================================
    // Known Issue: Filter by membership in nested array returns undefined
    // Pattern: array[?(value in @.field)].projection
    // Used in Brewer-Nash (Chinese Wall) policies
    // ==========================================================================

    @ParameterizedTest(name = "KNOWN ISSUE - membership in nested array: {0}")
    @MethodSource
    void whenFilterByMembershipInNestedArray_thenCurrentlyReturnsUndefined(String description, String expression,
            Value expectedCorrectResult) {
        // BUG: These expressions should return expectedCorrectResult but currently
        // return undefined
        val result = compileExpression(expression);
        assertThat(result).isEqualTo(Value.UNDEFINED); // Current incorrect behavior
        // TODO: When fixed, change assertion to:
        // assertThat(result).isEqualTo(expectedCorrectResult);
    }

    static Stream<Arguments> whenFilterByMembershipInNestedArray_thenCurrentlyReturnsUndefined() {
        return Stream.of(
                arguments("filter by membership - single match",
                        "[{\"class\": \"energy\", \"entities\": [\"OilCorp\", \"PetroEnergy\", \"GasGiant\"]}, "
                                + "{\"class\": \"banking\", \"entities\": [\"GlobalBank\", \"CityFinancial\"]}]"
                                + "[?(\"PetroEnergy\" in @.entities)].class",
                        Value.ofArray(Value.of("energy"))),
                arguments("filter by membership - no match",
                        "[{\"class\": \"energy\", \"entities\": [\"OilCorp\", \"GasGiant\"]}, "
                                + "{\"class\": \"banking\", \"entities\": [\"GlobalBank\"]}]"
                                + "[?(\"Unknown\" in @.entities)].class",
                        Value.EMPTY_ARRAY),
                arguments("filter by membership - multiple matches",
                        "[{\"class\": \"energy\", \"entities\": [\"OilCorp\", \"Shared\"]}, "
                                + "{\"class\": \"banking\", \"entities\": [\"GlobalBank\", \"Shared\"]}]"
                                + "[?(\"Shared\" in @.entities)].class",
                        Value.ofArray(Value.of("energy"), Value.of("banking"))));
    }


    @Test
    void bntest() {
        val compiled = compileExpression("""
                [
                  {"class": "energy", "entities": ["OilCorp", "Shared"]},
                  {"class": "banking", "entities": ["GlobalBank", "Shared"]}
                ]
                """);
        System.out.println("-> "+ compiled.getClass().getSimpleName());
    }
    @ParameterizedTest(name = "condition error: {0}")
    @MethodSource
    void whenConditionError_thenReturnsErrorContaining(String expression, String expectedSubstring) {
        val result = compileExpression(expression);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).containsIgnoringCase(expectedSubstring);
    }

    static Stream<Arguments> whenConditionError_thenReturnsErrorContaining() {
        return Stream.of(arguments("42[?(@ + 1)]", "Condition"), arguments("\"text\"[?(123)]", "Condition"),
                arguments("[1, 2, 3][?(@ * 2)]", "Condition"),
                arguments("{\"a\": 1, \"b\": 2}[?(@ + 5)]", "Condition"));
    }

    @Test
    void whenRelativeLocationUsedDirectly_thenReturnsUndefined() {
        val compiled = compileExpression("#");
        assertThat(compiled).isInstanceOf(PureExpression.class);
        val result = ((PureExpression) compiled).evaluate(evaluationContext);
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void whenRelativeValueUsedDirectly_thenReturnsUndefined() {
        val compiled = compileExpression("@");
        assertThat(compiled).isInstanceOf(PureExpression.class);
        val result = ((PureExpression) compiled).evaluate(evaluationContext);
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    // ==========================================================================
    // Nested Relative Expression Tests
    // ==========================================================================

    @Test
    void whenNestedConditionSteps_thenOuterRelativeValueShadowsInner() {
        val compiled   = compileExpression("[[1, 2], [3, 4]][?(@[0] > 1)]");
        val innerArray = Value.ofArray(Value.of(3), Value.of(4));
        assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(List.of(innerArray)));
    }

    @Test
    void whenNestedConditionWithInnerArrayIteration_thenConstantFolds() {
        val compiled   = compileExpression("[[1, 2, 3], [4, 5, 6]][?(@[?(# == 0)][0] < 3)]");
        val innerArray = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));
        assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(List.of(innerArray)));
    }

    @Test
    void whenDeeplyNestedConditions_thenCorrectlyScopesRelativeValues() {
        val compiled  = compileExpression("[[[1, 2]], [[3, 4]]][?(@[0][?(# == 0)][0] == 1)]");
        val innermost = Value.ofArray(Value.of(1), Value.of(2));
        val middle    = Value.ofArray(List.of(innermost));
        assertThat(compiled).isInstanceOf(Value.class).isEqualTo(Value.ofArray(List.of(middle)));
    }

    @Test
    void whenNestedRelativeLocationInCondition_thenInnerShadowsOuter() {
        val compiled = compileExpression(
                "[{id: 0, values: [0]}, {id: 1, values: [1]}, {id: 2, values: [0]}][?(@.values[?(@ == #)] != [])]");
        val expected = Value.ofArray(Value.ofObject(Map.of("id", Value.of(0), "values", Value.ofArray(Value.of(0)))),
                Value.ofObject(Map.of("id", Value.of(2), "values", Value.ofArray(Value.of(0)))));
        assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected);
    }

    // ==========================================================================
    // Function Call Tests
    // ==========================================================================

    @ParameterizedTest(name = "function: {0}")
    @MethodSource
    void whenFunctionCall_thenReturnsExpectedValue(String expression, Value expected) {
        val result = compileExpressionWithFunctions(expression);
        if (expected instanceof ErrorValue) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    static Stream<Arguments> whenFunctionCall_thenReturnsExpectedValue() {
        return Stream.of(arguments("standard.length(\"Cthulhu\")", Value.of(7)),
                arguments("standard.length([1, 2, 3, 4, 5])", Value.of(5)),
                arguments("standard.length({ name: \"Dagon\", age: 1000 })", Value.of(2)),
                arguments("string.concat(\"Deep\", \"One\")", Value.of("DeepOne")),
                arguments("standard.length(string.concat(\"Shub\", \"Niggurath\"))", Value.of(13)),
                arguments("standard.length(\"test\") + 10", Value.of(14)),
                arguments("standard.length(\"hello\") > 3", Value.TRUE),
                arguments("standard.length([1 + 1, 2 + 2, 3 + 3])", Value.of(3)),
                // Errors
                arguments("unknown.function()", Value.error("placeholder")),
                arguments("standard.length(42)", Value.error("placeholder")));
    }

    @ParameterizedTest(name = "function in condition: {0}")
    @MethodSource
    void whenConditionContainsPureFunction_thenConstantFolds(String description, String expression, Value expected) {
        val compiled = compileExpressionWithFunctions(expression);
        assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected);
    }

    static Stream<Arguments> whenConditionContainsPureFunction_thenConstantFolds() {
        return Stream.of(
                arguments("abs function", "[-3, -1, 2, 5][?(math.abs(@) > 2)]",
                        Value.ofArray(Value.of(-3), Value.of(5))),
                arguments("length function", "[\"a\", \"ab\", \"abc\", \"abcd\"][?(standard.length(@) > 2)]",
                        Value.ofArray(Value.of("abc"), Value.of("abcd"))),
                arguments("function on field",
                        "[{name: \"Al\"}, {name: \"Bob\"}, {name: \"Alice\"}][?(standard.length(@.name) > 2)]",
                        Value.ofArray(Value.ofObject(Map.of("name", Value.of("Bob"))),
                                Value.ofObject(Map.of("name", Value.of("Alice"))))),
                arguments("combined with arithmetic", "[-5, -2, 1, 3][?(math.abs(@) * 2 > 5)]",
                        Value.ofArray(Value.of(-5), Value.of(3))));
    }

    @Test
    void whenConditionUsesSubscriptionScopedVariable_thenReturnsPureExpression() {
        val compiled = compileExpression("[1, 2, 3][?(@ > config.threshold)]");
        assertThat(compiled).isInstanceOf(PureExpression.class);
        val subscriptionContext = new EvaluationContext(null, null, null, null, null, null).with("config",
                Value.ofObject(Map.of("threshold", Value.of(1))));
        val result              = ((PureExpression) compiled).evaluate(subscriptionContext);
        assertThat(result).isEqualTo(Value.ofArray(Value.of(2), Value.of(3)));
    }

    @Test
    void whenConditionUsesSubscriptionScopedFunctionArgument_thenReturnsPureExpression() {
        val compiled = compileExpressionWithFunctions("[1, 2, 3][?(math.abs(@ - threshold) < 2)]");
        assertThat(compiled).isInstanceOf(PureExpression.class);
        val subscriptionContext = new EvaluationContext(null, null, null, null,
                contextWithFunctions.getFunctionBroker(), null).with("threshold", Value.of(2));
        val result              = ((PureExpression) compiled).evaluate(subscriptionContext);
        assertThat(result).isEqualTo(Value.ofArray(Value.of(1), Value.of(2), Value.of(3)));
    }

    // ==========================================================================
    // Temporal Function Constant Folding Tests (from Xtext version)
    // ==========================================================================

    @ParameterizedTest(name = "temporal: {0}")
    @MethodSource
    void whenTemporalFunction_thenConstantFolds(String expression, Value expected) {
        assertExpressionCompilesToValue(expression, expected);
    }

    static Stream<Arguments> whenTemporalFunction_thenConstantFolds() {
        return Stream.of(
                // Duration conversions
                arguments("time.durationOfSeconds(60)", Value.of(60000)),
                arguments("time.durationOfMinutes(5)", Value.of(300000)),
                arguments("time.durationOfHours(2)", Value.of(7200000)),
                arguments("time.durationOfDays(1)", Value.of(86400000)),
                // Epoch conversions
                arguments("time.ofEpochSecond(0)", Value.of("1970-01-01T00:00:00Z")),
                arguments("time.ofEpochMilli(0)", Value.of("1970-01-01T00:00:00Z")),
                // Date/time extraction
                arguments("time.hourOf(\"2021-11-08T13:17:23Z\")", Value.of(13)),
                arguments("time.minuteOf(\"2021-11-08T13:17:23Z\")", Value.of(17)),
                arguments("time.secondOf(\"2021-11-08T13:00:23Z\")", Value.of(23)),
                arguments("time.dayOfYear(\"2021-11-08T13:00:00Z\")", Value.of(312)),
                arguments("time.weekOfYear(\"2021-11-08T13:00:00Z\")", Value.of(45)),
                // Date arithmetic
                arguments("time.plusDays(\"2021-11-08T13:00:00Z\", 5)", Value.of("2021-11-13T13:00:00Z")),
                arguments("time.minusDays(\"2021-11-08T13:00:00Z\", 5)", Value.of("2021-11-03T13:00:00Z")),
                arguments("time.plusSeconds(\"2021-11-08T13:00:00Z\", 10)", Value.of("2021-11-08T13:00:10Z")),
                arguments("time.minusSeconds(\"2021-11-08T13:00:00Z\", 10)", Value.of("2021-11-08T12:59:50Z")),
                // Date comparisons
                arguments("time.before(\"2021-11-08T13:00:00Z\", \"2021-11-08T13:00:01Z\")", Value.TRUE),
                arguments("time.after(\"2021-11-08T13:00:01Z\", \"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.between(\"2021-11-08T13:00:00Z\", \"2021-11-07T13:00:00Z\", \"2021-11-09T13:00:00Z\")",
                        Value.TRUE),
                // Temporal bounds
                arguments("time.startOfDay(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-08T00:00:00Z")),
                arguments("time.endOfDay(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-08T23:59:59.999999999Z")),
                arguments("time.startOfMonth(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-01T00:00:00Z")),
                arguments("time.endOfMonth(\"2021-11-08T13:45:30Z\")", Value.of("2021-11-30T23:59:59.999999999Z")),
                arguments("time.startOfYear(\"2021-11-08T13:45:30Z\")", Value.of("2021-01-01T00:00:00Z")),
                // Validation
                arguments("time.validUTC(\"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.validUTC(\"20111-000:00Z\")", Value.FALSE),
                arguments("time.validRFC3339(\"2021-11-08T13:00:00Z\")", Value.TRUE),
                arguments("time.validRFC3339(\"2021-11-08T13:00:00\")", Value.FALSE),
                // Age calculations
                arguments("time.ageInYears(\"1990-05-15\", \"2021-11-08\")", Value.of(31)),
                arguments("time.ageInMonths(\"1990-05-15\", \"1990-08-20\")", Value.of(3)),
                // Nested temporal functions
                arguments("time.hourOf(time.plusDays(\"2021-11-08T13:00:00Z\", 1))", Value.of(13)),
                arguments("time.durationOfMinutes(time.hourOf(\"2021-11-08T02:00:00Z\"))", Value.of(120000)));
    }

    // ==========================================================================
    // Pure Expression Evaluation Tests (from Xtext version)
    // ==========================================================================

    @ParameterizedTest(name = "pure: {0}")
    @MethodSource
    void whenPureExpression_thenEvaluatesCorrectly(String expression, Value expected) {
        assertCompiledExpressionEvaluatesTo(expression, expected);
    }

    static Stream<Arguments> whenPureExpression_thenEvaluatesCorrectly() {
        return Stream.of(
                // Subscription elements
                arguments("subject", Value.of("Elric")), arguments("action", Value.of("slay")),
                arguments("resource", Value.of("Moonglum")), arguments("environment", Value.of("Tanelorn")),
                // Operations on subscription
                arguments("subject == \"Elric\"", Value.TRUE), arguments("action == \"slay\"", Value.TRUE),
                arguments("resource != \"Stormbringer\"", Value.TRUE), arguments("subject == \"Arioch\"", Value.FALSE),
                // Key access on subscription
                arguments("{\"subject\": subject}.subject", Value.of("Elric")),
                arguments("{\"action\": action, \"resource\": resource}.action", Value.of("slay")),
                // Arrays with subscription
                arguments("[subject, action][0]", Value.of("Elric")),
                arguments("[subject, action, resource][1]", Value.of("slay")),
                // Arithmetic with subscription
                arguments("{\"clearance\": 3}.clearance + 1", Value.of(4)),
                arguments("{\"level\": 10}.level * 2", Value.of(20)),
                // Boolean with subscription
                arguments("true && (subject == \"Elric\")", Value.TRUE),
                arguments("false || (action == \"slay\")", Value.TRUE),
                arguments("!(subject == \"Arioch\")", Value.TRUE),
                // Complex mixed
                arguments("(subject == \"Elric\") && (5 > 3)", Value.TRUE),
                arguments("[10, 20, 30][?((@ > 15) && (subject == \"Elric\"))]",
                        Value.ofArray(Value.of(20), Value.of(30))),
                arguments("{\"authorized\": (action == \"slay\")}.authorized", Value.TRUE),
                // Nested access
                arguments("{\"user\": subject, \"weapons\": [\"Stormbringer\", \"Mournblade\"]}.user",
                        Value.of("Elric")),
                arguments("{\"metadata\": {\"actor\": subject}}.metadata.actor", Value.of("Elric")),
                // Comparisons
                arguments("{\"title\": \"emperor\"}.title == \"emperor\"", Value.TRUE),
                arguments("[\"Elric\", \"Moonglum\"][0] == subject", Value.TRUE),
                // Operations on objects
                arguments("{\"subject\": subject, \"action\": action}.subject == \"Elric\"", Value.TRUE),
                arguments("{\"a\": subject, \"b\": resource}.b", Value.of("Moonglum")),
                // Objects with undefined
                arguments("{\"name\": \"Arioch\", \"title\": undefined, \"realm\": \"Chaos\"}.name",
                        Value.of("Arioch")),
                arguments("{\"name\": \"Arioch\", \"title\": undefined, \"realm\": \"Chaos\"}.realm",
                        Value.of("Chaos")),
                // Arrays with undefined
                arguments("[1, undefined, 3, undefined, 5]", Value.ofArray(Value.of(1), Value.of(3), Value.of(5))),
                arguments("[subject, undefined, action]", Value.ofArray(Value.of("Elric"), Value.of("slay"))),
                // Complex structures
                arguments("{\"lord\": {\"name\": subject, \"domain\": \"Chaos\"}}",
                        ObjectValue.builder()
                                .put("lord",
                                        ObjectValue.builder().put("name", Value.of("Elric"))
                                                .put("domain", Value.of("Chaos")).build())
                                .build()),
                // Lazy operators with subscription
                arguments("(subject == \"Elric\") || (action == \"summon\")", Value.TRUE),
                arguments("(subject == \"Moonglum\") || (action == \"slay\")", Value.TRUE),
                arguments("(subject == \"Yyrkoon\") || (action == \"summon\")", Value.FALSE),
                arguments("(subject == \"Elric\") && (action == \"slay\")", Value.TRUE),
                arguments("(subject == \"Elric\") && (action == \"summon\")", Value.FALSE),
                // Condition steps with subscription
                arguments("[1, 2, 3, 4, 5][?((@  > 2) && (subject == \"Elric\"))]",
                        Value.ofArray(Value.of(3), Value.of(4), Value.of(5))),
                arguments("[1, 2, 3, 4, 5][?((@  < 2) || (action == \"slay\"))]",
                        Value.ofArray(Value.of(1), Value.of(2), Value.of(3), Value.of(4), Value.of(5))),
                // Condition with subscription filter
                arguments("[\"Elric\", \"Moonglum\", \"Cymoril\"][?(@ == subject)]", Value.ofArray(Value.of("Elric"))),
                arguments("{\"warrior\": \"Elric\", \"companion\": \"Moonglum\"}[?(@ == subject)]",
                        ObjectValue.builder().put("warrior", Value.of("Elric")).build()),
                // Undefined comparisons
                arguments("undefined == undefined", Value.TRUE), arguments("undefined != undefined", Value.FALSE),
                arguments("undefined == null", Value.FALSE), arguments("undefined == 42", Value.FALSE),
                arguments("undefined in [undefined, 1, 2]", Value.FALSE),
                arguments("undefined in [1, 2, 3]", Value.FALSE),
                // Null in boolean contexts
                arguments("null == null && true", Value.TRUE), arguments("(null == null) || false", Value.TRUE),
                // Key/index on non-matching types
                arguments("[0,1,2,3,4,5,6,7,8,9][(\"key\")]", Value.UNDEFINED),
                // Functions with subscription
                arguments("time.hourOf({\"time\": \"2021-11-08T13:00:00Z\"}.time)", Value.of(13)),
                arguments("time.durationOfSeconds({\"duration\": 60}.duration)", Value.of(60000)),
                arguments("time.validUTC({\"timestamp\": \"2021-11-08T13:00:00Z\"}.timestamp)", Value.TRUE),
                arguments("time.hourOf(\"2021-11-08T13:00:00Z\") > 12", Value.TRUE),
                arguments("time.durationOfMinutes(5) == 300000", Value.TRUE),
                arguments("time.before(\"2021-11-08T13:00:00Z\", \"2021-11-08T14:00:00Z\") && (subject == \"Elric\")",
                        Value.TRUE),
                arguments("time.dateOf(\"2021-11-08T13:00:00Z\") == \"2021-11-08\"", Value.TRUE),
                arguments("[time.hourOf(\"2021-11-08T13:00:00Z\"), time.minuteOf(\"2021-11-08T13:17:23Z\")][0]",
                        Value.of(13)),
                arguments("[time.hourOf(\"2021-11-08T13:00:00Z\"), time.hourOf(\"2021-11-08T14:00:00Z\")]",
                        Value.ofArray(Value.of(13), Value.of(14))),
                arguments("{\"hour\": time.hourOf(\"2021-11-08T13:00:00Z\")}",
                        ObjectValue.builder().put("hour", Value.of(13)).build()),
                arguments("[\"2021-11-08T13:00:00Z\", \"2021-11-08T14:00:00Z\"][?(time.hourOf(@) > 12)]",
                        Value.ofArray(Value.of("2021-11-08T13:00:00Z"), Value.of("2021-11-08T14:00:00Z"))),
                arguments(
                        "time.timeBetween(\"2021-01-01T00:00:00Z\", \"2022-01-01T00:00:00Z\", {\"unit\": \"YEARS\"}.unit)",
                        Value.of(1)));
    }

    // ==========================================================================
    // Error Message Tests (from Xtext version)
    // ==========================================================================

    @ParameterizedTest(name = "error: {0}")
    @MethodSource
    void whenExpressionProducesError_thenMessageContainsSubstring(String expression, String expectedSubstring) {
        assertCompiledExpressionEvaluatesToErrorContaining(expression, expectedSubstring);
    }

    static Stream<Arguments> whenExpressionProducesError_thenMessageContainsSubstring() {
        return Stream.of(arguments("10 / 0", "divis"), arguments("100 / 0", "divis"),
                arguments("42 / (5 - 5)", "divis"), arguments("10 % 0", "divis"), arguments("17 % (3 - 3)", "divis"),
                arguments("(100 / 0) + 5", "divis"), arguments("{\"value\": (10 % 0)}.value", "divis"));
    }

    // ==========================================================================
    // Attribute Finder Streaming Tests (from Xtext version)
    // ==========================================================================

    @ParameterizedTest(name = "attribute finder: {0}")
    @MethodSource
    void whenAttributeFinderBasicUsage_thenEmitsValues(String expression, int expectedStreamCount) {
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(expectedStreamCount)).expectNextCount(expectedStreamCount).verifyComplete();
    }

    static Stream<Arguments> whenAttributeFinderBasicUsage_thenEmitsValues() {
        return Stream.of(arguments("\"Elric\".<test.echo[{\"fresh\":true}]>", 1),
                arguments("subject.<test.echo[{\"fresh\":true}]>", 1), arguments("(43).<test.echo>", 1),
                arguments("true.<test.echo>", 1));
    }

    @ParameterizedTest(name = "attribute finder in expression: {0}")
    @MethodSource
    void whenAttributeFinderInExpressions_thenStreams(String expression) {
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1).log()).expectNextCount(1).verifyComplete();
    }

    static Stream<Arguments> whenAttributeFinderInExpressions_thenStreams() {
        return Stream.of(arguments("\"Elric\".<test.echo[{\"fresh\":true}]> == \"Elric\""),
                arguments("subject.<test.echo[{\"fresh\": true}]>"), arguments("(42).<test.echo> + 10"),
                arguments("(100).<test.echo> - 50"), arguments("\"Stormbringer\".<test.echo> == \"Stormbringer\""),
                arguments("(999).<test.echo> > 500"), arguments("\"Elric\" in [\"Elric\", \"Moonglum\"].<test.echo>"));
    }

    @ParameterizedTest(name = "attribute finder with steps: {0}")
    @MethodSource
    void whenAttributeFinderWithStepsAndFunctionCalls_thenNoErrors(String expression) {
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();
    }

    static Stream<Arguments> whenAttributeFinderWithStepsAndFunctionCalls_thenNoErrors() {
        return Stream.of(arguments("{\"weapon\": \"Stormbringer\"}.<test.echo>.weapon"),
                arguments("[\"Arioch\", \"Xiombarg\"].<test.echo>[0]"),
                arguments("[1, 2, 3, 4, 5].<test.echo>[?(@ > 2)]"), arguments("[10, 20, 30, 40, 50].<test.echo>[0:2]"),
                arguments("{\"a\": 1, \"b\": 2}.<test.echo>.*"),
                arguments("{\"level1\": {\"value\": 42}}.<test.echo>..value"),
                arguments("time.hourOf(\"2021-11-08T13:00:00Z\".<test.echo>)"),
                arguments("time.durationOfSeconds((60).<test.echo>)"),
                arguments("time.validUTC(\"2021-11-08T13:00:00Z\".<test.echo>)"));
    }

    @ParameterizedTest(name = "attribute finder in structures: {0}")
    @MethodSource
    void whenAttributeFinderInDataStructures_thenCreatesStructures(String expression) {
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.take(1))
                .expectNextMatches(v -> v instanceof ArrayValue || v instanceof ObjectValue).verifyComplete();
    }

    static Stream<Arguments> whenAttributeFinderInDataStructures_thenCreatesStructures() {
        return Stream.of(arguments("[\"Elric\".<test.echo>, \"static\"]"), arguments("[subject.<test.echo>, action]"),
                arguments("{\"dynamic\": \"Moonglum\".<test.echo>, \"static\": \"value\"}"),
                arguments("{\"s\": subject.<test.echo>, \"a\": action}"));
    }

    @ParameterizedTest(name = "chained attribute finder: {0}")
    @MethodSource
    void whenChainedAttributeFinderOperations_thenEmitsValues(String expression) {
        val evaluated = TestUtil.evaluateExpression(expression);
        StepVerifier.create(evaluated.filter(v -> !(v instanceof ErrorValue)).take(1)).expectNextCount(1)
                .verifyComplete();
    }

    static Stream<Arguments> whenChainedAttributeFinderOperations_thenEmitsValues() {
        return Stream.of(arguments("\"Elric\".<test.echo>.<test.echo>"), arguments("subject.<test.echo>.<test.echo>"),
                arguments("{\"key\": \"value\"}.<test.echo>.key.<test.echo>"));
    }

    // ==========================================================================
    // Mixed Step Operations Tests
    // ==========================================================================

    @Test
    void whenMixedStepChain_thenEvaluatesCorrectly() {
        val expr = "{ items: [{ name: \"sword\" }, { name: \"shield\" }] }.items[0].name";
        assertThat(compileExpression(expr)).isEqualTo(Value.of("sword"));
    }

    @Test
    void whenStepsOnFunctionResult_thenAppliesSteps() {
        val result = compileExpressionWithFunctions("standard.toString([1, 2, 3]).length");
        assertThat(result).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void whenGroupedExpressionWithSteps_thenAppliesSteps() {
        assertThat(compileExpression("({ a: 1, b: 2 }).a")).isEqualTo(Value.of(1));
    }

    @Test
    void whenComplexNestedStructure_thenCompilesProperly() {
        val result = compileExpression("""
                {
                    "creature": "Shoggoth",
                    "attributes": {
                        "size": "massive",
                        "eyes": 1000
                    },
                    "locations": ["R'lyeh", "Mountains of Madness"]
                }
                """);
        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj.get("creature")).isEqualTo(Value.of("Shoggoth"));
        assertThat(obj.get("attributes")).isInstanceOf(ObjectValue.class);
        assertThat(obj.get("locations")).isInstanceOf(ArrayValue.class);
    }

    @Test
    void whenNullExpression_thenReturnsNull() {
        assertThat(ExpressionCompiler.compileExpression(null, CONTEXT)).isNull();
    }

    @Test
    void whenFunctionCallWithoutBroker_thenReturnsError() {
        val result = compileExpression("someFunction()");
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private CompiledExpression compileExpression(String expression) {
        return compileExpressionWithContext(expression, CONTEXT);
    }

    private CompiledExpression compileExpressionWithFunctions(String expression) {
        return compileExpressionWithContext(expression, contextWithFunctions);
    }

    private CompiledExpression compileExpressionWithContext(String expression, CompilationContext context) {
        val charStream       = CharStreams.fromString("policy \"test\" permit " + expression);
        val lexer            = new SAPLLexer(charStream);
        val tokenStream      = new CommonTokenStream(lexer);
        val parser           = new SAPLParser(tokenStream);
        val sapl             = parser.sapl();
        val policyElement    = (PolicyOnlyElementContext) sapl.policyElement();
        val targetExpression = policyElement.policy().targetExpression;
        return ExpressionCompiler.compileExpression(targetExpression, context);
    }
}
