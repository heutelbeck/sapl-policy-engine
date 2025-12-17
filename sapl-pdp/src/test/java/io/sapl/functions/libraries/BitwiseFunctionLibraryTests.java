/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.libraries;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive test suite for BitwiseFunctionLibrary. Tests are primarily
 * parameterized to ensure comprehensive
 * coverage of edge cases and maintain consistency across similar operations.
 */
class BitwiseFunctionLibraryTests {
    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(BitwiseFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    /* Core Operations Tests */

    @ParameterizedTest
    @MethodSource("provideBitwiseAndTestCases")
    void when_bitwiseAnd_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseAnd(Value.of(left), Value.of(right));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideBitwiseAndTestCases() {
        return Stream.of(arguments(12L, 10L, 8L), arguments(255L, 15L, 15L), arguments(-1L, 42L, 42L),
                arguments(0L, 999L, 0L), arguments(-8L, 15L, 8L), arguments(-1L, -1L, -1L),
                arguments(Long.MAX_VALUE, 1L, 1L), arguments(Long.MIN_VALUE, -1L, Long.MIN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseOrTestCases")
    void when_bitwiseOr_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseOr(Value.of(left), Value.of(right));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideBitwiseOrTestCases() {
        return Stream.of(arguments(12L, 10L, 14L), arguments(8L, 4L, 12L), arguments(0L, 42L, 42L),
                arguments(-1L, 42L, -1L), arguments(1L, 2L, 3L), arguments(255L, 256L, 511L), arguments(-8L, 7L, -1L));
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseXorTestCases")
    void when_bitwiseXor_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseXor(Value.of(left), Value.of(right));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideBitwiseXorTestCases() {
        return Stream.of(arguments(12L, 10L, 6L), arguments(255L, 255L, 0L), arguments(42L, 0L, 42L),
                arguments(-1L, -1L, 0L), arguments(15L, 8L, 7L), arguments(-8L, 7L, -1L));
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseNotTestCases")
    void when_bitwiseNot_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseNot(Value.of(value));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideBitwiseNotTestCases() {
        return Stream.of(arguments(0L, -1L), arguments(-1L, 0L), arguments(42L, -43L), arguments(-43L, 42L),
                arguments(1L, -2L), arguments(Long.MAX_VALUE, Long.MIN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("provideLeftShiftTestCases")
    void when_leftShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.leftShift(Value.of(value), Value.of(positions));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideLeftShiftTestCases() {
        return Stream.of(arguments(1L, 3L, 8L), arguments(5L, 2L, 20L), arguments(1L, 10L, 1024L),
                arguments(42L, 0L, 42L), arguments(1L, 63L, Long.MIN_VALUE), arguments(0L, 10L, 0L));
    }

    @ParameterizedTest
    @MethodSource("provideRightShiftTestCases")
    void when_rightShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rightShift(Value.of(value), Value.of(positions));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideRightShiftTestCases() {
        return Stream.of(arguments(16L, 2L, 4L), arguments(-8L, 1L, -4L), arguments(1024L, 10L, 1L),
                arguments(42L, 0L, 42L), arguments(-1L, 5L, -1L), arguments(0L, 10L, 0L));
    }

    @ParameterizedTest
    @MethodSource("provideUnsignedRightShiftTestCases")
    void when_unsignedRightShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.unsignedRightShift(Value.of(value), Value.of(positions));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideUnsignedRightShiftTestCases() {
        return Stream.of(arguments(16L, 2L, 4L), arguments(-8L, 1L, 9223372036854775804L),
                arguments(-1L, 1L, 9223372036854775807L), arguments(42L, 0L, 42L), arguments(0L, 10L, 0L),
                arguments(Long.MIN_VALUE, 1L, 4611686018427387904L));
    }

    @ParameterizedTest
    @MethodSource("provideShiftInvalidPositionTestCases")
    void when_shiftWithInvalidPosition_then_returnsError(long value, long position, String expectedContext) {
        val leftShiftResult = BitwiseFunctionLibrary.leftShift(Value.of(value), Value.of(position));

        assertThat(leftShiftResult).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) leftShiftResult).message()).contains(expectedContext);
    }

    private static Stream<Arguments> provideShiftInvalidPositionTestCases() {
        return Stream.of(arguments(42L, -1L, "Shift"), arguments(42L, 64L, "Shift"), arguments(42L, 100L, "Shift"));
    }

    @ParameterizedTest
    @MethodSource("provideTestBitTestCases")
    void when_testBit_then_returnsCorrectResult(long value, long position, boolean expected) {
        val actual = BitwiseFunctionLibrary.testBit(Value.of(value), Value.of(position));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideTestBitTestCases() {
        return Stream.of(arguments(0L, 0L, false), arguments(1L, 0L, true), arguments(2L, 1L, true),
                arguments(4L, 2L, true), arguments(5L, 0L, true), arguments(5L, 1L, false), arguments(5L, 2L, true),
                arguments(255L, 7L, true), arguments(255L, 8L, false), arguments(-1L, 63L, true));
    }

    @ParameterizedTest
    @MethodSource("provideBitInvalidPositionTestCases")
    void when_bitOperationWithInvalidPosition_then_returnsError(long value, long position) {
        val testResult  = BitwiseFunctionLibrary.testBit(Value.of(value), Value.of(position));
        val setResult   = BitwiseFunctionLibrary.setBit(Value.of(value), Value.of(position));
        val clearResult = BitwiseFunctionLibrary.clearBit(Value.of(value), Value.of(position));

        assertThat(testResult).isInstanceOf(ErrorValue.class);
        assertThat(setResult).isInstanceOf(ErrorValue.class);
        assertThat(clearResult).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) testResult).message()).contains("Bit");
    }

    private static Stream<Arguments> provideBitInvalidPositionTestCases() {
        return Stream.of(arguments(42L, -1L), arguments(42L, 64L), arguments(42L, 100L));
    }

    @ParameterizedTest
    @MethodSource("provideSetBitTestCases")
    void when_setBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.setBit(Value.of(value), Value.of(position));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideSetBitTestCases() {
        return Stream.of(arguments(0L, 0L, 1L), arguments(0L, 3L, 8L), arguments(1L, 1L, 3L), arguments(5L, 1L, 7L),
                arguments(-1L, 5L, -1L));
    }

    @ParameterizedTest
    @MethodSource("provideClearBitTestCases")
    void when_clearBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.clearBit(Value.of(value), Value.of(position));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideClearBitTestCases() {
        return Stream.of(arguments(1L, 0L, 0L), arguments(3L, 1L, 1L), arguments(7L, 1L, 5L), arguments(255L, 7L, 127L),
                arguments(0L, 5L, 0L));
    }

    @ParameterizedTest
    @MethodSource("provideToggleBitTestCases")
    void when_toggleBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.toggleBit(Value.of(value), Value.of(position));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideToggleBitTestCases() {
        return Stream.of(arguments(0L, 0L, 1L), arguments(1L, 0L, 0L), arguments(5L, 1L, 7L), arguments(7L, 1L, 5L),
                arguments(0L, 3L, 8L));
    }

    @ParameterizedTest
    @MethodSource("provideBitCountTestCases")
    void when_bitCount_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.bitCount(Value.of(value));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideBitCountTestCases() {
        return Stream.of(arguments(0L, 0L), arguments(1L, 1L), arguments(3L, 2L), arguments(7L, 3L),
                arguments(255L, 8L), arguments(-1L, 64L), arguments(Long.MAX_VALUE, 63L),
                arguments(Long.MIN_VALUE, 1L));
    }

    @ParameterizedTest
    @MethodSource("provideRotateLeftTestCases")
    void when_rotateLeft_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rotateLeft(Value.of(value), Value.of(positions));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideRotateLeftTestCases() {
        return Stream.of(arguments(1L, 3L, 8L), arguments(8L, 3L, 64L), arguments(42L, 0L, 42L),
                arguments(0x8000000000000001L, 1L, 3L), arguments(-1L, 10L, -1L));
    }

    @ParameterizedTest
    @MethodSource("provideRotateRightTestCases")
    void when_rotateRight_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rotateRight(Value.of(value), Value.of(positions));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideRotateRightTestCases() {
        return Stream.of(arguments(8L, 3L, 1L), arguments(64L, 3L, 8L), arguments(42L, 0L, 42L),
                arguments(3L, 1L, 0x8000000000000001L), arguments(-1L, 10L, -1L));
    }

    @ParameterizedTest
    @MethodSource("provideLeadingZerosTestCases")
    void when_leadingZeros_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.leadingZeros(Value.of(value));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideLeadingZerosTestCases() {
        return Stream.of(arguments(0L, 64L), arguments(1L, 63L), arguments(2L, 62L), arguments(8L, 60L),
                arguments(255L, 56L), arguments(-1L, 0L), arguments(Long.MAX_VALUE, 1L), arguments(Long.MIN_VALUE, 0L));
    }

    @ParameterizedTest
    @MethodSource("provideTrailingZerosTestCases")
    void when_trailingZeros_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.trailingZeros(Value.of(value));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideTrailingZerosTestCases() {
        return Stream.of(arguments(0L, 64L), arguments(1L, 0L), arguments(2L, 1L), arguments(8L, 3L),
                arguments(16L, 4L), arguments(-1L, 0L), arguments(Long.MIN_VALUE, 63L));
    }

    @ParameterizedTest
    @MethodSource("provideReverseBitsTestCases")
    void when_reverseBits_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.reverseBits(Value.of(value));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideReverseBitsTestCases() {
        return Stream.of(arguments(1L, Long.MIN_VALUE), arguments(0L, 0L), arguments(Long.MIN_VALUE, 1L),
                arguments(-1L, -1L));
    }

    @Test
    void when_reverseBits_then_doubleReverseReturnsOriginal() {
        val original       = 12345678L;
        val reversed       = BitwiseFunctionLibrary.reverseBits(Value.of(original));
        val doubleReversed = BitwiseFunctionLibrary.reverseBits((NumberValue) reversed);

        assertThat(doubleReversed).isInstanceOf(NumberValue.class).isEqualTo(Value.of(original));
    }

    @ParameterizedTest
    @MethodSource("provideIsPowerOfTwoTestCases")
    void when_isPowerOfTwo_then_returnsCorrectResult(long value, boolean expected) {
        val actual = BitwiseFunctionLibrary.isPowerOfTwo(Value.of(value));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideIsPowerOfTwoTestCases() {
        return Stream.of(arguments(1L, true), arguments(2L, true), arguments(4L, true), arguments(8L, true),
                arguments(1024L, true), arguments(0L, false), arguments(3L, false), arguments(7L, false),
                arguments(-8L, false), arguments(-1L, false), arguments(Long.MAX_VALUE, false),
                arguments(0x4000000000000000L, true));
    }

    /* Edge Cases and Integration Tests */

    @Test
    void when_combinedOperations_then_producesCorrectResult() {
        val a = Value.of(12L);
        val b = Value.of(10L);
        val c = Value.of(4L);

        val orResult    = BitwiseFunctionLibrary.bitwiseOr(a, b);
        val notResult   = BitwiseFunctionLibrary.bitwiseNot(c);
        val finalResult = BitwiseFunctionLibrary.bitwiseAnd((NumberValue) orResult, (NumberValue) notResult);

        assertThat(finalResult).isInstanceOf(NumberValue.class).isEqualTo(Value.of(10L));
    }

    @Test
    void when_shiftAndRotate_then_produceDifferentResults() {
        val value     = Value.of(0x8000000000000001L);
        val positions = Value.of(1L);

        val leftShift  = BitwiseFunctionLibrary.leftShift(value, positions);
        val rotateLeft = BitwiseFunctionLibrary.rotateLeft(value, positions);

        assertThat(leftShift).isInstanceOf(NumberValue.class).isEqualTo(Value.of(2L));
        assertThat(rotateLeft).isInstanceOf(NumberValue.class).isEqualTo(Value.of(3L));
    }

    @Test
    void when_signedVsUnsignedRightShift_then_produceDifferentResults() {
        val value     = Value.of(-8L);
        val positions = Value.of(1L);

        val signedShift   = BitwiseFunctionLibrary.rightShift(value, positions);
        val unsignedShift = BitwiseFunctionLibrary.unsignedRightShift(value, positions);

        assertThat(signedShift).isInstanceOf(NumberValue.class).isEqualTo(Value.of(-4L));
        assertThat(unsignedShift).isInstanceOf(NumberValue.class).isEqualTo(Value.of(9223372036854775804L));
    }

    @Test
    void when_bitManipulation_then_maintainsConsistency() {
        val original = Value.of(42L);
        val position = Value.of(6L);

        val withBitSet = BitwiseFunctionLibrary.setBit(original, position);
        val isBitSet   = BitwiseFunctionLibrary.testBit((NumberValue) withBitSet, position);

        assertThat(isBitSet).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);

        val withBitCleared = BitwiseFunctionLibrary.clearBit((NumberValue) withBitSet, position);
        val isBitCleared   = BitwiseFunctionLibrary.testBit((NumberValue) withBitCleared, position);

        assertThat(isBitCleared).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
        assertThat(withBitCleared).isInstanceOf(NumberValue.class).isEqualTo(Value.of(42L));
    }

    /* Identity and Property Tests */

    @ParameterizedTest
    @MethodSource("provideBitwiseIdentityProperties")
    void when_bitwiseOperation_then_satisfiesIdentityProperty(String propertyName,
            BiFunction<Value, Value, Value> operation, long testValue, long identityValue, long expectedResult) {

        val result = operation.apply(Value.of(testValue), Value.of(identityValue));

        assertThat(result).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expectedResult));
    }

    private static Stream<Arguments> provideBitwiseIdentityProperties() {
        val testValue = 987654321L;
        return Stream.of(
                arguments("XOR with self returns zero",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseXor, testValue,
                        testValue, 0L),
                arguments("AND with -1 returns original",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseAnd, testValue,
                        -1L, testValue),
                arguments("OR with 0 returns original",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseOr, testValue, 0L,
                        testValue),
                arguments("XOR with 0 returns original",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseXor, testValue, 0L,
                        testValue),
                arguments("AND with 0 returns zero",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseAnd, testValue, 0L,
                        0L),
                arguments("OR with -1 returns -1",
                        (BiFunction<NumberValue, NumberValue, Value>) BitwiseFunctionLibrary::bitwiseOr, testValue, -1L,
                        -1L));
    }

    @Test
    void when_bitCountWithPowerOfTwo_then_returnsOne() {
        val value = Value.of(256L);
        val count = BitwiseFunctionLibrary.bitCount(value);

        assertThat(count).isInstanceOf(NumberValue.class).isEqualTo(Value.of(1L));

        val isPower = BitwiseFunctionLibrary.isPowerOfTwo(value);

        assertThat(isPower).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }
}
