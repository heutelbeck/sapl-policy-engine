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
package io.sapl.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.*;
import io.sapl.util.TestUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy filter and subtemplate tests ported from sapl-lang to verify compiler
 * compatibility.
 * <p>
 * These tests replicate the test cases from:
 * - ApplyFilteringSimpleTests.java
 * - ApplyFilteringExtendedTests.java
 * - DefaultSAPLInterpreterTransformationTests.java (subtemplate tests)
 * <p>
 * This ensures the compiler implementation maintains feature parity with the
 * interpreter.
 */
class LegacyFilterTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private static <T extends Value> T json(String jsonString) {
        val node  = MAPPER.readTree(jsonString);
        val value = ValueJsonMarshaller.fromJsonNode(node);
        return (T) value;
    }

    // =========================================================================
    // SIMPLE FILTER TESTS (from ApplyFilteringSimpleTests)
    // =========================================================================

    @Test
    void simpleFilter_filterPropagatesError() {
        var result = TestUtil.evaluate("(10/0) |- filter.remove");
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void simpleFilter_filterUndefined() {
        var result = TestUtil.evaluate("undefined |- filter.remove");
        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).contains("Filters cannot be applied to undefined values");
    }

    @Test
    void simpleFilter_removeNoEach() {
        var result = TestUtil.evaluate("{} |- filter.remove");
        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void simpleFilter_removeEachNoArray() {
        var result = TestUtil.evaluate("{} |- each filter.remove");
        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).contains("Cannot use 'each' keyword with non-array values");
    }

    @Test
    void simpleFilter_removeEachArray() {
        var result = TestUtil.evaluate("[null] |- each filter.remove");
        assertThat(result).isEqualTo(json("[]"));
    }

    @Test
    void simpleFilter_emptyStringNoEach() {
        var result = TestUtil.evaluate("[] |- mock.emptyString");
        assertThat(result).isEqualTo(Value.of(""));
    }

    @Test
    void simpleFilter_emptyStringEach() {
        var result = TestUtil.evaluate("[ null, 5 ] |- each mock.emptyString(null)");
        assertThat(result).isEqualTo(json("[\"\", \"\"]"));
    }

    // =========================================================================
    // EXTENDED FILTER TESTS (from ApplyFilteringExtendedTests)
    // =========================================================================

    @Test
    void extendedFilter_filterUndefined() {
        var result = TestUtil.evaluate("undefined |- { @.name : filter.remove }");
        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).contains("Filters cannot be applied to undefined values");
    }

    @Test
    void extendedFilter_filterError() {
        var result = TestUtil.evaluate("(10/0) |- { @.name : filter.remove }");
        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).contains("Division by zero");
    }

    // Note: Test for "|- { }" (empty filter statement) removed.
    // According to SAPL.xtext:252, FilterExtended requires at least one
    // FilterStatement.
    // The sapl-compiler correctly rejects this as a parse error.

    @Test
    void extendedFilter_removeKeyStepFromObject() {
        var result = TestUtil.evaluate("{ \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" } "
                + "|- { @.name : filter.remove }");
        assertThat(result).isEqualTo(json("{\"job\":\"recreational surgeon\"}"));
    }

    @Test
    void extendedFilter_removeTwoKeyStepsDown() {
        var result = TestUtil.evaluate(
                "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : 1000000 } } "
                        + "|- { @.job.wage : filter.remove }");
        assertThat(result)
                .isEqualTo(json("{\"name\":\"Jack the Ripper\",\"job\":{\"title\":\"recreational surgeon\"}}"));
    }

    @Test
    void extendedFilter_removeThreeKeyStepsDown() {
        var result = TestUtil.evaluate(
                "{ \"name\" : \"Jack the Ripper\", \"job\" : { \"title\" : \"recreational surgeon\", \"wage\" : { \"monthly\" : 1000000, \"currency\" : \"GBP\"} } } "
                        + "|- { @.job.wage.monthly : filter.remove }");
        assertThat(result).isEqualTo(json(
                "{\"name\":\"Jack the Ripper\",\"job\":{\"title\":\"recreational surgeon\",\"wage\":{\"currency\":\"GBP\"}}}"));
    }

    @Test
    void extendedFilter_removeKeyStepFromArray() {
        var result = TestUtil.evaluate(
                "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
                        + "|- { @.name : filter.remove }");
        assertThat(result)
                .isEqualTo(json("[{\"job\":\"recreational surgeon\"},{\"job\":\"professional perforator\"}]"));
    }

    @Test
    void extendedFilter_removeNoStepsEach() {
        var result = TestUtil.evaluate("[ null, true ] |- { each @ : filter.remove }");
        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void extendedFilter_removeNoStepsNoEach() {
        var result = TestUtil.evaluate("{} |- { @ : filter.remove }");
        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void extendedFilter_emptyStringNoStepsNoEach() {
        var result = TestUtil.evaluate("[ null, true ] |- { @ : mock.emptyString }");
        assertThat(result).isEqualTo(json("\"\""));
    }

    @Test
    void extendedFilter_emptyStringNoStepsEach() {
        var result = TestUtil.evaluate("[ null, true ] |- { each @ : mock.emptyString }");
        assertThat(result).isEqualTo(json("[\"\", \"\"]"));
    }

    @Test
    void extendedFilter_emptyStringEachNoArray() {
        var result = TestUtil.evaluate("[ {}, true ] |- { each @[0] : mock.emptyString }");
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void extendedFilter_removeEachNoArray() {
        var result = TestUtil.evaluate("{} |- { each @ : filter.remove }");
        assertThat(result).isInstanceOf(ErrorValue.class);
        var error = (ErrorValue) result;
        assertThat(error.message()).containsIgnoringCase("each").containsIgnoringCase("non-array");
    }

    @Test
    void extendedFilter_removeResultArrayNoEach() {
        var result = TestUtil.evaluate("[ null, true ] |- { @[0] : filter.remove }");
        assertThat(result).isEqualTo(json("[true]"));
    }

    @Test
    void extendedFilter_blackenIndexInSelectedField() {
        var result = TestUtil.evaluate(
                "[ { \"name\" : \"Jack the Ripper\", \"job\" : \"recreational surgeon\" }, { \"name\" : \"Billy the Kid\", \"job\" : \"professional perforator\" } ] "
                        + "|- { @[0].job : filter.blacken }");
        assertThat(result).isEqualTo(json(
                "[{\"name\":\"Jack the Ripper\",\"job\":\"XXXXXXXXXXXXXXXXXXXX\"},{\"name\":\"Billy the Kid\",\"job\":\"professional perforator\"}]"));
    }

    @Test
    void extendedFilter_blackenResultArrayNoEach() {
        var result = TestUtil.evaluate("[ null, \"secret\", true ] |- { @[-2] : filter.blacken }");
        assertThat(result).isEqualTo(json("[null, \"XXXXXX\", true]"));
    }

    @Test
    void extendedFilter_removeArraySliceNegative() {
        var result = TestUtil.evaluate("[ 0, 1, 2, 3, 4, 5 ] |- { @[-2:] : filter.remove }");
        assertThat(result).isEqualTo(json("[0, 1, 2, 3]"));
    }

    @Test
    void extendedFilter_removeArraySlicePositive() {
        var result = TestUtil.evaluate("[ 0, 1, 2, 3, 4, 5 ] |- { @[2:4:1] : filter.remove }");
        assertThat(result).isEqualTo(json("[0, 1, 4, 5]"));
    }

    @Test
    void extendedFilter_removeArraySliceNegativeTo() {
        var result = TestUtil.evaluate("[ 1, 2, 3, 4, 5 ] |- { @[0:-2:2] : filter.remove }");
        assertThat(result).isEqualTo(json("[2, 4, 5]"));
    }

    @Test
    void extendedFilter_removeArraySliceNegativeStep() {
        var result = TestUtil.evaluate("[ 0, 1, 2, 3, 4, 5 ] |- { @[1:5:-2] : filter.remove }");
        assertThat(result).isEqualTo(json("[0, 2, 4, 5]"));
    }

    @Test
    void extendedFilter_removeAttributeUnionStep() {
        var result = TestUtil.evaluate(
                "{ \"a\" : 1, \"b\" : 2, \"c\" : 3, \"d\" : 4 } " + "|- { @[\"b\" , \"d\"] : filter.remove }");
        assertThat(result).isEqualTo(json("{\"a\":1, \"c\":3}"));
    }

    @Test
    void extendedFilter_removeArrayElementInAttributeUnionStep() {
        var result = TestUtil.evaluate("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } "
                + "|- { @[\"b\" , \"d\"][1] : filter.remove }");
        assertThat(result).isEqualTo(json("{\"a\":[0, 1, 2, 3],\"b\":[0, 2, 3],\"c\":[0, 1, 2, 3],\"d\":[0, 2, 3]}"));
    }

    @Test
    void extendedFilter_replaceWithEmptyStringInAttributeUnionStep() {
        var result = TestUtil.evaluate("{ \"a\" : [0,1,2,3], \"b\" : [0,1,2,3], \"c\" : [0,1,2,3], \"d\" : [0,1,2,3] } "
                + "|- { @[\"b\" , \"d\"][1] : mock.emptyString }");
        assertThat(result)
                .isEqualTo(json("{\"a\":[0, 1, 2, 3],\"b\":[0, \"\", 2, 3],\"c\":[0, 1, 2, 3],\"d\":[0, \"\", 2, 3]}"));
    }

    @Test
    void extendedFilter_removeIndexUnionStep() {
        var result = TestUtil.evaluate(
                "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] " + "|- { @[1,3] : filter.remove }");
        assertThat(result).isEqualTo(json("[[0, 1, 2, 3], [2, 1, 2, 3], [4, 1, 2, 3]]"));
    }

    @Test
    void extendedFilter_doubleRemoveIndexUnionStep() {
        var result = TestUtil.evaluate(
                "[ [0,1,2,3], [1,1,2,3], [2,1,2,3], [3,1,2,3], [4,1,2,3] ] " + "|- { @[1,3][2,1] : filter.remove }");
        assertThat(result).isEqualTo(json("[[0, 1, 2, 3], [1, 3], [2, 1, 2, 3], [3, 3], [4, 1, 2, 3]]"));
    }

    @Test
    void extendedFilter_replaceRecursiveKeyStep() {
        var result = TestUtil.evaluate(
                "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
                        + "|- { @..key : filter.blacken }");
        assertThat(result).isEqualTo(json(
                "{\"key\":\"XXXXXX\",\"array1\":[{\"key\":\"XXXXXX\"},{\"key\":\"XXXXXX\"}],\"array2\":[1, 2, 3, 4, 5]}"));
    }

    @Test
    void extendedFilter_multipleFilterStatements() {
        var result = TestUtil.evaluate(
                "{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ] } "
                        + "|- { @..[0] : filter.remove, @..key : filter.blacken, @.array2[-1] : filter.remove }");
        assertThat(result)
                .isEqualTo(json("{\"key\":\"XXXXXX\",\"array1\":[{\"key\":\"XXXXXX\"}],\"array2\":[2, 3, 4]}"));
    }

    // =========================================================================
    // SUBTEMPLATE TESTS (from DefaultSAPLInterpreterTransformationTests)
    // =========================================================================

    @Test
    void subtemplate_emptyArrayToObject() {
        var result = TestUtil.evaluate("[] :: { \"name\": \"foo\" }");
        assertThat(result).isEqualTo(json("[]"));
    }

    @Test
    void subtemplate_simpleObjectTransformation() {
        var result = TestUtil.evaluate("{ \"name\": \"Alice\", \"age\": 30 } :: { \"newName\": @.name }");
        assertThat(result).isEqualTo(json("{\"newName\":\"Alice\"}"));
    }

    @Test
    void subtemplate_arrayTransformation() {
        var result = TestUtil.evaluate("[ { \"name\": \"Alice\" }, { \"name\": \"Bob\" } ] :: { \"newName\": @.name }");
        assertThat(result).isEqualTo(json("[{\"newName\":\"Alice\"}, {\"newName\":\"Bob\"}]"));
    }

    @Test
    void subtemplate_withConditionFiltering() {
        var result = TestUtil.evaluate(
                "[ { \"key1\": 1, \"key2\": 2 }, { \"key1\": 3, \"key2\": 4 }, { \"key1\": 5, \"key2\": 6 } ] "
                        + "[?(@.key1 > 2)] :: { \"key20\": @.key2 }");
        assertThat(result).isEqualTo(json("[{\"key20\":4}, {\"key20\":6}]"));
    }

    @Test
    void subtemplate_nestedFieldAccess() {
        var result = TestUtil.evaluate("{ \"person\": { \"name\": \"Alice\", \"address\": { \"city\": \"Berlin\" } } } "
                + ":: { \"city\": @.person.address.city }");
        assertThat(result).isEqualTo(json("{\"city\":\"Berlin\"}"));
    }

    @Test
    void subtemplate_withArrayElementAccess() {
        var result = TestUtil
                .evaluate("{ \"items\": [10, 20, 30] } :: { \"firstItem\": @.items[0], \"lastItem\": @.items[-1] }");
        assertThat(result).isEqualTo(json("{\"firstItem\":10, \"lastItem\":30}"));
    }

    @Test
    void subtemplate_propagatesError() {
        var result = TestUtil.evaluate("(10/0) :: { \"name\": \"foo\" }");
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void subtemplate_propagatesUndefined() {
        var result = TestUtil.evaluate("undefined :: { \"name\": \"foo\" }");
        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void subtemplate_withWildcardStep() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3 } :: { \"values\": @.* }");
        assertThat(result).isEqualTo(json("{\"values\":[1, 2, 3]}"));
    }

    @Test
    void subtemplate_relativeContextPreservation() {
        // Subtemplate should maintain @ as relative context
        var result = TestUtil.evaluate("{ \"x\": 5, \"y\": 10 } :: { \"sum\": @.x, \"product\": @.y }");
        assertThat(result).isEqualTo(json("{\"sum\":5, \"product\":10}"));
    }

    // =========================================================================
    // COMBINED FILTER AND SUBTEMPLATE TESTS
    // =========================================================================

    // Note: Tests for chaining "|- ... :: ..." removed.
    // According to SAPL.xtext:121, BasicExpression can have EITHER filter OR
    // subtemplate, not both.
    // The grammar is: Basic (FILTER filter=FilterComponent | SUBTEMPLATE
    // subtemplate=BasicExpression)?
    // The "|" means OR, not AND. The sapl-compiler correctly rejects this as a
    // parse error.

    @Test
    void combined_subtemplateWithFilteredInput() {
        var result = TestUtil.evaluate(
                "[ { \"key1\": 1, \"key2\": \"a\" }, { \"key1\": 3, \"key2\": \"b\" }, { \"key1\": 5, \"key2\": \"c\" } ] "
                        + "[?(@.key1 > 2)] :: { \"value\": @.key2 }");
        assertThat(result).isEqualTo(json("[{\"value\":\"b\"}, {\"value\":\"c\"}]"));
    }
}
