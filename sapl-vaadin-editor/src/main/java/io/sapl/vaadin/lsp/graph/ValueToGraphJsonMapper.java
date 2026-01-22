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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Maps SAPL Value types to JSON for graph visualization. Unlike
 * {@link io.sapl.api.model.ValueJsonMarshaller}, this
 * mapper handles all Value types including UndefinedValue and ErrorValue by
 * converting them to special JSON marker
 * objects that the graph visualization can interpret.
 * <p>
 * Special markers used:
 * <ul>
 * <li>{@code {"$undefined": true}} - represents UndefinedValue</li>
 * <li>{@code {"$error": true, "message": "...", "location": "..."}} -
 * represents ErrorValue</li>
 * </ul>
 * <p>
 * The graph visualization component must be in "value mode" to properly render
 * these special markers with appropriate
 * styling.
 */
@UtilityClass
public class ValueToGraphJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converts a Value to a JSON string suitable for graph visualization. All Value
     * types are supported, including
     * UndefinedValue and ErrorValue.
     *
     * @param value
     * the value to convert
     *
     * @return JSON string representation
     */
    public static String toJsonString(Value value) {
        return ValueJsonMarshaller.toJsonNode(value).toString();
    }

    /**
     * Converts a Value to a pretty-printed JSON string suitable for graph
     * visualization. All Value types are supported,
     * including UndefinedValue and ErrorValue.
     *
     * @param value
     * the value to convert
     *
     * @return pretty-printed JSON string representation
     */
    public static String toPrettyJsonString(Value value) {
        val json = ValueJsonMarshaller.toJsonNode(value);
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception exception) {
            return json.toString();
        }
    }

}
