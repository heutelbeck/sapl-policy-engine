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
import io.sapl.compiler.BooleanGuardCompiler.PureBooleanTypeCheck;
import io.sapl.compiler.BooleanGuardCompiler.StreamBooleanTypeCheck;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.compiler.BooleanGuardCompiler.applyBooleanGuard;
import static io.sapl.util.ExpressionTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BooleanGuardCompiler")
class BooleanGuardCompilerTests {

    private static final String ERROR_TEMPLATE = "Expected BOOLEAN but got: %s";

    @Nested
    @DisplayName("applyBooleanGuard")
    class ApplyBooleanGuardTests {

        @ParameterizedTest(name = "when input is {0} then returns it unchanged")
        @MethodSource("booleanValues")
        void whenBooleanValue_thenReturnsUnchanged(String description, BooleanValue input) {
            val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(result).isSameAs(input);
        }

        static Stream<Arguments> booleanValues() {
            return Stream.of(arguments("TRUE", Value.TRUE), arguments("FALSE", Value.FALSE));
        }

        @Test
        @DisplayName("when input is ErrorValue then returns it unchanged")
        void whenErrorValue_thenReturnsUnchanged() {
            val error  = Value.error("some error");
            val result = applyBooleanGuard(error, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(result).isSameAs(error);
        }

        @ParameterizedTest(name = "when input is {0} then returns error")
        @MethodSource("nonBooleanValues")
        @DisplayName("when input is non-boolean Value then returns ErrorValue")
        void whenNonBooleanValue_thenReturnsError(String description, Value input) {
            val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains(input.toString());
        }

        static Stream<Arguments> nonBooleanValues() {
            return Stream.of(arguments("NumberValue", Value.of(42)), arguments("TextValue", Value.of("hello")),
                    arguments("NullValue", Value.NULL), arguments("UndefinedValue", Value.UNDEFINED),
                    arguments("ArrayValue", array(Value.of(1), Value.of(2))),
                    arguments("ObjectValue", obj("key", Value.of("value"))));
        }

        @ParameterizedTest(name = "when PureOperator isDependingOnSubscription={0} then preserves flag")
        @MethodSource("booleanFlags")
        void whenPureOperator_thenReturnsPureBooleanTypeCheckWithPreservedFlag(boolean dependsOnSub) {
            val pureOp = new TestPureOperator(ctx -> Value.TRUE, dependsOnSub);
            val result = applyBooleanGuard(pureOp, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(result).isInstanceOf(PureBooleanTypeCheck.class);
            assertThat(((PureBooleanTypeCheck) result).isDependingOnSubscription()).isEqualTo(dependsOnSub);
        }

        static Stream<Boolean> booleanFlags() {
            return Stream.of(true, false);
        }

        @Test
        @DisplayName("when input is StreamOperator then returns StreamBooleanTypeCheck")
        void whenStreamOperator_thenReturnsStreamBooleanTypeCheck() {
            val streamOp = new TestStreamOperator(Value.TRUE);
            val result   = applyBooleanGuard(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(result).isInstanceOf(StreamBooleanTypeCheck.class);
        }
    }

    @Nested
    @DisplayName("PureBooleanTypeCheck")
    class PureBooleanTypeCheckTests {

        @ParameterizedTest(name = "when operator evaluates to {0} then returns it unchanged")
        @MethodSource("io.sapl.compiler.BooleanGuardCompilerTests$ApplyBooleanGuardTests#booleanValues")
        void whenEvaluatesToBoolean_thenReturnsUnchanged(String description, BooleanValue input) {
            val pureOp = new TestPureOperator(ctx -> input);
            val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
            val result = guard.evaluate(emptyEvaluationContext());
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("when operator evaluates to ErrorValue then returns it unchanged")
        void whenEvaluatesToError_thenReturnsError() {
            val error  = Value.error("inner error");
            val pureOp = new TestPureOperator(ctx -> error);
            val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
            val result = guard.evaluate(emptyEvaluationContext());
            assertThat(result).isSameAs(error);
        }

        @ParameterizedTest(name = "when operator evaluates to {0} then returns error")
        @MethodSource("io.sapl.compiler.BooleanGuardCompilerTests$ApplyBooleanGuardTests#nonBooleanValues")
        @DisplayName("when operator evaluates to non-boolean then returns ErrorValue")
        void whenEvaluatesToNonBoolean_thenReturnsError(String description, Value nonBoolean) {
            val pureOp = new TestPureOperator(ctx -> nonBoolean);
            val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
            val result = guard.evaluate(emptyEvaluationContext());
            assertThat(result).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) result).message()).contains(nonBoolean.toString());
        }

        @Test
        @DisplayName("location returns the guard location")
        void locationReturnsGuardLocation() {
            val pureOp = new TestPureOperator(ctx -> Value.TRUE);
            val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
            assertThat(guard.location()).isSameAs(TEST_LOCATION);
        }

        @ParameterizedTest(name = "isDependingOnSubscription returns {0} when configured with {0}")
        @MethodSource("io.sapl.compiler.BooleanGuardCompilerTests$ApplyBooleanGuardTests#booleanFlags")
        void isDependingOnSubscriptionReturnsConfiguredValue(boolean flag) {
            val pureOp = new TestPureOperator(ctx -> Value.TRUE);
            val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, flag, ERROR_TEMPLATE);
            assertThat(guard.isDependingOnSubscription()).isEqualTo(flag);
        }
    }

