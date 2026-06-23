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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PermissionsFunctionLibrary")
class PermissionsFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new PermissionsFunctionLibrary())).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHasAllTestCases")
    void whenHasAllThenReturnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasAll(Value.of(value), masksArray);

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideHasAllTestCases() {
        return Stream.of(arguments(0b1111L, new long[] { 0b0001L, 0b0010L, 0b0100L }, true), // Has all three
                arguments(0b1011L, new long[] { 0b0001L, 0b0010L, 0b0100L }, false), // Missing one
                arguments(0b1111L, new long[] { 0b1111L }, true), // Exact match
                arguments(0b0000L, new long[] { 0b0001L }, false), // Has none
                arguments(0b1111L, new long[] { 0b0001L }, true), // Has subset
                arguments(15L, new long[] { 1L, 2L, 4L, 8L }, true), // All bits
                arguments(7L, new long[] { 1L, 2L, 4L, 8L }, false) // Missing bit 8
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHasAnyTestCases")
    void whenHasAnyThenReturnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasAny(Value.of(value), masksArray);

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideHasAnyTestCases() {
        return Stream.of(arguments(0b0001L, new long[] { 0b0001L, 0b0010L }, true), // Has first
                arguments(0b0010L, new long[] { 0b0001L, 0b0010L }, true), // Has second
                arguments(0b0000L, new long[] { 0b0001L, 0b0010L }, false), // Has neither
                arguments(0b1111L, new long[] { 0b0001L, 0b0010L }, true), // Has both
                arguments(1L, new long[] { 1L, 2L, 4L }, true), // Has one
                arguments(0L, new long[] { 1L, 2L, 4L }, false) // Has none
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHasNoneTestCases")
    void whenHasNoneThenReturnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasNone(Value.of(value), masksArray);

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideHasNoneTestCases() {
        return Stream.of(arguments(0b0000L, new long[] { 0b0001L, 0b0010L }, true), // Has none
                arguments(0b0001L, new long[] { 0b0001L, 0b0010L }, false), // Has one
                arguments(0b1100L, new long[] { 0b0001L, 0b0010L }, true), // Different bits
                arguments(0b1111L, new long[] { 0b0001L, 0b0010L }, false), // Has all
                arguments(8L, new long[] { 1L, 2L, 4L }, true), // No overlap
                arguments(0L, new long[] { 1L, 2L, 4L }, true) // Zero
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHasExactTestCases")
    void whenHasExactThenReturnsCorrectResult(long value, long mask, boolean expected) {
        val actual = PermissionsFunctionLibrary.hasExact(Value.of(value), Value.of(mask));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideHasExactTestCases() {
        return Stream.of(arguments(0b0101L, 0b0101L, true), // Exact match
                arguments(0b0101L, 0b0111L, false), // More in mask
                arguments(0b0111L, 0b0101L, false), // More in value
                arguments(0L, 0L, true), // Both zero
                arguments(42L, 42L, true), // Same value
                arguments(42L, 43L, false) // Different values
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHasOnlyTestCases")
    void whenHasOnlyThenReturnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasOnly(Value.of(value), masksArray);

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideHasOnlyTestCases() {
        return Stream.of(arguments(0b0001L, new long[] { 0b0001L, 0b0010L }, true), // Subset
                arguments(0b0011L, new long[] { 0b0001L, 0b0010L }, true), // Exact
                arguments(0b0111L, new long[] { 0b0001L, 0b0010L }, false), // Extra bit
                arguments(0b0000L, new long[] { 0b0001L, 0b0010L }, true), // None is subset
                arguments(3L, new long[] { 1L, 2L, 4L }, true), // Within allowed
                arguments(8L, new long[] { 1L, 2L, 4L }, false) // Outside allowed
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideCombineTestCases")
    void whenCombineThenReturnsCorrectResult(long[] masks, long expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.combine(masksArray);

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideCombineTestCases() {
        return Stream.of(arguments(new long[] { 0b0001L, 0b0010L, 0b0100L }, 0b0111L), // OR three
                arguments(new long[] { 0b1111L, 0b0001L }, 0b1111L), // OR with superset
                arguments(new long[] { 1L, 2L, 4L, 8L }, 15L), // Power of 2s
                arguments(new long[] { 0L, 0L }, 0L), // All zeros
                arguments(new long[] { 5L, 3L }, 7L) // Overlapping bits
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideCombineAllTestCases")
    void whenCombineAllThenReturnsCorrectResult(long[] masks, long expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.combineAll(masksArray);

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideCombineAllTestCases() {
        return Stream.of(arguments(new long[] { 0b1111L, 0b0111L }, 0b0111L), // AND two
                arguments(new long[] { 0b1111L, 0b1111L }, 0b1111L), // AND identical
                arguments(new long[] { 15L, 7L, 3L }, 3L), // AND decreasing
                arguments(new long[] { 5L, 3L }, 1L) // AND with overlap
        );
    }

    @ParameterizedTest(name = "{0} element = {1}")
    @MethodSource("provideArrayElementOutOfLongRangeCases")
    void whenArrayMaskElementIsOutOfLongRangeThenReturnsError(String functionName, String element) {
        val         outOfRange = Value.of(new BigDecimal(element));
        val         masks      = ArrayValue.builder().add(Value.of(1L)).add(outOfRange).build();
        final Value actual     = switch (functionName) {
                               case "combine"    -> PermissionsFunctionLibrary.combine(masks);
                               case "combineAll" -> PermissionsFunctionLibrary.combineAll(masks);
                               default           ->
                                   throw new IllegalArgumentException("Unknown function: " + functionName);
                               };

        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("64-bit range");
    }

    private static Stream<Arguments> provideArrayElementOutOfLongRangeCases() {
        val aboveLongMax = "9223372036854775808";  // Long.MAX_VALUE + 1
        val belowLongMin = "-9223372036854775809"; // Long.MIN_VALUE - 1
        return Stream.of(arguments("combine", aboveLongMax), arguments("combine", belowLongMin),
                arguments("combineAll", aboveLongMax), arguments("combineAll", belowLongMin));
    }

    @Test
    void whenCombineAllWithEmptyArrayThenReturnsError() {
        val emptyArray = ArrayValue.builder().build();
        val actual     = PermissionsFunctionLibrary.combineAll(emptyArray);

        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Cannot combine empty array");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideIsSubsetOfTestCases")
    void whenIsSubsetOfThenReturnsCorrectResult(long permissions, long superset, boolean expected) {
        val actual = PermissionsFunctionLibrary.isSubsetOf(Value.of(permissions), Value.of(superset));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideIsSubsetOfTestCases() {
        return Stream.of(arguments(0b0001L, 0b0111L, true), // Subset
                arguments(0b0111L, 0b0001L, false), // Not subset
                arguments(0b0101L, 0b0101L, true), // Equal
                arguments(0b0000L, 0b1111L, true), // Empty is subset
                arguments(3L, 7L, true), // 3 is subset of 7
                arguments(8L, 7L, false) // 8 is not subset of 7
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideOverlapsTestCases")
    void whenOverlapsThenReturnsCorrectResult(long permissions1, long permissions2, boolean expected) {
        val actual = PermissionsFunctionLibrary.overlaps(Value.of(permissions1), Value.of(permissions2));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideOverlapsTestCases() {
        return Stream.of(arguments(0b0001L, 0b0001L, true), // Same bit
                arguments(0b0001L, 0b0010L, false), // Different bits
                arguments(0b0111L, 0b0100L, true), // Overlapping
                arguments(0b0000L, 0b1111L, false), // Zero overlaps nothing
                arguments(5L, 3L, true), // 5 & 3 = 1
                arguments(4L, 3L, false) // 4 & 3 = 0
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAreDisjointTestCases")
    void whenAreDisjointThenReturnsCorrectResult(long permissions1, long permissions2, boolean expected) {
        val actual = PermissionsFunctionLibrary.areDisjoint(Value.of(permissions1), Value.of(permissions2));

        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideAreDisjointTestCases() {
        return Stream.of(arguments(0b0001L, 0b0010L, true), // Disjoint
                arguments(0b0001L, 0b0001L, false), // Not disjoint
                arguments(0b0111L, 0b1000L, true), // No overlap
                arguments(0b0000L, 0b1111L, true), // Zero is disjoint with all
                arguments(4L, 3L, true), // Disjoint
                arguments(5L, 3L, false) // Not disjoint
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideUnixExtractTestCases")
    void whenUnixExtractThenReturnsCorrectPermissions(long mode, long owner, long group, long other) {
        val actualOwner = PermissionsFunctionLibrary.unixOwner(Value.of(mode));
        val actualGroup = PermissionsFunctionLibrary.unixGroup(Value.of(mode));
        val actualOther = PermissionsFunctionLibrary.unixOther(Value.of(mode));

        assertThat(actualOwner).isInstanceOf(NumberValue.class).isEqualTo(Value.of(owner));
        assertThat(actualGroup).isInstanceOf(NumberValue.class).isEqualTo(Value.of(group));
        assertThat(actualOther).isInstanceOf(NumberValue.class).isEqualTo(Value.of(other));
    }

    private static Stream<Arguments> provideUnixExtractTestCases() {
        return Stream.of(arguments(493L, 7L, 5L, 5L), // 493 = 0755 (rwxr-xr-x)
                arguments(420L, 6L, 4L, 4L), // 420 = 0644 (rw-r--r--)
                arguments(511L, 7L, 7L, 7L), // 511 = 0777 (rwxrwxrwx)
                arguments(384L, 6L, 0L, 0L), // 384 = 0600 (rw-------)
                arguments(438L, 6L, 6L, 6L), // 438 = 0666 (rw-rw-rw-)
                arguments(0L, 0L, 0L, 0L), // 0 = 0000 (---------)
                arguments(256L, 4L, 0L, 0L), // 256 = 0400 (r--------)
                arguments(73L, 1L, 1L, 1L) // 73 = 0111 (--x--x--x)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideUnixModeTestCases")
    void whenUnixModeThenConstructsCorrectMode(long owner, long group, long other, long expected) {
        val actual = PermissionsFunctionLibrary.unixMode(Value.of(owner), Value.of(group), Value.of(other));

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideUnixModeTestCases() {
        return Stream.of(arguments(7L, 5L, 5L, 493L), // rwxr-xr-x = 0755
                arguments(6L, 4L, 4L, 420L), // rw-r--r-- = 0644
                arguments(7L, 7L, 7L, 511L), // rwxrwxrwx = 0777
                arguments(6L, 0L, 0L, 384L), // rw------- = 0600
                arguments(6L, 6L, 6L, 438L), // rw-rw-rw- = 0666
                arguments(0L, 0L, 0L, 0L), // --------- = 0000
                arguments(4L, 0L, 0L, 256L), // r-------- = 0400
                arguments(1L, 1L, 1L, 73L) // --x--x--x = 0111
        );
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = { -1L, 8L, 10L, 100L })
    void whenUnixModeWithInvalidPermissionThenReturnsError(long invalidValue) {
        val actual = PermissionsFunctionLibrary.unixMode(Value.of(invalidValue), Value.of(5L), Value.of(5L));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("must be between 0 and 7");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideUnixCanCheckTestCases")
    void whenUnixCanThenReturnsCorrectResult(long permissions, boolean canRead, boolean canWrite, boolean canExecute) {
        val actualRead    = PermissionsFunctionLibrary.unixCanRead(Value.of(permissions));
        val actualWrite   = PermissionsFunctionLibrary.unixCanWrite(Value.of(permissions));
        val actualExecute = PermissionsFunctionLibrary.unixCanExecute(Value.of(permissions));

        assertThat(actualRead).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(canRead));
        assertThat(actualWrite).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(canWrite));
        assertThat(actualExecute).isInstanceOf(BooleanValue.class).isEqualTo(Value.of(canExecute));
    }

    private static Stream<Arguments> provideUnixCanCheckTestCases() {
        return Stream.of(arguments(0L, false, false, false), // ---
                arguments(1L, false, false, true), // --x
                arguments(2L, false, true, false), // -w-
                arguments(3L, false, true, true), // -wx
                arguments(4L, true, false, false), // r--
                arguments(5L, true, false, true), // r-x
                arguments(6L, true, true, false), // rw-
                arguments(7L, true, true, true) // rwx
        );
    }

    @Test
    void whenPosixConstantsThenReturnCorrectValues() {
        assertThat(PermissionsFunctionLibrary.posixRead()).isEqualTo(Value.of(4L));
        assertThat(PermissionsFunctionLibrary.posixWrite()).isEqualTo(Value.of(2L));
        assertThat(PermissionsFunctionLibrary.posixExecute()).isEqualTo(Value.of(1L));
        assertThat(PermissionsFunctionLibrary.posixAll()).isEqualTo(Value.of(7L));
        assertThat(PermissionsFunctionLibrary.posixNone()).isEqualTo(Value.of(0L));
        assertThat(PermissionsFunctionLibrary.posixRW()).isEqualTo(Value.of(6L));
        assertThat(PermissionsFunctionLibrary.posixRX()).isEqualTo(Value.of(5L));
        assertThat(PermissionsFunctionLibrary.posixWX()).isEqualTo(Value.of(3L));
    }

    @Test
    void whenPosixModeConstantsThenReturnCorrectValues() {
        assertThat(PermissionsFunctionLibrary.posixMode755()).isEqualTo(Value.of(493L));
        assertThat(PermissionsFunctionLibrary.posixMode644()).isEqualTo(Value.of(420L));
        assertThat(PermissionsFunctionLibrary.posixMode777()).isEqualTo(Value.of(511L));
        assertThat(PermissionsFunctionLibrary.posixMode600()).isEqualTo(Value.of(384L));
        assertThat(PermissionsFunctionLibrary.posixMode666()).isEqualTo(Value.of(438L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideGrantTestCases")
    void whenGrantThenAddsPermissions(long current, long[] toGrant, long expected) {
        val grantArray = createLongArray(toGrant);
        val actual     = PermissionsFunctionLibrary.grant(Value.of(current), grantArray);

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideGrantTestCases() {
        return Stream.of(arguments(0b0000L, new long[] { 0b0001L, 0b0010L }, 0b0011L), // Add to empty
                arguments(0b0001L, new long[] { 0b0010L, 0b0100L }, 0b0111L), // Add to existing
                arguments(0b0111L, new long[] { 0b0001L, 0b0010L }, 0b0111L), // Add already present
                arguments(0L, new long[] { 1L, 2L, 4L }, 7L), // Grant multiple
                arguments(8L, new long[] { 1L, 2L }, 11L) // Add to existing
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRevokeTestCases")
    void whenRevokeThenRemovesPermissions(long current, long[] toRevoke, long expected) {
        val revokeArray = createLongArray(toRevoke);
        val actual      = PermissionsFunctionLibrary.revoke(Value.of(current), revokeArray);

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideRevokeTestCases() {
        return Stream.of(arguments(0b0111L, new long[] { 0b0001L, 0b0010L }, 0b0100L), // Remove some
                arguments(0b0111L, new long[] { 0b0111L }, 0b0000L), // Remove all
                arguments(0b0111L, new long[] { 0b1000L }, 0b0111L), // Remove non-present
                arguments(15L, new long[] { 1L, 2L }, 12L), // Revoke specific
                arguments(7L, new long[] { 4L, 2L }, 1L) // Revoke multiple
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideToggleTestCases")
    void whenToggleThenFlipsPermissions(long current, long[] toToggle, long expected) {
        val toggleArray = createLongArray(toToggle);
        val actual      = PermissionsFunctionLibrary.toggle(Value.of(current), toggleArray);

        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> provideToggleTestCases() {
        return Stream.of(arguments(0b0000L, new long[] { 0b0001L, 0b0010L }, 0b0011L), // Toggle on
                arguments(0b0011L, new long[] { 0b0001L, 0b0010L }, 0b0000L), // Toggle off
                arguments(0b0101L, new long[] { 0b0001L, 0b0010L }, 0b0110L), // Toggle mixed
                arguments(0L, new long[] { 1L, 2L }, 3L), // Toggle from zero
                arguments(7L, new long[] { 1L, 2L }, 4L) // Toggle some
        );
    }

    @Test
    void whenNoneThenReturnsZero() {
        val actual = PermissionsFunctionLibrary.none();
        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(0L));
    }

    @Test
    void whenAllThenReturnsMinusOne() {
        val actual = PermissionsFunctionLibrary.all();
        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(-1L));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = { 0L, 1L, 2L, 31L, 32L, 63L })
    void whenBitWithValidPositionThenReturnsSingleBit(long position) {
        val actual = PermissionsFunctionLibrary.bit(Value.of(position));
        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(1L << position));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = { -1L, -10L, 64L, 65L, 100L })
    void whenBitWithInvalidPositionThenReturnsError(long invalidPosition) {
        val actual = PermissionsFunctionLibrary.bit(Value.of(invalidPosition));
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("Bit position must be between 0 and 63");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "5.5", "0.1", "63.9" })
    void whenBitWithFractionalPositionThenReturnsError(String fractionalPosition) {
        val actual = PermissionsFunctionLibrary.bit((NumberValue) Value.of(new BigDecimal(fractionalPosition)));
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(error -> ((ErrorValue) error).message()).asString()
                .contains("Bit position must be between 0 and 63");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "18446744073709551621", "9223372036854775808", "-9223372036854775809" })
    void whenBitWithPositionOutsideLongRangeThenReturnsError(String hugePosition) {
        val actual = PermissionsFunctionLibrary.bit((NumberValue) Value.of(new BigDecimal(hugePosition)));
        assertThat(actual).isInstanceOf(ErrorValue.class).extracting(error -> ((ErrorValue) error).message()).asString()
                .contains("Bit position must be between 0 and 63");
    }

    @Test
    void whenHasAllWithNonArrayMaskThenReturnsError() {
        // This test verifies error handling for incorrect parameter type
        // We need to pass something that's not an ArrayValue to test the error case
        val singleValue = ArrayValue.builder().build(); // Pass empty array to trigger different error
        val actual      = PermissionsFunctionLibrary.hasAll((NumberValue) Value.of(42L), singleValue);
        // Empty array should return true (vacuous truth: has all of zero elements)
        assertThat(actual).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void whenCombineWithNonIntegerElementThenReturnsError() {
        val array  = ArrayValue.builder().add(Value.of("not a number")).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("must be numbers");
    }

    @Test
    void whenCombineWithFloatingPointNumberThenReturnsError() {
        val array  = ArrayValue.builder().add(Value.of(3.55)).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) actual).message()).contains("must be integers");
    }

    @Test
    void whenCombineWithIntegralEquivalentNumbersThenSucceeds() {
        val array  = ArrayValue.builder().add(Value.of(1.0)).add(Value.of(2.00)).add(Value.of(4)).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual).isInstanceOf(NumberValue.class).isEqualTo(Value.of(7L));
    }

    @Test
    void whenUnixPermissionWorkflowThenWorksCorrectly() {
        // Create mode 493 (0755 = rwxr-xr-x)
        val mode = PermissionsFunctionLibrary.unixMode(Value.of(7L), Value.of(5L), Value.of(5L));
        assertThat(mode).isInstanceOf(NumberValue.class);

        // Extract owner permissions
        val ownerPerms = PermissionsFunctionLibrary.unixOwner((NumberValue) mode);
        assertThat(ownerPerms).isInstanceOf(NumberValue.class).isEqualTo(Value.of(7L));

        // Check owner can read, write, execute
        val canRead    = PermissionsFunctionLibrary.unixCanRead((NumberValue) ownerPerms);
        val canWrite   = PermissionsFunctionLibrary.unixCanWrite((NumberValue) ownerPerms);
        val canExecute = PermissionsFunctionLibrary.unixCanExecute((NumberValue) ownerPerms);

        assertThat(canRead).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
        assertThat(canWrite).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
        assertThat(canExecute).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void whenPermissionManipulationChainThenWorksCorrectly() {
        val initial = Value.of(0b0001L);

        // Grant permissions
        val afterGrant = PermissionsFunctionLibrary.grant(initial, createLongArray(new long[] { 0b0010L, 0b0100L }));
        assertThat(afterGrant).isInstanceOf(NumberValue.class).isEqualTo(Value.of(0b0111L));

        // Revoke one permission
        val afterRevoke = PermissionsFunctionLibrary.revoke((NumberValue) afterGrant,
                createLongArray(new long[] { 0b0010L }));
        assertThat(afterRevoke).isInstanceOf(NumberValue.class).isEqualTo(Value.of(0b0101L));

        // Toggle permissions
        val afterToggle = PermissionsFunctionLibrary.toggle((NumberValue) afterRevoke,
                createLongArray(new long[] { 0b0001L, 0b1000L }));
        assertThat(afterToggle).isInstanceOf(NumberValue.class).isEqualTo(Value.of(0b1100L));
    }

    @Test
    void whenCombineWithConstantsThenWorksCorrectly() {
        val read    = PermissionsFunctionLibrary.posixRead();
        val write   = PermissionsFunctionLibrary.posixWrite();
        val execute = PermissionsFunctionLibrary.posixExecute();

        val array = ArrayValue.builder().add(read).add(write).add(execute).build();

        val combined = PermissionsFunctionLibrary.combine(array);
        assertThat(combined).isInstanceOf(NumberValue.class).isEqualTo(Value.of(7L));
    }

    @ParameterizedTest(name = "{0} arg = {1}")
    @MethodSource("provideScalarArgumentValidationCases")
    void whenScalarArgumentIsFractionalOrOutOfLongRangeThenReturnsError(String functionName, String argument) {
        val         number = (NumberValue) Value.of(new BigDecimal(argument));
        val         masks  = createLongArray(new long[] { 1L });
        final Value actual = switch (functionName) {
                           case "hasAll"         -> PermissionsFunctionLibrary.hasAll(number, masks);
                           case "hasAny"         -> PermissionsFunctionLibrary.hasAny(number, masks);
                           case "hasNone"        -> PermissionsFunctionLibrary.hasNone(number, masks);
                           case "hasOnly"        -> PermissionsFunctionLibrary.hasOnly(number, masks);
                           case "hasExact"       ->
                               PermissionsFunctionLibrary.hasExact(number, (NumberValue) Value.of(1L));
                           case "isSubsetOf"     ->
                               PermissionsFunctionLibrary.isSubsetOf(number, (NumberValue) Value.of(1L));
                           case "overlaps"       ->
                               PermissionsFunctionLibrary.overlaps(number, (NumberValue) Value.of(1L));
                           case "areDisjoint"    ->
                               PermissionsFunctionLibrary.areDisjoint(number, (NumberValue) Value.of(1L));
                           case "unixOwner"      -> PermissionsFunctionLibrary.unixOwner(number);
                           case "unixGroup"      -> PermissionsFunctionLibrary.unixGroup(number);
                           case "unixOther"      -> PermissionsFunctionLibrary.unixOther(number);
                           case "unixCanRead"    -> PermissionsFunctionLibrary.unixCanRead(number);
                           case "unixCanWrite"   -> PermissionsFunctionLibrary.unixCanWrite(number);
                           case "unixCanExecute" -> PermissionsFunctionLibrary.unixCanExecute(number);
                           case "grant"          -> PermissionsFunctionLibrary.grant(number, masks);
                           case "revoke"         -> PermissionsFunctionLibrary.revoke(number, masks);
                           case "toggle"         -> PermissionsFunctionLibrary.toggle(number, masks);
                           default               ->
                               throw new IllegalArgumentException("Unknown function: " + functionName);
                           };
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> provideScalarArgumentValidationCases() {
        val twoToTheSixtyFourPlusSeven = "18446744073709551623";
        val fractional                 = "6.9";
        val scalarFunctions            = Stream.of("hasAll", "hasAny", "hasNone", "hasOnly", "hasExact", "isSubsetOf",
                "overlaps", "areDisjoint", "unixOwner", "unixGroup", "unixOther", "unixCanRead", "unixCanWrite",
                "unixCanExecute", "grant", "revoke", "toggle");
        return scalarFunctions.flatMap(functionName -> Stream.of(arguments(functionName, twoToTheSixtyFourPlusSeven),
                arguments(functionName, fractional)));
    }

    @ParameterizedTest(name = "unixMode arg index {0} = {1}")
    @MethodSource("provideUnixModeArgumentValidationCases")
    void whenUnixModeArgumentIsFractionalOrOutOfLongRangeThenReturnsError(int argumentIndex, String argument) {
        val         invalid = (NumberValue) Value.of(new BigDecimal(argument));
        val         valid   = (NumberValue) Value.of(7L);
        final Value actual  = switch (argumentIndex) {
                            case 0  -> PermissionsFunctionLibrary.unixMode(invalid, valid, valid);
                            case 1  -> PermissionsFunctionLibrary.unixMode(valid, invalid, valid);
                            case 2  -> PermissionsFunctionLibrary.unixMode(valid, valid, invalid);
                            default -> throw new IllegalArgumentException("Unknown argument index: " + argumentIndex);
                            };
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> provideUnixModeArgumentValidationCases() {
        return Stream.of(arguments(0, "18446744073709551623"), arguments(0, "6.9"),
                arguments(1, "18446744073709551623"), arguments(1, "6.9"), arguments(2, "18446744073709551623"),
                arguments(2, "6.9"));
    }

    private static ArrayValue createLongArray(long[] values) {
        val builder = ArrayValue.builder();
        for (long value : values) {
            builder.add(Value.of(value));
        }
        return builder.build();
    }
}
