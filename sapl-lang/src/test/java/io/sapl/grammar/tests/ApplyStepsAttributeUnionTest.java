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

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeUnionStep;
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
import reactor.test.StepVerifier;

public class ApplyStepsAttributeUnionTest {

	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;

	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();

	private static FunctionContext functionCtx = new MockFunctionContext();

	private static EvaluationContext ctx = new EvaluationContext(functionCtx, variableCtx);

	@Test
	public void applyToResultArray() {
		ResultNode previousResult = new ArrayResultNode(new ArrayList<>());

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToNonObject() {
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(JSON.nullNode()));

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		StepVerifier.create(previousResult.applyStep(step, ctx, Val.undefined()))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void applyToEmptyObject() {
		ObjectNode object = JSON.objectNode();
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));

		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		previousResult.applyStep(step, ctx, Val.undefined()).take(1)
				.subscribe(result -> assertEquals(
						"Attribute union applied to empty object should return empty result array", expectedResult,
						result));
	}

	@Test
	public void applyToObject() {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.booleanNode(true));
		object.set("key3", JSON.booleanNode(false));
		ResultNode previousResult = new JsonNodeWithoutParent(Val.of(object));

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(Val.of(JSON.nullNode()), Val.of(object), "key1"));
		expectedResultSet.add(new JsonNodeWithParentObject(Val.of(JSON.booleanNode(true)), Val.of(object), "key2"));

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		previousResult.applyStep(step, ctx, Val.undefined()).take(1).subscribe(result -> {
			Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(((ArrayResultNode) result).getNodes());
			assertEquals(
					"Attribute union applied to object should return result array with corresponding attribute values",
					expectedResultSet, resultSet);
		});
	}

}
