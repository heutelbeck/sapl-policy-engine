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
package io.sapl.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("TextValue Tests")
class TextValueTests {

    @ParameterizedTest(name = "TextValue(\"{0}\") construction")
    @ValueSource(strings = { "test", "", "longer text with spaces" })
    @DisplayName("Constructor creates value correctly")
    void when_constructed_then_createsValue(String text) {
        var value = new TextValue(text);

        assertThat(value.value()).isEqualTo(text);
    }

    @Test
    @DisplayName("Constructor with null throws NullPointerException")
    void when_constructedWithNullValue_then_throws() {
        assertThatThrownBy(() -> new TextValue(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Factory with empty string returns EMPTY_TEXT singleton")
    void when_factoryCalledWithEmptyString_then_returnsEmptyTextSingleton() {
        assertThat(Value.of("")).isSameAs(Value.EMPTY_TEXT);
    }

    @Test
    @DisplayName("Factory with non-empty string creates new instance")
    void when_factoryCalledWithNonEmptyString_then_createsNewInstance() {
        var text1 = Value.of("test");
        var text2 = Value.of("test");

        assertThat(text1).isEqualTo(text2).isNotSameAs(text2);
    }

    @ParameterizedTest(name = "{0} equals {1}: {2}")
    @MethodSource
    @DisplayName("equals() and hashCode() compare by value")
    void when_equalsAndHashCodeCompared_then_comparesByValue(TextValue value1, TextValue value2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
        } else {
            assertThat(value1).isNotEqualTo(value2).doesNotHaveSameHashCodeAs(value2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByValue() {
        return Stream.of(arguments(new TextValue("test"), new TextValue("test"), true),
                arguments(new TextValue(""), new TextValue(""), true),
                arguments(new TextValue("test"), new TextValue("other"), false),
                arguments(Value.EMPTY_TEXT, new TextValue(""), true));
    }

    @ParameterizedTest(name = "\"{0}\" toString()=\"{1}\"")
    @MethodSource
    @DisplayName("toString() shows quoted value")
    void when_toStringCalled_then_showsQuotedValue(String text, String expected) {
        var value = new TextValue(text);

        assertThat(value).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsQuotedValue() {
        return Stream.of(arguments("hello", "\"hello\""), arguments("", "\"\""));
    }

    @ParameterizedTest(name = "Text: {0}")
    @ValueSource(strings = { "simple text", "", " ", "   multiple   spaces   ", "Line1\nLine2\tTabbed",
            "Unicode: 世界 🌍", "Quotes: \"nested\"", "x" // single character
    })
    @DisplayName("Various text content handled correctly")
    void when_variousTextContent_then_handledCorrectly(String text) {
        var value = new TextValue(text);

        assertThat(value.value()).isEqualTo(text);
        assertThat(value).hasToString("\"" + text + "\"");
    }

    @Test
    @DisplayName("Very long string is supported")
    void when_veryLongStringUsed_then_supported() {
        var longString = "a".repeat(10000);
        var value      = new TextValue(longString);

        assertThat(value.value()).hasSize(10000);
    }

    @Test
    @DisplayName("Pattern matching extracts value correctly")
    void when_patternMatchingUsed_then_extractsValueCorrectly() {
        Value username = Value.of("admin");

        assertThat(username).isInstanceOf(TextValue.class);
        if (username instanceof TextValue(String name)) {
            assertThat(name).isEqualTo("admin");
        }
    }

    @Test
    @DisplayName("EMPTY_TEXT constant is empty string")
    void when_emptyTextConstantChecked_then_isEmptyString() {
        assertThat(((TextValue) Value.EMPTY_TEXT).value()).isEmpty();
    }

    @Test
    @DisplayName("TextValue is not equal to other value types")
    void when_comparedToOtherValueTypes_then_notEqual() {
        var text = Value.of("123");

        assertThat(text).isNotEqualTo(Value.of(123)).isNotEqualTo(Value.TRUE).isNotEqualTo(Value.NULL);
    }
}
