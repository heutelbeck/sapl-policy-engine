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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for BitwiseFunctionLibrary.
 * Tests are primarily parameterized to ensure comprehensive coverage
 * of edge cases and maintain consistency across similar operations.
 */
class BitwiseFunctionLibraryTests {

    /* Core Operations Tests */

    @ParameterizedTest
    @MethodSource("provideBitwiseAndTestCases")
    void when_bitwiseAnd_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseAnd(Val.of(left), Val.of(right));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBitwiseAndTestCases() {
        return Stream.of(Arguments.of(12L, 10L, 8L),                     // 1100 & 1010 = 1000
                Arguments.of(255L, 15L, 15L),                   // 0xFF & 0x0F = 0x0F
                Arguments.of(-1L, 42L, 42L),                    // all bits set & value = value
                Arguments.of(0L, 999L, 0L),                     // 0 & anything = 0
                Arguments.of(-8L, 15L, 8L),                     // negative & positive
                Arguments.of(-1L, -1L, -1L),                    // all bits & all bits = all bits
                Arguments.of(java.lang.Long.MAX_VALUE, 1L, 1L), // max long value
                Arguments.of(java.lang.Long.MIN_VALUE, -1L, java.lang.Long.MIN_VALUE) // min long value
        );
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseOrTestCases")
    void when_bitwiseOr_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseOr(Val.of(left), Val.of(right));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBitwiseOrTestCases() {
        return Stream.of(Arguments.of(12L, 10L, 14L),            // 1100 | 1010 = 1110
                Arguments.of(8L, 4L, 12L),              // 1000 | 0100 = 1100
                Arguments.of(0L, 42L, 42L),             // 0 | value = value
                Arguments.of(-1L, 42L, -1L),            // all bits | anything = all bits
                Arguments.of(1L, 2L, 3L),               // non-overlapping bits
                Arguments.of(255L, 256L, 511L),         // adjacent bit ranges
                Arguments.of(-8L, 7L, -1L)              // negative | positive
        );
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseXorTestCases")
    void when_bitwiseXor_then_returnsCorrectResult(long left, long right, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseXor(Val.of(left), Val.of(right));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBitwiseXorTestCases() {
        return Stream.of(Arguments.of(12L, 10L, 6L),             // 1100 ^ 1010 = 0110
                Arguments.of(255L, 255L, 0L),           // same value ^ same value = 0
                Arguments.of(42L, 0L, 42L),             // value ^ 0 = value
                Arguments.of(-1L, -1L, 0L),             // all bits ^ all bits = 0
                Arguments.of(15L, 8L, 7L),              // 1111 ^ 1000 = 0111
                Arguments.of(-8L, 7L, -1L)              // negative ^ positive
        );
    }

    @ParameterizedTest
    @MethodSource("provideBitwiseNotTestCases")
    void when_bitwiseNot_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.bitwiseNot(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBitwiseNotTestCases() {
        return Stream.of(Arguments.of(0L, -1L),                  // ~0 = all bits set
                Arguments.of(-1L, 0L),                  // ~all bits = 0
                Arguments.of(42L, -43L),                // ~42 = -(42+1)
                Arguments.of(-43L, 42L),                // double negation relationship
                Arguments.of(1L, -2L),                  // ~1 = -2
                Arguments.of(java.lang.Long.MAX_VALUE, java.lang.Long.MIN_VALUE) // ~max = min
        );
    }

    @ParameterizedTest
    @MethodSource("provideLeftShiftTestCases")
    void when_leftShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.leftShift(Val.of(value), Val.of(positions));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideLeftShiftTestCases() {
        return Stream.of(Arguments.of(1L, 3L, 8L),               // 0001 << 3 = 1000
                Arguments.of(5L, 2L, 20L),              // 0101 << 2 = 10100
                Arguments.of(1L, 10L, 1024L),           // 2^10
                Arguments.of(42L, 0L, 42L),             // shift by 0 = unchanged
                Arguments.of(1L, 63L, Long.MIN_VALUE),  // shift to sign bit
                Arguments.of(0L, 10L, 0L)               // shifting 0
        );
    }

    @ParameterizedTest
    @MethodSource("provideRightShiftTestCases")
    void when_rightShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rightShift(Val.of(value), Val.of(positions));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideRightShiftTestCases() {
        return Stream.of(Arguments.of(16L, 2L, 4L),              // 16 >> 2 = 4
                Arguments.of(-8L, 1L, -4L),             // sign-extended (arithmetic shift)
                Arguments.of(1024L, 10L, 1L),           // 1024 >> 10 = 1
                Arguments.of(42L, 0L, 42L),             // shift by 0 = unchanged
                Arguments.of(-1L, 5L, -1L),             // all bits set remains all bits set
                Arguments.of(0L, 10L, 0L)               // shifting 0
        );
    }

    @ParameterizedTest
    @MethodSource("provideUnsignedRightShiftTestCases")
    void when_unsignedRightShift_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.unsignedRightShift(Val.of(value), Val.of(positions));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideUnsignedRightShiftTestCases() {
        return Stream.of(Arguments.of(16L, 2L, 4L),                          // positive value
                Arguments.of(-8L, 1L, 9223372036854775804L),        // zero-fill, not sign-extend
                Arguments.of(-1L, 1L, 9223372036854775807L),        // largest positive long
                Arguments.of(42L, 0L, 42L),                         // shift by 0 = unchanged
                Arguments.of(0L, 10L, 0L),                          // shifting 0
                Arguments.of(Long.MIN_VALUE, 1L, 4611686018427387904L) // high bit moved
        );
    }

    @ParameterizedTest
    @MethodSource("providePositionValidatingFunctions")
    void when_functionWithInvalidPosition_then_returnsError(String functionName, BiFunction<Val, Val, Val> function,
            String expectedErrorPrefix) {

        val resultNegative = function.apply(Val.of(42L), Val.of(-1L));
        assertThatVal(resultNegative).isError();
        assertThat(resultNegative.getMessage()).contains(expectedErrorPrefix + " position must be between 0 and 63");

        val resultTooLarge = function.apply(Val.of(42L), Val.of(64L));
        assertThatVal(resultTooLarge).isError();
        assertThat(resultTooLarge.getMessage()).contains(expectedErrorPrefix + " position must be between 0 and 63");
    }

    private static Stream<Arguments> providePositionValidatingFunctions() {
        return Stream.of(
                Arguments.of("leftShift", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::leftShift, "Shift"),
                Arguments.of("rightShift", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::rightShift, "Shift"),
                Arguments.of("unsignedRightShift",
                        (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::unsignedRightShift, "Shift"),
                Arguments.of("testBit", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::testBit, "Bit"),
                Arguments.of("setBit", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::setBit, "Bit"),
                Arguments.of("clearBit", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::clearBit, "Bit"),
                Arguments.of("toggleBit", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::toggleBit, "Bit"),
                Arguments.of("rotateLeft", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::rotateLeft, "Shift"),
                Arguments.of("rotateRight", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::rotateRight, "Shift"));
    }

    /* Bit Manipulation Tests */

    @ParameterizedTest
    @MethodSource("provideTestBitTestCases")
    void when_testBit_then_returnsCorrectResult(long value, long position, boolean expected) {
        val actual = BitwiseFunctionLibrary.testBit(Val.of(value), Val.of(position));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideTestBitTestCases() {
        return Stream.of(Arguments.of(8L, 3L, true),             // 1000 has bit 3 set
                Arguments.of(8L, 2L, false),            // 1000 does not have bit 2 set
                Arguments.of(5L, 0L, true),             // 0101 has bit 0 set
                Arguments.of(5L, 2L, true),             // 0101 has bit 2 set
                Arguments.of(0L, 0L, false),            // no bits set
                Arguments.of(-1L, 63L, true),           // all bits set, including sign bit
                Arguments.of(-1L, 0L, true),            // all bits set
                Arguments.of(Long.MAX_VALUE, 63L, false) // sign bit not set in max value
        );
    }

    @ParameterizedTest
    @MethodSource("provideSetBitTestCases")
    void when_setBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.setBit(Val.of(value), Val.of(position));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideSetBitTestCases() {
        return Stream.of(Arguments.of(8L, 2L, 12L),              // 1000 -> 1100
                Arguments.of(0L, 3L, 8L),               // 0000 -> 1000
                Arguments.of(5L, 1L, 7L),               // 0101 -> 0111
                Arguments.of(7L, 2L, 7L),               // bit already set
                Arguments.of(0L, 0L, 1L),               // set LSB
                Arguments.of(0L, 63L, Long.MIN_VALUE)   // set sign bit
        );
    }

    @ParameterizedTest
    @MethodSource("provideClearBitTestCases")
    void when_clearBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.clearBit(Val.of(value), Val.of(position));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideClearBitTestCases() {
        return Stream.of(Arguments.of(15L, 2L, 11L),             // 1111 -> 1011
                Arguments.of(8L, 3L, 0L),               // 1000 -> 0000
                Arguments.of(7L, 1L, 5L),               // 0111 -> 0101
                Arguments.of(5L, 1L, 5L),               // bit already clear
                Arguments.of(-1L, 0L, -2L),             // clear LSB from all bits set
                Arguments.of(Long.MIN_VALUE, 63L, 0L)   // clear sign bit
        );
    }

    @ParameterizedTest
    @MethodSource("provideToggleBitTestCases")
    void when_toggleBit_then_returnsCorrectResult(long value, long position, long expected) {
        val actual = BitwiseFunctionLibrary.toggleBit(Val.of(value), Val.of(position));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideToggleBitTestCases() {
        return Stream.of(Arguments.of(8L, 3L, 0L),               // 1000 -> 0000
                Arguments.of(8L, 2L, 12L),              // 1000 -> 1100
                Arguments.of(5L, 1L, 7L),               // 0101 -> 0111
                Arguments.of(7L, 1L, 5L),               // 0111 -> 0101
                Arguments.of(0L, 0L, 1L),               // toggle LSB
                Arguments.of(Long.MAX_VALUE, 63L, -1L)  // toggle sign bit
        );
    }

    @ParameterizedTest
    @MethodSource("provideBitCountTestCases")
    void when_bitCount_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.bitCount(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBitCountTestCases() {
        return Stream.of(Arguments.of(0L, 0L),                   // no bits set
                Arguments.of(7L, 3L),                   // 0111 has 3 bits set
                Arguments.of(15L, 4L),                  // 1111 has 4 bits set
                Arguments.of(-1L, 64L),                 // all bits set in two's complement
                Arguments.of(1L, 1L),                   // single bit
                Arguments.of(255L, 8L),                 // 8 bits set
                Arguments.of(Long.MAX_VALUE, 63L),      // all bits except sign bit
                Arguments.of(Long.MIN_VALUE, 1L)        // only sign bit
        );
    }

    /* Utility Function Tests */

    @ParameterizedTest
    @MethodSource("provideRotateLeftTestCases")
    void when_rotateLeft_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rotateLeft(Val.of(value), Val.of(positions));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideRotateLeftTestCases() {
        return Stream.of(Arguments.of(1L, 3L, 8L),                               // simple rotation
                Arguments.of(0x8000000000000001L, 1L, 3L),              // high bit rotates to low
                Arguments.of(42L, 0L, 42L),                             // rotate by 0
                Arguments.of(0xFFL, 8L, 0xFF00L)                        // byte rotation
        );
    }

    @ParameterizedTest
    @MethodSource("provideRotateRightTestCases")
    void when_rotateRight_then_returnsCorrectResult(long value, long positions, long expected) {
        val actual = BitwiseFunctionLibrary.rotateRight(Val.of(value), Val.of(positions));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideRotateRightTestCases() {
        return Stream.of(Arguments.of(8L, 3L, 1L),                               // simple rotation
                Arguments.of(3L, 1L, 0x8000000000000001L),              // low bits rotate to high
                Arguments.of(42L, 0L, 42L),                             // rotate by 0
                Arguments.of(0xFF00L, 8L, 0xFFL)                        // byte rotation
        );
    }

    @ParameterizedTest
    @MethodSource("provideLeadingZerosTestCases")
    void when_leadingZeros_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.leadingZeros(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideLeadingZerosTestCases() {
        return Stream.of(Arguments.of(0L, 64L),                  // all bits are zero
                Arguments.of(1L, 63L),                  // only bit 0 is set
                Arguments.of(8L, 60L),                  // bit 3 is highest set bit
                Arguments.of(-1L, 0L),                  // sign bit is set
                Arguments.of(255L, 56L),                // 8-bit value
                Arguments.of(Long.MAX_VALUE, 1L),       // sign bit not set
                Arguments.of(Long.MIN_VALUE, 0L)        // only sign bit set
        );
    }

    @ParameterizedTest
    @MethodSource("provideTrailingZerosTestCases")
    void when_trailingZeros_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.trailingZeros(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideTrailingZerosTestCases() {
        return Stream.of(Arguments.of(0L, 64L),                  // all bits are zero
                Arguments.of(1L, 0L),                   // bit 0 is set
                Arguments.of(8L, 3L),                   // bits 0-2 are trailing zeros
                Arguments.of(12L, 2L),                  // 1100 has 2 trailing zeros
                Arguments.of(256L, 8L),                 // 8 trailing zeros
                Arguments.of(-1L, 0L),                  // all bits set
                Arguments.of(Long.MIN_VALUE, 63L)       // only sign bit set
        );
    }

    @ParameterizedTest
    @MethodSource("provideReverseBitsTestCases")
    void when_reverseBits_then_returnsCorrectResult(long value, long expected) {
        val actual = BitwiseFunctionLibrary.reverseBits(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideReverseBitsTestCases() {
        return Stream.of(Arguments.of(1L, Long.MIN_VALUE),       // bit 0 -> bit 63
                Arguments.of(0L, 0L),                   // all zeros stay zeros
                Arguments.of(Long.MIN_VALUE, 1L),       // bit 63 -> bit 0
                Arguments.of(-1L, -1L)                  // all ones stay all ones
        );
    }

    @Test
    void when_reverseBits_then_doubleReverseReturnsOriginal() {
        val original       = 12345678L;
        val reversed       = BitwiseFunctionLibrary.reverseBits(Val.of(original));
        val doubleReversed = BitwiseFunctionLibrary.reverseBits(reversed);

        assertThatVal(doubleReversed).hasValue();
        assertThat(doubleReversed.get().longValue()).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("provideIsPowerOfTwoTestCases")
    void when_isPowerOfTwo_then_returnsCorrectResult(long value, boolean expected) {
        val actual = BitwiseFunctionLibrary.isPowerOfTwo(Val.of(value));
        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsPowerOfTwoTestCases() {
        return Stream.of(Arguments.of(1L, true),                 // 2^0
                Arguments.of(2L, true),                 // 2^1
                Arguments.of(4L, true),                 // 2^2
                Arguments.of(8L, true),                 // 2^3
                Arguments.of(1024L, true),              // 2^10
                Arguments.of(0L, false),                // not a power of 2
                Arguments.of(3L, false),                // multiple bits set
                Arguments.of(7L, false),                // 0111 has multiple bits set
                Arguments.of(-8L, false),               // negative numbers are not powers of 2
                Arguments.of(-1L, false),               // all bits set
                Arguments.of(Long.MAX_VALUE, false),    // not a power of 2
                Arguments.of(0x4000000000000000L, true) // 2^62
        );
    }

    /* Edge Cases and Integration Tests */

    @Test
    void when_combinedOperations_then_producesCorrectResult() {
        // Test: (a | b) & ~c
        val a = Val.of(12L);
        val b = Val.of(10L);
        val c = Val.of(4L);

        val orResult    = BitwiseFunctionLibrary.bitwiseOr(a, b);
        val notResult   = BitwiseFunctionLibrary.bitwiseNot(c);
        val finalResult = BitwiseFunctionLibrary.bitwiseAnd(orResult, notResult);

        assertThatVal(finalResult).hasValue();
        assertThat(finalResult.get().longValue()).isEqualTo(10L);
    }

    @Test
    void when_shiftAndRotate_then_produceDifferentResults() {
        val value     = Val.of(0x8000000000000001L);
        val positions = Val.of(1L);

        val leftShift  = BitwiseFunctionLibrary.leftShift(value, positions);
        val rotateLeft = BitwiseFunctionLibrary.rotateLeft(value, positions);

        assertThatVal(leftShift).hasValue();
        assertThatVal(rotateLeft).hasValue();

        // Left shift loses the high bit
        assertThat(leftShift.get().longValue()).isEqualTo(2L);
        // Rotate preserves all bits
        assertThat(rotateLeft.get().longValue()).isEqualTo(3L);
    }

    @Test
    void when_signedVsUnsignedRightShift_then_produceDifferentResults() {
        val value     = Val.of(-8L);
        val positions = Val.of(1L);

        val signedShift   = BitwiseFunctionLibrary.rightShift(value, positions);
        val unsignedShift = BitwiseFunctionLibrary.unsignedRightShift(value, positions);

        assertThatVal(signedShift).hasValue();
        assertThatVal(unsignedShift).hasValue();

        // Signed shift preserves sign
        assertThat(signedShift.get().longValue()).isEqualTo(-4L);
        // Unsigned shift fills with zeros
        assertThat(unsignedShift.get().longValue()).isEqualTo(9223372036854775804L);
    }

    @Test
    void when_bitManipulation_then_maintainsConsistency() {
        val original = Val.of(42L);
        val position = Val.of(6L);  // bit 6 is NOT set in 42 (42 = 0b101010)

        // Set a bit that is currently clear
        val withBitSet = BitwiseFunctionLibrary.setBit(original, position);
        val isBitSet   = BitwiseFunctionLibrary.testBit(withBitSet, position);
        assertThatVal(isBitSet).hasValue();
        assertThat(isBitSet.get().booleanValue()).isTrue();

        // Clear the bit
        val withBitCleared = BitwiseFunctionLibrary.clearBit(withBitSet, position);
        val isBitCleared   = BitwiseFunctionLibrary.testBit(withBitCleared, position);
        assertThatVal(isBitCleared).hasValue();
        assertThat(isBitCleared.get().booleanValue()).isFalse();

        // Should be back to original
        assertThat(withBitCleared.get().longValue()).isEqualTo(original.get().longValue());
    }

    /* Identity and Property Tests */

    @ParameterizedTest
    @MethodSource("provideBitwiseIdentityProperties")
    void when_bitwiseOperation_then_satisfiesIdentityProperty(String propertyName, BiFunction<Val, Val, Val> operation,
            long testValue, long identityValue, long expectedResult) {

        val result = operation.apply(Val.of(testValue), Val.of(identityValue));

        assertThatVal(result).hasValue();
        assertThat(result.get().longValue()).isEqualTo(expectedResult);
    }

    private static Stream<Arguments> provideBitwiseIdentityProperties() {
        val testValue = 987654321L;
        return Stream.of(
                Arguments.of("XOR with self returns zero",
                        (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseXor, testValue, testValue, 0L),
                Arguments.of("AND with -1 returns original",
                        (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseAnd, testValue, -1L, testValue),
                Arguments.of("OR with 0 returns original",
                        (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseOr, testValue, 0L, testValue),
                Arguments.of("XOR with 0 returns original",
                        (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseXor, testValue, 0L, testValue),
                Arguments.of("AND with 0 returns zero", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseAnd,
                        testValue, 0L, 0L),
                Arguments.of("OR with -1 returns -1", (BiFunction<Val, Val, Val>) BitwiseFunctionLibrary::bitwiseOr,
                        testValue, -1L, -1L));
    }

    @Test
    void when_bitCountWithPowerOfTwo_then_returnsOne() {
        val value = Val.of(256L);
        val count = BitwiseFunctionLibrary.bitCount(value);

        assertThatVal(count).hasValue();
        assertThat(count.get().longValue()).isEqualTo(1L);

        val isPower = BitwiseFunctionLibrary.isPowerOfTwo(value);
        assertThatVal(isPower).hasValue();
        assertThat(isPower.get().booleanValue()).isTrue();
    }
}
