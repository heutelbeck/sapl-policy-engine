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
package io.sapl.attributes;

import io.sapl.validation.NameValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameValidatorTests {

    @ParameterizedTest
    @ValueSource(strings = { "", " ", " abc.def", "abc.def ", " abc.def ", "abc. def", "abc", "abc.123as",
            "a.b.c.d.e.f.g.h.i.j.k" })
    void whenPresentedWithInvalidNamesThenAssertionThrowsIllegalArgumentException(String invalidName) {
        assertThatThrownBy(() -> NameValidator.requireValidName(invalidName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "a.b", "a1.b2", "a2.d.x333", "a.b.c.d.e.f.g.h.i.j" })
    void whenPresentedWithValidNamesThenAssertionDoesNotThrow(String validName) {
        assertThatCode(() -> NameValidator.requireValidName(validName)).doesNotThrowAnyException();
    }

}
