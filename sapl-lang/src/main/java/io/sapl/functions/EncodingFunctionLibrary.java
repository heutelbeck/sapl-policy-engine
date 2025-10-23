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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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
 * <p>
 * <strong>Security Considerations:</strong>
 * <ul>
 * <li>Input length is limited to {@value #MAX_INPUT_LENGTH} characters to
 * prevent resource exhaustion attacks
 * <li>All decoded output is validated as proper UTF-8 to prevent injection of
 * invalid character sequences
 * <li>Error messages are sanitized to prevent information leakage
 * <li>Validation functions use early rejection to prevent CPU exhaustion
 * <li>Character comparisons use constant-time operations where appropriate to
 * prevent timing attacks
 * </ul>
 */
@Slf4j
@UtilityClass
@FunctionLibrary(name = EncodingFunctionLibrary.NAME, description = EncodingFunctionLibrary.DESCRIPTION)
public class EncodingFunctionLibrary {

    public static final String NAME        = "encoding";
    public static final String DESCRIPTION = "Encoding and decoding functions for Base64 and hexadecimal representations used in cryptographic operations.";

    /**
     * Maximum allowed input length in characters to prevent resource exhaustion
     * attacks.
     * This limit applies to both encoded and decoded strings.
     */
    private static final int MAX_INPUT_LENGTH = 10_000_000; // 10MB

    private static final String BASE64_ALPHABET     = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    private static final String BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_=";

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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 encode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        val encoded = Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
        return Val.of(encoded);
    }

    @Function(docs = """
            ```base64Decode(TEXT data)```: Decodes Base64 standard format to text (lenient).

            Decodes data encoded with the standard Base64 alphabet. This function is lenient
            and accepts input with or without proper padding. For strict RFC-compliant
            validation that requires proper padding, use base64DecodeStrict.

            The decoded output is validated as proper UTF-8. Invalid UTF-8 sequences will
            result in an error.

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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 decode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        try {
            val bytes   = Base64.getDecoder().decode(input);
            val decoded = decodeUtf8(bytes);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            log.debug("Invalid Base64 data", exception);
            return Val.error("Invalid Base64 data.");
        } catch (CharacterCodingException exception) {
            log.debug("Base64 decoded data contains invalid UTF-8", exception);
            return Val.error("Decoded data contains invalid UTF-8 sequences.");
        }
    }

    @Function(docs = """
            ```base64DecodeStrict(TEXT data)```: Decodes Base64 standard format to text (strict).

            Decodes data encoded with the standard Base64 alphabet with strict validation.
            Requires proper padding with '=' characters and input length to be a multiple of 4.
            Rejects improperly formatted input that would be accepted by the lenient decoder.

            The decoded output is validated as proper UTF-8. Invalid UTF-8 sequences will
            result in an error.

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

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 strict decode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        if (!isValidBase64Format(input, false)) {
            return Val.error("Invalid Base64 data: input must be properly padded and have length multiple of 4.");
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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            return Val.of(false);
        }

        try {
            Base64.getDecoder().decode(input);
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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 URL encode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        val encoded = Base64.getUrlEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
        return Val.of(encoded);
    }

    @Function(docs = """
            ```base64UrlDecode(TEXT data)```: Decodes Base64 URL-safe format to text (lenient).

            Decodes data encoded with the URL-safe Base64 alphabet. This function is lenient
            and accepts input with or without proper padding. For strict RFC-compliant
            validation that requires proper padding, use base64UrlDecodeStrict.

            The decoded output is validated as proper UTF-8. Invalid UTF-8 sequences will
            result in an error.

            **Examples:**
            ```sapl
            policy "example"
            permit
            where
              encoding.base64UrlDecode("aGVsbG8=") == "hello";
              encoding.base64UrlDecode("aGVsbG8") == "hello";  // lenient: missing padding accepted
            ```
            """, schema = RETURNS_TEXT)
    public static Val base64UrlDecode(@Text Val data) {
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 URL decode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        try {
            val bytes   = Base64.getUrlDecoder().decode(input);
            val decoded = decodeUtf8(bytes);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            log.debug("Invalid Base64 URL data", exception);
            return Val.error("Invalid Base64 URL data.");
        } catch (CharacterCodingException exception) {
            log.debug("Base64 URL decoded data contains invalid UTF-8", exception);
            return Val.error("Decoded data contains invalid UTF-8 sequences.");
        }
    }

    @Function(docs = """
            ```base64UrlDecodeStrict(TEXT data)```: Decodes Base64 URL-safe format to text (strict).

            Decodes data encoded with the URL-safe Base64 alphabet with strict validation.
            Requires proper padding with '=' characters and input length to be a multiple of 4.
            Rejects improperly formatted input that would be accepted by the lenient decoder.

            The decoded output is validated as proper UTF-8. Invalid UTF-8 sequences will
            result in an error.

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

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Base64 URL strict decode attempted with input length {}, exceeds maximum {}", input.length(),
                    MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        if (!isValidBase64Format(input, true)) {
            return Val.error("Invalid Base64 URL data: input must be properly padded and have length multiple of 4.");
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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            return Val.of(false);
        }

        try {
            Base64.getUrlDecoder().decode(input);
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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Hex encode attempted with input length {}, exceeds maximum {}", input.length(), MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        val bytes   = input.getBytes(StandardCharsets.UTF_8);
        val encoded = HexFormat.of().formatHex(bytes);
        return Val.of(encoded);
    }

    @Function(docs = """
            ```hexDecode(TEXT data)```: Decodes hexadecimal representation to text.

            Converts pairs of hexadecimal digits back to bytes and interprets as UTF-8 text.
            Accepts both uppercase and lowercase letters. Underscores are allowed as separators.
            The input must have an even number of hex characters (excluding underscores).

            The decoded output is validated as proper UTF-8. Invalid UTF-8 sequences will
            result in an error.

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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Hex decode attempted with input length {}, exceeds maximum {}", input.length(), MAX_INPUT_LENGTH);
            return Val.error("Input exceeds maximum allowed length.");
        }

        try {
            val cleanedInput = input.strip().replace("_", "");
            val bytes        = HexFormat.of().parseHex(cleanedInput);
            val decoded      = decodeUtf8(bytes);
            return Val.of(decoded);
        } catch (IllegalArgumentException exception) {
            log.debug("Invalid hexadecimal data", exception);
            return Val.error("Invalid hexadecimal data.");
        } catch (CharacterCodingException exception) {
            log.debug("Hex decoded data contains invalid UTF-8", exception);
            return Val.error("Decoded data contains invalid UTF-8 sequences.");
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
        val input = data.getText();

        if (input.length() > MAX_INPUT_LENGTH) {
            return Val.of(false);
        }

        try {
            val cleanedInput = input.strip().replace("_", "");
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
     * Decodes a byte array as UTF-8 with strict validation.
     * <p>
     * This method ensures that the decoded string contains only valid UTF-8
     * character
     * sequences. Any malformed or unmappable characters will cause a
     * CharacterCodingException to be thrown.
     *
     * @param bytes the bytes to decode as UTF-8
     * @return the decoded string
     * @throws CharacterCodingException if the bytes do not represent valid UTF-8
     */
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        val buffer  = decoder.decode(ByteBuffer.wrap(bytes));
        return buffer.toString();
    }

    /**
     * Validates Base64 format with strict padding requirements.
     * <p>
     * Validates that the input satisfies all of the following requirements:
     * <ul>
     * <li>Non-null and non-empty
     * <li>Length does not exceed maximum allowed length
     * <li>Length is a multiple of 4
     * <li>Contains only valid Base64 alphabet characters
     * <li>Padding characters (=) appear only at the end, never in the middle
     * <li>Can be successfully decoded by the appropriate decoder
     * </ul>
     * <p>
     * This method enforces strict RFC-compliant Base64 validation that the
     * standard JDK Base64.Decoder does not provide, as the JDK decoder is
     * lenient and accepts unpadded input.
     * <p>
     * <strong>Performance:</strong> Uses early rejection optimizations to quickly
     * reject obviously invalid input before performing expensive validation.
     *
     * @param input the Base64 string to validate
     * @param urlSafe whether to validate using URL-safe alphabet (- and _ instead
     * of + and /)
     * @return true if the input is properly formatted with correct padding, false
     * otherwise
     */
    private static boolean isValidBase64Format(String input, boolean urlSafe) {
        if (input == null || input.isEmpty() || input.length() > MAX_INPUT_LENGTH) {
            return false;
        }

        if (input.length() % 4 != 0) {
            return false;
        }

        // Early rejection: check for obviously invalid characters
        for (var i = 0; i < input.length(); i++) {
            val ch = input.charAt(i);
            if (ch < '+' || ch > 'z') {
                return false;
            }
        }

        val validChars = urlSafe ? BASE64_URL_ALPHABET : BASE64_ALPHABET;

        var paddingFound = false;
        for (var i = 0; i < input.length(); i++) {
            val ch = input.charAt(i);

            if (ch == '=') {
                paddingFound = true;
            } else if (paddingFound) {
                return false;
            }

            if (!containsChar(validChars, ch)) {
                return false;
            }
        }

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

    /**
     * Performs a constant-time check if a string contains a given character.
     * <p>
     * This method is designed to prevent timing attacks by always examining every
     * character in the string, regardless of whether a match is found early.
     *
     * @param str the string to search
     * @param ch the character to search for
     * @return true if the string contains the character, false otherwise
     */
    private static boolean containsChar(String str, char ch) {
        var found = false;
        for (var i = 0; i < str.length(); i++) {
            found |= (str.charAt(i) == ch);
        }
        return found;
    }
}
