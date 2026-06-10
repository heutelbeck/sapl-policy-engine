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
package io.sapl.compiler.policy;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.policy.BooleanGuardCompiler.PureBooleanTypeCheck;
import io.sapl.compiler.policy.BooleanGuardCompiler.StreamBooleanTypeCheck;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.compiler.policy.BooleanGuardCompiler.applyBooleanGuard;
import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BooleanGuardCompiler")
class BooleanGuardCompilerTests {

    private static final String ERROR_TEMPLATE = "Expected BOOLEAN but got: %s";

    static Stream<BooleanValue> booleanValues() {
        return Stream.of(Value.TRUE, Value.FALSE);
    }

    static Stream<Value> nonBooleanValues() {
        return Stream.of(Value.of(42), Value.of("hello"), Value.NULL, Value.UNDEFINED, array(Value.of(1), Value.of(2)),
                obj("key", Value.of("value")));
    }

    static Stream<Boolean> booleanFlags() {
        return Stream.of(true, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanValues")
    @DisplayName("applyBooleanGuard: when input is boolean then returns unchanged")
    void applyBooleanGuardWhenBooleanValueThenReturnsUnchanged(BooleanValue input) {
        val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("applyBooleanGuard: when input is ErrorValue then returns unchanged")
    void applyBooleanGuardWhenErrorValueThenReturnsUnchanged() {
        val error  = Value.error("some errors");
        val result = applyBooleanGuard(error, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isSameAs(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBooleanValues")
    @DisplayName("applyBooleanGuard: when input is non-boolean then returns errors")
    void applyBooleanGuardWhenNonBooleanValueThenReturnsError(Value input) {
        val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(input.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanFlags")
    @DisplayName("applyBooleanGuard: when PureOperator then preserves isDependingOnSubscription flag")
    void applyBooleanGuardWhenPureOperatorThenPreservesFlag(boolean dependsOnSub) {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE, dependsOnSub);
        val result = applyBooleanGuard(pureOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(PureBooleanTypeCheck.class);
        assertThat(((PureBooleanTypeCheck) result).isDependingOnSubscription()).isEqualTo(dependsOnSub);
    }

    @Test
    @DisplayName("applyBooleanGuard: when StreamOperator then returns StreamBooleanTypeCheck")
    void applyBooleanGuardWhenStreamOperatorThenReturnsStreamBooleanTypeCheck() {
        val streamOp = new TestStreamOperator(Value.TRUE);
        val result   = applyBooleanGuard(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(StreamBooleanTypeCheck.class);
    }

    // PureBooleanTypeCheck tests

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanValues")
    @DisplayName("PureBooleanTypeCheck: when evaluates to boolean then returns unchanged")
    void pureBooleanTypeCheckWhenEvaluatesToBooleanThenReturnsUnchanged(BooleanValue input) {
        val pureOp = new TestPureOperator(ctx -> input);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("PureBooleanTypeCheck: when evaluates to ErrorValue then returns unchanged")
    void pureBooleanTypeCheckWhenEvaluatesToErrorThenReturnsError() {
        val error  = Value.error("inner errors");
        val pureOp = new TestPureOperator(ctx -> error);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isSameAs(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBooleanValues")
    @DisplayName("PureBooleanTypeCheck: when evaluates to non-boolean then returns errors")
    void pureBooleanTypeCheckWhenEvaluatesToNonBooleanThenReturnsError(Value nonBoolean) {
        val pureOp = new TestPureOperator(ctx -> nonBoolean);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(nonBoolean.toString());
    }

    @Test
    @DisplayName("PureBooleanTypeCheck: location returns guard location")
    void pureBooleanTypeCheckLocationReturnsGuardLocation() {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, false, ERROR_TEMPLATE);
        assertThat(guard.location()).isSameAs(TEST_LOCATION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanFlags")
    @DisplayName("PureBooleanTypeCheck: isDependingOnSubscription returns configured value")
    void pureBooleanTypeCheckIsDependingOnSubscriptionReturnsConfiguredValue(boolean flag) {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, flag, false, ERROR_TEMPLATE);
        assertThat(guard.isDependingOnSubscription()).isEqualTo(flag);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booleanValues")
    @DisplayName("StreamBooleanTypeCheck: when stream emits boolean then passes through")
    void streamBooleanTypeCheckWhenStreamEmitsBooleanThenPassesThrough(BooleanValue input) {
        val streamOp = new TestStreamOperator(input);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(guard.evaluate(evaluationContext()).result()).isEqualTo(input);
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: when stream emits ErrorValue then passes through")
    void streamBooleanTypeCheckWhenStreamEmitsErrorThenPassesThrough() {
        val error    = Value.error("stream errors");
        val streamOp = new TestStreamOperator(error);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(guard.evaluate(evaluationContext()).result()).isSameAs(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonBooleanValues")
    @DisplayName("StreamBooleanTypeCheck: when stream emits non-boolean then maps to errors")
    void streamBooleanTypeCheckWhenStreamEmitsNonBooleanThenMapsToError(Value nonBoolean) {
        val streamOp = new TestStreamOperator(nonBoolean);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        val result   = guard.evaluate(evaluationContext()).result();
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(nonBoolean.toString());
    }

    @ParameterizedTest(name = "input={0} expectError={1}")
    @MethodSource("typeCheckCases")
    @DisplayName("StreamBooleanTypeCheck: each input is type-checked independently")
    void streamBooleanTypeCheckEachInputTypeChecked(Value input, boolean expectError) {
        val streamOp = new TestStreamOperator(input);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        val result   = guard.evaluate(evaluationContext()).result();
        if (expectError) {
            assertThat(result).isInstanceOf(ErrorValue.class);
        } else {
            assertThat(result).isEqualTo(input);
        }
    }

    static Stream<Arguments> typeCheckCases() {
        return Stream.of(Arguments.of(Value.TRUE, false), Arguments.of(Value.FALSE, false),
                Arguments.of(Value.of("bad"), true), Arguments.of(Value.TRUE, false));
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: preserves child dependencies through type-check")
    void streamBooleanTypeCheckPreservesDependencies() {
        val streamOp = new TestStreamOperator(Value.of("not boolean"));
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        val out      = guard.evaluate(evaluationContext());
        assertThat(out.result()).isInstanceOf(ErrorValue.class);
        assertThat(out.dependencies()).isEmpty();
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: location returns guard location")
    void streamBooleanTypeCheckLocationReturnsGuardLocation() {
        val streamOp = new TestStreamOperator(Value.TRUE);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(guard.location()).isSameAs(TEST_LOCATION);
    }

}
