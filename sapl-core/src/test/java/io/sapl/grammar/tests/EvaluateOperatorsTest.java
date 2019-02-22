package io.sapl.grammar.tests;

import static io.sapl.grammar.tests.BasicValueHelper.basicValueFrom;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
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
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EvaluateOperatorsTest {
	private static final BigDecimal TEST_NUMBER = BigDecimal.valueOf(100.50);
	private static final BigDecimal NUMBER_ONE = BigDecimal.valueOf(1);
	private static final BigDecimal NUMBER_TWO = BigDecimal.valueOf(2);
	private static final BigDecimal NUMBER_TEN = BigDecimal.valueOf(10);

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static EvaluationContext ctx = new EvaluationContext(null, null, null, new HashMap<>());

	@Test
	public void evaluateAndInTarget() {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createFalseLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, false, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateAndFalseFalse() {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createFalseLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		and.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False And False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateAndTrueFalse() {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createTrueLiteral()));
		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		and.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True And False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateAndTrueTrue() {
		And and = factory.createAnd();
		and.setLeft(basicValueFrom(factory.createTrueLiteral()));
		and.setRight(basicValueFrom(factory.createTrueLiteral()));

		and.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True And True should evaluate to BooleanNode(false)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateAndWrongDatatypeLeft() {
		And and = factory.createAnd();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateAndLeftTrueWrongDatatypeRight() {
		And and = factory.createAnd();

		and.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		StepVerifier.create(and.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateAndLeftFalseWrongDatatypeRight() {
		And and = factory.createAnd();

		and.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setRight(basicValueFrom(num));

		and.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"False And wrong datatype should evaluate to BooleanNode(false) (lazy evaluation)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEagerAndFalseFalse() {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createFalseLiteral()));

		eagerAnd.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False EagerAnd False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEagerAndTrueFalse() {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createFalseLiteral()));

		eagerAnd.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True EagerAnd False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEagerAndTrueTrue() {
		EagerAnd eagerAnd = factory.createEagerAnd();
		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerAnd.setRight(basicValueFrom(factory.createTrueLiteral()));

		eagerAnd.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True EagerAnd True should evaluate to BooleanNode(false)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateEagerAndWrongDatatypeLeft() {
		And and = factory.createAnd();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		and.setLeft(basicValueFrom(num));

		and.setRight(basicValueFrom(factory.createFalseLiteral()));

		StepVerifier.create(and.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateEagerAndLeftTrueWrongDatatypeRight() {
		EagerAnd eagerAnd = factory.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		StepVerifier.create(eagerAnd.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateEagerAndLeftFalseWrongDatatypeRight() {
		EagerAnd eagerAnd = factory.createEagerAnd();

		eagerAnd.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerAnd.setRight(basicValueFrom(num));

		StepVerifier.create(eagerAnd.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateOrInTarget() {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		StepVerifier.create(or.evaluate(ctx, false, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateOrFalseFalse() {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		or.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False Or False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateOrTrueFalse() {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createTrueLiteral()));
		or.setRight(basicValueFrom(factory.createFalseLiteral()));

		or.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True Or False should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateOrFalseTrue() {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));
		or.setRight(basicValueFrom(factory.createTrueLiteral()));

		or.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False Or True should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateOrWrongDatatypeLeft() {
		Or or = factory.createOr();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setLeft(basicValueFrom(num));

		or.setRight(basicValueFrom(factory.createTrueLiteral()));

		StepVerifier.create(or.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftFalse() {
		Or or = factory.createOr();
		or.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));

		StepVerifier.create(or.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateOrWrongDatatypeRightLeftTrue() {
		Or or = factory.createOr();

		or.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		or.setRight(basicValueFrom(num));

		or.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"True Or wrong datatype should evaluate to BooleanNode(true) (lazy evaluation)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateEagerOrFalseFalse() {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createFalseLiteral()));

		eagerOr.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False EagerOr False should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEagerOrTrueFalse() {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createTrueLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createFalseLiteral()));

		eagerOr.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True EagerOr False should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateEagerOrFalseTrue() {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));
		eagerOr.setRight(basicValueFrom(factory.createTrueLiteral()));

		eagerOr.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("False EagerOr True should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateEagerOrWrongDatatypeLeft() {
		EagerOr eagerOr = factory.createEagerOr();

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setLeft(basicValueFrom(num));

		eagerOr.setRight(basicValueFrom(factory.createTrueLiteral()));

		StepVerifier.create(eagerOr.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftFalse() {
		EagerOr eagerOr = factory.createEagerOr();
		eagerOr.setLeft(basicValueFrom(factory.createFalseLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		StepVerifier.create(eagerOr.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateEagerOrWrongDatatypeRightLeftTrue() {
		EagerOr eagerOr = factory.createEagerOr();

		eagerOr.setLeft(basicValueFrom(factory.createTrueLiteral()));

		NumberLiteral num = factory.createNumberLiteral();
		num.setNumber(TEST_NUMBER);
		eagerOr.setRight(basicValueFrom(num));

		StepVerifier.create(eagerOr.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateNotEqualsFalse() {
		NotEquals equals = factory.createNotEquals();
		equals.setLeft(basicValueFrom(factory.createTrueLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True NotEquals True should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateNotEqualsTrue() {
		NotEquals equals = factory.createNotEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null NotEquals True should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateNotEqualsNullLeftAndStringRightTrue() {
		NotEquals equals = factory.createNotEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null NotEquals String should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateNotEqualsNullLeftAndNullRightFalse() {
		NotEquals equals = factory.createNotEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		equals.setRight(basicValueFrom(factory.createNullLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null NotEquals Null should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEqualsTrue() {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createTrueLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("True Equals True should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateEqualsFalse() {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		equals.setRight(basicValueFrom(factory.createTrueLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null Equals True should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEqualsNullLeftAndStringRightFalse() {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("");
		equals.setRight(basicValueFrom(stringLiteral));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null Equals String should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateEqualsNullLeftAndNullRightTrue() {
		Equals equals = factory.createEquals();
		equals.setLeft(basicValueFrom(factory.createNullLiteral()));
		equals.setRight(basicValueFrom(factory.createNullLiteral()));

		equals.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Null Equals Null should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	private Flux<JsonNode> moreEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluateMoreEquals1ge1() {
		moreEquals(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 MoreEquals 1 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateMoreEquals1ge10() {
		moreEquals(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 MoreEquals 10 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateMoreEquals10ge1() {
		moreEquals(NUMBER_TEN, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("10 MoreEquals 1 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	private Flux<JsonNode> more(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluateMore1gt1() {
		more(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 More 1 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateMore1gt10() {
		more(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 More 10 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateMore10gt1() {
		more(NUMBER_TEN, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("10 More 1 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	private Flux<JsonNode> lessEquals(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluateLessEquals1le1() {
		lessEquals(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 LessEquals 1 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateLessEquals1le10() {
		lessEquals(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 LessEquals 10 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateLessEquals10le1() {
		lessEquals(NUMBER_TEN, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("10 LessEquals 1 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	private Flux<JsonNode> less(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluateLess1lt1() {
		less(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Less 1 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateLess1lt10() {
		less(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 Less 10 should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateLess10lt1() {
		less(NUMBER_TEN, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("10 Less 1 should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	private Flux<JsonNode> div(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluate1Div10() {
		div(NUMBER_ONE, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("1 Div 10 should evaluate to ValueNode(0.1)",
						JSON.numberNode(BigDecimal.valueOf(0.1)), result));
	}

	@Test
	public void evaluate10Div2() {
		div(NUMBER_TEN, NUMBER_TWO).take(1).subscribe(result -> assertEquals("10 Div 2 should evaluate to ValueNode(5)",
				JSON.numberNode(BigDecimal.valueOf(5.0)), result));
	}

	@Test
	public void evaluate1Div1() {
		div(NUMBER_ONE, NUMBER_ONE).take(1).subscribe(result -> assertEquals("1 Div 1 should evaluate to ValueNode(1)",
				JSON.numberNode(BigDecimal.valueOf(1)), result));
	}

	private Flux<JsonNode> minus(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluate2Minus10() {
		minus(NUMBER_TWO, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("2 Minus 10 should evaluate to ValueNode(-8)",
						JSON.numberNode(BigDecimal.valueOf(-8.0)), result));
	}

	@Test
	public void evaluate10Minus2() {
		minus(NUMBER_TEN, NUMBER_TWO).take(1)
				.subscribe(result -> assertEquals("10 Minus 2 should evaluate to ValueNode(8)",
						JSON.numberNode(BigDecimal.valueOf(8.0)), result));
	}

	@Test
	public void evaluate1Minus1() {
		minus(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Minus 1 should evaluate to ValueNode(0)",
						JSON.numberNode(BigDecimal.valueOf(0.0)), result));
	}

	private Flux<JsonNode> multi(BigDecimal leftNumber, BigDecimal rightNumber) {
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
	public void evaluate2Multi10() {
		multi(NUMBER_TWO, NUMBER_TEN).take(1)
				.subscribe(result -> assertEquals("2 Multi 10 should evaluate to ValueNode(20)",
						JSON.numberNode(BigDecimal.valueOf(20.0)), result));
	}

	@Test
	public void evaluate10Multi2() {
		multi(NUMBER_TEN, NUMBER_TWO).take(1)
				.subscribe(result -> assertEquals("10 Multi 2 should evaluate to ValueNode(20)",
						JSON.numberNode(BigDecimal.valueOf(20.0)), result));
	}

	@Test
	public void evaluate1Multi1() {
		multi(NUMBER_ONE, NUMBER_ONE).take(1)
				.subscribe(result -> assertEquals("1 Multi 1 should evaluate to ValueNode(1)",
						JSON.numberNode(BigDecimal.valueOf(1.0)), result));
	}

	@Test
	public void evaluateNotOnBooleanTrue() {
		Not not = factory.createNot();
		not.setExpression(basicValueFrom(factory.createTrueLiteral()));

		not.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Not True should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateNotOnBooleanFalse() {
		Not not = factory.createNot();
		not.setExpression(basicValueFrom(factory.createFalseLiteral()));

		not.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("Not False should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateNotOnWrongType() {
		Not not = factory.createNot();
		StringLiteral literal = factory.createStringLiteral();
		literal.setString("Makes no sense");
		not.setExpression(basicValueFrom(literal));

		StepVerifier.create(not.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void unaryMinus() {
		UnaryMinus unaryMinus = factory.createUnaryMinus();
		NumberLiteral numberLiteral = factory.createNumberLiteral();
		numberLiteral.setNumber(NUMBER_ONE);
		unaryMinus.setExpression(basicValueFrom(numberLiteral));

		unaryMinus.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("UnaryMinus 1 should evaluate to NumberNode(-1)",
						JSON.numberNode(BigDecimal.valueOf(-1L)), result));
	}

	@Test
	public void unaryMinusWrongType() {
		UnaryMinus unaryMinus = factory.createUnaryMinus();
		unaryMinus.setExpression(basicValueFrom(factory.createNullLiteral()));

		StepVerifier.create(unaryMinus.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluatePlusOnStrings() {
		Plus plus = factory.createPlus();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("part a &");
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString(" part b");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		plus.evaluate(ctx, true, null).take(1).subscribe(
				result -> assertEquals("Plus on Strings should evaluate to TextNode with concatenated strings",
						JSON.textNode("part a & part b"), result));
	}

	@Test
	public void evaluatePlusOnLeftString() {
		Plus plus = factory.createPlus();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("part a &");
		NumberLiteral rhs = factory.createNumberLiteral();
		rhs.setNumber(NUMBER_ONE);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		StepVerifier.create(plus.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluatePlusOnRightString() {
		Plus plus = factory.createPlus();
		NumberLiteral lhs = factory.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString("part a &");
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		StepVerifier.create(plus.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluatePlusOnNumbers() {
		Plus plus = factory.createPlus();
		NumberLiteral lhs = factory.createNumberLiteral();
		lhs.setNumber(NUMBER_ONE);
		NumberLiteral rhs = factory.createNumberLiteral();
		rhs.setNumber(NUMBER_TWO);
		plus.setLeft(basicValueFrom(lhs));
		plus.setRight(basicValueFrom(rhs));

		plus.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("1 Plus 2 should evaluate to ValueNode(3)",
						JSON.numberNode(BigDecimal.valueOf(3.0)), result));
	}

	@Test
	public void evaluateElementOfOnWrongType() {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("A");
		StringLiteral rhs = factory.createStringLiteral();
		rhs.setString("B");
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		StepVerifier.create(elementOf.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateElementOfOneElement() {
		ElementOf elementOf = factory.createElementOf();
		StringLiteral lhs = factory.createStringLiteral();
		lhs.setString("A");
		StringLiteral compare = factory.createStringLiteral();
		compare.setString("A");
		Array rhs = factory.createArray();
		rhs.getItems().add(basicValueFrom(compare));
		elementOf.setLeft(basicValueFrom(lhs));
		elementOf.setRight(basicValueFrom(rhs));

		elementOf.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("\"A\" ElementOf Array[\"A\"] should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateElementOfTwoElementsTrue() {
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

		elementOf.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"\"A\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateElementOfTwoElementsFalse() {
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

		elementOf.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals(
						"\"C\" ElementOf Array[\"A\", \"B\"] should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateElementOfNullLeftAndEmptyArrayFalse() {
		ElementOf elementOf = factory.createElementOf();
		elementOf.setLeft(basicValueFrom(factory.createNullLiteral()));
		elementOf.setRight(basicValueFrom(factory.createArray()));

		elementOf.evaluate(ctx, true, null).take(1).subscribe(
				result -> assertFalse("Null ElementOf Array[] should evaluate to false", result.asBoolean()));
	}

	@Test
	public void evaluateElementOfNullLeftAndArrayWithNullElementTrue() {
		ElementOf elementOf = factory.createElementOf();
		Array array = factory.createArray();
		array.getItems().add(basicValueFrom(factory.createNullLiteral()));
		elementOf.setLeft(basicValueFrom(factory.createNullLiteral()));
		elementOf.setRight(basicValueFrom(array));

		elementOf.evaluate(ctx, true, null).take(1).subscribe(
				result -> assertTrue("Null ElementOf Array[null] should evaluate to true", result.asBoolean()));
	}

	@Test
	public void evaluateRegExTrue() {
		String value = "test";
		String pattern = ".*";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		regEx.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("\"test\" RegEx \".*\" should evaluate to BooleanNode(true)",
						JSON.booleanNode(true), result));
	}

	@Test
	public void evaluateRegExFalse() {
		String value = "test";
		String pattern = ".";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		regEx.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertEquals("\"test\" RegEx \".\" should evaluate to BooleanNode(false)",
						JSON.booleanNode(false), result));
	}

	@Test
	public void evaluateRegExPatternError() {
		String value = "test";
		String pattern = "***";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		StepVerifier.create(regEx.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateRegExLeftNull() {
		Regex regex = factory.createRegex();
		regex.setLeft(basicValueFrom(factory.createNullLiteral()));
		StringLiteral stringLiteral = factory.createStringLiteral();
		stringLiteral.setString("");
		regex.setRight(basicValueFrom(stringLiteral));

		regex.evaluate(ctx, true, null).take(1)
				.subscribe(result -> assertFalse("NullLeft should evaluate to false", result.asBoolean()));
	}

	@Test
	public void evaluateRegExLeftWrongType() {
		String pattern = ".*";

		NumberLiteral left = factory.createNumberLiteral();
		left.setNumber(NUMBER_ONE);
		StringLiteral right = factory.createStringLiteral();
		right.setString(pattern);

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		StepVerifier.create(regEx.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void evaluateRegExRightWrongType() {
		String value = "test";

		StringLiteral left = factory.createStringLiteral();
		left.setString(value);
		NullLiteral right = factory.createNullLiteral();

		Regex regEx = factory.createRegex();
		regEx.setLeft(basicValueFrom(left));
		regEx.setRight(basicValueFrom(right));

		StepVerifier.create(regEx.evaluate(ctx, true, null)).expectError(PolicyEvaluationException.class).verify();
	}
}
