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
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BooleanValueTests {

    @ParameterizedTest(name = "BooleanValue({0}, {1}) construction")
    @MethodSource
    void when_constructedWithValueAndSecretFlag_then_createsValue(boolean value, boolean secret) {
        var metadata  = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var boolValue = new BooleanValue(value, metadata);

        assertThat(boolValue.value()).isEqualTo(value);
        assertThat(boolValue.isSecret()).isEqualTo(secret);
    }

    static Stream<Arguments> when_constructedWithValueAndSecretFlag_then_createsValue() {
        return Stream.of(arguments(true, false), arguments(true, true), arguments(false, false),
                arguments(false, true));
    }

    @ParameterizedTest(name = "Value.of({0}) returns singleton")
    @ValueSource(booleans = { true, false })
    void when_factoryCalledWithBoolean_then_returnsSingleton(boolean value) {
        var expected = value ? Value.TRUE : Value.FALSE;

        assertThat(Value.of(value)).isSameAs(expected);
    }

    @ParameterizedTest(name = "asSecret() on {0} returns secret value")
    @ValueSource(booleans = { true, false })
    void when_asSecretCalled_then_returnsSecretValueWithSameBooleanValue(boolean value) {
        var original = new BooleanValue(value, ValueMetadata.EMPTY);
        var secret   = original.asSecret();

        assertThat(secret.isSecret()).isTrue();
        assertThat(secret).isEqualTo(original);
    }

    @ParameterizedTest(name = "asSecret() on already secret {0} returns same instance")
    @ValueSource(booleans = { true, false })
    void when_asSecretCalledOnSecretValue_then_returnsSameInstance(boolean value) {
        var secretOriginal = new BooleanValue(value, ValueMetadata.SECRET_EMPTY);

        assertThat(secretOriginal.asSecret()).isSameAs(secretOriginal);
    }

    @ParameterizedTest(name = "{0}={1}, equal={2}")
    @MethodSource
    void when_equalsAndHashCodeCompared_then_comparesByValueIgnoringSecretFlag(BooleanValue value1, BooleanValue value2,
            boolean shouldBeEqual) {
        if (shouldBeEqual) {
            assertThat(value1).isEqualTo(value2).hasSameHashCodeAs(value2);
        } else {
            assertThat(value1).isNotEqualTo(value2).doesNotHaveSameHashCodeAs(value2);
        }
    }

    static Stream<Arguments> when_equalsAndHashCodeCompared_then_comparesByValueIgnoringSecretFlag() {
        return Stream.of(
                arguments(new BooleanValue(true, ValueMetadata.EMPTY),
                        new BooleanValue(true, ValueMetadata.SECRET_EMPTY), true),
                arguments(new BooleanValue(false, ValueMetadata.EMPTY),
                        new BooleanValue(false, ValueMetadata.SECRET_EMPTY), true),
                arguments(new BooleanValue(true, ValueMetadata.EMPTY), new BooleanValue(false, ValueMetadata.EMPTY),
                        false),
                arguments(new BooleanValue(true, ValueMetadata.SECRET_EMPTY),
                        new BooleanValue(false, ValueMetadata.SECRET_EMPTY), false));
    }

    @ParameterizedTest(name = "{0} with secret={1} toString()={2}")
    @MethodSource
    void when_toStringCalled_then_showsValueOrPlaceholder(boolean value, boolean secret, String expected) {
        var metadata  = secret ? ValueMetadata.SECRET_EMPTY : ValueMetadata.EMPTY;
        var boolValue = new BooleanValue(value, metadata);

        assertThat(boolValue).hasToString(expected);
    }

    static Stream<Arguments> when_toStringCalled_then_showsValueOrPlaceholder() {
        return Stream.of(arguments(true, false, "true"), arguments(false, false, "false"),
                arguments(true, true, "***SECRET***"), arguments(false, true, "***SECRET***"));
    }

    @Test
    void when_patternMatchingUsed_then_extractsValueCorrectly() {
        Value granted = Value.of(true);
        Value denied  = Value.of(false);

        assertThat(granted).isInstanceOf(BooleanValue.class);
        assertThat(denied).isInstanceOf(BooleanValue.class);

        if (granted instanceof BooleanValue(boolean allowed, ValueMetadata ignored)) {
            assertThat(allowed).isTrue();
        }
        if (denied instanceof BooleanValue(boolean allowed, ValueMetadata ignored)) {
            assertThat(allowed).isFalse();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constantsChecked_then_haveExpectedSecretFlag(String description, Value constant, boolean expectedSecret) {
        assertThat(constant.isSecret()).isEqualTo(expectedSecret);
    }

    static Stream<Arguments> when_constantsChecked_then_haveExpectedSecretFlag() {
        return Stream.of(arguments("Value.TRUE is not secret", Value.TRUE, false),
                arguments("Value.FALSE is not secret", Value.FALSE, false),
                arguments("BooleanValue.SECRET_TRUE is secret", BooleanValue.SECRET_TRUE, true),
                arguments("BooleanValue.SECRET_FALSE is secret", BooleanValue.SECRET_FALSE, true));
    }
}
