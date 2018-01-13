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
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.WildcardStep;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithParentObject;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyStepsWildcardStep {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test(expected = PolicyEvaluationException.class)
	public void applyToNullNode() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		WildcardStep step = factory.createWildcardStep();

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyToArray() throws PolicyEvaluationException {
		ArrayNode array = JSON.arrayNode();
		array.add(JSON.nullNode());
		array.add(JSON.booleanNode(true));
		array.add(JSON.booleanNode(false));

		ResultNode previousResult = new JsonNodeWithoutParent(array);

		ResultNode expectedResult = new JsonNodeWithoutParent(array);

		WildcardStep step = factory.createWildcardStep();
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Wildcard step applied to an array node should return the array", expectedResult, result);
	}

	@Test
	public void applyToResultArray() throws PolicyEvaluationException {
		List<AbstractAnnotatedJsonNode> list = new ArrayList<>();
		list.add(new JsonNodeWithoutParent(JSON.arrayNode()));
		list.add(new JsonNodeWithoutParent(JSON.nullNode()));

		ResultNode previousResult = new ArrayResultNode(list);

		ResultNode expectedResult = previousResult;

		WildcardStep step = factory.createWildcardStep();
		ResultNode result = previousResult.applyStep(step, ctx, true, null);

		assertEquals("Wildcard step applied to a result array node should return the result array", expectedResult,
				result);
	}

	@Test
	public void applyToObject() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		object.set("key1", JSON.nullNode());
		object.set("key2", JSON.booleanNode(true));
		object.set("key3", JSON.booleanNode(false));

		ResultNode previousResult = new JsonNodeWithoutParent(object);

		Multiset<AbstractAnnotatedJsonNode> expectedResultSet = HashMultiset.create();
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.nullNode(), object, "key1"));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(true), object, "key2"));
		expectedResultSet.add(new JsonNodeWithParentObject(JSON.booleanNode(false), object, "key3"));

		WildcardStep step = factory.createWildcardStep();

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());

		assertEquals("Wildcard step applied to an object should return all attribute values", expectedResultSet,
				resultSet);
	}
}
