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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LazyBooleanOperatorsTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	void andEvaluationSouldfailInPolicyTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void andEvaluationSouldfailInPolicySetTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void andEvaluationShouldFailWithNonBooleanLeft() {
		expressionErrors(CTX, "null && true");
	}

	@Test
	void andEvaluationShouldFailWithNonBooleanRight() {
		expressionErrors(CTX, "true && null");
	}

	@Test
	void andEvaluationShouldBeLazyAndReturnFalseInLazyCase() {
		expressionEvaluatesTo(CTX, "false && undefined", "false");

	}

	@Test
	void andEvaluationOfTrueAndFalseShouldBeFalse() {
		expressionEvaluatesTo(CTX, "true && false", "false");
	}

	@Test
	void andEvaluationTrueAndTrueSouldBeTrue() {
		expressionEvaluatesTo(CTX, "true && true", "true");
	}

	@Test
	void andEvaluationOfSequencesSouldReturnMathicingSequence() {
		var left = mock(Expression.class);
		var right = mock(Expression.class);
		var and = new AndImplCustom();
		and.left = left;
		and.right = right;
		var leftSequence = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE });
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(leftSequence);
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(rightSequence);
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.FALSE, Val.TRUE, Val.FALSE, Val.TRUE)
				.verifyComplete();
	}

	@Test
	void orEvaluationSouldfailInPolicyTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void orEvaluationSouldfailInPolicySetTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate(CTX, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void orEvaluationShouldFailWithNonBooleanLeft() {
		expressionErrors(CTX, "null || true");
	}

	@Test
	void orEvaluationShouldFailWithNonBooleanRight() {
		expressionErrors(CTX, "false || null");
	}

	@Test
	void orEvaluationShouldBeLazyAndReturnTrueInLazyCase() {
		expressionEvaluatesTo(CTX, "true || undefined", "true");
	}

	@Test
	void orEvaluationOfTrueAndFalseShouldBeTrue() {
		expressionEvaluatesTo(CTX, "true || false", "true");
	}

	@Test
	void orEvaluationOfFalseAndTrueShouldBeTrue() {
		expressionEvaluatesTo(CTX, "false || true", "true");
	}

	@Test
	void orEvaluationTrueAndTrueSouldBeTrue() {
		expressionEvaluatesTo(CTX, "true || true", "true");
	}

	@Test
	void orEvaluationOfSequencesSouldReturnMathicingSequence() {
		var left = mock(Expression.class);
		var right = mock(Expression.class);
		var or = new OrImplCustom();
		or.left = left;
		or.right = right;
		var leftSequence = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE, Val.FALSE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE });
		when(left.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(leftSequence);
		when(right.evaluate(any(EvaluationContext.class), nullable(Val.class))).thenReturn(rightSequence);
		StepVerifier.create(or.evaluate(CTX, Val.UNDEFINED)).expectNext(Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE,
				Val.TRUE, Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE).verifyComplete();
	}

}
