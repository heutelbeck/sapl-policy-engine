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

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.Div;
import io.sapl.grammar.sapl.EagerAnd;
import io.sapl.grammar.sapl.EagerOr;
import io.sapl.grammar.sapl.ElementOf;
import io.sapl.grammar.sapl.Equals;
import io.sapl.grammar.sapl.Less;
import io.sapl.grammar.sapl.LessEquals;
import io.sapl.grammar.sapl.Minus;
import io.sapl.grammar.sapl.More;
import io.sapl.grammar.sapl.MoreEquals;
import io.sapl.grammar.sapl.Multi;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EvaluateOperatorsTest {

	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50D);
	private static final BigDecimal NUMBER_ONE = BigDecimal.valueOf(1D);
	private static final BigDecimal NUMBER_TWO = BigDecimal.valueOf(2D);
	private static final BigDecimal NUMBER_TEN = BigDecimal.valueOf(10D);
	private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final EvaluationContext ctx = new EvaluationContext();

	@Test
	public void evaluateAndInTarget() {
		And and = FACTORY.createAnd();
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, false, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateAndFalseFalse() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicyBodyForExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		and.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("False And False should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateAndTrueFalse() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicyBodyForExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		and.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("True And False should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateAndTrueTrue() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicyBodyForExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		and.setRight(basicValueFrom(FACTORY.createTrueLiteral()));
		and.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("True And True should evaluate to BooleanNode(false)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateAndWrongDatatypeLeft() {
		And and = FACTORY.createAnd();

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateAndLeftTrueWrongDatatypeRight() {
		And and = FACTORY.createAnd();

		and.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		StepVerifier.create(and.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateAndLeftFalseWrongDatatypeRight() {
		And and = FACTORY.createAnd();
		MockUtil.mockPolicyBodyForExpression(and);
		and.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		and.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"False And wrong datatype should evaluate to BooleanNode(false) (lazy evaluation)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEagerAndFalseFalse() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		eagerAnd.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("False EagerAnd False should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEagerAndTrueFalse() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		eagerAnd.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("True EagerAnd False should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEagerAndTrueTrue() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		eagerAnd.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("True EagerAnd True should evaluate to BooleanNode(false)",
						Val.ofTrue(), result));
	}

	@Test
	public void evaluateEagerAndWrongDatatypeLeft() {
		And and = FACTORY.createAnd();

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		StepVerifier.create(eagerAnd.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		EagerAnd eagerAnd = FACTORY.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		StepVerifier.create(eagerAnd.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateOrInTarget() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		StepVerifier.create(or.evaluate(ctx, false, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateOrFalseFalse() {
		Or or = FACTORY.createOr();
		MockUtil.mockPolicyBodyForExpression(or);
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		or.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("False Or False should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateOrTrueFalse() {
		Or or = FACTORY.createOr();
		MockUtil.mockPolicyBodyForExpression(or);
		or.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		or.setRight(basicValueFrom(FACTORY.createFalseLiteral()));
		or.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("True Or False should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateOrFalseTrue() {
		Or or = FACTORY.createOr();
		MockUtil.mockPolicyBodyForExpression(or);
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		or.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		or.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("False Or True should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateOrWrongDatatypeLeft() {
		Or or = FACTORY.createOr();
		MockUtil.mockPolicyBodyForExpression(or);
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setLeft(basicValueFrom(num));

		or.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		StepVerifier.create(or.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftFalse() {
		Or or = FACTORY.createOr();
		or.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));

		StepVerifier.create(or.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftTrue() {
		Or or = FACTORY.createOr();
		MockUtil.mockPolicyBodyForExpression(or);
		or.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));
		or.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"True Or wrong datatype should evaluate to BooleanNode(true) (lazy evaluation)", Val.ofTrue(),
						result));
	}

	@Test
	public void evaluateEagerOrFalseFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		eagerOr.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("False EagerOr False should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEagerOrTrueFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createFalseLiteral()));

		eagerOr.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("True EagerOr False should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
	}

	@Test
	public void evaluateEagerOrFalseTrue() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		eagerOr.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("False EagerOr True should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
	}

	@Test
	public void evaluateEagerOrWrongDatatypeLeft() {
		EagerOr eagerOr = FACTORY.createEagerOr();

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setLeft(basicValueFrom(num));

		eagerOr.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		StepVerifier.create(eagerOr.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		EagerOr eagerOr = FACTORY.createEagerOr();
		eagerOr.setLeft(basicValueFrom(FACTORY.createFalseLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		StepVerifier.create(eagerOr.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		EagerOr eagerOr = FACTORY.createEagerOr();

		eagerOr.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));

		NumberLiteral num = FACTORY.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		StepVerifier.create(eagerOr.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateNotEqualsFalse() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("True NotEquals True should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateNotEqualsTrue() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Null NotEquals True should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
	}

	@Test
	public void evaluateNotEqualsNullLeftAndStringRightTrue() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Null NotEquals String should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
	}

	@Test
	public void evaluateNotEqualsNullLeftAndNullRightFalse() {
		NotEquals equals = FACTORY.createNotEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createNullLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Null NotEquals Null should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEqualsTrue() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createTrueLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("True Equals True should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateEqualsFalse() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createTrueLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Null Equals True should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEqualsNullLeftAndStringRightFalse() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));

		equals.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Null Equals String should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
	}

	@Test
	public void evaluateEqualsNullLeftAndNullRightTrue() {
		Equals equals = FACTORY.createEquals();
		equals.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		equals.setRight(basicValueFrom(FACTORY.createNullLiteral()));

		equals.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Null Equals Null should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	private Flux<Val> moreEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
		MoreEquals moreEquals = FACTORY.createMoreEquals();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		moreEquals.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		moreEquals.setRight(basicValueFrom(right));

		return moreEquals.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluateMoreEquals1ge1() {
		moreEquals(NUMBER_ONE, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("1 MoreEquals 1 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateMoreEquals1ge10() {
		moreEquals(NUMBER_ONE, NUMBER_TEN).take(1).subscribe(
				result -> assertEquals("1 MoreEquals 10 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateMoreEquals10ge1() {
		moreEquals(NUMBER_TEN, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("10 MoreEquals 1 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	private Flux<Val> more(BigDecimal leftNumber, BigDecimal rightNumber) {
		More more = FACTORY.createMore();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		more.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		more.setRight(basicValueFrom(right));

		return more.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluateMore1gt1() {
		more(NUMBER_ONE, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("1 More 1 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateMore1gt10() {
		more(NUMBER_ONE, NUMBER_TEN).take(1).subscribe(
				result -> assertEquals("1 More 10 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateMore10gt1() {
		more(NUMBER_TEN, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("10 More 1 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	private Flux<Val> lessEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
		LessEquals lessEquals = FACTORY.createLessEquals();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		lessEquals.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		lessEquals.setRight(basicValueFrom(right));

		return lessEquals.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluateLessEquals1le1() {
		lessEquals(NUMBER_ONE, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("1 LessEquals 1 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateLessEquals1le10() {
		lessEquals(NUMBER_ONE, NUMBER_TEN).take(1).subscribe(
				result -> assertEquals("1 LessEquals 10 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateLessEquals10le1() {
		lessEquals(NUMBER_TEN, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("10 LessEquals 1 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	private Flux<Val> less(BigDecimal leftNumber, BigDecimal rightNumber) {
		Less less = FACTORY.createLess();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		less.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		less.setRight(basicValueFrom(right));

		return less.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluateLess1lt1() {
		less(NUMBER_ONE, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("1 Less 1 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateLess1lt10() {
		less(NUMBER_ONE, NUMBER_TEN).take(1).subscribe(
				result -> assertEquals("1 Less 10 should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateLess10lt1() {
		less(NUMBER_TEN, NUMBER_ONE).take(1).subscribe(
				result -> assertEquals("10 Less 1 should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	private Flux<Val> div(BigDecimal leftNumber, BigDecimal rightNumber) {
		Div div = FACTORY.createDiv();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		div.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		div.setRight(basicValueFrom(right));

		return div.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluate1Div10() {
		div(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 Div 10 should evaluate to ValueNode(0.1)", Val.of(0.1D), result));
	}

	@Test
	public void evaluate10Div2() {
		div(NUMBER_TEN, NUMBER_TWO).take(1)
				.subscribe(result -> assertEquals("10 Div 2 should evaluate to ValueNode(5)", Val.of(5), result));
	}

	@Test
	public void evaluate1Div1() {
		div(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Div 1 should evaluate to ValueNode(1)", Val.of(1D), result));
	}

	private Flux<Val> minus(BigDecimal leftNumber, BigDecimal rightNumber) {
		Minus minus = FACTORY.createMinus();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		minus.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		minus.setRight(basicValueFrom(right));

		return minus.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluate2Minus10() {
		minus(NUMBER_TWO, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("2 Minus 10 should evaluate to ValueNode(-8)", Val.of(-8), result));
	}

	@Test
	public void evaluate10Minus2() {
		minus(NUMBER_TEN, NUMBER_TWO).take(1)
				.subscribe(result -> assertEquals("10 Minus 2 should evaluate to ValueNode(8)", Val.of(8), result));
	}

	@Test
	public void evaluate1Minus1() {
		minus(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Minus 1 should evaluate to ValueNode(0)", Val.of(0), result));
	}

	private Flux<Val> multi(BigDecimal leftNumber, BigDecimal rightNumber) {
		Multi multi = FACTORY.createMulti();

		NumberLiteral left = FACTORY.createNumberLiteral();
		left.setNumber(leftNumber);
		multi.setLeft(basicValueFrom(left));

		NumberLiteral right = FACTORY.createNumberLiteral();
		right.setNumber(rightNumber);
		multi.setRight(basicValueFrom(right));

		return multi.evaluate(ctx, true, Val.undefined());
	}

	@Test
	public void evaluate2Multi10() {
		multi(NUMBER_TWO, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("2 Multi 10 should evaluate to ValueNode(20)", Val.of(20), result));
	}

	@Test
	public void evaluate10Multi2() {
		multi(NUMBER_TEN, NUMBER_TWO).take(1)
				.subscribe(result -> assertEquals("10 Multi 2 should evaluate to ValueNode(20)", Val.of(20), result));
	}

	@Test
	public void evaluate1Multi1() {
		multi(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Multi 1 should evaluate to ValueNode(1)", Val.of(1), result));
	}

	@Test
	public void evaluateNotOnBooleanTrue() {
		Not not = FACTORY.createNot();
		not.setExpression(basicValueFrom(FACTORY.createTrueLiteral()));

		not.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Not True should evaluate to BooleanNode(false)", Val.ofFalse(), result));
	}

	@Test
	public void evaluateNotOnBooleanFalse() {
		Not not = FACTORY.createNot();
		not.setExpression(basicValueFrom(FACTORY.createFalseLiteral()));

		not.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Not False should evaluate to BooleanNode(true)", Val.ofTrue(), result));
	}

	@Test
	public void evaluateNotOnWrongType() {
		Not not = FACTORY.createNot();
		StringLiteral literal = FACTORY.createStringLiteral();
		literal.setString("Makes no sense");
		not.setExpression(basicValueFrom(literal));

		StepVerifier.create(not.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void unaryMinus() {
		UnaryMinus unaryMinus = FACTORY.createUnaryMinus();
		NumberLiteral numberLiteral = FACTORY.createNumberLiteral();
		numberLiteral.setNumber(NUMBER_ONE);
		unaryMinus.setExpression(basicValueFrom(numberLiteral));

		unaryMinus.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("UnaryMinus 1 should evaluate to NumberNode(-1)", Val.of(-1L), result));
	}

	@Test
	public void unaryMinusWrongType() {
		UnaryMinus unaryMinus = FACTORY.createUnaryMinus();
		unaryMinus.setExpression(basicValueFrom(FACTORY.createNullLiteral()));

		StepVerifier.create(unaryMinus.evaluate(ctx, true, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
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

		plus.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Plus on Strings should evaluate to TextNode with concatenated strings",
						Val.of(JSON.textNode("part a & part b")), result));
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

		plus.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"Plus on Strings + number should evaluate to TextNode with concatenated strings",
						Val.of(JSON.textNode("part a &1")), result));
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

		plus.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"Plus on Strings + number should evaluate to TextNode with concatenated strings",
						Val.of("1part a &"), result));
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

		plus.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("1 Plus 2 should evaluate to ValueNode(3)", Val.of(3), result));
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

		elementOf.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("\"A\" ElementOf Array\"B\" should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
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

		elementOf.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("\"A\" ElementOf Array[\"A\"] should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
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

		elementOf.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"\"A\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(true)", Val.ofTrue(),
						result));
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

		elementOf.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"\"C\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(false)", Val.ofFalse(),
						result));
	}

	@Test
	public void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		ElementOf elementOf = FACTORY.createElementOf();
		elementOf.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setRight(basicValueFrom(FACTORY.createArray()));

		elementOf.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertFalse("Null ElementOf Array[] should evaluate to false", result.get().asBoolean()));
	}

	@Test
	public void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		ElementOf elementOf = FACTORY.createElementOf();
		Array array = FACTORY.createArray();
		array.getItems().add(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		elementOf.setRight(basicValueFrom(array));

		elementOf.evaluate(ctx, true, Val.undefined()).take(1).subscribe(
				result -> assertTrue("Null ElementOf Array[null] should evaluate to true", result.get().asBoolean()));
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

		regEx.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("\"test\" RegEx \".*\" should evaluate to BooleanNode(true)",
						Val.ofTrue(), result));
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

		regEx.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("\"test\" RegEx \".\" should evaluate to BooleanNode(false)",
						Val.ofFalse(), result));
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

		StepVerifier.create(regEx.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void evaluateRegExLeftNull() {
		Regex regex = FACTORY.createRegex();
		regex.setLeft(basicValueFrom(FACTORY.createNullLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		regex.setRight(basicValueFrom(stringLiteral));

		regex.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertFalse("NullLeft should evaluate to false", result.get().asBoolean()));
	}

	@Test
	public void evaluateRegExLeftUndefined() {
		Regex regex = FACTORY.createRegex();
		regex.setLeft(basicValueFrom(FACTORY.createUndefinedLiteral()));
		StringLiteral stringLiteral = FACTORY.createStringLiteral();
		stringLiteral.setString("");
		regex.setRight(basicValueFrom(stringLiteral));

		regex.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertFalse("Undefined left should evaluate to false", result.get().asBoolean()));
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

		regEx.evaluate(ctx, true, Val.undefined()).take(1)
				.subscribe(result -> assertFalse("Null number should evaluate to false", result.get().asBoolean()));
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

		StepVerifier.create(regEx.evaluate(ctx, true, Val.undefined())).expectError(PolicyEvaluationException.class)
				.verify();
	}

}
