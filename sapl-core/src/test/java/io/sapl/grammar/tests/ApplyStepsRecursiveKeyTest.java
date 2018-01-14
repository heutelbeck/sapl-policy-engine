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
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void applyToNull() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());
		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Recursive key step applied to null node should return empty result array", expectedResult,
				result);
	}

	@Test
	public void applyToSimpleObject() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set(KEY, JSON.nullNode());

		ResultNode previousResult = new JsonNodeWithoutParent(object);

		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithParentObject(JSON.nullNode(), object, KEY));
		ResultNode expectedResult = new ArrayResultNode(list);

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Recursive key step applied to simple object should return result array with attribute value",
				expectedResult, result);
	}

	@Test
	public void applyToDeeperStructure() throws PolicyEvaluationException {
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

		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(true), object1, KEY));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(false), object2, KEY));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.arrayNode(), object3, KEY));

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Recursive key step should return result array with attribute value", expectedResultSet,
				resultSet);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		ObjectNode object1 = JSON.objectNode();
		object1.set(KEY, JSON.booleanNode(true));
		ObjectNode object2 = JSON.objectNode();
		object2.set(KEY, JSON.booleanNode(false));

		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();
		listIn.add(new JsonNodeWithoutParent(JSON.nullNode()));
		listIn.add(new JsonNodeWithoutParent(object1));
		listIn.add(new JsonNodeWithoutParent(object2));
		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(true), object1, KEY));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(false), object2, KEY));

		RecursiveKeyStep step = factory.createRecursiveKeyStep();
		step.setId(KEY);

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Recursive key step applied to result array should return result array with values of attributes",
				expectedResultSet, resultSet);
	}

}
