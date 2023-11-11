/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsErrors;

import org.junit.jupiter.api.Test;

class EagerOperatorsTests {

    @Test
    void evaluateXOrFalseFalse() {
        assertExpressionEvaluatesTo("false ^ false", "false");
    }

    @Test
    void evaluateXOrTrueFalse() {
        assertExpressionEvaluatesTo("true ^ false", "true");
    }

    @Test
    void evaluateXOrFalseTrue() {
        assertExpressionEvaluatesTo("false ^ true", "true");
    }

    @Test
    void evaluateXOrTrueTrue() {
        assertExpressionEvaluatesTo("true ^ true", "false");
    }

    @Test
    void evaluateEagerAndFalseFalse() {
        assertExpressionEvaluatesTo("false & false", "false");
    }

    @Test
    void evaluateEagerAndTrueFalse() {
        assertExpressionEvaluatesTo("true & false", "false");
    }

    @Test
    void evaluateEagerAndFalseTrue() {
        assertExpressionEvaluatesTo("false & true", "false");
    }

    @Test
    void evaluateEagerAndTrueTrue() {
        assertExpressionEvaluatesTo("true & true", "true");
    }

    @Test
    void evaluateEagerAndWrongDatatypeLeft() {
        assertExpressionReturnsErrors("5 & true");
    }

    @Test
    void evaluateEagerAndLeftTrueWrongDatatypeRight() {
        assertExpressionReturnsErrors("true & 5");
    }

    @Test
    void evaluateEagerAndLeftFalseWrongDatatypeRight() {
        assertExpressionReturnsErrors("false & 5");
    }

    @Test
    void evaluateEagerAndLeftError() {
        assertExpressionReturnsErrors("(10/0) & true");
    }

    @Test
    void evaluateEagerAndRightError() {
        assertExpressionReturnsErrors("true & (10/0)");
    }

    @Test
    void evaluateEagerOrFalseFalse() {
        assertExpressionEvaluatesTo("false | false", "false");
    }

    @Test
    void evaluateEagerOrTrueFalse() {
        assertExpressionEvaluatesTo("true | false", "true");
    }

    @Test
    void evaluateEagerOrFalseTrue() {
        assertExpressionEvaluatesTo("false | true", "true");
    }

    @Test
    void evaluateEagerOrWrongDatatypeLeft() {
        assertExpressionReturnsErrors("5 | true");
    }

    @Test
    void evaluateEagerOrWrongDatatypeRightLeftFalse() {
        assertExpressionReturnsErrors("false | 7");
    }

    @Test
    void evaluateEagerOrWrongDatatypeRightLeftTrue() {
        assertExpressionReturnsErrors("true | 7");
    }

    @Test
    void evaluateEagerOrLeftError() {
        assertExpressionReturnsErrors("(10/0) | true");
    }

    @Test
    void evaluateEagerOrRightError() {
        assertExpressionReturnsErrors("true | (10/0)");
    }

    @Test
    void evaluateNotEqualsFalse() {
        assertExpressionEvaluatesTo("true != true", "false");
    }

    @Test
    void evaluateNotEqualsTrue() {
        assertExpressionEvaluatesTo("null != true", "true");
    }

    @Test
    void evaluateNotEqualsNullLeftAndStringRightTrue() {
        assertExpressionEvaluatesTo("null != \"x\"", "true");
    }

    @Test
    void evaluateNotEqualsNullLeftAndNullRightFalse() {
        assertExpressionEvaluatesTo("null != null", "false");
    }

    @Test
    void evaluateNotEqualsNumericalFalse() {
        assertExpressionEvaluatesTo("17 != 17.0", "false");
    }

    @Test
    void evaluateNotEqualsNumericalTrue() {
        assertExpressionEvaluatesTo("17 != (17+2)", "true");
    }

    @Test
    void evaluateNotEqualsLeftError() {
        assertExpressionReturnsErrors("(10/0) != true");
    }

    @Test
    void evaluateNotEqualsRightError() {
        assertExpressionReturnsErrors("true != (10/0)");
    }

    @Test
    void evaluateNotEqualsOnlyRightNumberFalse() {
        assertExpressionEvaluatesTo("\"x\" != 12", "true");
    }

    @Test
    void evaluateNotEqualsOnlyLeftNumberFalse() {
        assertExpressionEvaluatesTo("12 != \"x\"", "true");
    }

    @Test
    void evaluateNotEqualsArraysFalse() {
        assertExpressionEvaluatesTo("[1,2,3] != [1,2,3]", "false");
    }

