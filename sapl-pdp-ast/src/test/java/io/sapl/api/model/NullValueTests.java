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

@DisplayName("NullValue Tests")
class NullValueTests {

    @Test
    @DisplayName("Constructor creates NullValue")
    void when_constructed_then_createsValue() {
        var value = new NullValue();

        assertThat(value).isNotNull();
    }

    @Test
    @DisplayName("All NullValues are equal")
    void when_equalsAndHashCodeCompared_then_allNullValuesAreEqual() {
        var null1 = new NullValue();
        var null2 = new NullValue();

        assertThat(null1).isEqualTo(null2).hasSameHashCodeAs(null2);
        assertThat(null1).isEqualTo(Value.NULL);
    }

    @Test
    @DisplayName("NullValue is not equal to other value types")
    void when_comparedToOtherValueTypes_then_notEqual() {
        var nullValue = new NullValue();

        assertThat(nullValue).isNotEqualTo(Value.UNDEFINED).isNotEqualTo(Value.of(0)).isNotEqualTo(Value.of("null"));
    }

    @Test
    @DisplayName("toString() returns 'null'")
    void when_toStringCalled_then_showsNull() {
        var value = new NullValue();

        assertThat(value).hasToString("null");
    }

    @Test
    @DisplayName("Value.NULL constant is NullValue")
    void when_nullConstantChecked_then_isNullValue() {
        assertThat(Value.NULL).isInstanceOf(NullValue.class);
    }

    @Test
    @DisplayName("hashCode is consistent")
    void when_hashCodeCalled_then_consistent() {
        var null1 = new NullValue();
        var null2 = new NullValue();

        assertThat(null1.hashCode()).isEqualTo(null2.hashCode());
    }
}
