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
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.grammar.sapl.RecursiveKeyStep;
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

public class ApplyStepsRecursiveKeyTest {

	private static String KEY = "key";

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyToNull() {
		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(JSON.nullNode()));

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);

		ResultNode expectedResult = new JsonNodeWithoutParent(Optional.empty());

		previousResult.applyStep(step, ctx, true, Optional.empty()).take(1)
				.subscribe(result -> assertEquals("key of undefined is undefined", expectedResult, result));
	}

	@Test
	public void applyToSimpleObject() {
		ObjectNode object = JSON.objectNode();
		object.set(KEY, JSON.nullNode());

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(object));

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentObject(Optional.of(JSON.nullNode()), Optional.of(object), KEY));
		ResultNode expectedResult = new ArrayResultNode(list);

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);
		previousResult.applyStep(step, ctx, true, Optional.empty()).take(1)
				.subscribe(result -> assertEquals(
						"Recursive key step applied to simple object should return result array with attribute value",
						expectedResult, result));
	}

	@Test
	public void applyToDeeperStructure() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());

		ObjectNode object1 = JSON.objectNode();
		object1.set(KEY, JSON.booleanNode(true));
		array.add(object1);

		ObjectNode object2 = JSON.objectNode();
		object2.set(KEY, JSON.booleanNode(false));

		ObjectNode object3 = JSON.objectNode();
		object3.set(KEY, JSON.arrayNode());
		object2.set("key2", object3);

		array.add(object2);

		ResultNode previousResult = new JsonNodeWithoutParent(Optional.of(array));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet
				.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(true)), Optional.of(object1), KEY));
		expectedResultSet
				.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(false)), Optional.of(object2), KEY));
		expectedResultSet.add(new JsonNodeWithParentObject(Optional.of(JSON.arrayNode()), Optional.of(object3), KEY));

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, true, Optional.empty()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Recursive key step should return result array with attribute value", expectedResultSet,
					resultSet);
		});
	}

	@Test
	public void applyToResultArray() {
		ObjectNode object1 = JSON.objectNode();
		object1.set(KEY, JSON.booleanNode(true));
		ObjectNode object2 = JSON.objectNode();
		object2.set(KEY, JSON.booleanNode(false));

		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		listIn.add(new JsonNodeWithoutParent(Optional.of(JSON.nullNode())));
		listIn.add(new JsonNodeWithoutParent(Optional.of(object1)));
		listIn.add(new JsonNodeWithoutParent(Optional.of(object2)));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet
				.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(true)), Optional.of(object1), KEY));
		expectedResultSet
				.add(new JsonNodeWithParentObject(Optional.of(JSON.booleanNode(false)), Optional.of(object2), KEY));

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, true, Optional.empty()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Recursive key step applied to result array should return result array with values of attributes",
					expectedResultSet, resultSet);
		});
	}

}
