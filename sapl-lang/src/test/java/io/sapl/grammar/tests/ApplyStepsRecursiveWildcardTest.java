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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.RecursiveWildcardStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentArray;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;
import reactor.test.StepVerifier;

public class ApplyStepsRecursiveWildcardTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyToNullNode() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));
		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();
		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToArray() {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.booleanNode(false));
		array.add(object);

		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(array));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(array), 0));
		expectedResultSet.add(new JsonNodeWithParentArray(Val.ofTrue(), Val.of(array), 1));
		expectedResultSet.add(new JsonNodeWithParentArray(Val.of(object), Val.of(array), 2));
		expectedResultSet.add(new JsonNodeWithParentObject(Val.ofFalse(), Val.of(object), "key"));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Recursive wildcard step applied to array should return all items and attribute values",
					expectedResultSet, resultSet);
		});
	}

	@Test
	public void applyToObject() {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.booleanNode(true));

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.booleanNode(false));
		object.set("key3", array);

		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(Val.ofNull(), Val.of(object), "key1"));
		expectedResultSet.add(new JsonNodeWithParentObject(Val.ofTrue(), Val.of(object), "key2"));
		expectedResultSet.add(new JsonNodeWithParentObject(Val.of(array), Val.of(object), "key3"));
		expectedResultSet.add(new JsonNodeWithParentArray(Val.ofFalse(), Val.of(array), 0));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals("Recursive wildcard step applied to object should return all items and attribute values",
					expectedResultSet, resultSet);
		});
	}

	@Test
	public void applyToResultArray() {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		AbstractAnnotatedJsonNode annotatedNode1 = new JsonNodeWithParentArray(Val.of(array), Val.of(JSON.arrayNode()),
				0);
		listIn.add(annotatedNode1);

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.booleanNode(false));
		AbstractAnnotatedJsonNode annotatedNode2 = new JsonNodeWithParentArray(Val.of(object), Val.of(JSON.arrayNode()),
				0);
		listIn.add(annotatedNode2);

		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(annotatedNode1);
		expectedResultSet.add(new JsonNodeWithParentArray(Val.ofNull(), Val.of(array), 0));
		expectedResultSet.add(new JsonNodeWithParentArray(Val.ofTrue(), Val.of(array), 1));
		expectedResultSet.add(annotatedNode2);
		expectedResultSet.add(new JsonNodeWithParentObject(Val.ofFalse(), Val.of(object), "key"));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Recursive wildcard step applied to a result array node should return all items and attribute values",
					expectedResultSet, resultSet);
		});
	}

}
