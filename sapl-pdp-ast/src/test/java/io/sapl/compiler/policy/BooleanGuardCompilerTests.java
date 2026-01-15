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

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler.PureBooleanTypeCheck;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler.StreamBooleanTypeCheck;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.compiler.policy.policybody.BooleanGuardCompiler.applyBooleanGuard;
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

    @ParameterizedTest
    @MethodSource("booleanValues")
    @DisplayName("applyBooleanGuard: when input is boolean then returns unchanged")
    void applyBooleanGuard_whenBooleanValue_thenReturnsUnchanged(BooleanValue input) {
        val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("applyBooleanGuard: when input is ErrorValue then returns unchanged")
    void applyBooleanGuard_whenErrorValue_thenReturnsUnchanged() {
        val error  = Value.error("some error");
        val result = applyBooleanGuard(error, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isSameAs(error);
    }

    @ParameterizedTest
    @MethodSource("nonBooleanValues")
    @DisplayName("applyBooleanGuard: when input is non-boolean then returns error")
    void applyBooleanGuard_whenNonBooleanValue_thenReturnsError(Value input) {
        val result = applyBooleanGuard(input, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(input.toString());
    }

    @ParameterizedTest
    @MethodSource("booleanFlags")
    @DisplayName("applyBooleanGuard: when PureOperator then preserves isDependingOnSubscription flag")
    void applyBooleanGuard_whenPureOperator_thenPreservesFlag(boolean dependsOnSub) {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE, dependsOnSub);
        val result = applyBooleanGuard(pureOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(PureBooleanTypeCheck.class);
        assertThat(((PureBooleanTypeCheck) result).isDependingOnSubscription()).isEqualTo(dependsOnSub);
    }

    @Test
    @DisplayName("applyBooleanGuard: when StreamOperator then returns StreamBooleanTypeCheck")
    void applyBooleanGuard_whenStreamOperator_thenReturnsStreamBooleanTypeCheck() {
        val streamOp = new TestStreamOperator(Value.TRUE);
        val result   = applyBooleanGuard(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(result).isInstanceOf(StreamBooleanTypeCheck.class);
    }

    // --- PureBooleanTypeCheck tests ---

    @ParameterizedTest
    @MethodSource("booleanValues")
    @DisplayName("PureBooleanTypeCheck: when evaluates to boolean then returns unchanged")
    void pureBooleanTypeCheck_whenEvaluatesToBoolean_thenReturnsUnchanged(BooleanValue input) {
        val pureOp = new TestPureOperator(ctx -> input);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("PureBooleanTypeCheck: when evaluates to ErrorValue then returns unchanged")
    void pureBooleanTypeCheck_whenEvaluatesToError_thenReturnsError() {
        val error  = Value.error("inner error");
        val pureOp = new TestPureOperator(ctx -> error);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isSameAs(error);
    }

    @ParameterizedTest
    @MethodSource("nonBooleanValues")
    @DisplayName("PureBooleanTypeCheck: when evaluates to non-boolean then returns error")
    void pureBooleanTypeCheck_whenEvaluatesToNonBoolean_thenReturnsError(Value nonBoolean) {
        val pureOp = new TestPureOperator(ctx -> nonBoolean);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
        val result = guard.evaluate(evaluationContext());
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(nonBoolean.toString());
    }

    @Test
    @DisplayName("PureBooleanTypeCheck: location returns guard location")
    void pureBooleanTypeCheck_locationReturnsGuardLocation() {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, false, ERROR_TEMPLATE);
        assertThat(guard.location()).isSameAs(TEST_LOCATION);
    }

    @ParameterizedTest
    @MethodSource("booleanFlags")
    @DisplayName("PureBooleanTypeCheck: isDependingOnSubscription returns configured value")
    void pureBooleanTypeCheck_isDependingOnSubscriptionReturnsConfiguredValue(boolean flag) {
        val pureOp = new TestPureOperator(ctx -> Value.TRUE);
        val guard  = new PureBooleanTypeCheck(pureOp, TEST_LOCATION, flag, ERROR_TEMPLATE);
        assertThat(guard.isDependingOnSubscription()).isEqualTo(flag);
    }

    @ParameterizedTest
    @MethodSource("booleanValues")
    @DisplayName("StreamBooleanTypeCheck: when stream emits boolean then passes through")
    void streamBooleanTypeCheck_whenStreamEmitsBoolean_thenPassesThrough(BooleanValue input) {
        val streamOp = new TestStreamOperator(input);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        verifyStreamEmits(guard, evaluationContext(), input);
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: when stream emits ErrorValue then passes through")
    void streamBooleanTypeCheck_whenStreamEmitsError_thenPassesThrough() {
        val error    = Value.error("stream error");
        val streamOp = new TestStreamOperator(error);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        verifyStream(guard, evaluationContext(), tv -> assertThat(tv.value()).isSameAs(error));
    }

    @ParameterizedTest
    @MethodSource("nonBooleanValues")
    @DisplayName("StreamBooleanTypeCheck: when stream emits non-boolean then maps to error")
    void streamBooleanTypeCheck_whenStreamEmitsNonBoolean_thenMapsToError(Value nonBoolean) {
        val streamOp = new TestStreamOperator(nonBoolean);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        verifyStream(guard, evaluationContext(), tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) tv.value()).message()).contains(nonBoolean.toString());
        });
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: when stream emits multiple values then each is type-checked")
    void streamBooleanTypeCheck_whenStreamEmitsMultipleValues_thenEachIsTypeChecked() {
        val streamOp = new TestStreamOperator(Value.TRUE, Value.FALSE, Value.of("bad"), Value.TRUE);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        verifyStream(guard, evaluationContext(), tv -> assertThat(tv.value()).isEqualTo(Value.TRUE),
                tv -> assertThat(tv.value()).isEqualTo(Value.FALSE),
                tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class),
                tv -> assertThat(tv.value()).isEqualTo(Value.TRUE));
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: preserves contributing attributes from traced value")
    void streamBooleanTypeCheck_preservesContributingAttributes() {
        val attrs    = List.<AttributeRecord>of();
        val traced   = new TracedValue(Value.of("not boolean"), attrs);
        val streamOp = new TestStreamOperatorWithTraced(traced);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        verifyStream(guard, evaluationContext(), tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class);
            assertThat(tv.contributingAttributes()).isSameAs(attrs);
        });
    }

    @Test
    @DisplayName("StreamBooleanTypeCheck: location returns guard location")
    void streamBooleanTypeCheck_locationReturnsGuardLocation() {
        val streamOp = new TestStreamOperator(Value.TRUE);
        val guard    = new StreamBooleanTypeCheck(streamOp, TEST_LOCATION, ERROR_TEMPLATE);
        assertThat(guard.location()).isSameAs(TEST_LOCATION);
    }

}
