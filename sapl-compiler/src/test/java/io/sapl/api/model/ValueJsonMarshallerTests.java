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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive test suite for ValueJsonMarshaller. Tests cover round-trip
 * conversion, edge cases, error handling, and
 * depth protection.
 */
class ValueJsonMarshallerTests {

    private static final JsonNodeFactory FACTORY         = JsonNodeFactory.instance;
    private static final int             MALICIOUS_DEPTH = ValueJsonMarshaller.MAX_DEPTH + 2;

    // Lovecraftian test data
    private static final String ENTITY_CTHULHU      = "Cthulhu";
    private static final String ENTITY_AZATHOTH     = "Azathoth";
    private static final String ENTITY_NYARLATHOTEP = "Nyarlathotep";
    private static final String LOCATION_RLYEH      = "R'lyeh";
    private static final int    SANITY_THRESHOLD    = 42;
    private static final int    HORROR_LEVEL_MAX    = 100;

    // ============================================================
    // Round-trip Conversion Tests - Primitives
    // ============================================================

    @ParameterizedTest(name = "round-trip null: {0}")
    @MethodSource("nullValues")
    void roundTripNull(String description, Value value) {
        var node   = ValueJsonMarshaller.toJsonNode(value);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(Value.NULL).isInstanceOf(NullValue.class);
    }

    static Stream<Arguments> nullValues() {
        return Stream.of(arguments("singleton", Value.NULL), arguments("non-secret", new NullValue(false)),
                arguments("secret", new NullValue(true)));
    }

    @ParameterizedTest(name = "round-trip boolean: {0}")
    @ValueSource(booleans = { true, false })
    void roundTripBoolean(boolean value) {
        var original = Value.of(value);
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(BooleanValue.class);
        assertThat(node.isBoolean()).isTrue();
        assertThat(node.asBoolean()).isEqualTo(value);
    }

    @ParameterizedTest(name = "round-trip number: {0}")
    @MethodSource("numberValues")
    void roundTripNumber(BigDecimal value) {
        var original = Value.of(value);
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(NumberValue.class);
        assertThat(node.isNumber()).isTrue();
        assertThat(node.decimalValue()).isEqualByComparingTo(value);
    }

    static Stream<Arguments> numberValues() {
        return Stream.of(arguments(BigDecimal.ZERO), arguments(BigDecimal.ONE), arguments(BigDecimal.TEN),
                arguments(new BigDecimal("-1")), arguments(new BigDecimal("3.14159")),
                arguments(new BigDecimal("42.42")),
                arguments(new BigDecimal("999999999999999999999999999999.999999999999")),
                arguments(new BigDecimal("-0.00000000000000000001")), arguments(new BigDecimal(SANITY_THRESHOLD)),
                arguments(new BigDecimal(HORROR_LEVEL_MAX)));
    }

    @ParameterizedTest(name = "round-trip text: {0}")
    @MethodSource("textValues")
    void roundTripText(String description, String value) {
        var original = Value.of(value);
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(TextValue.class);
        assertThat(node.isTextual()).isTrue();
        assertThat(node.asText()).isEqualTo(value);
    }

    static Stream<Arguments> textValues() {
        return Stream.of(arguments("empty", ""), arguments("space", " "), arguments("simple", "simple"),
                arguments("entity", ENTITY_CTHULHU),
                arguments("incantation", "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn"),
                arguments("unicode", "日本語 中文 한국어 العربية"), arguments("special", "\n\t\r\"'\\"),
                arguments("emoji", "🐙🦑👁️"), arguments("long", "a".repeat(10000)));
    }

