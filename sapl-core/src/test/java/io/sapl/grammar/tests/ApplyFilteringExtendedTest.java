/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.FilterExtended;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.IndexStep;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyFilteringExtendedTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFilteringContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	private static final String REMOVE = "remove";

	@Test
	public void removeNoStepsNoEach() {
		JsonNode root = JSON.objectNode();

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		filter.getStatements().add(statement);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.verifyError(PolicyEvaluationException.class);
	}

	@Test
	public void removeEachNoArray() {
		JsonNode root = JSON.objectNode();

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.verifyError(PolicyEvaluationException.class);
	}

	@Test
	public void removeNoStepsEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		Optional<JsonNode> expectedResult = Optional.of(JSON.arrayNode());

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.consumeNextWith(result -> assertEquals("Function remove, no steps and each should return empty array",
						expectedResult, result))
				.thenCancel().verify();
	}

	@Test
	public void emptyStringNoStepsNoEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");
		filter.getStatements().add(statement);

		Optional<JsonNode> expectedResult = Optional.of(JSON.textNode(""));

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.consumeNextWith(result -> assertEquals(
						"Mock function EMPTY_STRING, no steps, no each should return empty string", expectedResult,
						result))
				.thenCancel().verify();
	}

	@Test
	public void emptyStringNoStepsEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.textNode(""));

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.consumeNextWith(result -> assertEquals(
						"Mock function EMPTY_STRING, no steps, each should array with empty strings",
						Optional.of(expectedResult), result))
				.thenCancel().verify();
	}

	@Test
	public void emptyStringEachNoArray() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.objectNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		statement.setTarget(factory.createBasicRelative());
		statement.getFsteps().add("EMPTY_STRING");

		IndexStep step = factory.createIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		BasicRelative expression = factory.createBasicRelative();
		expression.getSteps().add(step);
		statement.setTarget(expression);

		statement.setEach(true);

		filter.getStatements().add(statement);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.verifyError(PolicyEvaluationException.class);
	}

	@Test
	public void removeResultArrayNoEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add(REMOVE);
		filter.getStatements().add(statement);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void emptyStringResultArrayNoEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add("EMPTY_STRING");
		filter.getStatements().add(statement);

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.verifyError(PolicyEvaluationException.class);
	}

	@Test
	public void emptyStringResultArrayEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add("EMPTY_STRING");
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.booleanNode(true));

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.consumeNextWith(result -> assertEquals(
						"Mock function EMPTY_STRING applied to result array and each should replace selected elements by empty string",
						Optional.of(expectedResult), result))
				.thenCancel().verify();
	}

	@Test
	public void removeResultArrayEach() {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.booleanNode(true));

		FilterExtended filter = factory.createFilterExtended();
		FilterStatement statement = factory.createFilterStatement();
		BasicRelative target = factory.createBasicRelative();
		RecursiveIndexStep step = factory.createRecursiveIndexStep();
		step.setIndex(BigDecimal.valueOf(0));
		target.getSteps().add(step);

		statement.setTarget(target);
		statement.getFsteps().add(REMOVE);
		statement.setEach(true);
		filter.getStatements().add(statement);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.booleanNode(true));

		StepVerifier.create(filter.apply(Optional.of(root), ctx, false, Optional.empty()))
				.consumeNextWith(
						result -> assertEquals("Remove applied to result array and each should remove each element",
								Optional.of(expectedResult), result))
				.thenCancel().verify();
	}

}
