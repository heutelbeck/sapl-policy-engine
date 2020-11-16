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

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.util.ArrayUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsRecursiveKeyTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void recursiveKeyStepPropagatesErrors() {
		var step = FACTORY.createRecursiveKeyStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void recursiveKeyStepOnUndefinedIsEmpty() {
		// test case : undefined..key == []
		var step = FACTORY.createRecursiveKeyStep();
		StepVerifier.create(step.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNext(Val.ofEmptyArray())
				.verifyComplete();
	}

	@Test
	public void applyToNull() {
		var parentValue = Val.NULL;
		var step = FACTORY.createRecursiveKeyStep();
		step.setId("key");
		var expectedValue = Val.ofEmptyArray();
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedValue).verifyComplete();
	}

	@Test
	public void applyToObject() throws JsonProcessingException {
		var parentValue = Val.ofJson(
				"{ \"key\" : \"value1\", \"array1\" : [ { \"key\" : \"value2\" }, { \"key\" : \"value3\" } ], \"array2\" : [ 1, 2, 3, 4, 5 ]}");
		var step = FACTORY.createRecursiveKeyStep();
		step.setId("key");
		var expectedValue = Val.ofJson("[ \"value1\", \"value2\", \"value3\" ]");
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED))
				.expectNextMatches(result -> ArrayUtil.arraysMatchWithSetSemantics(result, expectedValue))
				.verifyComplete();
	}

}
