package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import io.sapl.api.interpreter.PolicyEvaluationException;
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

public class ApplyStepsAttributeUnionStep {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFunctionContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	@Test(expected = PolicyEvaluationException.class)
	public void applyToResultArray() throws PolicyEvaluationException {
		ResultNode previousResult = new ArrayResultNode(new ArrayList<>());

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void applyToNonObject() throws PolicyEvaluationException {
		ResultNode previousResult = new JsonNodeWithoutParent(JSON.nullNode());

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		previousResult.applyStep(step, ctx, true, null);
	}

	@Test
	public void applyToEmptyObject() throws PolicyEvaluationException {
		ObjectNode object = JSON.objectNode();
		ResultNode previousResult = new JsonNodeWithoutParent(object);

		ResultNode expectedResult = new ArrayResultNode(new ArrayList<>());
		
		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		ResultNode result = previousResult.applyStep(step, ctx, true, null);
		
		assertEquals("Attribute union applied to empty object should return empty result array",
				expectedResult, result);
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

		AttributeUnionStep step = factory.createAttributeUnionStep();
		step.getAttributes().add("key1");
		step.getAttributes().add("key2");

		ArrayResultNode result = (ArrayResultNode) previousResult.applyStep(step, ctx, true, null);
		Multiset<AbstractAnnotatedJsonNode> resultSet = HashMultiset.create(result.getNodes());
		
		assertEquals("Attribute union applied to object should return result array with corresponding attribute values",
				expectedResultSet, resultSet);
	}
}
