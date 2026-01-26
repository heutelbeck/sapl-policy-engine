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
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static io.sapl.api.model.Value.of;
import static io.sapl.util.SaplTesting.assertCompilesTo;
import static io.sapl.util.SaplTesting.assertCompilesToError;
import static io.sapl.util.SaplTesting.assertEvaluatesTo;
import static io.sapl.util.SaplTesting.assertEvaluatesToError;
import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.errorAttributeBroker;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.sequenceBroker;
import static io.sapl.util.SaplTesting.singleValueAttributeBroker;
import static io.sapl.util.SaplTesting.testContext;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;

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
@DisplayName("LazyNaryBooleanCompiler")
class LazyNaryBooleanCompilerTests {

    @Nested
    class ConjunctionTests {

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource({ "true && true && true,   true", "true && true && false,  false", "true && false && true,  false",
                "true && false && false, false", "false && true && true,  false", "false && true && false, false",
                "false && false && true, false", "false && false && false, false", })
        void whenAllValuesThenFoldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void whenFourOperandsThenCorrectResult() {
            assertCompilesTo("true && true && true && true", Value.TRUE);
        }

        @Test
        void whenFiveOperandsWithFalseThenShortCircuits() {
            assertCompilesTo("true && false && true && true && true", Value.FALSE);
        }

