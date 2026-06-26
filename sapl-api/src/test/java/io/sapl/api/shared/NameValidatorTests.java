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
package io.sapl.api.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameValidatorTests {

    @ParameterizedTest(name = "valid name \"{0}\" is accepted")
    @ValueSource(strings = { "pip.attribute", "a.b.c", "foo.bar.baz.qux" })
    @DisplayName("when name is syntactically valid then no exception is thrown")
    void whenNameIsValidThenNoExceptionIsThrown(String name) {
        assertThatCode(() -> NameValidator.requireValidName(name)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "invalid name \"{0}\" is rejected")
    @ValueSource(strings = { "noseparator", "1foo.bar", "foo .bar", "" })
    @DisplayName("when name is syntactically invalid then IllegalArgumentException is thrown")
    void whenNameIsInvalidThenIllegalArgumentExceptionIsThrown(String name) {
        assertThatThrownBy(() -> NameValidator.requireValidName(name)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(name);
    }

    @Test
    @DisplayName("when name is null then documented IllegalArgumentException is thrown not NullPointerException")
    void whenNameIsNullThenIllegalArgumentExceptionIsThrown() {
        assertThatThrownBy(() -> NameValidator.requireValidName(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
