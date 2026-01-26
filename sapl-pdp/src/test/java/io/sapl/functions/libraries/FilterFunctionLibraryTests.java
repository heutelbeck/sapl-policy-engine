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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FilterFunctionLibrary using new Value model.
 */
@DisplayName("FilterFunctionLibrary")
class FilterFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    // ============================================================================
    // BLACKEN FUNCTION TESTS
    // ============================================================================

    @Test
    void blackenNoArgumentsThrowsException() {
        assertThatThrownBy(io.sapl.functions.libraries.FilterFunctionLibrary::blacken)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("STRING");
    }

    @Test
    void blackenNonStringTextThrowsException() {
        val nonStringValue = Value.of(9999);
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(nonStringValue))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("STRING");
    }

    @Test
    void blackenTooManyArgumentsThrowsException() {
        var params = new Value[] { Value.of("test"), Value.of(2), Value.of(2), Value.of("*"), Value.of(2),
                Value.of(2) };
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(params))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Illegal number");
    }

    @Test
    void blackenNegativeDiscloseLeftThrowsException() {
        val text               = Value.of("test");
        val negativeDisclosure = Value.of(-1);
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(text, negativeDisclosure))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DISCLOSE_LEFT");
    }

    @Test
    void blackenNegativeDiscloseRightThrowsException() {
        val text                  = Value.of("test");
        val discloseLeft          = Value.of(2);
        val negativeDiscloseRight = Value.of(-1);
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(text, discloseLeft,
                negativeDiscloseRight)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DISCLOSE_RIGHT");
    }

    @Test
    void blackenNonStringReplacementThrowsException() {
        val text                 = Value.of("test");
        val discloseLeft         = Value.of(2);
        val discloseRight        = Value.of(2);
        val nonStringReplacement = Value.of(13);
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(text, discloseLeft,
                discloseRight, nonStringReplacement)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REPLACEMENT");
    }

    @Test
    void blackenNegativeLengthThrowsException() {
        val text           = Value.of("test");
        val discloseLeft   = Value.of(2);
        val discloseRight  = Value.of(2);
        val replacement    = Value.of("*");
        val negativeLength = Value.of(-1);
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(text, discloseLeft,
                discloseRight, replacement, negativeLength)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLACKEN_LENGTH");
    }

    @Test
    void blackenNonNumericLengthThrowsException() {
        val text             = Value.of("test");
        val discloseLeft     = Value.of(2);
        val discloseRight    = Value.of(2);
        val replacement      = Value.of("*");
        val nonNumericLength = Value.of("bad");
        assertThatThrownBy(() -> io.sapl.functions.libraries.FilterFunctionLibrary.blacken(text, discloseLeft,
                discloseRight, replacement, nonNumericLength)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLACKEN_LENGTH");
    }

    @ParameterizedTest(name = "Blacken {0}: left={1}, right={2}, replacement={3} -> {4}")
    @CsvSource(delimiter = '|', textBlock = """
            Necronomicon      | 5 | 3 | *   | Necro****con
            Cthulhu           | 2 | 2 | X   | CtXXXhu
            Yog-Sothoth       | 3 | 0 | #   | Yog########
            Azathoth          | 0 | 4 | *   | ****hoth
            R'lyeh            | 1 | 1 | █   | R████h
            Miskatonic        | 0 | 0 | *   | **********
            Innsmouth         | 10| 10| *   | Innsmouth
            Arkham            | 3 | 2 | ░   | Ark░am
            Dunwich           | 4 | 4 | ▓   | Dunwich
            Kadath            | 2 | 1 | ◼   | Ka◼◼◼h
            """)
    void blackenVariousInputsRedactsCorrectly(String text, int discloseLeft, int discloseRight, String replacement,
            String expected) {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of(text), Value.of(discloseLeft),
                Value.of(discloseRight), Value.of(replacement));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "{0}: left={1}, right={2}, overrideLength={3} -> {4}")
    @CsvSource(delimiter = '|', textBlock = """
            Necronomicon | 3 | 3 | 2  | Nec**con
            Necronomicon | 3 | 3 | 10 | Nec**********con
            Cthulhu      | 2 | 2 | 10 | Ct**********hu
            Yog-Sothoth  | 3 | 4 | 0  | Yoghoth
            Azathoth     | 4 | 0 | 2  | Azat**
            """)
    void blackenWithLengthOverrideRedactsWithCustomLength(String text, int discloseLeft, int discloseRight,
            int blackenLength, String expected) {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of(text), Value.of(discloseLeft),
                Value.of(discloseRight), Value.of("*"), Value.of(blackenLength));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of(expected));
    }

    @Test
    void blackenFullyDisclosedReturnsOriginal() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("Shub-Niggurath"), Value.of(7),
                Value.of(7), Value.of("*"));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("Shub-Niggurath"));
    }

    @Test
    void blackenFullyBlackenedReplacesAll() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("Yog-Sothoth"), Value.of(0),
                Value.of(0), Value.of("#"));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("###########"));
    }

    @Test
    void blackenMultiCharacterReplacementRepeatsReplacement() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("Nyarlathotep"), Value.of(4),
                Value.of(4), Value.of("[REDACTED]"));

        assertThat(result).isInstanceOf(TextValue.class)
                .isEqualTo(Value.of("Nyar[REDACTED][REDACTED][REDACTED][REDACTED]otep"));
    }

    @Test
    void blackenUnicodeCharactersHandlesCorrectly() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("古のもの"), Value.of(1),
                Value.of(1), Value.of("█"));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("古██の"));
    }

    @Test
    void blackenEmptyReplacementWithZeroLengthPreservesEnds() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("Nyarlathotep"), Value.of(4),
                Value.of(4), Value.of(""), Value.of(0));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("Nyarotep"));
    }

    @Test
    void blackenEmptyStringReturnsEmpty() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of(""));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of(""));
    }

    @Test
    void blackenSingleCharacterBlackensCorrectly() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("R"), Value.of(0), Value.of(0));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("X"));
    }

    @Test
    void blackenSingleCharacterFullyDisclosedReturnsOriginal() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of("R"), Value.of(1), Value.of(0));

        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of("R"));
    }

    @Test
    void blackenLongIncantationRedactsLargeText() {
        val incantation   = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn";
        val discloseLeft  = Value.of(10);
        val discloseRight = Value.of(10);
        val replacement   = Value.of("*");

        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of(incantation), discloseLeft,
                discloseRight, replacement);

        val expected = "Ph'nglui m******************************agl fhtagn";
        assertThat(result).isInstanceOf(TextValue.class).isEqualTo(Value.of(expected));
    }

    @Test
    void blackenVeryLongTextHandlesCorrectly() {
        val longIncantation = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn ".repeat(100);
        val result          = io.sapl.functions.libraries.FilterFunctionLibrary.blacken(Value.of(longIncantation),
                Value.of(10), Value.of(10));

        assertThat(result).isInstanceOf(TextValue.class);
        val resultText = ((TextValue) result).value();
        assertThat(resultText.length()).isEqualTo(longIncantation.length());
        assertThat(resultText.substring(0, 10)).isEqualTo(longIncantation.substring(0, 10));
        assertThat(resultText.substring(resultText.length() - 10))
                .isEqualTo(longIncantation.substring(longIncantation.length() - 10));
    }

    // ============================================================================
    // BLACKEN_UTIL TESTS
    // ============================================================================

    @ParameterizedTest(name = "blackenUtil: {0} with replacement={1}, left={2}, right={3}, length={4}")
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            Necronomicon | * | 5 | 3 | null | Necro****con
            Cthulhu      | X | 2 | 2 | 10   | CtXXXXXXXXXXhu
            Yog-Sothoth  | # | 3 | 4 | 0    | Yoghoth
            Azathoth     | * | 4 | 0 | 2    | Azat**
            R'lyeh       | █ | 1 | 1 | null | R████h
            """)
    void blackenUtilVariousInputsRedactsCorrectly(String text, String replacement, int left, int right, Integer length,
            String expected) {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.blackenUtil(text, replacement, right, left,
                length);
        assertThat(result).isEqualTo(expected);
    }

    // ============================================================================
    // REPLACE FUNCTION TESTS
    // ============================================================================

    @Test
    void replaceNormalValueReturnsReplacement() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.replace(Value.NULL, Value.of(13));

        assertThat(result).isEqualTo(Value.of(13));
    }

    @Test
    void replaceErrorValuePreservesError() {
        val error  = Value.error("The ritual failed");
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.replace(error, Value.of("Safe"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo("The ritual failed");
    }

    @Test
    void replaceUndefinedValueReturnsReplacement() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.replace(Value.UNDEFINED,
                Value.of("replacement"));

        assertThat(result).isEqualTo(Value.of("replacement"));
    }

    // ============================================================================
    // REMOVE FUNCTION TESTS
    // ============================================================================

    @Test
    void removeAnyValueReturnsUndefined() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.remove(Value.of("Elder Sign"));

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void removeNullValueReturnsUndefined() {
        val result = io.sapl.functions.libraries.FilterFunctionLibrary.remove(Value.NULL);

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void removeErrorValueReturnsUndefined() {
        val result = FilterFunctionLibrary.remove(Value.error("error"));

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }
}
