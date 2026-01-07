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
package io.sapl.compiler;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.util.ExpressionTestUtil.evaluateExpression;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;

/**
 * Tests for path navigation algorithm in ExtendedFilterCompiler.
 * Uses actual SAPL expressions with |- syntax.
 * Current sketch replaces target with "***" marker instead of calling function.
 */
class PathNavigationTests {

    // Note: Empty path (@) is converted to SimpleFilter by parser, not
    // ExtendedFilter.
    // ExtendedFilter tests start with paths that have at least one element.

    // === KeyPath (@.key): navigate into object field ===

    @Test
    void keyPath_replacesDirectField() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @.a : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": 2}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyPath_replacesNestedField() {
        var result   = evaluateExpression("""
                {"outer": {"inner": "secret"}, "other": "visible"} |- { @.outer.inner : mock.func }
                """);
        var expected = json("""
                {"outer": {"inner": "***"}, "other": "visible"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyPath_preservesFieldOrder() {
        var result   = evaluateExpression("""
                {"first": 1, "second": 2, "third": 3} |- { @.second : mock.func }
                """);
        var expected = json("""
                {"first": 1, "second": "***", "third": 3}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === Blacklist semantics: type mismatches return unchanged ===

    @Test
    void keyPath_onNonObject_returnsUnchanged() {
        var result   = evaluateExpression("""
                "not an object" |- { @.a : mock.func }
                """);
        var expected = Value.of("not an object");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyPath_missingKey_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"a": 1} |- { @.nonexistent : mock.func }
                """);
        var expected = json("""
                {"a": 1}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyPath_nestedMissingKey_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"outer": {"a": 1}} |- { @.outer.nonexistent : mock.func }
                """);
        var expected = json("""
                {"outer": {"a": 1}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === IndexPath (@[n]): navigate into array element ===

    @Test
    void indexPath_replacesFirstElement() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[0] : mock.func }
                """);
        var expected = json("""
                ["***", 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_replacesMiddleElement() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[1] : mock.func }
                """);
        var expected = json("""
                [1, "***", 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_replacesLastElement() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[2] : mock.func }
                """);
        var expected = json("""
                [1, 2, "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_negativeIndex_fromEnd() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[-1] : mock.func }
                """);
        var expected = json("""
                [1, 2, "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_negativeIndex_secondFromEnd() {
        var result   = evaluateExpression("""
                [1, 2, 3, 4] |- { @[-2] : mock.func }
                """);
        var expected = json("""
                [1, 2, "***", 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === IndexPath blacklist semantics ===

    @Test
    void indexPath_onNonArray_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"a": 1} |- { @[0] : mock.func }
                """);
        var expected = json("""
                {"a": 1}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_outOfBounds_returnsUnchanged() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[10] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_negativeOutOfBounds_returnsUnchanged() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[-10] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === Combined paths: KeyPath + IndexPath ===

    @Test
    void keyThenIndex_replacesNestedArrayElement() {
        var result   = evaluateExpression("""
                {"items": [1, 2, 3]} |- { @.items[1] : mock.func }
                """);
        var expected = json("""
                {"items": [1, "***", 3]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexThenKey_replacesObjectInArray() {
        var result   = evaluateExpression("""
                [{"name": "alice"}, {"name": "bob"}] |- { @[1].name : mock.func }
                """);
        var expected = json("""
                [{"name": "alice"}, {"name": "***"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deepNesting_keyIndexKeyIndex() {
        var result   = evaluateExpression("""
                {"data": [{"values": [10, 20, 30]}]} |- { @.data[0].values[1] : mock.func }
                """);
        var expected = json("""
                {"data": [{"values": [10, "***", 30]}]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void combined_missingIntermediateKey_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"other": [1, 2]} |- { @.items[0] : mock.func }
                """);
        var expected = json("""
                {"other": [1, 2]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void combined_intermediateNotArray_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"items": "not an array"} |- { @.items[0] : mock.func }
                """);
        var expected = json("""
                {"items": "not an array"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === WildcardPath (@[*] / @.*): mark all children ===

    @Test
    void wildcardPath_marksAllArrayElements() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[*] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardPath_marksAllObjectValues() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @.* : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardPath_emptyArray_returnsEmptyArray() {
        var result   = evaluateExpression("""
                [] |- { @[*] : mock.func }
                """);
        var expected = json("[]");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardPath_emptyObject_returnsEmptyObject() {
        var result   = evaluateExpression("""
                {} |- { @.* : mock.func }
                """);
        var expected = json("{}");

        assertThat(result).isEqualTo(expected);
    }

    // === WildcardPath blacklist semantics ===

    @Test
    void wildcardPath_onScalar_returnsUnchanged() {
        var result   = evaluateExpression("""
                42 |- { @[*] : mock.func }
                """);
        var expected = Value.of(42);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardPath_onString_returnsUnchanged() {
        var result   = evaluateExpression("""
                "hello" |- { @.* : mock.func }
                """);
        var expected = Value.of("hello");

        assertThat(result).isEqualTo(expected);
    }

    // === WildcardPath + nested paths ===

    @Test
    void wildcardThenKey_marksFieldInAllObjects() {
        var result   = evaluateExpression("""
                [{"name": "alice"}, {"name": "bob"}] |- { @[*].name : mock.func }
                """);
        var expected = json("""
                [{"name": "***"}, {"name": "***"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyThenWildcard_marksAllInNestedArray() {
        var result   = evaluateExpression("""
                {"items": [1, 2, 3]} |- { @.items[*] : mock.func }
                """);
        var expected = json("""
                {"items": ["***", "***", "***"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenIndex_marksElementInAllArrays() {
        var result   = evaluateExpression("""
                [[1, 2], [3, 4], [5, 6]] |- { @[*][0] : mock.func }
                """);
        var expected = json("""
                [["***", 2], ["***", 4], ["***", 6]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenKey_blacklistSkipsMissingKeys() {
        var result   = evaluateExpression("""
                [{"name": "alice"}, {"other": "bob"}] |- { @[*].name : mock.func }
                """);
        var expected = json("""
                [{"name": "***"}, {"other": "bob"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenWildcard_marksNestedArrays() {
        var result   = evaluateExpression("""
                [[1, 2], [3, 4]] |- { @[*][*] : mock.func }
                """);
        var expected = json("""
                [["***", "***"], ["***", "***"]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === AttributeUnionPath (@["a","b"]): mark multiple keys ===

    @Test
    void attributeUnion_marksTwoKeys() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2, "c": 3} |- { @["a","b"] : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": "***", "c": 3}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void attributeUnion_marksThreeKeys() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2, "c": 3, "d": 4} |- { @["a","c","d"] : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": 2, "c": "***", "d": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void attributeUnion_singleKey_sameAsKeyPath() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @["a"] : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": 2}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void attributeUnion_blacklistSkipsMissingKeys() {
        var result   = evaluateExpression("""
                {"a": 1, "c": 3} |- { @["a","b","c"] : mock.func }
                """);
        var expected = json("""
                {"a": "***", "c": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void attributeUnion_allMissing_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"x": 1} |- { @["a","b"] : mock.func }
                """);
        var expected = json("""
                {"x": 1}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === IndexUnionPath (@[1,3]): mark multiple indices ===

    @Test
    void indexUnion_marksTwoIndices() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[1,3] : mock.func }
                """);
        var expected = json("""
                [0, "***", 2, "***", 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexUnion_marksThreeIndices() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[0,2,4] : mock.func }
                """);
        var expected = json("""
                ["***", 1, "***", 3, "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexUnion_singleIndex_sameAsIndexPath() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[1] : mock.func }
                """);
        var expected = json("""
                [1, "***", 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexUnion_withOutOfBounds_appliesValidIndices() {
        // Out-of-bounds index 5 is skipped, valid indices 0 and 2 get transformed
        var result   = evaluateExpression("""
                [0, 1, 2] |- { @[0,5,2] : mock.func }
                """);
        var expected = json("""
                ["***", 1, "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexUnion_allOutOfBounds_returnsUnchanged() {
        var result   = evaluateExpression("""
                [0, 1, 2] |- { @[10,20] : mock.func }
                """);
        var expected = json("""
                [0, 1, 2]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === Union + nested path combinations ===

    @Test
    void attributeUnionThenKey_marksNestedInMultiple() {
        var result   = evaluateExpression("""
                {"a": {"x": 1}, "b": {"x": 2}, "c": {"x": 3}} |- { @["a","c"].x : mock.func }
                """);
        var expected = json("""
                {"a": {"x": "***"}, "b": {"x": 2}, "c": {"x": "***"}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexUnionThenKey_marksNestedInMultiple() {
        var result   = evaluateExpression("""
                [{"n": 1}, {"n": 2}, {"n": 3}] |- { @[0,2].n : mock.func }
                """);
        var expected = json("""
                [{"n": "***"}, {"n": 2}, {"n": "***"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyThenAttributeUnion_marksMultipleInNested() {
        var result   = evaluateExpression("""
                {"data": {"a": 1, "b": 2, "c": 3}} |- { @.data["a","c"] : mock.func }
                """);
        var expected = json("""
                {"data": {"a": "***", "b": 2, "c": "***"}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyThenIndexUnion_marksMultipleInNestedArray() {
        var result   = evaluateExpression("""
                {"items": [0, 1, 2, 3]} |- { @.items[0,2] : mock.func }
                """);
        var expected = json("""
                {"items": ["***", 1, "***", 3]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenAttributeUnion_marksMultipleKeysInAll() {
        var result   = evaluateExpression("""
                [{"a": 1, "b": 2}, {"a": 3, "b": 4}] |- { @[*]["a","b"] : mock.func }
                """);
        var expected = json("""
                [{"a": "***", "b": "***"}, {"a": "***", "b": "***"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === SlicePath (@[start:end:step]): mark indices from slice ===

    @Test
    void slicePath_basicRange_marksSlice() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[1:3] : mock.func }
                """);
        var expected = json("""
                [0, "***", "***", 3, 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_allElements_marksAll() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[:] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_fromIndex_marksFromIndexToEnd() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[2:] : mock.func }
                """);
        var expected = json("""
                [0, 1, "***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_toIndex_marksFromStartToIndex() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[:3] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***", 3, 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_negativeStart_marksLastN() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[-3:] : mock.func }
                """);
        var expected = json("""
                [0, 1, "***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_negativeEnd_marksExceptLastN() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[:-2] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***", 3, 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_withStep_marksEveryNth() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4, 5] |- { @[::2] : mock.func }
                """);
        var expected = json("""
                ["***", 1, "***", 3, "***", 5]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_fromToStep_marksRangeWithStep() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4, 5] |- { @[1:5:2] : mock.func }
                """);
        var expected = json("""
                [0, "***", 2, "***", 4, 5]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === SlicePath with negative step (no reversal, just index selection) ===

    @Test
    void slicePath_negativeStep_marksAllSameAsPositive() {
        // [::-1] selects all indices (same set as [:])
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[::-1] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_negativeStepWithBounds_marksSelectedIndices() {
        // [4:1:-1] selects indices 4, 3, 2 (same set as [2:5])
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[4:1:-1] : mock.func }
                """);
        var expected = json("""
                [0, 1, "***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_negativeStepFromIndex_marksDownToStart() {
        // [3::-1] selects indices 3, 2, 1, 0
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[3::-1] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***", "***", 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === SlicePath blacklist semantics ===

    @Test
    void slicePath_onNonArray_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"a": 1} |- { @[1:3] : mock.func }
                """);
        var expected = json("""
                {"a": 1}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_emptyRange_returnsUnchanged() {
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[3:1] : mock.func }
                """);
        var expected = json("""
                [0, 1, 2, 3, 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_stepZero_returnsUnchanged() {
        // Step zero = empty set of indices
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[::0] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void slicePath_outOfBoundsStart_returnsUnchanged() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[10:] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === SlicePath + nested path combinations ===

    @Test
    void sliceThenKey_marksFieldInSlicedObjects() {
        var result   = evaluateExpression("""
                [{"n": 0}, {"n": 1}, {"n": 2}, {"n": 3}] |- { @[1:3].n : mock.func }
                """);
        var expected = json("""
                [{"n": 0}, {"n": "***"}, {"n": "***"}, {"n": 3}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyThenSlice_marksSliceInNestedArray() {
        var result   = evaluateExpression("""
                {"items": [0, 1, 2, 3, 4]} |- { @.items[1:4] : mock.func }
                """);
        var expected = json("""
                {"items": [0, "***", "***", "***", 4]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void sliceThenSlice_marksNestedSlice() {
        var result   = evaluateExpression("""
                [[0, 1, 2], [3, 4, 5], [6, 7, 8]] |- { @[0:2][1:] : mock.func }
                """);
        var expected = json("""
                [[0, "***", "***"], [3, "***", "***"], [6, 7, 8]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenSlice_marksSliceInAllArrays() {
        var result   = evaluateExpression("""
                [[0, 1, 2], [3, 4, 5]] |- { @[*][0:2] : mock.func }
                """);
        var expected = json("""
                [["***", "***", 2], ["***", "***", 5]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void sliceThenWildcard_marksAllInSlicedArrays() {
        var result   = evaluateExpression("""
                [[0, 1], [2, 3], [4, 5]] |- { @[0:2][*] : mock.func }
                """);
        var expected = json("""
                [["***", "***"], ["***", "***"], [4, 5]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath (@[(expr)]): dynamic key/index from expression ===

    @Test
    void expressionPath_literalIndex_redactsSensitiveItem() {
        // Redact the second medical record (index 1)
        var result   = evaluateExpression("""
                {"patient": "John", "records": ["checkup", "surgery", "followup"]}
                |- { @.records[(1)] : mock.func }
                """);
        var expected = json("""
                {"patient": "John", "records": ["checkup", "***", "followup"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_literalKey_redactsSensitiveField() {
        // Redact SSN by computed field name
        var result   = evaluateExpression("""
                {"name": "Alice", "ssn": "123-45-6789", "email": "alice@example.com"}
                |- { @[("ssn")] : mock.func }
                """);
        var expected = json("""
                {"name": "Alice", "ssn": "***", "email": "alice@example.com"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_computedIndex_redactsCalculatedPosition() {
        // Redact the last item (length-1 = index 2)
        var result   = evaluateExpression("""
                {"tasks": ["public", "internal", "confidential"]} |- { @.tasks[(1+1)] : mock.func }
                """);
        var expected = json("""
                {"tasks": ["public", "internal", "***"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_negativeIndex_redactsFromEnd() {
        // Redact the most recent log entry (last item)
        var result   = evaluateExpression("""
                {"logs": ["login", "view", "download", "logout"]} |- { @.logs[(-1)] : mock.func }
                """);
        var expected = json("""
                {"logs": ["login", "view", "download", "***"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath with dynamic @ binding (root context) ===

    @Test
    void expressionPath_atFromRoot_redactsConfiguredField() {
        // Config specifies which field to redact; @ refers to root
        var result   = evaluateExpression("""
                {
                    "redactField": "salary",
                    "employee": {"name": "Bob", "salary": 75000, "department": "Engineering"}
                } |- { @.employee[(@.redactField)] : mock.func }
                """);
        var expected = json("""
                {
                    "redactField": "salary",
                    "employee": {"name": "Bob", "salary": "***", "department": "Engineering"}
                }
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_atFromRoot_redactsAtConfiguredIndex() {
        // Config specifies which priority level to redact
        var result   = evaluateExpression("""
                {
                    "sensitiveLevel": 2,
                    "priorities": ["low", "medium", "high", "critical"]
                } |- { @.priorities[(@.sensitiveLevel)] : mock.func }
                """);
        var expected = json("""
                {
                    "sensitiveLevel": 2,
                    "priorities": ["low", "medium", "***", "critical"]
                }
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath with dynamic @ binding (after wildcard) ===

    @Test
    void expressionPath_atAfterWildcard_redactsPerUserSensitiveField() {
        // Each user specifies which of their own fields is sensitive
        var result   = evaluateExpression("""
                [
                    {"name": "Alice", "sensitiveField": "phone", "phone": "555-1234", "email": "a@x.com"},
                    {"name": "Bob", "sensitiveField": "email", "phone": "555-5678", "email": "b@x.com"}
                ] |- { @[*][(@.sensitiveField)] : mock.func }
                """);
        var expected = json("""
                [
                    {"name": "Alice", "sensitiveField": "phone", "phone": "***", "email": "a@x.com"},
                    {"name": "Bob", "sensitiveField": "email", "phone": "555-5678", "email": "***"}
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_atAfterWildcard_redactsAtUserSpecifiedIndex() {
        // Each record specifies which of its values to redact
        var result   = evaluateExpression("""
                [
                    {"redactIndex": 0, "values": ["secret", "public", "public"]},
                    {"redactIndex": 2, "values": ["public", "public", "secret"]}
                ] |- { @[*].values[(@.redactIndex)] : mock.func }
                """);
        var expected = json("""
                [
                    {"redactIndex": 0, "values": ["***", "public", "public"]},
                    {"redactIndex": 2, "values": ["public", "public", "***"]}
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath with # (position in iteration) ===

    @Test
    void expressionPath_hashBinding_redactsMatchingPosition() {
        // Redact element at position matching the iteration index
        // First array: redact index 0, second array: redact index 1
        var result   = evaluateExpression("""
                [
                    ["a0", "a1", "a2"],
                    ["b0", "b1", "b2"]
                ] |- { @[*][(#)] : mock.func }
                """);
        var expected = json("""
                [
                    ["***", "a1", "a2"],
                    ["b0", "***", "b2"]
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_hashWithComputation_redactsComputedFromPosition() {
        // Redact element at (iteration index + 1) mod 3
        var result   = evaluateExpression("""
                [
                    ["x", "y", "z"],
                    ["x", "y", "z"],
                    ["x", "y", "z"]
                ] |- { @[*][((# + 1) % 3)] : mock.func }
                """);
        var expected = json("""
                [
                    ["x", "***", "z"],
                    ["x", "y", "***"],
                    ["***", "y", "z"]
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath blacklist semantics ===

    @Test
    void expressionPath_invalidExprType_returnsUnchanged() {
        // Expression evaluates to boolean - invalid for key/index, return unchanged
        var result   = evaluateExpression("""
                {"data": [1, 2, 3]} |- { @.data[(true)] : mock.func }
                """);
        var expected = json("""
                {"data": [1, 2, 3]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_outOfBounds_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"items": ["a", "b", "c"]} |- { @.items[(99)] : mock.func }
                """);
        var expected = json("""
                {"items": ["a", "b", "c"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void expressionPath_missingKey_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"name": "test"} |- { @[("nonexistent")] : mock.func }
                """);
        var expected = json("""
                {"name": "test"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ExpressionPath + nested path combinations ===

    @Test
    void expressionPathThenKey_redactsNestedSensitiveField() {
        // Select first user by index, then redact their password
        var result   = evaluateExpression("""
                {"users": [
                    {"username": "admin", "password": "secret123"},
                    {"username": "guest", "password": "guest456"}
                ]} |- { @.users[(0)].password : mock.func }
                """);
        var expected = json("""
                {"users": [
                    {"username": "admin", "password": "***"},
                    {"username": "guest", "password": "guest456"}
                ]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenExpressionPath_redactsSecondColumnInAllRows() {
        // Redact column 1 in each row of a table
        var result   = evaluateExpression("""
                {"table": [
                    ["Name", "SSN", "Department"],
                    ["Alice", "111-22-3333", "Sales"],
                    ["Bob", "444-55-6666", "Engineering"]
                ]} |- { @.table[*][(1)] : mock.func }
                """);
        var expected = json("""
                {"table": [
                    ["Name", "***", "Department"],
                    ["Alice", "***", "Sales"],
                    ["Bob", "***", "Engineering"]
                ]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ConditionPath (@[?(cond)]): filter elements by condition ===

    @Test
    void conditionPath_literalTrue_redactsAllElements() {
        // Condition always true - redacts all (like wildcard)
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[?(true)] : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_literalFalse_redactsNone() {
        // Condition always false - redacts none
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[?(false)] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_objectLiteralTrue_redactsAllValues() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @[?(true)] : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ConditionPath with @ (current element access) ===

    @Test
    void conditionPath_atComparison_redactsHighValueItems() {
        // Redact prices over 100
        var result   = evaluateExpression("""
                {"products": [
                    {"name": "Basic", "price": 50},
                    {"name": "Premium", "price": 150},
                    {"name": "Standard", "price": 80}
                ]} |- { @.products[?(@.price > 100)] : mock.func }
                """);
        var expected = json("""
                {"products": [
                    {"name": "Basic", "price": 50},
                    "***",
                    {"name": "Standard", "price": 80}
                ]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_atEquality_redactsSensitiveRole() {
        // Redact users with admin role
        var result   = evaluateExpression("""
                [
                    {"user": "alice", "role": "admin", "token": "abc123"},
                    {"user": "bob", "role": "user", "token": "xyz789"},
                    {"user": "charlie", "role": "admin", "token": "def456"}
                ] |- { @[?(@.role == "admin")] : mock.func }
                """);
        var expected = json("""
                [
                    "***",
                    {"user": "bob", "role": "user", "token": "xyz789"},
                    "***"
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_atNestedThenKey_redactsFieldInMatchingOnly() {
        // Redact SSN only for minors (age < 18)
        var result   = evaluateExpression("""
                [
                    {"name": "Alice", "age": 25, "ssn": "111-22-3333"},
                    {"name": "Bobby", "age": 12, "ssn": "444-55-6666"},
                    {"name": "Carol", "age": 30, "ssn": "777-88-9999"}
                ] |- { @[?(@.age < 18)].ssn : mock.func }
                """);
        var expected = json("""
                [
                    {"name": "Alice", "age": 25, "ssn": "111-22-3333"},
                    {"name": "Bobby", "age": 12, "ssn": "***"},
                    {"name": "Carol", "age": 30, "ssn": "777-88-9999"}
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ConditionPath with # (position/key access) ===

    @Test
    void conditionPath_hashComparison_redactsEvenIndices() {
        // Redact elements at even indices
        var result   = evaluateExpression("""
                ["a", "b", "c", "d", "e"] |- { @[?(# % 2 == 0)] : mock.func }
                """);
        var expected = json("""
                ["***", "b", "***", "d", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_hashRange_redactsMiddleElements() {
        // Redact elements at indices 1 to 3
        var result   = evaluateExpression("""
                [0, 1, 2, 3, 4] |- { @[?(# > 0 && # < 4)] : mock.func }
                """);
        var expected = json("""
                [0, "***", "***", "***", 4]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_objectHashEquality_redactsSpecificKey() {
        // Redact value where key is "password"
        var result   = evaluateExpression("""
                {"username": "admin", "password": "secret", "email": "admin@example.com"}
                |- { @[?(# == "password")] : mock.func }
                """);
        var expected = json("""
                {"username": "admin", "password": "***", "email": "admin@example.com"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ConditionPath blacklist semantics ===

    @Test
    void conditionPath_onScalar_returnsUnchanged() {
        var result   = evaluateExpression("""
                42 |- { @[?(true)] : mock.func }
                """);
        var expected = Value.of(42);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPath_nonBooleanCondition_treatsAsFalse() {
        // Condition evaluates to number - treated as false
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[?(123)] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === ConditionPath + nested path combinations ===

    @Test
    void wildcardThenConditionPath_redactsMatchingInEachArray() {
        // For each inner array, redact elements > 5
        var result   = evaluateExpression("""
                [[1, 10, 3], [8, 2, 9]] |- { @[*][?(@ > 5)] : mock.func }
                """);
        var expected = json("""
                [[1, "***", 3], ["***", 2, "***"]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPathThenWildcard_redactsAllFieldsInMatchingObjects() {
        // For objects with status "classified", redact all fields
        var result   = evaluateExpression("""
                [
                    {"status": "public", "data": {"a": 1}},
                    {"status": "classified", "data": {"b": 2}},
                    {"status": "public", "data": {"c": 3}}
                ] |- { @[?(@.status == "classified")].* : mock.func }
                """);
        var expected = json("""
                [
                    {"status": "public", "data": {"a": 1}},
                    {"status": "***", "data": "***"},
                    {"status": "public", "data": {"c": 3}}
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void conditionPathThenIndex_redactsSpecificIndexInMatchingArrays() {
        // For arrays where first element > 3, redact index 1
        var result   = evaluateExpression("""
                [
                    [1, 2, 3, 4],
                    [5, 6, 7, 8],
                    [2, 8, 9, 10]
                ] |- { @[?(@[0] > 3)][1] : mock.func }
                """);
        var expected = json("""
                [
                    [1, 2, 3, 4],
                    [5, "***", 7, 8],
                    [2, 8, 9, 10]
                ]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void keyThenConditionPath_redactsMatchingInNestedArray() {
        // In orders, redact items costing over 50
        var result   = evaluateExpression("""
                {"orders": [
                    {"item": "Widget", "cost": 25},
                    {"item": "Gadget", "cost": 75},
                    {"item": "Doodad", "cost": 40}
                ]} |- { @.orders[?(@.cost > 50)] : mock.func }
                """);
        var expected = json("""
                {"orders": [
                    {"item": "Widget", "cost": 25},
                    "***",
                    {"item": "Doodad", "cost": 40}
                ]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === Path blacklist semantics: out-of-bounds index in path returns unchanged
    // ===

    @Test
    void indexPath_outOfBoundsInPath_returnsUnchanged() {
        // Index 10 doesn't exist - blacklist semantics returns unchanged
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[10] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void indexPath_negativeOutOfBoundsInPath_returnsUnchanged() {
        // Index -10 doesn't exist - blacklist semantics returns unchanged
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @[-10] : mock.func }
                """);
        var expected = json("""
                [1, 2, 3]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void nestedIndexPath_outOfBoundsInPath_returnsUnchanged() {
        // @.items[5] - index 5 doesn't exist in 3-element array, return unchanged
        var result   = evaluateExpression("""
                {"items": [1, 2, 3]} |- { @.items[5] : mock.func }
                """);
        var expected = json("""
                {"items": [1, 2, 3]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wildcardThenIndexPath_outOfBoundsInSomeArrays_appliesWhereValid() {
        // Some inner arrays don't have index 2 - blacklist semantics applies where
        // valid
        var result   = evaluateExpression("""
                [[1, 2, 3], [4, 5], [6, 7, 8, 9]] |- { @[*][2] : mock.func }
                """);
        var expected = json("""
                [[1, 2, "***"], [4, 5], [6, 7, "***", 9]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deepPath_intermediateOutOfBounds_returnsUnchanged() {
        // @[5].name - index 5 doesn't exist, return unchanged
        var result   = evaluateExpression("""
                [{"name": "a"}, {"name": "b"}] |- { @[5].name : mock.func }
                """);
        var expected = json("""
                [{"name": "a"}, {"name": "b"}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === RecursiveKeyPath (@..key): find and mark all occurrences of key ===

    @Test
    void recursiveKeyPath_singleMatch_marksIt() {
        var result   = evaluateExpression("""
                {"a": {"b": 1}} |- { @..b : mock.func }
                """);
        var expected = json("""
                {"a": {"b": "***"}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_multipleParallelMatches_marksAll() {
        var result   = evaluateExpression("""
                {"a": {"password": "secret1"}, "b": {"password": "secret2"}}
                |- { @..password : mock.func }
                """);
        var expected = json("""
                {"a": {"password": "***"}, "b": {"password": "***"}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_nestedMatch_outerConsumesInner() {
        // Outer "b" match consumes the nested "b"
        var result   = evaluateExpression("""
                {"b": {"b": 1}} |- { @..b : mock.func }
                """);
        var expected = json("""
                {"b": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_inArray_findsInAllElements() {
        var result   = evaluateExpression("""
                [{"x": 1}, {"x": 2}, {"y": 3}] |- { @..x : mock.func }
                """);
        var expected = json("""
                [{"x": "***"}, {"x": "***"}, {"y": 3}]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_deepNesting_findsAtAllDepths() {
        var result   = evaluateExpression("""
                {"a": {"b": {"c": {"target": 1}}, "target": 2}, "target": 3}
                |- { @..target : mock.func }
                """);
        var expected = json("""
                {"a": {"b": {"c": {"target": "***"}}, "target": "***"}, "target": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_withTailPath_navigatesFurther() {
        var result   = evaluateExpression("""
                {"user": {"info": {"name": "Alice"}}, "admin": {"info": {"name": "Bob"}}}
                |- { @..info.name : mock.func }
                """);
        var expected = json("""
                {"user": {"info": {"name": "***"}}, "admin": {"info": {"name": "***"}}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_noMatches_returnsUnchanged() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @..nonexistent : mock.func }
                """);
        var expected = json("""
                {"a": 1, "b": 2}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveKeyPath_onScalar_returnsUnchanged() {
        var result   = evaluateExpression("""
                42 |- { @..key : mock.func }
                """);
        var expected = Value.of(42);

        assertThat(result).isEqualTo(expected);
    }

    // === RecursiveIndexPath (@..[n]): find and mark all arrays at index n ===

    @Test
    void recursiveIndexPath_singleArray_marksIndex() {
        var result   = evaluateExpression("""
                {"items": [1, 2, 3]} |- { @..[1] : mock.func }
                """);
        var expected = json("""
                {"items": [1, "***", 3]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveIndexPath_multipleArrays_marksInAll() {
        var result   = evaluateExpression("""
                {"a": [1, 2], "b": [3, 4]} |- { @..[0] : mock.func }
                """);
        var expected = json("""
                {"a": ["***", 2], "b": ["***", 4]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveIndexPath_nestedArrays_marksAtAllLevels() {
        var result = evaluateExpression("""
                [[1, 2], [3, 4]] |- { @..[0] : mock.func }
                """);
        // Root array: mark index 0 → [1,2] becomes "***"
        // BUT we don't recurse INTO [1,2] because it was matched
        // Index 1 is [3,4], recurse into it: mark index 0 → "***"
        var expected = json("""
                ["***", ["***", 4]]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveIndexPath_negativeIndex_marksFromEnd() {
        var result   = evaluateExpression("""
                {"data": [1, 2, 3], "more": [4, 5]} |- { @..[-1] : mock.func }
                """);
        var expected = json("""
                {"data": [1, 2, "***"], "more": [4, "***"]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveIndexPath_withTailPath_navigatesFurther() {
        var result   = evaluateExpression("""
                {"rows": [{"name": "a"}, {"name": "b"}]} |- { @..[0].name : mock.func }
                """);
        var expected = json("""
                {"rows": [{"name": "***"}, {"name": "b"}]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveIndexPath_outOfBounds_skipped() {
        var result   = evaluateExpression("""
                {"items": [1, 2]} |- { @..[5] : mock.func }
                """);
        var expected = json("""
                {"items": [1, 2]}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === RecursiveWildcardPath (@..*): mark all values at all depths ===

    @Test
    void recursiveWildcardPath_flatObject_marksAllValues() {
        var result   = evaluateExpression("""
                {"a": 1, "b": 2} |- { @..* : mock.func }
                """);
        var expected = json("""
                {"a": "***", "b": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveWildcardPath_flatArray_marksAllElements() {
        var result   = evaluateExpression("""
                [1, 2, 3] |- { @..* : mock.func }
                """);
        var expected = json("""
                ["***", "***", "***"]
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveWildcardPath_nested_depthFirstProcessing() {
        // Depth-first: inner values processed before containers
        // Inner 1 becomes "***", then {"b": "***"} becomes "***"
        var result   = evaluateExpression("""
                {"a": {"b": 1}} |- { @..* : mock.func }
                """);
        var expected = json("""
                {"a": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveWildcardPath_withTailPath_appliesAtAllLevels() {
        // At each object at each depth that has key 'x', apply filter to value at 'x'
        // Using @..x to recursively find and modify all 'x' keys
        var result   = evaluateExpression("""
                {"a": {"x": 1}, "b": {"x": 2, "c": {"x": 3}}} |- { @..x : mock.func }
                """);
        var expected = json("""
                {"a": {"x": "***"}, "b": {"x": "***", "c": {"x": "***"}}}
                """);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveWildcardPath_onScalar_returnsUnchanged() {
        var result   = evaluateExpression("""
                "hello" |- { @..* : mock.func }
                """);
        var expected = Value.of("hello");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recursiveWildcardPath_emptyContainers_returnEmpty() {
        var result   = evaluateExpression("""
                {"empty": {}, "also": []} |- { @..* : mock.func }
                """);
        var expected = json("""
                {"empty": "***", "also": "***"}
                """);

        assertThat(result).isEqualTo(expected);
    }

    // === Error short-circuit tests for ExtendedFilter ===

    @Test
    void conditionPath_errorInCondition_bubblesUpImmediately() {
        // Division by zero in condition should return error, not continue processing
        var result = evaluateExpression("""
                [1, 2, 3, 4, 5] |- { @[?(1/0 == 1)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void conditionPath_errorOnSecondElement_shortCircuits() {
        // First element (5): 10/5 = 2, ok
        // Second element (0): 10/0 = error, should short-circuit
        var result = evaluateExpression("""
                [5, 0, 3] |- { @[?(10/@ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void conditionPath_errorOnThirdElement_shortCircuits() {
        // First two elements pass, third causes error
        var result = evaluateExpression("""
                [1, 2, 0] |- { @[?(10 / @ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void conditionPath_objectError_bubblesUp() {
        // Error on value check in object iteration
        var result = evaluateExpression("""
                {"a": 1, "b": 0, "c": 3} |- { @[?(10 / @ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void expressionPath_errorInExpression_bubblesUp() {
        // Division by zero in expression path should return error
        var result = evaluateExpression("""
                {"items": [1, 2, 3]} |- { @.items[(1/0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void recursiveKeyPath_errorInNestedPath_shortCircuits() {
        // Recursive key finds "items" arrays, then condition path filters elements
        // First items array [5, 2]: 10/5=2, 10/2=5, ok
        // Second items array [3, 0]: 10/3=ok, 10/0=error, should short-circuit
        var result = evaluateExpression("""
                {"a": {"items": [5, 2]}, "b": {"items": [3, 0]}} |- { @..items[?(10/@ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void recursiveWildcard_errorInTailPath_shortCircuits() {
        // Recursive wildcard finds all values depth-first
        // For each array found, condition path [?(10/@ > 0)] is applied to its elements
        // Inner array [2, 0] contains 0, causing division by zero error
        var result = evaluateExpression("""
                {"data": [[5, 2], [3, 0]]} |- { @..*[?(10/@ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void wildcardThenCondition_errorInDeepArray_shortCircuits() {
        // Error occurs deep in nested structure
        var result = evaluateExpression("""
                [[1, 2], [3, 0], [5, 6]] |- { @[*][?(10/@ > 0)] : mock.func }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

}
