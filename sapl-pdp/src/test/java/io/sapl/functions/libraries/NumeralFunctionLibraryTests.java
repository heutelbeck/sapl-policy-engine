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

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.DisplayName;

@DisplayName("NumeralFunctionLibrary")
class NumeralFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(NumeralFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    /* Parsing Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHexParsingTestCases")
    void whenFromHexThenReturnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromHex(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHexParsingTestCases() {
        return Stream.of(arguments("0", 0L), arguments("1", 1L), arguments("FF", 255L), arguments("ff", 255L),
                arguments("Ff", 255L), arguments("0xFF", 255L), arguments("0XFF", 255L), arguments("FFF", 4095L),
                arguments("FFFF", 65535L), arguments("FF_FF", 65535L), arguments("F_F_F_F", 65535L),
                arguments("-1", -1L), arguments("-FF", -255L), arguments("FFFFFFFFFFFFFFFF", -1L),
                arguments("7FFFFFFFFFFFFFFF", Long.MAX_VALUE), arguments("8000000000000000", Long.MIN_VALUE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideBinaryParsingTestCases")
    void whenFromBinaryThenReturnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromBinary(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBinaryParsingTestCases() {
        return Stream.of(arguments("0", 0L), arguments("1", 1L), arguments("1010", 10L), arguments("0b1010", 10L),
                arguments("0B1010", 10L), arguments("11111111", 255L), arguments("1111_1111", 255L),
                arguments("1_0_1_0", 10L), arguments("-1", -1L), arguments("-1010", -10L),
                arguments("1111111111111111111111111111111111111111111111111111111111111111", -1L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideOctalParsingTestCases")
    void whenFromOctalThenReturnsCorrectValue(String input, long expected) {
        val actual = NumeralFunctionLibrary.fromOctal(Value.of(input));
        assertThat(actual).isInstanceOf(NumberValue.class);
        val number = (NumberValue) actual;
        assertThat(number.value().longValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideOctalParsingTestCases() {
        return Stream.of(arguments("0", 0L), arguments("7", 7L), arguments("10", 8L), arguments("77", 63L),
                arguments("0o77", 63L), arguments("0O77", 63L), arguments("755", 493L), arguments("7_5_5", 493L),
                arguments("-1", -1L), arguments("-10", -8L), arguments("1777777777777777777777", -1L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvalidParsingTestCases")
    void whenParseWithInvalidInputThenReturnsError(String baseName, Function<Value, Value> parseFunction,
            String invalidInput) {

        val actual = parseFunction.apply(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(ErrorValue.class);
    }

    private static Stream<Arguments> provideInvalidParsingTestCases() {
        return Stream.of(arguments("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, ""),
                arguments("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "   "),
                arguments("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "GG"),
                arguments("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "0x"),
                arguments("hex", (Function<TextValue, Value>) NumeralFunctionLibrary::fromHex, "-"),

                arguments("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, ""),
                arguments("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, "102"),
                arguments("binary", (Function<TextValue, Value>) NumeralFunctionLibrary::fromBinary, "0b"),

                arguments("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, ""),
                arguments("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, "88"),
                arguments("octal", (Function<TextValue, Value>) NumeralFunctionLibrary::fromOctal, "0o"));
    }

    /* Formatting Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHexFormattingTestCases")
    void whenToHexThenReturnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toHex(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHexFormattingTestCases() {
        return Stream.of(arguments(0L, "0"), arguments(1L, "1"), arguments(255L, "FF"), arguments(4095L, "FFF"),
                arguments(65535L, "FFFF"), arguments(-1L, "FFFFFFFFFFFFFFFF"), arguments(-255L, "FFFFFFFFFFFFFF01"),
                arguments(Long.MAX_VALUE, "7FFFFFFFFFFFFFFF"), arguments(Long.MIN_VALUE, "8000000000000000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideBinaryFormattingTestCases")
    void whenToBinaryThenReturnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toBinary(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideBinaryFormattingTestCases() {
        return Stream.of(arguments(0L, "0"), arguments(1L, "1"), arguments(10L, "1010"), arguments(255L, "11111111"),
                arguments(-1L, "1111111111111111111111111111111111111111111111111111111111111111"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideOctalFormattingTestCases")
    void whenToOctalThenReturnsCorrectString(long value, String expected) {
        val actual = NumeralFunctionLibrary.toOctal(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideOctalFormattingTestCases() {
        return Stream.of(arguments(0L, "0"), arguments(7L, "7"), arguments(8L, "10"), arguments(63L, "77"),
                arguments(493L, "755"), arguments(-1L, "1777777777777777777777"));
    }

    /* Prefixed Formatting Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("providePrefixedFormattingTestCases")
    void whenToPrefixedFormatThenReturnsCorrectString(String baseName, Function<Value, Value> formatFunction,
            long value, String expected) {

        val actual = formatFunction.apply(Value.of(value));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> providePrefixedFormattingTestCases() {
        return Stream.of(
                arguments("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, 255L, "0xFF"),
                arguments("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, 0L, "0x0"),
                arguments("hex", (Function<NumberValue, Value>) NumeralFunctionLibrary::toHexPrefixed, -1L,
                        "0xFFFFFFFFFFFFFFFF"),

                arguments("binary", (Function<NumberValue, Value>) NumeralFunctionLibrary::toBinaryPrefixed, 10L,
                        "0b1010"),
                arguments("binary", (Function<NumberValue, Value>) NumeralFunctionLibrary::toBinaryPrefixed, 0L, "0b0"),

                arguments("octal", (Function<NumberValue, Value>) NumeralFunctionLibrary::toOctalPrefixed, 63L, "0o77"),
                arguments("octal", (Function<NumberValue, Value>) NumeralFunctionLibrary::toOctalPrefixed, 0L, "0o0"));
    }

    /* Padded Formatting Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("providePaddedFormattingTestCases")
    void whenToPaddedFormatThenReturnsCorrectString(String baseName, BiFunction<Value, Value, Value> formatFunction,
            long value, int width, String expected) {

        val actual = formatFunction.apply(Value.of(value), Value.of(width));
        assertThat(actual).isInstanceOf(TextValue.class);
        val text = (TextValue) actual;
        assertThat(text.value()).isEqualTo(expected);
    }

    private static Stream<Arguments> providePaddedFormattingTestCases() {
        return Stream.of(
                arguments("hex", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        255L, 4, "00FF"),
                arguments("hex", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        255L, 2, "FF"),
                arguments("hex", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded,
                        4095L, 2, "FFF"),
                arguments("hex", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toHexPadded, 0L,
                        4, "0000"),

                arguments("binary",
                        (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded, 10L, 8,
                        "00001010"),
                arguments("binary",
                        (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded, 10L, 4,
                        "1010"),
                arguments("binary",
                        (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toBinaryPadded, 255L, 4,
                        "11111111"),

                arguments("octal", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        63L, 4, "0077"),
                arguments("octal", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        63L, 2, "77"),
                arguments("octal", (BiFunction<NumberValue, NumberValue, Value>) NumeralFunctionLibrary::toOctalPadded,
                        493L, 2, "755"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = { 0, -1, -10 })
    void whenToPaddedFormatWithInvalidWidthThenReturnsError(int invalidWidth) {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidHexStrings")
    void whenIsValidHexWithValidInputThenReturnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidHex(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidHexStrings() {
        return Stream.of("0", "1", "FF", "ff", "Ff", "0xFF", "0XFF", "FF_FF", "F_F_F_F", "-1", "-FF",
                "FFFFFFFFFFFFFFFF");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidHexStrings")
    void whenIsValidHexWithInvalidInputThenReturnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidHex(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidHexStrings() {
        return Stream.of("", "   ", "GG", "XYZ", "0x", "-", "0x-");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidBinaryStrings")
    void whenIsValidBinaryWithValidInputThenReturnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidBinary(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidBinaryStrings() {
        return Stream.of("0", "1", "1010", "0b1010", "0B1010", "1111_1111", "1_0_1_0", "-1", "-1010");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidBinaryStrings")
    void whenIsValidBinaryWithInvalidInputThenReturnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidBinary(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidBinaryStrings() {
        return Stream.of("", "   ", "102", "abc", "0b", "-", "0b-");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideValidOctalStrings")
    void whenIsValidOctalWithValidInputThenReturnsTrue(String validInput) {
        val actual = NumeralFunctionLibrary.isValidOctal(Value.of(validInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isTrue();
    }

    private static Stream<String> provideValidOctalStrings() {
        return Stream.of("0", "7", "10", "77", "0o77", "0O77", "7_5_5", "755", "-1", "-10");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidOctalStrings")
    void whenIsValidOctalWithInvalidInputThenReturnsFalse(String invalidInput) {
        val actual = NumeralFunctionLibrary.isValidOctal(Value.of(invalidInput));
        assertThat(actual).isInstanceOf(BooleanValue.class);
        val bool = (BooleanValue) actual;
        assertThat(bool.value()).isFalse();
    }

    private static Stream<String> provideInvalidOctalStrings() {
        return Stream.of("", "   ", "88", "9", "abc", "0o", "-", "0o-");
    }

    /* Round-trip Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRoundTripValues")
    void whenFormatThenParseThenReturnsOriginalValue(long originalValue) {
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
    void whenParseMaxAndMinValuesThenReturnsCorrectResults() {
        val maxHex = NumeralFunctionLibrary.fromHex(Value.of("7FFFFFFFFFFFFFFF"));
        assertThat(maxHex).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) maxHex).value().longValue()).isEqualTo(Long.MAX_VALUE);

        val minHex = NumeralFunctionLibrary.fromHex(Value.of("8000000000000000"));
        assertThat(minHex).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) minHex).value().longValue()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void whenParseCaseInsensitiveThenReturnsCorrectValue() {
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
    void whenFormatAlwaysUppercaseThenReturnsUppercaseHex() {
        val result = NumeralFunctionLibrary.toHex(Value.of(0xDEADBEEFL));
        assertThat(result).isInstanceOf(TextValue.class);
        val text = (TextValue) result;
        assertThat(text.value()).isEqualTo("DEADBEEF");
        assertThat(text.value()).doesNotContain("a", "b", "c", "d", "e", "f");
    }

    @Test
    void whenParseWithMultipleUnderscoresThenIgnoresAllUnderscores() {
        val result = NumeralFunctionLibrary.fromHex(Value.of("F_F_F_F"));
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().longValue()).isEqualTo(0xFFFFL);
    }

}
