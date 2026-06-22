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
package io.sapl.vaadin.lsp.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;

@DisplayName("JSON graph visualization maximize behavior")
class JsonGraphVisualizationTests {

    @Test
    @DisplayName("maximizing a value-mode graph keeps value mode on the maximized view")
    void whenValueModeGraphIsMaximizedThenMaximizedViewStaysInValueMode() throws Exception {
        final var graph = new JsonGraphVisualization();
        graph.setValueData(Value.UNDEFINED);

        final var maximized = createMaximizedVisualization(graph);

        assertThat(maximized.isValueMode())
                .as("maximized visualization must inherit value mode so special values render correctly").isTrue();
    }

    @Test
    @DisplayName("maximizing a plain-JSON graph leaves the maximized view in plain mode")
    void whenPlainJsonGraphIsMaximizedThenMaximizedViewStaysInPlainMode() throws Exception {
        final var graph = new JsonGraphVisualization();
        graph.setJsonData("{\"name\":\"example\"}");

        final var maximized = createMaximizedVisualization(graph);

        assertThat(maximized.isValueMode()).as("maximized visualization of plain JSON data must not be in value mode")
                .isFalse();
    }

    private static JsonGraphVisualization createMaximizedVisualization(JsonGraphVisualization graph) throws Exception {
        final Method factory = JsonGraphVisualization.class.getDeclaredMethod("createMaximizedVisualization");
        factory.setAccessible(true);
        return (JsonGraphVisualization) factory.invoke(graph);
    }
}
