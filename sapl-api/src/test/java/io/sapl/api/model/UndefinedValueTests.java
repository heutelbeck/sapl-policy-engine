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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class UndefinedValueTests {

    @ParameterizedTest(name = "UndefinedValue(secret={0}) construction")
    @MethodSource
    void when_constructedWithSecretFlag_then_createsValue(boolean secret) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new UndefinedValue(metadata);

        assertThat(value.isSecret()).isEqualTo(secret);
    }

    static Stream<Arguments> when_constructedWithSecretFlag_then_createsValue() {
        return Stream.of(arguments(false), arguments(true));
    }

    @Test
    void when_asSecretCalled_then_returnsSecretUndefined() {
        var regular = new UndefinedValue(ValueMetadata.EMPTY);

        assertThat(regular.asSecret().isSecret()).isTrue();
        assertThat(regular.asSecret()).isEqualTo(UndefinedValue.SECRET_UNDEFINED);
        assertThat(UndefinedValue.SECRET_UNDEFINED.asSecret()).isSameAs(UndefinedValue.SECRET_UNDEFINED);
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource
    void when_equalsAndHashCodeCompared_then_allUndefinedValuesAreEqual(UndefinedValue value1, UndefinedValue value2,
            boolean shouldBeEqual) {
        assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_allUndefinedValuesAreEqual() {
        return Stream.of(
                arguments(new UndefinedValue(ValueMetadata.EMPTY), new UndefinedValue(ValueMetadata.EMPTY), true),
                arguments(new UndefinedValue(ValueMetadata.EMPTY), new UndefinedValue(ValueMetadata.SECRET_EMPTY),
                        true),
                arguments(new UndefinedValue(ValueMetadata.SECRET_EMPTY),
                        new UndefinedValue(ValueMetadata.SECRET_EMPTY), true));
    }

    @Test
    void when_comparedToOtherValueTypes_then_notEqual() {
        var undefinedValue = new UndefinedValue(ValueMetadata.EMPTY);

        assertThat(undefinedValue).isNotEqualTo(Value.NULL).isNotEqualTo(Value.of(0))
                .isNotEqualTo(Value.of("undefined"));
    }

    @ParameterizedTest(name = "secret={0} toString()={1}")
    @MethodSource
    void when_toStringCalled_then_showsUndefinedOrPlaceholder(boolean secret, String expected) {
        var metadata = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var value    = new UndefinedValue(metadata);

        assertThat(value).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsUndefinedOrPlaceholder() {
        return Stream.of(arguments(false, "undefined"), arguments(true, "***SECRET***"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constantsChecked_then_haveExpectedSecretFlag(String description, Value constant, boolean expectedSecret) {
        assertThat(constant.isSecret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> when_constantsChecked_then_haveExpectedSecretFlag() {
        return Stream.of(arguments("Value.UNDEFINED is not secret", Value.UNDEFINED, false),
                arguments("UndefinedValue.SECRET_UNDEFINED is secret", UndefinedValue.SECRET_UNDEFINED, true));
    }
}
