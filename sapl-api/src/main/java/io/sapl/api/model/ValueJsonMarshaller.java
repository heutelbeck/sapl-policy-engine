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
package io.sapl.api.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.SaplVersion;
import lombok.experimental.UtilityClass;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;

import static java.util.stream.Collectors.joining;

import lombok.val;

/**
 * Marshalling between SAPL Value types and JSON.
 * <p>
 * UndefinedValue and ErrorValue cannot be marshalled. Secret flags are not
 * preserved. Nesting depth limited to 500
 * levels.
 */
@UtilityClass
public class ValueJsonMarshaller {

    private static final JsonNodeFactory FACTORY       = JsonNodeFactory.instance;
    private static final JsonMapper      OBJECT_MAPPER = JsonMapper.builder().build();
    static final int                     MAX_DEPTH     = 500;

    private static final String ERROR_CANNOT_MARSHALL_ERROR_VALUE     = "Cannot marshall ErrorValue to JSON: %s.";
    private static final String ERROR_CANNOT_MARSHALL_NULL            = "Cannot marshall null to JsonNode.";
    private static final String ERROR_CANNOT_MARSHALL_UNDEFINED_VALUE = "Cannot marshall UndefinedValue to JSON.";
    private static final String ERROR_FAILED_TO_PARSE_JSON            = "Failed to parse JSON: %s";
    private static final String ERROR_MAXIMUM_NESTING_DEPTH_EXCEEDED  = "Maximum nesting depth exceeded.";
    private static final String ERROR_UNKNOWN_JSON_NODE_TYPE          = "Unknown JsonNode type: %s.";

    private static final String TYPE_FIELD     = "_type";
    private static final String TYPE_UNDEFINED = "undefined";
    private static final String TYPE_ERROR     = "error";

    /**
     * Checks whether a Value can be marshalled to JSON without throwing.
     *
     * @param value
     * the value to check
     *
     * @return true if the value can be converted to JSON, false otherwise
     */
    public static boolean isJsonCompatible(Value value) {
        return isJsonCompatible(value, 0);
    }

    private static boolean isJsonCompatible(Value value, int depth) {
        if (value == null || depth >= MAX_DEPTH) {
            return false;
        }
        return switch (value) {
        case ArrayValue array   -> array.stream().allMatch(item -> isJsonCompatible(item, depth + 1));
        case ObjectValue object -> object.values().stream().allMatch(v -> isJsonCompatible(v, depth + 1));
        case UndefinedValue u   -> false;
        case ErrorValue e       -> false;
        default                 -> true;
        };
    }

    /**
     * Converts a Value to a Jackson JsonNode.
     *
     * @param value the value to convert
     * @return JsonNode representation
     * @throws IllegalArgumentException if value is null, UndefinedValue,
     * ErrorValue, or depth exceeds limit
     */
    public static JsonNode toJsonNode(Value value) {
        if (value == null) {
            throw new IllegalArgumentException(ERROR_CANNOT_MARSHALL_NULL);
        }
        return toJsonNode(value, 0, false);
    }

    /**
     * Converts a Value to a Jackson JsonNode, including UndefinedValue and
     * ErrorValue as JSON objects.
     * <p>
     * UndefinedValue is serialized as: {@code { "_type": "undefined" }}
     * <p>
     * ErrorValue is serialized as:
     * {@code { "_type": "error", "message": "...", ... }} with location fields
     * if available. The cause and document source are excluded.
     *
     * @param value the value to convert
     * @return JsonNode representation
     * @throws IllegalArgumentException if value is null or depth exceeds limit
     */
    public static JsonNode toJsonNodeLenient(Value value) {
        if (value == null) {
            throw new IllegalArgumentException(ERROR_CANNOT_MARSHALL_NULL);
        }
        return toJsonNode(value, 0, true);
    }

    /**
     * Converts a Value to a JSON string.
     *
     * @param value the value to convert
     * @return JSON string representation
     * @throws IllegalArgumentException if value is null, UndefinedValue,
     * ErrorValue, or depth exceeds limit
     */
    public static String toJsonString(Value value) {
        return toJsonNode(value).toString();
    }

    /**
     * Converts a Value to a JSON string, including UndefinedValue and
     * ErrorValue as JSON objects.
     *
     * @param value the value to convert
     * @return JSON string representation
     * @throws IllegalArgumentException if value is null or depth exceeds limit
     * @see #toJsonNodeLenient(Value)
     */
    public static String toJsonStringLenient(Value value) {
        return toJsonNodeLenient(value).toString();
    }

