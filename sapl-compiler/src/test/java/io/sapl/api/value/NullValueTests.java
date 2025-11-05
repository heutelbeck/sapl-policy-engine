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
package io.sapl.api.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NullValue Tests")
class NullValueTests {

    @ParameterizedTest(name = "NullValue(secret={0}) construction")
    @MethodSource("provideSecretFlags")
    @DisplayName("Constructor creates NullValue")
    void constructorCreatesValue(boolean secret) {
        var value = new NullValue(secret);

        assertThat(value.secret()).isEqualTo(secret);
    }

    @Test
    @DisplayName("asSecret() returns SECRET_NULL singleton")
    void asSecretReturnsSingleton() {
        var regular = new NullValue(false);

        assertThat(regular.asSecret()).isSameAs(NullValue.SECRET_NULL).isSameAs(NullValue.SECRET_NULL.asSecret());
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource("provideEqualityHashCodeCases")
    @DisplayName("All NullValues are equal regardless of secret flag")
    void equalsAndHashCode(NullValue value1, NullValue value2, boolean shouldBeEqual) {
        assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
    }

    @Test
    @DisplayName("NullValue not equal to other Value types")
    void notEqualToOtherValueTypes() {
        var nullValue = new NullValue(false);

        assertThat(nullValue).isNotEqualTo(Value.UNDEFINED).isNotEqualTo(Value.of(0)).isNotEqualTo(Value.of("null"));
    }

    @ParameterizedTest(name = "secret={0} toString()={1}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() shows null or placeholder")
    void toStringShowsNullOrPlaceholder(boolean secret, String expected) {
        var value = new NullValue(secret);

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
        return Stream.of(Arguments.of(new NullValue(false), new NullValue(false), true),
                Arguments.of(new NullValue(false), new NullValue(true), true),
                Arguments.of(new NullValue(true), new NullValue(true), true));
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(Arguments.of(false, "null"), Arguments.of(true, "***SECRET***"));
    }

    static Stream<Arguments> provideConstantCases() {
        return Stream.of(Arguments.of("Value.NULL is not secret", Value.NULL, false),
                Arguments.of("NullValue.SECRET_NULL is secret", NullValue.SECRET_NULL, true));
    }
}
