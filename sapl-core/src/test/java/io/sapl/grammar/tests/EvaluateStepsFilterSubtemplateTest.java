/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Array;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class EvaluateStepsFilterSubtemplateTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFilteringContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void basicExpressionWithStep() {
		BasicValue nullExpression = factory.createBasicValue();
		nullExpression.setValue(factory.createNullLiteral());

		Array array = factory.createArray();
		array.getItems().add(nullExpression);

		BasicValue expression = factory.createBasicValue();
		expression.setValue(array);

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.ZERO);
		expression.getSteps().add(step);

		expression.evaluate(ctx, true, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("Index step applied to BasicValue should return correct result",
						Optional.of(JSON.nullNode()), result));
	}

	@Test
	public void basicExpressionWithFilter() {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add("EMPTY_STRING");
		expression.setFilter(filter);

		expression.evaluate(ctx, true, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("Filter applied to BasicValue should return correct result",
						Optional.of(JSON.textNode("")), result));
	}

	@Test
	public void subtemplateNoArray() {
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());

		BasicValue subtemplate = factory.createBasicValue();
		subtemplate.setValue(factory.createNullLiteral());
		expression.setSubtemplate(subtemplate);

		StepVerifier.create(expression.evaluate(ctx, true, Optional.empty()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void subtemplateArray() {
		BasicValue expression = factory.createBasicValue();

		BasicValue item1 = factory.createBasicValue();
		item1.setValue(factory.createTrueLiteral());
		BasicValue item2 = factory.createBasicValue();
		item2.setValue(factory.createFalseLiteral());

		Array array = factory.createArray();
		array.getItems().add(item1);
		array.getItems().add(item2);

		expression.setValue(array);

		BasicValue subtemplate = factory.createBasicValue();
		subtemplate.setValue(factory.createNullLiteral());
		expression.setSubtemplate(subtemplate);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.nullNode());
		expectedResult.add(JSON.nullNode());

		expression.evaluate(ctx, true, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("Subtemplate applied to array should return correct result",
						Optional.of(expectedResult), result));
	}

}
