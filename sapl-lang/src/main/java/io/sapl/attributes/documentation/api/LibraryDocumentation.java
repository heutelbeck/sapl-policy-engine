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
package io.sapl.attributes.documentation.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;

public record LibraryDocumentation(
        @NonNull LibraryType type,
        @NonNull String namespace,
        @NonNull String descriptionMarkdown,
        @NonNull String documentationMarkdown,
        @NonNull List<LibraryFunctionDocumentation> attributes) {

    public Map<String, String> attributesMap() {
        final var map = new HashMap<String, String>();
        for (var attribute : attributes) {
            map.put(attribute.attributeName(), attribute.documentationMarkdown());
        }
        return map;
    }
}
