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
package io.sapl.grammar.tests;

import static io.sapl.grammar.tests.BasicValueUtil.basicValueFrom;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.EagerAnd;
import io.sapl.grammar.sapl.EagerOr;
import io.sapl.grammar.sapl.ElementOf;
import io.sapl.grammar.sapl.Equals;
import io.sapl.grammar.sapl.Not;
import io.sapl.grammar.sapl.NotEquals;
import io.sapl.grammar.sapl.NullLiteral;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.Or;
import io.sapl.grammar.sapl.Plus;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.UnaryMinus;
import io.sapl.grammar.sapl.impl.MockUtil;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;
import reactor.test.StepVerifier.FirstStep;

public class EvaluateOperatorsTest {

	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50D);
	private static final BigDecimal NUMBER_ONE = BigDecimal.valueOf(1D);
	private static final BigDecimal NUMBER_TWO = BigDecimal.valueOf(2D);
	private static final BigDecimal NUMBER_TEN = BigDecimal.valueOf(10D);
	private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static final EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void evaluateAndInPolicyTarget() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateAndInPolicySetTarget() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateAndFalseFalse() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateAndTrueFalse() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateAndTrueTrue() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		and.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateAndWrongDatatypeLeft() {
		And and = FACTORY.createAnd();
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateAndLeftTrueWrongDatatypeRight() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateAndLeftFalseWrongDatatypeRight() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndFalseFalse() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndTrueFalse() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndTrueTrue() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerAndWrongDatatypeLeft() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setLeft(basicValueFrom(num));
		eagerAnd.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));
		StepVerifier.create(eagerAnd.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateOrInPolicyTarget() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		MockUtil.mockPolicyTargetExpressionContainerExpression(or);
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateOrInPolicySetTarget() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		MockUtil.mockPolicySetTargetExpressionContainerExpression(or);
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateOrFalseFalse() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateOrTrueFalse() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateOrFalseTrue() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateOrWrongDatatypeLeft() {
		Or or = FACTORY.createOr();
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setLeft(basicValueFrom(num));
		or.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftFalse() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftTrue() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrFalseFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrTrueFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrFalseTrue() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeLeft() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setLeft(basicValueFrom(num));
		eagerOr.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));
		StepVerifier.create(eagerOr.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsFalse() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsTrue() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNullLeftAndStringRightTrue() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotEqualsNullLeftAndNullRightFalse() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createNullLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsTrue() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateEqualsFalse() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNullLeftAndStringRightFalse() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateEqualsNullLeftAndNullRightTrue() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createNullLiteral()));
		StepVerifier.create(equals.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	private FirstStep<Val> verifyMoreEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
		var moreEquals = FACTORY.createMoreEquals();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		moreEquals.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		moreEquals.setRight(basicValueFrom(right));
		return StepVerifier.create(moreEquals.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluateMoreEquals1ge1() {
		verifyMoreEquals(NUMBER_ONE, NUMBER_ONE).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateMoreEquals1ge10() {
		verifyMoreEquals(NUMBER_ONE, NUMBER_TEN).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMoreEquals10ge1() {
		verifyMoreEquals(NUMBER_TEN, NUMBER_ONE).expectNext(Val.TRUE).verifyComplete();
	}

	private FirstStep<Val> verifyMore(BigDecimal leftNumber, BigDecimal rightNumber) {
		var more = FACTORY.createMore();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		more.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		more.setRight(basicValueFrom(right));
		return StepVerifier.create(more.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluateMore1gt1() {
		verifyMore(NUMBER_ONE, NUMBER_ONE).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMore1gt10() {
		verifyMore(NUMBER_ONE, NUMBER_TEN).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateMore10gt1() {
		verifyMore(NUMBER_TEN, NUMBER_ONE).expectNext(Val.TRUE).verifyComplete();
	}

	private FirstStep<Val> verifyLessEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
		var lessEquals = FACTORY.createLessEquals();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		lessEquals.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		lessEquals.setRight(basicValueFrom(right));
		return StepVerifier.create(lessEquals.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluateLessEquals1le1() {
		verifyLessEquals(NUMBER_ONE, NUMBER_ONE).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLessEquals1le10() {
		verifyLessEquals(NUMBER_ONE, NUMBER_TEN).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLessEquals10le1() {
		verifyLessEquals(NUMBER_TEN, NUMBER_ONE).expectNext(Val.FALSE).verifyComplete();
	}

	private FirstStep<Val> verifyLess(BigDecimal leftNumber, BigDecimal rightNumber) {
		var less = FACTORY.createLess();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		less.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		less.setRight(basicValueFrom(right));
		return StepVerifier.create(less.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluateLess1lt1() {
		verifyLess(NUMBER_ONE, NUMBER_ONE).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateLess1lt10() {
		verifyLess(NUMBER_ONE, NUMBER_TEN).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateLess10lt1() {
		verifyLess(NUMBER_TEN, NUMBER_ONE).expectNext(Val.FALSE).verifyComplete();
	}

	private FirstStep<Val> verifyDiv(double leftNumber, double rightNumber) {
		var div = FACTORY.createDiv();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(BigDecimal.valueOf(leftNumber));
		div.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(BigDecimal.valueOf(rightNumber));
		div.setRight(basicValueFrom(right));
		return StepVerifier.create(div.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluate1Div10() {
		verifyDiv(1.0D, 10.0D).expectNext(Val.of(0.1D)).verifyComplete();
	}

	@Test
	public void evaluate10Div2() {
		verifyDiv(10.0D, 2.0D).expectNext(Val.of(5.0D)).verifyComplete();
	}

	@Test
	public void evaluate1Div1() {
		verifyDiv(1.0D, 1.0D).expectNext(Val.of(1.0D)).verifyComplete();
	}

	@Test
	public void evaluate1Div0() {
		verifyDiv(1.0D, 0.0D).expectNextMatches(Val::isError).verifyComplete();
	}

	private FirstStep<Val> verifyMinus(double leftNumber, double rightNumber) {
		var minus = FACTORY.createMinus();
		var left = FACTORY.createNumberLiteral();
		left.setNumber(BigDecimal.valueOf(leftNumber));
		minus.setLeft(basicValueFrom(left));
		var right = FACTORY.createNumberLiteral();
		right.setNumber(BigDecimal.valueOf(rightNumber));
		minus.setRight(basicValueFrom(right));
		return StepVerifier.create(minus.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluate2Minus10() {
		verifyMinus(2.0D, 10.0D).expectNext(Val.of(-8.0D)).verifyComplete();
	}

	@Test
	public void evaluate10Minus2() {
		verifyMinus(10.0D, 2.0D).expectNext(Val.of(8.0D)).verifyComplete();
	}

	@Test
	public void evaluate1Minus1() {
		verifyMinus(1.0D, 1.0D).expectNext(Val.of(0.0D)).verifyComplete();
	}

	private FirstStep<Val> verifyMulti(double leftNumber, double rightNumber) {
		var multi = FACTORY.createMulti();
		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(BigDecimal.valueOf(leftNumber));
		multi.setLeft(basicValueFrom(left));
		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(BigDecimal.valueOf(rightNumber));
		multi.setRight(basicValueFrom(right));
		return StepVerifier.create(multi.evaluate(CTX, Val.UNDEFINED));
	}

	@Test
	public void evaluate2Multi10() {
		verifyMulti(2.0D, 10.0D).expectNext(Val.of(20.0D)).verifyComplete();
	}

	@Test
	public void evaluate10Multi2() {
		verifyMulti(10.0D, 2.0D).expectNext(Val.of(20.0D)).verifyComplete();
	}

	@Test
	public void evaluate1Multi1() {
		verifyMulti(1.0D, 1.0D).expectNext(Val.of(1.0D)).verifyComplete();
	}

	@Test
	public void evaluateNotOnBooleanTrue() {
		Not not = FACTORY.createNot();
		not.setExpression(basicValueFrom(FACTORY.createTrueLiteral()));
		StepVerifier.create(not.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateNotOnBooleanFalse() {
		Not not = FACTORY.createNot();
		not.setExpression(basicValueFrom(FACTORY.createFalseLiteral()));
		StepVerifier.create(not.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateNotOnWrongType() {
		Not not = FACTORY.createNot();
		StringLiteral literal = FACTORY.createStringLiteral();
		literal.setString("Makes no sense");
		not.setExpression(basicValueFrom(literal));
		StepVerifier.create(not.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void unaryMinus() {
		UnaryMinus unaryMinus = FACTORY.createUnaryMinus();
		NumberLiteral numberLiteral = FACTORY.createNumberLiteral();
		numberLiteral.setNumber(NUMBER_ONE);
		unaryMinus.setExpression(basicValueFrom(numberLiteral));
		StepVerifier.create(unaryMinus.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.of(-1.0D)).verifyComplete();
	}

	@Test
	public void unaryMinusWrongType() {
		UnaryMinus unaryMinus = FACTORY.createUnaryMinus();
		unaryMinus.setExpression(basicValueFrom(FACTORY.createNullLiteral()));
		StepVerifier.create(unaryMinus.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluatePlusOnStrings() {
		Plus plus = FACTORY.createPlus();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("part a &");
		StringLiteral rhs = FACTORY.createStringLiteral();
		rhs.setString(" part b");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));
		StepVerifier.create(plus.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.of("part a & part b")).verifyComplete();
	}

	@Test
	public void evaluatePlusOnLeftString() {
		Plus plus = FACTORY.createPlus();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("part a &");
		NumberLiteral rhs = FACTORY.createNumberLiteral();
		rhs.setNumber(NUMBER_ONE);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));
		StepVerifier.create(plus.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.of("part a &1")).verifyComplete();
	}

	@Test
	public void evaluatePlusOnRightString() {
		Plus plus = FACTORY.createPlus();
		NumberLiteral lhs = FACTORY.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		StringLiteral rhs = FACTORY.createStringLiteral();
		rhs.setString("part a &");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));
		StepVerifier.create(plus.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.of("1part a &")).verifyComplete();
	}

	@Test
	public void evaluatePlusOnNumbers() {
		Plus plus = FACTORY.createPlus();
		NumberLiteral lhs = FACTORY.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		NumberLiteral rhs = FACTORY.createNumberLiteral();
		rhs.setNumber(NUMBER_TWO);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));
		StepVerifier.create(plus.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.of(3)).verifyComplete();
	}

	@Test
	public void evaluateElementOfOnWrongType() {
		ElementOf elementOf = FACTORY.createElementOf();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("A");
		StringLiteral rhs = FACTORY.createStringLiteral();
		rhs.setString("B");
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateElementOfOneElement() {
		ElementOf elementOf = FACTORY.createElementOf();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("A");
		StringLiteral compare = FACTORY.createStringLiteral();
		compare.setString("A");
		Array rhs = FACTORY.createArray();
		rhs.getItems().add(basicValueFrom(compare));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateElementOfTwoElementsTrue() {
		ElementOf elementOf = FACTORY.createElementOf();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("A");
		StringLiteral compare1 = FACTORY.createStringLiteral();
		compare1.setString("A");
		StringLiteral compare2 = FACTORY.createStringLiteral();
		compare2.setString("B");
		Array rhs = FACTORY.createArray();
		rhs.getItems().add(basicValueFrom(compare1));
		rhs.getItems().add(basicValueFrom(compare2));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateElementOfTwoElementsFalse() {
		ElementOf elementOf = FACTORY.createElementOf();
		StringLiteral lhs = FACTORY.createStringLiteral();
		lhs.setString("C");
		StringLiteral compare1 = FACTORY.createStringLiteral();
		compare1.setString("A");
		StringLiteral compare2 = FACTORY.createStringLiteral();
		compare2.setString("B");
		Array rhs = FACTORY.createArray();
		rhs.getItems().add(basicValueFrom(compare1));
		rhs.getItems().add(basicValueFrom(compare2));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		ElementOf elementOf = FACTORY.createElementOf();
		elementOf.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setRight(basicValueFrom(FACTORY.createArray()));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		ElementOf elementOf = FACTORY.createElementOf();
		Array array = FACTORY.createArray();
		array.getItems().add(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setRight(basicValueFrom(array));
		StepVerifier.create(elementOf.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateRegExTrue() {
		String value = "test";
		String pattern = ".*";
		StringLiteral left = FACTORY.createStringLiteral();
		left.setString(value);
		StringLiteral right = FACTORY.createStringLiteral();
		right.setString(pattern);
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluateRegExFalse() {
		String value = "test";
		String pattern = ".";
		StringLiteral left = FACTORY.createStringLiteral();
		left.setString(value);
		StringLiteral right = FACTORY.createStringLiteral();
		right.setString(pattern);
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateRegExPatternError() {
		String value = "test";
		String pattern = "***";
		StringLiteral left = FACTORY.createStringLiteral();
		left.setString(value);
		StringLiteral right = FACTORY.createStringLiteral();
		right.setString(pattern);
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftNull() {
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		regEx.setRight(basicValueFrom(stringLiteral));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftUndefined() {
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(FACTORY.createUndefinedLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		regEx.setRight(basicValueFrom(stringLiteral));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateRegExLeftWrongType() {
		String pattern = ".*";
		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(NUMBER_ONE);
		StringLiteral right = FACTORY.createStringLiteral();
		right.setString(pattern);
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluateRegExRightWrongType() {
		String value = "test";
		StringLiteral left = FACTORY.createStringLiteral();
		left.setString(value);
		NullLiteral right = FACTORY.createNullLiteral();
		Regex regEx = FACTORY.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));
		StepVerifier.create(regEx.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

}
