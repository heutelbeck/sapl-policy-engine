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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

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
        return toGraphJsonNode(value).toString();
    }

    private static JsonNode toGraphJsonNode(Value value) {
        return switch (value) {
        case UndefinedValue ignored -> undefinedMarker();
        case ErrorValue error       -> errorMarker(error);
        default                     -> ValueJsonMarshaller.toJsonNodeLenient(value);
        };
    }

    private static ObjectNode undefinedMarker() {
        val node = OBJECT_MAPPER.createObjectNode();
        node.put("$undefined", true);
        return node;
    }

    private static ObjectNode errorMarker(ErrorValue error) {
        val node = OBJECT_MAPPER.createObjectNode();
        node.put("$error", true);
        node.put("message", error.message());
        if (error.location() != null) {
            node.put("location", error.location().toString());
        }
        return node;
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
        try {
            val json = toGraphJsonNode(value);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception exception) {
            // toGraphJsonNode can reject a null or over-deep Value. Never let that
            // escape the editor's pretty-printer.
            return String.valueOf(value);
        }
    }

}
