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
package io.sapl.api.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TextValue Tests")
class TextValueTests {

    @ParameterizedTest(name = "TextValue(\"{0}\", {1}) construction")
    @MethodSource("provideTextCombinations")
    @DisplayName("Constructor creates TextValue")
    void constructorCreatesValue(String text, boolean secret) {
        var value = new TextValue(text, secret);

        assertThat(value.value()).isEqualTo(text);
        assertThat(value.secret()).isEqualTo(secret);
    }

    @Test
    @DisplayName("Constructor with null value throws NullPointerException")
    void constructorNullValueThrows() {
        assertThatThrownBy(() -> new TextValue(null, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Value.of() with empty string returns EMPTY_TEXT singleton")
    void factoryReturnsEmptySingleton() {
        assertThat(Value.of("")).isSameAs(Value.EMPTY_TEXT);
    }

    @Test
    @DisplayName("Value.of() with non-empty string creates new instance")
    void factoryCreatesNewInstance() {
        var text1 = Value.of("test");
        var text2 = Value.of("test");

        assertThat(text1).isEqualTo(text2).isNotSameAs(text2);
    }

    @ParameterizedTest(name = "asSecret() on \"{0}\"")
    @ValueSource(strings = { "test", "", "very long text string with lots of characters" })
    @DisplayName("asSecret() creates secret copy or returns same instance")
    void asSecretBehavior(String text) {
        var original      = new TextValue(text, false);
        var alreadySecret = new TextValue(text, true);

        var secretCopy = original.asSecret();
        assertThat(secretCopy.secret()).isTrue();
        assertThat(((TextValue) secretCopy).value()).isEqualTo(text);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource("provideEqualityHashCodeCases")
    @DisplayName("equals() and hashCode() compare by value only, ignoring secret flag")
    void equalsAndHashCode(TextValue value1, TextValue value2, boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
        } else {
            assertThat(value1).isNotEqualTo(value2).doesNotHaveSameHashCodeAs(value2);
        }
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() shows quoted value or placeholder")
    void toStringShowsQuotedValueOrPlaceholder(String text, boolean secret, String expected) {
        var value = new TextValue(text, secret);

        assertThat(value.toString()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Text: {0}")
    @ValueSource(strings = { "simple text", "", " ", "   multiple   spaces   ", "Line1\nLine2\tTabbed",
            "Unicode: ä¸–ç•Œ ðŸŒ", "Quotes: \"nested\"", "x" // single character
    })
    @DisplayName("Various text content handled correctly")
    void variousTextContent(String text) {
        var value = new TextValue(text, false);

        assertThat(value.value()).isEqualTo(text);
        assertThat(value.toString()).isEqualTo("\"" + text + "\"");
    }

    @Test
    @DisplayName("Very long string supported")
    void veryLongString() {
        var longString = "a".repeat(10000);
        var value      = new TextValue(longString, false);

        assertThat(value.value()).hasSize(10000);
    }

    @Test
    @DisplayName("Pattern matching extracts value correctly")
    void patternMatchingExtractsValue() {
        Value username = Value.of("admin");

        var result = switch (username) {
        case TextValue(String name, boolean i) when "admin".equals(name) -> "Administrator access";
        case TextValue(String name, boolean i)                           -> "User " + name;
        default                                                          -> "Invalid";
        };

        assertThat(result).isEqualTo("Administrator access");
    }

    @Test
    @DisplayName("EMPTY_TEXT constant is not secret and empty")
    void emptyTextConstantNotSecret() {
        assertThat(Value.EMPTY_TEXT.secret()).isFalse();
        assertThat(((TextValue) Value.EMPTY_TEXT).value()).isEmpty();
    }

    static Stream<Arguments> provideTextCombinations() {
        return Stream.of(Arguments.of("test", false), Arguments.of("test", true), Arguments.of("", false),
                Arguments.of("", true), Arguments.of("longer text with spaces", false));
    }

    static Stream<Arguments> provideEqualityHashCodeCases() {
        return Stream.of(Arguments.of(new TextValue("test", false), new TextValue("test", true), true),
                Arguments.of(new TextValue("", false), new TextValue("", true), true),
                Arguments.of(new TextValue("test", false), new TextValue("other", false), false),
                Arguments.of(new TextValue("test", true), new TextValue("other", true), false));
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(Arguments.of("hello", false, "\"hello\""), Arguments.of("secret", true, "***SECRET***"),
                Arguments.of("", false, "\"\""), Arguments.of("", true, "***SECRET***"));
    }
}
