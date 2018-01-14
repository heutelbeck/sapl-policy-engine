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

import io.sapl.api.interpreter.PolicyEvaluationException;
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
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void applyToSimpleObject() throws PolicyEvaluationException {
		String value = "value";

		ObjectNode node = JSON.objectNode();
		node.set(KEY, JSON.textNode(value));
		ResultNode previousResult = new JsonNodeWithoutParent(node);

		ResultNode expectedResult = new JsonNodeWithParentObject(JSON.textNode(value), node, KEY);

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Key step applied to object should return the value of the attribute", expectedResult, result);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyToNullNode() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyToObjectWithoutKey() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.objectNode());

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyToArrayNodeWithNoObject() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Key step applied to array node without objects should return empty ArrayResultNode",
				expectedResult, result);
	}

	@Test
	public void applyToArrayNodeWithObject() throws PolicyEvaluationException {
		JsonNode value = JSON.booleanNode(true);

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.objectNode());
		array.add(JSON.nullNode());
		ObjectNode object = JSON.objectNode();
		object.set(KEY, value);
		array.add(object);
		ResultNode previousResult = new JsonNodeWithoutParent(array);

		JsonNodeWithParentObject expectedResultNode = new JsonNodeWithParentObject(value, object, KEY);
		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(expectedResultNode);

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Key step applied to array node should return ArrayResultNode with results",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		String value = "value";

		ObjectNode node = JSON.objectNode();
		node.set(KEY, JSON.textNode(value));

		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		listIn.add(new JsonNodeWithoutParent(node));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.textNode(value), node, KEY));

		KeyStep step = factory.createKeyStep();
		step.setId(KEY);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());
		
		assertEquals("Key step applied to ArrayResultNode should return an array with the correct values",
				expectedResultSet, resultSet);

	}

}
