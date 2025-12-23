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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

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

    private static final JsonNodeFactory FACTORY       = JsonNodeFactory.instance;
    private static final ObjectMapper    OBJECT_MAPPER = new ObjectMapper();
    private static final int             MAX_DEPTH     = 500;

    private static final String MARKER_UNDEFINED = "$undefined";
    private static final String MARKER_ERROR     = "$error";
    private static final String FIELD_MESSAGE    = "message";
    private static final String FIELD_LOCATION   = "location";

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
        return toJsonNode(value).toString();
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
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toJsonNode(value));
        } catch (Exception exception) {
            return toJsonNode(value).toString();
        }
    }

    /**
     * Converts a Value to a Jackson JsonNode suitable for graph visualization. All
     * Value types are supported, including
     * UndefinedValue and ErrorValue.
     *
     * @param value
     * the value to convert
     *
     * @return JsonNode representation
     */
    public static JsonNode toJsonNode(Value value) {
        if (value == null) {
            return FACTORY.nullNode();
        }
        return toJsonNode(value, 0);
    }

    private static JsonNode toJsonNode(Value value, int depth) {
        if (depth >= MAX_DEPTH) {
            return createErrorNode("Maximum nesting depth exceeded", null);
        }
        return switch (value) {
        case NullValue ignored                                          -> FACTORY.nullNode();
        case BooleanValue(boolean booleanValue, ValueMetadata ignored)  -> FACTORY.booleanNode(booleanValue);
        case NumberValue(BigDecimal numberValue, ValueMetadata ignored) -> FACTORY.numberNode(numberValue);
        case TextValue(String textValue, ValueMetadata ignored)         -> FACTORY.textNode(textValue);
        case ArrayValue array                                           -> toJsonArray(array, depth + 1);
        case ObjectValue object                                         -> toJsonObject(object, depth + 1);
        case UndefinedValue ignored                                     -> createUndefinedNode();
        case ErrorValue error                                           ->
            createErrorNode(error.message(), error.location());
        };
    }

    private static JsonNode toJsonArray(ArrayValue array, int depth) {
        var arrayNode = FACTORY.arrayNode();
        for (var item : array) {
            arrayNode.add(toJsonNode(item, depth));
        }
        return arrayNode;
    }

    private static JsonNode toJsonObject(ObjectValue object, int depth) {
        var objectNode = FACTORY.objectNode();
        for (var entry : object.entrySet()) {
            objectNode.set(entry.getKey(), toJsonNode(entry.getValue(), depth));
        }
        return objectNode;
    }

    private static JsonNode createUndefinedNode() {
        var node = FACTORY.objectNode();
        node.put(MARKER_UNDEFINED, true);
        return node;
    }

    private static JsonNode createErrorNode(String message, SourceLocation location) {
        var node = FACTORY.objectNode();
        node.put(MARKER_ERROR, true);
        node.put(FIELD_MESSAGE, message != null ? message : "Unknown error");
        if (location != null) {
            var locationStr = "%s (line %d, offset %d-%d)".formatted(
                    location.documentName() != null ? location.documentName() : "unknown", location.line(),
                    location.start(), location.end());
            node.put(FIELD_LOCATION, locationStr);
        }
        return node;
    }
}
