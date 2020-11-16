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

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsKeyTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void keyStepPropagatesErrors() {
		var step = FACTORY.createKeyStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void keyStepToNonObjectUndefined() {
		// test case : true.key -> undefined
		var step = FACTORY.createKeyStep();
		StepVerifier.create(step.apply(Val.TRUE, CTX, Val.UNDEFINED)).expectNext(Val.UNDEFINED).verifyComplete();
	}

	@Test
	public void keyStepToEmptyObject() {
		// test case : {}.key == undefined
		var step = FACTORY.createKeyStep();
		step.setId("key");
		StepVerifier.create(step.apply(Val.ofEmptyObject(), CTX, Val.UNDEFINED)).expectNext(Val.UNDEFINED)
				.verifyComplete();
	}

	@Test
	public void keyStepToObject() {
		// test case : {"key" : true}.key == true
		var object = Val.JSON.objectNode();
		object.set("key", Val.JSON.booleanNode(true));
		var step = FACTORY.createKeyStep();
		step.setId("key");
		StepVerifier.create(step.apply(Val.of(object), CTX, Val.UNDEFINED)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void keyStepToArray() {
		// test case : [{"key" : true},{"key",123}].key == [true,123]
		var array = Val.JSON.arrayNode();
		var object1 = Val.JSON.objectNode();
		object1.set("key", Val.JSON.booleanNode(true));
		array.add(object1);
		var object2 = Val.JSON.objectNode();
		object2.set("key", Val.JSON.numberNode(123));
		array.add(object2);
		var parentValue = Val.of(array);

		var step = FACTORY.createKeyStep();
		step.setId("key");

		var resultArray = Val.JSON.arrayNode();
		resultArray.add(Val.JSON.booleanNode(true));
		resultArray.add(Val.JSON.numberNode(123));
		var expectedResult = Val.of(resultArray);

		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void keyStepToArrayNoMatch() {
		// test case : [{"key" : true},{"key",123}].x == []
		var array = Val.JSON.arrayNode();
		var object1 = Val.JSON.objectNode();
		object1.set("key", Val.JSON.booleanNode(true));
		array.add(object1);
		var object2 = Val.JSON.objectNode();
		object2.set("key", Val.JSON.numberNode(123));
		array.add(object2);
		var parentValue = Val.of(array);

		var step = FACTORY.createKeyStep();
		step.setId("x");

		var resultArray = Val.JSON.arrayNode();
		var expectedResult = Val.of(resultArray);

		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

}
