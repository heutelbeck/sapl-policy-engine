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
package io.sapl.api.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.SaplVersion;
import lombok.experimental.UtilityClass;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * Marshalling between SAPL Value types and Jackson JsonNode.
 * <p>
 * Provides bidirectional conversion for integration with JSON-based libraries.
 * UndefinedValue and ErrorValue cannot be marshalled. Secret flags are not
 * preserved. Recursion depth is limited to prevent stack overflow from
 * malicious input.
 */
@UtilityClass
public class ValueJsonMarshaller {

    private static final JsonNodeFactory FACTORY   = JsonNodeFactory.instance;
    static final int                     MAX_DEPTH = 500;

    /**
     * Checks whether a Value can be marshalled to JSON.
     * <p>
     * A Value is JSON-compatible if it is not null, not UndefinedValue, not
     * ErrorValue, and does not exceed the maximum nesting depth. This method
     * performs a non-throwing validation check without actually creating the
     * JsonNode.
     * <p>
     * Use this method in tests and validation logic where you need to verify that a
     * computed Value can be serialized to JSON, without extracting and comparing
     * the JSON structure.
     *
     * <pre>{@code
     * // In access control policy evaluation, verify the decision can be serialized:
     * Value decision = policyEngine.evaluate(authorizationRequest);
     * if (ValueJsonMarshaller.isJsonCompatible(decision)) {
     *     // Safe to send as JSON response to the client
     *     return ValueJsonMarshaller.toJsonNode(decision);
     * }
     *
     * // In tests, assert JSON compatibility without extracting primitives:
     * assertThat(ValueJsonMarshaller.isJsonCompatible(result)).isTrue();
     * }</pre>
     *
     * @param value the value to check
     * @return true if the value can be converted to JsonNode, false otherwise
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
     * <p>
     * Secret flags are ignored during marshalling.
     *
     * @param value
     * the value to convert
     *
     * @return JsonNode representation
     *
     * @throws IllegalArgumentException
     * if value is null, UndefinedValue, ErrorValue, or depth exceeds limit
     */
    public static JsonNode toJsonNode(Value value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot marshall null to JsonNode.");
        }
        return toJsonNode(value, 0);
    }

    /**
     * Converts a Jackson JsonNode to a Value.
     * <p>
     * NullNode and null map to Value.NULL. Values are created without secret flag.
     * BINARY, POJO, and MISSING nodes
     * return ErrorValue. Nesting exceeding 500 levels returns ErrorValue.
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

    private static JsonNode toJsonNode(Value value, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException("Maximum nesting depth exceeded.");
        }

        return switch (value) {
        case NullValue n                            -> FACTORY.nullNode();
        case BooleanValue(boolean b, boolean s)     -> FACTORY.booleanNode(b);
        case NumberValue(BigDecimal num, boolean s) -> FACTORY.numberNode(num);
        case TextValue(String text, boolean s)      -> FACTORY.textNode(text);
        case ArrayValue array                       -> toJsonArray(array, depth + 1);
        case ObjectValue object                     -> toJsonObject(object, depth + 1);
        case UndefinedValue u                       ->
            throw new IllegalArgumentException("Cannot marshall UndefinedValue to JSON.");
        case ErrorValue e                           ->
            throw new IllegalArgumentException("Cannot marshall ErrorValue to JSON: " + e.message() + ".");
        };
    }

    private static Value fromJsonNode(JsonNode node, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new DepthLimitExceededException();
        }

        if (node == null || node.isNull()) {
            return Value.NULL;
        }

        return switch (node.getNodeType()) {
        case BOOLEAN -> Value.of(node.asBoolean());
        case NUMBER  -> Value.of(node.decimalValue());
        case STRING  -> Value.of(node.asText());
        case ARRAY   -> fromJsonArray(node, depth + 1);
        case OBJECT  -> fromJsonObject(node, depth + 1);
        default      -> Value.error("Unknown JsonNode type: " + node.getNodeType() + ".");
        };
    }

    private static JsonNode toJsonArray(ArrayValue array, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException("Maximum nesting depth exceeded.");
        }
        var arrayNode = FACTORY.arrayNode();
        for (Value item : array) {
            arrayNode.add(toJsonNode(item, depth));
        }
        return arrayNode;
    }

    private static JsonNode toJsonObject(ObjectValue object, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException("Maximum nesting depth exceeded.");
        }
        var objectNode = FACTORY.objectNode();
        for (var entry : object.entrySet()) {
            objectNode.set(entry.getKey(), toJsonNode(entry.getValue(), depth));
        }
        return objectNode;
    }

    private static Value fromJsonArray(JsonNode arrayNode, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new DepthLimitExceededException();
        }
        var items = new ArrayList<Value>();
        arrayNode.forEach(item -> items.add(fromJsonNode(item, depth)));
        return Value.ofArray(items);
    }

    private static Value fromJsonObject(JsonNode objectNode, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new DepthLimitExceededException();
        }
        var builder = ObjectValue.builder();
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
            super("Maximum nesting depth exceeded.");
        }
    }
}
