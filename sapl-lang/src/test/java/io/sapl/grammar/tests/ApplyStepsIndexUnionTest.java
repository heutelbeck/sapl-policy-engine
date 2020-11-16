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

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsIndexUnionTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void indexUnionStepPropagatesErrors() {
		var step = FACTORY.createIndexUnionStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void applyIndexUnionStepToNonArrayFails() {
		// test case : undefined[...] -> fails
		var step = FACTORY.createIndexUnionStep();
		StepVerifier.create(step.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToArray() {
		// test case : [0,1,2,3,4,5,6,7,8,9][0,1,-2,10,-10] == [0,1,8]
		var parentValue = ArrayUtil.numberArrayRange(0, 9);
		var step = FACTORY.createIndexUnionStep();
		step.getIndices().add(BigDecimal.valueOf(0));
		step.getIndices().add(BigDecimal.valueOf(1));
		step.getIndices().add(BigDecimal.valueOf(-2));
		step.getIndices().add(BigDecimal.valueOf(10));
		step.getIndices().add(BigDecimal.valueOf(-10));
		var expectedResult = ArrayUtil.numberArray(0, 1, 8);
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

}
