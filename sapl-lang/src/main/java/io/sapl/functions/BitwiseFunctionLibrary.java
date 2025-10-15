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
 * Collection of bitwise operations for 64-bit signed long integers.
 * All operations use two's complement representation for negative numbers.
 */
@UtilityClass
@FunctionLibrary(name = BitwiseFunctionLibrary.NAME, description = BitwiseFunctionLibrary.DESCRIPTION)
public class BitwiseFunctionLibrary {

    public static final String NAME        = "bitwise";
    public static final String DESCRIPTION = "A collection of bitwise operations for 64-bit signed long integers using two's complement representation.";

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

    /* Core Operations */

    @Function(docs = """
            ```bitwiseAnd(LONG left, LONG right)```: Returns the bitwise AND of two long values.

            Performs bitwise AND operation where the result bit is 1 only if both corresponding bits are 1.
            Uses 64-bit signed long arithmetic with two's complement representation for negative numbers.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.bitwiseAnd(12, 10) == 8;      // 1100 & 1010 = 1000
              bitwise.bitwiseAnd(255, 15) == 15;    // 0xFF & 0x0F = 0x0F
              bitwise.bitwiseAnd(-1, 42) == 42;     // all bits set & value = value
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseAnd(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() & right.get().longValue());
    }

    @Function(docs = """
            ```bitwiseOr(LONG left, LONG right)```: Returns the bitwise OR of two long values.

            Performs bitwise OR operation where the result bit is 1 if at least one corresponding bit is 1.
            Uses 64-bit signed long arithmetic with two's complement representation for negative numbers.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.bitwiseOr(12, 10) == 14;      // 1100 | 1010 = 1110
              bitwise.bitwiseOr(8, 4) == 12;        // 1000 | 0100 = 1100
              bitwise.bitwiseOr(0, 42) == 42;       // 0 | value = value
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseOr(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() | right.get().longValue());
    }