    /**
     * Converts a Value to a pretty-printed string with meaningful indentation.
     * Supports all Value types including
     * UndefinedValue and ErrorValue.
     *
     * @param value
     * the value to convert
     *
     * @return pretty-printed string representation
     */
    public static String toPrettyString(Value value) {
        return toPrettyString(value, 0);
    }

    /**
     * Converts a Value to a pretty-printed string with specified initial
     * indentation.
     *
     * @param value
     * the value to convert
     * @param indent
     * the initial indentation level
     *
     * @return pretty-printed string representation
     */
    public static String toPrettyString(Value value, int indent) {
        if (value == null) {
            return "null";
        }
        return switch (value) {
        case NullValue n      -> "null";
        case BooleanValue b   -> String.valueOf(b.value());
        case NumberValue n    -> n.value().toPlainString();
        case TextValue t      -> quoteString(t.value());
        case UndefinedValue u -> TYPE_UNDEFINED;
        case ErrorValue e     -> formatError(e);
        case ArrayValue a     -> formatArray(a, indent);
        case ObjectValue o    -> formatObject(o, indent);
        };
    }

    private static String quoteString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String formatError(ErrorValue error) {
        val result = new StringBuilder("ERROR[");
        result.append("message=").append(quoteString(error.message()));
        if (error.location() != null) {
            result.append(", at=").append(error.location());
        }
        result.append(']');
        return result.toString();
    }

    private static String formatArray(ArrayValue array, int indent) {
        if (array.isEmpty()) {
            return "[]";
        }
        if (isSimpleArray(array)) {
            return "[" + array.stream().map(v -> toPrettyString(v, 0)).collect(joining(", ")) + "]";
        }
        val result      = new StringBuilder("[\n");
        val childIndent = indent + 1;
        val padding     = "  ".repeat(childIndent);
        var first       = true;
        for (val item : array) {
            if (!first) {
                result.append(",\n");
            }
            result.append(padding).append(toPrettyString(item, childIndent));
            first = false;
        }
        result.append('\n').append("  ".repeat(indent)).append(']');
        return result.toString();
    }

    private static String formatObject(ObjectValue object, int indent) {
        if (object.isEmpty()) {
            return "{}";
        }
        val result      = new StringBuilder("{\n");
        val childIndent = indent + 1;
        val padding     = "  ".repeat(childIndent);
        var first       = true;
        for (val entry : object.entrySet()) {
            if (!first) {
                result.append(",\n");
            }
            result.append(padding).append(quoteString(entry.getKey())).append(": ")
                    .append(toPrettyString(entry.getValue(), childIndent));
            first = false;
        }
        result.append('\n').append("  ".repeat(indent)).append('}');
        return result.toString();
    }

    private static boolean isSimpleArray(ArrayValue array) {
        return array.size() <= 3 && array.stream().allMatch(ValueJsonMarshaller::isSimpleValue);
    }

    private static boolean isSimpleValue(Value value) {
        return value instanceof NullValue || value instanceof BooleanValue || value instanceof NumberValue
                || value instanceof TextValue || (value instanceof ArrayValue a && a.isEmpty())
                || (value instanceof ObjectValue o && o.isEmpty());
    }

    /**
     * Converts a Jackson JsonNode to a Value.
     * <p>
     * NullNode and null map to Value.NULL. BINARY, POJO, and MISSING nodes return
     * ErrorValue.
     *
     * @param node
     * the JsonNode to convert
     *
     * @return Value representation, or ErrorValue for unsupported types or
     * excessive depth
     */
    public static Value fromJsonNode(JsonNode node) {
        try {
            return fromJsonNode(node, 0);
        } catch (DepthLimitExceededException e) {
            return Value.error(e.getMessage());
        }
    }

    /**
     * Parses a JSON string and converts it to a Value.
     * <p>
     * Returns ErrorValue if parsing fails.
     *
     * @param json
     * the JSON string to parse
     *
     * @return the parsed Value, or ErrorValue if parsing fails
     */
    public static Value json(String json) {
        try {
            return fromJsonNode(OBJECT_MAPPER.readTree(json));
        } catch (JacksonException e) {
            return Value.error(ERROR_FAILED_TO_PARSE_JSON.formatted(e.getMessage()));
        }
    }

