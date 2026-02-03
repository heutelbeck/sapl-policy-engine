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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImportType tests")
class ImportTypeTests {

    @Test
    @DisplayName("PIP enum value exists")
    void whenAccessingPip_thenValueExists() {
        assertThat(ImportType.PIP).isNotNull();
        assertThat(ImportType.PIP.name()).isEqualTo("PIP");
    }

    @Test
    @DisplayName("STATIC_PIP enum value exists")
    void whenAccessingStaticPip_thenValueExists() {
        assertThat(ImportType.STATIC_PIP).isNotNull();
        assertThat(ImportType.STATIC_PIP.name()).isEqualTo("STATIC_PIP");
    }

    @Test
    @DisplayName("FUNCTION_LIBRARY enum value exists")
    void whenAccessingFunctionLibrary_thenValueExists() {
        assertThat(ImportType.FUNCTION_LIBRARY).isNotNull();
        assertThat(ImportType.FUNCTION_LIBRARY.name()).isEqualTo("FUNCTION_LIBRARY");
    }

    @Test
    @DisplayName("STATIC_FUNCTION_LIBRARY enum value exists")
    void whenAccessingStaticFunctionLibrary_thenValueExists() {
        assertThat(ImportType.STATIC_FUNCTION_LIBRARY).isNotNull();
        assertThat(ImportType.STATIC_FUNCTION_LIBRARY.name()).isEqualTo("STATIC_FUNCTION_LIBRARY");
    }

    @Test
    @DisplayName("all four import types are defined")
    void whenGettingAllValues_thenFourTypesExist() {
        assertThat(ImportType.values()).hasSize(4).containsExactly(ImportType.PIP, ImportType.STATIC_PIP,
                ImportType.FUNCTION_LIBRARY, ImportType.STATIC_FUNCTION_LIBRARY);
    }

    @Test
    @DisplayName("valueOf returns correct enum constant")
    void whenUsingValueOf_thenReturnsCorrectConstant() {
        assertThat(ImportType.valueOf("PIP")).isEqualTo(ImportType.PIP);
        assertThat(ImportType.valueOf("STATIC_PIP")).isEqualTo(ImportType.STATIC_PIP);
        assertThat(ImportType.valueOf("FUNCTION_LIBRARY")).isEqualTo(ImportType.FUNCTION_LIBRARY);
        assertThat(ImportType.valueOf("STATIC_FUNCTION_LIBRARY")).isEqualTo(ImportType.STATIC_FUNCTION_LIBRARY);
    }
}
