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
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("BooleanValue Tests")
class BooleanValueTests {

    @ParameterizedTest(name = "BooleanValue({0}) construction")
    @ValueSource(booleans = { true, false })
    @DisplayName("Constructor creates value correctly")
    void when_constructed_then_createsValue(boolean value) {
        var boolValue = new BooleanValue(value);

        assertThat(boolValue.value()).isEqualTo(value);
    }

    @ParameterizedTest(name = "Value.of({0}) returns singleton")
    @ValueSource(booleans = { true, false })
    @DisplayName("Factory returns singleton")
    void when_factoryCalledWithBoolean_then_returnsSingleton(boolean value) {
        var expected = value ? Value.TRUE : Value.FALSE;

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource
    @DisplayName("equals() and hashCode() compare by value")
    void when_equalsAndHashCodeCompared_then_comparesByValue(BooleanValue value1, BooleanValue value2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
        } else {
            assertThat(value1).isNotEqualTo(value2).doesNotHaveSameHashCodeAs(value2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByValue() {
        return Stream.of(arguments(new BooleanValue(true), new BooleanValue(true), true),
                arguments(new BooleanValue(false), new BooleanValue(false), true),
                arguments(new BooleanValue(true), new BooleanValue(false), false),
                arguments(Value.TRUE, new BooleanValue(true), true),
                arguments(Value.FALSE, new BooleanValue(false), true));
    }

    @ParameterizedTest(name = "{0} toString()={1}")
    @MethodSource
    @DisplayName("toString() shows value")
    void when_toStringCalled_then_showsValue(boolean value, String expected) {
        var boolValue = new BooleanValue(value);

        assertThat(boolValue).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsValue() {
        return Stream.of(arguments(true, "true"), arguments(false, "false"));
    }

    @Test
    @DisplayName("Pattern matching extracts value correctly")
    void when_patternMatchingUsed_then_extractsValueCorrectly() {
        Value granted = Value.of(true);
        Value denied  = Value.of(false);

        assertThat(granted).isInstanceOf(BooleanValue.class);
        assertThat(denied).isInstanceOf(BooleanValue.class);

        if (granted instanceof BooleanValue(boolean allowed)) {
            assertThat(allowed).isTrue();
        }
        if (denied instanceof BooleanValue(boolean allowed)) {
            assertThat(allowed).isFalse();
        }
    }

    @Test
    @DisplayName("Value.TRUE constant is true")
    void when_trueConstantChecked_then_isTrue() {
        assertThat(Value.TRUE.value()).isTrue();
        assertThat(Value.TRUE).isInstanceOf(BooleanValue.class);
    }

    @Test
    @DisplayName("Value.FALSE constant is false")
    void when_falseConstantChecked_then_isFalse() {
        assertThat(Value.FALSE.value()).isFalse();
        assertThat(Value.FALSE).isInstanceOf(BooleanValue.class);
    }

    @Test
    @DisplayName("BooleanValue is not equal to other value types")
    void when_comparedToOtherValueTypes_then_notEqual() {
        assertThat(Value.TRUE).isNotEqualTo(Value.of(1)).isNotEqualTo(Value.of("true")).isNotEqualTo(Value.NULL);
        assertThat(Value.FALSE).isNotEqualTo(Value.of(0)).isNotEqualTo(Value.of("false")).isNotEqualTo(Value.UNDEFINED);
    }
}