    @Test
    void evaluateNotEqualsArraysTrue() {
        assertExpressionEvaluatesTo("[1,3] != [1,2,3]", "true");
    }

    @Test
    void evaluateNotEqualsObjectsFalse() {
        assertExpressionEvaluatesTo("{ \"key\" : true } != { \"key\" : true })", "false");
    }

    @Test
    void evaluateNotEqualsObjectsTrue() {
        assertExpressionEvaluatesTo("{ \"key\" : true } != { \"key\" : false })", "true");
    }

    @Test
    void evaluateNotEqualsBothUndefinedFalse() {
        assertExpressionEvaluatesTo("undefined != undefined", "false");
    }

    @Test
    void evaluateNotEqualsLeftUndefinedTrue() {
        assertExpressionEvaluatesTo("undefined != 0", "true");
    }

    @Test
    void evaluateNotEqualsRightUndefinedTrue() {
        assertExpressionEvaluatesTo("0 != undefined", "true");
    }

    @Test
    void evaluateEqualsTrue() {
        assertExpressionEvaluatesTo("true == true", "true");
    }

    @Test
    void evaluateEqualsFalse() {
        assertExpressionEvaluatesTo("null == true", "false");
    }

    @Test
    void evaluateEqualsNullLeftAndStringRightFalse() {
        assertExpressionEvaluatesTo("null == \"a\"", "false");
    }

    @Test
    void evaluateEqualsNullLeftAndNullRightTrue() {
        assertExpressionEvaluatesTo("null == null", "true");
    }

    @Test
    void evaluateEqualsBothUndefinedTrue() {
        assertExpressionEvaluatesTo("undefined == undefined", "true");
    }

    @Test
    void evaluateEqualsLeftUndefinedFalse() {
        assertExpressionEvaluatesTo("undefined == 0", "false");
    }

    @Test
    void evaluateEqualsRightUndefinedFalse() {
        assertExpressionEvaluatesTo("0 == undefined", "false");
    }

    @Test
    void evaluateEqualsNumbersTrue() {
        assertExpressionEvaluatesTo("10.0 == (12 - 2)", "true");
    }

    @Test
    void evaluateEqualsNumbersFalse() {
        assertExpressionEvaluatesTo("10.0 == 12", "false");
    }

    @Test
    void evaluateEqualsOnlyRightNumberFalse() {
        assertExpressionEvaluatesTo("\"x\" == 12", "false");
    }

    @Test
    void evaluateEqualsOnlyLeftNumberFalse() {
        assertExpressionEvaluatesTo("12 == \"x\"", "false");
    }

    @Test
    void evaluateEqualsArraysTrue() {
        assertExpressionEvaluatesTo("[1,2,3] == [1,2,3]", "true");
    }

    @Test
    void evaluateEqualsArraysFalse() {
        assertExpressionEvaluatesTo("[1,3] == [1,2,3]", "false");
    }

    @Test
    void evaluateEqualsObjectsTrue() {
        assertExpressionEvaluatesTo("{ \"key\" : true } == { \"key\" : true }", "true");
    }

    @Test
    void evaluateEqualsObjectsFalse() {
        assertExpressionEvaluatesTo("{ \"key\" : true } == { \"key\" : false })", "false");
    }

    @Test
    void evaluateEqualsLeftError() {
        assertExpressionReturnsErrors("(10/0) == true");
    }

    @Test
    void evaluateEqualsRightError() {
        assertExpressionReturnsErrors("true == (10/0)");
    }

    @Test
    void evaluateMoreEquals1ge1() {
        assertExpressionEvaluatesTo("1 >= 1", "true");
    }

    @Test
    void evaluateMoreEquals1ge10() {
        assertExpressionEvaluatesTo("1 >= 10", "false");
    }

    @Test
    void evaluateMoreEquals10ge1() {
        assertExpressionEvaluatesTo("10 >= 1", "true");
    }

    @Test
    void evaluateMoreEqualsLeftError() {
        assertExpressionReturnsErrors("(10/0) >= 10");
    }

    @Test
    void evaluateMoreEqualsRightError() {
        assertExpressionReturnsErrors("10 >= (10/0)");
    }

    @Test
    void evaluateMore1gt1() {
        assertExpressionEvaluatesTo("1 > 1", "false");
    }

    @Test
    void evaluateMore1gt10() {
        assertExpressionEvaluatesTo("1 > 10", "false");
    }

    @Test
    void evaluateMore10gt1() {
        assertExpressionEvaluatesTo("10 > 1", "true");
    }

    @Test
    void evaluateMoreLeftError() {
        assertExpressionReturnsErrors("(10/0) > 10");
    }

