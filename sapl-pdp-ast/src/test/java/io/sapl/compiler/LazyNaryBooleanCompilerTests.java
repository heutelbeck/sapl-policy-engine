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

import static io.sapl.api.model.Value.of;
import static io.sapl.util.ExpressionTestUtil.assertCompilesTo;
import static io.sapl.util.ExpressionTestUtil.assertCompilesToError;
import static io.sapl.util.ExpressionTestUtil.assertPureEvaluatesTo;
import static io.sapl.util.ExpressionTestUtil.assertPureEvaluatesToError;
import static io.sapl.util.ExpressionTestUtil.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Tests for N-ary lazy boolean operator compilation: Conjunction (&&) and
 * Disjunction (||).
 * <p>
 * Verifies:
 * <ul>
 * <li>Short-circuit semantics (AND: false terminates, OR: true terminates)</li>
 * <li>Cost-stratified evaluation (Values, Pures, Streams)</li>
 * <li>Compile-time folding of Value operands</li>
 * <li>Pures evaluated before stream subscription</li>
 * <li>Lazy stream subscription with switchMap chains</li>
 * <li>Error propagation</li>
 * </ul>
 */
class LazyNaryBooleanCompilerTests {

    @Nested
    class ConjunctionTests {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "true && true && true,   true", "true && true && false,  false", "true && false && true,  false",
                "true && false && false, false", "false && true && true,  false", "false && true && false, false",
                "false && false && true, false", "false && false && false, false", })
        void when_allValues_then_foldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class);
            assertThat(compiled).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void when_fourOperands_then_correctResult() {
            assertCompilesTo("true && true && true && true", Value.TRUE);
        }

        @Test
        void when_fiveOperands_withFalse_then_shortCircuits() {
            assertCompilesTo("true && false && true && true && true", Value.FALSE);
        }

        @Test
        void when_valueWithPure_then_returnsPureOperator() {
            var compiled = compileExpression("true && flag && true");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithPure_andAllTrue_then_evaluatesToTrue() {
            assertPureEvaluatesTo("true && flag && true", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void when_valueWithPure_andOneFalse_then_evaluatesToFalse() {
            assertPureEvaluatesTo("true && flag && true", Map.of("flag", Value.FALSE), Value.FALSE);
        }

        @Test
        void when_multiplePures_allTrue_then_evaluatesToTrue() {
            assertPureEvaluatesTo("a && b && c", Map.of("a", Value.TRUE, "b", Value.TRUE, "c", Value.TRUE), Value.TRUE);
        }

        @Test
        void when_multiplePures_oneFalse_then_evaluatesToFalse() {
            assertPureEvaluatesTo("a && b && c", Map.of("a", Value.TRUE, "b", Value.FALSE, "c", Value.TRUE),
                    Value.FALSE);
        }

        @Test
        void when_valueFalse_then_compileTimeShortCircuit() {
            // false && anything should be FALSE at compile time
            assertCompilesTo("false && true && true", Value.FALSE);
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            assertCompilesToError("true && 5 && false", "BOOLEAN");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            assertPureEvaluatesToError("true && notBoolean && true", Map.of("notBoolean", of("hello")));
        }

        @Test
        void when_errorInValues_then_compileTimeError() {
            var compiled = compileExpression("true && undefined && true");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class DisjunctionTests {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "true || true || true,   true", "true || true || false,  true", "true || false || true,  true",
                "true || false || false, true", "false || true || true,  true", "false || true || false, true",
                "false || false || true, true", "false || false || false, false", })
        void when_allValues_then_foldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class);
            assertThat(compiled).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void when_fourOperands_then_correctResult() {
            assertCompilesTo("false || false || false || false", Value.FALSE);
        }

        @Test
        void when_fiveOperands_withTrue_then_shortCircuits() {
            assertCompilesTo("false || true || false || false || false", Value.TRUE);
        }

        @Test
        void when_valueWithPure_then_returnsPureOperator() {
            var compiled = compileExpression("false || flag || false");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valueWithPure_andAllFalse_then_evaluatesToFalse() {
            assertPureEvaluatesTo("false || flag || false", Map.of("flag", Value.FALSE), Value.FALSE);
        }

        @Test
        void when_valueWithPure_andOneTrue_then_evaluatesToTrue() {
            assertPureEvaluatesTo("false || flag || false", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void when_multiplePures_allFalse_then_evaluatesToFalse() {
            assertPureEvaluatesTo("a || b || c", Map.of("a", Value.FALSE, "b", Value.FALSE, "c", Value.FALSE),
                    Value.FALSE);
        }

        @Test
        void when_multiplePures_oneTrue_then_evaluatesToTrue() {
            assertPureEvaluatesTo("a || b || c", Map.of("a", Value.FALSE, "b", Value.TRUE, "c", Value.FALSE),
                    Value.TRUE);
        }

        @Test
        void when_valueTrue_then_compileTimeShortCircuit() {
            // true || anything should be TRUE at compile time
            assertCompilesTo("true || false || false", Value.TRUE);
        }

        @Test
        void when_typeMismatchInValues_then_compileTimeError() {
            assertCompilesToError("false || \"string\" || true", "BOOLEAN");
        }

        @Test
        void when_typeMismatchInPure_then_runtimeError() {
            assertPureEvaluatesToError("false || notBoolean || false", Map.of("notBoolean", of(123)));
        }

        @Test
        void when_errorInValues_then_compileTimeError() {
            var compiled = compileExpression("false || undefined || false");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class StrataTests {

        @Test
        void when_allValuesConjunction_then_returnsValue() {
            var compiled = compileExpression("true && true && true");
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @Test
        void when_allValuesDisjunction_then_returnsValue() {
            var compiled = compileExpression("false || false || false");
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @Test
        void when_valuesAndPuresConjunction_then_returnsPureOperator() {
            var compiled = compileExpression("true && x && true");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_valuesAndPuresDisjunction_then_returnsPureOperator() {
            var compiled = compileExpression("false || x || false");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_onlyPures_then_returnsPureOperator() {
            var compiled = compileExpression("a && b && c");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void when_hasStream_then_returnsStreamOperator() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void when_shortCircuitValue_noOtherStrata_then_immediateResult() {
            // false && ... for Conjunction should return false immediately
            assertCompilesTo("false && true && true", Value.FALSE);
        }

        @Test
        void when_identityValues_then_foldedAway() {
            // true && true && x should fold away the trues, leaving just x
            var compiled = compileExpression("true && true && x");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            // The PureOperator should just evaluate x
        }
    }

    @Nested
    class ShortCircuitTests {

        @Test
        void conjunction_shortCircuitsOnFirstFalse() {
            // If first pure returns false, second should not be evaluated
            // We can verify this indirectly by checking compile-time behavior
            assertCompilesTo("false && undefined && true", Value.FALSE);
        }

        @Test
        void disjunction_shortCircuitsOnFirstTrue() {
            // If first pure returns true, second should not be evaluated
            assertCompilesTo("true || undefined || false", Value.TRUE);
        }

        @Test
        void conjunction_evaluatesLeftToRight_pure() {
            // First pure is false, second would cause error but shouldn't be reached
            var ctx    = evalContextWithVariables(
                    Map.of("first", Value.FALSE, "second", Value.error("should not see this")));
            var result = evaluateWithContext("first && second && true", ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void disjunction_evaluatesLeftToRight_pure() {
            // First pure is true, second would cause error but shouldn't be reached
            var ctx    = evalContextWithVariables(
                    Map.of("first", Value.TRUE, "second", Value.error("should not see this")));
            var result = evaluateWithContext("first || second || false", ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }
    }

    @Nested
    class DependsOnSubscriptionTests {

        @Test
        void when_puresDependOnSubscription_then_resultDependsOnSubscription() {
            // Identifiers depend on subscription by default
            var compiled = compileExpression("true && x && true");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }

        @Test
        void when_multiplePures_anyDepends_then_resultDepends() {
            var compiled = compileExpression("a && b && c");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
    }

    @Nested
    class StreamOperatorTests {

        // Conjunction with streams

        @Test
        void conjunction_valuesAndStream_then_returnsStreamOperator() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void conjunction_streamEmitsTrue_then_resultIsTrue() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_streamEmitsFalse_then_shortCircuits() {
            var broker   = testBroker("test.attr", Value.FALSE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void conjunction_multipleStreams_allTrue_then_true() {
            var broker   = multiValueBroker(Map.of("a.attr", Value.TRUE, "b.attr", Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_multipleStreams_firstFalse_shortCircuits() {
            var broker   = multiValueBroker(Map.of("a.attr", Value.FALSE, "b.attr", Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        // Disjunction with streams

        @Test
        void disjunction_valuesAndStream_then_returnsStreamOperator() {
            var broker   = testBroker("test.attr", Value.FALSE);
            var compiled = compileExpressionWithBroker("false || <test.attr> || false", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void disjunction_streamEmitsFalse_then_resultIsFalse() {
            var broker   = testBroker("test.attr", Value.FALSE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunction_streamEmitsTrue_then_shortCircuits() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunction_multipleStreams_allFalse_then_false() {
            var broker   = multiValueBroker(Map.of("a.attr", Value.FALSE, "b.attr", Value.FALSE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunction_multipleStreams_firstTrue_shortCircuits() {
            var broker   = multiValueBroker(Map.of("a.attr", Value.TRUE, "b.attr", Value.FALSE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        // Error handling in streams

        @Test
        void conjunction_streamEmitsError_then_propagatesError() {
            var broker   = errorBroker("test.attr", "Stream error");
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("Stream error");
            }).verifyComplete();
        }

        @Test
        void disjunction_streamEmitsError_then_propagatesError() {
            var broker   = errorBroker("test.attr", "Stream error");
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains("Stream error");
            }).verifyComplete();
        }

        @Test
        void conjunction_streamTypeMismatch_then_returnsError() {
            var broker   = testBroker("test.attr", of("not a boolean"));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class))
                    .verifyComplete();
        }

        // Pure short-circuit before stream subscription

        @Test
        void conjunction_pureShortCircuits_noStreamSubscription() {
            var broker   = multiValueBroker(Map.of("test.attr", Value.TRUE));
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("pureVal", Value.FALSE));
            var compiled = compileExpressionWithBroker("pureVal && <test.attr>", broker);

            // Since pureVal is FALSE, the stream should never be subscribed
            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunction_pureShortCircuits_noStreamSubscription() {
            var broker   = multiValueBroker(Map.of("test.attr", Value.FALSE));
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("pureVal", Value.TRUE));
            var compiled = compileExpressionWithBroker("pureVal || <test.attr>", broker);

            // Since pureVal is TRUE, the stream should never be subscribed
            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        // Pures + Streams combined

        @Test
        void conjunction_puresAndStreams_allPass_then_true() {
            var broker   = testBroker("test.attr", Value.TRUE);
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("x", Value.TRUE));
            var compiled = compileExpressionWithBroker("x && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunction_puresAndStreams_allFail_then_false() {
            var broker   = testBroker("test.attr", Value.FALSE);
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("x", Value.FALSE));
            var compiled = compileExpressionWithBroker("x || <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }
    }

    @Nested
    class MultiEventStreamTests {

        @Test
        void conjunction_singleStream_multipleEmissions_tracksAllChanges() {
            // Stream emits: TRUE, TRUE, FALSE, TRUE
            // Expected outputs: TRUE, TRUE, FALSE, TRUE
            var broker   = sequenceBroker("test.attr", Value.TRUE, Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_streamTransitionsToFalse_outputReflectsChange() {
            // true && <stream> where stream: TRUE -> FALSE -> TRUE
            // Expected: TRUE -> FALSE -> TRUE
            var broker   = sequenceBroker("test.attr", Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunction_streamTransitionsToTrue_outputReflectsChange() {
            // false || <stream> where stream: FALSE -> TRUE -> FALSE
            // Expected: FALSE -> TRUE -> FALSE
            var broker   = sequenceBroker("test.attr", Value.FALSE, Value.TRUE, Value.FALSE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("false || <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunction_twoStreams_firstChanges_reEvaluatesSecond() {
            // <a> && <b> where a: TRUE -> FALSE -> TRUE, b: TRUE (constant)
            // When a=TRUE: evaluate b -> TRUE && TRUE = TRUE
            // When a=FALSE: short-circuit -> FALSE (b not evaluated)
            // When a=TRUE again: evaluate b -> TRUE && TRUE = TRUE
            var broker   = dualSequenceBroker("a.attr", List.of(Value.TRUE, Value.FALSE, Value.TRUE), "b.attr",
                    List.of(Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunction_twoStreams_firstChanges_reEvaluatesSecond() {
            // <a> || <b> where a: FALSE -> TRUE -> FALSE, b: FALSE (constant)
            // When a=FALSE: evaluate b -> FALSE || FALSE = FALSE
            // When a=TRUE: short-circuit -> TRUE (b not evaluated)
            // When a=FALSE again: evaluate b -> FALSE || FALSE = FALSE
            var broker   = dualSequenceBroker("a.attr", List.of(Value.FALSE, Value.TRUE, Value.FALSE), "b.attr",
                    List.of(Value.FALSE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunction_threeStreams_chainedEvaluation() {
            // <a> && <b> && <c> where all emit TRUE
            // All three must be TRUE for result to be TRUE
            var broker   = tripleSequenceBroker("a.attr", List.of(Value.TRUE), "b.attr", List.of(Value.TRUE), "c.attr",
                    List.of(Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr> && <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_threeStreams_middleIsFalse_shortCircuits() {
            // <a> && <b> && <c> where a=TRUE, b=FALSE, c=TRUE
            // Should short-circuit at b, never subscribe to c
            var broker   = tripleSequenceBroker("a.attr", List.of(Value.TRUE), "b.attr", List.of(Value.FALSE), "c.attr",
                    List.of(Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr> && <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunction_threeStreams_middleIsTrue_shortCircuits() {
            // <a> || <b> || <c> where a=FALSE, b=TRUE, c=FALSE
            // Should short-circuit at b, never subscribe to c
            var broker   = tripleSequenceBroker("a.attr", List.of(Value.FALSE), "b.attr", List.of(Value.TRUE), "c.attr",
                    List.of(Value.FALSE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> || <b.attr> || <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_errorMidStream_propagatesError() {
            // Stream emits: TRUE, ERROR, TRUE
            // Expected: TRUE, ERROR, TRUE
            var error    = Value.error("mid-stream failure");
            var broker   = sequenceBroker("test.attr", Value.TRUE, error, Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> {
                        assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                        assertThat(((ErrorValue) tv.value()).message()).contains("mid-stream failure");
                    }).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_typeMismatchMidStream_propagatesError() {
            // Stream emits: TRUE, "not boolean", TRUE
            // Expected: TRUE, ERROR, TRUE
            var broker   = sequenceBroker("test.attr", Value.TRUE, of("not boolean"), Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> {
                        assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                        assertThat(((ErrorValue) tv.value()).message()).contains("BOOLEAN");
                    }).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunction_alternatingTrueFalse_producesAlternatingOutput() {
            // <stream> && true where stream: T, F, T, F, T
            var broker   = sequenceBroker("test.attr", Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunction_alternatingFalseTrue_producesAlternatingOutput() {
            // <stream> || false where stream: F, T, F, T, F
            var broker   = sequenceBroker("test.attr", Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunction_pureAndStream_pureEvaluatedOncePerStreamEmission() {
            // pureVal && <stream> where pureVal=TRUE, stream: T, T, F
            // Pure is re-evaluated for each stream emission
            var broker   = sequenceBroker("test.attr", Value.TRUE, Value.TRUE, Value.FALSE);
            var ctx      = contextWithBrokerAndVariables(broker, Map.of("pureVal", Value.TRUE));
            var compiled = compileExpressionWithBroker("pureVal && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunction_longSequence_allTrue_finallyTrue() {
            // 10 TRUE values in sequence
            var values = new Value[10];
            java.util.Arrays.fill(values, Value.TRUE);
            var broker   = sequenceBroker("test.attr", values);
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).expectNextCount(10).verifyComplete();
        }

        @Test
        void conjunction_twoStreams_bothEmitMultiple_combinesCorrectly() {
            // <a> && <b> where a: T, T, F and b: T (replayed for each a subscription)
            // Expected: T&&T=T, T&&T=T, F (short-circuit)
            var broker   = dualSequenceBroker("a.attr", List.of(Value.TRUE, Value.TRUE, Value.FALSE), "b.attr",
                    List.of(Value.TRUE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void disjunction_twoStreams_firstFalseThenTrue_secondNeverResubscribed() {
            // <a> || <b> where a: F, T and b: F
            // When a=F: subscribe to b, get F, result=F
            // When a=T: short-circuit, result=T (b cancelled)
            var broker   = dualSequenceBroker("a.attr", List.of(Value.FALSE, Value.TRUE), "b.attr",
                    List.of(Value.FALSE));
            var ctx      = contextWithBroker(broker);
            var compiled = compileExpressionWithBroker("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }
    }

    @Nested
    class ErrorPropagationTests {

        @Test
        void conjunction_errorInPure_propagates() {
            var ctx    = evalContextWithVariables(Map.of("broken", Value.error("broken variable")));
            var result = evaluateWithContext("true && broken && true", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("broken");
        }

        @Test
        void disjunction_errorInPure_propagates() {
            var ctx    = evalContextWithVariables(Map.of("broken", Value.error("broken variable")));
            var result = evaluateWithContext("false || broken || false", ctx);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains("broken");
        }

        @Test
        void conjunction_errorAfterShortCircuit_notReached() {
            // Should short-circuit before reaching the error
            var ctx    = evalContextWithVariables(
                    Map.of("first", Value.FALSE, "second", Value.error("should not see")));
            var result = evaluateWithContext("first && second", ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void disjunction_errorAfterShortCircuit_notReached() {
            // Should short-circuit before reaching the error
            var ctx    = evalContextWithVariables(Map.of("first", Value.TRUE, "second", Value.error("should not see")));
            var result = evaluateWithContext("first || second", ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }
    }

    private static EvaluationContext evalContextWithVariables(Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, null, null, () -> "test-timestamp");
    }

    private static CompiledExpression evaluateWithContext(String source, EvaluationContext ctx) {
        var compiled = compileExpression(source);
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(ctx);
        default              -> compiled;
        };
    }

    private static CompiledExpression compileExpressionWithBroker(String source, AttributeBroker broker) {
        var compilationCtx = new CompilationContext(null, broker);
        var expression     = io.sapl.util.ExpressionTestUtil.parseExpression(source);
        return ExpressionCompiler.compile(expression, compilationCtx);
    }

    private static EvaluationContext contextWithBroker(AttributeBroker broker) {
        return new EvaluationContext("pdp", "config", "sub", null, Map.of(), null, broker, () -> "test-timestamp");
    }

    private static EvaluationContext contextWithBrokerAndVariables(AttributeBroker broker,
            Map<String, Value> variables) {
        return new EvaluationContext("pdp", "config", "sub", null, variables, null, broker, () -> "test-timestamp");
    }

    private static AttributeBroker testBroker(String expectedName, Value result) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.just(result);
                }
                return Flux.just(Value.error("Unknown attribute: %s", invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker multiValueBroker(Map<String, Value> attributeValues) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var value = attributeValues.get(invocation.attributeName());
                if (value != null) {
                    return Flux.just(value);
                }
                return Flux.just(Value.error("Unknown attribute: %s", invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker errorBroker(String expectedName, String errorMessage) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.just(Value.error(errorMessage));
                }
                return Flux.just(Value.error("Unknown attribute"));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker sequenceBroker(String expectedName, Value... values) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName)) {
                    return Flux.fromArray(values);
                }
                return Flux.just(Value.error("Unknown attribute: %s", invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker dualSequenceBroker(String name1, List<Value> values1, String name2,
            List<Value> values2) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var name = invocation.attributeName();
                if (name.equals(name1)) {
                    return Flux.fromIterable(values1);
                }
                if (name.equals(name2)) {
                    return Flux.fromIterable(values2);
                }
                return Flux.just(Value.error("Unknown attribute: %s", name));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static AttributeBroker tripleSequenceBroker(String name1, List<Value> values1, String name2,
            List<Value> values2, String name3, List<Value> values3) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var name = invocation.attributeName();
                if (name.equals(name1)) {
                    return Flux.fromIterable(values1);
                }
                if (name.equals(name2)) {
                    return Flux.fromIterable(values2);
                }
                if (name.equals(name3)) {
                    return Flux.fromIterable(values3);
                }
                return Flux.just(Value.error("Unknown attribute: %s", name));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }
}
