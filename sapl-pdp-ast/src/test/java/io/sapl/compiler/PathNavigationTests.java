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
import static io.sapl.util.ExpressionTestUtil.assertIsErrorContaining;
import static io.sapl.util.ExpressionTestUtil.evaluateExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;

/**
 * Tests for path navigation algorithm in ExtendedFilterCompiler.
 * Uses actual SAPL expressions with |- syntax.
 * Current sketch replaces target with "***" marker instead of calling function.
 */
class PathNavigationTests {

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_pathNavigation_then_returnsExpected(String description, String expression, Value expected) {
        var result = evaluateExpression(expression);
        assertThat(result).isEqualTo(expected);
    }

    // @formatter:off
    private static Stream<Arguments> when_pathNavigation_then_returnsExpected() {
        return Stream.of(
            // === KeyPath (@.key): navigate into object field ===
            arguments("keyPath_replacesDirectField",
                """
                {"a": 1, "b": 2} |- { @.a : mock.func }
                """,
                json("""
                {"a": "***", "b": 2}
                """)),
            arguments("keyPath_replacesNestedField",
                """
                {"outer": {"inner": "secret"}, "other": "visible"} |- { @.outer.inner : mock.func }
                """,
                json("""
                {"outer": {"inner": "***"}, "other": "visible"}
                """)),
            arguments("keyPath_preservesFieldOrder",
                """
                {"first": 1, "second": 2, "third": 3} |- { @.second : mock.func }
                """,
                json("""
                {"first": 1, "second": "***", "third": 3}
                """)),

            // === Blacklist semantics: type mismatches return unchanged ===
            arguments("keyPath_onNonObject_returnsUnchanged",
                """
                "not an object" |- { @.a : mock.func }
                """,
                Value.of("not an object")),
            arguments("keyPath_missingKey_returnsUnchanged",
                """
                {"a": 1} |- { @.nonexistent : mock.func }
                """,
                json("""
                {"a": 1}
                """)),
            arguments("keyPath_nestedMissingKey_returnsUnchanged",
                """
                {"outer": {"a": 1}} |- { @.outer.nonexistent : mock.func }
                """,
                json("""
                {"outer": {"a": 1}}
                """)),

            // === IndexPath (@[n]): navigate into array element ===
            arguments("indexPath_replacesFirstElement",
                """
                [1, 2, 3] |- { @[0] : mock.func }
                """,
                json("""
                ["***", 2, 3]
                """)),
            arguments("indexPath_replacesMiddleElement",
                """
                [1, 2, 3] |- { @[1] : mock.func }
                """,
                json("""
                [1, "***", 3]
                """)),
            arguments("indexPath_replacesLastElement",
                """
                [1, 2, 3] |- { @[2] : mock.func }
                """,
                json("""
                [1, 2, "***"]
                """)),
            arguments("indexPath_negativeIndex_fromEnd",
                """
                [1, 2, 3] |- { @[-1] : mock.func }
                """,
                json("""
                [1, 2, "***"]
                """)),
            arguments("indexPath_negativeIndex_secondFromEnd",
                """
                [1, 2, 3, 4] |- { @[-2] : mock.func }
                """,
                json("""
                [1, 2, "***", 4]
                """)),

            // === IndexPath blacklist semantics ===
            arguments("indexPath_onNonArray_returnsUnchanged",
                """
                {"a": 1} |- { @[0] : mock.func }
                """,
                json("""
                {"a": 1}
                """)),
            arguments("indexPath_outOfBounds_returnsUnchanged",
                """
                [1, 2, 3] |- { @[10] : mock.func }
                """,
                json("[1, 2, 3]")),
            arguments("indexPath_negativeOutOfBounds_returnsUnchanged",
                """
                [1, 2, 3] |- { @[-10] : mock.func }
                """,
                json("[1, 2, 3]")),

            // === Combined paths: KeyPath + IndexPath ===
            arguments("keyThenIndex_replacesNestedArrayElement",
                """
                {"items": [1, 2, 3]} |- { @.items[1] : mock.func }
                """,
                json("""
                {"items": [1, "***", 3]}
                """)),
            arguments("indexThenKey_replacesObjectInArray",
                """
                [{"name": "alice"}, {"name": "bob"}] |- { @[1].name : mock.func }
                """,
                json("""
                [{"name": "alice"}, {"name": "***"}]
                """)),
            arguments("deepNesting_keyIndexKeyIndex",
                """
                {"data": [{"values": [10, 20, 30]}]} |- { @.data[0].values[1] : mock.func }
                """,
                json("""
                {"data": [{"values": [10, "***", 30]}]}
                """)),
            arguments("combined_missingIntermediateKey_returnsUnchanged",
                """
                {"other": [1, 2]} |- { @.items[0] : mock.func }
                """,
                json("""
                {"other": [1, 2]}
                """)),
            arguments("combined_intermediateNotArray_returnsUnchanged",
                """
                {"items": "not an array"} |- { @.items[0] : mock.func }
                """,
                json("""
                {"items": "not an array"}
                """)),

            // === WildcardPath (@[*] / @.*): mark all children ===
            arguments("wildcardPath_marksAllArrayElements",
                """
                [1, 2, 3] |- { @[*] : mock.func }
                """,
                json("""
                ["***", "***", "***"]
                """)),
            arguments("wildcardPath_marksAllObjectValues",
                """
                {"a": 1, "b": 2} |- { @.* : mock.func }
                """,
                json("""
                {"a": "***", "b": "***"}
                """)),
            arguments("wildcardPath_emptyArray_returnsEmptyArray",
                """
                [] |- { @[*] : mock.func }
                """,
                json("[]")),
            arguments("wildcardPath_emptyObject_returnsEmptyObject",
                """
                {} |- { @.* : mock.func }
                """,
                json("{}")),

            // === WildcardPath blacklist semantics ===
            arguments("wildcardPath_onScalar_returnsUnchanged",
                """
                42 |- { @[*] : mock.func }
                """,
                Value.of(42)),
            arguments("wildcardPath_onString_returnsUnchanged",
                """
                "hello" |- { @.* : mock.func }
                """,
                Value.of("hello")),

            // === WildcardPath + nested paths ===
            arguments("wildcardThenKey_marksFieldInAllObjects",
                """
                [{"name": "alice"}, {"name": "bob"}] |- { @[*].name : mock.func }
                """,
                json("""
                [{"name": "***"}, {"name": "***"}]
                """)),
            arguments("keyThenWildcard_marksAllInNestedArray",
                """
                {"items": [1, 2, 3]} |- { @.items[*] : mock.func }
                """,
                json("""
                {"items": ["***", "***", "***"]}
                """)),
            arguments("wildcardThenIndex_marksElementInAllArrays",
                """
                [[1, 2], [3, 4], [5, 6]] |- { @[*][0] : mock.func }
                """,
                json("""
                [["***", 2], ["***", 4], ["***", 6]]
                """)),
            arguments("wildcardThenKey_blacklistSkipsMissingKeys",
                """
                [{"name": "alice"}, {"other": "bob"}, {"name": "carol"}] |- { @[*].name : mock.func }
                """,
                json("""
                [{"name": "***"}, {"other": "bob"}, {"name": "***"}]
                """)),
            arguments("wildcardThenWildcard_marksNestedArrays",
                """
                [[1, 2], [3, 4]] |- { @[*][*] : mock.func }
                """,
                json("""
                [["***", "***"], ["***", "***"]]
                """)),

            // === AttributeUnionPath (@["a","b"]): mark multiple keys ===
            arguments("attributeUnion_marksTwoKeys",
                """
                {"a": 1, "b": 2, "c": 3} |- { @["a","c"] : mock.func }
                """,
                json("""
                {"a": "***", "b": 2, "c": "***"}
                """)),
            arguments("attributeUnion_marksThreeKeys",
                """
                {"x": 1, "y": 2, "z": 3, "w": 4} |- { @["x","y","z"] : mock.func }
                """,
                json("""
                {"x": "***", "y": "***", "z": "***", "w": 4}
                """)),
            arguments("attributeUnion_singleKey_sameAsKeyPath",
                """
                {"a": 1, "b": 2} |- { @["a"] : mock.func }
                """,
                json("""
                {"a": "***", "b": 2}
                """)),
            arguments("attributeUnion_blacklistSkipsMissingKeys",
                """
                {"a": 1, "c": 3} |- { @["a","b","c"] : mock.func }
                """,
                json("""
                {"a": "***", "c": "***"}
                """)),
            arguments("attributeUnion_allMissing_returnsUnchanged",
                """
                {"x": 1} |- { @["a","b","c"] : mock.func }
                """,
                json("""
                {"x": 1}
                """)),

            // === IndexUnionPath (@[0,2]): mark multiple indices ===
            arguments("indexUnion_marksTwoIndices",
                """
                [1, 2, 3, 4, 5] |- { @[0,2] : mock.func }
                """,
                json("""
                ["***", 2, "***", 4, 5]
                """)),
            arguments("indexUnion_marksThreeIndices",
                """
                [1, 2, 3, 4, 5] |- { @[0,2,4] : mock.func }
                """,
                json("""
                ["***", 2, "***", 4, "***"]
                """)),
            arguments("indexUnion_singleIndex_sameAsIndexPath",
                """
                [1, 2, 3] |- { @[1] : mock.func }
                """,
                json("""
                [1, "***", 3]
                """)),
            arguments("indexUnion_withOutOfBounds_appliesValidIndices",
                """
                [1, 2, 3] |- { @[0,10,2] : mock.func }
                """,
                json("""
                ["***", 2, "***"]
                """)),
            arguments("indexUnion_allOutOfBounds_returnsUnchanged",
                """
                [1, 2, 3] |- { @[10,20,30] : mock.func }
                """,
                json("[1, 2, 3]")),

            // === Union + nested paths ===
            arguments("attributeUnionThenKey_marksNestedInMultiple",
                """
                {"user": {"name": "alice"}, "admin": {"name": "bob"}} |- { @["user","admin"].name : mock.func }
                """,
                json("""
                {"user": {"name": "***"}, "admin": {"name": "***"}}
                """)),
            arguments("indexUnionThenKey_marksNestedInMultiple",
                """
                [{"name": "alice"}, {"name": "bob"}, {"name": "carol"}] |- { @[0,2].name : mock.func }
                """,
                json("""
                [{"name": "***"}, {"name": "bob"}, {"name": "***"}]
                """)),
            arguments("keyThenAttributeUnion_marksMultipleInNested",
                """
                {"data": {"a": 1, "b": 2, "c": 3}} |- { @.data["a","c"] : mock.func }
                """,
                json("""
                {"data": {"a": "***", "b": 2, "c": "***"}}
                """)),
            arguments("keyThenIndexUnion_marksMultipleInNestedArray",
                """
                {"items": [1, 2, 3, 4]} |- { @.items[0,3] : mock.func }
                """,
                json("""
                {"items": ["***", 2, 3, "***"]}
                """)),
            arguments("wildcardThenAttributeUnion_marksMultipleKeysInAll",
                """
                [{"a": 1, "b": 2, "c": 3}, {"a": 4, "b": 5, "c": 6}] |- { @[*]["a","c"] : mock.func }
                """,
                json("""
                [{"a": "***", "b": 2, "c": "***"}, {"a": "***", "b": 5, "c": "***"}]
                """)),

            // === SlicePath (@[start:end:step]): mark array slices ===
            arguments("slicePath_basicRange_marksSlice",
                """
                [1, 2, 3, 4, 5] |- { @[1:4] : mock.func }
                """,
                json("""
                [1, "***", "***", "***", 5]
                """)),
            arguments("slicePath_allElements_marksAll",
                """
                [1, 2, 3] |- { @[:] : mock.func }
                """,
                json("""
                ["***", "***", "***"]
                """)),
            arguments("slicePath_fromIndex_marksFromIndexToEnd",
                """
                [1, 2, 3, 4, 5] |- { @[2:] : mock.func }
                """,
                json("""
                [1, 2, "***", "***", "***"]
                """)),
            arguments("slicePath_toIndex_marksFromStartToIndex",
                """
                [1, 2, 3, 4, 5] |- { @[:3] : mock.func }
                """,
                json("""
                ["***", "***", "***", 4, 5]
                """)),
            arguments("slicePath_negativeStart_marksLastN",
                """
                [1, 2, 3, 4, 5] |- { @[-2:] : mock.func }
                """,
                json("""
                [1, 2, 3, "***", "***"]
                """)),
            arguments("slicePath_negativeEnd_marksExceptLastN",
                """
                [1, 2, 3, 4, 5] |- { @[:-2] : mock.func }
                """,
                json("""
                ["***", "***", "***", 4, 5]
                """)),
            arguments("slicePath_withStep_marksEveryNth",
                """
                [1, 2, 3, 4, 5, 6] |- { @[::2] : mock.func }
                """,
                json("""
                ["***", 2, "***", 4, "***", 6]
                """)),
            arguments("slicePath_fromToStep_marksRangeWithStep",
                """
                [1, 2, 3, 4, 5, 6, 7, 8] |- { @[1:7:2] : mock.func }
                """,
                json("""
                [1, "***", 3, "***", 5, "***", 7, 8]
                """)),
            arguments("slicePath_negativeStep_marksAllSameAsPositive",
                """
                [1, 2, 3, 4, 5] |- { @[::-1] : mock.func }
                """,
                json("""
                ["***", "***", "***", "***", "***"]
                """)),
            arguments("slicePath_negativeStepWithBounds_marksSelectedIndices",
                """
                [1, 2, 3, 4, 5] |- { @[4:1:-1] : mock.func }
                """,
                json("""
                [1, 2, "***", "***", "***"]
                """)),
            arguments("slicePath_negativeStepFromIndex_marksDownToStart",
                """
                [1, 2, 3, 4, 5] |- { @[3::-1] : mock.func }
                """,
                json("""
                ["***", "***", "***", "***", 5]
                """)),

            // === SlicePath blacklist semantics ===
            arguments("slicePath_onNonArray_returnsUnchanged",
                """
                {"a": 1} |- { @[1:3] : mock.func }
                """,
                json("""
                {"a": 1}
                """)),
            arguments("slicePath_emptyRange_returnsUnchanged",
                """
                [1, 2, 3] |- { @[2:2] : mock.func }
                """,
                json("[1, 2, 3]")),
            arguments("slicePath_stepZero_returnsUnchanged",
                """
                [1, 2, 3] |- { @[::0] : mock.func }
                """,
                json("[1, 2, 3]")),
            arguments("slicePath_outOfBoundsStart_returnsUnchanged",
                """
                [1, 2, 3] |- { @[10:20] : mock.func }
                """,
                json("[1, 2, 3]")),

            // === SlicePath + nested paths ===
            arguments("sliceThenKey_marksFieldInSlicedObjects",
                """
                [{"x": 1}, {"x": 2}, {"x": 3}, {"x": 4}] |- { @[1:3].x : mock.func }
                """,
                json("""
                [{"x": 1}, {"x": "***"}, {"x": "***"}, {"x": 4}]
                """)),
            arguments("keyThenSlice_marksSliceInNestedArray",
                """
                {"data": [1, 2, 3, 4, 5]} |- { @.data[1:4] : mock.func }
                """,
                json("""
                {"data": [1, "***", "***", "***", 5]}
                """)),
            arguments("sliceThenSlice_marksNestedSlice",
                """
                [[1, 2, 3], [4, 5, 6], [7, 8, 9]] |- { @[0:2][1:] : mock.func }
                """,
                json("""
                [[1, "***", "***"], [4, "***", "***"], [7, 8, 9]]
                """)),
            arguments("wildcardThenSlice_marksSliceInAllArrays",
                """
                [[1, 2, 3, 4], [5, 6, 7, 8]] |- { @[*][1:3] : mock.func }
                """,
                json("""
                [[1, "***", "***", 4], [5, "***", "***", 8]]
                """)),
            arguments("sliceThenWildcard_marksAllInSlicedArrays",
                """
                [[1, 2], [3, 4], [5, 6]] |- { @[0:2][*] : mock.func }
                """,
                json("""
                [["***", "***"], ["***", "***"], [5, 6]]
                """)),

            // === ExpressionPath (@[(expr)]): dynamic index/key navigation ===
            arguments("expressionPath_literalIndex_redactsSensitiveItem",
                """
                ["public", "secret", "public"] |- { @[(1)] : mock.func }
                """,
                json("""
                ["public", "***", "public"]
                """)),
            arguments("expressionPath_literalKey_redactsSensitiveField",
                """
                {"username": "alice", "password": "secret123"} |- { @[("password")] : mock.func }
                """,
                json("""
                {"username": "alice", "password": "***"}
                """)),
            arguments("expressionPath_computedIndex_redactsCalculatedPosition",
                """
                ["a", "b", "c", "d"] |- { @[(1+1)] : mock.func }
                """,
                json("""
                ["a", "b", "***", "d"]
                """)),
            arguments("expressionPath_negativeIndex_redactsFromEnd",
                """
                ["first", "middle", "last"] |- { @[(-1)] : mock.func }
                """,
                json("""
                ["first", "middle", "***"]
                """)),
            arguments("expressionPath_atFromRoot_redactsConfiguredField",
                """
                {"config": {"sensitiveField": "password"}, "data": {"password": "secret", "name": "test"}} |- { @.data[(@.config.sensitiveField)] : mock.func }
                """,
                json("""
                {"config": {"sensitiveField": "password"}, "data": {"password": "***", "name": "test"}}
                """)),
            arguments("expressionPath_atFromRoot_redactsAtConfiguredIndex",
                """
                {"config": {"sensitiveIndex": 1}, "items": ["public", "secret", "public"]} |- { @.items[(@.config.sensitiveIndex)] : mock.func }
                """,
                json("""
                {"config": {"sensitiveIndex": 1}, "items": ["public", "***", "public"]}
                """)),
            arguments("expressionPath_atAfterWildcard_redactsPerUserSensitiveField",
                """
                [{"sensitiveField": "ssn", "ssn": "123", "name": "alice"}, {"sensitiveField": "phone", "phone": "555", "name": "bob"}] |- { @[*][(@.sensitiveField)] : mock.func }
                """,
                json("""
                [{"sensitiveField": "ssn", "ssn": "***", "name": "alice"}, {"sensitiveField": "phone", "phone": "***", "name": "bob"}]
                """)),
            arguments("expressionPath_atAfterWildcard_redactsAtUserSpecifiedIndex",
                """
                [{"redactIndex": 0, "items": ["secret", "public"]}, {"redactIndex": 1, "items": ["public", "secret"]}] |- { @[*].items[(@.redactIndex)] : mock.func }
                """,
                json("""
                [{"redactIndex": 0, "items": ["***", "public"]}, {"redactIndex": 1, "items": ["public", "***"]}]
                """)),
            arguments("expressionPath_hashBinding_redactsMatchingPosition",
                """
                [["a", "b", "c"], ["d", "e", "f"], ["g", "h", "i"]] |- { @[*][(#)] : mock.func }
                """,
                json("""
                [["***", "b", "c"], ["d", "***", "f"], ["g", "h", "***"]]
                """)),
            arguments("expressionPath_hashWithComputation_redactsComputedFromPosition",
                """
                [["a", "b", "c"], ["d", "e", "f"]] |- { @[*][(# * 2)] : mock.func }
                """,
                json("""
                [["***", "b", "c"], ["d", "e", "***"]]
                """)),

            // === ExpressionPath blacklist semantics ===
            arguments("expressionPath_invalidExprType_returnsUnchanged",
                """
                [1, 2, 3] |- { @[(true)] : mock.func }
                """,
                json("[1, 2, 3]")),
            arguments("expressionPath_outOfBounds_returnsUnchanged",
                """
                [1, 2, 3] |- { @[(10)] : mock.func }
                """,
                json("[1, 2, 3]")),
            arguments("expressionPath_missingKey_returnsUnchanged",
                """
                {"a": 1} |- { @[("nonexistent")] : mock.func }
                """,
                json("""
                {"a": 1}
                """)),

            // === ExpressionPath + nested paths ===
            arguments("expressionPathThenKey_redactsNestedSensitiveField",
                """
                {"sensitiveKey": "user2", "user1": {"data": "public"}, "user2": {"data": "secret"}} |- { @[(@.sensitiveKey)].data : mock.func }
                """,
                json("""
                {"sensitiveKey": "user2", "user1": {"data": "public"}, "user2": {"data": "***"}}
                """)),
            arguments("wildcardThenExpressionPath_redactsSecondColumnInAllRows",
                """
                [[1, 2, 3], [4, 5, 6], [7, 8, 9]] |- { @[*][(1)] : mock.func }
                """,
                json("""
                [[1, "***", 3], [4, "***", 6], [7, "***", 9]]
                """)),

            // === ConditionPath (@[?(cond)]): filter elements by condition ===
            arguments("conditionPath_literalTrue_redactsAllElements",
                """
                [1, 2, 3, 4] |- { @[?(true)] : mock.func }
                """,
                json("""
                ["***", "***", "***", "***"]
                """)),
            arguments("conditionPath_literalFalse_redactsNone",
                """
                [1, 2, 3, 4] |- { @[?(false)] : mock.func }
                """,
                json("[1, 2, 3, 4]")),
            arguments("conditionPath_objectLiteralTrue_redactsAllValues",
                """
                {"a": 1, "b": 2, "c": 3} |- { @[?(true)] : mock.func }
                """,
                json("""
                {"a": "***", "b": "***", "c": "***"}
                """)),
            arguments("conditionPath_atComparison_redactsHighValueItems",
                """
                [{"value": 10}, {"value": 100}, {"value": 50}, {"value": 200}] |- { @[?(@ .value > 50)] : mock.func }
                """,
                json("""
                [{"value": 10}, "***", {"value": 50}, "***"]
                """)),
            arguments("conditionPath_atEquality_redactsSensitiveRole",
                """
                [{"role": "user", "data": "x"}, {"role": "admin", "data": "y"}, {"role": "user", "data": "z"}] |- { @[?(@.role == "admin")] : mock.func }
                """,
                json("""
                [{"role": "user", "data": "x"}, "***", {"role": "user", "data": "z"}]
                """)),
            arguments("conditionPath_atNestedThenKey_redactsFieldInMatchingOnly",
                """
                [{"type": "secret", "data": {"info": "hidden"}}, {"type": "public", "data": {"info": "visible"}}] |- { @[?(@.type == "secret")].data.info : mock.func }
                """,
                json("""
                [{"type": "secret", "data": {"info": "***"}}, {"type": "public", "data": {"info": "visible"}}]
                """)),
            arguments("conditionPath_hashComparison_redactsEvenIndices",
                """
                ["a", "b", "c", "d", "e"] |- { @[?(# % 2 == 0)] : mock.func }
                """,
                json("""
                ["***", "b", "***", "d", "***"]
                """)),
            arguments("conditionPath_hashRange_redactsMiddleElements",
                """
                [1, 2, 3, 4, 5] |- { @[?(# > 0 && # < 4)] : mock.func }
                """,
                json("""
                [1, "***", "***", "***", 5]
                """)),
            arguments("conditionPath_objectHashEquality_redactsSpecificKey",
                """
                {"first": 1, "second": 2, "third": 3} |- { @[?(# == "second")] : mock.func }
                """,
                json("""
                {"first": 1, "second": "***", "third": 3}
                """)),

            // === ConditionPath blacklist semantics ===
            arguments("conditionPath_onScalar_returnsUnchanged",
                """
                42 |- { @[?(true)] : mock.func }
                """,
                Value.of(42)),
            arguments("conditionPath_nonBooleanCondition_treatsAsFalse",
                """
                [1, 2, 3] |- { @[?("not a boolean")] : mock.func }
                """,
                json("[1, 2, 3]")),

            // === ConditionPath + nested paths ===
            arguments("wildcardThenConditionPath_redactsMatchingInEachArray",
                """
                [[1, 5, 2], [10, 3, 8]] |- { @[*][?(@ > 4)] : mock.func }
                """,
                json("""
                [[1, "***", 2], ["***", 3, "***"]]
                """)),
            arguments("conditionPathThenWildcard_redactsAllFieldsInMatchingObjects",
                """
                [{"active": true, "a": 1, "b": 2}, {"active": false, "a": 3, "b": 4}] |- { @[?(@.active == true)].* : mock.func }
                """,
                json("""
                [{"active": "***", "a": "***", "b": "***"}, {"active": false, "a": 3, "b": 4}]
                """)),
            arguments("conditionPathThenIndex_redactsSpecificIndexInMatchingArrays",
                """
                [[1, 2, 3], [10, 20, 30], [4, 5, 6]] |- { @[?(@[0] > 5)][1] : mock.func }
                """,
                json("""
                [[1, 2, 3], [10, "***", 30], [4, 5, 6]]
                """)),
            arguments("keyThenConditionPath_redactsMatchingInNestedArray",
                """
                {"items": [{"keep": false, "v": 1}, {"keep": true, "v": 2}, {"keep": false, "v": 3}]} |- { @.items[?(@.keep == true)] : mock.func }
                """,
                json("""
                {"items": [{"keep": false, "v": 1}, "***", {"keep": false, "v": 3}]}
                """)),

            // === Additional IndexPath edge cases ===
            arguments("indexPath_outOfBoundsInPath_returnsUnchanged",
                """
                {"arr": [1, 2, 3]} |- { @.arr[10].field : mock.func }
                """,
                json("""
                {"arr": [1, 2, 3]}
                """)),
            arguments("indexPath_negativeOutOfBoundsInPath_returnsUnchanged",
                """
                {"arr": [1, 2, 3]} |- { @.arr[-10].field : mock.func }
                """,
                json("""
                {"arr": [1, 2, 3]}
                """)),
            arguments("nestedIndexPath_outOfBoundsInPath_returnsUnchanged",
                """
                [[1, 2], [3, 4]] |- { @[0][10].field : mock.func }
                """,
                json("[[1, 2], [3, 4]]")),
            arguments("wildcardThenIndexPath_outOfBoundsInSomeArrays_appliesWhereValid",
                """
                [[1, 2, 3], [4], [5, 6]] |- { @[*][1] : mock.func }
                """,
                json("""
                [[1, "***", 3], [4], [5, "***"]]
                """)),
            arguments("deepPath_intermediateOutOfBounds_returnsUnchanged",
                """
                {"data": [[1, 2]]} |- { @.data[0][10][0] : mock.func }
                """,
                json("""
                {"data": [[1, 2]]}
                """)),

            // === RecursiveKeyPath (@..key): find key at any depth ===
            arguments("recursiveKeyPath_singleMatch_marksIt",
                """
                {"a": {"b": {"target": "found"}}} |- { @..target : mock.func }
                """,
                json("""
                {"a": {"b": {"target": "***"}}}
                """)),
            arguments("recursiveKeyPath_multipleParallelMatches_marksAll",
                """
                {"x": {"target": 1}, "y": {"target": 2}} |- { @..target : mock.func }
                """,
                json("""
                {"x": {"target": "***"}, "y": {"target": "***"}}
                """)),
            arguments("recursiveKeyPath_nestedMatch_outerConsumesInner",
                """
                {"target": {"target": "inner"}} |- { @..target : mock.func }
                """,
                json("""
                {"target": "***"}
                """)),
            arguments("recursiveKeyPath_inArray_findsInAllElements",
                """
                [{"target": 1}, {"other": 2}, {"target": 3}] |- { @..target : mock.func }
                """,
                json("""
                [{"target": "***"}, {"other": 2}, {"target": "***"}]
                """)),
            arguments("recursiveKeyPath_deepNesting_findsAtAllDepths",
                """
                {"a": {"b": {"c": {"target": "deep"}}}} |- { @..target : mock.func }
                """,
                json("""
                {"a": {"b": {"c": {"target": "***"}}}}
                """)),
            arguments("recursiveKeyPath_withTailPath_navigatesFurther",
                """
                {"items": [{"data": {"value": 1}}, {"data": {"value": 2}}]} |- { @..data.value : mock.func }
                """,
                json("""
                {"items": [{"data": {"value": "***"}}, {"data": {"value": "***"}}]}
                """)),
            arguments("recursiveKeyPath_noMatches_returnsUnchanged",
                """
                {"a": 1, "b": 2} |- { @..nonexistent : mock.func }
                """,
                json("""
                {"a": 1, "b": 2}
                """)),
            arguments("recursiveKeyPath_onScalar_returnsUnchanged",
                """
                42 |- { @..key : mock.func }
                """,
                Value.of(42)),

            // === RecursiveIndexPath (@..[n]): find index at any depth ===
            arguments("recursiveIndexPath_singleArray_marksIndex",
                """
                [1, 2, 3] |- { @..[1] : mock.func }
                """,
                json("""
                [1, "***", 3]
                """)),
            arguments("recursiveIndexPath_multipleArrays_marksInAll",
                """
                {"a": [1, 2], "b": [3, 4]} |- { @..[0] : mock.func }
                """,
                json("""
                {"a": ["***", 2], "b": ["***", 4]}
                """)),
            arguments("recursiveIndexPath_nestedArrays_marksAtAllLevels",
                """
                [[1, 2], [3, 4]] |- { @..[0] : mock.func }
                """,
                json("""
                ["***", ["***", 4]]
                """)),
            arguments("recursiveIndexPath_negativeIndex_marksFromEnd",
                """
                {"arr1": [1, 2, 3], "arr2": [4, 5]} |- { @..[-1] : mock.func }
                """,
                json("""
                {"arr1": [1, 2, "***"], "arr2": [4, "***"]}
                """)),
            arguments("recursiveIndexPath_withTailPath_navigatesFurther",
                """
                {"rows": [{"name": "a"}, {"name": "b"}]} |- { @..[0].name : mock.func }
                """,
                json("""
                {"rows": [{"name": "***"}, {"name": "b"}]}
                """)),
            arguments("recursiveIndexPath_outOfBounds_skipped",
                """
                {"short": [1], "long": [1, 2, 3]} |- { @..[2] : mock.func }
                """,
                json("""
                {"short": [1], "long": [1, 2, "***"]}
                """)),

            // === RecursiveWildcardPath (@..*): descend into all nested values ===
            arguments("recursiveWildcardPath_flatObject_marksAllValues",
                """
                {"a": 1, "b": 2} |- { @..* : mock.func }
                """,
                json("""
                {"a": "***", "b": "***"}
                """)),
            arguments("recursiveWildcardPath_flatArray_marksAllElements",
                """
                [1, 2, 3] |- { @..* : mock.func }
                """,
                json("""
                ["***", "***", "***"]
                """)),
            arguments("recursiveWildcardPath_nested_depthFirstProcessing",
                """
                {"a": {"b": 1}} |- { @..* : mock.func }
                """,
                json("""
                {"a": "***"}
                """)),
            arguments("recursiveWildcardPath_withTailPath_appliesAtAllLevels",
                """
                {"x": {"val": 1}, "y": {"val": 2}} |- { @..*.val : mock.func }
                """,
                json("""
                {"x": {"val": "***"}, "y": {"val": "***"}}
                """)),
            arguments("recursiveWildcardPath_onScalar_returnsUnchanged",
                """
                42 |- { @..* : mock.func }
                """,
                Value.of(42)),
            arguments("recursiveWildcardPath_emptyContainers_returnEmpty",
                """
                {"arr": [], "obj": {}} |- { @..* : mock.func }
                """,
                json("""
                {"arr": "***", "obj": "***"}
                """))
        );
    }
    // @formatter:on