    private static void checkDepthForMarshalling(int depth) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException(ERROR_MAXIMUM_NESTING_DEPTH_EXCEEDED);
        }
    }

    private static void checkDepthForUnmarshalling(int depth) {
        if (depth >= MAX_DEPTH) {
            throw new DepthLimitExceededException();
        }
    }

    private static JsonNode toJsonNode(Value value, int depth, boolean lenient) {
        checkDepthForMarshalling(depth);
        return switch (value) {
        case NullValue ignored           -> FACTORY.nullNode();
        case BooleanValue(boolean b)     -> FACTORY.booleanNode(b);
        case NumberValue(BigDecimal num) -> FACTORY.numberNode(num);
        case TextValue(String text)      -> FACTORY.stringNode(text);
        case ArrayValue array            -> toJsonArray(array, depth + 1, lenient);
        case ObjectValue object          -> toJsonObject(object, depth + 1, lenient);
        case UndefinedValue ignored      -> {
            if (lenient) {
                yield toUndefinedJsonNode();
            }
            throw new IllegalArgumentException(ERROR_CANNOT_MARSHALL_UNDEFINED_VALUE);
        }
        case ErrorValue e                -> {
            if (lenient) {
                yield toErrorJsonNode(e);
            }
            throw new IllegalArgumentException(ERROR_CANNOT_MARSHALL_ERROR_VALUE.formatted(e.message()));
        }
        };
    }

    private static JsonNode toUndefinedJsonNode() {
        val node = FACTORY.objectNode();
        node.put(TYPE_FIELD, TYPE_UNDEFINED);
        return node;
    }

    private static JsonNode toErrorJsonNode(ErrorValue error) {
        val node = FACTORY.objectNode();
        node.put(TYPE_FIELD, TYPE_ERROR);
        node.put("message", error.message());
        if (error.location() != null) {
            val loc = error.location();
            if (loc.documentName() != null) {
                node.put("documentName", loc.documentName());
            }
            node.put("line", loc.line());
            node.put("column", loc.column());
            node.put("endLine", loc.endLine());
            node.put("endColumn", loc.endColumn());
        }
        return node;
    }

    private static Value fromJsonNode(JsonNode node, int depth) {
        checkDepthForUnmarshalling(depth);
        if (node == null || node.isNull()) {
            return Value.NULL;
        }
        return switch (node.getNodeType()) {
        case BOOLEAN -> Value.of(node.asBoolean());
        case NUMBER  -> Value.of(node.decimalValue());
        case STRING  -> Value.of(node.asString());
        case ARRAY   -> fromJsonArray(node, depth + 1);
        case OBJECT  -> fromJsonObject(node, depth + 1);
        default      -> Value.error(ERROR_UNKNOWN_JSON_NODE_TYPE.formatted(node.getNodeType()));
        };
    }

    private static JsonNode toJsonArray(ArrayValue array, int depth, boolean lenient) {
        checkDepthForMarshalling(depth);
        val arrayNode = FACTORY.arrayNode();
        for (val item : array) {
            arrayNode.add(toJsonNode(item, depth, lenient));
        }
        return arrayNode;
    }

    private static JsonNode toJsonObject(ObjectValue object, int depth, boolean lenient) {
        checkDepthForMarshalling(depth);
        val objectNode = FACTORY.objectNode();
        for (val entry : object.entrySet()) {
            objectNode.set(entry.getKey(), toJsonNode(entry.getValue(), depth, lenient));
        }
        return objectNode;
    }

    private static Value fromJsonArray(JsonNode arrayNode, int depth) {
        checkDepthForUnmarshalling(depth);
        val items = new ArrayList<Value>();
        arrayNode.forEach(item -> items.add(fromJsonNode(item, depth)));
        return Value.ofArray(items);
    }

    private static Value fromJsonObject(JsonNode objectNode, int depth) {
        checkDepthForUnmarshalling(depth);
        val builder = ObjectValue.builder();
        objectNode.properties().forEach(entry -> builder.put(entry.getKey(), fromJsonNode(entry.getValue(), depth)));
        return builder.build();
    }

    /**
     * Internal exception for depth limit enforcement. Caught at top level and
     * converted to ErrorValue.
     */
    private static class DepthLimitExceededException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        DepthLimitExceededException() {
            super(ERROR_MAXIMUM_NESTING_DEPTH_EXCEEDED);
        }
    }
}
