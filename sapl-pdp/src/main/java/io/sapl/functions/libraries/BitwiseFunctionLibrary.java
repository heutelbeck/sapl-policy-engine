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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;

/**
 * Bitwise operations for authorization policies using 64-bit signed long
 * integers.
 * <p>
 * Functions accept NumberValue parameters and return Value to support the
 * error-as-value pattern. Overflow checking is
 * performed when converting BigDecimal to long primitives.
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
            permit action == "read_document";
                var READ_PERMISSION = 0;
                bitwise.testBit(subject.permissions, READ_PERMISSION);
            ```

            Combine permission sets using bitwise OR when merging permissions from multiple sources
            like direct grants and group memberships.

            ```sapl
            policy "merge_permissions"
            permit
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
            permit action == "admin_panel";
                var ADMIN_PERMS = 240;
                bitwise.bitwiseAnd(subject.permissions, ADMIN_PERMS) == ADMIN_PERMS;
            ```

            Implement feature flags by testing individual bits. Each bit represents whether a
            specific feature is enabled for the user.

            ```sapl
            policy "feature_access"
            permit action == "use_beta_feature";
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
            deny action == "grant_permission";
                bitwise.bitCount(subject.permissions) >= 10;
            ```

            Use XOR to toggle permissions or feature flags. This switches between states without
            conditional logic.

            ```sapl
            policy "toggle_debug_mode"
            permit action == "toggle_debug";
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

    private static final String CONTEXT_BIT   = "Bit";
    private static final String CONTEXT_SHIFT = "Shift";

    private static final String ERROR_POSITION_MUST_BE_BETWEEN_0_AND_63 = " position must be between 0 and 63.";
    private static final String ERROR_VALUE_OUT_OF_LONG_RANGE           = "Value out of long range.";

    /**
     * Safely converts NumberValue to long, checking for overflow.
     *
     * @param number
     * the number to convert
     *
     * @return the long value, or null if out of range
     */
    private static Long toLongSafe(NumberValue number) {
        val value = number.value();
        if (value.scale() > 0) {
            return null;
        }
        if (value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                || value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
            return null;
        }
        return value.longValue();
    }

    /**
     * Performs bitwise AND operation.
     *
     * @param left
     * first operand
     * @param right
     * second operand
     *
     * @return bitwise AND result
     */
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
                var REQUIRED = 15;
                bitwise.bitwiseAnd(subject.permissions, REQUIRED) == REQUIRED;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bitwiseAnd(NumberValue left, NumberValue right) {
        val leftLong  = toLongSafe(left);
        val rightLong = toLongSafe(right);
        if (leftLong == null || rightLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(leftLong & rightLong);
    }

    /**
     * Performs bitwise OR operation.
     *
     * @param left
     * first operand
     * @param right
     * second operand
     *
     * @return bitwise OR result
     */
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
                var all = bitwise.bitwiseOr(subject.directPermissions, subject.inheritedPermissions);
                bitwise.testBit(all, 5);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bitwiseOr(NumberValue left, NumberValue right) {
        val leftLong  = toLongSafe(left);
        val rightLong = toLongSafe(right);
        if (leftLong == null || rightLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(leftLong | rightLong);
    }

    /**
     * Performs bitwise XOR operation.
     *
     * @param left
     * first operand
     * @param right
     * second operand
     *
     * @return bitwise XOR result
     */
    @Function(docs = """
            ```bitwise.bitwiseXor(LONG left, LONG right)```

            Performs bitwise XOR operation where the result bit is 1 if exactly one corresponding
            bit is 1. Use this to find differences between permission sets or to toggle bits.

            Parameters:
            - left: First operand
            - right: Second operand

            Returns: Bitwise XOR result

            Example - find permission differences:
            ```sapl
            policy "example"
            permit
                var differences = bitwise.bitwiseXor(subject.permissions, resource.requiredPermissions);
                differences == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bitwiseXor(NumberValue left, NumberValue right) {
        val leftLong  = toLongSafe(left);
        val rightLong = toLongSafe(right);
        if (leftLong == null || rightLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(leftLong ^ rightLong);
    }

    /**
     * Performs bitwise NOT operation.
     *
     * @param value
     * operand
     *
     * @return bitwise NOT result
     */
    @Function(docs = """
            ```bitwise.bitwiseNot(LONG value)```

            Performs bitwise NOT operation, flipping every bit. Each 0 becomes 1 and each 1
            becomes 0. Use this to invert permission sets or create permission masks.

            Parameters:
            - value: Operand

            Returns: Bitwise NOT result

            Example - invert permissions:
            ```sapl
            policy "example"
            permit
                var allowed = 15;
                var denied = bitwise.bitwiseNot(allowed);
                bitwise.bitwiseAnd(subject.permissions, denied) == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bitwiseNot(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(~valueLong);
    }

    /**
     * Tests whether a specific bit is set.
     *
     * @param value
     * value to test
     * @param position
     * bit position (0 to 63)
     *
     * @return Value.TRUE if bit is set, Value.FALSE otherwise
     */
    @Function(docs = """
            ```bitwise.testBit(LONG value, LONG position)```

            Tests whether the bit at the specified position is set to 1. Returns true if the bit
            is set, false otherwise. Bit positions range from 0 (rightmost) to 63 (leftmost).

            Parameters:
            - value: Value to test
            - position: Bit position (0 to 63)

            Returns: Boolean indicating whether bit is set

            Example - check specific permission:
            ```sapl
            policy "example"
            permit action == "delete";
                var DELETE_BIT = 3;
                bitwise.testBit(subject.permissions, DELETE_BIT);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value testBit(NumberValue value, NumberValue position) {
        val positionValidation = validatePosition(position, CONTEXT_BIT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(position);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        val mask = 1L << posLong.intValue();
        return Value.of((valueLong & mask) != 0);
    }

    /**
     * Sets a specific bit to 1.
     *
     * @param value
     * value to modify
     * @param position
     * bit position (0 to 63)
     *
     * @return value with bit set
     */
    @Function(docs = """
            ```bitwise.setBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position set to 1. Other bits remain
            unchanged. Bit positions range from 0 (rightmost) to 63 (leftmost).

            Parameters:
            - value: Value to modify
            - position: Bit position (0 to 63)

            Returns: Value with bit set

            Example - grant specific permission:
            ```sapl
            policy "example"
            permit action == "grant_read";
            transform
                var READ_BIT = 0;
                subject.permissions = bitwise.setBit(subject.permissions, READ_BIT);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value setBit(NumberValue value, NumberValue position) {
        val positionValidation = validatePosition(position, CONTEXT_BIT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(position);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        val mask = 1L << posLong.intValue();
        return Value.of(valueLong | mask);
    }

    /**
     * Clears a specific bit to 0.
     *
     * @param value
     * value to modify
     * @param position
     * bit position (0 to 63)
     *
     * @return value with bit cleared
     */
    @Function(docs = """
            ```bitwise.clearBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position set to 0. Other bits remain
            unchanged. Bit positions range from 0 (rightmost) to 63 (leftmost).

            Parameters:
            - value: Value to modify
            - position: Bit position (0 to 63)

            Returns: Value with bit cleared

            Example - revoke specific permission:
            ```sapl
            policy "example"
            permit action == "revoke_write";
            transform
                var WRITE_BIT = 1;
                subject.permissions = bitwise.clearBit(subject.permissions, WRITE_BIT);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value clearBit(NumberValue value, NumberValue position) {
        val positionValidation = validatePosition(position, CONTEXT_BIT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(position);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        val mask = 1L << posLong.intValue();
        return Value.of(valueLong & ~mask);
    }

    /**
     * Toggles a specific bit.
     *
     * @param value
     * value to modify
     * @param position
     * bit position (0 to 63)
     *
     * @return value with bit toggled
     */
    @Function(docs = """
            ```bitwise.toggleBit(LONG value, LONG position)```

            Returns the value with the bit at the specified position flipped. If the bit is 0 it
            becomes 1, if it is 1 it becomes 0. Other bits remain unchanged. Bit positions range
            from 0 (rightmost) to 63 (leftmost).

            Parameters:
            - value: Value to modify
            - position: Bit position (0 to 63)

            Returns: Value with bit toggled

            Example - toggle debug mode:
            ```sapl
            policy "example"
            permit action == "toggle_feature";
            transform
                var FEATURE_BIT = 5;
                subject.flags = bitwise.toggleBit(subject.flags, FEATURE_BIT);
            ```
            """, schema = RETURNS_NUMBER)
    public static Value toggleBit(NumberValue value, NumberValue position) {
        val positionValidation = validatePosition(position, CONTEXT_BIT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(position);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        val mask = 1L << posLong.intValue();
        return Value.of(valueLong ^ mask);
    }

    /**
     * Returns the number of one-bits.
     *
     * @param value
     * value to analyze
     *
     * @return number of one-bits
     */
    @Function(docs = """
            ```bitwise.bitCount(LONG value)```

            Returns the number of one-bits in the two's complement binary representation.
            Useful for counting how many permissions are granted or features are enabled.

            Parameters:
            - value: Value to analyze

            Returns: Number of one-bits

            Example - enforce permission limit:
            ```sapl
            policy "example"
            deny action == "grant_permission";
                bitwise.bitCount(subject.permissions) >= 10;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value bitCount(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(Long.bitCount(valueLong));
    }

    /**
     * Shifts bits left by the specified number of positions.
     *
     * @param value
     * value to shift
     * @param positions
     * number of positions to shift (0 to 63)
     *
     * @return shifted value
     */
    @Function(docs = """
            ```bitwise.leftShift(LONG value, LONG positions)```

            Returns the value with bits shifted left by the specified number of positions.
            Equivalent to multiplying by 2 to the power of positions. Bits shifted off the
            left end are discarded, zeros are shifted in from the right.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Shifted value

            Example:
            ```sapl
            policy "example"
            permit
                bitwise.leftShift(1, 3) == 8;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value leftShift(NumberValue value, NumberValue positions) {
        val positionValidation = validatePosition(positions, CONTEXT_SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(positions);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        return Value.of(valueLong << posLong.intValue());
    }

    /**
     * Shifts bits right by the specified number of positions with sign extension.
     *
     * @param value
     * value to shift
     * @param positions
     * number of positions to shift (0 to 63)
     *
     * @return shifted value
     */
    @Function(docs = """
            ```bitwise.rightShift(LONG value, LONG positions)```

            Returns the value with bits shifted right by the specified number of positions.
            This is an arithmetic shift that preserves the sign. For positive numbers, zeros
            are shifted in from the left. For negative numbers, ones are shifted in from the
            left.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Shifted value

            Example:
            ```sapl
            policy "example"
            permit
                bitwise.rightShift(16, 2) == 4;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value rightShift(NumberValue value, NumberValue positions) {
        val positionValidation = validatePosition(positions, CONTEXT_SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(positions);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        return Value.of(valueLong >> posLong.intValue());
    }

    /**
     * Shifts bits right by the specified number of positions with zero extension.
     *
     * @param value
     * value to shift
     * @param positions
     * number of positions to shift (0 to 63)
     *
     * @return shifted value
     */
    @Function(docs = """
            ```bitwise.unsignedRightShift(LONG value, LONG positions)```

            Returns the value with bits shifted right by the specified number of positions.
            This is a logical shift that does not preserve the sign. Zeros are always shifted
            in from the left, regardless of the sign bit.

            Parameters:
            - value: Value to shift
            - positions: Number of positions to shift (0 to 63)

            Returns: Shifted value

            Example:
            ```sapl
            policy "example"
            permit
                bitwise.unsignedRightShift(16, 2) == 4;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value unsignedRightShift(NumberValue value, NumberValue positions) {
        val positionValidation = validatePosition(positions, CONTEXT_SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(positions);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        return Value.of(valueLong >>> posLong.intValue());
    }

    /**
     * Rotates bits left by the specified number of positions.
     *
     * @param value
     * value to rotate
     * @param positions
     * number of positions to rotate (0 to 63)
     *
     * @return rotated value
     */
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
                bitwise.rotateLeft(1, 3) == 8;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value rotateLeft(NumberValue value, NumberValue positions) {
        val positionValidation = validatePosition(positions, CONTEXT_SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(positions);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        return Value.of(Long.rotateLeft(valueLong, posLong.intValue()));
    }

    /**
     * Rotates bits right by the specified number of positions.
     *
     * @param value
     * value to rotate
     * @param positions
     * number of positions to rotate (0 to 63)
     *
     * @return rotated value
     */
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
                bitwise.rotateRight(8, 3) == 1;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value rotateRight(NumberValue value, NumberValue positions) {
        val positionValidation = validatePosition(positions, CONTEXT_SHIFT);
        if (positionValidation != null) {
            return positionValidation;
        }

        val valueLong = toLongSafe(value);
        val posLong   = toLongSafe(positions);
        if (valueLong == null || posLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }

        return Value.of(Long.rotateRight(valueLong, posLong.intValue()));
    }

    /**
     * Returns the number of zero bits preceding the highest-order one-bit.
     *
     * @param value
     * value to analyze
     *
     * @return number of leading zero bits
     */
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
                bitwise.leadingZeros(0) == 64;
                bitwise.leadingZeros(1) == 63;
                bitwise.leadingZeros(8) == 60;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value leadingZeros(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(Long.numberOfLeadingZeros(valueLong));
    }

    /**
     * Returns the number of zero bits following the lowest-order one-bit.
     *
     * @param value
     * value to analyze
     *
     * @return number of trailing zero bits
     */
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
                bitwise.trailingZeros(0) == 64;
                bitwise.trailingZeros(1) == 0;
                bitwise.trailingZeros(8) == 3;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value trailingZeros(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(Long.numberOfTrailingZeros(valueLong));
    }

    /**
     * Returns the value with bit order reversed.
     *
     * @param value
     * value to reverse
     *
     * @return value with reversed bit order
     */
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
                bitwise.reverseBits(0) == 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value reverseBits(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(Long.reverse(valueLong));
    }

    /**
     * Tests whether the value is a power of two.
     *
     * @param value
     * value to test
     *
     * @return Value.TRUE if value is a power of two, Value.FALSE otherwise
     */
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
                bitwise.isPowerOfTwo(1);
                bitwise.isPowerOfTwo(8);
                bitwise.isPowerOfTwo(1024);
                !bitwise.isPowerOfTwo(0);
                !bitwise.isPowerOfTwo(7);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isPowerOfTwo(NumberValue value) {
        val valueLong = toLongSafe(value);
        if (valueLong == null) {
            return Value.error(ERROR_VALUE_OUT_OF_LONG_RANGE);
        }
        return Value.of(valueLong > 0 && (valueLong & (valueLong - 1)) == 0);
    }

    /**
     * Validates that the position is between 0 and 63 (inclusive).
     *
     * @param position
     * the position to validate
     * @param context
     * the context for error messages (e.g., "Shift", "Bit")
     *
     * @return error Value if invalid, null if valid
     */
    private static Value validatePosition(NumberValue position, String context) {
        val value = position.value();
        if (value.compareTo(BigDecimal.valueOf(64)) >= 0 || value.compareTo(BigDecimal.ZERO) < 0) {
            return Value.error(context + ERROR_POSITION_MUST_BE_BETWEEN_0_AND_63);
        }
        return null;
    }
}
