package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class LazyBooleanOperatorsTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void andEvaluationSouldfailInPolicyTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void andEvaluationSouldfailInPolicySetTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void andEvaluationShouldFailWithNonBooleanLeft() throws IOException {
		var expression = ParserUtil.expression("null && true");
		expressionErrors(ctx, expression);
	}

	@Test
	public void andEvaluationShouldFailWithNonBooleanRight() throws IOException {
		var expression = ParserUtil.expression("true && null");
		expressionErrors(ctx, expression);
	}

	@Test
	public void andEvaluationShouldBeLazyAndReturnFalseInLazyCase() throws IOException {
		var expression = ParserUtil.expression("false && undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void andEvaluationOfTrueAndFalseShouldBeFalse() throws IOException {
		var expression = ParserUtil.expression("true && false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void andEvaluationTrueAndTrueSouldBeTrue() throws IOException {
		var expression = ParserUtil.expression("true && true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void andEvaluationOfSequencesSouldReturnMathicingSequence() {
		var left = mock(Expression.class);
		var right = mock(Expression.class);
		var and = new AndImplCustom();
		and.left = left;
		and.right = right;
		var leftSequence = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE });
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(leftSequence);
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(rightSequence);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE, Val.TRUE, Val.FALSE, Val.TRUE)
				.verifyComplete();
	}

	@Test
	public void orEvaluationSouldfailInPolicyTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void orEvaluationSouldfailInPolicySetTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void orEvaluationShouldFailWithNonBooleanLeft() throws IOException {
		var expression = ParserUtil.expression("null || true");
		expressionErrors(ctx, expression);
	}

	@Test
	public void orEvaluationShouldFailWithNonBooleanRight() throws IOException {
		var expression = ParserUtil.expression("false || null");
		expressionErrors(ctx, expression);
	}

	@Test
	public void orEvaluationShouldBeLazyAndReturnTrueInLazyCase() throws IOException {
		var expression = ParserUtil.expression("true || undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void orEvaluationOfTrueAndFalseShouldBeTrue() throws IOException {
		var expression = ParserUtil.expression("true || false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void orEvaluationOfFalseAndTrueShouldBeTrue() throws IOException {
		var expression = ParserUtil.expression("false || true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void orEvaluationTrueAndTrueSouldBeTrue() throws IOException {
		var expression = ParserUtil.expression("true || true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void orEvaluationOfSequencesSouldReturnMathicingSequence() {
		var left = mock(Expression.class);
		var right = mock(Expression.class);
		var or = new OrImplCustom();
		or.left = left;
		or.right = right;
		var leftSequence = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE, Val.FALSE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE });
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(leftSequence);
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(rightSequence);
		StepVerifier.create(or.evaluate(ctx, Val.UNDEFINED).log()).expectNext(Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE,
				Val.TRUE, Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE).verifyComplete();
	}

}
