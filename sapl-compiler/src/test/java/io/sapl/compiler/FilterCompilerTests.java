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
import org.junit.jupiter.api.Test;

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
        assertThat(((TextValue) objectResult.get("name")).value()).isEqualTo("XXXXXX");
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
        assertThat(((NumberValue) objectResult.get("count")).value().intValue()).isEqualTo(10);
    }

    @Test
    void extendedFilterWithTargetPath_multipleFields() {
        var result = TestUtil.evaluate(
                "{ \"first\": \"hello\", \"second\": \"world\" } |- { @.first : simple.append(\"!\"), @.second : simple.append(\"?\") }");

        assertThat(result).isInstanceOf(ObjectValue.class);
        var objectResult = (ObjectValue) result;
        assertThat(((TextValue) objectResult.get("first")).value()).isEqualTo("hello!");
        assertThat(((TextValue) objectResult.get("second")).value()).isEqualTo("world?");
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
        assertThat(((TextValue) objectResult.get("status")).value()).isEqualTo("new");
    }
}
