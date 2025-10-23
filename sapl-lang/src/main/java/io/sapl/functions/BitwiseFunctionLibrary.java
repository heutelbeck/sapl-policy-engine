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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Long;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Bitwise operations for authorization policies using 64-bit signed long
 * integers.
 */
@UtilityClass
@FunctionLibrary(name = BitwiseFunctionLibrary.NAME, description = BitwiseFunctionLibrary.DESCRIPTION, libraryDocumentation = BitwiseFunctionLibrary.DOCUMENTATION)
public class BitwiseFunctionLibrary {

    public static final String NAME          = "bitwise";
    public static final String DESCRIPTION   = "Bitwise operations for authorization policies using 64-bit signed long integers.";
    public static final String DOCUMENTATION = """
            # Bitwise Operations

            Bitwise manipulation of 64-bit signed long integers for permission management in
            authorization policies. Test individual permission bits, combine permission sets,
            and manipulate feature flags using compact bit representations.

            ## Core Principles

            All operations use 64-bit signed long integers with two's complement representation
            for negative numbers. Bit positions are numbered from right to left, starting at 0
            for the least significant bit and ending at 63 for the most significant bit (sign bit).
            Shift and rotate operations accept position values from 0 to 63 inclusive.

            A single 64-bit integer represents up to 64 distinct permissions or feature flags.
            Each bit position corresponds to one permission. Operations execute in constant time
            regardless of how many permissions are checked.

            ## Access Control Patterns

            Store permissions as bit flags where each bit position represents a specific permission.
            Check if a user has required permissions by testing individual bits.

            ```sapl
            policy "check_read_permission"
            permit action == "read_document"
            where
                var READ_PERMISSION = 0;
                bitwise.testBit(subject.permissions, READ_PERMISSION);
            ```

            Combine permission sets using bitwise OR when merging permissions from multiple sources
            like direct grants and group memberships.

            ```sapl
            policy "merge_permissions"
            permit
            where
                var direct = subject.directPermissions;
                var inherited = subject.groupPermissions;
                var combined = bitwise.bitwiseOr(direct, inherited);
                var REQUIRED_PERMISSIONS = 15;
                bitwise.bitwiseAnd(combined, REQUIRED_PERMISSIONS) == REQUIRED_PERMISSIONS;
            ```

            Use bitwise AND to check if all required permissions are present. When the result of
            ANDing user permissions with required permissions equals the required permissions,
            all necessary bits are set.

            ```sapl
            policy "require_all_permissions"
            permit action == "admin_panel"
            where
                var ADMIN_PERMS = 240;
                bitwise.bitwiseAnd(subject.permissions, ADMIN_PERMS) == ADMIN_PERMS;
            ```

            Implement feature flags by testing individual bits. Each bit represents whether a
            specific feature is enabled for the user.

            ```sapl
            policy "feature_access"
            permit action == "use_beta_feature"
            where
                var BETA_FEATURES_BIT = 5;
                bitwise.testBit(subject.featureFlags, BETA_FEATURES_BIT);
            ```

            Remove specific permissions by clearing bits. This revokes individual permissions
            without affecting others.

            ```sapl
            policy "revoke_permission"
            permit
            transform
                var WRITE_BIT = 1;
                subject.permissions = bitwise.clearBit(subject.permissions, WRITE_BIT);
            ```

            Count active permissions or enabled features using bit counting. This enforces
            constraints on total permission counts.

            ```sapl
            policy "limit_permission_count"
            deny action == "grant_permission"
            where
                bitwise.bitCount(subject.permissions) >= 10;
            ```

            Use XOR to toggle permissions or feature flags. This switches between states without
            conditional logic.

            ```sapl
            policy "toggle_debug_mode"
            permit action == "toggle_debug"
            transform
                var DEBUG_BIT = 7;
                subject.flags = bitwise.toggleBit(subject.flags, DEBUG_BIT);
            ```
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;
    public static final String  SHIFT           = "Shift";

    @Function(docs = """
            ```bitwise.bitwiseAnd(LONG left, LONG right)```

            Performs bitwise AND operation where the result bit is 1 only if both corresponding
            bits are 1. Use this to check if all required permission bits are set or to mask
            out specific bits.

