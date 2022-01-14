/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class EagerOperatorsTest {

	@Test
	void evaluateXOrFalseFalse() {
		expressionEvaluatesTo("false ^ false", "false");
	}

	@Test
	void evaluateXOrTrueFalse() {
		expressionEvaluatesTo("true ^ false", "true");
	}

	@Test
	void evaluateXOrFalseTrue() {
		expressionEvaluatesTo("false ^ true", "true");
	}

	@Test
	void evaluateXOrTrueTrue() {
		expressionEvaluatesTo("true ^ true", "false");
	}

	@Test
	void evaluateEagerAndFalseFalse() {
		expressionEvaluatesTo("false & false", "false");
	}

	@Test
	void evaluateEagerAndTrueFalse() {
		expressionEvaluatesTo("true & false", "false");
	}

	@Test
	void evaluateEagerAndFalseTrue() {
		expressionEvaluatesTo("false & true", "false");
	}

	@Test
	void evaluateEagerAndTrueTrue() {
		expressionEvaluatesTo("true & true", "true");
	}

	@Test
	void evaluateEagerAndWrongDatatypeLeft() {
		expressionErrors("5 & true");
	}

	@Test
	void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		expressionErrors("true & 5");
	}

	@Test
	void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		expressionErrors("false & 5");
	}

	@Test
	void evaluateEagerAndLeftError() {
		expressionErrors("(10/0) & true");
	}

	@Test
	void evaluateEagerAndRightError() {
		expressionErrors("true & (10/0)");
	}

	@Test
	void evaluateEagerOrFalseFalse() {
		expressionEvaluatesTo("false | false", "false");
	}

	@Test
	void evaluateEagerOrTrueFalse() {
		expressionEvaluatesTo("true | false", "true");
	}

	@Test
	void evaluateEagerOrFalseTrue() {
		expressionEvaluatesTo("false | true", "true");
	}

	@Test
	void evaluateEagerOrWrongDatatypeLeft() {
		expressionErrors("5 | true");
	}

	@Test
	void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		expressionErrors("false | 7");
	}

	@Test
	void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		expressionErrors("true | 7");
	}

	@Test
	void evaluateEagerOrLeftError() {
		expressionErrors("(10/0) | true");
	}

	@Test
	void evaluateEagerOrRightError() {
		expressionErrors("true | (10/0)");
	}

	@Test
	void evaluateNotEqualsFalse() {
		expressionEvaluatesTo("true != true", "false");
	}

	@Test
	void evaluateNotEqualsTrue() {
		expressionEvaluatesTo("null != true", "true");
	}

	@Test
	void evaluateNotEqualsNullLeftAndStringRightTrue() {
		expressionEvaluatesTo("null != \"x\"", "true");
	}

	@Test
	void evaluateNotEqualsNullLeftAndNullRightFalse() {
		expressionEvaluatesTo("null != null", "false");
	}

	@Test
	void evaluateNotEqualsNumericalFalse() {
		expressionEvaluatesTo("17 != 17.0", "false");
	}

	@Test
	void evaluateNotEqualsNumericalTrue() {
		expressionEvaluatesTo("17 != (17+2)", "true");
	}

	@Test
	void evaluateNotEqualsLeftError() {
		expressionErrors("(10/0) != true");
	}

	@Test
	void evaluateNotEqualsRightError() {
		expressionErrors("true != (10/0)");
	}

	@Test
	void evaluateNotEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo("\"x\" != 12", "true");
	}

	@Test
	void evaluateNotEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo("12 != \"x\"", "true");
	}

	@Test
	void evaluateNotEqualsArraysFalse() {
		expressionEvaluatesTo("[1,2,3] != [1,2,3]", "false");
	}

	@Test
	void evaluateNotEqualsArraysTrue() {
		expressionEvaluatesTo("[1,3] != [1,2,3]", "true");
	}

	@Test
	void evaluateNotEqualsObjectsFalse() {
		expressionEvaluatesTo("{ \"key\" : true } != { \"key\" : true })", "false");
	}

	@Test
	void evaluateNotEqualsObjectsTrue() {
		expressionEvaluatesTo("{ \"key\" : true } != { \"key\" : false })", "true");
	}

	@Test
	void evaluateNotEqualsBothUndefinedFalse() {
		expressionEvaluatesTo("undefined != undefined", "false");
	}

	@Test
	void evaluateNotEqualsLeftUndefinedTrue() {
		expressionEvaluatesTo("undefined != 0", "true");
	}

	@Test
	void evaluateNotEqualsRightUndefinedTrue() {
		expressionEvaluatesTo("0 != undefined", "true");
	}

	@Test
	void evaluateEqualsTrue() {
		expressionEvaluatesTo("true == true", "true");
	}

	@Test
	void evaluateEqualsFalse() {
		expressionEvaluatesTo("null == true", "false");
	}

	@Test
	void evaluateEqualsNullLeftAndStringRightFalse() {
		expressionEvaluatesTo("null == \"a\"", "false");
	}

	@Test
	void evaluateEqualsNullLeftAndNullRightTrue() {
		expressionEvaluatesTo("null == null", "true");
	}

	@Test
	void evaluateEqualsBothUndefinedTrue() {
		expressionEvaluatesTo("undefined == undefined", "true");
	}

	@Test
	void evaluateEqualsLeftUndefinedFalse() {
		expressionEvaluatesTo("undefined == 0", "false");
	}

	@Test
	void evaluateEqualsRightUndefinedFalse() {
		expressionEvaluatesTo("0 == undefined", "false");
	}

	@Test
	void evaluateEqualsNumbersTrue() {
		expressionEvaluatesTo("10.0 == (12 - 2)", "true");
	}

	@Test
	void evaluateEqualsNumbersFalse() {
		expressionEvaluatesTo("10.0 == 12", "false");
	}

	@Test
	void evaluateEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo("\"x\" == 12", "false");
	}

	@Test
	void evaluateEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo("12 == \"x\"", "false");
	}

	@Test
	void evaluateEqualsArraysTrue() {
		expressionEvaluatesTo("[1,2,3] == [1,2,3]", "true");
	}

	@Test
	void evaluateEqualsArraysFalse() {
		expressionEvaluatesTo("[1,3] == [1,2,3]", "false");
	}

	@Test
	void evaluateEqualsObjectsTrue() {
		expressionEvaluatesTo("{ \"key\" : true } == { \"key\" : true }", "true");
	}

	@Test
	void evaluateEqualsObjectsFalse() {
		expressionEvaluatesTo("{ \"key\" : true } == { \"key\" : false })", "false");
	}

	@Test
	void evaluateEqualsLeftError() {
		expressionErrors("(10/0) == true");
	}

	@Test
	void evaluateEqualsRightError() {
		expressionErrors("true == (10/0)");
	}

	@Test
	void evaluateMoreEquals1ge1() {
		expressionEvaluatesTo("1 >= 1", "true");
	}

	@Test
	void evaluateMoreEquals1ge10() {
		expressionEvaluatesTo("1 >= 10", "false");
	}

	@Test
	void evaluateMoreEquals10ge1() {
		expressionEvaluatesTo("10 >= 1", "true");
	}

	@Test
	void evaluateMoreEqualsLeftError() {
		expressionErrors("(10/0) >= 10");
	}

	@Test
	void evaluateMoreEqualsRightError() {
		expressionErrors("10 >= (10/0)");
	}

	@Test
	void evaluateMore1gt1() {
		expressionEvaluatesTo("1 > 1", "false");
	}

	@Test
	void evaluateMore1gt10() {
		expressionEvaluatesTo("1 > 10", "false");
	}

	@Test
	void evaluateMore10gt1() {
		expressionEvaluatesTo("10 > 1", "true");
	}

	@Test
	void evaluateMoreLeftError() {
		expressionErrors("(10/0) > 10");
	}

	@Test
	void evaluateMoreRightError() {
		expressionErrors("10 > (10/0)");
	}

	@Test
	void evaluateLessEquals1le1() {
		expressionEvaluatesTo("1 <= 1", "true");
	}

	@Test
	void evaluateLessEquals1le10() {
		expressionEvaluatesTo("1 <= 10", "true");
	}

	@Test
	void evaluateLessEquals10le1() {
		expressionEvaluatesTo("10 <= 1", "false");
	}

	@Test
	void evaluateLessEqualsLeftError() {
		expressionErrors("(10/0) <= 10");
	}

	@Test
	void evaluateLessEqualsRightError() {
		expressionErrors("10 <= (10/0)");
	}

	@Test
	void evaluateLess1lt1() {
		expressionEvaluatesTo("1 < 1", "false");
	}

	@Test
	void evaluateLess1lt10() {
		expressionEvaluatesTo("1 < 10", "true");
	}

	@Test
	void evaluateLess10lt1() {
		expressionEvaluatesTo("10 < 1", "false");
	}

	@Test
	void evaluateLessLeftError() {
		expressionErrors("(10/0) < 10");
	}

	@Test
	void evaluateLessRightError() {
		expressionErrors("10 < (10/0)");
	}

	@Test
	void divEvaluationShouldFailWithNonNumberLeft() {
		expressionErrors("null/10");
	}

	@Test
	void divEvaluationShouldFailWithNonNumberRight() {
		expressionErrors("10/null");
	}

	@Test
	void divEvaluationShouldFailDivisionByZero() {
		expressionErrors("10/0");
	}

	@Test
	void divEvaluationSucceed() {
		expressionEvaluatesTo("10/2", "5");
	}

	@Test
	void evaluateDivLeftError() {
		expressionErrors("(10/0) / 5");
	}

	@Test
	void evaluateDivRightError() {
		expressionErrors("10 / (10/0)");
	}

	@Test
	void moduloEvaluationShouldFailWithNonNumberLeft() {
		expressionErrors("null%10");
	}

	@Test
	void moduloEvaluationShouldFailWithNonNumberRight() {
		expressionErrors("10%null");
	}

	@Test
	void moduloEvaluationShouldFailDivisionByZero() {
		expressionErrors("10%0");
	}

	@Test
	void moduloEvaluationSucceed() {
		expressionEvaluatesTo("11%2", "1");
	}

	@Test
	void evaluateModuloLeftError() {
		expressionErrors("(10/0) % 5");
	}

	@Test
	void evaluate2Minus10() {
		expressionEvaluatesTo("2.0-10", "-8");
	}

	@Test
	void evaluate10Minus2() {
		expressionEvaluatesTo("10-2", "8");
	}

	@Test
	void evaluate1Minus1() {
		expressionEvaluatesTo("1 - 1", "0");
	}

	@Test
	void evaluate1Minus1BAD() {
		expressionEvaluatesTo("1-1", "0");
	}

	@Test
	void evaluateBAD() {
		expressionEvaluatesTo("5+5-3", "7");
	}

	@Test
	void evaluateMinusLeftError() {
		expressionErrors("(10/0) - 5");
	}

	@Test
	void evaluateMinusRightError() {
		expressionErrors("10 - (10/0)");
	}

	@Test
	void evaluate2Multi10() {
		expressionEvaluatesTo("2*10", "20");
	}

	@Test
	void evaluate10Multi2() {
		expressionEvaluatesTo("10*2", "20");
	}

	@Test
	void evaluate1Multi1() {
		expressionEvaluatesTo("1*1", "1");
	}

	@Test
	void evaluateMultiLeftError() {
		expressionErrors("(10/0) * 5");
	}

	@Test
	void evaluateMultiRightError() {
		expressionErrors("10 * (10/0)");
	}

	@Test
	void evaluateNotOnBooleanTrue() {
		expressionEvaluatesTo("!true", "false");
	}

	@Test
	void evaluateNotOnBooleanFalse() {
		expressionEvaluatesTo("!false", "true");
	}

	@Test
	void evaluateNotOnWrongType() {
		expressionErrors("![1,2,3]");
	}

	@Test
	void evaluateNotOnError() {
		expressionErrors("!(10/0)");
	}

	@Test
	void unaryMinus() {
		expressionEvaluatesTo("-(1)", "-1");
	}

	@Test
	void unaryPlus() {
		expressionEvaluatesTo("+(1)", "1");
	}

	@Test
	void unaryMinusOnError() {
		expressionErrors("-(10/0)");
	}

	@Test
	void unaryMinusWrongType() {
		expressionErrors("-null");
	}

	@Test
	void evaluatePlusOnStrings() {
		expressionEvaluatesTo("\"part a &\" + \" part b\"", "\"part a & part b\"");
	}

	@Test
	void evaluatePlusOnLeftString() {
		expressionEvaluatesTo("\"part a &\" + 1", "\"part a &1\"");
	}

	@Test
	void evaluatePlusOnRightString() {
		expressionEvaluatesTo("1 + \"part a &\"", "\"1part a &\"");
	}

	@Test
	void evaluatePlusOnNumbers() {
		expressionEvaluatesTo("1+2", "3");
	}

	@Test
	void evaluatePlusLeftError() {
		expressionErrors("(10/0) + 10");
	}

	@Test
	void evaluatePlusRightError() {
		expressionErrors("10 + (10/0)");
	}

	@Test
	void evaluateElementOfOnWrongType() {
		expressionEvaluatesTo("\"A\" in \"B\"", "false");
	}

	@Test
	void evaluateElementOfOneElement() {
		expressionEvaluatesTo("\"A\" in [\"A\"]", "true");
	}

	@Test
	void evaluateElementOfTwoElementsTrue() {
		expressionEvaluatesTo("\"A\" in [\"B\", \"A\"]", "true");
	}

	@Test
	void evaluateElementOfTwoElementsFalse() {
		expressionEvaluatesTo("\"C\" in [\"B\", \"A\"]", "false");
	}

	@Test
	void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		expressionEvaluatesTo("null in []", "false");
	}

	@Test
	void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		expressionEvaluatesTo("null in [null]", "true");
	}

	@Test
	void evaluateElementOfUndefinedHaystack() {
		expressionEvaluatesTo("\"C\" in undefined", "false");
	}

	@Test
	void evaluateElementOfUndefinedNeedle() {
		expressionEvaluatesTo("undefined in [1,2,3]", "false");
	}

	@Test
	void evaluateElementOfNumbersTrue() {
		expressionEvaluatesTo("1 in [2, 1.0]", "true");
	}

	@Test
	void evaluateElementOfNumbersFalse() {
		expressionEvaluatesTo("1 in [2, \"1.0\"]", "false");
	}

	@Test
	void evaluateElementOfNumbersTrue2() throws IOException {
		expressionEvaluatesTo("1 in [2, 1.000]", "true");
	}

	@Test
	void evaluateElementOfLeftError() {
		expressionErrors("(10/0) in []");
	}

	@Test
	void evaluateElementOfRightError() {
		expressionErrors("10 in (10/0)");
	}

	@Test
	void evaluateRegExTrue() {
		expressionEvaluatesTo("\"test\"=~\".*\"", "true");
	}

	@Test
	void evaluateRegExFalse() {
		expressionEvaluatesTo("\"test\"=~\".\"", "false");
	}

	@Test
	void evaluateRegExPatternError() {
		expressionErrors("\"test\"=~\"***\"");
	}

	@Test
	void evaluateRegExLeftNull() {
		expressionEvaluatesTo("null =~ \"\"", "false");
	}

	@Test
	void evaluateRegExLeftUndefined() {
		expressionEvaluatesTo("undefined =~ \"\"", "false");
	}

	@Test
	void evaluateRegExLeftWrongType() {
		expressionEvaluatesTo("666 =~ \"\"", "false");
	}

	@Test
	void evaluateRegExRightWrongType() {
		expressionErrors("\"test\" =~ null");
	}

	@Test
	void evaluateRegExLeftError() {
		expressionErrors("(10/0) =~ null");
	}

	@Test
	void evaluateRegExRightError() {
		expressionErrors("\"aaa\" =~ (10/0)");
	}

}