    @ParameterizedTest(name = "round-trip double: {0}")
    @MethodSource("doubleValues")
    void roundTripDoubles(String description, double value) {
        var original = Value.of(value);
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    static Stream<Arguments> doubleValues() {
        return Stream.of(arguments("zero", 0.0), arguments("negative zero", -0.0), arguments("min", Double.MIN_VALUE),
                arguments("max", Double.MAX_VALUE), arguments("e", Math.E), arguments("pi", Math.PI));
    }

    @Test
    void secretFlagNotPreservedOnPrimitives() {
        var original = Value.of(true).asSecret();
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(Value.TRUE).isNotSameAs(original);
        assertThat(result.secret()).isFalse();
    }

    // ============================================================
    // Round-trip Conversion Tests - Collections
    // ============================================================

    @ParameterizedTest(name = "round-trip empty: {0}")
    @MethodSource("emptyCollections")
    void roundTripEmpty(String description, Value original, Class<?> expectedType) {
        var node   = ValueJsonMarshaller.toJsonNode(original);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(expectedType);
        assertThat(node).isEmpty();
    }

    static Stream<Arguments> emptyCollections() {
        return Stream.of(arguments("array", Value.EMPTY_ARRAY, ArrayValue.class),
                arguments("object", Value.EMPTY_OBJECT, ObjectValue.class));
    }

    @Test
    void roundTripSimpleArray() {
        var original = Value.ofArray(Value.of(ENTITY_CTHULHU), Value.of(ENTITY_AZATHOTH),
                Value.of(ENTITY_NYARLATHOTEP));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(ArrayValue.class);
        assertThat(node.isArray()).isTrue();
        assertThat(node).hasSize(3);
    }

    @Test
    void roundTripMixedTypeArray() {
        var original = Value.ofArray(Value.of(true), Value.of(SANITY_THRESHOLD), Value.of(LOCATION_RLYEH), Value.NULL,
                Value.ofArray(Value.of("nested")));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
        assertThat((ArrayValue) result).hasSize(5);
    }

    @Test
    void roundTripSimpleObject() {
        var original = Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "location", Value.of(LOCATION_RLYEH),
                "sanity", Value.of(SANITY_THRESHOLD), "awakened", Value.of(false)));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(ObjectValue.class);
        assertThat(node.isObject()).isTrue();
        assertThat(node).hasSize(4);
    }

    @Test
    void roundTripNestedObject() {
        var original = Value.ofObject(Map.of("cultist",
                Value.ofObject(Map.of("name", Value.of("Abdul Alhazred"), "title", Value.of("Mad Arab"), "sanity",
                        Value.of(0))),
                "tome", Value.ofObject(Map.of("name", Value.of("Necronomicon"), "forbidden", Value.of(true)))));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void roundTripComplexStructure() {
        var original = Value.ofObject(Map.of("entities",
                Value.ofArray(
                        Value.ofObject(Map.of("name", Value.of(ENTITY_CTHULHU), "location", Value.of(LOCATION_RLYEH),
                                "power", Value.of(HORROR_LEVEL_MAX))),
                        Value.ofObject(Map.of("name", Value.of(ENTITY_AZATHOTH), "location",
                                Value.of("Court of Azathoth"), "power", Value.of(Integer.MAX_VALUE)))),
                "investigators", Value.ofArray(Value.of("Herbert West"), Value.of("Randolph Carter")), "active",
                Value.of(true)));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void roundTripAccessControlPolicy() {
        var original = Value.ofObject(Map.of("subject",
                Value.ofObject(Map.of("userId", Value.of("alice"), "clearanceLevel", Value.of(3), "roles",
                        Value.ofArray(Value.of("analyst"), Value.of("viewer")))),
                "resource", Value.ofObject(Map.of("documentId", Value.of("doc-12345"), "classification",
                        Value.of("confidential"), "department", Value.of("intelligence"))),
                "action", Value.of("read")));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    // ============================================================
    // Error Handling Tests
    // ============================================================

    @Test
    void toJsonNodeRejectsNull() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot marshall null to JsonNode.");
    }

    @Test
    void toJsonNodeRejectsUndefinedValue() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(Value.UNDEFINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot marshall UndefinedValue to JSON");
    }

    @Test
    void toJsonNodeRejectsErrorValue() {
        var error = Value.error("Eldritch horror encountered");

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(error)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot marshall ErrorValue to JSON")
                .hasMessageContaining("Eldritch horror encountered");
    }

    @Test
    void toJsonNodeRejectsNestedUndefinedInArray() {
        var arrayWithUndefined = Value.ofArray(Value.of(ENTITY_CTHULHU), Value.UNDEFINED);

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(arrayWithUndefined))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UndefinedValue");
    }

    @Test
    void toJsonNodeRejectsNestedErrorInObject() {
        var objectWithError = Value
                .ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "status", Value.error("Gone mad")));

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(objectWithError))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ErrorValue");
    }

    @ParameterizedTest(name = "fromJsonNode handles: {0}")
    @MethodSource("specialJsonNodes")
    void fromJsonNodeHandlesSpecialNodes(String description, JsonNode node, Value expected) {
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> specialJsonNodes() {
        var objectNode = FACTORY.objectNode();
        return Stream.of(arguments("null", null, Value.NULL), arguments("null node", FACTORY.nullNode(), Value.NULL),
                arguments("missing node", objectNode.get("nonexistent"), Value.NULL));
    }

    @ParameterizedTest(name = "fromJsonNode returns error for: {0}")
    @MethodSource("unsupportedJsonNodes")
    void fromJsonNodeReturnsErrorForUnsupportedTypes(String description, JsonNode node) {
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) result).message()).contains("Unknown JsonNode type");
    }

    static Stream<Arguments> unsupportedJsonNodes() {
        return Stream.of(arguments("binary", FACTORY.binaryNode(new byte[] { 0x01, 0x02, 0x03 })),
                arguments("pojo", FACTORY.pojoNode(new Object())));
    }

    // ============================================================
    // JSON Compatibility Tests
    // ============================================================

    @ParameterizedTest(name = "isJsonCompatible true for: {0}")
    @MethodSource("jsonCompatibleValues")
    void isJsonCompatibleReturnsTrueForCompatibleValues(String description, Value value) {
        assertThat(ValueJsonMarshaller.isJsonCompatible(value)).isTrue();
    }

    static Stream<Arguments> jsonCompatibleValues() {
        return Stream.of(arguments("null", Value.NULL), arguments("true", Value.TRUE), arguments("false", Value.FALSE),
                arguments("number", Value.of(SANITY_THRESHOLD)), arguments("text", Value.of(ENTITY_CTHULHU)),
                arguments("empty array", Value.EMPTY_ARRAY), arguments("empty object", Value.EMPTY_OBJECT),
                arguments("simple array", Value.ofArray(Value.of("item1"), Value.of("item2"))),
                arguments("simple object", Value.ofObject(Map.of("key", Value.of("value")))),
                arguments("nested structure",
                        Value.ofObject(Map.of("investigators", Value.ofArray(Value.of("Carter"), Value.of("West")),
                                "active", Value.of(true)))),
                arguments("secret value", Value.of("classified").asSecret()),
                arguments("max depth", createDeeplyNestedArray(ValueJsonMarshaller.MAX_DEPTH)));
    }

    @ParameterizedTest(name = "isJsonCompatible false for: {0}")
    @MethodSource("jsonIncompatibleValues")
    void isJsonCompatibleReturnsFalseForIncompatibleValues(String description, Value value) {
        assertThat(ValueJsonMarshaller.isJsonCompatible(value)).isFalse();
    }

    static Stream<Arguments> jsonIncompatibleValues() {
        return Stream.of(arguments("undefined", Value.UNDEFINED), arguments("error", Value.error("Eldritch horror")),
                arguments("nested undefined in array", Value.ofArray(Value.of(ENTITY_CTHULHU), Value.UNDEFINED)),
                arguments("nested error in object", Value.ofObject(Map.of("status", Value.error("Madness")))),
                arguments("deeply nested error",
                        Value.ofObject(Map.of("outer",
                                Value.ofArray(Value.ofObject(Map.of("inner", Value.error("Deep horror"))))))),
                arguments("excessive depth", createDeeplyNestedArray(MALICIOUS_DEPTH)));
    }

    @Test
    void isJsonCompatibleReturnsFalseForNull() {
        assertThat(ValueJsonMarshaller.isJsonCompatible(null)).isFalse();
    }

    @Test
    void isJsonCompatibleDoesNotThrow() {
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(Value.UNDEFINED)).doesNotThrowAnyException();
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(Value.error("test"))).doesNotThrowAnyException();
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(null)).doesNotThrowAnyException();
    }

    // ============================================================
    // Depth Protection Tests
    // ============================================================

    @ParameterizedTest(name = "toJsonNode rejects excessive depth: {0}")
    @MethodSource("excessivelyDeepValueStructures")
    void toJsonNodeRejectsExcessiveDepth(String description, Value deepStructure) {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(deepStructure))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Maximum nesting depth exceeded.");
    }

    static Stream<Arguments> excessivelyDeepValueStructures() {
        return Stream.of(arguments("arrays", createDeeplyNestedArray(MALICIOUS_DEPTH)),
                arguments("objects", createDeeplyNestedObject()), arguments("mixed", createDeeplyNestedMixed()),
                arguments("auth context", createMaliciousAuthorizationContext()));
    }

    @Test
    void toJsonNodeAcceptsMaximumAllowedDepth() {
        var deepArray = createDeeplyNestedArray(ValueJsonMarshaller.MAX_DEPTH);

        assertThatCode(() -> ValueJsonMarshaller.toJsonNode(deepArray)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "fromJsonNode rejects excessive depth: {0}")
    @MethodSource("excessivelyDeepJsonNodes")
    void fromJsonNodeRejectsExcessiveDepth(String description, JsonNode deepNode) {
        var result = ValueJsonMarshaller.fromJsonNode(deepNode);

        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) result).message()).isEqualTo("Maximum nesting depth exceeded.");
    }

    static Stream<Arguments> excessivelyDeepJsonNodes() {
        return Stream.of(arguments("arrays", createDeeplyNestedJsonArray(MALICIOUS_DEPTH)),
                arguments("objects", createDeeplyNestedJsonObject()));
    }

    @Test
    void fromJsonNodeAcceptsMaximumAllowedDepth() {
        var deepNode = createDeeplyNestedJsonArray(ValueJsonMarshaller.MAX_DEPTH);
        var result   = ValueJsonMarshaller.fromJsonNode(deepNode);

        assertThat(result instanceof ErrorValue).isFalse();
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @ParameterizedTest(name = "round-trip large: {0}")
    @MethodSource("largeStructures")
    void roundTripLargeStructures(String description, Value original) {
        var node   = ValueJsonMarshaller.toJsonNode(original);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    static Stream<Arguments> largeStructures() {
        var largeArray = new Value[10000];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = Value.of("investigator-" + i);
        }

        var largeObjectBuilder = ObjectValue.builder();
        for (int i = 0; i < 10000; i++) {
            largeObjectBuilder.put("cultist-" + i, Value.of("investigator-" + i));
        }

        return Stream.of(arguments("array", Value.ofArray(largeArray)), arguments("object", largeObjectBuilder.build()),
                arguments("string", Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn ".repeat(1000))),
                arguments("number", Value.of(new BigDecimal("9".repeat(1000) + "." + "9".repeat(1000)))));
    }

    @Test
    void roundTripObjectWithSpecialKeys() {
        var original = Value.ofObject(Map.of("", Value.of("empty key"), " ", Value.of("space key"), "key with spaces",
                Value.of("spaces in key"), "key-with-dashes", Value.of("dashes"), "key_with_underscores",
                Value.of("underscores"), "キー", Value.of("unicode key"), "🔑", Value.of("emoji key")));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void secretFlagNotPreservedInNestedStructures() {
        var secretInner = Value.of("classified").asSecret();
        var original    = Value.ofObject(Map.of("public", Value.of("open data"), "secret", secretInner));
        var node        = ValueJsonMarshaller.toJsonNode(original);
        var result      = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);

        var resultObj = (ObjectValue) result;
        assertThat(Objects.requireNonNull(resultObj.get("secret")).secret()).isFalse();
        assertThat(Objects.requireNonNull(((ObjectValue) original).get("secret")).secret()).isTrue();

        assertThat(result.toString()).isNotEqualTo(original.toString());
    }

    @ParameterizedTest(name = "round-trip all null: {0}")
    @MethodSource("allNullCollections")
    void roundTripAllNull(String description, Value original) {
        var node   = ValueJsonMarshaller.toJsonNode(original);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    static Stream<Arguments> allNullCollections() {
        return Stream.of(arguments("array", Value.ofArray(Value.NULL, Value.NULL, Value.NULL)), arguments("object",
                Value.ofObject(Map.of("entity", Value.NULL, "location", Value.NULL, "status", Value.NULL))));
    }

    // ============================================================
    // Helper Methods for Creating Deep Structures
    // ============================================================

    private static Value createDeeplyNestedArray(int depth) {
        Value current = Value.of(SANITY_THRESHOLD);
        for (int i = 0; i < depth - 1; i++) {
            current = Value.ofArray(current);
        }
        return current;
    }

    private static Value createDeeplyNestedObject() {
        Value current = Value.of(SANITY_THRESHOLD);
        for (int i = 0; i < ValueJsonMarshallerTests.MALICIOUS_DEPTH - 1; i++) {
            current = Value.ofObject(Map.of("nest", current));
        }
        return current;
    }

    private static Value createDeeplyNestedMixed() {
        Value current = Value.of(ENTITY_CTHULHU);
        for (int i = 0; i < ValueJsonMarshallerTests.MALICIOUS_DEPTH - 1; i++) {
            if (i % 2 == 0) {
                current = Value.ofArray(current);
            } else {
                current = Value.ofObject(Map.of("data", current));
            }
        }
        return current;
    }

    private static JsonNode createDeeplyNestedJsonArray(int depth) {
        JsonNode current = FACTORY.numberNode(SANITY_THRESHOLD);
        for (int i = 0; i < depth - 1; i++) {
            var array = FACTORY.arrayNode();
            array.add(current);
            current = array;
        }
        return current;
    }

    private static JsonNode createDeeplyNestedJsonObject() {
        JsonNode current = FACTORY.numberNode(SANITY_THRESHOLD);
        for (int i = 0; i < ValueJsonMarshallerTests.MALICIOUS_DEPTH - 1; i++) {
            var object = FACTORY.objectNode();
            object.set("nest", current);
            current = object;
        }
        return current;
    }

    private static Value createMaliciousAuthorizationContext() {
        Value current = Value.ofObject(Map.of("userId", Value.of("attacker"), "malicious", Value.of(true)));
        for (int i = 0; i < ValueJsonMarshallerTests.MALICIOUS_DEPTH; i++) {
            current = Value.ofObject(Map.of("level" + i, current, "metadata", Value.of("layer-" + i)));
        }
        return Value
                .ofObject(Map.of("subject", current, "action", Value.of("exploit"), "resource", Value.of("system")));
    }
}
