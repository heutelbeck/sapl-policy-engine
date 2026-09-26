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
package io.sapl.pdp.plugins;

import io.sapl.api.pdp.DecisionInterceptor;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LoadedPlugin holds immutable copies of the function libraries, PIPs and decision interceptors of a plugin")
class LoadedPluginTests {

    private static final String PLUGIN_ID        = "test-plugin";
    private static final Object FUNCTION_LIBRARY = new Object();

    @Test
    @DisplayName("the lists cannot be modified")
    void whenModifyingListsThenUnsupportedOperation() {
        val plugin = new LoadedPlugin(PLUGIN_ID, List.of(FUNCTION_LIBRARY), List.of(), List.of());

        assertThatThrownBy(() -> plugin.functionLibraries().add(new Object()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("later changes to the source lists do not leak into the record")
    void whenSourceListChangesAfterConstructionThenRecordIsUnaffected() {
        val libraries = new ArrayList<Object>(List.of(FUNCTION_LIBRARY));
        val plugin    = new LoadedPlugin(PLUGIN_ID, libraries, new ArrayList<>(), new ArrayList<DecisionInterceptor>());

        libraries.add(new Object());

        assertThat(plugin.functionLibraries()).containsExactly(FUNCTION_LIBRARY);
    }

    @Test
    @DisplayName("a null list element is rejected")
    void whenListContainsNullThenRejected() {
        val libraries = Arrays.asList(FUNCTION_LIBRARY, null);

        assertThatThrownBy(() -> new LoadedPlugin(PLUGIN_ID, libraries, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
