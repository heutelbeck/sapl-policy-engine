/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import lombok.val;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StringFunctionLibraryTest {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(StringFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            CTHULHU                  | cthulhu
            necronomicon             | necronomicon
            aZaThOtH                 | azathoth
            YOG-SOTHOTH              | yog-sothoth
            The Elder Things         | the elder things
            """)
    void toLowerCase_convertsTextToLowercase(String input, String expected) {
        TextValue result = (TextValue) StringFunctionLibrary.toLowerCase((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            yog-sothoth              | YOG-SOTHOTH
            SHUB-NIGGURATH           | SHUB-NIGGURATH
            NyArLaThOtEp             | NYARLATHOTEP
            hastur                   | HASTUR
            The Deep Ones            | THE DEEP ONES
            """)
    void toUpperCase_convertsTextToUppercase(String input, String expected) {
        TextValue result = (TextValue) StringFunctionLibrary.toUpperCase((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "{0} equals {1} ignoring case")
    @CsvSource(delimiter = '|', textBlock = """
            Cthulhu       | CTHULHU       | true
            necronomicon  | NECRONOMICON  | true
            Dagon         | Hydra         | false
            Azathoth      | azathoth      | true
            elder         | ELDER         | true
            shoggoth      | Byakhee       | false
            """)
    void equalsIgnoreCase_comparesTextCaseInsensitively(String input1, String input2, boolean expected) {
        BooleanValue result = (BooleanValue) StringFunctionLibrary.equalsIgnoreCase((TextValue) Value.of(input1),
                (TextValue) Value.of(input2));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "trim: [{0}] -> [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            '  elder sign  '                  | elder sign
            '\t
            ph''nglui mglw''nafh
            \t'    | ph'nglui mglw'nafh
            '  iä  iä  cthulhu  fhtagn  '     | iä  iä  cthulhu  fhtagn
            '   '                             | ''
            'no whitespace'                   | no whitespace
            """)
    void trim_removesLeadingAndTrailingWhitespace(String input, String expected) {
        TextValue result = (TextValue) StringFunctionLibrary.trim((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "trimStart: [{0}] -> [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            '  summon the old ones'     | 'summon the old ones'
            '  dark ritual  '           | 'dark ritual  '
            '\t
            text'                  | text
            'no leading space'          | 'no leading space'
            """)
    void trimStart_removesLeadingWhitespaceOnly(String input, String expected) {
        TextValue result = (TextValue) StringFunctionLibrary.trimStart((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "trimEnd: [{0}] -> [{1}]")
    @CsvSource(delimiter = '|', textBlock = """
            'the stars are right  '     | 'the stars are right'
            '  madness approaches  '    | '  madness approaches'
            'text\t
            '                  | text
            'no trailing space'         | 'no trailing space'
            """)
    void trimEnd_removesTrailingWhitespaceOnly(String input, String expected) {
        val result = (TextValue) StringFunctionLibrary.trimEnd((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "isBlank: [{0}] -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ''                    | true
            '   \t
              '           | true
            '  tekeli-li  '       | false
            text                  | false
            ' '                   | true
            """)
    void isBlank_detectsEmptyOrWhitespaceOnlyText(String input, boolean expected) {
        val result = (BooleanValue) StringFunctionLibrary.isBlank((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "{0} contains {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'eldritch horror from beyond' | horror         | true
            'madness reigns'              | sanity         | false
            'The Cultist'                 | cultist        | false
            'necronomicon'                | nom            | true
            'R''lyeh'                     | lyeh           | true
            """)
    void contains_searchesForSubstring(String text, String substring, boolean expected) {
        val result = (BooleanValue) StringFunctionLibrary.contains((TextValue) Value.of(text),
                (TextValue) Value.of(substring));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "{0} starts with {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'ritual of summoning'  | ritual      | true
            'summoning ritual'     | banishing   | false
            Necronomicon           | necro       | false
            Necronomicon           | Necro       | true
            'elder things'         | elder       | true
            """)
    void startsWith_checksForPrefix(String text, String prefix, boolean expected) {
        val result = (BooleanValue) StringFunctionLibrary.startsWith((TextValue) Value.of(text),
                (TextValue) Value.of(prefix));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "{0} ends with {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'forbidden tome'   | tome      | true
            'ancient scroll'   | tome      | false
            manuscript         | SCRIPT    | false
            manuscript         | script    | true
            'deep ones'        | ones      | true
            """)
    void endsWith_checksForSuffix(String text, String suffix, boolean expected) {
        val result = (BooleanValue) StringFunctionLibrary.endsWith((TextValue) Value.of(text),
                (TextValue) Value.of(suffix));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "length of {0} is {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Hastur          | 6
            ''              | 0
            Yog-Sothoth     | 11
            'a b c'         | 5
            Nyarlathotep    | 12
            """)
    void length_countsCharacters(String input, int expected) {
        val result = (NumberValue) StringFunctionLibrary.length((TextValue) Value.of(input));
        assertEquals(expected, result.value().intValue());
    }

    @ParameterizedTest(name = "isEmpty: [{0}] -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ''              | true
            spells          | false
            '   '           | false
            'x'             | false
            """)
    void isEmpty_checksForZeroLength(String input, boolean expected) {
        val result = (BooleanValue) StringFunctionLibrary.isEmpty((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "substring({0}, {1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'elder cultist'  | 6  | cultist
            shoggoth         | 0  | shoggoth
            nightgaunt       | 5  | gaunt
            'deep one'       | 5  | 'one'
            """)
    void substring_extractsFromStartToEnd(String text, int start, String expected) {
        val result = (TextValue) StringFunctionLibrary.substring((TextValue) Value.of(text), Value.of(start));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "substring({0}, {1}) -> error")
    @CsvSource(delimiter = '|', textBlock = """
            nightgaunt  | -1
            byakhee     | 100
            text        | 10
            """)
    void substring_returnsErrorForInvalidIndices(String text, int start) {
        val result = StringFunctionLibrary.substring((TextValue) Value.of(text), Value.of(start));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "substringRange({0}, {1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            'summoning ritual'  | 10 | 16 | ritual
            'elder things'      | 0  | 5  | elder
            'deep one'          | 4  | 4  | ''
            'mi-go'             | 0  | 2  | mi
            Nyarlathotep        | 5  | 10 | athot
            """)
    void substringRange_extractsBetweenIndices(String text, int start, int end, String expected) {
        val result = (TextValue) StringFunctionLibrary.substringRange((TextValue) Value.of(text), Value.of(start),
                Value.of(end));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "substringRange({0}, {1}, {2}) -> error")
    @CsvSource(delimiter = '|', textBlock = """
            'mi-go'  | 5   | 2
            dagon    | -1  | 3
            hydra    | 0   | 100
            text     | 10  | 5
            """)
    void substringRange_returnsErrorForInvalidIndices(String text, int start, int end) {
        Value result = StringFunctionLibrary.substringRange((TextValue) Value.of(text), Value.of(start), Value.of(end));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "indexOf({0}, {1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'necronomicon:page:13'  | ':'        | 12
            'elder sign'            | pentagram  | -1
            'cthulhu fhtagn'        | cthulhu    | 0
            'ia ia cthulhu'         | ia         | 0
            shoggoth                | goth       | 4
            """)
    void indexOf_findsFirstOccurrence(String text, String substring, int expected) {
        val result = (NumberValue) StringFunctionLibrary.indexOf((TextValue) Value.of(text),
                (TextValue) Value.of(substring));
        assertEquals(expected, result.value().intValue());
    }

    @ParameterizedTest(name = "lastIndexOf({0}, {1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'ia ia cthulhu fhtagn'  | ia      | 3
            'ancient tome'          | scroll  | -1
            shoggoth                | shog    | 0
            'ia ia ia'              | ia      | 6
            'a b a b'               | a       | 4
            """)
    void lastIndexOf_findsLastOccurrence(String text, String substring, int expected) {
        val result = (NumberValue) StringFunctionLibrary.lastIndexOf((TextValue) Value.of(text),
                (TextValue) Value.of(substring));
        assertEquals(expected, result.value().intValue());
    }

    @ParameterizedTest(name = "join([{0}], {1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'Cthulhu,Hastur,Azathoth'  | ', '  | 'Cthulhu, Hastur, Azathoth'
            ''                         | ','   | ''
            Nyarlathotep               | '|'   | Nyarlathotep
            'a,b,c'                    | '-'   | 'a-b-c'
            """)
    void join_combinesArrayElementsWithDelimiter(String elements, String delimiter, String expected) {
        val builder = ArrayValue.builder();
        if (!elements.isEmpty()) {
            for (String element : elements.split(",")) {
                builder.add(Value.of(element));
            }
        }
        val result = (TextValue) StringFunctionLibrary.join(builder.build(), (TextValue) Value.of(delimiter));
        assertEquals(expected, result.value());
    }

    @Test
    void join_returnsErrorWhenTextFollowedByNumber() {
        val builder = ArrayValue.builder();
        builder.add(Value.of("text"));
        builder.add(Value.of(42));
        Value result = StringFunctionLibrary.join(builder.build(), (TextValue) Value.of(","));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void join_returnsErrorWhenNumberFollowedByText() {
        val builder = ArrayValue.builder();
        builder.add(Value.of(42));
        builder.add(Value.of("text"));
        Value result = StringFunctionLibrary.join(builder.build(), (TextValue) Value.of(","));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void join_returnsErrorForBooleanElements() {
        val builder = ArrayValue.builder();
        builder.add(Value.of(true));
        builder.add(Value.of(false));
        Value result = StringFunctionLibrary.join(builder.build(), (TextValue) Value.of(","));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "concat({0}) -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            'necro,nomicon'                                      | necronomicon
            'Ph''nglui ,mglw''nafh ,Cthulhu ,R''lyeh'            | 'Ph''nglui mglw''nafh Cthulhu R''lyeh'
            'Yog-Sothoth'                                        | Yog-Sothoth
            'a,b,c,d,e'                                          | abcde
            """)
    void concat_combinesMultipleStrings(String parts, String expected) {
        String[]    partsArray = parts.split(",");
        TextValue[] values     = new TextValue[partsArray.length];
        for (int i = 0; i < partsArray.length; i++) {
            values[i] = (TextValue) Value.of(partsArray[i]);
        }
        val result = (TextValue) StringFunctionLibrary.concat(values);
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "replace({0}, {1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            'madness and madness and more madness'  | madness  | sanity  | 'sanity and sanity and more sanity'
            'ancient ritual'                        | modern   | elder   | 'ancient ritual'
            'horror horror'                         | horror   | terror  | 'terror terror'
            abcabc                                  | a        | x       | xbcxbc
            """)
    void replace_replacesAllOccurrences(String text, String target, String replacement, String expected) {
        val result = (TextValue) StringFunctionLibrary.replace((TextValue) Value.of(text), (TextValue) Value.of(target),
                (TextValue) Value.of(replacement));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "replace with empty target returns error")
    @ValueSource(strings = { "text", "another", "something" })
    void replace_returnsErrorForEmptyTarget(String text) {
        val result = StringFunctionLibrary.replace((TextValue) Value.of(text), (TextValue) Value.of(""),
                (TextValue) Value.of("x"));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "replaceFirst({0}, {1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            'horror upon horror'  | horror  | terror  | 'terror upon horror'
            'elder sign'          | yellow  | black   | 'elder sign'
            'a b a b'             | a       | x       | 'x b a b'
            abcabc                | bc      | XY      | aXYabc
            """)
    void replaceFirst_replacesOnlyFirstOccurrence(String text, String target, String replacement, String expected) {
        val result = (TextValue) StringFunctionLibrary.replaceFirst((TextValue) Value.of(text),
                (TextValue) Value.of(target), (TextValue) Value.of(replacement));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "replaceFirst with empty target returns error")
    @ValueSource(strings = { "text", "another", "something" })
    void replaceFirst_returnsErrorForEmptyTarget(String text) {
        val result = StringFunctionLibrary.replaceFirst((TextValue) Value.of(text), (TextValue) Value.of(""),
                (TextValue) Value.of("x"));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "leftPad({0}, {1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            '13' | 5 | '0' | '00013'
            Nyarlathotep | 5 | x | Nyarlathotep
            ritual | 10 | ' ' | '    ritual'
            abc | 7 | '-' | '----abc'
            """)
    void leftPad_addsLeadingPadding(String text, int length, String padChar, String expected) {
        val result = (TextValue) StringFunctionLibrary.leftPad((TextValue) Value.of(text), Value.of(length),
                (TextValue) Value.of(padChar));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "leftPad with multi-char pad returns error")
    @ValueSource(strings = { "xx", "ab", "---" })
    void leftPad_returnsErrorForMultiCharacterPad(String padChar) {
        val result = StringFunctionLibrary.leftPad((TextValue) Value.of("text"), Value.of(10),
                (TextValue) Value.of(padChar));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "rightPad({0}, {1}, {2}) -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            cult | 10 | '-' | 'cult------'
            'Shub-Niggurath' | 5 | x | 'Shub-Niggurath'
            tome | 10 | ' ' | 'tome      '
            abc | 7 | '*' | 'abc****'
            """)
    void rightPad_addsTrailingPadding(String text, int length, String padChar, String expected) {
        val result = (TextValue) StringFunctionLibrary.rightPad((TextValue) Value.of(text), Value.of(length),
                (TextValue) Value.of(padChar));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "rightPad with multi-char pad returns error")
    @ValueSource(strings = { "ab", "xyz", "--" })
    void rightPad_returnsErrorForMultiCharacterPad(String padChar) {
        var result = StringFunctionLibrary.rightPad((TextValue) Value.of("text"), Value.of(10),
                (TextValue) Value.of(padChar));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "repeat({0}, {1}) -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            'ia! ' | 3 | 'ia! ia! ia! '
            cthulhu | 0 | ''
            fhtagn | 1 | fhtagn
            x | 5 | xxxxx
            abc | 3 | abcabcabc
            """)
    void repeat_repeatsStringMultipleTimes(String text, int count, String expected) {
        val result = (TextValue) StringFunctionLibrary.repeat((TextValue) Value.of(text), Value.of(count));
        assertEquals(expected, result.value());
    }

    @ParameterizedTest(name = "repeat with count {0} returns error")
    @ValueSource(ints = { -1, -10, 10_001, 20_000 })
    void repeat_returnsErrorForInvalidCount(int count) {
        var result = StringFunctionLibrary.repeat((TextValue) Value.of("x"), Value.of(count));
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "repeat handles maximum allowed count")
    @ValueSource(ints = { 10_000, 9_999, 5_000 })
    void repeat_handlesLargeValidCounts(int count) {
        val result = (TextValue) StringFunctionLibrary.repeat((TextValue) Value.of("x"), Value.of(count));
        assertEquals(count, result.value().length());
    }

    @ParameterizedTest(name = "reverse({0}) -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            cthulhu | uhluhtc
            fhtagn | ngathf
            '' | ''
            R | R
            rotator | rotator
            abc | cba
            'ia ia' | 'ai ai'
            """)
    void reverse_reversesCharacterOrder(String input, String expected) {
        var result = (TextValue) StringFunctionLibrary.reverse((TextValue) Value.of(input));
        assertEquals(expected, result.value());
    }
}