    @MethodSource
    @ParameterizedTest(name = "{0}")
    void when_pathNavigation_withError_then_returnsError(String description, String expression, String errorSubstring) {
        var result = evaluateExpression(expression);
        assertIsErrorContaining(result, errorSubstring);
    }

    // @formatter:off
    private static Stream<Arguments> when_pathNavigation_withError_then_returnsError() {
        return Stream.of(
            // === Error short-circuit tests for ExtendedFilter ===
            arguments("conditionPath_errorInCondition_bubblesUpImmediately",
                """
                [1, 2, 3, 4, 5] |- { @[?(1/0 == 1)] : mock.func }
                """,
                "Division by zero"),
            arguments("conditionPath_errorOnSecondElement_shortCircuits",
                """
                [5, 0, 3] |- { @[?(10/@ > 0)] : mock.func }
                """,
                "Division by zero"),
            arguments("conditionPath_errorOnThirdElement_shortCircuits",
                """
                [1, 2, 0] |- { @[?(10 / @ > 0)] : mock.func }
                """,
                "Division by zero"),
            arguments("conditionPath_objectError_bubblesUp",
                """
                {"a": 1, "b": 0, "c": 3} |- { @[?(10 / @ > 0)] : mock.func }
                """,
                "Division by zero"),
            arguments("expressionPath_errorInExpression_bubblesUp",
                """
                {"items": [1, 2, 3]} |- { @.items[(1/0)] : mock.func }
                """,
                "Division by zero"),
            arguments("recursiveKeyPath_errorInNestedPath_shortCircuits",
                """
                {"a": {"items": [5, 2]}, "b": {"items": [3, 0]}} |- { @..items[?(10/@ > 0)] : mock.func }
                """,
                "Division by zero"),
            arguments("recursiveWildcard_errorInTailPath_shortCircuits",
                """
                {"data": [[5, 2], [3, 0]]} |- { @..*[?(10/@ > 0)] : mock.func }
                """,
                "Division by zero"),
            arguments("wildcardThenCondition_errorInDeepArray_shortCircuits",
                """
                [[1, 2], [3, 0], [5, 6]] |- { @[*][?(10/@ > 0)] : mock.func }
                """,
                "Division by zero")
        );
    }
    // @formatter:on

}
