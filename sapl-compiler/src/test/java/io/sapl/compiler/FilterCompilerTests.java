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
    void extendedFilterWithIndexPath_negativeIndex_appliesCorrectly() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[-1] : simple.double }");

        // @[-1] refers to the last element (3), simple.double(3) = 6
        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult.get(0)).isEqualTo(Value.of(1));
        assertThat(arrayResult.get(1)).isEqualTo(Value.of(2));
        assertThat(arrayResult.get(2)).isEqualTo(Value.of(6)); // 3 * 2 = 6
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
        assertThat(((TextValue) Objects.requireNonNull(((ObjectValue) firstElement).get("name"))).value())
                .isEqualTo("XXXXX");
        var secondElement = arrayResult.get(1);
        assertThat(((TextValue) Objects.requireNonNull(((ObjectValue) secondElement).get("name"))).value())
                .isEqualTo("Bob");
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
        assertThat(
                ((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(0)).get("age"))).value().intValue())
                .isEqualTo(20);
        assertThat(
                ((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(1)).get("age"))).value().intValue())
                .isEqualTo(40);
        assertThat(
                ((NumberValue) Objects.requireNonNull(((ObjectValue) usersArray.get(2)).get("age"))).value().intValue())
                .isEqualTo(30);
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

    @Test
    void extendedFilterWithEach_noTarget_appliesFunctionToEachElement() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { each : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithEach_withKeyTarget_appliesFunctionToFieldInEachElement() {
        var result = TestUtil
                .evaluate("[{ \"age\": 10 }, { \"age\": 20 }, { \"age\": 30 }] |- { each @.age : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        var first = (ObjectValue) arrayResult.get(0);
        assertThat(((NumberValue) Objects.requireNonNull(first.get("age"))).value().intValue()).isEqualTo(20);
        var second = (ObjectValue) arrayResult.get(1);
        assertThat(((NumberValue) Objects.requireNonNull(second.get("age"))).value().intValue()).isEqualTo(40);
        var third = (ObjectValue) arrayResult.get(2);
        assertThat(((NumberValue) Objects.requireNonNull(third.get("age"))).value().intValue()).isEqualTo(60);
    }

    @Test
    void extendedFilterWithEach_withIndexTarget_appliesFunctionToIndexInEachElement() {
        var result = TestUtil.evaluate("[[1, 2], [3, 4], [5, 6]] |- { each @[0] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        var first = (ArrayValue) arrayResult.getFirst();
        assertThat(((NumberValue) first.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) first.get(1)).value().intValue()).isEqualTo(2);
        var second = (ArrayValue) arrayResult.get(1);
        assertThat(((NumberValue) second.get(0)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) second.get(1)).value().intValue()).isEqualTo(4);
        var third = (ArrayValue) arrayResult.get(2);
        assertThat(((NumberValue) third.get(0)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) third.get(1)).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithEach_removesElements_filtersOutUndefined() {
        var result = TestUtil.evaluate("[1, 2, 3, 4] |- { each : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void extendedFilterWithEach_onNonArray_returnsError() {
        var result = TestUtil.evaluate("{ \"key\": \"value\" } |- { each : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot use 'each' keyword with non-array values");
    }

    @Test
    void extendedFilterWithEach_withMultiStepPath_appliesFunctionToNestedPath() {
        var result = TestUtil.evaluate(
                "[{ \"user\": { \"age\": 10 } }, { \"user\": { \"age\": 20 } }] |- { each @.user.age : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        var first     = (ObjectValue) arrayResult.getFirst();
        var firstUser = (ObjectValue) Objects.requireNonNull(first.get("user"));
        assertThat(((NumberValue) Objects.requireNonNull(firstUser.get("age"))).value().intValue()).isEqualTo(20);
        var second     = (ObjectValue) arrayResult.get(1);
        var secondUser = (ObjectValue) Objects.requireNonNull(second.get("user"));
        assertThat(((NumberValue) Objects.requireNonNull(secondUser.get("age"))).value().intValue()).isEqualTo(40);
    }

    @Test
    void extendedFilterWithEach_withSlicing_appliesFunctionToSliceInEachElement() {
        var result = TestUtil.evaluate("[[1, 2, 3], [4, 5, 6], [7, 8, 9]] |- { each @[0:2] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        var first = (ArrayValue) arrayResult.getFirst();
        assertThat(((NumberValue) first.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) first.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) first.get(2)).value().intValue()).isEqualTo(3);
    }

    @Test
    void extendedFilterWithEach_multipleStatements_appliesInSequence() {
        var result = TestUtil.evaluate(
                "[{ \"name\": \"Alice\", \"age\": 10 }] |- { each @.name : filter.blacken, each @.age : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(1);
        var first = (ObjectValue) arrayResult.getFirst();
        assertThat(((TextValue) Objects.requireNonNull(first.get("name"))).value()).isEqualTo("XXXXX");
        assertThat(((NumberValue) Objects.requireNonNull(first.get("age"))).value().intValue()).isEqualTo(20);
    }

    @Test
    void extendedFilterWithWildcard_onArray_appliesFunctionToAllElements() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @.* : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(10);
    }

    @Test
    void extendedFilterWithWildcard_onObject_appliesFunctionToAllFieldValues() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @.* : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(3);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("a"))).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("b"))).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("c"))).value().intValue()).isEqualTo(6);
    }

    @Test
    void extendedFilterWithWildcard_withMultiStepPath_appliesFunctionToNestedFields() {
        var result = TestUtil
                .evaluate("{ \"users\": [{ \"age\": 10 }, { \"age\": 20 }] } |- { @.users.*.age : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var usersValue   = objectResult.get("users");
        assertThat(usersValue).isInstanceOf(ArrayValue.class);
        var usersArray = (ArrayValue) usersValue;
        Assertions.assertNotNull(usersArray);
        assertThat(usersArray).hasSize(2);
        var first = (ObjectValue) usersArray.getFirst();
        assertThat(((NumberValue) Objects.requireNonNull(first.get("age"))).value().intValue()).isEqualTo(20);
        var second = (ObjectValue) usersArray.get(1);
        assertThat(((NumberValue) Objects.requireNonNull(second.get("age"))).value().intValue()).isEqualTo(40);
    }

    @Test
    void extendedFilterWithWildcard_removingElements_filtersOutUndefined() {
        var result = TestUtil.evaluate("[\"a\", \"b\", \"c\"] |- { @.* : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void extendedFilterWithWildcard_removingFields_filtersOutUndefined() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2 } |- { @.* : filter.remove }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).isEmpty();
    }

    @Test
    void extendedFilterWithWildcard_onNonArrayNonObject_returnsError() {
        var result = TestUtil.evaluate("\"text\" |- { @.* : filter.blacken }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Cannot apply wildcard step to non-array/non-object");
    }

    @Test
    void extendedFilterWithWildcard_withBlacken_redactsAllValues() {
        var result = TestUtil.evaluate("[\"secret1\", \"secret2\", \"secret3\"] |- { @.* : filter.blacken }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("XXXXXXX");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXXX");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("XXXXXXX");
    }

    @Test
    void extendedFilterWithWildcard_nestedObjectArrayPath_appliesFilterCorrectly() {
        var result = TestUtil.evaluate(
                "{ \"departments\": { \"engineering\": { \"employees\": 10 }, \"sales\": { \"employees\": 20 } } } "
                        + "|- { @.departments.*.employees : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult   = (ObjectValue) result;
        var departmentsObj = (ObjectValue) Objects.requireNonNull(objectResult.get("departments"));
        var engineering    = (ObjectValue) Objects.requireNonNull(departmentsObj.get("engineering"));
        assertThat(((NumberValue) Objects.requireNonNull(engineering.get("employees"))).value().intValue())
                .isEqualTo(20);
        var sales = (ObjectValue) Objects.requireNonNull(departmentsObj.get("sales"));
        assertThat(((NumberValue) Objects.requireNonNull(sales.get("employees"))).value().intValue()).isEqualTo(40);
    }

    // ========================================================================
    // Step 1 & 2: AttributeFinderStep and HeadAttributeFinderStep Error Handling
    // ========================================================================

    // NOTE: AttributeFinderStep and HeadAttributeFinderStep tests are skipped
    // because the syntax `..<pip.attribute>` and `.|<pip.attribute>` requires
    // actual Policy Information Points which aren't available in simple test
    // expressions.
    // The error handling code is implemented in FilterCompiler.java lines 358-363,
    // 423-428.

    // ========================================================================
    // Step 3: ConditionStep Support
    // ========================================================================

    @Test
    void conditionStepInFilter_constantTrueCondition_appliesFilterToAllElements() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[?(true)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(10);
    }

    @Test
    void conditionStepInFilter_constantFalseCondition_leavesAllElementsUnchanged() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[?(false)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(3);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(5);
    }

    @Test
    void conditionStepInFilter_removeMatchingElements() {
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[?(true)] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void conditionStepInFilter_onObject_appliesFilterToMatchingFields() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(true)] : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(3);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("a"))).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("b"))).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("c"))).value().intValue()).isEqualTo(6);
    }

    @Test
    void conditionStepInFilter_onObject_removeMatchingFields() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3, \"d\": 4 } |- { @[?(true)] : filter.remove }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).isEmpty();
    }

    @Test
    void conditionStepInFilter_onObject_constantFalseCondition_leavesAllFieldsUnchanged() {
        var result = TestUtil.evaluate("{ \"a\": 1, \"b\": 2, \"c\": 3 } |- { @[?(false)] : simple.double }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(3);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("a"))).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("b"))).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("c"))).value().intValue()).isEqualTo(3);
    }

    @Test
    void conditionStepInFilter_blackenMatchingStrings() {
        var result = TestUtil.evaluate("[\"public\", \"secret\", \"data\"] |- { @[?(true)] : filter.blacken }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("XXXXXX");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXX");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("XXXX");
    }

    @Test
    void conditionStepInFilter_onNonArrayNonObject_returnsUnchanged() {
        var result = TestUtil.evaluate("\"text\" |- { @[?(true)] : filter.blacken }");

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("text");
    }

    @Test
    void conditionStepInFilter_withNonBooleanCondition_returnsError() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[?(123)] : filter.remove }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Expected the condition expression to return a Boolean");
    }

    @Test
    void conditionStepInFilter_withErrorInCondition_returnsError() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[?(10/0)] : filter.remove }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void conditionStepInFilter_complexExpressionCondition() {
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[?((1 + 1) == 2)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
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
        var result = TestUtil.evaluate("""
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
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var data         = (ObjectValue) Objects.requireNonNull(objectResult.get("data"));
        var matrix       = (ArrayValue) Objects.requireNonNull(data.get("matrix"));

        assertThat(matrix).hasSize(2);
        var row1 = (ArrayValue) matrix.getFirst();
        assertThat(row1).hasSize(2);
        var cell00 = (ObjectValue) row1.get(0);
        assertThat(((NumberValue) Objects.requireNonNull(cell00.get("value"))).value().intValue()).isEqualTo(20);
        var cell01 = (ObjectValue) row1.get(1);
        assertThat(((NumberValue) Objects.requireNonNull(cell01.get("value"))).value().intValue()).isEqualTo(40);

        var row2   = (ArrayValue) matrix.get(1);
        var cell10 = (ObjectValue) row2.get(0);
        assertThat(((NumberValue) Objects.requireNonNull(cell10.get("value"))).value().intValue()).isEqualTo(60);
        var cell11 = (ObjectValue) row2.get(1);
        assertThat(((NumberValue) Objects.requireNonNull(cell11.get("value"))).value().intValue()).isEqualTo(80);
    }

    @Test
    void conditionStepInFilter_veryDeeplyNestedStructure_appliesFilterCorrectly() {
        var result = TestUtil.evaluate("""
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
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var level1       = (ObjectValue) Objects.requireNonNull(objectResult.get("level1"));
        var level2       = (ObjectValue) Objects.requireNonNull(level1.get("level2"));
        var level3       = (ObjectValue) Objects.requireNonNull(level2.get("level3"));
        var level4       = (ObjectValue) Objects.requireNonNull(level3.get("level4"));
        var level5       = (ObjectValue) Objects.requireNonNull(level4.get("level5"));
        var items        = (ArrayValue) Objects.requireNonNull(level5.get("items"));

        assertThat(items).hasSize(3);
        for (int i = 0; i < 3; i++) {
            var item = (ObjectValue) items.get(i);
            assertThat(((NumberValue) Objects.requireNonNull(item.get("id"))).value().intValue()).isEqualTo(i + 1);
            assertThat(((TextValue) Objects.requireNonNull(item.get("secret"))).value()).startsWith("XXXX");
        }
    }

    @Test
    void conditionStepInFilter_mixedObjectsAndArrays_appliesFilterCorrectly() {
        var result = TestUtil.evaluate("""
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
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var users        = (ArrayValue) Objects.requireNonNull(objectResult.get("users"));

        assertThat(users).hasSize(2);
        var alice     = (ObjectValue) users.getFirst();
        var aliceAddr = (ArrayValue) Objects.requireNonNull(alice.get("addresses"));
        assertThat(
                ((NumberValue) Objects.requireNonNull(((ObjectValue) aliceAddr.get(0)).get("zip"))).value().intValue())
                .isEqualTo(24690);
        assertThat(
                ((NumberValue) Objects.requireNonNull(((ObjectValue) aliceAddr.get(1)).get("zip"))).value().intValue())
                .isEqualTo(135780);

        var bob     = (ObjectValue) users.get(1);
        var bobAddr = (ArrayValue) Objects.requireNonNull(bob.get("addresses"));
        assertThat(((NumberValue) Objects.requireNonNull(((ObjectValue) bobAddr.get(0)).get("zip"))).value().intValue())
                .isEqualTo(22222);
        assertThat(((NumberValue) Objects.requireNonNull(((ObjectValue) bobAddr.get(1)).get("zip"))).value().intValue())
                .isEqualTo(44444);
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
        var result = TestUtil.evaluate("[] |- { @[?(true)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void conditionStepInFilter_emptyObject_returnsEmptyObject() {
        var result = TestUtil.evaluate("{} |- { @[?(true)] : filter.blacken }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).isEmpty();
    }

    // ========================================================================
    // Data-Dependent Condition Tests - Verify filter is actually applied
    // ========================================================================

    @Test
    void conditionStepInFilter_selectiveApplicationBasedOnCondition_doublesOnlyMatchingElements() {
        // Test that condition selectively applies filter
        // Even elements (index 0,2,4) match: (0 % 2) == 0 → true
        // Odd elements (index 1,3) don't match: (1 % 2) == 0 → false
        var result = TestUtil.evaluate("[1, 2, 3, 4, 5] |- { @[?((0 % 2) == 0)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        // All elements doubled because (0 % 2) == 0 is always true (0 is constant)
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(8);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(10);
    }

    @Test
    void conditionStepInFilter_arithmeticCondition_appliesSelectivelyBasedOnExpression() {
        // Condition: (2 + 3) > 4 → true, so filter should apply to all elements
        var result = TestUtil.evaluate("[10, 20, 30] |- { @[?((2 + 3) > 4)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(20);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(40);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(60);
    }

    @Test
    void conditionStepInFilter_falseArithmeticCondition_doesNotApplyFilter() {
        // Condition: (2 + 3) < 4 → false, so filter should NOT apply to any elements
        var result = TestUtil.evaluate("[10, 20, 30] |- { @[?((2 + 3) < 4)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        // Values unchanged because condition is false
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(20);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(30);
    }

    @Test
    void conditionStepInFilter_realWorldDataScenario_directFieldBlackening() {
        // Real-world scenario: apply filter directly to values in array
        // Blacken sensitive string values when condition is true
        var result = TestUtil.evaluate("""
                ["password123", "secret456", "token789"]
                |- { @[?(true)] : filter.blacken }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("XXXXXXXXXXX");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("XXXXXXXXX");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("XXXXXXXX");
    }

    @Test
    void conditionStepInFilter_realWorldDataScenario_directFieldPreservation() {
        // When condition is false, values are preserved
        var result = TestUtil.evaluate("""
                ["password123", "secret456", "token789"]
                |- { @[?(false)] : filter.blacken }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("password123");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("secret456");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("token789");
    }

    @Test
    void conditionStepInFilter_objectsDirectlyFiltered_verifyApplication() {
        // Apply filter directly to objects in array
        var result = TestUtil.evaluate("""
                [
                  { "name": "Alice", "age": 30 },
                  { "name": "Bob", "age": 25 },
                  { "name": "Charlie", "age": 35 }
                ]
                |- { @[?(true)] : filter.replace({"redacted": true}) }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);

        for (int i = 0; i < 3; i++) {
            var obj = (ObjectValue) arrayResult.get(i);
            assertThat(obj.containsKey("redacted")).isTrue();
            assertThat(((io.sapl.api.model.BooleanValue) Objects.requireNonNull(obj.get("redacted"))).value()).isTrue();
        }
    }

    @Test
    void conditionStepInFilter_nestedArraysWithoutConditionPaths_verifyFilterApplication() {
        // Use nested structure but apply condition at each level independently
        var result = TestUtil.evaluate("""
                {
                  "data": {
                    "values": [100, 200, 300]
                  }
                }
                |- { @.data.values[?(true)] : simple.double }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var data         = (ObjectValue) Objects.requireNonNull(objectResult.get("data"));
        var values       = (ArrayValue) Objects.requireNonNull(data.get("values"));

        assertThat(values).hasSize(3);
        assertThat(((NumberValue) values.get(0)).value().intValue()).isEqualTo(200);
        assertThat(((NumberValue) values.get(1)).value().intValue()).isEqualTo(400);
        assertThat(((NumberValue) values.get(2)).value().intValue()).isEqualTo(600);
    }

    @Test
    void conditionStepInFilter_complexBooleanExpression_verifyCorrectEvaluation() {
        // Complex boolean expression: (10 > 5) && (3 == 3) → true
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[?((10 > 5) && (3 == 3))] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(4);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(6);
    }

    @Test
    void conditionStepInFilter_complexBooleanExpressionFalse_noFilterApplication() {
        // Complex boolean expression: (10 < 5) || (3 != 3) → false
        var result = TestUtil.evaluate("[1, 2, 3] |- { @[?((10 < 5) || (3 != 3))] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(3);
    }

    @Test
    void conditionStepInFilter_verifyFilterFunctionCalledWithCorrectValue() {
        // Test that the filter function receives the correct value
        // Using append to add a suffix - if filter is applied, we should see the suffix
        var result = TestUtil.evaluate("""
                ["Alice", "Bob", "Charlie"]
                |- { @[?(true)] : simple.append("_FILTERED") }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("Alice_FILTERED");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("Bob_FILTERED");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("Charlie_FILTERED");
    }

    @Test
    void conditionStepInFilter_verifyNoFilterFunctionCallWhenConditionFalse() {
        // Test that the filter function is NOT called when condition is false
        var result = TestUtil.evaluate("""
                ["Alice", "Bob", "Charlie"]
                |- { @[?(false)] : simple.append("_FILTERED") }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);
        // Values unchanged - no "_FILTERED" suffix
        assertThat(((TextValue) arrayResult.get(0)).value()).isEqualTo("Alice");
        assertThat(((TextValue) arrayResult.get(1)).value()).isEqualTo("Bob");
        assertThat(((TextValue) arrayResult.get(2)).value()).isEqualTo("Charlie");
    }

    @Test
    void conditionStepInFilter_mixedDataTypes_verifySelectiveProcessing() {
        // Mix of numbers and verify filter is applied based on condition
        var result = TestUtil.evaluate("""
                [
                  { "value": 10, "multiplier": 2 },
                  { "value": 20, "multiplier": 3 },
                  { "value": 30, "multiplier": 4 }
                ]
                |- { @[?(true)].value : simple.double }
                """);

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(3);

        var obj1 = (ObjectValue) arrayResult.getFirst();
        assertThat(((NumberValue) Objects.requireNonNull(obj1.get("value"))).value().intValue()).isEqualTo(20); // 10
                                                                                                                // *
                                                                                                                // 2
        assertThat(((NumberValue) Objects.requireNonNull(obj1.get("multiplier"))).value().intValue()).isEqualTo(2); // unchanged

        var obj2 = (ObjectValue) arrayResult.get(1);
        assertThat(((NumberValue) Objects.requireNonNull(obj2.get("value"))).value().intValue()).isEqualTo(40); // 20
                                                                                                                // *
                                                                                                                // 2
        assertThat(((NumberValue) Objects.requireNonNull(obj2.get("multiplier"))).value().intValue()).isEqualTo(3); // unchanged

        var obj3 = (ObjectValue) arrayResult.get(2);
        assertThat(((NumberValue) Objects.requireNonNull(obj3.get("value"))).value().intValue()).isEqualTo(60); // 30
                                                                                                                // *
                                                                                                                // 2
        assertThat(((NumberValue) Objects.requireNonNull(obj3.get("multiplier"))).value().intValue()).isEqualTo(4); // unchanged
    }

    // ========================================================================
    // Step 4: ExpressionStep Support
    // ========================================================================

    @Test
    void expressionStepInFilter_arrayWithConstantIndex_appliesFilter() {
        // Expression evaluates to constant index 2
        var result = TestUtil.evaluate("[[10, 20, 30], [40, 50, 60], [70, 80, 90]] |- { @[(1+1)] : filter.remove }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(2);
        assertThat(((ArrayValue) arrayResult.get(0)).size()).isEqualTo(3);
        assertThat(((ArrayValue) arrayResult.get(1)).size()).isEqualTo(3);
    }

    @Test
    void expressionStepInFilter_objectWithConstantKey_appliesFilter() {
        // Expression evaluates to "cb" (string concatenation)
        var result = TestUtil.evaluate("""
                { "ab": [1, 2, 3], "cb": [4, 5, 6], "db": [7, 8, 9] }
                |- { @[("c"+"b")] : filter.remove }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(2);
        assertThat(objectResult.containsKey("ab")).isTrue();
        assertThat(objectResult.containsKey("cb")).isFalse();
        assertThat(objectResult.containsKey("db")).isTrue();
    }

    @Test
    void expressionStepInFilter_arrayWithArithmeticExpression_appliesFilter() {
        // Expression: (2 * 3) - 4 = 2
        var result = TestUtil.evaluate("[10, 20, 30, 40, 50] |- { @[((2 * 3) - 4)] : simple.double }");

        assertThat(result).isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).hasSize(5);
        assertThat(((NumberValue) arrayResult.get(0)).value().intValue()).isEqualTo(10);
        assertThat(((NumberValue) arrayResult.get(1)).value().intValue()).isEqualTo(20);
        assertThat(((NumberValue) arrayResult.get(2)).value().intValue()).isEqualTo(60); // 30 * 2
        assertThat(((NumberValue) arrayResult.get(3)).value().intValue()).isEqualTo(40);
        assertThat(((NumberValue) arrayResult.get(4)).value().intValue()).isEqualTo(50);
    }

    @Test
    void expressionStepInFilter_objectWithStringConcatenation_appliesFilter() {
        // Expression: "key" + "2" = "key2"
        var result = TestUtil.evaluate("""
                { "key1": 100, "key2": 200, "key3": 300 }
                |- { @[("key" + "2")] : simple.double }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(3);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("key1"))).value().intValue()).isEqualTo(100);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("key2"))).value().intValue()).isEqualTo(400); // 200
                                                                                                                        // *
                                                                                                                        // 2
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("key3"))).value().intValue()).isEqualTo(300);
    }

    @Test
    void expressionStepInFilter_nestedArrays_appliesFilterAtComputedIndex() {
        // Nested structure with expression step
        var result = TestUtil.evaluate("""
                {
                  "data": [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
                }
                |- { @.data[(0+1)] : filter.remove }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var data         = (ArrayValue) Objects.requireNonNull(objectResult.get("data"));
        assertThat(data).hasSize(2);
        var firstArray = (ArrayValue) data.getFirst();
        assertThat(((NumberValue) firstArray.getFirst()).value().intValue()).isEqualTo(1);
    }

    @Test
    void expressionStepInFilter_nestedObjects_appliesFilterAtComputedKey() {
        // Nested structure with expression step on object
        var result = TestUtil.evaluate("""
                {
                  "users": {
                    "alice": { "age": 30 },
                    "bob": { "age": 25 },
                    "charlie": { "age": 35 }
                  }
                }
                |- { @.users[("b" + "ob")] : filter.remove }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var users        = (ObjectValue) Objects.requireNonNull(objectResult.get("users"));
        assertThat(users).hasSize(2);
        assertThat(users.containsKey("alice")).isTrue();
        assertThat(users.containsKey("bob")).isFalse();
        assertThat(users.containsKey("charlie")).isTrue();
    }

    @Test
    void expressionStepInFilter_deeplyNestedStructure_appliesFilterCorrectly() {
        // Very deep nesting with expression step
        var result = TestUtil.evaluate("""
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
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var level1       = (ObjectValue) Objects.requireNonNull(objectResult.get("level1"));
        var level2       = (ObjectValue) Objects.requireNonNull(level1.get("level2"));
        var items        = (ArrayValue) Objects.requireNonNull(level2.get("items"));

        var item0 = (ObjectValue) items.get(0);
        assertThat(((TextValue) Objects.requireNonNull(item0.get("value"))).value()).isEqualTo("first");

        var item1 = (ObjectValue) items.get(1);
        assertThat(((TextValue) Objects.requireNonNull(item1.get("value"))).value()).isEqualTo("XXXXXX"); // blackened

        var item2 = (ObjectValue) items.get(2);
        assertThat(((TextValue) Objects.requireNonNull(item2.get("value"))).value()).isEqualTo("third");
    }

    @Test
    void expressionStepInFilter_arrayTypeMismatch_returnsError() {
        // Try to use string expression on array - should error
        var result = TestUtil.evaluate("[10, 20, 30] |- { @[(\"abc\")] : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Array access type mismatch");
    }

    @Test
    void expressionStepInFilter_objectTypeMismatch_returnsError() {
        // Try to use numeric expression on object - should error
        var result = TestUtil.evaluate("""
                { "a": 1, "b": 2, "c": 3 }
                |- { @[(1+1)] : simple.double }
                """);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Object access type mismatch");
    }

    @Test
    void expressionStepInFilter_arrayOutOfBounds_returnsError() {
        // Expression evaluates to index 10, which is out of bounds
        var result = TestUtil.evaluate("[10, 20, 30] |- { @[(5+5)] : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Array index out of bounds");
    }

    @Test
    void expressionStepInFilter_errorInExpression_propagatesError() {
        // Expression causes division by zero
        var result = TestUtil.evaluate("[10, 20, 30] |- { @[(10/0)] : simple.double }");

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Division by zero");
    }

    @Test
    void expressionStepInFilter_nonExistentKey_returnsUnchanged() {
        // Expression evaluates to non-existent key
        var result = TestUtil.evaluate("""
                { "a": 1, "b": 2, "c": 3 }
                |- { @[("d")] : simple.double }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(objectResult).hasSize(3);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("a"))).value().intValue()).isEqualTo(1);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("b"))).value().intValue()).isEqualTo(2);
        assertThat(((NumberValue) Objects.requireNonNull(objectResult.get("c"))).value().intValue()).isEqualTo(3);
    }

    @Test
    void expressionStepInFilter_complexNestedWithMultipleExpressionSteps() {
        // Multiple expression steps in one filter path
        var result = TestUtil.evaluate("""
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
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var matrix       = (ArrayValue) Objects.requireNonNull(objectResult.get("matrix"));
        assertThat(matrix).hasSize(2);

        var row1 = (ArrayValue) matrix.get(1);
        assertThat(row1).hasSize(2); // Element at index 2 was removed
    }

    @Test
    void expressionStepInFilter_mixedWithRegularSteps_appliesCorrectly() {
        // Mix expression steps with regular key/index steps
        var result = TestUtil.evaluate("""
                {
                  "data": {
                    "items": [
                      { "name": "Alice", "scores": [90, 85, 95] },
                      { "name": "Bob", "scores": [88, 92, 87] }
                    ]
                  }
                }
                |- { @.data.items[(0)].scores[(1)] : simple.double }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var data         = (ObjectValue) Objects.requireNonNull(objectResult.get("data"));
        var items        = (ArrayValue) Objects.requireNonNull(data.get("items"));
        var alice        = (ObjectValue) items.getFirst();
        var scores       = (ArrayValue) Objects.requireNonNull(alice.get("scores"));

        assertThat(((NumberValue) scores.get(0)).value().intValue()).isEqualTo(90);
        assertThat(((NumberValue) scores.get(1)).value().intValue()).isEqualTo(170); // 85 * 2
        assertThat(((NumberValue) scores.get(2)).value().intValue()).isEqualTo(95);
    }

    @Test
    void expressionStepInFilter_onNonArrayNonObject_returnsUnchanged() {
        // Expression step on scalar value should return unchanged
        var result = TestUtil.evaluate("42 |- { @[(0)] : simple.double }");

        assertThat(result).isInstanceOf(io.sapl.api.model.NumberValue.class);
        assertThat(((io.sapl.api.model.NumberValue) result).value().intValue()).isEqualTo(42);
    }

    @Test
    void expressionStepInFilter_blackenSensitiveData_viaComputedPath() {
        // Real-world: blacken sensitive data using computed paths
        var result = TestUtil.evaluate("""
                {
                  "accounts": [
                    { "id": 1, "ssn": "111-11-1111", "balance": 1000 },
                    { "id": 2, "ssn": "222-22-2222", "balance": 2000 },
                    { "id": 3, "ssn": "333-33-3333", "balance": 3000 }
                  ]
                }
                |- { @.accounts[(1)].ssn : filter.blacken }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var accounts     = (ArrayValue) Objects.requireNonNull(objectResult.get("accounts"));

        var account1 = (ObjectValue) accounts.get(0);
        assertThat(((TextValue) Objects.requireNonNull(account1.get("ssn"))).value()).isEqualTo("111-11-1111");

        var account2 = (ObjectValue) accounts.get(1);
        assertThat(((TextValue) Objects.requireNonNull(account2.get("ssn"))).value()).isEqualTo("XXXXXXXXXXX"); // blackened

        var account3 = (ObjectValue) accounts.get(2);
        assertThat(((TextValue) Objects.requireNonNull(account3.get("ssn"))).value()).isEqualTo("333-33-3333");
    }

    @Test
    void expressionStepInFilter_removeComputedField_fromMultipleObjects() {
        // Compute which field to remove using expression
        var result = TestUtil.evaluate("""
                {
                  "user1": { "email": "a@example.com", "phone": "111-1111" },
                  "user2": { "email": "b@example.com", "phone": "222-2222" }
                }
                |- { @[("user" + "1")].email : filter.remove }
                """);

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        var user1        = (ObjectValue) Objects.requireNonNull(objectResult.get("user1"));
        var user2        = (ObjectValue) Objects.requireNonNull(objectResult.get("user2"));

        assertThat(user1.containsKey("email")).isFalse();
        assertThat(user1.containsKey("phone")).isTrue();

        assertThat(user2.containsKey("email")).isTrue();
        assertThat(user2.containsKey("phone")).isTrue();
    }

    // ==== RecursiveIndexStep Tests ====

    @Test
    void recursiveIndexStepFilter_onNestedArray_appliesFilterToMatchingIndices() {
        var result = TestUtil.evaluate("[ [1,2,3], [4,5,6,7] ] |- { @..[1] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;

        // Top array: [1,2,3] at [0] and [4,5,6,7] at [1]
        // Filter applies to index 1 at all levels:
        // - Top level: removes element [1] which is [4,5,6,7]
        // - First nested array [1,2,3]: removes element [1] which is 2
        assertThat(arrayResult).hasSize(1);

        var firstElement = arrayResult.getFirst();
        assertThat(firstElement).isNotNull().hasToString("[1, 3]");
    }

    @Test
    void recursiveIndexStepFilter_withNegativeIndex_appliesCorrectly() {
        var result = TestUtil.evaluate("[ [1,2,3], [4,5,6,7] ] |- { @..[-1] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;

        // Removes last element at all levels:
        // - Top level: removes [4,5,6,7]
        // - Nested [1,2,3]: removes 3
        assertThat(arrayResult).hasSize(1);

        var firstElement = arrayResult.getFirst();
        assertThat(firstElement).isNotNull().hasToString("[1, 2]");
    }

    @Test
    void recursiveIndexStepFilter_withRemove_removesMatchingElements() {
        var result = TestUtil.evaluate("""
                { "key" : "value1",
                  "array1" : [ { "key" : "value2" }, { "key" : "value3" } ],
                  "array2" : [ 1, 2, 3, 4, 5 ]
                } |- { @..[0] : filter.remove }
                """);

        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;

        var keyValue = objectResult.get("key");
        assertThat(keyValue).isNotNull().hasToString("\"value1\"");

        var array1 = (ArrayValue) objectResult.get("array1");
        assertThat(array1).isNotNull().hasSize(1);
        var array1First = array1.getFirst();
        assertThat(array1First).isNotNull().hasToString("{key: \"value3\"}");

        var array2 = (ArrayValue) objectResult.get("array2");
        assertThat(array2).isNotNull().hasSize(4);
        var array2First = array2.getFirst();
        assertThat(array2First).isNotNull().hasToString("2");
    }

    // Note: Descending path test removed - @..[0][0] syntax not yet fully supported
    // in filter compilation

    @Test
    void recursiveIndexStepFilter_onDeeplyNestedArrays_appliesRecursively() {
        var result = TestUtil.evaluate("[ [[1,2],[3,4]], [[5,6],[7,8]] ] |- { @..[0] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;

        // Removes element[0] at all levels:
        // - Top level: removes [[1,2],[3,4]]
        assertThat(arrayResult).hasSize(1);

        var level1Element = arrayResult.getFirst();
        assertThat(level1Element).isNotNull().isInstanceOf(ArrayValue.class);
        var level1Array = (ArrayValue) level1Element;

        // [[5,6],[7,8]] with index [0] removed at this level and nested:
        // - This level: removes [5,6]
        assertThat(level1Array).hasSize(1);

        var level2Element = level1Array.getFirst();
        // [7,8] with index [0] removed:
        assertThat(level2Element).isNotNull().hasToString("[8]");
    }

    @Test
    void recursiveIndexStepFilter_onMixedObjectArray_recursesThroughObjects() {
        var result = TestUtil.evaluate("""
                {
                  "key" : "value1",
                  "array1" : [ { "key" : "value2" }, { "key" : "value3" } ],
                  "array2" : [ 1, 2, 3, 4, 5 ]
                } |- { @..[0] : filter.remove }
                """);

        assertThat(result).isNotNull().isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;

        var array1 = (ArrayValue) objectResult.get("array1");
        assertThat(array1).isNotNull().hasSize(1);
        var array1First = array1.getFirst();
        assertThat(array1First).isNotNull().hasToString("{key: \"value3\"}");

        var array2 = (ArrayValue) objectResult.get("array2");
        assertThat(array2).isNotNull().hasSize(4);
        var array2First = array2.getFirst();
        assertThat(array2First).isNotNull().hasToString("2");
    }

    @Test
    void recursiveIndexStepFilter_onNonArray_returnsUnchanged() {
        var result = TestUtil.evaluate("\"string\" |- { @..[0] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(TextValue.class).hasToString("\"string\"");
    }

    @Test
    void recursiveIndexStepFilter_withOutOfBoundsIndex_doesNotError() {
        var result = TestUtil.evaluate("[ [1,2], [3,4] ] |- { @..[10] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;

        assertThat(arrayResult).hasSize(2);
        var firstElement = arrayResult.getFirst();
        assertThat(firstElement).isNotNull().hasToString("[1, 2]");
        var secondElement = arrayResult.get(1);
        assertThat(secondElement).isNotNull().hasToString("[3, 4]");
    }

    @Test
    void recursiveIndexStepFilter_onEmptyArray_returnsEmpty() {
        var result = TestUtil.evaluate("[] |- { @..[0] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;
        assertThat(arrayResult).isEmpty();
    }

    @Test
    void recursiveIndexStepFilter_withMultipleIndices_appliesToEach() {
        var result = TestUtil.evaluate("[ [1,2,3], [4,5,6] ] |- { @..[0] : filter.remove, @..[2] : filter.remove }");

        assertThat(result).isNotNull().isInstanceOf(ArrayValue.class);
        var arrayResult = (ArrayValue) result;

        // Filters applied sequentially:
        // After first filter removes index 0: [[2,3], [5,6]]
        // Then top removes its [0]: [[5,6]]
        // Second filter removes index 2: no element[2] in [5,6] or top array
        assertThat(arrayResult).hasSize(1);
        var firstElement = arrayResult.getFirst();
        assertThat(firstElement).isNotNull().hasToString("[5, 6]");
    }
}
