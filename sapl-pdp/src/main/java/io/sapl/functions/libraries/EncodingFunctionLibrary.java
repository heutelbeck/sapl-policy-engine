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
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Provides encoding and decoding functions for working with different data
 * representations commonly used in
 * cryptographic operations.
 * <p>
 * Supports Base64 (standard and URL-safe variants) and hexadecimal encoding for
 * converting between text and encoded
 * representations. These functions are essential for working with cryptographic
 * data like signatures, certificates, and
 * message authentication codes.
 * <p>
 * All decoding functions are lenient by default, accepting input with or
 * without proper padding. Strict variants are
 * available for cases requiring RFC-compliant validation with proper padding.
 * <p>
 * <strong>Security Considerations:</strong>
 * <ul>
 * <li>Input length is limited to {@value #MAX_INPUT_LENGTH} characters to
 * prevent resource exhaustion attacks
 * <li>All decoded output is validated as proper UTF-8 to prevent injection of
 * invalid character sequences
 * <li>Error messages are sanitized to prevent information leakage
 * </ul>
 */
@FunctionLibrary(name = EncodingFunctionLibrary.NAME, description = EncodingFunctionLibrary.DESCRIPTION, libraryDocumentation = EncodingFunctionLibrary.DOCUMENTATION)
public class EncodingFunctionLibrary {

    public static final String NAME          = "encoding";
    public static final String DESCRIPTION   = "Encoding and decoding functions for Base64 and hexadecimal representations.";
    public static final String DOCUMENTATION = """
            # Encoding Functions

            Convert between text and encoded representations (Base64, hexadecimal) for
            working with cryptographic data, URLs, and binary content.

            ## Encoding Variants

            | Format          | Characters      | Use Case                          |
            |-----------------|-----------------|-----------------------------------|
            | Base64          | A-Z, a-z, +, /  | General binary-to-text            |
            | Base64 URL-safe | A-Z, a-z, -, _  | URLs, filenames, JWT tokens       |
            | Hexadecimal     | 0-9, a-f        | Hashes, byte inspection           |

            ## Lenient vs Strict Decoding

            Both Base64 alphabets require zero unused pad bits and accept the empty string.
            Lenient functions accept complete encodings with or without padding. Strict functions
            require the padding produced by the encoder. For example, `Zg==` encodes `f`. The
            lenient decoder also reads `Zg`. Both forms of the decoder reject `Zh==`.

            ```sapl
            policy "validate token format"
            permit
                action == "authenticate"
            where
                encoding.isValidBase64UrlStrict(resource.token);
            ```

            ## Working with Cryptographic Data

            Decode Base64-encoded signatures or certificates for verification:

            ```sapl
            policy "verify signature format"
            permit
            where
                var sig = encoding.base64Decode(resource.signature);
                standard.length(sig) > 0;
            ```

            ## Limits

            To bound memory and computation on untrusted input, the following limits apply:

            - Input length is capped at 10000000 characters (10 MB). This applies to every encode and decode function, base64Encode, base64Decode, base64DecodeStrict, base64UrlEncode, base64UrlDecode, base64UrlDecodeStrict, and hexEncode, hexDecode, as well as the validation functions isValidBase64, isValidBase64Strict, isValidBase64Url, isValidBase64UrlStrict, and isValidHex. Encode and decode functions return an error when the input exceeds this length. The validation functions return false.

            These limits apply because this input may originate from the authorization subscription or from policy information points, which are not vetted to the same degree as the policies and variables shipped with the PDP configuration.
            """;

    /**
     * Maximum allowed input length in characters to prevent resource exhaustion
     * attacks. This limit applies to both
     * encoded and decoded strings.
     */
    private static final int MAX_INPUT_LENGTH = 10_000_000; // 10MB

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

    private static final String EMPTY_STRING = "";
    private static final String UNDERSCORE   = "_";

    private static final String ERROR_DECODED_INVALID_UTF8     = "Decoded data contains invalid UTF-8 sequences: %s";
    private static final String ERROR_INPUT_EXCEEDS_MAX_LENGTH = "Input length %d exceeds maximum allowed length of %d.";
    private static final String ERROR_INVALID_BASE64           = "Expected canonical %s data with %s padding.";
    private static final String ERROR_INVALID_HEX              = "Invalid hexadecimal data: %s";

    @Function(docs = """
            ```base64Encode(TEXT data)```: Encodes text data to Base64 standard format.

            Uses the standard Base64 alphabet with '+' and '/' characters. Includes padding
            with '=' characters to ensure the output length is a multiple of 4.

            **Examples:**
            ```sapl
            policy "example"
            permit
              encoding.base64Encode("hello") == "aGVsbG8=";
              encoding.base64Encode("hello world") == "aGVsbG8gd29ybGQ=";
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64Encode(TextValue input) {
        if (input.value().length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_EXCEEDS_MAX_LENGTH.formatted(input.value().length(), MAX_INPUT_LENGTH));
        }
        val bytes   = input.value().getBytes(StandardCharsets.UTF_8);
        val encoded = Base64.getEncoder().encodeToString(bytes);
        return Value.of(encoded);
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
              encoding.base64Decode("aGVsbG8=") == "hello";
              encoding.base64Decode("aGVsbG8") == "hello";  // lenient: missing padding accepted
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64Decode(TextValue data) {
        return decodeBase64Text(data.value(), false, false);
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
              encoding.base64DecodeStrict("aGVsbG8=") == "hello";
              // encoding.base64DecodeStrict("aGVsbG8") results in error (missing padding)
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64DecodeStrict(TextValue data) {
        return decodeBase64Text(data.value(), false, true);
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
              encoding.isValidBase64("aGVsbG8=") == true;
              encoding.isValidBase64("aGVsbG8") == true;  // lenient: missing padding accepted
              encoding.isValidBase64("invalid!@#") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidBase64(TextValue data) {
        return validBase64(data.value(), false, false);
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
              encoding.isValidBase64Strict("aGVsbG8=") == true;
              encoding.isValidBase64Strict("aGVsbG8") == false;  // strict: missing padding rejected
              encoding.isValidBase64Strict("invalid!@#") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidBase64Strict(TextValue data) {
        return validBase64(data.value(), false, true);
    }

    @Function(docs = """
            ```base64UrlEncode(TEXT data)```: Encodes text data to Base64 URL-safe format.

            Uses the URL-safe Base64 alphabet with '-' and '_' instead of '+' and '/'.
            This encoding is safe to use in URLs and filenames. Includes padding by default.

            **Examples:**
            ```sapl
            policy "example"
            permit
              encoding.base64UrlEncode("hello") == "aGVsbG8=";
              encoding.base64UrlEncode("test?data") == "dGVzdD9kYXRh";
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64UrlEncode(TextValue data) {
        val input = data.value();
        if (input.length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_EXCEEDS_MAX_LENGTH.formatted(input.length(), MAX_INPUT_LENGTH));
        }
        val encoded = Base64.getUrlEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
        return Value.of(encoded);
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
              encoding.base64UrlDecode("aGVsbG8=") == "hello";
              encoding.base64UrlDecode("aGVsbG8") == "hello";  // lenient: missing padding accepted
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64UrlDecode(TextValue data) {
        return decodeBase64Text(data.value(), true, false);
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
              encoding.base64UrlDecodeStrict("aGVsbG8=") == "hello";
              // encoding.base64UrlDecodeStrict("aGVsbG8") results in error (missing padding)
            ```
            """, schema = RETURNS_TEXT)
    public static Value base64UrlDecodeStrict(TextValue data) {
        return decodeBase64Text(data.value(), true, true);
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
              encoding.isValidBase64Url("aGVsbG8=") == true;
              encoding.isValidBase64Url("aGVsbG8") == true;  // lenient: unpadded accepted
              encoding.isValidBase64Url("invalid+/") == false;  // wrong alphabet
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidBase64Url(TextValue data) {
        return validBase64(data.value(), true, false);
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
              encoding.isValidBase64UrlStrict("aGVsbG8=") == true;
              encoding.isValidBase64UrlStrict("aGVsbG8") == false;  // strict: missing padding rejected
              encoding.isValidBase64UrlStrict("invalid+/") == false;
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidBase64UrlStrict(TextValue data) {
        return validBase64(data.value(), true, true);
    }

    @Function(docs = """
            ```hexEncode(TEXT data)```: Encodes text data to hexadecimal representation.

            Converts each byte of the UTF-8 encoded text to two hexadecimal digits.
            Output uses lowercase letters (a-f).

            **Examples:**
            ```sapl
            policy "example"
            permit
              encoding.hexEncode("hello") == "68656c6c6f";
              encoding.hexEncode("A") == "41";
            ```
            """, schema = RETURNS_TEXT)
    public static Value hexEncode(TextValue data) {
        val input = data.value();
        if (input.length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_EXCEEDS_MAX_LENGTH.formatted(input.length(), MAX_INPUT_LENGTH));
        }
        val bytes   = input.getBytes(StandardCharsets.UTF_8);
        val encoded = HexFormat.of().formatHex(bytes);
        return Value.of(encoded);
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
              encoding.hexDecode("68656c6c6f") == "hello";
              encoding.hexDecode("68656C6C6F") == "hello";  // uppercase works
              encoding.hexDecode("68_65_6c_6c_6f") == "hello";  // underscores allowed
            ```
            """, schema = RETURNS_TEXT)
    public static Value hexDecode(TextValue data) {
        val input = data.value();
        if (input.length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_EXCEEDS_MAX_LENGTH.formatted(input.length(), MAX_INPUT_LENGTH));
        }
        try {
            val cleanedInput = input.strip().replace(UNDERSCORE, EMPTY_STRING);
            val bytes        = HexFormat.of().parseHex(cleanedInput);
            val decoded      = decodeUtf8(bytes);
            return Value.of(decoded);
        } catch (IllegalArgumentException exception) {
            return Value.error(ERROR_INVALID_HEX.formatted(exception.getMessage()));
        } catch (CharacterCodingException exception) {
            return Value.error(ERROR_DECODED_INVALID_UTF8.formatted(exception.getMessage()));
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
              encoding.isValidHex("68656c6c6f") == true;
              encoding.isValidHex("68656C6C6F") == true;
              encoding.isValidHex("68_65_6c_6c_6f") == true;
              encoding.isValidHex("xyz") == false;
              encoding.isValidHex("123") == false;  // odd number of characters
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isValidHex(TextValue data) {
        val input = data.value();

        if (input.length() > MAX_INPUT_LENGTH) {
            return Value.of(false);
        }

        try {
            val cleanedInput = input.strip().replace(UNDERSCORE, EMPTY_STRING);
            if (cleanedInput.isEmpty() || cleanedInput.length() % 2 != 0) {
                return Value.of(false);
            }
            HexFormat.of().parseHex(cleanedInput);
            return Value.of(true);
        } catch (IllegalArgumentException exception) {
            return Value.of(false);
        }
    }

    /**
     * Decodes a byte array as UTF-8 with strict validation.
     * <p>
     * This method ensures that the decoded string contains only valid UTF-8
     * character sequences. Any malformed or
     * unmappable characters will cause a CharacterCodingException to be thrown.
     *
     * @param bytes
     * the bytes to decode as UTF-8
     *
     * @return the decoded string
     *
     * @throws CharacterCodingException
     * if the bytes do not represent valid UTF-8
     */
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        val buffer  = decoder.decode(ByteBuffer.wrap(bytes));
        return buffer.toString();
    }

    private static Value decodeBase64Text(String input, boolean urlSafe, boolean paddingRequired) {
        if (input.length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_EXCEEDS_MAX_LENGTH.formatted(input.length(), MAX_INPUT_LENGTH));
        }
        val bytes = canonicalBase64Bytes(input, urlSafe, paddingRequired);
        if (bytes == null) {
            return Value.error(ERROR_INVALID_BASE64, urlSafe ? "Base64 URL" : "Base64",
                    paddingRequired ? "required" : "optional");
        }
        try {
            return Value.of(decodeUtf8(bytes));
        } catch (CharacterCodingException e) {
            return Value.error(ERROR_DECODED_INVALID_UTF8.formatted(e.getMessage()));
        }
    }

    private static Value validBase64(String input, boolean urlSafe, boolean paddingRequired) {
        return Value.of(
                input.length() <= MAX_INPUT_LENGTH && canonicalBase64Bytes(input, urlSafe, paddingRequired) != null);
    }

    private static byte @Nullable [] canonicalBase64Bytes(String input, boolean urlSafe, boolean paddingRequired) {
        val decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
        val encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
        try {
            val bytes = decoder.decode(input);
            val form  = paddingRequired || input.endsWith("=") ? encoder : encoder.withoutPadding();
            return form.encodeToString(bytes).equals(input) ? bytes : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
