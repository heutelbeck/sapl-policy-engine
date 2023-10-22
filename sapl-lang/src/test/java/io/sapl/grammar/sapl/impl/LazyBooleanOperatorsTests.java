/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionReturnsErrors;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LazyBooleanOperatorsTests {

	@Test
	void andEvaluationShouldFailInPolicyTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate()).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void andEvaluationShouldFailInPolicySetTargetExpression() {
		var and = new AndImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate()).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void andEvaluationShouldFailWithNonBooleanLeft() {
		assertExpressionReturnsErrors("null && true");
	}

	@Test
	void andEvaluationShouldFailWithNonBooleanRight() {
		assertExpressionReturnsErrors("true && null");
	}

	@Test
	void andEvaluationShouldBeLazyAndReturnFalseInLazyCase() {
		assertExpressionEvaluatesTo("false && undefined", "false");

	}

	@Test
	void andEvaluationOfTrueAndFalseShouldBeFalse() {
		assertExpressionEvaluatesTo("true && false", "false");
	}

	@Test
	void andEvaluationTrueAndTrueShouldBeTrue() {
		assertExpressionEvaluatesTo("true && true", "true");
	}

	@Test
	void andEvaluationOfSequencesShouldReturnMatchingSequence() {
		var left  = mock(Expression.class);
		var right = mock(Expression.class);
		var and   = new AndImplCustom();
		and.left  = left;
		and.right = right;
		var leftSequence  = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE });
		when(left.evaluate()).thenReturn(leftSequence);
		when(right.evaluate()).thenReturn(rightSequence);
		StepVerifier.create(and.evaluate()).expectNext(Val.FALSE, Val.TRUE, Val.FALSE, Val.TRUE)
				.verifyComplete();
	}

	@Test
	void orEvaluationShouldFailInPolicyTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicyTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate()).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void orEvaluationShouldFailInPolicySetTargetExpression() {
		var and = new OrImplCustom();
		MockUtil.mockPolicySetTargetExpressionContainerExpression(and);
		StepVerifier.create(and.evaluate()).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void orEvaluationShouldFailWithNonBooleanLeft() {
		assertExpressionReturnsErrors("null || true");
	}

	@Test
	void orEvaluationShouldFailWithNonBooleanRight() {
		assertExpressionReturnsErrors("false || null");
	}

	@Test
	void orEvaluationShouldBeLazyAndReturnTrueInLazyCase() {
		assertExpressionEvaluatesTo("true || undefined", "true");
	}

	@Test
	void orEvaluationOfTrueAndFalseShouldBeTrue() {
		assertExpressionEvaluatesTo("true || false", "true");
	}

	@Test
	void orEvaluationOfFalseAndTrueShouldBeTrue() {
		assertExpressionEvaluatesTo("false || true", "true");
	}

	@Test
	void orEvaluationTrueAndTrueShouldBeTrue() {
		assertExpressionEvaluatesTo("true || true", "true");
	}

	@Test
	void orEvaluationOfSequencesShouldReturnMatchingSequence() {
		var left  = mock(Expression.class);
		var right = mock(Expression.class);
		var or    = new OrImplCustom();
		or.left  = left;
		or.right = right;
		var leftSequence  = Flux.fromArray(new Val[] { Val.FALSE, Val.TRUE, Val.FALSE });
		var rightSequence = Flux.fromArray(new Val[] { Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE });
		when(left.evaluate()).thenReturn(leftSequence);
		when(right.evaluate()).thenReturn(rightSequence);
		StepVerifier.create(or.evaluate()).expectNext(Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE,
				Val.TRUE, Val.TRUE, Val.FALSE, Val.TRUE, Val.FALSE).verifyComplete();
	}

}
