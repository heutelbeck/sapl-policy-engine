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

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsRecursiveIndexTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void recursiveIndexStepPropagatesErrors() {
		var step = FACTORY.createRecursiveIndexStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void recursiveIndexStepOnUndefinedEmpty() {
		// test case : undefined..[2] == []
		var step = FACTORY.createRecursiveIndexStep();
		StepVerifier.create(step.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNext(Val.ofEmptyArray())
				.verifyComplete();
	}

	@Test
	public void applyToNull() {
		// 0..[2] == []
		var parentValue = Val.ofNull();
		var step = FACTORY.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(2));
		var expectedValue = Val.ofEmptyArray();
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void applyToObject() throws JsonProcessingException {
		// object..[0]
		var parentValue = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}");
		var step = FACTORY.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		var expectedValue = Val.ofJson("[ { \"key\" : \"value2\" }, 1 ]");

		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED))
				.expectNextMatches(result -> ArrayUtil.arraysMatchWithSetSemantics(result, expectedValue))
				.verifyComplete();
	}
}
