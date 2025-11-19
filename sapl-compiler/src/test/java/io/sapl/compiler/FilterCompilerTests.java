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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FilterCompiler.
 */
class FilterCompilerTests {

    @Test
    void removeFilterOnObject_returnsUndefined() {
        var result = TestUtil.evaluate("{} |- filter.remove");

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void removeFilterOnNull_returnsUndefined() {
        var result = TestUtil.evaluate("null |- filter.remove");

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void blackenFilterOnString_returnsBlackened() {
        var result = TestUtil.evaluate("\"secret\" |- filter.blacken");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("XXXXXX");
    }

    @Test
    void blackenFilterOnLongerString_returnsBlackened() {
        var result = TestUtil.evaluate("\"password\" |- filter.blacken");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("XXXXXXXX");
    }

    @Test
    void customFunctionWithArgs_appliesFunction() {
        var result = TestUtil.evaluate("\"Ben\" |- simple.append(\" from \", \"Berlin\")");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Ben from Berlin");
    }

    @Test
    void customFunctionNoArgs_appliesFunction() {
        var result = TestUtil.evaluate("\"hello\" |- simple.length");

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(5);
    }

    @Test
    void errorPropagatesFromParent() {
        var result = TestUtil.evaluate("(10/0) |- filter.remove");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void undefinedParentReturnsError() {
        var result = TestUtil.evaluate("undefined |- filter.remove");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Filters cannot be applied to undefined");
    }

    @Test
    void filterOnNumber_worksCorrectly() {
        var result = TestUtil.evaluate("42 |- simple.double");

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(84);
    }

    @Test
    void filterOnBoolean_worksCorrectly() {
        var result = TestUtil.evaluate("true |- simple.negate");

        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void filterOnArray_appliesWithoutEach() {
        var result = TestUtil.evaluate("[1,2,3] |- simple.length");

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(3);
    }

    @Test
    void filterWithErrorInArguments_returnsError() {
        var result = TestUtil.evaluate("\"text\" |- simple.append(10/0)");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void chainedFilterExpressions_appliesSequentially() {
        var result = TestUtil.evaluate("5 |- simple.double");

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(10);
    }

    @Test
    void eachRemovesAllElements_filtersAll() {
        var result = TestUtil.evaluate("[null, 5] |- each filter.remove");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void eachAppliesFunction_transformsElements() {
        var result = TestUtil.evaluate("[\"a\", \"b\"] |- each simple.append(\"!\")");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("a!");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("b!");
    }

    @Test
    void emptyArrayUnchanged_returnsEmpty() {
        var result = TestUtil.evaluate("[] |- each filter.remove");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void eachOnNonArray_returnsError() {
        var result = TestUtil.evaluate("{} |- each filter.remove");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot use 'each' keyword with non-array values");
    }

    @Test
    void eachDoublesNumbers_transformsNumbers() {
        var result = TestUtil.evaluate("[1, 2, 3] |- each simple.double");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
    }

    @Test
    void eachRemovesAllElements_returnsEmptyArray() {
        var result = TestUtil.evaluate("[null, null, null] |- each filter.remove");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void eachWithMultipleArguments_appliesCorrectly() {
        var result = TestUtil.evaluate("[\"Ben\", \"Alice\"] |- each simple.append(\" from \", \"Berlin\")");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("Ben from Berlin");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("Alice from Berlin");
    }

    @Test
    void eachBlackensStrings_redactsEachElement() {
        var result = TestUtil.evaluate("[\"secret\", \"password\"] |- each filter.blacken");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("XXXXXX");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXXXX");
    }

    @Test
    void eachNegatesBooleans_negatesEachElement() {
        var result = TestUtil.evaluate("[true, false, true] |- each simple.negate");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((BooleanValue) arrayResult.get(0)).value()).isFalse();
        assertThat(((BooleanValue) arrayResult.get(1)).value()).isTrue();
        assertThat(((BooleanValue) arrayResult.get(2)).value()).isFalse();
    }

    @Test
    void extendedFilterWithSingleStatement_appliesFunction() {
        var result = TestUtil.evaluate("\"test\" |- { : filter.blacken }");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("XXXX");
    }

    @Test
    void extendedFilterWithMultipleStatements_appliesSequentially() {
        var result = TestUtil.evaluate("5 |- { : simple.double, : simple.double }");

        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(20);
    }

    @Test
    void extendedFilterWithArguments_appliesCorrectly() {
        var result = TestUtil.evaluate("\"Hello\" |- { : simple.append(\" \"), : simple.append(\"World\") }");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Hello World");
    }

    @Test
    void extendedFilterRemovesValue_returnsUndefined() {
        var result = TestUtil.evaluate("42 |- { : filter.remove }");

        assertThat(result).isInstanceOf(UndefinedValue.class);
    }

    @Test
    void extendedFilterReplaceValue_returnsReplacement() {
        var result = TestUtil.evaluate("\"old\" |- { : filter.replace(\"new\") }");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("new");
    }

    @Test
    void extendedFilterErrorInParent_propagatesError() {
        var result = TestUtil.evaluate("(10/0) |- { : filter.remove }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void extendedFilterWithTargetPath_filtersField() {
        var result = TestUtil.evaluate("{ \"name\": \"secret\" } |- { @.name : filter.blacken }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult.get("name")).isInstanceOf(TextValue.class);
        assertThat(((TextValue) Objects.requireNonNull(objectResult.get("name"))).value()).isEqualTo("XXXXXX");
    }

    @Test
    void extendedFilterWithTargetPath_removesField() {
        var result = TestUtil.evaluate("{ \"name\": \"test\", \"age\": 42 } |- { @.name : filter.remove }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult.containsKey("name")).isFalse();
        assertThat(objectResult.containsKey("age")).isTrue();
        assertThat(objectResult.get("age")).isEqualTo(Value.of(42));
    }

    @Test
    void extendedFilterWithTargetPath_transformsField() {
        var result = TestUtil.evaluate("{ \"count\": 5 } |- { @.count : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult.get("count")).isInstanceOf(NumberValue.class);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("count"))).value().intValue()).isEqualTo(10);
    }

    @Test
    void extendedFilterWithTargetPath_multipleFields() {
        var result = TestUtil.evaluate(
                "{ \"first\": \"hello\", \"second\": \"world\" } |- { @.first : simple.append(\"!\"), @.second : simple.append(\"?\") }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(((TextValue) Objects.requireNonNull(objectResult.get("first"))).value()).isEqualTo("hello!");
        assertThat(((TextValue) Objects.requireNonNull(objectResult.get("second"))).value()).isEqualTo("world?");
    }

    @Test
    void extendedFilterWithTargetPath_nonExistentField_returnsError() {
        var result = TestUtil.evaluate("{ \"name\": \"test\" } |- { @.missing : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Field 'missing' not found");
    }

    @Test
    void extendedFilterWithTargetPath_onNonObject_returnsError() {
        var result = TestUtil.evaluate("\"text\" |- { @.field : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot apply key step to non-object");
    }

    @Test
    void extendedFilterWithTargetPath_replacesField() {
        var result = TestUtil.evaluate("{ \"status\": \"old\" } |- { @.status : filter.replace(\"new\") }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(((TextValue) Objects.requireNonNull(objectResult.get("status"))).value()).isEqualTo("new");
    }

    @Test
    void extendedFilterWithIndexPath_transformsElement() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[1] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(3);
    }

    @Test
    void extendedFilterWithIndexPath_removesElement() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[1] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(3);
    }

    @Test
    void extendedFilterWithIndexPath_blackensElement() {
        var result = TestUtil.evaluate("[\"public\", \"secret\", \"data\"] |- { @[1] : filter.blacken }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("public");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXX");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("data");
    }

    @Test
    void extendedFilterWithIndexPath_firstElement() {
        var result = TestUtil.evaluate("[5, 10, 15] |- { @[0] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(15);
    }

    @Test
    void extendedFilterWithIndexPath_lastElement() {
        var result = TestUtil.evaluate("[5, 10, 15] |- { @[2] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(5);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(30);
    }

    @Test
    void extendedFilterWithIndexPath_multipleIndices() {
        var result = TestUtil.evaluate("[1, 2, 3, 4] |- { @[0] : simple.double, @[2] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(4);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(4);
    }

    @Test
    void extendedFilterWithIndexPath_outOfBounds_returnsError() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[5] : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Array index out of bounds");
    }

    @Test
    void extendedFilterWithIndexPath_negativeIndex_returnsError() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[-1] : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Array index out of bounds");
    }

    @Test
    void extendedFilterWithIndexPath_onNonArray_returnsError() {
        var result = TestUtil.evaluate("\"text\" |- { @[0] : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot apply index step to non-array");
    }

    @Test
    void extendedFilterWithSlicing_transformsRange() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[1:3] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(5);
    }

    @Test
    void extendedFilterWithSlicing_fromStart() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[:3] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(5);
    }

    @Test
    void extendedFilterWithSlicing_toEnd() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[2:] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(10);
    }

    @Test
    void extendedFilterWithSlicing_entireArray() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[:] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithSlicing_withStep() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5, 6] |- { @[0:6:2] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(6);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(5)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithSlicing_rangeWithStep() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5, 6] |- { @[1:5:2] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(6);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(3);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(5);
        assertThat(((NumberValue) arrayResult.get(5)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithSlicing_removesElements() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[1:4] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(5);
    }

    @Test
    void extendedFilterWithSlicing_blackensStrings() {
        var result = TestUtil
                .evaluate("[\"public\", \"secret1\", \"secret2\", \"data\"] |- { @[1:3] : filter.blacken }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(4);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("public");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXXX");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("XXXXXXX");
        assertThat(((TextValue) arrayResult.get(3)).value()).isEqualTo("data");
    }

    @Test
    void extendedFilterWithSlicing_outOfBounds_clamps() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[1:10] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithSlicing_onNonArray_returnsError() {
        var result = TestUtil.evaluate("\"text\" |- { @[1:3] : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot apply slicing step to non-array");
    }

    @Test
    void extendedFilterWithSlicing_negativeToIndex_appliesFilterToSlice() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[7:-1] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(10);
        assertThat(((NumberValue) arrayResult.get(7)).value().intValue()).isEqualTo(14);
        assertThat(((NumberValue) arrayResult.get(8)).value().intValue()).isEqualTo(16);
    }

    @Test
    void extendedFilterWithSlicing_negativeFromIndex_appliesFilterToSlice() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:9] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(10);
        assertThat(((NumberValue) arrayResult.get(7)).value().intValue()).isEqualTo(14);
        assertThat(((NumberValue) arrayResult.get(8)).value().intValue()).isEqualTo(16);
    }

    @Test
    void extendedFilterWithSlicing_negativeFromOmittedTo_appliesFilterToSlice() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[-3:] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(10);
        assertThat(((NumberValue) arrayResult.get(7)).value().intValue()).isEqualTo(14);
        assertThat(((NumberValue) arrayResult.get(8)).value().intValue()).isEqualTo(16);
        assertThat(((NumberValue) arrayResult.get(9)).value().intValue()).isEqualTo(18);
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusOne_appliesFilterToAllElements() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-1] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(10);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(0);
        assertThat(((NumberValue) arrayResult.get(5)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(9)).value().intValue()).isEqualTo(18);
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusThree_appliesFilterToMatchingElements() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-3] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(10);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(7)).value().intValue()).isEqualTo(14);
    }

    @Test
    void extendedFilterWithSlicing_negativeStepMinusTwo_appliesFilterToMatchingElements() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[: :-2] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(3);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(9);
    }

    @Test
    void extendedFilterWithSlicing_negativeToWithFilter_appliesFilterBeforeTo() {
        var result = TestUtil.evaluate("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9] |- { @[:-2] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(9);
    }

    @Test
    void extendedFilterWithMultiStepPath_twoKeys_appliesFilterToNestedField() {
        var result = TestUtil.evaluate("{ \"user\": { \"age\": 25 } } |- { @.user.age : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var userValue    = objectResult.get("user");
        assertThat(userValue).isInstanceOf(ObjectValue.class);
        var userObject = (ObjectValue) userValue;
        Assertions.assertNotNull(userObject);
        assertThat(((NumberValue) Objects.requireNonNull(userObject.get("age"))).value().intValue()).isEqualTo(50);
    }

    @Test
    void extendedFilterWithMultiStepPath_threeKeys_appliesFilterToDeepNestedField() {
        var result = TestUtil.evaluate(
                "{ \"user\": { \"address\": { \"zip\": 12345 } } } |- { @.user.address.zip : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var userValue    = objectResult.get("user");
        assertThat(userValue).isInstanceOf(ObjectValue.class);
        Assertions.assertNotNull(userValue);
        var addressValue = ((ObjectValue) userValue).get("address");
        assertThat(addressValue).isInstanceOf(ObjectValue.class);
        Assertions.assertNotNull(addressValue);
        var zipValue = ((ObjectValue) addressValue).get("zip");
        Assertions.assertNotNull(zipValue);
        assertThat(((NumberValue) zipValue).value().intValue()).isEqualTo(24690);
    }

    @Test
    void extendedFilterWithMultiStepPath_keyThenIndex_appliesFilterToArrayElement() {
        var result = TestUtil.evaluate("{ \"items\": [10, 20, 30] } |- { @.items[1] : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var itemsValue   = objectResult.get("items");
        assertThat(itemsValue).isInstanceOf(ArrayValue.class);
        var itemsArray = (ArrayValue) itemsValue;
        Assertions.assertNotNull(itemsArray);
        assertThat(((NumberValue) itemsArray.get(1)).value().intValue()).isEqualTo(40);
        assertThat(((NumberValue) itemsArray.get(0)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) itemsArray.get(2)).value().intValue()).isEqualTo(30);
    }

    @Test
    void extendedFilterWithMultiStepPath_indexThenKey_appliesFilterToObjectInArray() {
        var result = TestUtil
                .evaluate("[{ \"name\": \"Alice\" }, { \"name\": \"Bob\" }] |- { @[0].name : filter.blacken }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult  = (ArrayValue) result;
        var firstElement = arrayResult.getFirst();
        assertThat(firstElement).isInstanceOf(ObjectValue.class);
        assertThat(((TextValue) Objects.requireNonNull(((ObjectValue) firstElement).get("name"))).value()).isEqualTo("XXXXX");
        var secondElement = arrayResult.get(1);
        assertThat(((TextValue) Objects.requireNonNull(((ObjectValue) secondElement).get("name"))).value()).isEqualTo("Bob");
    }

    @Test
    void extendedFilterWithMultiStepPath_keyIndexKey_appliesFilterToNestedArrayObject() {
        var result = TestUtil.evaluate(
                "{ \"users\": [{ \"name\": \"Alice\", \"age\": 30 }] } |- { @.users[0].age : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var usersValue   = objectResult.get("users");
        assertThat(usersValue).isInstanceOf(ArrayValue.class);
        Assertions.assertNotNull(usersValue);
        var firstUser = (ObjectValue) ((ArrayValue) usersValue).getFirst();
        assertThat(((NumberValue) Objects.requireNonNull(firstUser.get("age"))).value().intValue()).isEqualTo(60);
        assertThat(((TextValue) Objects.requireNonNull(firstUser.get("name"))).value()).isEqualTo("Alice");
    }

    @Test
    void extendedFilterWithMultiStepPath_keySliceKey_appliesFilterToFieldsInSlicedArrayElements() {
        var result = TestUtil.evaluate(
                "{ \"users\": [{ \"age\": 10 }, { \"age\": 20 }, { \"age\": 30 }] } |- { @.users[0:2].age : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var usersValue   = objectResult.get("users");
        assertThat(usersValue).isInstanceOf(ArrayValue.class);
        var usersArray = (ArrayValue) usersValue;
        Assertions.assertNotNull(usersArray);
        assertThat(((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(0)).get("age"))).value().intValue()).isEqualTo(20);
        assertThat(((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(1)).get("age"))).value().intValue()).isEqualTo(40);
        assertThat(((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(2)).get("age"))).value().intValue()).isEqualTo(30);
    }

    @Test
    void extendedFilterWithMultiStepPath_missingField_returnsError() {
        var result = TestUtil.evaluate("{ \"user\": { \"name\": \"Alice\" } } |- { @.user.age : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Field 'age' not found");
    }

    @Test
    void extendedFilterWithMultiStepPath_onNonObjectIntermediate_returnsError() {
        var result = TestUtil.evaluate("{ \"user\": 123 } |- { @.user.age : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot apply key step to non-object");
    }

    @Test
    void extendedFilterWithMultiStepPath_onNonArrayIntermediate_returnsError() {
        var result = TestUtil.evaluate("{ \"users\": \"text\" } |- { @.users[0].age : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot access array index");
    }
}