    @Test
    void evaluateMoreRightError() {
        assertExpressionReturnsErrors("10 > (10/0)");
    }

    @Test
    void evaluateLessEquals1le1() {
        assertExpressionEvaluatesTo("1 <= 1", "true");
    }

    @Test
    void evaluateLessEquals1le10() {
        assertExpressionEvaluatesTo("1 <= 10", "true");
    }

    @Test
    void evaluateLessEquals10le1() {
        assertExpressionEvaluatesTo("10 <= 1", "false");
    }

    @Test
    void evaluateLessEqualsLeftError() {
        assertExpressionReturnsErrors("(10/0) <= 10");
    }

    @Test
    void evaluateLessEqualsRightError() {
        assertExpressionReturnsErrors("10 <= (10/0)");
    }

    @Test
    void evaluateLess1lt1() {
        assertExpressionEvaluatesTo("1 < 1", "false");
    }

    @Test
    void evaluateLess1lt10() {
        assertExpressionEvaluatesTo("1 < 10", "true");
    }

    @Test
    void evaluateLess10lt1() {
        assertExpressionEvaluatesTo("10 < 1", "false");
    }

    @Test
    void evaluateLessLeftError() {
        assertExpressionReturnsErrors("(10/0) < 10");
    }

    @Test
    void evaluateLessRightError() {
        assertExpressionReturnsErrors("10 < (10/0)");
    }

    @Test
    void divEvaluationShouldFailWithNonNumberLeft() {
        assertExpressionReturnsErrors("null/10");
    }

    @Test
    void divEvaluationShouldFailWithNonNumberRight() {
        assertExpressionReturnsErrors("10/null");
    }

    @Test
    void divEvaluationShouldFailDivisionByZero() {
        assertExpressionReturnsErrors("10/0");
    }

    @Test
    void divEvaluationSucceed() {
        assertExpressionEvaluatesTo("10/2", "5");
    }

    @Test
    void evaluateDivLeftError() {
        assertExpressionReturnsErrors("(10/0) / 5");
    }

    @Test
    void evaluateDivRightError() {
        assertExpressionReturnsErrors("10 / (10/0)");
    }

    @Test
    void moduloEvaluationShouldFailWithNonNumberLeft() {
        assertExpressionReturnsErrors("null%10");
    }

    @Test
    void moduloEvaluationShouldFailWithNonNumberRight() {
        assertExpressionReturnsErrors("10%null");
    }

    @Test
    void moduloEvaluationShouldFailDivisionByZero() {
        assertExpressionReturnsErrors("10%0");
    }

    @Test
    void moduloEvaluationSucceed() {
        assertExpressionEvaluatesTo("11%2", "1");
    }

    @Test
    void evaluateModuloLeftError() {
        assertExpressionReturnsErrors("(10/0) % 5");
    }

    @Test
    void evaluate2Minus10() {
        assertExpressionEvaluatesTo("2.0-10", "-8");
    }

    @Test
    void evaluate10Minus2() {
        assertExpressionEvaluatesTo("10-2", "8");
    }

    @Test
    void evaluate1Minus1() {
        assertExpressionEvaluatesTo("1 - 1", "0");
    }

    @Test
    void evaluate1Minus1BAD() {
        assertExpressionEvaluatesTo("1-1", "0");
    }

    @Test
    void evaluateBAD() {
        assertExpressionEvaluatesTo("5+5-3", "7");
    }

    @Test
    void evaluateMinusLeftError() {
        assertExpressionReturnsErrors("(10/0) - 5");
    }

    @Test
    void evaluateMinusRightError() {
        assertExpressionReturnsErrors("10 - (10/0)");
    }

    @Test
    void evaluate2Multi10() {
        assertExpressionEvaluatesTo("2*10", "20");
    }

    @Test
    void evaluate10Multi2() {
        assertExpressionEvaluatesTo("10*2", "20");
    }

    @Test
    void evaluate1Multi1() {
        assertExpressionEvaluatesTo("1*1", "1");
    }

    @Test
    void evaluateMultiLeftError() {
        assertExpressionReturnsErrors("(10/0) * 5");
    }

    @Test
    void evaluateMultiRightError() {
        assertExpressionReturnsErrors("10 * (10/0)");
    }

    @Test
    void evaluateNotOnBooleanTrue() {
        assertExpressionEvaluatesTo("!true", "false");
    }

    @Test
    void evaluateNotOnBooleanFalse() {
        assertExpressionEvaluatesTo("!false", "true");
    }

    @Test
    void evaluateNotOnWrongType() {
        assertExpressionReturnsErrors("![1,2,3]");
    }

