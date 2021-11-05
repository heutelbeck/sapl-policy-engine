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
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.EvaluationContext;

class EagerOperatorsTest {

	final EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	void evaluateXOrFalseFalse() {
		expressionEvaluatesTo(CTX, "false ^ false", "false");
	}

	@Test
	void evaluateXOrTrueFalse() {
		expressionEvaluatesTo(CTX, "true ^ false", "true");
	}

	@Test
	void evaluateXOrFalseTrue() {
		expressionEvaluatesTo(CTX, "false ^ true", "true");
	}

	@Test
	void evaluateXOrTrueTrue() {
		expressionEvaluatesTo(CTX, "true ^ true", "false");
	}

	@Test
	void evaluateEagerAndFalseFalse() {
		expressionEvaluatesTo(CTX, "false & false", "false");
	}

	@Test
	void evaluateEagerAndTrueFalse() {
		expressionEvaluatesTo(CTX, "true & false", "false");
	}

	@Test
	void evaluateEagerAndFalseTrue() {
		expressionEvaluatesTo(CTX, "false & true", "false");
	}

	@Test
	void evaluateEagerAndTrueTrue() {
		expressionEvaluatesTo(CTX, "true & true", "true");
	}

	@Test
	void evaluateEagerAndWrongDatatypeLeft() {
		expressionErrors(CTX, "5 & true");
	}

