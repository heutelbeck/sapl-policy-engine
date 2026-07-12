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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;

@DisplayName("Value to graph JSON mapper emits client-detectable markers")
class ValueToGraphJsonMapperTests {

    @Test
    @DisplayName("undefined values are emitted with the $undefined marker the graph renderer detects")
    void whenValueIsUndefinedThenJsonCarriesUndefinedMarker() {
        final var json = ValueToGraphJsonMapper.toJsonString(Value.UNDEFINED);

        assertThat(json).contains("\"$undefined\":true").doesNotContain("_type");
    }

    @Test
    @DisplayName("error values are emitted with the $error marker plus message and location string")
    void whenValueIsErrorThenJsonCarriesErrorMarkerWithLocation() {
        final var location = new SourceLocation("policy.sapl", null, 0, 4, 2, 3, 2, 7);
        final var json     = ValueToGraphJsonMapper.toJsonString(Value.error("boom", location));

        assertThat(json).contains("\"$error\":true").contains("\"message\":\"boom\"")
                .contains("\"location\":\"" + location + "\"").doesNotContain("_type");
    }

    @Test
    @DisplayName("error values without a location still emit the $error marker")
    void whenValueIsErrorWithoutLocationThenJsonCarriesErrorMarker() {
        final var json = ValueToGraphJsonMapper.toJsonString(Value.error("boom"));

        assertThat(json).contains("\"$error\":true").contains("\"message\":\"boom\"").doesNotContain("_type");
    }
}
