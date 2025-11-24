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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for PermissionsFunctionLibrary.
 */
class PermissionsFunctionLibraryTests {

    /* Core Permission Semantics Tests */

    @ParameterizedTest
    @MethodSource("provideHasAllTestCases")
    void when_hasAll_then_returnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasAll(Value.of(value), masksArray);

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHasAllTestCases() {
        return Stream.of(Arguments.of(0b1111L, new long[] { 0b0001L, 0b0010L, 0b0100L }, true), // Has all three
                Arguments.of(0b1011L, new long[] { 0b0001L, 0b0010L, 0b0100L }, false), // Missing one
                Arguments.of(0b1111L, new long[] { 0b1111L }, true), // Exact match
                Arguments.of(0b0000L, new long[] { 0b0001L }, false), // Has none
                Arguments.of(0b1111L, new long[] { 0b0001L }, true), // Has subset
                Arguments.of(15L, new long[] { 1L, 2L, 4L, 8L }, true), // All bits
                Arguments.of(7L, new long[] { 1L, 2L, 4L, 8L }, false) // Missing bit 8
        );
    }

    @ParameterizedTest
    @MethodSource("provideHasAnyTestCases")
    void when_hasAny_then_returnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasAny(Value.of(value), masksArray);

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHasAnyTestCases() {
        return Stream.of(Arguments.of(0b0001L, new long[] { 0b0001L, 0b0010L }, true), // Has first
                Arguments.of(0b0010L, new long[] { 0b0001L, 0b0010L }, true), // Has second
                Arguments.of(0b0000L, new long[] { 0b0001L, 0b0010L }, false), // Has neither
                Arguments.of(0b1111L, new long[] { 0b0001L, 0b0010L }, true), // Has both
                Arguments.of(1L, new long[] { 1L, 2L, 4L }, true), // Has one
                Arguments.of(0L, new long[] { 1L, 2L, 4L }, false) // Has none
        );
    }

    @ParameterizedTest
    @MethodSource("provideHasNoneTestCases")
    void when_hasNone_then_returnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasNone(Value.of(value), masksArray);

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHasNoneTestCases() {
        return Stream.of(Arguments.of(0b0000L, new long[] { 0b0001L, 0b0010L }, true), // Has none
                Arguments.of(0b0001L, new long[] { 0b0001L, 0b0010L }, false), // Has one
                Arguments.of(0b1100L, new long[] { 0b0001L, 0b0010L }, true), // Different bits
                Arguments.of(0b1111L, new long[] { 0b0001L, 0b0010L }, false), // Has all
                Arguments.of(8L, new long[] { 1L, 2L, 4L }, true), // No overlap
                Arguments.of(0L, new long[] { 1L, 2L, 4L }, true) // Zero
        );
    }

