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

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class EncodingFunctionLibraryTests {

    /* Encoding Tests */

    @ParameterizedTest
    @CsvSource({ "hello,            aGVsbG8=", "hello world,      aGVsbG8gd29ybGQ=", "'',               ''" })
    void base64Encode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64Encode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64Encode_whenSpecialCharacters_encodesCorrectly() {
        var result = EncodingFunctionLibrary.base64Encode(Val.of("test!@#$%"));
        assertThat(result.isDefined()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "hello,     aGVsbG8=", "test?data, dGVzdD9kYXRh" })
    void base64UrlEncode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64UrlEncode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64UrlEncode_whenSpecialCharacters_encodesWithUrlSafeAlphabet() {
        var result = EncodingFunctionLibrary.base64UrlEncode(Val.of("test?data"));
        assertThat(result.isDefined()).isTrue();
        assertThat(result.getText()).doesNotContain("+", "/");
    }

    @ParameterizedTest
    @CsvSource({ "hello,     68656c6c6f", "A,         41", "'',        ''" })
    void hexEncode_whenVariousInputs_encodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.hexEncode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void hexEncode_whenSpecialCharacters_encodesCorrectly() {
        var result = EncodingFunctionLibrary.hexEncode(Val.of("!@#"));
        assertThat(result.isDefined()).isTrue();
    }

    /* Decoding Tests - Lenient */

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=,         hello", "aGVsbG8gd29ybGQ=, hello world", "aGVsbG8,          hello" })
    void base64Decode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64Decode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64Decode_whenInvalidBase64_returnsError() {
        var result = EncodingFunctionLibrary.base64Decode(Val.of("invalid!@#"));
        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=, hello", "aGVsbG8,  hello" })
    void base64UrlDecode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.base64UrlDecode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @Test
    void base64UrlDecode_whenInvalidBase64Url_returnsError() {
        var result = EncodingFunctionLibrary.base64UrlDecode(Val.of("invalid!@#"));
        assertThat(result.isError()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "68656c6c6f,       hello", "68656C6C6F,       hello", "41,               A",
            "68_65_6c_6c_6f,   hello" })
    void hexDecode_whenValidInput_decodesCorrectly(String input, String expected) {
        var result = EncodingFunctionLibrary.hexDecode(Val.of(input));
        assertThat(result.getText()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "xyz", "123" })
    void hexDecode_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.hexDecode(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    /* Decoding Tests - Strict */

    @Test
    void base64DecodeStrict_whenProperlyPadded_decodesCorrectly() {
        var result = EncodingFunctionLibrary.base64DecodeStrict(Val.of("aGVsbG8="));
        assertThat(result.getText()).isEqualTo("hello");
    }

    @ParameterizedTest
    @ValueSource(strings = { "aGVsbG8", "invalid!@#", "abc" })
    void base64DecodeStrict_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.base64DecodeStrict(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void base64UrlDecodeStrict_whenProperlyPadded_decodesCorrectly() {
        var result = EncodingFunctionLibrary.base64UrlDecodeStrict(Val.of("aGVsbG8="));
        assertThat(result.getText()).isEqualTo("hello");
    }

    @ParameterizedTest
    @ValueSource(strings = { "aGVsbG8", "invalid!@#", "abc" })
    void base64UrlDecodeStrict_whenInvalidInput_returnsError(String input) {
        var result = EncodingFunctionLibrary.base64UrlDecodeStrict(Val.of(input));
        assertThat(result.isError()).isTrue();
    }

    /* Validation Tests - Lenient */

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=,    true", "aGVsbG8,     true", "invalid!@#,  false" })
    void isValidBase64_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=,    true", "aGVsbG8,     true", "test+/==,    false" })
    void isValidBase64Url_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64Url(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "68656c6c6f,       true", "68656C6C6F,       true", "68_65_6c_6c_6f,   true",
            "xyz,              false", "123,              false", "'',               false" })
    void isValidHex_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidHex(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    /* Validation Tests - Strict */

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=,    true", "aGVsbG8,     false", "invalid!@#,  false", "abc,         false",
            "aG=sbG8=,    false" })
    void isValidBase64Strict_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64Strict(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "aGVsbG8=,    true", "aGVsbG8,     false", "invalid!@#,  false", "abc,         false",
            "test+/==,    false", "aG=sbG8=,    false" })
    void isValidBase64UrlStrict_whenVariousInputs_returnsExpectedResult(String input, boolean expected) {
        var result = EncodingFunctionLibrary.isValidBase64UrlStrict(Val.of(input));
        assertThat(result.getBoolean()).isEqualTo(expected);
    }

    /* Round-trip Tests */

    @ParameterizedTest
    @MethodSource("provideEncodingDecodingPairs")
    void roundTrip_whenEncodingAndDecoding_recoversOriginal(Function<Val, Val> encoder, Function<Val, Val> decoder,
            String testData) {
        var original = Val.of(testData);
        var encoded  = encoder.apply(original);
        var decoded  = decoder.apply(encoded);
        assertThat(decoded.getText()).isEqualTo(original.getText());
    }

    private static Stream<Arguments> provideEncodingDecodingPairs() {
        var testData = "test data with special chars: !@#$%^&*()";

        return Stream.of(
                arguments((Function<Val, Val>) EncodingFunctionLibrary::base64Encode,
                        (Function<Val, Val>) EncodingFunctionLibrary::base64Decode, testData),
                arguments((Function<Val, Val>) EncodingFunctionLibrary::base64Encode,
                        (Function<Val, Val>) EncodingFunctionLibrary::base64DecodeStrict, testData),
                arguments((Function<Val, Val>) EncodingFunctionLibrary::base64UrlEncode,
                        (Function<Val, Val>) EncodingFunctionLibrary::base64UrlDecode, testData),
                arguments((Function<Val, Val>) EncodingFunctionLibrary::base64UrlEncode,
                        (Function<Val, Val>) EncodingFunctionLibrary::base64UrlDecodeStrict, testData),
                arguments((Function<Val, Val>) EncodingFunctionLibrary::hexEncode,
                        (Function<Val, Val>) EncodingFunctionLibrary::hexDecode, testData));
    }

    /* Unicode Edge Cases */

    @ParameterizedTest
    @MethodSource("provideUnicodeEncodingDecodingPairs")
    void roundTrip_whenUnicodeCharacters_handlesCorrectly(Function<Val, Val> encoder, Function<Val, Val> decoder) {
        var original = Val.of("Hello 世界 🌍");
        var encoded  = encoder.apply(original);
        var decoded  = decoder.apply(encoded);
        assertThat(decoded.getText()).isEqualTo(original.getText());
    }

    private static Stream<Arguments> provideUnicodeEncodingDecodingPairs() {
        return Stream.of(
                arguments((Function<Val, Val>) EncodingFunctionLibrary::base64Encode,
                        (Function<Val, Val>) EncodingFunctionLibrary::base64Decode),
                arguments((Function<Val, Val>) EncodingFunctionLibrary::hexEncode,
                        (Function<Val, Val>) EncodingFunctionLibrary::hexDecode));
    }
}
