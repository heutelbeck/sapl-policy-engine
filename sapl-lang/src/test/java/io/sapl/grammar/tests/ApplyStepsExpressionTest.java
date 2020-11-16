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

import static io.sapl.grammar.sapl.impl.util.BasicValueUtil.basicValueFrom;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.util.ArrayUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsExpressionTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	@Test
	public void expressionStepPropagatesErrors() {
		var step = FACTORY.createExpressionStep();
		StepVerifier.create(step.apply(Val.error("TEST"), CTX, Val.UNDEFINED)).expectNext(Val.error("TEST"))
				.verifyComplete();
	}

	@Test
	public void applyExpressionStepToNonObjectNonArrayFails() {
		// test case : undefined[0] -> fails
		var step = FACTORY.createExpressionStep();
		StepVerifier.create(step.apply(Val.UNDEFINED, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void expressionEvaluatesToBooleanAndFails() {
		// test case: [][true] -> type mismatch
		var step = FACTORY.createExpressionStep();
		step.setExpression(basicValueFrom(FACTORY.createTrueLiteral()));

		StepVerifier.create(step.apply(Val.ofEmptyObject(), CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToArrayWithTextualExpressionResult() {
		// test case: [0,1,2,3,4,5,6,7,8,9]['key'] -> type mismatch error
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createStringLiteral();
		literal.setString("key");
		step.setExpression(basicValueFrom(literal));
		var parentValue = ArrayUtil.numberArrayRange(0, 9);
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToArrayWithNumberExpressionResult() {
		// test case: [0,1,2,3,4,5,6,7,8,9][5] == 5
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createNumberLiteral();
		literal.setNumber(BigDecimal.valueOf(5));
		step.setExpression(basicValueFrom(literal));
		var parentValue = ArrayUtil.numberArrayRange(0, 9);
		var expectedResult = Val.of(5);
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void applyToArrayWithNumberExpressionResultIndexOutOfBounds() {
		// test case: [0,1,2,3,4,5,6,7,8,9][100] -> Index out of bounds error
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createNumberLiteral();
		literal.setNumber(BigDecimal.valueOf(100));
		step.setExpression(basicValueFrom(literal));
		var parentValue = ArrayUtil.numberArrayRange(0, 9);
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applyToObjectWithTextualResult() throws JsonProcessingException {
		// test case : { "key" : true }['key'] == true
		var parentValue = Val.ofJson("{ \"key\" : true }");
		var expectedResult = Val.TRUE;
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createStringLiteral();
		literal.setString("key");
		step.setExpression(basicValueFrom(literal));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void applyToObjectWithTextualResultNonExistingKey() throws JsonProcessingException {
		// test case : { "key" : true }['key'] == true
		var parentValue = Val.ofJson("{ \"key\" : true }");
		var expectedResult = Val.UNDEFINED;
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createStringLiteral();
		literal.setString("other_key");
		step.setExpression(basicValueFrom(literal));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void applyToObjectWithNumericalResult() throws JsonProcessingException {
		// test case : { "key" : true }[5] --> fails
		var parentValue = Val.ofJson("{ \"key\" : true }");
		var step = FACTORY.createExpressionStep();
		var literal = FACTORY.createNumberLiteral();
		literal.setNumber(BigDecimal.valueOf(5));
		step.setExpression(basicValueFrom(literal));
		StepVerifier.create(step.apply(parentValue, CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

}
