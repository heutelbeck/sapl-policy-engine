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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.operators.ArithmeticOperators;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ArithmeticOperatorsTests {

    private static final BiFunction<Value, Value, Value> ADD    = (a, b) -> ArithmeticOperators.add(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> SUB    = (a, b) -> ArithmeticOperators.subtract(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> MUL    = (a, b) -> ArithmeticOperators.multiply(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> DIV    = (a, b) -> ArithmeticOperators.divide(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> MOD    = (a, b) -> ArithmeticOperators.modulo(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> LT     = (a, b) -> ArithmeticOperators.lessThan(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> LTE    = (a, b) -> ArithmeticOperators.lessThanOrEqual(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> GT     = (a, b) -> ArithmeticOperators.greaterThan(a, b,
            TEST_LOCATION);
    private static final BiFunction<Value, Value, Value> GTE    = (a, b) -> ArithmeticOperators.greaterThanOrEqual(a, b,
            TEST_LOCATION);
    private static final UnaryOperator<Value>            UPLUS  = a -> ArithmeticOperators.unaryPlus(a, TEST_LOCATION);
    private static final UnaryOperator<Value>            UMINUS = a -> ArithmeticOperators.unaryMinus(a, TEST_LOCATION);

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_binaryOp_then_returnsExpected(String description, BiFunction<Value, Value, Value> op, Value a, Value b,
            Value expected) {
        assertThat(op.apply(a, b)).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_binaryOp_then_returnsExpected() {
        return Stream.of(
            arguments("add integers", ADD, Value.of(3), Value.of(2), Value.of(5)),
            arguments("add decimals", ADD, Value.of(1.5), Value.of(2.5), Value.of(4.0)),
            arguments("concatenate strings", ADD, Value.of("hello"), Value.of("world"), Value.of("helloworld")),
            arguments("string + number coerces", ADD, Value.of("val:"), Value.of(5), Value.of("val:5")),
            arguments("string + boolean coerces", ADD, Value.of("flag:"), Value.TRUE, Value.of("flag:true")),
            arguments("subtract", SUB, Value.of(5), Value.of(3), Value.of(2)),
            arguments("multiply", MUL, Value.of(3), Value.of(4), Value.of(12)),
            arguments("multiply by zero", MUL, Value.of(5), Value.of(0), Value.of(0)),
            arguments("divide", DIV, Value.of(10), Value.of(2), Value.of(5)),
            arguments("modulo", MOD, Value.of(7), Value.of(3), Value.of(1)),
            arguments("modulo euclidean semantics", MOD, Value.of(-7), Value.of(3), Value.of(2)),
            arguments("less than true", LT, Value.of(3), Value.of(5), Value.TRUE),
            arguments("less than false", LT, Value.of(5), Value.of(3), Value.FALSE),
            arguments("less than or equal", LTE, Value.of(5), Value.of(5), Value.TRUE),
            arguments("greater than", GT, Value.of(5), Value.of(3), Value.TRUE),
            arguments("greater than or equal", GTE, Value.of(5), Value.of(5), Value.TRUE));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_binaryOp_withInvalidInput_then_returnsError(String description, BiFunction<Value, Value, Value> op,
            Value a, Value b, String errorContains) {
        assertThat(op.apply(a, b)).isInstanceOfSatisfying(ErrorValue.class,
                e -> assertThat(e.message()).contains(errorContains));
    }

    // @formatter:off
    private static Stream<Arguments> when_binaryOp_withInvalidInput_then_returnsError() {
        return Stream.of(
            arguments("add number + text", ADD, Value.of(5), Value.of("text"), "Numeric op"),
            arguments("subtract type errors", SUB, Value.of(5), Value.of("text"), "Numeric op"),
            arguments("multiply type errors", MUL, Value.of(5), Value.of("text"), "Numeric op"),
            arguments("divide type errors", DIV, Value.of(5), Value.of("text"), "Numeric op"),
            arguments("divide by zero", DIV, Value.of(5), Value.of(0), "Division by zero"),
            arguments("modulo type errors", MOD, Value.of(5), Value.of("text"), "Numeric op"),
            arguments("modulo by zero", MOD, Value.of(5), Value.of(0), "Division by zero"),
            arguments("add with null", ADD, Value.of(5), Value.NULL, "Numeric op"),
            arguments("add with undefined", ADD, Value.of(5), Value.UNDEFINED, "Numeric op"),
            arguments("add with array", ADD, Value.of(5), Value.EMPTY_ARRAY, "Numeric op"),
            arguments("compare with null", LT, Value.of(5), Value.NULL, "Numeric op"));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_unaryOp_then_returnsExpected(String description, UnaryOperator<Value> op, Value input, Value expected) {
        assertThat(op.apply(input)).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_unaryOp_then_returnsExpected() {
        return Stream.of(
            arguments("unary plus preserves positive", UPLUS, Value.of(5), Value.of(5)),
            arguments("unary plus preserves negative", UPLUS, Value.of(-5), Value.of(-5)),
            arguments("unary minus negates positive", UMINUS, Value.of(5), Value.of(-5)),
            arguments("unary minus negates negative", UMINUS, Value.of(-5), Value.of(5)),
            arguments("unary minus zero unchanged", UMINUS, Value.of(0), Value.of(0)));
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_unaryOp_withInvalidInput_then_returnsError(String description, UnaryOperator<Value> op, Value input) {
        assertThat(op.apply(input)).isInstanceOf(ErrorValue.class);
    }

    // @formatter:off
    private static Stream<Arguments> when_unaryOp_withInvalidInput_then_returnsError() {
        return Stream.of(
            arguments("unary plus with text", UPLUS, Value.of("text")),
            arguments("unary plus with boolean", UPLUS, Value.TRUE),
            arguments("unary minus with text", UMINUS, Value.of("text")),
            arguments("unary minus with null", UMINUS, Value.NULL));
    }
    // @formatter:on

    @Test
    void when_divide_nonTerminating_then_usesDecimal128Precision() {
        val result = DIV.apply(Value.of(1), Value.of(3));
        assertThat(result).isInstanceOfSatisfying(NumberValue.class,
                n -> assertThat(n.value().precision()).isEqualTo(34));
    }
}
