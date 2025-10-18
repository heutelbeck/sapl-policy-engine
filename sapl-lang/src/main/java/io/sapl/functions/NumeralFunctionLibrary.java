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
 * Numeric base conversion for authorization policies.
 */
@UtilityClass
@FunctionLibrary(name = NumeralFunctionLibrary.NAME, description = NumeralFunctionLibrary.DESCRIPTION, libraryDocumentation = NumeralFunctionLibrary.DOCUMENTATION)
public class NumeralFunctionLibrary {

    public static final String NAME          = "numeral";
    public static final String DESCRIPTION   = "Numeric base conversion for authorization policies.";
    public static final String DOCUMENTATION = """
            # Numeric Base Conversion

            Convert between numeric values and their string representations in different bases.
            Parse hexadecimal, binary, and octal strings into numbers for permission masks,
            hardware identifiers, and encoded resource IDs. Format numbers for logging and
            display in various notations.

            ## Core Principles

            All conversions use 64-bit signed long integers with two's complement representation
            for negative numbers. Parsing accepts optional prefixes (0x for hex, 0b for binary,
            0o for octal), optional sign prefixes, and underscores as visual separators. Formatting
            functions produce unprefixed output by default, with separate functions for prefixed
            and padded output.

            Negative numbers in two's complement representation appear as large unsigned values
            when formatted. For example, -1 becomes "FFFFFFFFFFFFFFFF" in hexadecimal (all 64
            bits set). Sign-prefixed strings like "-1" are accepted as input for convenience.

            ## Access Control Patterns

            Parse permission masks from configuration files or external systems that store
            permissions as hexadecimal strings.

            ```sapl
            policy "parse_permission_mask"
            permit
            where
                var maskString = resource.config.permissionMask;
                numeral.isValidHex(maskString);
                var mask = numeral.fromHex(maskString);
                bitwise.bitwiseAnd(subject.permissions, mask) == mask;
            ```

            Validate input formats before processing to prevent injection attacks or malformed
            data from reaching authorization logic.

            ```sapl
            policy "validate_hardware_id"
            permit action == "register_device"
            where
                numeral.isValidHex(resource.deviceId);
                var deviceId = numeral.fromHex(resource.deviceId);
                deviceId > 0;
            ```

            Convert hardware addresses or device identifiers from hexadecimal notation for
            comparison and matching.

            ```sapl
            policy "device_id_filter"
            permit action == "network_access"
            where
                var deviceId = numeral.fromHex(subject.deviceId);
                var allowedRange = numeral.fromHex("001A2B000000");
                deviceId >= allowedRange;
            ```

            Format permission values as hexadecimal for logging or display without exposing
            internal numeric representations.

            ```sapl
            policy "log_permissions"
            permit
            obligation
                {
                    "type": "log",
                    "permissions": numeral.toHexPrefixed(subject.permissions)
                }
            ```

            Parse binary strings from feature flag systems or bit-encoded configurations.

            ```sapl
            policy "feature_flags"
            permit
            where
                var flagsString = resource.config.features;
                numeral.isValidBinary(flagsString);
                var flags = numeral.fromBinary(flagsString);
                bitwise.testBit(flags, 5);
            ```

            Convert octal file permission strings from Unix-style systems for permission
            checking.

            ```sapl
            policy "file_permissions"
            permit action == "read_file"
            where
                var permString = resource.file.permissions;
                var permissions = numeral.fromOctal(permString);
                bitwise.bitwiseAnd(permissions, 4) == 4;
            ```
            """;

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

