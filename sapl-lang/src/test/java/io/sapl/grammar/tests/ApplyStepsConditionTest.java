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

import static io.sapl.grammar.sapl.impl.util.ArrayUtil.numberArray;
import static io.sapl.grammar.sapl.impl.util.BasicValueUtil.basicValueFrom;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.util.BasicValueUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsConditionTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void conditionPropagatesErrors() {
		var conditionStep = FACTORY.createConditionStep();
		StepVerifier.create(conditionStep.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void applySlicingToNonObjectNonArray() {
		var conditionStep = FACTORY.createConditionStep();
		StepVerifier.create(conditionStep.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToObjectConditionNotBoolean() throws JsonProcessingException {
		// Construct test case: { "key" : null }[?null] == []
		var parentValue = Val.ofJson("{ \"key\" : null }");
		var conditionStep = FACTORY.createConditionStep();
		var nullLiteral = FACTORY.createNullLiteral();
		var expression = FACTORY.createBasicValue();
		expression.setValue(nullLiteral);
		conditionStep.setExpression(expression);
		StepVerifier.create(conditionStep.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(Val.ofEmptyArray())
				.verifyComplete();
	}

	@Test
	public void applyToArray() {
		// Construct test case: [20, 5][@>10] == [20]
		var parentValue = numberArray(20, 5);
		var expectedResult = numberArray(20);

		var conditionStep = FACTORY.createConditionStep();
		var expression = FACTORY.createMore();
		expression.setLeft(FACTORY.createBasicRelative());
		var number = FACTORY.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(BasicValueUtil.basicValueFrom(number));
		conditionStep.setExpression(expression);

		StepVerifier.create(conditionStep.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	};

	@Test
	public void applyToObjectNode() throws JsonProcessingException {
		// Construct test case: { "key1" : 20, "key2" : 5 }[@>10] == [20]
		var parentValue = Val.ofJson("{ \"key1\" : 20, \"key2\" : 5 }");
		var expectedResult = numberArray(20);
		var conditionStep = FACTORY.createConditionStep();
		var expression = FACTORY.createMore();
		expression.setLeft(FACTORY.createBasicRelative());
		var number = FACTORY.createNumberLiteral();
		number.setNumber(BigDecimal.valueOf(10));
		expression.setRight(basicValueFrom(number));
		conditionStep.setExpression(expression);
		StepVerifier.create(conditionStep.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

}
