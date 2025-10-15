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
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Converts between numeric values and their string representations in different
 * bases.
 * Supports hexadecimal (base 16), binary (base 2), and octal (base 8)
 * notations.
 *
 * Negative numbers use 64-bit two's complement representation. For example, -1
 * appears
 * as "FFFFFFFFFFFFFFFF" in hexadecimal (all 64 bits set). Sign-prefixed strings
 * like
 * "-1" are also accepted as input for convenience.
 */
@UtilityClass
@FunctionLibrary(name = NumeralFunctionLibrary.NAME, description = NumeralFunctionLibrary.DESCRIPTION)
public class NumeralFunctionLibrary {

    public static final String NAME        = "numeral";
    public static final String DESCRIPTION = "Converts between numeric values and their string representations in different bases (hexadecimal, binary, octal).";

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
            }
            """;

    private static final String RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    /* Parsing Functions */

    @Function(docs = """
            ```fromHex(TEXT value)```: Parses a hexadecimal string and returns the corresponding number.

            Accepts strings with or without the "0x" or "0X" prefix. Letters may be uppercase or lowercase.
            Underscores are allowed as visual separators and are ignored during parsing.

            Negative numbers can be represented either with a sign prefix (e.g., "-FF") or as a full
            64-bit two's complement value (e.g., "FFFFFFFFFFFFFFFF" for -1).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.fromHex("FF") == 255;
              numeral.fromHex("0xFF") == 255;
              numeral.fromHex("ff") == 255;
              numeral.fromHex("FF_FF") == 65535;
              numeral.fromHex("-1") == -1;
              numeral.fromHex("FFFFFFFFFFFFFFFF") == -1;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromHex(@Text Val value) {
        return parseWithBase(value.get().textValue(), 16, "hex");
    }

    @Function(docs = """
            ```fromBinary(TEXT value)```: Parses a binary string and returns the corresponding number.

            Accepts strings with or without the "0b" or "0B" prefix. Only digits 0 and 1 are valid.
            Underscores are allowed as visual separators and are ignored during parsing.

            Negative numbers can be represented either with a sign prefix (e.g., "-1010") or as a full
            64-bit two's complement value.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.fromBinary("1010") == 10;
              numeral.fromBinary("0b1010") == 10;
              numeral.fromBinary("1111_1111") == 255;
              numeral.fromBinary("-1010") == -10;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromBinary(@Text Val value) {
        return parseWithBase(value.get().textValue(), 2, "binary");
    }

    @Function(docs = """
            ```fromOctal(TEXT value)```: Parses an octal string and returns the corresponding number.

            Accepts strings with or without the "0o" or "0O" prefix. Only digits 0-7 are valid.
            Underscores are allowed as visual separators and are ignored during parsing.

            Negative numbers can be represented either with a sign prefix (e.g., "-77") or as a full
            64-bit two's complement value.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.fromOctal("77") == 63;
              numeral.fromOctal("0o77") == 63;
              numeral.fromOctal("755") == 493;
              numeral.fromOctal("-10") == -8;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromOctal(@Text Val value) {
        return parseWithBase(value.get().textValue(), 8, "octal");
    }

    /* Formatting Functions */

    @Function(docs = """
            ```toHex(LONG value)```: Converts a number to its hexadecimal string representation.

            Returns uppercase letters (A-F) without any prefix. Negative numbers are represented
            using 64-bit two's complement notation, meaning -1 becomes "FFFFFFFFFFFFFFFF".

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toHex(255) == "FF";
              numeral.toHex(4095) == "FFF";
              numeral.toHex(-1) == "FFFFFFFFFFFFFFFF";
              numeral.toHex(0) == "0";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toHex(@Long Val value) {
        return Val.of(java.lang.Long.toHexString(value.get().longValue()).toUpperCase());
    }

    @Function(docs = """
            ```toBinary(LONG value)```: Converts a number to its binary string representation.

            Returns a string of 1s and 0s without any prefix. Negative numbers are represented
            using 64-bit two's complement notation, meaning -1 becomes 64 consecutive 1s.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toBinary(10) == "1010";
              numeral.toBinary(255) == "11111111";
              numeral.toBinary(0) == "0";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toBinary(@Long Val value) {
        return Val.of(java.lang.Long.toBinaryString(value.get().longValue()));
    }

    @Function(docs = """
            ```toOctal(LONG value)```: Converts a number to its octal string representation.

            Returns a string of digits 0-7 without any prefix. Negative numbers are represented
            using 64-bit two's complement notation.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toOctal(63) == "77";
              numeral.toOctal(493) == "755";
              numeral.toOctal(8) == "10";
              numeral.toOctal(0) == "0";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toOctal(@Long Val value) {
        return Val.of(java.lang.Long.toOctalString(value.get().longValue()));
    }

    /* Prefixed Formatting Functions */

    @Function(docs = """
            ```toHexPrefixed(LONG value)```: Converts a number to hexadecimal with the "0x" prefix.

            Returns uppercase letters (A-F) with a "0x" prefix. Negative numbers are represented
            using 64-bit two's complement notation.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toHexPrefixed(255) == "0xFF";
              numeral.toHexPrefixed(4095) == "0xFFF";
              numeral.toHexPrefixed(-1) == "0xFFFFFFFFFFFFFFFF";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toHexPrefixed(@Long Val value) {
        return Val.of("0x" + java.lang.Long.toHexString(value.get().longValue()).toUpperCase());
    }

    @Function(docs = """
            ```toBinaryPrefixed(LONG value)```: Converts a number to binary with the "0b" prefix.

            Returns a string of 1s and 0s with a "0b" prefix. Negative numbers are represented
            using 64-bit two's complement notation.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toBinaryPrefixed(10) == "0b1010";
              numeral.toBinaryPrefixed(255) == "0b11111111";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toBinaryPrefixed(@Long Val value) {
        return Val.of("0b" + java.lang.Long.toBinaryString(value.get().longValue()));
    }

    @Function(docs = """
            ```toOctalPrefixed(LONG value)```: Converts a number to octal with the "0o" prefix.

            Returns a string of digits 0-7 with a "0o" prefix. Negative numbers are represented
            using 64-bit two's complement notation.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toOctalPrefixed(63) == "0o77";
              numeral.toOctalPrefixed(493) == "0o755";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toOctalPrefixed(@Long Val value) {
        return Val.of("0o" + java.lang.Long.toOctalString(value.get().longValue()));
    }

    /* Padded Formatting Functions */

    @Function(docs = """
            ```toHexPadded(LONG value, LONG width)```: Converts a number to hexadecimal with zero-padding to a minimum width.

            Returns uppercase letters (A-F) padded with leading zeros to reach the specified width.
            If the natural representation is already wider than the specified width, no truncation
            occurs and the full representation is returned.

            **Requirements:**
            - width must be positive

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toHexPadded(255, 4) == "00FF";
              numeral.toHexPadded(255, 2) == "FF";
              numeral.toHexPadded(4095, 2) == "FFF";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toHexPadded(@Long Val value, @Long Val width) {
        val widthValidation = validatePositiveWidth(width);
        if (widthValidation != null) {
            return widthValidation;
        }

        val hexString = java.lang.Long.toHexString(value.get().longValue()).toUpperCase();
        return Val.of(padToWidth(hexString, width.get().intValue()));
    }

    @Function(docs = """
            ```toBinaryPadded(LONG value, LONG width)```: Converts a number to binary with zero-padding to a minimum width.

            Returns a string of 1s and 0s padded with leading zeros to reach the specified width.
            If the natural representation is already wider than the specified width, no truncation
            occurs and the full representation is returned.

            **Requirements:**
            - width must be positive

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toBinaryPadded(10, 8) == "00001010";
              numeral.toBinaryPadded(10, 4) == "1010";
              numeral.toBinaryPadded(255, 4) == "11111111";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toBinaryPadded(@Long Val value, @Long Val width) {
        val widthValidation = validatePositiveWidth(width);
        if (widthValidation != null) {
            return widthValidation;
        }

        val binaryString = java.lang.Long.toBinaryString(value.get().longValue());
        return Val.of(padToWidth(binaryString, width.get().intValue()));
    }

    @Function(docs = """
            ```toOctalPadded(LONG value, LONG width)```: Converts a number to octal with zero-padding to a minimum width.

            Returns a string of digits 0-7 padded with leading zeros to reach the specified width.
            If the natural representation is already wider than the specified width, no truncation
            occurs and the full representation is returned.

            **Requirements:**
            - width must be positive

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.toOctalPadded(63, 4) == "0077";
              numeral.toOctalPadded(63, 2) == "77";
              numeral.toOctalPadded(493, 2) == "755";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toOctalPadded(@Long Val value, @Long Val width) {
        val widthValidation = validatePositiveWidth(width);
        if (widthValidation != null) {
            return widthValidation;
        }

        val octalString = java.lang.Long.toOctalString(value.get().longValue());
        return Val.of(padToWidth(octalString, width.get().intValue()));
    }

    /* Validation Functions */

    @Function(docs = """
            ```isValidHex(TEXT value)```: Checks whether a string is a valid hexadecimal representation.

            Accepts strings with or without the "0x" or "0X" prefix. Letters may be uppercase or lowercase.
            Underscores are allowed as visual separators. Sign prefixes ("-") are accepted.
            Empty strings and strings with only whitespace are considered invalid.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.isValidHex("FF") == true;
              numeral.isValidHex("0xFF") == true;
              numeral.isValidHex("FF_FF") == true;
              numeral.isValidHex("-FF") == true;
              numeral.isValidHex("GG") == false;
              numeral.isValidHex("") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidHex(@Text Val value) {
        return Val.of(isValidFormat(value.get().textValue(), 16));
    }

    @Function(docs = """
            ```isValidBinary(TEXT value)```: Checks whether a string is a valid binary representation.

            Accepts strings with or without the "0b" or "0B" prefix. Only digits 0 and 1 are valid.
            Underscores are allowed as visual separators. Sign prefixes ("-") are accepted.
            Empty strings and strings with only whitespace are considered invalid.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.isValidBinary("1010") == true;
              numeral.isValidBinary("0b1010") == true;
              numeral.isValidBinary("10_10") == true;
              numeral.isValidBinary("-1010") == true;
              numeral.isValidBinary("102") == false;
              numeral.isValidBinary("") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBinary(@Text Val value) {
        return Val.of(isValidFormat(value.get().textValue(), 2));
    }

    @Function(docs = """
            ```isValidOctal(TEXT value)```: Checks whether a string is a valid octal representation.

            Accepts strings with or without the "0o" or "0O" prefix. Only digits 0-7 are valid.
            Underscores are allowed as visual separators. Sign prefixes ("-") are accepted.
            Empty strings and strings with only whitespace are considered invalid.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              numeral.isValidOctal("77") == true;
              numeral.isValidOctal("0o77") == true;
              numeral.isValidOctal("7_7") == true;
              numeral.isValidOctal("-77") == true;
              numeral.isValidOctal("88") == false;
              numeral.isValidOctal("") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidOctal(@Text Val value) {
        return Val.of(isValidFormat(value.get().textValue(), 8));
    }

    /**
     * Parses a string in the specified base, handling prefixes, signs, and
     * underscores.
     *
     * @param input the string to parse
     * @param radix the base (2, 8, or 16)
     * @param baseName the name of the base for error messages
     * @return a Val containing the parsed number or an error
     */
    private static Val parseWithBase(String input, int radix, String baseName) {
        if (input == null || input.isBlank()) {
            return Val.error("Cannot parse empty " + baseName + " string");
        }

        var cleanedInput = input.strip();
        var isNegative   = false;

        if (cleanedInput.startsWith("-")) {
            isNegative   = true;
            cleanedInput = cleanedInput.substring(1);
        }

        cleanedInput = stripPrefix(cleanedInput, radix);
        cleanedInput = cleanedInput.replace("_", "");

        if (cleanedInput.isEmpty()) {
            return Val.error("Invalid " + baseName + " string: " + input);
        }

        try {
            val parsedValue = java.lang.Long.parseUnsignedLong(cleanedInput, radix);
            return Val.of(isNegative ? -parsedValue : parsedValue);
        } catch (NumberFormatException exception) {
            return Val.error("Invalid " + baseName + " string: " + input);
        }
    }

    /**
     * Strips common prefixes based on the radix.
     */
    private static String stripPrefix(String input, int radix) {
        val lowerInput = input.toLowerCase();
        return switch (radix) {
        case 16 -> lowerInput.startsWith("0x") ? input.substring(2) : input;
        case 2  -> lowerInput.startsWith("0b") ? input.substring(2) : input;
        case 8  -> lowerInput.startsWith("0o") ? input.substring(2) : input;
        default -> input;
        };
    }

    /**
     * Pads a string with leading zeros to reach the specified width.
     * Does not truncate if the string is already longer than the width.
     */
    private static String padToWidth(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return "0".repeat(width - value.length()) + value;
    }

    /**
     * Validates that a string matches the expected format for the given radix.
     */
    private static boolean isValidFormat(String input, int radix) {
        if (input == null || input.isBlank()) {
            return false;
        }

        var cleanedInput = input.strip();

        if (cleanedInput.startsWith("-")) {
            cleanedInput = cleanedInput.substring(1);
        }

        cleanedInput = stripPrefix(cleanedInput, radix);
        cleanedInput = cleanedInput.replace("_", "");

        if (cleanedInput.isEmpty()) {
            return false;
        }

        try {
            java.lang.Long.parseUnsignedLong(cleanedInput, radix);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * Validates that width is positive.
     */
    private static Val validatePositiveWidth(Val width) {
        if (width.get().longValue() <= 0) {
            return Val.error("Width must be positive");
        }
        return null;
    }
}