    @Function(docs = """
            ```bitwiseXor(LONG left, LONG right)```: Returns the bitwise XOR (exclusive OR) of two long values.

            Performs bitwise XOR operation where the result bit is 1 if exactly one corresponding bit is 1.
            Uses 64-bit signed long arithmetic with two's complement representation for negative numbers.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.bitwiseXor(12, 10) == 6;      // 1100 ^ 1010 = 0110
              bitwise.bitwiseXor(255, 255) == 0;    // same value ^ same value = 0
              bitwise.bitwiseXor(42, 0) == 42;      // value ^ 0 = value
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseXor(@Long Val left, @Long Val right) {
        return Val.of(left.get().longValue() ^ right.get().longValue());
    }

    @Function(docs = """
            ```bitwiseNot(LONG value)```: Returns the bitwise NOT (one's complement) of a long value.

            Inverts all bits of the value. In two's complement representation, this is equivalent to -(value + 1).
            Uses 64-bit signed long arithmetic.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.bitwiseNot(0) == -1;          // all bits inverted
              bitwise.bitwiseNot(-1) == 0;          // double inversion
              bitwise.bitwiseNot(42) == -43;        // -(42 + 1)
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitwiseNot(@Long Val value) {
        return Val.of(~value.get().longValue());
    }

    @Function(docs = """
            ```leftShift(LONG value, LONG positions)```: Returns the value left-shifted by the specified number of bit positions.

            Shifts bits to the left. Bits shifted out of the left side are discarded, and zeros are shifted in from the right.
            Equivalent to multiplying by 2^positions (with overflow).

            **Requirements:**
            - positions must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.leftShift(1, 3) == 8;         // 0001 << 3 = 1000
              bitwise.leftShift(5, 2) == 20;        // 0101 << 2 = 10100
              bitwise.leftShift(1, 10) == 1024;     // 2^10
            ```
            """, schema = RETURNS_NUMBER)
    public static Val leftShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, "Shift");
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() << positions.get().longValue());
    }

    @Function(docs = """
            ```rightShift(LONG value, LONG positions)```: Returns the value right-shifted by the specified number of bit positions using arithmetic shift (sign extension).

            Shifts bits to the right with sign extension. For positive numbers, zeros are shifted in from the left.
            For negative numbers, ones are shifted in to preserve the sign (arithmetic shift).
            Equivalent to dividing by 2^positions (rounding toward negative infinity).

            **Requirements:**
            - positions must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.rightShift(16, 2) == 4;       // 16 / 4 = 4
              bitwise.rightShift(-8, 1) == -4;      // sign-extended (arithmetic shift)
              bitwise.rightShift(1024, 10) == 1;    // 1024 / 1024 = 1
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rightShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, "Shift");
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() >> positions.get().longValue());
    }

    @Function(docs = """
            ```unsignedRightShift(LONG value, LONG positions)```: Returns the value right-shifted by the specified number of bit positions using logical shift (zero-fill).

            Shifts bits to the right with zero-fill. Zeros are always shifted in from the left, regardless of the sign bit.
            Treats the value as an unsigned 64-bit integer for the purpose of shifting.

            **Requirements:**
            - positions must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.unsignedRightShift(16, 2) == 4;
              bitwise.unsignedRightShift(-8, 1) == 9223372036854775804;  // zero-fill, not sign-extend
              bitwise.unsignedRightShift(-1, 1) == 9223372036854775807;  // largest positive long
            ```
            """, schema = RETURNS_NUMBER)
    public static Val unsignedRightShift(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, "Shift");
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(value.get().longValue() >>> positions.get().longValue());
    }

    /* Bit Manipulation */

    @Function(docs = """
            ```testBit(LONG value, LONG position)```: Tests whether the bit at the specified position is set (1) or clear (0).

            Returns true if the bit at the given position is 1, false otherwise.
            Bit positions are numbered from right to left, starting at 0 (least significant bit).

            **Requirements:**
            - position must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.testBit(8, 3) == true;        // 1000 has bit 3 set
              bitwise.testBit(8, 2) == false;       // 1000 does not have bit 2 set
              bitwise.testBit(5, 0) == true;        // 0101 has bit 0 set
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
            ```setBit(LONG value, LONG position)```: Returns the value with the bit at the specified position set to 1.

            Sets the bit at the given position to 1, leaving all other bits unchanged.
            Bit positions are numbered from right to left, starting at 0 (least significant bit).

            **Requirements:**
            - position must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.setBit(8, 2) == 12;           // 1000 -> 1100
              bitwise.setBit(0, 3) == 8;            // 0000 -> 1000
              bitwise.setBit(5, 1) == 7;            // 0101 -> 0111
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
            ```clearBit(LONG value, LONG position)```: Returns the value with the bit at the specified position set to 0.

            Clears the bit at the given position to 0, leaving all other bits unchanged.
            Bit positions are numbered from right to left, starting at 0 (least significant bit).

            **Requirements:**
            - position must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.clearBit(15, 2) == 11;        // 1111 -> 1011
              bitwise.clearBit(8, 3) == 0;          // 1000 -> 0000
              bitwise.clearBit(7, 1) == 5;          // 0111 -> 0101
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
            ```toggleBit(LONG value, LONG position)```: Returns the value with the bit at the specified position flipped (0 becomes 1, 1 becomes 0).

            Toggles the bit at the given position, leaving all other bits unchanged.
            Bit positions are numbered from right to left, starting at 0 (least significant bit).

            **Requirements:**
            - position must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.toggleBit(8, 3) == 0;         // 1000 -> 0000
              bitwise.toggleBit(8, 2) == 12;        // 1000 -> 1100
              bitwise.toggleBit(5, 1) == 7;         // 0101 -> 0111
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
            ```bitCount(LONG value)```: Returns the number of one-bits (population count) in the two's complement binary representation of the value.

            Counts how many bits are set to 1 in the 64-bit representation.
            For negative numbers, this counts the 1-bits in the two's complement representation.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.bitCount(0) == 0;             // no bits set
              bitwise.bitCount(7) == 3;             // 0111 has 3 bits set
              bitwise.bitCount(15) == 4;            // 1111 has 4 bits set
              bitwise.bitCount(-1) == 64;           // all bits set in two's complement
            ```
            """, schema = RETURNS_NUMBER)
    public static Val bitCount(@Long Val value) {
        return Val.of(java.lang.Long.bitCount(value.get().longValue()));
    }

    /* Utility Functions */

    @Function(docs = """
            ```rotateLeft(LONG value, LONG positions)```: Returns the value with bits rotated left by the specified number of positions.

            Rotates bits circularly to the left. Bits shifted out of the left side are rotated back in from the right.
            Unlike left shift, no bits are lost in rotation.

            **Requirements:**
            - positions must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.rotateLeft(1, 3) == 8;
              bitwise.rotateLeft(0x8000000000000001, 1) == 3;  // high bit rotates to low
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rotateLeft(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, "Shift");
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(java.lang.Long.rotateLeft(value.get().longValue(), (int) positions.get().longValue()));
    }

    @Function(docs = """
            ```rotateRight(LONG value, LONG positions)```: Returns the value with bits rotated right by the specified number of positions.

            Rotates bits circularly to the right. Bits shifted out of the right side are rotated back in from the left.
            Unlike right shift, no bits are lost in rotation.

            **Requirements:**
            - positions must be between 0 and 63 (inclusive)

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.rotateRight(8, 3) == 1;
              bitwise.rotateRight(3, 1) == 0x8000000000000001;  // low bit rotates to high
            ```
            """, schema = RETURNS_NUMBER)
    public static Val rotateRight(@Long Val value, @Long Val positions) {
        val positionValidation = validatePosition(positions, "Shift");
        if (positionValidation != null) {
            return positionValidation;
        }

        return Val.of(java.lang.Long.rotateRight(value.get().longValue(), (int) positions.get().longValue()));
    }

    @Function(docs = """
            ```leadingZeros(LONG value)```: Returns the number of zero bits preceding the highest-order (leftmost) one-bit in the two's complement binary representation.

            Returns 64 if the value is zero (all bits are leading zeros).
            Useful for determining the position of the most significant set bit.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.leadingZeros(0) == 64;        // all bits are zero
              bitwise.leadingZeros(1) == 63;        // only bit 0 is set
              bitwise.leadingZeros(8) == 60;        // bit 3 is highest set bit
              bitwise.leadingZeros(-1) == 0;        // sign bit is set
            ```
            """, schema = RETURNS_NUMBER)
    public static Val leadingZeros(@Long Val value) {
        return Val.of(java.lang.Long.numberOfLeadingZeros(value.get().longValue()));
    }

    @Function(docs = """
            ```trailingZeros(LONG value)```: Returns the number of zero bits following the lowest-order (rightmost) one-bit in the two's complement binary representation.

            Returns 64 if the value is zero (all bits are trailing zeros).
            Useful for determining how many times a value is divisible by 2.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.trailingZeros(0) == 64;       // all bits are zero
              bitwise.trailingZeros(1) == 0;        // bit 0 is set
              bitwise.trailingZeros(8) == 3;        // bits 0-2 are trailing zeros
              bitwise.trailingZeros(12) == 2;       // 1100 has 2 trailing zeros
            ```
            """, schema = RETURNS_NUMBER)
    public static Val trailingZeros(@Long Val value) {
        return Val.of(java.lang.Long.numberOfTrailingZeros(value.get().longValue()));
    }

    @Function(docs = """
            ```reverseBits(LONG value)```: Returns the value with the bit order reversed.

            The bit at position 0 moves to position 63, the bit at position 1 moves to position 62, and so on.
            Effectively mirrors the bit pattern around the center.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.reverseBits(1) == -9223372036854775808;  // bit 0 -> bit 63
              bitwise.reverseBits(0) == 0;                      // all zeros stay zeros
            ```
            """, schema = RETURNS_NUMBER)
    public static Val reverseBits(@Long Val value) {
        return Val.of(java.lang.Long.reverse(value.get().longValue()));
    }

    @Function(docs = """
            ```isPowerOfTwo(LONG value)```: Tests whether the value is a power of two (exactly one bit is set).

            Returns true if the value has exactly one bit set to 1, which means it is a power of 2.
            Returns false for zero and negative numbers.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              bitwise.isPowerOfTwo(1) == true;      // 2^0
              bitwise.isPowerOfTwo(8) == true;      // 2^3
              bitwise.isPowerOfTwo(1024) == true;   // 2^10
              bitwise.isPowerOfTwo(0) == false;     // not a power of 2
              bitwise.isPowerOfTwo(7) == false;     // 0111 has multiple bits set
              bitwise.isPowerOfTwo(-8) == false;    // negative numbers are not powers of 2
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
            return Val.error(context + " position must be between 0 and 63");
        }
        return null;
    }
}
