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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
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

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.compiler.operators.StepOperators.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class StepOperatorsTests {

    private static final ArrayValue  NECRONOMICON_CHAPTERS = (ArrayValue) json(
            "[\"Al Azif\", \"Cultus Maleficarum\", \"Rites of Yog-Sothoth\", \"Forbidden Summonings\", \"The Key and the Gate\"]");
    private static final ArrayValue  ELDER_SIGNS           = (ArrayValue) json("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]");
    private static final ObjectValue CULTIST_RECORD        = ObjectValue.builder()
            .put("name", Value.of("Wilbur Whateley")).put("ritualKnowledge", Value.of(85)).put("sanity", Value.of(12))
            .build();

    private static final Value AL_AZIF              = Value.of("Al Azif");
    private static final Value CULTUS_MALEFICARUM   = Value.of("Cultus Maleficarum");
    private static final Value RITES_OF_YOG_SOTHOTH = Value.of("Rites of Yog-Sothoth");
    private static final Value FORBIDDEN_SUMMONINGS = Value.of("Forbidden Summonings");
    private static final Value THE_KEY_AND_THE_GATE = Value.of("The Key and the Gate");
    private static final Value WILBUR_WHATELEY      = Value.of("Wilbur Whateley");

    // ========== keyStep Tests ==========

    private static Stream<Arguments> keyStepCases() {
        return Stream.of(arguments(CULTIST_RECORD, "name", WILBUR_WHATELEY, "existing key"),
                arguments(CULTIST_RECORD, "ritualKnowledge", Value.of(85), "numeric value"),
                arguments(CULTIST_RECORD, "sanity", Value.of(12), "last key"),
                arguments(CULTIST_RECORD, "forbidden", Value.UNDEFINED, "missing key returns UNDEFINED"),
                arguments(Value.EMPTY_OBJECT, "any", Value.UNDEFINED, "empty object returns UNDEFINED"));
    }

    @ParameterizedTest(name = "[{index}] keyStep: {3}")
    @MethodSource("keyStepCases")
    void when_keyStep_then_returnsCorrectValue(ObjectValue object, String key, Value expected, String description) {
        assertThat(keyStep(null, object, key)).isEqualTo(expected);
    }

    @Test
    void when_keyStepOnNonObject_then_returnsError() {
        val result = keyStep(null, NECRONOMICON_CHAPTERS, "forbidden");
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an ObjectValue");
    }

    // ========== indexStep Tests ==========

    private static Stream<Arguments> indexStepValidCases() {
        return Stream.of(arguments(NECRONOMICON_CHAPTERS, 0, AL_AZIF, "first chapter"),
                arguments(NECRONOMICON_CHAPTERS, 2, RITES_OF_YOG_SOTHOTH, "middle chapter"),
                arguments(NECRONOMICON_CHAPTERS, 4, THE_KEY_AND_THE_GATE, "last chapter"),
                arguments(NECRONOMICON_CHAPTERS, -1, THE_KEY_AND_THE_GATE, "negative: last"),
                arguments(NECRONOMICON_CHAPTERS, -2, FORBIDDEN_SUMMONINGS, "negative: second-to-last"),
                arguments(NECRONOMICON_CHAPTERS, -5, AL_AZIF, "negative: first"),
                arguments(ELDER_SIGNS, 0, Value.of(0), "elder sign zero"),
                arguments(ELDER_SIGNS, 9, Value.of(9), "elder sign nine"));
    }

    @ParameterizedTest(name = "[{index}] indexStep valid: {3}")
    @MethodSource("indexStepValidCases")
    void when_indexStep_then_returnsCorrectElement(ArrayValue array, int index, Value expected, String description) {
        assertThat(indexStep(null, array, BigDecimal.valueOf(index))).isEqualTo(expected);
    }

    private static Stream<Arguments> indexStepErrorCases() {
        return Stream.of(arguments(NECRONOMICON_CHAPTERS, 5, "beyond known chapters"),
                arguments(NECRONOMICON_CHAPTERS, 10, "far beyond the veil"),
                arguments(NECRONOMICON_CHAPTERS, -6, "negative beyond start"),
                arguments(NECRONOMICON_CHAPTERS, -10, "far into the void"),
                arguments(Value.EMPTY_ARRAY, 0, "empty tome"), arguments(Value.EMPTY_ARRAY, -1, "empty tome negative"));
    }

    @ParameterizedTest(name = "[{index}] indexStep error: {2}")
    @MethodSource("indexStepErrorCases")
    void when_indexStepOutOfBounds_then_returnsError(ArrayValue array, int index, String description) {
        val result = indexStep(null, array, BigDecimal.valueOf(index));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("out of bounds");
    }

    @Test
    void when_indexStepOnNonArray_then_returnsError() {
        val result = indexStep(null, CULTIST_RECORD, BigDecimal.ZERO);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an Array");
    }

    // ========== wildcardStep Tests ==========

    @Test
    void when_wildcardStepOnArray_then_returnsArrayIdentity() {
        assertThat(wildcardStep(null, NECRONOMICON_CHAPTERS)).isSameAs(NECRONOMICON_CHAPTERS);
    }

    @Test
    void when_wildcardStepOnObject_then_convertsValuesToArray() {
        val result = wildcardStep(null, CULTIST_RECORD);
        assertThat(result).isInstanceOf(ArrayValue.class);
        val arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3).contains(WILBUR_WHATELEY, Value.of(85), Value.of(12));
    }

    @Test
    void when_wildcardStepOnObject_then_preservesInsertionOrder() {
        val cthulhuPriests = ObjectValue.builder().put("rlyeh", Value.of("Cthulhu"))
                .put("yuggoth", Value.of("Shub-Niggurath")).put("kadath", Value.of("Nyarlathotep")).build();
        val result         = (ArrayValue) wildcardStep(null, cthulhuPriests);
        assertThat(result.getFirst()).isEqualTo(Value.of("Cthulhu"));
        assertThat(result.get(1)).isEqualTo(Value.of("Shub-Niggurath"));
        assertThat(result.get(2)).isEqualTo(Value.of("Nyarlathotep"));
    }

    @Test
    void when_wildcardStepOnNonContainer_then_returnsError() {
        val result = wildcardStep(null, Value.of(42));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to arrays or objects");
    }

    // ========== recursiveKeyStep Tests ==========

    @Test
    void when_recursiveKeyStepWithShallowKey_then_findsKey() {
        val array = (ArrayValue) recursiveKeyStep(null, CULTIST_RECORD, "name");
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(WILBUR_WHATELEY);
    }

    @Test
    void when_recursiveKeyStepWithNestedKeys_then_findsAllKeys() {
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
        val array       = (ArrayValue) recursiveKeyStep(null, nestedCults, "power");
        assertThat(array).hasSize(3).contains(Value.of(100), Value.of(200), Value.of(50));
    }

    @Test
    void when_recursiveKeyStepWithMissingKey_then_returnsEmpty() {
        assertThat(recursiveKeyStep(null, CULTIST_RECORD, "unknownOldOne")).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_recursiveKeyStepOnArrayWithNestedObjects_then_searchesArrays() {
        val arrayOfObjects = json("""
                [
                    {"ritual": "summoning"},
                    {"ritual": "binding"},
                    [{"ritual": "banishment"}]
                ]
                """);
        val array          = (ArrayValue) recursiveKeyStep(null, arrayOfObjects, "ritual");
        assertThat(array).hasSize(3).contains(Value.of("summoning"), Value.of("binding"), Value.of("banishment"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void when_recursiveKeyStepExceedsMaxDepth_then_returnsError() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val abyssalDepth = createDeeplyNestedObject(501);
        val result       = recursiveKeyStep(null, abyssalDepth, "key");

        // Use boolean check to avoid AssertJ inspection overhead
        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(result.toString()).contains("Maximum nesting depth exceeded");
    }

    // ========== recursiveIndexStep Tests ==========

    @Test
    void when_recursiveIndexStepWithShallowIndex_then_findsIndex() {
        val array = (ArrayValue) recursiveIndexStep(null, NECRONOMICON_CHAPTERS, BigDecimal.valueOf(0));
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(AL_AZIF);
    }

    @Test
    void when_recursiveIndexStepWithNestedIndices_then_findsAllIndices() {
        val nestedChants = json("""
                [
                    ["Ia! Ia!", "Ph'nglui"],
                    ["Cthulhu", "R'lyeh"],
                    "fhtagn"
                ]
                """);
        val array        = (ArrayValue) recursiveIndexStep(null, nestedChants, BigDecimal.valueOf(0));
        assertThat(array).hasSize(3).contains(Value.of("Ia! Ia!"), Value.of("Cthulhu"),
                json("[\"Ia! Ia!\", \"Ph'nglui\"]"));
    }

    @Test
    void when_recursiveIndexStepWithNegativeIndex_then_handlesNegativeIndex() {
        val rituals = json("""
                [
                    ["Summoning", "Binding", "Banishment"],
                    ["Lesser", "Greater"]
                ]
                """);
        val array   = (ArrayValue) recursiveIndexStep(null, rituals, BigDecimal.valueOf(-1));
        assertThat(array).hasSize(3).contains(Value.of("Banishment"), Value.of("Greater"),
                json("[\"Lesser\", \"Greater\"]"));
    }

    @Test
    void when_recursiveIndexStepWithOutOfBoundsIndices_then_skipsOutOfBounds() {
        val unevenDepths = json("""
                [
                    ["shallow"],
                    ["deeper", "still", "descending"]
                ]
                """);
        val array        = (ArrayValue) recursiveIndexStep(null, unevenDepths, BigDecimal.valueOf(2));
        assertThat(array).hasSize(1);
        assertThat(array.getFirst()).isEqualTo(Value.of("descending"));
    }

    @Test
    void when_recursiveIndexStepOnObjectWithNestedArrays_then_searchesObjectValues() {
        val objectWithArrays = json("""
                {
                    "incantations": ["Ia", "Cthulhu", "fhtagn"],
                    "nested": {
                        "chants": ["Ph'nglui", "mglw'nafh"]
                    }
                }
                """);
        val array            = (ArrayValue) recursiveIndexStep(null, objectWithArrays, BigDecimal.valueOf(1));
        assertThat(array).hasSize(2).contains(Value.of("Cthulhu"), Value.of("mglw'nafh"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void when_recursiveIndexStepExceedsMaxDepth_then_returnsError() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val endlessVoid = createDeeplyNestedArray(501);
        val result      = recursiveIndexStep(null, endlessVoid, BigDecimal.valueOf(0));

        // Use boolean check to avoid AssertJ inspection overhead
        assertThat(result instanceof ErrorValue).isTrue();
        assertThat(result.toString()).contains("Maximum nesting depth exceeded");
    }

    // ========== recursiveWildcardStep Tests ==========

    @Test
    void when_recursiveWildcardStepOnPrimitives_then_returnsEmpty() {
        assertThat(recursiveWildcardStep(null, Value.of(42))).isEqualTo(Value.EMPTY_ARRAY);
        assertThat(recursiveWildcardStep(null, Value.of("Azathoth"))).isEqualTo(Value.EMPTY_ARRAY);
        assertThat(recursiveWildcardStep(null, Value.TRUE)).isEqualTo(Value.EMPTY_ARRAY);
        assertThat(recursiveWildcardStep(null, Value.NULL)).isEqualTo(Value.EMPTY_ARRAY);
        assertThat(recursiveWildcardStep(null, Value.UNDEFINED)).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_recursiveWildcardStepOnArray_then_collectsAllElements() {
        val result = (ArrayValue) recursiveWildcardStep(null, NECRONOMICON_CHAPTERS);
        assertThat(result).hasSize(5).contains(AL_AZIF, CULTUS_MALEFICARUM, RITES_OF_YOG_SOTHOTH, FORBIDDEN_SUMMONINGS,
                THE_KEY_AND_THE_GATE);
    }

    @Test
    void when_recursiveWildcardStepOnObject_then_collectsAllValues() {
        val result = (ArrayValue) recursiveWildcardStep(null, CULTIST_RECORD);
        assertThat(result).hasSize(3).contains(WILBUR_WHATELEY, Value.of(85), Value.of(12));
    }

    @Test
    void when_recursiveWildcardStepOnNestedArrays_then_collectsNestedElements() {
        val nestedChants = json("""
                [
                    ["Ia! Ia!", "Ph'nglui"],
                    ["Cthulhu", "R'lyeh"],
                    "fhtagn"
                ]
                """);
        val result       = (ArrayValue) recursiveWildcardStep(null, nestedChants);
        assertThat(result).hasSize(7).contains(json("[\"Ia! Ia!\", \"Ph'nglui\"]"), Value.of("Ia! Ia!"),
                Value.of("Ph'nglui"), json("[\"Cthulhu\", \"R'lyeh\"]"), Value.of("Cthulhu"), Value.of("R'lyeh"),
                Value.of("fhtagn"));
    }

    @Test
    void when_recursiveWildcardStepOnNestedObjects_then_collectsNestedValues() {
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
        val result      = (ArrayValue) recursiveWildcardStep(null, nestedCults);
        assertThat(result).hasSize(7).contains(json("{\"name\": \"Esoteric Order\", \"location\": \"Arkham\"}"),
                Value.of("Esoteric Order"), Value.of("Arkham"),
                json("{\"highPriest\": \"Cthulhu\", \"forbidden\": true}"), Value.of("Cthulhu"), Value.TRUE,
                Value.of(666));
    }

    @Test
    void when_recursiveWildcardStepOnMixedNested_then_collectsAllStructures() {
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
        val result           = (ArrayValue) recursiveWildcardStep(null, complexStructure);
        assertThat(result).hasSize(9).contains(json("[\"Necronomicon\", \"Unaussprechlichen Kulten\"]"),
                Value.of("Necronomicon"), Value.of("Unaussprechlichen Kulten"),
                json("{\"summoning\": [\"Lesser\", \"Greater\"], \"banishment\": \"Elder Sign\"}"),
                json("[\"Lesser\", \"Greater\"]"), Value.of("Lesser"), Value.of("Greater"), Value.of("Elder Sign"),
                Value.of(13));
    }

    @Test
    void when_recursiveWildcardStepOnEmptyContainers_then_returnsEmpty() {
        assertThat(recursiveWildcardStep(null, Value.EMPTY_ARRAY)).isEqualTo(Value.EMPTY_ARRAY);
        assertThat(recursiveWildcardStep(null, Value.EMPTY_OBJECT)).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_recursiveWildcardStepWithNullAndUndefined_then_includesSpecialValues() {
        val withSpecialValues = ObjectValue.builder().put("void", Value.NULL).put("madness", Value.UNDEFINED)
                .put("real", Value.of("Cthulhu")).build();
        val result            = (ArrayValue) recursiveWildcardStep(null, withSpecialValues);
        assertThat(result).hasSize(3).contains(Value.NULL, Value.UNDEFINED, Value.of("Cthulhu"));
    }

    @Test
    void when_recursiveWildcardStepWithErrorValues_then_includesErrors() {
        val builder = ArrayValue.builder();
        builder.add(Value.of("Elder Sign"));
        builder.add(Value.error("The void gazes back"));
        builder.add(Value.of("Ward"));
        val withError = builder.build();

        val result = (ArrayValue) recursiveWildcardStep(null, withError);
        assertThat(result).hasSize(3).contains(Value.of("Elder Sign"), Value.of("Ward"));
        assertThat(result.get(1)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_recursiveWildcardStep_then_traversesDepthFirst() {
        val structure = json("""
                [
                    "first",
                    ["nested-first", "nested-second"],
                    "second"
                ]
                """);
        val result    = (ArrayValue) recursiveWildcardStep(null, structure);
        assertThat(result).hasSize(5);
        assertThat(result.get(0)).isEqualTo(Value.of("first"));
        assertThat(result.get(1)).isEqualTo(json("[\"nested-first\", \"nested-second\"]"));
        assertThat(result.get(2)).isEqualTo(Value.of("nested-first"));
        assertThat(result.get(3)).isEqualTo(Value.of("nested-second"));
        assertThat(result.get(4)).isEqualTo(Value.of("second"));
    }

    @Test
    void when_recursiveWildcardStep_then_matchesIntegrationTestBehavior() {
        val structure = json("""
                {
                    "key": "value1",
                    "array1": [{"key": "value2"}, {"key": "value3"}],
                    "array2": [1, 2, 3, 4, 5]
                }
                """);
        val result    = (ArrayValue) recursiveWildcardStep(null, structure);
        // Verify all expected elements are present (order may vary by insertion order)
        assertThat(result).hasSize(12).contains(Value.of("value1"), json("[{\"key\":\"value2\"},{\"key\":\"value3\"}]"),
                json("{\"key\":\"value2\"}"), Value.of("value2"), json("{\"key\":\"value3\"}"), Value.of("value3"),
                json("[1,2,3,4,5]"), Value.of(1), Value.of(2), Value.of(3), Value.of(4), Value.of(5));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void when_recursiveWildcardStepExceedsMaxDepth_then_returnsError() {
        // Creates 501 levels which will process 500 before hitting depth limit
        val abyssalDepth = createDeeplyNestedArray(501);
        val result       = recursiveWildcardStep(null, abyssalDepth);

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
    void when_sliceArrayForward_then_returnsSlice(BigDecimal start, BigDecimal end, BigDecimal step,
            String expectedJson, String description) {
        assertThat(sliceArray(null, ELDER_SIGNS, start, end, step)).isEqualTo(json(expectedJson));
    }

    @ParameterizedTest(name = "[{index}] SAPL negative step: {3}")
    @CsvSource({ ",, -1, '[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]', 'step -1 yields all signs'",
            ",, -3, '[1, 4, 7]', 'step -3 pattern'", "1, 5, -1, '[1, 2, 3, 4]', 'negative step in range'",
            "-2, 6, -1, '[]', 'reversed range yields void'", "-2, -5, -1, '[]', 'negative reversed range'" })
    void when_sliceArrayNegativeStep_then_followsSaplSemantics(BigDecimal start, BigDecimal end, BigDecimal step,
            String expectedJson, String description) {
        assertThat(sliceArray(null, ELDER_SIGNS, start, end, step)).isEqualTo(json(expectedJson));
    }

    @Test
    void when_sliceArrayOnNonArray_then_returnsError() {
        val result = sliceArray(null, CULTIST_RECORD, BigDecimal.valueOf(0), BigDecimal.valueOf(3),
                BigDecimal.valueOf(1));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Expected an Array");
    }

    @Test
    void when_sliceArrayWithZeroStep_then_returnsError() {
        val result = sliceArray(null, ELDER_SIGNS, BigDecimal.valueOf(0), BigDecimal.valueOf(5), BigDecimal.valueOf(0));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Step must not be zero");
    }

    @Test
    void when_sliceArrayOnEmpty_then_returnsEmpty() {
        assertThat(sliceArray(null, Value.EMPTY_ARRAY, null, null, null)).isEqualTo(Value.EMPTY_ARRAY);
    }

    // ========== indexUnion Tests ==========

    @Test
    void when_indexUnionWithMultipleIndices_then_selectsMultiple() {
        val array = (ArrayValue) indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(0, 2, 4));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(AL_AZIF);
        assertThat(array.get(1)).isEqualTo(RITES_OF_YOG_SOTHOTH);
        assertThat(array.get(2)).isEqualTo(THE_KEY_AND_THE_GATE);
    }

    @Test
    void when_indexUnion_then_preservesArrayOrder() {
        val array = (ArrayValue) indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(4, 1, 3, 0));
        assertThat(array.getFirst()).isEqualTo(AL_AZIF);
        assertThat(array.get(1)).isEqualTo(CULTUS_MALEFICARUM);
        assertThat(array.get(2)).isEqualTo(FORBIDDEN_SUMMONINGS);
        assertThat(array.get(3)).isEqualTo(THE_KEY_AND_THE_GATE);
    }

    @Test
    void when_indexUnionWithDuplicates_then_deduplicatesIndices() {
        val array = (ArrayValue) indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(1, 2, 1, 2, 1));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(CULTUS_MALEFICARUM);
        assertThat(array.get(1)).isEqualTo(RITES_OF_YOG_SOTHOTH);
    }

    @Test
    void when_indexUnionWithNegativeIndices_then_handlesNegatives() {
        val array = (ArrayValue) indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(-1, 0, -2));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(AL_AZIF);
        assertThat(array.get(1)).isEqualTo(FORBIDDEN_SUMMONINGS);
        assertThat(array.get(2)).isEqualTo(THE_KEY_AND_THE_GATE);
    }

    @Test
    void when_indexUnionWithMixedIndices_then_handlesMixedPositiveNegative() {
        val array = (ArrayValue) indexUnion(null, ELDER_SIGNS, bigDecimals(3, -2, 1));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(Value.of(1));
        assertThat(array.get(1)).isEqualTo(Value.of(3));
        assertThat(array.get(2)).isEqualTo(Value.of(8));
    }

    @Test
    void when_indexUnionWithEmptyIndices_then_returnsEmpty() {
        assertThat(indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals())).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_indexUnionPositiveOutOfBounds_then_returnsErrorWithOriginalIndex() {
        val result = indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(1, 10, 2));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Index 10 out of bounds");
    }

    @Test
    void when_indexUnionNegativeOutOfBounds_then_returnsErrorWithOriginalIndex() {
        val result = indexUnion(null, NECRONOMICON_CHAPTERS, bigDecimals(1, -10, 2));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("Index -10 out of bounds");
    }

    @Test
    void when_indexUnionOnNonArray_then_returnsError() {
        val result = indexUnion(null, CULTIST_RECORD, bigDecimals(0));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to arrays");
    }

    // ========== keyUnion Tests ==========

    @Test
    void when_attributeUnionWithMultipleKeys_then_selectsMultiple() {
        val array = (ArrayValue) attributeUnion(null, CULTIST_RECORD, List.of("name", "sanity"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(WILBUR_WHATELEY);
        assertThat(array.get(1)).isEqualTo(Value.of(12));
    }

    @Test
    void when_attributeUnion_then_preservesObjectInsertionOrder() {
        val eldritchLocations = ObjectValue.builder().put("yuggoth", Value.of("Pluto"))
                .put("rlyeh", Value.of("Pacific")).put("kadath", Value.of("Dreamlands")).build();
        val array             = (ArrayValue) attributeUnion(null, eldritchLocations,
                List.of("kadath", "yuggoth", "rlyeh"));
        assertThat(array.getFirst()).isEqualTo(Value.of("Pluto"));
        assertThat(array.get(1)).isEqualTo(Value.of("Pacific"));
        assertThat(array.get(2)).isEqualTo(Value.of("Dreamlands"));
    }

    @Test
    void when_attributeUnionWithDuplicates_then_deduplicatesKeys() {
        val array = (ArrayValue) attributeUnion(null, CULTIST_RECORD,
                List.of("name", "ritualKnowledge", "name", "ritualKnowledge", "name"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(WILBUR_WHATELEY);
        assertThat(array.get(1)).isEqualTo(Value.of(85));
    }

    @Test
    void when_attributeUnionWithMissingKeys_then_skipsMissingKeys() {
        val array = (ArrayValue) attributeUnion(null, CULTIST_RECORD,
                List.of("name", "unknownOldOne", "sanity", "forbiddenKnowledge"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(WILBUR_WHATELEY);
        assertThat(array.get(1)).isEqualTo(Value.of(12));
    }

    @Test
    void when_attributeUnionWithErrorValues_then_includesErrors() {
        val builder = ObjectValue.builder();
        builder.put("readable", Value.of("Elder Sign"));
        builder.put("corrupted", Value.error("Maddening whispers beyond comprehension"));
        builder.put("legible", Value.of("Ward"));
        val withError = builder.build();

        val array = (ArrayValue) attributeUnion(null, withError, List.of("readable", "corrupted", "legible"));
        assertThat(array).hasSize(3);
        assertThat(array.getFirst()).isEqualTo(Value.of("Elder Sign"));
        assertThat(array.get(1)).isInstanceOf(ErrorValue.class);
        assertThat(array.get(2)).isEqualTo(Value.of("Ward"));
    }

    @Test
    void when_attributeUnionWithEmptyKeys_then_returnsEmpty() {
        assertThat(attributeUnion(null, CULTIST_RECORD, List.of())).isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_attributeUnionWithAllMissingKeys_then_returnsEmpty() {
        assertThat(attributeUnion(null, CULTIST_RECORD, List.of("azathoth", "nyarlathotep", "yogSothoth")))
                .isEqualTo(Value.EMPTY_ARRAY);
    }

    @Test
    void when_attributeUnionFindsAllKeys_then_earlyExits() {
        val extensiveGrimoire = ObjectValue.builder().put("chant1", Value.of("Ia")).put("chant2", Value.of("Cthulhu"))
                .put("chant3", Value.of("fhtagn")).put("chant4", Value.of("Ph'nglui"))
                .put("chant5", Value.of("mglw'nafh")).put("chant6", Value.of("R'lyeh"))
                .put("chant7", Value.of("wgah'nagl")).put("chant8", Value.of("fhtagn")).put("chant9", Value.of("n'gha"))
                .put("chant10", Value.of("ghaa")).build();
        val array             = (ArrayValue) attributeUnion(null, extensiveGrimoire, List.of("chant1", "chant2"));
        assertThat(array).hasSize(2);
        assertThat(array.getFirst()).isEqualTo(Value.of("Ia"));
        assertThat(array.get(1)).isEqualTo(Value.of("Cthulhu"));
    }

    @Test
    void when_attributeUnionOnNonObject_then_returnsError() {
        val result = attributeUnion(null, NECRONOMICON_CHAPTERS, List.of("name"));
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(result.toString()).contains("can only be applied to objects");
    }

    // ========== Helper Methods ==========

    private static List<BigDecimal> bigDecimals(Integer... values) {
        return Stream.of(values).map(BigDecimal::valueOf).toList();
    }

    private ObjectValue createDeeplyNestedObject(int depth) {
        if (depth == 0) {
            return (ObjectValue) json("{\"key\": \"At the threshold of madness\"}");
        }
        val builder = ObjectValue.builder();
        builder.put("descent", createDeeplyNestedObject(depth - 1));
        return builder.build();
    }

    private ArrayValue createDeeplyNestedArray(int depth) {
        if (depth == 0) {
            return (ArrayValue) json("[\"The darkness speaks\"]");
        }
        val builder = ArrayValue.builder();
        builder.add(createDeeplyNestedArray(depth - 1));
        return builder.build();
    }
}
