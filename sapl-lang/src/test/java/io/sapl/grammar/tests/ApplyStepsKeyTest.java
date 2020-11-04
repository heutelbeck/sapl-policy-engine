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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.KeyStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyStepsKeyTest {

	private static final String KEY = "key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyToSimpleObject() {
		String value = "value";

		ObjectNode node = JSON.objectNode();
		node.set(KEY, JSON.textNode(value));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(node));

		ResultNode expectedResult = new JsonNodeWithParentObject(Val.of(JSON.textNode(value)), Val.of(node), KEY);

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals("Key step applied to object should return the value of the attribute",
						expectedResult, result));
	}

	@Test
	public void applyToNullNode() {
		JsonNodeWithoutParent previousResult = new JsonNodeWithoutParent(Val.undefined());

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ResultNode expectedResult = new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(), KEY);
		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Accessing null object should yield undefined.", expectedResult, result));
	}

	@Test
	public void applyToObjectWithoutKey() {
		JsonNodeWithoutParent previousResult = new JsonNodeWithoutParent(Val.of(JSON.objectNode()));

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ResultNode expectedResult = new JsonNodeWithParentObject(Val.undefined(), previousResult.getNode(), KEY);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(
				result -> assertEquals("Accessing empty object should yield undefined.", expectedResult, result));
	}

	@Test
	public void applyToArrayNodeWithNoObject() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));

		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"Key step applied to array node without objects should return empty ArrayResultNode",
						expectedResult, result));
	}

	@Test
	public void applyToArrayNodeWithObject() {
		JsonNode value = JSON.booleanNode(true);

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.objectNode());
		array.add(JSON.nullNode());
		ObjectNode object = JSON.objectNode();
		object.set(KEY, value);
		array.add(object);
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));

		JsonNodeWithParentObject expectedResultNode = new JsonNodeWithParentObject(Val.of(value), Val.of(object), KEY);
		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(expectedResultNode);

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Key step applied to array node should return ArrayResultNode with results", expectedResultSet,
					resultSet);
		});
	}

	@Test
	public void applyToResultArray() {
		String value = "value";

		ObjectNode node = JSON.objectNode();
		node.set(KEY, JSON.textNode(value));

		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		listIn.add(new JsonNodeWithoutParent(Val.of(node)));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(Val.of(JSON.textNode(value)), Val.of(node), KEY));

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Key step applied to ArrayResultNode should return an array with the correct values",
					expectedResultSet, resultSet);
		});

	}

}
