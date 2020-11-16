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

import static io.sapl.grammar.tests.ArrayUtil.numberArrayRange;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsWildcardTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void wildcardStepPropagatesErrors() {
		var step = FACTORY.createWildcardStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void wildcardStepOnOtherThanArrayOrObjectFails() {
		// test case : "".* -> fails
		var step = FACTORY.createWildcardStep();
		StepVerifier.create(step.apply(Val.of(""), CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void wildcardStepOnArrayIsIdentity() {
		// test case : [1,2,3,4,5,6,7,8,9].* == [1,2,3,4,5,6,7,8,9]
		var step = FACTORY.createWildcardStep();
		var array = numberArrayRange(0, 9);
		StepVerifier.create(step.apply(array, CTX, Val.UNDEFINED)).expectNext(array).verifyComplete();
	}

	@Test
	public void applyToObject() throws JsonProcessingException {
		// test case:
		// {"key1":null,"key2":true,"key3":false,"key4":{"other_key":123}}.*
		// == [ null, true, false , { "other_key" : 123 } ]
		var parentValue = Val
				.ofJson("{ \"key1\" : null, \"key2\" : true, \"key3\" : false , \"key4\" : { \"other_key\" : 123 } }");
		var step = FACTORY.createWildcardStep();
		var expectedResult = Val.ofJson("[ null, true, false , { \"other_key\" : 123 } ]");
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}
}