	@Test
	void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		expressionErrors(CTX, "true & 5");
	}

	@Test
	void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		expressionErrors(CTX, "false & 5");
	}

	@Test
	void evaluateEagerAndLeftError() {
		expressionErrors(CTX, "(10/0) & true");
	}

	@Test
	void evaluateEagerAndRightError() {
		expressionErrors(CTX, "true & (10/0)");
	}

	@Test
	void evaluateEagerOrFalseFalse() {
		expressionEvaluatesTo(CTX, "false | false", "false");
	}

	@Test
	void evaluateEagerOrTrueFalse() {
		expressionEvaluatesTo(CTX, "true | false", "true");
	}

	@Test
	void evaluateEagerOrFalseTrue() {
		expressionEvaluatesTo(CTX, "false | true", "true");
	}

	@Test
	void evaluateEagerOrWrongDatatypeLeft() {
		expressionErrors(CTX, "5 | true");
	}

	@Test
	void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		expressionErrors(CTX, "false | 7");
	}

	@Test
	void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		expressionErrors(CTX, "true | 7");
	}

	@Test
	void evaluateEagerOrLeftError() {
		expressionErrors(CTX, "(10/0) | true");
	}

	@Test
	void evaluateEagerOrRightError() {
		expressionErrors(CTX, "true | (10/0)");
	}

	@Test
	void evaluateNotEqualsFalse() {
		expressionEvaluatesTo(CTX, "true != true", "false");
	}

	@Test
	void evaluateNotEqualsTrue() {
		expressionEvaluatesTo(CTX, "null != true", "true");
	}

	@Test
	void evaluateNotEqualsNullLeftAndStringRightTrue() {
		expressionEvaluatesTo(CTX, "null != \"x\"", "true");
	}

	@Test
	void evaluateNotEqualsNullLeftAndNullRightFalse() {
		expressionEvaluatesTo(CTX, "null != null", "false");
	}

	@Test
	void evaluateNotEqualsNumericalFalse() {
		expressionEvaluatesTo(CTX, "17 != 17.0", "false");
	}

	@Test
	void evaluateNotEqualsNumericalTrue() {
		expressionEvaluatesTo(CTX, "17 != (17+2)", "true");
	}

	@Test
	void evaluateNotEqualsLeftError() {
		expressionErrors(CTX, "(10/0) != true");
	}

	@Test
	void evaluateNotEqualsRightError() {
		expressionErrors(CTX, "true != (10/0)");
	}

	@Test
	void evaluateNotEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo(CTX, "\"x\" != 12", "true");
	}

	@Test
	void evaluateNotEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo(CTX, "12 != \"x\"", "true");
	}

	@Test
	void evaluateNotEqualsArraysFalse() {
		expressionEvaluatesTo(CTX, "[1,2,3] != [1,2,3]", "false");
	}

	@Test
	void evaluateNotEqualsArraysTrue() {
		expressionEvaluatesTo(CTX, "[1,3] != [1,2,3]", "true");
	}

	@Test
	void evaluateNotEqualsObjectsFalse() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } != { \"key\" : true })", "false");
	}

	@Test
	void evaluateNotEqualsObjectsTrue() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } != { \"key\" : false })", "true");
	}

	@Test
	void evaluateNotEqualsBothUndefinedFalse() {
		expressionEvaluatesTo(CTX, "undefined != undefined", "false");
	}

	@Test
	void evaluateNotEqualsLeftUndefinedTrue() {
		expressionEvaluatesTo(CTX, "undefined != 0", "true");
	}

	@Test
	void evaluateNotEqualsRightUndefinedTrue() {
		expressionEvaluatesTo(CTX, "0 != undefined", "true");
	}

	@Test
	void evaluateEqualsTrue() {
		expressionEvaluatesTo(CTX, "true == true", "true");
	}

	@Test
	void evaluateEqualsFalse() {
		expressionEvaluatesTo(CTX, "null == true", "false");
	}

	@Test
	void evaluateEqualsNullLeftAndStringRightFalse() {
		expressionEvaluatesTo(CTX, "null == \"a\"", "false");
	}

	@Test
	void evaluateEqualsNullLeftAndNullRightTrue() {
		expressionEvaluatesTo(CTX, "null == null", "true");
	}

	@Test
	void evaluateEqualsBothUndefinedTrue() {
		expressionEvaluatesTo(CTX, "undefined == undefined", "true");
	}

	@Test
	void evaluateEqualsLeftUndefinedFalse() {
		expressionEvaluatesTo(CTX, "undefined == 0", "false");
	}

	@Test
	void evaluateEqualsRightUndefinedFalse() {
		expressionEvaluatesTo(CTX, "0 == undefined", "false");
	}

	@Test
	void evaluateEqualsNumbersTrue() {
		expressionEvaluatesTo(CTX, "10.0 == (12 - 2)", "true");
	}

	@Test
	void evaluateEqualsNumbersFalse() {
		expressionEvaluatesTo(CTX, "10.0 == 12", "false");
	}

	@Test
	void evaluateEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo(CTX, "\"x\" == 12", "false");
	}

	@Test
	void evaluateEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo(CTX, "12 == \"x\"", "false");
	}

	@Test
	void evaluateEqualsArraysTrue() {
		expressionEvaluatesTo(CTX, "[1,2,3] == [1,2,3]", "true");
	}

	@Test
	void evaluateEqualsArraysFalse() {
		expressionEvaluatesTo(CTX, "[1,3] == [1,2,3]", "false");
	}

	@Test
	void evaluateEqualsObjectsTrue() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } == { \"key\" : true }", "true");
	}

	@Test
	void evaluateEqualsObjectsFalse() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } == { \"key\" : false })", "false");
	}

	@Test
	void evaluateEqualsLeftError() {
		expressionErrors(CTX, "(10/0) == true");
	}

	@Test
	void evaluateEqualsRightError() {
		expressionErrors(CTX, "true == (10/0)");
	}

	@Test
	void evaluateMoreEquals1ge1() {
		expressionEvaluatesTo(CTX, "1 >= 1", "true");
	}

	@Test
	void evaluateMoreEquals1ge10() {
		expressionEvaluatesTo(CTX, "1 >= 10", "false");
	}

	@Test
	void evaluateMoreEquals10ge1() {
		expressionEvaluatesTo(CTX, "10 >= 1", "true");
	}

	@Test
	void evaluateMoreEqualsLeftError() {
		expressionErrors(CTX, "(10/0) >= 10");
	}

	@Test
	void evaluateMoreEqualsRightError() {
		expressionErrors(CTX, "10 >= (10/0)");
	}

	@Test
	void evaluateMore1gt1() {
		expressionEvaluatesTo(CTX, "1 > 1", "false");
	}

	@Test
	void evaluateMore1gt10() {
		expressionEvaluatesTo(CTX, "1 > 10", "false");
	}

	@Test
	void evaluateMore10gt1() {
		expressionEvaluatesTo(CTX, "10 > 1", "true");
	}

	@Test
	void evaluateMoreLeftError() {
		expressionErrors(CTX, "(10/0) > 10");
	}

	@Test
	void evaluateMoreRightError() {
		expressionErrors(CTX, "10 > (10/0)");
	}

	@Test
	void evaluateLessEquals1le1() {
		expressionEvaluatesTo(CTX, "1 <= 1", "true");
	}

	@Test
	void evaluateLessEquals1le10() {
		expressionEvaluatesTo(CTX, "1 <= 10", "true");
	}

	@Test
	void evaluateLessEquals10le1() {
		expressionEvaluatesTo(CTX, "10 <= 1", "false");
	}

	@Test
	void evaluateLessEqualsLeftError() {
		expressionErrors(CTX, "(10/0) <= 10");
	}

	@Test
	void evaluateLessEqualsRightError() {
		expressionErrors(CTX, "10 <= (10/0)");
	}

	@Test
	void evaluateLess1lt1() {
		expressionEvaluatesTo(CTX, "1 < 1", "false");
	}

	@Test
	void evaluateLess1lt10() {
		expressionEvaluatesTo(CTX, "1 < 10", "true");
	}

	@Test
	void evaluateLess10lt1() {
		expressionEvaluatesTo(CTX, "10 < 1", "false");
	}

	@Test
	void evaluateLessLeftError() {
		expressionErrors(CTX, "(10/0) < 10");
	}

	@Test
	void evaluateLessRightError() {
		expressionErrors(CTX, "10 < (10/0)");
	}

	@Test
	void divEvaluationShouldFailWithNonNumberLeft() {
		expressionErrors(CTX, "null/10");
	}

	@Test
	void divEvaluationShouldFailWithNonNumberRight() {
		expressionErrors(CTX, "10/null");
	}

	@Test
	void divEvaluationShouldFailDivisionByZero() {
		expressionErrors(CTX, "10/0");
	}

	@Test
	void divEvaluationSucceed() {
		expressionEvaluatesTo(CTX, "10/2", "5");
	}

	@Test
	void evaluateDivLeftError() {
		expressionErrors(CTX, "(10/0) / 5");
	}

	@Test
	void evaluateDivRightError() {
		expressionErrors(CTX, "10 / (10/0)");
	}

	@Test
	void moduloEvaluationShouldFailWithNonNumberLeft() {
		expressionErrors(CTX, "null%10");
	}

	@Test
	void moduloEvaluationShouldFailWithNonNumberRight() {
		expressionErrors(CTX, "10%null");
	}

	@Test
	void moduloEvaluationShouldFailDivisionByZero() {
		expressionErrors(CTX, "10%0");
	}

	@Test
	void moduloEvaluationSucceed() {
		expressionEvaluatesTo(CTX, "11%2", "1");
	}

	@Test
	void evaluateModuloLeftError() {
		expressionErrors(CTX, "(10/0) % 5");
	}

	@Test
	void evaluate2Minus10() {
		expressionEvaluatesTo(CTX, "2.0-10", "-8");
	}

	@Test
	void evaluate10Minus2() {
		expressionEvaluatesTo(CTX, "10-2", "8");
	}

	@Test
	void evaluate1Minus1() {
		expressionEvaluatesTo(CTX, "1 - 1", "0");
	}

	@Test
	void evaluate1Minus1BAD() {
		expressionEvaluatesTo(CTX, "1-1", "0");
	}

	@Test
	void evaluateBAD() {
		expressionEvaluatesTo(CTX, "5+5-3", "7");
	}

	@Test
	void evaluateMinusLeftError() {
		expressionErrors(CTX, "(10/0) - 5");
	}

	@Test
	void evaluateMinusRightError() {
		expressionErrors(CTX, "10 - (10/0)");
	}

	@Test
	void evaluate2Multi10() {
		expressionEvaluatesTo(CTX, "2*10", "20");
	}

	@Test
	void evaluate10Multi2() {
		expressionEvaluatesTo(CTX, "10*2", "20");
	}

	@Test
	void evaluate1Multi1() {
		expressionEvaluatesTo(CTX, "1*1", "1");
	}

	@Test
	void evaluateMultiLeftError() {
		expressionErrors(CTX, "(10/0) * 5");
	}

	@Test
	void evaluateMultiRightError() {
		expressionErrors(CTX, "10 * (10/0)");
	}

	@Test
	void evaluateNotOnBooleanTrue() {
		expressionEvaluatesTo(CTX, "!true", "false");
	}

	@Test
	void evaluateNotOnBooleanFalse() {
		expressionEvaluatesTo(CTX, "!false", "true");
	}

	@Test
	void evaluateNotOnWrongType() {
		expressionErrors(CTX, "![1,2,3]");
	}

	@Test
	void evaluateNotOnError() {
		expressionErrors(CTX, "!(10/0)");
	}

	@Test
	void unaryMinus() {
		expressionEvaluatesTo(CTX, "-(1)", "-1");
	}

	@Test
	void unaryPlus() {
		expressionEvaluatesTo(CTX, "+(1)", "1");
	}

	@Test
	void unaryMinusOnError() {
		expressionErrors(CTX, "-(10/0)");
	}

	@Test
	void unaryMinusWrongType() {
		expressionErrors(CTX, "-null");
	}

	@Test
	void evaluatePlusOnStrings() {
		expressionEvaluatesTo(CTX, "\"part a &\" + \" part b\"", "\"part a & part b\"");
	}

	@Test
	void evaluatePlusOnLeftString() {
		expressionEvaluatesTo(CTX, "\"part a &\" + 1", "\"part a &1\"");
	}

	@Test
	void evaluatePlusOnRightString() {
		expressionEvaluatesTo(CTX, "1 + \"part a &\"", "\"1part a &\"");
	}

	@Test
	void evaluatePlusOnNumbers() {
		expressionEvaluatesTo(CTX, "1+2", "3");
	}

	@Test
	void evaluatePlusLeftError() {
		expressionErrors(CTX, "(10/0) + 10");
	}

	@Test
	void evaluatePlusRightError() {
		expressionErrors(CTX, "10 + (10/0)");
	}

	@Test
	void evaluateElementOfOnWrongType() {
		expressionEvaluatesTo(CTX, "\"A\" in \"B\"", "false");
	}

	@Test
	void evaluateElementOfOneElement() {
		expressionEvaluatesTo(CTX, "\"A\" in [\"A\"]", "true");
	}

	@Test
	void evaluateElementOfTwoElementsTrue() {
		expressionEvaluatesTo(CTX, "\"A\" in [\"B\", \"A\"]", "true");
	}

	@Test
	void evaluateElementOfTwoElementsFalse() {
		expressionEvaluatesTo(CTX, "\"C\" in [\"B\", \"A\"]", "false");
	}

	@Test
	void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		expressionEvaluatesTo(CTX, "null in []", "false");
	}

	@Test
	void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		expressionEvaluatesTo(CTX, "null in [null]", "true");
	}

	@Test
	void evaluateElementOfUndefinedHaystack() {
		expressionEvaluatesTo(CTX, "\"C\" in undefined", "false");
	}

	@Test
	void evaluateElementOfUndefinedNeedle() {
		expressionEvaluatesTo(CTX, "undefined in [1,2,3]", "false");
	}

	@Test
	void evaluateElementOfNumbersTrue() {
		expressionEvaluatesTo(CTX, "1 in [2, 1.0]", "true");
	}

	@Test
	void evaluateElementOfNumbersFalse() {
		expressionEvaluatesTo(CTX, "1 in [2, \"1.0\"]", "false");
	}

	@Test
	void evaluateElementOfNumbersTrue2() throws IOException {
		expressionEvaluatesTo(CTX, "1 in [2, 1.000]", "true");
	}

	@Test
	void evaluateElementOfLeftError() {
		expressionErrors(CTX, "(10/0) in []");
	}

	@Test
	void evaluateElementOfRightError() {
		expressionErrors(CTX, "10 in (10/0)");
	}

	@Test
	void evaluateRegExTrue() {
		expressionEvaluatesTo(CTX, "\"test\"=~\".*\"", "true");
	}

	@Test
	void evaluateRegExFalse() {
		expressionEvaluatesTo(CTX, "\"test\"=~\".\"", "false");
	}

	@Test
	void evaluateRegExPatternError() {
		expressionErrors(CTX, "\"test\"=~\"***\"");
	}

	@Test
	void evaluateRegExLeftNull() {
		expressionEvaluatesTo(CTX, "null =~ \"\"", "false");
	}

	@Test
	void evaluateRegExLeftUndefined() {
		expressionEvaluatesTo(CTX, "undefined =~ \"\"", "false");
	}

	@Test
	void evaluateRegExLeftWrongType() {
		expressionEvaluatesTo(CTX, "666 =~ \"\"", "false");
	}

	@Test
	void evaluateRegExRightWrongType() {
		expressionErrors(CTX, "\"test\" =~ null");
	}

	@Test
	void evaluateRegExLeftError() {
		expressionErrors(CTX, "(10/0) =~ null");
	}

	@Test
	void evaluateRegExRightError() {
		expressionErrors(CTX, "\"aaa\" =~ (10/0)");
	}

}
