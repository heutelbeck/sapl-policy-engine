package io.sapl.grammar.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.BasicValue;
import io.sapl.grammar.sapl.FilterSimple;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;

public class ApplyFilteringSimpleTest {
	private static SaplFactory factory = SaplFactoryImpl.eINSTANCE;
	private static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static VariableContext variableCtx = new VariableContext();
	private static FunctionContext functionCtx = new MockFilteringContext();
	private static EvaluationContext ctx = new EvaluationContext(null, functionCtx, variableCtx);

	private static final String REMOVE = "remove";

	@Test(expected = PolicyEvaluationException.class)
	public void removeNoEach() throws PolicyEvaluationException {
		JsonNode root = JSON.objectNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);

		filter.apply(root, ctx, null);
	}

	@Test(expected = PolicyEvaluationException.class)
	public void removeEachNoArray() throws PolicyEvaluationException {
		ObjectNode root = JSON.objectNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);
		filter.setEach(true);

		filter.apply(root, ctx, null);
	}

	@Test
	public void removeEachArray() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add(REMOVE);
		filter.setEach(true);

		JsonNode expectedResult = JSON.arrayNode();

		JsonNode result = filter.apply(root, ctx, null);

		assertEquals("Remove applied to array with each should return empty array", expectedResult, result);
	}

	@Test
	public void emptyStringNoEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();

		FilterSimple filter = factory.createFilterSimple();
		filter.getFsteps().add("EMPTY_STRING");

		JsonNode expectedResult = JSON.textNode("");

		JsonNode result = filter.apply(root, ctx, null);

		assertEquals("Mock function EMPTY_STRING applied to array without each should return empty string",
				expectedResult, result);
	}

	@Test
	public void emptyStringEach() throws PolicyEvaluationException {
		ArrayNode root = JSON.arrayNode();
		root.add(JSON.nullNode());
		root.add(JSON.numberNode(5));

		FilterSimple filter = factory.createFilterSimple();
		BasicValue expression = factory.createBasicValue();
		expression.setValue(factory.createNullLiteral());
		Arguments arguments = factory.createArguments();
		arguments.getArgs().add(expression);
		filter.setArguments(arguments);
		filter.getFsteps().add("EMPTY_STRING");
		filter.setEach(true);

		ArrayNode expectedResult = JSON.arrayNode();
		expectedResult.add(JSON.textNode(""));
		expectedResult.add(JSON.textNode(""));

		JsonNode result = filter.apply(root, ctx, null);

		assertEquals("Mock function EMPTY_STRING applied to array with each should return array with empty strings",
				expectedResult, result);
	}

}
