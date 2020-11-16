package io.sapl.grammar.sapl.impl;

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

public class AndImplCustomTest {

	EvaluationContext ctx = mock(EvaluationContext.class);

	@Test
	public void evaluationSouldfailInPolicyTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationSouldfailInPolicySetTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldFailWithNonBooleanLeft() throws IOException {
		var expression = ParserUtil.expression("null && true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldFailWithNonBooleanRight() throws IOException {
		var expression = ParserUtil.expression("true && null");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldBeLazyAndReturnFalseInLazyCase() throws IOException {
		var expression = ParserUtil.expression("false && undefined");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluationOfTrueAndFalseShouldBeFalse() throws IOException {
		var expression = ParserUtil.expression("true && false");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluationTrueAndTrueSouldBeTrue() throws IOException {
		var expression = ParserUtil.expression("true && true");
		StepVerifier.create(expression.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluationOfSequencesSouldReturnMathicingSequence() {
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

}