    @Test
    void evaluateNotOnError() {
        assertExpressionReturnsErrors("!(10/0)");
    }

    @Test
    void unaryMinus() {
        assertExpressionEvaluatesTo("-(1)", "-1");
    }

    @Test
    void unaryPlus() {
        assertExpressionEvaluatesTo("+(1)", "1");
    }

    @Test
    void unaryMinusOnError() {
        assertExpressionReturnsErrors("-(10/0)");
    }

    @Test
    void unaryMinusWrongType() {
        assertExpressionReturnsErrors("-null");
    }

    @Test
    void evaluatePlusOnStrings() {
        assertExpressionEvaluatesTo("\"part a &\" + \" part b\"", "\"part a & part b\"");
    }

    @Test
    void evaluatePlusOnLeftString() {
        assertExpressionEvaluatesTo("\"part a &\" + 1", "\"part a &1\"");
    }

    @Test
    void evaluatePlusOnRightString() {
        assertExpressionEvaluatesTo("1 + \"part a &\"", "\"1part a &\"");
    }

    @Test
    void evaluatePlusOnNumbers() {
        assertExpressionEvaluatesTo("1+2", "3");
    }

    @Test
    void evaluatePlusLeftError() {
        assertExpressionReturnsErrors("(10/0) + 10");
    }

    @Test
    void evaluatePlusRightError() {
        assertExpressionReturnsErrors("10 + (10/0)");
    }

    @Test
    void evaluateElementOfOnWrongType() {
        assertExpressionEvaluatesTo("\"A\" in \"B\"", "false");
    }

    @Test
    void evaluateElementOfOneElement() {
        assertExpressionEvaluatesTo("\"A\" in [\"A\"]", "true");
    }

    @Test
    void evaluateElementOfTwoElementsTrue() {
        assertExpressionEvaluatesTo("\"A\" in [\"B\", \"A\"]", "true");
    }

    @Test
    void evaluateElementOfTwoElementsFalse() {
        assertExpressionEvaluatesTo("\"C\" in [\"B\", \"A\"]", "false");
    }

    @Test
    void evaluateElementOfNullLeftAndEmptyArrayFalse() {
        assertExpressionEvaluatesTo("null in []", "false");
    }

    @Test
    void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
        assertExpressionEvaluatesTo("null in [null]", "true");
    }

    @Test
    void evaluateElementOfUndefinedHaystack() {
        assertExpressionEvaluatesTo("\"C\" in undefined", "false");
    }

    @Test
    void evaluateElementOfUndefinedNeedle() {
        assertExpressionEvaluatesTo("undefined in [1,2,3]", "false");
    }

    @Test
    void evaluateElementOfNumbersTrue() {
        assertExpressionEvaluatesTo("1 in [2, 1.0]", "true");
    }

    @Test
    void evaluateElementOfNumbersFalse() {
        assertExpressionEvaluatesTo("1 in [2, \"1.0\"]", "false");
    }

    @Test
    void evaluateElementOfNumbersTrue2() {
        assertExpressionEvaluatesTo("1 in [2, 1.000]", "true");
    }

    @Test
    void evaluateElementOfLeftError() {
        assertExpressionReturnsErrors("(10/0) in []");
    }

    @Test
    void evaluateElementOfRightError() {
        assertExpressionReturnsErrors("10 in (10/0)");
    }

    @Test
    void evaluateRegExTrue() {
        assertExpressionEvaluatesTo("\"test\"=~\".*\"", "true");
    }

    @Test
    void evaluateRegExFalse() {
        assertExpressionEvaluatesTo("\"test\"=~\".\"", "false");
    }

    @Test
    void evaluateRegExPatternError() {
        assertExpressionReturnsErrors("\"test\"=~\"***\"");
    }

    @Test
    void evaluateRegExLeftNull() {
        assertExpressionEvaluatesTo("null =~ \"\"", "false");
    }

    @Test
    void evaluateRegExLeftUndefined() {
        assertExpressionEvaluatesTo("undefined =~ \"\"", "false");
    }

    @Test
    void evaluateRegExLeftWrongType() {
        assertExpressionEvaluatesTo("666 =~ \"\"", "false");
    }

    @Test
    void evaluateRegExRightWrongType() {
        assertExpressionReturnsErrors("\"test\" =~ null");
    }

    @Test
    void evaluateRegExLeftError() {
        assertExpressionReturnsErrors("(10/0) =~ null");
    }

    @Test
    void evaluateRegExRightError() {
        assertExpressionReturnsErrors("\"aaa\" =~ (10/0)");
    }

}
