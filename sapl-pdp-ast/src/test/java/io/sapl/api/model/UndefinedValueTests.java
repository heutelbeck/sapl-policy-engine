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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UndefinedValue Tests")
class UndefinedValueTests {

    @Test
    @DisplayName("Constructor creates UndefinedValue")
    void when_constructed_then_createsValue() {
        var value = new UndefinedValue();

        assertThat(value).isNotNull();
    }

    @Test
    @DisplayName("All UndefinedValues are equal")
    void when_equalsAndHashCodeCompared_then_allUndefinedValuesAreEqual() {
        var undefined1 = new UndefinedValue();
        var undefined2 = new UndefinedValue();

        assertThat(undefined1).isEqualTo(undefined2).hasSameHashCodeAs(undefined2).isEqualTo(Value.UNDEFINED);
    }

    @Test
    @DisplayName("UndefinedValue is not equal to other value types")
    void when_comparedToOtherValueTypes_then_notEqual() {
        var undefinedValue = new UndefinedValue();

        assertThat(undefinedValue).isNotEqualTo(Value.NULL).isNotEqualTo(Value.of(0))
                .isNotEqualTo(Value.of("undefined"));
    }

    @Test
    @DisplayName("toString() returns 'undefined'")
    void when_toStringCalled_then_showsUndefined() {
        var value = new UndefinedValue();

        assertThat(value).hasToString("undefined");
    }

    @Test
    @DisplayName("Value.UNDEFINED constant is UndefinedValue")
    void when_undefinedConstantChecked_then_isUndefinedValue() {
        assertThat(Value.UNDEFINED).isInstanceOf(UndefinedValue.class);
    }

    @Test
    @DisplayName("hashCode is consistent")
    void when_hashCodeCalled_then_consistent() {
        var undefined1 = new UndefinedValue();
        var undefined2 = new UndefinedValue();

        assertThat(undefined1.hashCode()).isEqualTo(undefined2.hashCode());
    }
}