            Parameters:
            - left: First operand
            - right: Second operand

            Returns: Bitwise AND result

            Example - check if user has all required permissions:
            ```sapl
            policy "example"
            permit
            where
                var REQUIRED = 15;
                bitwise.bitwiseAnd(subject.permissions, REQUIRED) == REQUIRED;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseAnd(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() & right.get().longValue());
    }

    @Function(docs = """
            ```bitwise.bitwiseOr(LONG left, LONG right)```

            Performs bitwise OR operation where the result bit is 1 if at least one corresponding
            bit is 1. Use this to combine permission sets or feature flags from multiple sources.

            Parameters:
            - left: First operand
            - right: Second operand

            Returns: Bitwise OR result

            Example - combine direct and inherited permissions:
            ```sapl
            policy "example"
            permit
            where
                var all = bitwise.bitwiseOr(subject.directPermissions, subject.inheritedPermissions);
                bitwise.testBit(all, 5);
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseOr(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() | right.get().longValue());
    }

    @Function(docs = """
            ```bitwise.bitwiseXor(LONG left, LONG right)```

            Performs bitwise XOR (exclusive OR) operation where the result bit is 1 if exactly
            one corresponding bit is 1. Use this to find differences between permission sets
            or to toggle multiple bits simultaneously.

            Parameters:
            - left: First operand
            - right: Second operand

            Returns: Bitwise XOR result

            Example - find permissions that differ between two roles:
            ```sapl
            policy "example"
            permit
            where
                var differences = bitwise.bitwiseXor(resource.roleA, resource.roleB);
                bitwise.bitCount(differences) < 5;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseXor(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() ^ right.get().longValue());
    }

    @Function(docs = """
            ```bitwise.bitwiseNot(LONG value)```

            Inverts all bits of the value (one's complement). In two's complement representation,
            this is equivalent to negating the value and subtracting 1.

            Parameters:
            - value: Value to invert

            Returns: Bitwise NOT result

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.bitwiseNot(0) == -1;
                bitwise.bitwiseNot(42) == -43;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseNot(@Long Val value) {
        return Val.of(~value.get().longValue());
    }

    @Function(docs = """
            ```bitwise.leftShift(LONG value, LONG positions)```

            Shifts bits to the left by the specified number of positions. Bits shifted out of
            the left side are discarded, and zeros are shifted in from the right. Equivalent
            to multiplying by 2 raised to the power of positions, with overflow.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Left-shifted value

            Example - calculate permission mask for bit position:
            ```sapl
            policy "example"
            permit
            where
                var PERMISSION_BIT = 3;
                var mask = bitwise.leftShift(1, PERMISSION_BIT);
                bitwise.bitwiseAnd(subject.permissions, mask) == mask;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val leftShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() << positions.get().longValue());
    }

    @Function(docs = """
            ```bitwise.rightShift(LONG value, LONG positions)```

            Shifts bits to the right by the specified number of positions using arithmetic shift
            (sign extension). For positive numbers, zeros are shifted in from the left. For
            negative numbers, ones are shifted in to preserve the sign. Equivalent to dividing
            by 2 raised to the power of positions, with rounding toward negative infinity.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Right-shifted value with sign extension

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.rightShift(16, 2) == 4;
                bitwise.rightShift(-8, 1) == -4;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rightShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() >> positions.get().longValue());
    }

    @Function(docs = """
            ```bitwise.unsignedRightShift(LONG value, LONG positions)```

            Shifts bits to the right by the specified number of positions using logical shift
            (zero-fill). Zeros are always shifted in from the left, regardless of the sign bit.
            Treats the value as an unsigned 64-bit integer for the purpose of shifting.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Right-shifted value with zero-fill

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.unsignedRightShift(16, 2) == 4;
                bitwise.unsignedRightShift(-1, 1) == 9223372036854775807;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val unsignedRightShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() >>> positions.get().longValue());
    }

    @Function(docs = """
            ```bitwise.testBit(LONG value, LONG position)```

            Tests whether the bit at the specified position is set (1) or clear (0). Returns
            true if the bit is 1, false otherwise. Bit positions are numbered from right to
            left, starting at 0 for the least significant bit.

            Parameters:
            - value: Value to test
            - position: Bit position to test (0 to 63)

            Returns: Boolean indicating whether bit is set

            Example - check specific permission:
            ```sapl
            policy "example"
            permit action == "delete_user"
            where
                var DELETE_PERMISSION = 4;
                bitwise.testBit(subject.permissions, DELETE_PERMISSION);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val testBit(@Long Val value, @Long Val position) {
        val positionValidation = validatePosition(position, "Bit");
        if (positionValidation != null) {
            return positionValidation;
        }

        val bitPosition = position.get().longValue();
        val mask        = 1L << bitPosition;
        return Val.of((value.get().longValue() & mask) != 0);
    }

    @Function(docs = """
            ```bitwise.setBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position set to 1, leaving all
            other bits unchanged. Bit positions are numbered from right to left, starting
            at 0 for the least significant bit.

            Parameters:
            - value: Value to modify
            - position: Bit position to set (0 to 63)

            Returns: Value with bit set

            Example - grant specific permission:
            ```sapl
            policy "example"
            permit action == "grant_read_permission"
            transform
                var READ_BIT = 0;
                resource.user.permissions = bitwise.setBit(resource.user.permissions, READ_BIT);
            ```
            """, schema = RETURNS_NUMBER)
    public static Val setBit(@Long Val value, @Long Val position) {
        val positionValidation = validatePosition(position, "Bit");
        if (positionValidation != null) {
            return positionValidation;
        }

        val bitPosition = position.get().longValue();
        val mask        = 1L << bitPosition;
        return Val.of(value.get().longValue() | mask);
    }

    @Function(docs = """
            ```bitwise.clearBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position set to 0, leaving all
            other bits unchanged. Bit positions are numbered from right to left, starting
            at 0 for the least significant bit.

            Parameters:
            - value: Value to modify
            - position: Bit position to clear (0 to 63)

            Returns: Value with bit cleared

            Example - revoke specific permission:
            ```sapl
            policy "example"
            permit action == "revoke_write_permission"
            transform
                var WRITE_BIT = 1;
                resource.user.permissions = bitwise.clearBit(resource.user.permissions, WRITE_BIT);
            ```
            """, schema = RETURNS_NUMBER)
    public static Val clearBit(@Long Val value, @Long Val position) {
        val positionValidation = validatePosition(position, "Bit");
        if (positionValidation != null) {
            return positionValidation;
        }

        val bitPosition = position.get().longValue();
        val mask        = 1L << bitPosition;
        return Val.of(value.get().longValue() & ~mask);
    }

    @Function(docs = """
            ```bitwise.toggleBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position flipped: 0 becomes 1,
            and 1 becomes 0. All other bits remain unchanged. Bit positions are numbered from
            right to left, starting at 0 for the least significant bit.

            Parameters:
            - value: Value to modify
            - position: Bit position to toggle (0 to 63)

            Returns: Value with bit toggled

            Example - toggle feature flag:
            ```sapl
            policy "example"
            permit action == "toggle_feature"
            transform
                var featurePosition = resource.featureId;
                subject.featureFlags = bitwise.toggleBit(subject.featureFlags, featurePosition);
            ```
            """, schema = RETURNS_NUMBER)
    public static Val toggleBit(@Long Val value, @Long Val position) {
        val positionValidation = validatePosition(position, "Bit");
        if (positionValidation != null) {
            return positionValidation;
        }

        val bitPosition = position.get().longValue();
        val mask        = 1L << bitPosition;
        return Val.of(value.get().longValue() ^ mask);
    }

    @Function(docs = """
            ```bitwise.bitCount(LONG value)```

            Returns the number of one-bits (population count) in the two's complement binary
            representation of the value. Counts how many bits are set to 1 in the 64-bit
            representation. For negative numbers, this counts the 1-bits in the two's
            complement representation.

            Parameters:
            - value: Value to count bits in

            Returns: Number of set bits

            Example - enforce maximum permission count:
            ```sapl
            policy "example"
            deny action == "grant_permission"
            where
                bitwise.bitCount(subject.permissions) >= 20;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitCount(@Long Val value) {
        return Val.of(java.lang.Long.bitCount(value.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.rotateLeft(LONG value, LONG positions)```

            Returns the value with bits rotated left by the specified number of positions.
            Rotates bits circularly to the left. Bits shifted out of the left side are rotated
            back in from the right. Unlike left shift, no bits are lost in rotation.

            Parameters:
            - value: Value to rotate
            - positions: Number of positions to rotate (0 to 63)

            Returns: Rotated value

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.rotateLeft(1, 3) == 8;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rotateLeft(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(java.lang.Long.rotateLeft(value.get().longValue(), (int) positions.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.rotateRight(LONG value, LONG positions)```

            Returns the value with bits rotated right by the specified number of positions.
            Rotates bits circularly to the right. Bits shifted out of the right side are
            rotated back in from the left. Unlike right shift, no bits are lost in rotation.

            Parameters:
            - value: Value to rotate
            - positions: Number of positions to rotate (0 to 63)

            Returns: Rotated value

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.rotateRight(8, 3) == 1;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rotateRight(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(java.lang.Long.rotateRight(value.get().longValue(), (int) positions.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.leadingZeros(LONG value)```

            Returns the number of zero bits preceding the highest-order (leftmost) one-bit in
            the two's complement binary representation. Returns 64 if the value is zero (all
            bits are leading zeros). Useful for determining the position of the most significant
            set bit.

            Parameters:
            - value: Value to analyze

            Returns: Number of leading zero bits

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.leadingZeros(0) == 64;
                bitwise.leadingZeros(1) == 63;
                bitwise.leadingZeros(8) == 60;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val leadingZeros(@Long Val value) {
        return Val.of(java.lang.Long.numberOfLeadingZeros(value.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.trailingZeros(LONG value)```

            Returns the number of zero bits following the lowest-order (rightmost) one-bit in
            the two's complement binary representation. Returns 64 if the value is zero (all
            bits are trailing zeros). Useful for determining how many times a value is divisible
            by 2.

            Parameters:
            - value: Value to analyze

            Returns: Number of trailing zero bits

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.trailingZeros(0) == 64;
                bitwise.trailingZeros(1) == 0;
                bitwise.trailingZeros(8) == 3;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val trailingZeros(@Long Val value) {
        return Val.of(java.lang.Long.numberOfTrailingZeros(value.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.reverseBits(LONG value)```

            Returns the value with the bit order reversed. The bit at position 0 moves to
            position 63, the bit at position 1 moves to position 62, and so on. Effectively
            mirrors the bit pattern around the center.

            Parameters:
            - value: Value to reverse

            Returns: Value with reversed bit order

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.reverseBits(0) == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val reverseBits(@Long Val value) {
        return Val.of(java.lang.Long.reverse(value.get().longValue()));
    }

    @Function(docs = """
            ```bitwise.isPowerOfTwo(LONG value)```

            Tests whether the value is a power of two, meaning exactly one bit is set. Returns
            true if the value has exactly one bit set to 1, which means it is a power of 2.
            Returns false for zero and negative numbers.

            Parameters:
            - value: Value to test

            Returns: Boolean indicating whether value is a power of two

            Example:
            ```sapl
            policy "example"
            permit
            where
                bitwise.isPowerOfTwo(1);
                bitwise.isPowerOfTwo(8);
                bitwise.isPowerOfTwo(1024);
                !bitwise.isPowerOfTwo(0);
                !bitwise.isPowerOfTwo(7);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isPowerOfTwo(@Long Val value) {
        val longValue = value.get().longValue();
        return Val.of(longValue > 0 && (longValue & (longValue - 1)) == 0);
    }

    /**
     * Validates that the position is between 0 and 63 (inclusive).
     *
     * @param position the position to validate
     * @param context the context for error messages (e.g., "Shift", "Bit")
     * @return error Val if invalid, null if valid
     */
    private static Val validatePosition(Val position, String context) {
        val positionValue = position.get().longValue();
        if (positionValue < 0 || positionValue >= 64) {
            return Val.error(context + " position must be between 0 and 63.");
        }
        return null;
    }
}
