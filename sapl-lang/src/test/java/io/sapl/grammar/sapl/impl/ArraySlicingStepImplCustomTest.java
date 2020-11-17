package io.sapl.grammar.sapl.impl;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ArraySlicingStepImplCustomTest {

	private EvaluationContext ctx;

	@Before
	public void before() {
		ctx = MockUtil.mockEvaluationContext();
	}

	@Test
	public void slicingPropagatesErrors() throws IOException {
		var expression = ParserUtil.expression("(1/0)[0:1]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void applySlicingToNoArray() throws IOException {
		var expression = ParserUtil.expression("\"abc\"[0:1]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void defaultsToIdentity() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][:]");
		var expected = Val.ofJson("[0,1,2,3,4,5,6,7,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void useCaseTestTwoNull() throws IOException {
		var expression = ParserUtil.expression("[1,2,3,4,5][2:]");
		var expected = Val.ofJson("[3,4,5]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void negativeToTest() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][7:-1]");
		var expected = Val.ofJson("[7,8]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeNegativeFrom() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][-3:9]");
		var expected = Val.ofJson("[7,8]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithFromGreaterThanToReturnsEmptyArray() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][4:1]");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithoutTo() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][7:]");
		var expected = Val.ofJson("[7,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithoutFrom() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][:3]");
		var expected = Val.ofJson("[0,1,2]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeFrom() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][-3:]");
		var expected = Val.ofJson("[7,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStep() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][: :-1]");
		var expected = Val.ofJson("[0,1,2,3,4,5,6,7,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][-2:6:-1]");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][-2:-5:-1]");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][1:5:-1]");
		var expected = Val.ofJson("[1,2,3,4]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingStepZeroErrors() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][1:5:0]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void applySlicingToResultArray() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][3:6]");
		var expected = Val.ofJson("[3,4,5]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithThreeStep() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][: :3]");
		var expected = Val.ofJson("[0,3,6,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithNegativeStep() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9][: :-3]");
		var expected = Val.ofJson("[1,4,7]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterDefaultsToIdentity() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9] |- { @[:] : nil }");
		var expected = Val.ofJson("[null,null,null,null,null,null,null,null,null,null]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterDefaultsToIdentityDescendStep() throws IOException {
		var expression = ParserUtil.expression("[[10,11,12,13,14],0,1,2,3,4,5,6,7,8,9] |- { @[:][-2:] : nil }");
		var expected = Val.ofJson("[[10,11,12,null,null],0,1,2,3,4,5,6,7,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterErrorOnZeroStep() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9] |- { @[: :0] : nil }");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void filterEmptyArray() throws IOException {
		var expression = ParserUtil.expression("[] |- { @[:] : nil }");
		var expected = Val.ofJson("[]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterNegativeStepArray() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9] |- { @[: :-2] : nil }");
		var expected = Val.ofJson("[null,1,null,3,null,5,null,7,null,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

	@Test
	public void filterNegativeTo() throws IOException {
		var expression = ParserUtil.expression("[0,1,2,3,4,5,6,7,8,9] |- { @[:-2] : nil }");
		var expected = Val.ofJson("[null,null,null,null,null,null,null,null,8,9]");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(expected).verifyComplete();
	}

}
