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

import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyFilteringSimpleTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFilteringContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	private static final String REMOVE = "remove";

	@Test
	public void removeNoEach() {
		JsonNode root = JSON.objectNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void removeEachNoArray() {
		ObjectNode root = JSON.objectNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);
		filter.setEach(true);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void removeEachArray() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);
		filter.setEach(true);

		JsonNode expectedResult = JSON.arrayNode();

		filter.apply(Optional.of(root), ctx, false, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("Remove applied to array with each should return empty array",
						Optional.of(expectedResult), result));
	}

	@Test
	public void emptyStringNoEach() {
		ArrayNode root = JSON.arrayNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add("EMPTY_STRING");

		JsonNode expectedResult = JSON.textNode("");

		filter.apply(Optional.of(root), ctx, false, Optional.empty()).take(1)
				.subscribe(result -> assertEquals(
						"Mock function EMPTY_STRING applied to array without each should return empty string",
						Optional.of(expectedResult), result));
	}

	@Test
	public void emptyStringEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.numberNode(5));

		FilterSimple filter = factory.createFilterSimple();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());
		Arguments arguments = factory.createArguments();
		arguments.getArgs().add(expression);
		filter.setArguments(arguments);
		filter.getFsteps().add("EMPTY_STRING");
		filter.setEach(true);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.textNode(""));

		filter.apply(Optional.of(root), ctx, false, Optional.empty()).take(1)
				.subscribe(result -> assertEquals(
						"Mock function EMPTY_STRING applied to array with each should return array with empty strings",
						Optional.of(expectedResult), result));
	}

}
