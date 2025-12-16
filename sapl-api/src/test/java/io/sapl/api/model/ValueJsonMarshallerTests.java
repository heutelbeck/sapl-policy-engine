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
    void when_roundTripNull_then_preservesValue(String description, Value value) {
        var node   = ValueJsonMarshaller.toJsonNode(value);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(Value.NULL).isInstanceOf(NullValue.class);
    }

    static Stream<Arguments> nullValues() {
        return Stream.of(arguments("singleton", Value.NULL),
                arguments("non-secret", new NullValue(ValueMetadata.EMPTY)),
                arguments("secret", new NullValue(ValueMetadata.SECRET_EMPTY)));
    }

    @ParameterizedTest(name = "round-trip boolean: {0}")
    @ValueSource(booleans = { true, false })
    void when_roundTripBoolean_then_preservesValue(boolean value) {
        var original = Value.of(value);
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(BooleanValue.class);
        assertThat(node.isBoolean()).isTrue();
        assertThat(node.asBoolean()).isEqualTo(value);
    }

    @ParameterizedTest(name = "round-trip number: {0}")
    @MethodSource("numberValues")
    void when_roundTripNumber_then_preservesValue(BigDecimal value) {
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
    void when_roundTripText_then_preservesValue(String description, String value) {
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
    void when_roundTripDouble_then_preservesValue(String description, double value) {
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
    void when_roundTripSecretPrimitive_then_secretFlagNotPreserved() {
        var original = Value.of(true).asSecret();
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(Value.TRUE).isNotSameAs(original);
        assertThat(result.isSecret()).isFalse();
    }

    // ============================================================
    // Round-trip Conversion Tests - Collections
    // ============================================================

    @ParameterizedTest(name = "round-trip empty: {0}")
    @MethodSource("emptyCollections")
    void when_roundTripEmptyCollection_then_preservesValue(String description, Value original, Class<?> expectedType) {
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
    void when_roundTripSimpleArray_then_preservesValue() {
        var original = Value.ofArray(Value.of(ENTITY_CTHULHU), Value.of(ENTITY_AZATHOTH),
                Value.of(ENTITY_NYARLATHOTEP));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(ArrayValue.class);
        assertThat(node.isArray()).isTrue();
        assertThat(node).hasSize(3);
    }

    @Test
    void when_roundTripMixedTypeArray_then_preservesValue() {
        var original = Value.ofArray(Value.of(true), Value.of(SANITY_THRESHOLD), Value.of(LOCATION_RLYEH), Value.NULL,
                Value.ofArray(Value.of("nested")));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
        assertThat((ArrayValue) result).hasSize(5);
    }

    @Test
    void when_roundTripSimpleObject_then_preservesValue() {
        var original = Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "location", Value.of(LOCATION_RLYEH),
                "sanity", Value.of(SANITY_THRESHOLD), "awakened", Value.of(false)));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original).isInstanceOf(ObjectValue.class);
        assertThat(node.isObject()).isTrue();
        assertThat(node).hasSize(4);
    }

    @Test
    void when_roundTripNestedObject_then_preservesValue() {
        var original = Value.ofObject(Map.of("cultist",
                Value.ofObject(Map.of("name", Value.of("Abdul Alhazred"), "title", Value.of("Mad Arab"), "sanity",
                        Value.of(0))),
                "tome", Value.ofObject(Map.of("name", Value.of("Necronomicon"), "forbidden", Value.of(true)))));
        var node     = ValueJsonMarshaller.toJsonNode(original);
        var result   = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void when_roundTripComplexStructure_then_preservesValue() {
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
    void when_roundTripAccessControlPolicy_then_preservesValue() {
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
    void when_toJsonNodeWithNull_then_rejectsNull() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot marshall null to JsonNode.");
    }

    @Test
    void when_toJsonNodeWithUndefined_then_rejectsUndefinedValue() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(Value.UNDEFINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot marshall UndefinedValue to JSON");
    }

    @Test
    void when_toJsonNodeWithError_then_rejectsErrorValue() {
        var error = Value.error("Eldritch horror encountered");

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(error)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot marshall ErrorValue to JSON")
                .hasMessageContaining("Eldritch horror encountered");
    }

    @Test
    void when_toJsonNodeWithNestedUndefined_then_rejectsNestedUndefined() {
        var arrayWithUndefined = Value.ofArray(Value.of(ENTITY_CTHULHU), Value.UNDEFINED);

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(arrayWithUndefined))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UndefinedValue");
    }

    @Test
    void when_toJsonNodeWithNestedError_then_rejectsNestedError() {
        var objectWithError = Value
                .ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "status", Value.error("Gone mad")));

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(objectWithError))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ErrorValue");
    }

    @ParameterizedTest(name = "fromJsonNode handles: {0}")
    @MethodSource("specialJsonNodes")
    void when_fromJsonNodeWithSpecialNodes_then_handlesCorrectly(String description, JsonNode node, Value expected) {
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
    void when_fromJsonNodeWithUnsupportedType_then_returnsError(String description, JsonNode node) {
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isInstanceOf(ErrorValue.class);
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
    void when_isJsonCompatibleWithCompatibleValue_then_returnsTrue(String description, Value value) {
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
    void when_isJsonCompatibleWithIncompatibleValue_then_returnsFalse(String description, Value value) {
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
    void when_isJsonCompatibleWithNull_then_returnsFalse() {
        assertThat(ValueJsonMarshaller.isJsonCompatible(null)).isFalse();
    }

    @Test
    void when_isJsonCompatibleWithIncompatible_then_doesNotThrow() {
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(Value.UNDEFINED)).doesNotThrowAnyException();
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(Value.error("test"))).doesNotThrowAnyException();
        assertThatCode(() -> ValueJsonMarshaller.isJsonCompatible(null)).doesNotThrowAnyException();
    }

    // ============================================================
    // Depth Protection Tests
    // ============================================================

    @ParameterizedTest(name = "toJsonNode rejects excessive depth: {0}")
    @MethodSource("excessivelyDeepValueStructures")
    void when_toJsonNodeWithExcessiveDepth_then_rejects(String description, Value deepStructure) {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonNode(deepStructure))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Maximum nesting depth exceeded.");
    }

    static Stream<Arguments> excessivelyDeepValueStructures() {
        return Stream.of(arguments("arrays", createDeeplyNestedArray(MALICIOUS_DEPTH)),
                arguments("objects", createDeeplyNestedObject()), arguments("mixed", createDeeplyNestedMixed()),
                arguments("auth context", createMaliciousAuthorizationContext()));
    }

    @Test
    void when_toJsonNodeWithMaxDepth_then_accepts() {
        var deepArray = createDeeplyNestedArray(ValueJsonMarshaller.MAX_DEPTH);

        assertThatCode(() -> ValueJsonMarshaller.toJsonNode(deepArray)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "fromJsonNode rejects excessive depth: {0}")
    @MethodSource("excessivelyDeepJsonNodes")
    void when_fromJsonNodeWithExcessiveDepth_then_returnsError(String description, JsonNode deepNode) {
        var result = ValueJsonMarshaller.fromJsonNode(deepNode);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).isEqualTo("Maximum nesting depth exceeded.");
    }

    static Stream<Arguments> excessivelyDeepJsonNodes() {
        return Stream.of(arguments("arrays", createDeeplyNestedJsonArray(MALICIOUS_DEPTH)),
                arguments("objects", createDeeplyNestedJsonObject()));
    }

    @Test
    void when_fromJsonNodeWithMaxDepth_then_accepts() {
        var deepNode = createDeeplyNestedJsonArray(ValueJsonMarshaller.MAX_DEPTH);
        var result   = ValueJsonMarshaller.fromJsonNode(deepNode);

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    // ============================================================
    // ofJson Tests
    // ============================================================

    @ParameterizedTest(name = "ofJson parses: {0}")
    @MethodSource("validJsonStrings")
    void whenValidJson_thenJsonReturnsExpectedValue(String description, String json, Value expected) {
        var result = ValueJsonMarshaller.json(json);

        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> validJsonStrings() {
        return Stream.of(arguments("null", "null", Value.NULL), arguments("true", "true", Value.TRUE),
                arguments("false", "false", Value.FALSE), arguments("integer", "42", Value.of(42)),
                arguments("negative", "-17", Value.of(-17)),
                arguments("decimal", "3.14159", Value.of(new BigDecimal("3.14159"))),
                arguments("empty string", "\"\"", Value.EMPTY_TEXT),
                arguments("simple string", "\"Cthulhu\"", Value.of(ENTITY_CTHULHU)),
                arguments("empty array", "[]", Value.EMPTY_ARRAY), arguments("empty object", "{}", Value.EMPTY_OBJECT),
                arguments("simple array", "[1, 2, 3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("simple object", "{\"entity\": \"Cthulhu\", \"sanity\": 0}",
                        Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "sanity", Value.of(0)))),
                arguments("nested structure", "{\"location\": {\"name\": \"R'lyeh\", \"sunken\": true}}",
                        Value.ofObject(Map.of("location",
                                Value.ofObject(Map.of("name", Value.of(LOCATION_RLYEH), "sunken", Value.TRUE))))),
                arguments("access control subject", "{\"role\": \"admin\", \"clearance\": 5}",
                        Value.ofObject(Map.of("role", Value.of("admin"), "clearance", Value.of(5)))));
    }

    @ParameterizedTest(name = "ofJson returns error for: {0}")
    @MethodSource("invalidJsonStrings")
    void whenInvalidJson_thenJsonReturnsErrorValue(String description, String json) {
        var result = ValueJsonMarshaller.json(json);

        assertThat(result).isNotNull().isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Failed to parse JSON");
    }

    static Stream<Arguments> invalidJsonStrings() {
        return Stream.of(arguments("unquoted string", "hello"), arguments("trailing comma in array", "[1, 2, 3,]"),
                arguments("trailing comma in object", "{\"key\": \"value\",}"),
                arguments("single quotes", "{'key': 'value'}"), arguments("unclosed brace", "{\"key\": \"value\""),
                arguments("unclosed bracket", "[1, 2, 3"), arguments("unclosed string", "\"unterminated"),
                arguments("invalid escape", "\"bad\\x\""));
    }

    @Test
    void when_jsonWithEmptyString_then_returnsErrorForMissingNode() {
        var result = ValueJsonMarshaller.json("");

        assertThat(result).isNotNull().isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Unknown JsonNode type: MISSING");
    }

    // ============================================================
    // toJsonString Tests
    // ============================================================

    @ParameterizedTest(name = "toJsonString serializes: {0}")
    @MethodSource("valuesToJsonStrings")
    void whenValue_thenToJsonStringReturnsExpectedJson(String description, Value value, String expectedJson) {
        var result = ValueJsonMarshaller.toJsonString(value);

        assertThat(result).isEqualTo(expectedJson);
    }

    static Stream<Arguments> valuesToJsonStrings() {
        return Stream.of(arguments("null", Value.NULL, "null"), arguments("true", Value.TRUE, "true"),
                arguments("false", Value.FALSE, "false"), arguments("integer", Value.of(42), "42"),
                arguments("negative", Value.of(-17), "-17"), arguments("text", Value.of(ENTITY_CTHULHU), "\"Cthulhu\""),
                arguments("empty array", Value.EMPTY_ARRAY, "[]"), arguments("empty object", Value.EMPTY_OBJECT, "{}"),
                arguments("simple array", Value.ofArray(Value.of(1), Value.of(2), Value.of(3)), "[1,2,3]"),
                arguments("nested object", Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU))),
                        "{\"entity\":\"Cthulhu\"}"));
    }

    @Test
    void when_toJsonStringWithNull_then_throws() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonString(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot marshall null to JsonNode.");
    }

    @Test
    void when_toJsonStringWithUndefined_then_throws() {
        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonString(Value.UNDEFINED))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UndefinedValue");
    }

    @Test
    void when_toJsonStringWithError_then_throws() {
        var error = Value.error("Eldritch horror");

        assertThatThrownBy(() -> ValueJsonMarshaller.toJsonString(error)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ErrorValue").hasMessageContaining("Eldritch horror");
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @ParameterizedTest(name = "round-trip edge case: {0}")
    @MethodSource("edgeCaseStructures")
    void when_roundTripEdgeCase_then_preservesValue(String description, Value original) {
        var node   = ValueJsonMarshaller.toJsonNode(original);
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isEqualTo(original);
    }

    static Stream<Arguments> edgeCaseStructures() {
        // Large structures
        var largeArray = new Value[10000];
        for (int i = 0; i < largeArray.length; i++) {
            largeArray[i] = Value.of("investigator-" + i);
        }

        var largeObjectBuilder = ObjectValue.builder();
        for (int i = 0; i < 10000; i++) {
            largeObjectBuilder.put("cultist-" + i, Value.of("investigator-" + i));
        }

        // Special keys object
        var specialKeysObject = Value
                .ofObject(Map.of("", Value.of("empty key"), " ", Value.of("space key"), "key with spaces",
                        Value.of("spaces in key"), "key-with-dashes", Value.of("dashes"), "key_with_underscores",
                        Value.of("underscores"), "キー", Value.of("unicode key"), "🔑", Value.of("emoji key")));

        return Stream.of(
                // Large structures
                arguments("large array", Value.ofArray(largeArray)),
                arguments("large object", largeObjectBuilder.build()),
                arguments("large string", Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn ".repeat(1000))),
                arguments("large number", Value.of(new BigDecimal("9".repeat(1000) + "." + "9".repeat(1000)))),
                // Special keys
                arguments("special keys", specialKeysObject),
                // All null collections
                arguments("all null array", Value.ofArray(Value.NULL, Value.NULL, Value.NULL)),
                arguments("all null object",
                        Value.ofObject(Map.of("entity", Value.NULL, "location", Value.NULL, "status", Value.NULL))));
    }

    @Test
    void when_roundTripNestedSecretStructure_then_secretFlagNotPreserved() {
        var secretInner = Value.of("classified").asSecret();
        var original    = Value.ofObject(Map.of("public", Value.of("open data"), "secret", secretInner));
        var node        = ValueJsonMarshaller.toJsonNode(original);
        var result      = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result).isNotNull().isEqualTo(original).isInstanceOf(ObjectValue.class);

        var resultObj      = (ObjectValue) result;
        var resultSecret   = resultObj.get("secret");
        var originalSecret = original.get("secret");
        assertThat(resultSecret).isNotNull();
        assertThat(originalSecret).isNotNull();
        assertThat(resultSecret.isSecret()).isFalse();
        assertThat(originalSecret.isSecret()).isTrue();
        assertThat(result.toString()).isNotEqualTo(original.toString());
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

    // ============================================================
    // Pretty Printing Tests
    // ============================================================

    @ParameterizedTest(name = "toPrettyString primitive: {0}")
    @MethodSource("primitivePrettyStringCases")
    void when_toPrettyStringWithPrimitive_then_formatsCorrectly(String description, Value value, String expected) {
        assertThat(ValueJsonMarshaller.toPrettyString(value)).isEqualTo(expected);
    }

    static Stream<Arguments> primitivePrettyStringCases() {
        return Stream.of(arguments("null value", Value.NULL, "null"), arguments("true", Value.TRUE, "true"),
                arguments("false", Value.FALSE, "false"), arguments("integer", Value.of(42), "42"),
                arguments("negative", Value.of(-17), "-17"),
                arguments("decimal", Value.of(new BigDecimal("3.14159")), "3.14159"),
                arguments("simple text", Value.of(ENTITY_CTHULHU), "\"Cthulhu\""),
                arguments("text with quotes", Value.of("He said \"Ph'nglui\""), "\"He said \\\"Ph'nglui\\\"\""),
                arguments("text with newline", Value.of("line1\nline2"), "\"line1\\nline2\""),
                arguments("undefined", Value.UNDEFINED, "undefined"),
                arguments("error", Value.error("Eldritch horror"), "ERROR[message=\"Eldritch horror\"]"),
                arguments("java null", null, "null"));
    }

    @Test
    void when_toPrettyStringWithEmptyCollections_then_formatsCompact() {
        assertThat(ValueJsonMarshaller.toPrettyString(Value.EMPTY_ARRAY)).isNotNull().isEqualTo("[]");
        assertThat(ValueJsonMarshaller.toPrettyString(Value.EMPTY_OBJECT)).isNotNull().isEqualTo("{}");
    }

    @Test
    void when_toPrettyStringWithSimpleArray_then_formatsOnOneLine() {
        var array = Value.ofArray(Value.of(1), Value.of(2), Value.of(3));

        assertThat(ValueJsonMarshaller.toPrettyString(array)).isNotNull().isEqualTo("[1, 2, 3]");
    }

    @Test
    void when_toPrettyStringWithComplexArray_then_formatsWithIndentation() {
        var array = Value.ofArray(Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU))),
                Value.ofObject(Map.of("entity", Value.of(ENTITY_AZATHOTH))));

        var result   = ValueJsonMarshaller.toPrettyString(array);
        var expected = """
                [
                  {
                    "entity": "Cthulhu"
                  },
                  {
                    "entity": "Azathoth"
                  }
                ]""";

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void when_toPrettyStringWithObject_then_formatsWithIndentation() {
        var object = Value.ofObject(Map.of("entity", Value.of(ENTITY_CTHULHU), "location", Value.of(LOCATION_RLYEH),
                "sanity", Value.of(SANITY_THRESHOLD)));

        var result = ValueJsonMarshaller.toPrettyString(object);

        assertThat(result).isNotNull().contains("\"entity\": \"Cthulhu\"").contains("\"location\": \"R'lyeh\"")
                .contains("\"sanity\": 42").startsWith("{\n").endsWith("\n}");
    }

    @Test
    void when_toPrettyStringWithNestedObject_then_formatsWithProperIndentation() {
        var nested = Value.ofObject(Map.of("outer", Value.ofObject(Map.of("inner", Value.of("deep horror")))));

        var result   = ValueJsonMarshaller.toPrettyString(nested);
        var expected = """
                {
                  "outer": {
                    "inner": "deep horror"
                  }
                }""";

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void when_toPrettyStringWithMixedStructure_then_formatsCorrectly() {
        var decision = Value.ofObject(Map.of("decision", Value.of("PERMIT"), "obligations",
                Value.ofArray(Value.of("log"), Value.of("notify")), "resource",
                Value.ofObject(Map.of("filtered", Value.TRUE))));

        var result = ValueJsonMarshaller.toPrettyString(decision);

        assertThat(result).isNotNull().contains("\"decision\": \"PERMIT\"")
                .contains("\"obligations\": [\"log\", \"notify\"]")
                .contains("\"resource\": {\n    \"filtered\": true\n  }");
    }

    @Test
    void when_toPrettyStringWithSecretObject_then_masksContent() {
        var secret = Value.ofObject(Map.of("password", Value.of("secret123"))).asSecret();

        assertThat(ValueJsonMarshaller.toPrettyString(secret)).isEqualTo("<<SECRET>>");
    }

    @Test
    void when_toPrettyStringWithErrorWithLocation_then_includesLocation() {
        var error = new ErrorValue("Access denied")
                .withLocation(new SourceLocation("policy.sapl", null, 5, 20, 10, 10));

        var result = ValueJsonMarshaller.toPrettyString(error);

        assertThat(result).isNotNull().contains("ERROR[").contains("message=\"Access denied\"")
                .contains("at=policy.sapl:10 [5-20]");
    }

    @Test
    void when_toPrettyStringWithTracedDecision_then_producesReadableOutput() {
        var traced = Value.ofObject(Map.of("name", Value.of("permit-all"), "entitlement", Value.of("PERMIT"),
                "decision", Value.of("PERMIT"), "obligations", Value.EMPTY_ARRAY, "advice", Value.EMPTY_ARRAY,
                "resource", Value.UNDEFINED, "attributes",
                Value.ofArray(Value
                        .ofObject(Map.of("name", Value.of("time.now"), "value", Value.of("2025-01-01T00:00:00Z")))),
                "errors", Value.EMPTY_ARRAY));

        var result = ValueJsonMarshaller.toPrettyString(traced);

        assertThat(result).isNotNull().contains("\"name\": \"permit-all\"").contains("\"decision\": \"PERMIT\"")
                .contains("\"resource\": undefined").contains("\"time.now\"");
    }
}