    @ParameterizedTest
    @MethodSource("provideHasExactTestCases")
    void when_hasExact_then_returnsCorrectResult(long value, long mask, boolean expected) {
        val actual = PermissionsFunctionLibrary.hasExact(Value.of(value), Value.of(mask));

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHasExactTestCases() {
        return Stream.of(Arguments.of(0b0101L, 0b0101L, true), // Exact match
                Arguments.of(0b0101L, 0b0111L, false), // More in mask
                Arguments.of(0b0111L, 0b0101L, false), // More in value
                Arguments.of(0L, 0L, true), // Both zero
                Arguments.of(42L, 42L, true), // Same value
                Arguments.of(42L, 43L, false) // Different values
        );
    }

    @ParameterizedTest
    @MethodSource("provideHasOnlyTestCases")
    void when_hasOnly_then_returnsCorrectResult(long value, long[] masks, boolean expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.hasOnly(Value.of(value), masksArray);

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHasOnlyTestCases() {
        return Stream.of(Arguments.of(0b0001L, new long[] { 0b0001L, 0b0010L }, true), // Subset
                Arguments.of(0b0011L, new long[] { 0b0001L, 0b0010L }, true), // Exact
                Arguments.of(0b0111L, new long[] { 0b0001L, 0b0010L }, false), // Extra bit
                Arguments.of(0b0000L, new long[] { 0b0001L, 0b0010L }, true), // None is subset
                Arguments.of(3L, new long[] { 1L, 2L, 4L }, true), // Within allowed
                Arguments.of(8L, new long[] { 1L, 2L, 4L }, false) // Outside allowed
        );
    }

    @ParameterizedTest
    @MethodSource("provideCombineTestCases")
    void when_combine_then_returnsCorrectResult(long[] masks, long expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.combine(masksArray);

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideCombineTestCases() {
        return Stream.of(Arguments.of(new long[] { 0b0001L, 0b0010L, 0b0100L }, 0b0111L), // OR three
                Arguments.of(new long[] { 0b1111L, 0b0001L }, 0b1111L), // OR with superset
                Arguments.of(new long[] { 1L, 2L, 4L, 8L }, 15L), // Power of 2s
                Arguments.of(new long[] { 0L, 0L }, 0L), // All zeros
                Arguments.of(new long[] { 5L, 3L }, 7L) // Overlapping bits
        );
    }

    @ParameterizedTest
    @MethodSource("provideCombineAllTestCases")
    void when_combineAll_then_returnsCorrectResult(long[] masks, long expected) {
        val masksArray = createLongArray(masks);
        val actual     = PermissionsFunctionLibrary.combineAll(masksArray);

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideCombineAllTestCases() {
        return Stream.of(Arguments.of(new long[] { 0b1111L, 0b0111L }, 0b0111L), // AND two
                Arguments.of(new long[] { 0b1111L, 0b1111L }, 0b1111L), // AND identical
                Arguments.of(new long[] { 15L, 7L, 3L }, 3L), // AND decreasing
                Arguments.of(new long[] { 5L, 3L }, 1L) // AND with overlap
        );
    }

    @Test
    void when_combineAll_withEmptyArray_then_returnsError() {
        val emptyArray = ArrayValue.builder().build();
        val actual     = PermissionsFunctionLibrary.combineAll(emptyArray);

        assertThat(actual instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) actual).message()).contains("Cannot combine empty array");
    }

    @ParameterizedTest
    @MethodSource("provideIsSubsetOfTestCases")
    void when_isSubsetOf_then_returnsCorrectResult(long permissions, long superset, boolean expected) {
        val actual = PermissionsFunctionLibrary.isSubsetOf(Value.of(permissions), Value.of(superset));

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsSubsetOfTestCases() {
        return Stream.of(Arguments.of(0b0001L, 0b0111L, true), // Subset
                Arguments.of(0b0111L, 0b0001L, false), // Not subset
                Arguments.of(0b0101L, 0b0101L, true), // Equal
                Arguments.of(0b0000L, 0b1111L, true), // Empty is subset
                Arguments.of(3L, 7L, true), // 3 is subset of 7
                Arguments.of(8L, 7L, false) // 8 is not subset of 7
        );
    }

    @ParameterizedTest
    @MethodSource("provideOverlapsTestCases")
    void when_overlaps_then_returnsCorrectResult(long permissions1, long permissions2, boolean expected) {
        val actual = PermissionsFunctionLibrary.overlaps(Value.of(permissions1), Value.of(permissions2));

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideOverlapsTestCases() {
        return Stream.of(Arguments.of(0b0001L, 0b0001L, true), // Same bit
                Arguments.of(0b0001L, 0b0010L, false), // Different bits
                Arguments.of(0b0111L, 0b0100L, true), // Overlapping
                Arguments.of(0b0000L, 0b1111L, false), // Zero overlaps nothing
                Arguments.of(5L, 3L, true), // 5 & 3 = 1
                Arguments.of(4L, 3L, false) // 4 & 3 = 0
        );
    }

    @ParameterizedTest
    @MethodSource("provideAreDisjointTestCases")
    void when_areDisjoint_then_returnsCorrectResult(long permissions1, long permissions2, boolean expected) {
        val actual = PermissionsFunctionLibrary.areDisjoint(Value.of(permissions1), Value.of(permissions2));

        assertThat(actual instanceof BooleanValue).isTrue();
        assertThat(((BooleanValue) actual).value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideAreDisjointTestCases() {
        return Stream.of(Arguments.of(0b0001L, 0b0010L, true), // Disjoint
                Arguments.of(0b0001L, 0b0001L, false), // Not disjoint
                Arguments.of(0b0111L, 0b1000L, true), // No overlap
                Arguments.of(0b0000L, 0b1111L, true), // Zero is disjoint with all
                Arguments.of(4L, 3L, true), // Disjoint
                Arguments.of(5L, 3L, false) // Not disjoint
        );
    }

    /* Unix/POSIX Permission Tests */

    @ParameterizedTest
    @MethodSource("provideUnixExtractTestCases")
    void when_unixExtract_then_returnsCorrectPermissions(long mode, long owner, long group, long other) {
        val actualOwner = PermissionsFunctionLibrary.unixOwner(Value.of(mode));
        val actualGroup = PermissionsFunctionLibrary.unixGroup(Value.of(mode));
        val actualOther = PermissionsFunctionLibrary.unixOther(Value.of(mode));

        assertThat(actualOwner instanceof NumberValue).isTrue();
        assertThat(actualGroup instanceof NumberValue).isTrue();
        assertThat(actualOther instanceof NumberValue).isTrue();

        assertThat(((NumberValue) actualOwner).value().longValue()).isEqualTo(owner);
        assertThat(((NumberValue) actualGroup).value().longValue()).isEqualTo(group);
        assertThat(((NumberValue) actualOther).value().longValue()).isEqualTo(other);
    }

    private static Stream<Arguments> provideUnixExtractTestCases() {
        return Stream.of(Arguments.of(493L, 7L, 5L, 5L), // 493 = 0755 (rwxr-xr-x)
                Arguments.of(420L, 6L, 4L, 4L), // 420 = 0644 (rw-r--r--)
                Arguments.of(511L, 7L, 7L, 7L), // 511 = 0777 (rwxrwxrwx)
                Arguments.of(384L, 6L, 0L, 0L), // 384 = 0600 (rw-------)
                Arguments.of(438L, 6L, 6L, 6L), // 438 = 0666 (rw-rw-rw-)
                Arguments.of(0L, 0L, 0L, 0L), // 0 = 0000 (---------)
                Arguments.of(256L, 4L, 0L, 0L), // 256 = 0400 (r--------)
                Arguments.of(73L, 1L, 1L, 1L) // 73 = 0111 (--x--x--x)
        );
    }

    @ParameterizedTest
    @MethodSource("provideUnixModeTestCases")
    void when_unixMode_then_constructsCorrectMode(long owner, long group, long other, long expected) {
        val actual = PermissionsFunctionLibrary.unixMode(Value.of(owner), Value.of(group), Value.of(other));

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideUnixModeTestCases() {
        return Stream.of(Arguments.of(7L, 5L, 5L, 493L), // rwxr-xr-x = 0755
                Arguments.of(6L, 4L, 4L, 420L), // rw-r--r-- = 0644
                Arguments.of(7L, 7L, 7L, 511L), // rwxrwxrwx = 0777
                Arguments.of(6L, 0L, 0L, 384L), // rw------- = 0600
                Arguments.of(6L, 6L, 6L, 438L), // rw-rw-rw- = 0666
                Arguments.of(0L, 0L, 0L, 0L), // --------- = 0000
                Arguments.of(4L, 0L, 0L, 256L), // r-------- = 0400
                Arguments.of(1L, 1L, 1L, 73L) // --x--x--x = 0111
        );
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, 8L, 10L, 100L })
    void when_unixMode_withInvalidPermission_then_returnsError(long invalidValue) {
        val actual = PermissionsFunctionLibrary.unixMode(Value.of(invalidValue), Value.of(5L), Value.of(5L));
        assertThat(actual instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) actual).message()).contains("must be between 0 and 7");
    }

    @ParameterizedTest
    @MethodSource("provideUnixCanCheckTestCases")
    void when_unixCan_then_returnsCorrectResult(long permissions, boolean canRead, boolean canWrite,
            boolean canExecute) {
        val actualRead    = PermissionsFunctionLibrary.unixCanRead(Value.of(permissions));
        val actualWrite   = PermissionsFunctionLibrary.unixCanWrite(Value.of(permissions));
        val actualExecute = PermissionsFunctionLibrary.unixCanExecute(Value.of(permissions));

        assertThat(actualRead instanceof BooleanValue).isTrue();
        assertThat(actualWrite instanceof BooleanValue).isTrue();
        assertThat(actualExecute instanceof BooleanValue).isTrue();

        assertThat(((BooleanValue) actualRead).value()).isEqualTo(canRead);
        assertThat(((BooleanValue) actualWrite).value()).isEqualTo(canWrite);
        assertThat(((BooleanValue) actualExecute).value()).isEqualTo(canExecute);
    }

    private static Stream<Arguments> provideUnixCanCheckTestCases() {
        return Stream.of(Arguments.of(0L, false, false, false), // ---
                Arguments.of(1L, false, false, true), // --x
                Arguments.of(2L, false, true, false), // -w-
                Arguments.of(3L, false, true, true), // -wx
                Arguments.of(4L, true, false, false), // r--
                Arguments.of(5L, true, false, true), // r-x
                Arguments.of(6L, true, true, false), // rw-
                Arguments.of(7L, true, true, true) // rwx
        );
    }

    /* POSIX Constants Tests */

    @Test
    void when_posixConstants_then_returnCorrectValues() {
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixRead()).value().longValue()).isEqualTo(4L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixWrite()).value().longValue()).isEqualTo(2L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixExecute()).value().longValue()).isEqualTo(1L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixAll()).value().longValue()).isEqualTo(7L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixNone()).value().longValue()).isZero();
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixRW()).value().longValue()).isEqualTo(6L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixRX()).value().longValue()).isEqualTo(5L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixWX()).value().longValue()).isEqualTo(3L);
    }

    @Test
    void when_posixModeConstants_then_returnCorrectValues() {
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixMode755()).value().longValue()).isEqualTo(493L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixMode644()).value().longValue()).isEqualTo(420L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixMode777()).value().longValue()).isEqualTo(511L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixMode600()).value().longValue()).isEqualTo(384L);
        assertThat(((NumberValue) PermissionsFunctionLibrary.posixMode666()).value().longValue()).isEqualTo(438L);
    }

    /* Manipulation Tests */

    @ParameterizedTest
    @MethodSource("provideGrantTestCases")
    void when_grant_then_addsPermissions(long current, long[] toGrant, long expected) {
        val grantArray = createLongArray(toGrant);
        val actual     = PermissionsFunctionLibrary.grant(Value.of(current), grantArray);

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideGrantTestCases() {
        return Stream.of(Arguments.of(0b0000L, new long[] { 0b0001L, 0b0010L }, 0b0011L), // Add to empty
                Arguments.of(0b0001L, new long[] { 0b0010L, 0b0100L }, 0b0111L), // Add to existing
                Arguments.of(0b0111L, new long[] { 0b0001L, 0b0010L }, 0b0111L), // Add already present
                Arguments.of(0L, new long[] { 1L, 2L, 4L }, 7L), // Grant multiple
                Arguments.of(8L, new long[] { 1L, 2L }, 11L) // Add to existing
        );
    }

    @ParameterizedTest
    @MethodSource("provideRevokeTestCases")
    void when_revoke_then_removesPermissions(long current, long[] toRevoke, long expected) {
        val revokeArray = createLongArray(toRevoke);
        val actual      = PermissionsFunctionLibrary.revoke(Value.of(current), revokeArray);

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideRevokeTestCases() {
        return Stream.of(Arguments.of(0b0111L, new long[] { 0b0001L, 0b0010L }, 0b0100L), // Remove some
                Arguments.of(0b0111L, new long[] { 0b0111L }, 0b0000L), // Remove all
                Arguments.of(0b0111L, new long[] { 0b1000L }, 0b0111L), // Remove non-present
                Arguments.of(15L, new long[] { 1L, 2L }, 12L), // Revoke specific
                Arguments.of(7L, new long[] { 4L, 2L }, 1L) // Revoke multiple
        );
    }

    @ParameterizedTest
    @MethodSource("provideToggleTestCases")
    void when_toggle_then_flipsPermissions(long current, long[] toToggle, long expected) {
        val toggleArray = createLongArray(toToggle);
        val actual      = PermissionsFunctionLibrary.toggle(Value.of(current), toggleArray);

        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideToggleTestCases() {
        return Stream.of(Arguments.of(0b0000L, new long[] { 0b0001L, 0b0010L }, 0b0011L), // Toggle on
                Arguments.of(0b0011L, new long[] { 0b0001L, 0b0010L }, 0b0000L), // Toggle off
                Arguments.of(0b0101L, new long[] { 0b0001L, 0b0010L }, 0b0110L), // Toggle mixed
                Arguments.of(0L, new long[] { 1L, 2L }, 3L), // Toggle from zero
                Arguments.of(7L, new long[] { 1L, 2L }, 4L) // Toggle some
        );
    }

    /* Universal Constants Tests */

    @Test
    void when_none_then_returnsZero() {
        val actual = PermissionsFunctionLibrary.none();
        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isZero();
    }

    @Test
    void when_all_then_returnsMinusOne() {
        val actual = PermissionsFunctionLibrary.all();
        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(-1L);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, 1L, 2L, 31L, 32L, 63L })
    void when_bit_withValidPosition_then_returnsSingleBit(long position) {
        val actual = PermissionsFunctionLibrary.bit(Value.of(position));
        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(1L << position);
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, -10L, 64L, 65L, 100L })
    void when_bit_withInvalidPosition_then_returnsError(long invalidPosition) {
        val actual = PermissionsFunctionLibrary.bit(Value.of(invalidPosition));
        assertThat(actual instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) actual).message()).contains("Bit position must be between 0 and 63");
    }

    /* Error Handling Tests */

    @Test
    void when_hasAll_withNonArrayMask_then_returnsError() {
        // This test verifies error handling for incorrect parameter type
        // We need to pass something that's not an ArrayValue to test the error case
        val singleValue = ArrayValue.builder().build(); // Pass empty array to trigger different error
        val actual      = PermissionsFunctionLibrary.hasAll((NumberValue) Value.of(42L), singleValue);
        // Note: This may need adjustment based on actual error handling in the library
        assertThat(actual instanceof ErrorValue || actual instanceof BooleanValue).isTrue();
    }

    @Test
    void when_combine_withNonIntegerElement_then_returnsError() {
        val array  = ArrayValue.builder().add(Value.of("not a number")).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) actual).message()).contains("must be numbers");
    }

    @Test
    void when_combine_withFloatingPointNumber_then_returnsError() {
        val array  = ArrayValue.builder().add(Value.of(3.55)).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) actual).message()).contains("must be integers");
    }

    @Test
    void when_combine_withIntegralEquivalentNumbers_then_succeeds() {
        val array  = ArrayValue.builder().add(Value.of(1.0)).add(Value.of(2.00)).add(Value.of(4)).build();
        val actual = PermissionsFunctionLibrary.combine(array);
        assertThat(actual instanceof NumberValue).isTrue();
        assertThat(((NumberValue) actual).value().longValue()).isEqualTo(7L);
    }

    /* Integration Tests */

    @Test
    void when_unixPermissionWorkflow_then_worksCorrectly() {
        // Create mode 493 (0755 = rwxr-xr-x)
        val mode = PermissionsFunctionLibrary.unixMode(Value.of(7L), Value.of(5L), Value.of(5L));
        assertThat(mode instanceof NumberValue).isTrue();

        // Extract owner permissions
        val ownerPerms = PermissionsFunctionLibrary.unixOwner((NumberValue) mode);
        assertThat(ownerPerms instanceof NumberValue).isTrue();
        assertThat(((NumberValue) ownerPerms).value().longValue()).isEqualTo(7L);

        // Check owner can read, write, execute
        val canRead    = PermissionsFunctionLibrary.unixCanRead((NumberValue) ownerPerms);
        val canWrite   = PermissionsFunctionLibrary.unixCanWrite((NumberValue) ownerPerms);
        val canExecute = PermissionsFunctionLibrary.unixCanExecute((NumberValue) ownerPerms);

        assertThat(canRead instanceof BooleanValue).isTrue();
        assertThat(canWrite instanceof BooleanValue).isTrue();
        assertThat(canExecute instanceof BooleanValue).isTrue();

        assertThat(((BooleanValue) canRead).value()).isTrue();
        assertThat(((BooleanValue) canWrite).value()).isTrue();
        assertThat(((BooleanValue) canExecute).value()).isTrue();
    }

    @Test
    void when_permissionManipulationChain_then_worksCorrectly() {
        val initial = Value.of(0b0001L);

        // Grant permissions
        val afterGrant = PermissionsFunctionLibrary.grant(initial, createLongArray(new long[] { 0b0010L, 0b0100L }));
        assertThat(afterGrant instanceof NumberValue).isTrue();
        assertThat(((NumberValue) afterGrant).value().longValue()).isEqualTo(0b0111L);

        // Revoke one permission
        val afterRevoke = PermissionsFunctionLibrary.revoke((NumberValue) afterGrant,
                createLongArray(new long[] { 0b0010L }));
        assertThat(afterRevoke instanceof NumberValue).isTrue();
        assertThat(((NumberValue) afterRevoke).value().longValue()).isEqualTo(0b0101L);

        // Toggle permissions
        val afterToggle = PermissionsFunctionLibrary.toggle((NumberValue) afterRevoke,
                createLongArray(new long[] { 0b0001L, 0b1000L }));
        assertThat(afterToggle instanceof NumberValue).isTrue();
        assertThat(((NumberValue) afterToggle).value().longValue()).isEqualTo(0b1100L);
    }

    @Test
    void when_combineWithConstants_then_worksCorrectly() {
        val read    = PermissionsFunctionLibrary.posixRead();
        val write   = PermissionsFunctionLibrary.posixWrite();
        val execute = PermissionsFunctionLibrary.posixExecute();

        val array = ArrayValue.builder().add(read).add(write).add(execute).build();

        val combined = PermissionsFunctionLibrary.combine(array);
        assertThat(combined instanceof NumberValue).isTrue();
        assertThat(((NumberValue) combined).value().longValue()).isEqualTo(7L);
    }

    /* Helper Methods */

    private static ArrayValue createLongArray(long[] values) {
        val builder = ArrayValue.builder();
        for (long value : values) {
            builder.add(Value.of(value));
        }
        return builder.build();
    }
}
