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
package io.sapl.test.junit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImportType tests")
class ImportTypeTests {

    @ParameterizedTest(name = "{0} enum value exists and has correct name")
    @EnumSource(ImportType.class)
    void whenAccessingEnumValue_thenValueExistsWithCorrectName(ImportType importType) {
        assertThat(importType).isNotNull().satisfies(value -> assertThat(value.name()).isEqualTo(importType.name()));
    }

    @Test
    @DisplayName("all four import types are defined")
    void whenGettingAllValues_thenFourTypesExist() {
        assertThat(ImportType.values()).hasSize(4).containsExactly(ImportType.PIP, ImportType.STATIC_PIP,
                ImportType.FUNCTION_LIBRARY, ImportType.STATIC_FUNCTION_LIBRARY);
    }

    @ParameterizedTest(name = "valueOf(\"{0}\") returns correct constant")
    @EnumSource(ImportType.class)
    void whenUsingValueOf_thenReturnsCorrectConstant(ImportType expected) {
        assertThat(ImportType.valueOf(expected.name())).isEqualTo(expected);
    }
}
