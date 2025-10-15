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
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Provides encoding and decoding functions for working with different data
 * representations commonly used in cryptographic operations.
 * <p>
 * Supports Base64 (standard and URL-safe variants) and hexadecimal encoding
 * for converting between text and encoded representations. These functions
 * are essential for working with cryptographic data like signatures,
 * certificates, and message authentication codes.
 * <p>
 * All decoding functions are lenient by default, accepting input with or
 * without proper padding. Strict variants are available for cases requiring
 * RFC-compliant validation with proper padding.
 */
@UtilityClass
@FunctionLibrary(name = EncodingFunctionLibrary.NAME, description = EncodingFunctionLibrary.DESCRIPTION)
public class EncodingFunctionLibrary {

    public static final String NAME        = "encoding";
    public static final String DESCRIPTION = "Encoding and decoding functions for Base64 and hexadecimal representations used in cryptographic operations.";

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

    /* Base64 Standard Encoding */

    @Function(docs = """
            ```base64Encode(TEXT data)```: Encodes text data to Base64 standard format.

            Uses the standard Base64 alphabet with '+' and '/' characters. Includes padding
            with '=' characters to ensure the output length is a multiple of 4.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64Encode("hello") == "aGVsbG8=";
              encoding.base64Encode("hello world") == "aGVsbG8gd29ybGQ=";
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64Encode(@Text Val data) {
        try {
            val encoded = Base64.getEncoder().encodeToString(data.getText().getBytes(StandardCharsets.UTF_8));
            return Val.of(encoded);
        } catch (Exception exception) {
            return Val.error("Failed to Base64 encode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```base64Decode(TEXT data)```: Decodes Base64 standard format to text (lenient).

            Decodes data encoded with the standard Base64 alphabet. This function is lenient
            and accepts input with or without proper padding. For strict RFC-compliant
            validation that requires proper padding, use base64DecodeStrict.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64Decode("aGVsbG8=") == "hello";
              encoding.base64Decode("aGVsbG8") == "hello";  // lenient: missing padding accepted
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64Decode(@Text Val data) {
        try {
            val decoded = new String(Base64.getDecoder().decode(data.getText()), StandardCharsets.UTF_8);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            return Val.error("Invalid Base64 data: " + exception.getMessage());
        } catch (Exception exception) {
            return Val.error("Failed to Base64 decode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```base64DecodeStrict(TEXT data)```: Decodes Base64 standard format to text (strict).

            Decodes data encoded with the standard Base64 alphabet with strict validation.
            Requires proper padding with '=' characters and input length to be a multiple of 4.
            Rejects improperly formatted input that would be accepted by the lenient decoder.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64DecodeStrict("aGVsbG8=") == "hello";
              // encoding.base64DecodeStrict("aGVsbG8") results in error (missing padding)
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64DecodeStrict(@Text Val data) {
        val input = data.getText();
        if (!isValidBase64Format(input, false)) {
            return Val.error("Invalid Base64 data: input must be properly padded and have length multiple of 4");
        }
        return base64Decode(data);
    }

    @Function(docs = """
            ```isValidBase64(TEXT data)```: Checks whether text is valid Base64 standard format (lenient).

            Validates that the text can be successfully decoded as Base64. This function is
            lenient and accepts input with or without proper padding. For strict RFC-compliant
            validation that requires proper padding, use isValidBase64Strict.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.isValidBase64("aGVsbG8=") == true;
              encoding.isValidBase64("aGVsbG8") == true;  // lenient: missing padding accepted
              encoding.isValidBase64("invalid!@#") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBase64(@Text Val data) {
        try {
            Base64.getDecoder().decode(data.getText());
            return Val.of(true);
        } catch (IllegalArgumentException exception) {
            return Val.of(false);
        }
    }

    @Function(docs = """
            ```isValidBase64Strict(TEXT data)```: Checks whether text is valid Base64 standard format (strict).

            Validates that the text is properly formatted Base64 with required padding.
            Requires input length to be a multiple of 4 and padding characters to appear
            only at the end if present. Rejects improperly formatted input that would be
            accepted by the lenient validator.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.isValidBase64Strict("aGVsbG8=") == true;
              encoding.isValidBase64Strict("aGVsbG8") == false;  // strict: missing padding rejected
              encoding.isValidBase64Strict("invalid!@#") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBase64Strict(@Text Val data) {
        return Val.of(isValidBase64Format(data.getText(), false));
    }

    /* Base64 URL-Safe Encoding */

    @Function(docs = """
            ```base64UrlEncode(TEXT data)```: Encodes text data to Base64 URL-safe format.

            Uses the URL-safe Base64 alphabet with '-' and '_' instead of '+' and '/'.
            This encoding is safe to use in URLs and filenames. Includes padding by default.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64UrlEncode("hello") == "aGVsbG8=";
              encoding.base64UrlEncode("test?data") == "dGVzdD9kYXRh";
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64UrlEncode(@Text Val data) {
        try {
            val encoded = Base64.getUrlEncoder().encodeToString(data.getText().getBytes(StandardCharsets.UTF_8));
            return Val.of(encoded);
        } catch (Exception exception) {
            return Val.error("Failed to Base64 URL encode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```base64UrlDecode(TEXT data)```: Decodes Base64 URL-safe format to text (lenient).

            Decodes data encoded with the URL-safe Base64 alphabet. This function is lenient
            and accepts both padded and unpadded input. For strict validation that requires
            proper padding, use base64UrlDecodeStrict.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64UrlDecode("aGVsbG8=") == "hello";
              encoding.base64UrlDecode("aGVsbG8") == "hello";  // lenient: unpadded accepted
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64UrlDecode(@Text Val data) {
        try {
            val decoded = new String(Base64.getUrlDecoder().decode(data.getText()), StandardCharsets.UTF_8);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            return Val.error("Invalid Base64 URL data: " + exception.getMessage());
        } catch (Exception exception) {
            return Val.error("Failed to Base64 URL decode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```base64UrlDecodeStrict(TEXT data)```: Decodes Base64 URL-safe format to text (strict).

            Decodes data encoded with the URL-safe Base64 alphabet with strict validation.
            Requires proper padding with '=' characters and input length to be a multiple of 4.
            Rejects improperly formatted input that would be accepted by the lenient decoder.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64UrlDecodeStrict("aGVsbG8=") == "hello";
              // encoding.base64UrlDecodeStrict("aGVsbG8") results in error (missing padding)
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64UrlDecodeStrict(@Text Val data) {
        val input = data.getText();
        if (!isValidBase64Format(input, true)) {
            return Val.error("Invalid Base64 URL data: input must be properly padded and have length multiple of 4");
        }
        return base64UrlDecode(data);
    }

    @Function(docs = """
            ```isValidBase64Url(TEXT data)```: Checks whether text is valid Base64 URL-safe format (lenient).

            Validates that the text can be successfully decoded as URL-safe Base64. This
            function is lenient and accepts both padded and unpadded input. For strict
            validation that requires proper padding, use isValidBase64UrlStrict.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.isValidBase64Url("aGVsbG8=") == true;
              encoding.isValidBase64Url("aGVsbG8") == true;  // lenient: unpadded accepted
              encoding.isValidBase64Url("invalid+/") == false;  // wrong alphabet
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBase64Url(@Text Val data) {
        try {
            Base64.getUrlDecoder().decode(data.getText());
            return Val.of(true);
        } catch (IllegalArgumentException exception) {
            return Val.of(false);
        }
    }

    @Function(docs = """
            ```isValidBase64UrlStrict(TEXT data)```: Checks whether text is valid Base64 URL-safe format (strict).

            Validates that the text is properly formatted URL-safe Base64 with required padding.
            Requires input length to be a multiple of 4 and padding characters to appear only
            at the end if present. Rejects improperly formatted input that would be accepted
            by the lenient validator.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.isValidBase64UrlStrict("aGVsbG8=") == true;
              encoding.isValidBase64UrlStrict("aGVsbG8") == false;  // strict: missing padding rejected
              encoding.isValidBase64UrlStrict("invalid+/") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidBase64UrlStrict(@Text Val data) {
        return Val.of(isValidBase64Format(data.getText(), true));
    }

    /* Hexadecimal Encoding */

    @Function(docs = """
            ```hexEncode(TEXT data)```: Encodes text data to hexadecimal representation.

            Converts each byte of the UTF-8 encoded text to two hexadecimal digits.
            Output uses lowercase letters (a-f).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.hexEncode("hello") == "68656c6c6f";
              encoding.hexEncode("A") == "41";
            ```
            """, schema = RETURNS_TEXT)
    public static Val hexEncode(@Text Val data) {
        try {
            val bytes   = data.getText().getBytes(StandardCharsets.UTF_8);
            val encoded = HexFormat.of().formatHex(bytes);
            return Val.of(encoded);
        } catch (Exception exception) {
            return Val.error("Failed to hex encode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```hexDecode(TEXT data)```: Decodes hexadecimal representation to text.

            Converts pairs of hexadecimal digits back to bytes and interprets as UTF-8 text.
            Accepts both uppercase and lowercase letters. Underscores are allowed as separators.
            The input must have an even number of hex characters (excluding underscores).

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.hexDecode("68656c6c6f") == "hello";
              encoding.hexDecode("68656C6C6F") == "hello";  // uppercase works
              encoding.hexDecode("68_65_6c_6c_6f") == "hello";  // underscores allowed
            ```
            """, schema = RETURNS_TEXT)
    public static Val hexDecode(@Text Val data) {
        try {
            val cleanedInput = data.getText().strip().replace("_", "");
            val bytes        = HexFormat.of().parseHex(cleanedInput);
            val decoded      = new String(bytes, StandardCharsets.UTF_8);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            return Val.error("Invalid hexadecimal data: " + exception.getMessage());
        } catch (Exception exception) {
            return Val.error("Failed to hex decode data: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```isValidHex(TEXT data)```: Checks whether text is valid hexadecimal representation.

            Validates that the text contains only hexadecimal characters (0-9, a-f, A-F) and
            has an even number of characters. Underscores are allowed as separators and do
            not count toward the character count requirement.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.isValidHex("68656c6c6f") == true;
              encoding.isValidHex("68656C6C6F") == true;
              encoding.isValidHex("68_65_6c_6c_6f") == true;
              encoding.isValidHex("xyz") == false;
              encoding.isValidHex("123") == false;  // odd number of characters
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidHex(@Text Val data) {
        try {
            val cleanedInput = data.getText().strip().replace("_", "");
            if (cleanedInput.isEmpty() || cleanedInput.length() % 2 != 0) {
                return Val.of(false);
            }
            HexFormat.of().parseHex(cleanedInput);
            return Val.of(true);
        } catch (IllegalArgumentException exception) {
            return Val.of(false);
        }
    }

    /* Helper Methods */

    /**
     * Validates Base64 format with strict padding requirements.
     *
     * @param input the Base64 string to validate
     * @param urlSafe whether to validate using URL-safe alphabet
     * @return true if the input is properly formatted with correct padding
     */
    private static boolean isValidBase64Format(String input, boolean urlSafe) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Must be multiple of 4
        if (input.length() % 4 != 0) {
            return false;
        }

        // Check for valid characters and proper padding placement
        val validChars = urlSafe ? "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_="
                : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

        var paddingFound = false;
        for (var i = 0; i < input.length(); i++) {
            val ch = input.charAt(i);

            if (ch == '=') {
                paddingFound = true;
            } else if (paddingFound) {
                // Non-padding character after padding
                return false;
            }

            if (validChars.indexOf(ch) == -1) {
                return false;
            }
        }

        // Verify it can actually be decoded
        try {
            if (urlSafe) {
                Base64.getUrlDecoder().decode(input);
            } else {
                Base64.getDecoder().decode(input);
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
