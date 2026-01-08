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

import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static io.sapl.util.ExpressionTestUtil.evaluateExpression;
import static io.sapl.util.TestBrokers.*;
import static org.assertj.core.api.Assertions.assertThat;

class LazyBooleanOperationCompilerTests {

    @Nested
    class AndConstantFolding {

        @ParameterizedTest(name = "{0} && {1} = {2}")
        @CsvSource({ "true,  true,  true", "true,  false, false", "false, true,  false", "false, false, false" })
        void truthTable(boolean left, boolean right, boolean expected) {
            var result = evaluateExpression(left + " && " + right);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void when_leftShortCircuits_then_constantFolded() {
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
        void when_leftShortCircuits_then_constantFolded() {
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
        void when_nonBoolean_then_error(String expr, String expectedError) {
            var result = evaluateExpression(expr);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains(expectedError);
        }
    }

    @Nested
    class PureOperators {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "(1 == 1) && true,   true", "false || (2 > 1),   true", "(1 == 1) && (2 == 2), true",
                "(1 != 1) && (2 == 2), false", "(1 != 1) || (2 == 2), true", "(1 == 1) || (2 != 2), true" })
        void when_pureOperands_then_evaluatesCorrectly(String expr, boolean expected) {
            var result = evaluateExpression(expr);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }
    }

    @Nested
    class StreamShortCircuit {

        @Test
        void when_andWithStreamOnRight_andLeftFalse_then_shortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed, Value.TRUE);
            var ctx        = compilationContext(broker);

            var compiled = compileExpression("false && <test.attr>", ctx);

            // false && stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.FALSE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void when_orWithStreamOnRight_andLeftTrue_then_shortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed, Value.TRUE);
            var ctx        = compilationContext(broker);

            var compiled = compileExpression("true || <test.attr>", ctx);

            // true || stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.TRUE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void when_andWithStreamOnRight_andLeftTrue_then_returnsStream() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var ctx      = compilationContext(broker);
            var compiled = compileExpression("true && <test.attr>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evaluationContext(broker);
            var stream  = ((StreamOperator) compiled).stream();
            StepVerifier.create(stream.contextWrite(c -> c.put(EvaluationContext.class, evalCtx)))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void when_orWithStreamOnRight_andLeftFalse_then_returnsStream() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var ctx      = compilationContext(broker);
            var compiled = compileExpression("false || <test.attr>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evaluationContext(broker);
            var stream  = ((StreamOperator) compiled).stream();
            StepVerifier.create(stream.contextWrite(c -> c.put(EvaluationContext.class, evalCtx)))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void when_streamAndStream_leftEmitsFalse_then_shortCircuitsRight() {
            var broker = sequenceBroker(Map.of("test.left", List.of(Value.FALSE, Value.TRUE, Value.FALSE), "test.right",
                    List.of(Value.TRUE)));
            var ctx    = compilationContext(broker);

            var compiled = compileExpression("<test.left> && <test.right>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evaluationContext(broker);
            var stream  = ((StreamOperator) compiled).stream();

            // left emits: false, true, false
            // When left=false, short-circuits (doesn't need right)
            // When left=true, evaluates right (which returns true)
            StepVerifier.create(stream.contextWrite(c -> c.put(EvaluationContext.class, evalCtx)))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)) // false short-circuits
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))  // true && true
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)) // false short-circuits
                    .verifyComplete();
        }
    }

}
