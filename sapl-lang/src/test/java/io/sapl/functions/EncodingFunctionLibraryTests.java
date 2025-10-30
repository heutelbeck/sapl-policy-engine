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

import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class EncodingFunctionLibraryTests {

    /* Encoding Tests */

    @ParameterizedTest
    @CsvSource({ "Cthulhu,           Q3RodWxodQ==",       // "Cthulhu"
            "Necronomicon,      TmVjcm9ub21pY29u",   // "Necronomicon"
            "'',                ''"                  // empty string
    })
    void base64Encode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64Encode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64Encode_whenSpecialCharacters_encodesCorrectly() {
        var result = EncodingFunctionLibrary.base64Encode(Val.of("Yog-Sothoth!@#$%"));
        assertThat(result.isDefined()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "Azathoth,     QXphdGhvdGg=",    // "Azathoth"
            "R'lyeh?data,  UidseWVoP2RhdGE="  // "R'lyeh?data"
    })
    void base64UrlEncode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64UrlEncode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64UrlEncode_whenSpecialCharacters_encodesWithUrlSafeAlphabet() {
        var result = EncodingFunctionLibrary.base64UrlEncode(Val.of("Nyarlathotep?query"));
        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).doesNotContain("+", "/");
    }

    @ParameterizedTest
    @CsvSource({ "Yog-Sothoth,      596f672d536f74686f7468",  // "Yog-Sothoth"
            "Y,                59",                      // "Y"
            "'',               ''"                       // empty string
    })
    void hexEncode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.hexEncode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void hexEncode_whenSpecialCharacters_encodesCorrectly() {
        var result = EncodingFunctionLibrary.hexEncode(Val.of("Ia! Ia!"));
        assertThat(result.isDefined()).isTrue();
    }

    /* Decoding Tests - Lenient */

    @ParameterizedTest
    @CsvSource({ "Q3RodWxodQ==,         Cthulhu",       // "Cthulhu" with padding
            "TmVjcm9ub21pY29u,     Necronomicon",  // "Necronomicon" no padding needed
            "Q3RodWxodQ,           Cthulhu"        // "Cthulhu" lenient accepts missing padding
    })
    void base64Decode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64Decode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64Decode_whenInvalidBase64_returnsError() {
        var result = EncodingFunctionLibrary.base64Decode(Val.of("Hastur!@#"));
        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "QXphdGhvdGg=,  Azathoth",  // "Azathoth" with padding
            "QXphdGhvdGg,   Azathoth"   // "Azathoth" lenient accepts missing padding
    })
    void base64UrlDecode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64UrlDecode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64UrlDecode_whenInvalidBase64Url_returnsError() {
        var result = EncodingFunctionLibrary.base64UrlDecode(Val.of("Dagon!@#"));
        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "59,                   Y",         // "Y"
            "59_20_59,             Y Y",       // "Y Y" with underscore separators
            "4e7963726172,         Nycrar",    // "Nycrar"
            "4E7963726172,         Nycrar"     // "Nycrar" uppercase
    })
    void hexDecode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.hexDecode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Kadath", "123" })
    void hexDecode_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.hexDecode(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    /* Decoding Tests - Strict */

    @Test
    void base64DecodeStrict_whenProperlyPadded_decodesCorrectly() {
        var result = EncodingFunctionLibrary.base64DecodeStrict(Val.of("Q3RodWxodQ=="));
        assertThat(result.getText()).isEqualTo("Cthulhu");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Q3RodWxodQ", "Shoggoth!@#", "abc" })
    void base64DecodeStrict_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.base64DecodeStrict(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void base64UrlDecodeStrict_whenProperlyPadded_decodesCorrectly() {
        var result = EncodingFunctionLibrary.base64UrlDecodeStrict(Val.of("QXphdGhvdGg="));
        assertThat(result.getText()).isEqualTo("Azathoth");
    }

    @ParameterizedTest
    @ValueSource(strings = { "QXphdGhvdGg", "Elder!@#", "abc" })
    void base64UrlDecodeStrict_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.base64UrlDecodeStrict(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    /* Validation Tests - Lenient */

    @ParameterizedTest
    @CsvSource({ "Q3RodWxodQ==,     true",   // "Cthulhu" with padding
            "Q3RodWxodQ,      true",    // "Cthulhu" without padding (lenient)
            "Miskatonic!@#,   false"    // invalid characters
    })
    void isValidBase64_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "QXphdGhvdGg=,     true",   // "Azathoth" with padding
            "QXphdGhvdGg,     true",    // "Azathoth" without padding (lenient)
            "Elder+/==,       false"    // wrong alphabet (+ and /)
    })
    void isValidBase64Url_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64Url(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "59,                   true",   // "Y"
            "4e7963726172,         true",   // "Nycrar"
            "59_20_59,             true",   // "Y Y" with underscores
            "Innsmouth,            false",  // non-hex characters
            "123,                  false",  // odd length
            "'',                   false"   // empty string
    })
    void isValidHex_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidHex(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    /* Validation Tests - Strict */

    @ParameterizedTest
    @CsvSource({ "Q3RodWxodQ==,     true",   // "Cthulhu" properly padded
            "Q3RodWxodQ,      false",   // "Cthulhu" missing padding (strict rejects)
            "Arkham!@#,       false",   // invalid characters
            "abc,             false",   // wrong length
            "Q3=RodWxodQ=,    false"    // padding in middle
    })
    void isValidBase64Strict_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64Strict(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "QXphdGhvdGg=,     true",   // "Azathoth" properly padded
            "QXphdGhvdGg,     false",   // "Azathoth" missing padding (strict rejects)
            "Dunwich!@#,      false",   // invalid characters
            "abc,             false",   // wrong length
            "Elder+/==,       false",   // wrong alphabet
            "QX=pdGhvdGg=,    false"    // padding in middle
    })
    void isValidBase64UrlStrict_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64UrlStrict(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    /* Lenient vs Strict Comparison Tests */

    @ParameterizedTest
    @ValueSource(strings = { "Q3RodWxodQ",    // "Cthulhu" unpadded
            "QXphdGhvdGg",   // "Azathoth" unpadded
            "RGFnb24"        // "Dagon" unpadded
    })
    void lenientAcceptsUnpaddedButStrictRejects(String unpaddedInput) {
        var lenientResult = EncodingFunctionLibrary.isValidBase64(Val.of(unpaddedInput));
        var strictResult  = EncodingFunctionLibrary.isValidBase64Strict(Val.of(unpaddedInput));

        assertThat(lenientResult.getBoolean()).isTrue();
        assertThat(strictResult.getBoolean()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Q3RodWxodQ",    // "Cthulhu" unpadded
            "QXphdGhvdGg",   // "Azathoth" unpadded
            "U2hvZ2dvdGg"    // "Shoggoth" unpadded
    })
    void lenientAcceptsUnpaddedUrlButStrictRejects(String unpaddedInput) {
        var lenientResult = EncodingFunctionLibrary.isValidBase64Url(Val.of(unpaddedInput));
        var strictResult  = EncodingFunctionLibrary.isValidBase64UrlStrict(Val.of(unpaddedInput));

        assertThat(lenientResult.getBoolean()).isTrue();
        assertThat(strictResult.getBoolean()).isFalse();
    }

    /* Round-trip Tests */

    @ParameterizedTest
    @MethodSource("provideEncodingDecodingPairs")
    void roundTrip_whenEncodingAndDecoding_recoversOriginal(EncoderDecoder pair, String testData) {
        var original = Val.of(testData);
        var encoded  = pair.encoder().apply(original);
        var decoded  = pair.decoder().apply(encoded);
        assertThat(decoded.getText()).isEqualTo(original.getText());
    }

    private static Stream<Arguments> provideEncodingDecodingPairs() {
        var testData = "The Elder Things dwell in the Mountains of Madness: !@#$%^&*()";

        return Stream.of(
                arguments(
                        new EncoderDecoder(
                                EncodingFunctionLibrary::base64Encode, EncodingFunctionLibrary::base64Decode),
                        testData),
                arguments(new EncoderDecoder(EncodingFunctionLibrary::base64Encode,
                        EncodingFunctionLibrary::base64DecodeStrict), testData),
                arguments(new EncoderDecoder(EncodingFunctionLibrary::base64UrlEncode,
                        EncodingFunctionLibrary::base64UrlDecode), testData),
                arguments(new EncoderDecoder(EncodingFunctionLibrary::base64UrlEncode,
                        EncodingFunctionLibrary::base64UrlDecodeStrict), testData),
                arguments(new EncoderDecoder(EncodingFunctionLibrary::hexEncode, EncodingFunctionLibrary::hexDecode),
                        testData));
    }

    /* Unicode Edge Cases */

    @ParameterizedTest
    @MethodSource("provideUnicodeEncodingDecodingPairs")
    void roundTrip_whenUnicodeCharacters_handlesCorrectly(EncoderDecoder pair) {
        var original = Val.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn 世界 🌍");
        var encoded  = pair.encoder().apply(original);
        var decoded  = pair.decoder().apply(encoded);
        assertThat(decoded.getText()).isEqualTo(original.getText());
    }

    private static Stream<Arguments> provideUnicodeEncodingDecodingPairs() {
        return Stream.of(
                arguments(new EncoderDecoder(EncodingFunctionLibrary::base64Encode,
                        EncodingFunctionLibrary::base64Decode)),
                arguments(new EncoderDecoder(EncodingFunctionLibrary::hexEncode, EncodingFunctionLibrary::hexDecode)));
    }

    /* Security Tests - Resource Exhaustion Prevention */

    @Test
    void base64Encode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.base64Encode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void base64Decode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.base64Decode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void base64UrlEncode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.base64UrlEncode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void base64UrlDecode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.base64UrlDecode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void hexEncode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.hexEncode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void hexDecode_whenInputExceedsMaxLength_returnsError() {
        var largeInput = "4".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.hexDecode(Val.of(largeInput));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("exceeds maximum allowed length");
    }

    @Test
    void isValidBase64_whenInputExceedsMaxLength_returnsFalse() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.isValidBase64(Val.of(largeInput));
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void isValidBase64Url_whenInputExceedsMaxLength_returnsFalse() {
        var largeInput = "A".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.isValidBase64Url(Val.of(largeInput));
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void isValidHex_whenInputExceedsMaxLength_returnsFalse() {
        var largeInput = "4".repeat(10_000_001);
        var result     = EncodingFunctionLibrary.isValidHex(Val.of(largeInput));
        assertThat(result.getBoolean()).isFalse();
    }

    /* Security Tests - Invalid UTF-8 Handling */

    @Test
    void base64Decode_whenDecodedBytesAreInvalidUtf8_returnsError() {
        // 0xFF 0xFE is not valid UTF-8
        var invalidUtf8Base64 = Base64.getEncoder().encodeToString(new byte[] { (byte) 0xFF, (byte) 0xFE });
        var result            = EncodingFunctionLibrary.base64Decode(Val.of(invalidUtf8Base64));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("invalid UTF-8");
    }

    @Test
    void base64UrlDecode_whenDecodedBytesAreInvalidUtf8_returnsError() {
        // 0xFF 0xFE is not valid UTF-8
        var invalidUtf8Base64 = Base64.getUrlEncoder().encodeToString(new byte[] { (byte) 0xFF, (byte) 0xFE });
        var result            = EncodingFunctionLibrary.base64UrlDecode(Val.of(invalidUtf8Base64));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("invalid UTF-8");
    }

    @Test
    void hexDecode_whenDecodedBytesAreInvalidUtf8_returnsError() {
        // fffe is not valid UTF-8
        var result = EncodingFunctionLibrary.hexDecode(Val.of("fffe"));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("invalid UTF-8");
    }

    /* Security Tests - Control Characters */

    @Test
    void base64_whenControlCharacters_handlesCorrectly() {
        var controlChars = "The\u0000Deep\u0001Ones\u001Fawait";
        var encoded      = EncodingFunctionLibrary.base64Encode(Val.of(controlChars));
        var decoded      = EncodingFunctionLibrary.base64Decode(encoded);
        assertThat(decoded.getText()).isEqualTo(controlChars);
    }

    @Test
    void hex_whenControlCharacters_handlesCorrectly() {
        var controlChars = "Nyarlathotep\u0000\u0001\u0002";
        var encoded      = EncodingFunctionLibrary.hexEncode(Val.of(controlChars));
        var decoded      = EncodingFunctionLibrary.hexDecode(encoded);
        assertThat(decoded.getText()).isEqualTo(controlChars);
    }

    /* Security Tests - Null Bytes */

    @Test
    void base64_whenNullBytesInMiddle_preservesNullBytes() {
        var withNullBytes = "Shub\u0000Niggurath";
        var encoded       = EncodingFunctionLibrary.base64Encode(Val.of(withNullBytes));
        var decoded       = EncodingFunctionLibrary.base64Decode(encoded);
        assertThat(decoded.getText()).isEqualTo(withNullBytes);
        assertThat(decoded.getText()).contains("\u0000");
    }

    @Test
    void hex_whenNullBytesInMiddle_preservesNullBytes() {
        var withNullBytes = "Elder\u0000Things";
        var encoded       = EncodingFunctionLibrary.hexEncode(Val.of(withNullBytes));
        var decoded       = EncodingFunctionLibrary.hexDecode(encoded);
        assertThat(decoded.getText()).isEqualTo(withNullBytes);
        assertThat(decoded.getText()).contains("\u0000");
    }

    /* Security Tests - Early Rejection */

    @Test
    void isValidBase64Strict_whenObviouslyInvalidCharacter_rejectsQuickly() {
        // Test early rejection with character outside Base64 range
        var result = EncodingFunctionLibrary.isValidBase64Strict(Val.of("AAA\u007FAAA="));
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void isValidBase64Strict_whenCharacterBelowRange_rejectsQuickly() {
        // Test early rejection with character below valid range
        var result = EncodingFunctionLibrary.isValidBase64Strict(Val.of("AAA\u0020AAA="));
        assertThat(result.getBoolean()).isFalse();
    }

    /**
     * Helper record to reduce verbosity in round-trip tests.
     */
    private record EncoderDecoder(Function<Val, Val> encoder, Function<Val, Val> decoder) {}
}
