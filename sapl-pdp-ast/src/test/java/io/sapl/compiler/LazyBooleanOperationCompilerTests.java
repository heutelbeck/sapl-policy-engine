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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static io.sapl.util.ExpressionTestUtil.evaluateExpression;
import static org.assertj.core.api.Assertions.assertThat;

class LazyBooleanOperationCompilerTests {

    @Nested
    class AndConstantFolding {

        @Test
        void when_trueAndTrue_then_true() {
            var result = evaluateExpression("true && true");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_trueAndFalse_then_false() {
            var result = evaluateExpression("true && false");
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void when_falseAndTrue_then_false() {
            var result = evaluateExpression("false && true");
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void when_falseAndFalse_then_false() {
            var result = evaluateExpression("false && false");
            assertThat(result).isEqualTo(Value.FALSE);
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

        @Test
        void when_trueOrTrue_then_true() {
            var result = evaluateExpression("true || true");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_trueOrFalse_then_true() {
            var result = evaluateExpression("true || false");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_falseOrTrue_then_true() {
            var result = evaluateExpression("false || true");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_falseOrFalse_then_false() {
            var result = evaluateExpression("false || false");
            assertThat(result).isEqualTo(Value.FALSE);
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

        @Test
        void when_leftIsNotBoolean_then_error() {
            var result = evaluateExpression("1 && true");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("Expected BOOLEAN");
        }

        @Test
        void when_rightIsNotBoolean_then_error() {
            var result = evaluateExpression("true && 1");
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("Expected BOOLEAN");
        }

        @Test
        void when_leftIsNotBooleanOr_then_error() {
            var result = evaluateExpression("\"text\" || false");
            assertThat(result).isInstanceOf(ErrorValue.class);
        }

        @Test
        void when_rightIsNotBooleanOr_then_error() {
            var result = evaluateExpression("false || \"text\"");
            assertThat(result).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class PureOperators {

        @Test
        void when_andWithPureOperands_thatConstantFold_then_returnsValue() {
            // When all operands can be evaluated at compile time, result is constant-folded
            var compiled = compileExpression("(1 == 1) && true");
            assertThat(compiled).isEqualTo(Value.TRUE);
        }

        @Test
        void when_orWithPureOperands_thatConstantFold_then_returnsValue() {
            // When all operands can be evaluated at compile time, result is constant-folded
            var compiled = compileExpression("false || (2 > 1)");
            assertThat(compiled).isEqualTo(Value.TRUE);
        }

        @Test
        void when_purePureAnd_then_evaluatesCorrectly() {
            var result = evaluateExpression("(1 == 1) && (2 == 2)");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_purePureAndShortCircuit_then_shortCircuits() {
            var result = evaluateExpression("(1 != 1) && (2 == 2)");
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void when_purePureOr_then_evaluatesCorrectly() {
            var result = evaluateExpression("(1 != 1) || (2 == 2)");
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        void when_purePureOrShortCircuit_then_shortCircuits() {
            var result = evaluateExpression("(1 == 1) || (2 != 2)");
            assertThat(result).isEqualTo(Value.TRUE);
        }
    }

    @Nested
    class StreamShortCircuit {

        @Test
        void when_andWithStreamOnRight_andLeftFalse_then_shortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed);
            var ctx        = new CompilationContext(null, broker);

            var compiled = compileExpression("false && <test.attr>", ctx);

            // false && stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.FALSE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void when_orWithStreamOnRight_andLeftTrue_then_shortCircuitsAtCompileTime() {
            var subscribed = new AtomicBoolean(false);
            var broker     = trackingBroker(subscribed);
            var ctx        = new CompilationContext(null, broker);

            var compiled = compileExpression("true || <test.attr>", ctx);

            // true || stream should short-circuit at compile time
            assertThat(compiled).isEqualTo(Value.TRUE);
            assertThat(subscribed.get()).isFalse();
        }

        @Test
        void when_andWithStreamOnRight_andLeftTrue_then_returnsStream() {
            var broker   = simpleBroker(Value.TRUE);
            var ctx      = new CompilationContext(null, broker);
            var compiled = compileExpression("true && <test.attr>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evalContext(broker);
            var stream  = ((StreamOperator) compiled).stream();
            StepVerifier.create(stream.contextWrite(c -> c.put(EvaluationContext.class, evalCtx)))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void when_orWithStreamOnRight_andLeftFalse_then_returnsStream() {
            var broker   = simpleBroker(Value.TRUE);
            var ctx      = new CompilationContext(null, broker);
            var compiled = compileExpression("false || <test.attr>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evalContext(broker);
            var stream  = ((StreamOperator) compiled).stream();
            StepVerifier.create(stream.contextWrite(c -> c.put(EvaluationContext.class, evalCtx)))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void when_streamAndStream_leftEmitsFalse_then_shortCircuitsRight() {
            var broker = emittingBroker();
            var ctx    = new CompilationContext(null, broker);

            var compiled = compileExpression("<test.left> && <test.right>", ctx);

            assertThat(compiled).isInstanceOf(StreamOperator.class);

            var evalCtx = evalContext(broker);
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

    private static AttributeBroker trackingBroker(AtomicBoolean subscribed) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                subscribed.set(true);
                return Flux.just(Value.TRUE);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker simpleBroker(Value value) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                return Flux.just(value);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker emittingBroker() {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().contains("left")) {
                    return Flux.just(Value.FALSE, Value.TRUE, Value.FALSE);
                }
                return Flux.just(Value.TRUE);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

    private static EvaluationContext evalContext(AttributeBroker broker) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of(), DEFAULT_FUNCTION_BROKER, broker,
                () -> "now");
    }

}
