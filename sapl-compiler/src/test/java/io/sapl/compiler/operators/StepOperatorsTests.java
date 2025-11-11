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
package io.sapl.compiler.operators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.*;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.sapl.compiler.operators.StepOperators.*;
import static org.assertj.core.api.Assertions.assertThat;

class StepOperatorsTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Lovecraftian-themed test data
    private static final ArrayValue  EMPTY_TOME            = json("[]");
    private static final ArrayValue  NECRONOMICON_CHAPTERS = json(
            "[\"Al Azif\", \"Cultus Maleficarum\", \"Rites of Yog-Sothoth\", \"Forbidden Summonings\", \"The Key and the Gate\"]");
    private static final ArrayValue  ELDER_SIGNS           = json("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]");
    private static final ObjectValue EMPTY_GRIMOIRE        = json("{}");
    private static final ObjectValue CULTIST_RECORD        = ObjectValue.builder()
            .put("name", json("\"Wilbur Whateley\"")).put("ritualKnowledge", json("85")).put("sanity", json("12"))
            .build();
    private static final ObjectValue ARTIFACT_WITH_VOID    = json("""
            {
                "arkham": "Miskatonic University",
                "innsmouth": null,
                "dunwich": "Sentinel Hill"
            }
            """);

    @SneakyThrows
    private static <T extends Value> T json(String jsonString) {
        JsonNode node = MAPPER.readTree(jsonString);
        return (T) ValueJsonMarshaller.fromJsonNode(node);
    }

    // ========== keyStep Tests ==========

    private static Stream<Arguments> keyStepCases() {
        return Stream.of(Arguments.of(CULTIST_RECORD, "name", json("\"Wilbur Whateley\""), "existing key"),
                Arguments.of(CULTIST_RECORD, "ritualKnowledge", json("85"), "numeric value"),
                Arguments.of(CULTIST_RECORD, "sanity", json("12"), "last key"),
                Arguments.of(CULTIST_RECORD, "forbidden", Value.UNDEFINED, "missing key returns UNDEFINED"),
                Arguments.of(EMPTY_GRIMOIRE, "any", Value.UNDEFINED, "empty object returns UNDEFINED"));
    }

    @ParameterizedTest(name = "[{index}] keyStep: {3}")
    @MethodSource("keyStepCases")
    void keyStepReturnsCorrectValue(ObjectValue object, String key, Value expected, String description) {
        assertThat(keyStep(object, key)).isEqualTo(expected);
    }

    @Test
    void keyStepReturnsErrorForNonObject() {
        val result = keyStep(NECRONOMICON_CHAPTERS, "forbidden");
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an ObjectValue");
    }

    // ========== indexStep Tests ==========

    private static Stream<Arguments> indexStepValidCases() {
        return Stream.of(Arguments.of(NECRONOMICON_CHAPTERS, 0, json("\"Al Azif\""), "first chapter"),
                Arguments.of(NECRONOMICON_CHAPTERS, 2, json("\"Rites of Yog-Sothoth\""), "middle chapter"),
                Arguments.of(NECRONOMICON_CHAPTERS, 4, json("\"The Key and the Gate\""), "last chapter"),
                Arguments.of(NECRONOMICON_CHAPTERS, -1, json("\"The Key and the Gate\""), "negative: last"),
                Arguments.of(NECRONOMICON_CHAPTERS, -2, json("\"Forbidden Summonings\""), "negative: second-to-last"),
                Arguments.of(NECRONOMICON_CHAPTERS, -5, json("\"Al Azif\""), "negative: first"),
                Arguments.of(ELDER_SIGNS, 0, json("0"), "elder sign zero"),
                Arguments.of(ELDER_SIGNS, 9, json("9"), "elder sign nine"));
    }

    @ParameterizedTest(name = "[{index}] indexStep valid: {3}")
    @MethodSource("indexStepValidCases")
    void indexStepReturnsCorrectElement(ArrayValue array, int index, Value expected, String description) {
        assertThat(indexStep(array, BigDecimal.valueOf(index))).isEqualTo(expected);
    }

    private static Stream<Arguments> indexStepErrorCases() {
        return Stream.of(Arguments.of(NECRONOMICON_CHAPTERS, 5, "beyond known chapters"),
                Arguments.of(NECRONOMICON_CHAPTERS, 10, "far beyond the veil"),
                Arguments.of(NECRONOMICON_CHAPTERS, -6, "negative beyond start"),
                Arguments.of(NECRONOMICON_CHAPTERS, -10, "far into the void"),
                Arguments.of(EMPTY_TOME, 0, "empty tome"), Arguments.of(EMPTY_TOME, -1, "empty tome negative"));
    }

    @ParameterizedTest(name = "[{index}] indexStep error: {2}")
    @MethodSource("indexStepErrorCases")
    void indexStepReturnsErrorForOutOfBounds(ArrayValue array, int index, String description) {
        val result = indexStep(array, BigDecimal.valueOf(index));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("out of bounds");
    }

    @Test
    void indexStepReturnsErrorForNonArray() {
        val result = indexStep(CULTIST_RECORD, BigDecimal.ZERO);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an Array");
    }

    // ========== wildcardStep Tests ==========

    @Test
    void wildcardStepReturnsArrayIdentity() {
        assertThat(wildcardStep(NECRONOMICON_CHAPTERS)).isSameAs(NECRONOMICON_CHAPTERS);
    }

    @Test
    void wildcardStepConvertsObjectValuesToArray() {
        val result = wildcardStep(CULTIST_RECORD);
        assertThat(result).isInstanceOf(ArrayValue.class);
        val arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3).contains(json("\"Wilbur Whateley\"")).contains(json("85"))
                .contains(json("12"));
    }

    @Test
    void wildcardStepPreservesObjectInsertionOrder() {
        val cthulhuPriests = ObjectValue.builder().put("rlyeh", json("\"Cthulhu\""))
                .put("yuggoth", json("\"Shub-Niggurath\"")).put("kadath", json("\"Nyarlathotep\"")).build();
        val result         = (ArrayValue) wildcardStep(cthulhuPriests);
        assertThat(result.getFirst()).isEqualTo(json("\"Cthulhu\""));
        assertThat(result.get(1)).isEqualTo(json("\"Shub-Niggurath\""));
        assertThat(result.get(2)).isEqualTo(json("\"Nyarlathotep\""));
    }

    @Test
    void wildcardStepReturnsErrorForNonContainer() {
        val result = wildcardStep(json("42"));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to arrays or objects");
    }

    // ========== recursiveKeyStep Tests ==========

    @Test
    void recursiveKeyStepFindsShallowKey() {
        val array = (ArrayValue) recursiveKeyStep(CULTIST_RECORD, "name");
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(json("\"Wilbur Whateley\""));
    }

    @Test
    void recursiveKeyStepFindsNestedKeys() {
        val nestedCults = json("""
                {
                    "power": 100,
                    "innerCircle": {
                        "power": 200,
                        "highPriest": "forbidden"
                    },
                    "acolytes": [
                        {"power": 50}
                    ]
                }
                """);
        val array       = (ArrayValue) recursiveKeyStep(nestedCults, "power");
        assertThat(array).hasSize(3).contains(json("100")).contains(json("200")).contains(json("50"));
    }

    @Test
    void recursiveKeyStepReturnsEmptyForMissingKey() {
        assertThat(recursiveKeyStep(CULTIST_RECORD, "unknownOldOne")).isEqualTo(EMPTY_TOME);
    }

    @Test
    void recursiveKeyStepSearchesArraysForNestedObjects() {
        val arrayOfObjects = json("""
                [
                    {"ritual": "summoning"},
                    {"ritual": "binding"},
                    [{"ritual": "banishment"}]
                ]
                """);
        val array          = (ArrayValue) recursiveKeyStep(arrayOfObjects, "ritual");
        assertThat(array).hasSize(3).contains(json("\"summoning\"")).contains(json("\"binding\""))
                .contains(json("\"banishment\""));
    }

    /**
     * Tests that recursion depth limit is enforced to prevent stack overflow.
     * <p>
     * WARNING: This test is intentionally slow (~2-5 seconds) because it:
     * 1. Recursively creates 501 nested objects (expensive)
     * 2. Processes 500 levels before hitting the depth limit
     * <p>
     * The @Timeout prevents indefinite hangs if the depth check mechanism fails.
     * We use boolean instanceof check instead of AssertJ's isInstanceOf() to avoid
     * inspection overhead on deeply nested structures.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recursiveKeyStepHandlesMaxDepth() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val abyssalDepth = createDeeplyNestedObject(501);
        val result       = recursiveKeyStep(abyssalDepth, "key");

        // Use boolean check to avoid AssertJ inspection overhead
        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(result.toString()).contains("Maximum nesting depth exceeded");
    }

    // ========== recursiveIndexStep Tests ==========

    @Test
    void recursiveIndexStepFindsShallowIndex() {
        val array = (ArrayValue) recursiveIndexStep(NECRONOMICON_CHAPTERS, BigDecimal.valueOf(0));
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(json("\"Al Azif\""));
    }

    @Test
    void recursiveIndexStepFindsNestedIndices() {
        val nestedChants = json("""
                [
                    ["Ia! Ia!", "Ph'nglui"],
                    ["Cthulhu", "R'lyeh"],
                    "fhtagn"
                ]
                """);
        val array        = (ArrayValue) recursiveIndexStep(nestedChants, BigDecimal.valueOf(0));
        assertThat(array).hasSize(3).contains(json("\"Ia! Ia!\"")).contains(json("\"Cthulhu\""))
                .contains(json("[\"Ia! Ia!\", \"Ph'nglui\"]"));
    }

    @Test
    void recursiveIndexStepHandlesNegativeIndex() {
        val rituals = json("""
                [
                    ["Summoning", "Binding", "Banishment"],
                    ["Lesser", "Greater"]
                ]
                """);
        val array   = (ArrayValue) recursiveIndexStep(rituals, BigDecimal.valueOf(-1));
        assertThat(array).hasSize(3).contains(json("\"Banishment\"")).contains(json("\"Greater\""))
                .contains(json("[\"Lesser\", \"Greater\"]"));
    }

    @Test
    void recursiveIndexStepSkipsOutOfBoundsIndices() {
        val unevenDepths = json("""
                [
                    ["shallow"],
                    ["deeper", "still", "descending"]
                ]
                """);
        val array        = (ArrayValue) recursiveIndexStep(unevenDepths, BigDecimal.valueOf(2));
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(json("\"descending\""));
    }

    @Test
    void recursiveIndexStepSearchesObjectValuesForNestedArrays() {
        val objectWithArrays = json("""
                {
                    "incantations": ["Ia", "Cthulhu", "fhtagn"],
                    "nested": {
                        "chants": ["Ph'nglui", "mglw'nafh"]
                    }
                }
                """);
        val array            = (ArrayValue) recursiveIndexStep(objectWithArrays, BigDecimal.valueOf(1));
        assertThat(array).hasSize(2).contains(json("\"Cthulhu\"")).contains(json("\"mglw'nafh\""));
    }

    /**
     * Tests that recursion depth limit is enforced to prevent stack overflow.
     * <p>
     * WARNING: This test is intentionally slow (~2-5 seconds) because it:
     * 1. Recursively creates 501 nested arrays (expensive)
     * 2. Processes 500 levels before hitting the depth limit
     * <p>
     * The @Timeout prevents indefinite hangs if the depth check mechanism fails.
     * We use boolean instanceof check instead of AssertJ's isInstanceOf() to avoid
     * inspection overhead on deeply nested structures.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recursiveIndexStepHandlesMaxDepth() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val endlessVoid = createDeeplyNestedArray(501);
        val result      = recursiveIndexStep(endlessVoid, BigDecimal.valueOf(0));

        // Use boolean check to avoid AssertJ inspection overhead
        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(result.toString()).contains("Maximum nesting depth exceeded");
    }

    // ========== recursiveWildcardStep Tests ==========

    @Test
    void recursiveWildcardStepReturnsEmptyForPrimitives() {
        assertThat(recursiveWildcardStep(json("42"))).isEqualTo(EMPTY_TOME);
        assertThat(recursiveWildcardStep(json("\"Azathoth\""))).isEqualTo(EMPTY_TOME);
        assertThat(recursiveWildcardStep(json("true"))).isEqualTo(EMPTY_TOME);
        assertThat(recursiveWildcardStep(json("null"))).isEqualTo(EMPTY_TOME);
        assertThat(recursiveWildcardStep(Value.UNDEFINED)).isEqualTo(EMPTY_TOME);
    }

    @Test
    void recursiveWildcardStepCollectsAllArrayElements() {
        val result = (ArrayValue) recursiveWildcardStep(NECRONOMICON_CHAPTERS);
        assertThat(result).hasSize(5).contains(json("\"Al Azif\"")).contains(json("\"Cultus Maleficarum\""))
                .contains(json("\"Rites of Yog-Sothoth\"")).contains(json("\"Forbidden Summonings\""))
                .contains(json("\"The Key and the Gate\""));
    }

    @Test
    void recursiveWildcardStepCollectsAllObjectValues() {
        val result = (ArrayValue) recursiveWildcardStep(CULTIST_RECORD);
        assertThat(result).hasSize(3).contains(json("\"Wilbur Whateley\"")).contains(json("85")).contains(json("12"));
    }

    @Test
    void recursiveWildcardStepCollectsNestedArrayElements() {
        val nestedChants = json("""
                [
                    ["Ia! Ia!", "Ph'nglui"],
                    ["Cthulhu", "R'lyeh"],
                    "fhtagn"
                ]
                """);
        val result       = (ArrayValue) recursiveWildcardStep(nestedChants);
        assertThat(result).hasSize(7).contains(json("[\"Ia! Ia!\", \"Ph'nglui\"]")).contains(json("\"Ia! Ia!\""))
                .contains(json("\"Ph'nglui\"")).contains(json("[\"Cthulhu\", \"R'lyeh\"]"))
                .contains(json("\"Cthulhu\"")).contains(json("\"R'lyeh\"")).contains(json("\"fhtagn\""));
    }

    @Test
    void recursiveWildcardStepCollectsNestedObjectValues() {
        val nestedCults = json("""
                {
                    "outerCult": {
                        "name": "Esoteric Order",
                        "location": "Arkham"
                    },
                    "innerCircle": {
                        "highPriest": "Cthulhu",
                        "forbidden": true
                    },
                    "power": 666
                }
                """);
        val result      = (ArrayValue) recursiveWildcardStep(nestedCults);
        assertThat(result).hasSize(7).contains(json("{\"name\": \"Esoteric Order\", \"location\": \"Arkham\"}"))
                .contains(json("\"Esoteric Order\"")).contains(json("\"Arkham\""))
                .contains(json("{\"highPriest\": \"Cthulhu\", \"forbidden\": true}")).contains(json("\"Cthulhu\""))
                .contains(json("true")).contains(json("666"));
    }

    @Test
    void recursiveWildcardStepCollectsMixedNestedStructures() {
        val complexStructure = json("""
                {
                    "tomes": ["Necronomicon", "Unaussprechlichen Kulten"],
                    "rituals": {
                        "summoning": ["Lesser", "Greater"],
                        "banishment": "Elder Sign"
                    },
                    "power": 13
                }
                """);
        val result           = (ArrayValue) recursiveWildcardStep(complexStructure);
        assertThat(result).hasSize(9).contains(json("[\"Necronomicon\", \"Unaussprechlichen Kulten\"]"))
                .contains(json("\"Necronomicon\"")).contains(json("\"Unaussprechlichen Kulten\""))
                .contains(json("{\"summoning\": [\"Lesser\", \"Greater\"], \"banishment\": \"Elder Sign\"}"))
                .contains(json("[\"Lesser\", \"Greater\"]")).contains(json("\"Lesser\"")).contains(json("\"Greater\""))
                .contains(json("\"Elder Sign\"")).contains(json("13"));
    }

    @Test
    void recursiveWildcardStepReturnsEmptyForEmptyContainers() {
        assertThat(recursiveWildcardStep(EMPTY_TOME)).isEqualTo(EMPTY_TOME);
        assertThat(recursiveWildcardStep(EMPTY_GRIMOIRE)).isEqualTo(EMPTY_TOME);
    }

    @Test
    void recursiveWildcardStepIncludesNullAndUndefinedValues() {
        val withSpecialValues = ObjectValue.builder().put("void", json("null")).put("madness", Value.UNDEFINED)
                .put("real", json("\"Cthulhu\"")).build();
        val result            = (ArrayValue) recursiveWildcardStep(withSpecialValues);
        assertThat(result).hasSize(3).contains(json("null")).contains(Value.UNDEFINED).contains(json("\"Cthulhu\""));
    }

    @Test
    void recursiveWildcardStepIncludesErrorValues() {
        val builder = ArrayValue.builder();
        builder.add(json("\"Elder Sign\""));
        builder.add(Value.error("The void gazes back"));
        builder.add(json("\"Ward\""));
        val withError = builder.build();

        val result = (ArrayValue) recursiveWildcardStep(withError);
        assertThat(result).hasSize(3).contains(json("\"Elder Sign\"")).contains(json("\"Ward\""));
        assertThat(result.get(1)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void recursiveWildcardStepTraversesDepthFirst() {
        val structure = json("""
                [
                    "first",
                    ["nested-first", "nested-second"],
                    "second"
                ]
                """);
        val result    = (ArrayValue) recursiveWildcardStep(structure);
        assertThat(result).hasSize(5);
        assertThat(result.get(0)).isEqualTo(json("\"first\""));
        assertThat(result.get(1)).isEqualTo(json("[\"nested-first\", \"nested-second\"]"));
        assertThat(result.get(2)).isEqualTo(json("\"nested-first\""));
        assertThat(result.get(3)).isEqualTo(json("\"nested-second\""));
        assertThat(result.get(4)).isEqualTo(json("\"second\""));
    }

    @Test
    void recursiveWildcardStepMatchesIntegrationTestBehavior() {
        val structure = json("""
                {
                    "key": "value1",
                    "array1": [{"key": "value2"}, {"key": "value3"}],
                    "array2": [1, 2, 3, 4, 5]
                }
                """);
        val result    = (ArrayValue) recursiveWildcardStep(structure);
        // Verify all expected elements are present (order may vary by insertion order)
        assertThat(result).hasSize(12).contains(json("\"value1\""))
                .contains(json("[{\"key\":\"value2\"},{\"key\":\"value3\"}]")).contains(json("{\"key\":\"value2\"}"))
                .contains(json("\"value2\"")).contains(json("{\"key\":\"value3\"}")).contains(json("\"value3\""))
                .contains(json("[1,2,3,4,5]")).contains(json("1")).contains(json("2")).contains(json("3"))
                .contains(json("4")).contains(json("5"));
    }

    /**
     * Tests that recursion depth limit is enforced to prevent stack overflow.
     * <p>
     * WARNING: This test is intentionally slow (~2-5 seconds) because it:
     * 1. Recursively creates 501 nested arrays (expensive)
     * 2. Processes 500 levels before hitting the depth limit
     * <p>
     * The @Timeout prevents indefinite hangs if the depth check mechanism fails.
     * We use boolean instanceof check instead of AssertJ's isInstanceOf() to avoid
     * inspection overhead on deeply nested structures.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recursiveWildcardStepHandlesMaxDepth() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val abyssalDepth = createDeeplyNestedArray(501);
        val result       = recursiveWildcardStep(abyssalDepth);

        // Use boolean check to avoid AssertJ inspection overhead
        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(result.toString()).contains("Maximum nesting depth exceeded");
    }

    // ========== sliceArray Tests ==========

    @ParameterizedTest(name = "[{index}] slice: {3}")
    @CsvSource({ "0, 3, 1, '[0, 1, 2]', 'three signs from start'", "1, 4, 1, '[1, 2, 3]', 'middle three signs'",
            "0, 10, 2, '[0, 2, 4, 6, 8]', 'every second sign'", "0, 10, 3, '[0, 3, 6, 9]', 'every third sign'",
            ",, 1, '[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]', 'all signs default'",
            "2,, 1, '[2, 3, 4, 5, 6, 7, 8, 9]', 'from third onward'", ", 5, 1, '[0, 1, 2, 3, 4]', 'first five signs'",
            "-3,, 1, '[7, 8, 9]', 'last three signs'", ", -2, 1, '[0, 1, 2, 3, 4, 5, 6, 7]', 'all but last two'",
            "7, -1, 1, '[7, 8]', 'from seventh to penultimate'" })
    void sliceArrayForwardCases(BigDecimal start, BigDecimal end, BigDecimal step, String expectedJson,
            String description) {
        assertThat(sliceArray(ELDER_SIGNS, start, end, step)).isEqualTo(json(expectedJson));
    }

    @ParameterizedTest(name = "[{index}] SAPL negative step: {3}")
    @CsvSource({ ",, -1, '[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]', 'step -1 yields all signs'",
            ",, -3, '[1, 4, 7]', 'step -3 pattern'", "1, 5, -1, '[1, 2, 3, 4]', 'negative step in range'",
            "-2, 6, -1, '[]', 'reversed range yields void'", "-2, -5, -1, '[]', 'negative reversed range'" })
    void sliceArraySAPLNegativeStepSemantics(BigDecimal start, BigDecimal end, BigDecimal step, String expectedJson,
            String description) {
        assertThat(sliceArray(ELDER_SIGNS, start, end, step)).isEqualTo(json(expectedJson));
    }

    @Test
    void sliceArrayReturnsErrorForNonArray() {
        val result = sliceArray(CULTIST_RECORD, BigDecimal.valueOf(0), BigDecimal.valueOf(3), BigDecimal.valueOf(1));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an Array");
    }

    @Test
    void sliceArrayReturnsErrorForZeroStep() {
        val result = sliceArray(ELDER_SIGNS, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Step must not be zero");
    }

    @Test
    void sliceArrayHandlesEmptyArray() {
        assertThat(sliceArray(EMPTY_TOME, null, null, null)).isEqualTo(EMPTY_TOME);
    }

    // ========== indexUnion Tests ==========

    @Test
    void indexUnionSelectsMultipleIndices() {
        val array = (ArrayValue) indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(0, 2, 4));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(json("\"Al Azif\""));
        assertThat(array.get(1)).isEqualTo(json("\"Rites of Yog-Sothoth\""));
        assertThat(array.get(2)).isEqualTo(json("\"The Key and the Gate\""));
    }

    @Test
    void indexUnionPreservesArrayOrder() {
        val array = (ArrayValue) indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(4, 1, 3, 0));
        assertThat(array.getFirst()).isEqualTo(json("\"Al Azif\""));
        assertThat(array.get(1)).isEqualTo(json("\"Cultus Maleficarum\""));
        assertThat(array.get(2)).isEqualTo(json("\"Forbidden Summonings\""));
        assertThat(array.get(3)).isEqualTo(json("\"The Key and the Gate\""));
    }

    @Test
    void indexUnionDeduplicatesIndices() {
        val array = (ArrayValue) indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(1, 2, 1, 2, 1));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(json("\"Cultus Maleficarum\""));
        assertThat(array.get(1)).isEqualTo(json("\"Rites of Yog-Sothoth\""));
    }

    @Test
    void indexUnionHandlesNegativeIndices() {
        val array = (ArrayValue) indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(-1, 0, -2));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(json("\"Al Azif\""));
        assertThat(array.get(1)).isEqualTo(json("\"Forbidden Summonings\""));
        assertThat(array.get(2)).isEqualTo(json("\"The Key and the Gate\""));
    }

    @Test
    void indexUnionHandlesMixedPositiveNegativeIndices() {
        val array = (ArrayValue) indexUnion(ELDER_SIGNS, bigDecimals(3, -2, 1));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(json("1"));
        assertThat(array.get(1)).isEqualTo(json("3"));
        assertThat(array.get(2)).isEqualTo(json("8"));
    }

    @Test
    void indexUnionReturnsEmptyForEmptyIndices() {
        assertThat(indexUnion(NECRONOMICON_CHAPTERS, bigDecimals())).isEqualTo(EMPTY_TOME);
    }

    @Test
    void indexUnionReturnsErrorWithOriginalIndexForPositiveOutOfBounds() {
        val result = indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(1, 10, 2));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Index 10 out of bounds");
    }

    @Test
    void indexUnionReturnsErrorWithOriginalIndexForNegativeOutOfBounds() {
        val result = indexUnion(NECRONOMICON_CHAPTERS, bigDecimals(1, -10, 2));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Index -10 out of bounds");
    }

    @Test
    void indexUnionReturnsErrorForNonArray() {
        val result = indexUnion(CULTIST_RECORD, bigDecimals(0));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to arrays");
    }

    // ========== keyUnion Tests ==========

    @Test
    void attributeUnionSelectsMultipleKeys() {
        val array = (ArrayValue) attributeUnion(CULTIST_RECORD, List.of("name", "sanity"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(json("\"Wilbur Whateley\""));
        assertThat(array.get(1)).isEqualTo(json("12"));
    }

    @Test
    void attributeUnionPreservesObjectInsertionOrder() {
        val eldritchLocations = ObjectValue.builder().put("yuggoth", json("\"Pluto\""))
                .put("rlyeh", json("\"Pacific\"")).put("kadath", json("\"Dreamlands\"")).build();
        val array             = (ArrayValue) attributeUnion(eldritchLocations, List.of("kadath", "yuggoth", "rlyeh"));
        assertThat(array.getFirst()).isEqualTo(json("\"Pluto\""));
        assertThat(array.get(1)).isEqualTo(json("\"Pacific\""));
        assertThat(array.get(2)).isEqualTo(json("\"Dreamlands\""));
    }

    @Test
    void attributeUnionDeduplicatesKeys() {
        val array = (ArrayValue) attributeUnion(CULTIST_RECORD,
                List.of("name", "ritualKnowledge", "name", "ritualKnowledge", "name"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(json("\"Wilbur Whateley\""));
        assertThat(array.get(1)).isEqualTo(json("85"));
    }

    @Test
    void attributeUnionSkipsMissingKeys() {
        val array = (ArrayValue) attributeUnion(CULTIST_RECORD,
                List.of("name", "unknownOldOne", "sanity", "forbiddenKnowledge"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(json("\"Wilbur Whateley\""));
        assertThat(array.get(1)).isEqualTo(json("12"));
    }

    @Test
    void attributeUnionIncludesErrorValues() {
        val builder = ObjectValue.builder();
        builder.put("readable", json("\"Elder Sign\""));
        builder.put("corrupted", Value.error("Maddening whispers beyond comprehension"));
        builder.put("legible", json("\"Ward\""));
        val withError = builder.build();

        val array = (ArrayValue) attributeUnion(withError, List.of("readable", "corrupted", "legible"));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(json("\"Elder Sign\""));
        assertThat(array.get(1)).isInstanceOf(ErrorValue.class);
        assertThat(array.get(2)).isEqualTo(json("\"Ward\""));
    }

    @Test
    void attributeUnionReturnsEmptyForEmptyKeys() {
        assertThat(attributeUnion(CULTIST_RECORD, List.of())).isEqualTo(EMPTY_TOME);
    }

    @Test
    void attributeUnionReturnsEmptyForAllMissingKeys() {
        assertThat(attributeUnion(CULTIST_RECORD, List.of("azathoth", "nyarlathotep", "yogSothoth")))
                .isEqualTo(EMPTY_TOME);
    }

    @Test
    void attributeUnionEarlyExitsAfterFindingAllKeys() {
        val extensiveGrimoire = ObjectValue.builder().put("chant1", json("\"Ia\"")).put("chant2", json("\"Cthulhu\""))
                .put("chant3", json("\"fhtagn\"")).put("chant4", json("\"Ph'nglui\""))
                .put("chant5", json("\"mglw'nafh\"")).put("chant6", json("\"R'lyeh\""))
                .put("chant7", json("\"wgah'nagl\"")).put("chant8", json("\"fhtagn\"")).put("chant9", json("\"n'gha\""))
                .put("chant10", json("\"ghaa\"")).build();
        val array             = (ArrayValue) attributeUnion(extensiveGrimoire, List.of("chant1", "chant2"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(json("\"Ia\""));
        assertThat(array.get(1)).isEqualTo(json("\"Cthulhu\""));
    }

    @Test
    void attributeUnionReturnsErrorForNonObject() {
        val result = attributeUnion(NECRONOMICON_CHAPTERS, List.of("name"));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to objects");
    }

    // ========== Helper Methods ==========

    private static List<BigDecimal> bigDecimals(Integer... values) {
        return Stream.of(values).map(BigDecimal::valueOf).toList();
    }

    private ObjectValue createDeeplyNestedObject(int depth) {
        if (depth == 0) {
            return json("{\"key\": \"At the threshold of madness\"}");
        }
        val builder = ObjectValue.builder();
        builder.put("descent", createDeeplyNestedObject(depth - 1));
        return builder.build();
    }

    private ArrayValue createDeeplyNestedArray(int depth) {
        if (depth == 0) {
            return json("[\"The darkness speaks\"]");
        }
        val builder = ArrayValue.builder();
        builder.add(createDeeplyNestedArray(depth - 1));
        return builder.build();
    }
}
