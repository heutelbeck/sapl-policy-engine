/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class EagerOperatorsTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void evaluateEagerAndFalseFalse() throws IOException {
		var expression = ParserUtil.expression("false & false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndTrueFalse() throws IOException {
		var expression = ParserUtil.expression("true & false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndFalseTrue() throws IOException {
		var expression = ParserUtil.expression("false & true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndTrueTrue() throws IOException {
		var expression = ParserUtil.expression("true & true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndWrongDatatypeLeft() throws IOException {
		var expression = ParserUtil.expression("5 & true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() throws IOException {
		var expression = ParserUtil.expression("true & 5");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() throws IOException {
		var expression = ParserUtil.expression("false & 5");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) & true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndRightError() throws IOException {
		var expression = ParserUtil.expression("true & (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrFalseFalse() throws IOException {
		var expression = ParserUtil.expression("false | false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrTrueFalse() throws IOException {
		var expression = ParserUtil.expression("true | false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrFalseTrue() throws IOException {
		var expression = ParserUtil.expression("false | true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeLeft() throws IOException {
		var expression = ParserUtil.expression("5 | true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() throws IOException {
		var expression = ParserUtil.expression("false | 7");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() throws IOException {
		var expression = ParserUtil.expression("true | 7");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) | true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrRightError() throws IOException {
		var expression = ParserUtil.expression("true | (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsFalse() throws IOException {
		var expression = ParserUtil.expression("true != true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsTrue() throws IOException {
		var expression = ParserUtil.expression("null != true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNullLeftAndStringRightTrue() throws IOException {
		var expression = ParserUtil.expression("null != \"x\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNullLeftAndNullRightFalse() throws IOException {
		var expression = ParserUtil.expression("null != null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNumericalFalse() throws IOException {
		var expression = ParserUtil.expression("17 != 17.0");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNumericalTrue() throws IOException {
		var expression = ParserUtil.expression("17 != (17+2)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) != true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsRightError() throws IOException {
		var expression = ParserUtil.expression("true != (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsOnlyRightNumberFalse() throws IOException {
		var expression = ParserUtil.expression("\"x\" != 12");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsOnlyLeftNumberFalse() throws IOException {
		var expression = ParserUtil.expression("12 != \"x\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsArraysFalse() throws IOException {
		var expression = ParserUtil.expression("[1,2,3] != [1,2,3])");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsArraysTrue() throws IOException {
		var expression = ParserUtil.expression("[1,3] != [1,2,3])");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsObjectsFalse() throws IOException {
		var expression = ParserUtil.expression("{ \"key\" : true } != { \"key\" : true })");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsObjectsTrue() throws IOException {
		var expression = ParserUtil.expression("{ \"key\" : true } != { \"key\" : false })");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsBothUndefinedFalse() throws IOException {
		var expression = ParserUtil.expression("undefined != undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsLeftUndefinedTrue() throws IOException {
		var expression = ParserUtil.expression("undefined != 0");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsRightUndefinedTrue() throws IOException {
		var expression = ParserUtil.expression("0 != undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsTrue() throws IOException {
		var expression = ParserUtil.expression("true == true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsFalse() throws IOException {
		var expression = ParserUtil.expression("null == true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNullLeftAndStringRightFalse() throws IOException {
		var expression = ParserUtil.expression("null == \"a\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNullLeftAndNullRightTrue() throws IOException {
		var expression = ParserUtil.expression("null == null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsBothUndefinedTrue() throws IOException {
		var expression = ParserUtil.expression("undefined == undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsLeftUndefinedFalse() throws IOException {
		var expression = ParserUtil.expression("undefined == 0");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsRightUndefinedFalse() throws IOException {
		var expression = ParserUtil.expression("0 == undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNumbersTrue() throws IOException {
		var expression = ParserUtil.expression("10.0 == (12 - 2)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNumbersFalse() throws IOException {
		var expression = ParserUtil.expression("10.0 == 12");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsOnlyRightNumberFalse() throws IOException {
		var expression = ParserUtil.expression("\"x\" == 12");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsOnlyLeftNumberFalse() throws IOException {
		var expression = ParserUtil.expression("12 == \"x\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsArraysTrue() throws IOException {
		var expression = ParserUtil.expression("[1,2,3] == [1,2,3])");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsArraysFalse() throws IOException {
		var expression = ParserUtil.expression("[1,3] == [1,2,3])");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsObjectsTrue() throws IOException {
		var expression = ParserUtil.expression("{ \"key\" : true } == { \"key\" : true })");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsObjectsFalse() throws IOException {
		var expression = ParserUtil.expression("{ \"key\" : true } == { \"key\" : false })");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) == true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEqualsRightError() throws IOException {
		var expression = ParserUtil.expression("true == (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMoreEquals1ge1() throws IOException {
		var expression = ParserUtil.expression("1 >= 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateMoreEquals1ge10() throws IOException {
		var expression = ParserUtil.expression("1 >= 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMoreEquals10ge1() throws IOException {
		var expression = ParserUtil.expression("10 >= 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateMoreEqualsLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) >= 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMoreEqualsRightError() throws IOException {
		var expression = ParserUtil.expression("10 >= (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMore1gt1() throws IOException {
		var expression = ParserUtil.expression("1 > 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMore1gt10() throws IOException {
		var expression = ParserUtil.expression("1 > 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMore10gt1() throws IOException {
		var expression = ParserUtil.expression("10 > 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateMoreLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) > 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMoreRightError() throws IOException {
		var expression = ParserUtil.expression("10 > (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateLessEquals1le1() throws IOException {
		var expression = ParserUtil.expression("1 <= 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLessEquals1le10() throws IOException {
		var expression = ParserUtil.expression("1 <= 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLessEquals10le1() throws IOException {
		var expression = ParserUtil.expression("10 <= 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateLessEqualsLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) <= 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateLessEqualsRightError() throws IOException {
		var expression = ParserUtil.expression("10 <= (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateLess1lt1() throws IOException {
		var expression = ParserUtil.expression("1 < 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateLess1lt10() throws IOException {
		var expression = ParserUtil.expression("1 < 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLess10lt1() throws IOException {
		var expression = ParserUtil.expression("10 < 1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateLessLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) < 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateLessRightError() throws IOException {
		var expression = ParserUtil.expression("10 < (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void divEvaluationShouldFailWithNonNumberLeft() throws IOException {
		var expression = ParserUtil.expression("null/10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void divEvaluationShouldFailWithNonNumberRight() throws IOException {
		var expression = ParserUtil.expression("10/null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void divEvaluationShouldFailDivisionByZero() throws IOException {
		var expression = ParserUtil.expression("10/0");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void divEvaluationSucceed() throws IOException {
		var expression = ParserUtil.expression("10/2");
		var expected = Val.of(5);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateDivLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) / 5");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateDivRightError() throws IOException {
		var expression = ParserUtil.expression("10 / (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	// FIXME.. why spaces needed ? "2.0-10" fails
	@Test
	public void evaluate2Minus10() throws IOException {
		var expression = ParserUtil.expression("2.0 - 10");
		var expected = Val.of(-8);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	// FIXME.. why spaces needed ? "10-2" fails
	@Test
	public void evaluate10Minus2() throws IOException {
		var expression = ParserUtil.expression("10 - 2");
		var expected = Val.of(8);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluate1Minus1() throws IOException {
		var expression = ParserUtil.expression("1 - 1");
		var expected = Val.of(0);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Ignore
	@Test
	// FIXME
	public void evaluate1Minus1BAD() throws IOException {
		var expression = ParserUtil.expression("1-1");
		var expected = Val.of(0);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Ignore
	@Test
	// FIXME
	public void evaluateBAD() throws IOException {
		var expression = ParserUtil.expression("5+5-3");
		var expected = Val.of(7);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateMinusLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) - 5");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMinusRightError() throws IOException {
		var expression = ParserUtil.expression("10 - (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluate2Multi10() throws IOException {
		var expression = ParserUtil.expression("2*10");
		var expected = Val.of(20);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluate10Multi2() throws IOException {
		var expression = ParserUtil.expression("10*2");
		var expected = Val.of(20);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluate1Multi1() throws IOException {
		var expression = ParserUtil.expression("1*1");
		var expected = Val.of(1);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateMultiLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) * 5");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateMultiRightError() throws IOException {
		var expression = ParserUtil.expression("10 * (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotOnBooleanTrue() throws IOException {
		var expression = ParserUtil.expression("!true");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateNotOnBooleanFalse() throws IOException {
		var expression = ParserUtil.expression("!false");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateNotOnWrongType() throws IOException {
		var expression = ParserUtil.expression("![1,2,3]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotOnError() throws IOException {
		var expression = ParserUtil.expression("!(10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void unaryMinus() throws IOException {
		var expression = ParserUtil.expression("-(1)");
		var expected = Val.of(-1);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void unaryMinusOnError() throws IOException {
		var expression = ParserUtil.expression("-(10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void unaryMinusWrongType() throws IOException {
		var expression = ParserUtil.expression("-null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluatePlusOnStrings() throws IOException {
		var expression = ParserUtil.expression("\"part a &\" + \" part b\"");
		var expected = Val.of("part a & part b");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluatePlusOnLeftString() throws IOException {
		var expression = ParserUtil.expression("\"part a &\" + 1");
		var expected = Val.of("part a &1");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluatePlusOnRightString() throws IOException {
		var expression = ParserUtil.expression("1 + \"part a &\"");
		var expected = Val.of("1part a &");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluatePlusOnNumbers() throws IOException {
		var expression = ParserUtil.expression("1+2");
		var expected = Val.of(3);
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluatePlusLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) + 10");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluatePlusRightError() throws IOException {
		var expression = ParserUtil.expression("10 + (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateElementOfOnWrongType() throws IOException {
		var expression = ParserUtil.expression("\"A\" in \"B\"");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfOneElement() throws IOException {
		var expression = ParserUtil.expression("\"A\" in [\"A\"]");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfTwoElementsTrue() throws IOException {
		var expression = ParserUtil.expression("\"A\" in [\"B\", \"A\"]");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfTwoElementsFalse() throws IOException {
		var expression = ParserUtil.expression("\"C\" in [\"B\", \"A\"]");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfNullLeftAndEmptyArrayFalse() throws IOException {
		var expression = ParserUtil.expression("null in []");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfNullLeftAndArrayWithNullElementTrue() throws IOException {
		var expression = ParserUtil.expression("null in [null]");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfUndefinedHaystack() throws IOException {
		var expression = ParserUtil.expression("\"C\" in undefined");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}
	@Test
	public void evaluateElementOfUndefinedNeedle() throws IOException {
		var expression = ParserUtil.expression("undefined in [1,2,3]");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}
	@Test
	public void evaluateElementOfNumbersTrue() throws IOException {
		var expression = ParserUtil.expression("1 in [2, 1.0]");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfNumbersFalse() throws IOException {
		var expression = ParserUtil.expression("1 in [2, \"1.0\"]");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}
	
	@Test
	public void evaluateElementOfNumbersTrue2() throws IOException {
		var expression = ParserUtil.expression("1 in [2, 001.000]");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateElementOfLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) in []");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateElementOfRightError() throws IOException {
		var expression = ParserUtil.expression("10 in (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateRegExTrue() throws IOException {
		var expression = ParserUtil.expression("\"test\"=~\".*\"");
		var expected = Val.TRUE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateRegExFalse() throws IOException {
		var expression = ParserUtil.expression("\"test\"=~\".\"");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateRegExPatternError() throws IOException {
		var expression = ParserUtil.expression("\"test\"=~\"***\"");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftNull() throws IOException {
		var expression = ParserUtil.expression("null =~ \"\"");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftUndefined() throws IOException {
		var expression = ParserUtil.expression("undefined =~ \"\"");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftWrongType() throws IOException {
		var expression = ParserUtil.expression("666 =~ \"\"");
		var expected = Val.FALSE;
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void evaluateRegExRightWrongType() throws IOException {
		var expression = ParserUtil.expression("\"test\" =~ null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftError() throws IOException {
		var expression = ParserUtil.expression("(10/0) =~ null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateRegExRightError() throws IOException {
		var expression = ParserUtil.expression("\"aaa\" =~ (10/0)");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}
}
