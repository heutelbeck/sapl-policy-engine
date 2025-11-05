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

@DisplayName("BooleanValue Tests")
class BooleanValueTests {

    @ParameterizedTest(name = "BooleanValue({0}, {1}) construction")
    @MethodSource("provideBooleanCombinations")
    @DisplayName("Constructor creates BooleanValue with all combinations")
    void constructorCreatesValue(boolean value, boolean secret) {
        var boolValue = new BooleanValue(value, secret);

        assertThat(boolValue.value()).isEqualTo(value);
        assertThat(boolValue.secret()).isEqualTo(secret);
    }

    @ParameterizedTest(name = "Value.of({0}) returns singleton")
    @ValueSource(booleans = {true, false})
    @DisplayName("Value.of() returns singleton constants")
    void factoryReturnsSingletons(boolean value) {
        var expected = value ? Value.TRUE : Value.FALSE;

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @ParameterizedTest(name = "asSecret() on {0} returns singleton")
    @ValueSource(booleans = {true, false})
    @DisplayName("asSecret() returns appropriate singleton")
    void asSecretReturnsSingleton(boolean value) {
        var original = new BooleanValue(value, false);
        var expected = value ? BooleanValue.SECRET_TRUE : BooleanValue.SECRET_FALSE;

        assertThat(original.asSecret())
                .isSameAs(expected)
                .isSameAs(expected.asSecret());
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource("provideEqualityHashCodeCases")
    @DisplayName("equals() and hashCode() compare by value only, ignoring secret flag")
    void equalsAndHashCode(BooleanValue value1, BooleanValue value2, boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1)
                    .isEqualTo(value2)
                    .hasSameHashCodeAs(value2);
        } else {
            assertThat(value1)
                    .isNotEqualTo(value2)
                    .doesNotHaveSameHashCodeAs(value2);
        }
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource("provideToStringCases")
    @DisplayName("toString() shows value or placeholder")
    void toStringShowsValueOrPlaceholder(boolean value, boolean secret, String expected) {
        var boolValue = new BooleanValue(value, secret);

        assertThat(boolValue.toString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Pattern matching extracts value correctly")
    void patternMatchingExtractsValue() {
        Value granted = Value.of(true);
        Value denied = Value.of(false);

        var grantedResult = switch (granted) {
            case BooleanValue(boolean allowed, boolean ignore) -> allowed ? "PERMIT" : "DENY";
            default -> "INDETERMINATE";
        };

        var deniedResult = switch (denied) {
            case BooleanValue(boolean allowed, boolean ignore) -> allowed ? "PERMIT" : "DENY";
            default -> "INDETERMINATE";
        };

        assertThat(grantedResult).isEqualTo("PERMIT");
        assertThat(deniedResult).isEqualTo("DENY");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideConstantCases")
    @DisplayName("Constants have expected secret flag")
    void constantsHaveExpectedSecretFlag(String description, BooleanValue constant, boolean expectedSecret) {
        assertThat(constant.secret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> provideBooleanCombinations() {
        return Stream.of(
            Arguments.of(true, false),
            Arguments.of(true, true),
            Arguments.of(false, false),
            Arguments.of(false, true)
        );
    }

    static Stream<Arguments> provideEqualityHashCodeCases() {
        return Stream.of(
            Arguments.of(new BooleanValue(true, false), new BooleanValue(true, true), true),
            Arguments.of(new BooleanValue(false, false), new BooleanValue(false, true), true),
            Arguments.of(new BooleanValue(true, false), new BooleanValue(false, false), false),
            Arguments.of(new BooleanValue(true, true), new BooleanValue(false, true), false)
        );
    }

    static Stream<Arguments> provideToStringCases() {
        return Stream.of(
            Arguments.of(true, false, "true"),
            Arguments.of(false, false, "false"),
            Arguments.of(true, true, "***SECRET***"),
            Arguments.of(false, true, "***SECRET***")
        );
    }

    static Stream<Arguments> provideConstantCases() {
        return Stream.of(
            Arguments.of("Value.TRUE is not secret", Value.TRUE, false),
            Arguments.of("Value.FALSE is not secret", Value.FALSE, false),
            Arguments.of("BooleanValue.SECRET_TRUE is secret", BooleanValue.SECRET_TRUE, true),
            Arguments.of("BooleanValue.SECRET_FALSE is secret", BooleanValue.SECRET_FALSE, true)
        );
    }
}