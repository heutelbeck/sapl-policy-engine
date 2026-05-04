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

import java.util.Arrays;
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
import static io.sapl.util.SaplTesting.evaluate;
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
            var value = evaluate("true && <test.attr> && true").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        void conjunctionStreamEmitsFalseThenShortCircuits() {
            var value = evaluate("true && <test.attr> && true").with("test.attr", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        void conjunctionLeftFalseShortCircuitsRightNotDiscovered() {
            // a.attr=FALSE makes the lazy AND short-circuit at the left. The right
            // operand <b.attr> is never inspected, so its subscription is never
            // requested. Verifies the lazy non-subscription property explicitly,
            // which was previously hidden inside Reactor switchMap chains.
            var result = evaluate("<a.attr> && <b.attr>").with("a.attr", Value.FALSE).result();

            assertThat(result.result()).isEqualTo(Value.FALSE);
            assertThat(result.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");
        }

        @Test
        void conjunctionLeftTrueRightDiscoveredOnDemand() {
            // a.attr=TRUE forces the lazy AND to evaluate the right operand. The
            // right side <b.attr> is discovered as a new subscription this round.
            // Result is null because b.attr was unknown when this round's snapshot
            // was bound; the dependency set proves the right subscription request.
            var result = evaluate("<a.attr> && <b.attr>").with("a.attr", Value.TRUE).result();

            assertThat(result.result()).isNull();
            assertThat(result.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
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
            var value = evaluate("false || <test.attr> || false").with("test.attr", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        void disjunctionStreamEmitsTrueThenShortCircuits() {
            var value = evaluate("false || <test.attr> || false").with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        void disjunctionLeftTrueShortCircuitsRightNotDiscovered() {
            // a.attr=TRUE makes the lazy OR short-circuit at the left. The right
            // operand <b.attr> is never inspected, so its subscription is never
            // requested.
            var result = evaluate("<a.attr> || <b.attr>").with("a.attr", Value.TRUE).result();

            assertThat(result.result()).isEqualTo(Value.TRUE);
            assertThat(result.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");
        }

        @Test
        void disjunctionLeftFalseRightDiscoveredOnDemand() {
            // a.attr=FALSE forces the lazy OR to evaluate the right operand. The
            // right side <b.attr> is discovered as a new subscription this round.
            // Result is null because b.attr was unknown when this round's snapshot
            // was bound; the dependency set proves the right subscription request.
            var result = evaluate("<a.attr> || <b.attr>").with("a.attr", Value.FALSE).result();

            assertThat(result.result()).isNull();
            assertThat(result.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }

        // Error handling in streams

        @Test
        void conjunctionStreamEmitsErrorThenPropagatesError() {
            var value = evaluate("true && <test.attr> && true").with("test.attr", Value.error("Stream errors")).value();
            assertThat(value).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Stream errors");
        }

        @Test
        void disjunctionStreamEmitsErrorThenPropagatesError() {
            var value = evaluate("false || <test.attr> || false").with("test.attr", Value.error("Stream errors"))
                    .value();
            assertThat(value).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("Stream errors");
        }

        @Test
        void conjunctionStreamTypeMismatchThenReturnsError() {
            var value = evaluate("true && <test.attr> && true").with("test.attr", of("not a boolean")).value();
            assertThat(value).isInstanceOf(ErrorValue.class);
        }

        // Pure short-circuit before stream subscription (using subscription elements
        // for runtime resolution)

        @Test
        void conjunctionPureShortCircuitsNoStreamSubscription() {
            // Since subject is FALSE, the stream is never read.
            var value = evaluate("subject && <test.attr>")
                    .withSubscription(AuthorizationSubscription.of(Value.FALSE, Value.NULL, Value.NULL, Value.NULL))
                    .with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }

        @Test
        void disjunctionPureShortCircuitsNoStreamSubscription() {
            // Since subject is TRUE, the stream is never read.
            var value = evaluate("subject || <test.attr>")
                    .withSubscription(AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL))
                    .with("test.attr", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        // Pures + Streams combined (using subscription elements for runtime resolution)

        @Test
        void conjunctionPuresAndStreamsAllPassThenTrue() {
            var value = evaluate("subject && <test.attr>")
                    .withSubscription(AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL))
                    .with("test.attr", Value.TRUE).value();
            assertThat(value).isEqualTo(Value.TRUE);
        }

        @Test
        void disjunctionPuresAndStreamsAllFailThenFalse() {
            var value = evaluate("subject || <test.attr>")
                    .withSubscription(AuthorizationSubscription.of(Value.FALSE, Value.NULL, Value.NULL, Value.NULL))
                    .with("test.attr", Value.FALSE).value();
            assertThat(value).isEqualTo(Value.FALSE);
        }
    }

    @Nested
    class MultiEventStreamTests {

        @Test
        void conjunctionSingleStreamMultipleEmissionsTracksAllChanges() {
            // Each rebinding of test.attr is one round; the value flows through
            // the bare attribute access immediately.
            var driver = evaluate("<test.attr>");
            driver.step(); // discovery

            for (var v : List.of(Value.TRUE, Value.TRUE, Value.FALSE, Value.TRUE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void conjunctionStreamTransitionsToFalseOutputReflectsChange() {
            // true && <stream>: pure constant on left is folded; only the stream
            // attribute is in the dep set. Each round reflects the new value.
            var driver = evaluate("true && <test.attr>");
            driver.step();

            for (var v : List.of(Value.TRUE, Value.FALSE, Value.TRUE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void disjunctionStreamTransitionsToTrueOutputReflectsChange() {
            // false || <stream>: same shape, OR with constant false on left.
            var driver = evaluate("false || <test.attr>");
            driver.step();

            for (var v : List.of(Value.FALSE, Value.TRUE, Value.FALSE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void conjunctionTwoStreamsFirstChangesReEvaluatesSecond() {
            // Drives <a.attr> && <b.attr> through five rounds, asserting on the
            // dependency set after each step to verify the lazy AND's behaviour
            // directly:
            // * lazy discovery : b only enters the dep set once a resolves to TRUE
            // * lazy short-circuit : when a flips back to FALSE, b leaves the dep set
            // * lazy re-subscribe : when a returns to TRUE, b re-enters
            // Each round's deps reflect exactly the subscriptions the expression
            // touched that round; pre-snapshot, this property was not directly
            // observable - it was hidden in switchMap chains.
            var driver = evaluate("<a.attr> && <b.attr>");

            // Round 1: empty snapshot. Lazy AND only requests left.
            var r1 = driver.step();
            assertThat(r1.result()).isNull();
            assertThat(r1.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");

            // Round 2: bind a=TRUE. Lazy AND now needs the right side; b is discovered.
            driver.with("a.attr", Value.TRUE);
            var r2 = driver.step();
            assertThat(r2.result()).isNull();
            assertThat(r2.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");

            // Round 3: bind b=TRUE. Both resolved; lazy AND walks both sides.
            driver.with("b.attr", Value.TRUE);
            var r3 = driver.step();
            assertThat(r3.result()).isEqualTo(Value.TRUE);
            assertThat(r3.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");

            // Round 4: flip a to FALSE. Lazy short-circuits at left; b drops from deps.
            driver.with("a.attr", Value.FALSE);
            var r4 = driver.step();
            assertThat(r4.result()).isEqualTo(Value.FALSE);
            assertThat(r4.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");

            // Round 5: a returns to TRUE. b re-subscribes; its prior binding still applies.
            driver.with("a.attr", Value.TRUE);
            var r5 = driver.step();
            assertThat(r5.result()).isEqualTo(Value.TRUE);
            assertThat(r5.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }

        @Test
        void disjunctionTwoStreamsFirstChangesReEvaluatesSecond() {
            // Mirror of the conjunction equivalent above, but with || and inverted
            // truth values. Drives <a> || <b> with a: FALSE -> TRUE -> FALSE,
            // b held at FALSE. Verifies lazy short-circuit (a=TRUE drops b) and
            // re-subscribe (a=FALSE again brings b back).
            var driver = evaluate("<a.attr> || <b.attr>");

            driver.step(); // discovery: only a

            driver.with("a.attr", Value.FALSE);
            var r2 = driver.step();
            assertThat(r2.result()).isNull();
            assertThat(r2.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");

            driver.with("b.attr", Value.FALSE);
            assertThat(driver.step().result()).isEqualTo(Value.FALSE);

            driver.with("a.attr", Value.TRUE);
            var r4 = driver.step();
            assertThat(r4.result()).isEqualTo(Value.TRUE);
            assertThat(r4.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");

            driver.with("a.attr", Value.FALSE);
            var r5 = driver.step();
            assertThat(r5.result()).isEqualTo(Value.FALSE);
            assertThat(r5.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }

        @Test
        void conjunctionThreeStreamsAllTrueAllInDependencies() {
            // <a> && <b> && <c> with all TRUE: every step adds the next stream
            // to the dep set lazily, eventually all three contribute.
            var driver = evaluate("<a.attr> && <b.attr> && <c.attr>");

            driver.step(); // discovery: a only

            driver.with("a.attr", Value.TRUE);
            assertThat(driver.step().dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");

            driver.with("b.attr", Value.TRUE);
            assertThat(driver.step().dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr", "c.attr");

            driver.with("c.attr", Value.TRUE);
            var r4 = driver.step();
            assertThat(r4.result()).isEqualTo(Value.TRUE);
            assertThat(r4.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr", "c.attr");
        }

        @Test
        void conjunctionThreeStreamsMiddleIsFalseShortCircuitsThirdNeverDiscovered() {
            // <a> && <b> && <c> with a=TRUE, b=FALSE: short-circuits at b; c
            // never enters the dep set.
            var driver = evaluate("<a.attr> && <b.attr> && <c.attr>");

            driver.step();

            driver.with("a.attr", Value.TRUE);
            driver.step();

            driver.with("b.attr", Value.FALSE);
            var r3 = driver.step();
            assertThat(r3.result()).isEqualTo(Value.FALSE);
            assertThat(r3.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }

        @Test
        void disjunctionThreeStreamsMiddleIsTrueShortCircuitsThirdNeverDiscovered() {
            // <a> || <b> || <c> with a=FALSE, b=TRUE: short-circuits at b; c
            // never enters the dep set.
            var driver = evaluate("<a.attr> || <b.attr> || <c.attr>");

            driver.step();

            driver.with("a.attr", Value.FALSE);
            driver.step();

            driver.with("b.attr", Value.TRUE);
            var r3 = driver.step();
            assertThat(r3.result()).isEqualTo(Value.TRUE);
            assertThat(r3.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }

        @Test
        void barAttributeErrorPropagatesPerRound() {
            // <test.attr> rebound each round; an error binding propagates as the
            // round's value just like a normal value would.
            var driver = evaluate("<test.attr>");
            driver.step();

            driver.with("test.attr", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);

            driver.with("test.attr", Value.error("mid-stream failure"));
            var rErr = driver.step();
            assertThat(rErr.result()).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                    .asString().contains("mid-stream failure");

            driver.with("test.attr", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);
        }

        @Test
        void conjunctionTypeMismatchMidStreamPropagatesErrorPerRound() {
            // true && <test.attr>: when <test.attr> is bound to a non-boolean
            // value, the type-check inside the lazy AND fails for that round
            // and recovers when a boolean value is rebound.
            var driver = evaluate("true && <test.attr>");
            driver.step();

            driver.with("test.attr", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);

            driver.with("test.attr", of("not boolean"));
            var rErr = driver.step();
            assertThat(rErr.result()).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                    .asString().contains("BOOLEAN");

            driver.with("test.attr", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);
        }

        @Test
        void conjunctionAlternatingTrueFalseProducesAlternatingOutput() {
            // <stream> && true: each round echoes the bound stream value.
            var driver = evaluate("<test.attr> && true");
            driver.step();

            for (var v : List.of(Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void disjunctionAlternatingFalseTrueProducesAlternatingOutput() {
            // <stream> || false: each round echoes the bound stream value.
            var driver = evaluate("<test.attr> || false");
            driver.step();

            for (var v : List.of(Value.FALSE, Value.TRUE, Value.FALSE, Value.TRUE, Value.FALSE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void conjunctionPureAndStreamPureEvaluatedOncePerStreamEmission() {
            // subject && <test.attr> with subject=TRUE; the pure left is
            // evaluated each round, the stream right is rebound per round.
            var driver = evaluate("subject && <test.attr>")
                    .withSubscription(AuthorizationSubscription.of(Value.TRUE, Value.NULL, Value.NULL, Value.NULL));
            driver.step();

            for (var v : List.of(Value.TRUE, Value.TRUE, Value.FALSE)) {
                driver.with("test.attr", v);
                assertThat(driver.step().result()).isEqualTo(v);
            }
        }

        @Test
        void conjunctionLongSequenceAllTrueProducesTrueEachRound() {
            // Drives true && <test.attr> through 10 rounds with TRUE; each
            // round emits TRUE.
            var driver = evaluate("true && <test.attr>");
            driver.step();

            for (int i = 0; i < 10; i++) {
                driver.with("test.attr", Value.TRUE);
                assertThat(driver.step().result()).isEqualTo(Value.TRUE);
            }
        }

        @Test
        void conjunctionTwoStreamsBothEmitMultipleCombinesCorrectly() {
            // <a> && <b> where a flips TRUE, TRUE, FALSE; b held at TRUE.
            // First two rounds full-eval to TRUE; third round short-circuits
            // (b drops from deps).
            var driver = evaluate("<a.attr> && <b.attr>");
            driver.step();

            driver.with("a.attr", Value.TRUE);
            driver.step();

            driver.with("b.attr", Value.TRUE);
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);

            // a still TRUE; b still TRUE; deps still both
            assertThat(driver.step().result()).isEqualTo(Value.TRUE);

            driver.with("a.attr", Value.FALSE);
            var r5 = driver.step();
            assertThat(r5.result()).isEqualTo(Value.FALSE);
            assertThat(r5.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");
        }

        @Test
        void disjunctionTwoStreamsFirstFalseThenTrueSecondNotInDepsAfterShortCircuit() {
            // <a> || <b>: a=FALSE, b=FALSE; both in deps and resolve to FALSE.
            // Then a=TRUE; lazy OR short-circuits and b drops from deps.
            var driver = evaluate("<a.attr> || <b.attr>");
            driver.step();

            driver.with("a.attr", Value.FALSE);
            driver.step();

            driver.with("b.attr", Value.FALSE);
            var r3 = driver.step();
            assertThat(r3.result()).isEqualTo(Value.FALSE);
            assertThat(r3.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");

            driver.with("a.attr", Value.TRUE);
            var r4 = driver.step();
            assertThat(r4.result()).isEqualTo(Value.TRUE);
            assertThat(r4.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                    .containsExactly("a.attr");
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
