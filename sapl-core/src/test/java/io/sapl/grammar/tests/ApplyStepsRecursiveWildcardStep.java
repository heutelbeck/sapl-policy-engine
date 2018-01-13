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

public class ApplyStepsRecursiveWildcardStep {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test
	public void applyToNullNode() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Recursive wildcard step applied to null node should return empty result array", expectedResult,
				result);
	}

	@Test
	public void applyToArray() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.booleanNode(false));
		array.add(object);

		ResultNode previousResult = new JsonNodeWithoutParent(array);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.nullNode(), array, 0));
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.booleanNode(true), array, 1));
		expectedResultSet.add(new JsonNodeWithParentArray(object, array, 2));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(false), object, "key"));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Recursive wildcard step applied to array should return all items and attribute values",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToObject() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.booleanNode(true));

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.booleanNode(false));
		object.set("key3", array);

		ResultNode previousResult = new JsonNodeWithoutParent(object);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.nullNode(), object, "key1"));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(true), object, "key2"));
		expectedResultSet.add(new JsonNodeWithParentObject(array, object, "key3"));
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.booleanNode(false), array, 0));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Recursive wildcard step applied to object should return all items and attribute values",
				expectedResultSet, resultSet);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> listIn = new ArrayList<>();

		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		AbstractAnnotatedJsonNode annotatedNode1 = new JsonNodeWithParentArray(array, JSON.arrayNode(), 0);
		listIn.add(annotatedNode1);

		ObjectNode object = JSON.objectNode();
		object.set("key", JSON.booleanNode(false));
		AbstractAnnotatedJsonNode annotatedNode2 = new JsonNodeWithParentArray(object, JSON.arrayNode(), 0);
		listIn.add(annotatedNode2);

		ResultNode previousResult = new ArrayResultNode(listIn);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(annotatedNode1);
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.nullNode(), array, 0));
		expectedResultSet.add(new JsonNodeWithParentArray(JSON.booleanNode(true), array, 1));
		expectedResultSet.add(annotatedNode2);
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(false), object, "key"));

		RecursiveWildcardStep step = factory.createRecursiveWildcardStep();

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals(
				"Recursive wildcard step applied to a result array node should return all items and attribute values",
				expectedResultSet, resultSet);
	}
}
