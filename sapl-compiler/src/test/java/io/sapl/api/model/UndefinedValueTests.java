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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UndefinedValue Tests")
class UndefinedValueTests {

    @ParameterizedTest(name = "UndefinedValue(secret={0}) construction")
    @MethodSource("provideSecretFlags")
    @DisplayName("Constructor creates UndefinedValue")
    void constructorCreatesValue(boolean secret) {
        var value = new UndefinedValue(secret);

        assertThat(value.secret()).isEqualTo(secret);
    }

    @Test
    @DisplayName("asSecret() returns SECRET_UNDEFINED singleton")
    void asSecretReturnsSingleton() {
        var regular = new UndefinedValue(false);

        assertThat(regular.asSecret()).isSameAs(UndefinedValue.SECRET_UNDEFINED)
                .isSameAs(UndefinedValue.SECRET_UNDEFINED.asSecret());
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource("provideEqualityHashCodeCases")
    @DisplayName("All UndefinedValues are equal regardless of secret flag")
    void equalsAndHashCode(UndefinedValue value1, UndefinedValue value2, boolean shouldBeEqual) {
        assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
    }

    @Test
    @DisplayName("UndefinedValue not equal to other Value types")
    void notEqualToOtherValueTypes() {
        var undefinedValue = new UndefinedValue(false);

        assertThat(undefinedValue).isNotEqualTo(Value.NULL).isNotEqualTo(Value.of(0))
                .isNotEqualTo(Value.of("undefined"));
    }

    @ParameterizedTest(name = "secret={0} toString()={1}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() shows undefined or placeholder")
    void toStringShowsUndefinedOrPlaceholder(boolean secret, String expected) {
        var value = new UndefinedValue(secret);

        assertThat(value).hasToString(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideConstantCases")
    @DisplayName("Constants have expected secret flag")
    void constantsHaveExpectedSecretFlag(String description, Value constant, boolean expectedSecret) {
        assertThat(constant.secret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> provideSecretFlags() {
        return Stream.of(Arguments.of(false), Arguments.of(true));
    }

    static Stream<Arguments> provideEqualityHashCodeCases() {
        return Stream.of(Arguments.of(new UndefinedValue(false), new UndefinedValue(false), true),
                Arguments.of(new UndefinedValue(false), new UndefinedValue(true), true),
                Arguments.of(new UndefinedValue(true), new UndefinedValue(true), true));
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(Arguments.of(false, "undefined"), Arguments.of(true, "***SECRET***"));
    }

    static Stream<Arguments> provideConstantCases() {
        return Stream.of(Arguments.of("Value.UNDEFINED is not secret", Value.UNDEFINED, false),
                Arguments.of("UndefinedValue.SECRET_UNDEFINED is secret", UndefinedValue.SECRET_UNDEFINED, true));
    }
}
