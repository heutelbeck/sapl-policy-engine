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

import org.junit.Ignore;
import org.junit.Test;

import io.sapl.interpreter.EvaluationContext;

public class EagerOperatorsTest {

	EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void evaluateEagerAndFalseFalse() {
		expressionEvaluatesTo(CTX, "false & false", "false");
	}

	@Test
	public void evaluateEagerAndTrueFalse() {
		expressionEvaluatesTo(CTX, "true & false", "false");
	}

	@Test
	public void evaluateEagerAndFalseTrue() {
		expressionEvaluatesTo(CTX, "false & true", "false");
	}

	@Test
	public void evaluateEagerAndTrueTrue() {
		expressionEvaluatesTo(CTX, "true & true", "true");
	}

	@Test
	public void evaluateEagerAndWrongDatatypeLeft() {
		expressionErrors(CTX, "5 & true");
	}

	@Test
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		expressionErrors(CTX, "true & 5");
	}

	@Test
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		expressionErrors(CTX, "false & 5");
	}

	@Test
	public void evaluateEagerAndLeftError() {
		expressionErrors(CTX, "(10/0) & true");
	}

	@Test
	public void evaluateEagerAndRightError() {
		expressionErrors(CTX, "true & (10/0)");
	}

	@Test
	public void evaluateEagerOrFalseFalse() {
		expressionEvaluatesTo(CTX, "false | false", "false");
	}

	@Test
	public void evaluateEagerOrTrueFalse() {
		expressionEvaluatesTo(CTX, "true | false", "true");
	}

	@Test
	public void evaluateEagerOrFalseTrue() {
		expressionEvaluatesTo(CTX, "false | true", "true");
	}

	@Test
	public void evaluateEagerOrWrongDatatypeLeft() {
		expressionErrors(CTX, "5 | true");
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		expressionErrors(CTX, "false | 7");
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		expressionErrors(CTX, "true | 7");
	}

	@Test
	public void evaluateEagerOrLeftError() {
		expressionErrors(CTX, "(10/0) | true");
	}

	@Test
	public void evaluateEagerOrRightError() {
		expressionErrors(CTX, "true | (10/0)");
	}

	@Test
	public void evaluateNotEqualsFalse() {
		expressionEvaluatesTo(CTX, "true != true", "false");
	}

	@Test
	public void evaluateNotEqualsTrue() {
		expressionEvaluatesTo(CTX, "null != true", "true");
	}

	@Test
	public void evaluateNotEqualsNullLeftAndStringRightTrue() {
		expressionEvaluatesTo(CTX, "null != \"x\"", "true");
	}

	@Test
	public void evaluateNotEqualsNullLeftAndNullRightFalse() {
		expressionEvaluatesTo(CTX, "null != null", "false");
	}

	@Test
	public void evaluateNotEqualsNumericalFalse() {
		expressionEvaluatesTo(CTX, "17 != 17.0", "false");
	}

	@Test
	public void evaluateNotEqualsNumericalTrue() {
		expressionEvaluatesTo(CTX, "17 != (17+2)", "true");
	}

	@Test
	public void evaluateNotEqualsLeftError() {
		expressionErrors(CTX, "(10/0) != true");
	}

	@Test
	public void evaluateNotEqualsRightError() {
		expressionErrors(CTX, "true != (10/0)");
	}

	@Test
	public void evaluateNotEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo(CTX, "\"x\" != 12", "true");
	}

	@Test
	public void evaluateNotEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo(CTX, "12 != \"x\"", "true");
	}

	@Test
	public void evaluateNotEqualsArraysFalse() {
		expressionEvaluatesTo(CTX, "[1,2,3] != [1,2,3]", "false");
	}

	@Test
	public void evaluateNotEqualsArraysTrue() {
		expressionEvaluatesTo(CTX, "[1,3] != [1,2,3]", "true");
	}

	@Test
	public void evaluateNotEqualsObjectsFalse() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } != { \"key\" : true })", "false");
	}

	@Test
	public void evaluateNotEqualsObjectsTrue() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } != { \"key\" : false })", "true");
	}

	@Test
	public void evaluateNotEqualsBothUndefinedFalse() {
		expressionEvaluatesTo(CTX, "undefined != undefined", "false");
	}

	@Test
	public void evaluateNotEqualsLeftUndefinedTrue() {
		expressionEvaluatesTo(CTX, "undefined != 0", "true");
	}

	@Test
	public void evaluateNotEqualsRightUndefinedTrue() {
		expressionEvaluatesTo(CTX, "0 != undefined", "true");
	}

	@Test
	public void evaluateEqualsTrue() {
		expressionEvaluatesTo(CTX, "true == true", "true");
	}

	@Test
	public void evaluateEqualsFalse() {
		expressionEvaluatesTo(CTX, "null == true", "false");
	}

	@Test
	public void evaluateEqualsNullLeftAndStringRightFalse() {
		expressionEvaluatesTo(CTX, "null == \"a\"", "false");
	}

	@Test
	public void evaluateEqualsNullLeftAndNullRightTrue() {
		expressionEvaluatesTo(CTX, "null == null", "true");
	}

	@Test
	public void evaluateEqualsBothUndefinedTrue() {
		expressionEvaluatesTo(CTX, "undefined == undefined", "true");
	}

	@Test
	public void evaluateEqualsLeftUndefinedFalse() {
		expressionEvaluatesTo(CTX, "undefined == 0", "false");
	}

	@Test
	public void evaluateEqualsRightUndefinedFalse() {
		expressionEvaluatesTo(CTX, "0 == undefined", "false");
	}

	@Test
	public void evaluateEqualsNumbersTrue() {
		expressionEvaluatesTo(CTX, "10.0 == (12 - 2)", "true");
	}

	@Test
	public void evaluateEqualsNumbersFalse() {
		expressionEvaluatesTo(CTX, "10.0 == 12", "false");
	}

	@Test
	public void evaluateEqualsOnlyRightNumberFalse() {
		expressionEvaluatesTo(CTX, "\"x\" == 12", "false");
	}

	@Test
	public void evaluateEqualsOnlyLeftNumberFalse() {
		expressionEvaluatesTo(CTX, "12 == \"x\"", "false");
	}

	@Test
	public void evaluateEqualsArraysTrue() {
		expressionEvaluatesTo(CTX, "[1,2,3] == [1,2,3]", "true");
	}

	@Test
	public void evaluateEqualsArraysFalse() {
		expressionEvaluatesTo(CTX, "[1,3] == [1,2,3]", "false");
	}

	@Test
	public void evaluateEqualsObjectsTrue() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } == { \"key\" : true }", "true");
	}

	@Test
	public void evaluateEqualsObjectsFalse() {
		expressionEvaluatesTo(CTX, "{ \"key\" : true } == { \"key\" : false })", "false");
	}

	@Test
	public void evaluateEqualsLeftError() {
		expressionErrors(CTX, "(10/0) == true");
	}

	@Test
	public void evaluateEqualsRightError() {
		expressionErrors(CTX, "true == (10/0)");
	}

	@Test
	public void evaluateMoreEquals1ge1() {
		expressionEvaluatesTo(CTX, "1 >= 1", "true");
	}

	@Test
	public void evaluateMoreEquals1ge10() {
		expressionEvaluatesTo(CTX, "1 >= 10", "false");
	}

	@Test
	public void evaluateMoreEquals10ge1() {
		expressionEvaluatesTo(CTX, "10 >= 1", "true");
	}

	@Test
	public void evaluateMoreEqualsLeftError() {
		expressionErrors(CTX, "(10/0) >= 10");
	}

	@Test
	public void evaluateMoreEqualsRightError() {
		expressionErrors(CTX, "10 >= (10/0)");
	}

	@Test
	public void evaluateMore1gt1() {
		expressionEvaluatesTo(CTX, "1 > 1", "false");
	}

	@Test
	public void evaluateMore1gt10() {
		expressionEvaluatesTo(CTX, "1 > 10", "false");
	}

	@Test
	public void evaluateMore10gt1() {
		expressionEvaluatesTo(CTX, "10 > 1", "true");
	}

	@Test
	public void evaluateMoreLeftError() {
		expressionErrors(CTX, "(10/0) > 10");
	}

	@Test
	public void evaluateMoreRightError() {
		expressionErrors(CTX, "10 > (10/0)");
	}

	@Test
	public void evaluateLessEquals1le1() {
		expressionEvaluatesTo(CTX, "1 <= 1", "true");
	}

	@Test
	public void evaluateLessEquals1le10() {
		expressionEvaluatesTo(CTX, "1 <= 10", "true");
	}

	@Test
	public void evaluateLessEquals10le1() {
		expressionEvaluatesTo(CTX, "10 <= 1", "false");
	}

	@Test
	public void evaluateLessEqualsLeftError() {
		expressionErrors(CTX, "(10/0) <= 10");
	}

	@Test
	public void evaluateLessEqualsRightError() {
		expressionErrors(CTX, "10 <= (10/0)");
	}

	@Test
	public void evaluateLess1lt1() {
		expressionEvaluatesTo(CTX, "1 < 1", "false");
	}

	@Test
	public void evaluateLess1lt10() {
		expressionEvaluatesTo(CTX, "1 < 10", "true");
	}

	@Test
	public void evaluateLess10lt1() {
		expressionEvaluatesTo(CTX, "10 < 1", "false");
	}

	@Test
	public void evaluateLessLeftError() {
		expressionErrors(CTX, "(10/0) < 10");
	}

	@Test
	public void evaluateLessRightError() {
		expressionErrors(CTX, "10 < (10/0)");
	}

	@Test
	public void divEvaluationShouldFailWithNonNumberLeft() {
		expressionErrors(CTX, "null/10");
	}

	@Test
	public void divEvaluationShouldFailWithNonNumberRight() {
		expressionErrors(CTX, "10/null");
	}

	@Test
	public void divEvaluationShouldFailDivisionByZero() {
		expressionErrors(CTX, "10/0");
	}

	@Test
	public void divEvaluationSucceed() {
		expressionEvaluatesTo(CTX, "10/2", "5");
	}

	@Test
	public void evaluateDivLeftError() {
		expressionErrors(CTX, "(10/0) / 5");
	}

	@Test
	public void evaluateDivRightError() {
		expressionErrors(CTX, "10 / (10/0)");
	}

	// FIXME: why spaces needed ? "2.0-10" fails
	@Test
	public void evaluate2Minus10() {
		expressionEvaluatesTo(CTX, "2.0 - 10", "-8");
	}

	// FIXME: why spaces needed ? "10-2" fails
	@Test
	public void evaluate10Minus2() {
		expressionEvaluatesTo(CTX, "10 - 2", "8");
	}

	@Test
	public void evaluate1Minus1() {
		expressionEvaluatesTo(CTX, "1 - 1", "0");
	}

	@Test
	@Ignore
	// FIXME: needs spaces
	public void evaluate1Minus1BAD() {
		expressionEvaluatesTo(CTX, "1-1", "0");
	}

	@Test
	@Ignore
	// FIXME: needs spaces
	public void evaluateBAD() {
		expressionEvaluatesTo(CTX, "5+5-3", "7");
	}

	@Test
	public void evaluateMinusLeftError() {
		expressionErrors(CTX, "(10/0) - 5");
	}

	@Test
	public void evaluateMinusRightError() {
		expressionErrors(CTX, "10 - (10/0)");
	}

	@Test
	public void evaluate2Multi10() {
		expressionEvaluatesTo(CTX, "2*10", "20");
	}

	@Test
	public void evaluate10Multi2() {
		expressionEvaluatesTo(CTX, "10*2", "20");
	}

	@Test
	public void evaluate1Multi1() {
		expressionEvaluatesTo(CTX, "1*1", "1");
	}

	@Test
	public void evaluateMultiLeftError() {
		expressionErrors(CTX, "(10/0) * 5");
	}

	@Test
	public void evaluateMultiRightError() {
		expressionErrors(CTX, "10 * (10/0)");
	}

	@Test
	public void evaluateNotOnBooleanTrue() {
		expressionEvaluatesTo(CTX, "!true", "false");
	}

	@Test
	public void evaluateNotOnBooleanFalse() {
		expressionEvaluatesTo(CTX, "!false", "true");
	}

	@Test
	public void evaluateNotOnWrongType() {
		expressionErrors(CTX, "![1,2,3]");
	}

	@Test
	public void evaluateNotOnError() {
		expressionErrors(CTX, "!(10/0)");
	}

	@Test
	public void unaryMinus() {
		expressionEvaluatesTo(CTX, "-(1)", "-1");
	}

	@Test
	public void unaryMinusOnError() {
		expressionErrors(CTX, "-(10/0)");
	}

	@Test
	public void unaryMinusWrongType() {
		expressionErrors(CTX, "-null");
	}

	@Test
	public void evaluatePlusOnStrings() {
		expressionEvaluatesTo(CTX, "\"part a &\" + \" part b\"", "\"part a & part b\"");
	}

	@Test
	public void evaluatePlusOnLeftString() {
		expressionEvaluatesTo(CTX, "\"part a &\" + 1", "\"part a &1\"");
	}

	@Test
	public void evaluatePlusOnRightString() {
		expressionEvaluatesTo(CTX, "1 + \"part a &\"", "\"1part a &\"");
	}

	@Test
	public void evaluatePlusOnNumbers() {
		expressionEvaluatesTo(CTX, "1+2", "3");
	}

	@Test
	public void evaluatePlusLeftError() {
		expressionErrors(CTX, "(10/0) + 10");
	}

	@Test
	public void evaluatePlusRightError() {
		expressionErrors(CTX, "10 + (10/0)");
	}

	@Test
	public void evaluateElementOfOnWrongType() {
		expressionEvaluatesTo(CTX, "\"A\" in \"B\"", "false");
	}

	@Test
	public void evaluateElementOfOneElement() {
		expressionEvaluatesTo(CTX, "\"A\" in [\"A\"]", "true");
	}

	@Test
	public void evaluateElementOfTwoElementsTrue() {
		expressionEvaluatesTo(CTX, "\"A\" in [\"B\", \"A\"]", "true");
	}

	@Test
	public void evaluateElementOfTwoElementsFalse() {
		expressionEvaluatesTo(CTX, "\"C\" in [\"B\", \"A\"]", "false");
	}

	@Test
	public void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		expressionEvaluatesTo(CTX, "null in []", "false");
	}

	@Test
	public void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		expressionEvaluatesTo(CTX, "null in [null]", "true");
	}

	@Test
	public void evaluateElementOfUndefinedHaystack() {
		expressionEvaluatesTo(CTX, "\"C\" in undefined", "false");
	}

	@Test
	public void evaluateElementOfUndefinedNeedle() {
		expressionEvaluatesTo(CTX, "undefined in [1,2,3]", "false");
	}

	@Test
	public void evaluateElementOfNumbersTrue() {
		expressionEvaluatesTo(CTX, "1 in [2, 1.0]", "true");
	}

	@Test
	public void evaluateElementOfNumbersFalse() {
		expressionEvaluatesTo(CTX, "1 in [2, \"1.0\"]", "false");
	}

	@Test
	public void evaluateElementOfNumbersTrue2() throws IOException {
		expressionEvaluatesTo(CTX, "1 in [2, 1.000]", "true");
	}

	@Test
	public void evaluateElementOfLeftError() {
		expressionErrors(CTX, "(10/0) in []");
	}

	@Test
	public void evaluateElementOfRightError() {
		expressionErrors(CTX, "10 in (10/0)");
	}

	@Test
	public void evaluateRegExTrue() {
		expressionEvaluatesTo(CTX, "\"test\"=~\".*\"", "true");
	}

	@Test
	public void evaluateRegExFalse() {
		expressionEvaluatesTo(CTX, "\"test\"=~\".\"", "false");
	}

	@Test
	public void evaluateRegExPatternError() {
		expressionErrors(CTX, "\"test\"=~\"***\"");
	}

	@Test
	public void evaluateRegExLeftNull() {
		expressionEvaluatesTo(CTX, "null =~ \"\"", "false");
	}

	@Test
	public void evaluateRegExLeftUndefined() {
		expressionEvaluatesTo(CTX, "undefined =~ \"\"", "false");
	}

	@Test
	public void evaluateRegExLeftWrongType() {
		expressionEvaluatesTo(CTX, "666 =~ \"\"", "false");
	}

	@Test
	public void evaluateRegExRightWrongType() {
		expressionErrors(CTX, "\"test\" =~ null");
	}

	@Test
	public void evaluateRegExLeftError() {
		expressionErrors(CTX, "(10/0) =~ null");
	}

	@Test
	public void evaluateRegExRightError() {
		expressionErrors(CTX, "\"aaa\" =~ (10/0)");
	}
}