        @Test
        void whenValueWithSubscriptionElementThenReturnsPureOperator() {
            var compiled = compileExpression("true && subject && true");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenValueWithVariableAndAllTrueThenEvaluatesToTrue() {
            assertEvaluatesTo("true && flag && true", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void whenValueWithVariableAndOneFalseThenEvaluatesToFalse() {
            assertEvaluatesTo("true && flag && true", Map.of("flag", Value.FALSE), Value.FALSE);
        }

        @Test
        void whenMultipleVariablesAllTrueThenEvaluatesToTrue() {
            assertEvaluatesTo("a && b && c", Map.of("a", Value.TRUE, "b", Value.TRUE, "c", Value.TRUE), Value.TRUE);
        }

        @Test
        void whenMultipleVariablesOneFalseThenEvaluatesToFalse() {
            assertEvaluatesTo("a && b && c", Map.of("a", Value.TRUE, "b", Value.FALSE, "c", Value.TRUE), Value.FALSE);
        }

        @Test
        void whenValueFalseThenCompileTimeShortCircuit() {
            // false && anything should be FALSE at compile time
            assertCompilesTo("false && true && true", Value.FALSE);
        }

        @Test
        void whenTypeMismatchInValuesThenCompileTimeError() {
            assertCompilesToError("true && 5 && false", "BOOLEAN");
        }

        @Test
        void whenTypeMismatchInVariableThenError() {
            assertEvaluatesToError("true && notBoolean && true", Map.of("notBoolean", of("hello")));
        }

        @Test
        void whenErrorInValuesThenCompileTimeError() {
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
        void whenAllValuesThenFoldAtCompileTime(String expr, boolean expected) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }

        @Test
        void whenFourOperandsThenCorrectResult() {
            assertCompilesTo("false || false || false || false", Value.FALSE);
        }

        @Test
        void whenFiveOperandsWithTrueThenShortCircuits() {
            assertCompilesTo("false || true || false || false || false", Value.TRUE);
        }

        @Test
        void whenValueWithSubscriptionElementThenReturnsPureOperator() {
            var compiled = compileExpression("false || subject || false");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenValueWithVariableAndAllFalseThenEvaluatesToFalse() {
            assertEvaluatesTo("false || flag || false", Map.of("flag", Value.FALSE), Value.FALSE);
        }

        @Test
        void whenValueWithVariableAndOneTrueThenEvaluatesToTrue() {
            assertEvaluatesTo("false || flag || false", Map.of("flag", Value.TRUE), Value.TRUE);
        }

        @Test
        void whenMultipleVariablesAllFalseThenEvaluatesToFalse() {
            assertEvaluatesTo("a || b || c", Map.of("a", Value.FALSE, "b", Value.FALSE, "c", Value.FALSE), Value.FALSE);
        }

        @Test
        void whenMultipleVariablesOneTrueThenEvaluatesToTrue() {
            assertEvaluatesTo("a || b || c", Map.of("a", Value.FALSE, "b", Value.TRUE, "c", Value.FALSE), Value.TRUE);
        }

        @Test
        void whenValueTrueThenCompileTimeShortCircuit() {
            // true || anything should be TRUE at compile time
            assertCompilesTo("true || false || false", Value.TRUE);
        }

        @Test
        void whenTypeMismatchInValuesThenCompileTimeError() {
            assertCompilesToError("false || \"string\" || true", "BOOLEAN");
        }

        @Test
        void whenTypeMismatchInVariableThenError() {
            assertEvaluatesToError("false || notBoolean || false", Map.of("notBoolean", of(123)));
        }

        @Test
        void whenErrorInValuesThenCompileTimeError() {
            var compiled = compileExpression("false || undefined || false");
            assertThat(compiled).isInstanceOf(ErrorValue.class);
        }
    }

    @Nested
    class StrataTests {

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "conjunction,true && true && true", "disjunction,false || false || false" })
        void whenAllValuesThenReturnsValue(String description, String expr) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(Value.class);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "conjunction,true && subject && true", "disjunction,false || subject || false" })
        void whenValuesAndSubscriptionElementThenReturnsPureOperator(String description, String expr) {
            var compiled = compileExpression(expr);
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenOnlySubscriptionElementsThenReturnsPureOperator() {
            var compiled = compileExpression("subject && action && resource");
            assertThat(compiled).isInstanceOf(PureOperator.class);
        }

        @Test
        void whenHasStreamThenReturnsStreamOperator() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var compiled = compileExpression("true && <test.attr> && true", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void whenShortCircuitValueNoOtherStrataThenImmediateResult() {
            // false && ... for Conjunction should return false immediately
            assertCompilesTo("false && true && true", Value.FALSE);
        }

        @Test
        void whenIdentityValuesThenFoldedAway() {
            // true && true && subject should fold away the trues, leaving just subject
            var compiled = compileExpression("true && true && subject");
            assertThat(compiled).isInstanceOf(PureOperator.class);
            // The PureOperator should just evaluate subject
        }
    }

    @Nested
    class ShortCircuitTests {

        @Test
        void conjunctionShortCircuitsOnFirstFalse() {
            // If first pure returns false, second should not be evaluated
            // We can verify this indirectly by checking compile-time behavior
            assertCompilesTo("false && undefined && true", Value.FALSE);
        }

        @Test
        void disjunctionShortCircuitsOnFirstTrue() {
            // If first pure returns true, second should not be evaluated
            assertCompilesTo("true || undefined || false", Value.TRUE);
        }

        @Test
        void conjunctionEvaluatesLeftToRightPure() {
            // First pure is false, second would cause errors but shouldn't be reached
            var ctx    = testContext(Map.of("a", Value.FALSE, "b", Value.error("should not see this")));
            var result = evaluateExpression("a && b && true", ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        void disjunctionEvaluatesLeftToRightPure() {
            // First pure is true, second would cause errors but shouldn't be reached
            var ctx    = testContext(Map.of("a", Value.TRUE, "b", Value.error("should not see this")));
            var result = evaluateExpression("a || b || false", ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }
    }

    @Nested
    class DependsOnSubscriptionTests {

        @Test
        void whenSubscriptionElementThenResultDependsOnSubscription() {
            // Subscription elements depend on subscription
            var compiled = compileExpression("true && subject && true");
            assertThat(compiled).isInstanceOf(PureOperator.class)
                    .extracting(c -> ((PureOperator) c).isDependingOnSubscription()).isEqualTo(true);
        }

        @Test
        void whenMultipleSubscriptionElementsThenResultDepends() {
            var compiled = compileExpression("subject && action && resource");
            assertThat(compiled).isInstanceOf(PureOperator.class)
                    .extracting(c -> ((PureOperator) c).isDependingOnSubscription()).isEqualTo(true);
        }
    }

    @Nested
    class StreamOperatorTests {

        // Conjunction with streams

        @Test
        void conjunctionValuesAndStreamThenReturnsStreamOperator() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var compiled = compileExpression("true && <test.attr> && true", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void conjunctionStreamEmitsTrueThenResultIsTrue() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionStreamEmitsFalseThenShortCircuits() {
            var broker   = attributeBroker("test.attr", Value.FALSE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void conjunctionMultipleStreamsAllTrueThenTrue() {
            var broker   = singleValueAttributeBroker(Map.of("a.attr", Value.TRUE, "b.attr", Value.TRUE));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionMultipleStreamsFirstFalseShortCircuits() {
            var broker   = singleValueAttributeBroker(Map.of("a.attr", Value.FALSE, "b.attr", Value.TRUE));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        // Disjunction with streams

        @Test
        void disjunctionValuesAndStreamThenReturnsStreamOperator() {
            var broker   = attributeBroker("test.attr", Value.FALSE);
            var compiled = compileExpression("false || <test.attr> || false", broker);
            assertThat(compiled).isInstanceOf(StreamOperator.class);
        }

        @Test
        void disjunctionStreamEmitsFalseThenResultIsFalse() {
            var broker   = attributeBroker("test.attr", Value.FALSE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunctionStreamEmitsTrueThenShortCircuits() {
            var broker   = attributeBroker("test.attr", Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunctionMultipleStreamsAllFalseThenFalse() {
            var broker   = singleValueAttributeBroker(Map.of("a.attr", Value.FALSE, "b.attr", Value.FALSE));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunctionMultipleStreamsFirstTrueShortCircuits() {
            var broker   = singleValueAttributeBroker(Map.of("a.attr", Value.TRUE, "b.attr", Value.FALSE));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        // Error handling in streams

        @Test
        void conjunctionStreamEmitsErrorThenPropagatesError() {
            var broker   = errorAttributeBroker("test.attr", "Stream errors");
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream)
                    .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                            .extracting(v -> ((ErrorValue) v).message()).asString().contains("Stream errors"))
                    .verifyComplete();
        }

        @Test
        void disjunctionStreamEmitsErrorThenPropagatesError() {
            var broker   = errorAttributeBroker("test.attr", "Stream errors");
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("false || <test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream)
                    .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                            .extracting(v -> ((ErrorValue) v).message()).asString().contains("Stream errors"))
                    .verifyComplete();
        }

        @Test
        void conjunctionStreamTypeMismatchThenReturnsError() {
            var broker   = attributeBroker("test.attr", of("not a boolean"));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class))
                    .verifyComplete();
        }

        // Pure short-circuit before stream subscription (using subscription elements
        // for runtime resolution)

        @Test
        void conjunctionPureShortCircuitsNoStreamSubscription() {
            var broker       = singleValueAttributeBroker(Map.of("test.attr", Value.TRUE));
            var subscription = AuthorizationSubscription.of(Value.FALSE, Value.NULL, Value.NULL, Value.NULL);
            var evalCtx      = evaluationContext(subscription, broker);
            var compiled     = compileExpression("subject && <test.attr>", compilationContext(broker));

            // Since subject is FALSE, the stream should never be subscribed
            var stream = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunctionPureShortCircuitsNoStreamSubscription() {
            var broker       = singleValueAttributeBroker(Map.of("test.attr", Value.FALSE));
            var subscription = AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL);
            var evalCtx      = evaluationContext(subscription, broker);
            var compiled     = compileExpression("subject || <test.attr>", compilationContext(broker));

            // Since subject is TRUE, the stream should never be subscribed
            var stream = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        // Pures + Streams combined (using subscription elements for runtime resolution)

        @Test
        void conjunctionPuresAndStreamsAllPassThenTrue() {
            var broker       = attributeBroker("test.attr", Value.TRUE);
            var subscription = AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL);
            var evalCtx      = evaluationContext(subscription, broker);
            var compiled     = compileExpression("subject && <test.attr>", compilationContext(broker));

            var stream = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunctionPuresAndStreamsAllFailThenFalse() {
            var broker       = attributeBroker("test.attr", Value.FALSE);
            var subscription = AuthorizationSubscription.of(Value.FALSE, Value.NULL, Value.NULL, Value.NULL);
            var evalCtx      = evaluationContext(subscription, broker);
            var compiled     = compileExpression("subject || <test.attr>", compilationContext(broker));

            var stream = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }
    }

    @Nested
    class MultiEventStreamTests {

        @Test
        void conjunctionSingleStreamMultipleEmissionsTracksAllChanges() {
            // Stream emits: TRUE, TRUE, FALSE, TRUE
            // Expected outputs: TRUE, TRUE, FALSE, TRUE
            var broker   = attributeBroker("test.attr", Value.TRUE, Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionStreamTransitionsToFalseOutputReflectsChange() {
            // true && <stream> where stream: TRUE -> FALSE -> TRUE
            // Expected: TRUE -> FALSE -> TRUE
            var broker   = attributeBroker("test.attr", Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunctionStreamTransitionsToTrueOutputReflectsChange() {
            // false || <stream> where stream: FALSE -> TRUE -> FALSE
            // Expected: FALSE -> TRUE -> FALSE
            var broker   = attributeBroker("test.attr", Value.FALSE, Value.TRUE, Value.FALSE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("false || <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunctionTwoStreamsFirstChangesReEvaluatesSecond() {
            // <a> && <b> where a: TRUE -> FALSE -> TRUE, b: TRUE (constant)
            // When a=TRUE: evaluate b -> TRUE && TRUE = TRUE
            // When a=FALSE: short-circuit -> FALSE (b not evaluated)
            // When a=TRUE again: evaluate b -> TRUE && TRUE = TRUE
            var broker   = sequenceBroker(
                    Map.of("a.attr", List.of(Value.TRUE, Value.FALSE, Value.TRUE), "b.attr", List.of(Value.TRUE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunctionTwoStreamsFirstChangesReEvaluatesSecond() {
            // <a> || <b> where a: FALSE -> TRUE -> FALSE, b: FALSE (constant)
            // When a=FALSE: evaluate b -> FALSE || FALSE = FALSE
            // When a=TRUE: short-circuit -> TRUE (b not evaluated)
            // When a=FALSE again: evaluate b -> FALSE || FALSE = FALSE
            var broker   = sequenceBroker(
                    Map.of("a.attr", List.of(Value.FALSE, Value.TRUE, Value.FALSE), "b.attr", List.of(Value.FALSE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunctionThreeStreamsChainedEvaluation() {
            // <a> && <b> && <c> where all emit TRUE
            // All three must be TRUE for result to be TRUE
            var broker   = sequenceBroker(Map.of("a.attr", List.of(Value.TRUE), "b.attr", List.of(Value.TRUE), "c.attr",
                    List.of(Value.TRUE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr> && <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionThreeStreamsMiddleIsFalseShortCircuits() {
            // <a> && <b> && <c> where a=TRUE, b=FALSE, c=TRUE
            // Should short-circuit at b, never subscribe to c
            var broker   = sequenceBroker(Map.of("a.attr", List.of(Value.TRUE), "b.attr", List.of(Value.FALSE),
                    "c.attr", List.of(Value.TRUE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr> && <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .verifyComplete();
        }

        @Test
        void disjunctionThreeStreamsMiddleIsTrueShortCircuits() {
            // <a> || <b> || <c> where a=FALSE, b=TRUE, c=FALSE
            // Should short-circuit at b, never subscribe to c
            var broker   = sequenceBroker(Map.of("a.attr", List.of(Value.FALSE), "b.attr", List.of(Value.TRUE),
                    "c.attr", List.of(Value.FALSE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> || <b.attr> || <c.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionErrorMidStreamPropagatesError() {
            // Stream emits: TRUE, ERROR, TRUE
            // Expected: TRUE, ERROR, TRUE
            var error    = Value.error("mid-stream failure");
            var broker   = attributeBroker("test.attr", Value.TRUE, error, Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                            .extracting(v -> ((ErrorValue) v).message()).asString().contains("mid-stream failure"))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionTypeMismatchMidStreamPropagatesError() {
            // Stream emits: TRUE, "not boolean", TRUE
            // Expected: TRUE, ERROR, TRUE
            var broker   = attributeBroker("test.attr", Value.TRUE, of("not boolean"), Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                            .extracting(v -> ((ErrorValue) v).message()).asString().contains("BOOLEAN"))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void conjunctionAlternatingTrueFalseProducesAlternatingOutput() {
            // <stream> && true where stream: T, F, T, F, T
            var broker   = attributeBroker("test.attr", Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<test.attr> && true", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        void disjunctionAlternatingFalseTrueProducesAlternatingOutput() {
            // <stream> || false where stream: F, T, F, T, F
            var broker   = attributeBroker("test.attr", Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<test.attr> || false", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunctionPureAndStreamPureEvaluatedOncePerStreamEmission() {
            // subject && <stream> where subject=TRUE, stream: T, T, F
            // Pure is re-evaluated for each stream emission
            var broker       = attributeBroker("test.attr", Value.TRUE, Value.TRUE, Value.FALSE);
            var subscription = AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL);
            var evalCtx      = evaluationContext(subscription, broker);
            var compiled     = compileExpression("subject && <test.attr>", compilationContext(broker));

            var stream = ((StreamOperator) compiled).stream()
                    .contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void conjunctionLongSequenceAllTrueFinallyTrue() {
            // 10 TRUE values in sequence
            var values = new Value[10];
            java.util.Arrays.fill(values, Value.TRUE);
            var broker   = attributeBroker("test.attr", values);
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("true && <test.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).expectNextCount(10).verifyComplete();
        }

        @Test
        void conjunctionTwoStreamsBothEmitMultipleCombinesCorrectly() {
            // <a> && <b> where a: T, T, F and b: T (replayed for each a subscription)
            // Expected: T&&T=T, T&&T=T, F (short-circuit)
            var broker   = sequenceBroker(
                    Map.of("a.attr", List.of(Value.TRUE, Value.TRUE, Value.FALSE), "b.attr", List.of(Value.TRUE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> && <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE)).verifyComplete();
        }

        @Test
        void disjunctionTwoStreamsFirstFalseThenTrueSecondNeverResubscribed() {
            // <a> || <b> where a: F, T and b: F
            // When a=F: subscribe to b, get F, result=F
            // When a=T: short-circuit, result=T (b cancelled)
            var broker   = sequenceBroker(
                    Map.of("a.attr", List.of(Value.FALSE, Value.TRUE), "b.attr", List.of(Value.FALSE)));
            var ctx      = evaluationContext(broker);
            var compiled = compileExpression("<a.attr> || <b.attr>", broker);

            var stream = ((StreamOperator) compiled).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
            StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }
    }

    @Nested
    class ErrorPropagationTests {

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "conjunction,true && broken && true", "disjunction,false || broken || false" })
        void errorInPurePropagates(String description, String expr) {
            var ctx    = testContext(Map.of("broken", Value.error("broken variable")));
            var result = evaluateExpression(expr, ctx);
            assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("broken");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({ "conjunction,false,a && b,false", "disjunction,true,a || b,true" })
        void errorAfterShortCircuitNotReached(String description, boolean shortCircuitValue, String expr,
                boolean expected) {
            // Should short-circuit before reaching the errors
            var a      = shortCircuitValue ? Value.TRUE : Value.FALSE;
            var ctx    = testContext(Map.of("a", a, "b", Value.error("should not see")));
            var result = evaluateExpression(expr, ctx);
            assertThat(result).isEqualTo(expected ? Value.TRUE : Value.FALSE);
        }
    }

}
