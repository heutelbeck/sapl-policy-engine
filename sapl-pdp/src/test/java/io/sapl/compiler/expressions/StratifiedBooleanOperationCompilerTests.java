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
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluate;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.sequenceBroker;
import static io.sapl.util.SaplTesting.trackingBroker;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;

@DisplayName("StratifiedBooleanOperationCompiler")
class StratifiedBooleanOperationCompilerTests {

    @Nested
    class AndConstantFolding {

        @ParameterizedTest(name = "{0} && {1} = {2}")
        @CsvSource({ "true,  true,  true", "true,  false, false", "false, true,  false", "false, false, false" })
        void truthTable(boolean left, boolean right, boolean expected) {
            var result = evaluateExpression(left + " && " + right);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void whenLeftShortCircuitsThenConstantFolded() {
            // false && anything = false (short-circuit at compile time)
            var compiled = compileExpression("false && true");
            assertThat(compiled).isEqualTo(Value.FALSE);
        }
    }

    @Nested
    class OrConstantFolding {

        @ParameterizedTest(name = "{0} || {1} = {2}")
        @CsvSource({ "true,  true,  true", "true,  false, true", "false, true,  true", "false, false, false" })
        void truthTable(boolean left, boolean right, boolean expected) {
            var result = evaluateExpression(left + " || " + right);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void whenLeftShortCircuitsThenConstantFolded() {
            // true || anything = true (short-circuit at compile time)
            var compiled = compileExpression("true || false");
            assertThat(compiled).isEqualTo(Value.TRUE);
        }
    }

    @Nested
    class TypeErrors {

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "1 && true,        Expected BOOLEAN", "true && 1,        Expected BOOLEAN",
                "\"text\" || false, Expected BOOLEAN", "false || \"text\", Expected BOOLEAN" })
        void whenNonBooleanThenError(String expr, String expectedError) {
            var result = evaluateExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains(expectedError);
        }

        @Test
        @DisplayName("unknown function error propagates through && instead of type mismatch")
        void whenUnknownFunctionInAndThenErrorMentionsFunctionName() {
            var result = evaluateExpression("nonexisting.fn(subject) && subject.x");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("nonexisting.fn");
        }

        @Test
        @DisplayName("unknown function error propagates through || instead of type mismatch")
        void whenUnknownFunctionInOrThenErrorMentionsFunctionName() {
            var result = evaluateExpression("nonexisting.fn(subject) || subject.x");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("nonexisting.fn");
        }
    }

    @Nested
    class PureOperators {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "(1 == 1) && true,   true", "false || (2 > 1),   true", "(1 == 1) && (2 == 2), true",
                "(1 != 1) && (2 == 2), false", "(1 != 1) || (2 == 2), true", "(1 == 1) || (2 != 2), true" })
        void whenPureOperandsThenEvaluatesCorrectly(String expr, boolean expected) {
            var result = evaluateExpression(expr);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }
    }

    @Nested
    class StreamShortCircuit {

        @Test
        void whenAndWithStreamOnRightAndLeftFalseThenShortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed, Value.TRUE);
            var ctx        = compilationContext(broker);

            var compiled = compileExpression("false && <test.attr>", ctx);

            // false && stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.FALSE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void whenOrWithStreamOnRightAndLeftTrueThenShortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed, Value.TRUE);
            var ctx        = compilationContext(broker);

            var compiled = compileExpression("true || <test.attr>", ctx);

            // true || stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.TRUE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void whenAndWithStreamOnRightAndLeftTrueThenReturnsStream() {
            var value = evaluate("true && <test.attr>").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        void whenOrWithStreamOnRightAndLeftFalseThenReturnsStream() {
            var value = evaluate("false || <test.attr>").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        void whenStreamAndStreamLeftEmitsFalseThenShortCircuitsRight() {
            // Drives <test.left> && <test.right> through left transitions
            // FALSE -> TRUE -> FALSE, asserting that the right side is only
            // present in the dependency map when left is TRUE (lazy short-circuit
            // and lazy re-subscribe).
            var driver = evaluate("<test.left> && <test.right>");

            // Round 1: discover left.
            driver.step();

            // Round 2: left=FALSE. Lazy short-circuits; right never inspected.
            driver.with("test.left", Value.FALSE);
            var r2 = driver.step();
            assertThat(r2.result()).isEqualTo(Value.FALSE);
            assertThat(r2.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("test.left");

            // Round 3: left=TRUE. Right discovered; not yet bound.
            driver.with("test.left", Value.TRUE);
            var r3 = driver.step();
            assertThat(r3.result()).isNull();
            assertThat(r3.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("test.left", "test.right");

            // Round 4: bind right=TRUE. Both resolve.
            driver.with("test.right", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);

            // Round 5: left flips back to FALSE. Right unsubscribed.
            driver.with("test.left", Value.FALSE);
            var r5 = driver.step();
            assertThat(r5.result()).isEqualTo(Value.FALSE);
            assertThat(r5.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("test.left");
        }
    }

    @Nested
    @DisplayName("Eager stream operators")
    class EagerStreamOperators {

        @Test
        @DisplayName("& with two streams evaluates both sides")
        void whenEagerAndWithTwoStreamsThenBothSubscribed() {
            var value = evaluate("<test.left> & <test.right>").with("test.left", Value.TRUE)
                    .with("test.right", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("| with two streams evaluates both sides")
        void whenEagerOrWithTwoStreamsThenBothSubscribed() {
            var value = evaluate("<test.left> | <test.right>").with("test.left", Value.FALSE)
                    .with("test.right", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("& does not short-circuit when left is false")
        void whenEagerAndLeftFalseThenRightStillEvaluated() {
            var value = evaluate("<test.left> & <test.right>").with("test.left", Value.FALSE)
                    .with("test.right", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("| does not short-circuit when left is true")
        void whenEagerOrLeftTrueThenRightStillEvaluated() {
            var value = evaluate("<test.left> | <test.right>").with("test.left", Value.TRUE)
                    .with("test.right", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }
    }

}
