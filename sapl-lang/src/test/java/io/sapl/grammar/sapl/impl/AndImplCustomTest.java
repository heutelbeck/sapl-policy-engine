package io.sapl.grammar.sapl.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@RunWith(MockitoJUnitRunner.class)
public class AndImplCustomTest {

	@Mock
	Expression left;
	@Mock
	Expression right;
	@Mock
	EvaluationContext ctx;

	AndImplCustom and;

	@Before
	public void setUp() {
		and = new AndImplCustom();
		and.left = left;
		and.right = right;
	}

	@Test(expected = NullPointerException.class)
	public void fluxCreationShouldFailWithNullEvaluationContext() {
		and.evaluate(null, null);
	}

	@Test
	public void evaluationSouldfailInPolicyTargetExpression() {
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationSouldfailInPolicySetTargetExpression() {
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldFailWithNonBooleanLeft() {
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfUndefined());
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldFailWithNonBooleanRight() {
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfTrue());
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfUndefined());
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void evaluationShouldBeLazyAndReturnFalseInLazyCase() {
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfFalse());
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
		verify(right, times(0)).evaluate(any(EvaluationContext.class), nullable(Val.class));
	}

	@Test
	public void evaluationOfTrueAndFalseShouldBeFalse() {
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfTrue());
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfFalse());
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void evaluationTrueAndTrueSouldBeTrue() {
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfTrue());
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(Val.fluxOfTrue());
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void evaluationOfSequencesSouldReturnMathicingSequence() {
		var leftSequence = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE });
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(leftSequence);
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(rightSequence);
		StepVerifier.create(and.evaluate(ctx, Val.UNDEFINED)).expectNext(Val.FALSE, Val.TRUE, Val.FALSE, Val.TRUE)
				.verifyComplete();
	}

}