    @Function(docs = """
            ```numeral.fromHex(TEXT value)```

            Parses a hexadecimal string and returns the corresponding number. Accepts strings
            with or without the "0x" or "0X" prefix. Letters may be uppercase or lowercase.
            Underscores are allowed as visual separators. Negative numbers can be represented
            with a sign prefix or as full 64-bit two's complement values.

            Parameters:
            - value: Hexadecimal string to parse

            Returns: Parsed number

            Example - parse permission mask from config:
            ```sapl
            policy "example"
            permit
            where
                var mask = numeral.fromHex("0xFF");
                bitwise.bitwiseAnd(subject.permissions, mask) == mask;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromHex(@Text Val value) {
        return parseWithBase(value.get().textValue(), 16, "hex");
    }

    @Function(docs = """
            ```numeral.fromBinary(TEXT value)```

            Parses a binary string and returns the corresponding number. Accepts strings with
            or without the "0b" or "0B" prefix. Only digits 0 and 1 are valid. Underscores
            are allowed as visual separators. Negative numbers can be represented with a sign
            prefix or as full 64-bit two's complement values.

            Parameters:
            - value: Binary string to parse

            Returns: Parsed number

            Example - parse feature flags:
            ```sapl
            policy "example"
            permit
            where
                var flags = numeral.fromBinary("11110000");
                bitwise.testBit(flags, 7);
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromBinary(@Text Val value) {
        return parseWithBase(value.get().textValue(), 2, "binary");
    }

    @Function(docs = """
            ```numeral.fromOctal(TEXT value)```

            Parses an octal string and returns the corresponding number. Accepts strings with
            or without the "0o" or "0O" prefix. Only digits 0-7 are valid. Underscores are
            allowed as visual separators. Negative numbers can be represented with a sign
            prefix or as full 64-bit two's complement values.

            Parameters:
            - value: Octal string to parse

            Returns: Parsed number

            Example - parse Unix file permissions:
            ```sapl
            policy "example"
            permit action == "read_file"
            where
                var permissions = numeral.fromOctal(resource.file.mode);
                bitwise.bitwiseAnd(permissions, 4) == 4;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val fromOctal(@Text Val value) {
        return parseWithBase(value.get().textValue(), 8, "octal");
    }

    @Function(docs = """
            ```numeral.toHex(LONG value)```

            Converts a number to its hexadecimal string representation. Returns uppercase
            letters (A-F) without any prefix. Negative numbers are represented using 64-bit
            two's complement notation.

            Parameters:
            - value: Number to convert

            Returns: Hexadecimal string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toHex(255) == "FF";
                numeral.toHex(4095) == "FFF";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toHex(@Long Val value) {
        return Val.of(java.lang.Long.toHexString(value.get().longValue()).toUpperCase());
    }

    @Function(docs = """
            ```numeral.toBinary(LONG value)```

            Converts a number to its binary string representation. Returns a string of 1s
            and 0s without any prefix. Negative numbers are represented using 64-bit two's
            complement notation.

            Parameters:
            - value: Number to convert

            Returns: Binary string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toBinary(10) == "1010";
                numeral.toBinary(255) == "11111111";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toBinary(@Long Val value) {
        return Val.of(java.lang.Long.toBinaryString(value.get().longValue()));
    }

    @Function(docs = """
            ```numeral.toOctal(LONG value)```

            Converts a number to its octal string representation. Returns a string of digits
            0-7 without any prefix. Negative numbers are represented using 64-bit two's
            complement notation.

            Parameters:
            - value: Number to convert

            Returns: Octal string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toOctal(63) == "77";
                numeral.toOctal(493) == "755";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toOctal(@Long Val value) {
        return Val.of(java.lang.Long.toOctalString(value.get().longValue()));
    }

    @Function(docs = """
            ```numeral.toHexPrefixed(LONG value)```

            Converts a number to hexadecimal with the "0x" prefix. Returns uppercase letters
            (A-F) with a "0x" prefix. Negative numbers are represented using 64-bit two's
            complement notation.

            Parameters:
            - value: Number to convert

            Returns: Prefixed hexadecimal string

            Example - format for logging:
            ```sapl
            policy "example"
            permit
            obligation
                {
                    "type": "log",
                    "deviceId": numeral.toHexPrefixed(resource.deviceId)
                }
            ```
            """, schema = RETURNS_TEXT)
    public static Val toHexPrefixed(@Long Val value) {
        return Val.of("0x" + java.lang.Long.toHexString(value.get().longValue()).toUpperCase());
    }

    @Function(docs = """
            ```numeral.toBinaryPrefixed(LONG value)```

            Converts a number to binary with the "0b" prefix. Returns a string of 1s and 0s
            with a "0b" prefix. Negative numbers are represented using 64-bit two's complement
            notation.

            Parameters:
            - value: Number to convert

            Returns: Prefixed binary string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toBinaryPrefixed(10) == "0b1010";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toBinaryPrefixed(@Long Val value) {
        return Val.of("0b" + java.lang.Long.toBinaryString(value.get().longValue()));
    }

    @Function(docs = """
            ```numeral.toOctalPrefixed(LONG value)```

            Converts a number to octal with the "0o" prefix. Returns a string of digits 0-7
            with a "0o" prefix. Negative numbers are represented using 64-bit two's complement
            notation.

            Parameters:
            - value: Number to convert

            Returns: Prefixed octal string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toOctalPrefixed(63) == "0o77";
            ```
            """, schema = RETURNS_TEXT)
    public static Val toOctalPrefixed(@Long Val value) {
        return Val.of("0o" + java.lang.Long.toOctalString(value.get().longValue()));
    }

    @Function(docs = """
            ```numeral.toHexPadded(LONG value, LONG width)```

            Converts a number to hexadecimal with zero-padding to a minimum width. Returns
            uppercase letters (A-F) padded with leading zeros to reach the specified width.
            If the natural representation is already wider than the specified width, no
            truncation occurs and the full representation is returned.

            Parameters:
            - value: Number to convert
            - width: Minimum width (must be positive)

            Returns: Padded hexadecimal string

            Example - format device ID with fixed width:
            ```sapl
            policy "example"
            permit
            where
                numeral.toHexPadded(255, 4) == "00FF";
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
            ```numeral.toBinaryPadded(LONG value, LONG width)```

            Converts a number to binary with zero-padding to a minimum width. Returns a string
            of 1s and 0s padded with leading zeros to reach the specified width. If the natural
            representation is already wider than the specified width, no truncation occurs and
            the full representation is returned.

            Parameters:
            - value: Number to convert
            - width: Minimum width (must be positive)

            Returns: Padded binary string

            Example - format with fixed width:
            ```sapl
            policy "example"
            permit
            where
                numeral.toBinaryPadded(10, 8) == "00001010";
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
            ```numeral.toOctalPadded(LONG value, LONG width)```

            Converts a number to octal with zero-padding to a minimum width. Returns a string
            of digits 0-7 padded with leading zeros to reach the specified width. If the natural
            representation is already wider than the specified width, no truncation occurs and
            the full representation is returned.

            Parameters:
            - value: Number to convert
            - width: Minimum width (must be positive)

            Returns: Padded octal string

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.toOctalPadded(63, 4) == "0077";
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

    @Function(docs = """
            ```numeral.isValidHex(TEXT value)```

            Checks whether a string is a valid hexadecimal representation. Accepts strings
            with or without the "0x" or "0X" prefix. Letters may be uppercase or lowercase.
            Underscores are allowed as visual separators. Sign prefixes are accepted. Empty
            strings and strings with only whitespace are considered invalid.

            Parameters:
            - value: String to validate

            Returns: Boolean indicating validity

            Example - validate before parsing:
            ```sapl
            policy "example"
            permit action == "register_device"
            where
                numeral.isValidHex(resource.deviceId);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidHex(@Text Val value) {
        return Val.of(isValidFormat(value.get().textValue(), 16));
    }

    @Function(docs = """
            ```numeral.isValidBinary(TEXT value)```

            Checks whether a string is a valid binary representation. Accepts strings with
            or without the "0b" or "0B" prefix. Only digits 0 and 1 are valid. Underscores
            are allowed as visual separators. Sign prefixes are accepted. Empty strings and
            strings with only whitespace are considered invalid.

            Parameters:
            - value: String to validate

            Returns: Boolean indicating validity

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.isValidBinary(resource.flagString);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBinary(@Text Val value) {
        return Val.of(isValidFormat(value.get().textValue(), 2));
    }

    @Function(docs = """
            ```numeral.isValidOctal(TEXT value)```

            Checks whether a string is a valid octal representation. Accepts strings with
            or without the "0o" or "0O" prefix. Only digits 0-7 are valid. Underscores are
            allowed as visual separators. Sign prefixes are accepted. Empty strings and
            strings with only whitespace are considered invalid.

            Parameters:
            - value: String to validate

            Returns: Boolean indicating validity

            Example:
            ```sapl
            policy "example"
            permit
            where
                numeral.isValidOctal(resource.file.permissions);
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
     * Pads a string with leading zeros to reach the specified width. Does not
     * truncate if
     * the string is already longer than the width.
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
