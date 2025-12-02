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
package io.sapl.api.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class TextValueTests {

    @ParameterizedTest(name = "TextValue(\"{0}\", {1}) construction")
    @MethodSource
    void when_constructedWithTextAndSecretFlag_then_createsValue(String text, boolean secret) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new TextValue(text, metadata);

        assertThat(value.value()).isEqualTo(text);
        assertThat(value.isSecret()).isEqualTo(secret);
    }

    static Stream<Arguments> when_constructedWithTextAndSecretFlag_then_createsValue() {
        return Stream.of(arguments("test", false), arguments("test", true), arguments("", false), arguments("", true),
                arguments("longer text with spaces", false));
    }

    @Test
    void when_constructedWithNullValue_then_throws() {
        assertThatThrownBy(() -> new TextValue(null, ValueMetadata.EMPTY)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void when_factoryCalledWithEmptyString_then_returnsEmptyTextSingleton() {
        assertThat(Value.of("")).isSameAs(Value.EMPTY_TEXT);
    }

    @Test
    void when_factoryCalledWithNonEmptyString_then_createsNewInstance() {
        var text1 = Value.of("test");
        var text2 = Value.of("test");

        assertThat(text1).isEqualTo(text2).isNotSameAs(text2);
    }

    @ParameterizedTest(name = "asSecret() on \"{0}\"")
    @ValueSource(strings = { "test", "", "very long text string with lots of characters" })
    void when_asSecretCalled_then_createsSecretCopyOrReturnsSameInstance(String text) {
        var original      = new TextValue(text, ValueMetadata.EMPTY);
        var alreadySecret = new TextValue(text, ValueMetadata.SECRET_EMPTY);

        var secretCopy = original.asSecret();
        assertThat(secretCopy.isSecret()).isTrue();
        assertThat(((TextValue) secretCopy).value()).isEqualTo(text);
        assertThat(alreadySecret.asSecret()).isSameAs(alreadySecret);
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource
    void when_equalsAndHashCodeCompared_then_comparesByValueIgnoringSecretFlag(TextValue value1, TextValue value2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
        } else {
            assertThat(value1).isNotEqualTo(value2).doesNotHaveSameHashCodeAs(value2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByValueIgnoringSecretFlag() {
        return Stream.of(
                arguments(new TextValue("test", ValueMetadata.EMPTY), new TextValue("test", ValueMetadata.SECRET_EMPTY),
                        true),
                arguments(new TextValue("", ValueMetadata.EMPTY), new TextValue("", ValueMetadata.SECRET_EMPTY), true),
                arguments(new TextValue("test", ValueMetadata.EMPTY), new TextValue("other", ValueMetadata.EMPTY),
                        false),
                arguments(new TextValue("test", ValueMetadata.SECRET_EMPTY),
                        new TextValue("other", ValueMetadata.SECRET_EMPTY), false));
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource
    void when_toStringCalled_then_showsQuotedValueOrPlaceholder(String text, boolean secret, String expected) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new TextValue(text, metadata);

        assertThat(value).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsQuotedValueOrPlaceholder() {
        return Stream.of(arguments("hello", false, "\"hello\""), arguments("secret", true, "***SECRET***"),
                arguments("", false, "\"\""), arguments("", true, "***SECRET***"));
    }

    @ParameterizedTest(name = "Text: {0}")
    @ValueSource(strings = { "simple text", "", " ", "   multiple   spaces   ", "Line1\nLine2\tTabbed",
            "Unicode: ä¸–ç•Œ ðŸŒ", "Quotes: \"nested\"", "x" // single character
    })
    void when_variousTextContent_then_handledCorrectly(String text) {
        var value = new TextValue(text, ValueMetadata.EMPTY);

        assertThat(value.value()).isEqualTo(text);
        assertThat(value).hasToString("\"" + text + "\"");
    }

    @Test
    void when_veryLongStringUsed_then_supported() {
        var longString = "a".repeat(10000);
        var value      = new TextValue(longString, ValueMetadata.EMPTY);

        assertThat(value.value()).hasSize(10000);
    }

    @Test
    void when_patternMatchingUsed_then_extractsValueCorrectly() {
        Value username = Value.of("admin");

        assertThat(username).isInstanceOf(TextValue.class);
        if (username instanceof TextValue(String name, ValueMetadata ignored)) {
            assertThat(name).isEqualTo("admin");
        }
    }

    @Test
    void when_emptyTextConstantChecked_then_notSecretAndEmpty() {
        assertThat(Value.EMPTY_TEXT.isSecret()).isFalse();
        assertThat(((TextValue) Value.EMPTY_TEXT).value()).isEmpty();
    }

}
