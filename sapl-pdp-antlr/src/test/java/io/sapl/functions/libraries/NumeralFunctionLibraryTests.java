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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NumeralFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(NumeralFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    /* Parsing Tests */

    @ParameterizedTest
    @MethodSource("provideHexParsingTestCases")
    void when_fromHex_then_returnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromHex(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHexParsingTestCases() {
        return Stream.of(Arguments.of("0", 0L), Arguments.of("1", 1L), Arguments.of("FF", 255L),
                Arguments.of("ff", 255L), Arguments.of("Ff", 255L), Arguments.of("0xFF", 255L),
                Arguments.of("0XFF", 255L), Arguments.of("FFF", 4095L), Arguments.of("FFFF", 65535L),
                Arguments.of("FF_FF", 65535L), Arguments.of("F_F_F_F", 65535L), Arguments.of("-1", -1L),
                Arguments.of("-FF", -255L), Arguments.of("FFFFFFFFFFFFFFFF", -1L),
                Arguments.of("7FFFFFFFFFFFFFFF", Long.MAX_VALUE), Arguments.of("8000000000000000", Long.MIN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("provideBinaryParsingTestCases")
    void when_fromBinary_then_returnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromBinary(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBinaryParsingTestCases() {
        return Stream.of(Arguments.of("0", 0L), Arguments.of("1", 1L), Arguments.of("1010", 10L),
                Arguments.of("0b1010", 10L), Arguments.of("0B1010", 10L), Arguments.of("11111111", 255L),
                Arguments.of("1111_1111", 255L), Arguments.of("1_0_1_0", 10L), Arguments.of("-1", -1L),
                Arguments.of("-1010", -10L),
                Arguments.of("1111111111111111111111111111111111111111111111111111111111111111", -1L));
    }

    @ParameterizedTest
    @MethodSource("provideOctalParsingTestCases")
    void when_fromOctal_then_returnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromOctal(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideOctalParsingTestCases() {
        return Stream.of(Arguments.of("0", 0L), Arguments.of("7", 7L), Arguments.of("10", 8L), Arguments.of("77", 63L),
                Arguments.of("0o77", 63L), Arguments.of("0O77", 63L), Arguments.of("755", 493L),
                Arguments.of("7_5_5", 493L), Arguments.of("-1", -1L), Arguments.of("-10", -8L),
                Arguments.of("1777777777777777777777", -1L));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidParsingTestCases")
    void when_parseWithInvalidInput_then_returnsError(String baseName, Function<Value, Value> parseFunction,
            String invalidInput) {

        val actual = parseFunction.apply(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> provideInvalidParsingTestCases() {
        return Stream.of(Arguments.of("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, ""),
                Arguments.of("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "   "),
                Arguments.of("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "GG"),
                Arguments.of("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "0x"),
                Arguments.of("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "-"),

                Arguments.of("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, ""),
                Arguments.of("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, "102"),
                Arguments.of("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, "0b"),

                Arguments.of("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, ""),
                Arguments.of("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, "88"),
                Arguments.of("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, "0o"));
    }

    /* Formatting Tests */

    @ParameterizedTest
    @MethodSource("provideHexFormattingTestCases")
    void when_toHex_then_returnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toHex(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHexFormattingTestCases() {
        return Stream.of(Arguments.of(0L, "0"), Arguments.of(1L, "1"), Arguments.of(255L, "FF"),
                Arguments.of(4095L, "FFF"), Arguments.of(65535L, "FFFF"), Arguments.of(-1L, "FFFFFFFFFFFFFFFF"),
                Arguments.of(-255L, "FFFFFFFFFFFFFF01"), Arguments.of(Long.MAX_VALUE, "7FFFFFFFFFFFFFFF"),
                Arguments.of(Long.MIN_VALUE, "8000000000000000"));
    }

    @ParameterizedTest
    @MethodSource("provideBinaryFormattingTestCases")
    void when_toBinary_then_returnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toBinary(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBinaryFormattingTestCases() {
        return Stream.of(Arguments.of(0L, "0"), Arguments.of(1L, "1"), Arguments.of(10L, "1010"),
                Arguments.of(255L, "11111111"),
                Arguments.of(-1L, "1111111111111111111111111111111111111111111111111111111111111111"));
    }

    @ParameterizedTest
    @MethodSource("provideOctalFormattingTestCases")
    void when_toOctal_then_returnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toOctal(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideOctalFormattingTestCases() {
        return Stream.of(Arguments.of(0L, "0"), Arguments.of(7L, "7"), Arguments.of(8L, "10"), Arguments.of(63L, "77"),
                Arguments.of(493L, "755"), Arguments.of(-1L, "1777777777777777777777"));
    }

    /* Prefixed Formatting Tests */

    @ParameterizedTest
    @MethodSource("providePrefixedFormattingTestCases")
    void when_toPrefixedFormat_then_returnsCorrectString(String baseName, Function<Value, Value> formatFunction,
            long value, String expected) {

        val actual = formatFunction.apply(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> providePrefixedFormattingTestCases() {
        return Stream.of(
                Arguments.of("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, 255L, "0xFF"),
                Arguments.of("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, 0L, "0x0"),
                Arguments.of("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, -1L,
                        "0xFFFFFFFFFFFFFFFF"),

                Arguments.of("binary", (Function<NumberValue, Value>) NumeralFunctionLibrary::toBinaryPrefixed, 10L,
                        "0b1010"),
                Arguments.of("binary", (Function<NumberValue, Value>) NumeralFunctionLibrary::toBinaryPrefixed, 0L,
                        "0b0"),

                Arguments.of("octal", (Function<NumberValue, Value>) NumeralFunctionLibrary::toOctalPrefixed, 63L,
                        "0o77"),
                Arguments.of("octal", (Function<NumberValue, Value>) NumeralFunctionLibrary::toOctalPrefixed, 0L,
                        "0o0"));
    }

    /* Padded Formatting Tests */

    @ParameterizedTest
    @MethodSource("providePaddedFormattingTestCases")
    void when_toPaddedFormat_then_returnsCorrectString(String baseName,
            java.util.function.BiFunction<Value, Value, Value> formatFunction, long value, int width, String expected) {

        val actual = formatFunction.apply(Value.of(value), Value.of(width));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> providePaddedFormattingTestCases() {
        return Stream.of(Arguments.of("hex",
                (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                255L, 4, "00FF"),
                Arguments.of("hex",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        255L, 2, "FF"),
                Arguments.of("hex",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        4095L, 2, "FFF"),
                Arguments.of("hex",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        0L, 4, "0000"),

                Arguments.of("binary",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded,
                        10L, 8, "00001010"),
                Arguments.of("binary",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded,
                        10L, 4, "1010"),
                Arguments.of("binary",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded,
                        255L, 4, "11111111"),

                Arguments.of("octal",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        63L, 4, "0077"),
                Arguments.of("octal",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        63L, 2, "77"),
                Arguments.of("octal",
                        (java.util.function.BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        493L, 2, "755"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -10 })
    void when_toPaddedFormatWithInvalidWidth_then_returnsError(int invalidWidth) {
        val actualHex = NumeralFunctionLibrary.toHexPadded(Value.of(255L), Value.of(invalidWidth));
        assertThat(actualHex).isInstanceOf(ErrorValue.class);
        assertThat(actualHex.toString()).contains("Width must be positive");

        val actualBinary = NumeralFunctionLibrary.toBinaryPadded(Value.of(10L), Value.of(invalidWidth));
        assertThat(actualBinary).isInstanceOf(ErrorValue.class);
        assertThat(actualBinary.toString()).contains("Width must be positive");

        val actualOctal = NumeralFunctionLibrary.toOctalPadded(Value.of(63L), Value.of(invalidWidth));
        assertThat(actualOctal).isInstanceOf(ErrorValue.class);
        assertThat(actualOctal.toString()).contains("Width must be positive");
    }

    /* Validation Tests */

    @ParameterizedTest
    @MethodSource("provideValidHexStrings")
    void when_isValidHex_withValidInput_then_returnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidHex(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidHexStrings() {
        return Stream.of("0", "1", "FF", "ff", "Ff", "0xFF", "0XFF", "FF_FF", "F_F_F_F", "-1", "-FF",
                "FFFFFFFFFFFFFFFF");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidHexStrings")
    void when_isValidHex_withInvalidInput_then_returnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidHex(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidHexStrings() {
        return Stream.of("", "   ", "GG", "XYZ", "0x", "-", "0x-");
    }

    @ParameterizedTest
    @MethodSource("provideValidBinaryStrings")
    void when_isValidBinary_withValidInput_then_returnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidBinary(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidBinaryStrings() {
        return Stream.of("0", "1", "1010", "0b1010", "0B1010", "1111_1111", "1_0_1_0", "-1", "-1010");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidBinaryStrings")
    void when_isValidBinary_withInvalidInput_then_returnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidBinary(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidBinaryStrings() {
        return Stream.of("", "   ", "102", "abc", "0b", "-", "0b-");
    }

    @ParameterizedTest
    @MethodSource("provideValidOctalStrings")
    void when_isValidOctal_withValidInput_then_returnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidOctal(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidOctalStrings() {
        return Stream.of("0", "7", "10", "77", "0o77", "0O77", "7_5_5", "755", "-1", "-10");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidOctalStrings")
    void when_isValidOctal_withInvalidInput_then_returnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidOctal(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidOctalStrings() {
        return Stream.of("", "   ", "88", "9", "abc", "0o", "-", "0o-");
    }

    /* Round-trip Tests */

    @ParameterizedTest
    @MethodSource("provideRoundTripValues")
    void when_formatThenParse_then_returnsOriginalValue(long originalValue) {
        val hexString = NumeralFunctionLibrary.toHex(Value.of(originalValue));
        assertThat(hexString).isInstanceOf(TextValue.class);
        val parsedFromHex = NumeralFunctionLibrary.fromHex((TextValue) hexString);
        assertThat(parsedFromHex).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) parsedFromHex).value().longValue()).isEqualTo(originalValue);

        val binaryString = NumeralFunctionLibrary.toBinary(Value.of(originalValue));
        assertThat(binaryString).isInstanceOf(TextValue.class);
        val parsedFromBinary = NumeralFunctionLibrary.fromBinary((TextValue) binaryString);
        assertThat(parsedFromBinary).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) parsedFromBinary).value().longValue()).isEqualTo(originalValue);

        val octalString = NumeralFunctionLibrary.toOctal(Value.of(originalValue));
        assertThat(octalString).isInstanceOf(TextValue.class);
        val parsedFromOctal = NumeralFunctionLibrary.fromOctal((TextValue) octalString);
        assertThat(parsedFromOctal).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) parsedFromOctal).value().longValue()).isEqualTo(originalValue);
    }

    private static Stream<Long> provideRoundTripValues() {
        return Stream.of(0L, 1L, 10L, 255L, 4095L, 65535L, -1L, -10L, -255L, Long.MAX_VALUE, Long.MIN_VALUE, 42L,
                1234567890L);
    }

    /* Edge Cases */

    @Test
    void when_parseMaxAndMinValues_then_returnsCorrectResults() {
        val maxHex = NumeralFunctionLibrary.fromHex(Value.of("7FFFFFFFFFFFFFFF"));
        assertThat(maxHex).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) maxHex).value().longValue()).isEqualTo(Long.MAX_VALUE);

        val minHex = NumeralFunctionLibrary.fromHex(Value.of("8000000000000000"));
        assertThat(minHex).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) minHex).value().longValue()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void when_parseCaseInsensitive_then_returnsCorrectValue() {
        val lower = NumeralFunctionLibrary.fromHex(Value.of("deadbeef"));
        val upper = NumeralFunctionLibrary.fromHex(Value.of("DEADBEEF"));
        val mixed = NumeralFunctionLibrary.fromHex(Value.of("DeAdBeEf"));

        assertThat(lower).isInstanceOf(NumberValue.class);
        assertThat(upper).isInstanceOf(NumberValue.class);
        assertThat(mixed).isInstanceOf(NumberValue.class);

        val expectedValue = 0xDEADBEEFL;
        assertThat(((NumberValue) lower).value().longValue()).isEqualTo(expectedValue);
        assertThat(((NumberValue) upper).value().longValue()).isEqualTo(expectedValue);
        assertThat(((NumberValue) mixed).value().longValue()).isEqualTo(expectedValue);
    }

    @Test
    void when_formatAlwaysUppercase_then_returnsUppercaseHex() {
        val result = NumeralFunctionLibrary.toHex(Value.of(0xDEADBEEFL));
        assertThat(result).isInstanceOf(TextValue.class);
        val text = (TextValue) result;
        assertThat(text.value()).isEqualTo("DEADBEEF");
        assertThat(text.value()).doesNotContain("a", "b", "c", "d", "e", "f");
    }

    @Test
    void when_parseWithMultipleUnderscores_then_ignoresAllUnderscores() {
        val result = NumeralFunctionLibrary.fromHex(Value.of("F_F_F_F"));
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().longValue()).isEqualTo(0xFFFFL);
    }

}
