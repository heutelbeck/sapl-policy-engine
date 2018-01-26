package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.BasicValue;
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
import io.sapl.grammar.sapl.NullLiteral;
import io.sapl.grammar.sapl.NumberLiteral;
import io.sapl.grammar.sapl.Or;
import io.sapl.grammar.sapl.Plus;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.StringLiteral;
import io.sapl.grammar.sapl.UnaryMinus;
import io.sapl.grammar.sapl.Value;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;

public class EvaluateOperatorsTest {
	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50);
	private static final BigDecimal NUMBER_ONE = BigDecimal.valueOf(1);
	private static final BigDecimal NUMBER_TWO = BigDecimal.valueOf(2);
	private static final BigDecimal NUMBER_TEN = BigDecimal.valueOf(10);

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateAndInTarget() throws PolicyEvaluationException {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createFalseLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		and.evaluate(ctx, false, null);
	}

	@Test
	public void evaluateAndFalseFalse() throws PolicyEvaluationException {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createFalseLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = and.evaluate(ctx, true, null);

		assertEquals("False And False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateAndTrueFalse() throws PolicyEvaluationException {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createTrueLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = and.evaluate(ctx, true, null);

		assertEquals("True And False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateAndTrueTrue() throws PolicyEvaluationException {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createTrueLiteral()));
		and.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = and.evaluate(ctx, true, null);

		assertEquals("True And True should evaluate to BooleanNode(false)", JSON.booleanNode(true), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateAndWrongDatatypeLeft() throws PolicyEvaluationException {
		And and = factory.createAnd();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		and.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateAndLeftTrueWrongDatatypeRight() throws PolicyEvaluationException {
		And and = factory.createAnd();

		and.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		and.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateAndLeftFalseWrongDatatypeRight() throws PolicyEvaluationException {
		And and = factory.createAnd();

		and.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		JsonNode result = and.evaluate(ctx, true, null);

		assertEquals("False And wrong datatype should evaluate to BooleanNode(false) (lazy evaluation)",
				JSON.booleanNode(false),
				result);
	}

	@Test
	public void evaluateEagerAndFalseFalse() throws PolicyEvaluationException {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = eagerAnd.evaluate(ctx, true, null);

		assertEquals("False EagerAnd False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateEagerAndTrueFalse() throws PolicyEvaluationException {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = eagerAnd.evaluate(ctx, true, null);

		assertEquals("True EagerAnd False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateEagerAndTrueTrue() throws PolicyEvaluationException {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = eagerAnd.evaluate(ctx, true, null);

		assertEquals("True EagerAnd True should evaluate to BooleanNode(false)", JSON.booleanNode(true), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerAndWrongDatatypeLeft() throws PolicyEvaluationException {
		And and = factory.createAnd();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		and.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() throws PolicyEvaluationException {
		EagerAnd eagerAnd = factory.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		eagerAnd.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() throws PolicyEvaluationException {
		EagerAnd eagerAnd = factory.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		eagerAnd.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateOrInTarget() throws PolicyEvaluationException {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		or.evaluate(ctx, false, null);
	}

	@Test
	public void evaluateOrFalseFalse() throws PolicyEvaluationException {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = or.evaluate(ctx, true, null);

		assertEquals("False Or False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateOrTrueFalse() throws PolicyEvaluationException {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createTrueLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = or.evaluate(ctx, true, null);

		assertEquals("True Or False should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateOrFalseTrue() throws PolicyEvaluationException {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = or.evaluate(ctx, true, null);

		assertEquals("False Or True should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateOrWrongDatatypeLeft() throws PolicyEvaluationException {
		Or or = factory.createOr();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setLeft(basicValueFrom(num));

		or.setRight(basicValueFrom(factory.createTrueLiteral()));

		or.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateOrWrongDatatypeRightLeftFalse() throws PolicyEvaluationException {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));

		or.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftTrue() throws PolicyEvaluationException {
		Or or = factory.createOr();

		or.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));

		JsonNode result = or.evaluate(ctx, true, null);
		assertEquals("True Or wrong datatype should evaluate to BooleanNode(true) (lazy evaluation)",
				JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateEagerOrFalseFalse() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = eagerOr.evaluate(ctx, true, null);

		assertEquals("False EagerOr False should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateEagerOrTrueFalse() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = eagerOr.evaluate(ctx, true, null);

		assertEquals("True EagerOr False should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateEagerOrFalseTrue() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = eagerOr.evaluate(ctx, true, null);

		assertEquals("False EagerOr True should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerOrWrongDatatypeLeft() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setLeft(basicValueFrom(num));

		eagerOr.setRight(basicValueFrom(factory.createTrueLiteral()));

		eagerOr.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		eagerOr.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() throws PolicyEvaluationException {
		EagerOr eagerOr = factory.createEagerOr();

		eagerOr.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		eagerOr.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateEqualsTrue() throws PolicyEvaluationException {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createTrueLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = equals.evaluate(ctx, true, null);

		assertEquals("True Equals True should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateEqualsFalse() throws PolicyEvaluationException {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = equals.evaluate(ctx, true, null);

		assertEquals("Null Equals True should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	private JsonNode moreEquals(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		MoreEquals moreEquals = factory.createMoreEquals();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		moreEquals.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		moreEquals.setRight(basicValueFrom(right));

		return moreEquals.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateMoreEquals1ge1() throws PolicyEvaluationException {
		JsonNode result = moreEquals(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 MoreEquals 1 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateMoreEquals1ge10() throws PolicyEvaluationException {
		JsonNode result = moreEquals(NUMBER_ONE, NUMBER_TEN);
		assertEquals("1 MoreEquals 10 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateMoreEquals10ge1() throws PolicyEvaluationException {
		JsonNode result = moreEquals(NUMBER_TEN, NUMBER_ONE);
		assertEquals("10 MoreEquals 1 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	private JsonNode more(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		More more = factory.createMore();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		more.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		more.setRight(basicValueFrom(right));

		return more.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateMore1gt1() throws PolicyEvaluationException {
		JsonNode result = more(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 More 1 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateMore1gt10() throws PolicyEvaluationException {
		JsonNode result = more(NUMBER_ONE, NUMBER_TEN);
		assertEquals("1 More 10 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateMore10gt1() throws PolicyEvaluationException {
		JsonNode result = more(NUMBER_TEN, NUMBER_ONE);
		assertEquals("10 More 1 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	private JsonNode lessEquals(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		LessEquals lessEquals = factory.createLessEquals();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		lessEquals.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		lessEquals.setRight(basicValueFrom(right));

		return lessEquals.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateLessEquals1le1() throws PolicyEvaluationException {
		JsonNode result = lessEquals(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 LessEquals 1 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateLessEquals1le10() throws PolicyEvaluationException {
		JsonNode result = lessEquals(NUMBER_ONE, NUMBER_TEN);
		assertEquals("1 LessEquals 10 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateLessEquals10le1() throws PolicyEvaluationException {
		JsonNode result = lessEquals(NUMBER_TEN, NUMBER_ONE);
		assertEquals("10 LessEquals 1 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	private JsonNode less(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		Less less = factory.createLess();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		less.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		less.setRight(basicValueFrom(right));

		return less.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateLess1lt1() throws PolicyEvaluationException {
		JsonNode result = less(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 Less 1 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateLess1lt10() throws PolicyEvaluationException {
		JsonNode result = less(NUMBER_ONE, NUMBER_TEN);
		assertEquals("1 Less 10 should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateLess10lt1() throws PolicyEvaluationException {
		JsonNode result = less(NUMBER_TEN, NUMBER_ONE);
		assertEquals("10 Less 1 should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	private JsonNode div(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		Div div = factory.createDiv();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		div.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		div.setRight(basicValueFrom(right));

		return div.evaluate(ctx, true, null);
	}

	@Test
	public void evaluate1Div10() throws PolicyEvaluationException {
		JsonNode result = div(NUMBER_ONE, NUMBER_TEN);
		assertEquals("1 Div 10 should evaluate to ValueNode(0.1)", JSON.numberNode(BigDecimal.valueOf(0.1)), result);
	}

	@Test
	public void evaluate10Div2() throws PolicyEvaluationException {
		JsonNode result = div(NUMBER_TEN, NUMBER_TWO);
		assertEquals("10 Div 2 should evaluate to ValueNode(5)", JSON.numberNode(BigDecimal.valueOf(5.0)), result);
	}

	@Test
	public void evaluate1Div1() throws PolicyEvaluationException {
		JsonNode result = div(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 Div 1 should evaluate to ValueNode(1)", JSON.numberNode(BigDecimal.valueOf(1)), result);
	}

	private JsonNode minus(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		Minus minus = factory.createMinus();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		minus.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		minus.setRight(basicValueFrom(right));

		return minus.evaluate(ctx, true, null);
	}

	@Test
	public void evaluate2Minus10() throws PolicyEvaluationException {
		JsonNode result = minus(NUMBER_TWO, NUMBER_TEN);
		assertEquals("2 Minus 10 should evaluate to ValueNode(-8)", JSON.numberNode(BigDecimal.valueOf(-8.0)), result);
	}

	@Test
	public void evaluate10Minus2() throws PolicyEvaluationException {
		JsonNode result = minus(NUMBER_TEN, NUMBER_TWO);
		assertEquals("10 Minus 2 should evaluate to ValueNode(8)", JSON.numberNode(BigDecimal.valueOf(8.0)), result);
	}

	@Test
	public void evaluate1Minus1() throws PolicyEvaluationException {
		JsonNode result = minus(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 Minus 1 should evaluate to ValueNode(0)", JSON.numberNode(BigDecimal.valueOf(0.0)), result);
	}

	private JsonNode multi(BigDecimal leftNumber, BigDecimal rightNumber) throws PolicyEvaluationException {
		Multi multi = factory.createMulti();

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(leftNumber);
		multi.setLeft(basicValueFrom(left));

		NumberLiteral right = factory.createNumberLiteral();
		right.setNumber(rightNumber);
		multi.setRight(basicValueFrom(right));

		return multi.evaluate(ctx, true, null);
	}

	@Test
	public void evaluate2Multi10() throws PolicyEvaluationException {
		JsonNode result = multi(NUMBER_TWO, NUMBER_TEN);
		assertEquals("2 Multi 10 should evaluate to ValueNode(20)", JSON.numberNode(BigDecimal.valueOf(20.0)), result);
	}

	@Test
	public void evaluate10Multi2() throws PolicyEvaluationException {
		JsonNode result = multi(NUMBER_TEN, NUMBER_TWO);
		assertEquals("10 Multi 2 should evaluate to ValueNode(20)", JSON.numberNode(BigDecimal.valueOf(20.0)), result);
	}

	@Test
	public void evaluate1Multi1() throws PolicyEvaluationException {
		JsonNode result = multi(NUMBER_ONE, NUMBER_ONE);
		assertEquals("1 Multi 1 should evaluate to ValueNode(1)", JSON.numberNode(BigDecimal.valueOf(1.0)), result);
	}

	@Test
	public void evaluateNotOnBooleanTrue() throws PolicyEvaluationException {
		Not not = factory.createNot();
		not.setExpression(basicValueFrom(factory.createTrueLiteral()));

		JsonNode result = not.evaluate(ctx, true, null);
		assertEquals("Not True should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateNotOnBooleanFalse() throws PolicyEvaluationException {
		Not not = factory.createNot();
		not.setExpression(basicValueFrom(factory.createFalseLiteral()));

		JsonNode result = not.evaluate(ctx, true, null);
		assertEquals("Not False should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateNotOnWrongType() throws PolicyEvaluationException {
		Not not = factory.createNot();
		StringLiteral literal = factory.createStringLiteral();
		literal.setString("Makes no sense");
		not.setExpression(basicValueFrom(literal));

		not.evaluate(ctx, true, null);
	}

	@Test
	public void unaryMinus() throws PolicyEvaluationException {
		UnaryMinus unaryMinus = factory.createUnaryMinus();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(NUMBER_ONE);
		unaryMinus.setExpression(basicValueFrom(numberLiteral));

		JsonNode result = unaryMinus.evaluate(ctx, true, null);
		assertEquals("UnaryMinus 1 should evaluate to NumberNode(-1)", JSON.numberNode(BigDecimal.valueOf(-1L)),
				result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void unaryMinusWrongType() throws PolicyEvaluationException {
		UnaryMinus unaryMinus = factory.createUnaryMinus();
		unaryMinus.setExpression(basicValueFrom(factory.createNullLiteral()));

		unaryMinus.evaluate(ctx, true, null);
	}

	@Test
	public void evaluatePlusOnStrings() throws PolicyEvaluationException {
		Plus plus = factory.createPlus();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("part a &");
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString(" part b");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		JsonNode result = plus.evaluate(ctx, true, null);
		assertEquals("Plus on Strings should evaluate to TextNode with concatenated strings",
				JSON.textNode("part a & part b"), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluatePlusOnLeftString() throws PolicyEvaluationException {
		Plus plus = factory.createPlus();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("part a &");
		NumberLiteral rhs = factory.createNumberLiteral();
		rhs.setNumber(NUMBER_ONE);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		plus.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluatePlusOnRightString() throws PolicyEvaluationException {
		Plus plus = factory.createPlus();
		NumberLiteral lhs = factory.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString("part a &");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		plus.evaluate(ctx, true, null);
	}

	@Test
	public void evaluatePlusOnNumbers() throws PolicyEvaluationException {
		Plus plus = factory.createPlus();
		NumberLiteral lhs = factory.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		NumberLiteral rhs = factory.createNumberLiteral();
		rhs.setNumber(NUMBER_TWO);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		JsonNode result = plus.evaluate(ctx, true, null);
		assertEquals("1 Plus 2 should evaluate to ValueNode(3)", JSON.numberNode(BigDecimal.valueOf(3.0)), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateElementOfOnWrongType() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("A");
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString("B");
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		elementOf.evaluate(ctx, true, null);
	}

	@Test
	public void evaluateElementOfOneElement() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("A");
		StringLiteral compare = factory.createStringLiteral();
		compare.setString("A");
		Array rhs = factory.createArray();
		rhs.getItems().add(basicValueFrom(compare));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		JsonNode result = elementOf.evaluate(ctx, true, null);
		assertEquals("\"A\" ElementOf Array[\"A\"] should evaluate to BooleanNode(true)", JSON.booleanNode(true),
				result);
	}

	@Test
	public void evaluateElementOfTwoElementsTrue() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("A");
		StringLiteral compare1 = factory.createStringLiteral();
		compare1.setString("A");
		StringLiteral compare2 = factory.createStringLiteral();
		compare2.setString("B");
		Array rhs = factory.createArray();
		rhs.getItems().add(basicValueFrom(compare1));
		rhs.getItems().add(basicValueFrom(compare2));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		JsonNode result = elementOf.evaluate(ctx, true, null);
		assertEquals("\"A\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(true)", JSON.booleanNode(true),
				result);
	}

	@Test
	public void evaluateElementOfTwoElementsFalse() throws PolicyEvaluationException {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("C");
		StringLiteral compare1 = factory.createStringLiteral();
		compare1.setString("A");
		StringLiteral compare2 = factory.createStringLiteral();
		compare2.setString("B");
		Array rhs = factory.createArray();
		rhs.getItems().add(basicValueFrom(compare1));
		rhs.getItems().add(basicValueFrom(compare2));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		JsonNode result = elementOf.evaluate(ctx, true, null);
		assertEquals("\"C\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(false)",
				JSON.booleanNode(false), result);
	}

	@Test
	public void evaluateRegExTrue() throws PolicyEvaluationException {
		String value = "test";
		String pattern = ".*";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		JsonNode result = regEx.evaluate(ctx, true, null);
		assertEquals("\"test\" RegEx \".*\" should evaluate to BooleanNode(true)", JSON.booleanNode(true), result);
	}

	@Test
	public void evaluateRegExFalse() throws PolicyEvaluationException {
		String value = "test";
		String pattern = ".";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		JsonNode result = regEx.evaluate(ctx, true, null);
		assertEquals("\"test\" RegEx \".\" should evaluate to BooleanNode(false)", JSON.booleanNode(false), result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateRegExPatternError() throws PolicyEvaluationException {
		String value = "test";
		String pattern = "***";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		regEx.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateRegExLeftWrongType() throws PolicyEvaluationException {
		String pattern = ".*";

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(NUMBER_ONE);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		regEx.evaluate(ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void evaluateRegExRightWrongType() throws PolicyEvaluationException {
		String value = "test";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		NullLiteral right = factory.createNullLiteral();

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		regEx.evaluate(ctx, true, null);
	}

	private static BasicValue basicValueFrom(Value value) {
		BasicValue basicValue = factory.createBasicValue();
		basicValue.setValue(value);
		return basicValue;
	}
}
