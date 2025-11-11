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

/**
 * Comprehensive test suite for ValueJsonMarshaller.
 * Tests cover round-trip conversion, edge cases, error handling, and depth
 * protection.
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
        return Stream.of(Arguments.of("singleton", Value.NULL), Arguments.of("non-secret", new NullValue(false)),
                Arguments.of("secret", new NullValue(true)));
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
        return Stream.of(Arguments.of(BigDecimal.ZERO), Arguments.of(BigDecimal.ONE), Arguments.of(BigDecimal.TEN),
                Arguments.of(new BigDecimal("-1")), Arguments.of(new BigDecimal("3.14159")),
                Arguments.of(new BigDecimal("42.42")),
                Arguments.of(new BigDecimal("999999999999999999999999999999.999999999999")),
                Arguments.of(new BigDecimal("-0.00000000000000000001")), Arguments.of(new BigDecimal(SANITY_THRESHOLD)),
                Arguments.of(new BigDecimal(HORROR_LEVEL_MAX)));
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
        return Stream.of(Arguments.of("empty", ""), Arguments.of("space", " "), Arguments.of("simple", "simple"),
                Arguments.of("entity", ENTITY_CTHULHU),
                Arguments.of("incantation", "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn"),
                Arguments.of("unicode", "日本語 中文 한국어 العربية"), Arguments.of("special", "\n\t\r\"'\\"),
                Arguments.of("emoji", "🐙🦑👁️"), Arguments.of("long", "a".repeat(10000)));
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
        return Stream.of(Arguments.of("zero", 0.0), Arguments.of("negative zero", -0.0),
                Arguments.of("min", Double.MIN_VALUE), Arguments.of("max", Double.MAX_VALUE), Arguments.of("e", Math.E),
                Arguments.of("pi", Math.PI));
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
        return Stream.of(Arguments.of("array", Value.EMPTY_ARRAY, ArrayValue.class),
                Arguments.of("object", Value.EMPTY_OBJECT, ObjectValue.class));
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
        return Stream.of(Arguments.of("null", null, Value.NULL),
                Arguments.of("null node", FACTORY.nullNode(), Value.NULL),
                Arguments.of("missing node", objectNode.get("nonexistent"), Value.NULL));
    }

    @ParameterizedTest(name = "fromJsonNode returns error for: {0}")
    @MethodSource("unsupportedJsonNodes")
    void fromJsonNodeReturnsErrorForUnsupportedTypes(String description, JsonNode node) {
        var result = ValueJsonMarshaller.fromJsonNode(node);

        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(((ErrorValue) result).message()).contains("Unknown JsonNode type");
    }

    static Stream<Arguments> unsupportedJsonNodes() {
        return Stream.of(Arguments.of("binary", FACTORY.binaryNode(new byte[] { 0x01, 0x02, 0x03 })),
                Arguments.of("pojo", FACTORY.pojoNode(new Object())));
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
        return Stream.of(Arguments.of("arrays", createDeeplyNestedArray(MALICIOUS_DEPTH)),
                Arguments.of("objects", createDeeplyNestedObject(MALICIOUS_DEPTH)),
                Arguments.of("mixed", createDeeplyNestedMixed(MALICIOUS_DEPTH)),
                Arguments.of("auth context", createMaliciousAuthorizationContext(MALICIOUS_DEPTH)));
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
        return Stream.of(Arguments.of("arrays", createDeeplyNestedJsonArray(MALICIOUS_DEPTH)),
                Arguments.of("objects", createDeeplyNestedJsonObject(MALICIOUS_DEPTH)));
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

        return Stream.of(Arguments.of("array", Value.ofArray(largeArray)),
                Arguments.of("object", largeObjectBuilder.build()),
                Arguments.of("string", Value.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn ".repeat(1000))),
                Arguments.of("number", Value.of(new BigDecimal("9".repeat(1000) + "." + "9".repeat(1000)))));
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

        var resultObj   = (ObjectValue) result;
        var originalObj = (ObjectValue) original;
        assertThat(Objects.requireNonNull(resultObj.get("secret")).secret()).isFalse();
        assertThat(Objects.requireNonNull(originalObj.get("secret")).secret()).isTrue();

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
        return Stream.of(Arguments.of("array", Value.ofArray(Value.NULL, Value.NULL, Value.NULL)), Arguments.of(
                "object", Value.ofObject(Map.of("entity", Value.NULL, "location", Value.NULL, "status", Value.NULL))));
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

    private static Value createDeeplyNestedObject(int depth) {
        Value current = Value.of(SANITY_THRESHOLD);
        for (int i = 0; i < depth - 1; i++) {
            current = Value.ofObject(Map.of("nest", current));
        }
        return current;
    }

    private static Value createDeeplyNestedMixed(int depth) {
        Value current = Value.of(ENTITY_CTHULHU);
        for (int i = 0; i < depth - 1; i++) {
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

    private static JsonNode createDeeplyNestedJsonObject(int depth) {
        JsonNode current = FACTORY.numberNode(SANITY_THRESHOLD);
        for (int i = 0; i < depth - 1; i++) {
            var object = FACTORY.objectNode();
            object.set("nest", current);
            current = object;
        }
        return current;
    }

    private static Value createMaliciousAuthorizationContext(int depth) {
        Value current = Value.ofObject(Map.of("userId", Value.of("attacker"), "malicious", Value.of(true)));
        for (int i = 0; i < depth; i++) {
            current = Value.ofObject(Map.of("level" + i, current, "metadata", Value.of("layer-" + i)));
        }
        return Value
                .ofObject(Map.of("subject", current, "action", Value.of("exploit"), "resource", Value.of("system")));
    }
}
