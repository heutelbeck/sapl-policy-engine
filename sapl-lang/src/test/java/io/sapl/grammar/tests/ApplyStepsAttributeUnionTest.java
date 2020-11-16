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
import io.sapl.grammar.sapl.AttributeUnionStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsAttributeUnionTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void unionPropagatesErrors() {
		var unionStep = unionStep();
		StepVerifier.create(unionStep.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void applySlicingToNonObject() {
		var unionStep = unionStep();
		StepVerifier.create(unionStep.apply(Val.ofNull(), CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToEmptyObject() {
		var unionStep = unionStep("key1", "key2");
		var parentValue = Val.ofEmptyObject();
		StepVerifier.create(unionStep.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(Val.ofEmptyArray())
				.verifyComplete();
	}

	@Test
	public void applyToObject() throws JsonProcessingException {
		var parentValue = Val.ofJson("{ \"key1\" : null, \"key2\" : true,  \"key3\" : false }");
		var unionStep = unionStep("key3", "key2");
		var expectedResult = Val.ofJson("[ true, false ]");
		StepVerifier.create(unionStep.apply(parentValue, CTX, Val.UNDEFINED))
				.expectNextMatches(val -> ArrayUtil.arraysMatchWithSetSemantics(val, expectedResult)).verifyComplete();
	}

	private AttributeUnionStep unionStep(String... keys) {
		var step = FACTORY.createAttributeUnionStep();
		for (var key : keys)
			step.getAttributes().add(key);
		return step;
	}
}