    @Nested
    @DisplayName("StreamBooleanTypeCheck")
    class StreamBooleanTypeCheckTests {

        @ParameterizedTest(name = "when stream emits {0} then passes through unchanged")
        @MethodSource("io.sapl.compiler.BooleanGuardCompilerTests$ApplyBooleanGuardTests#booleanValues")
        void whenStreamEmitsBoolean_thenPassesThrough(String description, BooleanValue input) {
            val streamOp = new TestStreamOperator(input);
            val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            StepVerifier.create(guard.stream()).assertNext(tv -> assertThat(tv.value()).isEqualTo(input))
                    .verifyComplete();
        }

        @Test
        @DisplayName("when stream emits ErrorValue then passes through")
        void whenStreamEmitsError_thenPassesThrough() {
            val error    = Value.error("stream error");
            val streamOp = new TestStreamOperator(error);
            val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            StepVerifier.create(guard.stream()).assertNext(tv -> assertThat(tv.value()).isSameAs(error))
                    .verifyComplete();
        }

        @ParameterizedTest(name = "when stream emits {0} then maps to error")
        @MethodSource("io.sapl.compiler.BooleanGuardCompilerTests$ApplyBooleanGuardTests#nonBooleanValues")
        @DisplayName("when stream emits non-boolean then maps to ErrorValue")
        void whenStreamEmitsNonBoolean_thenMapsToError(String description, Value nonBoolean) {
            val streamOp = new TestStreamOperator(nonBoolean);
            val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            StepVerifier.create(guard.stream()).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(((ErrorValue) tv.value()).message()).contains(nonBoolean.toString());
            }).verifyComplete();
        }

        @Test
        @DisplayName("when stream emits multiple values then each is type-checked")
        void whenStreamEmitsMultipleValues_thenEachIsTypeChecked() {
            val streamOp = new TestStreamOperator(Value.TRUE, Value.FALSE, Value.of("bad"), Value.TRUE);
            val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            StepVerifier.create(guard.stream()).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.FALSE))
                    .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class))
                    .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.TRUE)).verifyComplete();
        }

        @Test
        @DisplayName("preserves contributing attributes from traced value")
        @SuppressWarnings("unchecked")
        void preservesContributingAttributes() {
            // Use raw type to avoid complex AttributeRecord construction - we only verify
            // list preservation
            List attrs    = List.<String>of("marker-for-test");
            val  traced   = new TracedValue(Value.of("not boolean"), attrs);
            val  streamOp = new TestStreamOperatorWithTraced(traced);
            val  guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            StepVerifier.create(guard.stream()).assertNext(tv -> {
                assertThat(tv.value()).isInstanceOf(ErrorValue.class);
                assertThat(tv.contributingAttributes()).isSameAs(attrs);
            }).verifyComplete();
        }

        @Test
        @DisplayName("location returns the guard location")
        void locationReturnsGuardLocation() {
            val streamOp = new TestStreamOperator(Value.TRUE);
            val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
            assertThat(guard.location()).isSameAs(TEST_LOCATION);
        }
    }

    private record TestStreamOperator(Value... values) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.fromArray(values).map(v -> new TracedValue(v, List.of()));
        }
    }

    private record TestStreamOperatorWithTraced(TracedValue... tracedValues) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.fromArray(tracedValues);
        }
    }
}
