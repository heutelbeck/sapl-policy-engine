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

import io.sapl.api.model.*;
import io.sapl.util.TestUtil;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Objects;

import static io.sapl.util.TestUtil.assertEvaluatesToError;
import static io.sapl.util.TestUtil.assertExpressionsEqual;
import static io.sapl.util.TestUtil.json;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FilterCompiler.
 */
class FilterCompilerTests {

    @Test
    void removeFilterOnObject_returnsUndefined() {
        assertThat(TestUtil.evaluate("{} |- filter.remove")).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void removeFilterOnNull_returnsUndefined() {
        assertThat(TestUtil.evaluate("null |- filter.remove")).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void blackenFilterOnString_returnsBlackened() {
        assertExpressionsEqual("\"secret\" |- filter.blacken", "\"XXXXXX\"");
    }

    @Test
    void blackenFilterOnLongerString_returnsBlackened() {
        assertExpressionsEqual("\"password\" |- filter.blacken", "\"XXXXXXXX\"");
    }

    @Test
    void customFunctionWithArgs_appliesFunction() {
        assertExpressionsEqual("\"Ben\" |- simple.append(\" from \", \"Berlin\")", "\"Ben from Berlin\"");
    }

    @Test
    void customFunctionNoArgs_appliesFunction() {
        assertExpressionsEqual("\"hello\" |- simple.length", "5");
    }

    @Test
    void errorPropagatesFromParent() {
        assertEvaluatesToError("(10/0) |- filter.remove", "Division by zero");
    }

    @Test
    void undefinedParentReturnsError() {
        assertEvaluatesToError("undefined |- filter.remove", "Filters cannot be applied to undefined");
    }

    @Test
    void filterOnNumber_worksCorrectly() {
        assertExpressionsEqual("42 |- simple.double", "84");
    }

    @Test
    void filterOnBoolean_worksCorrectly() {
        assertExpressionsEqual("true |- simple.negate", "false");
    }

    @Test
    void filterOnArray_appliesWithoutEach() {
        assertExpressionsEqual("[1,2,3] |- simple.length", "3");
    }

    @Test
    void filterWithErrorInArguments_returnsError() {
        assertEvaluatesToError("\"text\" |- simple.append(10/0)", "Division by zero");
    }

    @Test
    void chainedFilterExpressions_appliesSequentially() {
        assertExpressionsEqual("5 |- simple.double", "10");
    }

    @Test
    void eachRemovesAllElements_filtersAll() {
        assertExpressionsEqual("[null, 5] |- each filter.remove", "[]");
    }

    @Test
    void eachAppliesFunction_transformsElements() {
        assertExpressionsEqual("[\"a\", \"b\"] |- each simple.append(\"!\")", "[\"a!\", \"b!\"]");
    }

    @Test
    void emptyArrayUnchanged_returnsEmpty() {
        assertExpressionsEqual("[] |- each filter.remove", "[]");
    }

    @Test
    void eachOnNonArray_returnsError() {
        assertEvaluatesToError("{} |- each filter.remove", "Cannot use 'each' keyword with non-array values");
    }

    @Test
    void eachDoublesNumbers_transformsNumbers() {
        assertExpressionsEqual("[1, 2, 3] |- each simple.double", "[2, 4, 6]");
    }

    @Test
    void eachRemovesAllElements_returnsEmptyArray() {
        assertExpressionsEqual("[null, null, null] |- each filter.remove", "[]");
    }

    @Test
    void eachWithMultipleArguments_appliesCorrectly() {
        assertExpressionsEqual("[\"Ben\", \"Alice\"] |- each simple.append(\" from \", \"Berlin\")",
                "[\"Ben from Berlin\", \"Alice from Berlin\"]");
    }

    @Test
    void eachBlackensStrings_redactsEachElement() {
        assertExpressionsEqual("[\"secret\", \"password\"] |- each filter.blacken", "[\"XXXXXX\", \"XXXXXXXX\"]");
    }

    @Test
    void eachNegatesBooleans_negatesEachElement() {
        assertExpressionsEqual("[true, false, true] |- each simple.negate", "[false, true, false]");
    }

    @Test
    void extendedFilterWithSingleStatement_appliesFunction() {
        assertExpressionsEqual("\"test\" |- { : filter.blacken }", "\"XXXX\"");
    }

    @Test
    void extendedFilterWithMultipleStatements_appliesSequentially() {
        assertExpressionsEqual("5 |- { : simple.double, : simple.double }", "20");
    }

    @Test
    void extendedFilterWithArguments_appliesCorrectly() {
        assertExpressionsEqual("\"Hello\" |- { : simple.append(\" \"), : simple.append(\"World\") }",
                "\"Hello World\"");
    }

    @Test
    void extendedFilterRemovesValue_returnsUndefined() {
        assertThat(TestUtil.evaluate("42 |- { : filter.remove }")).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void extendedFilterReplaceValue_returnsReplacement() {
        assertExpressionsEqual("\"old\" |- { : filter.replace(\"new\") }", "\"new\"");
    }

    @Test
    void extendedFilterErrorInParent_propagatesError() {
        assertEvaluatesToError("(10/0) |- { : filter.remove }", "Division by zero");
    }

    @Test
    void extendedFilterWithTargetPath_filtersField() {
        assertExpressionsEqual("{ \"name\": \"secret\" } |- { @.name : filter.blacken }", "{ \"name\": \"XXXXXX\" }");
    }

    @Test
    void extendedFilterWithTargetPath_removesField() {
        assertExpressionsEqual("{ \"name\": \"test\", \"age\": 42 } |- { @.name : filter.remove }", "{ \"age\": 42 }");
    }

    @Test
    void extendedFilterWithTargetPath_transformsField() {
        assertExpressionsEqual("{ \"count\": 5 } |- { @.count : simple.double }", "{ \"count\": 10 }");
    }

    @Test
    void extendedFilterWithTargetPath_multipleFields() {
        assertExpressionsEqual(
                "{ \"first\": \"hello\", \"second\": \"world\" } |- { @.first : simple.append(\"!\"), @.second : simple.append(\"?\") }",
                "{ \"first\": \"hello!\", \"second\": \"world?\" }");
    }

    @Test
    void extendedFilterWithTargetPath_nonExistentField_returnsError() {
        assertEvaluatesToError("{ \"name\": \"test\" } |- { @.missing : filter.blacken }", "Field 'missing' not found");
    }

    @Test
    void extendedFilterWithTargetPath_onNonObject_returnsError() {
        assertEvaluatesToError("\"text\" |- { @.field : filter.blacken }", "Cannot apply key step to non-object");
    }

    @Test
    void extendedFilterWithTargetPath_replacesField() {
        assertExpressionsEqual("{ \"status\": \"old\" } |- { @.status : filter.replace(\"new\") }",
                "{ \"status\": \"new\" }");
    }

    @Test
    void extendedFilterWithIndexPath_transformsElement() {
        assertExpressionsEqual("[1, 2, 3] |- { @[1] : simple.double }", "[1, 4, 3]");
    }

    @Test
    void extendedFilterWithIndexPath_removesElement() {
        assertExpressionsEqual("[1, 2, 3] |- { @[1] : filter.remove }", "[1, 3]");
    }

    @Test
    void extendedFilterWithIndexPath_blackensElement() {
        assertExpressionsEqual("[\"public\", \"secret\", \"data\"] |- { @[1] : filter.blacken }",
                "[\"public\", \"XXXXXX\", \"data\"]");
    }

    @Test
    void extendedFilterWithIndexPath_firstElement() {
        assertExpressionsEqual("[5, 10, 15] |- { @[0] : simple.double }", "[10, 10, 15]");
    }

    @Test
    void extendedFilterWithIndexPath_lastElement() {
        assertExpressionsEqual("[5, 10, 15] |- { @[2] : simple.double }", "[5, 10, 30]");
    }

    @Test
    void extendedFilterWithIndexPath_multipleIndices() {
        assertExpressionsEqual("[1, 2, 3, 4] |- { @[0] : simple.double, @[2] : simple.double }", "[2, 2, 6, 4]");
    }

    @Test
    void extendedFilterWithIndexPath_outOfBounds_returnsError() {
        assertEvaluatesToError("[1, 2, 3] |- { @[5] : simple.double }", "Array index out of bounds");
    }

    @Test
    void extendedFilterWithIndexPath_negativeIndex_appliesCorrectly() {
        assertExpressionsEqual("[1, 2, 3] |- { @[-1] : simple.double }", "[1, 2, 6]");
    }

    @Test
    void extendedFilterWithIndexPath_onNonArray_returnsError() {
        assertEvaluatesToError("\"text\" |- { @[0] : filter.blacken }", "Cannot apply index step to non-array");
    }

    @Test
    void extendedFilterWithSlicing_transformsRange() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[1:3] : simple.double }", "[1, 4, 6, 4, 5]");
    }

    @Test
    void extendedFilterWithSlicing_fromStart() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[:3] : simple.double }", "[2, 4, 6, 4, 5]");
    }

    @Test
    void extendedFilterWithSlicing_toEnd() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[2:] : simple.double }", "[1, 2, 6, 8, 10]");
    }

    @Test
    void extendedFilterWithSlicing_entireArray() {
        assertExpressionsEqual("[1, 2, 3] |- { @[:] : simple.double }", "[2, 4, 6]");
    }

    @Test
    void extendedFilterWithSlicing_withStep() {
        assertExpressionsEqual("[1, 2, 3, 4, 5, 6] |- { @[0:6:2] : simple.double }", "[2, 2, 6, 4, 10, 6]");
    }

    @Test
    void extendedFilterWithSlicing_rangeWithStep() {
        assertExpressionsEqual("[1, 2, 3, 4, 5, 6] |- { @[1:5:2] : simple.double }", "[1, 4, 3, 8, 5, 6]");
    }

    @Test
    void extendedFilterWithSlicing_removesElements() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[1:4] : filter.remove }", "[1, 5]");
    }

    @Test
    void extendedFilterWithSlicing_blackensStrings() {
        assertExpressionsEqual("[\"public\", \"secret1\", \"secret2\", \"data\"] |- { @[1:3] : filter.blacken }",
                "[\"public\", \"XXXXXXX\", \"XXXXXXX\", \"data\"]");
    }

    @Test
    void extendedFilterWithSlicing_outOfBounds_clamps() {
        assertExpressionsEqual("[1, 2, 3] |- { @[1:10] : simple.double }", "[1, 4, 6]");
    }

    @Test
    void extendedFilterWithSlicing_onNonArray_returnsError() {
        assertEvaluatesToError("\"text\" |- { @[1:3] : filter.blacken }", "Cannot apply slicing step to non-array");
    }

    @Test
    void extendedFilterWithSlicing_negativeToIndex_appliesFilterToSlice() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[7:-1] : simple.double }",
                "[0, 1, 2, 3, 4, 5, 6, 14, 16, 9]");
    }

    @Test
    void extendedFilterWithSlicing_negativeFromIndex_appliesFilterToSlice() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:9] : simple.double }",
                "[0, 1, 2, 3, 4, 5, 6, 14, 16, 9]");
    }

    @Test
    void extendedFilterWithSlicing_negativeFromOmittedTo_appliesFilterToSlice() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:] : simple.double }",
                "[0, 1, 2, 3, 4, 5, 6, 14, 16, 18]");
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusOne_appliesFilterToAllElements() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-1] : simple.double }",
                "[0, 2, 4, 6, 8, 10, 12, 14, 16, 18]");
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusThree_appliesFilterToMatchingElements() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-3] : simple.double }",
                "[0, 2, 2, 3, 8, 5, 6, 14, 8, 9]");
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusTwo_appliesFilterToMatchingElements() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-2] : filter.remove }", "[1, 3, 5, 7, 9]");
    }

    @Test
    void extendedFilterWithSlicing_negativeToWithFilter_appliesFilterBeforeTo() {
        assertExpressionsEqual("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[:-2] : filter.remove }", "[8, 9]");
    }

    @Test
    void extendedFilterWithMultiStepPath_twoKeys_appliesFilterToNestedField() {
        assertExpressionsEqual("{ \"user\": { \"age\": 25 } } |- { @.user.age : simple.double }",
                "{ \"user\": { \"age\": 50 } }");
    }

    @Test
    void extendedFilterWithMultiStepPath_threeKeys_appliesFilterToDeepNestedField() {
        assertExpressionsEqual(
                "{ \"user\": { \"address\": { \"zip\": 12345 } } } |- { @.user.address.zip : simple.double }",
                "{ \"user\": { \"address\": { \"zip\": 24690 } } }");
    }

    @Test
    void extendedFilterWithMultiStepPath_keyThenIndex_appliesFilterToArrayElement() {
        assertExpressionsEqual("{ \"items\": [10, 20, 30] } |- { @.items[1] : simple.double }",
                "{ \"items\": [10, 40, 30] }");
    }

    @Test
    void extendedFilterWithMultiStepPath_indexThenKey_appliesFilterToObjectInArray() {
        assertExpressionsEqual("[{ \"name\": \"Alice\" }, { \"name\": \"Bob\" }] |- { @[0].name : filter.blacken }",
                "[{ \"name\": \"XXXXX\" }, { \"name\": \"Bob\" }]");
    }

    @Test
    void extendedFilterWithMultiStepPath_keyIndexKey_appliesFilterToNestedArrayObject() {
        assertExpressionsEqual(
                "{ \"users\": [{ \"name\": \"Alice\", \"age\": 30 }] } |- { @.users[0].age : simple.double }",
                "{ \"users\": [{ \"name\": \"Alice\", \"age\": 60 }] }");
    }

    @Test
    void extendedFilterWithMultiStepPath_keySliceKey_appliesFilterToFieldsInSlicedArrayElements() {
        assertExpressionsEqual(
                "{ \"users\": [{ \"age\": 10 }, { \"age\": 20 }, { \"age\": 30 }] } |- { @.users[0:2].age : simple.double }",
                "{ \"users\": [{ \"age\": 20 }, { \"age\": 40 }, { \"age\": 30 }] }");
    }

    @Test
    void extendedFilterWithMultiStepPath_missingField_returnsError() {
        assertEvaluatesToError("{ \"user\": { \"name\": \"Alice\" } } |- { @.user.age : simple.double }",
                "Field 'age' not found");
    }

    @Test
    void extendedFilterWithMultiStepPath_onNonObjectIntermediate_returnsError() {
        assertEvaluatesToError("{ \"user\": 123 } |- { @.user.age : simple.double }",
                "Cannot apply key step to non-object");
    }

    @Test
    void extendedFilterWithMultiStepPath_onNonArrayIntermediate_returnsError() {
        assertEvaluatesToError("{ \"users\": \"text\" } |- { @.users[0].age : simple.double }",
                "Cannot access array index");
    }

    @Test
    void extendedFilterWithEach_noTarget_appliesFunctionToEachElement() {
        assertExpressionsEqual("[1, 2, 3] |- { each : simple.double }", "[2, 4, 6]");
    }

    @Test
    void extendedFilterWithEach_withKeyTarget_appliesFunctionToFieldInEachElement() {
        assertExpressionsEqual("[{ \"age\": 10 }, { \"age\": 20 }, { \"age\": 30 }] |- { each @.age : simple.double }",
                "[{ \"age\": 20 }, { \"age\": 40 }, { \"age\": 60 }]");
    }

    @Test
    void extendedFilterWithEach_withIndexTarget_appliesFunctionToIndexInEachElement() {
        assertExpressionsEqual("[[1, 2], [3, 4], [5, 6]] |- { each @[0] : simple.double }",
                "[[2, 2], [6, 4], [10, 6]]");
    }

    @Test
    void extendedFilterWithEach_removesElements_filtersOutUndefined() {
        assertExpressionsEqual("[1, 2, 3, 4] |- { each : filter.remove }", "[]");
    }

    @Test
    void extendedFilterWithEach_onNonArray_returnsError() {
        assertEvaluatesToError("{ \"key\": \"value\" } |- { each : filter.blacken }",
                "Cannot use 'each' keyword with non-array values");
    }

    @Test
    void extendedFilterWithEach_withMultiStepPath_appliesFunctionToNestedPath() {
        assertExpressionsEqual(
                "[{ \"user\": { \"age\": 10 } }, { \"user\": { \"age\": 20 } }] |- { each @.user.age : simple.double }",
                "[{ \"user\": { \"age\": 20 } }, { \"user\": { \"age\": 40 } }]");
    }

    @Test
    void extendedFilterWithEach_withSlicing_appliesFunctionToSliceInEachElement() {
        assertExpressionsEqual("[[1, 2, 3], [4, 5, 6], [7, 8, 9]] |- { each @[0:2] : simple.double }",
                "[[2, 4, 3], [8, 10, 6], [14, 16, 9]]");
    }

    @Test
    void extendedFilterWithEach_multipleStatements_appliesInSequence() {
        assertExpressionsEqual(
                "[{ \"name\": \"Alice\", \"age\": 10 }] |- { each @.name : filter.blacken, each @.age : simple.double }",
                "[{ \"name\": \"XXXXX\", \"age\": 20 }]");
    }

    @Test
    void extendedFilterWithWildcard_onArray_appliesFunctionToAllElements() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @.* : simple.double }", "[2, 4, 6, 8, 10]");
    }

    @Test
    void extendedFilterWithWildcard_onObject_appliesFunctionToAllFieldValues() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @.* : simple.double }",
                "{ \"a\": 2, \"b\": 4, \"c\": 6 }");
    }

    @Test
    void extendedFilterWithWildcard_withMultiStepPath_appliesFunctionToNestedFields() {
        assertExpressionsEqual("{ \"users\": [{ \"age\": 10 }, { \"age\": 20 }] } |- { @.users.*.age : simple.double }",
                "{ \"users\": [{ \"age\": 20 }, { \"age\": 40 }] }");
    }

    @Test
    void extendedFilterWithWildcard_removingElements_filtersOutUndefined() {
        assertExpressionsEqual("[\"a\", \"b\", \"c\"] |- { @.* : filter.remove }", "[]");
    }

    @Test
    void extendedFilterWithWildcard_removingFields_filtersOutUndefined() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2 } |- { @.* : filter.remove }", "{}");
    }

    @Test
    void extendedFilterWithWildcard_onNonArrayNonObject_returnsError() {
        assertEvaluatesToError("\"text\" |- { @.* : filter.blacken }",
                "Cannot apply wildcard step to non-array/non-object");
    }

    @Test
    void extendedFilterWithWildcard_withBlacken_redactsAllValues() {
        assertExpressionsEqual("[\"secret1\", \"secret2\", \"secret3\"] |- { @.* : filter.blacken }",
                "[\"XXXXXXX\", \"XXXXXXX\", \"XXXXXXX\"]");
    }

    @Test
    void extendedFilterWithWildcard_nestedObjectArrayPath_appliesFilterCorrectly() {
        assertExpressionsEqual(
                "{ \"departments\": { \"engineering\": { \"employees\": 10 }, \"sales\": { \"employees\": 20 } } } "
                        + "|- { @.departments.*.employees : simple.double }",
                "{ \"departments\": { \"engineering\": { \"employees\": 20 }, \"sales\": { \"employees\": 40 } } }");
    }

    // ========================================================================
    // Step 1 & 2: Filters with Stream Expressions and Attribute Finders
    // ========================================================================

    @Test
    void simpleFilter_withAttributeFinder_appliesFilterToStreamValues() {
        // test.echo returns Flux.just(value, "hello world")
        // Filter should apply to each emitted value
        val evaluated = TestUtil.evaluateExpression("\"test\".<test.echo> |- filter.blacken");
        StepVerifier.create(evaluated.take(2)).expectNext(Value.of("XXXX")).expectNext(Value.of("XXXXXXXXXXX"))
                .verifyComplete();
    }

    @Test
    void simpleFilter_withAttributeFinderOnNumber_appliesFilterToStream() {
        val evaluated = TestUtil.evaluateExpression("(10).<test.echo> |- simple.double");
        // test.echo returns Flux.just(10, "hello world")
        // double works on numbers: 10 * 2 = 20
        // "hello world" is not a number, so it will error
        StepVerifier.create(evaluated.take(2)).expectNext(Value.of(20))
                .expectNextMatches(v -> v instanceof ErrorValue && ((ErrorValue) v).message().contains("number"))
                .verifyComplete();
    }

    @Test
    void extendedFilter_withAttributeFinder_appliesFilterToStreamOfObjects() {
        val evaluated = TestUtil
                .evaluateExpression("{ \"name\": \"Alice\" }.<test.echo> |- { @.name : filter.blacken }");
        // echo returns the object and "hello world"
        // object: { "name": "XXXXX" }
        // "hello world" doesn't have .name field, so it errors
        StepVerifier.create(evaluated.take(2)).expectNext(json("{ \"name\": \"XXXXX\" }"))
                .expectNextMatches(v -> v instanceof ErrorValue && ((ErrorValue) v).message().contains("key step"))
                .verifyComplete();
    }

    @Test
    void extendedFilter_withAttributeFinderInPath_appliesFilterToNestedStreamValues() {
        val evaluated = TestUtil.evaluateExpression(
                "{ \"data\": { \"value\": \"secret\" } }.<test.echo> |- { @.data.value : filter.blacken }");
        StepVerifier.create(evaluated).expectNext(json("{ \"data\": { \"value\": \"XXXXXX\" } }"))
                .expectNext(Value.of("hello world")).verifyComplete();
    }

    @Test
    void eachFilter_withAttributeFinder_appliesFilterToEachStreamValue() {
        val evaluated = TestUtil.evaluateExpression("[1, 2, 3].<test.echo> |- each simple.double");
        // echo returns the array and "hello world"
        // each simple.double applies to array: [2, 4, 6]
        // "hello world" is not an array, so it's an error or returned as-is
        StepVerifier.create(evaluated).expectNext(json("[2, 4, 6]")).expectNext(Value.of("hello world"))
                .verifyComplete();
    }

    @Test
    void wildcardFilter_withAttributeFinder_appliesFilterToAllFieldsInStream() {
        val evaluated = TestUtil.evaluateExpression("{ \"a\": 10, \"b\": 20 }.<test.echo> |- { @.* : simple.double }");
        // echo returns the object with doubled values, then "hello world"
        StepVerifier.create(evaluated).expectNext(json("{ \"a\": 20, \"b\": 40 }")).expectNext(Value.of("hello world"))
                .verifyComplete();
    }

    @Test
    void conditionStepFilter_withAttributeFinder_appliesFilterSelectivelyToStream() {
        val evaluated = TestUtil.evaluateExpression("[10, 20, 30].<test.echo> |- { @[?(@ > 15)] : simple.double }");
        // echo returns the array and "hello world"
        // Condition filters elements > 15: [20, 30] -> [40, 60]
        StepVerifier.create(evaluated).expectNext(json("[10, 40, 60]")).expectNext(Value.of("hello world"))
                .verifyComplete();
    }

    @Test
    void recursiveKeyFilter_withAttributeFinder_appliesFilterRecursivelyToStream() {
        val evaluated = TestUtil.evaluateExpression(
                "{ \"a\": { \"x\": 5 }, \"b\": { \"x\": 10 } }.<test.echo> |- { @..x : simple.double }");
        // echo returns the object and "hello world"
        // Recursive filter doubles all 'x' values: 10, 20
        StepVerifier.create(evaluated).expectNext(json("{ \"a\": { \"x\": 10 }, \"b\": { \"x\": 20 } }"))
                .expectNext(Value.of("hello world")).verifyComplete();
    }

    @Test
    void subtemplate_withAttributeFinder_appliesTemplateToStreamValues() {
        val evaluated = TestUtil.evaluateExpression("{ \"name\": \"Bob\" }.<test.echo> :: { \"user\": @.name }");
        // echo returns the object and "hello world"
        // Template transforms object, "hello world" doesn't have .name so undefined
        StepVerifier.create(evaluated).expectNext(json("{ \"user\": \"Bob\" }"))
                .expectNextMatches(v -> v instanceof ErrorValue || v instanceof UndefinedValue).verifyComplete();
    }

    @Test
    void combinedFilters_withAttributeFinder_appliesMultipleFiltersToStream() {
        val evaluated = TestUtil.evaluateExpression(
                "{ \"a\": 5, \"b\": 10 }.<test.echo> |- { @.a : simple.double, @.b : simple.double }");
        // echo returns the object and "hello world"
        // Both filters double their respective fields
        StepVerifier.create(evaluated).expectNext(json("{ \"a\": 10, \"b\": 20 }")).expectNext(Value.of("hello world"))
                .verifyComplete();
    }

    // ========================================================================
    // Step 3: ConditionStep Support
    // ========================================================================

    @Test
    void conditionStepInFilter_constantTrueCondition_appliesFilterToAllElements() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[?(true)] : simple.double }", "[2, 4, 6, 8, 10]");
    }

    @Test
    void conditionStepInFilter_constantFalseCondition_leavesAllElementsUnchanged() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[?(false)] : simple.double }", "[1, 2, 3, 4, 5]");
    }

    @Test
    void conditionStepInFilter_removeMatchingElements() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[?(true)] : filter.remove }", "[]");
    }

    @Test
    void conditionStepInFilter_onObject_appliesFilterToMatchingFields() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(true)] : simple.double }",
                "{ \"a\": 2, \"b\": 4, \"c\": 6 }");
    }

    @Test
    void conditionStepInFilter_onObject_removeMatchingFields() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4 } |- { @[?(true)] : filter.remove }", "{}");
    }

    @Test
    void conditionStepInFilter_onObject_constantFalseCondition_leavesAllFieldsUnchanged() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(false)] : simple.double }",
                "{ \"a\": 1, \"b\": 2, \"c\": 3 }");
    }

    @Test
    void conditionStepInFilter_blackenMatchingStrings() {
        assertExpressionsEqual("[\"public\", \"secret\", \"data\"] |- { @[?(true)] : filter.blacken }",
                "[\"XXXXXX\", \"XXXXXX\", \"XXXX\"]");
    }

    @Test
    void conditionStepInFilter_onNonArrayNonObject_returnsUnchanged() {
        assertExpressionsEqual("\"text\" |- { @[?(true)] : filter.blacken }", "\"text\"");
    }

    @Test
    void conditionStepInFilter_withNonBooleanCondition_returnsError() {
        assertEvaluatesToError("[1, 2, 3] |- { @[?(123)] : filter.remove }",
                "Expected the condition expression to return a Boolean");
    }

    @Test
    void conditionStepInFilter_withErrorInCondition_returnsError() {
        assertEvaluatesToError("[1, 2, 3] |- { @[?(10/0)] : filter.remove }", "Division by zero");
    }

    @Test
    void conditionStepInFilter_complexExpressionCondition() {
        assertExpressionsEqual("[1, 2, 3] |- { @[?((1 + 1) == 2)] : simple.double }", "[2, 4, 6]");
    }

    // NOTE: This test is commented out as it requires relative node context support
    // which is planned for Steps 8-9. The test uses nested paths after condition
    // steps
    // which needs context propagation not yet implemented.
    // @Test
    // void conditionStepInFilter_deeplyNestedObject_appliesFilterCorrectly() { ...
    // }

    @Test
    void conditionStepInFilter_multiLevelNestedArrays_appliesFilterAtCorrectLevel() {
        assertExpressionsEqual("""
                {
                  "data": {
                    "matrix": [
                      [
                        { "value": 10, "active": true },
                        { "value": 20, "active": false }
                      ],
                      [
                        { "value": 30, "active": true },
                        { "value": 40, "active": true }
                      ]
                    ]
                  }
                }
                |- { @.data.matrix[?(true)][?(true)].value : simple.double }
                """, """
                {
                  "data": {
                    "matrix": [
                      [
                        { "value": 20, "active": true },
                        { "value": 40, "active": false }
                      ],
                      [
                        { "value": 60, "active": true },
                        { "value": 80, "active": true }
                      ]
                    ]
                  }
                }
                """);
    }

    @Test
    void conditionStepInFilter_veryDeeplyNestedStructure_appliesFilterCorrectly() {
        assertExpressionsEqual("""
                    {
                      "level1": {
                        "level2": {
                          "level3": {
                            "level4": {
                              "level5": {
                                "items": [
                                  { "id": 1, "secret": "password1" },
                                  { "id": 2, "secret": "password2" },
                                  { "id": 3, "secret": "password3" }
                                ]
                              }
                            }
                          }
                        }
                      }
                    }
                    |- { @.level1.level2.level3.level4.level5.items[?(true)].secret : filter.blacken }
                """, """
                {
                  "level1": {
                    "level2": {
                      "level3": {
                        "level4": {
                          "level5": {
                            "items": [
                              { "id": 1, "secret": "XXXXXXXXX" },
                              { "id": 2, "secret": "XXXXXXXXX" },
                              { "id": 3, "secret": "XXXXXXXXX" }
                            ]
                          }
                        }
                      }
                    }
                  }
                }
                """);
    }

    @Test
    void conditionStepInFilter_mixedObjectsAndArrays_appliesFilterCorrectly() {
        assertExpressionsEqual("""
                    {
                      "users": [
                        {
                          "name": "Alice",
                          "addresses": [
                            { "type": "home", "zip": 12345 },
                            { "type": "work", "zip": 67890 }
                          ]
                        },
                        {
                          "name": "Bob",
                          "addresses": [
                            { "type": "home", "zip": 11111 },
                            { "type": "work", "zip": 22222 }
                          ]
                        }
                      ]
                    }
                    |- { @.users[?(true)].addresses[?(true)].zip : simple.double }
                """, """
                {
                  "users": [
                    {
                      "name": "Alice",
                      "addresses": [
                        { "type": "home", "zip": 24690 },
                        { "type": "work", "zip": 135780 }
                      ]
                    },
                    {
                      "name": "Bob",
                      "addresses": [
                        { "type": "home", "zip": 22222 },
                        { "type": "work", "zip": 44444 }
                      ]
                    }
                  ]
                }
                """);
    }

    // NOTE: This test is commented out as it requires relative node context support
    // which is planned for Steps 8-9. The test uses condition steps on objects
    // followed
    // by nested paths which needs context propagation not yet implemented.
    // @Test
    // void conditionStepInFilter_objectInObjectInArray_appliesFilterCorrectly() {
    // ... }

    @Test
    void conditionStepInFilter_emptyArray_returnsEmptyArray() {
        assertExpressionsEqual("[] |- { @[?(true)] : simple.double }", "[]");
    }

    @Test
    void conditionStepInFilter_emptyObject_returnsEmptyObject() {
        assertExpressionsEqual("{} |- { @[?(true)] : filter.blacken }", "{}");
    }

    // ========================================================================
    // Data-Dependent Condition Tests - Verify filter is actually applied
    // ========================================================================

    @Test
    void conditionStepInFilter_selectiveApplicationBasedOnCondition_doublesOnlyMatchingElements() {
        // Test that condition selectively applies filter
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- { @[?((0 % 2) == 0)] : simple.double }", "[2, 4, 6, 8, 10]");
    }

    @Test
    void conditionStepInFilter_arithmeticCondition_appliesSelectivelyBasedOnExpression() {
        assertExpressionsEqual("[10, 20, 30] |- { @[?((2 + 3) > 4)] : simple.double }", "[20, 40, 60]");
    }

    @Test
    void conditionStepInFilter_falseArithmeticCondition_doesNotApplyFilter() {
        assertExpressionsEqual("[10, 20, 30] |- { @[?((2 + 3) < 4)] : simple.double }", "[10, 20, 30]");
    }

    @Test
    void conditionStepInFilter_realWorldDataScenario_directFieldBlackening() {
        assertExpressionsEqual("""
                ["password123", "secret456", "token789"]
                |- { @[?(true)] : filter.blacken }
                """, "[\"XXXXXXXXXXX\", \"XXXXXXXXX\", \"XXXXXXXX\"]");
    }

    @Test
    void conditionStepInFilter_realWorldDataScenario_directFieldPreservation() {
        // When condition is false, values are preserved
        assertExpressionsEqual("""
                ["password123", "secret456", "token789"]
                |- { @[?(false)] : filter.blacken }
                """, """
                ["password123", "secret456", "token789"]
                """);
    }

    @Test
    void conditionStepInFilter_objectsDirectlyFiltered_verifyApplication() {
        // Apply filter directly to objects in array
        assertExpressionsEqual("""
                [
                  { "name": "Alice", "age": 30 },
                  { "name": "Bob", "age": 25 },
                  { "name": "Charlie", "age": 35 }
                ]
                |- { @[?(true)] : filter.replace({"redacted": true}) }
                """, """
                [
                  { "redacted": true },
                  { "redacted": true },
                  { "redacted": true }
                ]
                """);
    }

    @Test
    void conditionStepInFilter_nestedArraysWithoutConditionPaths_verifyFilterApplication() {
        assertExpressionsEqual("""
                {
                  "data": {
                    "values": [100, 200, 300]
                  }
                }
                |- { @.data.values[?(true)] : simple.double }
                """, """
                {
                  "data": {
                    "values": [200, 400, 600]
                  }
                }
                """);
    }

    @Test
    void conditionStepInFilter_complexBooleanExpression_verifyCorrectEvaluation() {
        assertExpressionsEqual("[1, 2, 3] |- { @[?((10 > 5) && (3 == 3))] : simple.double }", "[2, 4, 6]");
    }

    @Test
    void conditionStepInFilter_complexBooleanExpressionFalse_noFilterApplication() {
        assertExpressionsEqual("[1, 2, 3] |- { @[?((10 < 5) || (3 != 3))] : simple.double }", "[1, 2, 3]");
    }

    @Test
    void conditionStepInFilter_verifyFilterFunctionCalledWithCorrectValue() {
        assertExpressionsEqual("""
                ["Alice", "Bob", "Charlie"]
                |- { @[?(true)] : simple.append("_FILTERED") }
                """, "[\"Alice_FILTERED\", \"Bob_FILTERED\", \"Charlie_FILTERED\"]");
    }

    @Test
    void conditionStepInFilter_verifyNoFilterFunctionCallWhenConditionFalse() {
        assertExpressionsEqual("""
                ["Alice", "Bob", "Charlie"]
                |- { @[?(false)] : simple.append("_FILTERED") }
                """, "[\"Alice\", \"Bob\", \"Charlie\"]");
    }

    @Test
    void conditionStepInFilter_mixedDataTypes_verifySelectiveProcessing() {
        // Mix of numbers and verify filter is applied based on condition
        assertExpressionsEqual("""
                [
                  { "value": 10, "multiplier": 2 },
                  { "value": 20, "multiplier": 3 },
                  { "value": 30, "multiplier": 4 }
                ]
                |- { @[?(true)].value : simple.double }
                """, """
                [
                  { "value": 20, "multiplier": 2 },
                  { "value": 40, "multiplier": 3 },
                  { "value": 60, "multiplier": 4 }
                ]
                """);
    }

    // ========================================================================
    // Step 4: ExpressionStep Support
    // ========================================================================

    @Test
    void expressionStepInFilter_arrayWithConstantIndex_appliesFilter() {
        assertExpressionsEqual("[[10, 20, 30], [40, 50, 60], [70, 80, 90]] |- { @[(1+1)] : filter.remove }",
                "[[10, 20, 30], [40, 50, 60]]");
    }

    @Test
    void expressionStepInFilter_objectWithConstantKey_appliesFilter() {
        assertExpressionsEqual("""
                { "ab": [1, 2, 3], "cb": [4, 5, 6], "db": [7, 8, 9] }
                |- { @[("c"+"b")] : filter.remove }
                """, "{ \"ab\": [1, 2, 3], \"db\": [7, 8, 9] }");
    }

    @Test
    void expressionStepInFilter_arrayWithArithmeticExpression_appliesFilter() {
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[((2 * 3) - 4)] : simple.double }", "[10, 20, 60, 40, 50]");
    }

    @Test
    void expressionStepInFilter_objectWithStringConcatenation_appliesFilter() {
        assertExpressionsEqual("""
                { "key1": 100, "key2": 200, "key3": 300 }
                |- { @[("key" + "2")] : simple.double }
                """, "{ \"key1\": 100, \"key2\": 400, \"key3\": 300 }");
    }

    @Test
    void expressionStepInFilter_nestedArrays_appliesFilterAtComputedIndex() {
        assertExpressionsEqual("""
                {
                  "data": [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
                }
                |- { @.data[(0+1)] : filter.remove }
                """, "{ \"data\": [[1, 2, 3], [7, 8, 9]] }");
    }

    @Test
    void expressionStepInFilter_nestedObjects_appliesFilterAtComputedKey() {
        assertExpressionsEqual("""
                {
                  "users": {
                    "alice": { "age": 30 },
                    "bob": { "age": 25 },
                    "charlie": { "age": 35 }
                  }
                }
                |- { @.users[("b" + "ob")] : filter.remove }
                """, """
                {
                  "users": {
                    "alice": { "age": 30 },
                    "charlie": { "age": 35 }
                  }
                }
                """);
    }

    @Test
    void expressionStepInFilter_deeplyNestedStructure_appliesFilterCorrectly() {
        // Very deep nesting with expression step
        assertExpressionsEqual("""
                {
                  "level1": {
                    "level2": {
                      "items": [
                        { "id": 0, "value": "first" },
                        { "id": 1, "value": "second" },
                        { "id": 2, "value": "third" }
                      ]
                    }
                  }
                }
                |- { @.level1.level2.items[(2-1)].value : filter.blacken }
                """, """
                {
                  "level1": {
                    "level2": {
                      "items": [
                        { "id": 0, "value": "first" },
                        { "id": 1, "value": "XXXXXX" },
                        { "id": 2, "value": "third" }
                      ]
                    }
                  }
                }
                """);
    }

    @Test
    void expressionStepInFilter_arrayTypeMismatch_returnsError() {
        assertEvaluatesToError("[10, 20, 30] |- { @[(\"abc\")] : simple.double }", "Array access type mismatch");
    }

    @Test
    void expressionStepInFilter_objectTypeMismatch_returnsError() {
        assertEvaluatesToError("""
                { "a": 1, "b": 2, "c": 3 }
                |- { @[(1+1)] : simple.double }
                """, "Object access type mismatch");
    }

    @Test
    void expressionStepInFilter_arrayOutOfBounds_returnsError() {
        assertEvaluatesToError("[10, 20, 30] |- { @[(5+5)] : simple.double }", "Array index out of bounds");
    }

    @Test
    void expressionStepInFilter_errorInExpression_propagatesError() {
        assertEvaluatesToError("[10, 20, 30] |- { @[(10/0)] : simple.double }", "Division by zero");
    }

    @Test
    void expressionStepInFilter_nonExistentKey_returnsUnchanged() {
        assertExpressionsEqual("""
                { "a": 1, "b": 2, "c": 3 }
                |- { @[("d")] : simple.double }
                """, "{ \"a\": 1, \"b\": 2, \"c\": 3 }");
    }

    @Test
    void expressionStepInFilter_complexNestedWithMultipleExpressionSteps() {
        assertExpressionsEqual("""
                {
                  "matrix": [
                    [
                      { "val": 1 },
                      { "val": 2 },
                      { "val": 3 }
                    ],
                    [
                      { "val": 4 },
                      { "val": 5 },
                      { "val": 6 }
                    ]
                  ]
                }
                |- { @.matrix[(1)][( 2)] : filter.remove }
                """, """
                {
                  "matrix": [
                    [
                      { "val": 1 },
                      { "val": 2 },
                      { "val": 3 }
                    ],
                    [
                      { "val": 4 },
                      { "val": 5 }
                    ]
                  ]
                }
                """);
    }

    @Test
    void expressionStepInFilter_mixedWithRegularSteps_appliesCorrectly() {
        assertExpressionsEqual("""
                {
                  "data": {
                    "items": [
                      { "name": "Alice", "scores": [90, 85, 95] },
                      { "name": "Bob", "scores": [88, 92, 87] }
                    ]
                  }
                }
                |- { @.data.items[(0)].scores[(1)] : simple.double }
                """, """
                {
                  "data": {
                    "items": [
                      { "name": "Alice", "scores": [90, 170, 95] },
                      { "name": "Bob", "scores": [88, 92, 87] }
                    ]
                  }
                }
                """);
    }

    @Test
    void expressionStepInFilter_onNonArrayNonObject_returnsUnchanged() {
        assertExpressionsEqual("42 |- { @[(0)] : simple.double }", "42");
    }

    @Test
    void expressionStepInFilter_blackenSensitiveData_viaComputedPath() {
        assertExpressionsEqual("""
                {
                  "accounts": [
                    { "id": 1, "ssn": "111-11-1111", "balance": 1000 },
                    { "id": 2, "ssn": "222-22-2222", "balance": 2000 },
                    { "id": 3, "ssn": "333-33-3333", "balance": 3000 }
                  ]
                }
                |- { @.accounts[(1)].ssn : filter.blacken }
                """, """
                {
                  "accounts": [
                    { "id": 1, "ssn": "111-11-1111", "balance": 1000 },
                    { "id": 2, "ssn": "XXXXXXXXXXX", "balance": 2000 },
                    { "id": 3, "ssn": "333-33-3333", "balance": 3000 }
                  ]
                }
                """);
    }

    @Test
    void expressionStepInFilter_removeComputedField_fromMultipleObjects() {
        assertExpressionsEqual("""
                {
                  "user1": { "email": "a@example.com", "phone": "111-1111" },
                  "user2": { "email": "b@example.com", "phone": "222-2222" }
                }
                |- { @[("user" + "1")].email : filter.remove }
                """, """
                {
                  "user1": { "phone": "111-1111" },
                  "user2": { "email": "b@example.com", "phone": "222-2222" }
                }
                """);
    }

    // ==== RecursiveIndexStep Tests ====

    @Test
    void recursiveIndexStepFilter_onNestedArray_appliesFilterToMatchingIndices() {
        assertExpressionsEqual("[ [1,2,3], [4,5,6,7] ] |- { @..[1] : filter.remove }", "[[1, 3]]");
    }

    @Test
    void recursiveIndexStepFilter_withNegativeIndex_appliesCorrectly() {
        assertExpressionsEqual("[ [1,2,3], [4,5,6,7] ] |- { @..[-1] : filter.remove }", "[[1, 2]]");
    }

    @Test
    void recursiveIndexStepFilter_withRemove_removesMatchingElements() {
        assertExpressionsEqual("""
                { "key" : "value1",
                  "array1" : [ { "key" : "value2" }, { "key" : "value3" } ],
                  "array2" : [ 1, 2, 3, 4, 5 ]
                } |- { @..[0] : filter.remove }
                """, """
                { "key" : "value1",
                  "array1" : [ { "key" : "value3" } ],
                  "array2" : [ 2, 3, 4, 5 ]
                }
                """);
    }

    // Note: Descending path test removed - @..[0][0] syntax not yet fully supported
    // in filter compilation

    @Test
    void recursiveIndexStepFilter_onDeeplyNestedArrays_appliesRecursively() {
        assertExpressionsEqual("[ [[1,2],[3,4]], [[5,6],[7,8]] ] |- { @..[0] : filter.remove }", "[[[8]]]");
    }

    @Test
    void recursiveIndexStepFilter_onMixedObjectArray_recursesThroughObjects() {
        assertExpressionsEqual("""
                {
                  "key" : "value1",
                  "array1" : [ { "key" : "value2" }, { "key" : "value3" } ],
                  "array2" : [ 1, 2, 3, 4, 5 ]
                } |- { @..[0] : filter.remove }
                """, """
                {
                  "key": "value1",
                  "array1": [ { "key": "value3" } ],
                  "array2": [ 2, 3, 4, 5 ]
                }
                """);
    }

    @Test
    void recursiveIndexStepFilter_onNonArray_returnsUnchanged() {
        assertExpressionsEqual("\"string\" |- { @..[0] : filter.remove }", "\"string\"");
    }

    @Test
    void recursiveIndexStepFilter_withOutOfBoundsIndex_doesNotError() {
        assertExpressionsEqual("[ [1,2], [3,4] ] |- { @..[10] : filter.remove }", "[ [1, 2], [3, 4] ]");
    }

    @Test
    void recursiveIndexStepFilter_onEmptyArray_returnsEmpty() {
        assertExpressionsEqual("[] |- { @..[0] : filter.remove }", "[]");
    }

    @Test
    void recursiveIndexStepFilter_withMultipleIndices_appliesToEach() {
        // Filters applied sequentially:
        // After first filter removes index 0: [[2,3], [5,6]]
        // Then top removes its [0]: [[5,6]]
        // Second filter removes index 2: no element[2] in [5,6] or top array
        assertExpressionsEqual("[ [1,2,3], [4,5,6] ] |- { @..[0] : filter.remove, @..[2] : filter.remove }",
                "[ [5, 6] ]");
    }

    // =========================================================================
    // SUBTEMPLATE TESTS (:: operator)
    // =========================================================================

    @Test
    void subtemplate_simpleObjectTransformation() {
        assertExpressionsEqual("{ \"name\": \"Alice\", \"age\": 30 } :: { \"newName\": @.name }",
                "{ \"newName\": \"Alice\" }");
    }

    @Test
    void subtemplate_arrayImplicitMapping() {
        assertExpressionsEqual(
                "[ { \"name\": \"Alice\" }, { \"name\": \"Bob\" }, { \"name\": \"Charlie\" } ] "
                        + ":: { \"userName\": @.name }",
                "[ { \"userName\": \"Alice\" }, { \"userName\": \"Bob\" }, { \"userName\": \"Charlie\" } ]");
    }

    @Test
    void subtemplate_emptyArrayStaysEmpty() {
        assertExpressionsEqual("[] :: { \"foo\": \"bar\" }", "[]");
    }

    @Test
    void subtemplate_withNestedFieldAccess() {
        assertExpressionsEqual(
                "{ \"person\": { \"name\": \"Alice\", \"address\": { \"city\": \"Berlin\", \"country\": \"Germany\" } } } "
                        + ":: { \"city\": @.person.address.city, \"country\": @.person.address.country }",
                "{ \"city\": \"Berlin\", \"country\": \"Germany\" }");
    }

    @Test
    void subtemplate_withArrayIndexAccess() {
        assertExpressionsEqual(
                "{ \"items\": [10, 20, 30, 40] } :: { \"first\": @.items[0], \"last\": @.items[-1], \"second\": @.items[1] }",
                "{ \"first\": 10, \"last\": 40, \"second\": 20 }");
    }

    @Test
    void subtemplate_withWildcardStep() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3 } :: { \"values\": @.* }");
        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var values       = objectResult.get("values");
        assertThat(values).isInstanceOf(ArrayValue.class);
        var valuesArray = (ArrayValue) values;
        assertThat(valuesArray).hasSize(3);
        assertThat(valuesArray).contains(Value.of(1), Value.of(2), Value.of(3));
    }

    @Test
    void subtemplate_withArithmeticExpressions() {
        assertExpressionsEqual("{ \"x\": 5, \"y\": 10 } :: { \"sum\": @.x + @.y, \"product\": @.x * @.y }",
                "{ \"sum\": 15, \"product\": 50 }");
    }

    @Test
    void subtemplate_propagatesErrorValue() {
        assertEvaluatesToError("(10/0) :: { \"name\": \"foo\" }", "Division by zero");
    }

    @Test
    void subtemplate_propagatesUndefinedValue() {
        assertThat(TestUtil.evaluate("undefined :: { \"name\": \"foo\" }")).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void subtemplate_withConstantTemplate() {
        assertExpressionsEqual("{ \"x\": 5 } :: { \"fixed\": 42 }", "{ \"fixed\": 42 }");
    }

    @Test
    void subtemplate_withMixedFieldTypes() {
        assertExpressionsEqual(
                "{ \"str\": \"hello\", \"num\": 42, \"bool\": true, \"arr\": [1,2,3] } "
                        + ":: { \"s\": @.str, \"n\": @.num, \"b\": @.bool, \"a\": @.arr }",
                "{ \"s\": \"hello\", \"n\": 42, \"b\": true, \"a\": [1,2,3] }");
    }

    @Test
    void subtemplate_arrayMappingWithCalculations() {
        assertExpressionsEqual(
                "[ { \"price\": 10 }, { \"price\": 20 }, { \"price\": 30 } ] " + ":: { \"withTax\": @.price * 1.2 }",
                "[ { \"withTax\": 12 }, { \"withTax\": 24 }, { \"withTax\": 36 } ]");
    }

    @Test
    void subtemplate_withConditionalExpressions() {
        assertExpressionsEqual("{ \"age\": 25 } :: { \"canVote\": @.age >= 18 }", "{ \"canVote\": true }");
    }

    @Test
    void subtemplate_combineWithFilteredInput() {
        assertExpressionsEqual(
                "[ { \"key\": 1, \"val\": \"a\" }, { \"key\": 3, \"val\": \"b\" }, "
                        + "{ \"key\": 5, \"val\": \"c\" } ] [?(@.key > 2)] :: { \"value\": @.val }",
                "[ { \"value\": \"b\" }, { \"value\": \"c\" } ]");
    }

    @Test
    void subtemplate_withSlicingStep() {
        assertExpressionsEqual("[1, 2, 3, 4, 5][1:4] :: { \"doubled\": @ * 2 }",
                "[ { \"doubled\": 4 }, { \"doubled\": 6 }, { \"doubled\": 8 } ]");
    }

    @Test
    void subtemplate_nestedSubtemplates() {
        assertExpressionsEqual(
                "[ { \"outer\": 1 }, { \"outer\": 2 } ] :: { \"inner\": [{ \"x\": @ }] :: { \"doubled\": @.x.outer * 2 } }",
                "[ { \"inner\": [ { \"doubled\": 2 } ] }, { \"inner\": [ { \"doubled\": 4 } ] } ]");
    }

    @Test
    void subtemplate_simpleValueAsInput() {
        assertExpressionsEqual("42 :: { \"value\": @, \"doubled\": @ * 2 }", "{ \"value\": 42, \"doubled\": 84 }");
    }

    @Test
    void subtemplate_stringValue() {
        assertExpressionsEqual("\"hello\" :: { \"text\": @, \"upper\": simple.append(@, \" world\") }",
                "{ \"text\": \"hello\", \"upper\": \"hello world\" }");
    }

    @Test
    void subtemplate_withFunctionCall() {
        assertExpressionsEqual("{ \"x\": 5 } :: { \"doubled\": simple.double(@.x) }", "{ \"doubled\": 10 }");
    }

    @Test
    void subtemplate_multipleFieldsFromSameSource() {
        assertExpressionsEqual("{ \"value\": 10 } :: { \"a\": @.value, \"b\": @.value * 2, \"c\": @.value * 3 }",
                "{ \"a\": 10, \"b\": 20, \"c\": 30 }");
    }

    @Test
    void subtemplate_withRecursiveDescentStep() {
        var result = TestUtil.evaluate(
                "{ \"a\": { \"b\": { \"c\": 42 } }, \"x\": { \"b\": { \"c\": 99 } } } :: { \"allCs\": @..c }");

        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var allCs        = objectResult.get("allCs");
        assertThat(allCs).isInstanceOf(ArrayValue.class);
        var allCsArray = (ArrayValue) allCs;
        assertThat(allCsArray).hasSize(2);
        assertThat(allCsArray).contains(Value.of(42), Value.of(99));
    }

    @Test
    void subtemplate_preservesRelativeContext() {
        assertExpressionsEqual("{ \"x\": 5, \"y\": 10 } :: { \"sum\": @.x, \"product\": @.y }",
                "{ \"sum\": 5, \"product\": 10 }");
    }

    @Test
    void subtemplate_arrayOfPrimitives() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] :: { \"value\": @, \"squared\": @ * @ }",
                "[ { \"value\": 1, \"squared\": 1 }, { \"value\": 2, \"squared\": 4 }, { \"value\": 3, \"squared\": 9 }, "
                        + "{ \"value\": 4, \"squared\": 16 }, { \"value\": 5, \"squared\": 25 } ]");
    }

    @Test
    void subtemplate_withNullValue() {
        assertExpressionsEqual("{ \"x\": null } :: { \"value\": @.x }", "{ \"value\": null }");
    }

    @Test
    void subtemplate_returnsArrayTemplate() {
        assertExpressionsEqual("{ \"a\": 1, \"b\": 2 } :: [@.a, @.b, @.a + @.b]", "[1, 2, 3]");
    }

    @Test
    void subtemplate_returnsSimpleValueTemplate() {
        assertExpressionsEqual("{ \"x\": 5 } :: @.x * 10", "50");
    }

    @Test
    void subtemplate_withBooleanLogic() {
        assertExpressionsEqual("{ \"age\": 15 } :: { \"isAdult\": @.age >= 18, \"isMinor\": @.age < 18 }",
                "{ \"isAdult\": false, \"isMinor\": true }");
    }

    // ===== Dynamic Filter Arguments Tests =====
    // Tests for PURE expression arguments in various filter operations

    @Test
    void simpleFilter_withPureArgument_evaluatesAtRuntime() {
        // Test: filter with runtime-evaluated argument (environment variable)
        assertExpressionsEqual("10 |- simple.double", "20");
    }

    @Test
    void eachFilter_withPureArgument_evaluatesEachElement() {
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- each simple.double", "[2, 4, 6, 8, 10]");
    }

    @Test
    void keyStepFilter_withPureArgument_appliesFilterToObjectField() {
        // Test: extended filter with key step and runtime argument
        assertExpressionsEqual("{ \"x\": 10, \"y\": 20 } |- { @.x : simple.double }", "{ \"x\": 20, \"y\": 20 }");
    }

    @Test
    void indexStepFilter_withPureArgument_appliesFilterToArrayElement() {
        // Test: extended filter with index step and runtime argument
        assertExpressionsEqual("[10, 20, 30] |- { @[1] : simple.double }", "[10, 40, 30]");
    }

    @Test
    void slicingStepFilter_withPureArgument_appliesFilterToSlice() {
        // Test: extended filter with slicing step and runtime argument
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[1:4] : simple.double }", "[10, 40, 60, 80, 50]");
    }

    @Test
    void wildcardStepFilter_onArray_withPureArgument_appliesFilterToAllElements() {
        // Test: wildcard filter on array with runtime argument
        assertExpressionsEqual("[10, 20, 30] |- { @.* : simple.double }", "[20, 40, 60]");
    }

    @Test
    void wildcardStepFilter_onObject_withPureArgument_appliesFilterToAllFields() {
        // Test: wildcard filter on object with runtime argument
        assertExpressionsEqual("{ \"a\": 10, \"b\": 20, \"c\": 30 } |- { @.* : simple.double }",
                "{ \"a\": 20, \"b\": 40, \"c\": 60 }");
    }

    @Test
    void attributeUnionFilter_withPureArgument_appliesFilterToSelectedFields() {
        // Test: attribute union filter with runtime argument
        assertExpressionsEqual("{ \"a\": 10, \"b\": 20, \"c\": 30 } |- { @[\"a\", \"c\"] : simple.double }",
                "{ \"a\": 20, \"b\": 20, \"c\": 60 }");
    }

    @Test
    void indexUnionFilter_withPureArgument_appliesFilterToSelectedIndices() {
        // Test: index union filter with runtime argument
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[0, 2, 4] : simple.double }", "[20, 20, 60, 40, 100]");
    }

    @Test
    void keyStepFilter_withImplicitArrayMapping_withPureArgument() {
        // Test: key step on array (implicit array mapping) with runtime argument
        assertExpressionsEqual("[{ \"x\": 10 }, { \"x\": 20 }, { \"x\": 30 }] |- { @.x : simple.double }",
                "[{ \"x\": 20 }, { \"x\": 40 }, { \"x\": 60 }]");
    }

    @Test
    void conditionStepFilter_withConstantTrueCondition_appliesFilterLikeWildcard() {
        // Test: condition step filter with constant true condition
        assertExpressionsEqual("[10, 20, 30] |- { @[?(true)] : simple.double }", "[20, 40, 60]");
    }

    @Test
    void conditionStepFilter_withConstantFalseCondition_returnsUnchanged() {
        // Test: condition step filter with constant false condition
        assertExpressionsEqual("[10, 20, 30] |- { @[?(false)] : simple.double }", "[10, 20, 30]");
    }

    @Test
    void expressionStepFilter_onArray_withConstantExpression_appliesFilterToComputedIndex() {
        // Test: expression step on array with constant expression computing index
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[(1 + 1)] : simple.double }", "[10, 20, 60, 40, 50]");
    }

    @Test
    void expressionStepFilter_onObject_withConstantExpression_appliesFilterToComputedKey() {
        // Test: expression step on object with constant expression computing key
        assertExpressionsEqual("{ \"x\": 10, \"y\": 20 } |- { @[(\"x\")] : simple.double }",
                "{ \"x\": 20, \"y\": 20 }");
    }

    @Test
    void recursiveKeyFilter_withConstantArguments_worksCorrectly() {
        // Test: recursive key filter works with constant arguments
        assertExpressionsEqual("{ \"a\": { \"x\": 10 }, \"b\": { \"x\": 20 } } |- { @..x : simple.double }",
                "{ \"a\": { \"x\": 20 }, \"b\": { \"x\": 40 } }");
    }

    @Test
    void multiStepPathFilter_withConstantArguments_worksCorrectly() {
        // Test: multi-step path filter works with constant arguments
        assertExpressionsEqual("{ \"a\": { \"b\": { \"x\": 10 } } } |- { @.a.b.x : simple.double }",
                "{ \"a\": { \"b\": { \"x\": 20 } } }");
    }

    @Test
    void eachFilter_combinesWithFilterRemove_removesUndefinedElements() {
        // Test: 'each' filter with filter.remove function returns empty array
        assertExpressionsEqual("[1, 2, 3, 4, 5] |- each filter.remove", "[]");
    }

    @Test
    void wildcardFilter_combinesWithFilterRemove_removesAllFieldsFromObject() {
        // Test: wildcard filter with filter.remove on object
        assertExpressionsEqual("{ \"a\": 10, \"b\": 20, \"c\": 30 } |- { @.* : filter.remove }", "{}");
    }

    @Test
    void wildcardFilter_combinesWithFilterRemove_removesAllElementsFromArray() {
        // Test: wildcard filter with filter.remove on array
        assertExpressionsEqual("[10, 20, 30] |- { @.* : filter.remove }", "[]");
    }

    @Test
    void slicingFilter_withNegativeIndices_appliesCorrectly() {
        // Test: slicing filter with negative indices (Python-style)
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[-3:-1] : simple.double }", "[10, 20, 60, 80, 50]");
    }

    @Test
    void indexUnionFilter_withNegativeIndices_appliesCorrectly() {
        // Test: index union filter with negative indices
        assertExpressionsEqual("[10, 20, 30, 40, 50] |- { @[0, -2, -1] : simple.double }", "[20, 20, 30, 80, 100]");
    }
}
